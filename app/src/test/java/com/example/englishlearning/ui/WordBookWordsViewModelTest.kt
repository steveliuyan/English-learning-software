package com.example.englishlearning.ui

import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.LearningRecordRepository
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.LearningHistoryGroup
import com.example.englishlearning.learning.LearningProfileRepositoryError
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.WordBookRecordStatus
import com.example.englishlearning.learning.domain.WordCard
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordBookWordsViewModelTest {
    @Test
    fun `groups words by first letter case-insensitively with a trailing hash bucket`() {
        val groups = groupWordBookWordsByLetter(
            listOf(
                word("book:banana", "Banana"),
                word("book:apple", "apple"),
                word("book:avocado", "Avocado"),
                word("book:zebra", "zebra"),
                word("book:苹果", "苹果"),
            ),
        )
        assertEquals(listOf("A", "B", "Z", "#"), groups.map { it.letter })
        assertEquals(listOf("apple", "Avocado"), groups.first().records.map { it.displayLemma })
        assertEquals(listOf("Banana"), groups[1].records.map { it.displayLemma })
        assertEquals("苹果", groups.last().records.single().displayLemma)
    }

    @Test
    fun `letter buckets fold case and send non-letters to hash`() {
        assertEquals("A", letterBucketOf("apple"))
        assertEquals("A", letterBucketOf("Apple"))
        assertEquals("Z", letterBucketOf("  zebra "))
        assertEquals("#", letterBucketOf("苹果"))
        assertEquals("#", letterBucketOf("3rd"))
        assertEquals("#", letterBucketOf(""))
    }

    @Test
    fun `load fetches all words for the active word book and re-entry refreshes`() = runTest {
        val repository = FakeLearningRecordRepository()
        repository.allWordsResult = listOf(word("book:a", "apple", WordBookRecordStatus.Unlearned))
        val vm = WordBookWordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("book")
        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.allWordsCalls)
        assertEquals("apple", vm.state.value.words.single().displayLemma)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
        vm.load("book")
        testScheduler.advanceUntilIdle()
        assertEquals(2, repository.allWordsCalls)
    }

    @Test
    fun `filter narrows groups locally without reloading`() = runTest {
        val repository = FakeLearningRecordRepository()
        repository.allWordsResult = listOf(
            word("book:a", "apple", WordBookRecordStatus.Unlearned),
            word("book:b", "banana", WordBookRecordStatus.Due),
        )
        val vm = WordBookWordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("book")
        testScheduler.advanceUntilIdle()
        vm.selectFilter(WordBookRecordStatus.Due)
        assertEquals(1, repository.allWordsCalls)
        assertEquals(listOf("B"), vm.state.value.groups.map { it.letter })
        assertEquals("banana", vm.state.value.groups.single().records.single().displayLemma)
        vm.selectFilter(null)
        assertEquals(2, vm.state.value.groups.size)
        assertEquals(1, repository.allWordsCalls)
    }

    @Test
    fun `failed load reports an error and retry calls the repository again`() = runTest {
        val repository = FakeLearningRecordRepository()
        repository.allWordsResults += RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
        repository.allWordsResults += RepositoryResult.Success(emptyList())
        val vm = WordBookWordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load("book")
        testScheduler.advanceUntilIdle()
        assertEquals("词书单词暂时无法读取", vm.state.value.error)
        assertTrue(vm.state.value.words.isEmpty())
        vm.retry()
        testScheduler.advanceUntilIdle()
        assertEquals(2, repository.allWordsCalls)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `null word book id yields an empty idle state without touching the repository`() = runTest {
        val repository = FakeLearningRecordRepository()
        val vm = WordBookWordsViewModel(repository, FixedClockProvider(Instant.EPOCH, ZoneId.of("UTC")), StandardTestDispatcher(testScheduler))
        vm.load(null)
        testScheduler.advanceUntilIdle()
        assertEquals(0, repository.allWordsCalls)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.groups.isEmpty())
    }

    private fun word(
        cardId: String,
        lemma: String,
        status: WordBookRecordStatus = WordBookRecordStatus.Unlearned,
    ): WordBookRecord {
        val card = WordCard(cardId, "book", lemma, "", "", "释义")
        return WordBookRecord(card, cardId, lemma, status)
    }

    private class FakeLearningRecordRepository : LearningRecordRepository {
        var todayCalls = 0
        var historyCalls = 0
        var allWordsCalls = 0
        var allWordsResult: List<WordBookRecord> = emptyList()
        val allWordsResults = ArrayDeque<RepositoryResult<List<WordBookRecord>>>()
        override suspend fun today(profileId: String, planId: String): RepositoryResult<List<LearningRecord>> {
            todayCalls++
            return RepositoryResult.Success(emptyList())
        }
        override suspend fun history(profileId: String): RepositoryResult<List<LearningHistoryGroup>> {
            historyCalls++
            return RepositoryResult.Success(emptyList())
        }
        override suspend fun allWords(wordBookId: String, now: Instant): RepositoryResult<List<WordBookRecord>> {
            allWordsCalls++
            allWordsResults.removeFirstOrNull()?.let { return it }
            return RepositoryResult.Success(allWordsResult)
        }
    }
}
