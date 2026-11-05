package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.PronunciationAccent
import com.example.englishlearning.language.domain.PronunciationAudioMetadata
import com.example.englishlearning.language.domain.PronunciationContentKind
import com.example.englishlearning.language.domain.PronunciationEntityRef
import com.example.englishlearning.language.domain.SynthesizedAudioProvenance
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * 合成音频缓存契约：尽力而为的旁路存储。
 * 读失败 = 未命中（返回 null，绝不抛）；写失败 = 静默（绝不打断播放）。
 *
 * 「尽力而为」修饰的是 **IO 故障**，不是完整性：台账缺失/摘要不符/大小不符一律按未命中处理
 * （Spec 03 F3-02、AGENTS.md「损坏或替换后不得播放」）。
 */
interface SynthesizedAudioCache {
    suspend fun read(text: String, accent: PronunciationAccent): ByteArray?

    /**
     * [entity] 是触发这次生成的实体（词卡主键等），只写进台账、**不进缓存键**（决策 D-8）。
     * 缺省按 `Unattached` 落盘，好让"调用方没交代"在台账里看得出来，而不是与某个具体屏混同。
     */
    suspend fun write(text: String, accent: PronunciationAccent, wav: ByteArray, entity: PronunciationEntityRef? = null)
}

/**
 * 文件版合成音频缓存：按「口音 + 文本」的 SHA-256 命名存 WAV，
 * 供词卡发音复用——重复点击不再重跑 Kokoro 推理（2026-10-13 真机反馈）。
 *
 * 每条 WAV 旁配一份 [PronunciationAudioMetadata] 台账（`.json`）：内容摘要、大小、来源
 * （端侧 providerId + 模型版本 + 口音对应音色）、创建时间与内容类别。两份文件同批原子发布，
 * 任一步失败就两份都不留——**不允许出现「只有 WAV」的半条缓存**。
 *
 * 目录在应用私有 filesDir 下；超出 [maxTotalBytes] 按最旧修改时间优先成对逐出。
 * 这里只缓存可再生的合成产物，不是用户内容：不进备份、不参与备份完整性台账（决策 D-4/D-6）。
 */
class FileSynthesizedAudioCache(
    private val root: File,
    private val maxTotalBytes: Long = DEFAULT_BUDGET_BYTES,
    private val provenance: SynthesizedAudioProvenance = SynthesizedAudioProvenance.KokoroOnDevice,
    private val clock: () -> Long = System::currentTimeMillis,
) : SynthesizedAudioCache {
    companion object {
        private const val DEFAULT_BUDGET_BYTES = 64L * 1024 * 1024

        // 口音与词干之间的不可见分隔符：防止「AME RI...」这类拼接歧义撞 key。
        private val KEY_JOINER: String = String(Character.toChars(0))

        private const val AUDIO_SUFFIX = ".wav"
        private const val LEDGER_SUFFIX = ".json"
    }

    override suspend fun read(text: String, accent: PronunciationAccent): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val audio = fileFor(text, accent)
                val ledger = ledgerFor(audio)
                val bytes = if (audio.isFile && audio.length() > 0L) audio.readBytes() else null
                val metadata = PronunciationAudioMetadata.fromJson(readLedger(ledger))
                if (bytes == null || metadata == null || !metadata.matches(bytes)) {
                    // 拒绝播放，并让它可被重新生成。
                    audio.delete()
                    ledger.delete()
                    null
                } else {
                    bytes
                }
            }.getOrNull()
        }

    override suspend fun write(
        text: String,
        accent: PronunciationAccent,
        wav: ByteArray,
        entity: PronunciationEntityRef?,
    ) {
        if (wav.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                if (!ensureDirectory()) return@runCatching
                val audio = fileFor(text, accent)
                val ledger = ledgerFor(audio)
                val metadata = PronunciationAudioMetadata.of(
                    bytes = wav,
                    providerId = provenance.providerId,
                    model = provenance.model,
                    voice = provenance.voiceFor(accent),
                    accent = accent,
                    contentKind = PronunciationContentKind.Word,
                    text = text,
                    createdAtEpochMillis = clock(),
                    entity = entity,
                )
                val temporaryAudio = temporaryFile(audio)
                val temporaryLedger = temporaryFile(ledger)
                temporaryAudio.delete()
                temporaryLedger.delete()
                temporaryAudio.writeBytes(wav)
                temporaryLedger.writeText(metadata.toJson())
                val published = publish(temporaryAudio, audio) && publish(temporaryLedger, ledger)
                if (!published) {
                    audio.delete()
                    ledger.delete()
                    return@runCatching
                }
                enforceBudget(audio, ledger)
            }
        }
    }

    private fun ensureDirectory(): Boolean = root.isDirectory || root.mkdirs()

    private fun fileFor(text: String, accent: PronunciationAccent): File =
        File(root, sha256Hex(accent.name + KEY_JOINER + text) + AUDIO_SUFFIX)

    private fun ledgerFor(audio: File): File = File(audio.parentFile, audio.nameWithoutExtension + LEDGER_SUFFIX)

    private fun readLedger(ledger: File): String? =
        runCatching { if (ledger.isFile) ledger.readText() else null }.getOrNull()

    private fun temporaryFile(target: File): File = File(target.parentFile, target.name + ".tmp")

    /** 原子发布：先临时文件改名，改名失败就删目标重试一次（保留原有语义）。 */
    private fun publish(temporary: File, target: File): Boolean {
        if (temporary.renameTo(target)) return true
        target.delete()
        return temporary.renameTo(target)
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * 清残留临时文件；台账与音频**成对**存在才算条目，孤儿直接删。
     * 超出预算从最旧修改时间优先成对逐出，刚写入的 [keepAudio]/[keepLedger] 永不逐出。
     */
    private fun enforceBudget(keepAudio: File, keepLedger: File) {
        val files = root.listFiles()?.filter { it.isFile }.orEmpty()
        files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
        files.filter { it.name.endsWith(AUDIO_SUFFIX) }
            .filterNot { ledgerFor(it).isFile }
            .forEach { it.delete() }
        files.filter { it.name.endsWith(LEDGER_SUFFIX) }
            .filterNot { File(root, it.nameWithoutExtension + AUDIO_SUFFIX).isFile }
            .forEach { it.delete() }

        val entries = root.listFiles()?.filter { it.isFile }.orEmpty()
            .filter { it.name.endsWith(AUDIO_SUFFIX) && ledgerFor(it).isFile }
        var total = entries.sumOf { it.length() }
        if (total <= maxTotalBytes) return
        for (audio in entries.filter { it != keepAudio }.sortedBy { it.lastModified() }) {
            if (total <= maxTotalBytes) break
            total -= audio.length()
            audio.delete()
            ledgerFor(audio).delete()
        }
    }
}
