package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.englishlearning.core.storage.entity.WordBookEntity

@Dao
internal interface InternalWordBookDao {
    /** 点查读全量（含软删行）：历史详展与收藏仍要把已删词书的册名归属查出来。 */
    @Query("SELECT * FROM word_books WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WordBookEntity?

    /** 列表只出未删行（F4-01 决策⑥：软删即对用户不可见，但行保留）。 */
    @Query("SELECT * FROM word_books WHERE deleted = 0 ORDER BY id")
    suspend fun listAll(): List<WordBookEntity>

    @Upsert
    suspend fun upsert(wordBook: WordBookEntity)

    @Query("UPDATE word_books SET deleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: String)
}
