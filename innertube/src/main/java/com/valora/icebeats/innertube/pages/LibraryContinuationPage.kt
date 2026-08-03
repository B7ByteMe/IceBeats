package com.valora.icebeats.innertube.pages

import com.valora.icebeats.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
