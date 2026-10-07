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
     * Sinkronkan level, total jam dengar, rank badge, dan border terkini pengguna dari server Supabase.
     * Langsung mengupdate tampilan Settings, Badge profil, dan Border saat diubah di Admin Dashboard.
     */
    suspend fun syncCurrentUserStats(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val nameManager = com.valora.icebeats.ui.component.NamePreferenceManager(context)
            val rankManager = com.valora.icebeats.ui.component.RankPreferenceManager(context)
            val borderManager = com.valora.icebeats.ui.component.BorderPreferenceManager(context)
            val userId = com.valora.icebeats.utils.IceBeatsStatsCloudSync.resolveStableUserId(context, nameManager)
            val email = nameManager.accountEmail.first().trim()

            val token = authManager.accessToken ?: anonKey
            val queryUrl = if (email.isNotBlank()) {
                val encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8.name())
                "$baseUrl/rest/v1/user_stats?or=(id.eq.$userId,email.eq.$encodedEmail)&select=id,name,total_listen_ms,border_style&limit=1"
            } else {
                "$baseUrl/rest/v1/user_stats?id=eq.$userId&select=id,name,total_listen_ms,border_style&limit=1"
            }

            val request = Request.Builder()
                .url(queryUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val bodyStr = response.body?.string().orEmpty()
                val arr = runCatching { JSONArray(bodyStr) }.getOrNull() ?: return@use
                val prefs = context.getSharedPreferences(com.valora.icebeats.utils.IceBeatsStatsCloudSync.PREFERENCES_NAME, Context.MODE_PRIVATE)
                val userKey = "saved_max_total_listen_ms_${userId}"
                val anchorKey = "last_local_anchor_ms_${userId}"

                if (arr.length() == 0) {
                    // Akun baru belum punya data di cloud: set 0 jam & reset rank dan border
                    prefs.edit()
                        .putLong(userKey, 0L)
                        .putLong("saved_max_total_listen_ms", 0L)
                        .putLong(anchorKey, 0L)
                        .putLong("last_local_anchor_ms", 0L)
                        .apply()
                    rankManager.saveDisplayedRank(null)
                    borderManager.saveSelectedBorder(com.valora.icebeats.ui.component.MasterBorderStyle.ROYAL_CROWN)
                    return@use
                }

                val obj = arr.getJSONObject(0)
                val serverMs = obj.optLong("total_listen_ms", 0L)
                val serverHours = (serverMs / (1000 * 3600)).toInt()
                val serverBorder = obj.optString("border_style").trim().takeIf { it.isNotBlank() && it != "null" }

                // Simpan jam dengar server khusus akun ini
                prefs.edit()
                    .putLong(userKey, serverMs)
                    .putLong("saved_max_total_listen_ms", serverMs)
                    .putLong(anchorKey, 0L)
                    .putLong("last_local_anchor_ms", 0L)
                    .apply()

                // Hitung dan simpan rank baru dari server sesuai jam akun ini
                val newRank = if (serverHours >= 1) com.valora.icebeats.ui.component.icebeatsRank.fromHours(serverHours) else null
                rankManager.saveDisplayedRank(newRank)

                // Simpan border yang dipilih di server jika ada, atau default jika belum ada
                val style = if (serverBorder != null) {
                    com.valora.icebeats.ui.component.MasterBorderStyle.fromId(serverBorder)
                } else {
                    com.valora.icebeats.ui.component.MasterBorderStyle.ROYAL_CROWN
                }
                borderManager.saveSelectedBorder(style)
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
    /**
     * Menyetujui sesi login QR Code di IceBeats Windows Desktop
     */
    suspend fun approveDesktopQrSession(qrToken: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (!authManager.isLoggedIn.value) {
                throw Exception("Silakan login di aplikasi terlebih dahulu.")
            }
            val uid = authManager.userId.value
            val email = authManager.userEmail.value
            val name = authManager.userName.value
            val token = authManager.accessToken ?: anonKey

            val payload = JSONObject().apply {
                put("id", qrToken)
                put("status", "approved")
                put("user_id", uid)
                put("user_email", email)
                put("user_name", name)
                put("auth_token", token)
            }

            // 1. Upload ke storage bucket public backups/qr_sessions/.json
            val storageUrl = "$baseUrl/storage/v1/object/backups/qr_sessions/$qrToken.json"
            val storageReq = Request.Builder()
                .url(storageUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("x-upsert", "true")
                .post(payload.toString().toByteArray().toRequestBody(jsonMediaType))
                .build()
            runCatching {
                httpClient.newCall(storageReq).execute().close()
            }

            // 2. Update tabel auth_qr_sessions (jika tabel SQL sudah dibuat)
            val tableUrl = "$baseUrl/rest/v1/auth_qr_sessions?id=eq.$qrToken"
            val tableReq = Request.Builder()
                .url(tableUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .patch(payload.toString().toRequestBody(jsonMediaType))
                .build()
            runCatching {
                httpClient.newCall(tableReq).execute().close()
            }

            true
        }
    }

    // ==============================================================================
    // FITUR CHAT & BERBAGI MUSIK (v7.0.9)
    // ==============================================================================

    /**
     * Cari pengguna lain untuk memulai obrolan
     */
    suspend fun searchChatUsers(query: String, currentUserId: String): Result<List<ChatUser>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = authManager.accessToken ?: anonKey
            val trimmed = query.trim()
            val url = if (trimmed.isBlank()) {
                "$baseUrl/rest/v1/user_stats?select=id,name,profile_url,total_listen_ms,border_style&order=total_listen_ms.desc&limit=30"
            } else {
                val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
                "$baseUrl/rest/v1/user_stats?select=id,name,profile_url,total_listen_ms,border_style&or=(name.ilike.*$encoded*,email.ilike.*$encoded*)&order=total_listen_ms.desc&limit=30"
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val bodyStr = response.body?.string().orEmpty()
                val jsonArr = runCatching { JSONArray(bodyStr) }.getOrNull() ?: return@use emptyList()
                val result = mutableListOf<ChatUser>()

                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").ifBlank { obj.optString("uuid") }
                    if (id.isBlank() || id == currentUserId) continue

                    val name = obj.optString("name", "User IceBeats")
                    val profileUrl = obj.optString("profile_url").takeIf { it.isNotBlank() && it != "null" }
                    val totalMs = obj.optLong("total_listen_ms", 0L)
                    val totalHours = (totalMs / (1000 * 3600)).toInt()
                    val rank = if (totalHours >= 1) com.valora.icebeats.ui.component.icebeatsRank.fromHours(totalHours) else null
                    val bStyle = obj.optString("border_style").trim().takeIf { it.isNotBlank() && it != "null" }

                    result.add(
                        ChatUser(
                            id = id,
                            name = name,
                            profileUrl = profileUrl,
                            totalListenMs = totalMs,
                            rank = rank,
                            borderStyle = bStyle
                        )
                    )
                }
                result
            }
        }
    }

    /**
     * Dapatkan daftar percakapan aktif untuk pengguna saat ini
     */
    suspend fun getChatConversations(currentUserId: String): Result<List<ChatConversation>> = withContext(Dispatchers.IO) {
        runCatching {
            if (currentUserId.isBlank()) return@runCatching emptyList()
            val token = authManager.accessToken ?: anonKey

            val convUrl = "$baseUrl/rest/v1/chat_conversations?or=(user1_id.eq.$currentUserId,user2_id.eq.$currentUserId)&order=last_message_at.desc&limit=50"
            val convReq = Request.Builder()
                .url(convUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            val rawConversations = mutableListOf<Triple<String, String, String>>() // convId, otherUserId, lastMsg
            httpClient.newCall(convReq).execute().use { response ->
                if (!response.isSuccessful) return@use
                val bodyStr = response.body?.string().orEmpty()
                val arr = runCatching { JSONArray(bodyStr) }.getOrNull() ?: return@use
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val u1 = obj.optString("user1_id")
                    val u2 = obj.optString("user2_id")
                    val otherId = if (u1 == currentUserId) u2 else u1
                    val lastMsg = obj.optString("last_message")
                    val lastMsgAt = obj.optString("last_message_at")
                    if (id.isNotBlank() && otherId.isNotBlank()) {
                        rawConversations.add(Triple(id, otherId, lastMsg))
                    }
                }
            }

            if (rawConversations.isEmpty()) return@runCatching emptyList()

            // Ambil info profil user lain
            val otherUserIds = rawConversations.map { it.second }.distinct()
            val userMap = mutableMapOf<String, ChatUser>()
            for (batch in otherUserIds.chunked(20)) {
                val inQuery = batch.joinToString(",")
                val userUrl = "$baseUrl/rest/v1/user_stats?id=in.($inQuery)&select=id,name,profile_url,total_listen_ms,border_style"
                val userReq = Request.Builder()
                    .url(userUrl)
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                httpClient.newCall(userReq).execute().use { uResp ->
                    if (uResp.isSuccessful) {
                        val uArr = runCatching { JSONArray(uResp.body?.string().orEmpty()) }.getOrNull()
                        if (uArr != null) {
                            for (j in 0 until uArr.length()) {
                                val uObj = uArr.optJSONObject(j) ?: continue
                                val uid = uObj.optString("id")
                                val name = uObj.optString("name", "User")
                                val profile = uObj.optString("profile_url").takeIf { it.isNotBlank() && it != "null" }
                                val ms = uObj.optLong("total_listen_ms", 0L)
                                val hours = (ms / (1000 * 3600)).toInt()
                                val rank = if (hours >= 1) com.valora.icebeats.ui.component.icebeatsRank.fromHours(hours) else null
                                val bStyle = uObj.optString("border_style").trim().takeIf { it.isNotBlank() && it != "null" }
                                userMap[uid] = ChatUser(uid, name, profile, ms, rank, bStyle)
                            }
                        }
                    }
                }
            }

            rawConversations.map { (convId, otherId, lastMsg) ->
                val other = userMap[otherId] ?: ChatUser(otherId, "User")
                ChatConversation(
                    id = convId,
                    otherUser = other,
                    lastMessage = lastMsg
                )
            }
        }
    }

    /**
     * Dapatkan atau buat ID percakapan antar dua pengguna
     */
    suspend fun getOrCreateConversation(currentUserId: String, otherUserId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (currentUserId.isBlank() || otherUserId.isBlank()) throw IllegalArgumentException("User ID tidak valid")
            val token = authManager.accessToken ?: anonKey

            val (u1, u2) = if (currentUserId < otherUserId) currentUserId to otherUserId else otherUserId to currentUserId

            // Cek apakah percakapan sudah ada
            val findUrl = "$baseUrl/rest/v1/chat_conversations?user1_id=eq.$u1&user2_id=eq.$u2&select=id"
            val findReq = Request.Builder()
                .url(findUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            var existingId: String? = null
            httpClient.newCall(findReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = runCatching { JSONArray(resp.body?.string().orEmpty()) }.getOrNull()
                    if (arr != null && arr.length() > 0) {
                        existingId = arr.optJSONObject(0)?.optString("id")
                    }
                }
            }

            if (!existingId.isNullOrBlank()) {
                return@runCatching existingId!!
            }

            // Buat percakapan baru
            val insertUrl = "$baseUrl/rest/v1/chat_conversations"
            val payload = JSONObject().apply {
                put("user1_id", u1)
                put("user2_id", u2)
                put("last_message", "")
            }

            val insertReq = Request.Builder()
                .url(insertUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(insertReq).execute().use { resp ->
                val resBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw Exception("Gagal membuat percakapan: HTTP ${resp.code}")
                }
                val arr = runCatching { JSONArray(resBody) }.getOrNull()
                val createdId = arr?.optJSONObject(0)?.optString("id")
                    ?: throw Exception("ID percakapan kosong")
                createdId
            }
        }
    }

    /**
     * Dapatkan riwayat pesan dalam percakapan
     */
    suspend fun getChatMessages(conversationId: String): Result<List<ChatMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = authManager.accessToken ?: anonKey
            val url = "$baseUrl/rest/v1/chat_messages?conversation_id=eq.$conversationId&order=created_at.asc&limit=100"

            val request = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val bodyStr = response.body?.string().orEmpty()
                val arr = runCatching { JSONArray(bodyStr) }.getOrNull() ?: return@use emptyList()
                val list = mutableListOf<ChatMessage>()

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val convId = obj.optString("conversation_id")
                    val senderId = obj.optString("sender_id")
                    val receiverId = obj.optString("receiver_id")
                    val type = obj.optString("message_type", "text")
                    val content = obj.optString("content")
                    val isRead = obj.optBoolean("is_read", false)
                    val createdAt = obj.optString("created_at")

                    var media: ChatSharedMedia? = null
                    val mediaObj = obj.optJSONObject("media_data")
                    if (mediaObj != null) {
                        media = ChatSharedMedia(
                            songId = mediaObj.optString("song_id"),
                            title = mediaObj.optString("title"),
                            artistName = mediaObj.optString("artist_name"),
                            albumName = mediaObj.optString("album_name").takeIf { it.isNotBlank() },
                            thumbnailUrl = mediaObj.optString("thumbnail_url").takeIf { it.isNotBlank() },
                            duration = mediaObj.optInt("duration", 0)
                        )
                    }

                    list.add(
                        ChatMessage(
                            id = id,
                            conversationId = convId,
                            senderId = senderId,
                            receiverId = receiverId,
                            messageType = type,
                            content = content,
                            mediaData = media,
                            isRead = isRead,
                            createdAt = createdAt
                        )
                    )
                }
                list
            }
        }
    }

    /**
     * Kirim pesan teks
     */
    suspend fun sendChatMessage(
        conversationId: String,
        senderId: String,
        receiverId: String,
        content: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = content.trim()
            if (sanitized.isEmpty()) throw IllegalArgumentException("Pesan tidak boleh kosong")
            val token = authManager.accessToken ?: anonKey

            val payload = JSONObject().apply {
                put("conversation_id", conversationId)
                put("sender_id", senderId)
                put("receiver_id", receiverId)
                put("message_type", "text")
                put("content", sanitized)
                put("is_read", false)
            }

            val request = Request.Builder()
                .url("$baseUrl/rest/v1/chat_messages")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Gagal mengirim pesan: HTTP ${resp.code}")
            }

            // Update status percakapan terakhir
            val updatePayload = JSONObject().apply {
                put("last_message", sanitized)
                put("last_message_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date()))
            }
            val updateReq = Request.Builder()
                .url("$baseUrl/rest/v1/chat_conversations?id=eq.$conversationId")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .patch(updatePayload.toString().toRequestBody(jsonMediaType))
                .build()

            runCatching { httpClient.newCall(updateReq).execute().close() }
            true
        }
    }

    /**
     * Kirim pesan berbagi lagu (Music Card)
     */
    suspend fun sendSharedMusic(
        conversationId: String,
        senderId: String,
        receiverId: String,
        media: ChatSharedMedia,
        note: String = ""
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val token = authManager.accessToken ?: anonKey
            val displayNote = note.trim().ifEmpty { "🎵 Berbagi lagu: ${media.title} - ${media.artistName}" }

            val mediaJson = JSONObject().apply {
                put("song_id", media.songId)
                put("title", media.title)
                put("artist_name", media.artistName)
                put("album_name", media.albumName ?: "")
                put("thumbnail_url", media.thumbnailUrl ?: "")
                put("duration", media.duration)
            }

            val payload = JSONObject().apply {
                put("conversation_id", conversationId)
                put("sender_id", senderId)
                put("receiver_id", receiverId)
                put("message_type", "music")
                put("content", displayNote)
                put("media_data", mediaJson)
                put("is_read", false)
            }

            val request = Request.Builder()
                .url("$baseUrl/rest/v1/chat_messages")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Gagal berbagi lagu: HTTP ${resp.code}")
            }

            // Update status percakapan terakhir
            val updatePayload = JSONObject().apply {
                put("last_message", "🎵 ${media.title}")
                put("last_message_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date()))
            }
            val updateReq = Request.Builder()
                .url("$baseUrl/rest/v1/chat_conversations?id=eq.$conversationId")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .patch(updatePayload.toString().toRequestBody(jsonMediaType))
                .build()

            runCatching { httpClient.newCall(updateReq).execute().close() }
            true
        }
    }

    /**
     * Tandai pesan sebagai telah dibaca
     */
    suspend fun markChatAsRead(conversationId: String, currentUserId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val token = authManager.accessToken ?: anonKey
            val updatePayload = JSONObject().apply {
                put("is_read", true)
            }
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/chat_messages?conversation_id=eq.$conversationId&receiver_id=eq.$currentUserId&is_read=eq.false")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .patch(updatePayload.toString().toRequestBody(jsonMediaType))
                .build()

            runCatching { httpClient.newCall(req).execute().close() }
            true
        }
    }

    /**
     * Dapatkan status pertemanan antara 2 user
     */
    suspend fun getFriendshipStatus(myUserId: String, targetUserId: String): Result<FriendshipStatus> = withContext(Dispatchers.IO) {
        runCatching {
            if (myUserId.isBlank() || targetUserId.isBlank() || myUserId == targetUserId) {
                return@runCatching FriendshipStatus.NONE
            }
            val token = authManager.accessToken ?: anonKey
            val url = "$baseUrl/rest/v1/chat_friendships?or=(and(sender_id.eq.$myUserId,receiver_id.eq.$targetUserId),and(sender_id.eq.$targetUserId,receiver_id.eq.$myUserId))&limit=1"
            val req = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use FriendshipStatus.NONE
                val bodyStr = resp.body?.string().orEmpty()
                val arr = runCatching { JSONArray(bodyStr) }.getOrNull() ?: return@use FriendshipStatus.NONE
                if (arr.length() == 0) return@use FriendshipStatus.NONE
                val obj = arr.optJSONObject(0) ?: return@use FriendshipStatus.NONE
                val senderId = obj.optString("sender_id")
                val status = obj.optString("status")
                when {
                    status == "accepted" -> FriendshipStatus.FRIENDS
                    senderId == myUserId && status == "pending" -> FriendshipStatus.PENDING_SENT
                    senderId == targetUserId && status == "pending" -> FriendshipStatus.PENDING_RECEIVED
                    else -> FriendshipStatus.NONE
                }
            }
        }
    }

    /**
     * Kirim permintaan pertemanan
     */
    suspend fun sendFriendRequest(myUserId: String, targetUserId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (myUserId.isBlank() || targetUserId.isBlank() || myUserId == targetUserId) return@runCatching false
            val token = authManager.accessToken ?: anonKey
            val payload = JSONObject().apply {
                put("sender_id", myUserId)
                put("receiver_id", targetUserId)
                put("status", "pending")
            }
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/chat_friendships")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        }
    }

    /**
     * Terima permintaan pertemanan
     */
    suspend fun acceptFriendRequest(myUserId: String, targetUserId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (myUserId.isBlank() || targetUserId.isBlank()) return@runCatching false
            val token = authManager.accessToken ?: anonKey
            val payload = JSONObject().apply {
                put("status", "accepted")
            }
            val url = "$baseUrl/rest/v1/chat_friendships?or=(and(sender_id.eq.$myUserId,receiver_id.eq.$targetUserId),and(sender_id.eq.$targetUserId,receiver_id.eq.$myUserId))"
            val req = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .patch(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        }
    }

    /**
     * Tolak atau batalkan pertemanan
     */
    suspend fun rejectOrRemoveFriend(myUserId: String, targetUserId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            if (myUserId.isBlank() || targetUserId.isBlank()) return@runCatching false
            val token = authManager.accessToken ?: anonKey
            val url = "$baseUrl/rest/v1/chat_friendships?or=(and(sender_id.eq.$myUserId,receiver_id.eq.$targetUserId),and(sender_id.eq.$targetUserId,receiver_id.eq.$myUserId))"
            val req = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        }
    }

    /**
     * Dapatkan daftar permintaan pertemanan masuk (Pending requests)
     */
    suspend fun getPendingFriendRequests(myUserId: String): Result<List<ChatUser>> = withContext(Dispatchers.IO) {
        runCatching {
            if (myUserId.isBlank()) return@runCatching emptyList()
            val token = authManager.accessToken ?: anonKey
            val url = "$baseUrl/rest/v1/chat_friendships?receiver_id=eq.$myUserId&status=eq.pending&order=created_at.desc&limit=50"
            val req = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            val senderIds = mutableListOf<String>()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val bodyStr = resp.body?.string().orEmpty()
                val arr = runCatching { JSONArray(bodyStr) }.getOrNull() ?: return@use
                for (i in 0 until arr.length()) {
                    val sid = arr.optJSONObject(i)?.optString("sender_id").orEmpty()
                    if (sid.isNotBlank()) senderIds.add(sid)
                }
            }

            if (senderIds.isEmpty()) return@runCatching emptyList()

            val inQuery = senderIds.distinct().joinToString(",")
            val userUrl = "$baseUrl/rest/v1/user_stats?id=in.($inQuery)&select=id,name,profile_url,total_listen_ms,border_style"
            val userReq = Request.Builder()
                .url(userUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            httpClient.newCall(userReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val bodyStr = resp.body?.string().orEmpty()
                val arr = runCatching { JSONArray(bodyStr) }.getOrNull() ?: return@use emptyList()
                val result = mutableListOf<ChatUser>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val name = obj.optString("name", "IceBeats User")
                    val profileUrl = obj.optString("profile_url").takeIf { it.isNotBlank() && it != "null" }
                    val totalMs = obj.optLong("total_listen_ms", 0L)
                    val rank = com.valora.icebeats.ui.component.icebeatsRank.fromHours((totalMs / 3600000L).toInt())
                    val bStyle = obj.optString("border_style").takeIf { it.isNotBlank() && it != "null" }
                    result.add(ChatUser(id = id, name = name, profileUrl = profileUrl, totalListenMs = totalMs, rank = rank, borderStyle = bStyle))
                }
                result
            }
        }
    }

    /**
     * Dapatkan daftar teman yang sudah disetujui (Friends)
     */
    suspend fun getFriendsList(myUserId: String): Result<List<ChatUser>> = withContext(Dispatchers.IO) {
        runCatching {
            if (myUserId.isBlank()) return@runCatching emptyList()
            val token = authManager.accessToken ?: anonKey
            val url = "$baseUrl/rest/v1/chat_friendships?or=(sender_id.eq.$myUserId,receiver_id.eq.$myUserId)&status=eq.accepted&order=updated_at.desc&limit=100"
            val req = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            val friendIds = mutableListOf<String>()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val bodyStr = resp.body?.string().orEmpty()
                val arr = runCatching { JSONArray(bodyStr) }.getOrNull() ?: return@use
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val sId = obj.optString("sender_id")
                    val rId = obj.optString("receiver_id")
                    val other = if (sId == myUserId) rId else sId
                    if (other.isNotBlank() && other != myUserId) friendIds.add(other)
                }
            }

            if (friendIds.isEmpty()) return@runCatching emptyList()

            val inQuery = friendIds.distinct().joinToString(",")
            val userUrl = "$baseUrl/rest/v1/user_stats?id=in.($inQuery)&select=id,name,profile_url,total_listen_ms,border_style"
            val userReq = Request.Builder()
                .url(userUrl)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            httpClient.newCall(userReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val bodyStr = resp.body?.string().orEmpty()
                val arr = runCatching { JSONArray(bodyStr) }.getOrNull() ?: return@use emptyList()
                val result = mutableListOf<ChatUser>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val name = obj.optString("name", "IceBeats User")
                    val profileUrl = obj.optString("profile_url").takeIf { it.isNotBlank() && it != "null" }
                    val totalMs = obj.optLong("total_listen_ms", 0L)
                    val rank = com.valora.icebeats.ui.component.icebeatsRank.fromHours((totalMs / 3600000L).toInt())
                    val bStyle = obj.optString("border_style").takeIf { it.isNotBlank() && it != "null" }
                    result.add(ChatUser(id = id, name = name, profileUrl = profileUrl, totalListenMs = totalMs, rank = rank, borderStyle = bStyle))
                }
                result
            }
        }
    }

    /**
     * Dapatkan detail profil user dari user_stats
     */
    suspend fun getUserProfile(userId: String): Result<ChatUser> = withContext(Dispatchers.IO) {
        runCatching {
            if (userId.isBlank()) throw IllegalArgumentException("User ID kosong")
            val token = authManager.accessToken ?: anonKey
            val url = "$baseUrl/rest/v1/user_stats?id=eq.$userId&select=id,name,profile_url,total_listen_ms,border_style&limit=1"
            val req = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                val arr = runCatching { JSONArray(bodyStr) }.getOrNull()
                val obj = arr?.optJSONObject(0) ?: throw Exception("User tidak ditemukan")
                val id = obj.optString("id")
                val name = obj.optString("name", "IceBeats User")
                val profileUrl = obj.optString("profile_url").takeIf { it.isNotBlank() && it != "null" }
                val totalMs = obj.optLong("total_listen_ms", 0L)
                val rank = com.valora.icebeats.ui.component.icebeatsRank.fromHours((totalMs / 3600000L).toInt())
                val bStyle = obj.optString("border_style").takeIf { it.isNotBlank() && it != "null" }
                ChatUser(id = id, name = name, profileUrl = profileUrl, totalListenMs = totalMs, rank = rank, borderStyle = bStyle)
            }
        }
    }

    /**
     * Dapatkan daftar playlist publik dari user_playlists
     */
    suspend fun getUserPublicPlaylists(userId: String): Result<List<UserPublicPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            if (userId.isBlank()) return@runCatching emptyList()
            val token = authManager.accessToken ?: anonKey
            val url = "$baseUrl/rest/v1/user_playlists?user_id=eq.$userId&order=updated_at.desc&limit=20"
            val req = Request.Builder()
                .url(url)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val bodyStr = resp.body?.string().orEmpty()
                val arr = runCatching { JSONArray(bodyStr) }.getOrNull() ?: return@use emptyList()
                val list = mutableListOf<UserPublicPlaylist>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val pid = obj.optString("playlist_id", obj.optString("id"))
                    val name = obj.optString("name", "Playlist")
                    val songCount = obj.optInt("song_count", 0)
                    val updatedAt = obj.optString("updated_at", "")
                    list.add(UserPublicPlaylist(playlistId = pid, name = name, songCount = songCount, updatedAt = updatedAt))
                }
                list
            }
        }
    }
}

