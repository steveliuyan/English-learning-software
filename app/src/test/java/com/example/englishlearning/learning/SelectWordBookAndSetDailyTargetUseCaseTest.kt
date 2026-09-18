package com.example.englishlearning.learning

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectWordBookAndSetDailyTargetUseCaseTest {
    private val repository = FakeLearningProfileRepository()
    private val useCase = SelectWordBookAndSetDailyTargetUseCase(repository)

    @Test
    fun `zero target is rejected without persisting`() = runTest {
        val result = useCase("default", "primary-school", 0)

        assertEquals(SetupResult.InvalidDailyTarget, result)
        assertNull(repository.current("default"))
    }

    @Test
    fun `negative target is rejected without persisting`() = runTest {
        val result = useCase("default", "primary-school", -1)

        assertEquals(SetupResult.InvalidDailyTarget, result)
        assertNull(repository.current("default"))
    }

    @Test
    fun `unknown wordbook is rejected without persisting`() = runTest {
        val result = useCase("default", "missing", 10)

        assertEquals(SetupResult.UnknownWordBook, result)
        assertNull(repository.current("default"))
    }

    @Test
    fun `valid selection saves matching profile wordbook and target`() = runTest {
        repository.upsertWordBook(WordBook("primary-school", "小学", "基础", 0, "v1", "ngsl-nawl-1.2"))

        val result = useCase("default", "primary-school", 10)

        assertEquals(SetupResult.Saved, result)
        assertEquals(
            LearningProfile("default", "primary-school", 10),
            repository.current("default"),
        )
    }

    private class FakeLearningProfileRepository : LearningProfileRepository {
        private val profiles = mutableMapOf<String, LearningProfile>()
        private val wordBooks = mutableMapOf<String, WordBook>()

        override suspend fun current(profileId: String): LearningProfile? = profiles[profileId]

        override suspend fun save(profile: LearningProfile) {
            profiles[profile.profileId] = profile
        }

        override suspend fun listWordBooks(): List<WordBook> = wordBooks.values.toList()

        override suspend fun findWordBook(id: String): WordBook? = wordBooks[id]

        override suspend fun upsertWordBook(wordBook: WordBook) {
            wordBooks[wordBook.id] = wordBook
        }
    }
}
