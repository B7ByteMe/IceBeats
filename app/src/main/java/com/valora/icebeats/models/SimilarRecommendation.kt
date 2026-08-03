package com.valora.icebeats.models

import com.valora.icebeats.innertube.models.YTItem
import com.valora.icebeats.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
