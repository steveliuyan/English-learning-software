package com.example.englishlearning.language.domain

import java.security.MessageDigest
import java.util.Locale

/** Stable identity for one generated pronunciation asset. */
data class PronunciationCacheKey(
    val text: String,
    val accent: PronunciationAccent,
    val providerId: String,
    val model: String,
    val voice: String,
    val requestVersion: Int = REQUEST_VERSION,
) {
    private val normalizedText: String
        get() = text.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    fun cacheId(): String {
        val canonical = listOf(
            normalizedText,
            accent.name,
            providerId.trim(),
            model.trim(),
            voice.trim(),
            requestVersion.toString(),
        ).joinToString("\\u001f")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val REQUEST_VERSION = 1
    }
}
