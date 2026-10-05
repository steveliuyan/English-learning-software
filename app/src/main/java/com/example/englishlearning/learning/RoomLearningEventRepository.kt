package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.CardReviewStateEntity
import com.example.englishlearning.core.storage.entity.LearningEventEntity
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.ReviewFeedback
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import androidx.room.withTransaction
import kotlinx.coroutines.withContext

class RoomLearningEventRepository(
    private val database: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) : LearningEventRepository {
    override suspend fun append(event: LearningEvent, nextState: CardReviewState): AppendEventResult {
        return try {
            val rowId =
                withContext(ioDispatcher) {
                    database.internalLearningEventDao().appendEvent(event.toEntity(), nextState.toEntity())
                }
            AppendEventResult.Appended(duplicate = rowId == DUPLICATE_ROW_ID)
        } catch (cancellation: CancellationException) {
            if (currentCoroutineContext().isActive) AppendEventResult.StorageUnavailable else throw cancellation
        } catch (_: Exception) {
            AppendEventResult.StorageUnavailable
        }
    }

    override suspend fun findEvent(eventId: String): RepositoryResult<LearningEvent?> =
        runStorage { database.internalLearningEventDao().findEvent(eventId)?.toDomain() }

    override suspend fun findCardState(cardId: String): RepositoryResult<CardReviewState?> =
        runStorage { database.internalLearningEventDao().findCardState(cardId)?.toDomain() }

    override suspend fun countEventsForCard(planId: String, cardId: String): RepositoryResult<Int> =
        runStorage { database.internalLearningEventDao().countEventsForCard(planId, cardId) }

    override suspend fun completedCardIds(planId: String): RepositoryResult<List<String>> =
        runStorage { database.internalLearningEventDao().completedCardIds(planId) }

    override suspend fun completedCardFeedback(planId: String): RepositoryResult<List<PlanCardFeedback>> =
        runStorage {
            database.internalLearningEventDao().completedCardFeedback(planId).map { row ->
                PlanCardFeedback(row.cardId, row.feedback.toReviewFeedback())
            }
        }

    override suspend fun reviewedCardIds(wordBookId: String): RepositoryResult<List<String>> =
        runStorage { database.internalLearningEventDao().reviewedCardIds(wordBookId) }

    override suspend fun dueCardIds(wordBookId: String, now: Instant): RepositoryResult<List<String>> =
        runStorage { database.internalLearningEventDao().dueCardIds(wordBookId, now.toEpochMilli()) }

    override suspend fun reviewedStates(wordBookId: String): RepositoryResult<List<CardReviewState>> =
        runStorage { database.internalLearningEventDao().fullReviewStates(wordBookId).map { it.toDomain() } }

    override suspend fun upsertReviewState(state: CardReviewState): RepositoryResult<Unit> =
        runStorage { database.internalLearningEventDao().upsertCardState(state.toEntity()) }

    override suspend fun migrateReviewStates(
        profileId: String,
        sourceBookId: String,
        targetBookId: String,
        candidates: List<ProgressMigrationCandidate>,
    ): RepositoryResult<Int> = runStorage {
        database.withTransaction {
            var count = 0
            candidates.forEach { candidate ->
                val audit = com.example.englishlearning.core.storage.entity.WordBookProgressMigrationAuditEntity(
                    profileId = profileId,
                    sourceBookId = sourceBookId,
                    targetBookId = targetBookId,
                    sourceCardId = candidate.source.cardId,
                    targetCardId = candidate.target.cardId,
                    migratedAtEpochMillis = candidate.sourceState.lastReviewedAt.toEpochMilli(),
                )
                val auditId = database.internalWordBookProgressMigrationAuditDao().insertIfAbsent(audit)
                if (auditId == -1L) return@forEach
                val existing = database.internalLearningEventDao().findCardStateByCardId(candidate.target.cardId)
                if (existing == null || existing.lastReviewedAtEpochMillis < candidate.sourceState.lastReviewedAt.toEpochMilli()) {
                    database.internalLearningEventDao().upsertCardState(
                        candidate.sourceState.copy(cardId = candidate.target.cardId, wordBookId = targetBookId).toEntity(),
                    )
                    count++
                }
            }
            count
        }
    }

    private suspend fun <T> runStorage(block: suspend () -> T): RepositoryResult<T> {
        return try {
            RepositoryResult.Success(withContext(ioDispatcher) { block() })
        } catch (cancellation: CancellationException) {
            if (currentCoroutineContext().isActive) {
                RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            } else {
                throw cancellation
            }
        } catch (_: Exception) {
            RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
        }
    }

    private companion object {
        const val DUPLICATE_ROW_ID = -1L
    }
}

private fun LearningEvent.toEntity() =
    LearningEventEntity(
        eventId = eventId,
        profileId = profileId,
        planId = planId,
        cardId = cardId,
        wordBookId = wordBookId,
        feedback = feedback.name,
        occurredAtEpochMillis = occurredAt.toEpochMilli(),
        algorithmVersion = algorithmVersion,
        paramsVersion = paramsVersion,
        dueBeforeEpochMillis = dueBefore?.toEpochMilli(),
        nextReviewAtEpochMillis = nextReviewAt.toEpochMilli(),
    )

private fun CardReviewState.toEntity() =
    CardReviewStateEntity(
        cardId = cardId,
        wordBookId = wordBookId,
        lastFeedback = lastFeedback.name,
        lastReviewedAtEpochMillis = lastReviewedAt.toEpochMilli(),
        nextReviewAtEpochMillis = nextReviewAt.toEpochMilli(),
    )

private fun LearningEventEntity.toDomain() =
    LearningEvent(
        eventId = eventId,
        profileId = profileId,
        planId = planId,
        cardId = cardId,
        wordBookId = wordBookId,
        feedback = feedback.toReviewFeedback(),
        occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
        algorithmVersion = algorithmVersion,
        paramsVersion = paramsVersion,
        dueBefore = dueBeforeEpochMillis?.let(Instant::ofEpochMilli),
        nextReviewAt = Instant.ofEpochMilli(nextReviewAtEpochMillis),
    )

private fun CardReviewStateEntity.toDomain() =
    CardReviewState(
        cardId = cardId,
        wordBookId = wordBookId,
        lastFeedback = lastFeedback.toReviewFeedback(),
        lastReviewedAt = Instant.ofEpochMilli(lastReviewedAtEpochMillis),
        nextReviewAt = Instant.ofEpochMilli(nextReviewAtEpochMillis),
    )

/** Storage only ever holds the fixed V1 feedback names; anything else is corruption. */
private fun String.toReviewFeedback(): ReviewFeedback =
    ReviewFeedback.entries.firstOrNull { it.name == this }
        ?: throw IllegalArgumentException("unknown feedback: $this")
