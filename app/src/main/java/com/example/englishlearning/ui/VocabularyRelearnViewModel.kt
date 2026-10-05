package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.EventIdFactory
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.SubmitFeedbackCommand
import com.example.englishlearning.learning.SubmitFeedbackResult
import com.example.englishlearning.learning.VocabularyRepository
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.WordCard
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface VocabularyRelearnUiState {
    data object Idle : VocabularyRelearnUiState
    data object Loading : VocabularyRelearnUiState
    data class Ready(val card: WordCard, val position: Int, val total: Int, val submitting: Boolean = false, val message: String? = null) : VocabularyRelearnUiState
    data class Done(val total: Int) : VocabularyRelearnUiState
    data object Unavailable : VocabularyRelearnUiState
}

@HiltViewModel
class VocabularyRelearnViewModel @Inject constructor(
    private val vocabulary: VocabularyRepository,
    private val cards: WordCardSource,
    private val submitFeedback: SubmitCardFeedbackUseCase,
    private val eventIds: EventIdFactory,
) : ViewModel() {
    private val _state = MutableStateFlow<VocabularyRelearnUiState>(VocabularyRelearnUiState.Idle)
    val state: StateFlow<VocabularyRelearnUiState> = _state
    private var profileId: String? = null
    private var queue: List<WordCard> = emptyList()
    private var index = 0

    fun loadCard(profileId: String, card: WordCard) {
        this.profileId = profileId
        queue = listOf(card)
        index = 0
        _state.value = VocabularyRelearnUiState.Ready(card, 1, 1)
    }

    fun load(profileId: String, activeWordBookId: String? = null) {
        this.profileId = profileId
        _state.value = VocabularyRelearnUiState.Loading
        viewModelScope.launch {
            when (val result = vocabulary.list(profileId, activeWordBookId)) {
                is com.example.englishlearning.learning.RepositoryResult.Failure -> _state.value = VocabularyRelearnUiState.Unavailable
                is com.example.englishlearning.learning.RepositoryResult.Success -> {
                    queue = cards.cards(result.value.map { it.cardId })
                    index = 0
                    if (queue.isEmpty()) _state.value = VocabularyRelearnUiState.Done(0) else emit()
                }
            }
        }
    }

    fun submit(feedback: CardFeedback) {
        val current = _state.value as? VocabularyRelearnUiState.Ready ?: return
        if (current.submitting) return
        val profile = profileId ?: return
        _state.value = current.copy(submitting = true, message = null)
        viewModelScope.launch {
            val card = current.card
            val result = submitFeedback(
                SubmitFeedbackCommand(
                    eventId = eventIds.newId(),
                    profileId = profile,
                    planId = "relearn:$profile",
                    cardId = card.cardId,
                    wordBookId = card.wordBookId,
                    feedback = feedback,
                ),
            )
            when (result) {
                is SubmitFeedbackResult.Recorded, is SubmitFeedbackResult.AlreadyRecorded -> {
                    // 重新学习中的“认识”表示用户已确认掌握：从生词本移除；
                    // 其它反馈保留，后续仍可从生词本再次复习。
                    if (feedback == CardFeedback.Known) {
                        vocabulary.remove(profile, card.wordBookId, card.cardId)
                    }
                    index++
                    if (index >= queue.size) _state.value = VocabularyRelearnUiState.Done(queue.size) else emit()
                }
                SubmitFeedbackResult.StorageUnavailable -> _state.value = current.copy(submitting = false, message = "保存失败，请重试")
            }
        }
    }

    private fun emit() {
        _state.value = VocabularyRelearnUiState.Ready(queue[index], index + 1, queue.size)
    }
}
