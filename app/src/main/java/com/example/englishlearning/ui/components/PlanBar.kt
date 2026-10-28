package com.example.englishlearning.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppMotion
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.appColors

/**
 * 今日计划的双段进度条（原型 `assets/app.css` `.plan-bar` / `.plan-seg` / `.plan-seg-due`）。
 *
 * 几何：高 10dp、两段间隙 2dp、药片形、轨道 = 内容色 14% 透明（原型 `rgba(233,242,236,.14)` 就是
 * `on-strong` 的 14%）；新学段 `brand`，复习段内容色 62%（`rgba(233,242,236,.62)`）。
 *
 * 轨道与复习段取 `LocalContentColor` 而不是写死 `onStrong`：这条进度条住在 [StrongCard] 里，
 * 而强卡浅套按 2026-10-31 用户评审改成了浅薄荷底 + `text1` 墨；写死 `onStrong` 会在浅套里
 * 画出「浅薄荷底 + 浅薄荷段」的隐形进度条，分套后的内容色才是同一段代码在深浅两套里都成立的那枚。
 *
 * 两段的宽度走 300ms 入场缓动（原型 `transition: width var(--dur-slow) var(--ease-out)`）。
 * 语义挂 `progressBarRangeInfo`，读屏播报「已完成百分之多少」；两段合计即总量。
 */
@Composable
fun PlanProgressBar(
    newRatio: Float,
    dueRatio: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = appColors()
    val track = LocalContentColor.current
    val newTarget = newRatio.coerceIn(0f, 1f)
    val dueTarget = dueRatio.coerceIn(0f, 1f)
    val newWidth by animateFloatAsState(
        targetValue = newTarget,
        animationSpec = tween(durationMillis = AppMotion.Slow, easing = AppMotion.EasingOut),
        label = "plan_new_segment",
    )
    val dueWidth by animateFloatAsState(
        targetValue = dueTarget,
        animationSpec = tween(durationMillis = AppMotion.Slow, easing = AppMotion.EasingOut),
        label = "plan_due_segment",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(AppShape.Pill)
            .background(track.copy(alpha = 0.14f))
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = (newTarget + dueTarget).coerceIn(0f, 1f),
                    range = 0f..1f,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (newWidth > 0f) {
            Box(
                modifier = Modifier
                    .weight(newWidth.coerceAtLeast(0.0001f))
                    .fillMaxHeight()
                    .clip(AppShape.Pill)
                    .background(colors.brand),
            )
        }
        if (dueWidth > 0f) {
            Box(
                modifier = Modifier
                    .weight(dueWidth.coerceAtLeast(0.0001f))
                    .fillMaxHeight()
                    .clip(AppShape.Pill)
                    .background(track.copy(alpha = 0.62f)),
            )
        }
    }
}
