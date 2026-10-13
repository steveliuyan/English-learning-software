package com.example.englishlearning.language.infrastructure

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir

/**
 * Kokoro 资产物化契约（2026-10-13 真机反馈：每次 prepare 全量拷贝+内存哈希 134MB，
 * 是发音点击「没反应」的主要放大器）：
 * 已存在且大小匹配 → 直接复用，一次资产流都不读；
 * 缺失或大小不符 → 重拷并流式校验 SHA-256；校验失败不留半截文件。
 */
class KokoroAssetMaterializerTest {

    private val payload = ByteArray(1_024) { (it % 251).toByte() }
    private val payloadHash = sha256Hex(payload)
    private val spec = KokoroAsset("model-files-v1.0/kokoro.int8.onnx", payload.size.toLong(), payloadHash)

    private class CountingSource(val bytes: ByteArray) {
        var opens = 0
        fun open(path: String): java.io.InputStream {
            opens += 1
            return ByteArrayInputStream(bytes)
        }
    }

    @Test
    fun `materializes a missing asset and verifies its digest`(@TempDir dir: File) = runTest {
        val source = CountingSource(payload)
        val materializer = KokoroAssetMaterializer(dir, source::open)

        val target = materializer.materialize(mapOf("model" to spec)).getValue("model")

        assertEquals(payload.size.toLong(), target.length())
        assertEquals(1, source.opens)
        assertTrue(target.readBytes().contentEquals(payload))
    }

    @Test
    fun `an existing correct asset is reused without touching the asset stream`(@TempDir dir: File) = runTest {
        val seed = CountingSource(payload)
        KokoroAssetMaterializer(dir, seed::open).materialize(mapOf("model" to spec))

        val reuse = CountingSource(payload)
        val target = KokoroAssetMaterializer(dir, reuse::open).materialize(mapOf("model" to spec)).getValue("model")

        assertEquals(0, reuse.opens, "大小已匹配的资产不得重新拷贝")
        assertEquals(payload.size.toLong(), target.length())
    }

    @Test
    fun `a truncated asset is refetched`(@TempDir dir: File) = runTest {
        val target = dir.resolve(spec.relativePath)
        target.parentFile!!.mkdirs()
        target.writeBytes(ByteArray(10))

        val source = CountingSource(payload)
        val materialized = KokoroAssetMaterializer(dir, source::open).materialize(mapOf("model" to spec)).getValue("model")

        assertEquals(1, source.opens)
        assertEquals(target.path, materialized.path)
        assertTrue(materialized.readBytes().contentEquals(payload))
    }

    @Test
    fun `a digest mismatch fails closed and leaves no half-written asset`(@TempDir dir: File) = runTest {
        val source = CountingSource(ByteArray(payload.size) { (it % 97).toByte() })
        val materializer = KokoroAssetMaterializer(dir, source::open)

        assertFailsWith<IllegalArgumentException> { materializer.materialize(mapOf("model" to spec)) }
        assertTrue(!dir.resolve(spec.relativePath).exists())
        assertTrue(dir.walkTopDown().none { it.isFile && it.name.endsWith(".tmp") })
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
