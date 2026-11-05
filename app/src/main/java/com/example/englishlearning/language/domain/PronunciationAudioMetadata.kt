package com.example.englishlearning.language.domain

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 缓存音频对应的内容类别；主键级的关联见 [PronunciationEntityRef]（决策 2026-11-05 D-7）。 */
enum class PronunciationContentKind {
    Word,
    Sentence,
}

/**
 * AI 音频缓存的完整性台账（Spec 03 F3-02 第 30 行 / AGENTS.md「AI 音频缓存需记录内容哈希、来源、
 * 模型版本和关联实体」）。与 WAV 同名同目录落盘为 `.json` sidecar：**读侧必须先过它**，
 * 摘要或大小任一不符就按未命中处理并删除两份文件，让上层重新生成。
 *
 * 字段面是刻意收窄的：只有来源标识（providerId / model / voice）、口音、请求版本、内容类别、
 * 朗读文本、关联实体（kind + 可空主键）与时钟。Endpoint URL 与 API Key 在本类型里**没有对应字段**——
 * 缓存层拿不到 `AiProfile`，也就结构上写不出那两样东西（AGENTS.md 红线）。
 */
@Serializable
data class PronunciationAudioMetadata(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val sha256: String,
    val byteSize: Long,
    val createdAtEpochMillis: Long,
    val providerId: String,
    val model: String,
    val voice: String,
    val accent: String,
    val requestVersion: Int = PronunciationCacheKey.REQUEST_VERSION,
    val contentKind: String,
    val text: String,
    // 关联实体的主键级那一半（决策 D-7）：kind 恒有值，id 可空。
    // 两枚都带默认值是**故意的**（决策 D-12）：上一版台账没有这两枚键，判它们缺失=不可信，
    // 等于把整座缓存打回重新合成——MiMo 那条路径每次都是真花钱的付费请求，而完整性并没有因此提升。
    val entityKind: String = PronunciationEntityKind.Unattached.name,
    val entityId: String? = null,
) {

    /** 大小与内容摘要**两枚都要**对上才可播放。 */
    fun matches(bytes: ByteArray): Boolean =
        bytes.size.toLong() == byteSize && sha256Hex(bytes).equals(sha256, ignoreCase = true)

    fun toJson(): String = json.encodeToString(this)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        private const val SHA256_HEX = "^[0-9a-fA-F]{64}$"

        // 默认值也要写全：台账是给取证看的落盘记录，省略 schemaVersion/requestVersion 等于少两枚字段。
        private val json = Json { encodeDefaults = true }

        fun of(
            bytes: ByteArray,
            providerId: String,
            model: String,
            voice: String,
            accent: PronunciationAccent,
            contentKind: PronunciationContentKind,
            text: String,
            createdAtEpochMillis: Long,
            requestVersion: Int = PronunciationCacheKey.REQUEST_VERSION,
            entity: PronunciationEntityRef? = null,
        ): PronunciationAudioMetadata = PronunciationAudioMetadata(
            sha256 = sha256Hex(bytes),
            byteSize = bytes.size.toLong(),
            createdAtEpochMillis = createdAtEpochMillis,
            providerId = providerId.trim(),
            model = model.trim(),
            voice = voice.trim(),
            accent = accent.name,
            requestVersion = requestVersion,
            contentKind = contentKind.name,
            text = text,
            entityKind = entity?.kind?.name ?: PronunciationEntityKind.Unattached.name,
            entityId = entity?.idOrNull,
        )

        /** 畸形、缺字段、未知 schemaVersion、字段值不可信——一律 null，由调用方按未命中处理。 */
        fun fromJson(raw: String?): PronunciationAudioMetadata? {
            if (raw.isNullOrBlank()) return null
            val parsed = runCatching { json.decodeFromString<PronunciationAudioMetadata>(raw) }.getOrNull()
            return parsed?.takeIf { it.isTrustworthy() }
        }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun isTrustworthy(): Boolean =
        schemaVersion == CURRENT_SCHEMA_VERSION &&
            sha256.matches(Regex(SHA256_HEX)) &&
            byteSize > 0L &&
            providerId.isNotBlank() &&
            model.isNotBlank() &&
            voice.isNotBlank() &&
            accent.isNotBlank() &&
            contentKind.isNotBlank() &&
            text.isNotBlank() &&
            // 实体类别必须是认识的那一枚：手写歪了的 kind 说明台账已经没人维护了，按不可信处理（决策 D-9）。
            PronunciationEntityKind.values().any { it.name == entityKind } &&
            (entityId == null || entityId.isNotBlank())
}

/**
 * 端侧合成音频的来源标识。三枚值的事实来源是 [com.example.englishlearning.language.infrastructure.KokoroAndroidEngine]
 * 里的私有资产常量；换模型时若只改引擎不改这里，`AudioCacheLedgerContractTest` 的防漂移判据会红（决策 D-5）。
 */
data class SynthesizedAudioProvenance(
    val providerId: String,
    val model: String,
    val voiceByAccent: Map<PronunciationAccent, String>,
) {
    fun voiceFor(accent: PronunciationAccent): String =
        voiceByAccent[accent] ?: error("口音 $accent 没有登记音色：$providerId/$model")

    companion object {
        /** 生产装配用的端侧 Kokoro 台账来源（不含任何机密）。 */
        val KokoroOnDevice = SynthesizedAudioProvenance(
            providerId = "KOKORO_ON_DEVICE",
            model = "model-files-v1.0/kokoro-v1.0.int8.onnx",
            voiceByAccent = mapOf(
                PronunciationAccent.American to "af_heart.npy",
                PronunciationAccent.British to "bf_emma.npy",
            ),
        )
    }
}
