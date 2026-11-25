package com.valora.icebeats.utils

import android.content.Context
import android.util.Log
import com.valora.icebeats.extensions.toMediaItem
import com.valora.icebeats.innertube.models.WatchEndpoint
import com.valora.icebeats.playback.PlayerConnection
import com.valora.icebeats.playback.queues.YouTubeQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

enum class ListenTogetherConnectionMode {
    ONLINE,
    LAN
}

object ListenTogetherSync {
    private const val TAG = "ListenTogetherSync"
    private const val SYNC_MS = 280L
    private const val CONTROLLER_PUBLISH_MS = 4000L
    private const val SEEK_TOLERANCE_MS = 1000L
    private const val ONLINE_SEEK_TOLERANCE_MS = 1800L
    private const val APPLY_GRACE_MS = 2500L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null
    private var appContext: Context? = null
    private var playerConnection: PlayerConnection? = null
    private var lastAppliedVersion = 0L
    private var lastControllerPublishAt = 0L
    private var suppressLocalSyncUntil = 0L
    private var pendingGuestSongId: String? = null
    private var lastHandledActionId: String? = null
    private var loadingSongId: String? = null
    private var lastSongLoadAttempt = 0L

    private var originalGuestParticipantId: String? = null
    private var sessionHostId: String? = null

    private val _sharedQueue = MutableStateFlow<List<ListenTogetherQueueItem>>(emptyList())
    val sharedQueue = _sharedQueue.asStateFlow()

    private val _session = MutableStateFlow<ListenTogetherSession?>(null)
    val session = _session.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost = _isHost.asStateFlow()

    private val _connectionMode = MutableStateFlow(ListenTogetherConnectionMode.ONLINE)
    val connectionMode = _connectionMode.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setDisplayMessage(msg: String?) {
        if (msg.isNullOrBlank()) {
            _message.value = null
            return
        }
        val lower = msg.lowercase()
        val isTechnicalNoise = lower.contains("cancelled") ||
                lower.contains("timeout") ||
                lower.contains("timed out") ||
                lower.contains("left session") ||
                lower.contains("socket") ||
                lower.contains("broken pipe") ||
                lower.contains("reset by peer") ||
                lower.contains("eofexception") ||
                lower.contains("standalonecoroutine") ||
                lower.contains("jobcancellationexception") ||
                lower.contains("b2 was") ||
                lower.contains("failed to connect") ||
                lower.contains("connection refused") ||
                lower.contains("unable to resolve host") ||
                lower.contains("end of input")
        if (!isTechnicalNoise) {
            _message.value = msg
        }
    }

    private val _displayName = MutableStateFlow(ListenTogetherStore.defaultName())
    val displayName = _displayName.asStateFlow()

    private var lanServer: LanTogetherServer? = null
    var lanHostAddress: String? = null
        private set
    val lanHost: String? get() = lanHostAddress

    fun start(
        context: Context,
        connection: PlayerConnection,
    ) {
        appContext = context.applicationContext
        playerConnection = connection
        val defName = ListenTogetherStore.defaultName(context.applicationContext)
        _displayName.value = defName
        val saved = ListenTogetherStore.load(context.applicationContext)
        if (saved != null && _session.value == null) {
            _displayName.value = saved.displayName.ifBlank { defName }
            _isHost.value = saved.isHost
            _connectionMode.value = if (saved.isLan) ListenTogetherConnectionMode.LAN else ListenTogetherConnectionMode.ONLINE
            lanHostAddress = saved.hostAddress
            scope.launch {
                runCatching {
                    if (saved.isLan) {
                        if (saved.isHost) {
                            val localIp = LanTogetherServer.getLocalIpAddress(context) ?: "127.0.0.1"
                            val port = saved.code.substringAfterLast(":").toIntOrNull() ?: 8765
                            startLanHost(port)
                            return@launch
                        } else {
                            val host = saved.hostAddress ?: saved.code
                            LanTogetherClient.getSession(host, saved.participantId)
                        }
                    } else {
                        ListenTogetherClient.getSession(saved.code).copy(participantId = saved.participantId)
                    }
                }.onSuccess {
                    if (saved.isHost) {
                        originalGuestParticipantId = null
                        sessionHostId = it.participantId
                        _session.value = it
                    } else {
                        originalGuestParticipantId = saved.participantId
                        sessionHostId = it.controllerId.ifBlank { if (saved.isLan) "host" else null }
                        val hasPermission = it.settings.allowGuestsToControlPlayback
                        val effectiveId = if (hasPermission && !sessionHostId.isNullOrBlank()) sessionHostId!! else saved.participantId
                        _session.value = it.copy(participantId = effectiveId)
                    }
                    lastAppliedVersion = it.stateVersion
                    restartSync()
                }.onFailure {
                    ListenTogetherStore.clear(context.applicationContext)
                    setDisplayMessage(it.message)
                }
            }
        } else if (_session.value != null) {
            restartSync()
        }
    }

    fun setDisplayName(name: String) {
        _displayName.value = name.ifBlank { ListenTogetherStore.defaultName(appContext) }
    }

    fun setConnectionMode(mode: ListenTogetherConnectionMode) {
        _connectionMode.value = mode
    }

    fun adoptSession(
        context: Context,
        session: ListenTogetherSession,
        displayName: String,
        isHost: Boolean,
        isLan: Boolean = false,
        hostAddress: String? = null,
    ) {
        _displayName.value = displayName.ifBlank { ListenTogetherStore.defaultName(context) }
        _isHost.value = isHost
        _connectionMode.value = if (isLan) ListenTogetherConnectionMode.LAN else ListenTogetherConnectionMode.ONLINE
        lanHostAddress = hostAddress
        appContext = context.applicationContext
        lastAppliedVersion = session.stateVersion
        loadingSongId = null
        lastSongLoadAttempt = 0L

        if (isHost) {
            originalGuestParticipantId = null
            sessionHostId = session.participantId
            _session.value = session
            ListenTogetherStore.save(
                context = context.applicationContext,
                session = session,
                displayName = _displayName.value,
                isHost = true,
                isLan = isLan,
                hostAddress = hostAddress
            )
        } else {
            originalGuestParticipantId = session.participantId
            sessionHostId = session.controllerId.ifBlank { if (isLan) "host" else null }
            val hasPermission = session.settings.allowGuestsToControlPlayback
            val effectiveId = if (hasPermission && !sessionHostId.isNullOrBlank()) sessionHostId!! else session.participantId
            val effectiveSession = session.copy(participantId = effectiveId)
            _session.value = effectiveSession
            ListenTogetherStore.save(
                context = context.applicationContext,
                session = session.copy(participantId = originalGuestParticipantId ?: session.participantId),
                displayName = _displayName.value,
                isHost = false,
                isLan = isLan,
                hostAddress = hostAddress
            )
        }
        restartSync()
    }

