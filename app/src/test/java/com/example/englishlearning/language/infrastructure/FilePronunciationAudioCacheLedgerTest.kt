package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.PronunciationAccent
import com.example.englishlearning.language.domain.PronunciationCacheKey
import com.example.englishlearning.language.domain.PronunciationEntityRef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir

/**
 * 云端（MiMo）发音缓存的完整性台账：Spec 03 F3-02 要求落盘 SHA-256、大小、来源、创建时间与关联实体，
 * AGENTS.md 要求「损坏或替换后不得播放」。这些用例在 JVM 临时目录里跑——
 * 本轮之前这类断言只能上真机/模拟器，因为该类只收 Context。
 */
class FilePronunciationAudioCacheLedgerTest {

    private val key = PronunciationCacheKey(
        text = "  Remote   Ledger  Check  ",
        accent = PronunciationAccent.British,
        providerId = "XIAOMI_MIMO",
        model = "mimo-v2.5",
        voice = "en_female_1",
    )

    @Test
    fun `round-trips exact bytes`(@TempDir dir: File) = runTest {
        val cache = FilePronunciationAudioCache(dir)
        val bytes = byteArrayOf(0, 1, 2, 127, -1)

        cache.put(key, bytes)

        assertContentEquals(bytes, FilePronunciationAudioCache(dir).get(key))
    }

    @Test
    fun `every stored entry carries one integrity ledger`(@TempDir dir: File) = runTest {
        FilePronunciationAudioCache(dir).put(key, byteArrayOf(1, 2, 3))

        assertEquals(
            1,
            ledgers(dir).size,
            "只有裸 WAV + 摘要文件的台账不算数：Spec 03 的五枚字段必须有地方写",
        )
    }

    @Test
    fun `the ledger names the source the model and the creation time`(@TempDir dir: File) = runTest {
        FilePronunciationAudioCache(dir).put(key, byteArrayOf(1, 2, 3))

        val ledger = ledgers(dir).single().readText()
        listOf(
            "sha256",
            "byteSize",
            "createdAtEpochMillis",
            "providerId",
            "model",
            "voice",
            "accent",
            "requestVersion",
            "contentKind",
            "text",
            "entityKind",
            "entityId",
        ).forEach { field ->
            assertTrue(ledger.contains("\"$field\""), "台账缺 Spec 03 要求的字段 \"$field\"：$ledger")
        }
        // 来源取自缓存键本身：换 provider/模型/音色都必须能在台账里读到。
        assertTrue(ledger.contains("XIAOMI_MIMO"), "台账没记 providerId：$ledger")
        assertTrue(ledger.contains("mimo-v2.5"), "台账没记模型版本：$ledger")
        assertTrue(ledger.contains("en_female_1"), "台账没记音色：$ledger")
        assertTrue(ledger.contains("British"), "台账没记口音：$ledger")
    }

    @Test
    fun `the ledger names the article behind a sentence`(@TempDir dir: File) = runTest {
        FilePronunciationAudioCache(dir).put(key, byteArrayOf(1, 2, 3), entity = PronunciationEntityRef.article("art-7"))

        val ledger = ledgers(dir).single().readText()
        assertTrue(ledger.contains("\"entityKind\":\"Article\""), "台账答不出「哪篇文章」：关联实体还停在类别级（决策 D-7）：$ledger")
        assertTrue(ledger.contains("\"entityId\":\"art-7\""), "台账没落主键：$ledger")
    }

    @Test
    fun `an unsaved photo sentence records its kind and an honestly absent id`(@TempDir dir: File) = runTest {
        // 拍照翻译的句子在保存之前没有主键：kind 照记，id 必须是 null，不能拿文本冒充（决策 D-9）。
        FilePronunciationAudioCache(dir).put(key, byteArrayOf(1, 2, 3), entity = PronunciationEntityRef.photoTranslation(null))

        val ledger = ledgers(dir).single().readText()
        assertTrue(ledger.contains("\"entityKind\":\"PhotoTranslation\""), "未保存也要记清来源屏：$ledger")
        assertTrue(ledger.contains("\"entityId\":null"), "没主键就写 null：$ledger")
    }

    @Test
    fun `a tampered wav is refused and its ledger is purged too`(@TempDir dir: File) = runTest {
        val cache = FilePronunciationAudioCache(dir)
        cache.put(key, byteArrayOf(1, 2, 3))
        val stored = File(dir, "${key.cacheId()}.wav").apply { appendBytes(byteArrayOf(9)) }

        assertNull(cache.get(key), "替换后的音频必须拒绝播放")
        assertFalse(stored.exists(), "拒绝播放必须连带清掉坏音频")
        assertTrue(ledgers(dir).isEmpty(), "坏音频的台账不得留下，否则下一次取证读到假来源")
    }

    @Test
    fun `an entry without its ledger is refused and cleaned up`(@TempDir dir: File) = runTest {
        val cache = FilePronunciationAudioCache(dir)
        cache.put(key, byteArrayOf(1, 2, 3))
        ledgers(dir).forEach { it.delete() }

        assertNull(cache.get(key), "台账丢失即无法证明完整性，必须按未命中处理")
        assertFalse(File(dir, "${key.cacheId()}.wav").exists(), "无台账的孤儿 WAV 必须被清掉，让它可被重新生成")
    }

    private fun ledgers(store: File): List<File> =
        store.listFiles()?.filter { it.name.endsWith(".json") }.orEmpty()
}
