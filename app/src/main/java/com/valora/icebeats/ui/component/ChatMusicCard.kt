package com.valora.icebeats.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valora.icebeats.LocalPlayerConnection
import com.valora.icebeats.R
import com.valora.icebeats.models.MediaMetadata
import com.valora.icebeats.playback.queues.YouTubeQueue
import com.valora.icebeats.supabase.ChatSharedMedia
import com.valora.icebeats.utils.makeTimeString

/**
 * Kartu Musik Interaktif (Music Card) untuk fitur berbagi lagu melalui chat IceBeats.
 * Menampilkan thumbnail, judul lagu, artis, serta tombol Putar/Jeda langsung.
 */
@Composable
fun ChatMusicCard(
    media: ChatSharedMedia,
    modifier: Modifier = Modifier,
    isFromMe: Boolean = false
) {
    val playerConnection = LocalPlayerConnection.current
    val currentMedia by playerConnection?.mediaMetadata?.collectAsState() ?: androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(null)
    }
    val isPlaying by playerConnection?.isPlaying?.collectAsState() ?: androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    val isThisSongPlaying = isPlaying && currentMedia?.id == media.songId

    val cardBg = if (isFromMe) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .clickable {
                if (isThisSongPlaying) {
                    playerConnection?.player?.pause()
                } else if (currentMedia?.id == media.songId) {
                    playerConnection?.player?.play()
                } else {
                    val metadata = MediaMetadata(
                        id = media.songId,
                        title = media.title,
                        artists = listOf(MediaMetadata.Artist(id = null, name = media.artistName)),
                        duration = media.duration,
                        thumbnailUrl = media.thumbnailUrl,
                        album = media.albumName?.let { MediaMetadata.Album(id = "", title = it) }
                    )
                    playerConnection?.playQueue(YouTubeQueue.radio(metadata))
                }
            }
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover Seni Musik
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (!media.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = media.thumbnailUrl,
                        contentDescription = media.title,
                        modifier = Modifier.size(54.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata Lagu
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = media.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (media.duration > 0) {
                    Text(
                        text = makeTimeString(media.duration * 1000L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Tombol Play / Pause Interaktif
            IconButton(
                onClick = {
                    if (isThisSongPlaying) {
                        playerConnection?.player?.pause()
                    } else if (currentMedia?.id == media.songId) {
                        playerConnection?.player?.play()
                    } else {
                        val metadata = MediaMetadata(
                            id = media.songId,
                            title = media.title,
                            artists = listOf(MediaMetadata.Artist(id = null, name = media.artistName)),
                            duration = media.duration,
                            thumbnailUrl = media.thumbnailUrl,
                            album = media.albumName?.let { MediaMetadata.Album(id = "", title = it) }
                        )
                        playerConnection?.playQueue(YouTubeQueue.radio(metadata))
                    }
                },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    painter = painterResource(
                        if (isThisSongPlaying) R.drawable.pause else R.drawable.play
                    ),
                    contentDescription = if (isThisSongPlaying) "Jeda" else "Putar",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
