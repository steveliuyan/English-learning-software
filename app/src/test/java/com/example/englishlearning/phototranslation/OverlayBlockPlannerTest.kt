package com.example.englishlearning.phototranslation

import com.example.englishlearning.language.domain.OcrRegion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F3-04：块清单与「哪些块参与覆盖」的判定必须是纯函数，可被单测钉住。 */
class OverlayBlockPlannerTest {
    private fun region(
        text: String,
        confidence: Float = 0.9f,
        left: Int = 10,
        top: Int = 20,
        right: Int = 110,
        bottom: Int = 60,
    ): OcrRegion =
        OcrRegion(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            confidence = confidence,
            text = text,
        )

    @Test
    fun sourcesAssignOneBasedIdsAndKeepGeometryAndText() {
        val sources =
            OverlayBlockPlanner.sources(
                listOf(
                    region("Hello there", left = 10, top = 20, right = 110, bottom = 60),
                    region("Good morning", left = 12, top = 70, right = 90, bottom = 100),
                ),
            )

        assertEquals(2, sources.size)
        assertEquals(listOf(1, 2), sources.map { it.id })
        assertEquals("Hello there", sources[0].text)
        assertEquals(10, sources[0].left)
        assertEquals(60, sources[0].bottom)
        assertEquals("Good morning", sources[1].text)
    }

    @Test
    fun sourcesFlagMissingConfidenceAsLowConfidence() {
        val sources = OverlayBlockPlanner.sources(listOf(region("blurry", confidence = Float.NaN)))

        assertTrue(sources.single().lowConfidence)
    }

    @Test
    fun sourcesSplitAtTheLowConfidenceThreshold() {
        val sources =
            OverlayBlockPlanner.sources(
                listOf(
                    region("sharp", confidence = 0.5f),
                    region("fuzzy", confidence = 0.49f),
                ),
            )

        assertFalse(sources[0].lowConfidence)
        assertTrue(sources[1].lowConfidence)
    }

    @Test
    fun sourcesTreatBlankTextAsMissingBlockText() {
        // 服务端契约（AC3-07）要求块内文本非空；空文本块即使有框也不参与覆盖。
        val sources = OverlayBlockPlanner.sources(listOf(region("   ", confidence = 0.99f)))

        assertTrue(sources.single().missingText)
    }

    @Test
    fun defaultIncludedIdsLeaveOutLowConfidenceBlocks() {
        val sources =
            OverlayBlockPlanner.sources(
                listOf(
                    region("a", confidence = 0.9f),
                    region("b", confidence = 0.2f),
                    region("c", confidence = 0.7f),
                ),
            )

        assertEquals(setOf(1, 3), OverlayBlockPlanner.defaultIncludedIds(sources))
    }

    @Test
    fun planMarksEligibilityFromIncludedIdsAndTakesTranslationsById() {
        val sources = OverlayBlockPlanner.sources(listOf(region("a"), region("b")))

        val blocks =
            OverlayBlockPlanner.plan(
                sources = sources,
                includedIds = setOf(2),
                translations = mapOf(2 to "乙"),
            )

        assertEquals(listOf(1, 2), blocks.map { it.id })
        assertFalse(blocks[0].eligible)
        assertEquals("", blocks[0].translation)
        assertTrue(blocks[1].eligible)
        assertEquals("乙", blocks[1].translation)
        assertEquals(110f, blocks[1].right)
        assertEquals(60f, blocks[1].bottom)
    }

    @Test
    fun planKeepsIncludedButUntranslatedBlocksSoTheRendererCanReportThem() {
        val sources = OverlayBlockPlanner.sources(listOf(region("a")))

        val blocks = OverlayBlockPlanner.plan(sources, includedIds = setOf(1), translations = emptyMap())

        assertTrue(blocks.single().eligible)
        assertEquals("", blocks.single().translation)
    }

    @Test
    fun emptyRegionsProduceEmptyPlan() {
        assertEquals(emptyList(), OverlayBlockPlanner.sources(emptyList()))
        assertEquals(emptySet<Int>(), OverlayBlockPlanner.defaultIncludedIds(emptyList()))
        assertEquals(emptyList(), OverlayBlockPlanner.plan(emptyList(), emptySet(), emptyMap()))
    }
}
