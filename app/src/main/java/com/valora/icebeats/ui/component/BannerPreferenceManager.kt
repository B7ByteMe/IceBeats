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

class BannerPreferenceManager(private val context: Context) {
    companion object {
        private val BANNER_URL_KEY = stringPreferencesKey("custom_banner_url")
    }

    val bannerUrl: Flow<String?> = context.bannerDataStore.data.map { preferences ->
        preferences[BANNER_URL_KEY]?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun saveBannerUrl(url: String?) {
        context.bannerDataStore.edit { preferences ->
            val cleanUrl = url?.trim()
            if (cleanUrl.isNullOrBlank()) {
                preferences.remove(BANNER_URL_KEY)
            } else {
                preferences[BANNER_URL_KEY] = cleanUrl
            }
        }
    }

    suspend fun clearBannerUrl() {
        context.bannerDataStore.edit { preferences ->
            preferences.remove(BANNER_URL_KEY)
        }
    }
}
