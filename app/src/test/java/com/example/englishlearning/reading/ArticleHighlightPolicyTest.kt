package com.example.englishlearning.reading

import com.example.englishlearning.learning.domain.WordCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleHighlightPolicyTest {
    private fun card(id: String, lemma: String, inflections: List<String> = emptyList()) =
        WordCard(id, "cet4", lemma, "", "", "", inflections = inflections)

    @Test
    fun matchesCaseInsensitivelyAndKeepsTheOriginalOffsets() {
        val coverage = ArticleHighlightPolicy.derive("An Apple a day.", listOf(card("c1", "apple")))
        val hit = coverage.highlights.single()
        assertEquals(3, hit.start)
        assertEquals(8, hit.end)
        assertEquals("Apple", "An Apple a day.".substring(hit.start, hit.end))
        assertTrue(coverage.uncoveredLemmas.isEmpty())
    }

    @Test
    fun respectsWordBoundariesSoAppDoesNotMatchInsideApple() {
        // 计划原文断言 highlights 为空，但 " app " 作为独立词在 \b 语义下必然命中，
        // 该断言与实现描述矛盾；本测试锁定的是真实意图：apple / grapple 内部不得命中。
        val coverage = ArticleHighlightPolicy.derive("apple app grapple", listOf(card("c1", "app")))
        val hit = coverage.highlights.single()
        assertEquals("app", hit.matched)
        assertEquals("app", "apple app grapple".substring(hit.start, hit.end))
        assertTrue(coverage.uncoveredLemmas.isEmpty())
    }

    @Test
    fun matchesInflectionsAndAttributesThemToTheSameCard() {
        val coverage = ArticleHighlightPolicy.derive("apples and apple", listOf(card("c1", "apple", listOf("apples"))))
        assertEquals(2, coverage.highlights.size)
        assertTrue(coverage.highlights.all { it.cardId == "c1" })
    }

    @Test
    fun highlightsEveryOccurrence() {
        val coverage = ArticleHighlightPolicy.derive("apple apple apple", listOf(card("c1", "apple")))
        assertEquals(3, coverage.highlights.size)
    }

    @Test
    fun listsMostUncoveredLemmasFirstInInputOrder() {
        val coverage = ArticleHighlightPolicy.derive(
            "only apple here",
            listOf(card("c1", "apple"), card("c2", "brief"), card("c3", "zebra")),
        )
        assertEquals(listOf("brief", "zebra"), coverage.uncoveredLemmas)
    }

    @Test
    fun returnsHighlightsSortedByStartOffset() {
        val coverage = ArticleHighlightPolicy.derive(
            "zebra then apple",
            listOf(card("c2", "apple"), card("c1", "zebra")),
        )
        assertEquals(listOf("zebra", "apple"), coverage.highlights.map { it.lemma })
    }

    @Test
    fun handlesNonAsciiTextWithoutCrashingOrMatching() {
        val coverage = ArticleHighlightPolicy.derive("这是一段中文。", listOf(card("c1", "apple")))
        assertTrue(coverage.highlights.isEmpty())
        assertEquals(listOf("apple"), coverage.uncoveredLemmas)
    }

    @Test
    fun doesNotMatchAcrossAHyphenOrApostrophe() {
        val coverage = ArticleHighlightPolicy.derive("apple-shaped, apple's", listOf(card("c1", "apple")))
        assertEquals(2, coverage.highlights.size)
    }

    @Test
    fun returnsEmptyCoverageForEmptyInputs() {
        val empty = ArticleCoverage(emptyList<WordHighlight>(), emptyList<String>())
        assertEquals(empty, ArticleHighlightPolicy.derive("", listOf(card("c1", "apple"))))
        assertEquals(empty, ArticleHighlightPolicy.derive("apple", emptyList<WordCard>()))
    }

    @Test
    fun isDeterministic() {
        val cards = listOf(card("c1", "apple"), card("c2", "brief"))
        assertEquals(
            ArticleHighlightPolicy.derive("apple and brief", cards),
            ArticleHighlightPolicy.derive("apple and brief", cards),
        )
    }

    @Test
    fun lemmaListOverloadMatchesTheCardOverload() {
        assertEquals(
            ArticleHighlightPolicy.derive(
                "apple and brief",
                listOf(card("c1", "apple"), card("c2", "brief")),
            ).highlights.map { it.lemma },
            ArticleHighlightPolicy.derive("apple and brief", listOf<String>("apple", "brief")).highlights.map { it.lemma },
        )
    }
}
