package com.example.englishlearning.ui.mascot

/**
 * 表情的几何参数，**全部归一化到画布半边长**（画布边长的一半记为 `1`）。
 *
 * 归一化的两个好处：绘制端只需乘以实际半径，不必关心控件尺寸；不变量可以在 JVM 上直接断言。
 *
 * @param eyeOpenFactor `0` 表示完全闭合，`1` 表示完全睁开。**这是语义值，不直接用于绘制**——
 *   闭合附近它会让眼睛降到不足 1px 而几乎看不见，见 [eyeHalfHeightFactor]。
 * @param eyeHalfHeightFactor 眼睛绘制用的半高比例（相对 [eyeRadius] 这个半宽）。下限被抬到
 *   [AiMascotGeometryPolicy.EYE_CLOSED_THICKNESS_FACTOR]，使闭眼是**一条有厚度的线**而不是零高度，
 *   从而消除闭合瞬间的厚度跳变。
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
    val eyeHalfHeightFactor: Float,
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

    /**
     * 眼睛绘制半高的下限，相对眼睛半宽。
     *
     * **这是修掉一个真实渲染缺陷后留下的值，不要当成随便取的数。**
     * 早期实现按 `eyeOpenFactor` 直接画高度，并在 `factor <= 0.02` 时改用一条固定粗细的线。
     * 真机录屏逐帧量化后暴露了这条路径的厚度跳变：闭合过程是
     * 细(292 暗像素) → 几乎看不见(51，最暗像素从 ~108 升到 180) → **突然变粗(571)** → 又几乎看不见(125) → 再变粗，
     * 一次眨眼闪三次。原因就是 `factor` 略大于阈值时画的是不足 1px 的细缝，
     * 而 `factor` 归零时画的是固定粗细的线——**闭眼那一刻眼睛反而弹粗**。
     *
     * 取 `0.25` 是因为它正好等于旧实现里那条闭眼线的半厚（线粗 = 半宽 × 0.5）：厚度函数在
     * 衔接处取到同一个值，跳变消失，闭眼仍是一条清晰的线。
     */
    const val EYE_CLOSED_THICKNESS_FACTOR: Float = 0.25f

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

        // 眨眼只碰 eyeOpenFactor 这一个语义值；身体与眼睛位置完全不受影响。
        val eyeOpenFactor = 1f - blinkPhase

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
            eyeOpenFactor = eyeOpenFactor,
            // 下限保证闭合时是一条线而不是零高度；见 EYE_CLOSED_THICKNESS_FACTOR 的注释。
            eyeHalfHeightFactor = maxOf(eyeOpenFactor, EYE_CLOSED_THICKNESS_FACTOR),
            highlightAlpha = when (expression) {
                AiMascotExpression.AI_CONFIGURED -> HIGHLIGHT_CONFIGURED
                AiMascotExpression.AI_UNCONFIGURED -> HIGHLIGHT_UNCONFIGURED
            },
        )
    }
}
