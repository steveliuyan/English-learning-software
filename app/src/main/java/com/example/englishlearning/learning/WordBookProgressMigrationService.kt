package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.WordCard

sealed interface ProgressMigrationResult {
    data class Applied(val count: Int) : ProgressMigrationResult
    data object StorageUnavailable : ProgressMigrationResult
}

class WordBookProgressMigrationService(
    private val cards: WordCardSource,
    private val events: LearningEventRepository,
) {
    suspend fun preview(profileId: String, sourceBookId: String, targetBookId: String): Result<ProgressMigrationPreview> {
        val sourceIds = cards.cardIds(sourceBookId)
        val targetIds = cards.cardIds(targetBookId)
        val sourceCards = cards.cards(sourceIds)
        val targetCards = cards.cards(targetIds)
        return when (val states = events.reviewedStates(sourceBookId)) {
            is RepositoryResult.Success -> Result.success(WordBookProgressMigration.preview(sourceCards, targetCards, states.value.associateBy(CardReviewState::cardId)))
            is RepositoryResult.Failure -> Result.failure(IllegalStateException("storage unavailable"))
        }
    }

    suspend fun migrate(
        profileId: String,
        sourceBookId: String,
        targetBookId: String,
        preview: ProgressMigrationPreview,
    ): ProgressMigrationResult =
        when (val result = events.migrateReviewStates(profileId, sourceBookId, targetBookId, preview.candidates)) {
            is RepositoryResult.Success -> ProgressMigrationResult.Applied(result.value)
            is RepositoryResult.Failure -> ProgressMigrationResult.StorageUnavailable
        }
}
