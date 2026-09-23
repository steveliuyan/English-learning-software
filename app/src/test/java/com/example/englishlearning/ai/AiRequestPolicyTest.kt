package com.example.englishlearning.ai

import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.storage.AppErrorException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 出站确认与请求参数白名单的契约。
 *
 * 这些规则要挡住的不是「用户忘了点确认」这种小问题，而是两类实打实的事故：
 * ① 拿 A 域名的确认去调 B 域名（改 Endpoint 后静默复用旧确认）；
 * ② 通过请求参数把任意 Header / 请求体塞进第三方调用。
 */
class AiRequestPolicyTest {
    @Test
    fun firstTextCallAsksForOneHostConfirmation() {
        val requirement = requiredConfirmation(
            profile = profile(),
            payloadKind = AiPayloadKind.Text,
            confirmedTextHost = null,
        ).getOrThrow()

        assertEquals(ConfirmationRequirement.Required("api.example.com", AiPayloadKind.Text), requirement)
    }

    @Test
    fun laterTextCallReusesTheRecordedHostConfirmation() {
        val requirement = requiredConfirmation(
            profile = profile(),
            payloadKind = AiPayloadKind.Text,
            confirmedTextHost = "api.example.com",
        ).getOrThrow()

        assertEquals(ConfirmationRequirement.Satisfied("api.example.com", AiPayloadKind.Text), requirement)
    }

    @Test
    fun textCallToAnotherHostAsksAgainInsteadOfReusingTheOldConfirmation() {
        // 用户换过 Endpoint 之后，旧域名的确认不能顺延到新域名上。
        val requirement = requiredConfirmation(
            profile = profile(endpoint = "https://other.example.com/v1"),
            payloadKind = AiPayloadKind.Text,
            confirmedTextHost = "api.example.com",
        ).getOrThrow()

        assertEquals(ConfirmationRequirement.Required("other.example.com", AiPayloadKind.Text), requirement)
    }

    @Test
    fun everyImageCallAsksEvenWhenTheSameHostWasConfirmedForText() {
        listOf(null, "api.example.com").forEach { confirmedTextHost ->
            val requirement = requiredConfirmation(
                profile = profile(capabilities = setOf(AiCapability.Text, AiCapability.Vision)),
                payloadKind = AiPayloadKind.Image,
                confirmedTextHost = confirmedTextHost,
            ).getOrThrow()

            assertEquals(
                ConfirmationRequirement.Required("api.example.com", AiPayloadKind.Image),
                requirement,
            )
        }
    }

    @Test
    fun invalidEndpointCannotProduceAConfirmationTarget() {
        listOf("http://api.example.com/v1", "https://127.0.0.1/v1").forEach { endpoint ->
            val result = requiredConfirmation(
                profile = profile(endpoint = endpoint),
                payloadKind = AiPayloadKind.Text,
                confirmedTextHost = null,
            )

            assertTrue(result.isFailure, "Endpoint「$endpoint」不合法，不能给出可确认的目标")
            val error = assertFailsWith<AppErrorException> { result.getOrThrow() }
            assertEquals(AppError.InvalidAiConfiguration, error.appError)
        }
    }

    @Test
    fun anUnconfirmedAnswerLeavesTheRequirementStanding() {
        val required = ConfirmationRequirement.Required("api.example.com", AiPayloadKind.Text)

        assertEquals(required, required.resolvedBy(null))
        assertEquals(
            required,
            required.resolvedBy(AiOutboundConfirmation("api.example.com", AiPayloadKind.Text, confirmed = false)),
        )
    }

    @Test
    fun aMatchingConfirmedAnswerSatisfiesTheRequirement() {
        val required = ConfirmationRequirement.Required("api.example.com", AiPayloadKind.Text)

        assertEquals(
            ConfirmationRequirement.Satisfied("api.example.com", AiPayloadKind.Text),
            required.resolvedBy(AiOutboundConfirmation("api.example.com", AiPayloadKind.Text, confirmed = true)),
        )
    }

    @Test
    fun anAnswerForAnotherHostCannotAuthorizeThisOne() {
        val required = ConfirmationRequirement.Required("api.example.com", AiPayloadKind.Text)

        assertEquals(
            required,
            required.resolvedBy(AiOutboundConfirmation("other.example.com", AiPayloadKind.Text, confirmed = true)),
        )
    }

    @Test
    fun anAnswerForTheTextKindCannotAuthorizeAnImageCall() {
        val required = ConfirmationRequirement.Required("api.example.com", AiPayloadKind.Image)

        assertEquals(
            required,
            required.resolvedBy(AiOutboundConfirmation("api.example.com", AiPayloadKind.Text, confirmed = true)),
        )
    }

    @Test
    fun anAlreadySatisfiedRequirementNeedsNoAnswer() {
        val satisfied = ConfirmationRequirement.Satisfied("api.example.com", AiPayloadKind.Text)

        assertEquals(satisfied, satisfied.resolvedBy(null))
    }

