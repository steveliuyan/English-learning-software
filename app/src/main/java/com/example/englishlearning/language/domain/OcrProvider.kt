package com.example.englishlearning.language.domain

/** Encoded image bytes handed to a local OCR engine; never persisted by the domain layer itself. */
class OcrImage(
    val bytes: ByteArray,
)

enum class OcrEngineKind {
    MlKit,
    Tesseract,
}

interface OcrProvider {
    val kind: OcrEngineKind

    suspend fun recognize(image: OcrImage): OcrOutcome
}

/**
 * Per-provider result. OCR categories are strictly separate from translation failures:
 * nothing here may be re-labelled as an AI/translation error.
 */
sealed interface OcrOutcome {
    data class Success(val result: OcrResult, val engine: OcrEngineKind) : OcrOutcome

    /** The engine cannot run on this device right now (missing GMS/module/model). */
    data object Unavailable : OcrOutcome

    /** The engine ran but could not read the image. Still an OCR failure, never a translation failure. */
    data class RecognitionFailed(val reason: String?) : OcrOutcome
}

data class OcrResult(
    val text: String,
    val regions: List<OcrRegion>,
)

data class OcrRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
)
