package com.valora.icebeats.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.innertube.models.PlaylistItem
import com.valora.icebeats.innertube.models.SongItem
import com.valora.icebeats.innertube.models.WatchEndpoint
import com.valora.icebeats.innertube.models.YTItem
import com.valora.icebeats.innertube.pages.ExplorePage
import com.valora.icebeats.innertube.pages.HomePage
import com.valora.icebeats.innertube.utils.completedLibraryPage
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.db.entities.Album
import com.valora.icebeats.db.entities.Artist
import com.valora.icebeats.db.entities.LocalItem
import com.valora.icebeats.db.entities.Playlist
import com.valora.icebeats.db.entities.Song
import com.valora.icebeats.models.SimilarRecommendation
import com.valora.icebeats.models.toMediaMetadata
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.get
import com.valora.icebeats.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val recentActivity = MutableStateFlow<List<YTItem>?>(null)
    val recentPlaylistsDb = MutableStateFlow<List<Playlist>?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    private var loadJob: Job? = null
    private var accountFingerprint: Int? = null

    private fun mapToSong(item: SongItem): Song {
        return Song(
            song = item.toMediaMetadata().toSongEntity(),
            artists = item.artists.map { a ->
                com.valora.icebeats.db.entities.ArtistEntity(id = a.id ?: "", name = a.name)
            },
            album = item.album?.let { a ->
                com.valora.icebeats.db.entities.AlbumEntity(id = a.id, title = a.name, songCount = 0, duration = 0)
            }
        )
    }

    private suspend fun load() {
        isLoading.value = true

        val musicProvider = context.dataStore.get(com.valora.icebeats.constants.MusicProviderKey, "YT")
        val isJioSaavn = musicProvider == "JIOSAAVN"

        if (isJioSaavn) {
            com.valora.icebeats.jiosaavn.JioSaavnApi.getTrendingSongs().onSuccess { songs ->
                homePage.value = HomePage(
                    chips = null,
                    sections = listOf(
                        HomePage.Section(
                            title = "Trending Songs",
                            label = "JioSaavn",
                            thumbnail = null,
                            endpoint = null,
                            items = songs
                        )
                    )
                )
                if (quickPicks.value.isNullOrEmpty()) {
                    quickPicks.value = songs.filterIsInstance<SongItem>().map(::mapToSong).take(20)
                }
            }.onFailure {
                reportException(it)
            }
            explorePage.value = ExplorePage(emptyList(), emptyList())
        }

        supervisorScope {
            // 1. Quick Picks snapshot
            launch(Dispatchers.IO) {
                if (isJioSaavn) return@launch
                val qpList = runCatching { database.quickPicks().first() }.getOrDefault(emptyList()).filter { !it.id.startsWith("JS:") }
                val rawPicks = if (qpList.isNotEmpty()) {
                    qpList
                } else {
                    runCatching { database.recentSongs(limit = 60).first() }.getOrDefault(emptyList()).filter { !it.id.startsWith("JS:") }
                }
                if (rawPicks.isNotEmpty()) {
                    quickPicks.value = rawPicks.distinctBy { it.id }.shuffled().take(20)
                }
            }

            // 2. Keep Listening snapshot
            launch(Dispatchers.IO) {
                val songs = runCatching { database.recentSongs(limit = 50, offset = 0).first() }.getOrDefault(emptyList())
                    .filter { if (isJioSaavn) it.id.startsWith("JS:") else !it.id.startsWith("JS:") }
                    .distinctBy { it.id }.shuffled().take(10)
                val albums = runCatching { database.recentAlbums(limit = 50, offset = 0).first() }.getOrDefault(emptyList())
                    .filter { it.album.thumbnailUrl != null && (if (isJioSaavn) it.id.startsWith("JS:") else !it.id.startsWith("JS:")) }
                    .distinctBy { it.id }.shuffled().take(5)
                val artists = runCatching { database.recentArtists(limit = 50, offset = 0).first() }.getOrDefault(emptyList())
                    .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null && (if (isJioSaavn) it.id.startsWith("JS:") else !it.id.startsWith("JS:")) }
                    .distinctBy { it.id }.shuffled().take(5)
                keepListening.value = (songs + albums + artists).shuffled().takeIf { it.isNotEmpty() }
            }

            // 3. Forgotten Favorites snapshot
            launch(Dispatchers.IO) {
                val favs = runCatching { database.forgottenFavorites().first() }.getOrDefault(emptyList())
                    .filter { if (isJioSaavn) it.id.startsWith("JS:") else !it.id.startsWith("JS:") }
                    .distinctBy { it.id }.shuffled().take(20)
                forgottenFavorites.value = favs.takeIf { it.isNotEmpty() }
            }

            // 4. Remote items snapshot (YouTube)
            if (!isJioSaavn) {
                launch(Dispatchers.IO) {
                    if (YouTube.cookie?.contains("SAPISID=") == true) {
                        YouTube.library("FEmusic_liked_playlists").completedLibraryPage().onSuccess {
                            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>().filterNot { it.id == "SE" }
                        }.onFailure { reportException(it) }
                    }
                }

                launch(Dispatchers.IO) {
                    val recentArtistsList = runCatching { database.recentArtists(limit = 10).first() }.getOrDefault(emptyList())
                    val artistRecs = recentArtistsList.filter { it.artist.isYouTubeArtist }.shuffled().take(3).mapNotNull {
                        val items = mutableListOf<YTItem>()
                        YouTube.artist(it.id).onSuccess { page ->
                            items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                            items += page.sections.lastOrNull()?.items.orEmpty()
                        }
                        SimilarRecommendation(title = it, items = items.distinctBy { it.id }.shuffled().take(8)).takeIf { it.items.isNotEmpty() }
                    }
                    val songRecs = runCatching { database.recentSongs(limit = 10).first() }.getOrDefault(emptyList()).filter { !it.id.startsWith("JS:") }.shuffled().take(2).mapNotNull { song ->
                        val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint ?: return@mapNotNull null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                        SimilarRecommendation(
                            title = song,
                            items = (page.songs.shuffled().take(8) +
                                    page.albums.shuffled().take(4) +
                                    page.artists.shuffled().take(4) +
                                    page.playlists.shuffled().take(4)).distinctBy { it.id }.shuffled().take(10)
                        )
                    }
                    similarRecommendations.value = (artistRecs + songRecs).shuffled().takeIf { it.isNotEmpty() }
                }

                launch(Dispatchers.IO) {
                    YouTube.home().onSuccess { page ->
                        homePage.value = page
                        // Fallback: If quickPicks is still empty (new user / empty history),
                        // fill quickPicks with songs from YouTube Home so the hero carousel is always rich!
                        if (quickPicks.value.isNullOrEmpty()) {
                            val ytSongs = page.sections.flatMap { it.items }.filterIsInstance<SongItem>()
                            if (ytSongs.isNotEmpty()) {
                                quickPicks.value = ytSongs.distinctBy { it.id }.shuffled().take(20).map(::mapToSong)
                            }
                        }
                    }.onFailure { reportException(it) }
                }

                launch(Dispatchers.IO) {
                    YouTube.explore().onSuccess { page ->
                        var artists: Set<String> = emptySet()
                        var favouriteArtists: Set<String> = emptySet()
                        runCatching {
                            database.artistsBookmarkedByCreateDateAsc().first().let { list ->
                                artists = list.map(Artist::id).toHashSet()
                                favouriteArtists = list
                                    .filter { it.artist.bookmarkedAt != null }
                                    .map { it.id }
                                    .toHashSet()
                            }
                            explorePage.value = page.copy(
                                newReleaseAlbums = page.newReleaseAlbums
                                    .sortedBy { album ->
                                        if (album.artists.orEmpty().any { it.id in favouriteArtists }) 0
                                        else if (album.artists.orEmpty().any { it.id in artists }) 1
                                        else 2
                                    }
                            )
                        }.onFailure {
                            explorePage.value = page
                        }
                    }.onFailure { reportException(it) }
                }
            }
        }

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty()).filter { it is Song || it is Album }
        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() + homePage.value?.sections?.flatMap { it.items }.orEmpty() + explorePage.value?.newReleaseAlbums.orEmpty()
        isLoading.value = false
    }

    fun refresh() {
        if (isRefreshing.value) return
        loadJob?.cancel()
        isRefreshing.value = true
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                load()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun onAccountChanged(cookie: String) {
        val fingerprint = cookie.hashCode()
        if (accountFingerprint == fingerprint) return
        accountFingerprint = fingerprint
        YouTube.cookie = cookie
        refresh()
    }

    init {
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            load()
        }
    }
}
