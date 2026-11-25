package com.valora.icebeats.lrclib.models

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class Track(
    val id: Int? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

internal fun List<Track>.bestMatchingFor(duration: Int) = firstOrNull { track ->
    track.duration?.let { abs(it.toInt() - duration) <= 2 } ?: false
}
