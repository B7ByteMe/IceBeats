package com.valora.icebeats.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valora.icebeats.innertube.YouTube
import com.valora.icebeats.innertube.pages.HistoryPage
import com.valora.icebeats.constants.HistorySource
import com.valora.icebeats.db.MusicDatabase
import com.valora.icebeats.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import com.valora.icebeats.db.entities.Song
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

enum class HistoryCategory {
    ALL, SONGS, ALBUMS, ARTISTS
}

@HiltViewModel
class HistoryViewModel
@Inject
constructor(
    val database: MusicDatabase,
) : ViewModel() {
    var historySource = MutableStateFlow(HistorySource.LOCAL)

    private val _selectedCategory = MutableStateFlow(HistoryCategory.ALL)
    val selectedCategory: StateFlow<HistoryCategory> = _selectedCategory.asStateFlow()

    fun setCategory(category: HistoryCategory) {
        _selectedCategory.value = category
    }

    private val today = LocalDate.now()
    private val thisMonday = today.with(DayOfWeek.MONDAY)
    private val lastMonday = thisMonday.minusDays(7)

    val historyPage = mutableStateOf<HistoryPage?>(null)

    val events =
        database
            .events()
            .map { events ->
                events
                    .groupBy {
                        val date = it.event.timestamp.toLocalDate()
                        val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
                        when {
                            daysAgo == 0 -> DateAgo.Today
                            daysAgo == 1 -> DateAgo.Yesterday
                            date >= thisMonday -> DateAgo.ThisWeek
                            date >= lastMonday -> DateAgo.LastWeek
                            else -> DateAgo.Other(date.withDayOfMonth(1))
                        }
                    }.toSortedMap(
                        compareBy { dateAgo ->
                            when (dateAgo) {
                                DateAgo.Today -> 0L
                                DateAgo.Yesterday -> 1L
                                DateAgo.ThisWeek -> 2L
                                DateAgo.LastWeek -> 3L
                                is DateAgo.Other -> ChronoUnit.DAYS.between(dateAgo.date, today)
                            }
                        },
                    ).mapValues { entry ->
                        entry.value
                            .sortedByDescending { it.event.timestamp }
                            .distinctBy { it.song.id }
                    }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        fetchRemoteHistory()
    }

    fun clearToday() {
        viewModelScope.launch(Dispatchers.IO) {
            val startOfToday = LocalDate.now().atStartOfDay()
            database.clearHistorySince(startOfToday)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clearListenHistory()
        }
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.deleteEvent(eventId)
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            database.update(song.song.toggleLike())
        }
    }

    fun fetchRemoteHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            YouTube.musicHistory().onSuccess {
                historyPage.value = it
            }.onFailure {
                reportException(it)
            }
        }
    }
}

sealed class DateAgo {
    data object Today : DateAgo()

    data object Yesterday : DateAgo()

    data object ThisWeek : DateAgo()

    data object LastWeek : DateAgo()

    class Other(
        val date: LocalDate,
    ) : DateAgo() {
        override fun equals(other: Any?): Boolean {
            if (other is Other) return date == other.date
            return super.equals(other)
        }

        override fun hashCode(): Int = date.hashCode()
    }
}
