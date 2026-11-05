package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.PronunciationAccent
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
 * 文件合成音频缓存契约：尽力而为——**IO** 读失败即未命中（返回 null，不抛），
 * 写失败静默（绝不打断播放）；按 文本+口音 分区；超出字节预算按最旧优先成对清理。
 *
 * 但完整性不是「尽力而为」：台账缺失、摘要或大小不符一律按未命中处理并连带删除，
 * 见下面 AC3-03 那五条（本轮之前这里只有裸 WAV，篡改后照样能播——那是真缺陷，不是缺字段）。
 */
class FileSynthesizedAudioCacheTest {

    @Test
    fun `writes and reads back per text and accent`(@TempDir dir: File) = runTest {
        val cache = FileSynthesizedAudioCache(dir)

        assertNull(cache.read("hello", PronunciationAccent.American))
        cache.write("hello", PronunciationAccent.American, byteArrayOf(1, 2, 3))

        assertContentEquals(byteArrayOf(1, 2, 3), cache.read("hello", PronunciationAccent.American))
        assertNull(cache.read("hello", PronunciationAccent.British))
        assertNull(cache.read("world", PronunciationAccent.American))
    }

    @Test
    fun `empty payloads never become playable entries`(@TempDir dir: File) = runTest {
        val cache = FileSynthesizedAudioCache(dir)

        cache.write("hello", PronunciationAccent.American, ByteArray(0))

        assertNull(cache.read("hello", PronunciationAccent.American))
    }

    @Test
    fun `a broken cache directory degrades to misses instead of throwing`(@TempDir dir: File) = runTest {
        // 根路径被一个普通文件占住：任何 IOException 都必须被吞掉。
        val blocker = File(dir, "blocked").apply { writeBytes(byteArrayOf(1)) }
        val cache = FileSynthesizedAudioCache(blocker)

        assertNull(cache.read("hello", PronunciationAccent.American))
        cache.write("hello", PronunciationAccent.American, byteArrayOf(1, 2))
        assertNull(cache.read("hello", PronunciationAccent.American))
    }

    @Test
    fun `writes evict the oldest entries beyond the byte budget`(@TempDir dir: File) = runTest {
        val cache = FileSynthesizedAudioCache(dir, maxTotalBytes = 25L)
        val payload = ByteArray(10) { it.toByte() }
        val store = dir

        cache.write("first", PronunciationAccent.American, payload)
        wavs(store).forEach { it.setLastModified(1_000L) }
        cache.write("second", PronunciationAccent.American, payload)
        // 文件名是哈希，认不出谁是 second：用 mtime 把它标成最新，first 保持最旧。
        wavs(store).filter { it.lastModified() != 1_000L }.forEach { it.setLastModified(5_000L) }
        cache.write("third", PronunciationAccent.American, payload)

        // 预算 25B：最旧的 first 必须被逐出，second/third 仍可播放。
        assertNull(cache.read("first", PronunciationAccent.American))
        assertEquals(10, cache.read("second", PronunciationAccent.American)?.size)
        assertEquals(10, cache.read("third", PronunciationAccent.American)?.size)
    }

    // ---- AC3-03 / AGENTS.md「损坏或替换后不得播放」：以下四条是本轮的 RED 依据 ----

    @Test
    fun `a tampered wav is refused and purged together with its ledger`(@TempDir dir: File) = runTest {
        val cache = FileSynthesizedAudioCache(dir)
        cache.write("hello", PronunciationAccent.American, byteArrayOf(1, 2, 3))
        val stored = wavs(dir).single().apply { appendBytes(byteArrayOf(9)) }

        assertNull(
            cache.read("hello", PronunciationAccent.American),
            "磁盘上的 WAV 被替换一个字节后仍被当成命中交给播放层——AGENTS.md 明令禁止",
        )
        assertFalse(stored.exists(), "拒绝播放必须连带清掉坏音频")
        assertTrue(
            ledgers(dir).isEmpty(),
            "坏音频的完整性台账不许留在目录里（孤儿台账会让下一次取证读到假数据）",
        )
    }

    @Test
    fun `a truncated wav is refused`(@TempDir dir: File) = runTest {
        val cache = FileSynthesizedAudioCache(dir)
        val payload = ByteArray(32) { it.toByte() }
        cache.write("hello", PronunciationAccent.American, payload)
        wavs(dir).single().writeBytes(payload.copyOfRange(0, payload.size - 1))

        assertNull(
            cache.read("hello", PronunciationAccent.American),
            "长度被截掉一字节必须判为未命中",
        )
    }

    @Test
    fun `each cached entry ships with an integrity ledger`(@TempDir dir: File) = runTest {
        val cache = FileSynthesizedAudioCache(dir)

        cache.write("hello", PronunciationAccent.American, byteArrayOf(1, 2, 3))

        assertEquals(1, ledgers(dir).size, "每条可播放缓存必须有且只有一份台账")
    }

    @Test
    fun `the ledger answers source size and creation time, not just the key hash`(@TempDir dir: File) = runTest {
        val cache = FileSynthesizedAudioCache(dir)
        cache.write("hello", PronunciationAccent.American, byteArrayOf(1, 2, 3))
        val ledger = ledgers(dir).single().readText()

        // Spec 03 F3-02 的五枚字段名必须在落盘台账里逐枚可见；值不钉死，避免和时钟较劲。
        listOf(
            "sha256",
            "byteSize",
            "createdAtEpochMillis",
            "providerId",
            "model",
            "voice",
            "accent",
            "contentKind",
            "text",
        ).forEach { field ->
            assertTrue(ledger.contains("\"$field\""), "台账缺 Spec 03 要求的字段 \"$field\"：$ledger")
        }
    }

    @Test
    fun `eviction never leaves an orphan ledger behind`(@TempDir dir: File) = runTest {
        val cache = FileSynthesizedAudioCache(dir, maxTotalBytes = 25L)
        val payload = ByteArray(10) { it.toByte() }

        cache.write("first", PronunciationAccent.American, payload)
        wavs(dir).forEach { it.setLastModified(1_000L) }
        ledgers(dir).forEach { it.setLastModified(1_000L) }
        cache.write("second", PronunciationAccent.American, payload)
        wavs(dir).filter { it.lastModified() != 1_000L }.forEach { it.setLastModified(5_000L) }
        ledgers(dir).filter { it.lastModified() != 1_000L }.forEach { it.setLastModified(5_000L) }
        cache.write("third", PronunciationAccent.American, payload)

        assertEquals(
            wavs(dir).map { it.nameWithoutExtension }.toSet(),
            ledgers(dir).map { it.nameWithoutExtension }.toSet(),
            "台账与音频必须成对存在，否则预算账本与实际字节脱钩",
        )
    }

    private fun wavs(store: File): List<File> =
        store.listFiles()?.filter { it.name.endsWith(".wav") }.orEmpty()

    /** 完整性台账文件（与 WAV 同名同目录）；本轮实现前目录里只有裸 WAV，故按后缀取。 */
    private fun ledgers(store: File): List<File> =
        store.listFiles()?.filter { it.name.endsWith(".json") }.orEmpty()
}
