package com.example.englishlearning.wordbook

import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class WordBookPackageCacheTest {
    @Test
    fun `same package fingerprint is parsed once`() {
        val directory = Files.createTempDirectory("wordbook-cache").toFile()
        try {
            File(directory, "book.json").writeText("v1")
            var parses = 0
            val cache = WordBookPackageCache<String> { _ -> parses++; "parsed-${File(directory, "book.json").readText()}" }

            assertEquals("parsed-v1", cache.get(directory))
            assertEquals("parsed-v1", cache.get(directory))
            assertEquals(1, parses)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `changed package fingerprint is reparsed`() {
        val directory = Files.createTempDirectory("wordbook-cache").toFile()
        try {
            File(directory, "book.json").writeText("v1")
            var parses = 0
            val cache = WordBookPackageCache<String> { _ -> parses++; File(directory, "book.json").readText() }

            assertEquals("v1", cache.get(directory))
            File(directory, "book.json").writeText("v2-with-more-content")
            assertEquals("v2-with-more-content", cache.get(directory))
            assertEquals(2, parses)
        } finally {
            directory.deleteRecursively()
        }
    }
}
