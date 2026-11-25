package com.valora.icebeats.recognition

import android.content.Context
import android.util.Log

class CompositeRecognitionEngine(
    private val context: Context,
    private val shazamEngine: ShazamRecognitionEngine = ShazamRecognitionEngine(context),
    private val auddEngine: AudDRecognitionEngine = AudDRecognitionEngine(context)
) : MusicRecognitionEngine {

    companion object {
        private const val TAG = "CompositeRecEngine"
    }

    override val providerName: String = "Composite (Shazam/AudD)"

    override suspend fun recognize(audioSource: AudioSource): RecognitionResult {
        try {
            val result = shazamEngine.recognize(audioSource)
            if (result.success && !result.title.isNullOrBlank()) {
                Log.d(TAG, "Successfully recognized track with Shazam: ${result.title} by ${result.artist}")
                return result
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Shazam recognition failed or threw exception, falling back", t)
        }

        // Fallback to AudD
        Log.d(TAG, "Attempting AudD recognition fallback")
        return try {
            auddEngine.recognize(audioSource)
        } catch (e: Exception) {
            Log.e(TAG, "AudD fallback recognition failed", e)
            RecognitionResult(success = false)
        }
    }
}
