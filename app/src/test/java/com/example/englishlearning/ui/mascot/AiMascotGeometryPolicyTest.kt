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
        // 眨眼只允许改眼睛自身的两个厚度字段。这里逐项枚举其余字段，
        // 而不是只比一个整体 copy——「眨眼顺手把身子也动了」这类缺陷才有机会被抓到。
        assertEquals(open.bodyHalfWidth, closed.bodyHalfWidth, "眨眼不该改变身体宽度")
        assertEquals(open.bodyHalfHeight, closed.bodyHalfHeight, "眨眼不该改变身体高度")
        assertEquals(open.cornerRadius, closed.cornerRadius, "眨眼不该改变圆角")
        assertEquals(open.eyeOffsetX, closed.eyeOffsetX, "眨眼不该横向移动眼睛")
        assertEquals(open.eyeOffsetY, closed.eyeOffsetY, "眨眼不该纵向移动眼睛")
        assertEquals(open.eyeRadius, closed.eyeRadius, "眨眼不该改变眼睛大小")
        assertEquals(open.highlightAlpha, closed.highlightAlpha, "眨眼不该改变高光")
    }

    @Test
    fun `the eye never thins below the closed thickness`() {
        // 这条锁的是一个真机上抓到的渲染缺陷：早期实现让绘制高度直接等于 eyeOpenFactor，
        // 于是闭合途中眼睛会降到不足 1px 而几乎看不见，等到归零又切成一条固定粗细的线——
        // 一次眨眼闪三下。现在厚度下限由几何侧保证，绘制端不再有第二条分支。
        val floor = AiMascotGeometryPolicy.EYE_CLOSED_THICKNESS_FACTOR
        for (blink in listOf(0f, 0.25f, 0.5f, 0.75f, 0.9f, 0.999f, 1f)) {
            val g = geometry(blink = blink)
            assertTrue(
                g.eyeHalfHeightFactor >= floor,
                "眼睛会细到看不见：blink=$blink 厚度=${g.eyeHalfHeightFactor} 下限=$floor",
            )
        }
    }

    @Test
    fun `the drawn thickness is continuous where the closed eye takes over`() {
        // 衔接点：eyeOpenFactor 恰好等于下限。两侧厚度必须几乎相等，否则闭合瞬间会「弹」一下。
        val join = 1f - AiMascotGeometryPolicy.EYE_CLOSED_THICKNESS_FACTOR
        val justBefore = geometry(blink = join + 1e-4f).eyeHalfHeightFactor
        val justAfter = geometry(blink = join - 1e-4f).eyeHalfHeightFactor
        assertTrue(
            kotlin.math.abs(justBefore - justAfter) < 1e-3f,
            "衔接处厚度跳变会让眨眼闪一下：$justBefore vs $justAfter",
        )
    }

    @Test
    fun `the drawn eye only thins while the eye is closing`() {
        val samples = listOf(0f, 0.2f, 0.4f, 0.6f, 0.75f, 1f)
        val thickness = samples.map { geometry(blink = it).eyeHalfHeightFactor }
        for (i in 1 until thickness.size) {
            assertTrue(
                thickness[i] <= thickness[i - 1] + 1e-6f,
                "闭合过程中眼睛反而变粗（眨眼会闪）：blink=${samples[i - 1]}->${samples[i]} " +
                    "厚度=${thickness[i - 1]}->${thickness[i]}",
            )
        }
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
                        "eyeHalfHeightFactor" to g.eyeHalfHeightFactor,
                        "highlightAlpha" to g.highlightAlpha,
                    )) {
                        assertTrue(value.isFinite(), "$name 不是有限值：$at")
                    }
                    assertTrue(g.bodyHalfWidth > 0f && g.bodyHalfWidth <= 1f, "bodyHalfWidth 越界：$at")
                    assertTrue(g.bodyHalfHeight > 0f && g.bodyHalfHeight <= 1f, "bodyHalfHeight 越界：$at")
                    assertTrue(g.cornerRadius > 0f, "cornerRadius 必须为正：$at")
                    assertTrue(g.eyeRadius > 0f, "eyeRadius 必须为正：$at")
                    assertTrue(g.eyeOpenFactor in 0f..1f, "eyeOpenFactor 越界：$at")
                    assertTrue(
                        g.eyeHalfHeightFactor in AiMascotGeometryPolicy.EYE_CLOSED_THICKNESS_FACTOR..1f,
                        "eyeHalfHeightFactor 越界：$at",
                    )
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
