package com.example.englishlearning.language.infrastructure

import android.content.Context
import com.example.englishlearning.language.domain.PronunciationAudioCache
import com.example.englishlearning.language.domain.PronunciationCacheKey
import java.io.File
import java.security.MessageDigest

/** App-private pronunciation cache. A corrupt entry is treated as a miss and removed. */
class FilePronunciationAudioCache(context: Context) : PronunciationAudioCache {
    private val root = File(context.filesDir, "pronunciation-cache").also { it.mkdirs() }

    override suspend fun get(key: PronunciationCacheKey): ByteArray? {
        val file = fileFor(key)
        if (!file.isFile) return null
        return runCatching {
            val bytes = file.readBytes()
            val expected = File(root, "${file.nameWithoutExtension}.sha256")
            if (bytes.isNotEmpty() && expected.readText() == sha256(bytes)) bytes else null
        }.getOrNull() ?: run {
            removeFiles(file)
            null
        }
    }

    override suspend fun put(key: PronunciationCacheKey, bytes: ByteArray) {
        require(bytes.isNotEmpty())
        val cacheId = key.cacheId()
        val target = File(root, "$cacheId.wav")
        val temporary = File(root, "$cacheId.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            error("pronunciation cache publication failed")
        }
        File(root, "$cacheId.sha256").writeText(sha256(bytes))
    }

    private fun fileFor(key: PronunciationCacheKey): File {
        val cacheId = key.cacheId()
        return File(root, "$cacheId.wav")
    }

    private fun removeFiles(file: File) {
        file.delete()
        File(root, "${file.nameWithoutExtension}.sha256").delete()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
