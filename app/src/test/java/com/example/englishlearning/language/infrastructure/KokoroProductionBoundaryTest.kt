package com.example.englishlearning.language.infrastructure

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KokoroProductionBoundaryTest {
    @Test
    fun `debug production classes contain the production Kokoro adapter`() {
        val appBuild = File(System.getProperty("user.dir"), "build")
        val productionOutputs = listOf(
            File(appBuild, "tmp/kotlin-classes/debug"),
            File(appBuild, "intermediates/javac/debug/classes"),
        ).filter(File::isDirectory)

        assertTrue(productionOutputs.isNotEmpty(), "未找到 debug production class output")
        val adapter = "com/example/englishlearning/language/infrastructure/KokoroAndroidEngine.class"
        assertTrue(
            productionOutputs.any { File(it, adapter).isFile },
            "生产 Kokoro adapter 缺失；当前仍未进入 production source set",
        )
    }

    @Test
    fun `production Kokoro manifest exists and every declared asset matches size and sha256`() {
        val manifest = File(System.getProperty("user.dir"), "src/main/assets/kokoro-production/manifest.sha256")
        assertTrue(manifest.isFile, "生产 Kokoro manifest 缺失: ${manifest.path}")

        val lines = manifest.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        assertEquals(5, lines.size, "生产 manifest 必须声明 model、voices、config 和两个 frontend")
        assertTrue(lines.all { it.trim().split(Regex("\\s+"), limit = 3)[2].matches(Regex("[A-Za-z0-9._/-]+")) })
        lines.forEach { line ->
            val fields = line.trim().split(Regex("\\s+"), limit = 3)
            assertEquals(3, fields.size, "manifest 行格式必须为 sha256 size relativePath: $line")
            val expectedHash = fields[0]
            val expectedSize = fields[1].toLong()
            val asset = File(manifest.parentFile, fields[2])
            assertTrue(asset.isFile, "生产资源缺失: ${fields[2]}")
            assertEquals(expectedSize, asset.length(), "生产资源大小不匹配: ${fields[2]}")
            val actualHash = MessageDigest.getInstance("SHA-256").digest(asset.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(expectedHash, actualHash, "生产资源 SHA-256 不匹配: ${fields[2]}")
        }
    }

    @Test
    fun `production APK input excludes instrumentation fixtures and spike implementation`() {
        val apk = File(System.getProperty("user.dir"), "build/outputs/apk/debug/app-debug.apk")
        assertTrue(apk.isFile, "未找到已构建的 debug production APK")
        ZipFile(apk).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertTrue(names.none { it.contains("kokoro-notices/") }, "production APK 不得携带测试许可证样本")
            assertTrue(names.none { it.contains("kokoro-lexicon-fixture/") }, "production APK 不得携带测试 fixture")
            assertTrue(names.none { it.contains("language/spike/") }, "production APK 不得携带 Spike 包")
            // ORT native libraries are required by the production Kokoro adapter; only test inputs/fixtures are forbidden.
            assertTrue(names.none { it.contains("onnxruntime-test") || it.contains("onnxruntime-fixture") }, "production APK 不得携带 ORT 测试输入")
        }
    }
}
