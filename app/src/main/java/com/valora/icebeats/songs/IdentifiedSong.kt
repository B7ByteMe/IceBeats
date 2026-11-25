package com.valora.icebeats.songs

data class IdentifiedSong(
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtUrl: String?,
    val durationMs: Long?,
    val identifiers: List<String> = emptyList()
)
