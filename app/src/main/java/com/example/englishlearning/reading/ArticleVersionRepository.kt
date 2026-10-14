package com.example.englishlearning.reading

import com.example.englishlearning.reading.domain.Article

/**
 * F4-01 决策 §8「看得见、删得掉、删不空」的级联影响预览。
 *
 * [audioAssetCount] 恒为 0 是刻意口径：articles 与 asset_records 没有任何 schema 关联
 * （资产只挂拍照翻译与发音缓存），虚构一个非零数反而误导；保留该字段是为了把
 * 「删除前必须核资产」这条口径本身留在类型里，将来建立关联时才有落点。
 */
data class ArticleDeletionImpact(
    val removedCompletionCount: Int,
    val remainingVersionCount: Int,
    val audioAssetCount: Int,
)

sealed interface VersionDeleteOutcome {
    /** 删除成功，impact 为实际生效的级联结果（不是删除前的估算）。 */
    data class Deleted(val impact: ArticleDeletionImpact) : VersionDeleteOutcome

    /** 同组（同日+同册+同类型+同长度档）只剩这一版：拒绝，历史至少要留一版。 */
    data object RefusedLastVersion : VersionDeleteOutcome
}

/**
 * 文章版本管理端口。不改 [ArticleRepository]（它的 fake 散布在 4 个既有用例里），
 * 版本删除是独立职责，单独开口。版本列表本身复用 findHistory 的全部版本行，
 * 由 UI 端按组分展——不再加一个与 findHistory 重复的 versions() 查询。
 */
interface ArticleVersionRepository {
    suspend fun deletionImpact(article: Article): Result<ArticleDeletionImpact>

    suspend fun deleteVersion(article: Article): Result<VersionDeleteOutcome>
}
