package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardFeedback
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LearningRecordModelsTest {
    @Test
    fun `review status uses exact now boundary`() {
        val now = Instant.parse("2026-09-30T10:00:00Z")
        assertEquals(WordBookRecordStatus.Unlearned, reviewStatus(null, now))
        assertEquals(WordBookRecordStatus.Due, reviewStatus(now, now))
        assertEquals(WordBookRecordStatus.Learning, reviewStatus(now.plusMillis(1), now))
    }

    @Test
    fun `missing card fallback keeps segment after final colon and cannot open`() {
        val row = LearningRecord.missing(
            cardId = "ngsl-core-100:parent",
            feedback = CardFeedback.Fuzzy,
            occurredAt = Instant.EPOCH,
            localDate = LocalDate.of(2026, 9, 30),
            planId = "plan-1",
        )
        assertEquals("parent", row.displayLemma)
        assertFalse(row.canOpenDetail)
        assertTrue(row.wordCard == null)
    }

    @Test
    fun `same plan and card keeps earliest event while cross-plan same card is preserved`() {
        val first = LearningRecord.missing("book:a", CardFeedback.Known, Instant.ofEpochMilli(10), LocalDate.of(2026, 9, 30), planId = "plan-1")
        val later = LearningRecord.missing("book:a", CardFeedback.Unknown, Instant.ofEpochMilli(20), LocalDate.of(2026, 9, 30), planId = "plan-1")
        val otherPlan = LearningRecord.missing("book:a", CardFeedback.Fuzzy, Instant.ofEpochMilli(5), LocalDate.of(2026, 10, 1), planId = "plan-2")
        val other = LearningRecord.missing("book:b", CardFeedback.Fuzzy, Instant.ofEpochMilli(5), LocalDate.of(2026, 9, 30), planId = "plan-1")
        val deduplicated = deduplicateLearningRecords(listOf(later, otherPlan, other, first))
        assertEquals(3, deduplicated.size)
        assertEquals(listOf("plan-1:book:a", "plan-1:book:b", "plan-2:book:a"), deduplicated.map { "${it.planId}:${it.cardId}" }.sorted())
        assertEquals(CardFeedback.Known, deduplicated.single { it.planId == "plan-1" && it.cardId == "book:a" }.feedback)
    }

    @Test
    fun `history groups dates descending and rows ascending`() {
        val old = LearningRecord.missing("book:old", CardFeedback.Known, Instant.ofEpochMilli(30), LocalDate.of(2026, 9, 29), "plan-1")
        val newest = LearningRecord.missing("book:new", CardFeedback.Fuzzy, Instant.ofEpochMilli(20), LocalDate.of(2026, 9, 30), "plan-1")
        val early = LearningRecord.missing("book:early", CardFeedback.Unknown, Instant.ofEpochMilli(10), LocalDate.of(2026, 9, 30), "plan-1")
        val groups = groupLearningHistory(listOf(old, newest, early))
        assertEquals(listOf(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 29)), groups.map { it.localDate })
        assertEquals(listOf("book:early", "book:new"), groups.first().records.map { it.cardId })
    }
}
