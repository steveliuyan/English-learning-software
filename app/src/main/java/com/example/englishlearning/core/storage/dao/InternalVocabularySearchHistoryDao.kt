package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.englishlearning.core.storage.entity.VocabularySearchHistoryEntity

@Dao
internal interface InternalVocabularySearchHistoryDao {
    @Query("SELECT * FROM vocabulary_search_history WHERE profileId = :profileId ORDER BY lastSearchedAtEpochMillis DESC LIMIT :limit")
    suspend fun list(profileId: String, limit: Int): List<VocabularySearchHistoryEntity>

    @Query("SELECT * FROM vocabulary_search_history WHERE profileId = :profileId AND normalizedQuery = :normalizedQuery LIMIT 1")
    suspend fun find(profileId: String, normalizedQuery: String): VocabularySearchHistoryEntity?

    @Upsert
    suspend fun upsert(entity: VocabularySearchHistoryEntity)

    @Query("DELETE FROM vocabulary_search_history WHERE profileId = :profileId")
    suspend fun clear(profileId: String)
}
