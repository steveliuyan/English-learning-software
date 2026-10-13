package com.example.englishlearning.phototranslation

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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/**
 * AC3-06 编排契约：确认在任何字节发出之前、且每次调用都要确认（不复用上次授权）；
 * OCR 文本是数据位不是提示词；翻译失败与 OCR 失败严格分离，失败不产出半份译文。
 */
class PhotoTranslationUseCaseTest {

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
            AiHttpResponse(200, """{"choices":[{"message":{"content":"鸟儿会飞。"}}]}"""),
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
    ): Pair<PhotoTranslationUseCase, RecordingTransport> {
        val useCase = PhotoTranslationUseCase(
            defaultTextProfile = DefaultTextProfileResolver { resolverResult },
            secrets = AiProfileSecretUseCase(FakeSecretStore(hasKey)),
            transport = transport,
        )
        return useCase to transport
    }

    @Test
    fun noDefaultProfileIsNotConfiguredWithoutAnyNetworkCall() = runTest {
        val (useCase, transport) = useCase(resolverResult = DefaultTextProfileResult.NoSelection)

        val result = useCase.translate("Birds fly.", confirmedTextHost = null)

        assertEquals(PhotoTranslationResult.NotConfigured(PhotoTranslationNotConfiguredReason.NoDefaultProfile), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun missingKeyIsNotConfiguredWithoutAnyNetworkCall() = runTest {
        val (useCase, transport) = useCase(hasKey = false)

        val result = useCase.translate("Birds fly.", confirmedTextHost = null)

        assertEquals(PhotoTranslationResult.NotConfigured(PhotoTranslationNotConfiguredReason.DefaultProfileUnavailable), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun firstCallDemandsConfirmationBeforeAnyByteLeaves() = runTest {
        val (useCase, transport) = useCase()

        val result = useCase.translate("Birds fly.", confirmedTextHost = null)

        assertEquals(PhotoTranslationResult.NeedsConfirmation("api.example.com", AiPayloadKind.Text), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun confirmationIsNeverRememberedAcrossCalls() = runTest {
        val (useCase, transport) = useCase()

        useCase.translate("Birds fly.", confirmedTextHost = "api.example.com")
        val again = useCase.translate("Birds fly.", confirmedTextHost = null)

        assertEquals(1, transport.requests.size)
        assertIs(PhotoTranslationResult.NeedsConfirmation::class, again)
    }

    @Test
    fun confirmedHostProducesTranslationOfSourceAndTargetOnly() = runTest {
        val (useCase, _) = useCase()

        val result = useCase.translate("Birds fly.", confirmedTextHost = "api.example.com")

        assertTrue(result is PhotoTranslationResult.Translated)
        val translated = result as PhotoTranslationResult.Translated
        assertEquals("Birds fly.", translated.source)
        assertEquals("鸟儿会飞。", translated.target)
    }

    @Test
    fun networkAndTimeoutFailuresSurfaceAsAiFailuresNotOcrFailures() = runTest {
        val offline = RecordingTransport().apply { result = AiHttpResult.NetworkUnavailable }
        val (useCaseA, _) = useCase(transport = offline)
        val timedOut = RecordingTransport().apply { result = AiHttpResult.TimedOut }
        val (useCaseB, _) = useCase(transport = timedOut)

        assertEquals(
            PhotoTranslationResult.Failed(AiFailure.NetworkUnavailable),
            useCaseA.translate("Birds fly.", confirmedTextHost = "api.example.com"),
        )
        assertEquals(
            PhotoTranslationResult.Failed(AiFailure.Timeout),
            useCaseB.translate("Birds fly.", confirmedTextHost = "api.example.com"),
        )
    }

    @Test
    fun userCancellationPropagatesInsteadOfFakingFailure() = runTest {
        val cancelled = RecordingTransport().apply { result = AiHttpResult.Cancelled }
        val (useCase, _) = useCase(transport = cancelled)

        assertFailsWith<CancellationException> {
            useCase.translate("Birds fly.", confirmedTextHost = "api.example.com")
        }
    }

    @Test
    fun blankOrDangerousModelOutputIsRejectedAsInvalidResponse() = runTest {
        for (content in listOf("", "   ", "<script>alert(1)</script>译文", "x".repeat(9000))) {
            val body = """{"choices":[{"message":{"content":${quote(content)}}}]}"""
            val transport = RecordingTransport().apply { result = AiHttpResult.Responded(AiHttpResponse(200, body)) }
            val (useCase, _) = useCase(transport = transport)

            val result = useCase.translate("Birds fly.", confirmedTextHost = "api.example.com")

            assertEquals(PhotoTranslationResult.Failed(AiFailure.InvalidResponse), result, "content=${content.take(30)}")
        }
    }

    @Test
    fun httpErrorStatusesMapToTheirActionableFailures() = runTest {
        for ((status, failure) in listOf(401 to AiFailure.Unauthorized, 429 to AiFailure.RateLimited, 503 to AiFailure.ServerUnavailable)) {
            val transport = RecordingTransport().apply { result = AiHttpResult.Responded(AiHttpResponse(status, "{}")) }
            val (useCase, _) = useCase(transport = transport)

            assertEquals(
                PhotoTranslationResult.Failed(failure),
                useCase.translate("Birds fly.", confirmedTextHost = "api.example.com"),
                "status=$status",
            )
        }
    }

    @Test
    fun oversizedOcrTextIsRejectedLocallyWithoutNetwork() = runTest {
        val (useCase, transport) = useCase()

        assertFailsWith<IllegalArgumentException> {
            useCase.translate("a".repeat(PhotoTranslationPromptPolicy.maxTextLength + 1), confirmedTextHost = "api.example.com")
        }
        assertEquals(0, transport.requests.size)
    }

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun assertIs(expected: kotlin.reflect.KClass<*>, value: Any?) {
        assertTrue(expected.isInstance(value), "expected $expected, got $value")
    }
}
