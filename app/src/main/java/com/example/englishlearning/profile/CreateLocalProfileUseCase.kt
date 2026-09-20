package com.example.englishlearning.profile

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.time.ClockProvider

class CreateLocalProfileUseCase(
    private val repository: LocalProfileRepository,
    private val clock: ClockProvider,
) {
    suspend operator fun invoke(displayName: String): Result<LocalProfile> {
        if (displayName.isBlank()) return Result.failure(ProfileException(AppError.InvalidProfileName))
        val profile = LocalProfile(RoomLocalProfileRepository.DEFAULT_ID, displayName.trim(), clock.instant())
        repository.save(profile)
        return Result.success(profile)
    }
}
class ProfileException(val error: AppError) : Exception()

fun Throwable.toSafeAppError(): AppError = when (this) {
    is ProfileException -> error
    is AppErrorException -> appError
    else -> (cause as? ProfileException)?.error ?: AppError.DatabaseMigrationFailed
}
