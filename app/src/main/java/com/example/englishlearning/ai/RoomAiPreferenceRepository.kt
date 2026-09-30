package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiPreference
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.entity.AiPreferenceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class RoomAiPreferenceRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : AiPreferenceRepository {
    override suspend fun get(): Result<AiPreference> = runStorage {
        database.internalAiPreferenceDao().find()?.toDomain() ?: AiPreference()
    }

    override suspend fun save(preference: AiPreference): Result<Unit> = runStorage {
        database.internalAiPreferenceDao().upsert(preference.toEntity())
    }

    private suspend fun <T> runStorage(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(ioDispatcher) { block() })
    } catch (cancellation: CancellationException) {
        if (currentCoroutineContext().isActive) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        } else {
            throw cancellation
        }
    } catch (_: Exception) {
        Result.failure(AppErrorException(AppError.StorageUnavailable))
    }

    private fun AiPreference.toEntity() = AiPreferenceEntity(
        preferenceId = preferenceId,
        defaultTextProfileId = defaultTextProfileId,
        defaultImageProfileId = defaultImageProfileId,
    )

    private fun AiPreferenceEntity.toDomain() = AiPreference(
        preferenceId = preferenceId,
        defaultTextProfileId = defaultTextProfileId,
        defaultImageProfileId = defaultImageProfileId,
    )
}
