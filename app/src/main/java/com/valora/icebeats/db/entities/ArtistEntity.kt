package com.valora.icebeats.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.valora.icebeats.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "artist")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val channelId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val songCount: Int = 0,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null
) {

    val isYouTubeArtist: Boolean
        get() = id.startsWith("UC") || id.startsWith("FEmusic_library_privately_owned_artist")

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith("FEmusic_library_privately_owned_artist")

    val isLocalArtist: Boolean
        get() = id.startsWith("LA")

    val isScrobbleArtist: Boolean
        get() = id.startsWith("artist_")

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now(),
    )

    fun toggleLike() = localToggleLike().also { updated ->
        val isSubscribed = updated.bookmarkedAt != null
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val targetChannelId = channelId ?: YouTube.getChannelId(id)
                if (!targetChannelId.isNullOrBlank()) {
                    com.valora.icebeats.data.artist.ArtistInnertubeClient.setSubscribed(targetChannelId, isSubscribed)
                }
            }.onFailure {
                runCatching {
                    val targetChannelId = channelId ?: YouTube.getChannelId(id)
                    if (!targetChannelId.isNullOrBlank()) {
                        YouTube.subscribeChannel(targetChannelId, isSubscribed)
                    }
                }
            }
        }
    }

    companion object {
        fun generateArtistId() = "LA" + (1..8).map { (('a'..'z') + ('A'..'Z')).random() }.joinToString("")
    }
}
