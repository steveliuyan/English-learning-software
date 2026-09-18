package com.example.englishlearning.learning

data class WordBookMetadata(
    val id: String,
    val displayName: String,
    val level: String,
    val totalWords: Int,
    val dataVersion: String,
    val sourceId: String,
    val sourcePolicy: String,
)

sealed interface MetadataValidationResult {
    data object Valid : MetadataValidationResult

    data object UnknownSource : MetadataValidationResult

    data object OfficialDescriptionNotAllowed : MetadataValidationResult
}

object WordBookMetadataPolicy {
    const val APPLICATION_GROUPING_POLICY = "应用内学习分组，不是官方考试大纲词表。词条尚未随本任务打包。"

    private val approvedSourceIds = setOf("ngsl-nawl-1.2", "cefr-j-1.5")

    fun validate(metadata: WordBookMetadata): MetadataValidationResult =
        when {
            metadata.sourceId !in approvedSourceIds -> MetadataValidationResult.UnknownSource
            metadata.sourcePolicy != APPLICATION_GROUPING_POLICY ->
                MetadataValidationResult.OfficialDescriptionNotAllowed
            else -> MetadataValidationResult.Valid
        }
}
