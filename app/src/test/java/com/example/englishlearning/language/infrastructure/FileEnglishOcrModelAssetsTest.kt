package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.OcrModelAssetInstallOutcome
import com.example.englishlearning.language.domain.OcrModelAssetStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileEnglishOcrModelAssetsTest {
    @TempDir
    lateinit var root: Path

    @AfterEach
    fun tearDown() {
        // 断言所有出口零残留：临时文件不得在测试结束后存活
        val leftovers = Files.walk(root).filter { it.fileName.toString().endsWith(TMP_SUFFIX) }.toList()
        assertTrue(leftovers.isEmpty(), "Temp files left behind: $leftovers")
    }

    private val validBytes: ByteArray
        get() = PINNED_BYTES.clone()

    private fun assets(block: suspend () -> ByteArray): FileEnglishOcrModelAssets =
        FileEnglishOcrModelAssets(root.toFile(), fetcher { block() }, OcrModelArtifactPin(PINNED_SIZE, HEX))

    @Test
    fun `no file installed reports NotInstalled`() = runTest {
        assertEquals(OcrModelAssetStatus.NotInstalled, assets { validBytes }.status())
    }

    @Test
    fun `install writes verified file and reports Installed`() = runTest {
        val assets = assets { validBytes }

        val outcome = assets.install(onProgress = { })

        assertIs<OcrModelAssetInstallOutcome.Installed>(outcome)
        assertEquals(PINNED_SIZE, outcome.sizeBytes)
        assertEquals(OcrModelAssetStatus.Installed(PINNED_SIZE), assets.status())
        assertEquals(HEX, sha256(assets.installedFile()!!))
    }

    @Test
    fun `integrity mismatch leaves no file behind`() = runTest {
        val assets = assets { ByteArray(PINNED_SIZE.toInt()) { (it % 7).toByte() } }

        val outcome = assets.install(onProgress = { })

        assertIs<OcrModelAssetInstallOutcome.IntegrityMismatch>(outcome)
        assertEquals(OcrModelAssetStatus.NotInstalled, assets.status())
        assertTrue(assets.installedFile() == null)
    }

    @Test
    fun `size mismatch leaves no file behind`() = runTest {
        val assets = assets { validBytes.copyOfRange(0, 16) }

        val outcome = assets.install(onProgress = { })

        assertIs<OcrModelAssetInstallOutcome.IntegrityMismatch>(outcome)
        assertEquals(OcrModelAssetStatus.NotInstalled, assets.status())
    }

    @Test
    fun `download failure reports failure and leaves no file`() = runTest {
        val assets = assets { throw java.io.IOException("offline") }

        val outcome = assets.install(onProgress = { })

        assertIs<OcrModelAssetInstallOutcome.DownloadFailed>(outcome)
        assertEquals(OcrModelAssetStatus.NotInstalled, assets.status())
    }

    @Test
    fun `uninstall removes the file and is idempotent`() = runTest {
        val assets = assets { validBytes }
        assets.install(onProgress = { })

        assets.uninstall()

        assertEquals(OcrModelAssetStatus.NotInstalled, assets.status())
        assets.uninstall()
        assertEquals(OcrModelAssetStatus.NotInstalled, assets.status())
    }

    @Test
    fun `corrupted file of wrong size reports NotInstalled and reinstall overwrites it`() = runTest {
        val assets = assets { validBytes }
        val target = root.resolve(ASSET_FILE_NAME).toFile()
        target.parentFile?.mkdirs()
        target.writeBytes(byteArrayOf(1, 2, 3))

        assertEquals(OcrModelAssetStatus.NotInstalled, assets.status())
        val outcome = assets.install(onProgress = { })
        assertIs<OcrModelAssetInstallOutcome.Installed>(outcome)
        assertEquals(HEX, sha256(assets.installedFile()!!))
    }

    private companion object {
        const val TMP_SUFFIX = ".part"
        const val ASSET_FILE_NAME = "tessdata/eng.traineddata"
        const val PINNED_SIZE = 1024L

        // 与固定校验一致的测试字节：全零 1024 字节
        val PINNED_BYTES = ByteArray(PINNED_SIZE.toInt())
        val HEX = sha256(PINNED_BYTES)

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun sha256(file: java.io.File): String = sha256(file.readBytes())

        fun fetcher(block: suspend () -> ByteArray): TraineddataFetcher =
            TraineddataFetcher { _ -> block() }
    }
}
