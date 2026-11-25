package com.valora.icebeats.usecases

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.valora.icebeats.media.LinkMediaResolver
import com.valora.icebeats.media.MediaInspector
import com.valora.icebeats.media.TemporaryMediaManager
import com.valora.icebeats.providers.ProviderSearchManager
import com.valora.icebeats.recognition.AudioExtractor
import com.valora.icebeats.recognition.AudioSegmentSelector
import com.valora.icebeats.recognition.AudioSource
import com.valora.icebeats.recognition.CompositeRecognitionEngine
import com.valora.icebeats.recognition.MusicRecognitionEngine
import com.valora.icebeats.recognition.RecognitionCache
import com.valora.icebeats.share.SharedContent
import com.valora.icebeats.share.SharedContentType
import com.valora.icebeats.songs.IdentifiedSong
import com.valora.icebeats.songs.SongMetadataNormalizer
import com.valora.icebeats.utils.GlobalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

enum class IdentificationStep {
    VALIDATING,
    RESOLVING_LINK,
    ANALYZING_MEDIA,
    EXTRACTING_AUDIO,
    IDENTIFYING,
    SEARCHING_AIRBEATS,
    COMPLETE
}

class IdentifySharedMusicUseCase(
    private val context: Context,
    private val tempManager: TemporaryMediaManager = TemporaryMediaManager(context),
    private val audioExtractor: AudioExtractor = AudioExtractor(context, tempManager),
    private val linkMediaResolver: LinkMediaResolver = LinkMediaResolver(context, tempManager),
    private val recognitionEngine: MusicRecognitionEngine = CompositeRecognitionEngine(context),
    private val providerSearchManager: ProviderSearchManager = ProviderSearchManager(context)
) {

    companion object {
        private const val TAG = "IdentifyMusicUseCase"
    }

    fun execute(content: SharedContent): Flow<Pair<IdentificationStep, IdentificationOutcome?>> = flow {
        emit(Pair(IdentificationStep.VALIDATING, null))
        GlobalLog.append(Log.INFO, TAG, "=== Starting Music Identification Pipeline ===")
        GlobalLog.append(Log.INFO, TAG, "Input type: ${content.type}, text: '${content.text.orEmpty().take(120)}', uri: ${content.uri}")

        // 1. Check network connectivity
        if (!isNetworkConnected(context)) {
            GlobalLog.append(Log.ERROR, TAG, "Network check failed: No active internet connection")
            emit(Pair(IdentificationStep.COMPLETE, IdentificationOutcome.NetworkError))
            return@flow
        }
        GlobalLog.append(Log.INFO, TAG, "Network check: Connected")

        var downloadedMediaFile: File? = null
        val targetUri: Uri

        // 2. Resolve media URI or download audio from shared link
        if (content.type == SharedContentType.URL) {
            val url = content.text.orEmpty().trim()
            if (url.isBlank()) {
                GlobalLog.append(Log.ERROR, TAG, "Validation error: Empty URL shared")
                emit(Pair(IdentificationStep.COMPLETE, IdentificationOutcome.UnsupportedMedia("Empty URL shared")))
                return@flow
            }
            emit(Pair(IdentificationStep.RESOLVING_LINK, null))
            GlobalLog.append(Log.INFO, TAG, "Resolving media for URL: $url")
            val resolvedFile = withContext(Dispatchers.IO) {
                linkMediaResolver.resolveMedia(url)
            }
            if (resolvedFile == null || !resolvedFile.exists()) {
                Log.w(TAG, "LinkMediaResolver could not download media from $url, prompting user")
                GlobalLog.append(Log.WARN, TAG, "LinkMediaResolver could not download media from $url")
                emit(Pair(IdentificationStep.COMPLETE, IdentificationOutcome.IsUrlOnly(url)))
                return@flow
            }
            GlobalLog.append(Log.INFO, TAG, "Media resolved: ${resolvedFile.name} (${resolvedFile.length()} bytes)")
            downloadedMediaFile = resolvedFile
            targetUri = Uri.fromFile(resolvedFile)
        } else {
            val uri = content.uri
            if (uri == null) {
                GlobalLog.append(Log.ERROR, TAG, "Validation error: No media URI provided")
                emit(Pair(IdentificationStep.COMPLETE, IdentificationOutcome.UnsupportedMedia("No media URI provided")))
                return@flow
            }
            GlobalLog.append(Log.INFO, TAG, "Local media URI received: $uri")
            targetUri = uri
        }

        // 3. Inspect Media
        emit(Pair(IdentificationStep.ANALYZING_MEDIA, null))
        GlobalLog.append(Log.INFO, TAG, "Analyzing media metadata via MediaInspector...")
        val mediaInfo = withContext(Dispatchers.IO) {
            MediaInspector.inspect(context, targetUri)
        }
        GlobalLog.append(Log.INFO, TAG, "Media metadata: duration=${mediaInfo.durationMs}ms, hasAudio=${mediaInfo.hasAudio}, audioMime=${mediaInfo.audioMime}")

        if (!mediaInfo.hasAudio) {
            GlobalLog.append(Log.WARN, TAG, "No audio track detected in media file")
            emit(Pair(IdentificationStep.COMPLETE, IdentificationOutcome.NoAudio))
            return@flow
        }

        var currentAudioSource: AudioSource? = null

        try {
            // 4. Extract Audio Segments dynamically and Recognize
            emit(Pair(IdentificationStep.EXTRACTING_AUDIO, null))

            val maxCandidates = AudioSegmentSelector.getCandidateCount(mediaInfo.durationMs)
            var currentCandidate = 0
            var recResult = com.valora.icebeats.recognition.RecognitionResult(success = false)
            var audioHash: String? = null

            while (currentCandidate < maxCandidates && (!recResult.success || recResult.title.isNullOrBlank())) {
                val window = AudioSegmentSelector.selectSegment(mediaInfo.durationMs, candidateIndex = currentCandidate)
                if (currentCandidate > 0) {
                    tempManager.cleanup(currentAudioSource?.file)
                    GlobalLog.append(Log.INFO, TAG, "Candidate ${currentCandidate - 1} returned no match. Trying candidate $currentCandidate (${window.startMs / 1000}s - ${(window.startMs + window.durationMs) / 1000}s)...")
                } else {
                    GlobalLog.append(Log.INFO, TAG, "Extracting audio segment 0 (${window.startMs / 1000}s - ${(window.startMs + window.durationMs) / 1000}s)...")
                }

                currentAudioSource = audioExtractor.extractSegment(
                    uri = targetUri,
                    mediaInfo = mediaInfo,
                    startMs = window.startMs,
                    durationMs = window.durationMs
                )
                GlobalLog.append(Log.INFO, TAG, "Segment $currentCandidate extracted: ${currentAudioSource.file.name} (${currentAudioSource.file.length()} bytes)")

                if (currentCandidate == 0) {
                    audioHash = RecognitionCache.computeHash(currentAudioSource.file)
                    val cachedSong = RecognitionCache.get(audioHash)
                    if (cachedSong != null) {
                        GlobalLog.append(Log.INFO, TAG, "Cache hit for audio: '${cachedSong.title}' by '${cachedSong.artist}'")
                        recResult = com.valora.icebeats.recognition.RecognitionResult(
                            success = true,
                            title = cachedSong.title,
                            artist = cachedSong.artist,
                            album = cachedSong.album,
                            albumArtUrl = cachedSong.albumArtUrl,
                            provider = "Cache"
                        )
                        break
                    }
                }

                emit(Pair(IdentificationStep.IDENTIFYING, null))
                GlobalLog.append(Log.INFO, TAG, "Recognizing segment $currentCandidate with Shazam...")
                recResult = recognitionEngine.recognize(currentAudioSource)
                currentCandidate++
            }

            val identifiedSong: IdentifiedSong = if (recResult.success && !recResult.title.isNullOrBlank()) {
                val normalized = SongMetadataNormalizer.normalize(recResult)
                GlobalLog.append(Log.INFO, TAG, "Song recognized: '${normalized.title}' by '${normalized.artist}'")
                audioHash?.let { RecognitionCache.put(it, normalized) }
                normalized
            } else {
                val ytTitle = linkMediaResolver.lastSuggestedTitle
                if (!ytTitle.isNullOrBlank()) {
                    GlobalLog.append(Log.INFO, TAG, "Shazam returned no match across segments. Falling back to video metadata: '$ytTitle'")
                    SongMetadataNormalizer.normalize(
                        com.valora.icebeats.recognition.RecognitionResult(
                            success = true,
                            title = ytTitle,
                            artist = linkMediaResolver.lastSuggestedArtist,
                            provider = "YouTube"
                        )
                    )
                } else {
                    GlobalLog.append(Log.WARN, TAG, "Recognition completed: No music matched")
                    emit(Pair(IdentificationStep.COMPLETE, IdentificationOutcome.NoMusicFound))
                    return@flow
                }
            }

            // 5. Search existing IceBeats providers
            emit(Pair(IdentificationStep.SEARCHING_AIRBEATS, null))
            GlobalLog.append(Log.INFO, TAG, "Searching IceBeats library for '${identifiedSong.title}' - '${identifiedSong.artist}'...")
            val candidates = providerSearchManager.searchProviders(identifiedSong)
            val bestMatch = candidates.firstOrNull()
            GlobalLog.append(Log.INFO, TAG, "IceBeats search completed: ${candidates.size} results found (Best: ${bestMatch?.title ?: "None"})")

            emit(
                Pair(
                    IdentificationStep.COMPLETE,
                    IdentificationOutcome.Success(
                        song = identifiedSong,
                        airBeatsMatch = bestMatch,
                        candidates = candidates
                    )
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Identification pipeline failed", e)
            GlobalLog.append(Log.ERROR, TAG, "Identification pipeline error: ${e.message}")
            emit(Pair(IdentificationStep.COMPLETE, IdentificationOutcome.Error(e.localizedMessage ?: "Processing error")))
        } finally {
            // Clean up temporary audio & downloaded files to protect user storage and privacy
            tempManager.cleanup(currentAudioSource?.file)
            tempManager.cleanup(downloadedMediaFile)
            tempManager.cleanupAll()
            GlobalLog.append(Log.INFO, TAG, "Identification pipeline finished (temp files cleaned)")
        }
    }

    private fun isNetworkConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }
}
