package com.example.englishlearning.imagegen

import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.ChatResponseEnvelope
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/** 生图第一阶段（文本生成提示词）的编排契约：与长难句分析同构，Text 按域名确认一次。 */
class DrawingPromptUseCaseTest {

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
        var lastRead: CharArray? = null
        override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
        override fun read(reference: SecretReference): Result<CharArray> {
            val value = if (hasKey) "k3y".toCharArray() else charArrayOf()
            lastRead = value
            return Result.success(value)
        }
        override fun delete(reference: SecretReference) = Result.success(Unit)
        override fun has(reference: SecretReference) = Result.success(hasKey)
    }

    private class RecordingTransport : AiHttpTransport {
        val requests = mutableListOf<AiHttpRequest>()
        var result: AiHttpResult = AiHttpResult.Responded(
            AiHttpResponse(200, """{"choices":[{"message":{"content":"a watercolor painting of an apple"}}]}"""),
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
    ): Triple<DrawingPromptUseCase, RecordingTransport, FakeSecretStore> {
        val secretStore = FakeSecretStore(hasKey)
        val useCase = DrawingPromptUseCase(
            defaultTextProfile = DefaultTextProfileResolver { resolverResult },
            secrets = AiProfileSecretUseCase(secretStore),
            transport = transport,
        )
        return Triple(useCase, transport, secretStore)
    }

    @Test
    fun noDefaultProfileIsNotConfiguredWithoutAnyNetworkCall() = runTest {
        val (useCase, transport, _) = useCase(resolverResult = DefaultTextProfileResult.NoSelection)

        val result = useCase.generate("苹果", confirmedTextHost = null)

        assertEquals(DrawingPromptResult.NotConfigured(ImageStudioNotConfiguredReason.NoDefaultProfile), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun unavailableProfileIsNotConfigured() = runTest {
        val (useCase, transport, _) = useCase(resolverResult = DefaultTextProfileResult.Unavailable)

        val result = useCase.generate("苹果", confirmedTextHost = null)

        assertEquals(
            DrawingPromptResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable),
            result,
        )
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun missingKeyIsNotConfigured() = runTest {
        val (useCase, transport, _) = useCase(hasKey = false)

        val result = useCase.generate("苹果", confirmedTextHost = null)

        assertEquals(
            DrawingPromptResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable),
            result,
        )
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun firstAskNeedsConfirmationBeforeAnyByteLeaves() = runTest {
        val (useCase, transport, _) = useCase()

        val result = useCase.generate("苹果", confirmedTextHost = null)

        assertEquals(DrawingPromptResult.NeedsConfirmation("api.example.com", AiPayloadKind.Text), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun confirmedHostSendsControlledPromptAndReturnsDraft() = runTest {
        val (useCase, transport, _) = useCase()

        val result = useCase.generate("苹果", confirmedTextHost = "api.example.com")

        assertEquals(DrawingPromptResult.Draft("a watercolor painting of an apple"), result)
        assertEquals(1, transport.requests.size)
        val request = transport.requests.single()
        assertEquals("https://api.example.com/v1/chat/completions", request.url)
        assertTrue(request.body.contains("苹果"))
        assertTrue(request.body.contains("英文"))
    }

    @Test
    fun responseIsTrimmedAndCodeFenceIsStripped() = runTest {
        val transport = RecordingTransport().apply {
            result = AiHttpResult.Responded(
                AiHttpResponse(200, """{"choices":[{"message":{"content":"```text\na watercolor apple\n```"}}]}"""),
            )
        }
        val (useCase, _, _) = useCase(transport = transport)

        val result = useCase.generate("苹果", confirmedTextHost = "api.example.com")

        assertEquals(DrawingPromptResult.Draft("a watercolor apple"), result)
    }

    @Test
    fun blankOrOverlongAnswerIsInvalidResponse() = runTest {
        val blankTransport = RecordingTransport().apply {
            result = AiHttpResult.Responded(
                AiHttpResponse(200, """{"choices":[{"message":{"content":"   "}}]}"""),
            )
        }
        val (blankCase, _, _) = useCase(transport = blankTransport)
        assertEquals(
            DrawingPromptResult.Failed(AiFailure.InvalidResponse),
            blankCase.generate("苹果", confirmedTextHost = "api.example.com"),
        )

        val overlongTransport = RecordingTransport().apply {
            result = AiHttpResult.Responded(
                AiHttpResponse(200, """{"choices":[{"message":{"content":"${"a".repeat(DrawingPromptPolicy.maxPromptLength + 1)}"}}]}"""),
            )
        }
        val (overlongCase, _, _) = useCase(transport = overlongTransport)
        assertEquals(
            DrawingPromptResult.Failed(AiFailure.InvalidResponse),
            overlongCase.generate("苹果", confirmedTextHost = "api.example.com"),
        )
    }

    @Test
    fun statusAndTransportFailuresMapToDomainFailures() = runTest {
        val unauthorizedTransport = RecordingTransport().apply {
            result = AiHttpResult.Responded(AiHttpResponse(401, "denied"))
        }
        val (unauthorizedCase, _, _) = useCase(transport = unauthorizedTransport)
        assertEquals(
            DrawingPromptResult.Failed(AiFailure.Unauthorized),
            unauthorizedCase.generate("苹果", confirmedTextHost = "api.example.com"),
        )

        val networkTransport = RecordingTransport().apply { result = AiHttpResult.NetworkUnavailable }
        val (networkCase, _, _) = useCase(transport = networkTransport)
        assertEquals(
            DrawingPromptResult.Failed(AiFailure.NetworkUnavailable),
            networkCase.generate("苹果", confirmedTextHost = "api.example.com"),
        )

        val timeoutTransport = RecordingTransport().apply { result = AiHttpResult.TimedOut }
        val (timeoutCase, _, _) = useCase(transport = timeoutTransport)
        assertEquals(
            DrawingPromptResult.Failed(AiFailure.Timeout),
            timeoutCase.generate("苹果", confirmedTextHost = "api.example.com"),
        )
    }

    @Test
    fun cancelledTransportIsRethrownAsCancellation() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.Cancelled }
        val (useCase, _, _) = useCase(transport = transport)

        assertFailsWith<CancellationException> {
            useCase.generate("苹果", confirmedTextHost = "api.example.com")
        }
    }

    @Test
    fun keyIsZeroedAfterTheFlowEnds() = runTest {
        val (useCase, _, secretStore) = useCase()

        useCase.generate("苹果", confirmedTextHost = "api.example.com")

        assertTrue(secretStore.lastRead!!.all { it == '\u0000' })
    }

    @Test
    fun blankOrOverlongSubjectIsAProgrammingError() = runTest {
        val (useCase, transport, _) = useCase()

        assertFailsWith<IllegalArgumentException> { useCase.generate("   ", confirmedTextHost = "api.example.com") }
        assertFailsWith<IllegalArgumentException> {
            useCase.generate("a".repeat(DrawingPromptPolicy.maxSubjectLength + 1), confirmedTextHost = "api.example.com")
        }
        assertEquals(0, transport.requests.size)
    }
}
