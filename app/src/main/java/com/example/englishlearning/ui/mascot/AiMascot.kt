package com.example.englishlearning.ui.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlin.random.Random

/** 表情的测试标识。真机断言与页面接线都只认这一个字符串。 */
const val AI_MASCOT_TEST_TAG: String = "ai_mascot"

private const val BREATH_PERIOD_MILLIS = 2600
private const val MIN_BLINK_GAP_MILLIS = 1_800L
private const val MAX_BLINK_GAP_MILLIS = 4_800L
private const val BLINK_CLOSE_MILLIS = 80
private const val BLINK_OPEN_MILLIS = 120

/**
 * 白色玻璃体上的眼睛颜色。
 *
 * 取值与主题里的 `MintPrimaryDark`（`0xFF188F76`）相同，让表情与整页同族；纯黑会显得与主题分家。
 * 但**刻意写成独立常量而不是引用主题 token**：表情是一份自撰插画，必须保证在任何主题下
 * 都与白色体有明确对比。若日后有人把主题色改浅，引用 token 会让眼睛静默消失在白底上，
 * 而独立常量会稳稳保持深色。
 */
private val EyeColor: Color = Color(0xFF188F76)

/** 闭眼判定阈值：低于它就把眼睛画成一条线，避免退化成一条看不清的细缝。 */
private const val CLOSED_EYE_THRESHOLD = 0.02f

/**
 * 「AI 学」页头部卡顶部的 AI 表情：白色玻璃质感的圆润体 + 两只眼睛。
 *
 * **它只表达一个真实事实**：`aiConfigured`。基础动效是 idle（呼吸 + 随机眨眼），
 * 此外仅把「已配置 / 尚未接通」映射到眼睛大小与高光强度。**不表现任何「在忙」的状态**，
 * 因为 AI 能力尚未接通（详见 `docs/superpowers/specs/2026-09-23-ai-learning-tab-design.md` §4.5）。
 *
 * 玻璃质感**不依赖 `Modifier.blur`**：`minSdk = 26`，而模糊在 API 31 以下会被忽略。
 * 这里用分层半透明白、垂直渐变与边缘高光叠出来，全 API 一致。
 *
 * @param aiConfigured 本机真实的 AI 配置状态。**这是唯一入参**——调用方无法指定一个
 *   不存在的状态，从签名上就堵住了「凭空演一个正在思考的 AI」。
 */
@Composable
fun AiMascot(
    aiConfigured: Boolean,
    modifier: Modifier = Modifier,
) {
    val expression = AiMascotExpression.of(aiConfigured)
    val description = if (aiConfigured) {
        "AI 表情：AI 已配置"
    } else {
        "AI 表情：AI 尚未接通"
    }

    val breath = rememberInfiniteTransition(label = "ai-mascot-breath")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = BREATH_PERIOD_MILLIS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breath",
        ).value

    val blink = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(MIN_BLINK_GAP_MILLIS, MAX_BLINK_GAP_MILLIS))
            blink.animateTo(1f, tween(durationMillis = BLINK_CLOSE_MILLIS, easing = LinearEasing))
            blink.animateTo(0f, tween(durationMillis = BLINK_OPEN_MILLIS, easing = LinearEasing))
        }
    }

    val geometry = AiMascotGeometryPolicy.geometryFor(expression, breath, blink.value)

    Canvas(
        modifier = modifier
            .testTag(AI_MASCOT_TEST_TAG)
            .semantics { contentDescription = description },
    ) {
        drawMascot(geometry)
    }
}

private fun DrawScope.drawMascot(geometry: AiMascotGeometry) {
    val half = size.minDimension / 2f
    val centerX = size.width / 2f
    val centerY = size.height / 2f

    val bodyHalfWidth = geometry.bodyHalfWidth * half
    val bodyHalfHeight = geometry.bodyHalfHeight * half
    val topLeft = Offset(centerX - bodyHalfWidth, centerY - bodyHalfHeight)
    val bodySize = Size(bodyHalfWidth * 2f, bodyHalfHeight * 2f)
    val cornerRadius = CornerRadius(geometry.cornerRadius * half)

    // 1) 贴住渐变的那圈亮边。
    drawRoundRect(
        color = Color.White.copy(alpha = 0.10f + 0.28f * geometry.highlightAlpha),
        topLeft = topLeft,
        size = bodySize,
        cornerRadius = cornerRadius,
        style = Stroke(width = half * 0.045f),
    )

    // 2) 主体：自上而下由实到虚的白色，做出玻璃的体积感。
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.94f), Color.White.copy(alpha = 0.64f)),
            startY = centerY - bodyHalfHeight,
            endY = centerY + bodyHalfHeight,
        ),
        topLeft = topLeft,
        size = bodySize,
        cornerRadius = cornerRadius,
    )

    // 3) 内侧高光：这是「已配置」与「尚未接通」最直观的差异。
    drawRoundRect(
        color = Color.White.copy(alpha = 0.55f * geometry.highlightAlpha),
        topLeft = Offset(centerX - bodyHalfWidth * 0.72f, centerY - bodyHalfHeight * 0.80f),
        size = Size(bodyHalfWidth * 1.44f, bodyHalfHeight * 0.46f),
        cornerRadius = CornerRadius(minOf(bodyHalfWidth * 0.72f, bodyHalfHeight * 0.23f)),
    )

    // 4) 眼睛。
    val eyeHalfWidth = geometry.eyeRadius * half
    val eyeHalfHeight = eyeHalfWidth * geometry.eyeOpenFactor
    val eyeCenterY = centerY + geometry.eyeOffsetY * half
    for (side in listOf(-1f, 1f)) {
        val eyeCenterX = centerX + side * geometry.eyeOffsetX * half
        if (geometry.eyeOpenFactor <= CLOSED_EYE_THRESHOLD) {
            drawLine(
                color = EyeColor,
                start = Offset(eyeCenterX - eyeHalfWidth, eyeCenterY),
                end = Offset(eyeCenterX + eyeHalfWidth, eyeCenterY),
                strokeWidth = eyeHalfWidth * 0.5f,
                cap = StrokeCap.Round,
            )
        } else {
            drawRoundRect(
                color = EyeColor,
                topLeft = Offset(eyeCenterX - eyeHalfWidth, eyeCenterY - eyeHalfHeight),
                size = Size(eyeHalfWidth * 2f, eyeHalfHeight * 2f),
                cornerRadius = CornerRadius(eyeHalfWidth),
            )
        }
    }
}
