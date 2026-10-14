package com.example.englishlearning.reading

import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.storage.dao.InternalArticleDao
import com.example.englishlearning.core.storage.dao.VersionDeleteReceipt
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/**
 * F4-01 决策 §8「看得见、删得掉、删不空」仓储接线（JVM）：mockk 钉端口↔DAO 映射——
 * 影响预览的计数口径、拒删唯一版本的 null 语义、异常收敛为存储不可用；
 * @Transaction 真语义由 androidTest `RoomArticleVersionFlowTest` 在真库兜底。
 */
class RoomArticleVersionRepositoryTest {
    @Test
    fun `deletionImpact counts completions and remaining versions with honest zero audio`() = runTest {
        val database = mockDatabase()
        val dao = daoOf(database)
        coEvery { dao.countCompletionsForArticle("a1") } returns 1
        coEvery { dao.countInGroup("p1", "2026-09-22", "cet4", "STORY", "STANDARD") } returns 3

        val impact = repository(database).deletionImpact(article()).getOrThrow()

        assertEquals(1, impact.removedCompletionCount)
        assertEquals(2, impact.remainingVersionCount)
        assertEquals(0, impact.audioAssetCount, "articles 与 asset_records 无 schema 关联，恒 0 才是诚实口径")
    }

    @Test
    fun `deleteVersion maps a null receipt to refusing the only version`() = runTest {
        val database = mockDatabase()
        val dao = daoOf(database)
        coEvery { dao.deleteVersionIfNotLast("p1", "2026-09-22", "cet4", "STORY", "STANDARD", "a1") } returns null

        val outcome = repository(database).deleteVersion(article()).getOrThrow()

        assertEquals(VersionDeleteOutcome.RefusedLastVersion, outcome)
    }

    @Test
    fun `deleteVersion maps the receipt to a deleted outcome carrying its impact`() = runTest {
        val database = mockDatabase()
        val dao = daoOf(database)
        coEvery { dao.deleteVersionIfNotLast("p1", "2026-09-22", "cet4", "STORY", "STANDARD", "a1") } returns
            VersionDeleteReceipt(removedCompletionCount = 1, remainingVersionCount = 2)

        val outcome = repository(database).deleteVersion(article()).getOrThrow()

        assertIs<VersionDeleteOutcome.Deleted>(outcome)
        assertEquals(ArticleDeletionImpact(removedCompletionCount = 1, remainingVersionCount = 2, audioAssetCount = 0), outcome.impact)
    }

    @Test
    fun `storage exception maps to storage unavailable failure on both ports`() = runTest {
        val impactDatabase = mockDatabase()
        coEvery { daoOf(impactDatabase).countCompletionsForArticle(any()) } throws IllegalStateException("boom")
        assertIs<AppErrorException>(repository(impactDatabase).deletionImpact(article()).exceptionOrNull())

        val deleteDatabase = mockDatabase()
        coEvery { daoOf(deleteDatabase).deleteVersionIfNotLast(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("boom")
        assertIs<AppErrorException>(repository(deleteDatabase).deleteVersion(article()).exceptionOrNull())
    }

    private fun mockDatabase(): AppDatabase {
        val database = mockk<AppDatabase>()
        val dao = mockk<InternalArticleDao>()
        every { database.internalArticleDao() } returns dao
        return database
    }

    private fun daoOf(database: AppDatabase): InternalArticleDao = database.internalArticleDao()

    private fun repository(database: AppDatabase) =
        RoomArticleVersionRepository(database, Dispatchers.Unconfined)

    private fun article() = Article(
        articleId = "a1", profileId = "p1", localDate = "2026-09-22", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = 2,
        title = "A local story", englishText = "text", chineseText = "译文",
        generatedAtEpochMillis = 1L, coveredLemmas = emptyList(),
        source = ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
    )
}
