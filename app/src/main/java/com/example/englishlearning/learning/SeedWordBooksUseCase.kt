package com.example.englishlearning.learning

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun interface WordBookMetadataAssetSource {
    fun read(): String
}

enum class SeedRejectionReason { InvalidAsset, InvalidMetadata, StorageUnavailable }

data class SeedWordBooksResult(
    val importedCount: Int,
    val rejectedCount: Int,
    val rejectionReasons: Set<SeedRejectionReason>,
)

class SeedWordBooksUseCase(
    private val assetSource: WordBookMetadataAssetSource,
    private val repository: LearningProfileRepository,
    private val json: Json = Json { ignoreUnknownKeys = false },
) {
    suspend operator fun invoke(): SeedWordBooksResult {
        val elements = try {
            json.parseToJsonElement(assetSource.read()).jsonArray
        } catch (_: Throwable) {
            return SeedWordBooksResult(0, 1, setOf(SeedRejectionReason.InvalidAsset))
        }
        var imported = 0
        var rejected = 0
        val reasons = mutableSetOf<SeedRejectionReason>()
        elements.forEach { entry ->
            val metadata = entry.toMetadata()
            if (metadata == null || WordBookMetadataPolicy.validate(metadata) != MetadataValidationResult.Valid) {
                rejected += 1
                reasons += SeedRejectionReason.InvalidMetadata
            } else {
                when (repository.upsertWordBook(metadata.toWordBook())) {
                    is RepositoryResult.Success -> imported += 1
                    is RepositoryResult.Failure -> {
                        rejected += 1
                        reasons += SeedRejectionReason.StorageUnavailable
                    }
                }
            }
        }
        return SeedWordBooksResult(imported, rejected, reasons)
    }

    private fun JsonElement.toMetadata(): WordBookMetadata? = try {
        val fields = jsonObject
        WordBookMetadata(
            id = fields.string("id"),
            displayName = fields.string("displayName"),
            level = fields.string("level"),
            totalWords = fields.string("totalWords").toInt(),
            dataVersion = fields.string("dataVersion"),
            sourceId = fields.string("sourceId"),
            sourcePolicy = fields.string("sourcePolicy"),
        )
    } catch (_: Throwable) {
        null
    }

    private fun WordBookMetadata.toWordBook() =
        WordBook(id, displayName, level, totalWords, dataVersion, sourceId)

    private fun Map<String, JsonElement>.string(name: String): String =
        getValue(name).jsonPrimitive.content
}
