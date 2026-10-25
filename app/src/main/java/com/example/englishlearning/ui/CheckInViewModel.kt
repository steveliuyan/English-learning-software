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
        // 批 1 Task 6：我的页三格统计要的是**数字**（页面用自己的 Headline 排版），
        // 不从 streakText / totalsText 里反解析——同一份快照喂两个面，文本与数字同源。
        val streakDays: Int = 0,
        val learnedWordCount: Int = 0,
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

    /**
     * 读一次打卡快照。
     *
     * [keepReady]：批 1 Task 6 的预加载口径——我的页与学习页会在切 tab 时各调一次，
     * 这时**不许**先把状态打回 `Loading`（页头连续天数与统计格会闪成 0，等于先撒谎再改口），
     * 失败时也保留上一份 Ready（页面停留在上一次的真数据，而不是整页换成不可用）。
     * 打卡页自己的显式加载仍走默认值 `false`：那时重读就该显示进度。
     */
    fun load(profileId: String, keepReady: Boolean = false) {
        val previous = _uiState.value
        if (!keepReady) _uiState.value = CheckInUiState.Loading
        viewModelScope.launch {
            val today = clock.instant().atZone(clock.zoneId()).toLocalDate()
            val month = YearMonth.from(today)
            val from = month.atDay(1)
            val to = month.atEndOfMonth()
            fun unavailable(): CheckInUiState =
                if (keepReady && previous is CheckInUiState.Ready) previous else CheckInUiState.Unavailable
            try {
                val result = repository.range(profileId, from, to)
                if (result.isFailure) {
                    _uiState.value = unavailable()
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
                        _uiState.value = unavailable()
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
                            streakDays = streak,
                            learnedWordCount = totals.learnedWordCount,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.value = unavailable()
            }
        }
    }
}
