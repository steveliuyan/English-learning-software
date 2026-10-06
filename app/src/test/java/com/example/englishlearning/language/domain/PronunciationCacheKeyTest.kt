package com.example.englishlearning.language.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PronunciationCacheKeyTest {
    @Test
    fun `same text accent provider model voice and version produce same key`() {
        val first = PronunciationCacheKey(" Schedule ", PronunciationAccent.American, "mimo", "mimo-v2.5-tts", "Mia")
        val second = PronunciationCacheKey("schedule", PronunciationAccent.American, "mimo", "mimo-v2.5-tts", "Mia")

        assertEquals(first.cacheId(), second.cacheId())
    }

    @Test
    fun `american and british pronunciations never share cache key`() {
        val american = PronunciationCacheKey("schedule", PronunciationAccent.American, "mimo", "mimo-v2.5-tts", "Mia")
        val british = american.copy(accent = PronunciationAccent.British)

        assertNotEquals(american, british)
        assertNotEquals(american.cacheId(), british.cacheId())
    }

    @Test
    fun `model voice provider and request version changes produce new cache key`() {
        val base = PronunciationCacheKey("schedule", PronunciationAccent.American, "mimo", "mimo-v2.5-tts", "Mia")

        assertNotEquals(base.cacheId(), base.copy(providerId = "mimo-2").cacheId())
        assertNotEquals(base.cacheId(), base.copy(model = "mimo-v3").cacheId())
        assertNotEquals(base.cacheId(), base.copy(voice = "Chloe").cacheId())
        assertNotEquals(base.cacheId(), base.copy(requestVersion = 2).cacheId())
    }

    @Test
    fun `cache id is a lowercase sha256 digest without user text`() {
        val key = PronunciationCacheKey("schedule", PronunciationAccent.American, "mimo", "mimo-v2.5-tts", "Mia")

        assertTrue(key.cacheId().matches(Regex("[0-9a-f]{64}")))
        assertTrue(!key.cacheId().contains("schedule"))
    }
}
