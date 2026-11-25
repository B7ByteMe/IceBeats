package com.valora.icebeats.providers

import android.content.Context
import android.util.Log
import com.valora.icebeats.constants.EnableJioSaavnKey
import com.valora.icebeats.constants.HideExplicitKey
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.innertube.models.SongItem
import com.valora.icebeats.innertube.models.filterExplicit
import com.valora.icebeats.jiosaavn.JioSaavnApi
import com.valora.icebeats.models.toMediaMetadata
import com.valora.icebeats.songs.IdentifiedSong
import com.valora.icebeats.songs.SongMatchEngine
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.getSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProviderSearchManager(private val context: Context) {

    companion object {
        private const val TAG = "ProviderSearchManager"
    }

    suspend fun searchProviders(song: IdentifiedSong): List<ProviderSong> = withContext(Dispatchers.IO) {
        val rawCandidates = mutableListOf<ProviderSong>()
        val cleanTitle = song.title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
        val artistsList = song.artist?.split(",", "&", "feat.", "ft.", "/")
            ?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val primaryArtist = artistsList.firstOrNull().orEmpty()

        val queries = linkedSetOf(
            "${song.title} ${song.artist.orEmpty()}".trim(),
            "$cleanTitle ${song.artist.orEmpty()}".trim(),
            "$cleanTitle $primaryArtist".trim(),
            song.title.trim()
        ).filter { it.isNotBlank() }

        val hideExplicit = context.dataStore.getSuspend(HideExplicitKey, false)
        val enableJioSaavn = context.dataStore.getSuspend(EnableJioSaavnKey, false)

        // 1. Search YouTube Music with progressive queries
        for (q in queries) {
            if (rawCandidates.size >= 5) break
            try {
                val ytResult = YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ytResult?.items
                    ?.filterIsInstance<SongItem>()
                    ?.filterExplicit(hideExplicit)
                    ?.forEach { item ->
                        if (rawCandidates.none { it.id == item.id }) {
                            rawCandidates.add(
                                ProviderSong(
                                    id = item.id,
                                    title = item.title,
                                    artists = item.artists.map { it.name },
                                    duration = item.duration ?: -1,
                                    thumbnailUrl = item.thumbnail,
                                    mediaMetadata = item.toMediaMetadata(),
                                    provider = "YT"
                                )
                            )
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube search failed for query: $q", e)
            }
        }

        // 2. If no song items found with FILTER_SONG, try general YouTube search summary
        if (rawCandidates.isEmpty()) {
            for (q in queries.take(2)) {
                try {
                    val summaryResult = YouTube.searchSummary(q).getOrNull()
                    summaryResult?.summaries
                        ?.flatMap { it.items }
                        ?.filterIsInstance<SongItem>()
                        ?.filterExplicit(hideExplicit)
                        ?.forEach { item ->
                            if (rawCandidates.none { it.id == item.id }) {
                                rawCandidates.add(
                                    ProviderSong(
                                        id = item.id,
                                        title = item.title,
                                        artists = item.artists.map { it.name },
                                        duration = item.duration ?: -1,
                                        thumbnailUrl = item.thumbnail,
                                        mediaMetadata = item.toMediaMetadata(),
                                        provider = "YT"
                                    )
                                )
                            }
                        }
                } catch (e: Exception) {
                    Log.w(TAG, "YouTube search summary failed for query: $q", e)
                }
            }
        }

        // 3. Search JioSaavn if enabled or if YT returned few results
        if (enableJioSaavn || rawCandidates.size < 3) {
            for (q in queries.take(2)) {
                try {
                    val jioResult = JioSaavnApi.searchSongs(q).getOrNull()
                    jioResult?.forEach { item ->
                        if (rawCandidates.none { it.id == item.id }) {
                            rawCandidates.add(
                                ProviderSong(
                                    id = item.id,
                                    title = item.title,
                                    artists = item.artists.map { it.name },
                                    duration = item.duration ?: -1,
                                    thumbnailUrl = item.thumbnail,
                                    mediaMetadata = item.toMediaMetadata(),
                                    provider = "JIOSAAVN"
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "JioSaavn search failed for query: $q", e)
                }
            }
        }

        // 4. Rank results with SongMatchEngine
        val ranked = SongMatchEngine.rankCandidates(song, rawCandidates)
        if (ranked.isNotEmpty()) {
            ranked
        } else if (rawCandidates.isNotEmpty()) {
            rawCandidates
        } else {
            emptyList()
        }
    }
}
