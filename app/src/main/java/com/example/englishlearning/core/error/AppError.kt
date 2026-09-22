package com.example.englishlearning.core.error

/**
 * Stable, safe-to-render failures for user-facing flows.
 *
 * No implementation exception or infrastructure identifier belongs in this model.
 */
sealed interface AppError {
    val uiText: AppErrorUiText

    data object NetworkUnavailable : AppError {
        override val uiText: AppErrorUiText = AppErrorUiText.NetworkUnavailable
    }

    data object StorageUnavailable : AppError {
        override val uiText: AppErrorUiText = AppErrorUiText.StorageUnavailable
    }

    data class StorageInsufficient(
        val requiredBytes: Long,
    ) : AppError {
        override val uiText: AppErrorUiText = AppErrorUiText.StorageInsufficient
    }

    data object DatabaseMigrationFailed : AppError {
        override val uiText: AppErrorUiText = AppErrorUiText.DatabaseMigrationFailed
    }

    data object KeyStoreUnavailable : AppError {
        override val uiText: AppErrorUiText = AppErrorUiText.KeyStoreUnavailable
    }

    data object IntegrityMismatch : AppError {
        override val uiText: AppErrorUiText = AppErrorUiText.IntegrityMismatch
    }

    data object PairingFailed : AppError {
        override val uiText: AppErrorUiText = AppErrorUiText.PairingFailed
    }

    data object InvalidProfileName : AppError {
        override val uiText: AppErrorUiText = AppErrorUiText.InvalidProfileName
    }

    data object InvalidAiConfiguration : AppError {
        override val uiText: AppErrorUiText = AppErrorUiText.InvalidAiConfiguration
    }
}

enum class AppErrorUiText {
    NetworkUnavailable,
    StorageUnavailable,
    StorageInsufficient,
    DatabaseMigrationFailed,
    KeyStoreUnavailable,
    IntegrityMismatch,
    PairingFailed,
    InvalidProfileName,
    InvalidAiConfiguration,
}
