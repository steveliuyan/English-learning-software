package com.example.englishlearning.screentranslation

import com.example.englishlearning.phototranslation.OverlayBlock
import com.example.englishlearning.phototranslation.OverlayRect

/**
 * 截屏坐标系 → 悬浮窗坐标系（F3-05）。
 *
 * 截屏得到的是显示器像素（VirtualDisplay 的宽高），悬浮覆盖层铺的是整屏 View 的像素。
 * 两者一般等宽高，但状态栏/剪裁/虚拟显示缩放会让它们不等，所以一律按「等比缩放 + 居中」换算，
 * 保证叠上去的译文与源文字重合，不跟手错位。
 *
 * 纯计算：不碰 View、不碰 Bitmap；排版（[com.example.englishlearning.phototranslation.OverlayTextFitting]）
 * 在**换算之后**的坐标系里做，字号才是屏幕上看得到的像素字号。
 */
class OverlayCoordinateMapper(
    private val captureWidth: Int,
    private val captureHeight: Int,
    private val viewWidth: Float,
    private val viewHeight: Float,
) {
    init {
        require(captureWidth > 0 && captureHeight > 0) { "capture size" }
        require(viewWidth > 0f && viewHeight > 0f) { "view size" }
    }

    val scale: Float = minOf(viewWidth / captureWidth, viewHeight / captureHeight)

    private val offsetX: Float = (viewWidth - captureWidth * scale) / 2f
    private val offsetY: Float = (viewHeight - captureHeight * scale) / 2f

    fun map(rect: OverlayRect): OverlayRect =
        OverlayRect(
            left = offsetX + rect.left * scale,
            top = offsetY + rect.top * scale,
            right = offsetX + rect.right * scale,
            bottom = offsetY + rect.bottom * scale,
        )

    fun map(block: OverlayBlock): OverlayBlock =
        block.copy(
            left = offsetX + block.left * scale,
            top = offsetY + block.top * scale,
            right = offsetX + block.right * scale,
            bottom = offsetY + block.bottom * scale,
        )

    fun mapBlocks(blocks: List<OverlayBlock>): List<OverlayBlock> = blocks.map(::map)
}
