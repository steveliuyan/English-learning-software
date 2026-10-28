package com.example.englishlearning.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppMotion
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 分段控件（原型 `assets/app.css` `.seg` / `.seg-solid`）。
 *
 * 几何逐条对位原型：轨道内距 3dp、段项间隙 2dp、药片形、轨道底 `surface-2` + 1dp `line` 描边；
 * 选中档 `surface` 底 + `ink` 墨（`.seg-item[aria-pressed='true']`），`solid = true` 时改 `strong`
 * 底 + `on-strong` 墨（`.seg-solid`）。未选中墨 `ink-2`，段项 12sp/600。
 *
 * **一处登记偏离**：原型段项 `min-height: 34px`，低于 F5-03 的 48dp 触控下限。spec §2.3 明令
 * 无障碍下限优先，故段项实际高 48dp（`heightIn(min = 48.dp)`）；内距仍是 3/2，视觉厚度只多出
 * 内容盒的垂直余量。该偏离写在批 0 报告里，不在屏侧逐处重议。
 *
 * 语义用 `selectable`（TalkBack 播报「已选择」）而不是裸 `clickable`；组容器挂 `selectableGroup()`，
 * 让读屏把这一排当成一枚单选组。选中底色走 160ms 入场缓动（原型 `--dur-fast var(--ease-out)`）。
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShape.Pill)
            .background(colors.surface2)
            .border(1.dp, colors.outline, AppShape.Pill)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val fill = if (selected) {
                if (solid) colors.strongSurface else colors.surface
            } else {
                colors.surface2.copy(alpha = 0f)
            }
            val fillAnimated by animateColorAsState(
                targetValue = fill,
                animationSpec = tween(durationMillis = AppMotion.Fast, easing = AppMotion.EasingOut),
                label = "segmented_fill",
            )
            val ink = when {
                !selected -> colors.text2
                solid -> colors.onStrong
                else -> colors.text1
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(AppShape.Pill)
                    .background(fillAnimated)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = AppType.Caption,
                    color = ink,
                    fontWeight = FontWeight(600),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
