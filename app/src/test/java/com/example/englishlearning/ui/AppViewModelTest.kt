package com.example.englishlearning.ui

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.InMemoryLocalProfileRepository
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
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
    fun `create profile persists and reloads without network`() = runTest(dispatcher) {
        val repo = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)
        val vm = AppViewModel(repo, CreateLocalProfileUseCase(repo, clock))
        vm.createProfile("本地学习者")
        advanceUntilIdle()
        val reloaded = AppViewModel(repo, CreateLocalProfileUseCase(repo, clock))
        advanceUntilIdle()
        assertEquals(AppUiState.Ready(com.example.englishlearning.profile.LocalProfile("default", "本地学习者", Instant.EPOCH)), reloaded.uiState.value)
    }

    @Test
    fun `blank profile name maps to stable safe error without echo`() = runTest(dispatcher) {
        val input = "   "
        val repo = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)
        val vm = AppViewModel(repo, CreateLocalProfileUseCase(repo, clock))
        vm.createProfile(input)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(AppUiState.Error(AppError.InvalidProfileName), state)
        assertFalse(state.toString().contains(input))
        assertFalse(state.toString().contains("sentinel"))
        assertFalse(state.toString().contains("/secret"))
        assertFalse(state.toString().contains("Exception"))
    }

    @Test
    fun `view model source has safe production dependency boundary`() {
        val source = locateViewModelSource().readText()
        assertTrue(source.isNotBlank())
        assertTrue(source.contains("import androidx.lifecycle.ViewModel"))
        assertTrue(source.contains("class AppViewModel"))
        val forbidden = listOf(".dao.", "OkHttpClient", "Retrofit", "SecretStore", "AndroidKeyStoreSecretStore")
        assertTrue(source.lineSequence().filter { it.trimStart().startsWith("import ") }.none { line -> forbidden.any(line::contains) })
    }

    private fun locateViewModelSource(): File {
        val candidates = sequenceOf(
            File("src/main/java/com/example/englishlearning/ui/AppViewModel.kt"),
            File("app/src/main/java/com/example/englishlearning/ui/AppViewModel.kt"),
        )
        return candidates.firstOrNull(File::exists) ?: error("AppViewModel source not found")
    }
}
