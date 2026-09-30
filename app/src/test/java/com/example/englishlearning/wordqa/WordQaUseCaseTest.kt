package com.example.englishlearning.wordqa

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
 * 词问答用例的编排契约。顺序是需求：配置检查 → 出站确认（发请求前）→ 构造请求 →
 * 出站 → 解析。Key 以 CharArray 流转并在请求后清零；任何失败分支不落库。
 */
class WordQaUseCaseTest {

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
        var result: AiHttpResult = AiHttpResult.Responded(AiHttpResponse(200, """{"choices":[{"message":{"content":"造句回答"}}]}"""))

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            requests += request
            return result
        }
    }

    private fun useCase(
        resolverResult: DefaultTextProfileResult = DefaultTextProfileResult.Selected(profile),
        hasKey: Boolean = true,
        transport: RecordingTransport = RecordingTransport(),
    ): Pair<WordQaUseCase, RecordingTransport> {
        val secrets = AiProfileSecretUseCase(FakeSecretStore(hasKey))
        val useCase = WordQaUseCase(
            defaultTextProfile = DefaultTextProfileResolver { resolverResult },
            secrets = secrets,
            transport = transport,
        )
        return useCase to transport
    }

    @Test
    fun noDefaultProfileIsNotConfiguredWithoutAnyNetworkCall() = runTest {
        val (useCase, transport) = useCase(resolverResult = DefaultTextProfileResult.NoSelection)

        val result = useCase.ask(WordQaRequest(WordQaKind.Sentence, "apple"), confirmedTextHost = null)

        assertEquals(WordQaResult.NotConfigured(WordQaNotConfiguredReason.NoDefaultProfile), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun unavailableProfileIsNotConfigured() = runTest {
        val (useCase, transport) = useCase(resolverResult = DefaultTextProfileResult.Unavailable)

        val result = useCase.ask(WordQaRequest(WordQaKind.Sentence, "apple"), confirmedTextHost = null)

        assertEquals(WordQaResult.NotConfigured(WordQaNotConfiguredReason.DefaultProfileUnavailable), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun missingKeyIsNotConfigured() = runTest {
        val (useCase, transport) = useCase(hasKey = false)

        val result = useCase.ask(WordQaRequest(WordQaKind.Sentence, "apple"), confirmedTextHost = null)

        assertEquals(WordQaResult.NotConfigured(WordQaNotConfiguredReason.DefaultProfileUnavailable), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun firstAskNeedsConfirmationBeforeAnyByteLeaves() = runTest {
        val (useCase, transport) = useCase()

        val result = useCase.ask(WordQaRequest(WordQaKind.Sentence, "apple"), confirmedTextHost = null)

        assertEquals(WordQaResult.NeedsConfirmation("api.example.com", AiPayloadKind.Text), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun confirmedHostSendsRequestAndReturnsTheAnswer() = runTest {
        val (useCase, transport) = useCase()

        val result = useCase.ask(WordQaRequest(WordQaKind.Sentence, "apple"), confirmedTextHost = "api.example.com")

        assertEquals(WordQaResult.Answered("造句回答"), result)
        assertEquals(1, transport.requests.size)
        assertTrue(transport.requests.single().body.contains("apple"), "请求体必须携带目标词")
        // Key 只进 Authorization 头，永不进 body。
        assertTrue(!transport.requests.single().body.contains("k3y"))
        assertTrue(transport.requests.single().headers["Authorization"] != null)
    }

    @Test
    fun transportFailuresMapToDomainFailures() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.NetworkUnavailable }
        val (useCase, _) = useCase(transport = transport)

        val result = useCase.ask(WordQaRequest(WordQaKind.Sentence, "apple"), confirmedTextHost = "api.example.com")

        assertEquals(WordQaResult.Failed(AiFailure.NetworkUnavailable), result)
    }

    @Test
    fun unauthorizedResponseMapsToUnauthorizedFailure() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.Responded(AiHttpResponse(401, "{}")) }
        val (useCase, _) = useCase(transport = transport)

        val result = useCase.ask(WordQaRequest(WordQaKind.Sentence, "apple"), confirmedTextHost = "api.example.com")

        assertEquals(WordQaResult.Failed(AiFailure.Unauthorized), result)
    }

    @Test
    fun blankAnswerIsAnInvalidResponse() = runTest {
        val transport = RecordingTransport().apply {
            result = AiHttpResult.Responded(AiHttpResponse(200, """{"choices":[{"message":{"content":"   "}}]}"""))
        }
        val (useCase, _) = useCase(transport = transport)

        val result = useCase.ask(WordQaRequest(WordQaKind.Sentence, "apple"), confirmedTextHost = "api.example.com")

        assertEquals(WordQaResult.Failed(AiFailure.InvalidResponse), result)
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
        val useCase = WordQaUseCase(
            defaultTextProfile = DefaultTextProfileResolver { DefaultTextProfileResult.Selected(profile) },
            secrets = secrets,
            transport = transport,
        )

        useCase.ask(WordQaRequest(WordQaKind.Sentence, "apple"), confirmedTextHost = "api.example.com")

        assertTrue(captured != null && captured!!.all { it == '\u0000' }, "请求结束后 Key 缓冲必须清零")
    }

    @Test
    fun blankLemmaIsRejectedAtTheEntranceBeforeAnythingElse() = runTest {
        val (useCase, transport) = useCase()

        try {
            useCase.ask(WordQaRequest(WordQaKind.Sentence, "  "), confirmedTextHost = null)
            fail("空白词必须在入口被拒绝")
        } catch (expected: IllegalArgumentException) {
            // 编程契约：词来自词卡数据，不该出现空白
        }

        assertEquals(0, transport.requests.size)
    }
}
