package com.example.englishlearning.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * 词条**字串索引**（FTS4 + 3-gram）的虚拟表声明。
 *
 * ## 为什么这张表必须被 Room 认识
 *
 * 它本来是「迁移里手写 `CREATE VIRTUAL TABLE` 建出来的额外表」。这在 Room 2.8 上**行不通**：
 * 迁移后的 schema 校验会把「数据库里存在、实体清单里没有」的表判为
 * `IllegalStateException: Migration didn't properly handle: Unexpected table ...`
 * ——不是测试独有的问题，真机从 v25 升到 v26 时会直接崩在升级上。
 * 所以刷成 Room 实体：建表交给 Room（全新安装）、迁移里的 DDL 必须与 Room 生成的**逐字一致**，
 * 这条约束由 `MigrationTestHelper.runMigrationsAndValidate` 自动把关。
 *
 * ## 字段
 *
 * `rowid` 是 Room 对 FTS 实体的硬性要求（docid 的映射）；本表不通过 Room 的 `@Insert` 写入
 * （写入在 [com.example.englishlearning.learning.VocabularySearchGramStore] 里用原始 SQL 做，
 * 避免为 45,290 行手工分配 docid），`rowid` 只是 schema 的一部分。
 *
 * `tokens` 是各词项 3-gram 用空格拼成、词项之间插屏障的检索串（见 `VocabularySearchGram`）。
 *
 * `wordBookId` / `cardId` 声明为 `notIndexed`：它们只用于回连基表（基表主键正是这两列），
 * 进倒排索引纯属浪费空间；查询也一律写成 `tokens MATCH ?` 限定列，不会误命中它们。
 */
@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    // 折叠字符必须与 VocabularySearchGram.GRAM_FILLER 一致：它被注册成 token 字符，
    // 才能保证折叠出来的 gram 是单个 token（否则会被切碎 → 短语查询漏命中 → 静默缺词）。
    tokenizerArgs = ["tokenchars=~"],
    notIndexed = ["wordBookId", "cardId"],
)
@Entity(tableName = "vocabulary_search_gram")
data class VocabularySearchGramEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    @ColumnInfo(name = "tokens") val tokens: String,
    @ColumnInfo(name = "wordBookId") val wordBookId: String,
    @ColumnInfo(name = "cardId") val cardId: String,
)
