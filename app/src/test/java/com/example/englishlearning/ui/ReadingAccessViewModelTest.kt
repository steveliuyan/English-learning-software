package com.example.englishlearning.ui

import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpResponse
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.ArticleIdFactory
import com.example.englishlearning.reading.FetchArticleUseCase
import com.example.englishlearning.reading.FetchFailure
import com.example.englishlearning.reading.FeedItem
import com.example.englishlearning.reading.GenerateArticleUseCase
import com.example.englishlearning.reading.ImportArticleResult
import com.example.englishlearning.reading.ImportArticleUseCase
import com.example.englishlearning.reading.ImportRejection
import com.example.englishlearning.reading.ArticleRepository
import com.example.englishlearning.reading.ReadingPreferenceRepository
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingAccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    // ---------- 既有行为：锁定 / 偏好 / 历史 ----------

    @Test
    fun lockedProfileShowsReasonWithoutExposingArticleChoices() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val viewModel = harness().viewModel

        viewModel.load("p1", isUnlocked = false, unlockReason = "还差 2 个新词")
        advanceUntilIdle()

        assertEquals(ReadingAccessUiState.Locked("还差 2 个新词"), viewModel.uiState.value)
    }

    @Test
    fun selectingArticleTypePersistsUpdatedPreference() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()
        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()

        h.viewModel.selectType(ArticleType.WORKPLACE)
        advanceUntilIdle()

        assertEquals(ArticleType.WORKPLACE, h.preferences.saved?.defaultArticleType)
    }

    @Test
    fun unlockedProfileLoadsPreferenceAndOfflineHistoryCount() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val preference = ReadingPreference("p1", ArticleType.SCIENCE, ArticleLengthTier.LONG)
        val h = harness(
            preference = preference,
            history = listOf(article("a1"), article("a2")),
        )

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()

        assertEquals(
            ReadingAccessUiState.Ready(preference, listOf(article("a1"), article("a2"))),
            viewModelReady(h),
        )
    }

    // ---------- 今日文章（读按钮） ----------

    @Test
    fun existingTodayArticleIsExposedForTheReadButton() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val existing = existingTodayArticle(coveredLemmas = listOf("apple"))
        val h = harness(history = listOf(existing))

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()

        assertEquals(existing, viewModelReady(h).todayArticle)
    }

    // ---------- AI 生成：出站确认闸 / 成功 / 拒绝 / 失败 / 换一篇 ----------

    @Test
    fun generateWithoutConfirmationStopsBeforeAnyRequest() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.generate()
        advanceUntilIdle()

        val generation = viewModelReady(h).generation
        assertEquals(GenerationUiState.NeedsConfirmation("api.test"), generation)
        assertEquals(0, h.transport.callCount) // 确认之前一个字节都不许出去
    }

    @Test
    fun confirmingOutboundGeneratesAndOpensTheArticle() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()
        h.transport.responses += AiHttpResult.Responded(AiHttpResponse(200, chatBody(validContent)))

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.generate()
        advanceUntilIdle()
        h.viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        val target = h.viewModel.readingTarget.value
        assertTrue(target != null, "confirmed generation must open the article")
        assertEquals(listOf("apple", "garden"), target.article.coveredLemmas)
        assertEquals(listOf("apple", "garden"), target.cards.map { it.lemma })
        assertEquals(1, h.transport.callCount)
        assertEquals(GenerationUiState.Idle, viewModelReady(h).generation)
    }

    @Test
    fun decliningConfirmationReturnsToIdleWithoutAnyRequest() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.generate()
        advanceUntilIdle()
        h.viewModel.confirmOutbound(confirmed = false)
        advanceUntilIdle()

        assertEquals(GenerationUiState.Idle, viewModelReady(h).generation)
        assertEquals(0, h.transport.callCount)
        assertNull(h.viewModel.readingTarget.value)
    }

    @Test
    fun failedGenerationShowsBannerAndKeepsTodayPlanUntouched() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()
        h.transport.responses += AiHttpResult.Responded(AiHttpResponse(401, "{\"error\":\"denied\"}"))

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.generate()
        advanceUntilIdle()
        h.viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        assertEquals(GenerationUiState.Failed(AiFailure.Unauthorized), viewModelReady(h).generation)
        assertNull(h.viewModel.readingTarget.value)
        assertTrue(h.articles.stored.isEmpty()) // 失败不落库
        assertEquals(0, h.plans.saveIfAbsentCalls) // AC2-10：失败的生成不得碰今日计划
    }

    @Test
    fun regeneratePassesPreviousCoveredLemmasAsExcluded() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val existing = existingTodayArticle(coveredLemmas = listOf("orchard"))
        val h = harness(history = listOf(existing))
        h.transport.responses += AiHttpResult.Responded(AiHttpResponse(200, chatBody(validContent)))

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.generate(regenerate = true)
        advanceUntilIdle()
        h.viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        val body = h.transport.lastRequest!!.body
        assertTrue(body.contains("orchard"), "regeneration must tell the model to avoid previously covered lemmas")
        assertTrue(h.viewModel.readingTarget.value != null)
    }

    // ---------- 粘贴导入 ----------

    @Test
    fun importFlowOpensTheImportedArticle() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.importTitleChange("My Flight Notes")
        h.viewModel.importBodyChange(englishBody())
        h.viewModel.submitImport()
        advanceUntilIdle()

        val target = h.viewModel.readingTarget.value
        assertTrue(target != null, "a well-formed import must open the article")
        assertEquals("My Flight Notes", target.article.title)
        assertEquals(ArticleSource.UserImported, target.article.source)
        assertEquals(1, h.articles.stored.size)
        // 导入无出站：整个流程不允许发任何请求。
        assertEquals(0, h.transport.callCount)
    }

    @Test
    fun importRejectionShowsTheSpecificReason() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.importTitleChange("Too Short")
        h.viewModel.importBodyChange("only three words here")
        h.viewModel.submitImport()
        advanceUntilIdle()

        assertEquals(ImportRejection.BodyTooShort, viewModelReady(h).import.rejection)
        assertNull(h.viewModel.readingTarget.value)
        assertTrue(h.articles.stored.isEmpty()) // 被拒绝的文本不得写入
    }

    // ---------- 外刊：列表 / 空态 / 不可达 / 抓取 / 抓取失败 ----------

    @Test
    fun feedListingLoadsTheItems() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()
        h.transport.responses += AiHttpResult.Responded(AiHttpResponse(200, feedXml))

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.openFeed()
        advanceUntilIdle()

        val feed = viewModelReady(h).feed
        assertTrue(feed is FeedUiState.Ready, "feed must list items, got $feed")
        assertEquals("VOA Learning English", feed.sourceDisplayName)
        assertEquals(3, feed.items.size) // 真实缩减样本：4 个 item，缺 link 的 1 个被跳过
        assertEquals(1, h.transport.callCount)
    }

    @Test
    fun emptyFeedShowsExplicitEmptyState() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()
        h.transport.responses += AiHttpResult.Responded(AiHttpResponse(200, "<rss><channel><title>VOA</title></channel></rss>"))

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.openFeed()
        advanceUntilIdle()

        assertEquals(FeedUiState.Empty, viewModelReady(h).feed)
    }

    @Test
    fun unreachableFeedShowsExplicitError() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()
        h.transport.responses += AiHttpResult.NetworkUnavailable

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.openFeed()
        advanceUntilIdle()

        assertEquals(FeedUiState.Unreachable, viewModelReady(h).feed)
        assertNull(h.viewModel.readingTarget.value)
    }

    @Test
    fun fetchingAFeedItemOpensTheArticle() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()
        h.transport.responses += AiHttpResult.Responded(AiHttpResponse(200, feedXml))
        h.transport.responses += AiHttpResult.Responded(AiHttpResponse(200, articleHtml))

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.openFeed()
        advanceUntilIdle()
        val item = (viewModelReady(h).feed as FeedUiState.Ready).items.first()
        h.viewModel.fetchFeedItem(item)
        advanceUntilIdle()

        val target = h.viewModel.readingTarget.value
        assertTrue(target != null, "fetching a feed item must open the article")
        assertTrue(target.article.source is ArticleSource.WebFetched)
        assertEquals("p1", target.article.profileId)
        assertEquals("2026-09-25", target.article.localDate)
        assertEquals(2, h.transport.callCount)
    }

    @Test
    fun fetchFailureShowsExplicitErrorWithoutOpening() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()
        h.transport.responses += AiHttpResult.Responded(AiHttpResponse(200, feedXml))
        h.transport.responses += AiHttpResult.TimedOut

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.openFeed()
        advanceUntilIdle()
        val item = (viewModelReady(h).feed as FeedUiState.Ready).items.first()
        h.viewModel.fetchFeedItem(item)
        advanceUntilIdle()

        assertEquals(FeedUiState.FetchFailed(FetchFailure.SourceUnreachable), viewModelReady(h).feed)
        assertNull(h.viewModel.readingTarget.value)
    }

    @Test
    fun closeArticleClearsTheReadingTarget() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val h = harness()
        h.transport.responses += AiHttpResult.Responded(AiHttpResponse(200, feedXml))
        h.transport.responses += AiHttpResult.Responded(AiHttpResponse(200, articleHtml))

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.openFeed()
        advanceUntilIdle()
        val item = (viewModelReady(h).feed as FeedUiState.Ready).items.first()
        h.viewModel.fetchFeedItem(item)
        advanceUntilIdle()
        h.viewModel.closeArticle()
        advanceUntilIdle()

        assertNull(h.viewModel.readingTarget.value)
    }

    @Test
    fun openingTodaysArticleSetsTheReadingTarget() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        val existing = existingTodayArticle(coveredLemmas = listOf("apple"))
        val h = harness(history = listOf(existing))

        h.viewModel.load("p1", isUnlocked = true, unlockReason = "")
        advanceUntilIdle()
        h.viewModel.openTodayArticle()
        advanceUntilIdle()

        val target = h.viewModel.readingTarget.value
        assertTrue(target != null, "the read button must open today's article")
        assertEquals(existing.articleId, target.article.articleId)
        assertEquals(listOf("apple", "garden"), target.cards.map { it.lemma })
        assertEquals(0, h.transport.callCount) // 打开本地文章不出网
    }

    // ---------- 断言辅助 ----------

    private fun viewModelReady(h: Harness): ReadingAccessUiState.Ready =
        h.viewModel.uiState.value as? ReadingAccessUiState.Ready
            ?: error("expected Ready state, got ${h.viewModel.uiState.value}")

    // ---------- 夹具 ----------

    private val feedXml = javaClass.getResourceAsStream("/voa/feed-zone1579-reduced.xml")!!
        .readBytes().decodeToString()
    private val articleHtml = javaClass.getResourceAsStream("/voa/article-7998765-reduced.html")!!
        .readBytes().decodeToString()

    private companion object {
        const val TEST_KEY = "sk-test-secret-123"

        /** 解包后 content 里的内层 JSON；词数在 cet4/STANDARD 接受区间（162~330）内。 */
        val validContent: String = kotlinx.serialization.json.JsonObject(
            mapOf(
                "title" to kotlinx.serialization.json.JsonPrimitive("A Day"),
                "english" to kotlinx.serialization.json.JsonPrimitive("apple garden " + List(198) { "word" }.joinToString(" ")),
                "chinese" to kotlinx.serialization.json.JsonPrimitive("这是一段中文译文。".repeat(5)),
            ),
        ).toString()

        fun chatBody(content: String): String = kotlinx.serialization.json.JsonObject(
            mapOf(
                "choices" to kotlinx.serialization.json.JsonArray(
                    listOf(
                        kotlinx.serialization.json.JsonObject(
                            mapOf("message" to kotlinx.serialization.json.JsonObject(mapOf("content" to kotlinx.serialization.json.JsonPrimitive(content)))),
                        ),
                    ),
                ),
            ),
        ).toString()

        fun profile() = AiProfile(
            profileId = "p1",
            displayName = "Test Provider",
            websiteUrl = "https://example.com",
            endpoint = "https://api.test/v1",
            model = "gpt-x",
            capabilities = setOf(AiCapability.Text),
            secretReference = SecretReference("ai-profile-p1"),
        )

        fun todayPlan() = TodayPlan(
            planId = "plan-1",
            profileId = "p1",
            localDate = LocalDate.parse("2026-09-25"),
            zoneId = "Asia/Shanghai",
            activeWordBookId = "cet4",
            newTarget = 5,
            dueTarget = 5,
            newCardIds = listOf("c1"),
            dueCardIds = listOf("c2"),
            ruleVersion = "v1",
            generatedAt = Instant.EPOCH,
        )
    }

    /** 44 个词的英文正文，含今日完成词 apple 与 garden（复用导入用例的夹具标准）。 */
    private fun englishBody(): String = "Wilbur built the first airplane " + List(40) { "word" }.joinToString(" ")

    private fun article(id: String) = Article(
        articleId = id, profileId = "p1", localDate = "2026-09-22", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = 1,
        title = "title", englishText = "text", chineseText = "译文", generatedAtEpochMillis = 1,
        coveredLemmas = listOf("title"),
        source = ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
    )

    /** 与 load() 的「今日文章」复用键完全一致的文章：p1 / 2026-09-25 / cet4 / STORY / STANDARD。 */
    private fun existingTodayArticle(coveredLemmas: List<String>) = Article(
        articleId = "today-1", profileId = "p1", localDate = "2026-09-25", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = 1,
        title = "Today", englishText = List(200) { "word" }.joinToString(" "), chineseText = "译文",
        generatedAtEpochMillis = 1, coveredLemmas = coveredLemmas,
        source = ArticleSource.AiGenerated(modelName = "gpt-x", parameterSummary = ""),
    )

    // ---------- 假实现与 Harness ----------

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

    private open class FakeArticleRepository(history: List<Article> = emptyList()) : ArticleRepository {
        val stored = mutableListOf<Article>()
        private val seededHistory = history
        override suspend fun saveNewVersion(article: Article): Result<Article> {
            val saved = article.copy(version = stored.size + 1)
            stored += saved
            return Result.success(saved)
        }

        override suspend fun findLatest(profileId: String, localDate: String, activeWordBookId: String, articleType: ArticleType, lengthTier: ArticleLengthTier) = Result.success(
            (stored + seededHistory).lastOrNull {
                it.profileId == profileId && it.localDate == localDate && it.activeWordBookId == activeWordBookId &&
                    it.articleType == articleType && it.lengthTier == lengthTier
            },
        )

        override suspend fun findHistory(profileId: String) = Result.success((seededHistory + stored).filter { it.profileId == profileId })
        override suspend fun findBySourceUrl(url: String): Result<Article?> =
            Result.success((stored + seededHistory).lastOrNull { (it.source as? ArticleSource.WebFetched)?.articleUrl == url })
    }

    private class FakePreferenceRepository(private val preference: ReadingPreference = ReadingPreference("p1")) : ReadingPreferenceRepository {
        var saved: ReadingPreference? = null
        override suspend fun getPreference(profileId: String) = Result.success(preference)
        override suspend fun savePreference(preference: ReadingPreference): Result<Unit> {
            saved = preference
            return Result.success(Unit)
        }
    }

    private class FakePlans(private val plan: TodayPlan?) : TodayPlanRepository {
        var saveIfAbsentCalls = 0
        override suspend fun find(profileId: String, localDate: LocalDate): TodayPlanResult =
            plan?.takeIf { it.profileId == profileId && it.localDate == localDate }
                ?.let { TodayPlanResult.Ready(it) }
                ?: TodayPlanResult.NotFound

        override suspend fun findLatest(profileId: String): TodayPlanResult =
            plan?.let { TodayPlanResult.Ready(it) } ?: TodayPlanResult.NotFound

        override suspend fun saveIfAbsent(plan: TodayPlan): TodayPlanResult {
            saveIfAbsentCalls++
            return TodayPlanResult.Ready(plan)
        }
    }

    private class FakeEvents(private val completed: List<String>) : LearningEventRepository {
        override suspend fun append(event: LearningEvent, nextState: CardReviewState) =
            com.example.englishlearning.learning.AppendEventResult.Appended(false)
        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)
        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)
        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)
        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(completed)
        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(emptyList<String>())
        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    }

    private class FakeCards : WordCardSource {
        override suspend fun cardIds(wordBookId: String) = emptyList<String>()
        override suspend fun cards(cardIds: List<String>) = cardIds.mapNotNull { id ->
            when (id) {
                "c1" -> WordCard("c1", "cet4", "apple", "", "", "")
                "c2" -> WordCard("c2", "cet4", "garden", "", "", "")
                else -> null
            }
        }
    }

    private class FakeProfiles(private val profiles: List<AiProfile>) : AiProfileRepository {
        override suspend fun list() = Result.success(profiles)
        override suspend fun find(profileId: String) = Result.success(profiles.firstOrNull { it.profileId == profileId })
        override suspend fun save(profile: AiProfile) = Result.success(Unit)
        override suspend fun delete(profileId: String) = Result.success(Unit)
    }

    private class FakeSecretStore : SecretStore {
        val keys = mutableMapOf<String, CharArray>()
        fun put(alias: String, key: String) {
            keys[alias] = key.toCharArray()
        }

        override fun save(reference: SecretReference, secret: CharArray): Result<Unit> {
            keys[reference.alias] = secret.copyOf()
            return Result.success(Unit)
        }

        override fun read(reference: SecretReference): Result<CharArray> =
            keys[reference.alias]?.let { Result.success(it.copyOf()) }
                ?: Result.failure(AppErrorException(AppError.KeyStoreUnavailable))

        override fun delete(reference: SecretReference): Result<Unit> {
            keys.remove(reference.alias)
            return Result.success(Unit)
        }

        override fun has(reference: SecretReference): Result<Boolean> = Result.success(keys.containsKey(reference.alias))
    }

    private class Harness(
        preference: ReadingPreference = ReadingPreference("p1"),
        history: List<Article> = emptyList(),
    ) {
        val transport = FakeTransport()
        val articles = FakeArticleRepository(history)
        val preferences = FakePreferenceRepository(preference)
        val plans = FakePlans(todayPlan())
        val clock: ClockProvider = FixedClockProvider(
            Instant.parse("2026-09-25T04:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        )

        val viewModel = ReadingAccessViewModel(
            articles = articles,
            preferences = preferences,
            plans = plans,
            events = FakeEvents(completed = listOf("c1", "c2")),
            cardSource = FakeCards(),
            generateArticles = GenerateArticleUseCase(
                profiles = FakeProfiles(listOf(profile())),
                secrets = AiProfileSecretUseCase(FakeSecretStore().apply { put("ai-profile-p1", TEST_KEY) }),
                transport = transport,
                articles = articles,
                ids = ArticleIdFactory { "article-${articles.stored.size + 1}" },
                clock = { clock.instant() },
            ),
            fetchArticles = FetchArticleUseCase(
                transport = transport,
                articles = articles,
                ids = ArticleIdFactory { "article-${articles.stored.size + 1}" },
                clock = { clock.instant() },
            ),
            importArticles = ImportArticleUseCase(
                articles = articles,
                plans = plans,
                events = FakeEvents(completed = listOf("c1", "c2")),
                cards = FakeCards(),
                ids = ArticleIdFactory { "article-${articles.stored.size + 1}" },
                clock = { clock.instant() },
            ),
            clock = clock,
        )
    }

    private fun harness(
        preference: ReadingPreference = ReadingPreference("p1"),
        history: List<Article> = emptyList(),
    ) = Harness(preference, history)
}
