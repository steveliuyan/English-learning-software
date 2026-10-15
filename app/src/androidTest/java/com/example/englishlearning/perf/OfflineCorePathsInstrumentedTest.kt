package com.example.englishlearning.perf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.RoomLearningEventRepository
import com.example.englishlearning.learning.RoomVocabularySearchIndexRepository
import com.example.englishlearning.learning.SubmitCardFeedbackUseCase
import com.example.englishlearning.learning.SubmitFeedbackCommand
import com.example.englishlearning.learning.SubmitFeedbackResult
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.FsrsReviewScheduler
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.ArticleGenerationRequest
import com.example.englishlearning.reading.ArticleIdFactory
import com.example.englishlearning.reading.GenerateArticleResult
import com.example.englishlearning.reading.GenerateArticleUseCase
import com.example.englishlearning.reading.RoomArticleRepository
import com.example.englishlearning.reading.TranslationLanguage
import com.example.englishlearning.reading.resolveArticleLength
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SP-B Task 7（AC5-02 自动化子集）：AGENTS「本地优先」红线在真库进程内的可执行证明——
 * 无网络参与的学习/复习/缓存阅读/离线查词四条链路全部仅依赖 Room 即通；
 * AI endpoint 不可达映射为终态 [AiFailure.NetworkUnavailable] 且正式文章表零写入
 * （「loading 卡死」的库层对偶：失败必须是有界终态，不许留半篇幽灵文章）。
 *
 * 命名说明：决策/规格口径里的「AiUnavailable」在代码中的落点是
 * `GenerateArticleResult.Failed(AiFailure.NetworkUnavailable)`（映射分支见
 * GenerateArticleUseCase L112-119），本测试钉的是真实落点而非文档别名。
 * UI 态非 loading 由 JVM 侧 ReadingAccessViewModelTest 系覆盖，不在此重复。
 */
@RunWith(AndroidJUnit4::class)
class OfflineCorePathsInstrumentedTest {
    private val now: Instant = Instant.parse("2026-09-25T02:00:00Z")

