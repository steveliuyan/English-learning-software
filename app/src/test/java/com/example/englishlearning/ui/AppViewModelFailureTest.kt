package com.example.englishlearning.ui

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.LocalProfile
import com.example.englishlearning.profile.LocalProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelFailureTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `repository read failure becomes safe error`() = runTest(dispatcher) {
        val vm = AppViewModel(FailingRepository(read = true), CreateLocalProfileUseCase(FailingRepository(), clock))
        advanceUntilIdle()
        assertEquals(AppUiState.Error(AppError.StorageUnavailable), vm.uiState.value)
    }
    @Test fun `known app error is preserved`() = runTest(dispatcher) {
        val repo = FailingRepository(saveError = AppError.StorageInsufficient(10))
        val vm = AppViewModel(repo, CreateLocalProfileUseCase(repo, clock))
        vm.createProfile("valid")
        advanceUntilIdle()
        assertEquals(AppUiState.Error(AppError.StorageInsufficient(10)), vm.uiState.value)
    }
    @Test fun `repository save failure becomes safe error`() = runTest(dispatcher) {
        val repo = FailingRepository(save = true)
        val vm = AppViewModel(repo, CreateLocalProfileUseCase(repo, clock))
        vm.createProfile("valid")
        advanceUntilIdle()
        assertEquals(AppUiState.Error(AppError.StorageUnavailable), vm.uiState.value)
    }
    private class FailingRepository(
        private val read: Boolean = false,
        private val save: Boolean = false,
        private val saveError: AppError? = null,
    ) : LocalProfileRepository {
        override suspend fun getDefault(): LocalProfile? { if (read) error("/private/path secret"); return null }
        override suspend fun save(profile: LocalProfile) {
            if (saveError != null) throw com.example.englishlearning.profile.ProfileException(saveError)
            if (save) error("/private/path secret")
        }
    }
}
