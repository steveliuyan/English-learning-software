package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.UnlockPolicy
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Compatibility [LearningEventRepository] backing the two-argument secondary constructor.
 *
 * It models a plan that has recorded no learning events yet: every completion read returns an
 * empty success, so [load] still emits the classic [TodayPlanUiState.Ready] (locked, zero
 * progress) the older two-argument callers rely on. This is an *inert* implementation, not an
 * "unavailable" one — returning a storage failure would route a Ready plan to
 * [TodayPlanUiState.Unavailable] and break the legacy UI semantics.
 *
 * The event repository is only consulted inside the `Ready` branch of [load]; any non-Ready
 * result (Loading, MissingSetup, NotFound, StorageUnavailable) never reaches it.
 */
private object NoLearningEventRepository : LearningEventRepository {
    override suspend fun append(event: LearningEvent, nextState: CardReviewState) =
        AppendEventResult.Appended(duplicate = false)

    override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)
    override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)
    override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
    override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(emptyList<String>())
    override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())
    override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
}

sealed interface TodayPlanUiState {
    data object Loading : TodayPlanUiState
    data class Ready(
        val wordBookName: String,
        val localDateLabel: String,
        val newTarget: Int,
        val dueTarget: Int,
        val totalTasks: Int,
        val newDone: Int = 0,
        val dueDone: Int = 0,
        val isUnlocked: Boolean = false,
        val unlockReason: String = "等待完成今日计划",
    ) : TodayPlanUiState
    data object MissingSetup : TodayPlanUiState
    data object Unavailable : TodayPlanUiState
}

fun interface TodayPlanUseCaseContract { suspend operator fun invoke(profileId: String): TodayPlanResult }

@HiltViewModel
class TodayPlanViewModel @Inject constructor(
    private val useCase: TodayPlanUseCaseContract,
    private val profiles: LearningProfileRepository,
    private val events: LearningEventRepository,
) : ViewModel() {
    constructor(useCase: TodayPlanUseCaseContract, profiles: LearningProfileRepository) : this(useCase, profiles, NoLearningEventRepository)
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
                    when (val completed = events.completedCardIds(plan.planId)) {
                        is RepositoryResult.Success -> {
                            val mode = UnlockPolicy.modeForRuleVersion(plan.ruleVersion)
                            val progress = UnlockPolicy.progress(plan, completed.value)
                            val unlocked = UnlockPolicy.isUnlocked(plan, completed.value, mode)
                            val reason = if (unlocked) "今日计划已完成" else UnlockPolicy.lockedReason(mode)
                            _uiState.value = TodayPlanUiState.Ready(
                                name, plan.localDate.toString(), plan.newTarget, plan.dueTarget,
                                plan.newTarget + plan.dueTarget, progress.newDone, progress.dueDone,
                                unlocked, reason,
                            )
                        }
                        is RepositoryResult.Failure -> _uiState.value = TodayPlanUiState.Unavailable
                    }
                }
                TodayPlanResult.MissingLearningSetup -> _uiState.value = TodayPlanUiState.MissingSetup
                TodayPlanResult.NotFound, TodayPlanResult.StorageUnavailable -> _uiState.value = TodayPlanUiState.Unavailable
            }
        }
    }
}
