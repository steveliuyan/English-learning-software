package com.example.englishlearning.imagegen

import java.io.File
import java.util.Base64
import java.util.UUID

/**
 * 生图结果落盘：base64 解码后写入**应用 cache 目录**（文件名随机 UUID，按魔数给扩展名）。
 * 不进相册、不进 Room、不备份；系统可随时清理。目录由调用方（VM 接线）提供
 * `cacheDir/generated-images/`，本对象只负责「字节 → 文件」。
 */
object GeneratedImageWriter {

    fun save(base64Data: String, directory: File): Result<File> =
        runCatching { Base64.getDecoder().decode(base64Data) }.fold(
            // 严格解码器：非法字符直接失败，不留半张图。
            onSuccess = { saveBytes(it, directory) },
            onFailure = { Result.failure(it) },
        )

    /** 已解码的图像字节直接落盘（url 型结果下载回来的字节也走这里）。 */
    fun saveBytes(bytes: ByteArray, directory: File): Result<File> = runCatching {
        directory.mkdirs()
        val extension = when {
            bytes.size >= 4 &&
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            else -> "img"
        }
        val file = File(directory, "${UUID.randomUUID()}.$extension")
        file.writeBytes(bytes)
        file
    }
}
