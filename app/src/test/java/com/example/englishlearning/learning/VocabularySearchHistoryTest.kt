package com.example.englishlearning.learning

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class VocabularySearchHistoryTest {
    @Test
    fun `first record starts at one and repeated normalized query accumulates`() = runBlocking {
        val repository = InMemoryVocabularySearchHistoryRepository()
        val representative = SearchRepresentative("book-1", "card-1")
        val firstAt = Instant.parse("2026-10-01T10:00:00Z")
        val secondAt = firstAt.plusSeconds(10)

        repository.record("profile-1", "  Ability  ", representative, firstAt)
        repository.record("profile-1", "ABILITY", representative, secondAt)

        val history = repository.find("profile-1", "ability").successValue()
        assertEquals(2, history?.searchCount)
        assertEquals("ABILITY", history?.displayQuery)
        assertEquals(firstAt, history?.firstSearchedAt)
        assertEquals(secondAt, history?.lastSearchedAt)
        assertEquals(representative, history?.representative)
    }

    @Test
    fun `profiles are isolated`() = runBlocking {
        val repository = InMemoryVocabularySearchHistoryRepository()
        val at = Instant.parse("2026-10-01T10:00:00Z")

        repository.record("profile-1", "ability", null, at)
        repository.record("profile-2", "ability", null, at)

        assertEquals(1, repository.list("profile-1", 20).successValue().single().searchCount)
        assertEquals(1, repository.list("profile-2", 20).successValue().single().searchCount)
    }

    @Test
    fun `empty and whitespace-only queries do not produce valid records`() {
        assertNull(normalizeVocabularySearchQuery(""))
        assertNull(normalizeVocabularySearchQuery(" \t\n "))
    }

    @Test
    fun `normalization folds whitespace and lowercases with root locale`() {
        assertEquals("hello world", normalizeVocabularySearchQuery("  HELLO\t  WORLD  "))
        assertEquals("istanbul", normalizeVocabularySearchQuery("ISTANBUL"))
    }

    @Test
    fun `display setting is independent from accumulated count`() = runBlocking {
        val repository = InMemoryVocabularySearchHistoryRepository()
        repository.record("profile-1", "ability", null, Instant.EPOCH)
        repository.record("profile-1", "ability", null, Instant.EPOCH.plusSeconds(1))

        val history = repository.find("profile-1", "ability").successValue()!!
        val showCount = false
        assertEquals(2, history.searchCount)
        assertTrue(!showCount)
    }

    private class InMemoryVocabularySearchHistoryRepository : VocabularySearchHistoryRepository {
        private val rows = linkedMapOf<Pair<String, String>, VocabularySearchHistory>()

        override suspend fun list(profileId: String, limit: Int): RepositoryResult<List<VocabularySearchHistory>> =
            RepositoryResult.Success(rows.values.filter { it.profileId == profileId }.take(limit))

        override suspend fun record(
            profileId: String,
            query: String,
            representative: SearchRepresentative?,
            at: Instant,
        ): RepositoryResult<Unit> {
            val normalized = normalizeVocabularySearchQuery(query) ?: return RepositoryResult.Success(Unit)
            val key = profileId to normalized
            val existing = rows[key]
            rows[key] = if (existing == null) {
                VocabularySearchHistory(profileId, normalized, query.trim(), 1, at, at, representative)
            } else {
                existing.copy(searchCount = existing.searchCount + 1, displayQuery = query.trim(), lastSearchedAt = at, representative = representative)
            }
            return RepositoryResult.Success(Unit)
        }

        override suspend fun find(profileId: String, normalizedQuery: String): RepositoryResult<VocabularySearchHistory?> =
            RepositoryResult.Success(rows[profileId to normalizedQuery])

        override suspend fun clear(profileId: String): RepositoryResult<Unit> {
            rows.keys.removeIf { it.first == profileId }
            return RepositoryResult.Success(Unit)
        }
    }

    private fun <T> RepositoryResult<T>.successValue(): T = (this as RepositoryResult.Success).value
}
