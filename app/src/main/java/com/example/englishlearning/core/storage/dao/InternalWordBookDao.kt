package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.englishlearning.core.storage.entity.WordBookEntity

@Dao
internal interface InternalWordBookDao {
    @Query("SELECT * FROM word_books WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WordBookEntity?

    @Query("SELECT * FROM word_books ORDER BY id")
    suspend fun listAll(): List<WordBookEntity>

    @Upsert
    suspend fun upsert(wordBook: WordBookEntity)
}
