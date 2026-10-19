package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
import com.example.englishlearning.learning.PlaceholderWordCardSource
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordBook
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
 * 有状态的学习档案假仓库（R-13.1 / m-3：`SetupScreenScreenshotTest` 与 `WordbookPickerHeaderTest` 原先各带
 * 一份**逐字相同**的私有副本，本条按本包既有约定把它们上移到共享落点，两边只留调用）。
 *
 * 与 [NoopLearningEventRepository] 那一族的区别：这一族**会记住**写入，所以 `SeedWordBooksUseCase`
 * 种下的册能在 `listWordBooks` 里读回来——词书选取屏的 connected 判据依赖这个行为。
 */
internal class FakeLearningProfileRepository : LearningProfileRepository {
    private var profile: LearningProfile? = null
    private val wordBooks = mutableListOf<WordBook>()

    override suspend fun current(profileId: String) = RepositoryResult.Success(profile)

    override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> {
        this.profile = profile
        return RepositoryResult.Success(Unit)
    }

    override suspend fun listWordBooks() = RepositoryResult.Success(wordBooks)

    override suspend fun findWordBook(id: String) = RepositoryResult.Success(wordBooks.find { it.id == id })

    override suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit> {
        wordBooks.removeAll { it.id == wordBook.id }
        wordBooks += wordBook
        return RepositoryResult.Success(Unit)
    }
}

/** 有状态的学习设置假仓库（同上：`save` 写得进、`find` 读得出，永不失败）。 */
internal class FakeLearningSettingsRepository : LearningSettingsRepository {
    private val stored = mutableMapOf<String, LearningSettings>()

    override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> =
        LearningSettingsRepositoryResult.Success(stored[profileId])

    override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> {
        stored[settings.profileId] = settings
        return LearningSettingsRepositoryResult.Success(Unit)
    }
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
