package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.ReviewFeedback
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class WordBookProgressMigrationTest {
    @Test
    fun exactNormalizedLemmaMatchesAcrossBooks() {
        val source = card("junior", " Ability ")
        val target = card("cet4", "ability")
        val state = CardReviewState("junior:ability-1", "junior", ReviewFeedback.Good, Instant.EPOCH, Instant.ofEpochMilli(1))
        val preview = WordBookProgressMigration.preview(listOf(source), listOf(target), mapOf(source.cardId to state))
        assertEquals(1, preview.candidates.size)
        assertEquals(target.cardId, preview.candidates.single().target.cardId)
    }

    @Test
    fun ambiguousTargetLemmaIsNotMigrated() {
        val source = card("junior", "ability")
        val state = CardReviewState(source.cardId, "junior", ReviewFeedback.Good, Instant.EPOCH, Instant.ofEpochMilli(1))
        val preview = WordBookProgressMigration.preview(listOf(source), listOf(card("cet4", "ability"), card("cet4", "ABILITY")), mapOf(source.cardId to state))
        assertEquals(0, preview.candidates.size)
        assertEquals(setOf("ability"), preview.ambiguousLemmas)
    }

    private fun card(book: String, lemma: String) = WordCard(
        cardId = "$book:${normalizeWordLemma(lemma)}-1",
        wordBookId = book,
        lemma = lemma,
        ipa = "/a/",
        partOfSpeech = "n.",
        meaningZh = "能力",
        example = "example",
    )
}
