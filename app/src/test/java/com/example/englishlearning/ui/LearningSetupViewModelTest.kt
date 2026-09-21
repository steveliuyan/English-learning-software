package com.example.englishlearning.ui

import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
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
        val viewModel = setupViewModel(repository)

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
        val viewModel = setupViewModel(repository)

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
        val viewModel = setupViewModel(repository)
        viewModel.load("default")
        advanceUntilIdle()
        val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }

        viewModel.save("default")
        advanceUntilIdle()

        assertEquals(LearningSetupEffect.Saved, effect.await())
        assertEquals("大学英语四级", viewModel.uiState.value.savedWordBookName)
    }

    @Test
    fun `loads detail toggles with defaults when no settings are stored`() = runTest(dispatcher) {
        val viewModel = setupViewModel()

        viewModel.load("profile-x")
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.openDetailOnKnown)
        assertEquals(true, viewModel.uiState.value.openDetailOnFuzzy)
        assertEquals(true, viewModel.uiState.value.openDetailOnForgotten)
    }

    @Test
    fun `loads stored detail toggles for the profile`() = runTest(dispatcher) {
        val settingsRepo = FakeSettingsRepository().apply {
            stored["profile-x"] = LearningSettings(
                "profile-x",
                openDetailOnKnown = true,
                openDetailOnFuzzy = false,
                openDetailOnForgotten = false,
            )
        }
        val viewModel = setupViewModel(settingsRepo = settingsRepo)

        viewModel.load("profile-x")
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.openDetailOnKnown)
        assertEquals(false, viewModel.uiState.value.openDetailOnFuzzy)
        assertEquals(false, viewModel.uiState.value.openDetailOnForgotten)
    }

    @Test
    fun `toggling a detail switch persists immediately and keeps the value`() = runTest(dispatcher) {
        val settingsRepo = FakeSettingsRepository()
        val viewModel = setupViewModel(settingsRepo = settingsRepo)

        viewModel.load("profile-x")
        advanceUntilIdle()
        viewModel.setOpenDetailOnKnown(true)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.openDetailOnKnown)
        assertEquals(
            LearningSettings("profile-x", openDetailOnKnown = true, openDetailOnFuzzy = true, openDetailOnForgotten = true),
            settingsRepo.stored["profile-x"],
        )
    }

    @Test
    fun `detail settings are isolated per profile`() = runTest(dispatcher) {
        val settingsRepo = FakeSettingsRepository()
        val vm1 = setupViewModel(settingsRepo = settingsRepo)
        vm1.load("profile-1")
        advanceUntilIdle()
        vm1.setOpenDetailOnKnown(true)
        advanceUntilIdle()

        val vm2 = setupViewModel(settingsRepo = settingsRepo)
        vm2.load("profile-2")
        advanceUntilIdle()
        vm2.setOpenDetailOnForgotten(false)
        advanceUntilIdle()

        assertEquals(
            LearningSettings("profile-1", openDetailOnKnown = true, openDetailOnFuzzy = true, openDetailOnForgotten = true),
            settingsRepo.stored["profile-1"],
        )
        assertEquals(
            LearningSettings("profile-2", openDetailOnKnown = false, openDetailOnFuzzy = true, openDetailOnForgotten = false),
            settingsRepo.stored["profile-2"],
        )
    }

    private fun seed(repository: LearningProfileRepository) =
        SeedWordBooksUseCase({ "[]" }, repository)

    private fun select(repository: LearningProfileRepository) =
        SelectWordBookAndSetDailyTargetUseCase(repository)

    private fun setupViewModel(
        repository: LearningProfileRepository = FakeRepository(),
        settingsRepo: LearningSettingsRepository = FakeSettingsRepository(),
    ) = LearningSetupViewModel(
        repository,
        seed(repository),
        select(repository),
        GetLearningSettingsUseCase(settingsRepo),
        SaveLearningSettingsUseCase(settingsRepo),
    )

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

    private class FakeSettingsRepository : LearningSettingsRepository {
        val stored = mutableMapOf<String, LearningSettings>()

        override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> =
            LearningSettingsRepositoryResult.Success(stored[profileId])

        override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> {
            stored[settings.profileId] = settings
            return LearningSettingsRepositoryResult.Success(Unit)
        }
    }
}
