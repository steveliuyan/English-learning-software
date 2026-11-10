package com.example.englishlearning.language.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * F3-04：原位译文覆盖要求 OCR 结果携带块级信息（文本 + 包围盒 + 置信度 + 朝向）。
 * 只有整段字符串、拿不到「哪块矩形对应哪段文字」的实现，必须被挡在覆盖流程之外（AC3-07）。
 */
class OcrBlockContractTest {
    @Test
    fun `region carries its own text, orientation and measured size`() {
        val region =
            OcrRegion(
                left = 10,
                top = 20,
                right = 210,
                bottom = 60,
                confidence = 0.91f,
                text = "progressive education",
            )

        assertEquals("progressive education", region.text)
        assertEquals(OcrBlockOrientation.Horizontal, region.orientation)
        assertEquals(200, region.width)
        assertEquals(40, region.height)
    }

    @Test
    fun `tall narrow region defaults to vertical orientation only when explicitly detected`() {
        val region =
            OcrRegion(left = 0, top = 0, right = 40, bottom = 400, confidence = 0.5f, text = "縦書き")

        assertEquals(OcrBlockOrientation.Vertical, region.detectedOrientation())
        assertEquals(OcrBlockOrientation.Horizontal, region.orientation)
    }

    @Test
    fun `result with block text on every region is eligible for in-place coverage`() {
        val result =
            OcrResult(
                text = "first line\nsecond line",
                regions =
                    listOf(
                        OcrRegion(0, 0, 100, 20, 0.9f, text = "first line"),
                        OcrRegion(0, 24, 100, 44, 0.8f, text = "second line"),
                    ),
            )

        assertEquals(InPlaceCoverageEligibility.Eligible, result.inPlaceCoverageEligibility())
    }

    @Test
    fun `result without any region is rejected before rendering`() {
        val result = OcrResult(text = "text without geometry", regions = emptyList())

        val eligibility = result.inPlaceCoverageEligibility()

        assertIs<InPlaceCoverageEligibility.Rejected>(eligibility)
        assertEquals(InPlaceCoverageRejection.NoBlocks, eligibility.reason)
    }

    @Test
    fun `legacy region with blank text is rejected, never rendered onto the wrong box`() {
        val result =
            OcrResult(
                text = "some text",
                regions = listOf(OcrRegion(0, 0, 100, 20, 0.9f)),
            )

        val eligibility = result.inPlaceCoverageEligibility()

        assertIs<InPlaceCoverageEligibility.Rejected>(eligibility)
        assertEquals(InPlaceCoverageRejection.MissingBlockText, eligibility.reason)
    }

    @Test
    fun `degenerate geometry is rejected instead of producing a zero-height box`() {
        val result =
            OcrResult(
                text = "collapsed",
                regions = listOf(OcrRegion(30, 10, 30, 10, 0.7f, text = "collapsed")),
            )

        val eligibility = result.inPlaceCoverageEligibility()

        assertIs<InPlaceCoverageEligibility.Rejected>(eligibility)
        assertEquals(InPlaceCoverageRejection.DegenerateBlockGeometry, eligibility.reason)
    }

    @Test
    fun `blank text on one region among many is enough to reject the whole result`() {
        val result =
            OcrResult(
                text = "ok\n",
                regions =
                    listOf(
                        OcrRegion(0, 0, 100, 20, 0.9f, text = "ok"),
                        OcrRegion(0, 24, 100, 44, 0.9f, text = "   "),
                    ),
            )

        val eligibility = result.inPlaceCoverageEligibility()

        assertIs<InPlaceCoverageEligibility.Rejected>(eligibility)
        assertEquals(InPlaceCoverageRejection.MissingBlockText, eligibility.reason)
        assertTrue(result.regions.last().text.isBlank())
    }
}
