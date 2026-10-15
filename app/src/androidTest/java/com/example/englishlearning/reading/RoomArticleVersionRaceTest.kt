package com.example.englishlearning.reading

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SP-B 决策「saveNewVersion 并发竞态」（F5-02 性能与可靠性）真库口径（androidTest）：
 * 同一组键（profileId + localDate + wordBook + type + tier）下多篇并发生成时，
 * 「先读 maxVersion 再 insert」的非事务写法会让多个协程算出同一个 version，
 * 第二个插入撞 index_articles_reuse_key 的 UNIQUE 约束——用户侧表现为随机丢文。
 * 这里钉并发契约：N 路并发全部成功，且版本号恰为 1..N 连续无重复。
 */
@RunWith(AndroidJUnit4::class)
class RoomArticleVersionRaceTest {
    @Test
    fun twoConcurrentGeneratorsGetDistinctVersions(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "article-version-race-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
        try {
            val repository = RoomArticleRepository(database, Dispatchers.Unconfined)
            val generators = 8

            val results = coroutineScope {
                (1..generators).map { index ->
                    async { repository.saveNewVersion(article("race-$index")) }
                }.awaitAll()
            }

            results.forEachIndexed { index, result ->
                assertTrue("第 ${index + 1} 路并发保存应成功，实际失败：${result.exceptionOrNull()}", result.isSuccess)
            }
            val versions = results.map { it.getOrThrow().version }
            assertEquals(
                "并发版本号应为 1..$generators 连续且无重复",
                (1..generators).toSet(),
                versions.toSet(),
            )

            // 落库核验：组内恰有 generators 篇，版本各一。
            val stored = database.internalArticleDao().findHistory("p1")
            assertEquals(generators, stored.size)
            assertEquals((1..generators).toSet(), stored.map { it.version }.toSet())
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(name)
        }
    }

    private fun article(articleId: String) = Article(
        articleId = articleId, profileId = "p1", localDate = "2026-09-22", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = 0,
        title = "A local story", englishText = "text", chineseText = "译文",
        generatedAtEpochMillis = 1L, coveredLemmas = emptyList(),
        source = ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
    )
}
