package com.example.englishlearning.learning.spelling

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SpellingQuizTest {

    private fun prompt(answer: String, cardId: String = answer, meaning: String = "释义-$answer") =
        SpellingPrompt(
            cardId = cardId,
            wordBookId = "wb",
            answer = answer,
            partOfSpeech = "n.",
            meaningZh = meaning,
            ipa = "/$answer/",
            example = null,
        )

    // --- compareKey ---

    @Test
    fun `compareKey ignores case and surrounding whitespace`() {
        assertEquals("apple", SpellingJudge.compareKey("  Apple  "))
    }

    @Test
    fun `compareKey drops spaces hyphens and punctuation`() {
        assertEquals("alot", SpellingJudge.compareKey("a lot"))
        assertEquals("alot", SpellingJudge.compareKey("a-lot"))
        assertEquals("alot", SpellingJudge.compareKey("a, lot!"))
    }

    @Test
    fun `compareKey unifies curly apostrophes to straight`() {
        val curly = "don’t"
        val straight = "don't"
        assertEquals("don't", SpellingJudge.compareKey(curly))
        assertEquals(SpellingJudge.compareKey(straight), SpellingJudge.compareKey(curly))
    }

    @Test
    fun `compareKey strips diacritics`() {
        assertEquals("cafe", SpellingJudge.compareKey("café"))
        assertEquals("naive", SpellingJudge.compareKey("naïve"))
    }

    @Test
    fun `compareKey of blank is empty`() {
        assertEquals("", SpellingJudge.compareKey("   "))
        assertEquals("", SpellingJudge.compareKey("..."))
    }

    // --- judge ---

    @Test
    fun `exact match is Correct even with case and spacing differences`() {
        assertIs<SpellingVerdict.Correct>(SpellingJudge.judge("apple", "apple"))
        assertIs<SpellingVerdict.Correct>(SpellingJudge.judge("apple", "APPLE"))
        assertIs<SpellingVerdict.Correct>(SpellingJudge.judge("a lot", "alot"))
    }

    @Test
    fun `one letter off on a short word is CloseMiss`() {
        val verdict = SpellingJudge.judge("apple", "aple")
        assertIs<SpellingVerdict.CloseMiss>(verdict)
        assertEquals(1, verdict.distance)
    }

    @Test
    fun `two letters off on a long word is CloseMiss`() {
        val verdict = SpellingJudge.judge("beautiful", "beatiful")
        assertIs<SpellingVerdict.CloseMiss>(verdict)
        assertEquals(1, verdict.distance)
    }

    @Test
    fun `far off answers are Wrong`() {
        assertIs<SpellingVerdict.Wrong>(SpellingJudge.judge("apple", "orange"))
        assertIs<SpellingVerdict.Wrong>(SpellingJudge.judge("cat", "elephant"))
    }

    @Test
    fun `short words tolerate only one edit`() {
        // cat -> cet : distance 1 (CloseMiss); cat -> cxt distance 1; cat -> cxy distance 2 (Wrong)
        assertIs<SpellingVerdict.CloseMiss>(SpellingJudge.judge("cat", "cot"))
        assertIs<SpellingVerdict.Wrong>(SpellingJudge.judge("cat", "cxx"))
    }

    @Test
    fun `tolerance is one for short words and two for long words`() {
        assertEquals(1, SpellingJudge.tolerance(5))
        assertEquals(2, SpellingJudge.tolerance(6))
        assertEquals(2, SpellingJudge.tolerance(12))
    }

    @Test
    fun `empty input is Wrong not crash`() {
        assertIs<SpellingVerdict.Wrong>(SpellingJudge.judge("apple", ""))
    }

    @Test
    fun `levenshtein basic distances`() {
        assertEquals(0, SpellingJudge.levenshtein("kitten", "kitten"))
        assertEquals(1, SpellingJudge.levenshtein("kitten", "sitten"))
        assertEquals(3, SpellingJudge.levenshtein("kitten", "sitting"))
        assertEquals(5, SpellingJudge.levenshtein("", "hello"))
    }

    // --- session flow ---

    @Test
    fun `session starts at first prompt with no answers`() {
        val quiz = SpellingQuiz.of(listOf(prompt("apple"), prompt("banana")))
        assertEquals(2, quiz.total)
        assertEquals(0, quiz.currentIndex)
        assertEquals(0, quiz.answeredCount)
        assertFalse(quiz.isFinished)
        assertFalse(quiz.isCurrentAnswered)
        assertNotNull(quiz.current)
        assertEquals("apple", quiz.current!!.answer)
    }

    @Test
    fun `cannot submit twice for the same prompt`() {
        val quiz = SpellingQuiz.of(listOf(prompt("apple")))
        assertNotNull(quiz.submit("apple"))
        assertTrue(quiz.isCurrentAnswered)
        assertNull(quiz.submit("orange"), "second submit should be rejected")
    }

    @Test
    fun `advance requires an answer`() {
        val quiz = SpellingQuiz.of(listOf(prompt("apple"), prompt("banana")))
        assertFalse(quiz.advance(), "advance before answering must fail")
        quiz.submit("apple")
        assertTrue(quiz.advance())
        assertEquals(1, quiz.currentIndex)
        assertEquals("banana", quiz.current!!.answer)
    }

    @Test
    fun `submitting past the end returns null`() {
        val quiz = SpellingQuiz.of(listOf(prompt("apple")))
        quiz.submit("apple")
        quiz.advance()
        assertTrue(quiz.isFinished)
        assertNull(quiz.submit("apple"))
        assertFalse(quiz.advance())
    }

    @Test
    fun `full run records score and accuracy`() {
        val quiz = SpellingQuiz.of(listOf(prompt("apple"), prompt("banana"), prompt("cherry")))
        quiz.submit("apple")
        quiz.advance()
        quiz.submit("banan")   // CloseMiss, not correct
        quiz.advance()
        quiz.submit("cherry")
        quiz.advance()

        assertTrue(quiz.isFinished)
        assertEquals(3, quiz.answeredCount)
        assertEquals(2, quiz.correctCount)
        assertEquals(2f / 3f, quiz.accuracy())
        assertEquals(3, quiz.results().size)
        assertTrue(quiz.results()[1].verdict is SpellingVerdict.CloseMiss)
    }

    @Test
    fun `empty quiz is immediately finished`() {
        val quiz = SpellingQuiz.of(emptyList())
        assertTrue(quiz.isFinished)
        assertNull(quiz.current)
        assertNull(quiz.submit("x"))
        assertEquals(0f, quiz.accuracy())
    }
}
