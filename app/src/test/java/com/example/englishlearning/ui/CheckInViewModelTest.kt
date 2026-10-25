package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.LearningStatsRepository
import com.example.englishlearning.learning.domain.DailyLearningStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {
    private val date = LocalDate.of(2026, 9, 25)
    private val clock = FixedClockProvider(date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"))

    @Test
    fun `load derives week month and completion`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stats = (1..30).map { day -> DailyLearningStats(LocalDate.of(2026, 9, day), if (day == 25) 1 else 0, 0, if (day == 25) 1 else 0, 1) }
        val vm = CheckInViewModel(FakeStats(Result.success(stats)), clock)
        vm.load("p"); advanceUntilIdle()
        val state = vm.uiState.value as CheckInUiState.Ready
        assertEquals(7, state.week.size)
        assertEquals(date, state.today.localDate)
        assertEquals(30, state.month.size)
        assertTrue(state.month.zipWithNext().all { (previous, next) -> next.localDate == previous.localDate.plusDays(1) })
        assertTrue(state.completed)
        Dispatchers.resetMain()
    }

    @Test
    fun `sparse month is padded with zero stats and target alone is not completed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val sparse = listOf(
            DailyLearningStats(date, 0, 0, 1, 3),
            DailyLearningStats(date.minusDays(2), 1, 0, 0, 0),
        )
        val vm = CheckInViewModel(FakeStats(Result.success(sparse)), clock)
        vm.load("p"); advanceUntilIdle()
        val state = vm.uiState.value as CheckInUiState.Ready
        assertEquals(30, state.month.size)
        assertEquals(LocalDate.of(2026, 9, 1), state.month.first().localDate)
        assertEquals(0, state.month.first().reviewedWordCount)
        assertEquals(0, state.month.first().completedReadingCount)
        assertEquals(0, state.month.first().completedTaskCount)
        assertEquals(0, state.month.first().targetTaskCount)
        assertEquals(false, state.completed)
        Dispatchers.resetMain()
    }

    @Test
    fun `throwing repository is unavailable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = CheckInViewModel(ThrowingStats(), clock)
        vm.load("p")
        advanceUntilIdle()
        assertEquals(CheckInUiState.Unavailable, vm.uiState.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `failure is unavailable and reload starts loading`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeStats(Result.failure(IllegalStateException()))
        val vm = CheckInViewModel(repository, clock)
        vm.load("p")
        assertEquals(CheckInUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(CheckInUiState.Unavailable, vm.uiState.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `ready exposes cumulative totals streak and last seven days`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stats = (1..30).map { day -> DailyLearningStats(LocalDate.of(2026, 9, day), 1, 0, 1, 2) }
        val repository = FakeStats(Result.success(stats))
        repository.totals = Result.success(com.example.englishlearning.learning.domain.LearningTotals(42, 17))
        repository.streakResult = Result.success(5)
        val vm = CheckInViewModel(repository, clock)
        vm.load("p"); advanceUntilIdle()
        val state = vm.uiState.value as CheckInUiState.Ready
        assertEquals("累计学习 42 词 · 复习 17 次", state.totalsText)
        assertEquals("连续学习 5 天", state.streakText)
        assertEquals(7, state.recentDays.size)
        assertEquals(date, state.recentDays.last().localDate)
        assertEquals(date.minusDays(6), state.recentDays.first().localDate)
        Dispatchers.resetMain()
    }

    @Test
    fun `zero totals read as no learning records yet`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeStats(Result.success(emptyList()))
        repository.totals = Result.success(com.example.englishlearning.learning.domain.LearningTotals(0, 0))
        val vm = CheckInViewModel(repository, clock)
        vm.load("p"); advanceUntilIdle()
        assertEquals("暂无学习记录", (vm.uiState.value as CheckInUiState.Ready).totalsText)
        Dispatchers.resetMain()
    }

    @Test
    fun `totals failure makes the page unavailable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeStats(Result.success(emptyList()))
        repository.totals = Result.failure(IllegalStateException())
        val vm = CheckInViewModel(repository, clock)
        vm.load("p"); advanceUntilIdle()
        assertEquals(CheckInUiState.Unavailable, vm.uiState.value)
        Dispatchers.resetMain()
    }

    /**
     * 批 1 Task 6：我的页三格统计要**数字**，不是已经拼好的中文句子
     * （「连续学习 5 天」这枚字符串无法再拆回 5，页面要在自己的排版里用 Headline 数）。
     * 同一份快照喂两个面：打卡页读文本，我的页读数。
     */
    @Test
    fun `ready carries the streak and learned totals for the profile page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stats = (1..30).map { day -> DailyLearningStats(LocalDate.of(2026, 9, day), 1, 0, 1, 2) }
        val repository = FakeStats(Result.success(stats))
        repository.totals = Result.success(com.example.englishlearning.learning.domain.LearningTotals(42, 17))
        repository.streakResult = Result.success(5)
        val vm = CheckInViewModel(repository, clock)
        vm.load("p"); advanceUntilIdle()
        val state = vm.uiState.value as CheckInUiState.Ready
        assertEquals(5, state.streakDays)
        assertEquals(42, state.learnedWordCount)
        // 文本与数字同源：句子仍按同一份 shot 拼，不许一条从别处取数。
        assertEquals("连续学习 5 天", state.streakText)
        Dispatchers.resetMain()
    }

    /**
     * 批 1 Task 6 的预加载口径：我的页与学习页会在切 tab 时各调一次 `load(keepReady = true)`。
     * 若这次重载先把状态打回 `Loading`，页头的连续天数会闪成 0、三格统计会闪成占位——
     * 于是「刷新」变成了「先撒谎再改口」。keepReady 下：不回落 Loading，失败也保留上一份 Ready。
     */
    @Test
    fun `keepReady refresh neither flashes loading nor drops a good snapshot on failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FakeStats(Result.success(listOf(DailyLearningStats(date, 1, 0, 1, 1))))
        repository.totals = Result.success(com.example.englishlearning.learning.domain.LearningTotals(7, 3))
        repository.streakResult = Result.success(2)
        val vm = CheckInViewModel(repository, clock)
        vm.load("p"); advanceUntilIdle()
        val first = vm.uiState.value as CheckInUiState.Ready
        assertEquals(2, first.streakDays)

        // 第二次刷新失败：不许打回 Loading，也不许把上一份好快照换成 Unavailable。
        repository.totals = Result.failure(IllegalStateException())
        vm.load("p", keepReady = true)
        assertTrue(vm.uiState.value is CheckInUiState.Ready, "keepReady 刷新不得先打回 Loading")
        advanceUntilIdle()
        assertEquals(first, vm.uiState.value, "刷新失败时应保留上一份 Ready，而不是闪成不可用")
        Dispatchers.resetMain()
    }

    private class FakeStats(private val result: Result<List<DailyLearningStats>>) : LearningStatsRepository {
        var totals: Result<com.example.englishlearning.learning.domain.LearningTotals> =
            Result.success(com.example.englishlearning.learning.domain.LearningTotals(0, 0))
        var streakResult: Result<Int> = Result.success(0)
        override suspend fun today(profileId: String, localDate: LocalDate) = Result.failure<DailyLearningStats>(UnsupportedOperationException())
        override suspend fun range(profileId: String, from: LocalDate, to: LocalDate) = result
        override suspend fun historyRange(profileId: String, from: LocalDate, to: LocalDate) =
            Result.failure<kotlin.collections.List<com.example.englishlearning.learning.domain.DailyLearningHistory>>(NotImplementedError())
        override suspend fun totals(profileId: String) = totals
        override suspend fun streak(profileId: String, today: LocalDate) = streakResult
    }

    private class ThrowingStats : LearningStatsRepository {
        override suspend fun today(profileId: String, localDate: LocalDate) = Result.failure<DailyLearningStats>(UnsupportedOperationException())
        override suspend fun range(profileId: String, from: LocalDate, to: LocalDate): Result<List<DailyLearningStats>> {
            throw IllegalStateException("boom")
        }
        override suspend fun historyRange(profileId: String, from: LocalDate, to: LocalDate) =
            Result.failure<kotlin.collections.List<com.example.englishlearning.learning.domain.DailyLearningHistory>>(NotImplementedError())
        override suspend fun totals(profileId: String) =
            Result.failure<com.example.englishlearning.learning.domain.LearningTotals>(NotImplementedError())
        override suspend fun streak(profileId: String, today: LocalDate) = Result.failure<Int>(NotImplementedError())
    }
}
