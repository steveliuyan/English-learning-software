package com.example.englishlearning.screentranslation

/**
 * 屏幕帧指纹（F3-05 / AC3-12）：把整屏缩成一小格亮度样本再混成一个 Long。
 *
 * 目标不是「认出这是什么屏」，而是「判断是不是同一屏」：
 * 滚动/切页必须变，抗锯齿与细微抖动必须不变。所以先量化（亮度右移 [QUANTIZE_SHIFT] 位）再混，
 * 并且只取网格亮度，不取颜色——深色主题下的底色差异不该干扰判断。
 *
 * 量化必然有档位边界：恰好压在边界上的抗锯齿抖动可能翻档，被判成「不同屏」。这类误判只会多花一次请求，
 * 由 3 秒节流兜底；反之为了抗噪而放弃去重则会少发真正的滚动屏，代价更高。
 *
 * 纯函数，无 Android 依赖；从 Bitmap 取样本在 Android 侧（ScreenFrameSampler）。
 */
object ScreenCaptureFingerprint {
    /** 亮度量化的丢弃位数：8 - 3 = 5 位有效亮度，能吃掉压缩噪声，又不至于把浅灰与白判成同屏。 */
    const val QUANTIZE_SHIFT = 3

    private const val FNV_OFFSET = -3750763034362895579L // 0xcbf29ce484222325
    private const val FNV_PRIME = 1099511628211L

    /**
     * [lumaSamples] 必须非空且顺序稳定（行优先）。同一组样本永远得到同一个值；
     * 任一格的量化亮度变化都会改变结果（见单测）。
     */
    fun of(lumaSamples: IntArray): Long {
        require(lumaSamples.isNotEmpty()) { "empty fingerprint samples" }
        var hash = FNV_OFFSET
        hash = mix(hash, 0x51_47L)
        for (luma in lumaSamples) {
            val quantized = (luma.coerceIn(0, 255)) shr QUANTIZE_SHIFT
            hash = mix(hash, quantized.toLong())
        }
        hash = mix(hash, lumaSamples.size.toLong())
        return hash
    }

    /** 亮度换算：Rec.601 定点近似，纯整数，避免浮点在不同 JVM 上的抖动。 */
    fun lumaOf(
        red: Int,
        green: Int,
        blue: Int,
    ): Int = (299 * red + 587 * green + 114 * blue) / 1000

    private fun mix(
        hash: Long,
        value: Long,
    ): Long {
        var mixed = hash xor value
        mixed *= FNV_PRIME
        return mixed
    }
}
