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
import kotlinx.coroutines.CancellationException
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
        // F4-01 基础统计三卡：累计（词数/复习次数）、连续天数、近 7 日完成率条。
        val totalsText: String = "暂无学习记录",
        val streakText: String = "连续学习 0 天",
        val recentDays: List<DailyLearningStats> = emptyList(),
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
        _uiState.value = CheckInUiState.Loading
        viewModelScope.launch {
            val today = clock.instant().atZone(clock.zoneId()).toLocalDate()
            val month = YearMonth.from(today)
            val from = month.atDay(1)
            val to = month.atEndOfMonth()
            try {
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
                        todayStats.completedReadingCount > 0
                    val totalsResult = repository.totals(profileId)
                    val streakResult = repository.streak(profileId, today)
                    val totals = totalsResult.getOrNull()
                    val streak = streakResult.getOrNull()
                    if (totals == null || streak == null) {
                        // 统计口径任一不可得即整页诚实降级，不显示半真数据。
                        _uiState.value = CheckInUiState.Unavailable
                    } else {
                        val totalsText = if (totals.learnedWordCount == 0 && totals.reviewEventCount == 0) {
                            "暂无学习记录"
                        } else {
                            "累计学习 ${totals.learnedWordCount} 词 · 复习 ${totals.reviewEventCount} 次"
                        }
                        _uiState.value = CheckInUiState.Ready(
                            today = todayStats,
                            week = weekStats,
                            month = monthStats,
                            completed = completed,
                            totalsText = totalsText,
                            streakText = "连续学习 $streak 天",
                            recentDays = (6L downTo 0L).map { stats(today.minusDays(it)) },
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.value = CheckInUiState.Unavailable
            }
        }
    }
}
