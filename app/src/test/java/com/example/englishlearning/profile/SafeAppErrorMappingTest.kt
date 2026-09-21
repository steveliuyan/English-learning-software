package com.example.englishlearning.profile

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `toSafeAppError()` is the single safe error-mapping boundary of the app; it must preserve
 * every strongly typed [AppError] carrier instead of collapsing it to a generic failure.
 */
class SafeAppErrorMappingTest {
    @Test
    fun `app error exception keeps its typed error`() {
        assertEquals(
            AppError.KeyStoreUnavailable,
            AppErrorException(AppError.KeyStoreUnavailable).toSafeAppError(),
        )
    }

    @Test
    fun `storage insufficient app error exception is preserved`() {
        assertEquals(
            AppError.StorageInsufficient(42),
            AppErrorException(AppError.StorageInsufficient(42)).toSafeAppError(),
        )
    }

    @Test
    fun `integrity mismatch app error exception is preserved`() {
        assertEquals(
            AppError.IntegrityMismatch,
            AppErrorException(AppError.IntegrityMismatch).toSafeAppError(),
        )
    }

    @Test
    fun `profile exception still maps to its error`() {
        assertEquals(AppError.InvalidProfileName, ProfileException(AppError.InvalidProfileName).toSafeAppError())
    }

    @Test
    fun `unknown failure degrades to storage unavailable`() {
        assertEquals(AppError.StorageUnavailable, IllegalStateException("boom").toSafeAppError())
    }

    @Test
    fun `unrelated failure wrapping a profile exception is unwrapped`() {
        val wrapped = RuntimeException("wrap", ProfileException(AppError.StorageInsufficient(5)))
        assertEquals(AppError.StorageInsufficient(5), wrapped.toSafeAppError())
    }
}
