package com.valora.icebeats.playback.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.valora.icebeats.models.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

interface CastPlaybackListener {
    suspend fun resolveCastStream(mediaId: String): ResolvedCastStream?
    fun onCastSessionStarted(deviceName: String)
    fun onCastSessionEnded()
    fun onCastTrackEnded()
    fun onCastStateUpdated(isPlaying: Boolean, isBuffering: Boolean, positionMs: Long, durationMs: Long)
    fun onCastError(message: String)
}

internal class CastPlayback(
    private val context: Context,
    private val listener: CastPlaybackListener,
    private val scope: CoroutineScope,
    private val castContext: CastContext,
) {
    private var session: CastSession? = null
    private var client: RemoteMediaClient? = null
    private var server: CastStreamServer? = null
    private var loadJob: Job? = null
    private var contentId: String? = null
    private var requestedPlaying = true
    private var loading = false

    val active: Boolean get() = session != null
    val deviceName: String? get() = session?.castDevice?.friendlyName

    private val callback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = updateState()
        override fun onMetadataUpdated() = updateState()
    }

    private val progressListener = RemoteMediaClient.ProgressListener { _, _ -> updateState() }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, id: String) = attach(session)
        override fun onSessionResumed(session: CastSession, suspended: Boolean) = attach(session)
        override fun onSessionEnded(session: CastSession, error: Int) = detach()
        override fun onSessionResumeFailed(session: CastSession, error: Int) = detach()
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Timber.w("Cast session start failed: $error")
            listener.onCastError("Could not connect to Cast device ($error)")
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            loadJob?.cancel()
            listener.onCastError("Cast connection interrupted")
        }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, id: String) = Unit
    }

    fun initialize() {
        castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        castContext.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let(::attach)
    }

    fun release() {
        castContext.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
        detach()
    }

    private fun attach(newSession: CastSession) {
        client?.unregisterCallback(callback)
        client?.removeProgressListener(progressListener)
        session = newSession
        client = newSession.remoteMediaClient
        client?.registerCallback(callback)
        client?.addProgressListener(progressListener, 500)
        listener.onCastSessionStarted(newSession.castDevice?.friendlyName ?: "Chromecast")
    }

    fun load(track: MediaMetadata, positionMs: Long = 0L, autoplay: Boolean = true) {
        loadJob?.cancel()
        contentId = null
        loading = true
        requestedPlaying = autoplay
        client?.stop()

        loadJob = scope.launch(Dispatchers.Main.immediate) {
            try {
                val resolved = listener.resolveCastStream(track.id)
                    ?: error("Unable to resolve audio stream for casting")

                val streamServer = server ?: createServer()
                val url = streamServer.publish(resolved)
                contentId = url

                val metadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                    putString(CastMediaMetadata.KEY_TITLE, track.title)
                    putString(CastMediaMetadata.KEY_ARTIST, track.artists.joinToString { it.name })
                    track.album?.let { putString(CastMediaMetadata.KEY_ALBUM_TITLE, it.title) }
                    track.thumbnailUrl?.takeIf { it.startsWith("http") }?.let {
                        addImage(WebImage(Uri.parse(it)))
                    }
                }

                val media = MediaInfo.Builder(url)
                    .setContentType(resolved.mimeType.substringBefore(';'))
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setMetadata(metadata)
                    .apply {
                        if (track.duration > 0) {
                            setStreamDuration(track.duration * 1000L)
                        }
                    }
                    .build()

                val remote = client ?: return@launch
                val requestData = MediaLoadRequestData.Builder()
                    .setMediaInfo(media)
                    .setAutoplay(requestedPlaying)
                    .setCurrentTime(positionMs)
                    .build()

                remote.load(requestData).setResultCallback { result ->
                    if (contentId == url) {
                        loading = false
                        if (!result.status.isSuccess) {
                            listener.onCastError("Chromecast could not play audio (${result.status.statusCode})")
                        } else {
                            if (!requestedPlaying) remote.pause()
                            updateState()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                loading = false
                Timber.e(error, "Error loading cast media")
                listener.onCastError(error.message ?: "Could not cast this track")
            }
        }
    }

    fun play() {
        requestedPlaying = true
        if (!loading) {
            if (contentId != null && client?.hasMediaSession() == true) {
                client?.play()
            }
        }
    }

    fun pause() {
        requestedPlaying = false
        client?.pause()
    }

    fun seek(positionMs: Long) {
        if (!loading) {
            client?.seek(MediaSeekOptions.Builder().setPosition(positionMs).build())
        }
    }

    fun disconnect() {
        castContext.sessionManager.endCurrentSession(true)
    }

    private fun updateState() {
        val remote = client ?: return
        val expected = contentId ?: return
        if (loading || remote.mediaInfo?.contentId != expected) return

        val isPlaying = remote.isPlaying
        val isBuffering = remote.isBuffering
        val position = remote.approximateStreamPosition
        val duration = remote.streamDuration

        listener.onCastStateUpdated(isPlaying, isBuffering, position, duration)

        if (remote.playerState == MediaStatus.PLAYER_STATE_IDLE) {
            when (remote.idleReason) {
                MediaStatus.IDLE_REASON_FINISHED -> {
                    contentId = null
                    listener.onCastTrackEnded()
                }
                MediaStatus.IDLE_REASON_ERROR -> {
                    contentId = null
                    listener.onCastError("Playback error on Chromecast")
                }
            }
        }
    }

    private fun detach() {
        loadJob?.cancel()
        client?.unregisterCallback(callback)
        client?.removeProgressListener(progressListener)
        client = null
        session = null
        contentId = null
        loading = false
        val previousServer = server
        server = null
        scope.launch(Dispatchers.IO) { previousServer?.stop() }
        listener.onCastSessionEnded()
    }

    private suspend fun createServer(): CastStreamServer {
        var created: CastStreamServer? = null
        try {
            return withContext(Dispatchers.IO) {
                CastStreamServer.create(context).also { created = it }
            }.also { server = it }
        } catch (error: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { created?.stop() }
            throw error
        }
    }
}
