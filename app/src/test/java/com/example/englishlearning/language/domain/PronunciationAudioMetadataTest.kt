package com.example.englishlearning.language.domain

import java.security.MessageDigest
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 完整性台账本身的可信性：Spec 03 F3-02 要求的五枚字段必须逐枚可写可读，
 * 而任何畸形、版本不符或字段缺失都必须判为「不可信」——缓存层拿到不可信台账只能按未命中处理。
 */
class PronunciationAudioMetadataTest {

    private val bytes = byteArrayOf(9, 8, 7, 6, 5)

    private fun ledger(
        audio: ByteArray = bytes,
        providerId: String = "KOKORO_ON_DEVICE",
        model: String = "model-files-v1.0/kokoro-v1.0.int8.onnx",
        voice: String = "af_heart.npy",
        accent: PronunciationAccent = PronunciationAccent.American,
        kind: PronunciationContentKind = PronunciationContentKind.Word,
        text: String = "hello",
        createdAt: Long = 1_760_000_000_000L,
    ) = PronunciationAudioMetadata.of(
        bytes = audio,
        providerId = providerId,
        model = model,
        voice = voice,
        accent = accent,
        contentKind = kind,
        text = text,
        createdAtEpochMillis = createdAt,
    )

    @Test
    fun recordsEveryFieldTheSpecMandates() {
        val entry = ledger()

        assertEquals(bytes.size.toLong(), entry.byteSize, "大小")
        assertEquals(hex(bytes), entry.sha256, "内容摘要")
        assertEquals(1_760_000_000_000L, entry.createdAtEpochMillis, "创建时间")
        assertEquals("KOKORO_ON_DEVICE", entry.providerId, "来源")
        assertEquals("model-files-v1.0/kokoro-v1.0.int8.onnx", entry.model, "模型版本")
        assertEquals("af_heart.npy", entry.voice)
        assertEquals("American", entry.accent)
        assertEquals("Word", entry.contentKind, "关联实体的内容类别")
        assertEquals("hello", entry.text)
        assertEquals(PronunciationCacheKey.REQUEST_VERSION, entry.requestVersion)
    }

    @Test
    fun roundTripsThroughJson() {
        val entry = ledger()

        val restored = PronunciationAudioMetadata.fromJson(entry.toJson())

        assertEquals(entry, restored, "台账写出去再读回来必须逐字段相同")
    }

    @Test
    fun refusesMissingBlankMalformedAndUnknownVersionLedgers() {
        assertNull(PronunciationAudioMetadata.fromJson(null), "台账缺失必须判不可信")
        assertNull(PronunciationAudioMetadata.fromJson(""), "空文件必须判不可信")
        assertNull(PronunciationAudioMetadata.fromJson("这不是 JSON"), "非 JSON 必须判不可信")
        assertNull(PronunciationAudioMetadata.fromJson("""{"sha256":"${hex(bytes)}"}"""), "缺字段必须判不可信")
        val staleVersion = ledger().toJson().replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":99")
        assertNull(PronunciationAudioMetadata.fromJson(staleVersion), "未知 schemaVersion 不许当成有效台账")
        val blankSource = ledger(providerId = "   ").toJson()
        assertNull(PronunciationAudioMetadata.fromJson(blankSource), "来源空白不可信")
        val badDigest = ledger().toJson().replaceFirst(hex(bytes), "not-a-sha256")
        assertNull(PronunciationAudioMetadata.fromJson(badDigest), "摘要形状不对就不可信")
    }

    @Test
    fun matchesRequiresBothSizeAndDigest() {
        val entry = ledger()

        assertTrue(entry.matches(bytes), "逐字节相同才可播放")
        assertFalse(entry.matches(bytes.copyOf(bytes.size + 1)), "多一字节不可播")
        assertFalse(entry.matches(bytes.copyOfRange(0, bytes.size - 1)), "少一字节不可播")
        val tampered = bytes.copyOf().also { it[2] = (it[2] + 1).toByte() }
        assertFalse(entry.matches(tampered), "内容被替换不可播")
        assertTrue(entry.matches(bytes.copyOf()), "同一份内容换个数组也要可播")
    }

    @Test
    fun theLedgerHasNoFieldThatCouldHoldACredentialOrEndpoint() {
        val names = PronunciationAudioMetadata::class.memberProperties.map { it.name.lowercase() }
        val forbidden = listOf("endpoint", "apikey", "key", "token", "secret", "url", "host", "authorization")
        val offenders = names.filter { name -> forbidden.any { name.contains(it) } }

        assertTrue(
            offenders.isEmpty(),
            "台账字段面必须与 AGENTS.md 红线兼容，多的那一枚迟早被人填进 Key：$offenders ← $names",
        )
        assertNotNull(PronunciationAudioMetadata.fromJson(ledger().toJson()))
        assertFalse(ledger().toJson().contains("http"), "序列化结果里不该出现任何 URL 形状")
    }

    @Test
    fun provenanceRefusesToGuessAnUnregisteredVoice() {
        val provenance = SynthesizedAudioProvenance(
            providerId = "KOKORO_ON_DEVICE",
            model = "m",
            voiceByAccent = mapOf(PronunciationAccent.American to "af_heart.npy"),
        )

        assertEquals("af_heart.npy", provenance.voiceFor(PronunciationAccent.American))
        assertTrue(
            runCatching { provenance.voiceFor(PronunciationAccent.British) }.isFailure,
            "口音没登记音色时必须炸出来，不许悄悄写一个假来源进台账",
        )
    }

    private fun hex(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
