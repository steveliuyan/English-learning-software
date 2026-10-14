package com.example.englishlearning.learning

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.dao.InternalFavoriteWordDao
import com.example.englishlearning.core.storage.entity.FavoriteWordEntity
import com.example.englishlearning.core.time.FixedClockProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/**
 * F4-01 决策⑦收藏仓储：JVM 侧用 mockk 钉仓储接线与语义
 * （toggle 的加/撤分支、addedAt 取时钟、失败映射），真库 SQL 行为由
 * androidTest `RoomFavoritesFlowTest` 在同一组用例上兜底。
 */
class RoomFavoriteWordRepositoryTest {
    private val addedAt = Instant.parse("2026-10-14T02:00:00Z")

    @Test
    fun `toggle inserts with clock timestamp when card is not favorited yet`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalFavoriteWordDao>()
        every { database.internalFavoriteWordDao() } returns dao
        coEvery { dao.isFavorite("default", "cet4:cat") } returns false
        val captured = slot<FavoriteWordEntity>()
        coEvery { dao.insertIgnore(capture(captured)) } returns 1L

        val result = repository(database).toggle("default", "cet4:cat", "cet4", "cat")

        assertEquals(Unit, result.getOrThrow())
        assertEquals(
            FavoriteWordEntity("default", "cet4:cat", "cet4", "cat", addedAt.toEpochMilli()),
            captured.captured,
        )
        coVerify(exactly = 0) { dao.delete(any(), any()) }
    }

    @Test
    fun `toggle deletes when card is already favorited`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalFavoriteWordDao>()
        every { database.internalFavoriteWordDao() } returns dao
        coEvery { dao.isFavorite("default", "cet4:cat") } returns true
        coEvery { dao.delete("default", "cet4:cat") } returns Unit

        assertEquals(Unit, repository(database).toggle("default", "cet4:cat", "cet4", "cat").getOrThrow())

        coVerify(exactly = 0) { dao.insertIgnore(any()) }
    }

    @Test
    fun `isFavorite exposes the dao answer`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalFavoriteWordDao>()
        every { database.internalFavoriteWordDao() } returns dao
        coEvery { dao.isFavorite("default", "cet4:cat") } returns true

        assertTrue(repository(database).isFavorite("default", "cet4:cat").getOrThrow())
    }

    @Test
    fun `listFavorites maps entities preserving dao order`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalFavoriteWordDao>()
        every { database.internalFavoriteWordDao() } returns dao
        // DAO 查询已按 addedAtEpochMillis 降序；仓储保持顺序不重排。
        coEvery { dao.listForProfile("default") } returns listOf(
            FavoriteWordEntity("default", "cet4:cat", "cet4", "cat", 200L),
            FavoriteWordEntity("default", "cet4:dog", "cet4", "dog", 100L),
        )

        val favorites = repository(database).listFavorites("default").getOrThrow()

        assertEquals(listOf("cet4:cat", "cet4:dog"), favorites.map { it.cardId })
        assertEquals(Instant.ofEpochMilli(200L), favorites.first().addedAt)
        assertEquals("cat", favorites.first().lemma)
    }

    @Test
    fun `storage exception maps to storage unavailable failure`() = runTest {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalFavoriteWordDao>()
        every { database.internalFavoriteWordDao() } returns dao
        coEvery { dao.listForProfile("default") } throws IllegalStateException("boom")

        val result = repository(database).listFavorites("default")

        assertFalse(result.isSuccess)
        // 与 RoomLearningStatsRepository 同一映射：底层异常收敛为存储不可用，不外泄原始信息。
        assertIs<com.example.englishlearning.core.storage.AppErrorException>(result.exceptionOrNull())
    }

    private fun repository(database: AppDatabase) = RoomFavoriteWordRepository(
        database,
        FixedClockProvider(addedAt, ZoneId.of("UTC")),
        Dispatchers.Unconfined,
    )
}
