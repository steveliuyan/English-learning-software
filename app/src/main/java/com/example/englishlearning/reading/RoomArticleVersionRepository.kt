package com.example.englishlearning.reading

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.example.englishlearning.reading.domain.Article

/**
 * 「删不空」与级联清完成记录的真语义在 DAO 的 @Transaction 默认方法里（androidTest
 * `RoomArticleVersionFlowTest` 兜底）；这里负责域字段→SQL 参数的映射与
 * 异常收敛口径（与 RoomArticleRepository 一致：取消上抛、其余归 StorageUnavailable）。
 */
class RoomArticleVersionRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : ArticleVersionRepository {
    override suspend fun deletionImpact(article: Article): Result<ArticleDeletionImpact> = runStorage {
        val dao = database.internalArticleDao()
        ArticleDeletionImpact(
            removedCompletionCount = dao.countCompletionsForArticle(article.articleId),
            remainingVersionCount = dao.countInGroup(
                article.profileId, article.localDate, article.activeWordBookId,
                article.articleType.name, article.lengthTier.name,
            ) - 1,
            audioAssetCount = 0,
        )
    }

    override suspend fun deleteVersion(article: Article): Result<VersionDeleteOutcome> = runStorage {
        val receipt = database.internalArticleDao().deleteVersionIfNotLast(
            article.profileId, article.localDate, article.activeWordBookId,
            article.articleType.name, article.lengthTier.name, article.articleId,
        ) ?: return@runStorage VersionDeleteOutcome.RefusedLastVersion
        VersionDeleteOutcome.Deleted(
            ArticleDeletionImpact(
                removedCompletionCount = receipt.removedCompletionCount,
                remainingVersionCount = receipt.remainingVersionCount,
                audioAssetCount = 0,
            ),
        )
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
}
