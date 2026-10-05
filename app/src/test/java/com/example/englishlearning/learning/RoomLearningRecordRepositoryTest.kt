package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardFeedback
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoomLearningRecordRepositoryTest {
    @Test
    fun `event feedback storage names map to user feedback`() {
        assertEquals(CardFeedback.Known, cardFeedbackFromStorage("Good"))
        assertEquals(CardFeedback.Fuzzy, cardFeedbackFromStorage("Hard"))
        assertEquals(CardFeedback.Unknown, cardFeedbackFromStorage("Again"))
    }

    @Test
    fun `review projection maps exact now boundary`() {
        val now = Instant.parse("2026-09-30T10:00:00Z")
        assertEquals(WordBookRecordStatus.Due, reviewStatus(now, now))
        assertEquals(WordBookRecordStatus.Learning, reviewStatus(now.plusMillis(1), now))
    }
}
