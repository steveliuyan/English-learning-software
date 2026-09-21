package com.example.englishlearning.reading

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.entity.ArticleEntity
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class RoomArticleRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : ArticleRepository {
    override suspend fun saveNewVersion(article: Article): Result<Article> = runStorage {
        require(article.title.isNotBlank()) { "Article title must not be blank" }
        require(isSafePlainText(article.title)) { "Article title contains unsupported markup" }
        require(isSafePlainText(article.englishText)) { "Article text contains unsupported markup" }
        require(isSafePlainText(article.chineseText)) { "Article translation contains unsupported markup" }
        val dao = database.internalArticleDao()
        val nextVersion = (dao.maxVersion(
            article.profileId,
            article.localDate,
            article.activeWordBookId,
            article.articleType.name,
            article.lengthTier.name,
        ) ?: 0) + 1
        val saved = article.copy(version = nextVersion)
        dao.insert(saved.toEntity())
        saved
    }

    override suspend fun findLatest(
        profileId: String,
        localDate: String,
        activeWordBookId: String,
        articleType: ArticleType,
        lengthTier: ArticleLengthTier,
    ): Result<Article?> = runStorage {
        database.internalArticleDao().findLatest(
            profileId, localDate, activeWordBookId, articleType.name, lengthTier.name,
        )?.toDomain()
    }

    override suspend fun findHistory(profileId: String): Result<List<Article>> = runStorage {
        database.internalArticleDao().findHistory(profileId).map { it.toDomain() }
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

    private fun isSafePlainText(value: String): Boolean =
        !value.contains('<') && !value.contains('>') && !value.contains("javascript:", ignoreCase = true)

    private fun Article.toEntity() = ArticleEntity(
        articleId, profileId, localDate, activeWordBookId, articleType.name, lengthTier.name,
        version, title, englishText, chineseText, generatedAtEpochMillis, modelName,
    )

    private fun ArticleEntity.toDomain() = Article(
        articleId, profileId, localDate, activeWordBookId,
        ArticleType.valueOf(articleType), ArticleLengthTier.valueOf(lengthTier), version,
        title, englishText, chineseText, generatedAtEpochMillis, modelName,
    )
}
