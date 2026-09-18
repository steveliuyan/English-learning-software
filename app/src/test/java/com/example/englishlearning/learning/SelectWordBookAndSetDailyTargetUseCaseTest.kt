package com.example.englishlearning.learning

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SelectWordBookAndSetDailyTargetUseCaseTest {
    @Test
    fun `invalid target and unavailable storage do not save`() = runTest {
        val repository = FakeLearningProfileRepository()
        val useCase = SelectWordBookAndSetDailyTargetUseCase(repository)

        assertEquals(SetupResult.InvalidDailyTarget, useCase("default", "primary-school", 0))
        repository.findFailure = true
        assertEquals(SetupResult.StorageUnavailable, useCase("default", "primary-school", 1))
        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun `unknown wordbook does not save`() = runTest {
        val repository = FakeLearningProfileRepository()
        val useCase = SelectWordBookAndSetDailyTargetUseCase(repository)

        assertEquals(SetupResult.UnknownWordBook, useCase("default", "missing", 1))
        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun `save failure is reported without persistence`() = runTest {
        val repository = FakeLearningProfileRepository().apply {
            wordBooks["primary-school"] = wordBook()
            saveFailure = true
        }
        val useCase = SelectWordBookAndSetDailyTargetUseCase(repository)

        assertEquals(SetupResult.StorageUnavailable, useCase("default", "primary-school", 1))
        assertEquals(1, repository.saveCalls)
        assertEquals(null, repository.profiles["default"])
    }

    @Test
    fun `valid selection saves matching profile wordbook and target`() = runTest {
        val repository = FakeLearningProfileRepository().apply {
            wordBooks["primary-school"] = wordBook()
        }
        val useCase = SelectWordBookAndSetDailyTargetUseCase(repository)

        assertEquals(SetupResult.Saved, useCase("default", "primary-school", 10))
        assertEquals(
            LearningProfile("default", "primary-school", 10),
            (repository.current("default") as RepositoryResult.Success).value,
        )
    }

    private fun wordBook() =
        WordBook("primary-school", "小学", "基础", 0, "v1", "ngsl-nawl-1.2")

    private class FakeLearningProfileRepository : LearningProfileRepository {
        val profiles = mutableMapOf<String, LearningProfile>()
        val wordBooks = mutableMapOf<String, WordBook>()
        var findFailure = false
        var saveFailure = false
        var saveCalls = 0

        override suspend fun current(profileId: String): RepositoryResult<LearningProfile?> =
            RepositoryResult.Success(profiles[profileId])

        override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> {
            saveCalls += 1
            if (saveFailure) return RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            profiles[profile.profileId] = profile
            return RepositoryResult.Success(Unit)
        }

        override suspend fun listWordBooks(): RepositoryResult<List<WordBook>> =
            RepositoryResult.Success(wordBooks.values.toList())

        override suspend fun findWordBook(id: String): RepositoryResult<WordBook?> =
            if (findFailure) {
                RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            } else {
                RepositoryResult.Success(wordBooks[id])
            }

        override suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit> {
            wordBooks[wordBook.id] = wordBook
            return RepositoryResult.Success(Unit)
        }
    }
}
