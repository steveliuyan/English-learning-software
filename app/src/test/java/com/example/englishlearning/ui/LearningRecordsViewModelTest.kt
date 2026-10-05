package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.LearningRecordRepository
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.LearningHistoryGroup
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearningRecordsViewModelTest {
    @Test
    fun `first load is lazy and switching tabs does not reload cached data`() = runTest {
        val repository = FakeLearningRecordRepository()
        val vm = LearningRecordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
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
    }

    @Test
    fun `switching to all words loads only once and filtering stays local`() = runTest {
        val repository = FakeLearningRecordRepository()
        repository.allWordsResult = listOf(
            WordBookRecord(null, "book:a", "a", WordBookRecordStatus.Unlearned, false),
            WordBookRecord(null, "book:b", "b", WordBookRecordStatus.Due, false),
        )
        val vm = LearningRecordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("profile", "plan", "book")
        testScheduler.advanceUntilIdle()
        vm.selectTab(LearningRecordsTab.ALL_WORDS)
        testScheduler.advanceUntilIdle()
        vm.selectAllWordsFilter(WordBookRecordStatus.Due)
        vm.selectTab(LearningRecordsTab.ALL_WORDS)
        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.allWordsCalls)
        assertEquals(WordBookRecordStatus.Due, vm.state.value.filter)
        assertEquals(2, vm.state.value.allWords.size)
    }

    @Test
    fun `failed tab can be retried without reloading other tabs`() = runTest {
        val repository = FakeLearningRecordRepository()
        repository.historyResults += RepositoryResult.Failure(com.example.englishlearning.learning.LearningProfileRepositoryError.StorageUnavailable)
        repository.historyResults += RepositoryResult.Success(emptyList())
        val vm = LearningRecordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
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
        val vm = LearningRecordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("profile", "plan", "book")
        testScheduler.advanceUntilIdle()
        vm.load("profile", "plan", "book")
        testScheduler.advanceUntilIdle()
        assertEquals(2, repository.todayCalls)
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
