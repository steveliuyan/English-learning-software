package com.example.englishlearning.reading.domain

data class ReadingPreference(
    val profileId: String,
    val defaultArticleType: ArticleType = ArticleType.STORY,
    val explicitLengthTier: ArticleLengthTier? = null,
    val displayMode: ArticleDisplayMode = ArticleDisplayMode.ENGLISH_FIRST,
)
