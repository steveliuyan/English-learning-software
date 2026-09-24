package com.example.englishlearning.reading

import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FetchArticleUseCaseTest {
    private class FakeTransport : AiHttpTransport {
        var callCount = 0
        var lastRequest: AiHttpRequest? = null
        val responses = ArrayDeque<AiHttpResult>()
        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            callCount++
            lastRequest = request
            return responses.removeFirstOrNull() ?: error("no scripted response for call #$callCount")
        }
    }

    private class FakeArticleRepository : ArticleRepository {
        val stored = mutableListOf<Article>()
        override suspend fun saveNewVersion(article: Article): Result<Article> {
            val saved = article.copy(version = stored.size + 1)
            stored += saved
            return Result.success(saved)
        }

        override suspend fun findLatest(profileId: String, localDate: String, activeWordBookId: String, articleType: ArticleType, lengthTier: ArticleLengthTier) = Result.success<Article?>(null)
        override suspend fun findHistory(profileId: String) = Result.success(stored.toList())

        override suspend fun findBySourceUrl(url: String): Result<Article?> =
            Result.success(stored.lastOrNull { (it.source as? ArticleSource.WebFetched)?.articleUrl == url })
    }

    private val feed = javaClass.getResourceAsStream("/voa/feed-zone1579-reduced.xml")!!.readBytes().decodeToString()
    private val page = javaClass.getResourceAsStream("/voa/article-7998765-reduced.html")!!.readBytes().decodeToString()

    private val transport = FakeTransport()
    private val articles = FakeArticleRepository()
    private val ids = ArticleIdFactory { "article-${articles.stored.size + 1}" }
    private val context = FetchContext(profileId = "p1", localDate = "2026-09-24", activeWordBookId = "cet4")

    private fun useCase() = FetchArticleUseCase(
        transport = transport,
        articles = articles,
        quality = ArticleQualityPolicy,
        ids = ids,
        clock = { java.time.Instant.ofEpochMilli(1_000L) },
    )

    private fun sampleItem(url: String = "https://learningenglish.voanews.com/a/wilbur-and-orville-wright-the-first-airplane/7998765.html") =
        FeedItem(title = "Wilbur and Orville Wright: The First Airplane", articleUrl = url, publishedAtEpochMillis = 1L, summary = "s")

    @Test
    fun listReturnsTheFeedItemsOfAWhitelistedSource() = runTest {
        transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, feed))

        val result = useCase().list("voa-learning-english")

        val listed = result as FetchArticleResult.Listed
        assertEquals("voa-learning-english", listed.sourceId)
        assertEquals(3, listed.items.size)
        assertEquals("Wilbur and Orville Wright: The First Airplane", listed.items[0].title)
        // 抓取请求必须是 GET、无凭据。
        assertEquals("GET", transport.lastRequest?.method)
        assertTrue(transport.lastRequest!!.headers.isEmpty())
    }

    @Test
    fun fetchRefusesAFeedItemWhoseUrlIsNotWhitelisted() = runTest {
        val result = useCase().fetch("voa-learning-english", sampleItem("https://evil.test/a/x.html"), context)

        assertEquals(FetchArticleResult.RejectedTarget(RejectedTargetReason.NotWhitelisted), result)
        assertEquals(0, transport.callCount) // 未通过白名单就绝不能发请求
    }

    @Test
    fun fetchRefusesAnHttpFeedItem() = runTest {
        val result = useCase().fetch("voa-learning-english", sampleItem("http://learningenglish.voanews.com/a/x.html"), context)

        assertEquals(FetchArticleResult.RejectedTarget(RejectedTargetReason.NotHttps), result)
        assertEquals(0, transport.callCount)
    }

    @Test
    fun fetchStoresAWebFetchedArticleWithAttribution() = runTest {
        transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, page))

        val result = useCase().fetch("voa-learning-english", sampleItem(), context)

        val fetched = result as FetchArticleResult.Fetched
        val source = fetched.article.source as ArticleSource.WebFetched
        assertEquals("voa-learning-english", source.sourceId)
        assertEquals("VOA Learning English", source.displayName)
        assertEquals(sampleItem().articleUrl, source.articleUrl)
        assertEquals("learningenglish.voanews.com", source.attributionText)
        assertTrue(source.licenseNote.contains("公有领域"))
        assertEquals(1, articles.stored.size)
    }

    @Test
    fun fetchDoesNotStoreChineseText() = runTest {
        transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, page))

        val fetched = useCase().fetch("voa-learning-english", sampleItem(), context) as FetchArticleResult.Fetched

        // 抓取来源没有译文，也不伪造：空串 + 阅读页提示（Task E），绝不能机器凑一篇中文。
        assertEquals("", fetched.article.chineseText)
    }

    @Test
    fun fetchRejectsABodyThatFailsQuality() = runTest {
        val tiny = "<html><head><title>Too short</title></head><body><div class=\"wsw\"><p>way too short</p></div></body></html>"
        transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, tiny))

        val result = useCase().fetch("voa-learning-english", sampleItem(), context)

        assertEquals(FetchArticleResult.Failed(FetchFailure.QualityRejected), result)
        assertTrue(articles.stored.isEmpty())
    }

    @Test
    fun fetchMapsANetworkFailureWithoutStoring() = runTest {
        transport.responses += AiHttpResult.NetworkUnavailable

        val result = useCase().fetch("voa-learning-english", sampleItem(), context)

        assertEquals(FetchArticleResult.Failed(FetchFailure.NetworkUnavailable), result)
        assertTrue(articles.stored.isEmpty())
    }

    @Test
    fun fetchSendsNoCredentials() = runTest {
        transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, page))

        useCase().fetch("voa-learning-english", sampleItem(), context)

        assertTrue(transport.lastRequest!!.headers.none { it.key.equals("Authorization", ignoreCase = true) })
    }

    @Test
    fun fetchNeverStoresTheApiKey() = runTest {
        transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, page))

        useCase().fetch("voa-learning-english", sampleItem(), context)

        // 抓取流程没有任何密钥输入，落库文章更不能带着密钥字样——这是防将来误拼的哨兵。
        val fetched = articles.stored.single()
        for (text in listOf(fetched.title, fetched.englishText, fetched.chineseText, fetched.source.toString())) {
            assertTrue(!text.contains("sk-"), "article must never contain a key fragment")
        }
    }

    @Test
    fun fetchReusesAnAlreadyStoredArticleForTheSameUrl() = runTest {
        val useCase = useCase()
        transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, page))
        val first = useCase.fetch("voa-learning-english", sampleItem(), context) as FetchArticleResult.Fetched
        assertEquals(1, transport.callCount)

        val second = useCase.fetch("voa-learning-english", sampleItem(), context)

        val reused = second as FetchArticleResult.Fetched
        assertEquals(first.article.articleId, reused.article.articleId)
        assertEquals(1, transport.callCount) // 同一 URL 第二次抓取不发请求
        assertEquals(1, articles.stored.size)
    }
}
