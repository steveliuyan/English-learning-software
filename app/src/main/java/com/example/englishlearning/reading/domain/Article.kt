package com.example.englishlearning.reading.domain

data class Article(
    val articleId: String,
    val profileId: String,
    val localDate: String,
    val activeWordBookId: String,
    val articleType: ArticleType,
    val lengthTier: ArticleLengthTier,
    val version: Int,
    val title: String,
    val englishText: String,
    val chineseText: String,
    val generatedAtEpochMillis: Long,
    val modelName: String?,
)
