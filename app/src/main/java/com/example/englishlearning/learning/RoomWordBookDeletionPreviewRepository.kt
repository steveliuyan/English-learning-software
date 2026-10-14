package com.example.englishlearning.learning

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 删除词书确认框的计数仓储（F4-01 Task10b）：三个 count 都只读，SQL 语义由
 * androidTest `RoomWordBookDeletionPreviewTest` 真库兜底；这里钉接线与失败口径
 * （与 RoomArticleVersionRepository 同款 runStorage）。
 */
class RoomWordBookDeletionPreviewRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : WordBookDeletionPreviewRepository {
    override suspend fun preview(profileId: String, wordBookId: String): Result<WordBookDeletionPreview> = try {
        Result.success(withContext(ioDispatcher) {
            val dao = database.internalLearningStatsDao()
            WordBookDeletionPreview(
                entryCount = dao.entriesForWordBook(wordBookId),
                keptEventCount = dao.eventsForWordBook(profileId, wordBookId),
                keptArticleCount = dao.articlesForWordBook(profileId, wordBookId),
            )
        })
    } catch (cancellation: CancellationException) {
        if (currentCoroutineContext().isActive) {
            Result.failure(AppErrorException(AppError.StorageUnavailable))
        } else {
            throw cancellation
        }
    } catch (_: Exception) {
        Result.failure(AppErrorException(AppError.StorageUnavailable))
    }
}
