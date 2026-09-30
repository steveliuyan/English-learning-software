package com.example.englishlearning.sentence

import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResponse
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

/**
 * 长难句分析用例的编排契约。顺序是需求：配置检查 → 出站确认（发请求前）→ 构造请求 →
 * 出站 → 解析。Key 以 CharArray 流转并在请求后清零；分析不落库。
 */
class SentenceAnalysisUseCaseTest {

    private val profile = AiProfile(
        profileId = "p1",
        displayName = "测试",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "gpt-x",
        capabilities = setOf(AiCapability.Text),
        secretReference = SecretReference("ai-profile-p1"),
    )

    private class FakeSecretStore(private val hasKey: Boolean) : SecretStore {
        override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
        override fun read(reference: SecretReference) = Result.success(if (hasKey) "k3y".toCharArray() else charArrayOf())
        override fun delete(reference: SecretReference) = Result.success(Unit)
        override fun has(reference: SecretReference) = Result.success(hasKey)
    }

    private class RecordingTransport : AiHttpTransport {
        val requests = mutableListOf<AiHttpRequest>()
        var result: AiHttpResult = AiHttpResult.Responded(
            AiHttpResponse(200, """{"choices":[{"message":{"content":"主句\tBirds fly.\t鸟会飞。"}}]}"""),
        )

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            requests += request
            return result
        }
    }

    private fun useCase(
        resolverResult: DefaultTextProfileResult = DefaultTextProfileResult.Selected(profile),
        hasKey: Boolean = true,
        transport: RecordingTransport = RecordingTransport(),
    ): Pair<SentenceAnalysisUseCase, RecordingTransport> {
        val secrets = AiProfileSecretUseCase(FakeSecretStore(hasKey))
        val useCase = SentenceAnalysisUseCase(
            defaultTextProfile = DefaultTextProfileResolver { resolverResult },
            secrets = secrets,
            transport = transport,
        )
        return useCase to transport
    }

    @Test
    fun noDefaultProfileIsNotConfiguredWithoutAnyNetworkCall() = runTest {
        val (useCase, transport) = useCase(resolverResult = DefaultTextProfileResult.NoSelection)

        val result = useCase.analyze("Birds fly.", confirmedTextHost = null)

        assertEquals(SentenceAnalysisResult.NotConfigured(SentenceAnalysisNotConfiguredReason.NoDefaultProfile), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun unavailableProfileIsNotConfigured() = runTest {
        val (useCase, transport) = useCase(resolverResult = DefaultTextProfileResult.Unavailable)

        val result = useCase.analyze("Birds fly.", confirmedTextHost = null)

        assertEquals(SentenceAnalysisResult.NotConfigured(SentenceAnalysisNotConfiguredReason.DefaultProfileUnavailable), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun missingKeyIsNotConfigured() = runTest {
        val (useCase, transport) = useCase(hasKey = false)

        val result = useCase.analyze("Birds fly.", confirmedTextHost = null)

        assertEquals(SentenceAnalysisResult.NotConfigured(SentenceAnalysisNotConfiguredReason.DefaultProfileUnavailable), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun firstAskNeedsConfirmationBeforeAnyByteLeaves() = runTest {
        val (useCase, transport) = useCase()

        val result = useCase.analyze("Birds fly.", confirmedTextHost = null)

        assertEquals(SentenceAnalysisResult.NeedsConfirmation("api.example.com", AiPayloadKind.Text), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun confirmedHostSendsRequestAndReturnsParsedSegments() = runTest {
        val transport = RecordingTransport().apply {
            result = AiHttpResult.Responded(
                AiHttpResponse(200, """{"choices":[{"message":{"content":"主句\tBirds fly.\t鸟会飞。\n短语\tbirds\t鸟"}}]}"""),
            )
        }
        val (useCase, _) = useCase(transport = transport)

        val result = useCase.analyze("Birds fly.", confirmedTextHost = "api.example.com")

        val segments = (result as SentenceAnalysisResult.Analyzed).segments
        assertEquals(2, segments.size)
        assertEquals(SentenceRole.Main, segments[0].role)
        assertEquals("鸟会飞。", segments[0].explanation)
        assertEquals(1, transport.requests.size)
        assertTrue(transport.requests.single().body.contains("Birds fly."), "请求体必须携带原句")
        // Key 只进 Authorization 头，永不进 body。
        assertTrue(!transport.requests.single().body.contains("k3y"))
        assertTrue(transport.requests.single().headers["Authorization"] != null)
    }

    @Test
    fun transportFailuresMapToDomainFailures() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.NetworkUnavailable }
        val (useCase, _) = useCase(transport = transport)

        val result = useCase.analyze("Birds fly.", confirmedTextHost = "api.example.com")

        assertEquals(SentenceAnalysisResult.Failed(AiFailure.NetworkUnavailable), result)
    }

    @Test
    fun unauthorizedResponseMapsToUnauthorizedFailure() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.Responded(AiHttpResponse(401, "{}")) }
        val (useCase, _) = useCase(transport = transport)

        val result = useCase.analyze("Birds fly.", confirmedTextHost = "api.example.com")

        assertEquals(SentenceAnalysisResult.Failed(AiFailure.Unauthorized), result)
    }

    @Test
    fun malformedLineProtocolIsAnInvalidResponse() = runTest {
        val transport = RecordingTransport().apply {
            result = AiHttpResult.Responded(AiHttpResponse(200, """{"choices":[{"message":{"content":"没有分隔符"}}]}"""))
        }
        val (useCase, _) = useCase(transport = transport)

        val result = useCase.analyze("Birds fly.", confirmedTextHost = "api.example.com")

        assertEquals(SentenceAnalysisResult.Failed(AiFailure.InvalidResponse), result)
    }

    @Test
    fun keyBufferIsClearedAfterTheRequest() = runTest {
        val transport = RecordingTransport()
        var captured: CharArray? = null
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference): Result<CharArray> {
                val key = "k3y".toCharArray()
                captured = key
                return Result.success(key)
            }
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(true)
        })
        val useCase = SentenceAnalysisUseCase(
            defaultTextProfile = DefaultTextProfileResolver { DefaultTextProfileResult.Selected(profile) },
            secrets = secrets,
            transport = transport,
        )

        useCase.analyze("Birds fly.", confirmedTextHost = "api.example.com")

        assertTrue(captured != null && captured!!.all { it == '\u0000' }, "请求结束后 Key 缓冲必须清零")
    }

    @Test
    fun blankSentenceIsRejectedAtTheEntranceBeforeAnythingElse() = runTest {
        val (useCase, transport) = useCase()

        try {
            useCase.analyze("   ", confirmedTextHost = null)
            fail("空白句子必须在入口被拒绝")
        } catch (expected: IllegalArgumentException) {
            // 编程契约：句子来自输入框的长度约束，不该出现空白
        }

        assertEquals(0, transport.requests.size)
    }
}
