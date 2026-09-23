package com.example.englishlearning.learning.worksheet

import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.ReviewFeedback

class BuildWorksheetContentUseCase(
    private val plans: TodayPlanRepository,
    private val events: LearningEventRepository,
    private val content: WordCardSource,
) {
    suspend operator fun invoke(
        profileId: String,
        range: WorksheetRange,
    ): Result<WorksheetSource> {
        val plan = when (val result = plans.findLatest(profileId)) {
            is TodayPlanResult.Ready -> result.plan
            TodayPlanResult.NotFound, TodayPlanResult.MissingLearningSetup -> return failure(WorksheetContentFailure.NoPlan)
            TodayPlanResult.StorageUnavailable -> return failure(WorksheetContentFailure.StorageUnavailable)
        }
        val completed = when (val result = events.completedCardIds(plan.planId)) {
            is RepositoryResult.Success -> result.value.toSet()
            is RepositoryResult.Failure -> return failure(WorksheetContentFailure.StorageUnavailable)
        }
        val feedback = if (range == WorksheetRange.DIFFICULT_TODAY) {
            when (val result = events.completedCardFeedback(plan.planId)) {
                is RepositoryResult.Success -> result.value.associate { it.cardId to it.feedback }
                is RepositoryResult.Failure -> return failure(WorksheetContentFailure.StorageUnavailable)
            }
        } else {
            emptyMap()
        }
        val selectedIds = selectIds(plan, completed, feedback, range)
        if (selectedIds.isEmpty()) return failure(WorksheetContentFailure.NoSelectedWords)

        val cards = content.cards(selectedIds)
        val cardsById = cards.associateBy { it.cardId }
        val items = selectedIds.mapNotNull { cardId ->
            cardsById[cardId]?.let { card ->
                WorksheetItem(
                    cardId = card.cardId,
                    lemma = card.lemma,
                    ipa = card.ipa,
                    partOfSpeech = card.partOfSpeech,
                    meaningZh = card.meaningZh,
                    example = card.example,
                )
            }
        }
        if (items.isEmpty()) return failure(WorksheetContentFailure.NoSelectedWords)
        return Result.success(
            WorksheetSource(
                localDate = plan.localDate,
                wordBookId = plan.activeWordBookId,
                items = items,
                missingCardCount = selectedIds.size - items.size,
            ),
        )
    }

    private fun selectIds(
        plan: TodayPlan,
        completed: Set<String>,
        feedback: Map<String, ReviewFeedback>,
        range: WorksheetRange,
    ): List<String> {
        val due = plan.dueCardIds.distinct()
        val new = plan.newCardIds.distinct()
        return when (range) {
            WorksheetRange.ALL_PLAN_ITEMS -> (due + new).distinct()
            WorksheetRange.COMPLETED_TODAY -> (due + new).distinct().filter(completed::contains)
            WorksheetRange.COMPLETED_NEW -> new.filter(completed::contains)
            WorksheetRange.COMPLETED_REVIEW -> due.filter(completed::contains)
            WorksheetRange.DIFFICULT_TODAY -> (due + new).distinct().filter { cardId ->
                cardId in completed && feedback[cardId] in setOf(ReviewFeedback.Again, ReviewFeedback.Hard)
            }
        }
    }

    private fun failure(reason: WorksheetContentFailure): Result<Nothing> =
        Result.failure(WorksheetContentException(reason))
}

class WorksheetContentException(
    val reason: WorksheetContentFailure,
) : RuntimeException(null, null, false, false)
