package com.example.englishlearning.ai.net

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiAdvancedParameters
import com.example.englishlearning.core.security.SecretReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TtsRequestBuilderTest {
    private val profile = AiProfile(
        profileId = "p1", displayName = "d", websiteUrl = "https://x.test",
        endpoint = "https://api.test/v1", model = "tts-1",
        capabilities = setOf(AiCapability.Speech),
        secretReference = SecretReference("ai-profile-p1"),
        advancedParameters = AiAdvancedParameters(timeoutSeconds = 20),
    )

    @Test
    fun appendsAudioSpeechPath() {
        assertEquals("https://api.test/v1/audio/speech", TtsRequestBuilder.joinEndpoint(profile.endpoint).getOrThrow())
    }

    @Test
    fun buildsWhitelistedHeadersAndBody() {
        val request = TtsRequestBuilder.build(profile, "Hello", "alloy", "mp3", "sk-secret".toCharArray()).getOrThrow()
        assertEquals(setOf("Content-Type", "Authorization"), request.headers.keys)
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("Bearer sk-secret", request.headers["Authorization"])
        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals(setOf("model", "input", "voice", "response_format"), body.keys)
        assertEquals("tts-1", body["model"]!!.jsonPrimitive.content)
        assertEquals("Hello", body["input"]!!.jsonPrimitive.content)
        assertEquals("alloy", body["voice"]!!.jsonPrimitive.content)
        assertEquals("mp3", body["response_format"]!!.jsonPrimitive.content)
        assertFalse(request.body.contains("sk-secret"))
        assertEquals(20, request.timeoutSeconds)
    }

    @Test
    fun rejectsProfileWithoutSpeechCapability() {
        val textOnly = profile.copy(capabilities = setOf(AiCapability.Text))
        val result = TtsRequestBuilder.build(textOnly, "Hello", "alloy", "mp3", "secret".toCharArray())
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()!!.message.orEmpty().contains("secret"))
    }

    @Test
    fun rejectsBlankInput() {
        val result = TtsRequestBuilder.build(profile, "  \n", "alloy", "mp3", "secret".toCharArray())
        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsEndpointQueryAndHostileEndpoint() {
        assertTrue(TtsRequestBuilder.joinEndpoint("https://api.test/v1?x=1").isFailure)
        assertTrue(TtsRequestBuilder.joinEndpoint("http://192.168.1.9/v1").isFailure)
    }
}
