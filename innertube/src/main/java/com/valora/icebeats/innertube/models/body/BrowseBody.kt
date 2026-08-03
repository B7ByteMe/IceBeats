package com.valora.icebeats.innertube.models.body

import com.valora.icebeats.innertube.models.Context
import com.valora.icebeats.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)
