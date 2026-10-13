package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.PronunciationAccent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir

/**
 * 文件合成音频缓存契约：尽力而为——读失败即未命中（返回 null，不抛），
 * 写失败静默（绝不打断播放）；按 文本+口音 分区；超出字节预算按最旧优先清理。
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

    private fun wavs(store: File): List<File> =
        store.listFiles()?.filter { it.name.endsWith(".wav") }.orEmpty()
}