    private fun withDatabase(block: suspend (AppDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = SeedData.newDatabaseName("offline-core")
        val database = SeedData.newDatabase(context, name)
        try {
            runBlocking { block(database) }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun feedbackSubmitReplayAndDueQueryWorkOnLocalDataAlone(): Unit = withDatabase { database ->
        val repository = RoomLearningEventRepository(database, Dispatchers.IO)
        val submit = SubmitCardFeedbackUseCase(
            repository = repository,
            clock = FixedClockProvider(now, ZoneOffset.UTC),
            scheduler = FsrsReviewScheduler(),
        )
        val command = SubmitFeedbackCommand(
            eventId = "offline-event-1",
            profileId = SeedData.PROFILE_ID,
            planId = "plan-offline",
            cardId = "card-offline",
            wordBookId = SeedData.WORD_BOOK_ID,
            feedback = CardFeedback.Known,
        )

        val recorded = submit(command)
        val replayed = submit(command)

        assertTrue("首次反馈必须记为 Recorded：$recorded", recorded is SubmitFeedbackResult.Recorded)
        assertTrue("同一事件重放不得重复计数：$replayed", replayed is SubmitFeedbackResult.AlreadyRecorded)
        val nextReviewAt = (recorded as SubmitFeedbackResult.Recorded).nextReviewAt
        assertEquals(
            "事件只落一条", 1,
            (repository.countEventsForCard("plan-offline", "card-offline") as RepositoryResult.Success).value,
        )
        assertEquals(
            "到期前不该出现该卡", emptyList<String>(),
            (repository.dueCardIds(SeedData.WORD_BOOK_ID, nextReviewAt.minusSeconds(1)) as RepositoryResult.Success).value,
        )
        assertEquals(
            "到达 nextReviewAt 即到期（SQL 口径 <=）", listOf("card-offline"),
            (repository.dueCardIds(SeedData.WORD_BOOK_ID, nextReviewAt) as RepositoryResult.Success).value,
        )
    }

    @Test
    fun cachedArticleReadBackNeedsNoNetwork(): Unit = withDatabase { database ->
        val articles = RoomArticleRepository(database, Dispatchers.IO)
        val saved = articles.saveNewVersion(storedArticle("offline-article", version = 1)).getOrThrow()

        val found = articles.findLatest(
            SeedData.PROFILE_ID, "2026-09-25", "cet4", ArticleType.STORY, ArticleLengthTier.STANDARD,
        ).getOrThrow()

        assertNotNull("已缓存文章离线必须可读", found)
        assertEquals(saved.title, found?.title)
        assertEquals(saved.englishText, found?.englishText)
        assertEquals(saved.chineseText, found?.chineseText)
    }

    @Test
    fun offlineWordSearchHitsIndexedCards(): Unit = withDatabase { database ->
        val index = RoomVocabularySearchIndexRepository(database, Dispatchers.IO)
        (index.replaceWordBook(SeedData.WORD_BOOK_ID, "Offline Book", "1", SeedData.wordCards(200)) as RepositoryResult.Success)

        val hits = (index.search("capword", limit = 30) as RepositoryResult.Success).value

        assertTrue("离线索引进的词条必须可查", hits.isNotEmpty())
        assertEquals("limit=30 必须生效", 30, hits.size)
    }

    @Test
    fun unreachableAiEndpointMapsToTerminalFailureAndWritesNoArticle(): Unit = withDatabase { database ->
        val articles = RoomArticleRepository(database, Dispatchers.IO)
        val profile = AiProfile(
            profileId = "ai-p1",
            displayName = "Test Provider",
            websiteUrl = "https://example.com",
            endpoint = "https://api.test/v1",
            model = "gpt-x",
            capabilities = setOf(AiCapability.Text),
            secretReference = SecretReference("ai-profile-ai-p1"),
        )
        val useCase = GenerateArticleUseCase(
            defaultTextProfile = DefaultTextProfileResolver { DefaultTextProfileResult.Selected(profile) },
            secrets = AiProfileSecretUseCase(InMemorySecretStore(mapOf("ai-profile-ai-p1" to "sk-offline-test"))) ,
            transport = UnreachableTransport(),
            articles = articles,
            ids = ArticleIdFactory { "generated-offline" },
            clock = { now },
        )

        val result = useCase.generate(generationRequest(), confirmedTextHost = "api.test")

        assertEquals("不可达必须映射为终态 Failed(NetworkUnavailable)", GenerateArticleResult.Failed(AiFailure.NetworkUnavailable), result)
        assertEquals("失败分支正式文章表零写入（不损坏版本、不留幽灵文章）", emptyList<Article>(), articles.findHistory(SeedData.PROFILE_ID).getOrThrow())
        assertNull(articles.findLatest(SeedData.PROFILE_ID, "2026-09-25", "cet4", ArticleType.STORY, ArticleLengthTier.STANDARD).getOrThrow())
    }

    private class UnreachableTransport : AiHttpTransport {
        override suspend fun send(request: AiHttpRequest): AiHttpResult = AiHttpResult.NetworkUnavailable
    }

    private class InMemorySecretStore(private val keys: Map<String, String>) : SecretStore {
        override fun save(reference: SecretReference, secret: CharArray): Result<Unit> = Result.success(Unit)
        override fun read(reference: SecretReference): Result<CharArray> =
            keys[reference.alias]?.toCharArray()?.let { Result.success(it) }
                ?: Result.failure(AppErrorException(AppError.KeyStoreUnavailable))
        override fun delete(reference: SecretReference): Result<Unit> = Result.success(Unit)
        override fun has(reference: SecretReference): Result<Boolean> = Result.success(keys.containsKey(reference.alias))
    }

    private fun generationRequest() = ArticleGenerationRequest(
        profileId = SeedData.PROFILE_ID,
        localDate = "2026-09-25",
        wordBookId = "cet4",
        articleType = ArticleType.STORY,
        length = resolveArticleLength("cet4", ArticleLengthTier.STANDARD),
        language = TranslationLanguage.ZH,
        targetCards = listOf(
            WordCard("c1", "cet4", "apple", "", "", "", inflections = listOf("apples")),
            WordCard("c2", "cet4", "garden", "", "", ""),
        ),
        excludedLemmas = emptyList(),
    )

    private fun storedArticle(id: String, version: Int) = Article(
        articleId = id, profileId = SeedData.PROFILE_ID, localDate = "2026-09-25", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = version,
        title = "An offline story", englishText = "text", chineseText = "译文",
        generatedAtEpochMillis = now.toEpochMilli(), coveredLemmas = emptyList(),
        source = ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
    )
}
