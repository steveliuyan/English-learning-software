package com.example.englishlearning.reading.domain

data class ReadingPreference(
    val profileId: String,
    val defaultArticleType: ArticleType = ArticleType.STORY,
    val explicitLengthTier: ArticleLengthTier? = null,
    val displayMode: ArticleDisplayMode = ArticleDisplayMode.ENGLISH_FIRST,
    // F3-01C：是否在正文里标记已背词（高亮）。标记来源 V1 固定为当前激活词书（即你背的
    // 那本书），多词书可选随词库数据闭合再做。
    val showLearnedMarks: Boolean = true,
)
