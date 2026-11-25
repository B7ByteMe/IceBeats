package com.valora.icebeats.recognition

import android.content.Context
import android.util.Log
import com.alexmercerind.audire.native.ShazamSignature
import com.valora.icebeats.utils.GlobalLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

class ShazamRecognitionEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MusicRecognitionEngine {

    companion object {
        private const val TAG = "ShazamEngine"
        private const val TARGET_SAMPLE_RATE = 16000
    }

    override val providerName: String = "Shazam"

    override suspend fun recognize(audioSource: AudioSource): RecognitionResult = withContext(Dispatchers.IO) {
        val file = audioSource.file
        if (!file.exists() || file.length() <= 44) {
            Log.e(TAG, "Audio file does not exist or has invalid size: ${file.length()}")
            GlobalLog.append(Log.ERROR, TAG, "Audio file invalid or too small: ${file.length()} bytes")
            return@withContext RecognitionResult(success = false)
        }

        var inSampleRate = if (audioSource.sampleRate > 0) audioSource.sampleRate else 44100
        var inChannels = if (audioSource.channels > 0) audioSource.channels else 2

        val pcmBytes = try {
            file.inputStream().use { input ->
                val header = ByteArray(44)
                val readCount = input.read(header)
                if (readCount == 44 && header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte()) {
                    val channelsFromHdr = (header[22].toInt() and 0xFF) or ((header[23].toInt() and 0xFF) shl 8)
                    val rateFromHdr = (header[24].toInt() and 0xFF) or
                            ((header[25].toInt() and 0xFF) shl 8) or
                            ((header[26].toInt() and 0xFF) shl 16) or
                            ((header[27].toInt() and 0xFF) shl 24)
                    if (channelsFromHdr in 1..8) inChannels = channelsFromHdr
                    if (rateFromHdr in 8000..192000) inSampleRate = rateFromHdr
                }
                input.readBytes()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read PCM bytes from file", e)
            GlobalLog.append(Log.ERROR, TAG, "Failed to read PCM bytes: ${e.message}")
            return@withContext RecognitionResult(success = false)
        }

        val converted = convertTo16kMono(
            pcmBytes = pcmBytes,
            inSampleRate = inSampleRate,
            inChannels = inChannels
        )

        if (converted.isEmpty()) {
            Log.e(TAG, "Converted sample buffer is empty")
            GlobalLog.append(Log.ERROR, TAG, "Shazam sample buffer is empty")
            return@withContext RecognitionResult(success = false)
        }

        // Optimal Shazam fingerprint window is 10-12s (192,000 samples). Cap to avoid signature distortion.
        val maxSamples = 12 * TARGET_SAMPLE_RATE
        val samples16kMono = if (converted.size > maxSamples) {
            converted.copyOf(maxSamples)
        } else {
            converted
        }

        val samplems = (samples16kMono.size * 1000L / TARGET_SAMPLE_RATE).toInt()
        GlobalLog.append(Log.INFO, TAG, "Shazam: generating signature for ${samples16kMono.size} samples (${samplems}ms)...")

        val signatureUri = try {
            ShazamSignature.create(samples16kMono)
        } catch (t: Throwable) {
            Log.e(TAG, "Native ShazamSignature.create call failed", t)
            GlobalLog.append(Log.ERROR, TAG, "Native ShazamSignature failed: ${t.message}")
            return@withContext RecognitionResult(success = false)
        }

        if (signatureUri.isBlank()) {
            Log.e(TAG, "Native signature uri returned blank")
            GlobalLog.append(Log.ERROR, TAG, "Native Shazam signature returned blank")
            return@withContext RecognitionResult(success = false)
        }

        GlobalLog.append(Log.INFO, TAG, "Shazam: signature generated successfully (uri length=${signatureUri.length})")

        try {
            val timestamp = System.currentTimeMillis()
            val uuid1 = UUID.randomUUID().toString().uppercase(Locale.US)
            val uuid2 = UUID.randomUUID().toString().lowercase(Locale.US)

            val endpoint = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$uuid1/$uuid2" +
                    "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"

            val jsonBody = JSONObject().apply {
                put("geolocation", JSONObject().apply {
                    put("altitude", 300)
                    put("latitude", 45.0)
                    put("longitude", 2.0)
                })
                put("signature", JSONObject().apply {
                    put("samplems", samplems)
                    put("timestamp", timestamp)
                    put("uri", signatureUri)
                })
                put("timestamp", timestamp)
                put("timezone", TimeZone.getDefault().id.ifBlank { "Europe/London" })
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("User-Agent", "Shazam/13.15.0 (Android; Android 14; Pixel 7)")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Shazam API HTTP ${response.code}: $body")
                    GlobalLog.append(Log.ERROR, TAG, "Shazam API HTTP ${response.code}: $body")
                    return@withContext RecognitionResult(success = false)
                }

                val json = JSONObject(body)
                val track = json.optJSONObject("track")
                if (track == null) {
                    GlobalLog.append(Log.INFO, TAG, "Shazam API: No track matched in database")
                    return@withContext RecognitionResult(
                        success = false,
                        rawMetadata = mapOf("shazam_response" to body)
                    )
                }

                val title = track.optString("title").takeIf { it.isNotBlank() }
                val artist = track.optString("subtitle").takeIf { it.isNotBlank() }
                GlobalLog.append(Log.INFO, TAG, "Shazam matched: '$title' by '$artist'")

                val images = track.optJSONObject("images")
                val coverArt = images?.optString("coverarthq")?.takeIf { it.isNotBlank() }
                    ?: images?.optString("coverart")?.takeIf { it.isNotBlank() }

                var album: String? = null
                var releaseDate: String? = null
                val sections = track.optJSONArray("sections")
                if (sections != null) {
                    for (i in 0 until sections.length()) {
                        val section = sections.optJSONObject(i)
                        if (section?.optString("type") == "SONG") {
                            val metadata = section.optJSONArray("metadata")
                            if (metadata != null) {
                                for (j in 0 until metadata.length()) {
                                    val item = metadata.optJSONObject(j)
                                    val mTitle = item?.optString("title")
                                    val mText = item?.optString("text")
                                    if (mTitle.equals("Album", ignoreCase = true)) album = mText
                                    if (mTitle.equals("Released", ignoreCase = true)) releaseDate = mText
                                }
                            }
                        }
                    }
                }

                val rawMap = mutableMapOf<String, String>()
                rawMap["provider"] = "Shazam"
                track.optString("key").takeIf { it.isNotBlank() }?.let { rawMap["shazam_key"] = it }

                RecognitionResult(
                    success = !title.isNullOrBlank(),
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtUrl = coverArt,
                    provider = "Shazam",
                    externalId = track.optString("key"),
                    rawMetadata = rawMap
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shazam recognition failed", e)
            GlobalLog.append(Log.ERROR, TAG, "Shazam recognition failed: ${e.message}")
            RecognitionResult(success = false)
        }
    }

    private fun convertTo16kMono(
        pcmBytes: ByteArray,
        inSampleRate: Int,
        inChannels: Int
    ): ShortArray {
        val numInputFrames = pcmBytes.size / (inChannels * 2)
        if (numInputFrames <= 0) return ShortArray(0)

        // 1. Convert byte stream into mono ShortArray at inSampleRate
        val monoInput = ShortArray(numInputFrames)
        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

        if (inChannels == 1) {
            for (i in 0 until numInputFrames) {
                monoInput[i] = bb.short
            }
        } else {
            for (i in 0 until numInputFrames) {
                var sum = 0
                for (ch in 0 until inChannels) {
                    sum += bb.short.toInt()
                }
                monoInput[i] = (sum / inChannels).coerceIn(-32768, 32767).toShort()
            }
        }

        // 2. Resample linearly to 16,000 Hz if necessary
        if (inSampleRate == TARGET_SAMPLE_RATE) {
            return monoInput
        }

        val targetLength = ((numInputFrames.toDouble() * TARGET_SAMPLE_RATE) / inSampleRate).toInt()
        if (targetLength <= 0) return ShortArray(0)

        val output = ShortArray(targetLength)
        val ratio = inSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()

        for (i in 0 until targetLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val frac = srcPos - srcIndex

            if (srcIndex + 1 < monoInput.size) {
                val s0 = monoInput[srcIndex].toDouble()
                val s1 = monoInput[srcIndex + 1].toDouble()
                val interpolated = s0 + frac * (s1 - s0)
                output[i] = interpolated.toInt().coerceIn(-32768, 32767).toShort()
            } else if (srcIndex < monoInput.size) {
                output[i] = monoInput[srcIndex]
            }
        }

        return output
    }
}
