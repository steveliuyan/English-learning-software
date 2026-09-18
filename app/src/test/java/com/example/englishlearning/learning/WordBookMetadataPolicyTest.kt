package com.example.englishlearning.learning

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordBookMetadataPolicyTest {
    @Test
    fun `metadata rejects a book without an approved source id`() {
        val result =
            WordBookMetadataPolicy.validate(
                WordBookMetadata(
                    id = "cet4",
                    displayName = "大学英语四级",
                    level = "CET-4",
                    totalWords = 0,
                    dataVersion = "v1",
                    sourceId = "unknown",
                    sourcePolicy = WordBookMetadataPolicy.APPLICATION_GROUPING_POLICY,
                ),
            )

        assertEquals(MetadataValidationResult.UnknownSource, result)
    }

    @Test
    fun `metadata rejects a policy that describes a grouping as official`() {
        val result =
            WordBookMetadataPolicy.validate(
                WordBookMetadata(
                    id = "cet4",
                    displayName = "大学英语四级",
                    level = "CET-4",
                    totalWords = 0,
                    dataVersion = "v1",
                    sourceId = "ngsl-nawl-1.2",
                    sourcePolicy = "官方四级词表",
                ),
            )

        assertEquals(MetadataValidationResult.OfficialDescriptionNotAllowed, result)
    }

    @Test
    fun `metadata rejects mixed negation that still attributes official exam syllabus`() {
        val result =
            WordBookMetadataPolicy.validate(
                WordBookMetadata(
                    id = "cet4",
                    displayName = "大学英语四级",
                    level = "CET-4",
                    totalWords = 0,
                    dataVersion = "v1",
                    sourceId = "ngsl-nawl-1.2",
                    sourcePolicy = "这不是官方的说法；本分组是官方考试大纲词表。",
                ),
            )

        assertEquals(MetadataValidationResult.OfficialDescriptionNotAllowed, result)
    }

    @Test
    fun `packaged wordbook metadata contains exactly six compliant learning groupings`() {
        val entries =
            Json.parseToJsonElement(metadataAsset().readText())
                .jsonArray
                .map { it.jsonObject }

        assertEquals(
            setOf(
                "primary-school",
                "junior-high-school",
                "senior-high-school",
                "cet4",
                "cet6",
                "postgraduate-entrance-exam",
            ),
            entries.map { it.requiredString("id") }.toSet(),
        )
        assertEquals(6, entries.size)

        entries.forEach { entry ->
            assertEquals(
                setOf("id", "displayName", "level", "totalWords", "dataVersion", "sourceId", "sourcePolicy"),
                entry.keys,
            )
            assertTrue(entry.requiredString("displayName").isNotBlank())
            assertTrue(entry.requiredString("level").isNotBlank())
            assertEquals(0, entry.requiredInt("totalWords"))
            assertEquals("v1", entry.requiredString("dataVersion"))
            assertTrue(entry.requiredString("sourcePolicy").contains("应用内学习分组"))
            assertTrue(entry.requiredString("sourcePolicy").contains("不是官方"))
            assertTrue(entry.requiredString("sourcePolicy").contains("词条尚未随本任务打包"))

            assertEquals(
                MetadataValidationResult.Valid,
                WordBookMetadataPolicy.validate(
                    WordBookMetadata(
                        id = entry.requiredString("id"),
                        displayName = entry.requiredString("displayName"),
                        level = entry.requiredString("level"),
                        totalWords = entry.requiredInt("totalWords"),
                        dataVersion = entry.requiredString("dataVersion"),
                        sourceId = entry.requiredString("sourceId"),
                        sourcePolicy = entry.requiredString("sourcePolicy"),
                    ),
                ),
            )
        }
    }

    private fun metadataAsset(): File =
        sequenceOf(
            File("src/main/assets/wordbooks/metadata.json"),
            File("app/src/main/assets/wordbooks/metadata.json"),
        ).firstOrNull(File::isFile)
            ?: error("wordbooks/metadata.json must be available to this test")

    private fun Map<String, kotlinx.serialization.json.JsonElement>.requiredString(name: String): String =
        getValue(name).jsonPrimitive.content

    private fun Map<String, kotlinx.serialization.json.JsonElement>.requiredInt(name: String): Int =
        getValue(name).jsonPrimitive.int
}
