package com.example.englishlearning.learning

/**
 * F4-01 Task10b：删除词书确认框的级联影响预览（口径钉决策 §6）。
 *
 * - [entryCount] 将被物理删除的词条数（按册，跨 profile——与 `deleteForWordBook` 同一口径）；
 * - [keptEventCount] 将被**保留**的学习事件数（profile+册）；
 * - [keptArticleCount] 将被保留的文章版本行数（profile+册；同日重生成算多版）。
 */
data class WordBookDeletionPreview(
    val entryCount: Int,
    val keptEventCount: Int,
    val keptArticleCount: Int,
)

interface WordBookDeletionPreviewRepository {
    suspend fun preview(profileId: String, wordBookId: String): Result<WordBookDeletionPreview>
}
