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
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
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
    fun `a submitted feedback completes the card and advancing shows the next one`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()
        harness.viewModel.advance()
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
    fun `a recorded feedback holds the card with a receipt until the user advances`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Fuzzy)
        advanceUntilIdle()

        val ready = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertEquals("ability", ready.card.lemma, "评分成功后必须停在原卡上，回执读完再走")
        val receipt = assertNotNull(ready.receipt, "评分成功后必须给出回执")
        assertEquals(CardFeedback.Fuzzy, receipt.feedback)
        assertEquals(1, ready.completedCount, "停在原卡上不等于没记完成")
        assertFalse(ready.submitting)
        assertTrue(ready.meaningRevealed, "回执与释义必须同屏，否则看不出自己记成了什么")
    }

    @Test
    fun `advancing after a receipt shows the next card and clears the receipt`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        harness.viewModel.advance()
        advanceUntilIdle()

        val ready = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertEquals("achieve", ready.card.lemma)
        assertEquals(2, ready.position)
        assertNull(ready.receipt, "进入下一张卡时回执必须消失")
        assertFalse(ready.meaningRevealed, "下一张卡重新回到先自评的状态")
    }

    @Test
    fun `the receipt carries the interval the scheduler actually recorded`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        val receipt = assertNotNull(assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value).receipt)
        assertEquals(
            Instant.EPOCH.plus(java.time.Duration.ofDays(3)),
            receipt.nextReviewAt,
            "回执上的时间必须就是调度器写进去的那一个，不能是原型里的演示数字",
        )
        assertEquals("3 天后再问一次", receipt.whenLabel)
    }

    @Test
    fun `the receipt wording is derived from the recorded instant`() {
        assertEquals(
            "约 10 分钟后再问一次",
            receiptWhenLabel(Instant.EPOCH.plus(java.time.Duration.ofMinutes(10)), Instant.EPOCH),
        )
        assertEquals(
            "明天再问一次",
            receiptWhenLabel(Instant.EPOCH.plus(java.time.Duration.ofDays(1)), Instant.EPOCH),
        )
        assertEquals(
            "3 天后再问一次",
            receiptWhenLabel(Instant.EPOCH.plus(java.time.Duration.ofDays(3)), Instant.EPOCH),
        )
    }

    @Test
    fun `a second submission while the receipt is shown records nothing`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Unknown)
        advanceUntilIdle()

        assertEquals(1, harness.events.events.size, "回执期间再点三档不能写第二个事件")
        assertEquals(
            CardFeedback.Known,
            assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value).receipt?.feedback,
            "回执必须还是第一档，不能被第二次点击改写",
        )
    }

    @Test
    fun `advancing without a receipt is a no-op`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.advance()
        advanceUntilIdle()

        val ready = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertEquals("ability", ready.card.lemma)
        assertEquals(1, ready.position)
        assertTrue(harness.events.events.isEmpty(), "没评分就没有可「下一个」的对象")
    }

    @Test
    fun `the last card's receipt is marked as having nothing after it`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()
        val first = assertNotNull(assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value).receipt)
        assertTrue(first.hasNext, "第一张后面还有一张，回执要收在「下一个词」上")

        harness.viewModel.advance()
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()
        val last = assertNotNull(assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value).receipt)
        assertFalse(last.hasNext, "最后一张的回执要收在「完成本轮」上")
    }

    @Test
    fun `the three tiers are forwarded as their fixed ratings`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Unknown)
        advanceUntilIdle()
        harness.viewModel.advance()
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
    fun `submitting known opens the detail page only when openDetailOnKnown is enabled`() = cardTest { harness ->
        harness.settings.stored = LearningSettings(PROFILE_ID, openDetailOnKnown = true, openDetailOnFuzzy = true, openDetailOnForgotten = true)
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        val detail = harness.viewModel.detailCard.value
        assertNotNull(detail, "detail must open for the submitted card when the Known tier is enabled")
        assertEquals("ability", detail?.lemma)
    }

    @Test
    fun `submitting known does not open the detail page when openDetailOnKnown is disabled`() = cardTest { harness ->
        harness.settings.stored = LearningSettings(PROFILE_ID, openDetailOnKnown = false, openDetailOnFuzzy = true, openDetailOnForgotten = true)
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        assertNull(harness.viewModel.detailCard.value, "detail must stay closed when the Known tier is disabled")
    }

    @Test
    fun `submitting fuzzy opens the detail page by default`() = cardTest { harness ->
        // No stored settings (null) => repository returns defaults: fuzzy enabled.
        harness.settings.stored = null
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Fuzzy)
        advanceUntilIdle()

        assertNotNull(harness.viewModel.detailCard.value, "fuzzy must open detail by default")
    }

    @Test
    fun `submitting unknown opens the detail page by default`() = cardTest { harness ->
        harness.settings.stored = null
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Unknown)
        advanceUntilIdle()

        assertNotNull(harness.viewModel.detailCard.value, "forgotten must open detail by default")
    }

    @Test
    fun `a feedback write failure never opens the detail page`() = cardTest { harness ->
        harness.settings.stored = LearningSettings(PROFILE_ID, openDetailOnKnown = true, openDetailOnFuzzy = true, openDetailOnForgotten = true)
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.events.failWrites = true

        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()

        assertNull(harness.viewModel.detailCard.value, "a failed write must never open the detail page")
    }

    @Test
    fun `a settings read failure never opens the detail page`() = cardTest { harness ->
        harness.settings.failReads = true
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.submit(CardFeedback.Unknown)
        advanceUntilIdle()

        assertNull(harness.viewModel.detailCard.value, "when settings cannot be read, assume no tier is enabled")
    }

    @Test
    fun `clearing the detail page does not append a learning event`() = cardTest { harness ->
        harness.settings.stored = LearningSettings(PROFILE_ID, openDetailOnKnown = true, openDetailOnFuzzy = true, openDetailOnForgotten = true)
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()
        assertNotNull(harness.viewModel.detailCard.value)

        val eventsBefore = harness.events.events.size
        harness.viewModel.clearDetail()
        advanceUntilIdle()

        assertNull(harness.viewModel.detailCard.value, "returning from detail clears the detail state")
        assertEquals(eventsBefore, harness.events.events.size, "leaving the detail page must not write a learning event")
    }

    @Test
    fun `re-entering the flow keeps recorded cards complete and unanswered ones open`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Fuzzy)
        advanceUntilIdle()
        harness.viewModel.advance()
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
        harness.viewModel.advance()
        advanceUntilIdle()
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()
        harness.viewModel.advance()
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

    @Test
    fun `revealing the meaning alone records no event and keeps the card pending`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        val before = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertFalse(before.meaningRevealed, "a freshly shown card must start with the meaning hidden")

        harness.viewModel.revealMeaning()
        advanceUntilIdle()

        val ready = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertTrue(ready.meaningRevealed, "the reveal action must expose the meaning on the same card")
        assertEquals("ability", ready.card.lemma)
        assertEquals(0, ready.completedCount, "revealing is not a learning event and must not count as completion")
        assertTrue(harness.events.events.isEmpty(), "revealing must append no learning event")
    }

    @Test
    fun `advancing to the next card hides the meaning again`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.revealMeaning()
        harness.viewModel.submit(CardFeedback.Known)
        advanceUntilIdle()
        harness.viewModel.advance()
        advanceUntilIdle()

        val ready = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertEquals("achieve", ready.card.lemma)
        assertFalse(ready.meaningRevealed, "each new card starts recall-first again")
    }

    @Test
    fun `re-entering the flow hides the meaning of an unsubmitted card`() = cardTest { harness ->
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()
        harness.viewModel.revealMeaning()
        advanceUntilIdle()
        assertTrue(assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value).meaningRevealed)

        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        val ready = assertIs<WordCardUiState.Ready>(harness.viewModel.uiState.value)
        assertEquals("ability", ready.card.lemma)
        assertFalse(ready.meaningRevealed, "leaving and re-entering an unsubmitted card resets it to recall-first")
    }

    @Test
    fun `reveal is ignored outside the ready state`() = cardTest { harness ->
        harness.planResult = TodayPlanResult.MissingLearningSetup
        harness.viewModel.load(PROFILE_ID)
        advanceUntilIdle()

        harness.viewModel.revealMeaning()
        advanceUntilIdle()

        assertEquals(WordCardUiState.MissingSetup, harness.viewModel.uiState.value)
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
        val settings = FakeSettingsRepository()
        var planResult: TodayPlanResult = TodayPlanResult.Ready(plan(content.cards.map(WordCard::cardId)))
        private var nextEventId = 0

        val viewModel =
            WordCardViewModel(
                todayPlan = { planResult },
                content = content,
                events = events,
                submitFeedback = SubmitCardFeedbackUseCase(events, FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)),
                eventIds = EventIdFactory { "event-${++nextEventId}" },
                settings = settings,
                vocabulary = object : com.example.englishlearning.learning.VocabularyRepository {
                    override suspend fun add(profileId: String, wordBookId: String, cardId: String, feedback: String, addedAt: Instant) = Unit
                    override suspend fun remove(profileId: String, wordBookId: String, cardId: String) = Unit
                    override suspend fun contains(profileId: String, wordBookId: String, cardId: String) = false
                    override suspend fun list(profileId: String, wordBookId: String?) = RepositoryResult.Success(emptyList<com.example.englishlearning.learning.VocabularyEntry>())
                },
                clock = FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC),
            )
    }

    /**
     * In-memory [LearningSettingsRepository]. [stored] holds the last written settings so a test
     * can assert per-profile persistence; [failReads] simulates a settings read that cannot reach
     * storage (which must never open the detail page).
     */
    private class FakeSettingsRepository(
        var stored: LearningSettings? = null,
        var failReads: Boolean = false,
    ) : LearningSettingsRepository {
        override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> =
            if (failReads) {
                LearningSettingsRepositoryResult.StorageUnavailable
            } else {
                LearningSettingsRepositoryResult.Success(stored)
            }

        override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> {
            stored = settings
            return LearningSettingsRepositoryResult.Success(Unit)
        }
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
