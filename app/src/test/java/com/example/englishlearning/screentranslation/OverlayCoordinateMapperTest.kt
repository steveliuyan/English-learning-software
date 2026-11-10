package com.example.englishlearning.screentranslation

import com.example.englishlearning.phototranslation.OverlayBlock
import com.example.englishlearning.phototranslation.OverlayRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** F3-05：截屏坐标 → 覆盖层坐标必须等比且居中，否则译文叠不到原文上。 */
class OverlayCoordinateMapperTest {
    private val eps = 0.001f

    private fun assertRect(
        expected: OverlayRect,
        actual: OverlayRect,
    ) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.right, actual.right, eps)
        assertEquals(expected.bottom, actual.bottom, eps)
    }

    @Test
    fun equalSizesMapIdentically() {
        val mapper = OverlayCoordinateMapper(captureWidth = 1080, captureHeight = 2400, viewWidth = 1080f, viewHeight = 2400f)

        assertEquals(1f, mapper.scale, eps)
        assertRect(OverlayRect(10f, 20f, 110f, 60f), mapper.map(OverlayRect(10f, 20f, 110f, 60f)))
    }

    @Test
    fun smallerViewScalesDownWithoutOffset() {
        val mapper = OverlayCoordinateMapper(captureWidth = 1080, captureHeight = 2400, viewWidth = 540f, viewHeight = 1200f)

        assertEquals(0.5f, mapper.scale, eps)
        assertRect(OverlayRect(5f, 10f, 55f, 30f), mapper.map(OverlayRect(10f, 20f, 110f, 60f)))
    }

    @Test
    fun letterboxingCentersTheCaptureInsideTheView() {
        val mapper = OverlayCoordinateMapper(captureWidth = 1080, captureHeight = 2400, viewWidth = 1080f, viewHeight = 1080f)

        assertEquals(0.45f, mapper.scale, eps)
        val mapped = mapper.map(OverlayRect(0f, 0f, 1080f, 2400f))
        assertEquals(297f, mapped.left, eps)
        assertEquals(783f, mapped.right, eps)
        assertEquals(0f, mapped.top, eps)
        assertEquals(1080f, mapped.bottom, eps)
    }

    @Test
    fun mappingBlocksKeepsIdTranslationAndEligibility() {
        val mapper = OverlayCoordinateMapper(captureWidth = 100, captureHeight = 100, viewWidth = 200f, viewHeight = 200f)
        val block = OverlayBlock(id = 3, left = 10f, top = 10f, right = 50f, bottom = 30f, translation = "译文", eligible = false)

        val mapped = mapper.mapBlocks(listOf(block)).single()

        assertEquals(3, mapped.id)
        assertEquals("译文", mapped.translation)
        assertEquals(false, mapped.eligible)
        assertEquals(20f, mapped.left, eps)
        assertEquals(60f, mapped.bottom, eps)
    }

    @Test
    fun nonPositiveSizesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            OverlayCoordinateMapper(captureWidth = 0, captureHeight = 100, viewWidth = 10f, viewHeight = 10f)
        }
        assertFailsWith<IllegalArgumentException> {
            OverlayCoordinateMapper(captureWidth = 100, captureHeight = 100, viewWidth = 10f, viewHeight = 0f)
        }
    }

    @Test
    fun scaleNeverExceedsOneWhenTheViewIsLarger() {
        val mapper = OverlayCoordinateMapper(captureWidth = 100, captureHeight = 100, viewWidth = 400f, viewHeight = 400f)

        assertTrue(mapper.scale >= 1f)
        assertRect(OverlayRect(40f, 40f, 80f, 120f), mapper.map(OverlayRect(10f, 10f, 20f, 30f)))
    }
}
