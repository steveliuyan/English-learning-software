package com.example.englishlearning.screentranslation

import android.graphics.Bitmap

/**
 * 从一帧截图里取指纹样本（F3-05 / AC3-12）。缩到很小的网格再取亮度，
 * 既省时间也天然抗压缩噪声；整帧像素**不落盘、不出内存**，用完即弃。
 */
object ScreenFrameSampler {
    const val COLUMNS = 32
    const val ROWS = 64

    fun fingerprintOf(bitmap: Bitmap): Long {
        val small =
            if (bitmap.width == COLUMNS && bitmap.height == ROWS) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, COLUMNS, ROWS, true)
            }
        val pixels = IntArray(COLUMNS * ROWS)
        small.getPixels(pixels, 0, COLUMNS, 0, 0, COLUMNS, ROWS)
        if (small !== bitmap) small.recycle()
        val luma =
            IntArray(pixels.size) { index ->
                val argb = pixels[index]
                ScreenCaptureFingerprint.lumaOf(
                    red = (argb shr 16) and 0xFF,
                    green = (argb shr 8) and 0xFF,
                    blue = argb and 0xFF,
                )
            }
        return ScreenCaptureFingerprint.of(luma)
    }
}
