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
}
