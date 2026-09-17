package com.valora.icebeats.utils

import android.content.Context
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.ui.component.AvatarPreferenceManager
import com.valora.icebeats.ui.component.AvatarSelection
import com.valora.icebeats.ui.component.NamePreferenceManager
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

object icebeatsStatsCloudSync {
    suspend fun syncDaily(
        context: Context,
        database: MusicDatabase,
        namePreferenceManager: NamePreferenceManager,
    ): Result<GlobalStatsBoard>? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val userId = resolveStableUserId(context, namePreferenceManager, preferences)
        val upload = buildUpload(context, database, namePreferenceManager, userId) ?: return null
        return icebeatsStatsCloudClient()
            .uploadDaily(upload)
            .onSuccess {
                preferences.edit().putString(KEY_LAST_UPLOAD_DAY, LocalDate.now().toString()).apply()
            }
    }

    suspend fun buildUpload(
        context: Context,
        database: MusicDatabase,
        namePreferenceManager: NamePreferenceManager,
        userId: String,
    ): LocalStatsUpload? {
        val isNameSet = namePreferenceManager.isNameSet.first()
        if (!isNameSet) return null

        val now = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
        val weekStart =
            LocalDate
                .now()
                .with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        val allSongs = database.mostPlayedSongsStats(0L, limit = -1, toTimeStamp = now).first()
        val weekSongs = database.mostPlayedSongsStats(weekStart, limit = -1, toTimeStamp = now).first()
        val calculatedTotalMs = allSongs.sumOf { it.timeListened?.toLong() ?: 0L }
        val weeklyListenMs = weekSongs.sumOf { it.timeListened?.toLong() ?: 0L }
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val savedTotalMs = prefs.getLong("saved_max_total_listen_ms", 0L)
        val totalListenMs = maxOf(calculatedTotalMs, savedTotalMs)
        if (calculatedTotalMs > savedTotalMs) {
            prefs.edit().putLong("saved_max_total_listen_ms", calculatedTotalMs).apply()
        }
        val name = namePreferenceManager.userName.first().ifBlank { android.os.Build.MODEL ?: "icebeats User" }
        val email = namePreferenceManager.accountEmail.first().normalizedEmail()
        val profileUrl =
            when (val avatar = AvatarPreferenceManager(context).getAvatarSelection.first()) {
                is AvatarSelection.DiceBear -> avatar.url
                is AvatarSelection.Custom -> avatar.cloudUrl
                else -> null
            }
        return LocalStatsUpload(
            userId = userId,
            name = name,
            profileUrl = profileUrl,
            email = email,
            totalListenMs = totalListenMs,
            weeklyListenMs = weeklyListenMs,
        )
    }

    fun stableUserId(context: Context): String =
        stableUserId(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

    suspend fun resolveStableUserId(
        context: Context,
        namePreferenceManager: NamePreferenceManager,
        preferences: android.content.SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    ): String {
        val supabaseUid = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context).userId.value.trim()
        if (supabaseUid.isNotBlank()) {
            preferences.edit().putString(KEY_USER_ID, supabaseUid).apply()
            return supabaseUid
        }

        val email = namePreferenceManager.accountEmail.first().normalizedEmail()
        if (!email.isNullOrBlank()) {
            val resolved = "user-${sha256(email)}"
            preferences.edit().putString(KEY_USER_ID, resolved).apply()
            return resolved
        }

        val existing = preferences.getString(KEY_USER_ID, null)
        if (!existing.isNullOrBlank()) return existing

        return stableUserId(preferences)
    }

    fun clearCachedUserId(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().remove(KEY_USER_ID).remove(KEY_LAST_UPLOAD_DAY).apply()
    }

    private fun stableUserId(preferences: android.content.SharedPreferences): String {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "device-" + UUID.randomUUID().toString()
        preferences.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun String?.normalizedEmail(): String? =
        this
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() && it != "null" }

    const val PREFERENCES_NAME = "icebeats_global_stats"
    const val KEY_USER_ID = "global_stats_user_id"
    const val KEY_DEVICE_ID = "global_stats_device_id"
    const val KEY_LAST_UPLOAD_DAY = "last_global_stats_upload_day"
    const val KEY_LAST_WEEKLY_POPUP = "last_weekly_global_popup"
}
