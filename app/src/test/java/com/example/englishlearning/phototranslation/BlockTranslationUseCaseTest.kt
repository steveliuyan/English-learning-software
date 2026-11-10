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
 * F3-04 编排契约：确认在任何字节发出之前、每次调用都要确认；上行只有块文本；失败不产出半份译文。
 */
class BlockTranslationUseCaseTest {

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
        var result: AiHttpResult =
            AiHttpResult.Responded(
                AiHttpResponse(
                    200,
                    """{"choices":[{"message":{"content":"[{\"id\":1,\"translation\":\"甲\"},{\"id\":2,\"translation\":\"乙\"}]"}}]}""",
                ),
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
    ): Pair<BlockTranslationUseCase, RecordingTransport> {
        val useCase =
            BlockTranslationUseCase(
                defaultTextProfile = DefaultTextProfileResolver { resolverResult },
                secrets = AiProfileSecretUseCase(FakeSecretStore(hasKey)),
                transport = transport,
            )
        return useCase to transport
    }

    @Test
    fun noDefaultProfileIsNotConfiguredWithoutAnyNetworkCall() = runTest {
        val (useCase, transport) = useCase(resolverResult = DefaultTextProfileResult.NoSelection)

        val result = useCase.translate(listOf("Hello"), confirmedTextHosts = emptySet())

        assertEquals(BlockTranslationResult.NotConfigured(BlockTranslationNotConfiguredReason.NoDefaultProfile), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun missingKeyIsNotConfiguredWithoutAnyNetworkCall() = runTest {
        val (useCase, transport) = useCase(hasKey = false)

        val result = useCase.translate(listOf("Hello"), confirmedTextHosts = emptySet())

        assertEquals(BlockTranslationResult.NotConfigured(BlockTranslationNotConfiguredReason.DefaultProfileUnavailable), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun firstCallDemandsConfirmationBeforeAnyByteLeaves() = runTest {
        val (useCase, transport) = useCase()

        val result = useCase.translate(listOf("Hello", "Good morning"), confirmedTextHosts = emptySet())

        assertEquals(BlockTranslationResult.NeedsConfirmation("api.example.com", AiPayloadKind.Text), result)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun confirmedHostProducesBlockMapWithNumberedIds() = runTest {
        val (useCase, transport) = useCase()

        val result = useCase.translate(listOf("Hello", "Good morning"), confirmedTextHosts = setOf("api.example.com"))

        assertEquals(1, transport.requests.size)
        assertTrue(result is BlockTranslationResult.Translated)
        val translated = result as BlockTranslationResult.Translated
        assertEquals(mapOf(1 to "甲", 2 to "乙"), translated.translations)
        assertEquals(emptyList(), translated.missing)
    }

    @Test
    fun blankBlocksAreRejectedLocallyWithoutNetwork() = runTest {
        val (useCase, transport) = useCase()

        assertFailsWith<IllegalArgumentException> {
            useCase.translate(listOf("Hello", "  "), confirmedTextHosts = setOf("api.example.com"))
        }
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun networkTimeoutAndCancelMapToDistinctOutcomes() = runTest {
        val offline = RecordingTransport().apply { result = AiHttpResult.NetworkUnavailable }
        val (offlineUseCase, _) = useCase(transport = offline)
        assertEquals(
            BlockTranslationResult.Failed(AiFailure.NetworkUnavailable),
            offlineUseCase.translate(listOf("Hello"), confirmedTextHosts = setOf("api.example.com")),
        )

        val timedOut = RecordingTransport().apply { result = AiHttpResult.TimedOut }
        val (timedOutUseCase, _) = useCase(transport = timedOut)
        assertEquals(
            BlockTranslationResult.Failed(AiFailure.Timeout),
            timedOutUseCase.translate(listOf("Hello"), confirmedTextHosts = setOf("api.example.com")),
        )

        val cancelled = RecordingTransport().apply { result = AiHttpResult.Cancelled }
        val (cancelledUseCase, _) = useCase(transport = cancelled)
        assertFailsWith<CancellationException> {
            cancelledUseCase.translate(listOf("Hello"), confirmedTextHosts = setOf("api.example.com"))
        }
    }
}
