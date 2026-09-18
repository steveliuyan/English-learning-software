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

        assertEquals(1, result.importedCount)
        assertEquals(1, result.rejectedCount)
        assertEquals(setOf(SeedRejectionReason.InvalidMetadata), result.rejectionReasons)
        assertEquals("小学", repository.findWordBook("primary-school").getOrNull()?.displayName)
    }

    private class FakeRepository : LearningProfileRepository {
        private val wordBooks = mutableMapOf<String, WordBook>()

        override suspend fun current(profileId: String): RepositoryResult<LearningProfile?> = RepositoryResult.Success(null)
        override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
        override suspend fun listWordBooks(): RepositoryResult<List<WordBook>> = RepositoryResult.Success(wordBooks.values.toList())
        override suspend fun findWordBook(id: String): RepositoryResult<WordBook?> = RepositoryResult.Success(wordBooks[id])
        override suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit> {
            wordBooks[wordBook.id] = wordBook
            return RepositoryResult.Success(Unit)
        }
    }
}
