package com.valora.icebeats.models

import com.valora.icebeats.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
