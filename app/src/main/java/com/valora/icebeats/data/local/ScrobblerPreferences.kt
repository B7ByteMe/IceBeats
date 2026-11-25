package com.valora.icebeats.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.valora.icebeats.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ScrobblerSettings(
    val enabled: Boolean = false,
    val submitNowPlaying: Boolean = true,
    val scrobblePercent: Int = 50,
    val selectedPackages: Set<String> = emptySet(),
)

@Singleton
class ScrobblerPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("scrobbler_enabled")
        val SUBMIT_NOW_PLAYING = booleanPreferencesKey("scrobbler_now_playing")
        val PERCENT = intPreferencesKey("scrobbler_percent")
        val PACKAGES = stringSetPreferencesKey("scrobbler_packages")
    }

    val settings: Flow<ScrobblerSettings> = context.dataStore.data
        .map { p ->
            ScrobblerSettings(
                enabled = p[Keys.ENABLED] ?: false,
                submitNowPlaying = p[Keys.SUBMIT_NOW_PLAYING] ?: true,
                scrobblePercent = (p[Keys.PERCENT] ?: 50).coerceIn(25, 90),
                selectedPackages = p[Keys.PACKAGES] ?: emptySet(),
            )
        }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setSubmitNowPlaying(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SUBMIT_NOW_PLAYING] = enabled }
    }

    suspend fun setScrobblePercent(percent: Int) {
        context.dataStore.edit { it[Keys.PERCENT] = percent.coerceIn(25, 90) }
    }

    suspend fun setSelectedPackages(packages: Set<String>) {
        context.dataStore.edit { it[Keys.PACKAGES] = packages }
    }

    suspend fun togglePackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PACKAGES] ?: emptySet()
            prefs[Keys.PACKAGES] = if (packageName in current) current - packageName else current + packageName
        }
    }
}
