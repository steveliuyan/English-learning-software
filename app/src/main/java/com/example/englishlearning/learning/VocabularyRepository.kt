package com.example.englishlearning.learning

import java.time.Instant

interface VocabularyRepository {
    suspend fun add(profileId: String, wordBookId: String, cardId: String, feedback: String, addedAt: Instant)
    suspend fun remove(profileId: String, wordBookId: String, cardId: String)
    suspend fun contains(profileId: String, wordBookId: String, cardId: String): Boolean
    suspend fun list(profileId: String, wordBookId: String? = null): RepositoryResult<List<VocabularyEntry>>
}
