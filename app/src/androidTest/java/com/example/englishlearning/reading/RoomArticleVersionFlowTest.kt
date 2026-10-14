package com.example.englishlearning.reading

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.ArticleEntity
import com.example.englishlearning.core.storage.entity.ReadingCompletionEntity
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F4-01 决策 §8 真库流程（androidTest）：JVM mockk 只能钉端口接线，这里钉 SQL 本身——
 * 「删不空」拒删同组唯一版本、删除连带清掉该版的阅读完成记录、同组其他版本与
 * 其他 profile 的数据零触碰。
 */
@RunWith(AndroidJUnit4::class)
class RoomArticleVersionFlowTest {
    @Test
    fun deleteVersionRefusesLastKeepsGroupHealthyAndCascadesCompletions(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "article-version-flow-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
        try {
            val dao = database.internalArticleDao()
            val completions = database.internalReadingCompletionDao()
            val repository = RoomArticleVersionRepository(database, Dispatchers.Unconfined)

            dao.insert(entity("v1", version = 1))
            dao.insert(entity("v2", version = 2))
            completions.insert(ReadingCompletionEntity("v1", profileId = "p1", localDate = "2026-09-22", completedAtEpochMillis = 1L))
            completions.insert(ReadingCompletionEntity("v2", profileId = "p1", localDate = "2026-09-22", completedAtEpochMillis = 2L))
            // 另一 profile 的同 id 不可能（articleId 主键全局唯一），改钉「另一篇文章」零触碰。
            completions.insert(ReadingCompletionEntity("other", profileId = "p1", localDate = "2026-09-23", completedAtEpochMillis = 3L))

            val impact = repository.deletionImpact(article("v1", version = 1)).getOrThrow()
            assertEquals(1, impact.removedCompletionCount)
            assertEquals("删 v1 后组里应只剩 v2", 1, impact.remainingVersionCount)
            assertEquals("articles 与资产表无关联，恒 0 是诚实口径", 0, impact.audioAssetCount)

            val outcome = repository.deleteVersion(article("v1", version = 1)).getOrThrow()
            assertTrue("应返回 Deleted：$outcome", outcome is VersionDeleteOutcome.Deleted)
            assertEquals(1, (outcome as VersionDeleteOutcome.Deleted).impact.removedCompletionCount)
            assertEquals(1, outcome.impact.remainingVersionCount)

            // 级联核验：v1 文章与其当日完成记录消失（当天只剩 v2 一条），别篇完成记录零触碰。
            assertEquals(listOf("v2"), dao.findHistory("p1").map { it.articleId })
            assertEquals("只连带清该版的当日完成记录", 1, completions.countForDate("p1", "2026-09-22"))
            assertEquals("别的篇目的完成记录不许动", 1, completions.countForDate("p1", "2026-09-23"))

            // 「删不空」：组里只剩 v2 后，再删 v2 必须被拒——历史至少留一版。
            val refused = repository.deleteVersion(article("v2", version = 2)).getOrThrow()
            assertEquals(VersionDeleteOutcome.RefusedLastVersion, refused)
            assertEquals(listOf("v2"), dao.findHistory("p1").map { it.articleId })
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(name)
        }
    }

    private fun entity(articleId: String, version: Int) = ArticleEntity(
        articleId = articleId, profileId = "p1", localDate = "2026-09-22", activeWordBookId = "cet4",
        articleType = ArticleType.STORY.name, lengthTier = ArticleLengthTier.STANDARD.name, version = version,
        title = "A local story", englishText = "text", chineseText = "译文", generatedAtEpochMillis = version.toLong(),
        coveredLemmas = "[]", parameterSummary = "", modelName = null, sourceType = "AI_GENERATED",
        sourceId = "", sourceDisplayName = "", sourceUrl = "", sourceLicenseNote = "", sourceAttribution = "",
    )

    private fun article(articleId: String, version: Int) = Article(
        articleId = articleId, profileId = "p1", localDate = "2026-09-22", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = version,
        title = "A local story", englishText = "text", chineseText = "译文",
        generatedAtEpochMillis = version.toLong(), coveredLemmas = emptyList(),
        source = ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
    )
}
