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
import androidx.compose.ui.Alignment
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
 * 两段进度条的**纯布局**：段宽看目标份额，段内填充看各自的完成比。
 *
 * 口径逐字对齐原型（`assets/app.js:303-307` 与 `:324-327`）：
 *  - `newShare = newTarget / (newTarget + dueTarget)`——段宽是 `flex:0 0 share%`，与进度无关；
 *  - `newFill = doneNew / newTarget`——段内 `<i style="width:newPct%">` 才是完成比；
 *  - 目标为 0 的段塌成 0 宽（0% 不可见），两段都 0 时原型给 50/50 缺省（`total ? … : 50`）。
 *
 * 纯函数放在组件文件里由 JVM 契约 `PlanBarContractTest` 直接复算：份额与填充比是算术题，
 * 不该靠读源码文本猜。
 */
data class PlanBarLayout(
    val newShare: Float,
    val dueShare: Float,
    val newFill: Float,
    val dueFill: Float,
)

/** 见 [PlanBarLayout]。负数按 0 处理（快照回滚/脏数据不应画出反方向的条），超额完成填充封顶 1。 */
fun planBarLayout(newDone: Int, newTarget: Int, dueDone: Int, dueTarget: Int): PlanBarLayout {
    val targetNew = newTarget.coerceAtLeast(0)
    val targetDue = dueTarget.coerceAtLeast(0)
    val doneNew = newDone.coerceAtLeast(0)
    val doneDue = dueDone.coerceAtLeast(0)
    val total = targetNew + targetDue
    val newShare = if (total > 0) targetNew.toFloat() / total else 0.5f
    return PlanBarLayout(
        newShare = newShare,
        dueShare = 1f - newShare,
        newFill = if (targetNew > 0) (doneNew.toFloat() / targetNew).coerceIn(0f, 1f) else 0f,
        dueFill = if (targetDue > 0) (doneDue.toFloat() / targetDue).coerceIn(0f, 1f) else 0f,
    )
}

/**
 * 今日计划的双段进度条（原型 `assets/app.css` `.plan-bar` / `.plan-seg` / `.plan-legend`）。
 *
 * 几何：高 10dp、两段间隙 2dp、药片形、轨道 = 内容色 14% 透明（原型 `rgba(233,242,236,.14)`）；
 * 新学段 `brand`，复习段内容色 62%（`rgba(233,242,236,.62)`）。
 *
 * 两段宽度按**目标份额**切（[planBarLayout]），各自内部的填充条按完成比伸缩并走 300ms 入场缓动
 * （原型 `transition: width var(--dur-slow) var(--ease-out)`）。批 0 那版把段宽本身按完成比加权，
 * 「还剩多少」在条上读不出来；这里改成原型的 `flex:0 0 share%` + 内层 `width` 两级结构。
 *
 * 轨道与复习段取 `LocalContentColor` 而不是写死 `onStrong`：这根条住在 [StrongCard] 里，
 * 而强卡浅套按 2026-10-31 用户评审改成了浅薄荷底 + `text1` 墨；写死 `onStrong` 会在浅套里
 * 画出「浅薄荷底 + 浅薄荷段」的隐形进度条，分套后的内容色才是同一段代码在深浅两套里都成立的那枚。
 *
 * 语义挂 `progressBarRangeInfo`（总量 = 两段目标之和，当前 = 两段完成之和），读屏播报「已完成百分之多少」；
 * 图例里逐段的 `x / y` 由调用方按 [PlanBarLayout] 摆，本组件只管条本身。
 */
@Composable
fun PlanProgressBar(
    newDone: Int,
    newTarget: Int,
    dueDone: Int,
    dueTarget: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = appColors()
    val track = LocalContentColor.current
    val layout = planBarLayout(newDone, newTarget, dueDone, dueTarget)
    val newWidth by animateFloatAsState(
        targetValue = layout.newFill,
        animationSpec = tween(durationMillis = AppMotion.Slow, easing = AppMotion.EasingOut),
        label = "plan_new_fill",
    )
    val dueWidth by animateFloatAsState(
        targetValue = layout.dueFill,
        animationSpec = tween(durationMillis = AppMotion.Slow, easing = AppMotion.EasingOut),
        label = "plan_due_fill",
    )
    val totalTarget = (newTarget.coerceAtLeast(0) + dueTarget.coerceAtLeast(0))
    val totalDone = (newDone.coerceAtLeast(0) + dueDone.coerceAtLeast(0)).coerceAtMost(totalTarget)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(AppShape.Pill)
            .background(track.copy(alpha = 0.14f))
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = totalDone.toFloat(),
                    range = 0f..totalTarget.coerceAtLeast(1).toFloat(),
                )
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (layout.newShare > 0f) {
            Box(
                modifier = Modifier
                    .weight(layout.newShare)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(newWidth)
                        .fillMaxHeight()
                        .clip(AppShape.Pill)
                        .background(colors.brand),
                )
            }
        }
        if (layout.dueShare > 0f) {
            Box(
                modifier = Modifier
                    .weight(layout.dueShare)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(dueWidth)
                        .fillMaxHeight()
                        .clip(AppShape.Pill)
                        .background(track.copy(alpha = 0.62f)),
                )
            }
        }
    }
}
