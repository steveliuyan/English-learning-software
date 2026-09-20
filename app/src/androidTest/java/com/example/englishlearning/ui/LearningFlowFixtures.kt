package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.PlaceholderWordCardSource
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Shared androidTest fixtures for the F1-03 learning flow.
 *
 * Tests that only need the word card flow to be constructible use [wordCardFixtureViewModel];
 * it is wired to the real placeholder content so a test that does enter the flow sees a real
 * card instead of an empty one.
 */
internal fun wordCardFixtureViewModel(
    planResult: () -> TodayPlanResult = { placeholderCardPlan() },
): WordCardViewModel {
    val events = NoLearningEventRepository()
    return WordCardViewModel(
        todayPlan = { planResult() },
        content = PlaceholderWordCardSource(),
        events = events,
        submitFeedback = SubmitCardFeedbackUseCase(events, FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)),
        eventIds = EventIdFactory { "event-1" },
    )
}

/** A plan holding one real placeholder card, so entering learning really shows content. */
internal fun placeholderCardPlan(): TodayPlanResult =
    TodayPlanResult.Ready(
        TodayPlan(
            planId = "plan-1",
            profileId = "profile-1",
            localDate = LocalDate.of(2026, 9, 19),
            zoneId = "Asia/Shanghai",
            activeWordBookId = "cet4",
            newTarget = 1,
            dueTarget = 0,
            newCardIds = listOf(PlaceholderWordCardSource.cardId("cet4", "ability")),
            dueCardIds = emptyList(),
            ruleVersion = "v1",
            generatedAt = Instant.EPOCH,
        ),
    )

/** An event log with nothing recorded and no behaviour, for tests that never submit feedback. */
internal class NoLearningEventRepository : LearningEventRepository {
    override suspend fun append(event: LearningEvent, nextState: CardReviewState) =
        AppendEventResult.Appended(duplicate = false)

    override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)

    override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)

    override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)

    override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())

    override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())

    override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
}
