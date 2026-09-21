package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.LearningSettingsEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class LearningSettings(
    val profileId: String,
    val openDetailOnKnown: Boolean,
    val openDetailOnFuzzy: Boolean,
    val openDetailOnForgotten: Boolean,
) {
    companion object {
        fun defaults(profileId: String) = LearningSettings(profileId, false, true, true)
    }
}

sealed interface LearningSettingsRepositoryResult<out T> {
    data class Success<T>(val value: T) : LearningSettingsRepositoryResult<T>
    data object StorageUnavailable : LearningSettingsRepositoryResult<Nothing>
}

interface LearningSettingsRepository {
    suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?>
    suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit>
}

class RoomLearningSettingsRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : LearningSettingsRepository {
    override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> =
        runStorage { database.internalLearningSettingsDao().findByProfileId(profileId)?.toDomain() }

    override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> =
        runStorage { database.internalLearningSettingsDao().upsert(settings.toEntity()) }

    private suspend fun <T> runStorage(block: suspend () -> T): LearningSettingsRepositoryResult<T> =
        try {
            LearningSettingsRepositoryResult.Success(withContext(ioDispatcher) { block() })
        } catch (cancellation: CancellationException) {
            if (currentCoroutineContext().isActive) {
                LearningSettingsRepositoryResult.StorageUnavailable
            } else {
                throw cancellation
            }
        } catch (_: Exception) {
            LearningSettingsRepositoryResult.StorageUnavailable
        }

    private fun LearningSettingsEntity.toDomain() =
        LearningSettings(profileId, openDetailOnKnown, openDetailOnFuzzy, openDetailOnForgotten)

    private fun LearningSettings.toEntity() =
        LearningSettingsEntity(profileId, openDetailOnKnown, openDetailOnFuzzy, openDetailOnForgotten)
}
