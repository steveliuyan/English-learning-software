package com.example.englishlearning.ui

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.LearningRecordRepository
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.LearningHistoryGroup
import com.example.englishlearning.learning.LearningStatsRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.UnlockMode
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.learning.domain.DailyLearningHistory
import com.example.englishlearning.learning.domain.LearningTotals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearningRecordsViewModelTest {
    @Test
    fun `first load is lazy and switching tabs does not reload cached data`() = runTest {
        val repository = FakeLearningRecordRepository()
        val stats = FakeLearningStatsRepository()
        val vm = LearningRecordsViewModel(repository, stats, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("profile", "plan", "book")
        testScheduler.advanceUntilIdle()
        vm.selectTab(LearningRecordsTab.TODAY)
        testScheduler.advanceUntilIdle()
        vm.selectTab(LearningRecordsTab.HISTORY)
        testScheduler.advanceUntilIdle()
        vm.selectTab(LearningRecordsTab.TODAY)
        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.todayCalls)
        assertEquals(1, repository.historyCalls)
        assertEquals(1, stats.historyRangeCalls)
    }

    @Test
    fun `failed tab can be retried without reloading other tabs`() = runTest {
        val repository = FakeLearningRecordRepository()
        repository.historyResults += RepositoryResult.Failure(com.example.englishlearning.learning.LearningProfileRepositoryError.StorageUnavailable)
        repository.historyResults += RepositoryResult.Success(emptyList())
        val vm = LearningRecordsViewModel(repository, FakeLearningStatsRepository(), FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("profile", "plan", "book")
        testScheduler.advanceUntilIdle()
        vm.selectTab(LearningRecordsTab.HISTORY)
        testScheduler.advanceUntilIdle()
        assertEquals("学习记录暂时无法读取", vm.state.value.errors[LearningRecordsTab.HISTORY])
        vm.retry()
        testScheduler.advanceUntilIdle()
        assertEquals(2, repository.historyCalls)
        assertEquals(emptyList<LearningHistoryGroup>(), vm.state.value.history)
        assertEquals(emptySet<LearningRecordsTab>(), vm.state.value.errors.keys)
    }

    @Test
    fun `reentering refreshes cached today records`() = runTest {
        val repository = FakeLearningRecordRepository()
        val vm = LearningRecordsViewModel(repository, FakeLearningStatsRepository(), FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("profile", "plan", "book")
        testScheduler.advanceUntilIdle()
        vm.load("profile", "plan", "book")
        testScheduler.advanceUntilIdle()
        assertEquals(2, repository.todayCalls)
    }

    @Test
    fun `history tab loads daily expanded stats alongside per-word records`() = runTest {
        val repository = FakeLearningRecordRepository()
        repository.historyResults += RepositoryResult.Success(
            listOf(LearningHistoryGroup(LocalDate.of(2026, 9, 25), emptyList())),
        )
        val stats = FakeLearningStatsRepository()
        stats.historyRangeResult = Result.success(listOf(historyRow(LocalDate.of(2026, 9, 25))))
        val vm = LearningRecordsViewModel(
            repository,
            stats,
            FixedClockProvider(Instant.parse("2026-09-26T00:00:00Z"), ZoneId.of("UTC")),
            StandardTestDispatcher(testScheduler),
        )

        vm.load("profile", "plan", "book")
        testScheduler.advanceUntilIdle()
        assertEquals(0, stats.historyRangeCalls, "首屏只加载今日页签，详展统计必须惰性")

        vm.selectTab(LearningRecordsTab.HISTORY)
        testScheduler.advanceUntilIdle()

        val row = vm.state.value.historyStats.single()
        assertEquals(LocalDate.of(2026, 9, 25), row.localDate)
        assertEquals("四级", row.activeWordBookName)
        assertEquals(2, row.newDone)
        assertTrue(row.unlocked)
        assertEquals(1, stats.historyRangeCalls)
    }

    @Test
    fun `history stats failure keeps the tab in error state`() = runTest {
        val repository = FakeLearningRecordRepository()
        val stats = FakeLearningStatsRepository()
        stats.historyRangeResult = Result.failure(AppErrorException(AppError.StorageUnavailable))
        val vm = LearningRecordsViewModel(repository, stats, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("profile", "plan", "book")
        testScheduler.advanceUntilIdle()
        vm.selectTab(LearningRecordsTab.HISTORY)
        testScheduler.advanceUntilIdle()
        assertEquals("学习记录暂时无法读取", vm.state.value.errors[LearningRecordsTab.HISTORY])
        assertTrue(vm.state.value.historyStats.isEmpty())
    }

    private fun historyRow(localDate: LocalDate) = DailyLearningHistory(
        localDate = localDate,
        activeWordBookId = "cet4",
        activeWordBookName = "四级",
        newDone = 2,
        newTarget = 2,
        dueDone = 1,
        dueTarget = 1,
        unlockMode = UnlockMode.Strict,
        unlocked = true,
        articleGenerated = true,
        articleRead = true,
    )

    private class FakeLearningStatsRepository : LearningStatsRepository {
        var historyRangeCalls = 0
        var historyRangeResult: Result<List<DailyLearningHistory>> = Result.success(emptyList())
        override suspend fun today(profileId: String, localDate: LocalDate) =
            Result.failure<com.example.englishlearning.learning.domain.DailyLearningStats>(UnsupportedOperationException())
        override suspend fun range(profileId: String, from: LocalDate, to: LocalDate) =
            Result.failure<kotlin.collections.List<com.example.englishlearning.learning.domain.DailyLearningStats>>(UnsupportedOperationException())
        override suspend fun historyRange(profileId: String, from: LocalDate, to: LocalDate): Result<List<DailyLearningHistory>> {
            historyRangeCalls++
            return historyRangeResult
        }
        override suspend fun totals(profileId: String) = Result.failure<LearningTotals>(UnsupportedOperationException())
        override suspend fun streak(profileId: String, today: LocalDate) = Result.failure<Int>(UnsupportedOperationException())
    }

    private class FakeLearningRecordRepository : LearningRecordRepository {
        var todayCalls = 0
        var historyCalls = 0
        var allWordsCalls = 0
        var allWordsResult: List<WordBookRecord> = emptyList()
        val historyResults = ArrayDeque<RepositoryResult<List<LearningHistoryGroup>>>()
        override suspend fun today(profileId: String, planId: String): RepositoryResult<List<LearningRecord>> { todayCalls++; return RepositoryResult.Success(emptyList()) }
        override suspend fun history(profileId: String): RepositoryResult<List<LearningHistoryGroup>> { historyCalls++; return historyResults.removeFirstOrNull() ?: RepositoryResult.Success(emptyList()) }
        override suspend fun allWords(wordBookId: String, now: Instant): RepositoryResult<List<WordBookRecord>> { allWordsCalls++; return RepositoryResult.Success(allWordsResult) }
    }
}
