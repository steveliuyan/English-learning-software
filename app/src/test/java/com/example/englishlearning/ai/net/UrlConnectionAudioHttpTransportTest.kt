package com.example.englishlearning.ai.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UrlConnectionAudioHttpTransportTest {
    private val transport = UrlConnectionAudioHttpTransport(Dispatchers.IO, maxResponseBytes = 8)

    @Test
    fun rejectsNonHttpsUrlsBeforeOpeningConnection() = runTest {
        val result = transport.send(AudioHttpRequest("http://127.0.0.1:1/audio"))
        assertEquals(AudioHttpResult.InsecureUrl, result)
    }

    @Test
    fun reportsConnectionFailureForHttpsEndpoint() = runTest {
        val result = transport.send(AudioHttpRequest("https://127.0.0.1:1/audio", timeoutSeconds = 1))
        assertEquals(AudioHttpResult.NetworkUnavailable, result)
    }
}
