package com.example.englishlearning.reading

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.entity.ReadingPreferenceEntity
import com.example.englishlearning.reading.domain.ArticleDisplayMode
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class RoomReadingPreferenceRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : ReadingPreferenceRepository {
    override suspend fun getPreference(profileId: String): Result<ReadingPreference> = runStorage {
        val entity = database.internalReadingPreferenceDao().find(profileId)
        entity?.toDomain() ?: ReadingPreference(profileId)
    }

    override suspend fun savePreference(preference: ReadingPreference): Result<Unit> = runStorage {
        database.internalReadingPreferenceDao().upsert(preference.toEntity())
    }

    private suspend fun <T> runStorage(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(ioDispatcher) { block() })
    } catch (cancellation: CancellationException) {
        if (currentCoroutineContext().isActive) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        } else {
            throw cancellation
        }
    } catch (error: Exception) {
        Result.failure(AppErrorException(AppError.StorageUnavailable))
    }

    private fun ReadingPreference.toEntity() = ReadingPreferenceEntity(
        profileId = profileId,
        defaultArticleType = defaultArticleType.name,
        explicitLengthTier = explicitLengthTier?.name,
        displayMode = displayMode.name,
    )

    private fun ReadingPreferenceEntity.toDomain() = ReadingPreference(
        profileId = profileId,
        defaultArticleType = ArticleType.valueOf(defaultArticleType),
        explicitLengthTier = explicitLengthTier?.let { ArticleLengthTier.valueOf(it) },
        // 解析失败退回产品默认：老行或将来删掉的枚举值都不能让阅读页开不出来。
        displayMode = runCatching { ArticleDisplayMode.valueOf(displayMode) }
            .getOrElse { ArticleDisplayMode.ENGLISH_FIRST },
    )
}