    fun createSession() {
        val context = appContext ?: return
        val connection = playerConnection ?: return
        val metadata = connection.mediaMetadata.value ?: run {
            _message.value = "Play a song first"
            return
        }

        scope.launch {
            runCatching {
                ListenTogetherClient.createSession(
                    displayName = _displayName.value,
                    state =
                        ListenTogetherPlaybackState(
                            songId = metadata.id,
                            title = metadata.title,
                            artists = metadata.artists.map { it.name },
                            thumbnailUrl = metadata.thumbnailUrl,
                            positionMs = connection.player.currentPosition,
                            isPlaying = connection.player.playWhenReady,
                            durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                        )
                )
            }.onSuccess {
                originalGuestParticipantId = null
                sessionHostId = it.participantId
                _session.value = it
                _isHost.value = true
                _connectionMode.value = ListenTogetherConnectionMode.ONLINE
                lastAppliedVersion = it.stateVersion
                ListenTogetherStore.save(context, it, _displayName.value, true, isLan = false)
                setDisplayMessage("Session created")
                restartSync()
            }.onFailure {
                setDisplayMessage(it.message)
            }
        }
    }

    fun joinSession(code: String) {
        val context = appContext ?: return
        scope.launch {
            runCatching {
                ListenTogetherClient.joinSession(code, _displayName.value)
            }.onSuccess {
                originalGuestParticipantId = it.participantId
                sessionHostId = it.controllerId.ifBlank { null }
                _isHost.value = false
                _connectionMode.value = ListenTogetherConnectionMode.ONLINE
                lastAppliedVersion = 0L
                loadingSongId = null
                lastSongLoadAttempt = 0L
                val hasPermission = it.settings.allowGuestsToControlPlayback
                val effectiveId = if (hasPermission && !sessionHostId.isNullOrBlank()) sessionHostId!! else it.participantId
                val effectiveSession = it.copy(participantId = effectiveId)
                _session.value = effectiveSession
                ListenTogetherStore.save(context, it.copy(participantId = it.participantId), _displayName.value, false, isLan = false)
                setDisplayMessage("Joined session")
                restartSync()
            }.onFailure {
                setDisplayMessage(it.message)
            }
        }
    }

