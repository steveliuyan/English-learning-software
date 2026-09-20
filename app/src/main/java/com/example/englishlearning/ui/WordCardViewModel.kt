package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.SubmitFeedbackCommand
import com.example.englishlearning.learning.SubmitFeedbackResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.WordCard
import dagger.hilt.android.lifecycle.HiltViewModel
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
    ) : WordCardUiState

    /** Every card of the session has a recorded feedback; unlocking is F1-04's concern. */
    data class AllDone(val total: Int, val completedCount: Int) : WordCardUiState

    data object NoCards : WordCardUiState

    data object MissingSetup : WordCardUiState

    data object Unavailable : WordCardUiState
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
) : ViewModel() {
    private val _uiState = MutableStateFlow<WordCardUiState>(WordCardUiState.Loading)
    val uiState: StateFlow<WordCardUiState> = _uiState

    private var session: Session? = null
    private var pendingEventId: String? = null

    fun load(profileId: String) {
        session = null
        pendingEventId = null
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

    fun submit(feedback: CardFeedback) {
        val current = session ?: return
        // Only a visible, not-yet-completed card accepts a rating; a double tap or a card that
        // is already recorded must be a no-op rather than a second event.
        val ready =
            (_uiState.value as? WordCardUiState.Ready)
                ?.takeIf { !it.submitting && it.card.cardId !in current.completed }
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
            when (submitFeedback(command)) {
                is SubmitFeedbackResult.Recorded,
                is SubmitFeedbackResult.AlreadyRecorded,
                -> {
                    pendingEventId = null
                    current.completed += card.cardId
                    emit(current)
                }
                SubmitFeedbackResult.StorageUnavailable ->
                    emit(current, message = FEEDBACK_NOT_SAVED)
            }
        }
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
        // Due cards first: overdue reviews must not be crowded out by new ones.
        val cards = content.cards((plan.dueCardIds + plan.newCardIds).distinct())
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
            )
        session = started
        emit(started)
    }

    private fun emit(current: Session, message: String? = null) {
        val index = current.cards.indexOfFirst { it.cardId !in current.completed }
        _uiState.value =
            if (index < 0) {
                WordCardUiState.AllDone(total = current.cards.size, completedCount = current.completed.size)
            } else {
                WordCardUiState.Ready(
                    card = current.cards[index],
                    position = index + 1,
                    total = current.cards.size,
                    completedCount = current.completed.size,
                    submitting = false,
                    message = message,
                )
            }
    }

    private class Session(
        val profileId: String,
        val planId: String,
        val wordBookId: String,
        val cards: List<WordCard>,
        val completed: MutableSet<String>,
    )

    private companion object {
        const val FEEDBACK_NOT_SAVED: String = "反馈未保存成功，请重试"
    }
}
