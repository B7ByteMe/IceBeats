/*
 * AirBeats Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.valora.icebeats.innertube.pages

import com.valora.icebeats.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
