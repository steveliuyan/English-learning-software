package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.ReadingCompletionEntity

@Dao
internal interface InternalReadingCompletionDao {
    /** 返回新行 id；-1 表示该文章已完成过（主键冲突），调用方按幂等重复处理。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: ReadingCompletionEntity): Long

    @Query("SELECT COUNT(*) FROM reading_completions WHERE profileId = :profileId AND localDate = :localDate")
    suspend fun countForDate(profileId: String, localDate: String): Int

    // ---- F4-01 决策 §9：阅读完成记录按档案计入「删除全部学习数据」 ----

    @Query("SELECT COUNT(*) FROM reading_completions WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: String): Int

    @Query("DELETE FROM reading_completions WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
