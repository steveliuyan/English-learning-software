package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.WordBookProgressMigrationAuditEntity

@Dao
internal interface InternalWordBookProgressMigrationAuditDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(audit: WordBookProgressMigrationAuditEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM word_book_progress_migration_audits WHERE profileId = :profileId AND sourceBookId = :sourceBookId AND targetBookId = :targetBookId AND sourceCardId = :sourceCardId AND targetCardId = :targetCardId)")
    suspend fun exists(profileId: String, sourceBookId: String, targetBookId: String, sourceCardId: String, targetCardId: String): Boolean

    @Query("DELETE FROM word_book_progress_migration_audits WHERE sourceBookId = :wordBookId OR targetBookId = :wordBookId")
    suspend fun deleteForWordBook(wordBookId: String)

    // ---- F4-01 决策 §9：迁移审计随进度一起清（审计对象已不存在） ----

    @Query("SELECT COUNT(*) FROM word_book_progress_migration_audits WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: String): Int

    @Query("DELETE FROM word_book_progress_migration_audits WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
