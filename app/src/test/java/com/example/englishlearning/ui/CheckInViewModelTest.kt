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
        assertTrue(state.completed)
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

    private class FakeStats(private val result: Result<List<DailyLearningStats>>) : LearningStatsRepository {
        override suspend fun today(profileId: String, localDate: LocalDate) = Result.failure<DailyLearningStats>(UnsupportedOperationException())
        override suspend fun range(profileId: String, from: LocalDate, to: LocalDate) = result
    }
}
