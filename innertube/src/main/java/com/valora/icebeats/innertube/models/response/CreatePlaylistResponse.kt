/*
 * AirBeats Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.valora.icebeats.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class CreatePlaylistResponse(
    val playlistId: String
)
