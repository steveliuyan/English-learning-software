package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 表单字段（原型 `assets/app.css` `.field` / `.field-label` / `.input` / `.field-hint` / `.field-error`）。
 *
 * 几何：标签 12sp/600 `ink-2`，字段与标签间隙 6dp（原型 `.field { gap: var(--s2) }`）；输入盒 1dp
 * `line` 描边、圆角 14dp、内距 12/14；焦点态描边转 `brand-deep`，外圈 3dp `brand` 30% 薄荷环
 * （`box-shadow: 0 0 0 3px color-mix(… 30%, transparent)`）；错误态描边与下方文案取 `error`，
 * 文案 12sp/600。
 *
 * 用自绘 [BasicTextField] 而不是 M3 `OutlinedTextField`：后者自带浮动标签、下划线档与 56dp 高，
 * 与原型那套「标签在上、实心盒、圆角 14」的配方是三套不同的几何，改参数也压不回原型。
 *
 * 焦点态用 `collectIsFocusedAsState` 跟踪（比 `FocusRequester` 省一次手动管理——这里只需要知道
 * 「有没有焦点」来画环，不需要主动请求焦点）。环画在盒外 3dp（`drawBehind` 负偏移），所以焦点切换
 * 不改变布局尺寸：输入框不会因为获得焦点而整体跳一格。
 */
@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    hint: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = appColors()
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val edge = when {
        error != null -> colors.error
        focused -> colors.brandDeep
        else -> colors.outline
    }
    val ringColor = colors.brand.copy(alpha = 0.30f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(text = label, style = AppType.Label, color = colors.text2)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (focused) {
                        val ring = 3.dp.toPx()
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(-ring, -ring),
                            size = Size(size.width + ring * 2, size.height + ring * 2),
                            cornerRadius = CornerRadius(14.dp.toPx() + ring),
                            style = Stroke(width = ring),
                        )
                    }
                }
                .clip(AppShape.Button)
                .background(colors.surface)
                .border(1.dp, edge, AppShape.Button),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = AppType.Body.copy(color = colors.text1),
                cursorBrush = SolidColor(colors.brandDeep),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(text = placeholder, style = AppType.Body, color = colors.text3)
                        }
                        innerTextField()
                    }
                },
            )
        }
        val supporting = error ?: hint
        if (supporting != null) {
            Text(
                text = supporting,
                style = AppType.Caption,
                color = if (error != null) colors.error else colors.text3,
                fontWeight = if (error != null) FontWeight(600) else null,
            )
        }
    }
}
