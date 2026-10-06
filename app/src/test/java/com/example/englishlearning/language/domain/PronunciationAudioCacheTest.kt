package com.example.englishlearning.language.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PronunciationAudioCacheTest {
    private val key = PronunciationCacheKey("schedule", PronunciationAccent.American, "mimo", "mimo-v2.5-tts", "Mia")

    @Test
    fun `successful audio is replayable by exact cache key`() = runTest {
        val cache = InMemoryPronunciationAudioCache()
        val bytes = byteArrayOf(1, 2, 3)

        cache.put(key, bytes)

        assertContentEquals(bytes, cache.get(key))
    }

    @Test
    fun `cache returns a defensive copy`() = runTest {
        val cache = InMemoryPronunciationAudioCache()
        cache.put(key, byteArrayOf(1, 2, 3))

        val first = cache.get(key)!!
        first[0] = 9

        assertContentEquals(byteArrayOf(1, 2, 3), cache.get(key))
    }

    @Test
    fun `failed generation is represented by no write`() = runTest {
        val cache = InMemoryPronunciationAudioCache()

        assertEquals(false, cache.putIfSuccessful(key, null))
        assertNull(cache.get(key))
    }

    @Test
    fun `regeneration replaces only the same accent key`() = runTest {
        val cache = InMemoryPronunciationAudioCache()
        val british = key.copy(accent = PronunciationAccent.British)
        cache.put(key, byteArrayOf(1))
        cache.put(british, byteArrayOf(2))

        cache.put(key, byteArrayOf(3))

        assertContentEquals(byteArrayOf(3), cache.get(key))
        assertContentEquals(byteArrayOf(2), cache.get(british))
    }
}
