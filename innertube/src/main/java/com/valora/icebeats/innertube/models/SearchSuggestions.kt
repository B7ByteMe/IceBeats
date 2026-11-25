/*
 * AirBeats Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.valora.icebeats.innertube.models

data class SearchSuggestions(
    val queries: List<String>,
    val recommendedItems: List<YTItem>,
)
