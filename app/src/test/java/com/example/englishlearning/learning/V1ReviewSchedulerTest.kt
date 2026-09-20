package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.ReviewFeedback
import com.example.englishlearning.learning.domain.ReviewState
import com.example.englishlearning.learning.domain.V1ReviewScheduler
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class V1ReviewSchedulerTest {
    private val scheduler = V1ReviewScheduler()
    private val now: Instant = Instant.parse("2026-09-19T08:00:00Z")

    @Test
    fun `again is not scheduled later than hard and hard not later than good`() {
        val again = scheduler.schedule(state(), ReviewFeedback.Again, now).nextReviewAt
        val hard = scheduler.schedule(state(), ReviewFeedback.Hard, now).nextReviewAt
        val good = scheduler.schedule(state(), ReviewFeedback.Good, now).nextReviewAt

        assertTrue(again <= hard, "expected Again <= Hard but was $again > $hard")
        assertTrue(hard <= good, "expected Hard <= Good but was $hard > $good")
    }

    @Test
    fun `every rating schedules strictly after now`() {
        ReviewFeedback.entries.forEach { feedback ->
            assertTrue(scheduler.schedule(state(), feedback, now).nextReviewAt > now, "$feedback did not move forward")
        }
    }

    @Test
    fun `scheduling carries the returned instant into the state`() {
        val scheduled = scheduler.schedule(state(dueAt = now.minusSeconds(600)), ReviewFeedback.Good, now)

        assertEquals(scheduled.nextReviewAt, scheduled.state.dueAt)
        assertEquals("card-1", scheduled.state.cardId)
    }

    @Test
    fun `scheduling is deterministic for the same inputs`() {
        val first = scheduler.schedule(state(), ReviewFeedback.Hard, now)
        val second = scheduler.schedule(state(), ReviewFeedback.Hard, now)

        assertEquals(first, second)
    }

    private fun state(dueAt: Instant? = null) = ReviewState(cardId = "card-1", dueAt = dueAt)
}
