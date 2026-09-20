package com.example.englishlearning.learning

import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.ReviewFeedback
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SubmitCardFeedbackUseCaseTest {
    private val firstSubmitAt: Instant = Instant.parse("2026-09-19T08:00:00Z")

    @Test
    fun `first submission records one complete event`() = runSubmit { repository, clock ->
        val useCase = useCase(repository, clock, firstSubmitAt)

        val result = useCase(command(eventId = "event-1", feedback = CardFeedback.Known))

        val recorded = assertIs<SubmitFeedbackResult.Recorded>(result)
        assertEquals(firstSubmitAt.plus(Duration.ofDays(3)), recorded.nextReviewAt)
        val event = repository.events.getValue("event-1")
        assertEquals("profile-1", event.profileId)
        assertEquals("plan-1", event.planId)
        assertEquals("card-1", event.cardId)
        assertEquals("cet4", event.wordBookId)
        assertEquals(ReviewFeedback.Good, event.feedback)
        assertEquals(firstSubmitAt, event.occurredAt)
        assertEquals("v1", event.algorithmVersion)
        assertEquals("v1", event.paramsVersion)
        assertNull(event.dueBefore, "the first submission has no state before it")
        assertEquals(recorded.nextReviewAt, event.nextReviewAt)
    }

    @Test
    fun `injected fsrs scheduler persists its algorithm and params versions`() = runSubmit { repository, clock ->
        val useCase = useCase(repository, clock, firstSubmitAt, com.example.englishlearning.learning.domain.FsrsReviewScheduler())

        val result = assertIs<SubmitFeedbackResult.Recorded>(useCase(command(eventId = "fsrs-1")))

        assertEquals(firstSubmitAt.plus(Duration.ofDays(1)), result.nextReviewAt)
        val event = repository.events.getValue("fsrs-1")
        assertEquals("fsrs-v1", event.algorithmVersion)
        assertEquals("fsrs-v1-default", event.paramsVersion)
        assertEquals(1, repository.events.size)
    }

    @Test
    fun `three tiers are recorded as their fixed ratings`() = runSubmit { repository, clock ->
        val useCase = useCase(repository, clock, firstSubmitAt)

        useCase(command(eventId = "event-unknown", cardId = "card-a", feedback = CardFeedback.Unknown))
        useCase(command(eventId = "event-fuzzy", cardId = "card-b", feedback = CardFeedback.Fuzzy))
        useCase(command(eventId = "event-known", cardId = "card-c", feedback = CardFeedback.Known))

        assertEquals(ReviewFeedback.Again, repository.events.getValue("event-unknown").feedback)
        assertEquals(ReviewFeedback.Hard, repository.events.getValue("event-fuzzy").feedback)
        assertEquals(ReviewFeedback.Good, repository.events.getValue("event-known").feedback)
    }

    @Test
    fun `replaying the same event id neither counts again nor adjusts the interval`() = runSubmit { repository, clock ->
        val useCase = useCase(repository, clock, firstSubmitAt)
        val original = useCase(command(eventId = "event-1", feedback = CardFeedback.Fuzzy))
        val recorded = assertIs<SubmitFeedbackResult.Recorded>(original)
        val stateAfterFirst = repository.states.getValue("card-1")

        clock.advanceTo(Instant.parse("2026-09-25T08:00:00Z"))
        // Replay with a different rating on purpose: the event id, not the payload,
        // decides idempotency, so nothing may move even if the payload differs.
        val replay = useCase(command(eventId = "event-1", feedback = CardFeedback.Known))

        val alreadyRecorded = assertIs<SubmitFeedbackResult.AlreadyRecorded>(replay)
        assertEquals(recorded.nextReviewAt, alreadyRecorded.nextReviewAt)
        assertEquals(1, repository.events.size, "a replay must not append a second event")
        assertEquals(1, repository.countFor("plan-1", "card-1"))
        assertEquals(stateAfterFirst, repository.states.getValue("card-1"), "a replay must not move the due date")
    }

    @Test
    fun `a later submission schedules from the previous next review`() = runSubmit { repository, clock ->
        val useCase = useCase(repository, clock, firstSubmitAt)
        val first = assertIs<SubmitFeedbackResult.Recorded>(useCase(command(eventId = "event-1")))
        val secondAt = Instant.parse("2026-09-24T08:00:00Z")
        clock.advanceTo(secondAt)

        val second = assertIs<SubmitFeedbackResult.Recorded>(useCase(command(eventId = "event-2")))

        assertEquals(first.nextReviewAt, repository.events.getValue("event-2").dueBefore)
        assertEquals(secondAt.plus(Duration.ofDays(3)), second.nextReviewAt)
        assertEquals(2, repository.countFor("plan-1", "card-1"))
    }

    @Test
    fun `submitting for a different card does not reuse the other card state`() = runSubmit { repository, clock ->
        val useCase = useCase(repository, clock, firstSubmitAt)
        useCase(command(eventId = "event-1", cardId = "card-1"))
        clock.advanceTo(Instant.parse("2026-09-24T08:00:00Z"))

        useCase(command(eventId = "event-2", cardId = "card-2"))

        assertNull(repository.events.getValue("event-2").dueBefore)
    }

    @Test
    fun `storage failure is reported and nothing is recorded`() = runSubmit { repository, clock ->
        val useCase = useCase(repository, clock, firstSubmitAt)
        repository.failReads = true

        assertEquals(SubmitFeedbackResult.StorageUnavailable, useCase(command(eventId = "event-1")))

        assertTrue(repository.events.isEmpty())
        assertTrue(repository.states.isEmpty())
    }

    @Test
    fun `losing the insert race reports the stored next review without counting again`() =
        runSubmit { repository, clock ->
            val useCase = useCase(repository, clock, firstSubmitAt)
            val winner = assertIs<SubmitFeedbackResult.Recorded>(useCase(command(eventId = "event-1")))
            val stateAfterWinner = repository.states.getValue("card-1")
            repository.simulateInsertRaceThenReadBack()
            clock.advanceTo(Instant.parse("2026-09-25T08:00:00Z"))

            val loser = useCase(command(eventId = "event-1"))

            val alreadyRecorded = assertIs<SubmitFeedbackResult.AlreadyRecorded>(loser)
            assertEquals(winner.nextReviewAt, alreadyRecorded.nextReviewAt)
            assertEquals(stateAfterWinner, repository.states.getValue("card-1"))
        }

    @Test
    fun `read failure after losing the insert race is retryable instead of returning an unpersisted schedule`() =
        runSubmit { repository, clock ->
            val useCase = useCase(repository, clock, firstSubmitAt)
            useCase(command(eventId = "event-1"))
            val stateAfterWinner = repository.states.getValue("card-1")
            repository.simulateInsertRaceThenReadBack(failReadBack = true)
            clock.advanceTo(Instant.parse("2026-09-25T08:00:00Z"))

            val result = useCase(command(eventId = "event-1", feedback = CardFeedback.Unknown))

            assertEquals(SubmitFeedbackResult.StorageUnavailable, result)
            assertEquals(1, repository.events.size)
            assertEquals(stateAfterWinner, repository.states.getValue("card-1"))
        }

    private fun useCase(
        repository: LearningEventRepository,
        clock: MutableClock,
        start: Instant,
        scheduler: com.example.englishlearning.learning.domain.ReviewScheduler = com.example.englishlearning.learning.domain.V1ReviewScheduler(),
    ): SubmitCardFeedbackUseCase {
        clock.advanceTo(start)
        return SubmitCardFeedbackUseCase(repository = repository, clock = clock, scheduler = scheduler)
    }

    private fun command(
        eventId: String,
        cardId: String = "card-1",
        feedback: CardFeedback = CardFeedback.Known,
    ) = SubmitFeedbackCommand(
        eventId = eventId,
        profileId = "profile-1",
        planId = "plan-1",
        cardId = cardId,
        wordBookId = "cet4",
        feedback = feedback,
    )

    private fun runSubmit(block: suspend (InMemoryLearningEventRepository, MutableClock) -> Unit) {
        kotlinx.coroutines.test.runTest {
            block(InMemoryLearningEventRepository(), MutableClock(firstSubmitAt))
        }
    }

    private class MutableClock(private var now: Instant) : ClockProvider {
        override fun instant(): Instant = now

        override fun zoneId(): ZoneOffset = ZoneOffset.UTC

        fun advanceTo(next: Instant) {
            now = next
        }
    }

    private class InMemoryLearningEventRepository : LearningEventRepository {
        val events = LinkedHashMap<String, LearningEvent>()
        val states = LinkedHashMap<String, CardReviewState>()
        var failReads = false

        private var raceEventId: String? = null
        private var hideEventUntilAppend = false
        private var failReadBack = false

        /**
         * Makes the next command traverse the real check-then-insert race branch: its first
         * lookup returns null, `append` returns duplicate, then the stored winner is readable.
         */
        fun simulateInsertRaceThenReadBack(failReadBack: Boolean = false) {
            raceEventId = events.keys.single()
            hideEventUntilAppend = true
            this.failReadBack = failReadBack
        }

        override suspend fun append(event: LearningEvent, nextState: CardReviewState): AppendEventResult =
            when {
                event.eventId == raceEventId -> {
                    hideEventUntilAppend = false
                    AppendEventResult.Appended(duplicate = true)
                }

                events.containsKey(event.eventId) -> AppendEventResult.Appended(duplicate = true)
                else -> {
                    events[event.eventId] = event
                    states[event.cardId] = nextState
                    AppendEventResult.Appended(duplicate = false)
                }
            }

        override suspend fun findEvent(eventId: String): RepositoryResult<LearningEvent?> =
            when {
                failReadBack && eventId == raceEventId && !hideEventUntilAppend ->
                    RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)

                hideEventUntilAppend && eventId == raceEventId -> RepositoryResult.Success(null)
                else -> reads { events[eventId] }
            }

        override suspend fun findCardState(cardId: String): RepositoryResult<CardReviewState?> =
            reads { states[cardId] }

        override suspend fun countEventsForCard(planId: String, cardId: String): RepositoryResult<Int> =
            reads { events.values.count { it.planId == planId && it.cardId == cardId } }

        override suspend fun completedCardIds(planId: String): RepositoryResult<List<String>> =
            reads { events.values.filter { it.planId == planId }.map { it.cardId }.distinct() }

        override suspend fun reviewedCardIds(wordBookId: String): RepositoryResult<List<String>> =
            reads { states.values.filter { it.wordBookId == wordBookId }.map { it.cardId } }

        override suspend fun dueCardIds(wordBookId: String, now: Instant): RepositoryResult<List<String>> =
            reads {
                states.values
                    .filter { it.wordBookId == wordBookId && !it.nextReviewAt.isAfter(now) }
                    .sortedBy { it.nextReviewAt }
                    .map { it.cardId }
            }

        fun countFor(planId: String, cardId: String): Int =
            events.values.count { it.planId == planId && it.cardId == cardId }

        private fun <T> reads(block: () -> T): RepositoryResult<T> =
            if (failReads) {
                RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            } else {
                RepositoryResult.Success(block())
            }
    }
}
