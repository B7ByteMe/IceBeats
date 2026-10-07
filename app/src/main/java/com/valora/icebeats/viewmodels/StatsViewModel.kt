package com.valora.icebeats.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.constants.statToPeriod
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.ui.component.AvatarPreferenceManager
import com.valora.icebeats.ui.component.AvatarSelection
import com.valora.icebeats.ui.component.NamePreferenceManager
import com.valora.icebeats.ui.screens.OptionStats
import com.valora.icebeats.utils.icebeatsStatsCloudClient
import com.valora.icebeats.utils.icebeatsStatsCloudSync
import com.valora.icebeats.utils.GlobalStatsBoard
import com.valora.icebeats.utils.LocalStatsUpload
import com.valora.icebeats.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

import kotlinx.coroutines.flow.Flow
import com.valora.icebeats.ui.component.icebeatsRank

data class GlobalStatsUiState(
    val isLoading: Boolean = true,
    val board: GlobalStatsBoard = GlobalStatsBoard(),
    val error: String? = null,
    val currentUserId: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
@Inject
constructor(
    val database: MusicDatabase,
    @ApplicationContext private val context: Context,
    private val namePreferenceManager: NamePreferenceManager,
) : ViewModel() {
    val selectedOption = MutableStateFlow(OptionStats.CONTINUOUS)
    val indexChips = MutableStateFlow(0)
    val globalStats = MutableStateFlow(GlobalStatsUiState())

    val totalListenHours: Flow<Double> = database.mostPlayedSongsStats(0L, limit = -1, toTimeStamp = Long.MAX_VALUE)
        .map { songs ->
            val totalMs = songs.sumOf { it.timeListened?.toLong() ?: 0L }
            totalMs.toDouble() / (3600.0 * 1000.0)
        }

    val currentRank: Flow<icebeatsRank?> = totalListenHours.map { hours ->
        if (hours >= 1.0) icebeatsRank.fromHours(hours.toInt()) else null
    }

    private val cloudClient = icebeatsStatsCloudClient()
    private val statsPreferences =
        context.getSharedPreferences("icebeats_global_stats", Context.MODE_PRIVATE)

    val mostPlayedSongsStats =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedSongsStats(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                            if (selection == OptionStats.CONTINUOUS || t == 0) {
                                LocalDateTime
                                    .now()
                                    .toInstant(
                                        ZoneOffset.UTC,
                                    ).toEpochMilli()
                            } else {
                                statToPeriod(selection, t - 1)
                            },
                    )
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedSongs =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedSongs(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                            if (selection == OptionStats.CONTINUOUS || t == 0) {
                                LocalDateTime
                                    .now()
                                    .toInstant(
                                        ZoneOffset.UTC,
                                    ).toEpochMilli()
                            } else {
                                statToPeriod(selection, t - 1)
                            },
                    )
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedArtists =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database
                    .mostPlayedArtists(
                        statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp =
                            if (selection == OptionStats.CONTINUOUS || t == 0) {
                                LocalDateTime
                                    .now()
                                    .toInstant(
                                        ZoneOffset.UTC,
                                    ).toEpochMilli()
                            } else {
                                statToPeriod(selection, t - 1)
                            },
                    ).map { artists ->
                        artists.filter { it.artist.isYouTubeArtist }
                    }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mostPlayedAlbums =
        combine(
            selectedOption,
            indexChips,
        ) { first, second -> Pair(first, second) }
            .flatMapLatest { (selection, t) ->
                database.mostPlayedAlbums(
                    statToPeriod(selection, t),
                    limit = -1,
                    toTimeStamp =
                        if (selection == OptionStats.CONTINUOUS || t == 0) {
                            LocalDateTime
                                .now()
                                .toInstant(
                                    ZoneOffset.UTC,
                                ).toEpochMilli()
                        } else {
                            statToPeriod(selection, t - 1)
                        },
                )
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val firstEvent =
        database
            .firstEvent()
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    init {
        viewModelScope.launch {
            syncAndLoadGlobalStats()
        }
        viewModelScope.launch {
            mostPlayedArtists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
        viewModelScope.launch {
            mostPlayedAlbums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
    }

    fun markWeeklyPopupSeen() {
        statsPreferences.edit().putString(KEY_LAST_WEEKLY_POPUP, currentWeekKey()).apply()
    }

    fun shouldShowWeeklyPopup(): Boolean =
        statsPreferences.getString(KEY_LAST_WEEKLY_POPUP, "") != currentWeekKey()

    fun refreshGlobalStats() {
        viewModelScope.launch {
            syncAndLoadGlobalStats(forceUpload = false)
        }
    }

    private suspend fun syncAndLoadGlobalStats(forceUpload: Boolean = false) {
        globalStats.value = globalStats.value.copy(isLoading = true, error = null)
        val userId = icebeatsStatsCloudSync.resolveStableUserId(context, namePreferenceManager, statsPreferences)
        if (forceUpload || shouldUploadToday()) {
            val upload = runCatching { buildUpload(userId) }.getOrNull()
            if (upload != null) {
                val authToken = com.valora.icebeats.supabase.SupabaseAuthManager.getInstance(context).accessToken
                val uploadResult = cloudClient.uploadDaily(upload, authToken)
                if (uploadResult.isSuccess) {
                    val board = uploadResult.getOrThrow()
                    // Pastikan HP menyimpan rekor skor tertinggi dari server agar tidak ter-reset
                    val myCloudStats = board.users.find { it.id == userId }
                    if (myCloudStats != null) {
                        val userKey = "saved_max_total_listen_ms_${userId}"
                        val currentSaved = statsPreferences.getLong(userKey, statsPreferences.getLong("saved_max_total_listen_ms", 0L))
                        if (myCloudStats.totalListenMs > currentSaved) {
                            statsPreferences.edit()
                                .putLong(userKey, myCloudStats.totalListenMs)
                                .putLong("saved_max_total_listen_ms", myCloudStats.totalListenMs)
                                .apply()
                        }
                    }
                    statsPreferences.edit().putString(KEY_LAST_UPLOAD_DAY, LocalDate.now().toString()).apply()
                    globalStats.value =
                        GlobalStatsUiState(
                            isLoading = false,
                            board = board,
                            currentUserId = userId,
                        )
                    return
                }
                // If upload failed, continue below to readBoard so leaderboard is not lost
            }
        }

        cloudClient
            .readBoard()
            .onSuccess { board ->
                // Sinkronkan juga skor tertinggi dari cloud jika readBoard dipanggil
                val myCloudStats = board.users.find { it.id == userId }
                if (myCloudStats != null) {
                    val userKey = "saved_max_total_listen_ms_${userId}"
                    val currentSaved = statsPreferences.getLong(userKey, statsPreferences.getLong("saved_max_total_listen_ms", 0L))
                    if (myCloudStats.totalListenMs > currentSaved) {
                        statsPreferences.edit()
                            .putLong(userKey, myCloudStats.totalListenMs)
                            .putLong("saved_max_total_listen_ms", myCloudStats.totalListenMs)
                            .apply()
                    }
                }
                globalStats.value =
                    GlobalStatsUiState(
                        isLoading = false,
                        board = board,
                        currentUserId = userId,
                    )
            }.onFailure { error ->
                globalStats.value =
                    globalStats.value.copy(
                        isLoading = false,
                        error = error.message,
                        currentUserId = userId,
                    )
            }
    }

    private suspend fun buildUpload(userId: String): LocalStatsUpload? {
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
        val userKey = "saved_max_total_listen_ms_${userId}"
        val anchorKey = "last_local_anchor_ms_${userId}"
        val savedTotalMs = statsPreferences.getLong(userKey, statsPreferences.getLong("saved_max_total_listen_ms", 0L))
        val lastLocalAnchorMs = statsPreferences.getLong(anchorKey, statsPreferences.getLong("last_local_anchor_ms", calculatedTotalMs))

        // Hitung selisih waktu mendengarkan baru sejak sinkronisasi terakhir
        val localDeltaMs = if (calculatedTotalMs >= lastLocalAnchorMs) {
            calculatedTotalMs - lastLocalAnchorMs
        } else {
            0L
        }

        // Skor akhir: nilai tertinggi (dari server/boost) DITAMBAH waktu lagu yang baru diputar di HP
        val totalListenMs = if (savedTotalMs > calculatedTotalMs) {
            savedTotalMs + localDeltaMs
        } else {
            calculatedTotalMs
        }

        // Perbarui rekor dan titik acuan lokal
        statsPreferences.edit()
            .putLong(userKey, totalListenMs)
            .putLong("saved_max_total_listen_ms", totalListenMs)
            .putLong(anchorKey, calculatedTotalMs)
            .putLong("last_local_anchor_ms", calculatedTotalMs)
            .apply()

        val weeklyListenMs = weekSongs.sumOf { it.timeListened?.toLong() ?: 0L }
        val name = namePreferenceManager.userName.first().ifBlank { android.os.Build.MODEL ?: "icebeats User" }
        val email = namePreferenceManager.accountEmail.first().trim().lowercase().takeIf { it.isNotBlank() }
        val profileUrl =
            when (val avatar = AvatarPreferenceManager(context).getAvatarSelection.first()) {
                is AvatarSelection.DiceBear -> avatar.url
                is AvatarSelection.Custom -> avatar.cloudUrl
                else -> null
            }
        var fcmToken: String? = null
        try {
            fcmToken = kotlinx.coroutines.withTimeoutOrNull(2000L) {
                suspendCancellableCoroutine<String?> { continuation ->
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (continuation.isActive) {
                            continuation.resume(if (task.isSuccessful) task.result else null)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            fcmToken = null
        }
        if (fcmToken == null) {
            fcmToken = "n/v"
        }

        return LocalStatsUpload(
            userId = userId,
            name = name,
            profileUrl = profileUrl,
            email = email,
            totalListenMs = totalListenMs,
            weeklyListenMs = weeklyListenMs,
            fcmToken = fcmToken,
        )
    }

    private fun shouldUploadToday(): Boolean = true

    private fun currentWeekKey(): String {
        val date = LocalDate.now()
        val fields = WeekFields.of(Locale.getDefault())
        return "${date.get(fields.weekBasedYear())}-${date.get(fields.weekOfWeekBasedYear())}"
    }

    private companion object {
        const val KEY_USER_ID = "global_stats_user_id"
        const val KEY_LAST_UPLOAD_DAY = "last_global_stats_upload_day"
        const val KEY_LAST_WEEKLY_POPUP = "last_weekly_global_popup"
    }
}
