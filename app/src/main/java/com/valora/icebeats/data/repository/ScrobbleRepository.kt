package com.valora.icebeats.data.repository

import android.os.SystemClock
import com.valora.icebeats.data.local.LastFmSessionPreferences
import com.valora.icebeats.data.network.LastFmApiClient
import com.valora.icebeats.data.network.LastFmRateGuard
import com.valora.icebeats.data.network.LastFmSigner
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.db.entities.AlbumEntity
import com.valora.icebeats.db.entities.ArtistEntity
import com.valora.icebeats.db.entities.Event
import com.valora.icebeats.db.entities.SongAlbumMap
import com.valora.icebeats.db.entities.SongArtistMap
import com.valora.icebeats.db.entities.SongEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

data class NowPlayingTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val thumbnailUrl: String? = null,
    val packageName: String? = null,
    val songId: String? = null,
    val isPlaying: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
)

@Singleton
class ScrobbleRepository @Inject constructor(
    private val database: MusicDatabase,
    private val lastFmApi: LastFmApiClient,
    private val lastFmSessionPreferences: LastFmSessionPreferences,
    private val rateGuard: LastFmRateGuard,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val callMutex = Mutex()
    private var lastWriteAtElapsed = 0L

    @Volatile private var lastNowPlayingKey: String? = null
    @Volatile private var lastRemoteNowPlayingKey: String? = null

    private val _nowPlaying = MutableStateFlow<NowPlayingTrack?>(null)
    val nowPlaying: StateFlow<NowPlayingTrack?> = _nowPlaying.asStateFlow()

    private val _scrobbleEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrobbleEvents: SharedFlow<Unit> = _scrobbleEvents.asSharedFlow()

    private data class PendingScrobble(
        val artist: String,
        val track: String,
        val album: String?,
        val timestampSec: Long,
    )
    private val offlineQueue = ConcurrentLinkedQueue<PendingScrobble>()

    sealed interface Result {
        data object Success : Result
        data object NoSessionKey : Result
        data class Failed(
            val message: String,
            val retryable: Boolean = false,
        ) : Result
    }

    private fun cleanArtist(raw: String): String {
        val trimmed = raw.trim()
        val suffixes = listOf(" - Topic", " Topic")
        for (suffix in suffixes) {
            if (trimmed.endsWith(suffix, ignoreCase = true)) {
                return trimmed.substring(0, trimmed.length - suffix.length).trim()
            }
        }
        return trimmed
    }

    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String?,
        packageName: String? = null,
    ): Result {
        val safeArtist = cleanArtist(artist.trim().ifEmpty { "Unknown Artist" })
        val safeTrack = track.trim().ifEmpty { "Unknown Track" }
        val key = "${safeArtist.lowercase()}|${safeTrack.lowercase()}"

        val (matchedSong, matchedArtist) = withContext(Dispatchers.IO) {
            val song = database.findSongByTitleAndArtist(safeTrack, safeArtist)
                ?: database.findSongByTitle(safeTrack)
            val art = if (song == null) database.artistByName(safeArtist) else null
            song to art
        }

        val displayTitle = matchedSong?.title ?: safeTrack
        val displayArtist = matchedSong?.artists?.firstOrNull()?.name ?: matchedArtist?.name ?: safeArtist
        val displayAlbum = matchedSong?.album?.title ?: album?.trim()
        val thumb = matchedSong?.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: matchedArtist?.thumbnailUrl?.takeIf { it.isNotBlank() }

        _nowPlaying.value = NowPlayingTrack(
            title = displayTitle,
            artist = displayArtist,
            album = displayAlbum,
            thumbnailUrl = thumb,
            packageName = packageName,
            songId = matchedSong?.id,
            isPlaying = true,
        )

        lastNowPlayingKey = key

        // Send now playing to Last.fm if authenticated with session key
        val session = lastFmSessionPreferences.currentSession
        if (session.hasSessionKey && key != lastRemoteNowPlayingKey) {
            val remoteRes = signedCall(
                method = "track.updateNowPlaying",
                extra = buildMap {
                    put("artist", displayArtist)
                    put("track", displayTitle)
                    if (!displayAlbum.isNullOrBlank()) put("album", displayAlbum)
                },
            )
            if (remoteRes is Result.Success) {
                lastRemoteNowPlayingKey = key
            }
        }

        return Result.Success
    }

    suspend fun submitNowPlaying(
        artist: String,
        track: String,
        album: String?,
        packageName: String? = null,
    ): Result = updateNowPlaying(artist, track, album, packageName)

    fun clearNowPlaying() {
        lastNowPlayingKey = null
        lastRemoteNowPlayingKey = null
        _nowPlaying.value = null
    }

    suspend fun scrobble(
        artist: String,
        track: String,
        album: String?,
        timestampSec: Long,
        durationMs: Long = 0L,
        playTimeMs: Long = 0L,
    ): Result {
        return runCatching {
            val safeArtist = cleanArtist(artist.trim().ifEmpty { "Unknown Artist" })
            val safeTrack = track.trim().ifEmpty { "Unknown Track" }
            val actualDurationSec = if (durationMs > 0L) (durationMs / 1000L).toInt() else 180
            val actualPlayTimeMs = if (playTimeMs > 0L) playTimeMs else if (durationMs > 0L) durationMs else 180_000L

            withContext(Dispatchers.IO) {
                // 1. Check if the song already exists in IceBeats
                val matchedSong = database.findSongByTitleAndArtist(safeTrack, safeArtist)
                    ?: database.findSongByTitle(safeTrack)

                val finalSongId: String
                if (matchedSong != null) {
                    finalSongId = matchedSong.id
                } else {
                    // Not in database: generate deterministic IDs
                    finalSongId = "scrobble_" + java.lang.Integer.toHexString("$safeArtist|$safeTrack".hashCode())

                    val existingArtist = database.artistByName(safeArtist)
                    val finalArtistId = existingArtist?.id
                        ?: ("artist_" + java.lang.Integer.toHexString(safeArtist.lowercase().hashCode()))

                    val existing = database.getSongById(finalSongId)
                    if (existing == null) {
                        database.insert(
                            SongEntity(
                                id = finalSongId,
                                title = safeTrack,
                                duration = actualDurationSec,
                                albumName = album,
                                thumbnailUrl = existingArtist?.thumbnailUrl ?: "",
                                inLibrary = null,
                            )
                        )
                        if (existingArtist == null) {
                            database.insert(
                                ArtistEntity(
                                    id = finalArtistId,
                                    name = safeArtist,
                                    thumbnailUrl = "",
                                )
                            )
                        }
                        database.insert(
                            SongArtistMap(
                                songId = finalSongId,
                                artistId = finalArtistId,
                                position = 0,
                            )
                        )
                        if (!album.isNullOrBlank()) {
                            val existingAlbum = database.albumByName(album.trim())
                            val finalAlbumId = existingAlbum?.id
                                ?: ("album_" + java.lang.Integer.toHexString(album.lowercase().hashCode()))
                            if (existingAlbum == null) {
                                database.insert(
                                    AlbumEntity(
                                        id = finalAlbumId,
                                        title = album.trim(),
                                        songCount = 1,
                                        duration = actualDurationSec,
                                        thumbnailUrl = "",
                                    )
                                )
                            }
                            database.insert(
                                SongAlbumMap(
                                    songId = finalSongId,
                                    albumId = finalAlbumId,
                                    index = 0,
                                )
                            )
                        }
                    }
                }

                // Record listening history event
                database.insert(
                    Event(
                        songId = finalSongId,
                        timestamp = LocalDateTime.now(),
                        playTime = actualPlayTimeMs,
                    )
                )
                database.incrementTotalPlayTime(finalSongId, actualPlayTimeMs)
                try {
                    database.incrementPlayCount(finalSongId)
                } catch (e: Exception) {
                    Timber.w(e, "incrementPlayCount failed for $finalSongId")
                }
            }

            // Flush offline scrobbles to Last.fm
            flushPendingScrobbles()

            // Scrobble to Last.fm if session key is active
            val session = lastFmSessionPreferences.currentSession
            if (session.hasSessionKey) {
                val remoteRes = signedCall(
                    method = "track.scrobble",
                    extra = buildMap {
                        put("artist", safeArtist)
                        put("track", safeTrack)
                        put("timestamp", timestampSec.toString())
                        if (!album.isNullOrBlank()) put("album", album)
                    }
                )
                if (remoteRes is Result.Failed && remoteRes.retryable) {
                    offlineQueue.add(PendingScrobble(safeArtist, safeTrack, album, timestampSec))
                    while (offlineQueue.size > MAX_OFFLINE_QUEUE) offlineQueue.poll()
                }
            }

            _scrobbleEvents.tryEmit(Unit)
            Result.Success
        }.getOrElse { error ->
            Timber.e(error, "Failed to scrobble track $track")
            Result.Failed(error.message ?: "Failed to save scrobble")
        }
    }

    /** Mirrors player heart/favorite to the authenticated Last.fm library */
    suspend fun setTrackLoved(artist: String, track: String, loved: Boolean): Result {
        val session = lastFmSessionPreferences.currentSession
        if (!session.hasSessionKey) return Result.NoSessionKey
        return signedCall(
            method = if (loved) "track.love" else "track.unlove",
            extra = mapOf(
                "artist" to cleanArtist(artist),
                "track" to track.trim(),
            ),
        )
    }

    suspend fun love(artist: String, track: String): Result = setTrackLoved(artist, track, true)
    suspend fun unlove(artist: String, track: String): Result = setTrackLoved(artist, track, false)

    private suspend fun flushPendingScrobbles() {
        if (offlineQueue.isEmpty()) return
        if (rateGuard.cooldownRemainingMs > 0L) return

        while (offlineQueue.isNotEmpty()) {
            val pending = offlineQueue.peek() ?: break
            val res = signedCall(
                method = "track.scrobble",
                extra = buildMap {
                    put("artist", pending.artist)
                    put("track", pending.track)
                    put("timestamp", pending.timestampSec.toString())
                    if (!pending.album.isNullOrBlank()) put("album", pending.album)
                },
            )
            if (res is Result.Success) {
                offlineQueue.poll()
                _scrobbleEvents.tryEmit(Unit)
            } else {
                break
            }
        }
    }

    private suspend fun signedCall(method: String, extra: Map<String, String>): Result = callMutex.withLock {
        val session = lastFmSessionPreferences.session.first()
        if (session.apiKey.isBlank() || session.apiSecret.isBlank()) {
            return Result.Failed("Last.fm credentials missing")
        }
        if (session.sessionKey.isBlank()) {
            return Result.NoSessionKey
        }

        if (!rateGuard.suspendAwaitClearance(WRITE_COOLDOWN_WAIT_MS)) {
            return Result.Failed("Rate limit cooldown active", retryable = true)
        }

        val sinceLastWrite = SystemClock.elapsedRealtime() - lastWriteAtElapsed
        if (sinceLastWrite in 1 until WRITE_SPACING_MS) {
            delay(WRITE_SPACING_MS - sinceLastWrite)
        }

        return try {
            val signParams = extra + mapOf(
                "method" to method,
                "sk" to session.sessionKey,
                "api_key" to session.apiKey,
            )
            val sig = LastFmSigner.sign(signParams, session.apiSecret)
            val bodyParams = signParams + mapOf("api_sig" to sig, "format" to "json")
            val (code, responseText) = lastFmApi.post(bodyParams)
            lastWriteAtElapsed = SystemClock.elapsedRealtime()

            if (code == 429) {
                rateGuard.onRequestLimited()
                return Result.Failed("Last.fm rate limited (429)", retryable = true)
            }

            if (responseText.isBlank()) return Result.Failed("Empty response from Last.fm")

            val parsed = json.parseToJsonElement(responseText).jsonObject
            val errorCode = (parsed["error"] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
            if (errorCode != null) {
                if (errorCode == 29) {
                    rateGuard.onRequestLimited()
                    return Result.Failed("Last.fm rate limited (error 29)", retryable = true)
                }
                Result.Failed("Last.fm error $errorCode")
            } else {
                rateGuard.onRequestSucceeded()
                Result.Success
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: IOException) {
            Result.Failed(e.message ?: "Could not reach Last.fm", retryable = true)
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Could not reach Last.fm")
        }
    }

    private companion object {
        const val WRITE_COOLDOWN_WAIT_MS = 15_000L
        const val WRITE_SPACING_MS = 600L
        const val MAX_OFFLINE_QUEUE = 150
    }
}
