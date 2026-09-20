package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.LocalProfile
import com.example.englishlearning.profile.LocalProfileRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
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

/**
 * Locks the invariant that a slow initial `reload()` must never clobber the result of a
 * later `createProfile()`: the action that is started later owns the final [AppUiState].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelStateSerializationTest {
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

    @Test
    fun `late not-found reload does not overwrite createProfile success`() = runTest(dispatcher) {
        val repository = gatedStaleReadRepository()
        // Construction triggers `init { reload() }`, which blocks inside getDefault().
        val viewModel = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))

        // Queue the newer action, then drain: reload parks on the gate, so createProfile is
        // serialized behind it. Its success must still win once the stale read eventually lands.
        viewModel.createProfile("本地学习者")
        advanceUntilIdle()

        // Let the slow "no profile found" read finish and run every follow-up continuation.
        repository.releaseRead()
        advanceUntilIdle()

        assertEquals(
            AppUiState.Ready(LocalProfile("default", "本地学习者", Instant.EPOCH)),
            viewModel.uiState.value,
            "the late reload result must not overwrite the newer createProfile success",
        )
    }

    private fun gatedStaleReadRepository(): GatedStaleReadRepository = GatedStaleReadRepository()

    /**
     * First [getDefault] parks on [releaseRead] and always reports "no profile" afterwards,
     * modelling a read that started before the profile was saved and completes late.
     */
    private class GatedStaleReadRepository : LocalProfileRepository {
        private val gate = CompletableDeferred<Unit>()
        private var firstRead = true
        private var saved: LocalProfile? = null

        fun releaseRead() {
            gate.complete(Unit)
        }

        override suspend fun getDefault(): LocalProfile? {
            if (firstRead) {
                firstRead = false
                gate.await()
                return null
            }
            return saved
        }

        override suspend fun save(profile: LocalProfile) {
            saved = profile
        }
    }
}
