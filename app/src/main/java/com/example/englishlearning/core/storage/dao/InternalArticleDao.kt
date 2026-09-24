package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.englishlearning.core.storage.entity.ArticleEntity

@Dao
internal interface InternalArticleDao {
    @Query("SELECT * FROM articles WHERE profileId = :profileId AND localDate = :localDate AND activeWordBookId = :activeWordBookId AND articleType = :articleType AND lengthTier = :lengthTier ORDER BY version DESC LIMIT 1")
    suspend fun findLatest(
        profileId: String,
        localDate: String,
        activeWordBookId: String,
        articleType: String,
        lengthTier: String,
    ): ArticleEntity?

    @Query("SELECT MAX(version) FROM articles WHERE profileId = :profileId AND localDate = :localDate AND activeWordBookId = :activeWordBookId AND articleType = :articleType AND lengthTier = :lengthTier")
    suspend fun maxVersion(
        profileId: String,
        localDate: String,
        activeWordBookId: String,
        articleType: String,
        lengthTier: String,
    ): Int?

    @Query("SELECT * FROM articles WHERE profileId = :profileId ORDER BY localDate DESC, generatedAtEpochMillis DESC, version DESC")
    suspend fun findHistory(profileId: String): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE sourceUrl = :url ORDER BY generatedAtEpochMillis DESC LIMIT 1")
    suspend fun findBySourceUrl(url: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(article: ArticleEntity)
}
