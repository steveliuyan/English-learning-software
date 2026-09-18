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
    private val approvedSourceIds = setOf("ngsl-nawl-1.2", "cefr-j-1.5")

    fun validate(metadata: WordBookMetadata): MetadataValidationResult =
        when {
            metadata.sourceId !in approvedSourceIds -> MetadataValidationResult.UnknownSource
            metadata.sourcePolicy.contains("官方") && !metadata.sourcePolicy.contains("不是官方") ->
                MetadataValidationResult.OfficialDescriptionNotAllowed
            else -> MetadataValidationResult.Valid
        }
}
