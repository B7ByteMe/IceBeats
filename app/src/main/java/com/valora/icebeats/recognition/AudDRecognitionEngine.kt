package com.valora.icebeats.recognition

import android.content.Context
import android.util.Log
import com.valora.icebeats.constants.AudDTokenKey
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.getSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AudDRecognitionEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) : MusicRecognitionEngine {

    companion object {
        private const val TAG = "AudDRecognitionEngine"
        private const val AUDD_API_URL = "https://api.audd.io/"
        private const val DEFAULT_TEST_TOKEN = "test"
    }

    override val providerName: String = "AudD"

    override suspend fun recognize(audio: AudioSource): RecognitionResult = withContext(Dispatchers.IO) {
        val configuredToken = context.dataStore.getSuspend(AudDTokenKey, "")
        val apiToken = if (!configuredToken.isNullOrBlank()) configuredToken else DEFAULT_TEST_TOKEN

        val fileBody = audio.file.asRequestBody("audio/wav".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("api_token", apiToken)
            .addFormDataPart("return", "apple_music,spotify")
            .addFormDataPart("file", audio.file.name, fileBody)
            .build()

        val request = Request.Builder()
            .url(AUDD_API_URL)
            .post(multipartBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                Log.w(TAG, "AudD request failed with HTTP ${response.code}: $responseBody")
                return@withContext RecognitionResult(
                    success = false,
                    provider = providerName
                )
            }

            val json = JSONObject(responseBody)
            val status = json.optString("status")

            if (status.equals("success", ignoreCase = true)) {
                val resultObj = json.optJSONObject("result")
                if (resultObj != null) {
                    val title = resultObj.optString("title").takeIf { it.isNotBlank() }
                    val artist = resultObj.optString("artist").takeIf { it.isNotBlank() }
                    val album = resultObj.optString("album").takeIf { it.isNotBlank() }

                    var albumArtUrl: String? = null
                    val appleMusicObj = resultObj.optJSONObject("apple_music")
                    if (appleMusicObj != null) {
                        val artwork = appleMusicObj.optJSONObject("artwork")
                        val artTemplate = artwork?.optString("url")
                        if (!artTemplate.isNullOrBlank()) {
                            albumArtUrl = artTemplate.replace("{w}", "500").replace("{h}", "500")
                        }
                    }

                    if (albumArtUrl.isNullOrBlank()) {
                        val spotifyObj = resultObj.optJSONObject("spotify")
                        val albumObj = spotifyObj?.optJSONObject("album")
                        val images = albumObj?.optJSONArray("images")
                        if (images != null && images.length() > 0) {
                            albumArtUrl = images.optJSONObject(0)?.optString("url")
                        }
                    }

                    val rawMap = mutableMapOf<String, String>()
                    val keys = resultObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        rawMap[key] = resultObj.optString(key, "")
                    }

                    return@withContext RecognitionResult(
                        success = true,
                        title = title,
                        artist = artist,
                        album = album,
                        albumArtUrl = albumArtUrl,
                        durationMs = audio.durationMs,
                        confidence = 0.95f,
                        externalId = resultObj.optString("song_link"),
                        provider = providerName,
                        rawMetadata = rawMap
                    )
                } else {
                    // No match found
                    return@withContext RecognitionResult(
                        success = false,
                        provider = providerName
                    )
                }
            } else {
                val errorObj = json.optJSONObject("error")
                val errorMsg = errorObj?.optString("error_message") ?: "Unknown recognition error"
                Log.w(TAG, "AudD returned error: $errorMsg")
                return@withContext RecognitionResult(
                    success = false,
                    provider = providerName
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing music recognition via AudD", e)
            return@withContext RecognitionResult(
                success = false,
                provider = providerName
            )
        }
    }
}
