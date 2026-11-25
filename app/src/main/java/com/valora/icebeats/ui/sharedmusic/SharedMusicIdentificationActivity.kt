package com.valora.icebeats.ui.sharedmusic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.valora.icebeats.LocalPlayerConnection
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.playback.MusicService
import com.valora.icebeats.playback.PlayerConnection
import com.valora.icebeats.share.ShareIntentParser
import com.valora.icebeats.share.SharedContent
import com.valora.icebeats.ui.theme.IceBeatsTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SharedMusicIdentificationActivity : ComponentActivity() {

    private val viewModel: SharedMusicViewModel by viewModels()

    @Inject
    lateinit var database: MusicDatabase

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isServiceBound = false
    private var pendingAutoPlaySong: com.valora.icebeats.providers.ProviderSong? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicService.MusicBinder) {
                val pc = PlayerConnection(
                    this@SharedMusicIdentificationActivity,
                    service,
                    database,
                    lifecycleScope
                )
                playerConnection = pc
                pendingAutoPlaySong?.let { song ->
                    viewModel.playSong(song, pc)
                    pendingAutoPlaySong = null
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind or reuse existing player connection
        playerConnection = PlayerConnection.instance
        if (playerConnection == null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                runCatching {
                    startService(Intent(this, MusicService::class.java))
                }.onFailure { Timber.e(it, "Failed to start MusicService") }
            }
            runCatching {
                bindService(
                    Intent(this, MusicService::class.java),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )
            }.onSuccess { isServiceBound = it }
             .onFailure { Timber.e(it, "Failed to bind MusicService") }
        }

        val identifiedTitle = intent.getStringExtra(EXTRA_IDENTIFIED_TITLE)
        val shouldAutoPlay = intent.getBooleanExtra(EXTRA_AUTO_PLAY, false)
        val initialContent: SharedContent? = if (!identifiedTitle.isNullOrBlank()) {
            val artist = intent.getStringExtra(EXTRA_IDENTIFIED_ARTIST)
            val album = intent.getStringExtra(EXTRA_IDENTIFIED_ALBUM)
            val artUrl = intent.getStringExtra(EXTRA_IDENTIFIED_ART_URL)
            viewModel.loadIdentifiedSong(identifiedTitle, artist, album, artUrl) { bestMatch ->
                if (shouldAutoPlay) {
                    val pc = playerConnection ?: PlayerConnection.instance
                    if (pc != null) {
                        viewModel.playSong(bestMatch, pc)
                    } else {
                        pendingAutoPlaySong = bestMatch
                    }
                }
            }
            null
        } else {
            val sharedContents = ShareIntentParser.parse(this, intent)
            val content = sharedContents.firstOrNull()
            if (content != null) {
                viewModel.processSharedContent(content)
            }
            content
        }

        setContent {
            IceBeatsTheme {
                CompositionLocalProvider(
                    LocalPlayerConnection provides (playerConnection ?: PlayerConnection.instance)
                ) {
                    SharedMusicScreen(
                        viewModel = viewModel,
                        sharedContent = initialContent,
                        onPlay = { providerSong ->
                            val pc = playerConnection ?: PlayerConnection.instance
                            if (pc != null) {
                                viewModel.playSong(providerSong, pc)
                                val mainIntent = Intent(this@SharedMusicIdentificationActivity, com.valora.icebeats.MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                startActivity(mainIntent)
                                finish()
                            }
                        },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val identifiedTitle = intent.getStringExtra(EXTRA_IDENTIFIED_TITLE)
        val shouldAutoPlay = intent.getBooleanExtra(EXTRA_AUTO_PLAY, false)
        if (!identifiedTitle.isNullOrBlank()) {
            val artist = intent.getStringExtra(EXTRA_IDENTIFIED_ARTIST)
            val album = intent.getStringExtra(EXTRA_IDENTIFIED_ALBUM)
            val artUrl = intent.getStringExtra(EXTRA_IDENTIFIED_ART_URL)
            viewModel.loadIdentifiedSong(identifiedTitle, artist, album, artUrl) { bestMatch ->
                if (shouldAutoPlay) {
                    val pc = playerConnection ?: PlayerConnection.instance
                    if (pc != null) {
                        viewModel.playSong(bestMatch, pc)
                    } else {
                        pendingAutoPlaySong = bestMatch
                    }
                }
            }
        } else {
            val sharedContents = ShareIntentParser.parse(this, intent)
            val initialContent: SharedContent? = sharedContents.firstOrNull()
            if (initialContent != null) {
                viewModel.processSharedContent(initialContent)
            }
        }
    }

    companion object {
        const val EXTRA_IDENTIFIED_TITLE = "extra_identified_title"
        const val EXTRA_IDENTIFIED_ARTIST = "extra_identified_artist"
        const val EXTRA_IDENTIFIED_ALBUM = "extra_identified_album"
        const val EXTRA_IDENTIFIED_ART_URL = "extra_identified_art_url"
        const val EXTRA_AUTO_PLAY = "extra_auto_play"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
