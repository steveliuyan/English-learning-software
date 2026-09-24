package com.example.englishlearning.reading

import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.storage.AppErrorException
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenerateArticleUseCaseTest {

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

    private open class FakeArticleRepository : ArticleRepository {
        val stored = mutableListOf<Article>()
        override suspend fun saveNewVersion(article: Article): Result<Article> {
            val saved = article.copy(version = stored.size + 1)
            stored += saved
            return Result.success(saved)
        }

        override suspend fun findLatest(profileId: String, localDate: String, activeWordBookId: String, articleType: ArticleType, lengthTier: ArticleLengthTier) = Result.success(
            stored.lastOrNull {
                it.profileId == profileId && it.localDate == localDate && it.activeWordBookId == activeWordBookId &&
                    it.articleType == articleType && it.lengthTier == lengthTier
            },
        )

        override suspend fun findHistory(profileId: String) = Result.success(stored.filter { it.profileId == profileId })
        override suspend fun findBySourceUrl(url: String) = Result.success<Article?>(null)
    }

    private class FailingSaveRepository : FakeArticleRepository() {
        override suspend fun saveNewVersion(article: Article): Result<Article> =
            Result.failure(AppErrorException(AppError.StorageUnavailable))
    }

    private class FakeProfiles(private val profiles: List<AiProfile>) : com.example.englishlearning.ai.AiProfileRepository {
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
        profiles: List<AiProfile> = listOf(defaultProfile()),
        secretStore: FakeSecretStore = FakeSecretStore().apply { put("ai-profile-p1", TEST_KEY) },
    ) {
        val transport = FakeTransport()
        val repository = FakeArticleRepository()
        val useCase = GenerateArticleUseCase(
            profiles = FakeProfiles(profiles),
            secrets = AiProfileSecretUseCase(secretStore),
            transport = transport,
            articles = repository,
            ids = ArticleIdFactory { "article-1" },
            clock = { Instant.ofEpochMilli(1_000L) },
        )
    }

    private companion object {
        const val TEST_KEY = "sk-test-secret-123"

        fun defaultProfile(endpoint: String = "https://api.test/v1") = AiProfile(
            profileId = "p1",
            displayName = "Test Provider",
            websiteUrl = "https://example.com",
            endpoint = endpoint,
            model = "gpt-x",
            capabilities = setOf(AiCapability.Text),
            secretReference = SecretReference("ai-profile-p1"),
        )

        /** 解包后 content 里的内层 JSON；词数在 cet4/STANDARD 接受区间（162~330）内。 */
        val validContent: String = JsonObject(
            mapOf(
                "title" to JsonPrimitive("A Day"),
                "english" to JsonPrimitive("apple garden " + List(198) { "word" }.joinToString(" ")),
                "chinese" to JsonPrimitive("这是一段中文译文。".repeat(5)),
            ),
        ).toString()

        fun chatBody(content: String): String = JsonObject(
            mapOf(
                "choices" to JsonArray(
                    listOf(JsonObject(mapOf("message" to JsonObject(mapOf("content" to JsonPrimitive(content)))))),
                ),
            ),
        ).toString()
    }

    private val length = resolveArticleLength("cet4", ArticleLengthTier.STANDARD)
    private val targetCards = listOf(
        WordCard("c1", "cet4", "apple", "", "", "", inflections = listOf("apples")),
        WordCard("c2", "cet4", "garden", "", "", ""),
    )

    private fun request(excluded: List<String> = emptyList()) = ArticleGenerationRequest(
        profileId = "p1",
        localDate = "2026-09-24",
        wordBookId = "cet4",
        articleType = ArticleType.STORY,
        length = length,
        language = TranslationLanguage.ZH,
        targetCards = targetCards,
        excludedLemmas = excluded,
    )

    /** 与 request 同复用键的既有文章。 */
    private fun existingArticle(version: Int) = Article(
        articleId = "old",
        profileId = "p1",
        localDate = "2026-09-24",
        activeWordBookId = "cet4",
        articleType = ArticleType.STORY,
        lengthTier = ArticleLengthTier.STANDARD,
        version = version,
        title = "Old",
        englishText = List(200) { "word" }.joinToString(" "),
        chineseText = "旧译文。",
        generatedAtEpochMillis = 0L,
        coveredLemmas = listOf("apple"),
        source = ArticleSource.UserImported,
    )

    @Test
    fun reusesTheStoredArticleWithoutCallingTheAi() = runTest {
        val harness = Harness()
        harness.repository.stored += existingArticle(version = 1)

        val result = harness.useCase.generate(request(), confirmedTextHost = null)

        val reused = result as GenerateArticleResult.Reused
        assertEquals(1, reused.article.version)
        assertEquals(0, harness.transport.callCount)
    }

    @Test
    fun generatesAndStoresANewArticleWhenNothingMatches() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, chatBody(validContent)))

        val result = harness.useCase.generate(request(), confirmedTextHost = "api.test")

        val generated = result as GenerateArticleResult.Generated
        assertEquals(1, generated.article.version)
        assertEquals(listOf("apple", "garden"), generated.article.coveredLemmas)
        // 落库后能用 findLatest 读回，且复用键与请求一致。
        val back = harness.repository.findLatest("p1", "2026-09-24", "cet4", ArticleType.STORY, ArticleLengthTier.STANDARD).getOrThrow()
        assertEquals(generated.article.articleId, back?.articleId)
        assertEquals(1, harness.transport.callCount)
    }

    @Test
    fun regeneratingSavesANewVersionAndKeepsTheOldOneReadable() = runTest {
        val harness = Harness()
        harness.repository.stored += existingArticle(version = 1)
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, chatBody(validContent)))

        val result = harness.useCase.generate(request(), confirmedTextHost = "api.test", regenerate = true)

        val generated = result as GenerateArticleResult.Generated
        assertEquals(2, generated.article.version)
        // 假仓储按插入序保存；语义要点是两个版本都在、latest 是新版本。
        assertEquals(listOf(1, 2), harness.repository.stored.map { it.version }.sorted())
        val latest = harness.repository.findLatest("p1", "2026-09-24", "cet4", ArticleType.STORY, ArticleLengthTier.STANDARD).getOrThrow()
        assertEquals(2, latest?.version)
    }

    @Test
    fun asksForConfirmationBeforeTheFirstTextCallToAHost() = runTest {
        val harness = Harness()

        val result = harness.useCase.generate(request(), confirmedTextHost = null)

        val needs = result as GenerateArticleResult.NeedsConfirmation
        assertEquals("api.test", needs.host)
        assertEquals(AiPayloadKind.Text, needs.payloadKind)
        assertEquals(0, harness.transport.callCount) // 未确认就不许发请求
    }

    @Test
    fun proceedsOnceTheHostIsConfirmed() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, chatBody(validContent)))

        val result = harness.useCase.generate(request(), confirmedTextHost = "api.test")

        assertTrue(result is GenerateArticleResult.Generated)
        assertEquals(1, harness.transport.callCount)
    }

    @Test
    fun asksAgainWhenTheEndpointHostChanged() = runTest {
        val harness = Harness(profiles = listOf(defaultProfile(endpoint = "https://b.test/v1")))

        val result = harness.useCase.generate(request(), confirmedTextHost = "a.test")

        val needs = result as GenerateArticleResult.NeedsConfirmation
        assertEquals("b.test", needs.host)
        assertEquals(0, harness.transport.callCount)
    }

    @Test
    fun reportsNotConfiguredWhenNoProfileExists() = runTest {
        val harness = Harness(profiles = emptyList())

        val result = harness.useCase.generate(request(), confirmedTextHost = "api.test")

        assertEquals(GenerateArticleResult.NotConfigured(NotConfiguredReason.NoProfile), result)
        assertEquals(0, harness.transport.callCount)
    }

    @Test
    fun reportsNotConfiguredWhenTheProfileHasNoStoredKey() = runTest {
        val harness = Harness(secretStore = FakeSecretStore())

        val result = harness.useCase.generate(request(), confirmedTextHost = "api.test")

        assertEquals(GenerateArticleResult.NotConfigured(NotConfiguredReason.NoKey), result)
        assertEquals(0, harness.transport.callCount)
    }

    @Test
    fun maps401ToUnauthorizedAndStoresNothing() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(401, "{\"error\":\"denied\"}"))

        val result = harness.useCase.generate(request(), confirmedTextHost = "api.test")

        assertEquals(GenerateArticleResult.Failed(AiFailure.Unauthorized), result)
        assertTrue(harness.repository.stored.isEmpty()) // 失败不得写入伪造文章
    }

    @Test
    fun maps429ToRateLimited() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(429, "{\"error\":\"slow down\"}"))

        val result = harness.useCase.generate(request(), confirmedTextHost = "api.test")

        assertEquals(GenerateArticleResult.Failed(AiFailure.RateLimited), result)
        assertTrue(harness.repository.stored.isEmpty())
    }

    @Test
    fun mapsTimeoutToTimeout() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.TimedOut

        val result = harness.useCase.generate(request(), confirmedTextHost = "api.test")

        assertEquals(GenerateArticleResult.Failed(AiFailure.Timeout), result)
        assertTrue(harness.repository.stored.isEmpty())
    }

    @Test
    fun mapsNetworkFailureToNetworkUnavailable() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.NetworkUnavailable

        val result = harness.useCase.generate(request(), confirmedTextHost = "api.test")

        assertEquals(GenerateArticleResult.Failed(AiFailure.NetworkUnavailable), result)
        assertTrue(harness.repository.stored.isEmpty())
    }

    @Test
    fun mapsMalformedContentToInvalidResponseAndStoresNothing() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, "this is not json at all"))

        val result = harness.useCase.generate(request(), confirmedTextHost = "api.test")

        assertEquals(GenerateArticleResult.Failed(AiFailure.InvalidResponse), result)
        assertTrue(harness.repository.stored.isEmpty())
    }

    @Test
    fun rethrowsCancellationWithoutStoring() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.Cancelled

        assertFailsWith<CancellationException> { harness.useCase.generate(request(), confirmedTextHost = "api.test") }
        assertTrue(harness.repository.stored.isEmpty())
    }

    @Test
    fun reportsStorageFailureWhenSavingFails() = runTest {
        val harness = Harness()
        val failing = FailingSaveRepository()
        val useCase = GenerateArticleUseCase(
            profiles = FakeProfiles(listOf(defaultProfile())),
            secrets = AiProfileSecretUseCase(FakeSecretStore().apply { put("ai-profile-p1", TEST_KEY) }),
            transport = harness.transport,
            articles = failing,
            ids = ArticleIdFactory { "article-1" },
            clock = { Instant.ofEpochMilli(1_000L) },
        )
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, chatBody(validContent)))

        val result = useCase.generate(request(), confirmedTextHost = "api.test")

        assertEquals(GenerateArticleResult.StorageFailed, result)
    }

    @Test
    fun neverStoresTheKeyInTheArticle() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, chatBody(validContent)))

        harness.useCase.generate(request(), confirmedTextHost = "api.test")

        val article = harness.repository.stored.single()
        val texts = listOf(
            article.articleId, article.profileId, article.localDate, article.activeWordBookId,
            article.title, article.englishText, article.chineseText,
            article.coveredLemmas.joinToString(" "), article.source.toString(),
        )
        for (text in texts) {
            assertTrue(!text.contains("sk-"), "article must never contain a key fragment")
        }
    }

    @Test
    fun requestPromptUsesTheCardsCompletedToday() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, chatBody(validContent)))

        harness.useCase.generate(request(), confirmedTextHost = "api.test")

        val body = harness.transport.lastRequest!!.body
        assertTrue(body.contains("apple"), "prompt must carry the target lemma apple")
        assertTrue(body.contains("garden"), "prompt must carry the target lemma garden")
        assertTrue(!body.contains("banana"), "prompt must not carry words outside the request")
    }

    @Test
    fun excludesPreviouslyUsedWordsOnRegeneration() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, chatBody(validContent)))

        harness.useCase.generate(request(excluded = listOf("river")), confirmedTextHost = "api.test")

        val body = harness.transport.lastRequest!!.body
        assertTrue(body.contains("river"), "prompt must carry the excluded lemmas from the previous article")
    }

    @Test
    fun recordsAGenerationParameterSummaryWithoutTheKey() = runTest {
        val harness = Harness()
        harness.transport.responses += AiHttpResult.Responded(com.example.englishlearning.ai.net.AiHttpResponse(200, chatBody(validContent)))

        harness.useCase.generate(request(), confirmedTextHost = "api.test")

        val article = harness.repository.stored.single()
        val source = article.source as ArticleSource.AiGenerated
        assertEquals("gpt-x", source.modelName)
        val summary = source.parameterSummary
        assertTrue(summary.contains("model=gpt-x"))
        assertTrue(summary.contains("temperature"))
        assertTrue(summary.contains("max_tokens"))
        assertTrue(summary.contains("template=default-reading-v1"))
        assertTrue(!summary.contains("sk-"), "summary must never contain the key")
        assertTrue(!summary.contains("api.test"), "summary must never contain the endpoint")
    }
}
