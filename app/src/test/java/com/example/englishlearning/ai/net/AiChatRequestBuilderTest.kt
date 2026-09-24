package com.example.englishlearning.ai.net

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.security.SecretReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiChatRequestBuilderTest {
    private val profile = AiProfile(
        profileId = "p1", displayName = "d", websiteUrl = "https://x.test",
        endpoint = "https://api.test/v1", model = "gpt-x",
        capabilities = setOf(AiCapability.Text),
        secretReference = SecretReference("ai-profile-p1"),
        advancedParameters = com.example.englishlearning.ai.domain.AiAdvancedParameters(
            temperature = 0.3, topP = 0.9, maxTokens = 512, timeoutSeconds = 20,
        ),
    )
    private val prompt = AiPrompt(system = "SYS", user = "USR")

    @Test
    fun appendsTheChatCompletionsPathToTheEndpoint() {
        assertEquals(
            "https://api.test/v1/chat/completions",
            AiChatRequestBuilder.joinEndpoint("https://api.test/v1").getOrThrow(),
        )
        assertEquals(
            "https://api.test/v1/chat/completions",
            AiChatRequestBuilder.joinEndpoint("https://api.test/v1/").getOrThrow(),
        )
    }

    @Test
    fun rejectsAnEndpointThatAlreadyCarriesAQueryOrFragment() {
        assertTrue(AiChatRequestBuilder.joinEndpoint("https://api.test/v1?x=1").isFailure)
        assertTrue(AiChatRequestBuilder.joinEndpoint("https://api.test/v1#f").isFailure)
    }

    @Test
    fun sendsExactlyTwoHeadersAndNothingElse() {
        val request = AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, "sk-secret".toCharArray()).getOrThrow()
        assertEquals(setOf("Content-Type", "Authorization"), request.headers.keys)
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("Bearer sk-secret", request.headers["Authorization"])
    }

    @Test
    fun neverPutsTheKeyIntoTheBody() {
        val request = AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, "sk-secret".toCharArray()).getOrThrow()
        assertFalse(request.body.contains("sk-secret"))
    }

    @Test
    fun bodyMatchesOpenAiChatCompletionsShape() {
        val body = Json.parseToJsonElement(
            AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, "k".toCharArray()).getOrThrow().body,
        ).jsonObject
        assertEquals("gpt-x", body["model"]!!.jsonPrimitive.content)
        assertEquals(512, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(0.3, body["temperature"]!!.jsonPrimitive.double)
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("SYS", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("USR", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun timeoutComesFromTheAdvancedParameters() {
        val request = AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt, "k".toCharArray()).getOrThrow()
        assertEquals(20, request.timeoutSeconds)
    }

    @Test
    fun failsOnAnEndpointThatFailsTheSecurityPolicy() {
        val hostile = profile.copy(endpoint = "http://192.168.1.9/v1")
        assertTrue(AiChatRequestBuilder.build(hostile, hostile.advancedParameters, prompt, "k".toCharArray()).isFailure)
    }
}
