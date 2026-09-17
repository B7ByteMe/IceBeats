package com.valora.icebeats.lyrics

import android.content.Context
import android.util.LruCache
import com.valora.icebeats.constants.PreferredLyricsProvider
import com.valora.icebeats.constants.PreferredLyricsProviderKey
import com.valora.icebeats.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.valora.icebeats.extensions.toEnum
import com.valora.icebeats.models.MediaMetadata
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.get
import com.valora.icebeats.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import androidx.annotation.Keep

import com.valora.icebeats.lyrics.providers.IceBeatsSimpLyricsProvider
import com.valora.icebeats.lyrics.providers.IceBeatsUnisonLyricsProvider
import com.valora.icebeats.lyrics.providers.IceBeatsYouLyLyricsProvider
import com.valora.icebeats.lyrics.providers.IceBeatsMegalobizLyricsProvider
import com.valora.icebeats.lyrics.providers.IceBeatsPaxsenixLyricsProvider
import com.valora.icebeats.lyrics.providers.IceBeatsPortatoLyricsProvider

@Keep
class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val allLyricsProviders =
        listOf(
            LrcLibLyricsProvider,
            IceBeatsSimpLyricsProvider,
            YouTubeSubtitleLyricsProvider,
            KuGouLyricsProvider,
            IceBeatsPaxsenixLyricsProvider,
            IceBeatsUnisonLyricsProvider,
            BetterLyricsProvider,
            IceBeatsPortatoLyricsProvider,
            IceBeatsYouLyLyricsProvider,
            IceBeatsMegalobizLyricsProvider,
            YouTubeLyricsProvider
        )
    private var lyricsProviders = allLyricsProviders
    val preferred =
        context.dataStore.data
            .map {
                it[PreferredLyricsProviderKey].toEnum(PreferredLyricsProvider.LRCLIB)
            }.distinctUntilChanged()
            .map(::setPreferredProvider)

    private fun setPreferredProvider(preferredProvider: PreferredLyricsProvider) {
        val primary = when (preferredProvider) {
            PreferredLyricsProvider.LRCLIB -> LrcLibLyricsProvider
            PreferredLyricsProvider.KUGOU -> KuGouLyricsProvider
            PreferredLyricsProvider.SIMP_MUSIC -> IceBeatsSimpLyricsProvider
            PreferredLyricsProvider.YOUTUBE_SUBTITLES -> YouTubeSubtitleLyricsProvider
            PreferredLyricsProvider.PAXSENIX -> IceBeatsPaxsenixLyricsProvider
            PreferredLyricsProvider.UNISON -> IceBeatsUnisonLyricsProvider
            PreferredLyricsProvider.BETTER_LYRICS -> BetterLyricsProvider
            PreferredLyricsProvider.PORTATO -> IceBeatsPortatoLyricsProvider
            PreferredLyricsProvider.YOULY -> IceBeatsYouLyLyricsProvider
            PreferredLyricsProvider.MEGALOBIZ -> IceBeatsMegalobizLyricsProvider
            PreferredLyricsProvider.YOUTUBE_MUSIC -> YouTubeLyricsProvider
        }
        lyricsProviders = listOf(primary) + allLyricsProviders.filter { it !== primary }
    }

    private fun refreshProviderOrder() {
        setPreferredProvider(
            context.dataStore[PreferredLyricsProviderKey]
                .toEnum(PreferredLyricsProvider.LRCLIB),
        )
    }
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    suspend fun getLyrics(mediaMetadata: MediaMetadata): String {
        refreshProviderOrder()
        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return cached.lyrics
        }
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider
                    .getLyrics(
                        mediaMetadata.id,
                        mediaMetadata.title,
                        mediaMetadata.artists.joinToString { it.name },
                        mediaMetadata.duration,
                    ).onSuccess { lyrics ->
                        return lyrics
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
        return LYRICS_NOT_FOUND
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        callback: (LyricsResult) -> Unit,
    ) {
        refreshProviderOrder()
        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }
        val allResult = mutableListOf<LyricsResult>()
        lyricsProviders.forEach { provider ->
            if (provider.isEnabled(context)) {
                provider.getAllLyrics(mediaId, songTitle, songArtists, duration) { lyrics ->
                    val result = LyricsResult(provider.name, lyrics)
                    allResult += result
                    callback(result)
                }
            }
        }
        cache.put(cacheKey, allResult)
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
