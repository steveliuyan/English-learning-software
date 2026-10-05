package com.example.englishlearning.learning

import kotlin.test.Test
import kotlin.test.assertEquals

class WordBookProgressTest {
    @Test
    fun reviewedCardIdsAreCountedOnlyForTheRequestedBook() {
        val result = WordBookProgress.calculate(
            totalWords = 4,
            cardIds = listOf("cet4:a", "cet4:b", "cet4:c", "cet4:d"),
            reviewedCardIds = listOf("cet4:a", "cet4:a", "cet6:b", "other:x"),
        )
        assertEquals(1, result.learned)
        assertEquals(4, result.total)
        assertEquals(0.25f, result.fraction)
    }

    @Test
    fun emptyBookHasZeroProgressWithoutDivisionByZero() {
        val result = WordBookProgress.calculate(0, emptyList(), emptyList())
        assertEquals(0, result.learned)
        assertEquals(0f, result.fraction)
    }
}
