package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.PronunciationAccent
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * 合成音频缓存契约：尽力而为的旁路存储。
 * 读失败 = 未命中（返回 null，绝不抛）；写失败 = 静默（绝不打断播放）。
 */
interface SynthesizedAudioCache {
    suspend fun read(text: String, accent: PronunciationAccent): ByteArray?

    suspend fun write(text: String, accent: PronunciationAccent, wav: ByteArray)
}

/**
 * 文件版合成音频缓存：按「口音 + 文本」的 SHA-256 命名存 WAV，
 * 供词卡发音复用——重复点击不再重跑 Kokoro 推理（2026-10-13 真机反馈）。
 *
 * 目录在应用私有 filesDir 下；超出 [maxTotalBytes] 按最旧修改时间优先逐出。
 * 这里只缓存可再生的合成产物，不是用户内容：不进备份、不参与完整性台账。
 */
class FileSynthesizedAudioCache(
    private val root: File,
    private val maxTotalBytes: Long = DEFAULT_BUDGET_BYTES,
) : SynthesizedAudioCache {
    companion object {
        private const val DEFAULT_BUDGET_BYTES = 64L * 1024 * 1024

        // 口音与词干之间的不可见分隔符：防止「AME RI...」这类拼接歧义撞 key。
        private val KEY_JOINER: String = String(Character.toChars(0))
    }

    override suspend fun read(text: String, accent: PronunciationAccent): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = fileFor(text, accent)
                if (!file.isFile || file.length() == 0L) null else file.readBytes()
            }.getOrNull()
        }

    override suspend fun write(text: String, accent: PronunciationAccent, wav: ByteArray) {
        if (wav.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                if (!ensureDirectory()) return@runCatching
                val file = fileFor(text, accent)
                val temporary = File(file.parentFile, file.name + ".tmp")
                temporary.delete()
                temporary.writeBytes(wav)
                if (!temporary.renameTo(file)) {
                    file.delete()
                    temporary.renameTo(file)
                }
                enforceBudget(file)
            }
        }
    }

    private fun ensureDirectory(): Boolean = root.isDirectory || root.mkdirs()

    private fun fileFor(text: String, accent: PronunciationAccent): File =
        File(root, sha256Hex(accent.name + KEY_JOINER + text) + ".wav")

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** 清掉残留临时文件；超出预算从最旧 WAV 逐出，刚写入的 [keep] 永不逐出。 */
    private fun enforceBudget(keep: File) {
        val files = root.listFiles()?.filter { it.isFile }.orEmpty()
        files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
        val entries = files.filter { it.name.endsWith(".wav") }
        var total = entries.sumOf { it.length() }
        if (total <= maxTotalBytes) return
        for (entry in entries.filter { it != keep }.sortedBy { it.lastModified() }) {
            if (total <= maxTotalBytes) break
            total -= entry.length()
            entry.delete()
        }
    }
}
