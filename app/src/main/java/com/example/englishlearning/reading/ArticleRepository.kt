package com.example.englishlearning.reading

import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleType

interface ArticleRepository {
    suspend fun saveNewVersion(article: Article): Result<Article>

    suspend fun findLatest(
        profileId: String,
        localDate: String,
        activeWordBookId: String,
        articleType: ArticleType,
        lengthTier: ArticleLengthTier,
    ): Result<Article?>

    suspend fun findHistory(profileId: String): Result<List<Article>>

    /** 按抓取来源 URL 找已存文章（跨计划复用同一篇外刊）。仅 WebFetched 文章有 URL。 */
    suspend fun findBySourceUrl(url: String): Result<Article?>
}
