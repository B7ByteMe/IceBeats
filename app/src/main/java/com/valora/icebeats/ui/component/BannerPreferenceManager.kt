package com.valora.icebeats.ui.component

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.bannerDataStore: DataStore<Preferences> by preferencesDataStore(name = "banner_preferences")

/**
 * Manages custom animated GIF banner per user account.
 * Guarantees strict isolation so changing a banner on Account A never affects Account B.
 */
class BannerPreferenceManager(private val context: Context) {
    private fun getCurrentUserId(): String {
        val nameManager = NamePreferenceManager(context)
        return com.valora.icebeats.utils.IceBeatsStatsCloudSync.resolveStableUserIdBlocking(context, nameManager)
    }

    private fun userBannerKey(uid: String) = stringPreferencesKey("custom_banner_url_${uid}")
    private val legacyBannerKey = stringPreferencesKey("custom_banner_url")

    /**
     * Flow of banner URL for current active user.
     */
    val bannerUrl: Flow<String?> = context.bannerDataStore.data.map { preferences ->
        val uid = getCurrentUserId()
        val url = if (!uid.startsWith("device-")) {
            preferences[userBannerKey(uid)]
        } else {
            preferences[userBannerKey(uid)] ?: preferences[legacyBannerKey]
        }
        url?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Flow of banner URL for a specific user ID.
     */
    fun getBannerForUser(uid: String): Flow<String?> = context.bannerDataStore.data.map { preferences ->
        val url = if (!uid.startsWith("device-")) {
            preferences[userBannerKey(uid)]
        } else {
            preferences[userBannerKey(uid)] ?: preferences[legacyBannerKey]
        }
        url?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun saveBannerUrl(url: String?) {
        val uid = getCurrentUserId()
        saveBannerForUser(uid, url)
    }

    suspend fun saveBannerForUser(uid: String, url: String?) {
        context.bannerDataStore.edit { preferences ->
            val cleanUrl = url?.trim()
            if (cleanUrl.isNullOrBlank()) {
                preferences.remove(userBannerKey(uid))
                if (uid.startsWith("device-")) {
                    preferences.remove(legacyBannerKey)
                }
            } else {
                preferences[userBannerKey(uid)] = cleanUrl
                if (uid.startsWith("device-")) {
                    preferences[legacyBannerKey] = cleanUrl
                }
            }
        }
    }

    suspend fun clearBannerUrl() {
        val uid = getCurrentUserId()
        clearBannerForUser(uid)
    }

    suspend fun clearBannerForUser(uid: String) {
        context.bannerDataStore.edit { preferences ->
            preferences.remove(userBannerKey(uid))
            if (uid.startsWith("device-")) {
                preferences.remove(legacyBannerKey)
            }
        }
    }

    suspend fun resetAll() {
        context.bannerDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
