package com.example.englishlearning.ai.net

import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.core.security.SecretReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiMoTtsRequestBuilderTest {
    private val profile = AiProfile(
        profileId = "mimo-1",
        displayName = "小米 MiMo TTS",
        websiteUrl = "https://mimo.mi.com",
        endpoint = "https://api.xiaomimimo.com/v1/chat/completions",
        model = "mimo-v2.5-tts",
        capabilities = setOf(AiCapability.Speech),
        secretReference = SecretReference("ai-profile-mimo-1"),
        advancedParameters = AiAdvancedParameters(timeoutSeconds = 45),
        providerKind = AiProviderKind.XIAOMI_MIMO,
    )

    @Test
    fun `builds chat completions body with assistant role and wav audio`() {
        val request = MiMoTtsRequestBuilder.build(profile, "hello", "key".toCharArray()).getOrThrow()

        assertEquals("https://api.xiaomimimo.com/v1/chat/completions", request.url)
        assertEquals("POST", request.method)
        assertEquals(45, request.timeoutSeconds)
        val body = request.body
        assertTrue(body.contains("\"model\":\"mimo-v2.5-tts\""))
        assertTrue(body.contains("\"role\":\"assistant\""))
        assertTrue(body.contains("\"content\":\"hello\""))
        assertTrue(body.contains("\"audio\":{\"format\":\"wav\",\"voice\":"))
        assertTrue(body.contains("\"stream\":false"))
        // 合成文本只能出现在 assistant 消息里，不允许任何 user/指令位。
        assertTrue(!body.contains("\"role\":\"user\""))
    }

    @Test
    fun `authenticates with api-key header instead of bearer`() {
        val request = MiMoTtsRequestBuilder.build(profile, "hello", "secret-key".toCharArray()).getOrThrow()

        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("secret-key", request.headers["api-key"])
        assertTrue(request.headers.keys.none { it.equals("Authorization", ignoreCase = true) })
        // 密钥只允许出现在 api-key 头里，不允许写进请求体或 URL。
        assertTrue(!request.body.contains("secret-key"))
        assertTrue(!request.url.contains("secret-key"))
    }

    @Test
    fun `explicit profile voice overrides language auto selection`() {
        val request = MiMoTtsRequestBuilder.build(profile.copy(voice = "苏打"), "hello world", "key".toCharArray()).getOrThrow()
        assertTrue(request.body.contains("\"voice\":\"苏打\""))

        val chinese = MiMoTtsRequestBuilder.build(profile.copy(voice = "Milo"), "你好世界", "key".toCharArray()).getOrThrow()
        assertTrue(chinese.body.contains("\"voice\":\"Milo\""))
    }

    @Test
    fun `empty voice falls back to language auto selection`() {
        val request = MiMoTtsRequestBuilder.build(profile.copy(voice = ""), "你好世界", "key".toCharArray()).getOrThrow()
        assertTrue(request.body.contains("\"voice\":\"冰糖\""))
    }

    @Test
    fun `english text selects Mia voice`() {
        val request = MiMoTtsRequestBuilder.build(profile, "hello world", "key".toCharArray()).getOrThrow()
        assertTrue(request.body.contains("\"voice\":\"Mia\""))
    }

    @Test
    fun `chinese text selects bingtang voice`() {
        val request = MiMoTtsRequestBuilder.build(profile, "你好世界", "key".toCharArray()).getOrThrow()
        assertTrue(request.body.contains("\"voice\":\"冰糖\""))
    }

    @Test
    fun `mixed text follows cjk presence`() {
        val request = MiMoTtsRequestBuilder.build(profile, "apple 苹果", "key".toCharArray()).getOrThrow()
        assertTrue(request.body.contains("\"voice\":\"冰糖\""))
    }

    @Test
    fun `rejects blank text`() {
        assertTrue(MiMoTtsRequestBuilder.build(profile, "  ", "key".toCharArray()).isFailure)
    }

    @Test
    fun `rejects profile without speech capability`() {
        val textOnly = profile.copy(capabilities = setOf(AiCapability.Text))
        assertTrue(MiMoTtsRequestBuilder.build(textOnly, "hello", "key".toCharArray()).isFailure)
    }

    @Test
    fun `rejects insecure endpoint`() {
        val http = profile.copy(endpoint = "http://api.xiaomimimo.com/v1/chat/completions")
        assertTrue(MiMoTtsRequestBuilder.build(http, "hello", "key".toCharArray()).isFailure)
    }

    @Test
    fun `rejects endpoint with query string`() {
        val withQuery = profile.copy(endpoint = "https://api.xiaomimimo.com/v1/chat/completions?token=abc")
        assertTrue(MiMoTtsRequestBuilder.build(withQuery, "hello", "key".toCharArray()).isFailure)
    }
}