// ==============================================================================
// DATA CLASSES UNTUK CHAT, TEMAN & BERBAGI MUSIK
// ==============================================================================

enum class FriendshipStatus {
    NONE,            // Belum berteman
    PENDING_SENT,    // Permintaan pertemanan terkirim oleh saya, menunggu respon
    PENDING_RECEIVED,// Ada permintaan pertemanan dari pengguna ini untuk saya
    FRIENDS          // Resmi berteman
}

data class UserPublicPlaylist(
    val playlistId: String,
    val name: String,
    val songCount: Int = 0,
    val updatedAt: String = ""
)

data class ChatUser(
    val id: String,
    val name: String,
    val profileUrl: String? = null,
    val totalListenMs: Long = 0L,
    val rank: com.valora.icebeats.ui.component.icebeatsRank? = null,
    val borderStyle: String? = null
)

data class ChatSharedMedia(
    val songId: String,
    val title: String,
    val artistName: String,
    val albumName: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Int = 0
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val receiverId: String,
    val messageType: String, // "text" atau "music"
    val content: String,
    val mediaData: ChatSharedMedia? = null,
    val isRead: Boolean = false,
    val createdAt: String = ""
)

data class ChatConversation(
    val id: String,
    val otherUser: ChatUser,
    val lastMessage: String = "",
    val lastMessageAt: String = "",
    val unreadCount: Int = 0
)