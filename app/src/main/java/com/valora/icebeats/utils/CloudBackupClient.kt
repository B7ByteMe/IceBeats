package com.valora.icebeats.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class CloudBackupClient {
    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    // Create a secure salted hash identifier from email to prevent guessing/enumeration
    private fun getHashedFolder(email: String): String {
        val cleanEmail = email.trim().lowercase()
        val salted = "icebeats_salt_2026_${cleanEmail}"
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(salted.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // Legacy format for backward compatibility
    private fun getLegacyEmailFolder(email: String): String {
        return email.trim().lowercase().replace("@", "_at_").replace(".", "_dot_")
    }

    suspend fun checkBackupExists(email: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val hashed = getHashedFolder(email)
                val legacy = getLegacyEmailFolder(email)

                // 1. Try legacy URL if configured
                if (BASE_URL.isNotBlank() && BASE_URL.startsWith("http")) {
                    val fileName = "icebeats/backups/$legacy/icebeats_backup.backup"
                    val request =
                        Request
                            .Builder()
                            .url("$BASE_URL/download?file=$fileName&_t=${System.currentTimeMillis()}")
                            .header("Cache-Control", "no-cache")
                            .get()
                            .build()
                    val exists = client.newCall(request).execute().use { response ->
                        response.isSuccessful && (response.body?.contentLength() ?: 0L) != 0L
                    }
                    if (exists) return@runCatching true
                }

                // 2. Check Supabase Storage (first try secure hashed name)
                val hashedFileName = "icebeats_backup_$hashed.backup"
                val hashedUrl = "${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/backups/$hashedFileName"
                val request = Request.Builder()
                    .url(hashedUrl)
                    .header("apikey", com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY}")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                val hashedExists = client.newCall(request).execute().use { response ->
                    response.isSuccessful && (response.body?.contentLength() ?: 0L) != 0L
                }
                if (hashedExists) return@runCatching true

                // 3. Fallback check for legacy file name
                val legacyFileName = "icebeats_backup_$legacy.backup"
                val legacyUrl = "${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/backups/$legacyFileName"
                val legacyRequest = Request.Builder()
                    .url(legacyUrl)
                    .header("apikey", com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY}")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                client.newCall(legacyRequest).execute().use { response ->
                    response.isSuccessful && (response.body?.contentLength() ?: 0L) != 0L
                }
            }.getOrDefault(false)
        }

    suspend fun uploadBackup(email: String, name: String, backupFile: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val hashed = getHashedFolder(email)
                val legacy = getLegacyEmailFolder(email)

                // 1. If legacy URL configured, upload there
                if (BASE_URL.isNotBlank() && BASE_URL.startsWith("http")) {
                    val detailsJson = JSONObject()
                        .put("email", email)
                        .put("name", name)
                        .put("backupFile", "icebeats_backup.backup")
                        .put("backupSize", backupFile.length())
                        .put("lastBackupAt", System.currentTimeMillis())

                    val detailsRequest = Request.Builder()
                        .url("$BASE_URL/write?file=icebeats/backups/$legacy/details.json")
                        .addHeader("X-API-Key", API_KEY)
                        .post(detailsJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    runCatching { client.newCall(detailsRequest).execute().close() }

                    val backupRequest = Request.Builder()
                        .url("$BASE_URL/upload?file=icebeats/backups/$legacy/icebeats_backup.backup")
                        .addHeader("X-API-Key", API_KEY)
                        .post(backupFile.asRequestBody("application/octet-stream".toMediaType()))
                        .build()
                    val success = client.newCall(backupRequest).execute().use { it.isSuccessful }
                    if (success) return@runCatching true
                }

                // 2. Upload to Supabase Storage using secure hashed name
                val supabaseAnonKey = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY
                val supabaseBase = com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL

                // 2a. Upload details json to Supabase
                val detailsJson = JSONObject()
                    .put("email", email)
                    .put("name", name)
                    .put("backupFile", "icebeats_backup_$hashed.backup")
                    .put("backupSize", backupFile.length())
                    .put("lastBackupAt", System.currentTimeMillis())

                val detailsUrl = "$supabaseBase/storage/v1/object/backups/details_$hashed.json"
                val detailsRequest = Request.Builder()
                    .url(detailsUrl)
                    .header("apikey", supabaseAnonKey)
                    .header("Authorization", "Bearer $supabaseAnonKey")
                    .header("x-upsert", "true")
                    .post(detailsJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                runCatching { client.newCall(detailsRequest).execute().close() }

                // 2b. Upload backup zip file to Supabase
                val backupUrl = "$supabaseBase/storage/v1/object/backups/icebeats_backup_$hashed.backup"
                val backupRequest = Request.Builder()
                    .url(backupUrl)
                    .header("apikey", supabaseAnonKey)
                    .header("Authorization", "Bearer $supabaseAnonKey")
                    .header("x-upsert", "true")
                    .post(backupFile.asRequestBody("application/octet-stream".toMediaType()))
                    .build()

                client.newCall(backupRequest).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun downloadBackup(email: String, destFile: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val hashed = getHashedFolder(email)
                val legacy = getLegacyEmailFolder(email)

                // 1. Try legacy URL first if configured
                if (BASE_URL.isNotBlank() && BASE_URL.startsWith("http")) {
                    val fileName = "icebeats/backups/$legacy/icebeats_backup.backup"
                    val request = Request.Builder()
                        .url("$BASE_URL/download?file=$fileName&_t=${System.currentTimeMillis()}")
                        .header("Cache-Control", "no-cache")
                        .get()
                        .build()

                    val legacySuccess = client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body
                            if (body != null) {
                                FileOutputStream(destFile).use { fos ->
                                    body.byteStream().use { stream -> stream.copyTo(fos) }
                                }
                                true
                            } else false
                        } else false
                    }
                    if (legacySuccess) return@runCatching true
                }

                // 2. Download from Supabase Storage (try secure hashed name first)
                val hashedFileName = "icebeats_backup_$hashed.backup"
                val hashedUrl = "${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/backups/$hashedFileName"
                val hashedRequest = Request.Builder()
                    .url(hashedUrl)
                    .header("apikey", com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY}")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                val hashedSuccess = client.newCall(hashedRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) {
                            FileOutputStream(destFile).use { fos ->
                                body.byteStream().use { stream -> stream.copyTo(fos) }
                            }
                            true
                        } else false
                    } else false
                }
                if (hashedSuccess) return@runCatching true

                // 3. Fallback download using legacy name if hashed file was not found
                val legacyFileName = "icebeats_backup_$legacy.backup"
                val legacyUrl = "${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/backups/$legacyFileName"
                val legacyRequest = Request.Builder()
                    .url(legacyUrl)
                    .header("apikey", com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer ${com.valora.icebeats.supabase.SupabaseConfig.SUPABASE_ANON_KEY}")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                client.newCall(legacyRequest).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching false
                    val body = response.body ?: return@runCatching false
                    FileOutputStream(destFile).use { fos ->
                        body.byteStream().use { stream ->
                            stream.copyTo(fos)
                        }
                    }
                    true
                }
            }.getOrDefault(false)
        }

    private companion object {
        val BASE_URL = com.valora.icebeats.BuildConfig.STATS_BASE_URL
        val API_KEY = com.valora.icebeats.BuildConfig.STATS_API_KEY
    }
}
