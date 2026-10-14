package com.example.englishlearning.core.storage.entity

import androidx.room.Entity

/**
 * F4-01 决策⑦：收藏词行。主键 (profileId, cardId) 给出「同册同卡重复收藏幂等」
 * 的 INSERT OR IGNORE 语义；wordBookId/lemma 是展示与跨册去重需要的快照字段，
 * 词书软删后收藏仍可读（收藏与删除彼此独立）。
 *
 * 表由 v31 迁移建出（`MIGRATION_30_31`），DDL 必须与 Room 依据本实体生成的建表
 * 语句逐字一致，由 `MigrationTestHelper.runMigrationsAndValidate` 把关。
 * 备份导出在 Task9 接入（records/favorites.json）。
 */
@Entity(
    tableName = "favorite_words",
    primaryKeys = ["profileId", "cardId"],
)
data class FavoriteWordEntity(
    val profileId: String,
    val cardId: String,
    val wordBookId: String,
    val lemma: String,
    val addedAtEpochMillis: Long,
)
