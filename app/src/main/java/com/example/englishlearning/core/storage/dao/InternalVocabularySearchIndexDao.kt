package com.example.englishlearning.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.englishlearning.core.storage.entity.VocabularySearchIndexEntity

/**
 * 词条搜索索引的读写口。
 *
 * 三个检索方法对应三个匹配等级，**必须分开查**，而不是一个 `LIKE '%q%'` 加 `LIMIT`：
 *
 * 1. `LIKE '%q%'` 用不上 `normalizedLemma` 上的索引，等于全表扫描；
 * 2. 更严重的是语义错误——先按 `LIMIT` 截断再排序，会让排在前面的「包含匹配」把
 *    真正的「完全匹配」挤出结果集。旧实现（每次遍历全部词书）没有这个问题，索引化时
 *    一旦图省事写成单条 `LIKE ... LIMIT`，就等于用性能换正确性。
 *
 * 三级各走各的路（SQL 见 [VocabularySearchIndexSql]）：
 * exact / prefix 走 `normalizedLemma` 的 B 树；contains 由 FTS4 gram 索引收窄候选集，
 * 再由 `LIKE` 在候选集上做精确判断。
 *
 * LIKE 的 `%` 与 `_` 在用户输入里是合法字符，必须转义后配合 `ESCAPE '\'` 使用，
 * 否则用户搜 `a_b` 会意外命中 `axb`。
 */
@Dao
internal interface InternalVocabularySearchIndexDao {
    @Query("DELETE FROM vocabulary_search_index WHERE wordBookId = :wordBookId")
    suspend fun deleteWordBook(wordBookId: String)

    @Query("DELETE FROM vocabulary_search_index WHERE wordBookId NOT IN (:keepWordBookIds)")
    suspend fun deleteMissing(keepWordBookIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<VocabularySearchIndexEntity>)

    @Query(VocabularySearchIndexSql.EXACT)
    suspend fun searchExact(normalizedQuery: String, limit: Int): List<VocabularySearchIndexEntity>

    @Query(VocabularySearchIndexSql.PREFIX)
    suspend fun searchPrefix(normalizedQuery: String, prefixUpper: String, limit: Int): List<VocabularySearchIndexEntity>

    /**
     * 包含匹配的索引路径。
     *
     * 用 `@RawQuery` 而不是 `@Query`：`vocabulary_search_gram` 是迁移里建的 FTS4 虚拟表，
     * Room 的实体表清单里没有它，`@Query` 会在编译期报「表不存在」。`@RawQuery` 不做
     * schema 校验，SQL 由 [VocabularySearchIndexSql.CONTAINS] 提供——与查询计划守卫测试同源。
     */
    @RawQuery
    suspend fun searchContainsIndexed(query: SupportSQLiteQuery): List<VocabularySearchIndexEntity>

    /** 包含匹配的兜底路径：查询串短于 3 字符时没有 gram 可用。 */
    @Query(VocabularySearchIndexSql.CONTAINS_SHORT)
    suspend fun searchContainsShort(normalizedQuery: String, limit: Int): List<VocabularySearchIndexEntity>

    @Query("SELECT DISTINCT wordBookId, dataVersion FROM vocabulary_search_index")
    suspend fun indexedVersions(): List<IndexedWordBookVersion>
}

/** 索引里已建过索引的词书及其建立时的内容版本。 */
data class IndexedWordBookVersion(
    val wordBookId: String,
    val dataVersion: String,
)
