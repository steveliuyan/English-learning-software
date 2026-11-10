package com.example.englishlearning.phototranslation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AC3-10 / AC3-11：给同一组包围盒与译文必得同一份排版结论——字号在钳制区间、按块宽折行、
 * 放不下截断加省略号、连下限字号都放不下就保持原样并标「空间不足」。
 */
class OverlayTextFittingTest {

    /** 确定性假件：中日韩字符宽 = 字号，其余 = 字号 × 0.5，行高 = 字号 × 1.2。 */
    private val metrics = object : OverlayGlyphMetrics {
        override fun widthOf(text: String, textSizePx: Float): Float =
            text.sumOf { ch -> (if (ch.code > 0x2E80) textSizePx else textSizePx * 0.5).toDouble() }.toFloat()

        override fun lineHeightOf(textSizePx: Float): Float = textSizePx * 1.2f
    }

    private fun block(
        translation: String,
        width: Float = 200f,
        height: Float = 40f,
        eligible: Boolean = true,
    ) = OverlayBlock(
        id = 1,
        left = 10f,
        top = 100f,
        right = 10f + width,
        bottom = 100f + height,
        translation = translation,
        eligible = eligible,
    )

    private fun config(
        minTextSizePx: Float = 10f,
        expansionPerSidePx: Float = 0f,
    ) = OverlayTextFitting.Config(minTextSizePx = minTextSizePx, expansionPerSidePx = expansionPerSidePx)

    @Test
    fun shortTextIsDrawnAtTheSizeDerivedFromBlockHeight() {
        val layout = OverlayTextFitting.fit(block("短文"), metrics, config())

        assertTrue(layout is OverlayBlockLayout.Drawn)
        val drawn = layout as OverlayBlockLayout.Drawn
        assertEquals(30f, drawn.textSizePx)
        assertEquals(listOf("短文"), drawn.lines)
        assertEquals(false, drawn.truncated)
        assertEquals(10f, drawn.area.left)
        assertEquals(100f, drawn.area.top)
    }

    @Test
    fun derivedSizeIsClampedToTheUpperBoundOnTallBlocks() {
        val layout = OverlayTextFitting.fit(block("字", height = 400f), metrics, config())

        assertEquals(72f, (layout as OverlayBlockLayout.Drawn).textSizePx)
    }

    @Test
    fun textShrinksUntilItFitsButNeverBelowTheReadableFloor() {
        val layout = OverlayTextFitting.fit(block("一二三四五六七八九十", width = 60f, height = 40f), metrics, config())

        assertTrue(layout is OverlayBlockLayout.Drawn)
        val drawn = layout as OverlayBlockLayout.Drawn
        assertTrue(drawn.textSizePx >= 10f, "size=${drawn.textSizePx}")
        assertTrue(drawn.textSizePx < 30f, "size=${drawn.textSizePx}")
        assertEquals("一二三四五六七八九十", drawn.lines.joinToString(""))
        assertTrue(drawn.lines.size * metrics.lineHeightOf(drawn.textSizePx) <= drawn.area.height)
    }

    @Test
    fun overflowAtTheFloorSizeTruncatesWithEllipsisInsteadOfSquashing() {
        val layout = OverlayTextFitting.fit(block("一二三四五六七八九十", width = 60f, height = 20f), metrics, config())

        val drawn = layout as OverlayBlockLayout.Drawn
        assertEquals(10f, drawn.textSizePx)
        assertEquals(true, drawn.truncated)
        assertEquals(1, drawn.lines.size)
        assertTrue(drawn.lines.single().endsWith("…"))
        assertTrue(metrics.widthOf(drawn.lines.single(), drawn.textSizePx) <= drawn.area.width)
    }

    @Test
    fun blockTooShortForEvenOneLineIsLeftAloneAndMarkedSpaceInsufficient() {
        val layout = OverlayTextFitting.fit(block("短文", height = 8f), metrics, config())

        assertEquals(OverlayBlockLayout.Skipped(1, OverlaySkipReason.SpaceInsufficient), layout)
    }

    @Test
    fun ineligibleBlankOrDegenerateBlocksAreSkippedWithDistinctReasons() {
        assertEquals(
            OverlayBlockLayout.Skipped(1, OverlaySkipReason.NotEligible),
            OverlayTextFitting.fit(block("短文", eligible = false), metrics, config()),
        )
        assertEquals(
            OverlayBlockLayout.Skipped(1, OverlaySkipReason.MissingTranslation),
            OverlayTextFitting.fit(block("   "), metrics, config()),
        )
        assertEquals(
            OverlayBlockLayout.Skipped(1, OverlaySkipReason.DegenerateBlock),
            OverlayTextFitting.fit(block("短文", width = 0f), metrics, config()),
        )
    }

    @Test
    fun borrowedGapEnlargesTheDrawableArea() {
        val tight = OverlayTextFitting.fit(block("一二三四五六七八九十", width = 60f, height = 40f), metrics, config())
        val borrowed = OverlayTextFitting.fit(
            block("一二三四五六七八九十", width = 60f, height = 40f),
            metrics,
            config(expansionPerSidePx = 12f),
        )

        val tightDrawn = tight as OverlayBlockLayout.Drawn
        val borrowedDrawn = borrowed as OverlayBlockLayout.Drawn
        assertEquals(88f, borrowedDrawn.area.top)
        assertEquals(152f, borrowedDrawn.area.bottom)
        assertTrue(borrowedDrawn.textSizePx >= tightDrawn.textSizePx)
    }

    @Test
    fun asciiTextWrapsBetweenWordsAndBreaksOnlyUnbreakableLongWords() {
        val lines = OverlayTextFitting.wrap("hello world again", maxWidth = 120f, textSizePx = 20f, metrics = metrics)
        assertEquals(listOf("hello world", "again"), lines)

        val broken = OverlayTextFitting.wrap("aaaaaaaaaa", maxWidth = 40f, textSizePx = 20f, metrics = metrics)
        assertEquals(listOf("aaaa", "aaaa", "aa"), broken)
    }

    @Test
    fun sameInputAlwaysYieldsTheSameLayout() {
        val block = block("一二三四五六七八九十", width = 60f, height = 40f)

        val first = OverlayTextFitting.fit(block, metrics, config(expansionPerSidePx = 6f))
        val second = OverlayTextFitting.fit(block, metrics, config(expansionPerSidePx = 6f))

        assertEquals(first, second)
    }
}

class OverlayBackgroundSamplerTest {

    @Test
    fun medianOfTheRingIgnoresExtremePixels() {
        val ring = intArrayOf(0xFF000000.toInt(), 0xFFEEEEEE.toInt(), 0xFF202020.toInt(), 0xFFFFFFFF.toInt(), 0xFF101010.toInt())

        assertEquals(0xFF202020.toInt(), OverlayBackgroundSampler.medianColor(ring))
        assertEquals(OverlayBackgroundSampler.TRANSPARENT, OverlayBackgroundSampler.medianColor(intArrayOf()))
    }

    @Test
    fun textColorFlipsWithBackgroundLuminance() {
        assertEquals(OverlayBackgroundSampler.WHITE, OverlayBackgroundSampler.readableTextColor(0xFF101010.toInt()))
        assertEquals(OverlayBackgroundSampler.NEAR_BLACK, OverlayBackgroundSampler.readableTextColor(0xFFF5F5F5.toInt()))
    }
}
