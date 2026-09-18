package com.example.englishlearning.learning

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SeedWordBooksUseCaseTest {
    @Test
    fun `malformed asset is rejected without write`() = runTest {
        val repository = FakeRepository()

        val result = SeedWordBooksUseCase({ "not-json" }, repository)()

        assertEquals(0, result.importedCount)
        assertEquals(1, result.rejectedCount)
        assertEquals(setOf(SeedRejectionReason.InvalidAsset), result.rejectionReasons)
        assertEquals(0, repository.upsertCalls)
    }

    @Test
    fun `invalid metadata is rejected without exposing its id`() = runTest {
        val repository = FakeRepository()
        val maliciousId = "not-an-approved-id"
        val source = WordBookMetadataAssetSource {
            """[{"id":"$maliciousId","displayName":"x","level":"x","totalWords":"bad"}]"""
        }

        val result = SeedWordBooksUseCase(source, repository)()

        assertEquals(0, result.importedCount)
        assertEquals(1, result.rejectedCount)
        assertEquals(setOf(SeedRejectionReason.InvalidMetadata), result.rejectionReasons)
        assertEquals(0, repository.upsertCalls)
        assertEquals(false, result.toString().contains(maliciousId))
    }

    @Test
    fun `unapproved source and policy are rejected without write`() = runTest {
        val repository = FakeRepository()
        val source = WordBookMetadataAssetSource {
            """[
              {"id":"unknown","displayName":"未知","level":"x","totalWords":0,"dataVersion":"v1","sourceId":"unapproved","sourcePolicy":"${WordBookMetadataPolicy.APPLICATION_GROUPING_POLICY}"},
              {"id":"cet4","displayName":"四级","level":"x","totalWords":0,"dataVersion":"v1","sourceId":"cefr-j-1.5","sourcePolicy":"官方考试大纲"}
            ]"""
        }

        val result = SeedWordBooksUseCase(source, repository)()

        assertEquals(2, result.rejectedCount)
        assertEquals(setOf(SeedRejectionReason.InvalidMetadata), result.rejectionReasons)
        assertEquals(0, repository.upsertCalls)
    }

    @Test
    fun `storage failure is reported without raw metadata`() = runTest {
        val repository = FakeRepository().apply { failUpsert = true }
        val source = WordBookMetadataAssetSource { validMetadata() }

        val result = SeedWordBooksUseCase(source, repository)()

        assertEquals(0, result.importedCount)
        assertEquals(1, result.rejectedCount)
        assertEquals(setOf(SeedRejectionReason.StorageUnavailable), result.rejectionReasons)
        assertEquals(1, repository.upsertCalls)
    }

    private fun validMetadata() =
        """[{"id":"primary-school","displayName":"小学","level":"基础","totalWords":0,"dataVersion":"v1","sourceId":"ngsl-nawl-1.2","sourcePolicy":"${WordBookMetadataPolicy.APPLICATION_GROUPING_POLICY}"}]"""

    private class FakeRepository : LearningProfileRepository {
        var upsertCalls = 0
        var failUpsert = false

        override suspend fun current(profileId: String): RepositoryResult<LearningProfile?> =
            RepositoryResult.Success(null)

        override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)

        override suspend fun listWordBooks(): RepositoryResult<List<WordBook>> =
            RepositoryResult.Success(emptyList())

        override suspend fun findWordBook(id: String): RepositoryResult<WordBook?> =
            RepositoryResult.Success(null)

        override suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit> {
            upsertCalls += 1
            return if (failUpsert) {
                RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            } else {
                RepositoryResult.Success(Unit)
            }
        }
    }
}
