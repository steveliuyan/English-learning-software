package com.example.englishlearning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 分组小标题（2026-10-30 版式重做引入）：一级页把内容切成「一张卡 = 一件事」之后，
 * 卡与卡之间没有任何视觉缝，靠这行 `Label` 小字把「接下来」「设置」这类分组讲出来。
 *
 * 三条例行：
 *  - **没有可点子目标**。分组标题只是路牌，右侧的 `trailing` 是纯描述（例如「4 项」）；
 *    一级页的 connected 判据按「可点节点闭合集合」逐枚数，这里长出一枚 clickable 就当场数多。
 *    真要「查看全部」入口，那是 [BareRow] 的活（整行 52dp 可点，F5-03）。
 *  - **字级只取 `AppType.Label`**（12sp Medium、`text2`）。分组标题压不进 `Title` 或 `Body`：
 *    一旦和卡内条目标题同档，「这是分组」与「这是一条内容」就分不出来了（AC-R-20 的同一口径延伸）。
 *  - **不画线、不铺底**。分组靠留白与字重差异成立；再铺一层 `tint` 会把三档容器（强卡 / 软卡 / 裸行）
 *    的明度层序搅乱（spec §4.1）。
 *
 * 颜色一律 `appColors()` 取，深套自动翻色；本组件不设 `Modifier.clickable` 也不设背景。
 */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = AppType.Label,
            color = colors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = AppType.Label,
                color = colors.text2,
                maxLines = 1,
            )
        }
    }
}
