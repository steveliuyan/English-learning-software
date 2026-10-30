package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.SubmitFeedbackCommand
import com.example.englishlearning.learning.SubmitFeedbackResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.VocabularyRepository
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.WordCard
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface WordCardUiState {
    data object Loading : WordCardUiState

    data class Ready(
        val card: WordCard,
        /** 1-based position of [card] among the session's cards. */
        val position: Int,
        val total: Int,
        val completedCount: Int,
        val submitting: Boolean,
        val message: String?,
        val isReview: Boolean = false,
        val taskCompletedCount: Int = 0,
        val taskTotal: Int = 0,
        /**
         * Recall-first flow (spec F1-03, decision 2026-10-14): the Chinese meaning and
         * inflections stay hidden until the user explicitly reveals them. Revealing is not a
         * learning event; every freshly emitted card starts hidden, so advancing or
         * re-entering a session resets it.
         */
        val meaningRevealed: Boolean = false,
        /**
         * Non-null while the just-graded card is held for reading its receipt (prototype
         * `sSession` keeps the card on screen with `recap()` until the user taps 下一个词 /
         * 完成本轮). Completion is already recorded at this point; [WordCardViewModel.advance]
         * is the only action that moves on, so a second tap can never write a second event.
         */
        val receipt: FeedbackReceipt? = null,
    ) : WordCardUiState

    /** Every card of the session has a recorded feedback; unlocking is F1-04's concern. */
    data class AllDone(
        val total: Int,
        val completedCount: Int,
        val reviewTotal: Int = 0,
        val reviewCompleted: Int = 0,
        val newTotal: Int = 0,
        val newCompleted: Int = 0,
    ) : WordCardUiState

    data class ReviewCompleted(
        val reviewTotal: Int,
        val reviewCompleted: Int,
        val newTotal: Int,
    ) : WordCardUiState

    data object NoCards : WordCardUiState

    data object MissingSetup : WordCardUiState

    data object Unavailable : WordCardUiState
}

/**
 * What the user sees right after a recorded rating (prototype `recap()`).
 *
 * [nextReviewAt] and [whenLabel] are the **recorded** interval, not the prototype's demo copy:
 * the prototype's "6 天 / 本轮末尾再出现一次" describe a scheduling rule this app does not have
 * (V1: Again 10 分钟 / Hard 1 天 / Good 3 天, and a forgotten word is not re-queued this round).
 * [hasNext] picks 下一个词 vs 完成本轮 without the screen having to re-derive the session.
 */
data class FeedbackReceipt(
    val feedback: CardFeedback,
    val nextReviewAt: Instant,
    val whenLabel: String,
    val hasNext: Boolean,
)

/**
 * 回执上的时间口径：一律从调度器真正写入的 [nextReviewAt] 反推，禁止写死原型的演示数字。
 * 回执出现在提交成功之后，所以 [now] 就是提交时刻。
 */
internal fun receiptWhenLabel(nextReviewAt: Instant, now: Instant): String {
    val minutes = Duration.between(now, nextReviewAt).toMinutes()
    return when {
        minutes <= 0L -> "稍后再问一次"
        minutes < 60L -> "约 $minutes 分钟后再问一次"
        minutes < 24L * 60L -> "约 ${minutes / 60} 小时后再问一次"
        minutes < 48L * 60L -> "明天再问一次"
        else -> "${minutes / (24L * 60L)} 天后再问一次"
    }
}

/**
 * Session state for the F1-03 word card flow.
 *
 * One effective feedback completes a plan card. Failing to write locally must never advance
 * the card, and a retry must not be counted twice: the submission keeps its event id until it
 * succeeds, so an idempotent retry is indistinguishable from a single submission (AC1-04).
 * Leaving the flow is always allowed — unsubmitted cards stay unsubmitted, and cards already
 * recorded stay complete because completion is read back from the event log, not from memory.
 */
