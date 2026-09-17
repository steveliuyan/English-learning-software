package com.example.englishlearning.core.logging

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SafeLoggerTest {
    @Test
    fun `logger retains approved scalar attributes while rejecting sensitive and binary values`() {
        val sink = RecordingLogSink()
        val logger = SanitizingSafeLogger(sink)

        logger.info(
            event = "ai_profile_saved",
            attributes =
                mapOf(
                    "Authorization" to "Bearer secret-token",
                    "apiKey" to "sk-secret",
                    "password" to "p@ss",
                    "private key" to "private-material",
                    "imageBytes" to byteArrayOf(1, 2, 3),
                    "profileId" to "profile-1",
                    "unapprovedStatus" to "not-for-log",
                ),
        )

        val line = sink.requireLine()
        assertContains(line, "event=ai_profile_saved")
        assertContains(line, "profileId=profile-1")
        assertFalse(line.contains("secret-token"))
        assertFalse(line.contains("sk-secret"))
        assertFalse(line.contains("p@ss"))
        assertFalse(line.contains("private-material"))
        assertFalse(line.contains("1, 2, 3"))
        assertFalse(line.contains("not-for-log"))
    }

    @Test
    fun `logger rejects mixed case sensitive key fragments`() {
        val sink = RecordingLogSink()
        val logger = SanitizingSafeLogger(sink)

        logger.info(
            event = "sensitive_attributes_received",
            attributes =
                mapOf(
                    "TOKEN" to "token-sentinel",
                    "SeCrEt" to "secret-sentinel",
                    "KEY_ALIAS" to "alias-sentinel",
                    "filePATH" to "path-sentinel",
                    "contentHASH" to "hash-sentinel",
                    "profileId" to "profile-1",
                ),
        )

        val line = sink.requireLine()
        assertContains(line, "profileId=profile-1")
        assertFalse(line.contains("token-sentinel"))
        assertFalse(line.contains("secret-sentinel"))
        assertFalse(line.contains("alias-sentinel"))
        assertFalse(line.contains("path-sentinel"))
        assertFalse(line.contains("hash-sentinel"))
    }

    @Test
    fun `logger does not serialize throwable messages character arrays or sensitive key names`() {
        val sink = RecordingLogSink()
        val logger = SanitizingSafeLogger(sink)

        logger.info(
            event = "profile_updated",
            attributes =
                mapOf(
                    "reason" to IllegalStateException("exception-secret"),
                    "displayName" to charArrayOf('p', '@', 's', 's'),
                    "assetPath" to "/data/user/0/private-file",
                    "contentHash" to "hash-secret",
                    "alias" to "key-alias",
                    "attempt" to 2,
                ),
        )

        val line = sink.requireLine()
        assertContains(line, "attempt=2")
        assertFalse(line.contains("exception-secret"))
        assertFalse(line.contains("p@ss"))
        assertFalse(line.contains("/data/user/0/private-file"))
        assertFalse(line.contains("hash-secret"))
        assertFalse(line.contains("key-alias"))
    }

    private class RecordingLogSink : LogSink {
        private var line: String? = null

        override fun write(line: String) {
            this.line = line
        }

        fun requireLine(): String = requireNotNull(line)
    }
}
