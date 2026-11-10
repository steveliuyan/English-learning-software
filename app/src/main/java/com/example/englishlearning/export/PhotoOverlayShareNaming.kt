package com.example.englishlearning.export

/**
 * 原位译文覆盖图的临时文件名（F3-04）。
 *
 * 确定性 + 只含 ASCII 安全字符：分享失败重试时同一张图不产生一堆同义文件，
 * 临时区清理也能按 `photo-translation-` 前缀整批扫掉。
 */
object PhotoOverlayShareNaming {
    const val PREFIX = "photo-translation-"
    const val EXTENSION = "png"

    fun fileName(epochMillis: Long): String = "$PREFIX$epochMillis.$EXTENSION"
}
