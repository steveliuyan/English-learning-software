package com.example.englishlearning.learning

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.englishlearning.core.storage.AppDatabase
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomVocabularySearchHistoryRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomVocabularySearchHistoryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomVocabularySearchHistoryRepository(database, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun firstRecordStartsAtOneAndRepeatedNormalizedQueryAccumulates() = runBlocking {
        val firstAt = Instant.parse("2026-10-01T10:00:00Z")
        val secondAt = firstAt.plusSeconds(10)
        val representative = SearchRepresentative("book-1", "card-1")

        assertTrue(repository.record("profile-1", "  Ability  ", representative, firstAt) is RepositoryResult.Success)
        assertTrue(repository.record("profile-1", "ABILITY", representative, secondAt) is RepositoryResult.Success)

        val history = (repository.find("profile-1", "ability") as RepositoryResult.Success).value!!
        assertEquals(2, history.searchCount)
        assertEquals("ABILITY", history.displayQuery)
        assertEquals(firstAt, history.firstSearchedAt)
        assertEquals(secondAt, history.lastSearchedAt)
        assertEquals(representative, history.representative)
    }

    @Test
    fun listIsProfileScopedAndOrderedByMostRecent() = runBlocking {
        val at = Instant.parse("2026-10-01T10:00:00Z")
        repository.record("profile-1", "older", null, at)
        repository.record("profile-1", "newer", null, at.plusSeconds(1))
        repository.record("profile-2", "other", null, at.plusSeconds(2))

        val rows = (repository.list("profile-1", 20) as RepositoryResult.Success).value
        assertEquals(listOf("newer", "older"), rows.map { it.normalizedQuery })
    }

    @Test
    fun blankQueryIsIgnored() = runBlocking {
        repository.record("profile-1", "  \t ", null, Instant.EPOCH)

        val rows = (repository.list("profile-1", 20) as RepositoryResult.Success).value
        assertTrue(rows.isEmpty())
    }

    @Test
    fun clearRemovesOnlySelectedProfile() = runBlocking {
        val at = Instant.EPOCH
        repository.record("profile-1", "one", null, at)
        repository.record("profile-2", "two", null, at)

        repository.clear("profile-1")

        assertTrue(((repository.list("profile-1", 20) as RepositoryResult.Success).value).isEmpty())
        assertEquals(1, (repository.list("profile-2", 20) as RepositoryResult.Success).value.size)
    }
}
