package com.valora.icebeats.innertube.models.body

import com.valora.icebeats.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: Context,
)
