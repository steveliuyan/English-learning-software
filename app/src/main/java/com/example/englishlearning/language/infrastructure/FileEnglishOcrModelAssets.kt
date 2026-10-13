package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.EnglishOcrModelAssets
import com.example.englishlearning.language.domain.OcrModelAssetInstallOutcome
import com.example.englishlearning.language.domain.OcrModelAssetStatus
import java.io.File
import java.security.MessageDigest

/**
 * 英文 OCR 模型（tessdata_fast eng.traineddata）的按需下载资产。
 * 许可与完整性钉值见 docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md。
 */
data class OcrModelArtifactPin(
    val sizeBytes: Long,
    val sha256Hex: String,
)

val ENGLISH_TESSDATA_FAST_PIN =
    OcrModelArtifactPin(
        sizeBytes = 4_113_088L,
        sha256Hex = "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
    )

/** Streams the model artifact and reports cumulative bytes read; must not keep partial buffers on failure. */
fun interface TraineddataFetcher {
    suspend fun fetch(
        onBytesRead: (Int) -> Unit,
    ): ByteArray
}

class FileEnglishOcrModelAssets(
    private val root: File,
    private val fetcher: TraineddataFetcher,
    private val pin: OcrModelArtifactPin = ENGLISH_TESSDATA_FAST_PIN,
) : EnglishOcrModelAssets {
    private val modelDirectory = File(root, MODEL_DIRECTORY_NAME)
    private val targetFile = File(modelDirectory, MODEL_FILE_NAME)
    private val partFile = File(modelDirectory, "$MODEL_FILE_NAME$PART_SUFFIX")

    override suspend fun status(): OcrModelAssetStatus {
        val installed = installedFile() ?: return OcrModelAssetStatus.NotInstalled
        return OcrModelAssetStatus.Installed(installed.length())
    }

    override suspend fun install(
        onProgress: (Float) -> Unit,
    ): OcrModelAssetInstallOutcome {
        modelDirectory.mkdirs()
        partFile.delete()
        val bytes =
            try {
                fetcher.fetch { read -> onProgress(if (pin.sizeBytes <= 0L) 1f else (read.toFloat() / pin.sizeBytes).coerceIn(0f, 1f)) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                partFile.delete()
                throw cancelled
            } catch (error: Exception) {
                partFile.delete()
                return OcrModelAssetInstallOutcome.DownloadFailed(error.message)
            }
        if (bytes.size.toLong() != pin.sizeBytes || sha256Hex(bytes) != pin.sha256Hex) {
            partFile.delete()
            return OcrModelAssetInstallOutcome.IntegrityMismatch
        }
        return try {
            targetFile.delete()
            partFile.writeBytes(bytes)
            if (!partFile.renameTo(targetFile)) {
                OcrModelAssetInstallOutcome.DownloadFailed("Could not finalize the model file")
            } else {
                OcrModelAssetInstallOutcome.Installed(pin.sizeBytes)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            OcrModelAssetInstallOutcome.DownloadFailed(error.message)
        } finally {
            partFile.delete()
        }
    }

    override suspend fun uninstall() {
        targetFile.delete()
        partFile.delete()
    }

    override fun installedFile(): File? =
        if (targetFile.isFile && targetFile.length() == pin.sizeBytes) targetFile else null

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        // Tesseract init(datapath, "eng") resolves <datapath>/tessdata/eng.traineddata:
        // the engine receives this asset root as datapath, so the layout stays convention-compatible.
        const val MODEL_DIRECTORY_NAME = "tessdata"
        const val MODEL_FILE_NAME = "eng.traineddata"
        const val PART_SUFFIX = ".part"
    }
}
