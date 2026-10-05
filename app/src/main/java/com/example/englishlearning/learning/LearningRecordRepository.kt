package com.example.englishlearning.learning

import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import java.time.LocalDate

interface LearningRecordRepository {
    suspend fun today(profileId: String, planId: String): RepositoryResult<List<LearningRecord>>
    suspend fun history(profileId: String): RepositoryResult<List<LearningHistoryGroup>>
    suspend fun allWords(wordBookId: String, now: Instant): RepositoryResult<List<WordBookRecord>>
}

data class LearningRecord(
    val cardId: String,
    val displayLemma: String,
    val wordCard: WordCard?,
    val feedback: CardFeedback,
    val occurredAt: Instant,
    val localDate: LocalDate,
    val canOpenDetail: Boolean = wordCard != null,
    val planId: String,
) {
    companion object {
        fun missing(cardId: String, feedback: CardFeedback, occurredAt: Instant, localDate: LocalDate, planId: String) =
            LearningRecord(cardId, cardId.substringAfterLast(':'), null, feedback, occurredAt, localDate, false, planId)
    }
}

data class LearningHistoryGroup(
    val localDate: LocalDate,
    val records: List<LearningRecord>,
) {
    val count: Int get() = records.size
}

enum class WordBookRecordStatus { Unlearned, Learning, Due }

data class WordBookRecord(
    val card: WordCard?,
    val cardId: String,
    val displayLemma: String,
    val status: WordBookRecordStatus,
    val canOpenDetail: Boolean = card != null,
)

fun reviewStatus(nextReviewAt: Instant?, now: Instant): WordBookRecordStatus = when {
    nextReviewAt == null -> WordBookRecordStatus.Unlearned
    nextReviewAt <= now -> WordBookRecordStatus.Due
    else -> WordBookRecordStatus.Learning
}

fun deduplicateLearningRecords(records: List<LearningRecord>): List<LearningRecord> = records
    .groupBy { it.planId to it.cardId }
    .values
    .map { it.minBy { record -> record.occurredAt } }

fun groupLearningHistory(records: List<LearningRecord>): List<LearningHistoryGroup> = records
    .groupBy { it.localDate }
    .toList()
    .sortedByDescending { it.first }
    .map { (date, rows) -> LearningHistoryGroup(date, rows.sortedBy { it.occurredAt }) }
