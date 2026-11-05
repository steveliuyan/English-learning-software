package com.example.englishlearning.language.domain

interface PronunciationAudioCache {
    suspend fun get(key: PronunciationCacheKey): ByteArray?

    /** [entity] 只进台账，不进 [key]（决策 2026-11-05 D-8）。 */
    suspend fun put(key: PronunciationCacheKey, bytes: ByteArray, entity: PronunciationEntityRef? = null)

    suspend fun putIfSuccessful(
        key: PronunciationCacheKey,
        bytes: ByteArray?,
        entity: PronunciationEntityRef? = null,
    ): Boolean {
        if (bytes == null || bytes.isEmpty()) return false
        put(key, bytes, entity)
        return true
    }
}

/** Small deterministic cache implementation used until the persistent media adapter is wired. */
class InMemoryPronunciationAudioCache : PronunciationAudioCache {
    private val values = mutableMapOf<String, ByteArray>()

    override suspend fun get(key: PronunciationCacheKey): ByteArray? = values[key.cacheId()]?.copyOf()

    override suspend fun put(key: PronunciationCacheKey, bytes: ByteArray, entity: PronunciationEntityRef?) {
        values[key.cacheId()] = bytes.copyOf()
    }
}
