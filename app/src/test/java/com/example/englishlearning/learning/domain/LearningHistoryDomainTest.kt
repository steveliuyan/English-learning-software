package com.example.englishlearning.learning.domain

import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LearningHistoryDomainTest {
    private fun date(day: Int) = LocalDate.of(2026, 9, day)

    @Test
    fun `computeStreak counts consecutive unlocked days ending today`() {
        val unlocked = mapOf(date(25) to true, date(26) to true, date(27) to true)
        assertEquals(3, computeStreak(unlocked, date(27)))
    }

    @Test
    fun `computeStreak starts from yesterday when today is not unlocked`() {
        val unlocked = mapOf(date(25) to true, date(26) to true, date(27) to false)
        assertEquals(2, computeStreak(unlocked, date(27)))
    }

    @Test
    fun `computeStreak breaks on a day without a plan snapshot`() {
        val unlocked = mapOf(date(25) to true, date(27) to true)
        assertEquals(1, computeStreak(unlocked, date(27)))
    }

    @Test
    fun `computeStreak returns zero for an empty history`() {
        assertEquals(0, computeStreak(emptyMap(), date(27)))
    }

    @Test
    fun `computeStreak does not start when neither today nor yesterday unlocked`() {
        val unlocked = mapOf(date(25) to true, date(26) to false, date(27) to false)
        assertEquals(0, computeStreak(unlocked, date(27)))
    }
}
