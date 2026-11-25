package com.valora.icebeats.ai

import android.content.Context
import com.valora.icebeats.constants.AiProviderKey
import com.valora.icebeats.constants.DeeplApiKey
import com.valora.icebeats.constants.OpenRouterApiKey
import com.valora.icebeats.constants.OpenRouterBaseUrlKey
import com.valora.icebeats.constants.OpenRouterModelKey
import com.valora.icebeats.db.InternalDatabase
import com.valora.icebeats.db.entities.PlaylistEntity
import com.valora.icebeats.db.entities.PlaylistSongMap
import com.valora.icebeats.db.entities.Song
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.innertube.models.SongItem
import com.valora.icebeats.models.toMediaMetadata
import com.valora.icebeats.utils.dataStore
import com.valora.icebeats.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object AiRecommendationHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    const val PLAYLIST_NAME = "Recommended by AI"

    suspend fun generateRecommendations(
        context: Context,
        database: com.valora.icebeats.db.MusicDatabase? = null,
        onLog: (suspend (String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val database = database ?: InternalDatabase.newInstance(context)

        onLog?.invoke("Analyzing your listening history...")
        // 1. Gather User Taste
        val mostPlayed: List<Song> = runCatching {
            database.mostPlayedSongs(fromTimeStamp = 0L, limit = 25).firstOrNull()
        }.getOrNull() ?: emptyList()

        val likedSongs: List<Song> = runCatching {
            database.likedSongsByPlayTimeAsc().firstOrNull()?.take(25)
        }.getOrNull() ?: emptyList()

        val recentSongs: List<Song> = if (mostPlayed.isEmpty() && likedSongs.isEmpty()) {
            runCatching {
                database.recentSongs(limit = 25).firstOrNull()
            }.getOrNull() ?: emptyList()
        } else {
            emptyList()
        }

        val pool = (mostPlayed + likedSongs + recentSongs).distinctBy { it.id }
        if (pool.isEmpty()) {
            onLog?.invoke("Not enough listening history found to generate recommendations.")
            return@withContext Result.failure(Exception("Not enough listening history found"))
        }

        val tasteList = pool.take(30).map { songObj ->
            val artists = songObj.artists.joinToString { it.name }.ifBlank { "Unknown Artist" }
            "${songObj.title} by $artists"
        }

        val prompt = """
            Based on the following list of songs the user enjoys listening to:
            ${tasteList.joinToString("\n")}

            Please recommend 20 similar songs that match this musical taste, energy, and vibe.
            CRITICAL RULES:
            1. You MUST output ONLY a valid JSON array of objects.
            2. Do NOT include markdown code blocks, backticks, or explanatory prose.
            3. Each object MUST contain "title" and "artist".

            Example:
            [
              {"title": "Song Name", "artist": "Artist Name"}
            ]
        """.trimIndent()

        // 2. Query AI Provider
        onLog?.invoke("Connecting to AI Provider...")
        val aiProvider = context.dataStore.get(AiProviderKey, "OpenRouter")
        val apiKey = if (aiProvider == "DeepL") {
            context.dataStore.get(DeeplApiKey, "")
        } else {
            context.dataStore.get(OpenRouterApiKey, "")
        }

        val baseUrl = context.dataStore.get(OpenRouterBaseUrlKey, "https://openrouter.ai/api/v1/chat/completions")
        val model = context.dataStore.get(OpenRouterModelKey, "google/gemini-2.5-flash-lite")

        if (apiKey.isBlank() && aiProvider != "Custom" && aiProvider != "Puter") {
            onLog?.invoke("API Key is missing. Please configure it in AI Settings.")
            return@withContext Result.failure(Exception("API Key is missing for $aiProvider"))
        }

        val jsonOutput = try {
            if (aiProvider == "Claude") {
                val reqBody = JSONObject().apply {
                    put("model", model.ifBlank { "claude-3-5-haiku-latest" })
                    put("max_tokens", 2048)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(baseUrl.ifBlank { "https://api.anthropic.com/v1/messages" })
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .post(reqBody)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val err = "Claude request failed: code ${response.code}"
                    onLog?.invoke(err)
                    return@withContext Result.failure(Exception(err))
                }
                val bodyStr = response.body?.string() ?: ""
                val respJson = JSONObject(bodyStr)
                val contentArr = respJson.optJSONArray("content")
                contentArr?.optJSONObject(0)?.optString("text") ?: "[]"
            } else {
                val reqBody = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val reqBuilder = Request.Builder()
                    .url(baseUrl)
                    .post(reqBody)

                if (apiKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                val response = client.newCall(reqBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val err = "AI Request failed: HTTP ${response.code}"
                    onLog?.invoke(err)
                    return@withContext Result.failure(Exception(err))
                }
                val bodyStr = response.body?.string() ?: ""
                val respJson = JSONObject(bodyStr)
                val choices = respJson.optJSONArray("choices")
                choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: "[]"
            }
        } catch (e: Exception) {
            val err = "Connection to AI Provider failed: ${e.localizedMessage}"
            onLog?.invoke(err)
            return@withContext Result.failure(e)
        }

        // Clean output just in case of markdown formatting
        val cleanJsonStr = jsonOutput
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val jsonArray = try {
            onLog?.invoke("Parsing AI response...")
            val startIndex = cleanJsonStr.indexOf('[')
            val endIndex = cleanJsonStr.lastIndexOf(']')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                JSONArray(cleanJsonStr.substring(startIndex, endIndex + 1))
            } else {
                JSONArray(cleanJsonStr)
            }
        } catch (e: Exception) {
            onLog?.invoke("Failed to parse AI output format.")
            return@withContext Result.failure(e)
        }

        // 3. Resolve with YouTube and Save
        val resolvedSongs = mutableListOf<SongItem>()
        val totalSongs = jsonArray.length()
        onLog?.invoke("Resolving $totalSongs recommendations with YouTube Music...")

        for (i in 0 until totalSongs) {
            val item = jsonArray.optJSONObject(i) ?: continue
            val title = item.optString("title")
            val artist = item.optString("artist")
            if (title.isNotBlank()) {
                val searchQuery = "$title $artist".trim()
                onLog?.invoke("Searching [${i + 1}/$totalSongs]: $searchQuery")
                val searchResult = runCatching {
                    YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                }.getOrNull()

                val topResult = searchResult?.items?.firstOrNull() as? SongItem
                if (topResult != null && resolvedSongs.none { it.id == topResult.id }) {
                    resolvedSongs.add(topResult)
                }
            }
        }

        if (resolvedSongs.isEmpty()) {
            val err = "Could not resolve any suggested songs on YouTube."
            onLog?.invoke(err)
            return@withContext Result.failure(Exception(err))
        }

        // 4. Update Database Playlist
        onLog?.invoke("Syncing ${resolvedSongs.size} tracks into 'Recommended by AI'...")
        var playlist = database.searchPlaylists(PLAYLIST_NAME).firstOrNull()
            ?.find { it.playlist.name == PLAYLIST_NAME }?.playlist

        if (playlist == null) {
            playlist = PlaylistEntity(
                name = PLAYLIST_NAME,
                createdAt = LocalDateTime.now(),
                lastUpdateTime = LocalDateTime.now(),
                bookmarkedAt = LocalDateTime.now(),
                isEditable = true
            )
            database.insert(playlist)
        } else {
            database.clearPlaylist(playlist.id)
            database.update(playlist.copy(lastUpdateTime = LocalDateTime.now()))
        }

        resolvedSongs.forEachIndexed { index, songItem ->
            database.insert(songItem.toMediaMetadata())
            database.insert(
                PlaylistSongMap(
                    playlistId = playlist.id,
                    songId = songItem.id,
                    position = index
                )
            )
        }

        onLog?.invoke("AI Recommendations successfully updated!")
        Result.success(resolvedSongs.size)
    }
}
