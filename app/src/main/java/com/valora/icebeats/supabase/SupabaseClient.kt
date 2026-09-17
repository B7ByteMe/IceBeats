package com.valora.icebeats.supabase

import android.content.Context
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.db.entities.ArtistEntity
import com.valora.icebeats.db.entities.PlaylistEntity
import com.valora.icebeats.db.entities.SongArtistMap
import com.valora.icebeats.db.entities.SongEntity
import com.valora.icebeats.db.entities.PlaylistSongMap
import com.valora.icebeats.db.entities.Event
import com.valora.icebeats.db.entities.AlbumEntity
import com.valora.icebeats.db.entities.AlbumArtistMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.valora.icebeats.ui.component.AvatarPreferenceManager
import com.valora.icebeats.ui.component.AvatarSelection
import com.valora.icebeats.ui.component.NamePreferenceManager
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SupabaseClient(private val context: Context) {
    private val authManager = SupabaseAuthManager.getInstance(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetMediaType = "application/octet-stream".toMediaType()

    private val baseUrl = SupabaseConfig.SUPABASE_URL
    private val anonKey = SupabaseConfig.SUPABASE_ANON_KEY

    private fun buildAuthHeaders(builder: Request.Builder): Request.Builder {
        builder.header("apikey", anonKey)
        val token = authManager.accessToken ?: anonKey
        builder.header("Authorization", "Bearer $token")
        return builder
    }

    /**
     * Sign Up with Email and Password
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/auth/v1/signup"
            val bodyJson = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                val userData = JSONObject().apply {
                    put("display_name", displayName.trim())
                }
                put("data", userData)
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val resBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errJson = runCatching { JSONObject(resBody) }.getOrNull()
                    val msg = errJson?.optString("msg")
                        ?: errJson?.optString("error_description")
                        ?: errJson?.optString("message")
                        ?: "Gagal mendaftar (HTTP ${response.code})"
                    throw Exception(msg)
                }

                val json = JSONObject(resBody)
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token")
                val userObj = json.optJSONObject("user")
                val uid = userObj?.optString("id").orEmpty()
                val userMetadata = userObj?.optJSONObject("user_metadata")
                val name = userMetadata?.optString("display_name") ?: displayName

                if (accessToken.isNotBlank()) {
                    authManager.saveSession(
                        token = accessToken,
                        refreshTokenStr = refreshToken,
                        uid = uid,
                        email = email,
                        name = name
                    )
                    "Akun berhasil dibuat dan otomatis masuk!"
                } else {
                    // Email confirmation may be required by Supabase project settings
                    "Pendaftaran berhasil! Silakan cek email Anda untuk konfirmasi jika diperlukan."
                }
            }
        }
    }

    /**
     * Sign In with Email and Password
     */
    suspend fun signIn(
        email: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/auth/v1/token?grant_type=password"
            val bodyJson = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val resBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errJson = runCatching { JSONObject(resBody) }.getOrNull()
                    val msg = errJson?.optString("error_description")
                        ?: errJson?.optString("msg")
                        ?: errJson?.optString("message")
                        ?: "Email atau password salah"
                    throw Exception(msg)
                }

                val json = JSONObject(resBody)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token")
                val userObj = json.getJSONObject("user")
                val uid = userObj.getString("id")
                val userEmail = userObj.optString("email", email)
                val userMetadata = userObj.optJSONObject("user_metadata")
                val name = listOfNotNull(
                    userMetadata?.optString("display_name")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("full_name")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                    userEmail.takeIf { it.isNotBlank() }?.substringBefore("@")
                ).firstOrNull() ?: "User"

                val avatarUrl = listOfNotNull(
                    userMetadata?.optString("avatar_url")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("picture")?.takeIf { it.isNotBlank() && it != "null" }
                ).firstOrNull()

                authManager.saveSession(
                    token = accessToken,
                    refreshTokenStr = refreshToken,
                    uid = uid,
                    email = userEmail,
                    name = name,
                    provider = "Email"
                )

                val namePref = NamePreferenceManager(context)
                namePref.rememberGoogleLoginEmail(userEmail)
                namePref.saveUserName(name)
                namePref.saveAccountEmail(userEmail)

                val avatarPref = AvatarPreferenceManager(context)
                if (!avatarUrl.isNullOrBlank()) {
                    avatarPref.saveAvatarSelection(
                        AvatarSelection.Custom(uri = avatarUrl, cloudUrl = avatarUrl)
                    )
                }

                "Berhasil masuk!"
            }
        }
    }

    /**
     * Sign In with Google ID Token (Native Google Sign-In -> Supabase Auth)
     */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/auth/v1/token?grant_type=id_token"
            val bodyJson = JSONObject().apply {
                put("provider", "google")
                put("id_token", idToken)
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val resBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errJson = runCatching { JSONObject(resBody) }.getOrNull()
                    val msg = errJson?.optString("error_description")
                        ?: errJson?.optString("msg")
                        ?: "Gagal autentikasi Google ke Supabase"
                    throw Exception(msg)
                }

                val json = JSONObject(resBody)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token")
                val userObj = json.getJSONObject("user")
                val uid = userObj.getString("id")
                val userEmail = userObj.optString("email")
                val userMetadata = userObj.optJSONObject("user_metadata")
                val name = listOfNotNull(
                    userMetadata?.optString("full_name")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                    userEmail.takeIf { it.isNotBlank() }?.substringBefore("@")
                ).firstOrNull() ?: "Google User"

                val avatarUrl = listOfNotNull(
                    userMetadata?.optString("avatar_url")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("picture")?.takeIf { it.isNotBlank() && it != "null" }
                ).firstOrNull()

                authManager.saveSession(
                    token = accessToken,
                    refreshTokenStr = refreshToken,
                    uid = uid,
                    email = userEmail,
                    name = name,
                    provider = "Google"
                )

                val namePref = NamePreferenceManager(context)
                namePref.rememberGoogleLoginEmail(userEmail)
                namePref.saveUserName(name)
                namePref.saveAccountEmail(userEmail)

                val avatarPref = AvatarPreferenceManager(context)
                if (!avatarUrl.isNullOrBlank()) {
                    avatarPref.saveAvatarSelection(
                        AvatarSelection.Custom(uri = avatarUrl, cloudUrl = avatarUrl)
                    )
                }

                "Berhasil masuk dengan akun Google!"
            }
        }
    }

    /**
     * Get OAuth Authorization URL for any provider (Facebook, Google, etc.)
     */
    fun getOAuthAuthorizeUrl(provider: String = "facebook"): String {
        val redirectUri = java.net.URLEncoder.encode("icebeats://auth-callback", "UTF-8")
        return "$baseUrl/auth/v1/authorize?provider=$provider&redirect_to=$redirectUri"
    }

    /**
     * Fetch user profile from Supabase using access token and save session
     */
    suspend fun fetchAndSaveUserProfile(accessToken: String, refreshTokenStr: String?): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/auth/v1/user"
            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val resBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw Exception("Gagal memuat profil pengguna")
                }

                val userObj = JSONObject(resBody)
                val uid = userObj.getString("id")
                val userEmail = userObj.optString("email", "")
                val userMetadata = userObj.optJSONObject("user_metadata")
                val appMetadata = userObj.optJSONObject("app_metadata")

                val rawProvider = appMetadata?.optString("provider", "")?.ifBlank { null }
                    ?: userObj.optString("app_metadata_provider", "")
                val providerName = when {
                    rawProvider.equals("github", ignoreCase = true) -> "GitHub"
                    rawProvider.equals("gitlab", ignoreCase = true) -> "GitLab"
                    rawProvider.equals("google", ignoreCase = true) -> "Google"
                    rawProvider.equals("facebook", ignoreCase = true) -> "Facebook"
                    rawProvider.isNotBlank() -> rawProvider.replaceFirstChar { it.uppercase() }
                    else -> "Email"
                }

                val extractedName = listOfNotNull(
                    userMetadata?.optString("full_name", "")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("name", "")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("user_name", "")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("preferred_username", "")?.takeIf { it.isNotBlank() && it != "null" },
                    userEmail.takeIf { it.isNotBlank() }?.substringBefore("@")
                ).firstOrNull() ?: "User"

                val avatarUrl = listOfNotNull(
                    userMetadata?.optString("avatar_url", "")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("picture", "")?.takeIf { it.isNotBlank() && it != "null" }
                ).firstOrNull()

                authManager.saveSession(
                    token = accessToken,
                    refreshTokenStr = refreshTokenStr,
                    uid = uid,
                    email = userEmail,
                    name = extractedName,
                    provider = providerName
                )

                // Update local name and email immediately
                val namePref = NamePreferenceManager(context)
                namePref.rememberGoogleLoginEmail(userEmail)
                namePref.saveUserName(extractedName)
                namePref.saveAccountEmail(userEmail)

                // Update local avatar immediately
                val avatarPref = AvatarPreferenceManager(context)
                if (!avatarUrl.isNullOrBlank()) {
                    avatarPref.saveAvatarSelection(
                        AvatarSelection.Custom(uri = avatarUrl, cloudUrl = avatarUrl)
                    )
                } else {
                    val encodedSeed = URLEncoder.encode(extractedName.ifBlank { userEmail }, StandardCharsets.UTF_8.toString())
                    val diceBear = "https://api.dicebear.com/9.x/initials/svg?seed=$encodedSeed&backgroundType=gradientLinear"
                    avatarPref.saveAvatarSelection(AvatarSelection.DiceBear(diceBear))
                }

                "Berhasil masuk!"
            }
        }
    }

    /**
     * Send Password Reset Email (Lupa Password)
     */
    suspend fun sendPasswordReset(email: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/auth/v1/recover"
            val bodyJson = JSONObject().apply {
                put("email", email.trim())
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val resBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errJson = runCatching { JSONObject(resBody) }.getOrNull()
                    val msg = errJson?.optString("msg")
                        ?: errJson?.optString("error_description")
                        ?: "Gagal mengirim link reset password"
                    throw Exception(msg)
                }

                "Tautan pemulihan password telah dikirim ke email $email. Silakan periksa inbox atau folder spam Anda."
            }
        }
    }

    /**
     * Update Password (Ubah Password saat sedang login atau setelah reset)
     */
    suspend fun updateUserPassword(newPassword: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = authManager.accessToken ?: throw Exception("Anda harus login untuk mengubah password")
            val url = "$baseUrl/auth/v1/user"
            val bodyJson = JSONObject().apply {
                put("password", newPassword)
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .put(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val resBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errJson = runCatching { JSONObject(resBody) }.getOrNull()
                    val msg = errJson?.optString("msg")
                        ?: errJson?.optString("error_description")
                        ?: "Gagal memperbarui password"
                    throw Exception(msg)
                }

                "Password berhasil diperbarui!"
            }
        }
    }

    /**
     * Verify OTP Code for Signup
     */
    suspend fun verifySignupOtp(email: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/auth/v1/verify"
            val bodyJson = JSONObject().apply {
                put("type", "signup")
                put("email", email.trim())
                put("token", token.trim())
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val resBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errJson = runCatching { JSONObject(resBody) }.getOrNull()
                    val msg = errJson?.optString("msg")
                        ?: errJson?.optString("error_description")
                        ?: "Kode OTP salah atau sudah kedaluwarsa"
                    throw Exception(msg)
                }

                val json = JSONObject(resBody)
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token")
                val userObj = json.optJSONObject("user")
                val uid = userObj?.optString("id").orEmpty()
                val userMetadata = userObj?.optJSONObject("user_metadata")
                val name = userMetadata?.optString("display_name")
                    ?: email.substringBefore("@")

                if (accessToken.isNotBlank()) {
                    authManager.saveSession(
                        token = accessToken,
                        refreshTokenStr = refreshToken,
                        uid = uid,
                        email = email,
                        name = name
                    )
                }

                "Akun berhasil diverifikasi dan aktif!"
            }
        }
    }

    /**
     * Resend Signup OTP Code
     */
    suspend fun resendSignupOtp(email: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/auth/v1/resend"
            val bodyJson = JSONObject().apply {
                put("type", "signup")
                put("email", email.trim())
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val resBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errJson = runCatching { JSONObject(resBody) }.getOrNull()
                    val msg = errJson?.optString("msg")
                        ?: errJson?.optString("error_description")
                        ?: "Gagal mengirim ulang kode OTP"
                    throw Exception(msg)
                }
                "Kode OTP baru telah dikirim ke $email"
            }
        }
    }

    /**
     * Verify OTP Code for Password Reset and apply new password
     */
    suspend fun verifyResetPasswordOtp(email: String, token: String, newPassword: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/auth/v1/verify"
            val bodyJson = JSONObject().apply {
                put("type", "recovery")
                put("email", email.trim())
                put("token", token.trim())
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val tempAccessToken: String
            httpClient.newCall(request).execute().use { response ->
                val resBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errJson = runCatching { JSONObject(resBody) }.getOrNull()
                    val msg = errJson?.optString("msg")
                        ?: errJson?.optString("error_description")
                        ?: "Kode OTP salah atau sudah kedaluwarsa"
                    throw Exception(msg)
                }
                val json = JSONObject(resBody)
                tempAccessToken = json.getString("access_token")
            }

            // Apply the new password using the recovery session
            val updateUrl = "$baseUrl/auth/v1/user"
            val updateBody = JSONObject().apply {
                put("password", newPassword)
            }
            val updateReq = Request.Builder()
                .url(updateUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $tempAccessToken")
                .header("Content-Type", "application/json")
                .put(updateBody.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(updateReq).execute().use { updateRes ->
                if (!updateRes.isSuccessful) {
                    throw Exception("Gagal menyimpan password baru")
                }
            }

            "Password berhasil diubah! Silakan login dengan password baru Anda."
        }
    }

    /**
     * Sign Out
     */
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = authManager.accessToken
            if (!token.isNullOrBlank()) {
                val url = "$baseUrl/auth/v1/logout"
                val request = Request.Builder()
                    .url(url)
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                runCatching { httpClient.newCall(request).execute() }
            }
            authManager.clearSession()
        }
    }

    /**
     * Refresh expired JWT access token using refresh_token
     */
    suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val rToken = authManager.refreshToken ?: return@runCatching false
            val url = "$baseUrl/auth/v1/token?grant_type=refresh_token"
            val bodyJson = JSONObject().apply {
                put("refresh_token", rToken)
            }
            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val resBody = response.body?.string().orEmpty()
                val json = JSONObject(resBody)
                val newAccessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", rToken)
                val userObj = json.getJSONObject("user")
                val uid = userObj.getString("id")
                val email = userObj.optString("email")
                val userMetadata = userObj.optJSONObject("user_metadata")
                val name = listOfNotNull(
                    userMetadata?.optString("full_name")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("name")?.takeIf { it.isNotBlank() && it != "null" },
                    userMetadata?.optString("display_name")?.takeIf { it.isNotBlank() && it != "null" },
                    email.takeIf { it.isNotBlank() }?.substringBefore("@")
                ).firstOrNull() ?: "User"

                authManager.saveSession(
                    token = newAccessToken,
                    refreshTokenStr = newRefreshToken,
                    uid = uid,
                    email = email,
                    name = name,
                    provider = authManager.authProvider.value
                )
                true
            }
        }.getOrDefault(false)
    }

    private fun executePostWithRetry(url: String, jsonArray: JSONArray, token: String): Boolean {
        if (jsonArray.length() == 0) return true
        var currentToken = token
        var request = Request.Builder()
            .url(url)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $currentToken")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates")
            .post(jsonArray.toString().toRequestBody(jsonMediaType))
            .build()

        var response = httpClient.newCall(request).execute()
        if (response.code == 401) {
            response.close()
            val refreshed = kotlinx.coroutines.runBlocking { refreshSession() }
            if (refreshed) {
                currentToken = authManager.accessToken ?: token
                request = Request.Builder()
                    .url(url)
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $currentToken")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates")
                    .post(jsonArray.toString().toRequestBody(jsonMediaType))
                    .build()
                response = httpClient.newCall(request).execute()
            }
        }

        val isSuccess = response.isSuccessful
        if (!isSuccess) {
            val errBody = response.body?.string().orEmpty()
            android.util.Log.e("SupabaseClient", "POST to $url failed: code ${response.code} body: $errBody")
        }
        response.close()
        return isSuccess
    }

    /**
     * Sync Liked/Favorite Songs and Playlists to Supabase in Background
     */
    suspend fun syncUserData(database: MusicDatabase): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!authManager.isLoggedIn.value) {
                throw Exception("Silakan login terlebih dahulu untuk sinkronisasi cloud.")
            }

            val uid = authManager.userId.value
            val token = authManager.accessToken ?: throw Exception("Sesi login berakhir. Silakan login kembali.")

            // 1. Fetch liked songs from Room DB
            val likedSongs = database.likedSongsByCreateDateAsc().first()
            val favoritesArray = JSONArray()
            likedSongs.forEach { song ->
                favoritesArray.put(JSONObject().apply {
                    put("user_id", uid)
                    put("song_id", song.id)
                    put("title", song.title)
                    put("artist_name", song.artists.joinToString { it.name })
                    put("album_name", song.song.albumName.orEmpty())
                    put("thumbnail_url", song.thumbnailUrl.orEmpty())
                    put("duration", song.song.duration)
                })
            }

            if (favoritesArray.length() > 0) {
                val favUrl = "$baseUrl/rest/v1/user_favorites?on_conflict=user_id,song_id"
                executePostWithRetry(favUrl, favoritesArray, token)
            }

            // 2. Fetch playlists from Room DB
            val playlists = database.playlistsByNameAsc().first()
            val playlistsArray = JSONArray()
            val playlistSongsArray = JSONArray()

            playlists.forEach { playlist ->
                val songCount = playlist.songCount
                playlistsArray.put(JSONObject().apply {
                    put("user_id", uid)
                    put("playlist_id", playlist.playlist.id)
                    put("name", playlist.playlist.name)
                    put("song_count", songCount)
                })

                val songsInPl = runCatching { database.playlistSongs(playlist.playlist.id).first() }.getOrDefault(emptyList())
                songsInPl.forEachIndexed { index, item ->
                    val s = item.song
                    playlistSongsArray.put(JSONObject().apply {
                        put("user_id", uid)
                        put("playlist_id", playlist.playlist.id)
                        put("song_id", s.song.id)
                        put("title", s.song.title)
                        put("artist_name", s.artists.joinToString { it.name })
                        put("album_name", s.song.albumName.orEmpty())
                        put("thumbnail_url", s.thumbnailUrl.orEmpty())
                        put("duration", s.song.duration)
                        put("position", item.map.position)
                    })
                }
            }

            if (playlistsArray.length() > 0) {
                val plUrl = "$baseUrl/rest/v1/user_playlists?on_conflict=user_id,playlist_id"
                executePostWithRetry(plUrl, playlistsArray, token)
            }

            if (playlistSongsArray.length() > 0) {
                val plSongsUrl = "$baseUrl/rest/v1/user_playlist_songs?on_conflict=user_id,playlist_id,song_id"
                executePostWithRetry(plSongsUrl, playlistSongsArray, token)
            }

            // 3. Sync recent playback events & history from Room DB
            val recentEvents = runCatching { database.events().first() }.getOrDefault(emptyList()).take(500)
            val eventsArray = JSONArray()
            recentEvents.forEach { item ->
                eventsArray.put(JSONObject().apply {
                    put("user_id", uid)
                    put("song_id", item.event.songId)
                    put("title", item.song.song.title)
                    put("artist_name", item.song.artists.joinToString { it.name })
                    put("album_name", item.song.song.albumName.orEmpty())
                    put("thumbnail_url", item.song.thumbnailUrl.orEmpty())
                    put("play_time", item.event.playTime)
                    val tsStr = runCatching {
                        item.event.timestamp.atZone(java.time.ZoneOffset.UTC).toInstant().toString()
                    }.getOrDefault(item.event.timestamp.toString())
                    put("timestamp", tsStr)
                })
            }
            if (eventsArray.length() > 0) {
                val evUrl = "$baseUrl/rest/v1/user_events?on_conflict=user_id,song_id,timestamp"
                executePostWithRetry(evUrl, eventsArray, token)
            }

            // 4. Sync Favorite Artists from Room DB
            val artists = runCatching { database.artistsBookmarkedByNameAsc().first() }.getOrDefault(emptyList())
            val artistsArray = JSONArray()
            artists.forEach { item ->
                artistsArray.put(JSONObject().apply {
                    put("user_id", uid)
                    put("artist_id", item.artist.id)
                    put("name", item.artist.name)
                    put("thumbnail_url", item.artist.thumbnailUrl.orEmpty())
                })
            }
            if (artistsArray.length() > 0) {
                val artUrl = "$baseUrl/rest/v1/user_favorite_artists?on_conflict=user_id,artist_id"
                executePostWithRetry(artUrl, artistsArray, token)
            }

            // 5. Sync Saved Albums from Room DB
            val albums = runCatching { database.albumsLikedByNameAsc().first() }.getOrDefault(emptyList())
            val albumsArray = JSONArray()
            albums.forEach { item ->
                albumsArray.put(JSONObject().apply {
                    put("user_id", uid)
                    put("album_id", item.id)
                    put("title", item.title)
                    put("artist_name", item.artists.joinToString { it.name })
                    put("year", item.album.year ?: 0)
                    put("thumbnail_url", item.thumbnailUrl.orEmpty())
                    put("song_count", item.album.songCount)
                })
            }
            if (albumsArray.length() > 0) {
                val albUrl = "$baseUrl/rest/v1/user_saved_albums?on_conflict=user_id,album_id"
                executePostWithRetry(albUrl, albumsArray, token)
            }

            val nowFormatted = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            authManager.updateLastSyncTime(nowFormatted)

            "Sinkronisasi berhasil! (${likedSongs.size} lagu favorit & ${playlists.size} playlist tersimpan di Cloud)"
        }
    }

    /**
     * Restore user favorites and playlists from Supabase database
     */
    suspend fun restoreUserData(database: MusicDatabase): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!authManager.isLoggedIn.value) return@runCatching 0
            val uid = authManager.userId.value
            val token = authManager.accessToken ?: anonKey

            var restoredCount = 0

            // 1. Restore Liked Songs
            val favUrl = "$baseUrl/rest/v1/user_favorites?user_id=eq.$uid"
            val favReq = Request.Builder()
                .url(favUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            httpClient.newCall(favReq).execute().use { favResp ->
                if (favResp.isSuccessful) {
                    val bodyStr = favResp.body?.string().orEmpty()
                    val favArr = runCatching { JSONArray(bodyStr) }.getOrNull()
                    if (favArr != null && favArr.length() > 0) {
                        for (i in 0 until favArr.length()) {
                            val item = favArr.optJSONObject(i) ?: continue
                            val songId = item.optString("song_id")
                            val title = item.optString("title")
                            val artistName = item.optString("artist_name")
                            val albumName = item.optString("album_name")
                            val thumb = item.optString("thumbnail_url")
                            val duration = item.optInt("duration", 0)

                            if (songId.isNotBlank()) {
                                database.transaction {
                                    val songEntity = SongEntity(
                                        id = songId,
                                        title = title.ifBlank { "Song" },
                                        duration = duration,
                                        thumbnailUrl = thumb.takeIf { it.isNotBlank() },
                                        albumId = null,
                                        albumName = albumName.takeIf { it.isNotBlank() },
                                        liked = true,
                                        likedDate = java.time.LocalDateTime.now(),
                                        inLibrary = java.time.LocalDateTime.now()
                                    )
                                    val rowId = insert(songEntity)
                                    if (rowId == -1L) {
                                        update(songEntity)
                                    }
                                    if (artistName.isNotBlank()) {
                                        val artistId = ArtistEntity.generateArtistId()
                                        insert(ArtistEntity(id = artistId, name = artistName))
                                        insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                                    }
                                }
                                restoredCount++
                            }
                        }
                    }
                }
            }

            // 2. Restore Playlists
            val plUrl = "$baseUrl/rest/v1/user_playlists?user_id=eq.$uid"
            val plReq = Request.Builder()
                .url(plUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            httpClient.newCall(plReq).execute().use { plResp ->
                if (plResp.isSuccessful) {
                    val bodyStr = plResp.body?.string().orEmpty()
                    val plArr = runCatching { JSONArray(bodyStr) }.getOrNull()
                    if (plArr != null && plArr.length() > 0) {
                        for (i in 0 until plArr.length()) {
                            val item = plArr.optJSONObject(i) ?: continue
                            val playlistId = item.optString("playlist_id")
                            val name = item.optString("name")
                            if (name.isNotBlank()) {
                                database.query {
                                    val plEntity = PlaylistEntity(
                                        id = if (playlistId.isNotBlank()) playlistId else PlaylistEntity.generatePlaylistId(),
                                        name = name,
                                        browseId = null,
                                        bookmarkedAt = java.time.LocalDateTime.now(),
                                        isEditable = true,
                                    )
                                    insert(plEntity)
                                    update(plEntity)
                                }
                                restoredCount++
                            }
                        }
                    }
                }
            }

            // 3. Restore Songs in Playlists
            val plSongsUrl = "$baseUrl/rest/v1/user_playlist_songs?user_id=eq.$uid&order=position.asc"
            val plSongsReq = Request.Builder()
                .url(plSongsUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            httpClient.newCall(plSongsReq).execute().use { plSongsResp ->
                if (plSongsResp.isSuccessful) {
                    val bodyStr = plSongsResp.body?.string().orEmpty()
                    val plSongsArr = runCatching { JSONArray(bodyStr) }.getOrNull()
                    if (plSongsArr != null && plSongsArr.length() > 0) {
                        val existingSongIdsByPlaylist = mutableMapOf<String, MutableSet<String>>()

                        for (i in 0 until plSongsArr.length()) {
                            val item = plSongsArr.optJSONObject(i) ?: continue
                            val playlistId = item.optString("playlist_id")
                            val songId = item.optString("song_id")
                            val title = item.optString("title")
                            val artistName = item.optString("artist_name")
                            val albumName = item.optString("album_name")
                            val thumb = item.optString("thumbnail_url")
                            val duration = item.optInt("duration", 0)
                            val position = item.optInt("position", i)

                            if (playlistId.isNotBlank() && songId.isNotBlank()) {
                                val plSongsSet = existingSongIdsByPlaylist.getOrPut(playlistId) {
                                    runCatching { database.playlistSongs(playlistId).first() }
                                        .getOrDefault(emptyList())
                                        .map { it.song.song.id }
                                        .toMutableSet()
                                }
                                if (songId in plSongsSet) {
                                    continue
                                }
                                plSongsSet.add(songId)
                                database.transaction {
                                    insert(
                                        SongEntity(
                                            id = songId,
                                            title = title.ifBlank { "Song" },
                                            duration = duration,
                                            thumbnailUrl = thumb.takeIf { it.isNotBlank() },
                                            albumId = null,
                                            albumName = albumName.takeIf { it.isNotBlank() },
                                            liked = false,
                                            inLibrary = java.time.LocalDateTime.now()
                                        )
                                    )
                                    if (artistName.isNotBlank()) {
                                        val artistId = ArtistEntity.generateArtistId()
                                        insert(ArtistEntity(id = artistId, name = artistName))
                                        insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                                    }
                                    val mapEntity = PlaylistSongMap(
                                        playlistId = playlistId,
                                        songId = songId,
                                        position = position
                                    )
                                    insert(mapEntity)
                                    update(mapEntity)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Restore Events (Stats & Playback History)
            val evUrl = "$baseUrl/rest/v1/user_events?user_id=eq.$uid&order=timestamp.desc&limit=500"
            val evReq = Request.Builder()
                .url(evUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            httpClient.newCall(evReq).execute().use { evResp ->
                if (evResp.isSuccessful) {
                    val bodyStr = evResp.body?.string().orEmpty()
                    val evArr = runCatching { JSONArray(bodyStr) }.getOrNull()
                    if (evArr != null && evArr.length() > 0) {
                        val existingEvents = runCatching { database.events().first() }.getOrDefault(emptyList())
                        val existingKeys = existingEvents.map { "${it.event.songId}_${it.event.timestamp.toEpochSecond(java.time.ZoneOffset.UTC) / 60}" }.toMutableSet()

                        database.transaction {
                            for (i in 0 until evArr.length()) {
                                val item = evArr.optJSONObject(i) ?: continue
                                val songId = item.optString("song_id")
                                val title = item.optString("title")
                                val artistName = item.optString("artist_name")
                                val albumName = item.optString("album_name")
                                val thumb = item.optString("thumbnail_url")
                                val playTime = item.optLong("play_time", 0L)
                                val tsStr = item.optString("timestamp")
                                val ts = runCatching {
                                    java.time.Instant.parse(tsStr).atZone(java.time.ZoneOffset.UTC).toLocalDateTime()
                                }.getOrElse {
                                    runCatching {
                                        java.time.OffsetDateTime.parse(tsStr).atZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime()
                                    }.getOrElse {
                                        runCatching { java.time.LocalDateTime.parse(tsStr) }.getOrDefault(java.time.LocalDateTime.now())
                                    }
                                }

                                val eventKey = "${songId}_${ts.toEpochSecond(java.time.ZoneOffset.UTC) / 60}"
                                if (eventKey in existingKeys) {
                                    continue
                                }
                                existingKeys.add(eventKey)

                                if (songId.isNotBlank()) {
                                    insert(
                                        SongEntity(
                                            id = songId,
                                            title = title.ifBlank { "Song $songId" },
                                            duration = if (playTime > 0) (playTime / 1000).toInt() else 180,
                                            thumbnailUrl = thumb.takeIf { it.isNotBlank() },
                                            albumId = null,
                                            albumName = albumName.takeIf { it.isNotBlank() },
                                            liked = false,
                                            inLibrary = java.time.LocalDateTime.now()
                                        )
                                    )
                                    if (artistName.isNotBlank()) {
                                        val artistId = artistByName(artistName)?.id ?: ArtistEntity.generateArtistId()
                                        insert(ArtistEntity(id = artistId, name = artistName))
                                        insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                                    }
                                    insert(
                                        Event(
                                            songId = songId,
                                            timestamp = ts,
                                            playTime = playTime
                                        )
                                    )
                                    incrementTotalPlayTime(songId, playTime)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Restore User Stats & Displayed Rank
            val statsUrl = "$baseUrl/rest/v1/user_stats?id=eq.$uid"
            val statsReq = Request.Builder()
                .url(statsUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            httpClient.newCall(statsReq).execute().use { statsResp ->
                if (statsResp.isSuccessful) {
                    val bodyStr = statsResp.body?.string().orEmpty()
                    val statsArr = runCatching { JSONArray(bodyStr) }.getOrNull()
                    val statsObj = statsArr?.optJSONObject(0)
                    if (statsObj != null) {
                        val totalMs = statsObj.optLong("total_listen_ms", 0L)
                        val totalHours = (totalMs / (1000 * 3600)).toInt()
                        if (totalHours >= 1) {
                            val rank = com.valora.icebeats.ui.component.icebeatsRank.fromHours(totalHours)
                            com.valora.icebeats.ui.component.RankPreferenceManager(context).saveDisplayedRank(rank)
                        }
                    }
                }
            }

            // 6. Restore Favorite Artists
            val artUrl = "$baseUrl/rest/v1/user_favorite_artists?user_id=eq.$uid"
            val artReq = Request.Builder()
                .url(artUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            httpClient.newCall(artReq).execute().use { artResp ->
                if (artResp.isSuccessful) {
                    val bodyStr = artResp.body?.string().orEmpty()
                    val artArr = runCatching { JSONArray(bodyStr) }.getOrNull()
                    if (artArr != null && artArr.length() > 0) {
                        for (i in 0 until artArr.length()) {
                            val item = artArr.optJSONObject(i) ?: continue
                            val artistId = item.optString("artist_id")
                            val name = item.optString("name")
                            val thumb = item.optString("thumbnail_url")
                            if (artistId.isNotBlank() && name.isNotBlank()) {
                                database.transaction {
                                    insert(
                                        ArtistEntity(
                                            id = artistId,
                                            name = name,
                                            thumbnailUrl = thumb.takeIf { it.isNotBlank() },
                                            bookmarkedAt = java.time.LocalDateTime.now()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Restore Saved Albums
            val albUrl = "$baseUrl/rest/v1/user_saved_albums?user_id=eq.$uid"
            val albReq = Request.Builder()
                .url(albUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            httpClient.newCall(albReq).execute().use { albResp ->
                if (albResp.isSuccessful) {
                    val bodyStr = albResp.body?.string().orEmpty()
                    val albArr = runCatching { JSONArray(bodyStr) }.getOrNull()
                    if (albArr != null && albArr.length() > 0) {
                        for (i in 0 until albArr.length()) {
                            val item = albArr.optJSONObject(i) ?: continue
                            val albumId = item.optString("album_id")
                            val title = item.optString("title")
                            val thumb = item.optString("thumbnail_url")
                            val year = item.optInt("year", 0).takeIf { it > 0 }
                            val songCount = item.optInt("song_count", 0)
                            if (albumId.isNotBlank() && title.isNotBlank()) {
                                database.transaction {
                                    insert(
                                        AlbumEntity(
                                            id = albumId,
                                            title = title,
                                            thumbnailUrl = thumb.takeIf { it.isNotBlank() },
                                            year = year,
                                            songCount = songCount,
                                            duration = 0,
                                            bookmarkedAt = java.time.LocalDateTime.now()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            restoredCount
        }
    }

    /**
     * Auto-sync single playlist and its songs to Supabase in background
     */
    fun autoSyncPlaylist(database: MusicDatabase, playlistId: String) {
        if (!authManager.isLoggedIn.value) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                syncUserData(database)
            }
        }
    }

    /**
     * Upload backup zip/db file to Supabase Storage bucket
     */
    suspend fun uploadCloudBackup(backupFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = authManager.userId.value.ifBlank { "guest" }
            val token = authManager.accessToken ?: anonKey
            val fileName = "icebeats_backup_${uid}.backup"
            val uploadUrl = "$baseUrl/storage/v1/object/backups/$fileName"

            val request = Request.Builder()
                .url(uploadUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("x-upsert", "true")
                .post(backupFile.asRequestBody(octetMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }
    }

    /**
     * Download backup file from Supabase Storage
     */
    suspend fun downloadCloudBackup(destFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = authManager.userId.value.ifBlank { "guest" }
            val token = authManager.accessToken ?: anonKey
            val fileName = "icebeats_backup_${uid}.backup"
            val downloadUrl = "$baseUrl/storage/v1/object/public/backups/$fileName"

            val request = Request.Builder()
                .url(downloadUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val bytes = response.body?.bytes() ?: return@use false
                FileOutputStream(destFile).use { it.write(bytes) }
                true
            }
        }
    }
}
