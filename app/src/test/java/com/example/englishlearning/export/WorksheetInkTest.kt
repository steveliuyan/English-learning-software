package com.example.englishlearning.export

import kotlin.math.pow
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * 锁定用户要求：默写纸里「单词」和「音标」必须有明显的区分。
 *
 * 断言的是可度量的相对关系（谁更暗、谁更小、差得够不够远），而不是具体色值，
 * 这样以后微调配色不会误报，但把音标调成和单词一样黑、或把字号调大到无法区分时一定会失败。
 */
class WorksheetInkTest {
    @Test
    fun `lemma ink is the darkest and ipa ink is clearly lighter`() {
        val lemma = relativeLuminance(WorksheetInk.LEMMA)
        val ipa = relativeLuminance(WorksheetInk.IPA)
        val body = relativeLuminance(WorksheetInk.BODY)

        assertTrue(lemma < 0.05, "单词必须是近黑，实际亮度 $lemma")
        assertTrue(ipa > 0.30, "音标必须是明显的淡灰，不能接近黑色，实际亮度 $ipa")
        assertTrue(lemma < body && body < ipa, "明暗层次应为 单词 < 释义 < 音标，实际 $lemma / $body / $ipa")
        assertTrue(ipa - lemma > 0.25, "单词与音标的亮度差太小，用户看不出区别：${ipa - lemma}")
    }

    @Test
    fun `ipa is set smaller than the lemma so the hierarchy survives greyscale printing`() {
        assertTrue(
            WorksheetInk.LEMMA_SIZE > WorksheetInk.IPA_SIZE,
            "音标字号必须小于单词：${WorksheetInk.LEMMA_SIZE} vs ${WorksheetInk.IPA_SIZE}",
        )
        assertTrue(WorksheetInk.IPA_SIZE >= 6f, "音标也不能小到印不清：${WorksheetInk.IPA_SIZE}")
    }

    /** WCAG 相对亮度，0 为纯黑、1 为纯白。 */
    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val ratio = value / 255.0
            return if (ratio <= 0.03928) ratio / 12.92 else ((ratio + 0.055) / 1.055).pow(2.4)
        }

        return 0.2126 * channel((color shr 16) and 0xFF) +
            0.7152 * channel((color shr 8) and 0xFF) +
            0.0722 * channel(color and 0xFF)
    }
}
