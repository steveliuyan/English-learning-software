package com.example.englishlearning.language.domain

/** Encoded image bytes handed to a local OCR engine; never persisted by the domain layer itself. */
class OcrImage(
    val bytes: ByteArray,
)

enum class OcrEngineKind {
    MlKit,
    Tesseract,
}

/**
 * Reading direction of one recognized block. F3-04 原位覆盖要「顺着原来排版」重绘，
 * 所以朝向必须在契约里带出来，不能由渲染层猜。
 */
enum class OcrBlockOrientation {
    Horizontal,
    Vertical,
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

/**
 * One recognized block: its own text plus the box it occupies. [text] 为空表示该 Provider
 * 只给了几何不给块内文本——这类结果不得进入原位覆盖流程（AC3-07），只能用于对照视图。
 */
data class OcrRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
    val text: String = "",
    val orientation: OcrBlockOrientation = OcrBlockOrientation.Horizontal,
) {
    val width: Int get() = right - left

    val height: Int get() = bottom - top

    /** 只按几何推断阅读方向；[orientation] 才是引擎给出的权威值，二者分开以免覆盖掉真实朝向。 */
    fun detectedOrientation(): OcrBlockOrientation =
        if (height > width) OcrBlockOrientation.Vertical else OcrBlockOrientation.Horizontal
}

/** 原位覆盖的准入结论：不合格必须在渲染之前就给出确定理由，而不是画出一张半错的图。 */
sealed interface InPlaceCoverageEligibility {
    data object Eligible : InPlaceCoverageEligibility

    data class Rejected(val reason: InPlaceCoverageRejection) : InPlaceCoverageEligibility
}

enum class InPlaceCoverageRejection {
    /** 没有任何块，覆盖无从谈起。 */
    NoBlocks,

    /** 有块但没有块内文本（旧 Provider / 只有整段字符串），画出去就是假覆盖。 */
    MissingBlockText,

    /** 包围盒退化（宽或高非正），无法推出字号与折行宽度。 */
    DegenerateBlockGeometry,
}

fun OcrResult.inPlaceCoverageEligibility(): InPlaceCoverageEligibility {
    if (regions.isEmpty()) return InPlaceCoverageEligibility.Rejected(InPlaceCoverageRejection.NoBlocks)
    if (regions.any { it.text.isBlank() }) {
        return InPlaceCoverageEligibility.Rejected(InPlaceCoverageRejection.MissingBlockText)
    }
    if (regions.any { it.width <= 0 || it.height <= 0 }) {
        return InPlaceCoverageEligibility.Rejected(InPlaceCoverageRejection.DegenerateBlockGeometry)
    }
    return InPlaceCoverageEligibility.Eligible
}
