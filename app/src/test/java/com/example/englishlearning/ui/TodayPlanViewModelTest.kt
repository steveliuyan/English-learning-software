package com.example.englishlearning.ui

import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class TodayPlanViewModelTest {
    @Test
    fun `load exposes ready snapshot counts and date`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeLearningProfileRepository()
        var invocations = 0
        val viewModel = TodayPlanViewModel({ invocations++; TodayPlanResult.Ready(plan()) }, repository, FakeLearningEventRepository())
        viewModel.load("profile-1")
        viewModel.load("profile-1")
        advanceUntilIdle()
        assertEquals(2, invocations)
        assertEquals(
            TodayPlanUiState.Ready(
                "小学", "2026-09-19", 0, 5, 5,
                newDone = 0, dueDone = 0, isUnlocked = false,
                unlockReason = "完成新词与复习后解锁文章",
            ),
            viewModel.uiState.value,
        )
        Dispatchers.resetMain()
    }

    @Test
    fun `two argument constructor keeps legacy ready semantics`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = FakeLearningProfileRepository()
        var invocations = 0
        val viewModel = TodayPlanViewModel({ invocations++; TodayPlanResult.Ready(plan()) }, repository)
        viewModel.load("profile-1")
        viewModel.load("profile-1")
        advanceUntilIdle()
        assertEquals(2, invocations)
        assertEquals(
            TodayPlanUiState.Ready(
                "小学", "2026-09-19", 0, 5, 5,
                newDone = 0, dueDone = 0, isUnlocked = false,
                unlockReason = "完成新词与复习后解锁文章",
            ),
            viewModel.uiState.value,
        )
        Dispatchers.resetMain()
    }

    @Test
    fun `non ready result never triggers the event repository`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = SpyLearningEventRepository()
        val viewModel = TodayPlanViewModel({ TodayPlanResult.MissingLearningSetup }, FakeLearningProfileRepository(), events)
        viewModel.load("profile-1")
        advanceUntilIdle()
        assertEquals(TodayPlanUiState.MissingSetup, viewModel.uiState.value)
        assertEquals(0, events.completedCardIdsCalls)
        Dispatchers.resetMain()
    }

    @Test
    fun `strict default stays locked until both new and due groups complete`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = FakeLearningEventRepository(listOf("n1", "n2", "d1"))
        val viewModel = TodayPlanViewModel({ TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 2)) }, FakeLearningProfileRepository(), events)
        viewModel.load("profile-1")
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(2, state.newDone)
        assertEquals(1, state.dueDone)
        assertEquals(false, state.isUnlocked)
        assertEquals("完成新词与复习后解锁文章", state.unlockReason)
        Dispatchers.resetMain()
    }

    @Test
    fun `strict unlocks only when both groups done`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = FakeLearningEventRepository(listOf("n1", "n2", "d1", "d2"))
        val viewModel = TodayPlanViewModel({ TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 2)) }, FakeLearningProfileRepository(), events)
        viewModel.load("profile-1")
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(true, state.isUnlocked)
        assertEquals("今日计划已完成", state.unlockReason)
        Dispatchers.resetMain()
    }

    @Test
    fun `future relaxed ruleVersion unlocks on new cards alone`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = FakeLearningEventRepository(listOf("n1", "n2"))
        val viewModel = TodayPlanViewModel({ TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 3, ruleVersion = "f1-v1-relaxed")) }, FakeLearningProfileRepository(), events)
        viewModel.load("profile-1")
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(true, state.isUnlocked)
        assertEquals("今日计划已完成", state.unlockReason)
        Dispatchers.resetMain()
    }

    @Test
    fun `relaxed ruleVersion still requires new cards`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = FakeLearningEventRepository(emptyList())
        val viewModel = TodayPlanViewModel({ TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 3, ruleVersion = "f1-v1-relaxed")) }, FakeLearningProfileRepository(), events)
        viewModel.load("profile-1")
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(false, state.isUnlocked)
        assertEquals("完成新词后解锁文章", state.unlockReason)
        Dispatchers.resetMain()
    }

    @Test
    fun `completion counts only snapshot cards and ignores extras`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val events = FakeLearningEventRepository(listOf("n1", "n2", "d1", "outside", "late"))
        val viewModel = TodayPlanViewModel({ TodayPlanResult.Ready(planWith(newTarget = 2, dueTarget = 2)) }, FakeLearningProfileRepository(), events)
        viewModel.load("profile-1")
        advanceUntilIdle()
        val state = viewModel.uiState.value as TodayPlanUiState.Ready
        assertEquals(2, state.newDone)
        assertEquals(1, state.dueDone)
        Dispatchers.resetMain()
    }

    private fun plan() = TodayPlan("p1", "profile-1", LocalDate.of(2026, 9, 19), "Asia/Shanghai", "primary-school", 0, 5, emptyList(), listOf("d1", "d2", "d3", "d4", "d5"), "f1-v1", Instant.EPOCH)

    private fun planWith(newTarget: Int = 0, dueTarget: Int = 0, ruleVersion: String = "f1-v1") = TodayPlan(
        "p1", "profile-1", LocalDate.of(2026, 9, 19), "Asia/Shanghai", "primary-school",
        newTarget, dueTarget,
        (1..newTarget).map { "n$it" },
        (1..dueTarget).map { "d$it" },
        ruleVersion, Instant.EPOCH,
    )

    private class FakeLearningEventRepository(private val completed: List<String> = emptyList()) : com.example.englishlearning.learning.LearningEventRepository {
        override suspend fun append(event: com.example.englishlearning.learning.domain.LearningEvent, nextState: com.example.englishlearning.learning.domain.CardReviewState) = com.example.englishlearning.learning.AppendEventResult.Appended(false)
        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.LearningEvent?>(null)
        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.CardReviewState?>(null)
        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(completed)
        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    }

    private class FakeLearningProfileRepository : LearningProfileRepository {
        override suspend fun current(profileId: String) = RepositoryResult.Success<LearningProfile?>(null)
        override suspend fun save(profile: LearningProfile) = RepositoryResult.Success(Unit)
        override suspend fun listWordBooks() = RepositoryResult.Success(emptyList<WordBook>())
        override suspend fun findWordBook(id: String) = RepositoryResult.Success(WordBook(id, "小学", "Primary", 0, "v1", "fixture"))
        override suspend fun upsertWordBook(wordBook: WordBook) = RepositoryResult.Success(Unit)
    }

    private class SpyLearningEventRepository : com.example.englishlearning.learning.LearningEventRepository {
        var completedCardIdsCalls = 0
        override suspend fun append(event: com.example.englishlearning.learning.domain.LearningEvent, nextState: com.example.englishlearning.learning.domain.CardReviewState) = com.example.englishlearning.learning.AppendEventResult.Appended(false)
        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.LearningEvent?>(null)
        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<com.example.englishlearning.learning.domain.CardReviewState?>(null)
        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String): RepositoryResult<List<String>> {
            completedCardIdsCalls++
            return RepositoryResult.Success(emptyList())
        }
        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    }
}
