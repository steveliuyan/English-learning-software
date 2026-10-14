package com.example.englishlearning.learning

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.entity.ArticleEntity
import com.example.englishlearning.core.storage.entity.LearningEventEntity
import com.example.englishlearning.core.storage.entity.VocabularyEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F4-01 Task10b 真库计数（androidTest）：确认框文案里的三个数字必须来自真 SQL——
 * 词条按册（跨 profile，与 deleteForWordBook 同口径）、学习事件与文章按「profile+册」
 * 且**只数保留面**；别册、别 profile 的数据不得混进计数。
 */
@RunWith(AndroidJUnit4::class)
class RoomWordBookDeletionPreviewTest {
    @Test
    fun previewCountsEntriesEventsAndArticlesScopedToTheBook(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "wordbook-preview-${System.nanoTime()}.db"
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(AppDatabase.CONSTRAINT_CALLBACK)
            .build()
        try {
            val entries = database.internalVocabularyEntryDao()
            entries.upsert(VocabularyEntryEntity("p1", "my-pack", "my-pack:cat", 1L, "GOOD"))
            entries.upsert(VocabularyEntryEntity("p1", "my-pack", "my-pack:run", 2L, "AGAIN"))
            entries.upsert(VocabularyEntryEntity("p1", "other-pack", "other-pack:dog", 3L, "GOOD"))

            val events = database.internalLearningEventDao()
            events.insertEventIfAbsent(event("e1", "p1", "my-pack"))
            events.insertEventIfAbsent(event("e2", "p1", "my-pack"))
            events.insertEventIfAbsent(event("e3", "p2", "my-pack"))
            events.insertEventIfAbsent(event("e4", "p1", "other-pack"))

            // articles 走仓储正门（saveNewVersion 自派 version，字段口径与生产一致）。
            val articles = com.example.englishlearning.reading.RoomArticleRepository(database, Dispatchers.Unconfined)
            articles.saveNewVersion(article("a1", "p1", "my-pack")).getOrThrow()
            articles.saveNewVersion(article("a2", "p1", "my-pack", title = "B")).getOrThrow()
            articles.saveNewVersion(article("a3", "p1", "other-pack")).getOrThrow()
            articles.saveNewVersion(article("a4", "p2", "my-pack")).getOrThrow()

            val preview = RoomWordBookDeletionPreviewRepository(database, Dispatchers.Unconfined)
                .preview(profileId = "p1", wordBookId = "my-pack")
                .getOrThrow()

            assertEquals(2, preview.entryCount)
            assertEquals(2, preview.keptEventCount)
            assertEquals(2, preview.keptArticleCount)
        } finally {
            if (database.isOpen) database.close()
            context.deleteDatabase(name)
        }
    }

    private fun event(eventId: String, profileId: String, wordBookId: String) = LearningEventEntity(
        eventId = eventId, profileId = profileId, planId = "plan-$profileId",
        cardId = "$wordBookId:cat", wordBookId = wordBookId, feedback = "GOOD",
        occurredAtEpochMillis = 1L, algorithmVersion = "fsrs-1", paramsVersion = "p-1",
        dueBeforeEpochMillis = null, nextReviewAtEpochMillis = 2L,
    )

    private fun article(articleId: String, profileId: String, wordBookId: String, title: String = "A") =
        com.example.englishlearning.reading.domain.Article(
            articleId = articleId, profileId = profileId, localDate = "2026-09-22", activeWordBookId = wordBookId,
            articleType = com.example.englishlearning.reading.domain.ArticleType.STORY,
            lengthTier = com.example.englishlearning.reading.domain.ArticleLengthTier.STANDARD,
            version = 1, title = title, englishText = "text", chineseText = "译文",
            generatedAtEpochMillis = 1L, coveredLemmas = emptyList(),
            source = com.example.englishlearning.reading.domain.ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
        )
}
