package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface TodayPlanUiState {
    data object Loading : TodayPlanUiState
    data class Ready(val wordBookName: String, val localDateLabel: String, val newTarget: Int, val dueTarget: Int, val totalTasks: Int) : TodayPlanUiState
    data object MissingSetup : TodayPlanUiState
    data object Unavailable : TodayPlanUiState
}

fun interface TodayPlanUseCaseContract { suspend operator fun invoke(profileId: String): TodayPlanResult }

@HiltViewModel
class TodayPlanViewModel @Inject constructor(
    private val useCase: TodayPlanUseCaseContract,
    private val profiles: LearningProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TodayPlanUiState>(TodayPlanUiState.Loading)
    val uiState: StateFlow<TodayPlanUiState> = _uiState

    fun load(profileId: String) {
        viewModelScope.launch {
            _uiState.value = TodayPlanUiState.Loading
            when (val result = useCase(profileId)) {
                is TodayPlanResult.Ready -> {
                    val plan = result.plan
                    val name = when (val books = profiles.findWordBook(plan.activeWordBookId)) {
                        is RepositoryResult.Success -> books.value?.displayName ?: plan.activeWordBookId
                        is RepositoryResult.Failure -> plan.activeWordBookId
                    }
                    _uiState.value = TodayPlanUiState.Ready(name, plan.localDate.toString(), plan.newTarget, plan.dueTarget, plan.newTarget + plan.dueTarget)
                }
                TodayPlanResult.MissingLearningSetup -> _uiState.value = TodayPlanUiState.MissingSetup
                TodayPlanResult.NotFound, TodayPlanResult.StorageUnavailable -> _uiState.value = TodayPlanUiState.Unavailable
            }
        }
    }
}
