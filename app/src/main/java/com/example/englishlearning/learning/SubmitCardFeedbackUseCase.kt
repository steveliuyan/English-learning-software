package com.example.englishlearning.learning

import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.ReviewScheduler
import com.example.englishlearning.learning.domain.ReviewState
import com.example.englishlearning.learning.domain.V1ReviewScheduler
import java.time.Instant

data class SubmitFeedbackCommand(
    val eventId: String,
    val profileId: String,
    val planId: String,
    val cardId: String,
    val wordBookId: String,
    val feedback: CardFeedback,
)

sealed interface SubmitFeedbackResult {
    /** First effective submission: the card counts as complete and the interval moved. */
    data class Recorded(val nextReviewAt: Instant) : SubmitFeedbackResult

    /**
     * The same [SubmitFeedbackCommand.eventId] was submitted before. Nothing is counted
     * again and the interval is not adjusted a second time; [nextReviewAt] is the value
     * stored by the original submission, so a replay is indistinguishable from it (AC1-04).
     */
    data class AlreadyRecorded(val nextReviewAt: Instant) : SubmitFeedbackResult

    data object StorageUnavailable : SubmitFeedbackResult
}

/**
 * Records exactly one effective feedback submission per card (spec F1-03).
 *
 * Idempotency is anchored on the event log primary key rather than on a read-then-write
 * check: the pre-read below is only a fast path, while the unique key still rejects a
 * concurrent replay. Events carry the algorithm/parameter versions and the scheduling
 * state before and after, and are never rewritten.
 */
class SubmitCardFeedbackUseCase(
    private val repository: LearningEventRepository,
    private val clock: ClockProvider,
    private val scheduler: ReviewScheduler = V1ReviewScheduler(),
) {
    suspend operator fun invoke(command: SubmitFeedbackCommand): SubmitFeedbackResult =
        when (val existing = repository.findEvent(command.eventId)) {
            is RepositoryResult.Failure -> SubmitFeedbackResult.StorageUnavailable
            is RepositoryResult.Success ->
                existing.value?.let { SubmitFeedbackResult.AlreadyRecorded(it.nextReviewAt) }
                    ?: recordFirstSubmission(command)
        }

    /**
     * No event with this id exists yet, so this submission is the one that moves the interval.
     * Anything that cannot be read is treated as a write that must not happen.
     */
    private suspend fun recordFirstSubmission(command: SubmitFeedbackCommand): SubmitFeedbackResult {
        val before =
            when (val state = repository.findCardState(command.cardId)) {
                is RepositoryResult.Success -> state.value
                is RepositoryResult.Failure -> return SubmitFeedbackResult.StorageUnavailable
            }

        val occurredAt = clock.instant()
        val rating = command.feedback.toReviewFeedback()
        val decision =
            scheduler.schedule(
                state = ReviewState(cardId = command.cardId, dueAt = before?.nextReviewAt),
                feedback = rating,
                now = occurredAt,
            )

        val event =
            LearningEvent(
                eventId = command.eventId,
                profileId = command.profileId,
                planId = command.planId,
                cardId = command.cardId,
                wordBookId = command.wordBookId,
                feedback = rating,
                occurredAt = occurredAt,
                algorithmVersion = V1ReviewScheduler.ALGORITHM_VERSION,
                paramsVersion = V1ReviewScheduler.PARAMS_VERSION,
                dueBefore = before?.nextReviewAt,
                nextReviewAt = decision.nextReviewAt,
            )
        val nextState =
            CardReviewState(
                cardId = command.cardId,
                wordBookId = command.wordBookId,
                lastFeedback = rating,
                lastReviewedAt = occurredAt,
                nextReviewAt = decision.nextReviewAt,
            )

        return when (val appended = repository.append(event, nextState)) {
            is AppendEventResult.Appended ->
                if (!appended.duplicate) {
                    SubmitFeedbackResult.Recorded(decision.nextReviewAt)
                } else {
                    duplicateResult(command.eventId)
                }

            AppendEventResult.StorageUnavailable -> SubmitFeedbackResult.StorageUnavailable
        }
    }

    /**
     * Loses the race in the "check then insert" window: report only what the winner stored.
     * A failed read is not permission to return a locally calculated, unpersisted schedule.
     */
    private suspend fun duplicateResult(eventId: String): SubmitFeedbackResult =
        when (val stored = repository.findEvent(eventId)) {
            is RepositoryResult.Success ->
                stored.value?.let { SubmitFeedbackResult.AlreadyRecorded(it.nextReviewAt) }
                    ?: SubmitFeedbackResult.StorageUnavailable

            is RepositoryResult.Failure -> SubmitFeedbackResult.StorageUnavailable
        }
}
