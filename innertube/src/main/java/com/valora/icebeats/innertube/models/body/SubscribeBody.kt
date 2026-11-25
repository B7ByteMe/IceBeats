/*
 * AirBeats Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.valora.icebeats.innertube.models.body

import com.valora.icebeats.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: Context,
)
