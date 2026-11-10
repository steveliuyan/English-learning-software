package com.example.englishlearning.screentranslation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import com.example.englishlearning.phototranslation.OverlayBackgroundSampler
import com.example.englishlearning.phototranslation.OverlayBlock
import com.example.englishlearning.phototranslation.OverlayBlockLayout
import com.example.englishlearning.phototranslation.OverlayGlyphMetrics
import com.example.englishlearning.phototranslation.OverlayRect
import com.example.englishlearning.phototranslation.OverlayTextFitting

/**
 * 覆盖层（F3-05）：一整块**可点击穿透**的透明 View，只负责把排版结论画到屏幕上。
 *
 * 排版复用 F3-04 的同一份 [OverlayTextFitting]——同一套字号钳制/折行/截断/「空间不足」规则，
 * 只是画布从位图换成屏幕。块坐标先用 [OverlayCoordinateMapper] 从截屏坐标系换到本 View 坐标系，
 * 所以状态栏高度或虚拟显示缩放都不会让译文偏位。
 */
class ScreenTranslationOverlayView(
    context: Context,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val plate = RectF()
    private var layouts: List<OverlayBlockLayout> = emptyList()
    private var source: Bitmap? = null
    private var mapper: OverlayCoordinateMapper? = null

    private val metrics =
        object : OverlayGlyphMetrics {
            override fun widthOf(
                text: String,
                textSizePx: Float,
            ): Float {
                paint.textSize = textSizePx
                return paint.measureText(text)
            }

            override fun lineHeightOf(textSizePx: Float): Float = textSizePx * LINE_SPACING
        }

    var lastDrawnBlocks: Int = 0
        private set
    var lastSkippedBlocks: Int = 0
        private set

    /**
     * 换一帧的覆盖结论。[blocks] 必须已经是截屏坐标系（换算在本方法里做），
     * [frame] 只用来取背景色，不参与其它计算。
     */
    fun submit(
        frame: Bitmap,
        blocks: List<OverlayBlock>,
        coordinateMapper: OverlayCoordinateMapper,
    ) {
        source = frame
        mapper = coordinateMapper
        // scaledDensity 在 API 34 起被弃用（它只是 density * fontScale 的派生值）；
        // 用 applyDimension 走同一套 SP→PX 口径，换版本不改语义。
        val minTextSizePx =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_TEXT_SP_PX, resources.displayMetrics)
        val config =
            OverlayTextFitting.Config(
                minTextSizePx = minTextSizePx,
                lineSpacingMultiplier = LINE_SPACING,
            )
        layouts = coordinateMapper.mapBlocks(blocks).map { OverlayTextFitting.fit(it, metrics, config) }
        lastDrawnBlocks = layouts.count { it is OverlayBlockLayout.Drawn }
        lastSkippedBlocks = layouts.size - lastDrawnBlocks
        invalidate()
    }

    fun clear() {
        layouts = emptyList()
        source = null
        mapper = null
        lastDrawnBlocks = 0
        lastSkippedBlocks = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frame = source ?: return
        val map = mapper ?: return
        for (layout in layouts) {
            if (layout !is OverlayBlockLayout.Drawn) continue
            val area = layout.area
            val background = plateColor(frame, map, area)
            paint.style = Paint.Style.FILL
            paint.color = background
            plate.set(area.left, area.top, area.right, area.bottom)
            canvas.drawRoundRect(plate, PLATE_RADIUS_PX, PLATE_RADIUS_PX, paint)

            paint.color = OverlayBackgroundSampler.readableTextColor(background)
            paint.textSize = layout.textSizePx
            var baseline = area.top + layout.textSizePx
            for (line in layout.lines) {
                canvas.drawText(line, area.left + TEXT_PADDING_PX, baseline, paint)
                baseline += layout.textSizePx * LINE_SPACING
            }
        }
    }

    /** 取该块在原帧上的背景中位色；取不到就退回半透明黑，保证译文仍然可读。 */
    private fun plateColor(
        frame: Bitmap,
        map: OverlayCoordinateMapper,
        area: OverlayRect,
    ): Int {
        val scale = map.scale
        if (scale <= 0f) return FALLBACK_PLATE
        val inverse =
            OverlayRect(
                left = (area.left - (width - frame.width * scale) / 2f) / scale,
                top = (area.top - (height - frame.height * scale) / 2f) / scale,
                right = (area.right - (width - frame.width * scale) / 2f) / scale,
                bottom = (area.bottom - (height - frame.height * scale) / 2f) / scale,
            )
        val colors = sampleRing(frame, inverse)
        if (colors.isEmpty()) return FALLBACK_PLATE
        val median = OverlayBackgroundSampler.medianColor(colors)
        return if (Color.alpha(median) == 0) FALLBACK_PLATE else median
    }

    private fun sampleRing(
        frame: Bitmap,
        area: OverlayRect,
    ): IntArray {
        val left = (area.left - RING_PX).toInt().coerceAtLeast(0)
        val top = (area.top - RING_PX).toInt().coerceAtLeast(0)
        val right = (area.right + RING_PX).toInt().coerceAtMost(frame.width - 1)
        val bottom = (area.bottom + RING_PX).toInt().coerceAtMost(frame.height - 1)
        if (right <= left || bottom <= top) return IntArray(0)
        val colors = ArrayList<Int>(64)
        var y = top
        while (y <= bottom) {
            var x = left
            while (x <= right) {
                val onRing =
                    y < area.top.toInt() || y > area.bottom.toInt() || x < area.left.toInt() || x > area.right.toInt()
                if (onRing) colors += frame.getPixel(x, y)
                x += 3
            }
            y += 3
        }
        return colors.toIntArray()
    }

    private companion object {
        const val LINE_SPACING = 1.2f
        const val MIN_TEXT_SP_PX = 11f
        const val PLATE_RADIUS_PX = 4f
        const val TEXT_PADDING_PX = 2f
        const val RING_PX = 4f
        const val FALLBACK_PLATE = 0xCC000000.toInt()
    }
}
