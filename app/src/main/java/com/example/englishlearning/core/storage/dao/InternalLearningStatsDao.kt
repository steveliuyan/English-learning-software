package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Query

internal data class DateCountRow(
    val localDate: String,
    val count: Int,
)

@Dao
internal interface InternalLearningStatsDao {
    @Query("SELECT COUNT(DISTINCT cardId) FROM learning_events WHERE profileId = :profileId AND occurredAtEpochMillis >= :fromEpochMillis AND occurredAtEpochMillis < :toExclusiveEpochMillis")
    suspend fun countReviewedCards(profileId: String, fromEpochMillis: Long, toExclusiveEpochMillis: Long): Int

    @Query("SELECT localDate, COUNT(*) AS count FROM reading_completions WHERE profileId = :profileId AND localDate >= :fromDate AND localDate <= :toDate GROUP BY localDate")
    suspend fun countReadings(profileId: String, fromDate: String, toDate: String): List<DateCountRow>
}
