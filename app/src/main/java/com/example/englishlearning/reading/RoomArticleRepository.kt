package com.example.englishlearning.reading

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.entity.ArticleEntity
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleSourceType
import com.example.englishlearning.reading.domain.ArticleType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

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
        articleId = articleId,
        profileId = profileId,
        localDate = localDate,
        activeWordBookId = activeWordBookId,
        articleType = articleType.name,
        lengthTier = lengthTier.name,
        version = version,
        title = title,
        englishText = englishText,
        chineseText = chineseText,
        generatedAtEpochMillis = generatedAtEpochMillis,
        coveredLemmas = coveredLemmas.toLemmaJson(),
        // AiGenerated 复用既有两列承载审计信息；其余来源写空串/NULL，与迁移回填一致。
        parameterSummary = (source as? ArticleSource.AiGenerated)?.parameterSummary.orEmpty(),
        modelName = (source as? ArticleSource.AiGenerated)?.modelName,
        sourceType = source.type().name,
        sourceId = (source as? ArticleSource.WebFetched)?.sourceId.orEmpty(),
        sourceDisplayName = (source as? ArticleSource.WebFetched)?.displayName.orEmpty(),
        sourceUrl = (source as? ArticleSource.WebFetched)?.articleUrl.orEmpty(),
        sourceLicenseNote = (source as? ArticleSource.WebFetched)?.licenseNote.orEmpty(),
        sourceAttribution = (source as? ArticleSource.WebFetched)?.attributionText.orEmpty(),
    )

    private fun ArticleEntity.toDomain() = Article(
        articleId = articleId,
        profileId = profileId,
        localDate = localDate,
        activeWordBookId = activeWordBookId,
        articleType = ArticleType.valueOf(articleType),
        lengthTier = ArticleLengthTier.valueOf(lengthTier),
        version = version,
        title = title,
        englishText = englishText,
        chineseText = chineseText,
        generatedAtEpochMillis = generatedAtEpochMillis,
        coveredLemmas = coveredLemmas.toLemmaList(),
        source = toSource(),
    )

    private fun ArticleSource.type(): ArticleSourceType = when (this) {
        is ArticleSource.AiGenerated -> ArticleSourceType.AI_GENERATED
        is ArticleSource.WebFetched -> ArticleSourceType.WEB_FETCHED
        ArticleSource.UserImported -> ArticleSourceType.USER_IMPORTED
    }

    /**
     * 判别列解析失败时**不能**退回 UserImported：那会把抓取来源必须展示的署名与许可说明
     * 凭空抹掉，等于在读取路径上悄悄违反许可。读不出来就说读不出来——抛
     * [AppErrorException] 让调用方拿到 StorageUnavailable，而不是一篇丢失来源的文章。
     */
    private fun ArticleEntity.toSource(): ArticleSource {
        val type = try {
            ArticleSourceType.valueOf(sourceType)
        } catch (_: IllegalArgumentException) {
            throw AppErrorException(AppError.StorageUnavailable)
        }
        return when (type) {
            ArticleSourceType.AI_GENERATED ->
                ArticleSource.AiGenerated(modelName = modelName.orEmpty(), parameterSummary = parameterSummary)
            ArticleSourceType.WEB_FETCHED -> ArticleSource.WebFetched(
                sourceId = sourceId,
                displayName = sourceDisplayName,
                articleUrl = sourceUrl,
                licenseNote = sourceLicenseNote,
                attributionText = sourceAttribution,
            )
            ArticleSourceType.USER_IMPORTED -> ArticleSource.UserImported
        }
    }

    private fun List<String>.toLemmaJson(): String = JsonArray(map { JsonPrimitive(it) }).toString()

    /**
     * A column that cannot be parsed yields an empty list rather than throwing: a malformed
     * lemma column must cost the reader their highlights, not the whole article.
     */
    private fun String.toLemmaList(): List<String> {
        if (isBlank()) return emptyList()
        return runCatching { Json.parseToJsonElement(this).jsonArray.map { it.jsonPrimitive.content } }
            .getOrElse { emptyList() }
    }
}
