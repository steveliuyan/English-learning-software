package com.example.englishlearning.phototranslation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** 渲染结果：新位图 + 每块的排版结论（界面据此标「空间不足」「未覆盖」）。 */
data class OverlayRenderResult(
    val image: Bitmap,
    val layouts: List<OverlayBlockLayout>,
) {
    val skipped: List<OverlayBlockLayout.Skipped> = layouts.filterIsInstance<OverlayBlockLayout.Skipped>()
}

/**
 * 原位覆盖渲染器（F3-04 / AC3-10 / AC3-11）：擦除 = 背景取样填充，重绘 = 按 [OverlayTextFitting] 的结论画字。
 *
 * 输入位图**只读**：产物是 copy 出来的新位图，原图字节不变。同输入必得同输出（无随机、无时间依赖）。
 * 本轮不引入任何图像修复网络，纹理背景会有补丁感——这是决策文档里写明的已知取舍。
 */
class ImageTextOverlayRenderer(
    /** 11sp 等效像素，由调用方按当前 density 换算后传入。 */
    private val minTextSizePx: Float,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val metrics = object : OverlayGlyphMetrics {
        override fun widthOf(text: String, textSizePx: Float): Float {
            paint.textSize = textSizePx
            return paint.measureText(text)
        }

        override fun lineHeightOf(textSizePx: Float): Float = textSizePx * DEFAULT_LINE_SPACING
    }

    fun render(
        source: Bitmap,
        blocks: List<OverlayBlock>,
        expansionPerSidePx: Float = 0f,
    ): OverlayRenderResult {
        val config = OverlayTextFitting.Config(
            minTextSizePx = minTextSizePx,
            lineSpacingMultiplier = DEFAULT_LINE_SPACING,
            expansionPerSidePx = expansionPerSidePx,
        )
        val layouts = blocks.map { OverlayTextFitting.fit(it, metrics, config) }
        val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: return OverlayRenderResult(source, layouts)
        val canvas = Canvas(output)
        for (layout in layouts) {
            if (layout !is OverlayBlockLayout.Drawn) continue
            paintBlock(canvas, source, layout)
        }
        return OverlayRenderResult(output, layouts)
    }

    private fun paintBlock(
        canvas: Canvas,
        source: Bitmap,
        layout: OverlayBlockLayout.Drawn,
    ) {
        val area = layout.area
        val background = OverlayBackgroundSampler.medianColor(sampleRing(source, area))
        val opaque = if (Color.alpha(background) == 0) Color.WHITE else background

        paint.style = Paint.Style.FILL
        paint.color = opaque
        canvas.drawRect(area.left, area.top, area.right, area.bottom, paint)

        // 2px 羽化：边界一圈同色半透明，弱化硬切口。
        paint.color = opaque
        paint.alpha = FEATHER_ALPHA
        canvas.drawRect(
            area.left - FEATHER_PX,
            area.top - FEATHER_PX,
            area.right + FEATHER_PX,
            area.bottom + FEATHER_PX,
            paint,
        )
        paint.alpha = 255

        paint.color = OverlayBackgroundSampler.readableTextColor(opaque)
        paint.textSize = layout.textSizePx
        val lineHeight = layout.textSizePx * DEFAULT_LINE_SPACING
        val contentHeight = lineHeight * layout.lines.size
        var baseline = area.top + (area.height - contentHeight) / 2f + layout.textSizePx
        for (line in layout.lines) {
            canvas.drawText(line, area.left + TEXT_PADDING_PX, baseline, paint)
            baseline += lineHeight
        }
    }

    /** 采样包围盒外扩一圈的像素（步长 2 减少读数），越界处跳过。 */
    private fun sampleRing(
        source: Bitmap,
        area: OverlayRect,
    ): IntArray {
        val left = (area.left - RING_PX).toInt().coerceAtLeast(0)
        val top = (area.top - RING_PX).toInt().coerceAtLeast(0)
        val right = (area.right + RING_PX).toInt().coerceAtMost(source.width - 1)
        val bottom = (area.bottom + RING_PX).toInt().coerceAtMost(source.height - 1)
        if (right <= left || bottom <= top) return IntArray(0)

        val innerLeft = area.left.toInt()
        val innerTop = area.top.toInt()
        val innerRight = area.right.toInt()
        val innerBottom = area.bottom.toInt()
        val colors = ArrayList<Int>()
        var y = top
        while (y <= bottom) {
            var x = left
            while (x <= right) {
                val onRing = y < innerTop || y > innerBottom || x < innerLeft || x > innerRight
                if (onRing) colors += source.getPixel(x, y)
                x += 2
            }
            y += 2
        }
        return colors.toIntArray()
    }

    private companion object {
        const val DEFAULT_LINE_SPACING = 1.2f
        const val RING_PX = 4f
        const val FEATHER_PX = 2f
        const val FEATHER_ALPHA = 90
        const val TEXT_PADDING_PX = 2f
    }
}
