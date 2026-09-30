package com.example.englishlearning.imagegen

import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.core.security.SecretReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * images/generations 请求构造契约：URL 由本应用决定（`{base}/images/generations`），
 * body 只有 model/prompt/n/size 四个白名单字段；Key 只进 Authorization 头，永不进 body。
 */
class ImageGenerationRequestBuilderTest {

    private val profile = AiProfile(
        profileId = "p-img",
        displayName = "生图",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "img-x",
        capabilities = setOf(AiCapability.ImageGeneration),
        secretReference = SecretReference("ai-profile-p-img"),
    )

    @Test
    fun urlIsEndpointPlusImagesGenerations() {
        val request = ImageGenerationRequestBuilder.build(profile, "an apple", "k3y".toCharArray()).getOrThrow()
        assertEquals("https://api.example.com/v1/images/generations", request.url)
    }

    @Test
    fun endpointWithQueryOrFragmentIsRejected() {
        val queryProfile = profile.copy(endpoint = "https://api.example.com/v1?x=1")
        assertTrue(ImageGenerationRequestBuilder.build(queryProfile, "an apple", "k3y".toCharArray()).isFailure)
        val fragmentProfile = profile.copy(endpoint = "https://api.example.com/v1#frag")
        assertTrue(ImageGenerationRequestBuilder.build(fragmentProfile, "an apple", "k3y".toCharArray()).isFailure)
    }

    @Test
    fun bodyCarriesExactlyTheWhitelistedFields() {
        val request = ImageGenerationRequestBuilder.build(profile, "an apple", "k3y".toCharArray()).getOrThrow()
        val body = Json.parseToJsonElement(request.body) as JsonObject
        assertEquals(setOf("model", "prompt", "n", "size"), body.keys)
        assertEquals("img-x", body["model"]!!.jsonPrimitive.content)
        assertEquals("an apple", body["prompt"]!!.jsonPrimitive.content)
        assertEquals(1, body["n"]!!.jsonPrimitive.content.toInt())
        assertEquals("1024x1024", body["size"]!!.jsonPrimitive.content)
    }

    @Test
    fun apiKeyOnlyGoesIntoTheAuthorizationHeader() {
        val request = ImageGenerationRequestBuilder.build(profile, "an apple", "k3y".toCharArray()).getOrThrow()
        assertEquals(mapOf("Content-Type" to "application/json", "Authorization" to "Bearer k3y"), request.headers)
        assertTrue(!request.body.contains("k3y"))
    }

    @Test
    fun timeoutComesFromTheProfileParameters() {
        val request = ImageGenerationRequestBuilder.build(profile, "an apple", "k3y".toCharArray()).getOrThrow()
        assertEquals(profile.advancedParameters.timeoutSeconds, request.timeoutSeconds)
    }
}
