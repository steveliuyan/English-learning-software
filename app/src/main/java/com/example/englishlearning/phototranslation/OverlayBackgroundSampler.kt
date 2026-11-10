package com.example.englishlearning.phototranslation

/**
 * 背景取样填充的取色规则（F3-04）。纯计算：入参是 ARGB 整数数组，不依赖 Android 图形类，
 * 因此「一圈像素取中位色」这一步可以被单元测试逐值钉住。
 */
object OverlayBackgroundSampler {

    /** 取一圈像素的中位亮度色：极端像素（反光、纯黑文字边缘）不会把整块涂抹色拉偏。 */
    fun medianColor(colors: IntArray): Int {
        if (colors.isEmpty()) return TRANSPARENT
        val sorted = colors.sortedWith(compareBy { luminance(it) })
        return sorted[sorted.size / 2]
    }

    /** 相对亮度 0..1（粗口径，够用来挑前景色）。 */
    fun luminance(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /** 深底配近白字、浅底配近黑字——不在图上画对比度不合格的译文。 */
    fun readableTextColor(background: Int): Int = if (luminance(background) < 0.5f) WHITE else NEAR_BLACK

    const val TRANSPARENT: Int = 0x00000000
    const val WHITE: Int = -1 // 0xFFFFFFFF
    const val NEAR_BLACK: Int = -15066598 // 0xFF1A1A1A
}
