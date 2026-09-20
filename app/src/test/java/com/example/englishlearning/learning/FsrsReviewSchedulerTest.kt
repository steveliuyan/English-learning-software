package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.FsrsReviewScheduler
import com.example.englishlearning.learning.domain.ReviewFeedback
import com.example.englishlearning.learning.domain.ReviewState
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class FsrsReviewSchedulerTest {
    private val scheduler = FsrsReviewScheduler()
    private val now = Instant.parse("2026-09-20T04:00:00Z")

    @Test
    fun `first good review creates deterministic future due date`() {
        val result = scheduler.schedule(ReviewState("card-1", null), ReviewFeedback.Good, now)

        assertEquals(now.plusSeconds(86_400), result.nextReviewAt)
        assertEquals("fsrs-v1", result.algorithmVersion)
    }

    @Test
    fun `again is not later than hard and hard is not later than good`() {
        val state = ReviewState("card-1", null)
        val again = scheduler.schedule(state, ReviewFeedback.Again, now).nextReviewAt
        val hard = scheduler.schedule(state, ReviewFeedback.Hard, now).nextReviewAt
        val good = scheduler.schedule(state, ReviewFeedback.Good, now).nextReviewAt

        assertTrue(again <= hard)
        assertTrue(hard <= good)
    }

    @Test
    fun `same input is deterministic`() {
        val input = ReviewState("card-1", null)
        assertEquals(
            scheduler.schedule(input, ReviewFeedback.Good, now),
            scheduler.schedule(input, ReviewFeedback.Good, now),
        )
    }

    @Test
    fun `future due state advances from existing due date`() {
        val due = now.plusSeconds(86_400)
        val result = scheduler.schedule(ReviewState("card-1", due), ReviewFeedback.Good, now)

        assertTrue(result.nextReviewAt > due)
    }

    @Test
    fun `past due state is scheduled from now`() {
        val result = scheduler.schedule(
            ReviewState("card-1", now.minusSeconds(3600)),
            ReviewFeedback.Again,
            now,
        )

        assertEquals(now.plusSeconds(600), result.nextReviewAt)
    }
}
