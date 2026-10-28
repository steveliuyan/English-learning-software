package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 选择行（原型 `assets/app.css` `.pick` / `.pick-mark` / `.pick-radio`）。
 *
 * 行：上下 12dp、无横向内距（`padding: var(--s3) 0`，横向归宿主卡片）、标记与文字间隙 12dp、
 * 最小高 48dp（F5-03 下限；原型自然高 22 + 24 = 46，抬 2dp）。顶部 1dp `line-soft` 分隔线由宿主
 * 用 `hairlineTop` 决定——原型 `.pick` 自带 `border-top`，但组首行要 `:first-child { border-top: 0 }`，
 * 组件不替调用方决定「这是不是组首行」。
 *
 * 标记：22dp，复选圆角 7dp、单选 50%，1.5dp 描边；未选 `surface` 底 + `line` 边，选中 `brand-deep`
 * 底 + `brand-deep` 边 + 白色勾（原型 `color: #fff`，勾与底一起出现）。
 *
 * 语义用 `selectable`：`radio = true` → `Role.RadioButton`，否则 `Role.Checkbox`（单选组仍由宿主
 * 挂 `selectableGroup()`，与 `SegmentedControl` 同口径）。
 */
@Composable
fun PickRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    radio: Boolean = false,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val colors = appColors()
    val markShape: Shape = if (radio) CircleShape else RoundedCornerShape(7.dp)
    val markEdge = if (checked) colors.brandDeep else colors.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = checked,
                enabled = enabled,
                role = if (radio) Role.RadioButton else Role.Checkbox,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(markShape)
                .background(if (checked) colors.brandDeep else colors.surface)
                .border(1.5.dp, markEdge, markShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                AppGlyph(
                    icon = AppIcon.Check,
                    tint = Color.White,
                    size = 14.dp,
                    layer = AppGlyphLayer.StrokeOnly,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = AppType.Body,
                color = if (enabled) colors.text1 else colors.text3,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, style = AppType.Caption, color = colors.text3)
            }
        }
    }
}
