package com.valora.icebeats.ui.sharedmusic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valora.icebeats.extensions.toMediaItem
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.innertube.models.SongItem
import com.valora.icebeats.innertube.models.WatchEndpoint
import com.valora.icebeats.models.MediaMetadata
import com.valora.icebeats.models.toMediaMetadata
import com.valora.icebeats.playback.PlayerConnection
import com.valora.icebeats.playback.queues.ListQueue
import com.valora.icebeats.playback.queues.YouTubeQueue
import com.valora.icebeats.providers.ProviderSearchManager
import com.valora.icebeats.providers.ProviderSong
import com.valora.icebeats.share.SharedContent
import com.valora.icebeats.songs.IdentifiedSong
import com.valora.icebeats.usecases.IdentificationOutcome
import com.valora.icebeats.usecases.IdentificationStep
import com.valora.icebeats.usecases.IdentifySharedMusicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SharedMusicViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val identifyUseCase = IdentifySharedMusicUseCase(context)

    private val _uiState = MutableStateFlow<SharedMusicUiState>(SharedMusicUiState.Idle)
    val uiState: StateFlow<SharedMusicUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null

    fun processSharedContent(content: SharedContent) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.value = SharedMusicUiState.Idle

            identifyUseCase.execute(content).collect { (step, outcome) ->
                if (outcome == null) {
                    _uiState.value = SharedMusicUiState.Processing(step)
                } else {
                    _uiState.value = when (outcome) {
                        is IdentificationOutcome.Success -> SharedMusicUiState.Success(
                            identifiedSong = outcome.song,
                            airBeatsMatch = outcome.airBeatsMatch,
                            candidates = outcome.candidates
                        )
                        is IdentificationOutcome.NoMusicFound -> SharedMusicUiState.NoMusicFound
                        is IdentificationOutcome.NoAudio -> SharedMusicUiState.NoAudio
                        is IdentificationOutcome.UnsupportedMedia -> SharedMusicUiState.UnsupportedMedia(outcome.reason)
                        is IdentificationOutcome.IsUrlOnly -> SharedMusicUiState.UrlShared(outcome.url)
                        is IdentificationOutcome.NetworkError -> SharedMusicUiState.NetworkError
                        is IdentificationOutcome.Error -> SharedMusicUiState.Error(outcome.message)
                    }
                }
            }
        }
    }

    fun playSong(providerSong: ProviderSong, playerConnection: PlayerConnection) {
        if (providerSong.id.isNotBlank()) {
            if (providerSong.provider == "JIOSAAVN") {
                playerConnection.playQueue(
                    ListQueue(
                        title = providerSong.title,
                        items = listOf(providerSong.mediaMetadata.toMediaItem())
                    )
                )
            } else {
                playerConnection.playQueue(
                    YouTubeQueue(
                        WatchEndpoint(videoId = providerSong.id),
                        providerSong.mediaMetadata
                    )
                )
            }
        } else {
            // ID was empty - search YouTube on the fly and play top result
            viewModelScope.launch(Dispatchers.IO) {
                val query = "${providerSong.title} ${providerSong.artists.joinToString(" ")}".trim()
                val ytResult = runCatching { YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull() }.getOrNull()
                val topSong = ytResult?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                    ?: runCatching { YouTube.searchSummary(query).getOrNull() }?.getOrNull()?.summaries?.flatMap { it.items }?.filterIsInstance<SongItem>()?.firstOrNull()

                withContext(Dispatchers.Main) {
                    if (topSong != null) {
                        playerConnection.playQueue(
                            YouTubeQueue(
                                WatchEndpoint(videoId = topSong.id),
                                topSong.toMediaMetadata()
                            )
                        )
                    }
                }
            }
        }
    }

    fun loadIdentifiedSong(
        title: String,
        artist: String?,
        album: String?,
        albumArtUrl: String?,
        durationMs: Long? = null,
        onSuccess: ((ProviderSong) -> Unit)? = null
    ) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _uiState.value = SharedMusicUiState.Processing(IdentificationStep.SEARCHING_AIRBEATS)
            val song = IdentifiedSong(
                title = title,
                artist = artist,
                album = album,
                albumArtUrl = albumArtUrl,
                durationMs = durationMs
            )

            val providerSearchManager = ProviderSearchManager(context)
            val candidates = runCatching { providerSearchManager.searchProviders(song) }.getOrDefault(emptyList()).toMutableList()

            // Guaranteed playable candidate fallback if providers returned no items
            if (candidates.isEmpty()) {
                val fallbackMetadata = MediaMetadata(
                    id = "",
                    title = song.title,
                    artists = listOf(MediaMetadata.Artist(id = null, name = song.artist ?: "")),
                    album = song.album?.let { MediaMetadata.Album(id = "", title = it) },
                    thumbnailUrl = song.albumArtUrl,
                    duration = 0
                )
                candidates.add(
                    ProviderSong(
                        id = "",
                        title = song.title,
                        artists = listOfNotNull(song.artist?.ifBlank { null } ?: "Unknown Artist"),
                        duration = -1,
                        thumbnailUrl = song.albumArtUrl,
                        mediaMetadata = fallbackMetadata,
                        provider = "YT"
                    )
                )
            }

            val bestMatch = candidates.firstOrNull()
            _uiState.value = SharedMusicUiState.Success(
                identifiedSong = song,
                airBeatsMatch = bestMatch,
                candidates = candidates
            )

            if (bestMatch != null && onSuccess != null) {
                onSuccess(bestMatch)
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        _uiState.value = SharedMusicUiState.Idle
    }
}
