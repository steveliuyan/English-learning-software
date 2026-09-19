package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.SetupResult
import com.example.englishlearning.learning.WordBook
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LearningSetupEffect {
    data object Saved : LearningSetupEffect
}

data class LearningSetupUiState(
    val wordBooks: List<WordBook> = emptyList(),
    val selectedWordBookId: String? = null,
    val dailyNewTarget: Int = DEFAULT_DAILY_TARGET,
    val savedWordBookName: String? = null,
    val message: String? = null,
) {
    companion object {
        const val DEFAULT_DAILY_TARGET = 10
        const val MIN_DAILY_TARGET = 1
        const val MAX_DAILY_TARGET = 50
    }
}

@HiltViewModel
class LearningSetupViewModel @Inject constructor(
    private val repository: LearningProfileRepository,
    private val seedWordBooks: SeedWordBooksUseCase,
    private val selectWordBook: SelectWordBookAndSetDailyTargetUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LearningSetupUiState())
    val uiState: StateFlow<LearningSetupUiState> = _uiState
    private val _effects = MutableSharedFlow<LearningSetupEffect>()
    val effects: SharedFlow<LearningSetupEffect> = _effects

    fun load(profileId: String) {
        viewModelScope.launch {
            seedWordBooks()
            val wordBooksResult = repository.listWordBooks()
            val profileResult = repository.current(profileId)
            if (wordBooksResult is RepositoryResult.Failure || profileResult is RepositoryResult.Failure) {
                _uiState.value = LearningSetupUiState(message = "暂时无法加载学习设置")
                return@launch
            }
            val wordBooks = (wordBooksResult as RepositoryResult.Success).value
            val profile = (profileResult as RepositoryResult.Success).value
            _uiState.value =
                LearningSetupUiState(
                    wordBooks = wordBooks,
                    selectedWordBookId = profile?.activeWordBookId ?: wordBooks.firstOrNull()?.id,
                    dailyNewTarget = profile?.dailyNewTarget ?: LearningSetupUiState.DEFAULT_DAILY_TARGET,
                    savedWordBookName = wordBooks.find { it.id == profile?.activeWordBookId }?.displayName,
                )
        }
    }

    fun selectWordBook(wordBookId: String) {
        _uiState.value = _uiState.value.copy(selectedWordBookId = wordBookId, savedWordBookName = null)
    }

    fun updateDailyNewTarget(target: Int) {
        _uiState.value = _uiState.value.copy(
            dailyNewTarget = target.coerceIn(
                LearningSetupUiState.MIN_DAILY_TARGET,
                LearningSetupUiState.MAX_DAILY_TARGET,
            ),
            savedWordBookName = null,
        )
    }

    fun save(profileId: String) {
        val state = _uiState.value
        val wordBookId = state.selectedWordBookId ?: return
        viewModelScope.launch {
            when (selectWordBook(profileId, wordBookId, state.dailyNewTarget)) {
                SetupResult.Saved -> {
                    _uiState.value = state.copy(
                        savedWordBookName = state.wordBooks.find { it.id == wordBookId }?.displayName,
                        message = null,
                    )
                    _effects.emit(LearningSetupEffect.Saved)
                }
                SetupResult.InvalidDailyTarget -> _uiState.value = state.copy(message = "每日新词目标至少为 1")
                SetupResult.UnknownWordBook -> _uiState.value = state.copy(message = "词书不可用")
                SetupResult.StorageUnavailable -> _uiState.value = state.copy(message = "暂时无法保存学习设置")
            }
        }
    }
}
