package com.example.englishlearning.phototranslation

import com.example.englishlearning.language.domain.OcrRegion

/**
 * 一块 OCR 文本 + 它在原图上占据的框（像素坐标，与送进识别器的那张位图同一坐标系）。
 *
 * [lowConfidence] 与 [missingText] 是**事实标记**，不是结论：它们只说明「这块凭什么默认不覆盖」，
 * 真正的取舍由 [OverlayBlockPlanner.defaultIncludedIds] 与用户逐块开关决定（F3-04）。
 */
data class OverlayBlockSource(
    val id: Int,
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val lowConfidence: Boolean,
    val missingText: Boolean,
)

/**
 * F3-04 的块清单与取舍判定（纯函数，无 Android 依赖）。
 *
 * 一条链：OCR 块 → [sources]（补 id 与两个事实标记）→ [defaultIncludedIds]（低置信默认不覆盖）
 * → 用户逐块开关的最终集合 → [plan]（喂给 [ImageTextOverlayRenderer] 的块清单）。
 */
object OverlayBlockPlanner {
    /** 低于此置信度（含 NaN）默认不覆盖，但用户可以单独打开。与屏侧逐行对照同一口径。 */
    const val LOW_CONFIDENCE_THRESHOLD = 0.5f

    fun sources(
        regions: List<OcrRegion>,
        lowConfidenceThreshold: Float = LOW_CONFIDENCE_THRESHOLD,
    ): List<OverlayBlockSource> =
        regions.mapIndexed { index, region ->
            OverlayBlockSource(
                id = index + 1,
                text = region.text,
                left = region.left,
                top = region.top,
                right = region.right,
                bottom = region.bottom,
                lowConfidence = region.confidence.isNaN() || region.confidence < lowConfidenceThreshold,
                missingText = region.text.isBlank(),
            )
        }

    /** 低置信与空文本块默认不进覆盖集合；其余默认覆盖。 */
    fun defaultIncludedIds(sources: List<OverlayBlockSource>): Set<Int> =
        sources.filter { !it.lowConfidence && !it.missingText }.map { it.id }.toSet()

    /**
     * 把「用户选中了哪些块」+「哪些块拿到了译文」折成渲染器输入。
     * 选中但没拿到译文的块**照样留在清单里**（eligible = true、translation 为空），
     * 由渲染器判 `MissingTranslation` 跳过并计数，界面才说得清「哪些块没画」。
     */
    fun plan(
        sources: List<OverlayBlockSource>,
        includedIds: Set<Int>,
        translations: Map<Int, String>,
    ): List<OverlayBlock> =
        sources.map { source ->
            OverlayBlock(
                id = source.id,
                left = source.left.toFloat(),
                top = source.top.toFloat(),
                right = source.right.toFloat(),
                bottom = source.bottom.toFloat(),
                translation = translations[source.id].orEmpty(),
                eligible = source.id in includedIds && !source.missingText,
            )
        }
}
