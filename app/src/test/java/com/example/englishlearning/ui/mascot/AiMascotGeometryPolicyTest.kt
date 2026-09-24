package com.example.englishlearning.ui.mascot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「AI 学」页 AI 表情的几何是**纯函数**，所以这里能在 JVM 上确定性地锁住它的不变量。
 *
 * 这里断言的不是「画出来好不好看」，而是三条会产生真实视觉缺陷的性质：
 * 眼睛与高光不能跑出身体、眨眼只该闭眼不该动身体、越界输入要被夹住而不是画出畸形图形。
 */
class AiMascotGeometryPolicyTest {
    private val breathSamples = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    private val blinkSamples = listOf(0f, 0.3f, 0.6f, 1f)

    private fun geometry(
        expression: AiMascotExpression = AiMascotExpression.AI_CONFIGURED,
        breath: Float = 0.5f,
        blink: Float = 0f,
    ) = AiMascotGeometryPolicy.geometryFor(expression, breath, blink)

    @Test
    fun `eyes stay inside the body for every combination of inputs`() {
        for (expression in AiMascotExpression.entries) {
            for (breath in breathSamples) {
                for (blink in blinkSamples) {
                    val g = geometry(expression, breath, blink)
                    val at = "expression=$expression breath=$breath blink=$blink -> $g"
                    assertTrue(g.eyeOffsetX - g.eyeRadius >= 0f, "眼睛越过身体中线：$at")
                    assertTrue(g.eyeOffsetX + g.eyeRadius <= g.bodyHalfWidth, "眼睛横向出界：$at")
                    assertTrue(g.eyeOffsetY + g.eyeRadius <= g.bodyHalfHeight, "眼睛纵向出界：$at")
                }
            }
        }
    }

    @Test
    fun `corner radius never exceeds the shorter body axis`() {
        for (expression in AiMascotExpression.entries) {
            for (breath in breathSamples) {
                val g = geometry(expression, breath, 0f)
                assertTrue(
                    g.cornerRadius <= minOf(g.bodyHalfWidth, g.bodyHalfHeight) + 1e-4f,
                    "圆角超过身体半轴会画出自交的形状：$g",
                )
            }
        }
    }

    @Test
    fun `blinking closes only the eyes and leaves the body untouched`() {
        val open = geometry(blink = 0f)
        val closed = geometry(blink = 1f)
        assertEquals(1f, open.eyeOpenFactor, "blink=0 时眼睛应完全睁开")
        assertEquals(0f, closed.eyeOpenFactor, "blink=1 时眼睛应完全闭合")
        assertEquals(open.copy(eyeOpenFactor = 0f), closed, "眨眼不该改动身体与眼睛位置")
    }

    @Test
    fun `breathing reshapes the body without moving the eyes`() {
        val shallow = geometry(breath = 0f)
        val deep = geometry(breath = 1f)
        assertTrue(
            deep.bodyHalfHeight != shallow.bodyHalfHeight,
            "呼吸必须真的改变身体高度，否则动画是静止的：$shallow -> $deep",
        )
        assertEquals(shallow.eyeOffsetX, deep.eyeOffsetX, "呼吸不该横向移动眼睛")
        assertEquals(shallow.eyeOffsetY, deep.eyeOffsetY, "呼吸不该纵向移动眼睛")
        assertEquals(shallow.eyeRadius, deep.eyeRadius, "呼吸不该改变眼睛大小")
    }

    @Test
    fun `the configured expression is brighter than the unconfigured one`() {
        val unconfigured = geometry(AiMascotExpression.AI_UNCONFIGURED)
        val configured = geometry(AiMascotExpression.AI_CONFIGURED)
        assertTrue(
            configured.highlightAlpha > unconfigured.highlightAlpha,
            "已配置必须比未接通更亮，否则两种真实状态在视觉上不可区分：" +
                "configured=${configured.highlightAlpha} unconfigured=${unconfigured.highlightAlpha}",
        )
        assertTrue(unconfigured.highlightAlpha >= 0f, "高光强度不能为负")
        assertTrue(configured.highlightAlpha <= 1f, "高光强度不能超过 1")
    }

    @Test
    fun `the configured expression never has smaller eyes`() {
        val unconfigured = geometry(AiMascotExpression.AI_UNCONFIGURED)
        val configured = geometry(AiMascotExpression.AI_CONFIGURED)
        assertTrue(
            configured.eyeRadius >= unconfigured.eyeRadius,
            "已配置的眼睛不应比未接通更小：configured=${configured.eyeRadius} unconfigured=${unconfigured.eyeRadius}",
        )
    }

    @Test
    fun `out of range breath and blink are clamped instead of producing broken geometry`() {
        assertEquals(geometry(breath = 0f), geometry(breath = -3f), "呼吸下越界应夹到 0")
        assertEquals(geometry(breath = 1f), geometry(breath = 9f), "呼吸上越界应夹到 1")
        assertEquals(geometry(blink = 1f), geometry(blink = 4f), "眨眼下越界应夹到 1")
        assertTrue(geometry(blink = -1f).eyeOpenFactor == 1f, "眨眼负值应夹到 0 即完全睁开")
    }

    @Test
    fun `every factor stays finite and inside its declared range`() {
        for (expression in AiMascotExpression.entries) {
            for (breath in breathSamples) {
                for (blink in blinkSamples) {
                    val g = geometry(expression, breath, blink)
                    val at = "expression=$expression breath=$breath blink=$blink -> $g"
                    for ((name, value) in listOf(
                        "bodyHalfWidth" to g.bodyHalfWidth,
                        "bodyHalfHeight" to g.bodyHalfHeight,
                        "cornerRadius" to g.cornerRadius,
                        "eyeOffsetX" to g.eyeOffsetX,
                        "eyeOffsetY" to g.eyeOffsetY,
                        "eyeRadius" to g.eyeRadius,
                        "eyeOpenFactor" to g.eyeOpenFactor,
                        "highlightAlpha" to g.highlightAlpha,
                    )) {
                        assertTrue(value.isFinite(), "$name 不是有限值：$at")
                    }
                    assertTrue(g.bodyHalfWidth > 0f && g.bodyHalfWidth <= 1f, "bodyHalfWidth 越界：$at")
                    assertTrue(g.bodyHalfHeight > 0f && g.bodyHalfHeight <= 1f, "bodyHalfHeight 越界：$at")
                    assertTrue(g.cornerRadius > 0f, "cornerRadius 必须为正：$at")
                    assertTrue(g.eyeRadius > 0f, "eyeRadius 必须为正：$at")
                    assertTrue(g.eyeOpenFactor in 0f..1f, "eyeOpenFactor 越界：$at")
                    assertTrue(g.highlightAlpha in 0f..1f, "highlightAlpha 越界：$at")
                }
            }
        }
    }

    @Test
    fun `is deterministic for the same input`() {
        assertEquals(
            geometry(breath = 0.37f, blink = 0.42f),
            geometry(breath = 0.37f, blink = 0.42f),
        )
    }
}
