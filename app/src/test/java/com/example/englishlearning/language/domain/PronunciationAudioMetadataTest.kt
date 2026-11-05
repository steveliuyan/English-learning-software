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
        entity: PronunciationEntityRef? = PronunciationEntityRef.wordCard("card-42"),
    ) = PronunciationAudioMetadata.of(
        bytes = audio,
        providerId = providerId,
        model = model,
        voice = voice,
        accent = accent,
        contentKind = kind,
        text = text,
        createdAtEpochMillis = createdAt,
        entity = entity,
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
        assertEquals("WordCard", entry.entityKind, "关联实体的来源屏")
        assertEquals("card-42", entry.entityId, "关联实体的主键")
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
        val bogusKind = ledger().toJson().replaceFirst("\"entityKind\":\"WordCard\"", "\"entityKind\":\"MagicCard\"")
        assertNull(PronunciationAudioMetadata.fromJson(bogusKind), "不认识的实体类别说明台账没人维护了，按不可信处理（决策 D-9）")
        val blankId = ledger().toJson().replaceFirst("\"entityId\":\"card-42\"", "\"entityId\":\"  \"")
        assertNull(PronunciationAudioMetadata.fromJson(blankId), "实体主键要么有值要么是 null，空白串不算")
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

    @Test
    fun aLedgerThatNamesTheCardBehindItIsReadable() {
        // 决策 D-7：台账得能回答「这段音频是哪张卡触发生成的」。
        // 本用例刻意用字面 JSON 写，不借构造函数——它测的就是「盘上这条记录认不认识这两枚键」。
        val namingACard = legacyLedgerJson().removeSuffix("}") + ""","entityKind":"WordCard","entityId":"card-42"}"""

        val entry = PronunciationAudioMetadata.fromJson(namingACard)

        assertNotNull(entry, "记了主键的台账读不回来：说明 entityKind/entityId 这两枚键根本不存在")
        assertTrue(entry.matches(bytes), "带实体的台账照样要用摘要与大小守门")
    }

    @Test
    fun aLedgerWrittenBeforeThisBatchIsStillTrusted() {
        // 决策 D-12：上一版台账缺实体字段但字节可信；判它作废=把整座缓存打回重新合成/重新出网。
        assertNotNull(PronunciationAudioMetadata.fromJson(legacyLedgerJson()), "旧台账不许因缺实体字段而作废")
    }

    private fun legacyLedgerJson(): String =
        """{"schemaVersion":1,"sha256":"${hex(bytes)}","byteSize":5,"createdAtEpochMillis":1760000000000,""" +
            """"providerId":"KOKORO_ON_DEVICE","model":"m","voice":"af_heart.npy","accent":"American",""" +
            """"requestVersion":1,"contentKind":"Word","text":"hello"}"""

    private fun hex(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
