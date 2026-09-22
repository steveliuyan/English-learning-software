package com.example.englishlearning.ai.domain

import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.AppErrorException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiEndpointPolicyTest {
    @Test
    fun acceptsHttpsPublicHost() {
        val result = validateEndpoint("https://api.example.com/v1")

        assertTrue(result.isSuccess)
        assertEquals("api.example.com", result.getOrThrow().host)
    }

    @Test
    fun rejectsNonHttpsSchemes() {
        listOf("http://api.example.com", "file:///tmp/key", "content://local/item").forEach { endpoint ->
            val error = assertFailsWith<AppErrorException> { validateEndpoint(endpoint).getOrThrow() }
            assertEquals(AppError.InvalidAiConfiguration, error.appError)
        }
    }

    @Test
    fun rejectsLoopbackPrivateAndCredentialEndpoints() {
        listOf(
            "https://localhost/v1",
            "https://127.0.0.1/v1",
            "https://10.0.0.4/v1",
            "https://172.16.0.4/v1",
            "https://192.168.1.4/v1",
            "https://[::1]/v1",
            "https://user:secret@api.example.com/v1",
        ).forEach { endpoint ->
            val error = assertFailsWith<AppErrorException> { validateEndpoint(endpoint).getOrThrow() }
            assertEquals(AppError.InvalidAiConfiguration, error.appError)
        }
    }

    @Test
    fun rejectsMissingHostAndQueryCredentials() {
        listOf("https:///v1", "https://api.example.com/v1?api_key=secret").forEach { endpoint ->
            val error = assertFailsWith<AppErrorException> { validateEndpoint(endpoint).getOrThrow() }
            assertEquals(AppError.InvalidAiConfiguration, error.appError)
        }
    }
}
