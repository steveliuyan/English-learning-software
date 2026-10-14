package com.example.englishlearning.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Auditable metadata only for a locally available word book.
 * Word entries and learning progress deliberately live in later-stage tables.
 *
 * `deleted`（v31，F4-01 决策⑥）是软删标记：删词书只登记此行并物理删词条与审计，
 * 学习历史永不连带消失。[com.example.englishlearning.core.storage.dao.InternalWordBookDao]
 * 的列表查询过滤已删行，按 id 的点查读全量——历史详展仍要能归属原册名。
 * 声明顺序把 `deleted` 放最后，与 `ALTER TABLE ... ADD COLUMN` 的追加位逐字节同构。
 */
@Entity(tableName = "word_books")
data class WordBookEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val level: String,
    val totalWords: Int,
    val dataVersion: String,
    val sourceId: String,
    @ColumnInfo(defaultValue = "0") val deleted: Int = 0,
)
