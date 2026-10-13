package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.language.domain.OcrEngineKind
import com.example.englishlearning.language.domain.OcrImage
import com.example.englishlearning.language.domain.OcrOutcome
import com.example.englishlearning.language.domain.OcrProvider
import com.example.englishlearning.language.domain.OcrRegion
import com.example.englishlearning.language.domain.OcrResult
import kotlinx.coroutines.CancellationException

/** A recognized text block flattened to the coordinates and confidence the domain port exposes. */
data class MlClientBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float?,
)

/** Seam over the ML Kit text recognizer so the provider stays unit-testable off-device. */
interface MlTextRecognitionClient {
    suspend fun recognize(
        image: OcrImage,
    ): List<MlClientBlock>
}

class MlKitTextRecognitionProvider(
    private val client: MlTextRecognitionClient,
) : OcrProvider {
    override val kind: OcrEngineKind = OcrEngineKind.MlKit

    override suspend fun recognize(
        image: OcrImage,
    ): OcrOutcome =
        try {
            val blocks = client.recognize(image)
            OcrOutcome.Success(
                result =
                    OcrResult(
                        text = blocks.joinToString("\n") { it.text },
                        regions =
                            blocks.map { block ->
                                OcrRegion(
                                    left = block.left,
                                    top = block.top,
                                    right = block.right,
                                    bottom = block.bottom,
                                    confidence = block.confidence ?: Float.NaN,
                                )
                            },
                    ),
                engine = kind,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error.indicatesUnavailableEngine()) OcrOutcome.Unavailable else OcrOutcome.RecognitionFailed(error.message)
        }

    private fun Throwable.indicatesUnavailableEngine(): Boolean =
        generateSequence(this) { it.cause }.any { cause -> cause.javaClass.simpleName in unavailableEngineNames }

    private companion object {
        val unavailableEngineNames = setOf("MissingDependencyException", "DownloadableModelException")
    }
}
