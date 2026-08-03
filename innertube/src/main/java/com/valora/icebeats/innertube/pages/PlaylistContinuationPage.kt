package com.valora.icebeats.innertube.pages

import com.valora.icebeats.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
