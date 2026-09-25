package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.LearningStatsRepository
import com.example.englishlearning.learning.domain.DailyLearningStats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CheckInUiState {
    data object Loading : CheckInUiState
    data class Ready(
        val today: DailyLearningStats,
        val week: List<DailyLearningStats>,
        val month: List<DailyLearningStats>,
        val completed: Boolean,
    ) : CheckInUiState
    data object Unavailable : CheckInUiState
}

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val repository: LearningStatsRepository,
    private val clock: ClockProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<CheckInUiState>(CheckInUiState.Loading)
    val uiState: StateFlow<CheckInUiState> = _uiState

    fun load(profileId: String) {
        viewModelScope.launch {
            _uiState.value = CheckInUiState.Loading
            val today = clock.instant().atZone(clock.zoneId()).toLocalDate()
            val monthStart = today.withDayOfMonth(1)
            val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
            val result = runCatching { repository.range(profileId, monthStart, monthEnd) }.getOrElse {
                _uiState.value = CheckInUiState.Unavailable
                return@launch
            }
            result.onFailure {
                _uiState.value = CheckInUiState.Unavailable
                return@launch
            }
            val month = result.getOrThrow().sortedBy { it.localDate }
            val byDate = month.associateBy { it.localDate }
            val empty = { date: LocalDate -> DailyLearningStats(date, 0, 0, 0, 0) }
            val todayStats = byDate[today] ?: empty(today)
            val week = (0L..6L).map { today.minusDays(6L - it) }.map { byDate[it] ?: empty(it) }
            _uiState.value = CheckInUiState.Ready(
                today = todayStats,
                week = week,
                month = month,
                completed = todayStats.reviewedWordCount > 0 || todayStats.completedReadingCount > 0,
            )
        }
    }
}
