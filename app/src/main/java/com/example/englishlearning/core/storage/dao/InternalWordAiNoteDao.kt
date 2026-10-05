package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.WordAiNoteEntity

@Dao
internal interface InternalWordAiNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: WordAiNoteEntity)

    @Query(
        "SELECT * FROM word_ai_notes WHERE profileId = :profileId AND wordBookId = :wordBookId " +
            "AND cardId = :cardId AND lemma = :lemma ORDER BY createdAtEpochMillis DESC",
    )
    suspend fun findForCard(profileId: String, wordBookId: String, cardId: String, lemma: String): List<WordAiNoteEntity>

    @Query("DELETE FROM word_ai_notes WHERE wordBookId = :wordBookId")
    suspend fun deleteForWordBook(wordBookId: String)
}
