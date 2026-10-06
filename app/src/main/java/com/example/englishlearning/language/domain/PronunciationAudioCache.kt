package com.example.englishlearning.language.domain

interface PronunciationAudioCache {
    suspend fun get(key: PronunciationCacheKey): ByteArray?

    suspend fun put(key: PronunciationCacheKey, bytes: ByteArray)

    suspend fun putIfSuccessful(key: PronunciationCacheKey, bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.isEmpty()) return false
        put(key, bytes)
        return true
    }
}

/** Small deterministic cache implementation used until the persistent media adapter is wired. */
class InMemoryPronunciationAudioCache : PronunciationAudioCache {
    private val values = mutableMapOf<String, ByteArray>()

    override suspend fun get(key: PronunciationCacheKey): ByteArray? = values[key.cacheId()]?.copyOf()

    override suspend fun put(key: PronunciationCacheKey, bytes: ByteArray) {
        values[key.cacheId()] = bytes.copyOf()
    }
}
