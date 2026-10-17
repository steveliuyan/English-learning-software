package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Plan card selection must be honest about what has been learned: reviewed cards stop being
 * "new", due cards come from the derived schedule, and a storage failure is never reported as
 * an empty plan — the day's plan is an immutable snapshot, so an empty one would lose the day.
 *
 * New cards are drawn from the interleaved order (spec F1-02, decision 2026-10-17), never from
 * the word book's own order — a 4000-word cet4 book stored alphabetically would otherwise make
 * the learner finish every "a" word before ever seeing a "b" word.
 */
class StoredPlanCardSourceTest {
    private val now: Instant = Instant.parse("2026-09-19T08:00:00Z")

    /** 词条前 3 字母——交错序的「相似词」单位，与 [NewWordOrdering] 的分组口径一致。 */
    private fun prefixOf(cardId: String): String = cardId.substringAfter(':').take(3)

    @Test
    fun `new cards skip the ones already reviewed`() = runTest {
        val ids = listOf("cet4:ability", "cet4:achieve", "cet4:benefit")
        val source =
            StoredPlanCardSource(
                Content(ids),
                FakeEvents(reviewed = listOf("cet4:ability")),
            )

        assertEquals(
            NewWordOrdering.order(ids).filterNot { it == "cet4:ability" },
            source.newCardIds("cet4", 10),
        )
    }

    @Test
    fun `new cards are capped at the daily target`() = runTest {
        val ids = listOf("cet4:ability", "cet4:achieve", "cet4:benefit")
        val source =
            StoredPlanCardSource(
                Content(ids),
                FakeEvents(),
            )

        assertEquals(NewWordOrdering.order(ids).take(2), source.newCardIds("cet4", 2))
        assertEquals(emptyList<String>(), source.newCardIds("cet4", 0))
    }

    @Test
    fun `ten word sample fills target ten and target three truncates`() = runTest {
        val ids = (1..10).map { "sample:word-$it" }
        val source = StoredPlanCardSource(Content(ids), FakeEvents())
        val order = NewWordOrdering.order(ids)

        assertEquals(order, source.newCardIds("sample", 10))
        assertEquals(order.take(3), source.newCardIds("sample", 3))
    }

    @Test
    fun `new cards are the interleaved order minus reviewed ones as a subsequence`() = runTest {
        val ids =
            listOf(
                "cet4:act-1",
                "cet4:act-2",
                "cet4:com-1",
                "cet4:com-2",
                "cet4:con-1",
                "cet4:con-2",
            )
        val order = NewWordOrdering.order(ids)

        // 词书原始顺序的前两张就是 act-1/act-2 这对同前缀邻居；交错序必须把它们岔开。
        val firstTwo = StoredPlanCardSource(Content(ids), FakeEvents()).newCardIds("cet4", 2)
        assertEquals(2, firstTwo.size)
        assertNotEquals(prefixOf(firstTwo[0]), prefixOf(firstTwo[1]))

        // 剔除一个已学词只能从完整顺序里删掉它，其余相对次序与取到的张数都不许变。
        val source = StoredPlanCardSource(Content(ids), FakeEvents(reviewed = listOf("cet4:con-1")))
        val plan = source.newCardIds("cet4", 4)

        assertEquals(order.filterNot { it == "cet4:con-1" }.take(4), plan)
        assertEquals(4, plan.size)
        assertTrue(plan.none { it == "cet4:con-1" }, "a reviewed card must not be handed out as new")
        val positions = plan.map { order.indexOf(it) }
        assertEquals(positions.sorted(), positions, "removing a reviewed card must leave a subsequence")
    }

    @Test
    fun `short word book returns every remaining card and a zero target returns none`() = runTest {
        val ids = listOf("cet4:ability", "cet4:achieve")
        val source = StoredPlanCardSource(Content(ids), FakeEvents(reviewed = listOf("cet4:ability")))

        assertEquals(listOf("cet4:achieve"), source.newCardIds("cet4", 10))
        assertEquals(emptyList<String>(), source.newCardIds("cet4", 0))
        assertEquals(emptyList<String>(), source.newCardIds("cet4", -1))
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
