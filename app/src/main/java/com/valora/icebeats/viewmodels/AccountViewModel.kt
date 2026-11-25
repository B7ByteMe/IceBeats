package com.valora.icebeats.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valora.icebeats.constants.SpotifyCookieKey
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.innertube.models.AlbumItem
import com.valora.icebeats.innertube.models.ArtistItem
import com.valora.icebeats.innertube.models.PlaylistItem
import com.valora.icebeats.innertube.utils.completedLibraryPage
import com.valora.icebeats.spotify.Spotify
import com.valora.icebeats.spotify.SpotifyAuth
import com.valora.icebeats.spotify.models.SpotifyHomeFeed
import com.valora.icebeats.spotify.models.SpotifyPlaylist
import com.valora.icebeats.spotify.models.SpotifyUser
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.getSuspend
import com.valora.icebeats.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase
) : ViewModel() {
    val playlists = MutableStateFlow<List<PlaylistItem>?>(null)
    val albums = MutableStateFlow<List<AlbumItem>?>(null)
    val artists = MutableStateFlow<List<ArtistItem>?>(null)

    val spotifyUser = MutableStateFlow<SpotifyUser?>(null)
    val spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylist>?>(null)
    val spotifyFeed = MutableStateFlow<SpotifyHomeFeed?>(null)
    val isSpotifyLoading = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            loadYouTubeLibrary()
        }
    }

    fun refreshYouTubeLibrary() {
        viewModelScope.launch { loadYouTubeLibrary() }
    }

    private suspend fun loadYouTubeLibrary() = withContext(Dispatchers.IO) {
        val cookie = context.dataStore.getSuspend(com.valora.icebeats.constants.InnerTubeCookieKey)
            .orEmpty()
        YouTube.cookie = cookie
        val playlistResult = YouTube.library("FEmusic_liked_playlists").completedLibraryPage()
        playlistResult.onSuccess { page ->
            playlists.value = page.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id in setOf("LM", "SE", "NE", "SS", "SR", "LL", "WL") }
        }.onFailure { reportException(it) }

        YouTube.library("FEmusic_liked_albums").completedLibraryPage().onSuccess {
            albums.value = it.items.filterIsInstance<AlbumItem>()
        }.onFailure { reportException(it) }

        YouTube.library("FEmusic_library_corpus_artists").completedLibraryPage().onSuccess {
            artists.value = it.items.filterIsInstance<ArtistItem>()
        }.onFailure { reportException(it) }

        if (playlistResult.isSuccess) {
            runCatching { com.valora.icebeats.utils.SyncUtils(context, database).syncSavedPlaylists() }
        }
    }

    fun loadSpotifyData() {
        if (isSpotifyLoading.value || spotifyUser.value != null) return
        isSpotifyLoading.value = true
        viewModelScope.launch {
            try {
                val spDc = context.dataStore.getSuspend(SpotifyCookieKey)
                if (spDc != null) {
                    val tokenResult = SpotifyAuth.fetchAccessToken(spDc)
                    tokenResult.onSuccess { token ->
                        Spotify.accessToken = token.accessToken
                        
                        Spotify.me().onSuccess { user ->
                            spotifyUser.value = user
                        }.onFailure { reportException(it) }

                        Spotify.myPlaylists(limit = 20).onSuccess { paging ->
                            spotifyPlaylists.value = paging.items
                        }.onFailure { reportException(it) }

                        Spotify.home().onSuccess { feed ->
                            spotifyFeed.value = feed
                        }.onFailure { reportException(it) }
                    }.onFailure {
                        reportException(it)
                    }
                }
            } catch (e: Exception) {
                reportException(e)
            } finally {
                isSpotifyLoading.value = false
            }
        }
    }
}
