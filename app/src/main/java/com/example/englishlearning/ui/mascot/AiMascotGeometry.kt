package com.example.englishlearning.ui.mascot

/**
 * 表情的几何参数，**全部归一化到画布半边长**（画布边长的一半记为 `1`）。
 *
 * 归一化的两个好处：绘制端只需乘以实际半径，不必关心控件尺寸；不变量可以在 JVM 上直接断言。
 *
 * @param eyeOpenFactor `0` 表示完全闭合成一条线，`1` 表示完全睁开。
 * @param highlightAlpha 玻璃高光的强度，是「AI 是否已配置」在视觉上的唯一差异来源之一。
 */
data class AiMascotGeometry(
    val bodyHalfWidth: Float,
    val bodyHalfHeight: Float,
    val cornerRadius: Float,
    val eyeOffsetX: Float,
    val eyeOffsetY: Float,
    val eyeRadius: Float,
    val eyeOpenFactor: Float,
    val highlightAlpha: Float,
)

/**
 * 把（表达式、呼吸相位、眨眼相位）映射成几何参数。
 *
 * **纯函数**：同输入必得同输出，不持有状态、不读时间、不做副作用。这样「眼睛跑出身体」
 * 「眨眼顺手把身体也改了」这类真实视觉缺陷可以在 JVM 上确定性锁住，不必靠肉眼看截图。
 */
object AiMascotGeometryPolicy {
    private const val BASE_HALF_WIDTH = 0.62f
    private const val BASE_HALF_HEIGHT = 0.60f

    /** 呼吸对高度与宽度的作用方向相反，做出挤压拉伸的体积感。 */
    private const val BREATH_HEIGHT_SWING = 0.035f
    private const val BREATH_WIDTH_SWING = 0.022f

    /** 圆角取较短半轴的比例，保证不会超过半轴而画出自交形状。 */
    private const val CORNER_RADIUS_RATIO = 0.92f

    private const val EYE_OFFSET_X = 0.20f
    private const val EYE_OFFSET_Y = -0.06f
    private const val EYE_RADIUS_CONFIGURED = 0.088f
    private const val EYE_RADIUS_UNCONFIGURED = 0.078f

    private const val HIGHLIGHT_CONFIGURED = 0.85f
    private const val HIGHLIGHT_UNCONFIGURED = 0.42f

    fun geometryFor(
        expression: AiMascotExpression,
        breath: Float,
        blink: Float,
    ): AiMascotGeometry {
        // 越界输入夹住而不是照单全收：调用方（动画或测试）给了坏值也只该画得难看一点，不该画出畸形图形。
        val breathPhase = (breath.coerceIn(0f, 1f) - 0.5f) * 2f
        val blinkPhase = blink.coerceIn(0f, 1f)

        val bodyHalfHeight = BASE_HALF_HEIGHT + BREATH_HEIGHT_SWING * breathPhase
        val bodyHalfWidth = BASE_HALF_WIDTH - BREATH_WIDTH_SWING * breathPhase

        return AiMascotGeometry(
            bodyHalfWidth = bodyHalfWidth,
            bodyHalfHeight = bodyHalfHeight,
            cornerRadius = minOf(bodyHalfWidth, bodyHalfHeight) * CORNER_RADIUS_RATIO,
            eyeOffsetX = EYE_OFFSET_X,
            eyeOffsetY = EYE_OFFSET_Y,
            eyeRadius = when (expression) {
                AiMascotExpression.AI_CONFIGURED -> EYE_RADIUS_CONFIGURED
                AiMascotExpression.AI_UNCONFIGURED -> EYE_RADIUS_UNCONFIGURED
            },
            // 眨眼只碰这一个字段：身体与眼睛位置完全不受影响。
            eyeOpenFactor = 1f - blinkPhase,
            highlightAlpha = when (expression) {
                AiMascotExpression.AI_CONFIGURED -> HIGHLIGHT_CONFIGURED
                AiMascotExpression.AI_UNCONFIGURED -> HIGHLIGHT_UNCONFIGURED
            },
        )
    }
}
