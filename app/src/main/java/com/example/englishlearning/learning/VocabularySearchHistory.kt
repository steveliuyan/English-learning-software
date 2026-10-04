package com.example.englishlearning.learning

import java.time.Instant
import java.util.Locale

/** A word-card representative associated with a search history entry. */
data class SearchRepresentative(
    val wordBookId: String,
    val cardId: String,
)

data class VocabularySearchHistory(
    val profileId: String,
    val normalizedQuery: String,
    val displayQuery: String,
    val searchCount: Int,
    val firstSearchedAt: Instant,
    val lastSearchedAt: Instant,
    val representative: SearchRepresentative?,
)

interface VocabularySearchHistoryRepository {
    suspend fun list(profileId: String, limit: Int): RepositoryResult<List<VocabularySearchHistory>>

    suspend fun record(
        profileId: String,
        query: String,
        representative: SearchRepresentative?,
        at: Instant,
    ): RepositoryResult<Unit>

    suspend fun find(profileId: String, normalizedQuery: String): RepositoryResult<VocabularySearchHistory?>

    suspend fun clear(profileId: String): RepositoryResult<Unit>
}

fun normalizeVocabularySearchQuery(query: String): String? =
    query.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT).ifEmpty { null }
