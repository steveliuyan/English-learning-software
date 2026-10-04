package com.example.englishlearning.learning

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `empty and whitespace-only queries do not call record or create history`() = runBlocking {
        val repository = InMemoryVocabularySearchHistoryRepository()

        recordSearch(repository, "profile-1", "")
        recordSearch(repository, "profile-1", " \t\n ")

        assertEquals(0, repository.recordCallCount)
        assertTrue(repository.list("profile-1", 20).successValue().isEmpty())
        assertNull(normalizeVocabularySearchQuery(""))
        assertNull(normalizeVocabularySearchQuery(" \t\n "))
    }

    @Test
    fun `normalization folds whitespace and lowercases with root locale`() {
        assertEquals("hello world", normalizeVocabularySearchQuery("  HELLO\t  WORLD  "))
        assertEquals("istanbul", normalizeVocabularySearchQuery("ISTANBUL"))
    }

    @Test
    fun `disabling count display leaves persisted history statistics unchanged`() = runBlocking {
        val repository = InMemoryVocabularySearchHistoryRepository()
        repository.record("profile-1", "ability", null, Instant.EPOCH)
        repository.record("profile-1", "ability", null, Instant.EPOCH.plusSeconds(1))
        val settings = SearchHistoryDisplaySettings(showCount = true)
        val before = repository.find("profile-1", "ability").successValue()!!

        settings.showCount = false
        val after = repository.find("profile-1", "ability").successValue()!!

        assertFalse(settings.showCount)
        assertEquals(before, after)
        assertEquals(2, after.searchCount)
    }

    @Test
    fun `repository failures are exposed as RepositoryResult Failure`() = runBlocking {
        val repository = FailingVocabularySearchHistoryRepository()

        assertFailure(repository.list("profile-1", 20))
        assertFailure(repository.record("profile-1", "ability", null, Instant.EPOCH))
        assertFailure(repository.find("profile-1", "ability"))
        assertFailure(repository.clear("profile-1"))
    }

    private suspend fun recordSearch(
        repository: InMemoryVocabularySearchHistoryRepository,
        profileId: String,
        query: String,
    ) {
        if (normalizeVocabularySearchQuery(query) != null) {
            repository.record(profileId, query, null, Instant.EPOCH)
        }
    }

    private fun <T> assertFailure(result: RepositoryResult<T>) {
        assertTrue(result is RepositoryResult.Failure)
        assertEquals(LearningProfileRepositoryError.StorageUnavailable, (result as RepositoryResult.Failure).error)
    }

    private data class SearchHistoryDisplaySettings(var showCount: Boolean)

    private class FailingVocabularySearchHistoryRepository : VocabularySearchHistoryRepository {
        override suspend fun list(profileId: String, limit: Int): RepositoryResult<List<VocabularySearchHistory>> = failure()

        override suspend fun record(
            profileId: String,
            query: String,
            representative: SearchRepresentative?,
            at: Instant,
        ): RepositoryResult<Unit> = failure()

        override suspend fun find(profileId: String, normalizedQuery: String): RepositoryResult<VocabularySearchHistory?> = failure()

        override suspend fun clear(profileId: String): RepositoryResult<Unit> = failure()

        private fun <T> failure(): RepositoryResult<T> =
            RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
    }

    private class InMemoryVocabularySearchHistoryRepository : VocabularySearchHistoryRepository {
        var recordCallCount = 0
            private set
        private val rows = linkedMapOf<Pair<String, String>, VocabularySearchHistory>()

        override suspend fun list(profileId: String, limit: Int): RepositoryResult<List<VocabularySearchHistory>> =
            RepositoryResult.Success(rows.values.filter { it.profileId == profileId }.take(limit))

        override suspend fun record(
            profileId: String,
            query: String,
            representative: SearchRepresentative?,
            at: Instant,
        ): RepositoryResult<Unit> {
            recordCallCount += 1
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
