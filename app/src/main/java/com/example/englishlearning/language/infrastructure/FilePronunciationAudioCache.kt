package com.example.englishlearning.language.infrastructure

import android.content.Context
import com.example.englishlearning.language.domain.PronunciationAudioCache
import com.example.englishlearning.language.domain.PronunciationAudioMetadata
import com.example.englishlearning.language.domain.PronunciationCacheKey
import com.example.englishlearning.language.domain.PronunciationContentKind
import com.example.englishlearning.language.domain.PronunciationEntityRef
import java.io.File

/**
 * App-private pronunciation cache. A corrupt entry is treated as a miss and removed.
 *
 * 每条 WAV 旁配一份 [PronunciationAudioMetadata] 台账（`.json`）：内容摘要、大小、创建时间，
 * 以及从 [PronunciationCacheKey] 直接抄下来的来源（providerId / model / voice / accent / requestVersion）。
 * 本轮之前这里只有一个裸 `.sha256` 文件——摘要对得上就播，但「这段音频是谁、什么时候生成的」
 * 在设备上无处可查（决策 §2 欠账）。
 */
class FilePronunciationAudioCache internal constructor(
    private val root: File,
    private val contentKind: PronunciationContentKind = PronunciationContentKind.Sentence,
    private val clock: () -> Long = System::currentTimeMillis,
) : PronunciationAudioCache {

    constructor(context: Context) : this(File(context.filesDir, "pronunciation-cache"))

    init {
        root.mkdirs()
    }

    override suspend fun get(key: PronunciationCacheKey): ByteArray? {
        val audio = fileFor(key)
        val ledger = ledgerFor(audio)
        if (!audio.isFile) {
            ledger.delete()
            return null
        }
        val bytes = runCatching { audio.readBytes() }.getOrNull()
        val metadata = PronunciationAudioMetadata.fromJson(readLedger(ledger))
        return if (bytes != null && bytes.isNotEmpty() && metadata != null && metadata.matches(bytes)) {
            bytes
        } else {
            removeFiles(audio)
            null
        }
    }

    override suspend fun put(
        key: PronunciationCacheKey,
        bytes: ByteArray,
        entity: PronunciationEntityRef?,
    ) {
        require(bytes.isNotEmpty())
        val cacheId = key.cacheId()
        val audio = File(root, "$cacheId.wav")
        val ledger = File(root, "$cacheId.json")
        val metadata = PronunciationAudioMetadata.of(
            bytes = bytes,
            providerId = key.providerId,
            model = key.model,
            voice = key.voice,
            accent = key.accent,
            contentKind = contentKind,
            text = key.text,
            createdAtEpochMillis = clock(),
            requestVersion = key.requestVersion,
            entity = entity,
        )
        val temporaryAudio = File(root, "$cacheId.wav.tmp")
        val temporaryLedger = File(root, "$cacheId.json.tmp")
        temporaryAudio.delete()
        temporaryLedger.delete()
        temporaryAudio.writeBytes(bytes)
        temporaryLedger.writeText(metadata.toJson())
        if (!publish(temporaryAudio, audio)) {
            temporaryAudio.delete()
            temporaryLedger.delete()
            error("pronunciation cache publication failed")
        }
        if (!publish(temporaryLedger, ledger)) {
            // 绝不留「有 WAV 无台账」的半条缓存：它既不可证明完整性，也不可被取证。
            audio.delete()
            ledger.delete()
            temporaryLedger.delete()
            error("pronunciation cache ledger publication failed")
        }
    }

    private fun fileFor(key: PronunciationCacheKey): File = File(root, "${key.cacheId()}.wav")

    private fun ledgerFor(audio: File): File = File(root, "${audio.nameWithoutExtension}.json")

    private fun readLedger(ledger: File): String? =
        runCatching { if (ledger.isFile) ledger.readText() else null }.getOrNull()

    /** 原子发布：改名失败就删目标重试一次（沿用本轮之前的语义）。 */
    private fun publish(temporary: File, target: File): Boolean {
        if (temporary.renameTo(target)) return true
        target.delete()
        return temporary.renameTo(target)
    }

    private fun removeFiles(audio: File) {
        audio.delete()
        File(root, "${audio.nameWithoutExtension}.json").delete()
    }
}
