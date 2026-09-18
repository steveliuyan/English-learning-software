package com.example.englishlearning.learning

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SeedWordBooksUseCaseTest {
    @Test
    fun `imports compliant metadata and reports rejected metadata`() = runTest {
        val repository = FakeRepository()
        val source =
            WordBookMetadataAssetSource {
                """[
                    {"id":"primary-school","displayName":"小学","level":"基础","totalWords":0,"dataVersion":"v1","sourceId":"ngsl-nawl-1.2","sourcePolicy":"${WordBookMetadataPolicy.APPLICATION_GROUPING_POLICY}"},
                    {"id":"unknown","displayName":"未知","level":"未知","totalWords":0,"dataVersion":"v1","sourceId":"unapproved","sourcePolicy":"${WordBookMetadataPolicy.APPLICATION_GROUPING_POLICY}"}
                ]"""
            }

        val result = SeedWordBooksUseCase(source, repository)()

        assertEquals(listOf("primary-school"), result.importedIds)
        assertEquals(listOf("unknown"), result.rejectedIds)
        assertEquals("小学", repository.findWordBook("primary-school")?.displayName)
    }

    private class FakeRepository : LearningProfileRepository {
        private val wordBooks = mutableMapOf<String, WordBook>()

        override suspend fun current(profileId: String): LearningProfile? = null
        override suspend fun save(profile: LearningProfile) = Unit
        override suspend fun listWordBooks(): List<WordBook> = wordBooks.values.toList()
        override suspend fun findWordBook(id: String): WordBook? = wordBooks[id]
        override suspend fun upsertWordBook(wordBook: WordBook) {
            wordBooks[wordBook.id] = wordBook
        }
    }
}
