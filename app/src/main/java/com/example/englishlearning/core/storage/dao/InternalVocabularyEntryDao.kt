package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.VocabularyEntryEntity

@Dao
internal interface InternalVocabularyEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: VocabularyEntryEntity)

    @Query("DELETE FROM vocabulary_entries WHERE profileId = :profileId AND wordBookId = :wordBookId AND cardId = :cardId")
    suspend fun remove(profileId: String, wordBookId: String, cardId: String)

    @Query("SELECT * FROM vocabulary_entries WHERE profileId = :profileId ORDER BY addedAtEpochMillis DESC")
    suspend fun findAll(profileId: String): List<VocabularyEntryEntity>

    @Query("SELECT * FROM vocabulary_entries WHERE profileId = :profileId AND wordBookId = :wordBookId ORDER BY addedAtEpochMillis DESC")
    suspend fun findForWordBook(profileId: String, wordBookId: String): List<VocabularyEntryEntity>

    @Query("DELETE FROM vocabulary_entries WHERE wordBookId = :wordBookId")
    suspend fun deleteForWordBook(wordBookId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM vocabulary_entries WHERE profileId = :profileId AND wordBookId = :wordBookId AND cardId = :cardId)")
    suspend fun contains(profileId: String, wordBookId: String, cardId: String): Boolean
}
