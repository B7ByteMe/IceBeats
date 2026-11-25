package com.valora.icebeats.recognition

data class RecognitionResult(
    val success: Boolean,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtUrl: String? = null,
    val durationMs: Long? = null,
    val confidence: Float? = null,
    val externalId: String? = null,
    val provider: String? = null,
    val rawMetadata: Map<String, String> = emptyMap()
)
