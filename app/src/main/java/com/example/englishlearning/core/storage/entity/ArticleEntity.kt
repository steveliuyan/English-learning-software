package com.example.englishlearning.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [
        Index(
            value = ["profileId", "localDate", "activeWordBookId", "articleType", "lengthTier", "version"],
            unique = true,
            name = "index_articles_reuse_key",
        ),
        Index(value = ["profileId", "generatedAtEpochMillis"], name = "index_articles_profileId_generatedAtEpochMillis"),
    ],
)
data class ArticleEntity(
    @PrimaryKey val articleId: String,
    val profileId: String,
    val localDate: String,
    val activeWordBookId: String,
    val articleType: String,
    val lengthTier: String,
    val version: Int,
    val title: String,
    val englishText: String,
    val chineseText: String,
    val generatedAtEpochMillis: Long,
    /** JSON array of the lemmas used for the request; highlights are re-derived from it. */
    val coveredLemmas: String,
    val parameterSummary: String,
    val modelName: String?,
    /** 判别列：[com.example.englishlearning.reading.domain.ArticleSourceType] 的名称。 */
    val sourceType: String,
    // 以下五个来源列仅 WEB_FETCHED 非空；其余来源写空串保持 NOT NULL，与迁移回填一致。
    val sourceId: String,
    val sourceDisplayName: String,
    val sourceUrl: String,
    val sourceLicenseNote: String,
    val sourceAttribution: String,
)
