package com.example.englishlearning.ui

import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.WordBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LearningSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads existing learning settings for the profile`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            profile = LearningProfile("default", "cet4", 20)
            books += WordBook("cet4", "大学英语四级", "CET-4", 0, "v1", "cefr-j-1.5")
        }
        val viewModel = LearningSetupViewModel(repository, seed(repository), select(repository))

        viewModel.load("default")
        advanceUntilIdle()

        assertEquals("cet4", viewModel.uiState.value.selectedWordBookId)
        assertEquals(20, viewModel.uiState.value.dailyNewTarget)
        assertEquals("大学英语四级", viewModel.uiState.value.savedWordBookName)
    }

    @Test
    fun `saves selected wordbook and daily target`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "大学英语四级", "CET-4", 0, "v1", "cefr-j-1.5")
        }
        val viewModel = LearningSetupViewModel(repository, seed(repository), select(repository))

        viewModel.load("default")
        advanceUntilIdle()
        viewModel.selectWordBook("cet4")
        viewModel.updateDailyNewTarget(20)
        viewModel.save("default")
        advanceUntilIdle()

        assertEquals(LearningProfile("default", "cet4", 20), repository.profile)
        assertEquals("大学英语四级", viewModel.uiState.value.savedWordBookName)
        assertEquals(20, viewModel.uiState.value.dailyNewTarget)
    }

    @Test
    fun `save emits one Saved effect only when setup succeeds`() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            books += WordBook("cet4", "大学英语四级", "CET-4", 0, "v1", "cefr-j-1.5")
        }
        val viewModel = LearningSetupViewModel(repository, seed(repository), select(repository))
        viewModel.load("default")
        advanceUntilIdle()
        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }

        viewModel.save("default")
        advanceUntilIdle()

        assertEquals(LearningSetupEffect.Saved, effect.await())
        assertEquals("大学英语四级", viewModel.uiState.value.savedWordBookName)
    }

    private fun seed(repository: LearningProfileRepository) =
        SeedWordBooksUseCase({ "[]" }, repository)

    private fun select(repository: LearningProfileRepository) =
        SelectWordBookAndSetDailyTargetUseCase(repository)

    private class FakeRepository : LearningProfileRepository {
        var profile: LearningProfile? = null
        val books = mutableListOf<WordBook>()

        override suspend fun current(profileId: String) = RepositoryResult.Success(profile)

        override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> {
            this.profile = profile
            return RepositoryResult.Success(Unit)
        }

        override suspend fun listWordBooks() = RepositoryResult.Success(books)

        override suspend fun findWordBook(id: String) =
            RepositoryResult.Success(books.find { it.id == id })

        override suspend fun upsertWordBook(wordBook: WordBook) = RepositoryResult.Success(Unit)
    }
}
