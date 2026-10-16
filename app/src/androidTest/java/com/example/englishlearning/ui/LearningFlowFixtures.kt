package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
import com.example.englishlearning.learning.PlaceholderWordCardSource
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.WordCard
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
    val events = NoopLearningEventRepository()
    return WordCardViewModel(
        todayPlan = { planResult() },
        content = PlaceholderWordCardSource(),
        events = events,
        submitFeedback = SubmitCardFeedbackUseCase(events, FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)),
        eventIds = EventIdFactory { "event-1" },
        settings = NoopLearningSettingsRepository(),
        vocabulary = object : com.example.englishlearning.learning.VocabularyRepository {
            override suspend fun add(profileId: String, wordBookId: String, cardId: String, feedback: String, addedAt: Instant) = Unit
            override suspend fun remove(profileId: String, wordBookId: String, cardId: String) = Unit
            override suspend fun contains(profileId: String, wordBookId: String, cardId: String) = false
            override suspend fun list(profileId: String, wordBookId: String?) = RepositoryResult.Success(emptyList<com.example.englishlearning.learning.VocabularyEntry>())
        },
        clock = FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC),
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
internal class NoopLearningEventRepository : LearningEventRepository {
    override suspend fun append(event: LearningEvent, nextState: CardReviewState) =
        AppendEventResult.Appended(duplicate = false)

    override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)

    override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)

    override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)

    override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())

    override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())

    override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
}

/** A settings repository that reports defaults and never fails, for fixture wiring. */
internal class NoopLearningSettingsRepository : LearningSettingsRepository {
    override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> =
        LearningSettingsRepositoryResult.Success(LearningSettings.defaults(profileId))

    override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> =
        LearningSettingsRepositoryResult.Success(Unit)
}

/**
 * 词卡 `Ready` 态的共享构造器（Task 6 从 `WordCardScreenTest` 的私有成员**原样上移**：
 * 参数名、默认值与每个字段值一字未改，只换了声明位置与可见性）。
 *
 * `WordCardRevealFocusTest` 需要同一张卡才能在设备上证伪「揭晓后焦点是否真的落到释义节点」，
 * 复制一份构造器会让两个测试对同一状态的口径分叉，故按本包既有约定放这里是唯一正确落点。
 */
internal fun ready(
    card: WordCard = card(),
    submitting: Boolean = false,
    message: String? = null,
    meaningRevealed: Boolean = false,
) = WordCardUiState.Ready(
    card = card,
    position = 1,
    total = 3,
    completedCount = 0,
    submitting = submitting,
    message = message,
    meaningRevealed = meaningRevealed,
)

/** 占位词卡构造器（同上：从 `WordCardScreenTest` 私有成员原样上移）。 */
internal fun card(
    example: String? = "She has the ability to explain complex ideas simply.",
    inflections: List<String> = emptyList(),
) = WordCard(
    cardId = "placeholder:cet4:ability",
    wordBookId = "cet4",
    lemma = "ability",
    ipa = "əˈbɪləti",
    partOfSpeech = "n.",
    meaningZh = "能力；才能",
    example = example,
    inflections = inflections,
)
