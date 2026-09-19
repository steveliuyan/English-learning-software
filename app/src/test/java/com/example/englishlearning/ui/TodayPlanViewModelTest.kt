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
        val viewModel = TodayPlanViewModel({ invocations++; TodayPlanResult.Ready(plan()) }, repository)
        viewModel.load("profile-1")
        viewModel.load("profile-1")
        advanceUntilIdle()
        assertEquals(2, invocations)
        assertEquals(TodayPlanUiState.Ready("小学", "2026-09-19", 0, 5, 5), viewModel.uiState.value)
        Dispatchers.resetMain()
    }

    private fun plan() = TodayPlan("p1", "profile-1", LocalDate.of(2026, 9, 19), "Asia/Shanghai", "primary-school", 0, 5, emptyList(), listOf("d1", "d2", "d3", "d4", "d5"), "f1-v1", Instant.EPOCH)

    private class FakeLearningProfileRepository : LearningProfileRepository {
        override suspend fun current(profileId: String) = RepositoryResult.Success<LearningProfile?>(null)
        override suspend fun save(profile: LearningProfile) = RepositoryResult.Success(Unit)
        override suspend fun listWordBooks() = RepositoryResult.Success(emptyList<WordBook>())
        override suspend fun findWordBook(id: String) = RepositoryResult.Success(WordBook(id, "小学", "Primary", 0, "v1", "fixture"))
        override suspend fun upsertWordBook(wordBook: WordBook) = RepositoryResult.Success(Unit)
    }
}
