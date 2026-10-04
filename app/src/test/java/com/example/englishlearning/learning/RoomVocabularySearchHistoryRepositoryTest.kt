package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.entity.VocabularySearchHistoryEntity
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RoomVocabularySearchHistoryRepositoryTest {
    @Test
    fun `new record maps normalized query and starts count at one`() {
        val at = Instant.parse("2026-10-01T10:00:00Z")
        val entity = nextVocabularySearchHistoryEntity(
            existing = null,
            profileId = "profile-1",
            normalizedQuery = "ability",
            displayQuery = "  Ability\t",
            representative = SearchRepresentative("book-1", "card-1"),
            at = at,
        )

        assertEquals(
            VocabularySearchHistoryEntity(
                profileId = "profile-1",
                normalizedQuery = "ability",
                displayQuery = "Ability",
                searchCount = 1,
                firstSearchedAtEpochMillis = at.toEpochMilli(),
                lastSearchedAtEpochMillis = at.toEpochMilli(),
                representativeWordBookId = "book-1",
                representativeCardId = "card-1",
            ),
            entity,
        )
        assertEquals(
            VocabularySearchHistory("profile-1", "ability", "Ability", 1, at, at, SearchRepresentative("book-1", "card-1")),
            entity.toVocabularySearchHistory(),
        )
    }

    @Test
    fun `existing record increments count preserves first timestamp and replaces representative`() {
        val firstAt = Instant.parse("2026-10-01T10:00:00Z")
        val secondAt = firstAt.plusSeconds(10)
        val existing = VocabularySearchHistoryEntity(
            profileId = "profile-1",
            normalizedQuery = "ability",
            displayQuery = "Ability",
            searchCount = 2,
            firstSearchedAtEpochMillis = firstAt.toEpochMilli(),
            lastSearchedAtEpochMillis = firstAt.toEpochMilli(),
            representativeWordBookId = "book-old",
            representativeCardId = "card-old",
        )

        val updated = nextVocabularySearchHistoryEntity(
            existing = existing,
            profileId = "profile-1",
            normalizedQuery = "ability",
            displayQuery = "ABILITY",
            representative = null,
            at = secondAt,
        )

        assertEquals(existing.copy(
            displayQuery = "ABILITY",
            searchCount = 3,
            lastSearchedAtEpochMillis = secondAt.toEpochMilli(),
            representativeWordBookId = null,
            representativeCardId = null,
        ), updated)
    }

    @Test
    fun `partial representative does not map to domain representative`() {
        val entity = VocabularySearchHistoryEntity("p", "q", "q", 1, 0, 0, "book", null)
        assertNull(entity.toVocabularySearchHistory().representative)
    }
}
