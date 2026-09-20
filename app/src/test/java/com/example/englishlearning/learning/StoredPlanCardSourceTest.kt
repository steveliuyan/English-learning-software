package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Plan card selection must be honest about what has been learned: reviewed cards stop being
 * "new", due cards come from the derived schedule, and a storage failure is never reported as
 * an empty plan — the day's plan is an immutable snapshot, so an empty one would lose the day.
 */
class StoredPlanCardSourceTest {
    private val now: Instant = Instant.parse("2026-09-19T08:00:00Z")

    @Test
    fun `new cards skip the ones already reviewed`() = runTest {
        val source =
            StoredPlanCardSource(
                Content(listOf("cet4:ability", "cet4:achieve", "cet4:benefit")),
                FakeEvents(reviewed = listOf("cet4:ability")),
            )

        assertEquals(listOf("cet4:achieve", "cet4:benefit"), source.newCardIds("cet4", 10))
    }

    @Test
    fun `new cards are capped at the daily target`() = runTest {
        val source =
            StoredPlanCardSource(
                Content(listOf("cet4:ability", "cet4:achieve", "cet4:benefit")),
                FakeEvents(),
            )

        assertEquals(listOf("cet4:ability", "cet4:achieve"), source.newCardIds("cet4", 2))
        assertEquals(emptyList<String>(), source.newCardIds("cet4", 0))
    }

    @Test
    fun `due cards come from the derived schedule at the requested instant`() = runTest {
        val events = FakeEvents(due = listOf("cet4:ability"))
        val source = StoredPlanCardSource(Content(listOf("cet4:ability")), events)

        assertEquals(listOf("cet4:ability"), source.dueCardIds("cet4", now))
        assertEquals("cet4" to now, events.dueQuery)
    }

    @Test
    fun `a storage failure is never reported as an empty plan`() = runTest {
        val source = StoredPlanCardSource(Content(listOf("cet4:ability")), FakeEvents(failing = true))

        assertFailsWith<PlanCardSourceUnavailable> { source.newCardIds("cet4", 5) }
        assertFailsWith<PlanCardSourceUnavailable> { source.dueCardIds("cet4", now) }
    }

    private class Content(private val ids: List<String>) : WordCardSource {
        /** Id selection only; content resolution is covered by the placeholder content tests. */
        override suspend fun cards(cardIds: List<String>): List<WordCard> = emptyList()

        override suspend fun cardIds(wordBookId: String): List<String> = ids.filter { it.startsWith("$wordBookId:") }
    }

    private class FakeEvents(
        private val reviewed: List<String> = emptyList(),
        private val due: List<String> = emptyList(),
        private val failing: Boolean = false,
    ) : LearningEventRepository {
        var dueQuery: Pair<String, Instant>? = null

        /** This fake only serves the read paths the plan card selection uses. */
        override suspend fun append(event: LearningEvent, nextState: CardReviewState): AppendEventResult =
            AppendEventResult.StorageUnavailable

        override suspend fun findEvent(eventId: String) = respondWith(null as LearningEvent?)

        override suspend fun findCardState(cardId: String) = respondWith(null as CardReviewState?)

        override suspend fun countEventsForCard(planId: String, cardId: String) = respondWith(0)

        override suspend fun completedCardIds(planId: String) = respondWith(emptyList<String>())

        override suspend fun reviewedCardIds(wordBookId: String) = respondWith(reviewed)

        override suspend fun dueCardIds(wordBookId: String, now: Instant): RepositoryResult<List<String>> {
            dueQuery = wordBookId to now
            return respondWith(due)
        }

        private fun <T> respondWith(value: T): RepositoryResult<T> =
            if (failing) {
                RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            } else {
                RepositoryResult.Success(value)
            }
    }
}
