package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.LearningProfileRepositoryError
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.ReviewFeedback
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

/**
 * F1-03 session behaviour: one effective feedback per card, storage failures never advance a
 * card, a retry reuses the same event id, and completion is read back from the event log so
 * leaving and re-entering the flow neither loses nor invents progress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WordCardViewModelTest {
    @Test
    fun `load shows the first card of the plan and the session total`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        val ready = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertEquals("ability", ready.card.lemma)
        assertEquals(1, ready.position)
        assertEquals(2, ready.total)
        assertEquals(0, ready.completedCount)
        assertFalse(ready.submitting)
        assertNull(ready.message)
    }

    @Test
    fun `a submitted feedback completes the card and advances to the next one`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        val ready = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertEquals("achieve", ready.card.lemma)
        assertEquals(2, ready.position)
        assertEquals(1, ready.completedCount)
        val event = harness.events.events.values.single()
        assertEquals(PLAN_ID, event.planId)
        assertEquals(harness.content.cards.first().cardId, event.cardId)
        assertEquals(ReviewFeedback.Good, event.feedback)
    }

    @Test
    fun `the three tiers are forwarded as their fixed ratings`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Unknown)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Fuzzy)
        advanceUntilIdle()

        assertEquals(
            listOf(ReviewFeedback.Again, ReviewFeedback.Hard),
            harness.events.events.values.map { it.feedback },
        )
    }

    @Test
    fun `a second tap while saving is ignored`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Known)
        assertTrue(
            assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value).submitting,
            "saving must be visible immediately so a double tap can be rejected",
        )
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        assertEquals(1, harness.events.events.size)
    }

    @Test
    fun `a storage failure keeps the card open and reports an actionable message`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.events.failWrites = true

        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        val ready = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertEquals("ability", ready.card.lemma, "a card that was not saved must stay open")
        assertEquals(0, ready.completedCount)
        assertFalse(ready.submitting)
        assertNotNull(ready.message)
        assertTrue(harness.events.events.isEmpty())
    }

    @Test
    fun `a retry after a failure reuses the event id so nothing is counted twice`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.events.failWrites = true
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        harness.events.failWrites = false
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        assertEquals(
            listOf("event-1", "event-1"),
            harness.events.appendAttempts,
            "a retry must reuse the event id of the submission it is retrying",
        )
        assertEquals(1, harness.events.events.size)
        assertEquals(1, assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value).completedCount)
    }

    @Test
    fun `re-entering the flow keeps recorded cards complete and unanswered ones open`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Fuzzy)
        advanceUntilIdle()
        assertEquals(2, assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value).position)

        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        val ready = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertEquals("achieve", ready.card.lemma, "the unanswered card must still be open")
        assertEquals(1, ready.completedCount, "the recorded card must stay complete")
    }

    @Test
    fun `every card recorded reaches the all done state`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        val done = assertIs<WordCardUiState.AllDone>(harness.viewModel.uiState.value)
        assertEquals(2, done.total)
        assertEquals(2, done.completedCount)
    }

    @Test
    fun `a plan without content reports no cards instead of an empty ready state`() = cardTest { harness ->
        harness.content.cards = emptyList()

        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        assertEquals(WordCardUiState.NoCards, harness.viewModel.uiState.value)
    }

    @Test
    fun `a missing learning setup is reported as such`() =
        cardTest { harness ->
            harness.planResult = TodayPlanResult.MissingLearningSetup

            harness.viewModel.load(PROFILE_ID)
            advanceUntilIdle()

            assertEquals(WordCardUiState.MissingSetup, harness.viewModel.uiState.value)
        }

    @Test
    fun `an unreadable event log makes the flow unavailable instead of empty`() = cardTest { harness ->
        harness.events.failReads = true

        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        assertEquals(WordCardUiState.Unavailable, harness.viewModel.uiState.value)
    }

    private fun cardTest(block: suspend TestScope.(Harness) -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                block(Harness())
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class Harness {
        val content = FakeContent(defaultCards())
        val events = InMemoryEvents()
        var planResult: TodayPlanResult = TodayPlanResult.Ready(plan(content.cards.map(WordCard::cardId)))
        private var nextEventId = 0

        val viewModel =
            WordCardViewModel(
                todayPlan = { planResult },
                content = content,
                events = events,
                submitFeedback = SubmitCardFeedbackUseCase(events, FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)),
                eventIds = EventIdFactory { "event-${++nextEventId}" },
            )
    }

    private class FakeContent(var cards: List<WordCard>) : WordCardSource {
        override suspend fun cardIds(wordBookId: String): List<String> =
            cards.filter { it.wordBookId == wordBookId }.map(WordCard::cardId)

        override suspend fun cards(cardIds: List<String>): List<WordCard> =
            cardIds.mapNotNull { id -> cards.find { it.cardId == id } }
    }

    private class InMemoryEvents : LearningEventRepository {
        val events = LinkedHashMap<String, LearningEvent>()
        val states = LinkedHashMap<String, CardReviewState>()
        val appendAttempts = mutableListOf<String>()
        var failWrites = false
        var failReads = false

        override suspend fun append(event: LearningEvent, nextState: CardReviewState): AppendEventResult {
            appendAttempts += event.eventId
            return when {
                failWrites -> AppendEventResult.StorageUnavailable
                events.containsKey(event.eventId) -> AppendEventResult.Appended(duplicate = true)
                else -> {
                    events[event.eventId] = event
                    states[event.cardId] = nextState
                    AppendEventResult.Appended(duplicate = false)
                }
            }
        }

        override suspend fun findEvent(eventId: String) = respond { events[eventId] }

        override suspend fun findCardState(cardId: String) = respond { states[cardId] }

        override suspend fun countEventsForCard(planId: String, cardId: String) =
            respond { events.values.count { it.planId == planId && it.cardId == cardId } }

        override suspend fun completedCardIds(planId: String) =
            respond { events.values.filter { it.planId == planId }.map { it.cardId }.distinct() }

        override suspend fun reviewedCardIds(wordBookId: String) =
            respond { states.values.filter { it.wordBookId == wordBookId }.map { it.cardId } }

        override suspend fun dueCardIds(wordBookId: String, now: Instant) = respond { emptyList<String>() }

        private fun <T> respond(value: () -> T): RepositoryResult<T> =
            if (failReads) {
                RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            } else {
                RepositoryResult.Success(value())
            }
    }

    private companion object {
        const val PROFILE_ID = "profile-1"
        const val PLAN_ID = "plan-1"

        fun defaultCards() =
            listOf(
                card("card-ability", "ability"),
                card("card-achieve", "achieve"),
            )

        fun card(cardId: String, lemma: String) =
            WordCard(
                cardId = cardId,
                wordBookId = "cet4",
                lemma = lemma,
                ipa = "/ˈx/",
                partOfSpeech = "n.",
                meaningZh = "释义",
            )

        fun plan(cardIds: List<String>) =
            TodayPlan(
                planId = PLAN_ID,
                profileId = PROFILE_ID,
                localDate = LocalDate.of(2026, 9, 19),
                zoneId = "Asia/Shanghai",
                activeWordBookId = "cet4",
                newTarget = cardIds.size,
                dueTarget = 0,
                newCardIds = cardIds,
                dueCardIds = emptyList(),
                ruleVersion = "v1",
                generatedAt = Instant.EPOCH,
            )
    }
}
