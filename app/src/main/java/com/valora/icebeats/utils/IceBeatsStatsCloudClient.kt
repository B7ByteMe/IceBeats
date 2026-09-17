package com.valora.icebeats.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GlobalStatsUser(
    val id: String,
    val name: String,
    val profileUrl: String?,
    val email: String? = null,
    val totalListenMs: Long,
    val weeklyListenMs: Long,
    val lastUpdatedAt: Long,
    val rank: Int = 0,
    val fcmToken: String? = null,
)

data class GlobalStatsBoard(
    val users: List<GlobalStatsUser> = emptyList(),
    val updatedAt: Long = 0L,
)

data class LocalStatsUpload(
    val userId: String,
    val name: String,
    val profileUrl: String?,
    val email: String? = null,
    val totalListenMs: Long,
    val weeklyListenMs: Long,
    val fcmToken: String? = null,
)

class icebeatsStatsCloudClient {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

    suspend fun readBoard(fileName: String = GLOBAL_STATS_FILE): Result<GlobalStatsBoard> =
        withContext(Dispatchers.IO) {
            runCatching {
                val supabaseUrl = "${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL}/rest/v1/user_stats?select=*&order=total_listen_ms.desc&limit=500"
                val request =
                    Request
                        .Builder()
                        .url(supabaseUrl)
                        .header("apikey", com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer ${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY}")
                        .header("Cache-Control", "no-cache")
                        .header("Pragma", "no-cache")
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 404) return@use GlobalStatsBoard()
                    val text = response.body?.bytes()?.let { String(it, Charsets.UTF_8) }.orEmpty()
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}: $text")
                    }
                    val jsonArray = JSONArray(text)
                    val userList = mutableListOf<GlobalStatsUser>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue
                        val parsedId = obj.optString("id").ifBlank { obj.optString("uuid") }
                        if (parsedId.isBlank()) continue
                        userList.add(
                            GlobalStatsUser(
                                id = parsedId,
                                name = obj.optString("name", "User"),
                                profileUrl = obj.optString("profile_url").trim().takeIf { it.isNotBlank() && it != "null" },
                                email = obj.optString("email").trim().takeIf { it.isNotBlank() && it != "null" },
                                totalListenMs = obj.optLong("total_listen_ms", 0L),
                                weeklyListenMs = obj.optLong("weekly_listen_ms", 0L),
                                lastUpdatedAt = obj.optLong("last_updated_at", 0L),
                                rank = i + 1,
                                fcmToken = obj.optString("fcm_token").trim().takeIf { it.isNotBlank() && it != "null" }
                            )
                        )
                    }
                    val distinctUsers = userList
                        .groupBy { user ->
                            user.email?.takeIf { it.isNotBlank() }
                                ?: user.name.trim().lowercase().takeIf { it.isNotBlank() }
                                ?: user.id
                        }
                        .map { entry ->
                            entry.value.maxByOrNull { it.totalListenMs } ?: entry.value.first()
                        }
                        .sortedByDescending { it.totalListenMs }
                        .mapIndexed { index, user ->
                            user.copy(rank = index + 1)
                        }

                    GlobalStatsBoard(
                        users = distinctUsers,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        }

    suspend fun uploadDaily(upload: LocalStatsUpload): Result<GlobalStatsBoard> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitizedName = upload.name.trim()
                    .replace("<", "")
                    .replace(">", "")
                    .replace(";", "")
                    .take(40)
                    .ifBlank { "IceBeats User" }
                val safeTotalListenMs = upload.totalListenMs.coerceAtLeast(0L)
                val safeWeeklyListenMs = upload.weeklyListenMs.coerceAtLeast(0L)

                val bodyJson = JSONObject().apply {
                    put("id", upload.userId)
                    put("name", sanitizedName)
                    put("profile_url", upload.profileUrl ?: JSONObject.NULL)
                    put("email", upload.email?.normalizedEmail() ?: JSONObject.NULL)
                    put("total_listen_ms", safeTotalListenMs)
                    put("weekly_listen_ms", safeWeeklyListenMs)
                    put("last_updated_at", System.currentTimeMillis())
                    put("fcm_token", upload.fcmToken ?: JSONObject.NULL)
                }

                val supabaseUrl = "${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL}/rest/v1/user_stats?on_conflict=id"
                val request =
                    Request
                        .Builder()
                        .url(supabaseUrl)
                        .header("apikey", com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer ${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY}")
                        .header("Content-Type", "application/json")
                        .header("Prefer", "resolution=merge-duplicates")
                        .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            android.util.Log.w("IceBeatsStats", "Upsert returned ${response.code}")
                        }
                    }
                }

                readBoard().getOrThrow()
            }
        }

    private fun parseBoard(json: JSONObject): GlobalStatsBoard {
        val usersJson = json.optJSONArray("users") ?: JSONArray()
        val users =
            List(usersJson.length()) { index -> usersJson.optJSONObject(index) }
                .mapNotNull { user ->
                    user?.let {
                        val parsedId = it.optString("id").ifBlank { it.optString("uuid") }
                        if (parsedId.isBlank()) return@let null
                        val profileUrl =
                            it.optString("profileUrl")
                                .trim()
                                .takeIf { value -> value.isNotBlank() && !value.equals("null", ignoreCase = true) }
                        val email =
                            it.optString("email")
                                .trim()
                                .takeIf { value -> value.isNotBlank() && !value.equals("null", ignoreCase = true) }
                        
                        GlobalStatsUser(
                            id = parsedId,
                            name = it.optString("name", "icebeats User"),
                            profileUrl = profileUrl,
                            email = email,
                            totalListenMs = it.optLong("totalListenMs").takeIf { v -> v > 0 } ?: it.optLong("listenTime"),
                            weeklyListenMs = it.optLong("weeklyListenMs"),
                            lastUpdatedAt = it.optLong("lastUpdatedAt"),
                            rank = it.optInt("rank"),
                            fcmToken = it.optString("fcmToken").takeIf(String::isNotBlank),
                        )
                    }
                }
                .groupBy { user ->
                    user.email?.takeIf { it.isNotBlank() }
                        ?: user.name.trim().lowercase().takeIf { it.isNotBlank() }
                        ?: user.id
                }
                .map { entry ->
                    entry.value.maxByOrNull { it.totalListenMs } ?: entry.value.first()
                }
                .sortedByDescending { it.totalListenMs }
                .take(MAX_GLOBAL_USERS)
                .mapIndexed { index, user -> user.copy(rank = index + 1) }
        return GlobalStatsBoard(users = users, updatedAt = json.optLong("updatedAt"))
    }

    private fun GlobalStatsBoard.toJson(isFcmFile: Boolean): JSONObject =
        JSONObject()
            .put("service", if (isFcmFile) "icebeats FCM Stats" else "icebeats Global Stats")
            .put("folder", "icebeats")
            .put("updatedAt", updatedAt)
            .put(
                "users",
                JSONArray(
                    users.map { user ->
                        if (isFcmFile) {
                            JSONObject()
                                .put("uuid", user.id)
                                .put("name", user.name)
                                .put("email", user.email ?: JSONObject.NULL)
                                .put("fcmToken", user.fcmToken ?: JSONObject.NULL)
                                .put("listenTime", user.totalListenMs)
                                .put("rank", user.rank)
                        } else {
                            JSONObject()
                                .put("id", user.id)
                                .put("name", user.name)
                                .put("email", user.email ?: JSONObject.NULL)
                                .put("profileUrl", user.profileUrl ?: JSONObject.NULL)
                                .put("totalListenMs", user.totalListenMs)
                                .put("weeklyListenMs", user.weeklyListenMs)
                                .put("lastUpdatedAt", user.lastUpdatedAt)
                                .put("rank", user.rank)
                        }
                    },
                ),
            )

    private fun parseError(text: String, code: Int): String =
        runCatching { JSONObject(text).optString("error").ifBlank { "HTTP $code" } }
            .getOrDefault("HTTP $code")

    private fun String?.normalizedEmail(): String? =
        this
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() && it != "null" }

    private companion object {
        val BASE_URL = com.valora.icebeats.BuildConfig.STATS_BASE_URL
        val API_KEY = com.valora.icebeats.BuildConfig.STATS_API_KEY
        const val GLOBAL_STATS_FILE = "icebeats/global_stats.json"
        const val FCM_STATS_FILE = "icebeats/fcm.json"
        const val MAX_GLOBAL_USERS = 10000000
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
