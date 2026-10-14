package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.dao.InternalLearningStatsDao
import com.example.englishlearning.core.storage.dao.WrongWordRow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/**
 * F4-01 决策⑤错词仓储（JVM）：mockk 钉接线——DAO 行按序映射为域类型、
 * 底层异常收敛为存储不可用；派生 SQL 语义由 androidTest
 * `RoomWrongWordsDerivationTest` 在真库兜底。
 */
class RoomWrongWordsRepositoryTest {
    @Test
    fun `wrongWords maps dao rows preserving derived order`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalLearningStatsDao>()
        every { database.internalLearningStatsDao() } returns dao
        coEvery { dao.wrongWordRows("default") } returns listOf(
            WrongWordRow("cet4:run", "cet4", 100L, 50L),
            WrongWordRow("cet4:cat", "cet4", 500L, null),
        )

        val result = repository(database).wrongWords("default")

        val rows = result.getOrThrow()
        assertEquals(listOf("cet4:run", "cet4:cat"), rows.map { it.cardId })
        assertEquals(50L, rows.first().nextReviewAtEpochMillis)
        assertEquals(null, rows.last().nextReviewAtEpochMillis)
        assertEquals(500L, rows.last().lastFeedbackAtEpochMillis)
    }

    @Test
    fun `storage exception maps to storage unavailable failure`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalLearningStatsDao>()
        every { database.internalLearningStatsDao() } returns dao
        coEvery { dao.wrongWordRows("default") } throws IllegalStateException("boom")

        val result = repository(database).wrongWords("default")

        assertFalse(result.isSuccess)
        assertIs<AppErrorException>(result.exceptionOrNull())
    }

    private fun repository(database: AppDatabase) =
        RoomWrongWordsRepository(database, Dispatchers.Unconfined)
}
