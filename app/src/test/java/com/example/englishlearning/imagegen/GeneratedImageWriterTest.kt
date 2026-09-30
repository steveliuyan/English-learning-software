package com.example.englishlearning.imagegen

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 生图落盘契约：base64 解码写入指定目录、文件名随机 UUID、按魔数给扩展名；
 * 解不开的 base64 是失败而不是空文件。
 */
class GeneratedImageWriterTest {

    private val directory = Files.createTempDirectory("imagegen-test").toFile()

    @Test
    fun decodesBase64IntoAUuidNamedPngFile() {
        // 89 50 4E 47 0D 0A 1A 0A = PNG 魔数
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        val base64 = java.util.Base64.getEncoder().encodeToString(pngBytes)

        val file = GeneratedImageWriter.save(base64, directory).getOrThrow()

        assertTrue(file.parentFile.absolutePath == directory.absolutePath)
        assertTrue(file.name.endsWith(".png"))
        assertTrue(
            file.nameWithoutExtension.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")),
        ) // 文件名是随机 UUID
        assertTrue(pngBytes.contentEquals(file.readBytes()))
    }

    @Test
    fun jpegMagicGetsTheJpgExtension() {
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        val base64 = java.util.Base64.getEncoder().encodeToString(jpegBytes)

        val file = GeneratedImageWriter.save(base64, directory).getOrThrow()

        assertTrue(file.name.endsWith(".jpg"))
        assertTrue(jpegBytes.contentEquals(file.readBytes()))
    }

    @Test
    fun unknownMagicStillSavesWithGenericExtension() {
        val base64 = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))

        val file = GeneratedImageWriter.save(base64, directory).getOrThrow()

        assertTrue(file.name.endsWith(".img"))
        assertTrue(byteArrayOf(1, 2, 3, 4).contentEquals(file.readBytes()))
    }

    @Test
    fun undecodableBase64IsAFailureWithoutLeavingAFile() {
        val before = directory.listFiles()?.size ?: 0

        val result = GeneratedImageWriter.save("not-base64!!!", directory)

        assertTrue(result.isFailure)
        assertEquals(before, directory.listFiles()?.size ?: 0)
    }
}
