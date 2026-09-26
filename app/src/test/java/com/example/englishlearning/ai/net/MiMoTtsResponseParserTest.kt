package com.example.englishlearning.ai.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MiMoTtsResponseParserTest {
    @Test
    fun `extracts base64 wav audio from non-streaming response`() {
        val wav = byteArrayOf(1, 2, 3, 4, 5)
        val base64 = java.util.Base64.getEncoder().encodeToString(wav)
        val body = """
            {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"","audio":{"data":"$base64"}},"finish_reason":"stop"}]}
        """.trimIndent()

        val decoded = MiMoTtsResponseParser.extractWav(body.encodeToByteArray())
        assertEquals(wav.toList(), decoded?.toList())
    }

    @Test
    fun `returns null when audio data is missing`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"no audio"}}]}"""
        assertNull(MiMoTtsResponseParser.extractWav(body.encodeToByteArray()))
    }

    @Test
    fun `returns null when choices are empty`() {
        val body = """{"choices":[]}"""
        assertNull(MiMoTtsResponseParser.extractWav(body.encodeToByteArray()))
    }

    @Test
    fun `returns null on malformed json`() {
        assertNull(MiMoTtsResponseParser.extractWav("not json".encodeToByteArray()))
    }

    @Test
    fun `returns null when data is not valid base64`() {
        val body = """{"choices":[{"message":{"audio":{"data":"!!!not-base64!!!"}}}]}"""
        assertNull(MiMoTtsResponseParser.extractWav(body.encodeToByteArray()))
    }
}
