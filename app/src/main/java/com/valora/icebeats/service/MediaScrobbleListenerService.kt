package com.valora.icebeats.service

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.valora.icebeats.data.local.ScrobblerPreferences
import com.valora.icebeats.data.repository.ScrobbleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val TAG = "MediaScrobbleListener"
private const val NOW_PLAYING_RETRY_DELAY_MS = 12_000L

/**
 * System-wide media listener that detects tracks playing across any installed music app
 * (e.g. Spotify, YouTube Music, Apple Music, etc.) and scrobbles them into IceBeats's
 * history and stats.
 */
@AndroidEntryPoint
class MediaScrobbleListenerService : NotificationListenerService() {

    @Inject lateinit var scrobblerPreferences: ScrobblerPreferences
    @Inject lateinit var scrobbleRepository: ScrobbleRepository

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var pollJob: Job? = null

    private val watched = ConcurrentHashMap<MediaSession.Token, WatchedSession>()

    @Volatile private var lastAnnouncedKey: String = ""
    @Volatile private var enabled: Boolean = false
    @Volatile private var submitNowPlaying: Boolean = true
    @Volatile private var scrobblePercent: Int = 50
    @Volatile private var selectedPackages: Set<String> = emptySet()

    private inner class WatchedSession(val controller: MediaController) {
        var callback: MediaController.Callback? = null
        var trackKey: String = ""
        var accumulatedMs: Long = 0L
        var playingSinceElapsed: Long? = null
        var scrobbledForKey: String = ""
        var startedAtEpochSec: Long = 0L
        var scrobbleJob: Job? = null
        var durationKnown: Boolean = false
        var lastPositionMs: Long = 0L
        var lastActiveElapsed: Long = SystemClock.elapsedRealtime()
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            serviceScope.launch {
                scrobblerPreferences.settings.collect { s ->
                    val wasEnabled = enabled
                    val newlySelected = s.selectedPackages - selectedPackages
                    enabled = s.enabled
                    submitNowPlaying = s.submitNowPlaying
                    scrobblePercent = s.scrobblePercent
                    val changedPackages = selectedPackages != s.selectedPackages
                    selectedPackages = s.selectedPackages
                    if (changedPackages) refreshActiveSessions()
                    if (enabled && (!wasEnabled || newlySelected.isNotEmpty())) {
                        val packages = if (!wasEnabled) selectedPackages else newlySelected
                        watched.values.toList()
                            .filter { it.controller.packageName in packages }
                            .forEach(::rearmScrobbling)
                    }
                }
            }
        }.onFailure { Log.w(TAG, "onCreate settings collector failed to start", it) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching {
            val manager = getSystemService(MediaSessionManager::class.java) ?: return
            val component = ComponentName(this, MediaScrobbleListenerService::class.java)
            val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                runCatching { bindControllers(controllers.orEmpty()) }
                    .onFailure { Log.w(TAG, "bindControllers (session change) failed", it) }
            }
            sessionsListener = listener
            manager.addOnActiveSessionsChangedListener(listener, component, mainHandler)
            bindControllers(manager.getActiveSessions(component))
        }.onFailure {
            Log.w(TAG, "onListenerConnected failed — scrobbling unavailable this session", it)
        }

