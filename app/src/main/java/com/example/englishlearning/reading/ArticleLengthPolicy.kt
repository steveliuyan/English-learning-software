package com.example.englishlearning.reading

import com.example.englishlearning.reading.domain.ArticleLengthTier

object ArticleLengthPolicy {
    data class Resolved(
        val tier: ArticleLengthTier,
        val targetWords: IntRange,
        val acceptedWords: IntRange,
    )

    private val primaryJunior = mapOf(
        ArticleLengthTier.SHORT to (50..80),
        ArticleLengthTier.STANDARD to (80..120),
        ArticleLengthTier.LONG to (120..180),
    )
    private val senior = mapOf(
        ArticleLengthTier.SHORT to (80..120),
        ArticleLengthTier.STANDARD to (120..180),
        ArticleLengthTier.LONG to (180..260),
    )
    private val advanced = mapOf(
        ArticleLengthTier.SHORT to (120..200),
        ArticleLengthTier.STANDARD to (180..300),
        ArticleLengthTier.LONG to (300..420),
    )

    fun resolve(
        wordBookId: String,
        explicitTier: ArticleLengthTier?,
    ): Resolved {
        val tier = explicitTier ?: ArticleLengthTier.STANDARD
        val ranges = when (wordBookId.lowercase()) {
            "primary", "junior" -> primaryJunior
            "senior" -> senior
            "cet4", "cet6", "postgraduate" -> advanced
            else -> primaryJunior
        }
        val target = ranges.getValue(tier)
        return Resolved(
            tier = tier,
            targetWords = target,
            acceptedWords = floor(target.first * 0.9).toInt()..ceil(target.last * 1.1).toInt(),
        )
    }
}

fun resolveArticleLength(
    wordBookId: String,
    explicitTier: ArticleLengthTier?,
): ArticleLengthPolicy.Resolved = ArticleLengthPolicy.resolve(wordBookId, explicitTier)

private fun floor(value: Double): Double = kotlin.math.floor(value)
private fun ceil(value: Double): Double = kotlin.math.ceil(value)
