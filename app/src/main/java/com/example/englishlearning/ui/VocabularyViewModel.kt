package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.SubmitFeedbackCommand
import com.example.englishlearning.learning.SubmitFeedbackResult
import com.example.englishlearning.learning.VocabularyEntry
import com.example.englishlearning.learning.VocabularyRepository
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.WordCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VocabularyItem(val entry: VocabularyEntry, val card: WordCard?)

data class VocabularyMembership(val profileId: String, val wordBookId: String, val cardId: String, val present: Boolean)
sealed interface VocabularyUiState {
    data object Loading : VocabularyUiState
    data class Ready(val items: List<VocabularyItem>) : VocabularyUiState
    data object Unavailable : VocabularyUiState
}

sealed interface VocabularyMasteryState {
    data object Idle : VocabularyMasteryState
    data object Submitting : VocabularyMasteryState
    data object Success : VocabularyMasteryState
    data object Failure : VocabularyMasteryState
}

@HiltViewModel
class VocabularyViewModel @Inject constructor(
    private val repository: VocabularyRepository,
    private val cards: WordCardSource,
    private val submitFeedback: SubmitCardFeedbackUseCase,
    private val eventIds: EventIdFactory,
) : ViewModel() {
    private val _state = MutableStateFlow<VocabularyUiState>(VocabularyUiState.Loading)
    val state: StateFlow<VocabularyUiState> = _state
    private val _membership = MutableStateFlow<VocabularyMembership?>(null)
    val membership: StateFlow<VocabularyMembership?> = _membership
    private val _mastery = MutableStateFlow<VocabularyMasteryState>(VocabularyMasteryState.Idle)
    val mastery: StateFlow<VocabularyMasteryState> = _mastery

    fun load(profileId: String, wordBookId: String? = null) {
        _state.value = VocabularyUiState.Loading
        viewModelScope.launch {
            when (val result = repository.list(profileId, wordBookId)) {
                is RepositoryResult.Failure -> _state.value = VocabularyUiState.Unavailable
                is RepositoryResult.Success -> {
                    val resolved = cards.cards(result.value.map { it.cardId }).associateBy { it.cardId }
                    _state.value = VocabularyUiState.Ready(result.value.map { VocabularyItem(it, resolved[it.cardId]) })
                }
            }
        }
    }

    fun clearMasteryMessage() {
        _mastery.value = VocabularyMasteryState.Idle
    }

    fun markMastered(profileId: String, item: VocabularyItem) {
        val card = item.card ?: return
        if (_mastery.value == VocabularyMasteryState.Submitting) return
        _mastery.value = VocabularyMasteryState.Submitting
        viewModelScope.launch {
            when (submitFeedback(
                SubmitFeedbackCommand(
                    eventId = eventIds.newId(),
                    profileId = profileId,
                    planId = "vocabulary-mastered:$profileId",
                    cardId = card.cardId,
                    wordBookId = card.wordBookId,
                    feedback = CardFeedback.Known,
                ),
            )) {
                is SubmitFeedbackResult.Recorded,
                is SubmitFeedbackResult.AlreadyRecorded,
                -> {
                    repository.remove(profileId, card.wordBookId, card.cardId)
                    _mastery.value = VocabularyMasteryState.Success
                }
                SubmitFeedbackResult.StorageUnavailable -> {
                    _mastery.value = VocabularyMasteryState.Failure
                    return@launch
                }
            }
            load(profileId)
        }
    }

    fun loadMembership(profileId: String, card: WordCard) {
        viewModelScope.launch {
            val present = repository.contains(profileId, card.wordBookId, card.cardId)
            _membership.value = VocabularyMembership(profileId, card.wordBookId, card.cardId, present)
        }
    }

    fun toggle(profileId: String, card: WordCard, now: java.time.Instant, feedback: String = "Manual") {
        viewModelScope.launch {
            if (repository.contains(profileId, card.wordBookId, card.cardId)) {
                repository.remove(profileId, card.wordBookId, card.cardId)
                _membership.value = VocabularyMembership(profileId, card.wordBookId, card.cardId, false)
            } else {
                repository.add(profileId, card.wordBookId, card.cardId, feedback, now)
                _membership.value = VocabularyMembership(profileId, card.wordBookId, card.cardId, true)
            }
            load(profileId)
        }
    }
}
