package com.example.englishlearning.reading

import kotlin.test.Test
import com.example.englishlearning.reading.domain.ArticleLengthTier
import kotlin.test.assertEquals

class ArticleLengthPolicyTest {
    @Test
    fun `standard tier resolves grade-specific target ranges`() {
        assertEquals(80..120, resolveArticleLength("primary", null).targetWords)
        assertEquals(80..120, resolveArticleLength("junior", null).targetWords)
        assertEquals(120..180, resolveArticleLength("senior", null).targetWords)
        assertEquals(180..300, resolveArticleLength("cet4", null).targetWords)
        assertEquals(180..300, resolveArticleLength("cet6", null).targetWords)
        assertEquals(180..300, resolveArticleLength("postgraduate", null).targetWords)
    }

    @Test
    fun `explicit tier overrides wordbook default`() {
        assertEquals(120..200, resolveArticleLength("cet4", ArticleLengthTier.SHORT).targetWords)
        assertEquals(120..180, resolveArticleLength("primary", ArticleLengthTier.LONG).targetWords)
    }

    @Test
    fun `unknown wordbook uses product standard range`() {
        val resolved = resolveArticleLength("unknown", null)

        assertEquals(ArticleLengthTier.STANDARD, resolved.tier)
        assertEquals(80..120, resolved.targetWords)
    }

    @Test
    fun `accepted range applies ten percent tolerance`() {
        val resolved = resolveArticleLength("cet4", null)

        assertEquals(162..330, resolved.acceptedWords)
    }

    @Test
    fun `all tiers expose the locked ranges for senior wordbooks`() {
        assertEquals(120..200, resolveArticleLength("cet4", ArticleLengthTier.SHORT).targetWords)
        assertEquals(180..300, resolveArticleLength("cet4", ArticleLengthTier.STANDARD).targetWords)
        assertEquals(300..420, resolveArticleLength("cet4", ArticleLengthTier.LONG).targetWords)
    }
}
