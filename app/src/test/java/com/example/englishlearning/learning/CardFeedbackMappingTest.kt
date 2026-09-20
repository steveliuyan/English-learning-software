package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.ReviewFeedback
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class CardFeedbackMappingTest {
    @Test
    fun `unknown maps to again`() {
        assertEquals(ReviewFeedback.Again, CardFeedback.Unknown.toReviewFeedback())
    }

    @Test
    fun `fuzzy maps to hard`() {
        assertEquals(ReviewFeedback.Hard, CardFeedback.Fuzzy.toReviewFeedback())
    }

    @Test
    fun `known maps to good`() {
        assertEquals(ReviewFeedback.Good, CardFeedback.Known.toReviewFeedback())
    }

    @Test
    fun `every tier has a mapped rating`() {
        assertEquals(CardFeedback.entries.size, CardFeedback.entries.map { it.toReviewFeedback() }.toSet().size)
    }
}
