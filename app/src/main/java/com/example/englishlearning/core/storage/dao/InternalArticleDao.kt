package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.englishlearning.core.storage.entity.ArticleEntity

/** F4-01 决策 §8：事务删除的凭据——被连带清掉的完成记录数与同组剩余版本数。 */
internal data class VersionDeleteReceipt(
    val removedCompletionCount: Int,
    val remainingVersionCount: Int,
)

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

    @Query("SELECT COUNT(*) FROM articles WHERE profileId = :profileId AND localDate = :localDate AND activeWordBookId = :activeWordBookId AND articleType = :articleType AND lengthTier = :lengthTier")
    suspend fun countInGroup(
        profileId: String,
        localDate: String,
        activeWordBookId: String,
        articleType: String,
        lengthTier: String,
    ): Int

    /** 保留面计数：「删除全部学习数据」确认框要明说保住了几篇文章（决策 §9）。 */
    @Query("SELECT COUNT(*) FROM articles WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: String): Int

    @Query("SELECT COUNT(*) FROM reading_completions WHERE articleId = :articleId")
    suspend fun countCompletionsForArticle(articleId: String): Int

    @Query("DELETE FROM articles WHERE articleId = :articleId")
    suspend fun deleteById(articleId: String)

    @Query("DELETE FROM reading_completions WHERE articleId = :articleId")
    suspend fun deleteCompletionsForArticle(articleId: String): Int

    /**
     * F4-01 决策 §8「删不空」：同组（同日+同册+同类型+同长度档）只剩这一版时拒删并返回
     * null；否则在单个事务里连带删除该版的阅读完成记录与文章行，返回凭据。
     * 计数—判定—删除必须原子，否则并发重生成会绕过「至少留一版」。
     */
    @Transaction
    suspend fun deleteVersionIfNotLast(
        profileId: String,
        localDate: String,
        activeWordBookId: String,
        articleType: String,
        lengthTier: String,
        articleId: String,
    ): VersionDeleteReceipt? {
        val groupCount = countInGroup(profileId, localDate, activeWordBookId, articleType, lengthTier)
        if (groupCount <= 1) return null
        val removedCompletions = deleteCompletionsForArticle(articleId)
        deleteById(articleId)
        return VersionDeleteReceipt(
            removedCompletionCount = removedCompletions,
            remainingVersionCount = groupCount - 1,
        )
    }
}
