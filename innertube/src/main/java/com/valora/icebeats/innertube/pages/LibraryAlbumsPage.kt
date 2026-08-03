package com.valora.icebeats.innertube.pages

import com.valora.icebeats.innertube.models.Album
import com.valora.icebeats.innertube.models.AlbumItem
import com.valora.icebeats.innertube.models.Artist
import com.valora.icebeats.innertube.models.ArtistItem
import com.valora.icebeats.innertube.models.MusicResponsiveListItemRenderer
import com.valora.icebeats.innertube.models.MusicTwoRowItemRenderer
import com.valora.icebeats.innertube.models.PlaylistItem
import com.valora.icebeats.innertube.models.SongItem
import com.valora.icebeats.innertube.models.YTItem
import com.valora.icebeats.innertube.models.oddElements
import com.valora.icebeats.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
