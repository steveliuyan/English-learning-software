package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.dao.LearningRecordEventRow
import com.example.englishlearning.core.storage.dao.ReviewStateRow
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.learning.domain.CardFeedback
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal fun cardFeedbackFromStorage(value: String): CardFeedback = when (value) {
    "Good" -> CardFeedback.Known
    "Hard" -> CardFeedback.Fuzzy
    "Again" -> CardFeedback.Unknown
    else -> throw IllegalArgumentException("unknown feedback: $value")
}

class RoomLearningRecordRepository(
    private val database: AppDatabase,
    private val cards: WordCardSource,
    private val clock: ClockProvider,
    private val ioDispatcher: CoroutineDispatcher,
) : LearningRecordRepository {
    override suspend fun today(profileId: String, planId: String): RepositoryResult<List<LearningRecord>> = runStorage {
        val rows = database.internalLearningEventDao().todayRecords(profileId, planId)
        deduplicateLearningRecords(resolve(rows))
    }

    override suspend fun history(profileId: String): RepositoryResult<List<LearningHistoryGroup>> = runStorage {
        val rows = database.internalLearningEventDao().historyRecords(profileId)
        groupLearningHistory(deduplicateLearningRecords(resolve(rows)))
    }

    override suspend fun allWords(wordBookId: String, now: Instant): RepositoryResult<List<WordBookRecord>> = runStorage {
        val ids = cards.cardIds(wordBookId)
        val resolved = cards.cards(ids).associateBy { it.cardId }
        val reviews = database.internalLearningEventDao().reviewStates(wordBookId).associateBy { it.cardId }
        ids.map { id ->
            val card = resolved[id]
            WordBookRecord(card, id, card?.lemma ?: id.substringAfterLast(':'), reviewStatus(reviews[id]?.nextReviewAtEpochMillis?.let(Instant::ofEpochMilli), now), card != null)
        }
    }

    private suspend fun resolve(rows: List<LearningRecordEventRow>): List<LearningRecord> {
        val resolved = cards.cards(rows.map { it.cardId }).associateBy { it.cardId }
        return rows.map { row ->
            val card = resolved[row.cardId]
            if (card == null) LearningRecord.missing(row.cardId, cardFeedbackFromStorage(row.feedback), Instant.ofEpochMilli(row.occurredAtEpochMillis), java.time.LocalDate.parse(row.localDate), row.planId)
            else LearningRecord(row.cardId, card.lemma, card, cardFeedbackFromStorage(row.feedback), Instant.ofEpochMilli(row.occurredAtEpochMillis), java.time.LocalDate.parse(row.localDate), planId = row.planId)
        }
    }

    private suspend fun <T> runStorage(block: suspend () -> T): RepositoryResult<T> = try {
        RepositoryResult.Success(withContext(ioDispatcher) { block() })
    } catch (cancellation: CancellationException) {
        if (currentCoroutineContext().isActive) RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable) else throw cancellation
    } catch (_: Exception) { RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable) }
}
