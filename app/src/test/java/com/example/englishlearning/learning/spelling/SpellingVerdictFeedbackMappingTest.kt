package com.example.englishlearning.learning.spelling

import com.example.englishlearning.learning.domain.CardFeedback
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * 判分 → 三档反馈的映射（拼写结果写进排期时唯一的一处对应关系）。
 *
 * 这张表必须钉死：拼错若映射成「模糊」，排期会把拼错的词推后半天才再问一次，
 * 分数看着没问题，记忆效果却是反的。
 */
class SpellingVerdictFeedbackMappingTest {

    @Test
    fun `correct spelling maps to known`() {
        assertEquals(CardFeedback.Known, SpellingVerdict.Correct.toCardFeedback())
    }

    @Test
    fun `close miss maps to fuzzy`() {
        assertEquals(CardFeedback.Fuzzy, SpellingVerdict.CloseMiss(distance = 1).toCardFeedback())
    }

    @Test
    fun `wrong spelling maps to unknown`() {
        assertEquals(CardFeedback.Unknown, SpellingVerdict.Wrong(distance = 4).toCardFeedback())
    }

    @Test
    fun `judge fed through the mapping keeps the three tiers apart`() {
        val expected = "confidence"
        assertEquals(CardFeedback.Known, SpellingJudge.judge(expected, "Confidence").toCardFeedback())
        assertEquals(CardFeedback.Fuzzy, SpellingJudge.judge(expected, "confidense").toCardFeedback())
        assertEquals(CardFeedback.Unknown, SpellingJudge.judge(expected, "convenience").toCardFeedback())
    }
}