    @Test
    fun emptyParametersFallBackToTheDocumentedDefaults() {
        assertEquals(AiAdvancedParameters(), validateRequestParameters(emptyMap()).getOrThrow())
    }

    @Test
    fun acceptsExactlyTheWhitelistedParameters() {
        val validated = validateRequestParameters(
            mapOf(
                "temperature" to 0.3,
                "top_p" to 0.9,
                "max_tokens" to 512,
                "timeout_seconds" to 20,
                "system_prompt_template" to "default-reading-v1",
            ),
        ).getOrThrow()

        assertEquals(0.3, validated.temperature)
        assertEquals(0.9, validated.topP)
        assertEquals(512, validated.maxTokens)
        assertEquals(20, validated.timeoutSeconds)
        assertEquals("default-reading-v1", validated.systemPromptTemplateId)
    }

    @Test
    fun rejectsUnknownParameterNames() {
        // 任意 Header、请求体覆盖、工具调用都必须在这里被挡住，而不是透传到第三方。
        listOf(
            mapOf("extra_headers" to mapOf("Authorization" to "Bearer secret")),
            mapOf("authorization" to "Bearer secret"),
            mapOf("tools" to listOf("shell")),
            mapOf("body" to "{\"raw\":true}"),
            mapOf("stream" to true),
        ).forEach { parameters ->
            val result = validateRequestParameters(parameters)

            assertTrue(result.isFailure, "未知参数「${parameters.keys}」必须被拒绝")
            assertEquals(
                AppError.InvalidAiConfiguration,
                assertFailsWith<AppErrorException> { result.getOrThrow() }.appError,
            )
        }
    }

    @Test
    fun rejectsWrongValueTypes() {
        listOf(
            mapOf("temperature" to "0.5"),
            mapOf("top_p" to "0.9"),
            mapOf("max_tokens" to "512"),
            mapOf("timeout_seconds" to "20"),
            mapOf("system_prompt_template" to 1),
        ).forEach { parameters ->
            assertTrue(
                validateRequestParameters(parameters).isFailure,
                "参数「$parameters」的类型不对，必须被拒绝",
            )
        }
    }

    @Test
    fun rejectsNullValuesForKnownNames() {
        listOf("temperature", "top_p", "max_tokens", "timeout_seconds", "system_prompt_template").forEach { name ->
            assertTrue(
                validateRequestParameters(mapOf(name to null)).isFailure,
                "「$name」被显式传 null 时必须拒绝，不能悄悄退回默认值",
            )
        }
    }

    @Test
    fun rejectsOutOfRangeValuesWithoutThrowing() {
        listOf(
            mapOf("temperature" to -0.1),
            mapOf("temperature" to 2.1),
            mapOf("top_p" to -0.1),
            mapOf("top_p" to 1.1),
            mapOf("max_tokens" to 0),
            mapOf("max_tokens" to 4097),
            mapOf("timeout_seconds" to 4),
            mapOf("timeout_seconds" to 121),
        ).forEach { parameters ->
            // 关键：越界必须走 Result 失败，而不是从 AiAdvancedParameters 的 require 里抛出来。
            val result = validateRequestParameters(parameters)

            assertTrue(result.isFailure, "参数「$parameters」越界时必须返回失败而不是抛异常")
            assertEquals(
                AppError.InvalidAiConfiguration,
                assertFailsWith<AppErrorException> { result.getOrThrow() }.appError,
            )
        }
    }

    @Test
    fun rejectsNonFiniteAndNonIntegralNumbers() {
        listOf(
            mapOf("temperature" to Double.NaN),
            mapOf("temperature" to Double.POSITIVE_INFINITY),
            mapOf("top_p" to Double.NEGATIVE_INFINITY),
            mapOf("max_tokens" to 1024.5),
            mapOf("timeout_seconds" to 30.5),
        ).forEach { parameters ->
            assertFalse(
                validateRequestParameters(parameters).isSuccess,
                "参数「$parameters」不是有限整数/小数，必须被拒绝",
            )
        }
    }

    @Test
    fun rejectsUnsupportedSystemPromptTemplates() {
        val result = validateRequestParameters(mapOf("system_prompt_template" to "custom-injection-v1"))

        assertTrue(result.isFailure)
        assertEquals(
            AppError.InvalidAiConfiguration,
            assertFailsWith<AppErrorException> { result.getOrThrow() }.appError,
        )
    }

    private fun profile(
        endpoint: String = "https://api.example.com/v1",
        capabilities: Set<AiCapability> = setOf(AiCapability.Text),
    ) = AiProfile(
        profileId = "p1",
        displayName = "示例服务",
        websiteUrl = "https://example.com",
        endpoint = endpoint,
        model = "gpt-4o-mini",
        capabilities = capabilities,
        secretReference = SecretReference("ai-profile-p1"),
    )
}
