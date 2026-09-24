package com.example.englishlearning.reading

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
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

    @Test
    fun everySourceBranchSurvivesARoundTripThroughStorage() = runTest {
        val sources = listOf(
            ArticleSource.AiGenerated(modelName = "gpt-x", parameterSummary = "model=gpt-x temperature=0.4"),
            ArticleSource.WebFetched(
                sourceId = "voa-learning-english",
                displayName = "VOA Learning English",
                articleUrl = "https://learningenglish.voanews.com/a/7998765.html",
                licenseNote = "Public domain",
                attributionText = "learningenglish.voanews.com",
            ),
            ArticleSource.UserImported,
        )

        val readBack = sources.mapIndexed { index, source ->
            val id = "round-trip-$index"
            repository.saveNewVersion(article(articleId = id, source = source)).getOrThrow()
            repository.findHistory("p1").getOrThrow().single { it.articleId == id }.source
        }

        // 三个分支都要原样回来：少一个字段、错一个分支，署名条件就可能在读取路径上被悄悄丢掉。
        assertEquals(sources, readBack)
    }

    @Test
    fun findsAStoredArticleByItsSourceUrl() = runTest {
        val url = "https://learningenglish.voanews.com/a/7998765.html"
        repository.saveNewVersion(
            article(
                source = ArticleSource.WebFetched(
                    sourceId = "voa-learning-english",
                    displayName = "VOA Learning English",
                    articleUrl = url,
                    licenseNote = "Public domain",
                    attributionText = "learningenglish.voanews.com",
                ),
            ),
        ).getOrThrow()

        assertEquals("a1", repository.findBySourceUrl(url).getOrThrow()?.articleId)
        assertEquals(null, repository.findBySourceUrl("https://learningenglish.voanews.com/a/other.html").getOrThrow())
    }

    @Test
    fun unknownSourceTypeFailsTheReadInsteadOfBecomingUserImported() = runTest {
        // 'MYSTERY' 不是三个已知分支中的任何一个。把它读成 UserImported 会凭空抹掉抓取来源
        // 必须展示的署名与许可说明，因此整体失败才是唯一诚实的处置。
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO articles (articleId, profileId, localDate, activeWordBookId, articleType, lengthTier, " +
                "version, title, englishText, chineseText, generatedAtEpochMillis, coveredLemmas, " +
                "parameterSummary, modelName, sourceType, sourceId, sourceDisplayName, sourceUrl, " +
                "sourceLicenseNote, sourceAttribution) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                "a1", "p1", "2026-09-22", "cet4", "STORY", "STANDARD", 1,
                "A local story", "English body", "中文正文", 1L, "[]", "", null,
                "MYSTERY", "", "", "", "", "",
            ),
        )

        assertTrue(repository.findHistory("p1").isFailure)
    }

    private fun article(
        articleId: String = "a1",
        profileId: String = "p1",
        title: String = "A local story",
        englishText: String = "A short story about learning.",
        chineseText: String = "一篇关于学习的短文。",
        source: ArticleSource = ArticleSource.AiGenerated(
            modelName = "test-model",
            parameterSummary = "model=test-model temperature=0.7",
        ),
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
        source = source,
    )
}