    fun startLanHost(port: Int = 8765) {
        val context = appContext ?: return
        val connection = playerConnection ?: return
        val metadata = connection.mediaMetadata.value ?: run {
            _message.value = "Play a song first"
            return
        }

        val localIp = LanTogetherServer.getLocalIpAddress(context)
        if (localIp.isNullOrBlank()) {
            _message.value = "Connect to Wi-Fi or enable Hotspot first"
            return
        }

        scope.launch {
            try {
                lanServer?.stop()
                val server = LanTogetherServer(
                    context = context,
                    hostIp = localIp,
                    port = port,
                    hostDisplayName = _displayName.value,
                    audioStreamResolver = { songId ->
                        playerConnection?.service?.resolveAudioStreamForCast(songId)
                    }
                )
                server.playbackState = ListenTogetherPlaybackState(
                    songId = metadata.id,
                    title = metadata.title,
                    artists = metadata.artists.map { it.name },
                    thumbnailUrl = metadata.thumbnailUrl,
                    positionMs = connection.player.currentPosition,
                    isPlaying = connection.player.playWhenReady,
                    durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                )
                server.queueProvider = {
                    runCatching {
                        runBlocking(Dispatchers.Main) {
                            val player = connection.player
                            val currentQueue = mutableListOf<ListenTogetherQueueItem>()
                            val list = mutableListOf<JSONObject>()
                            if (!player.currentTimeline.isEmpty) {
                                val count = player.mediaItemCount
                                val currentIndex = player.currentMediaItemIndex
                                val start = (currentIndex + 1).coerceAtLeast(0)
                                for (i in start until count) {
                                    val item = player.getMediaItemAt(i)
                                    val meta = item.mediaMetadata
                                    val t = meta.title?.toString() ?: "Unknown Song"
                                    val a = meta.artist?.toString() ?: ""
                                    val thumb = meta.artworkUri?.toString()
                                    currentQueue.add(ListenTogetherQueueItem(songId = item.mediaId, title = t, artist = a, thumbnailUrl = thumb))
                                    list.add(JSONObject().apply {
                                        put("index", i - start)
                                        put("id", item.mediaId)
                                        put("title", t)
                                        put("artist", a)
                                        put("thumbnailUrl", thumb ?: "")
                                        put("isCurrent", i == currentIndex)
                                    })
                                }
                            }
                            _sharedQueue.value = currentQueue
                            list
                        }
                    }.getOrElse { emptyList() }
                }
                server.onPlaybackCommand = { action, positionMs, extraData ->
                    scope.launch(Dispatchers.Main) {
                        val player = connection.player
                        when (action) {
                            "play", "resume" -> {
                                if (!player.playWhenReady) {
                                    player.play()
                                }
                            }
                            "pause" -> {
                                if (player.playWhenReady) {
                                    player.pause()
                                }
                            }
                            "toggle_play_pause" -> {
                                connection.togglePlayPause()
                            }
                            "seek" -> {
                                if (positionMs != null && positionMs >= 0) {
                                    player.seekTo(positionMs)
                                }
                            }
                            "next", "skip_next" -> {
                                connection.seekToNext()
                            }
                            "previous", "skip_previous" -> {
                                connection.seekToPrevious()
                            }
                            "play_index", "jump_to" -> {
                                val targetIndex = extraData?.optInt("index", -1) ?: -1
                                val targetSongId = extraData?.optString("songId")
                                var match = -1
                                val cur = player.currentMediaItemIndex
                                if (!targetSongId.isNullOrBlank()) {
                                    for (i in (cur + 1).coerceAtLeast(0) until player.mediaItemCount) {
                                        if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                            match = i
                                            break
                                        }
                                    }
                                    if (match < 0) {
                                        for (i in 0 until player.mediaItemCount) {
                                            if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                                match = i
                                                break
                                            }
                                        }
                                    }
                                } else if (targetIndex >= 0) {
                                    val upcomingIdx = cur + 1 + targetIndex
                                    match = if (upcomingIdx in 0 until player.mediaItemCount) upcomingIdx else targetIndex
                                }
                                if (match in 0 until player.mediaItemCount) {
                                    player.seekToDefaultPosition(match)
                                    player.play()
                                }
                            }
                            "play_song" -> {
                                val targetSongId = extraData?.optString("songId")
                                var found = false
                                if (!targetSongId.isNullOrBlank()) {
                                    for (i in 0 until player.mediaItemCount) {
                                        if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                            player.seekToDefaultPosition(i)
                                            player.play()
                                            found = true
                                            break
                                        }
                                    }
                                }
                                if (!found && extraData != null) {
                                    runCatching {
                                        val track = ListenTogetherPlaybackState.fromJson(extraData)
                                        val meta = track.toMediaMetadata()
                                        connection.playQueue(com.valora.icebeats.playback.queues.ListQueue(items = listOf(meta.toMediaItem()), startIndex = 0))
                                    }
                                }
                            }
                            "add_track" -> {
                                extraData?.let { json ->
                                    val track = ListenTogetherPlaybackState.fromJson(json)
                                    connection.addToQueue(track.toMediaMetadata().toMediaItem())
                                }
                            }
                            "play_next" -> {
                                extraData?.let { json ->
                                    val track = ListenTogetherPlaybackState.fromJson(json)
                                    connection.playNext(track.toMediaMetadata().toMediaItem())
                                }
                            }
                            "remove_track", "remove_song" -> {
                                val targetIndex = extraData?.optInt("index", -1) ?: -1
                                val targetSongId = extraData?.optString("songId")
                                var match = -1
                                val cur = player.currentMediaItemIndex
                                if (!targetSongId.isNullOrBlank()) {
                                    for (i in (cur + 1).coerceAtLeast(0) until player.mediaItemCount) {
                                        if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                            match = i
                                            break
                                        }
                                    }
                                    if (match < 0) {
                                        for (i in 0 until player.mediaItemCount) {
                                            if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                                match = i
                                                break
                                            }
                                        }
                                    }
                                } else if (targetIndex >= 0) {
                                    val upcomingIdx = cur + 1 + targetIndex
                                    match = if (upcomingIdx in 0 until player.mediaItemCount) upcomingIdx else targetIndex
                                }
                                if (match in 0 until player.mediaItemCount) {
                                    player.removeMediaItem(match)
                                }
                            }
                            "move_track" -> {
                                val from = extraData?.optInt("from", -1) ?: -1
                                val to = extraData?.optInt("to", -1) ?: -1
                                val cur = player.currentMediaItemIndex
                                val fromTimeline = cur + 1 + from
                                val toTimeline = cur + 1 + to
                                if (fromTimeline in 0 until player.mediaItemCount && toTimeline in 0 until player.mediaItemCount) {
                                    player.moveMediaItem(fromTimeline, toTimeline)
                                } else if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount) {
                                    player.moveMediaItem(from, to)
                                }
                            }
                        }
                        server.stateVersion++
                    }
                }
                server.start(30000, true)
                lanServer = server
                lanHostAddress = "$localIp:$port"
                _connectionMode.value = ListenTogetherConnectionMode.LAN

                originalGuestParticipantId = null
                sessionHostId = "host"
                val initialSession = server.buildSessionSnapshot("host")
                _session.value = initialSession
                _isHost.value = true
                lastAppliedVersion = initialSession.stateVersion

                ListenTogetherStore.save(
                    context = context,
                    session = initialSession,
                    displayName = _displayName.value,
                    isHost = true,
                    isLan = true,
                    hostAddress = "$localIp:$port"
                )
                setDisplayMessage("LAN host started on $localIp:$port")
                restartSync()
            } catch (e: Exception) {
                setDisplayMessage("Failed to start LAN host: ${e.message}")
            }
        }
    }

    fun joinLanSession(hostAddress: String) {
        val context = appContext ?: return
        scope.launch {
            runCatching {
                val cleanAddress = LanTogetherClient.normalizeHostAddress(hostAddress)
                val newSession = LanTogetherClient.joinSession(cleanAddress, _displayName.value)
                cleanAddress to newSession
            }.onSuccess { (cleanAddress, newSession) ->
                lanHostAddress = cleanAddress
                _connectionMode.value = ListenTogetherConnectionMode.LAN
                originalGuestParticipantId = newSession.participantId
                sessionHostId = "host"
                val hasPermission = newSession.settings.allowGuestsToControlPlayback
                val effectiveId = if (hasPermission) "host" else newSession.participantId
                val effectiveSession = newSession.copy(participantId = effectiveId)
                _session.value = effectiveSession
                _isHost.value = false
                lastAppliedVersion = 0L
                loadingSongId = null
                lastSongLoadAttempt = 0L
                ListenTogetherStore.save(
                    context = context,
                    session = newSession.copy(participantId = newSession.participantId),
                    displayName = _displayName.value,
                    isHost = false,
                    isLan = true,
                    hostAddress = cleanAddress
                )
                setDisplayMessage("Joined LAN session")
                restartSync()
            }.onFailure {
                setDisplayMessage(it.message ?: "Failed to connect to LAN host")
            }
        }
    }

    fun leaveSession() {
        val context = appContext
        val activeSession = _session.value
        val isLan = _connectionMode.value == ListenTogetherConnectionMode.LAN
        val hostAddr = lanHostAddress
        scope.launch {
            if (activeSession != null) {
                if (isLan) {
                    if (_isHost.value) {
                        runCatching { lanServer?.stop() }
                        lanServer = null
                    } else if (!hostAddr.isNullOrBlank()) {
                        runCatching { LanTogetherClient.leaveSession(hostAddr, activeSession.participantId) }
                    }
                } else {
                    runCatching {
                        ListenTogetherClient.leaveSession(activeSession.code, activeSession.participantId)
                    }
                }
            }
            lanServer?.stop()
            lanServer = null
            lanHostAddress = null
            loadingSongId = null
            lastSongLoadAttempt = 0L
            originalGuestParticipantId = null
            sessionHostId = null
            playerConnection?.player?.let { player ->
                if (player.playbackParameters.speed != 1.0f) {
                    player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                }
            }
            if (context != null) ListenTogetherStore.clear(context)
            _session.value = null
            _isHost.value = false
            _message.value = null
            syncJob?.cancel()
        }
    }

    private fun restartSync() {
        syncJob?.cancel()
        val connection = playerConnection ?: return
        syncJob =
            scope.launch {
                while (true) {
                    val activeSession = _session.value ?: break
                    syncOnce(connection, activeSession)
                    delay(SYNC_MS)
                }
            }
    }

    private suspend fun syncOnce(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        if (_connectionMode.value == ListenTogetherConnectionMode.LAN) {
            syncOnceLan(connection, session)
        } else {
            syncOnceOnline(connection, session)
        }
    }

    private fun syncOnceLan(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        val server = lanServer
        if (_isHost.value && server != null) {
            val metadata = connection.mediaMetadata.value
            if (metadata != null) {
                val current = server.playbackState
                val isPlaying = connection.player.playWhenReady
                val pos = connection.player.currentPosition
                val timeSinceAction = System.currentTimeMillis() - server.lastActionTime
                val isActionPending = timeSinceAction < 2000L && current?.isPlaying != isPlaying

                val isTrackChange = current == null || current.songId != metadata.id
                val isPlayStateChange = current != null && current.isPlaying != isPlaying
                val expectedPos = if (current != null && current.isPlaying) {
                    current.positionMs + (System.currentTimeMillis() - current.updatedAt)
                } else {
                    current?.positionMs ?: 0L
                }
                val isHardSeek = current != null && kotlin.math.abs(pos - expectedPos) > 3000L

                val needsUpdate = !isActionPending && (isTrackChange || isPlayStateChange || isHardSeek ||
                    kotlin.math.abs(pos - (current?.positionMs ?: 0L)) > 1000L)

                if (needsUpdate) {
                    val player = connection.player
                    val currentQueue = mutableListOf<ListenTogetherQueueItem>()
                    if (!player.currentTimeline.isEmpty) {
                        val count = player.mediaItemCount
                        val currentIndex = player.currentMediaItemIndex
                        val start = (currentIndex + 1).coerceAtLeast(0)
                        for (i in start until count) {
                            val item = player.getMediaItemAt(i)
                            val meta = item.mediaMetadata
                            val t = meta.title?.toString() ?: "Unknown Song"
                            val a = meta.artist?.toString() ?: ""
                            val thumb = meta.artworkUri?.toString()
                            currentQueue.add(ListenTogetherQueueItem(songId = item.mediaId, title = t, artist = a, thumbnailUrl = thumb))
                        }
                    }
                    _sharedQueue.value = currentQueue

                    server.playbackState = ListenTogetherPlaybackState(
                        songId = metadata.id,
                        title = metadata.title,
                        artists = metadata.artists.map { it.name },
                        thumbnailUrl = metadata.thumbnailUrl,
                        positionMs = pos,
                        isPlaying = isPlaying,
                        durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                        updatedAt = System.currentTimeMillis(),
                        settings = server.settings,
                        queue = currentQueue,
                        totalQueueSize = player.mediaItemCount,
                    )
                    if (isTrackChange || isPlayStateChange || isHardSeek) {
                        server.stateVersion++
                    }
                }
            }
            _session.value = server.buildSessionSnapshot("host")
            return
        }

        val host = lanHostAddress ?: return
        scope.launch {
            runCatching {
                val hasPermission = _session.value?.settings?.allowGuestsToControlPlayback == true
                val participantIdToSend = if (hasPermission) "host" else (originalGuestParticipantId ?: session.participantId)
                LanTogetherClient.getSession(host, participantIdToSend)
            }.onSuccess { remoteSession ->
                val hasPermission = remoteSession.settings.allowGuestsToControlPlayback
                val effectiveId = if (hasPermission) "host" else (originalGuestParticipantId ?: session.participantId)
                val remoteQueue = remoteSession.state?.queue?.takeIf { it.isNotEmpty() } ?: remoteSession.queue
                val keptSession = remoteSession.copy(
                    participantId = effectiveId,
                    queue = remoteQueue
                )
                _session.value = keptSession
                if (remoteQueue.isNotEmpty() || _sharedQueue.value.isNotEmpty()) {
                    _sharedQueue.value = remoteQueue
                }
                val remoteState = remoteSession.state ?: return@onSuccess
                val now = System.currentTimeMillis()

                val currentSongId = connection.player.currentMediaItem?.mediaId
                val isHostSong = remoteState.songId.isNotBlank() && currentSongId == remoteState.songId
                val isPendingSongMatch = pendingGuestSongId != null && remoteState.songId == pendingGuestSongId

                if (now < suppressLocalSyncUntil) {
                    if (isPendingSongMatch || (pendingGuestSongId == null && isHostSong)) {
                        suppressLocalSyncUntil = 0L
                        pendingGuestSongId = null
                        lastAppliedVersion = remoteSession.stateVersion
                    } else {
                        return@onSuccess
                    }
                } else {
                    pendingGuestSongId = null
                }

                val isWrongSong = remoteState.songId.isNotBlank() && currentSongId != remoteState.songId
                val isNewVersion = remoteSession.stateVersion > lastAppliedVersion

                if (isWrongSong || isNewVersion) {
                    applyRemoteState(connection, remoteSession)
                    if (currentSongId == remoteState.songId) {
                        lastAppliedVersion = remoteSession.stateVersion
                    }
                    return@onSuccess
                }
                softCorrectPosition(connection, remoteSession)
            }.onFailure {
                setDisplayMessage(it.message)
            }
        }
    }

    private suspend fun syncOnceOnline(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        runCatching {
            ListenTogetherClient.getSession(session.code)
        }.onSuccess { remoteSession ->
            if (sessionHostId.isNullOrBlank() && remoteSession.controllerId.isNotBlank()) {
                sessionHostId = remoteSession.controllerId
            }
            val preservedSettings = _session.value?.settings ?: session.settings
            val resolvedSettings = if (_isHost.value) {
                preservedSettings
            } else {
                remoteSession.settings
            }
            val hasPlaybackPermission = resolvedSettings.allowGuestsToControlPlayback
            val effectiveParticipantId = if (!_isHost.value) {
                if (hasPlaybackPermission && !sessionHostId.isNullOrBlank()) {
                    sessionHostId!!
                } else {
                    originalGuestParticipantId ?: session.participantId
                }
            } else {
                session.participantId
            }

            val remoteQueue = remoteSession.state?.queue?.takeIf { it.isNotEmpty() }
                ?: remoteSession.queue
            val keptSession = remoteSession.copy(
                participantId = effectiveParticipantId,
                settings = resolvedSettings,
                state = remoteSession.state?.copy(settings = resolvedSettings, queue = remoteQueue) ?: remoteSession.state,
                queue = remoteQueue,
            )
            _session.value = keptSession
            if (remoteQueue.isNotEmpty() || _sharedQueue.value.isNotEmpty()) {
                _sharedQueue.value = remoteQueue
            }
            val remoteState = remoteSession.state ?: return@onSuccess
            val now = System.currentTimeMillis()

            if (_isHost.value) {
                // If an admin guest changed the song or playback state directly, sync immediately
                val isRemoteNewer = remoteSession.stateVersion > lastAppliedVersion
                val currentSongId = connection.player.currentMediaItem?.mediaId
                val isDifferentSong = remoteState.songId.isNotBlank() && remoteState.songId != currentSongId
                val isPlayStateChanged = remoteState.isPlaying != connection.player.playWhenReady

                if (isRemoteNewer && (isDifferentSong || isPlayStateChanged) && (now > suppressLocalSyncUntil)) {
                    applyRemoteState(connection, remoteSession)
                    lastAppliedVersion = remoteSession.stateVersion
                    lastControllerPublishAt = now
                    return@onSuccess
                }

                val cmd = remoteState.actionCommand
                val actionId = remoteState.actionId
                if (!cmd.isNullOrBlank() && actionId != null && actionId != lastHandledActionId) {
                    lastHandledActionId = actionId
                    val isControlCmd = cmd in listOf("play", "pause", "toggle_play_pause", "seek", "next", "skip_next", "previous", "skip_previous", "play_song", "play_index", "jump_to")
                    val isQueueCmd = cmd in listOf("add_track", "play_next", "remove_track", "remove_song", "move_track")
                    val allowed = if (isControlCmd) {
                        keptSession.settings.allowGuestsToControlPlayback || keptSession.settings.allowGuestsToAddTracks
                    } else if (isQueueCmd) {
                        keptSession.settings.allowGuestsToAddTracks || keptSession.settings.allowGuestsToControlPlayback
                    } else false

                    if (allowed) {
                        withContext(Dispatchers.Main) {
                            val player = connection.player
                            val json = remoteState.actionPayload?.let { runCatching { JSONObject(it) }.getOrNull() }
                            when (cmd) {
                                "play" -> player.play()
                                "pause" -> player.pause()
                                "toggle_play_pause" -> connection.togglePlayPause()
                                "seek" -> if (remoteState.positionMs >= 0) player.seekTo(remoteState.positionMs)
                                "next", "skip_next" -> connection.seekToNext()
                                "previous", "skip_previous" -> connection.seekToPrevious()
                                "play_index", "jump_to" -> {
                                    val targetIndex = json?.optInt("index", -1) ?: -1
                                    val targetSongId = json?.optString("songId")
                                    var match = -1
                                    val cur = player.currentMediaItemIndex
                                    if (!targetSongId.isNullOrBlank()) {
                                        for (i in (cur + 1).coerceAtLeast(0) until player.mediaItemCount) {
                                            if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                                match = i
                                                break
                                            }
                                        }
                                        if (match < 0) {
                                            for (i in 0 until player.mediaItemCount) {
                                                if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                                    match = i
                                                    break
                                                }
                                            }
                                        }
                                    } else if (targetIndex >= 0) {
                                        val upcomingIdx = cur + 1 + targetIndex
                                        match = if (upcomingIdx in 0 until player.mediaItemCount) upcomingIdx else targetIndex
                                    }
                                    if (match in 0 until player.mediaItemCount) {
                                        player.seekToDefaultPosition(match)
                                        player.play()
                                    }
                                }
                                "play_song" -> {
                                    val targetSongId = json?.optString("songId")?.ifBlank { json.optString("id") }
                                    var found = false
                                    if (!targetSongId.isNullOrBlank()) {
                                        for (i in 0 until player.mediaItemCount) {
                                            if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                                player.seekToDefaultPosition(i)
                                                player.play()
                                                found = true
                                                break
                                            }
                                        }
                                    }
                                    if (!found && json != null) {
                                        val title = json.optString("title").ifBlank { "Queued Track" }
                                        val artist = json.optString("artist").ifBlank { json.optString("artists") }
                                        val meta = com.valora.icebeats.models.MediaMetadata(
                                            id = targetSongId ?: "",
                                            title = title,
                                            artists = if (artist.isNotBlank()) listOf(com.valora.icebeats.models.MediaMetadata.Artist(id = null, name = artist)) else emptyList(),
                                            duration = -1,
                                            thumbnailUrl = targetSongId?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }
                                        )
                                        connection.playQueue(com.valora.icebeats.playback.queues.ListQueue(items = listOf(meta.toMediaItem()), startIndex = 0))
                                    }
                                }
                                "add_track" -> {
                                    if (json != null) {
                                        val songId = json.optString("songId").ifBlank { json.optString("id") }
                                        val title = json.optString("title").ifBlank { "Queued Track" }
                                        val artist = json.optString("artist").ifBlank { json.optString("artists") }
                                        val meta = com.valora.icebeats.models.MediaMetadata(
                                            id = songId,
                                            title = title,
                                            artists = if (artist.isNotBlank()) listOf(com.valora.icebeats.models.MediaMetadata.Artist(id = null, name = artist)) else emptyList(),
                                            duration = -1,
                                            thumbnailUrl = if (songId.isNotBlank()) "https://i.ytimg.com/vi/$songId/mqdefault.jpg" else null
                                        )
                                        connection.addToQueue(meta.toMediaItem())
                                    }
                                }
                                "play_next" -> {
                                    if (json != null) {
                                        val songId = json.optString("songId").ifBlank { json.optString("id") }
                                        val title = json.optString("title").ifBlank { "Queued Track" }
                                        val artist = json.optString("artist").ifBlank { json.optString("artists") }
                                        val meta = com.valora.icebeats.models.MediaMetadata(
                                            id = songId,
                                            title = title,
                                            artists = if (artist.isNotBlank()) listOf(com.valora.icebeats.models.MediaMetadata.Artist(id = null, name = artist)) else emptyList(),
                                            duration = -1,
                                            thumbnailUrl = if (songId.isNotBlank()) "https://i.ytimg.com/vi/$songId/mqdefault.jpg" else null
                                        )
                                        connection.playNext(meta.toMediaItem())
                                    }
                                }
                                "remove_track", "remove_song" -> {
                                    val targetIndex = json?.optInt("index", -1) ?: -1
                                    val targetSongId = json?.optString("songId")
                                    var match = -1
                                    val cur = player.currentMediaItemIndex
                                    if (!targetSongId.isNullOrBlank()) {
                                        for (i in (cur + 1).coerceAtLeast(0) until player.mediaItemCount) {
                                            if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                                match = i
                                                break
                                            }
                                        }
                                        if (match < 0) {
                                            for (i in 0 until player.mediaItemCount) {
                                                if (player.getMediaItemAt(i).mediaId == targetSongId) {
                                                    match = i
                                                    break
                                                }
                                            }
                                        }
                                    } else if (targetIndex >= 0) {
                                        val upcomingIdx = cur + 1 + targetIndex
                                        match = if (upcomingIdx in 0 until player.mediaItemCount) upcomingIdx else targetIndex
                                    }
                                    if (match in 0 until player.mediaItemCount) {
                                        player.removeMediaItem(match)
                                    }
                                }
                                "move_track" -> {
                                    val from = json?.optInt("from", -1) ?: -1
                                    val to = json?.optInt("to", -1) ?: -1
                                    val cur = player.currentMediaItemIndex
                                    val fromTimeline = cur + 1 + from
                                    val toTimeline = cur + 1 + to
                                    if (fromTimeline in 0 until player.mediaItemCount && toTimeline in 0 until player.mediaItemCount) {
                                        player.moveMediaItem(fromTimeline, toTimeline)
                                    } else if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount) {
                                        player.moveMediaItem(from, to)
                                    }
                                }
                            }
                        }
                        publishLocalStateOnline(connection, keptSession)
                        return@onSuccess
                    }
                }

                if (now - lastControllerPublishAt >= CONTROLLER_PUBLISH_MS) {
                    publishLocalStateOnline(connection, keptSession)
                    lastControllerPublishAt = now
                }
                return@onSuccess
            }

            val currentSongId = connection.player.currentMediaItem?.mediaId
            val isHostSong = remoteState.songId.isNotBlank() && currentSongId == remoteState.songId
            val isPendingSongMatch = pendingGuestSongId != null && remoteState.songId == pendingGuestSongId

            if (now < suppressLocalSyncUntil) {
                if (isPendingSongMatch || (pendingGuestSongId == null && isHostSong)) {
                    suppressLocalSyncUntil = 0L
                    pendingGuestSongId = null
                    lastAppliedVersion = remoteSession.stateVersion
                } else {
                    return@onSuccess
                }
            } else {
                pendingGuestSongId = null
            }

            val isWrongSong = remoteState.songId.isNotBlank() && currentSongId != remoteState.songId
            val isNewVersion = remoteSession.stateVersion > lastAppliedVersion

            if (isWrongSong || isNewVersion) {
                applyRemoteState(connection, remoteSession)
                if (currentSongId == remoteState.songId) {
                    lastAppliedVersion = remoteSession.stateVersion
                }
                return@onSuccess
            }
            softCorrectPosition(connection, remoteSession)
        }.onFailure {
            Log.w(TAG, "syncOnceOnline failed: ${it.message}")
        }
    }

    private suspend fun publishLocalStateOnline(
        connection: PlayerConnection,
        session: ListenTogetherSession,
    ) {
        val metadata = connection.mediaMetadata.value ?: return
        val player = connection.player
        val upcomingQueue = mutableListOf<ListenTogetherQueueItem>()
        if (!player.currentTimeline.isEmpty) {
            val count = player.mediaItemCount
            val currentIndex = player.currentMediaItemIndex
            val start = (currentIndex + 1).coerceAtLeast(0)
            val end = minOf(count, start + 30)
            for (i in start until end) {
                val item = player.getMediaItemAt(i)
                val meta = item.mediaMetadata
                val t = meta.title?.toString() ?: "Unknown Song"
                val a = meta.artist?.toString() ?: ""
                val thumb = meta.artworkUri?.toString()
                upcomingQueue.add(ListenTogetherQueueItem(songId = item.mediaId, title = t, artist = a, thumbnailUrl = thumb))
            }
        }
        _sharedQueue.value = upcomingQueue

        runCatching {
            ListenTogetherClient.updateState(
                code = session.code,
                participantId = session.participantId,
                state =
                    ListenTogetherPlaybackState(
                        songId = metadata.id,
                        title = metadata.title,
                        artists = metadata.artists.map { it.name },
                        thumbnailUrl = metadata.thumbnailUrl,
                        positionMs = connection.player.currentPosition,
                        isPlaying = connection.player.playWhenReady,
                        durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                        settings = session.settings,
                        actionCommand = null,
                        actionPayload = null,
                        actionId = null,
                        queue = upcomingQueue,
                        totalQueueSize = player.mediaItemCount,
                    )
            )
        }.onSuccess { updatedSession ->
            val preservedSettings = _session.value?.settings ?: session.settings
            _session.value = updatedSession.copy(
                participantId = session.participantId,
                settings = preservedSettings,
                state = updatedSession.state?.copy(settings = preservedSettings, queue = upcomingQueue) ?: updatedSession.state,
                queue = upcomingQueue,
            )
            lastAppliedVersion = updatedSession.stateVersion
            lastControllerPublishAt = System.currentTimeMillis()
        }.onFailure {
            Log.w(TAG, "publishLocalStateOnline failed: ${it.message}")
        }
    }

    fun publishNow() {
        val connection = playerConnection ?: return
        val session = _session.value ?: return
        if (_isHost.value) {
            scope.launch {
                if (_connectionMode.value == ListenTogetherConnectionMode.ONLINE) {
                    publishLocalStateOnline(connection, session)
                } else {
                    syncOnceLan(connection, session)
                }
            }
        }
    }

    fun onMediaItemTransitioned(newSongId: String? = null) {
        loadingSongId = null
        lastSongLoadAttempt = 0L
        if (!newSongId.isNullOrBlank() && newSongId == pendingGuestSongId) {
            pendingGuestSongId = null
            suppressLocalSyncUntil = 0L
        }
    }

    fun removeLocalSharedQueueItem(index: Int, songId: String? = null) {
        val current = _sharedQueue.value.toMutableList()
        if (!songId.isNullOrBlank()) {
            val idx = current.indexOfFirst { it.songId == songId }
            if (idx >= 0) {
                current.removeAt(idx)
                _sharedQueue.value = current
                return
            }
        }
        if (index in 0 until current.size) {
            current.removeAt(index)
            _sharedQueue.value = current
        }
    }

    fun moveLocalSharedQueueItem(from: Int, to: Int) {
        val current = _sharedQueue.value.toMutableList()
        if (from in 0 until current.size && to in 0 until current.size && from != to) {
            val item = current.removeAt(from)
            current.add(to, item)
            _sharedQueue.value = current
        }
    }

    private suspend fun applyRemoteState(
        connection: PlayerConnection,
        remoteSession: ListenTogetherSession,
    ) {
        val remoteState = remoteSession.state ?: return
        val remoteQueue = remoteSession.state?.queue?.takeIf { it.isNotEmpty() }
            ?: remoteSession.queue
        if (remoteQueue.isNotEmpty() || _sharedQueue.value.isNotEmpty()) {
            _sharedQueue.value = remoteQueue
        }

        val player = connection.player
        val currentSongId = player.currentMediaItem?.mediaId
        val now = System.currentTimeMillis()

        if (remoteState.songId.isNotBlank()) {
            if (currentSongId != remoteState.songId) {
                val isStaleLoad = (now - lastSongLoadAttempt) > 4000L
                if (loadingSongId != remoteState.songId || isStaleLoad) {
                    loadingSongId = remoteState.songId
                    lastSongLoadAttempt = now
                    suppressLocalSyncUntil = now + APPLY_GRACE_MS

                    withContext(Dispatchers.Main) {
                        var foundIndex = -1
                        for (i in 0 until player.mediaItemCount) {
                            if (player.getMediaItemAt(i).mediaId == remoteState.songId) {
                                foundIndex = i
                                break
                            }
                        }

                        val targetPos = expectedPosition(remoteSession)
                        if (foundIndex >= 0) {
                            player.seekTo(foundIndex, targetPos)
                            if (remoteState.isPlaying) {
                                player.play()
                            } else {
                                player.pause()
                            }
                            val from = foundIndex + 1
                            val to = player.mediaItemCount
                            if (to > from) {
                                player.removeMediaItems(from, to)
                            }
                            if (remoteQueue.isNotEmpty()) {
                                player.addMediaItems(from, remoteQueue.map { it.toMediaItem() })
                            }
                        } else {
                            val targetMetadata = remoteState.toMediaMetadata()
                            val upcomingMediaItems = remoteQueue.map { it.toMediaItem() }
                            val fullQueueItems = listOf(targetMetadata.toMediaItem()) + upcomingMediaItems
                            connection.playQueue(
                                com.valora.icebeats.playback.queues.ListQueue(
                                    title = "Listen Together",
                                    items = fullQueueItems,
                                    startIndex = 0,
                                    position = targetPos
                                )
                            )
                            if (remoteState.isPlaying) {
                                player.play()
                            } else {
                                player.pause()
                            }
                        }
                    }
                }
                return
            } else {
                loadingSongId = null
                withContext(Dispatchers.Main) {
                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex >= 0) {
                        val existingUpcomingIds = mutableListOf<String>()
                        for (i in (currentIndex + 1) until player.mediaItemCount) {
                            existingUpcomingIds.add(player.getMediaItemAt(i).mediaId)
                        }
                        val remoteUpcomingIds = remoteQueue.map { it.songId }
                        if (existingUpcomingIds != remoteUpcomingIds) {
                            val from = currentIndex + 1
                            val to = player.mediaItemCount
                            if (to > from) {
                                player.removeMediaItems(from, to)
                            }
                            if (remoteQueue.isNotEmpty()) {
                                player.addMediaItems(from, remoteQueue.map { it.toMediaItem() })
                            }
                        }
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (player.playbackState == androidx.media3.common.Player.STATE_READY) {
                val target = expectedPosition(remoteSession)
                val isLan = _connectionMode.value == ListenTogetherConnectionMode.LAN
                val hardSeekThreshold = if (isLan) 3500L else 5000L
                val diff = player.currentPosition - target
                val absDiff = kotlin.math.abs(diff)

                if (absDiff > hardSeekThreshold) {
                    player.seekTo(target)
                    if (player.playbackParameters.speed != 1.0f) {
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                    }
                } else if (absDiff > (if (isLan) 800L else 1200L)) {
                    val speed = if (diff < 0) 1.04f else 0.96f
                    if (player.playbackParameters.speed != speed) {
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                    }
                } else {
                    if (player.playbackParameters.speed != 1.0f) {
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
                    }
                }
            }

            if (remoteState.isPlaying && !player.playWhenReady) {
                player.play()
            } else if (!remoteState.isPlaying && player.playWhenReady) {
                player.pause()
            }
        }
    }

    private fun softCorrectPosition(
        connection: PlayerConnection,
        remoteSession: ListenTogetherSession,
    ) {
        val now = System.currentTimeMillis()
        if (now < suppressLocalSyncUntil) {
            return
        }

        val player = connection.player
        val remoteState = remoteSession.state ?: return
        val currentSongId = player.currentMediaItem?.mediaId
        if (currentSongId != remoteState.songId) {
            return
        }

        val remoteQueue = remoteSession.state?.queue?.takeIf { it.isNotEmpty() } ?: remoteSession.queue
        if (remoteQueue.isNotEmpty() || _sharedQueue.value.isNotEmpty()) {
            _sharedQueue.value = remoteQueue
        }

        if (player.playbackState != androidx.media3.common.Player.STATE_READY) {
            return
        }

        val target = expectedPosition(remoteSession)
        val isLan = _connectionMode.value == ListenTogetherConnectionMode.LAN
        val hardSeekThreshold = if (isLan) 3500L else 5000L
        val diff = player.currentPosition - target
        val absDiff = kotlin.math.abs(diff)

        if (absDiff > hardSeekThreshold) {
            suppressLocalSyncUntil = System.currentTimeMillis() + APPLY_GRACE_MS
            player.seekTo(target)
            if (player.playbackParameters.speed != 1.0f) {
                player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
            }
        } else if (absDiff > (if (isLan) 800L else 1200L)) {
            val speed = if (diff < 0) 1.04f else 0.96f
            if (player.playbackParameters.speed != speed) {
                player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
            }
        } else {
            if (player.playbackParameters.speed != 1.0f) {
                player.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
            }
        }
    }

    private fun expectedPosition(session: ListenTogetherSession): Long {
        val state = session.state ?: return 0L
        val serverAge = (session.serverNow - state.updatedAt).coerceAtLeast(0)
        return if (state.isPlaying) {
            state.positionMs + serverAge
        } else {
            state.positionMs
        }.coerceAtLeast(0)
    }

    fun updateSettings(
        allowGuestsToControlPlayback: Boolean,
        allowGuestsToAddTracks: Boolean,
    ) {
        if (!_isHost.value) return
        val newSettings = ListenTogetherSettings(
            allowGuestsToControlPlayback = allowGuestsToControlPlayback,
            allowGuestsToAddTracks = allowGuestsToAddTracks,
        )
        val currentSession = _session.value ?: return
        val updatedState = currentSession.state?.copy(settings = newSettings)
        _session.value = currentSession.copy(
            settings = newSettings,
            state = updatedState
        )

        scope.launch {
            if (_connectionMode.value == ListenTogetherConnectionMode.LAN) {
                val server = lanServer ?: return@launch
                server.settings = newSettings
                server.stateVersion++
                _session.value = server.buildSessionSnapshot("host")
            } else {
                val session = _session.value ?: return@launch
                val connection = playerConnection
                val metadata = connection?.mediaMetadata?.value
                val stateToSend = if (metadata != null && connection != null) {
                    ListenTogetherPlaybackState(
                        songId = metadata.id,
                        title = metadata.title,
                        artists = metadata.artists.map { it.name },
                        thumbnailUrl = metadata.thumbnailUrl,
                        positionMs = connection.player.currentPosition,
                        isPlaying = connection.player.playWhenReady,
                        durationMs = connection.player.duration.takeIf { it > 0 } ?: (metadata.duration.toLong() * 1000L).coerceAtLeast(0L),
                        settings = newSettings,
                    )
                } else {
                    session.state?.copy(settings = newSettings) ?: ListenTogetherPlaybackState(settings = newSettings)
                }

                runCatching {
                    ListenTogetherClient.updateState(
                        code = session.code,
                        participantId = session.participantId,
                        state = stateToSend
                    )
                }.onSuccess { updated ->
                    _session.value = updated.copy(
                        participantId = session.participantId,
                        settings = newSettings,
                        state = updated.state?.copy(settings = newSettings) ?: updated.state
                    )
                }.onFailure {
                    Log.w(TAG, "updateSettings online failed: ${it.message}")
                }
            }
        }
    }

    fun kickParticipant(participantId: String) {
        if (!_isHost.value) return
        lanServer?.kickParticipant(participantId)
        val currentSession = _session.value
        if (currentSession != null) {
            val updatedList = currentSession.participantList.filter { it.id != participantId }
            _session.value = currentSession.copy(
                participants = updatedList.size,
                participantList = updatedList
            )
        }
    }

    fun sendGuestAction(
        action: String,
        positionMs: Long? = null,
        extraData: JSONObject? = null,
    ): Boolean {
        val activeSession = _session.value ?: return false
        val settings = activeSession.settings

        if (action in listOf("play", "pause", "toggle_play_pause", "seek", "next", "skip_next", "previous", "skip_previous", "play_song", "play_index", "jump_to")) {
            if (!_isHost.value && !settings.allowGuestsToControlPlayback && !settings.allowGuestsToAddTracks) {
                setDisplayMessage("Playback controls are disabled by the host")
                return false
            }
        }
        if (action in listOf("add_track", "play_next", "remove_track", "remove_song", "move_track")) {
            if (!_isHost.value && !settings.allowGuestsToAddTracks && !settings.allowGuestsToControlPlayback) {
                setDisplayMessage("Queue modifications are disabled by the host")
                return false
            }
        }

        if (_isHost.value) {
            val connection = playerConnection ?: return false
            when (action) {
                "play" -> connection.player.play()
                "pause" -> connection.player.pause()
                "toggle_play_pause" -> connection.togglePlayPause()
                "seek" -> if (positionMs != null && positionMs >= 0) connection.player.seekTo(positionMs)
                "next", "skip_next" -> connection.seekToNext()
                "previous", "skip_previous" -> connection.seekToPrevious()
            }
            publishNow()
            return true
        }

        val hasControlPermission = settings.allowGuestsToControlPlayback
        val adminParticipantId = if (hasControlPermission && !sessionHostId.isNullOrBlank()) sessionHostId!! else activeSession.participantId

        var targetSongId: String? = null
        var targetTitle: String? = null
        var targetArtist: String? = null
        var targetThumb: String? = null

        when (action) {
            "play_song" -> {
                targetSongId = extraData?.optString("songId")?.ifBlank { extraData.optString("id") }
                targetTitle = extraData?.optString("title")
                targetArtist = extraData?.optString("artist")?.ifBlank { extraData?.optString("artists") }
                targetThumb = extraData?.optString("thumbnailUrl")
            }
            "play_index", "jump_to" -> {
                val targetIndex = extraData?.optInt("index", -1) ?: -1
                targetSongId = extraData?.optString("songId")
                if (targetSongId.isNullOrBlank() && targetIndex in 0 until _sharedQueue.value.size) {
                    val item = _sharedQueue.value[targetIndex]
                    targetSongId = item.songId
                    targetTitle = item.title
                    targetArtist = item.artist
                    targetThumb = item.thumbnailUrl
                }
            }
            "skip_next", "next" -> {
                targetSongId = extraData?.optString("songId")
                if (targetSongId.isNullOrBlank() && _sharedQueue.value.isNotEmpty()) {
                    val item = _sharedQueue.value.first()
                    targetSongId = item.songId
                    targetTitle = item.title
                    targetArtist = item.artist
                    targetThumb = item.thumbnailUrl
                }
            }
            "skip_previous", "previous" -> {
                targetSongId = extraData?.optString("songId")
            }
        }

        if (!targetSongId.isNullOrBlank()) {
            pendingGuestSongId = targetSongId
            suppressLocalSyncUntil = System.currentTimeMillis() + 4000L

            // Instant local playback on guest device for immediate response
            playerConnection?.let { conn ->
                val p = conn.player
                var matchIdx = -1
                for (i in 0 until p.mediaItemCount) {
                    if (p.getMediaItemAt(i).mediaId == targetSongId) {
                        matchIdx = i
                        break
                    }
                }
                if (matchIdx >= 0) {
                    p.seekToDefaultPosition(matchIdx)
                    p.play()
                } else {
                    val meta = com.valora.icebeats.models.MediaMetadata(
                        id = targetSongId,
                        title = targetTitle ?: "Song",
                        artists = if (!targetArtist.isNullOrBlank()) listOf(com.valora.icebeats.models.MediaMetadata.Artist(id = null, name = targetArtist)) else emptyList(),
                        duration = -1,
                        thumbnailUrl = targetThumb ?: "https://i.ytimg.com/vi/$targetSongId/mqdefault.jpg",
                    )
                    conn.playQueue(com.valora.icebeats.playback.queues.ListQueue(items = listOf(meta.toMediaItem()), startIndex = 0))
                }
            }
        } else {
            suppressLocalSyncUntil = System.currentTimeMillis() + 3000L
        }

        scope.launch {
            runCatching {
                if (_connectionMode.value == ListenTogetherConnectionMode.LAN) {
                    val hostAddr = lanHostAddress ?: return@launch
                    LanTogetherClient.sendAction(
                        hostAddress = hostAddr,
                        participantId = if (hasControlPermission) "host" else activeSession.participantId,
                        action = action,
                        positionMs = positionMs,
                        extraData = extraData,
                    )
                } else {
                    val currentState = activeSession.state ?: ListenTogetherPlaybackState(settings = activeSession.settings)
                    val targetPlaying = when (action) {
                        "play" -> true
                        "pause" -> false
                        "toggle_play_pause" -> !currentState.isPlaying
                        else -> true
                    }
                    val targetPosition = if (!targetSongId.isNullOrBlank()) 0L else (positionMs ?: currentState.positionMs)
                    val remainingQueue = if (!targetSongId.isNullOrBlank()) {
                        _sharedQueue.value.filterNot { it.songId == targetSongId }
                    } else {
                        _sharedQueue.value
                    }
                    _sharedQueue.value = remainingQueue

                    val newState = ListenTogetherPlaybackState(
                        songId = targetSongId ?: currentState.songId,
                        title = targetTitle ?: if (targetSongId == null) currentState.title else "Song",
                        artists = if (!targetArtist.isNullOrBlank()) listOf(targetArtist) else currentState.artists,
                        thumbnailUrl = targetThumb ?: currentState.thumbnailUrl,
                        positionMs = targetPosition,
                        isPlaying = targetPlaying,
                        durationMs = currentState.durationMs,
                        updatedAt = System.currentTimeMillis(),
                        settings = activeSession.settings,
                        actionCommand = action,
                        actionPayload = extraData?.toString(),
                        actionId = UUID.randomUUID().toString().take(8),
                        queue = remainingQueue,
                        totalQueueSize = remainingQueue.size,
                    )
                    ListenTogetherClient.updateState(
                        code = activeSession.code,
                        participantId = adminParticipantId,
                        state = newState,
                    )
                }
            }.onSuccess { updatedSession ->
                val preservedSettings = activeSession.settings
                _session.value = updatedSession.copy(
                    participantId = adminParticipantId,
                    settings = preservedSettings,
                    state = updatedSession.state?.copy(settings = preservedSettings) ?: updatedSession.state
                )
                lastAppliedVersion = updatedSession.stateVersion
                suppressLocalSyncUntil = System.currentTimeMillis() + 4000L
                playerConnection?.let { conn ->
                    scope.launch {
                        delay(100L)
                        syncOnce(conn, _session.value ?: updatedSession)
                    }
                }
            }.onFailure { e ->
                setDisplayMessage(e.message ?: "Failed to send action")
            }
        }
        return true
    }

    fun notifyDisallowedAction(notice: String) {
        setDisplayMessage(notice)
    }
}
