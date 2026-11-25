package com.valora.icebeats.recognition

import java.io.File

data class AudioSource(
    val file: File,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int
)
