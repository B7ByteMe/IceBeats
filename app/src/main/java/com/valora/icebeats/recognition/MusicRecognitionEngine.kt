package com.valora.icebeats.recognition

interface MusicRecognitionEngine {
    val providerName: String
    suspend fun recognize(audio: AudioSource): RecognitionResult
}
