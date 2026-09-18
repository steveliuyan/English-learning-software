package com.example.englishlearning.ui

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.LocalProfile
import com.example.englishlearning.profile.LocalProfileRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

class AppViewModelFailureTest {
    private val clock = FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)
    @Test fun `repository read failure becomes safe error`() = runTest {
        val vm = AppViewModel(FailingRepository(read = true), CreateLocalProfileUseCase(FailingRepository(), clock))
        advanceUntilIdle()
        assertEquals(AppUiState.Error(AppError.DatabaseMigrationFailed), vm.uiState.value)
    }
    @Test fun `known app error is preserved`() = runTest {
        val repo = FailingRepository(saveError = AppError.StorageInsufficient(10))
        val vm = AppViewModel(repo, CreateLocalProfileUseCase(repo, clock))
        vm.createProfile("valid")
        advanceUntilIdle()
        assertEquals(AppUiState.Error(AppError.StorageInsufficient(10)), vm.uiState.value)
    }
    @Test fun `repository save failure becomes safe error`() = runTest {
        val repo = FailingRepository(save = true)
        val vm = AppViewModel(repo, CreateLocalProfileUseCase(repo, clock))
        vm.createProfile("valid")
        advanceUntilIdle()
        assertEquals(AppUiState.Error(AppError.DatabaseMigrationFailed), vm.uiState.value)
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
