package com.example.englishlearning.ui

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * 详情页例句里要加粗的单词位置：按词首匹配、忽略大小写，
 * 这样 `parents`、`Parent` 这类变形也能标出词干，而 `separate` 里的 `rate` 不会被误标。
 */
class CardDetailTextTest {

    @Test
    fun `finds the lemma inside the sentence`() {
        assertEquals(6..11, lemmaRangeIn("Every parent wants the best for their child.", "parent"))
    }

    @Test
    fun `matches case-insensitively and marks the stem of an inflected form`() {
        assertEquals(0..5, lemmaRangeIn("Parents care about sleep.", "parent"))
    }

    @Test
    fun `skips matches that start in the middle of another word`() {
        val sentence = "They separate the rates."
        assertEquals(sentence.indexOf("rates").let { it..it + 3 }, lemmaRangeIn(sentence, "rate"))
    }

    @Test
    fun `returns null when the lemma is absent or blank`() {
        assertNull(lemmaRangeIn("Nothing to see here.", "parent"))
        assertNull(lemmaRangeIn("Nothing to see here.", " "))
    }
}