@HiltViewModel
class WordCardViewModel @Inject constructor(
    private val todayPlan: TodayPlanUseCaseContract,
    private val content: WordCardSource,
    private val events: LearningEventRepository,
    private val submitFeedback: SubmitCardFeedbackUseCase,
    private val eventIds: EventIdFactory,
    private val settings: LearningSettingsRepository,
    private val vocabulary: VocabularyRepository,
    private val clock: ClockProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<WordCardUiState>(WordCardUiState.Loading)
    val uiState: StateFlow<WordCardUiState> = _uiState

    /** Nullable detail state: non-null means the CardDetailScreen overlay is showing the held card. */
    private val _detailCard = MutableStateFlow<WordCard?>(null)
    val detailCard: StateFlow<WordCard?> = _detailCard

    private var session: Session? = null
    private var pendingEventId: String? = null

    /** Card whose meaning the user has explicitly revealed; at most one, and never a completed one. */
    private var revealedCardId: String? = null

    fun load(profileId: String) {
        session = null
        pendingEventId = null
        revealedCardId = null
        _detailCard.value = null
        _uiState.value = WordCardUiState.Loading
        viewModelScope.launch {
            when (val result = todayPlan(profileId)) {
                is TodayPlanResult.Ready -> startSession(profileId, result.plan)
                TodayPlanResult.MissingLearningSetup -> _uiState.value = WordCardUiState.MissingSetup
                TodayPlanResult.NotFound,
                TodayPlanResult.StorageUnavailable,
                -> _uiState.value = WordCardUiState.Unavailable
            }
        }
    }

    fun startNewPhase() {
        val current = session ?: return
        if (current.phase != SessionPhase.REVIEW || current.reviewCardIds.any { it !in current.completed }) return
        current.phase = SessionPhase.NEW
        emit(current)
    }

    /**
     * Explicit recall-first reveal (spec F1-03): exposes the hidden meaning on the visible card.
     * Pure view state — no learning event is written, no completion changes, and a mid-submit
     * or non-ready screen ignores it. Only the currently visible, unfinished card can hold the
     * revealed flag; [load] clears it, and [emit] re-hides every other card.
     */
    fun revealMeaning() {
        val ready = _uiState.value as? WordCardUiState.Ready ?: return
        if (ready.submitting) return
        revealedCardId = ready.card.cardId
        _uiState.value = ready.copy(meaningRevealed = true)
    }

    fun submit(feedback: CardFeedback) {
        val current = session ?: return
        // Only a visible, not-yet-completed card accepts a rating; a double tap or a card that
        // is already recorded must be a no-op rather than a second event.
        val ready =
            (_uiState.value as? WordCardUiState.Ready)
                ?.takeIf { !it.submitting && it.receipt == null && it.card.cardId !in current.completed }
                ?: return
        val card = ready.card

        val eventId = pendingEventId ?: eventIds.newId().also { pendingEventId = it }
        _uiState.value = ready.copy(submitting = true, message = null)

        viewModelScope.launch {
            val command =
                SubmitFeedbackCommand(
                    eventId = eventId,
                    profileId = current.profileId,
                    planId = current.planId,
                    cardId = card.cardId,
                    wordBookId = current.wordBookId,
                    feedback = feedback,
                )
            when (val result = submitFeedback(command)) {
                is SubmitFeedbackResult.Recorded,
                is SubmitFeedbackResult.AlreadyRecorded,
                -> {
                    val nextReviewAt =
                        when (result) {
                            is SubmitFeedbackResult.Recorded -> result.nextReviewAt
                            is SubmitFeedbackResult.AlreadyRecorded -> result.nextReviewAt
                            SubmitFeedbackResult.StorageUnavailable -> return@launch
                        }
                    pendingEventId = null
                    if (feedback == CardFeedback.Unknown) {
                        vocabulary.add(current.profileId, current.wordBookId, card.cardId, "Again", clock.instant())
                    }
                    current.completed += card.cardId
                    holdWithReceipt(ready, current, card, feedback, nextReviewAt)
                    // Only after the local event is written and the completion set is updated do we
                    // read the current profile's tier settings and decide whether to surface the
                    // just-submitted card's detail page. A settings read failure must never open it.
                    openDetailIfEnabled(feedback, card, current.profileId)
                }
                SubmitFeedbackResult.StorageUnavailable ->
                    emit(current, message = FEEDBACK_NOT_SAVED)
            }
        }
    }

    /**
     * 提交成功后**停在原卡**上出回执（原型 `sSession` 的 `recap()` + 下一个词）。
     *
     * 与 [emit] 的差别只在「换不换卡」：完成数、区间、详情开合都照常推进，唯一变化是
     * 画面不立刻翻到下一张，让用户读完「记为哪一档、下次什么时候再见」。释义同时展开——
     * 回执若配着一张还藏着释义的卡，用户看不出自己刚才是凭印象还是凭释义选的。
     */
    private fun holdWithReceipt(
        ready: WordCardUiState.Ready,
        current: Session,
        card: WordCard,
        feedback: CardFeedback,
        nextReviewAt: Instant,
    ) {
        _uiState.value =
            ready.copy(
                submitting = false,
                message = null,
                completedCount = ready.completedCount + 1,
                taskCompletedCount = ready.taskCompletedCount + 1,
                meaningRevealed = true,
                receipt =
                    FeedbackReceipt(
                        feedback = feedback,
                        nextReviewAt = nextReviewAt,
                        whenLabel = receiptWhenLabel(nextReviewAt, clock.instant()),
                        hasNext = hasFollowingCard(current, card),
                    ),
            )
    }

    /**
     * 离开回执、进入下一张卡（原型「下一个词 / 完成本轮」）。没有回执时是空操作——
     * 否则一次误触就能跳过一张还没评分的卡。
     */
    fun advance() {
        val ready = _uiState.value as? WordCardUiState.Ready ?: return
        if (ready.receipt == null) return
        val current = session ?: return
        emit(current)
    }

    /**
     * 回执之后本阶段是否还有下一张卡。复习阶段收尾时**不算**新词：这时 [emit] 会落到
     * `ReviewCompleted`，用户先看到「复习完成 → 开始新词」，所以文案应是「完成本轮」。
     */
    private fun hasFollowingCard(current: Session, card: WordCard): Boolean {
        val phaseIds =
            when (current.phase) {
                SessionPhase.REVIEW -> current.reviewCardIds
                SessionPhase.NEW -> current.newCardIds
            }
        return current.cards.any { it.cardId != card.cardId && it.cardId in phaseIds && it.cardId !in current.completed }
    }

    /**
     * Opens the detail page for [card] when the submitted [feedback] tier is enabled for the
     * profile. Reading happens after the write so the detail overlay can never show for an
     * unsaved submission; the detail state is a pure view, written nowhere into the event log.
     * A settings read failure (or an unknown profile with no stored row, which falls back to
     * [LearningSettings.defaults]) leaves the detail state closed.
     */
    private suspend fun openDetailIfEnabled(feedback: CardFeedback, card: WordCard, profileId: String) {
        val settings =
            when (val result = settings.find(profileId)) {
                is LearningSettingsRepositoryResult.Success -> result.value ?: LearningSettings.defaults(profileId)
                LearningSettingsRepositoryResult.StorageUnavailable -> return
            }
        val open =
            when (feedback) {
                CardFeedback.Known -> settings.openDetailOnKnown
                CardFeedback.Fuzzy -> settings.openDetailOnFuzzy
                CardFeedback.Unknown -> settings.openDetailOnForgotten
            }
        if (open) _detailCard.value = card
    }

    /** Returns from the detail overlay; leaves the learning session (and its completion) untouched. */
    fun clearDetail() {
        _detailCard.value = null
    }

    /** Whether the current detail overlay has another card in the same learning phase. */
    fun hasNextDetail(): Boolean {
        val currentCard = _detailCard.value ?: return false
        val currentSession = session ?: return false
        val phaseCards = currentSession.cards.filter { card ->
            when (currentSession.phase) {
                SessionPhase.REVIEW -> card.cardId in currentSession.reviewCardIds
                SessionPhase.NEW -> card.cardId in currentSession.newCardIds
            }
        }
        val currentIndex = phaseCards.indexOfFirst { it.cardId == currentCard.cardId }
        return currentIndex >= 0 && currentIndex + 1 < phaseCards.size
    }

    /** Opens the next card in the current learning phase without changing learning progress. */
    fun nextDetail() {
        val currentCard = _detailCard.value ?: return
        val currentSession = session ?: return
        val phaseCards = currentSession.cards.filter { card ->
            when (currentSession.phase) {
                SessionPhase.REVIEW -> card.cardId in currentSession.reviewCardIds
                SessionPhase.NEW -> card.cardId in currentSession.newCardIds
            }
        }
        val nextIndex = phaseCards.indexOfFirst { it.cardId == currentCard.cardId } + 1
        _detailCard.value = phaseCards.getOrNull(nextIndex)
    }

    private suspend fun startSession(profileId: String, plan: TodayPlan) {
        val completed =
            when (val stored = events.completedCardIds(plan.planId)) {
                is RepositoryResult.Success -> stored.value
                is RepositoryResult.Failure -> {
                    _uiState.value = WordCardUiState.Unavailable
                    return
                }
            }
        // Keep review and new cards as separate phases: review must be explicitly completed first.
        val dueIds = plan.dueCardIds.distinct()
        val newIds = plan.newCardIds.filterNot { it in dueIds }.distinct()
        val cards = content.cards((dueIds + newIds).distinct())
        if (cards.isEmpty()) {
            _uiState.value = WordCardUiState.NoCards
            return
        }
        val started =
            Session(
                profileId = profileId,
                planId = plan.planId,
                wordBookId = plan.activeWordBookId,
                cards = cards,
                completed = completed.toMutableSet(),
                dueCardIds = dueIds.toSet(),
                reviewCardIds = dueIds.toSet(),
                newCardIds = newIds.toSet(),
                phase = if (dueIds.any { it !in completed }) SessionPhase.REVIEW else SessionPhase.NEW,
            )
        session = started
        emit(started)
    }

    private fun emit(current: Session, message: String? = null) {
        val phaseCards = current.cards.filter { card ->
            when (current.phase) {
                SessionPhase.REVIEW -> card.cardId in current.reviewCardIds
                SessionPhase.NEW -> card.cardId in current.newCardIds
            }
        }
        val index = phaseCards.indexOfFirst { it.cardId !in current.completed }
        _uiState.value = when {
            index >= 0 -> {
                val card = phaseCards[index]
                WordCardUiState.Ready(
                    card = card,
                    position = index + 1,
                    total = phaseCards.size,
                    completedCount = current.completed.count { it in phaseCards.map(WordCard::cardId) },
                    submitting = false,
                    message = message,
                    isReview = current.phase == SessionPhase.REVIEW,
                    taskCompletedCount = phaseCards.count { it.cardId in current.completed },
                    taskTotal = phaseCards.size,
                    meaningRevealed = card.cardId == revealedCardId,
                )
            }
            current.phase == SessionPhase.REVIEW && current.newCardIds.isNotEmpty() -> {
                WordCardUiState.ReviewCompleted(
                    reviewTotal = current.reviewCardIds.size,
                    reviewCompleted = current.reviewCardIds.count { it in current.completed },
                    newTotal = current.newCardIds.size,
                )
            }
            else -> {
                WordCardUiState.AllDone(
                    total = current.cards.size,
                    completedCount = current.completed.intersect(current.cards.map(WordCard::cardId).toSet()).size,
                    reviewTotal = current.reviewCardIds.size,
                    reviewCompleted = current.reviewCardIds.count { it in current.completed },
                    newTotal = current.newCardIds.size,
                    newCompleted = current.newCardIds.count { it in current.completed },
                )
            }
        }
    }

    private enum class SessionPhase { REVIEW, NEW }

    private class Session(
        val profileId: String,
        val planId: String,
        val wordBookId: String,
        val cards: List<WordCard>,
        val completed: MutableSet<String>,
        val dueCardIds: Set<String>,
        val reviewCardIds: Set<String>,
        val newCardIds: Set<String>,
        var phase: SessionPhase,
    )

    private companion object {
        const val FEEDBACK_NOT_SAVED: String = "反馈未保存成功，请重试"
    }
}
