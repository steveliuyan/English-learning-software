package com.example.englishlearning.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppMotion
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.appColors

/**
 * 开关（原型 `assets/app.css` `.switch`）。
 *
 * 几何：轨道 46×28、药片形、1dp 描边、内缩 3dp、拇指 20dp 圆；行程 18dp = 46 − 2×3 − 20 − 2×1
 * （原型 `translateX(18px)`）。开态轨道与描边都是 `brand-deep`，关态轨道 `surface-2` + `line` 描边，
 * 拇指永恒 `surface`（`.switch::after`）。
 *
 * 语义角色 `Role.Switch` 由 `toggleable` 给出，TalkBack 播报「开 / 关」；位移与底色各走 220ms
 * 入场缓动（原型 `--dur var(--ease-out)`）。
 *
 * 拇指阴影按原型 `0 1px 3px rgba(21,33,28,.3)` 近似（`shadow(2.dp, CircleShape)`）——Compose 的
 * `shadow` 只有一个模糊半径，不接 spread 与颜色；这枚偏差在报告里点名，不另造 `drawBehind` 光晕。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = appColors()
    val track by animateColorAsState(
        targetValue = if (checked) colors.brandDeep else colors.surface2,
        animationSpec = tween(durationMillis = AppMotion.Normal, easing = AppMotion.EasingOut),
        label = "app_switch_track",
    )
    val border by animateColorAsState(
        targetValue = if (checked) colors.brandDeep else colors.outline,
        animationSpec = tween(durationMillis = AppMotion.Normal, easing = AppMotion.EasingOut),
        label = "app_switch_border",
    )
    val thumbShift by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        animationSpec = tween(durationMillis = AppMotion.Normal, easing = AppMotion.EasingOut),
        label = "app_switch_thumb",
    )
    Box(
        modifier = modifier
            .width(46.dp)
            .height(28.dp)
            .clip(AppShape.Pill)
            .background(track)
            .border(1.dp, border, AppShape.Pill)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(3.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbShift)
                .size(20.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(colors.surface),
        )
    }
}
