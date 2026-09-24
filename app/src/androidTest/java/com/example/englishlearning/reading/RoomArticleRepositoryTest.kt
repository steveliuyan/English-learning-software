package com.example.englishlearning.reading

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleType
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class RoomArticleRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ArticleRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomArticleRepository(database, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun firstSaveAssignsVersionOne() = runTest {
        val saved = repository.saveNewVersion(article()).getOrThrow()

        assertEquals(1, saved.version)
    }

    @Test
    fun secondSaveForSameReuseKeyAssignsNextVersionAndLatestIsReadable() = runTest {
        repository.saveNewVersion(article(articleId = "a1")).getOrThrow()
        val second = repository.saveNewVersion(article(articleId = "a2", title = "Second")).getOrThrow()

        assertEquals(2, second.version)
        assertEquals("Second", repository.findLatest("p1", "2026-09-22", "cet4", ArticleType.STORY, ArticleLengthTier.STANDARD).getOrThrow()?.title)
        assertEquals(listOf(2, 1), repository.findHistory("p1").getOrThrow().map { it.version })
    }

    @Test
    fun historyIsScopedByProfile() = runTest {
        repository.saveNewVersion(article(profileId = "p1")).getOrThrow()
        repository.saveNewVersion(article(profileId = "p2", articleId = "p2-a")).getOrThrow()

        assertEquals(1, repository.findHistory("p1").getOrThrow().size)
        assertEquals(1, repository.findHistory("p2").getOrThrow().size)
    }

    @Test
    fun blankOrUnsafeArticleIsRejectedBeforeWrite() = runTest {
        val blank = repository.saveNewVersion(article(title = " "))
        val unsafe = repository.saveNewVersion(article(articleId = "unsafe", englishText = "<script>alert(1)</script>"))

        assertTrue(blank.isFailure)
        assertTrue(unsafe.isFailure)
        assertTrue(repository.findHistory("p1").getOrThrow().isEmpty())
    }

    private fun article(
        articleId: String = "a1",
        profileId: String = "p1",
        title: String = "A local story",
        englishText: String = "A short story about learning.",
        chineseText: String = "一篇关于学习的短文。",
    ) = Article(
        articleId = articleId,
        profileId = profileId,
        localDate = "2026-09-22",
        activeWordBookId = "cet4",
        articleType = ArticleType.STORY,
        lengthTier = ArticleLengthTier.STANDARD,
        version = 99,
        title = title,
        englishText = englishText,
        chineseText = chineseText,
        generatedAtEpochMillis = 1L,
        coveredLemmas = listOf("story", "learning"),
        parameterSummary = "model=test-model temperature=0.7",
        modelName = "test-model",
    )
}
