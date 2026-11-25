package com.valora.icebeats.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.innertube.models.PlaylistItem
import com.valora.icebeats.innertube.models.SongItem
import com.valora.icebeats.innertube.models.Artist
import com.valora.icebeats.innertube.models.Album
import com.valora.icebeats.spotify.Spotify
import com.valora.icebeats.spotify.models.SpotifyPlaylistTrack
import com.valora.icebeats.innertube.utils.completedPlaylistPage
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    database: MusicDatabase
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var continuation: String? = null
        private set

    init {
        load(initial = true)
    }

    fun refresh() {
        load(initial = false)
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return

        continuation?.let {
            viewModelScope.launch(Dispatchers.IO) {
                _isLoadingMore.value = true
                if (playlistId.startsWith("sp:")) {
                    Spotify.playlistTracks(playlistId.removePrefix("sp:"), offset = playlistSongs.value.size)
                        .onSuccess { paging ->
                            val currentSongs = playlistSongs.value.toMutableList()
                            currentSongs.addAll(paging.items.mapNotNull { it.toSongItem() })
                            playlistSongs.value = currentSongs.distinctBy { it.id }
                            
                            continuation = if (playlistSongs.value.size < paging.total) "sp:next" else null
                            _isLoadingMore.value = false
                        }.onFailure { throwable ->
                            _isLoadingMore.value = false
                            reportException(throwable)
                        }
                } else {
                    YouTube.playlistContinuation(it)
                        .onSuccess { playlistContinuationPage ->
                            val currentSongs = playlistSongs.value.toMutableList()
                            currentSongs.addAll(playlistContinuationPage.songs)
                            playlistSongs.value = currentSongs.distinctBy { it.id }
                            continuation = playlistContinuationPage.continuation
                            _isLoadingMore.value = false
                        }.onFailure { throwable ->
                            _isLoadingMore.value = false
                            reportException(throwable)
                        }
                }
            }
        }
    }

    fun retry() {
        load(initial = true)
    }

    private fun load(initial: Boolean) {
        if (initial) {
            if (_isLoading.value && playlist.value != null) return
        } else {
            if (_isRefreshing.value || _isLoading.value) return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (initial) {
                _isLoading.value = true
            } else {
                _isRefreshing.value = true
            }

            _error.value = null

            if (playlistId.startsWith("sp:")) {
                val spId = playlistId.removePrefix("sp:")
                Spotify.playlist(spId).onSuccess { spPlaylist ->
                    playlist.value = PlaylistItem(
                        id = playlistId,
                        title = spPlaylist.name,
                        author = Artist(name = spPlaylist.owner?.displayName ?: "Spotify", id = null),
                        songCountText = spPlaylist.tracks?.total?.toString() ?: "0",
                        thumbnail = spPlaylist.images.firstOrNull()?.url ?: "",
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null
                    )
                    
                    Spotify.playlistTracks(spId).onSuccess { paging ->
                        playlistSongs.value = paging.items.mapNotNull { it.toSongItem() }.distinctBy { it.id }
                        continuation = if (playlistSongs.value.size < paging.total) "sp:next" else null
                    }.onFailure { throwable ->
                        _error.value = throwable.message ?: "Failed to load Spotify tracks"
                        reportException(throwable)
                    }
                }.onFailure { throwable ->
                    _error.value = throwable.message ?: "Failed to load Spotify playlist"
                    reportException(throwable)
                }
            } else {
                YouTube
                    .playlist(playlistId)
                    .completedPlaylistPage()
                    .onSuccess { playlistPage ->
                        playlist.value = playlistPage.playlist
                        playlistSongs.value = playlistPage.songs.distinctBy { it.id }
                        continuation = playlistPage.songsContinuation
                    }.onFailure { throwable ->
                        _error.value = throwable.message ?: "Failed to load playlist"
                        reportException(throwable)
                    }
            }

            if (initial) {
                _isLoading.value = false
            } else {
                _isRefreshing.value = false
            }
        }
    }
    
    private fun SpotifyPlaylistTrack.toSongItem(): SongItem? {
        val t = this.track ?: return null
        val a = t.album
        return SongItem(
            id = "sp:${t.id}",
            title = t.name,
            artists = t.artists.map { Artist(name = it.name, id = it.id) },
            album = if (a != null) Album(name = a.name, id = a.id ?: "") else null,
            duration = (t.durationMs / 1000).toInt(),
            thumbnail = t.album?.images?.firstOrNull()?.url ?: "",
            explicit = t.explicit
        )
    }
}
