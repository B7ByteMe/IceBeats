package com.valora.icebeats.ui.component

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rankDataStore by preferencesDataStore("rank_badge_prefs")

@Singleton
class RankPreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun getCurrentUserId(): String {
        val nameManager = com.valora.icebeats.utils.NamePreferenceManager(context)
        return com.valora.icebeats.utils.IceBeatsStatsCloudSync.resolveStableUserIdBlocking(context, nameManager)
    }

    private fun displayedKey(uid: String) = stringPreferencesKey("displayed_rank_${uid}")
    private fun highestEarnedKey(uid: String) = stringPreferencesKey("highest_earned_rank_${uid}")
    private val legacyDisplayedKey = stringPreferencesKey("displayed_rank")
    private val legacyLastSeenKey = stringPreferencesKey("last_seen_rank")

    /** The badge the user chose to display (null = auto = real rank). Scoped per user ID. */
    val displayedRank: Flow<icebeatsRank?> = context.rankDataStore.data.map { prefs ->
        val uid = getCurrentUserId()
        val raw = prefs[displayedKey(uid)] ?: if (uid.startsWith("device-")) prefs[legacyDisplayedKey] else null
        raw?.let { runCatching { icebeatsRank.valueOf(it) }.getOrNull() }
    }

    /** The highest rank earned by this account. Scoped per user ID. */
    val highestEarnedRank: Flow<icebeatsRank?> = context.rankDataStore.data.map { prefs ->
        val uid = getCurrentUserId()
        val raw = prefs[highestEarnedKey(uid)] ?: if (uid.startsWith("device-")) prefs[legacyLastSeenKey] else null
        raw?.let { runCatching { icebeatsRank.valueOf(it) }.getOrNull() }
    }

    /** The highest rank the user has been notified about / unlocked (used for rank-up popup). */
    val lastSeenRank: Flow<icebeatsRank?> = highestEarnedRank

    suspend fun saveDisplayedRank(rank: icebeatsRank?) {
        val uid = getCurrentUserId()
        context.rankDataStore.edit { prefs ->
            if (rank != null) {
                prefs[displayedKey(uid)] = rank.name
                if (uid.startsWith("device-")) prefs[legacyDisplayedKey] = rank.name
            } else {
                prefs.remove(displayedKey(uid))
                if (uid.startsWith("device-")) prefs.remove(legacyDisplayedKey)
            }
        }
    }

    suspend fun saveHighestEarnedRank(rank: icebeatsRank) {
        val uid = getCurrentUserId()
        context.rankDataStore.edit { prefs ->
            val existing = prefs[highestEarnedKey(uid)]?.let { runCatching { icebeatsRank.valueOf(it) }.getOrNull() }
            if (existing == null || rank.ordinal > existing.ordinal) {
                prefs[highestEarnedKey(uid)] = rank.name
                if (uid.startsWith("device-")) prefs[legacyLastSeenKey] = rank.name
            }
        }
    }

    suspend fun saveLastSeenRank(rank: icebeatsRank) {
        saveHighestEarnedRank(rank)
    }

    suspend fun resetAll() {
        val uid = getCurrentUserId()
        context.rankDataStore.edit { prefs ->
            prefs.remove(displayedKey(uid))
            prefs.remove(highestEarnedKey(uid))
            prefs.remove(legacyDisplayedKey)
            prefs.remove(legacyLastSeenKey)
        }
    }
}
