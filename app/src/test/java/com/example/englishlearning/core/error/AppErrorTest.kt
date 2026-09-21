package com.example.englishlearning.core.error

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppErrorTest {
    @Test
    fun `every error variant has a unique stable non-sensitive UI identifier`() {
        val errors =
            listOf(
                AppError.NetworkUnavailable,
                AppError.StorageUnavailable,
                AppError.StorageInsufficient(requiredBytes = 4096),
                AppError.DatabaseMigrationFailed,
                AppError.KeyStoreUnavailable,
                AppError.IntegrityMismatch,
                AppError.PairingFailed,
                AppError.InvalidProfileName,
            )

        assertEquals(
            listOf(
                AppErrorUiText.NetworkUnavailable,
                AppErrorUiText.StorageUnavailable,
                AppErrorUiText.StorageInsufficient,
                AppErrorUiText.DatabaseMigrationFailed,
                AppErrorUiText.KeyStoreUnavailable,
                AppErrorUiText.IntegrityMismatch,
                AppErrorUiText.PairingFailed,
                AppErrorUiText.InvalidProfileName,
            ),
            errors.map(AppError::uiText),
        )
        assertEquals(errors.size, errors.map(AppError::uiText).toSet().size)
        errors.forEach(::assertHasNoSensitivePayload)
    }

    private fun assertHasNoSensitivePayload(error: AppError) {
        error.javaClass.declaredFields
            .filterNot { field -> field.isSynthetic }
            .forEach { field ->
                assertFalse(field.name.contains("exception", ignoreCase = true))
                assertFalse(field.name.contains("secret", ignoreCase = true))
                assertFalse(field.name.contains("alias", ignoreCase = true))
                assertFalse(field.name.contains("path", ignoreCase = true))
                assertFalse(field.name.contains("hash", ignoreCase = true))
                assertFalse(Throwable::class.java.isAssignableFrom(field.type))
                assertFalse(CharArray::class.java.isAssignableFrom(field.type))
                assertFalse(String::class.java.isAssignableFrom(field.type))
            }
    }
}
