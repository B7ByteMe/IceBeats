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
    companion object {
        private val DISPLAYED_RANK_KEY = stringPreferencesKey("displayed_rank")
        private val LAST_SEEN_RANK_KEY = stringPreferencesKey("last_seen_rank")
        private val HIGHEST_EARNED_RANK_KEY = stringPreferencesKey("highest_earned_rank")
    }

    /** The badge the user chose to display (null = auto = real rank). */
    val displayedRank: Flow<IceBeatsRank?> = context.rankDataStore.data.map { prefs ->
        prefs[DISPLAYED_RANK_KEY]?.let { runCatching { IceBeatsRank.valueOf(it) }.getOrNull() }
    }

    /** The highest rank the user has earned / been notified about. */
    val highestEarnedRank: Flow<IceBeatsRank?> = context.rankDataStore.data.map { prefs ->
        (prefs[HIGHEST_EARNED_RANK_KEY] ?: prefs[LAST_SEEN_RANK_KEY])
            ?.let { runCatching { IceBeatsRank.valueOf(it) }.getOrNull() }
    }

    /** The highest rank the user has been notified about (used for rank-up popup). */
    val lastSeenRank: Flow<IceBeatsRank?> = context.rankDataStore.data.map { prefs ->
        prefs[LAST_SEEN_RANK_KEY]?.let { runCatching { IceBeatsRank.valueOf(it) }.getOrNull() }
    }

    suspend fun saveDisplayedRank(rank: IceBeatsRank?) {
        context.rankDataStore.edit { prefs ->
            if (rank != null) prefs[DISPLAYED_RANK_KEY] = rank.name
            else prefs.remove(DISPLAYED_RANK_KEY)
        }
    }

    suspend fun saveHighestEarnedRank(rank: IceBeatsRank) {
        context.rankDataStore.edit { prefs ->
            val existing = (prefs[HIGHEST_EARNED_RANK_KEY] ?: prefs[LAST_SEEN_RANK_KEY])
                ?.let { runCatching { IceBeatsRank.valueOf(it) }.getOrNull() }
            if (existing == null || rank.ordinal > existing.ordinal) {
                prefs[HIGHEST_EARNED_RANK_KEY] = rank.name
                prefs[LAST_SEEN_RANK_KEY] = rank.name
            }
        }
    }

    suspend fun saveLastSeenRank(rank: IceBeatsRank) {
        context.rankDataStore.edit { prefs ->
            prefs[LAST_SEEN_RANK_KEY] = rank.name
            val existing = prefs[HIGHEST_EARNED_RANK_KEY]
                ?.let { runCatching { IceBeatsRank.valueOf(it) }.getOrNull() }
            if (existing == null || rank.ordinal > existing.ordinal) {
                prefs[HIGHEST_EARNED_RANK_KEY] = rank.name
            }
        }
    }

    suspend fun resetAll() {
        context.rankDataStore.edit { prefs ->
            prefs.remove(DISPLAYED_RANK_KEY)
            prefs.remove(LAST_SEEN_RANK_KEY)
            prefs.remove(HIGHEST_EARNED_RANK_KEY)
        }
    }
}
