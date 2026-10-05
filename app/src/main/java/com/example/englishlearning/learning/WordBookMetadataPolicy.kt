package com.example.englishlearning.learning

data class WordBookMetadata(
    val id: String,
    val displayName: String,
    val level: String,
    val totalWords: Int,
    val dataVersion: String,
    val sourceId: String,
    val sourcePolicy: String,
    val attribution: String? = null,
)

sealed interface MetadataValidationResult {
    data object Valid : MetadataValidationResult

    data object UnknownSource : MetadataValidationResult

    data object OfficialDescriptionNotAllowed : MetadataValidationResult

    data object MissingAttribution : MetadataValidationResult
}

object WordBookMetadataPolicy {
    /** 内置**分组占位**词书的说明：这些册子只登记分组，词条由代码内的占位词卡提供。 */
    const val APPLICATION_GROUPING_POLICY = "应用内学习分组，不是官方考试大纲词表。词条尚未随本任务打包。"

    /**
     * **已打包**词书（`*.wbpack` 导入或导出）的说明。
     *
     * 与 [APPLICATION_GROUPING_POLICY] 分开，是因为两者的**事实不同**：占位分组册确实没有词条，
     * 而打包册带完整词条与配图。早期版本让打包器复用了占位文案，于是包里一边写着「词条尚未随本任务
     * 打包」一边装着 100 个词条——既是错的事实，又让导入端因逐字校验不过而永远拒绝整包。
     */
    const val PACKAGED_BOOK_POLICY = "应用内学习分组，不是官方考试大纲词表。"

    /** 已打包词书使用的词表来源 ID：与用户拍板的词源一致，且已在第三方声明中登记。 */
    const val PACKAGED_BOOK_SOURCE_ID = "ngsl-nawl-1.2"

    /** 内置分组占位册：词表来源 ID 白名单（NGSL/NAWL 与 CEFR-J 已登记第三方声明）。 */
    private val approvedSourceIds = setOf("ngsl-nawl-1.2", "cefr-j-1.5", "user-provided-xlsx")

    fun validate(metadata: WordBookMetadata): MetadataValidationResult =
        when {
            metadata.sourceId !in approvedSourceIds -> MetadataValidationResult.UnknownSource
            metadata.sourcePolicy != APPLICATION_GROUPING_POLICY &&
                metadata.sourcePolicy != PACKAGED_BOOK_POLICY ->
                MetadataValidationResult.OfficialDescriptionNotAllowed
            else -> MetadataValidationResult.Valid
        }

    /**
     * 已打包词书专用校验：来源 ID 仍走 [approvedSourceIds]，说明文案走 [PACKAGED_BOOK_POLICY]。
     * 两条防线都保持**逐字相等**，不放宽成模糊匹配——放宽就等于把「来源可信」这道检查废掉。
     */
    fun validatePackaged(metadata: WordBookMetadata): MetadataValidationResult =
        when {
            metadata.sourceId !in approvedSourceIds -> MetadataValidationResult.UnknownSource
            metadata.sourcePolicy != PACKAGED_BOOK_POLICY ->
                MetadataValidationResult.OfficialDescriptionNotAllowed
            metadata.attribution.isNullOrBlank() -> MetadataValidationResult.MissingAttribution
            else -> MetadataValidationResult.Valid
        }
}
