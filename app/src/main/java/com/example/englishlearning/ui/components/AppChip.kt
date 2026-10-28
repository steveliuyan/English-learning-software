package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 筛选芯片（原型 `assets/app.css` `.chip` / `.chip[aria-pressed='true']` / `.chip-quiet`）。
 *
 * 本体 30dp 高、左右 12dp、药片形、12sp/600，三档配色：
 *  - 默认：`surface` 底 + 1dp `line` 描边 + `ink-2` 墨；
 *  - 选中：`brand-tint` 底 + `brand-deep` 34% 描边 + `brand-deep` 墨（原型 `color-mix(… 34%, transparent)`）；
 *  - `quiet = true`：`surface-2` 底、不描边、`ink-3` 墨（`.chip-quiet`，筛选结果的静态标签）。
 *
 * **一处登记偏离**：原型芯片是 `height: 30px` 的可点元素，低于 F5-03 的 48dp 下限。这里保留 30dp
 * 本体（视觉厚度是原型的一部分），把可点面交给外层 48dp 触控盒（`heightIn(min = 48.dp)` + 居中）。
 * 芯片是开合筛选，语义用 `toggleable`（`aria-pressed` 等价物）而不是裸 `clickable`。
 */
@Composable
fun AppChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = appColors()
    val fill = when {
        quiet -> colors.surface2
        selected -> colors.brandTint
        else -> colors.surface
    }
    val ink = when {
        quiet -> colors.text3
        selected -> colors.brandDeep
        else -> colors.text2
    }
    val outline = when {
        quiet -> Color.Transparent
        selected -> colors.brandDeep.copy(alpha = 0.34f)
        else -> colors.outline
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(AppShape.Pill)
                .background(fill)
                .border(1.dp, outline, AppShape.Pill)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leading?.invoke()
            Text(
                text = label,
                style = AppType.Caption,
                color = ink,
                fontWeight = FontWeight(600),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
