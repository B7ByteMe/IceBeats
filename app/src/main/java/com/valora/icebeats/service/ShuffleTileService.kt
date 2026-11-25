package com.valora.icebeats.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.valora.icebeats.R
import com.valora.icebeats.extensions.toMediaItem
import com.valora.icebeats.jiosaavn.JioSaavnApi
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.playback.MusicService
import com.valora.icebeats.playback.PlayerConnection
import com.valora.icebeats.playback.queues.ListQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Quick Settings Tile to start playing music in shuffle mode in the background
 * without opening the application window.
 */
class ShuffleTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeConnection: ServiceConnection? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        activeConnection?.let {
            runCatching { unbindService(it) }
            activeConnection = null
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        activeConnection?.let {
            runCatching { unbindService(it) }
            activeConnection = null
        }
    }

    private fun updateTileState() {
        qsTile?.let { tile ->
            val isPlaying = PlayerConnection.instance?.isPlaying?.value == true ||
                    MusicService.instance?.player?.isPlaying == true

            tile.label = getString(R.string.shuffle)
            if (isPlaying) {
                tile.state = Tile.STATE_ACTIVE
                val currentTitle = PlayerConnection.instance?.mediaMetadata?.value?.title
                    ?: MusicService.instance?.player?.currentMediaItem?.mediaMetadata?.title?.toString()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = currentTitle?.ifBlank { null } ?: getString(R.string.shuffle_playing)
                }
            } else {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.shuffle_tap_to_play)
                }
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val action = Runnable {
            handleShuffle()
        }

        if (isLocked) {
            unlockAndRun(action)
        } else {
            action.run()
        }
    }

    private fun handleShuffle() {
        val existingService = MusicService.instance ?: PlayerConnection.instance?.service
        if (existingService != null) {
            playOrShuffleOnService(existingService)
            return
        }

        val intent = Intent(this, MusicService::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            runCatching { startService(intent) }
        }

        // Safely unbind existing connection if any
        activeConnection?.let {
            runCatching { unbindService(it) }
            activeConnection = null
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as? MusicService.MusicBinder)?.service ?: MusicService.instance
                if (service != null) {
                    playOrShuffleOnService(service)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                activeConnection = null
            }
        }

        activeConnection = connection
        try {
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind to MusicService for shuffle tile")
        }
    }

    private fun playOrShuffleOnService(service: MusicService) {
        val player = service.player

        if (player.mediaItemCount > 0) {
            player.shuffleModeEnabled = true
            if (!player.isPlaying) {
                player.play()
            } else {
                player.seekToNextMediaItem()
            }
            updateTileState()
        } else {
            // Queue is empty: load recent songs, library, liked songs, or trending music!
            serviceScope.launch {
                try {
                    val dbSongs = runCatching { service.database.recentSongs(100).firstOrNull() }.getOrNull()?.takeIf { it.isNotEmpty() }
                        ?: runCatching { service.database.allSongs().firstOrNull() }.getOrNull()?.takeIf { it.isNotEmpty() }
                        ?: runCatching { service.database.likedSongsByRowIdAsc().firstOrNull() }.getOrNull()?.takeIf { it.isNotEmpty() }
                        ?: runCatching { service.database.songsByPlayTimeAsc().firstOrNull() }.getOrNull()?.takeIf { it.isNotEmpty() }

                    if (!dbSongs.isNullOrEmpty()) {
                        val shuffled = dbSongs.shuffled().map { it.toMediaItem() }
                        withContext(Dispatchers.Main) {
                            service.playQueue(ListQueue(title = getString(R.string.shuffle), items = shuffled))
                            service.player.shuffleModeEnabled = true
                            service.player.play()
                            updateTileState()
                        }
                    } else {
                        // Brand new install fallback: fetch trending songs online (YouTube or JioSaavn)
                        val ytSongs = runCatching {
                            val ytRes = YouTube.search("top hits", YouTube.SearchFilter.FILTER_SONG).getOrNull()
                            ytRes?.items?.filterIsInstance<com.valora.icebeats.innertube.models.SongItem>()
                        }.getOrNull()?.takeIf { it.isNotEmpty() }

                        if (!ytSongs.isNullOrEmpty()) {
                            val shuffledMediaItems = ytSongs.shuffled().map { it.toMediaItem() }
                            withContext(Dispatchers.Main) {
                                service.playQueue(ListQueue(title = getString(R.string.shuffle), items = shuffledMediaItems))
                                service.player.shuffleModeEnabled = true
                                service.player.play()
                                updateTileState()
                            }
                        } else {
                            val trendingSongs = runCatching { JioSaavnApi.getTrendingSongs().getOrNull() }.getOrNull()
                            if (!trendingSongs.isNullOrEmpty()) {
                                val shuffledMediaItems = trendingSongs.shuffled().map { it.toMediaItem() }
                                withContext(Dispatchers.Main) {
                                    service.playQueue(ListQueue(title = getString(R.string.shuffle), items = shuffledMediaItems))
                                    service.player.shuffleModeEnabled = true
                                    service.player.play()
                                    updateTileState()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    updateTileState()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading songs for shuffle tile")
                }
            }
        }
    }
}
