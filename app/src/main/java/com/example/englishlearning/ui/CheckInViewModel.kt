package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.LearningStatsRepository
import com.example.englishlearning.learning.domain.DailyLearningStats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
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
            val month = YearMonth.from(today)
            val from = month.atDay(1)
            val to = month.atEndOfMonth()
            val result = repository.range(profileId, from, to)
            if (result.isFailure) {
                _uiState.value = CheckInUiState.Unavailable
            } else {
                val byDate = result.getOrThrow().associateBy { it.localDate }
                fun stats(date: LocalDate): DailyLearningStats = byDate[date]
                    ?: DailyLearningStats(date, 0, 0, 0, 0)
                val monthStats = (0 until month.lengthOfMonth()).map { stats(from.plusDays(it.toLong())) }
                val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1L)
                val weekStats = (0..6).map { stats(weekStart.plusDays(it.toLong())) }
                val todayStats = stats(today)
                val completed = todayStats.reviewedWordCount > 0 ||
                    todayStats.completedReadingCount > 0 ||
                    (todayStats.targetTaskCount > 0 && todayStats.completedTaskCount >= todayStats.targetTaskCount)
                _uiState.value = CheckInUiState.Ready(todayStats, weekStats, monthStats, completed)
            }
        }
    }
}