        pollJob?.cancel()
        pollJob = serviceScope.launch {
            while (true) {
                delay(4_000)
                refreshActiveSessions()
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        runCatching {
            val manager = getSystemService(MediaSessionManager::class.java)
            sessionsListener?.let { manager?.removeOnActiveSessionsChangedListener(it) }
        }.onFailure { Log.w(TAG, "onListenerDisconnected cleanup failed", it) }
        pollJob?.cancel()
        runCatching { unbindAll() }.onFailure { Log.w(TAG, "unbindAll on disconnect failed", it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollJob?.cancel()
        runCatching { unbindAll() }.onFailure { Log.w(TAG, "unbindAll on destroy failed", it) }
        serviceScope.cancel()
    }

    private fun refreshActiveSessions() {
        runCatching {
            val manager = getSystemService(MediaSessionManager::class.java) ?: return
            val component = ComponentName(this, MediaScrobbleListenerService::class.java)
            bindControllers(manager.getActiveSessions(component))
        }.onFailure { Log.w(TAG, "refreshActiveSessions failed", it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val isMediaNotification = sbn.notification?.extras?.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) == true
        if (pkg in selectedPackages || isMediaNotification) {
            runCatching { refreshActiveSessions() }.onFailure { Log.w(TAG, "onNotificationPosted refresh failed", it) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun bindControllers(controllers: List<MediaController>) {
        val liveTokens = controllers.mapNotNull { c -> runCatching { c.sessionToken }.getOrNull() }.toSet()
        val stale = watched.keys - liveTokens
        stale.forEach { unbindToken(it) }

        controllers.forEach { controller ->
            runCatching {
                if (controller.packageName == packageName) return@forEach
                val token = controller.sessionToken
                val existing = watched[token]
                if (existing != null) {
                    onTrackChanged(existing, controller.metadata)
                    onStateChanged(existing, controller.playbackState)
                    return@forEach
                }
                val session = WatchedSession(controller)
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        runCatching { onTrackChanged(session, metadata) }.onFailure { Log.w(TAG, "onMetadataChanged failed", it) }
                    }
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        runCatching { onStateChanged(session, state) }.onFailure { Log.w(TAG, "onPlaybackStateChanged failed", it) }
                    }
                    override fun onSessionDestroyed() {
                        runCatching { unbindToken(token) }.onFailure { Log.w(TAG, "unbind on session destroyed failed", it) }
                    }
                }
                session.callback = callback
                controller.registerCallback(callback, mainHandler)
                watched[token] = session
                onTrackChanged(session, controller.metadata)
                onStateChanged(session, controller.playbackState)
            }.onFailure { Log.w(TAG, "Failed to bind controller for ${runCatching { controller.packageName }.getOrDefault("?")}", it) }
        }
    }

    private fun unbindToken(token: MediaSession.Token) {
        val session = watched.remove(token) ?: return
        session.callback?.let { runCatching { session.controller.unregisterCallback(it) } }
        session.scrobbleJob?.cancel()
        if (watched.values.none { it.playingSinceElapsed != null }) {
            scrobbleRepository.clearNowPlaying()
        }
    }

    private fun unbindAll() {
        watched.keys.toList().forEach { unbindToken(it) }
        scrobbleRepository.clearNowPlaying()
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

    private fun onTrackChanged(session: WatchedSession, metadata: MediaMetadata?) {
        if (metadata == null) return
        val rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank()) return
        session.lastActiveElapsed = SystemClock.elapsedRealtime()
        if (rawArtist.isNullOrBlank()) return

        val artist = cleanArtist(rawArtist)
        val key = "$artist|$title"
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        if (key == session.trackKey) {
            if (!session.durationKnown && durationMs > 0L) {
                session.durationKnown = true
                session.scrobbleJob?.cancel()
                scheduleScrobbleCheck(session, key, artist, title, album, durationMs)
            }
            return
        }

        session.trackKey = key
        session.accumulatedMs = 0L
        session.durationKnown = durationMs > 0L
        session.scrobbledForKey = ""
        session.lastPositionMs = session.controller.playbackState?.position?.coerceAtLeast(0L) ?: 0L
        session.playingSinceElapsed = if (session.controller.playbackState?.state == PlaybackState.STATE_PLAYING) SystemClock.elapsedRealtime() else null
        session.startedAtEpochSec = System.currentTimeMillis() / 1000
        session.scrobbleJob?.cancel()

        if (session.playingSinceElapsed != null && isSelectedForScrobbling(session)) {
            announceNowPlaying(key, artist, title, album, session.controller.packageName)
        }
        scheduleScrobbleCheck(session, key, artist, title, album, durationMs)
    }

    private fun onStateChanged(session: WatchedSession, state: PlaybackState?) {
        val playing = state?.state == PlaybackState.STATE_PLAYING
        val position = state?.position ?: -1L
        session.lastActiveElapsed = SystemClock.elapsedRealtime()

        if (position >= 0L && session.trackKey.isNotBlank()) {
            val jumpedBack = session.lastPositionMs - position > 8_000L
            val nearStart = position < 5_000L
            val wasWellIntoIt = session.lastPositionMs > 20_000L
            if (jumpedBack && nearStart && wasWellIntoIt) {
                session.scrobbleJob?.cancel()
                session.accumulatedMs = 0L
                session.scrobbledForKey = ""
                session.startedAtEpochSec = System.currentTimeMillis() / 1000
                session.playingSinceElapsed = if (playing) SystemClock.elapsedRealtime() else null
                val metadata = session.controller.metadata
                val rawArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                if (!rawArtist.isNullOrBlank() && !title.isNullOrBlank()) {
                    val artist = cleanArtist(rawArtist)
                    val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                    val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                    if (playing && isSelectedForScrobbling(session)) {
                        announceNowPlaying(session.trackKey, artist, title, album, session.controller.packageName, forceReannounce = true)
                    }
                    scheduleScrobbleCheck(session, session.trackKey, artist, title, album, durationMs)
                }
            }
            session.lastPositionMs = position
        }

        if (playing) {
            if (session.playingSinceElapsed == null) {
                session.playingSinceElapsed = SystemClock.elapsedRealtime()
                if (session.trackKey.isNotBlank()) {
                    val metadata = session.controller.metadata
                    val rawArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    if (!rawArtist.isNullOrBlank() && !title.isNullOrBlank() && isSelectedForScrobbling(session)) {
                        announceNowPlaying(session.trackKey, cleanArtist(rawArtist), title, metadata.getString(MediaMetadata.METADATA_KEY_ALBUM), session.controller.packageName)
                    }
                }
            }
        } else {
            session.playingSinceElapsed?.let { since ->
                session.accumulatedMs += SystemClock.elapsedRealtime() - since
            }
            session.playingSinceElapsed = null
            if (watched.values.none { it.playingSinceElapsed != null }) {
                scrobbleRepository.clearNowPlaying()
            }
        }
    }

    private fun isSelectedForScrobbling(session: WatchedSession): Boolean =
        session.controller.packageName in selectedPackages

    private fun rearmScrobbling(session: WatchedSession) {
        val metadata = session.controller.metadata ?: return
        val rawArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (rawArtist.isNullOrBlank() || title.isNullOrBlank()) return
        val artist = cleanArtist(rawArtist)
        val key = "$artist|$title"
        session.trackKey = key
        session.accumulatedMs = 0L
        session.scrobbledForKey = ""
        session.startedAtEpochSec = System.currentTimeMillis() / 1000
        val playing = session.controller.playbackState?.state == PlaybackState.STATE_PLAYING
        session.playingSinceElapsed = if (playing) SystemClock.elapsedRealtime() else null
        session.scrobbleJob?.cancel()
        if (playing) announceNowPlaying(key, artist, title, metadata.getString(MediaMetadata.METADATA_KEY_ALBUM), session.controller.packageName)
        scheduleScrobbleCheck(
            session,
            key,
            artist,
            title,
            metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
        )
    }

    private fun announceNowPlaying(
        key: String,
        artist: String,
        title: String,
        album: String?,
        packageName: String? = null,
        forceReannounce: Boolean = false,
    ) {
        if (!enabled || !submitNowPlaying) return
        if (key == lastAnnouncedKey && !forceReannounce) return
        lastAnnouncedKey = key
        serviceScope.launch {
            val result = runCatching { scrobbleRepository.updateNowPlaying(artist, title, album, packageName) }
                .onFailure { Log.w(TAG, "updateNowPlaying failed", it) }
                .getOrNull()
            if (result is ScrobbleRepository.Result.Failed && result.retryable && lastAnnouncedKey == key) {
                delay(NOW_PLAYING_RETRY_DELAY_MS)
                if (enabled && submitNowPlaying && lastAnnouncedKey == key &&
                    sessionStillPlayingTrack(key)
                ) {
                    runCatching { scrobbleRepository.updateNowPlaying(artist, title, album, packageName) }
                        .onFailure { Log.w(TAG, "updateNowPlaying retry failed", it) }
                }
            }
        }
    }

    private fun sessionStillPlayingTrack(key: String): Boolean =
        watched.values.any { it.trackKey == key && it.playingSinceElapsed != null }

    private fun scheduleScrobbleCheck(session: WatchedSession, key: String, artist: String, title: String, album: String?, durationMs: Long) {
        if (!enabled || !isSelectedForScrobbling(session)) return

        val hasKnownDuration = durationMs > 0L
        if (hasKnownDuration && durationMs <= 30_000L) return
        val thresholdMs = if (hasKnownDuration) {
            minOf((durationMs * scrobblePercent) / 100, 4 * 60_000L)
        } else {
            4 * 60_000L
        }
        session.scrobbleJob = serviceScope.launch {
            while (true) {
                delay(3_000)
                if (!enabled || !isSelectedForScrobbling(session)) return@launch
                if (session.trackKey != key) return@launch
                val playedMs = session.accumulatedMs + (session.playingSinceElapsed?.let { SystemClock.elapsedRealtime() - it } ?: 0L)
                if (playedMs >= thresholdMs) {
                    if (session.scrobbledForKey != key) {
                        session.scrobbledForKey = key
                        runCatching {
                            scrobbleRepository.scrobble(
                                artist = artist,
                                track = title,
                                album = album,
                                timestampSec = session.startedAtEpochSec,
                                durationMs = durationMs,
                                playTimeMs = playedMs,
                            )
                        }
                            .onSuccess { result ->
                                if (result == ScrobbleRepository.Result.Success && session.trackKey == key &&
                                    session.playingSinceElapsed != null && isSelectedForScrobbling(session)
                                ) {
                                    announceNowPlaying(key, artist, title, album, session.controller.packageName, forceReannounce = true)
                                }
                            }
                            .onFailure {
                                Log.w(TAG, "scrobble failed", it)
                            }
                    }
                    return@launch
                }
            }
        }
    }
}
