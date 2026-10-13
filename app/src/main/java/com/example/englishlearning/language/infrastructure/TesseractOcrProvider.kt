package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.EnglishOcrModelAssets
import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrImage
import com.example.englishlearning.language.domain.OcrModelAssetStatus
import com.example.englishlearning.language.domain.OcrOutcome
import com.example.englishlearning.language.domain.OcrProvider
import com.example.englishlearning.language.domain.OcrRegion
import com.example.englishlearning.language.domain.OcrResult
import kotlinx.coroutines.CancellationException
import java.io.File

data class TesseractLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

sealed interface TesseractRunResult {
    /** meanConfidence follows Tesseract's 0..100 scale and is normalised by the provider. */
    data class Blocks(
        val lines: List<TesseractLine>,
        val meanConfidence: Float,
    ) : TesseractRunResult

    data object InitUnavailable : TesseractRunResult
}

/** Seam over the native Tesseract engine so the provider stays unit-testable off-device. */
interface TesseractEngineRunner {
    suspend fun recognize(
        image: OcrImage,
        traineddataFile: File,
    ): TesseractRunResult
}

class TesseractOcrProvider(
    private val assets: EnglishOcrModelAssets,
    private val runner: TesseractEngineRunner,
) : OcrProvider {
    override val kind: OcrEngineKind = OcrEngineKind.Tesseract

    override suspend fun recognize(
        image: OcrImage,
    ): OcrOutcome {
        if (assets.status() is OcrModelAssetStatus.Installed) {
            val run =
                try {
                    runner.recognize(image, assets.installedFile() ?: missingTraineddataPlaceholder)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    return OcrOutcome.RecognitionFailed(error.message)
                }
            return when (run) {
                TesseractRunResult.InitUnavailable -> OcrOutcome.Unavailable
                is TesseractRunResult.Blocks ->
                    OcrOutcome.Success(
                        result =
                            OcrResult(
                                text = run.lines.joinToString("\n") { it.text },
                                regions =
                                    run.lines.map { line ->
                                        OcrRegion(
                                            left = line.left,
                                            top = line.top,
                                            right = line.right,
                                            bottom = line.bottom,
                                            confidence = run.meanConfidence / CONFIDENCE_SCALE,
                                        )
                                    },
                            ),
                        engine = kind,
                    )
            }
        }
        return OcrOutcome.Unavailable
    }

    private companion object {
        const val CONFIDENCE_SCALE = 100f

        // Only reached when a custom assets implementation reports Installed without exposing a file;
        // the runner then fails on the missing path, which maps to RecognitionFailed — never a silent success.
        val missingTraineddataPlaceholder = File("eng.traineddata.unavailable")
    }
}
