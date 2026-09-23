package com.example.englishlearning.learning.worksheet

import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.PlanCardFeedback
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.ReviewFeedback
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BuildWorksheetContentUseCaseTest {
    @Test
    fun `completed cards keep due then new plan order`() = runTest {
        val result = useCase(
            completed = listOf("new", "review", "outside"),
            cards = listOf(card("review", "review"), card("new", "new")),
        ).invoke("profile", WorksheetRange.COMPLETED_TODAY).getOrThrow()

        assertEquals(listOf("review", "new"), result.items.map(WorksheetItem::lemma))
    }

    @Test
    fun `plan external completed card is never exported`() = runTest {
        val result = useCase(
            completed = listOf("review", "new", "outside"),
            cards = listOf(card("review", "review"), card("new", "new"), card("outside", "outside")),
        ).invoke("profile", WorksheetRange.COMPLETED_TODAY).getOrThrow()

        assertEquals(listOf("review", "new"), result.items.map(WorksheetItem::lemma))
    }

    @Test
    fun `missing word card is counted without reordering remaining items`() = runTest {
        val result = useCase(
            completed = listOf("review", "new"),
            cards = listOf(card("review", "review")),
        ).invoke("profile", WorksheetRange.ALL_PLAN_ITEMS).getOrThrow()

        assertEquals(1, result.missingCardCount)
        assertEquals(listOf("review"), result.items.map(WorksheetItem::lemma))
    }

    @Test
    fun `difficult today only includes fuzzy and unknown completed cards`() = runTest {
        val result = useCase(
            completed = listOf("review", "new"),
            cards = listOf(card("review", "review"), card("new", "new")),
            feedback = listOf(
                PlanCardFeedback("review", ReviewFeedback.Hard),
                PlanCardFeedback("new", ReviewFeedback.Good),
            ),
        ).invoke("profile", WorksheetRange.DIFFICULT_TODAY).getOrThrow()

        assertEquals(listOf("review"), result.items.map(WorksheetItem::lemma))
    }

    private fun useCase(
        completed: List<String>,
        cards: List<WordCard>,
        feedback: List<PlanCardFeedback> = emptyList(),
    ) =
        BuildWorksheetContentUseCase(
            plans = Plans,
            events = Events(completed, feedback),
            content = Content(cards),
        )

    private fun card(id: String, lemma: String) = WordCard(
        cardId = id,
        wordBookId = "book",
        lemma = lemma,
        ipa = "/$lemma/",
        partOfSpeech = "n.",
        meaningZh = "${lemma}的释义",
    )

    private object Plans : TodayPlanRepository {
        override suspend fun find(profileId: String, localDate: LocalDate): TodayPlanResult = TodayPlanResult.NotFound

        override suspend fun findLatest(profileId: String): TodayPlanResult = TodayPlanResult.Ready(
            TodayPlan(
                planId = "plan",
                profileId = profileId,
                localDate = LocalDate.of(2026, 9, 23),
                zoneId = "Asia/Shanghai",
                activeWordBookId = "book",
                newTarget = 1,
                dueTarget = 1,
                newCardIds = listOf("new"),
                dueCardIds = listOf("review"),
                ruleVersion = "f1-v1",
                generatedAt = Instant.parse("2026-09-23T00:00:00Z"),
            ),
        )

        override suspend fun saveIfAbsent(plan: TodayPlan): TodayPlanResult = TodayPlanResult.Ready(plan)
    }

    private class Events(
        private val completed: List<String>,
        private val feedback: List<PlanCardFeedback>,
    ) : LearningEventRepository {
        override suspend fun append(event: LearningEvent, nextState: CardReviewState) =
            AppendEventResult.StorageUnavailable

        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)

        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)

        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)

        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(completed)

        override suspend fun completedCardFeedback(planId: String) = RepositoryResult.Success(feedback)

        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())

        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    }

    private class Content(private val cards: List<WordCard>) : WordCardSource {
        override suspend fun cardIds(wordBookId: String): List<String> = cards.map(WordCard::cardId)

        override suspend fun cards(cardIds: List<String>): List<WordCard> = cards.filter { it.cardId in cardIds }
    }
}
