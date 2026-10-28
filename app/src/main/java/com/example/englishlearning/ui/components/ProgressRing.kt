package com.example.englishlearning.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 环形进度（2026-10-30 版式重做引入）：强档卡右上角那一枚「今天到哪儿了」的环。
 *
 * 与 `ThinProgress`（卡内细条）分工不同：条讲**每一类**还剩多少（新学 / 复习各一条），
 * 环只讲**总数**的占比，一眼可读。环里那枚数字由调用方给（`content` 槽），本组件
 * **不带任何字级**：字级一律由调用方按 `AppType` 档位取，否则就会在组件里长出
 * 第二套排版口径（AC-R-20 的同一口径延伸）。
 *
 * 颜色全部由调用方传入（`track` / `ink`），本组件不读 `appColors()`：
 * 环只出现在强档卡上，墨必须是 `onStrong` 及其稀释，组件自己取色就会翻成中性墨。
 *
 * 弧从 12 点方向起画（`startAngle = -90f`），进度夹到 0..1；`progress <= 0` 时只画轨道，
 * 不画一枚 0 长度的圆头端点（圆头端点在 0 时会留下一个小圆点，看着像「已经有一点进度」）。
 */
@Composable
fun ProgressRing(
    progress: Float,
    track: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 64.dp,
    strokeWidth: Dp = 7.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (clamped > 0f) {
                drawArc(
                    color = ink,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}
