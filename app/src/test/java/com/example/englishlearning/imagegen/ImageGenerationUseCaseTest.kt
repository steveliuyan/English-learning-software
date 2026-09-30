package com.example.englishlearning.imagegen

import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.AiOutboundConfirmation
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.DefaultImageProfileResult
import com.example.englishlearning.ai.DefaultImageProfileResolver
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
 * 生图用例的编排契约：配置检查 → **每次**出站确认（图片不适用按域名记住）→
 * 构造 images/generations 请求 → 出站 → 解析。Key 以 CharArray 流转并在结束后清零。
 */
class ImageGenerationUseCaseTest {

    private val profile = AiProfile(
        profileId = "p-img",
        displayName = "生图",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "img-x",
        capabilities = setOf(AiCapability.ImageGeneration),
        secretReference = SecretReference("ai-profile-p-img"),
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
            AiHttpResponse(200, """{"data":[{"b64_json":"QUJD"}]}"""),
        )

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            requests += request
            return result
        }
    }

    private fun useCase(
        resolverResult: DefaultImageProfileResult = DefaultImageProfileResult.Selected(profile),
        hasKey: Boolean = true,
        transport: RecordingTransport = RecordingTransport(),
    ): Triple<ImageGenerationUseCase, RecordingTransport, FakeSecretStore> {
        val secretStore = FakeSecretStore(hasKey)
        val useCase = ImageGenerationUseCase(
            defaultImageProfile = DefaultImageProfileResolver { resolverResult },
            secrets = AiProfileSecretUseCase(secretStore),
            transport = transport,
        )
        return Triple(useCase, transport, secretStore)
    }

    @Test
    fun noDefaultImageProfileIsNotConfiguredWithoutAnyNetworkCall() = runTest {
        val (useCase, transport, _) = useCase(resolverResult = DefaultImageProfileResult.NoSelection)

        val result = useCase.generate("a photo of an apple", answer = null)

        assertEquals(ImageGenerationResult.NotConfigured(ImageStudioNotConfiguredReason.NoDefaultProfile), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun unavailableProfileIsNotConfigured() = runTest {
        val (useCase, transport, _) = useCase(resolverResult = DefaultImageProfileResult.Unavailable)

        val result = useCase.generate("a photo of an apple", answer = null)

        assertEquals(
            ImageGenerationResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable),
            result,
        )
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun missingKeyIsNotConfigured() = runTest {
        val (useCase, transport, _) = useCase(hasKey = false)

        val result = useCase.generate("a photo of an apple", answer = null)

        assertEquals(
            ImageGenerationResult.NotConfigured(ImageStudioNotConfiguredReason.DefaultProfileUnavailable),
            result,
        )
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun everyCallNeedsConfirmationBeforeAnyByteLeaves() = runTest {
        val (useCase, transport, _) = useCase()

        val first = useCase.generate("a photo of an apple", answer = null)
        val second = useCase.generate("a photo of an apple", answer = null)

        assertEquals(ImageGenerationResult.NeedsConfirmation("api.example.com", AiPayloadKind.Image), first)
        assertEquals(ImageGenerationResult.NeedsConfirmation("api.example.com", AiPayloadKind.Image), second)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun wrongHostOrPayloadKindAnswerDoesNotSatisfyTheRequirement() = runTest {
        val (useCase, transport, _) = useCase()

        val wrongHost = useCase.generate(
            "a photo of an apple",
            answer = AiOutboundConfirmation("other.example.com", AiPayloadKind.Image, confirmed = true),
        )
        val textKind = useCase.generate(
            "a photo of an apple",
            answer = AiOutboundConfirmation("api.example.com", AiPayloadKind.Text, confirmed = true),
        )
        val declined = useCase.generate(
            "a photo of an apple",
            answer = AiOutboundConfirmation("api.example.com", AiPayloadKind.Image, confirmed = false),
        )

        assertEquals(ImageGenerationResult.NeedsConfirmation("api.example.com", AiPayloadKind.Image), wrongHost)
        assertEquals(ImageGenerationResult.NeedsConfirmation("api.example.com", AiPayloadKind.Image), textKind)
        assertEquals(ImageGenerationResult.NeedsConfirmation("api.example.com", AiPayloadKind.Image), declined)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun matchingAnswerSendsRequestToImagesGenerationsAndReturnsBase64() = runTest {
        val (useCase, transport, _) = useCase()

        val result = useCase.generate(
            "a photo of an apple",
            answer = AiOutboundConfirmation("api.example.com", AiPayloadKind.Image, confirmed = true),
        )

        assertEquals(ImageGenerationResult.Generated(GeneratedImage.Base64("QUJD")), result)
        assertEquals(1, transport.requests.size)
        val request = transport.requests.single()
        assertEquals("https://api.example.com/v1/images/generations", request.url)
        assertTrue(request.body.contains("a photo of an apple"))
    }

    @Test
    fun httpsUrlResponseIsReturnedAsUrl() = runTest {
        val transport = RecordingTransport().apply {
            result = AiHttpResult.Responded(
                AiHttpResponse(200, """{"data":[{"url":"https://cdn.example.com/img.png"}]}"""),
            )
        }
        val (useCase, _, _) = useCase(transport = transport)

        val result = useCase.generate(
            "a photo of an apple",
            answer = AiOutboundConfirmation("api.example.com", AiPayloadKind.Image, confirmed = true),
        )

        assertEquals(ImageGenerationResult.Generated(GeneratedImage.Url("https://cdn.example.com/img.png")), result)
    }

    @Test
    fun transportFailuresMapToDomainFailures() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.NetworkUnavailable }
        val (networkCase, _, _) = useCase(transport = transport)
        assertEquals(
            ImageGenerationResult.Failed(AiFailure.NetworkUnavailable),
            networkCase.generate("a photo", answer = AiOutboundConfirmation("api.example.com", AiPayloadKind.Image, true)),
        )

        val timeoutTransport = RecordingTransport().apply { result = AiHttpResult.TimedOut }
        val (timeoutCase, _, _) = useCase(transport = timeoutTransport)
        assertEquals(
            ImageGenerationResult.Failed(AiFailure.Timeout),
            timeoutCase.generate("a photo", answer = AiOutboundConfirmation("api.example.com", AiPayloadKind.Image, true)),
        )

        val unauthorizedTransport = RecordingTransport().apply {
            result = AiHttpResult.Responded(AiHttpResponse(401, "denied"))
        }
        val (unauthorizedCase, _, _) = useCase(transport = unauthorizedTransport)
        assertEquals(
            ImageGenerationResult.Failed(AiFailure.Unauthorized),
            unauthorizedCase.generate("a photo", answer = AiOutboundConfirmation("api.example.com", AiPayloadKind.Image, true)),
        )
    }

    @Test
    fun cancelledTransportIsRethrownAsCancellation() = runTest {
        val transport = RecordingTransport().apply { result = AiHttpResult.Cancelled }
        val (useCase, _, _) = useCase(transport = transport)

        assertFailsWith<CancellationException> {
            useCase.generate("a photo", answer = AiOutboundConfirmation("api.example.com", AiPayloadKind.Image, true))
        }
    }

    @Test
    fun keyIsZeroedAfterTheFlowEnds() = runTest {
        val (useCase, _, secretStore) = useCase()

        useCase.generate(
            "a photo of an apple",
            answer = AiOutboundConfirmation("api.example.com", AiPayloadKind.Image, confirmed = true),
        )

        assertTrue(secretStore.lastRead!!.all { it == '\u0000' })
    }

    @Test
    fun blankOrOverlongPromptIsAProgrammingError() = runTest {
        val (useCase, transport, _) = useCase()

        assertFailsWith<IllegalArgumentException> { useCase.generate("   ", answer = null) }
        assertFailsWith<IllegalArgumentException> {
            useCase.generate("a".repeat(DrawingPromptPolicy.maxPromptLength + 1), answer = null)
        }
        assertEquals(0, transport.requests.size)
    }
}
