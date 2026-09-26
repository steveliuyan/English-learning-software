package com.example.englishlearning.language

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.entity.SpeechPreferenceEntity
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.language.domain.SpeechPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class RoomSpeechPreferenceRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : SpeechPreferenceRepository {
    override suspend fun get(): Result<SpeechPreference> = runStorage {
        database.internalSpeechPreferenceDao().find()?.toDomain() ?: SpeechPreference()
    }

    override suspend fun save(preference: SpeechPreference): Result<Unit> = runStorage {
        database.internalSpeechPreferenceDao().upsert(preference.toEntity())
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

    private fun SpeechPreference.toEntity() = SpeechPreferenceEntity(
        preferenceId = "device",
        selectedEngine = selectedEngine.name,
        openAiProfileId = openAiProfileId,
        miMoProfileId = miMoProfileId,
    )

    private fun SpeechPreferenceEntity.toDomain() = SpeechPreference(
        preferenceId = preferenceId,
        selectedEngine = PronunciationEngine.valueOf(selectedEngine),
        openAiProfileId = openAiProfileId,
        miMoProfileId = miMoProfileId,
    )
}
