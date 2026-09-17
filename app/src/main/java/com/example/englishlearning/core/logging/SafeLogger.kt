package com.example.englishlearning.core.logging

interface SafeLogger {
    fun info(
        event: String,
        attributes: Map<String, Any?> = emptyMap(),
    )
}

fun interface LogSink {
    fun write(line: String)
}

class SanitizingSafeLogger(
    private val sink: LogSink,
) : SafeLogger {
    override fun info(
        event: String,
        attributes: Map<String, Any?>,
    ) {
        val safeAttributes =
            attributes
                .asSequence()
                .filter { (key, value) -> key in allowedKeys && isSafeKey(key) && isSafeScalar(value) }
                .sortedBy { (key, _) -> key }
                .joinToString(separator = " ") { (key, value) -> "$key=$value" }
        val line = listOf("event=$event", safeAttributes).filter(String::isNotBlank).joinToString(" ")

        sink.write(line)
    }

    private fun isSafeKey(key: String): Boolean {
        return sensitiveKeyFragments.none { fragment -> key.contains(fragment, ignoreCase = true) }
    }

    private fun isSafeScalar(value: Any?): Boolean =
        value == null ||
            value is String ||
            value is Number ||
            value is Boolean ||
            value is Enum<*>

    private companion object {
        val allowedKeys = setOf("attempt", "profileId")

        val sensitiveKeyFragments =
            setOf(
                "authorization",
                "api key",
                "apikey",
                "token",
                "password",
                "private key",
                "privatekey",
                "secret",
                "alias",
                "path",
                "hash",
            )
    }
}
