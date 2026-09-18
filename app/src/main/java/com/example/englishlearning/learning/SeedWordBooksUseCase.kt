package com.example.englishlearning.learning

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun interface WordBookMetadataAssetSource {
    fun read(): String
}

data class SeedWordBooksResult(
    val importedIds: List<String>,
    val rejectedIds: List<String>,
)

class SeedWordBooksUseCase(
    private val assetSource: WordBookMetadataAssetSource,
    private val repository: LearningProfileRepository,
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    suspend operator fun invoke(): SeedWordBooksResult {
        val importedIds = mutableListOf<String>()
        val rejectedIds = mutableListOf<String>()
        val metadata = json.parseToJsonElement(assetSource.read()).jsonArray

        metadata.forEach { entry ->
            val fields = entry.jsonObject
            val item =
                WordBookMetadata(
                    id = fields.string("id"),
                    displayName = fields.string("displayName"),
                    level = fields.string("level"),
                    totalWords = fields.string("totalWords").toInt(),
                    dataVersion = fields.string("dataVersion"),
                    sourceId = fields.string("sourceId"),
                    sourcePolicy = fields.string("sourcePolicy"),
                )
            if (WordBookMetadataPolicy.validate(item) == MetadataValidationResult.Valid) {
                repository.upsertWordBook(
                    WordBook(item.id, item.displayName, item.level, item.totalWords, item.dataVersion, item.sourceId),
                )
                importedIds += item.id
            } else {
                rejectedIds += item.id
            }
        }
        return SeedWordBooksResult(importedIds, rejectedIds)
    }

    private fun Map<String, kotlinx.serialization.json.JsonElement>.string(name: String): String =
        getValue(name).jsonPrimitive.content
}
