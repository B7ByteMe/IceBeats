package com.valora.icebeats.innertube.models.body

import com.valora.icebeats.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String
)
