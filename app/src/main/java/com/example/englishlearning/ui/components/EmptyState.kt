package com.example.englishlearning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 空状态（原型 `assets/app.css` `.empty` / `.empty-title` / `.empty-body`）。
 *
 * 贴纸 84dp、圆角 26dp、内描边 1dp 5% 墨——它复用 [StickerBadge] 的「贴纸 + 内描边」出口，
 * 只把边长与圆角覆盖成空状态档，不另起一枚位图渲染组件。
 *
 * 排布：上下 32 / 左右 16、纵向间隙 12dp、居中对齐；标题 15sp/600 `ink-1`，说明 12sp `ink-3`
 * 居中一行段。`action` 是尾部动作槽（如「去添加」）——原型空状态没有画按钮，本参数只给已经
 * 有动作语义的调用点用；没有真实动作就不传，不要为了「看起来完整」补一枚假按钮。
 */
@Composable
fun EmptyState(
    sticker: Sticker,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StickerBadge(
            sticker = sticker,
            size = StickerSize.Large,
            dimension = 84.dp,
            corner = 26.dp,
        )
        Text(
            text = title,
            style = AppType.Body,
            fontWeight = FontWeight(600),
            color = colors.text1,
            textAlign = TextAlign.Center,
        )
        if (!body.isNullOrBlank()) {
            Text(
                text = body,
                style = AppType.Caption,
                color = colors.text3,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}
