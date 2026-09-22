package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.entity.AiProfileEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class RoomAiProfileRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : AiProfileRepository {
    override suspend fun list(): Result<List<AiProfile>> =
        runStorage { database.internalAiProfileDao().list().map { it.toDomain() } }

    override suspend fun find(profileId: String): Result<AiProfile?> =
        runStorage { database.internalAiProfileDao().find(profileId)?.toDomain() }

    override suspend fun save(profile: AiProfile): Result<Unit> =
        runStorage { database.internalAiProfileDao().upsert(profile.toEntity()) }

    override suspend fun delete(profileId: String): Result<Unit> =
        runStorage { database.internalAiProfileDao().delete(profileId) }

    private suspend fun <T> runStorage(block: suspend () -> T): Result<T> =
        try {
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

    private fun AiProfile.toEntity() = AiProfileEntity(
        profileId = profileId,
        displayName = displayName,
        websiteUrl = websiteUrl,
        endpoint = endpoint,
        model = model,
        capabilities = capabilities.joinToString(",") { it.name },
        secretAlias = secretReference.alias,
        temperature = advancedParameters.temperature,
        topP = advancedParameters.topP,
        maxTokens = advancedParameters.maxTokens,
        timeoutSeconds = advancedParameters.timeoutSeconds,
        systemPromptTemplateId = advancedParameters.systemPromptTemplateId,
    )

    private fun AiProfileEntity.toDomain() = AiProfile(
        profileId = profileId,
        displayName = displayName,
        websiteUrl = websiteUrl,
        endpoint = endpoint,
        model = model,
        capabilities = capabilities.split(",").filter { it.isNotBlank() }.map { AiCapability.valueOf(it) }.toSet(),
        secretReference = SecretReference(secretAlias),
        advancedParameters = AiAdvancedParameters(temperature, topP, maxTokens, timeoutSeconds, systemPromptTemplateId),
    )
}
