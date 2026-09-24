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
    /**
     * Lemmas the request asked the model to use, in the order they were sent.
     *
     * Highlights are **derived** from these at read time rather than stored as offsets: a
     * re-read days later must produce the same positions, and today's plan no longer holds
     * the same completed cards. Persisting offsets instead would mean trusting the model's
     * coordinates, which spec F2-03 forbids.
     */
    val coveredLemmas: List<String>,
    /**
     * Non-sensitive summary of the parameters used, e.g. `model=... temperature=...`.
     *
     * Kept for auditability. Must never contain the Endpoint or any part of the Key.
     */
    val parameterSummary: String,
    val modelName: String?,
)
