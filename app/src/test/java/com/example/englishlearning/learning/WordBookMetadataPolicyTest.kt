package com.example.englishlearning.learning

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
                    sourcePolicy = "官方四级词表",
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
    fun `metadata accepts approved source and non official learning grouping policy`() {
        val result =
            WordBookMetadataPolicy.validate(
                WordBookMetadata(
                    id = "cet4",
                    displayName = "大学英语四级",
                    level = "CET-4",
                    totalWords = 0,
                    dataVersion = "v1",
                    sourceId = "cefr-j-1.5",
                    sourcePolicy = "应用内学习分组，不是官方考试大纲词表。",
                ),
            )

        assertEquals(MetadataValidationResult.Valid, result)
    }
}
