package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 批 1 的三档容器（spec §4.1）：**强卡 / 软卡 / 裸行**。
 *
 * 三档的分工是「一屏之内的层次」本身，不是三种皮肤：强卡一屏至多一枚（2 枚起就是又回到卡片汤），
 * 软卡只靠 `surface` 与 `strongSurface` 的明度差分层、**不描边**，裸行靠 1dp 发丝线与 52dp 缩进
 * 表达「同组」。三档都写在这一个文件里，因为它们是同一个层次决策的三个刻度；份数由
 * `SharedComponentUniquenessRatchetTest` 的 `canonicalDefinitions` 守着。
 */

/** 裸行分隔线的左缩进：与 40dp 图标 + 12dp 行内间距对齐，让线从文字起点开始（spec §4.3）。 */
val BareRowDividerIndent: Dp = 52.dp

/**
 * 强档卡：深墨实底 + 圆角 28 + 内边距 18（spec §4.1）。
 *
 * 底色取 [com.example.englishlearning.ui.theme.AppColorSet.strongSurface]——明暗两套都是深底，
 * 所以卡内文字一律取 [com.example.englishlearning.ui.theme.AppColorSet.onStrong]（两套同值 `#E9F2EC`，
 * 压两套 strongSurface 均 ≥4.5:1）。这里额外把 `LocalContentColor` 一并提供成 `onStrong`：
 * [AppType] 的六档 getter 会自带墨色，但未显式着色的 `Text`（例如图标旁边的计数）仍会落到
 * 容器内容色上——不提供就会在浅套里落下近黑的 `text1`，深底深字等于不可读。
 * `LocalContentColor` 拦不住 `AppType` 自带的墨，所以卡内正文仍须显式写 `color = colors.onStrong`。
 */
@Composable
fun StrongCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    CompositionLocalProvider(LocalContentColor provides colors.onStrong) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.strongSurface)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * 软档卡：`surface` 实底 + 圆角 20 + 内边距 16，**不描边**（spec §4.1）。
 *
 * 层次由明度差表达：浅套是白底压薄荷背景，深套是 `#17201C` 压 `#0E1512`。一旦加回描边，
 * 「卡片汤」就回来了——`ContainerContractTest` 的 `the soft card carries no border` 钉着这一条。
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 裸行：不套卡片，只有「标题 + 可选副标题 + 尾部值 + chevron」的一行（spec §4.3）。
 *
 * 几何：最小高 52dp、内边距 15/13；`topDivider = true` 时在行**外侧**画一条从
 * [BareRowDividerIndent] 起笔的 1dp 发丝线——线画在 padding 外侧，所以 52dp 的最小内容高不被吃掉。
 * 链序里 `clickable` 排在 `padding` **之前**（与 `EntryRow` 同一条裁决）：可点面是整盒，
 * 不是内容盒，否则真机上量到的可点高度会掉到 40dp 一带、撞 F5-03 的 48dp 下限。
 *
 * 只给 `onClick != null` 的行挂 `clickable`：`onClick == null` 的纯信息行（统计、只读摘要）
 * 不是「假入口」，也不该进 TalkBack 的可点序列。
 */
@Composable
fun BareRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    leading: @Composable (() -> Unit)? = null,
    showChevron: Boolean = true,
    topDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .then(hairlineTop(if (topDivider) colors.outlineSoft else null, startIndent = BareRowDividerIndent))
            .heightIn(min = 52.dp)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = AppType.Body,
                color = colors.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = AppType.Footnote,
                    color = colors.text2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = AppType.Footnote,
                color = colors.text2,
                maxLines = 1,
            )
        }
        if (showChevron && onClick != null) {
            AppGlyph(icon = AppIcon.ChevronRight, tint = colors.text2, size = 16.dp, layer = AppGlyphLayer.StrokeOnly)
        }
    }
}

/**
 * 顶线装饰：墨色为 `null` 时返回空链（不占位、不画线），非空时「先画线、再让出 1dp」。
 *
 * 两步的**顺序**就是契约：`drawBehind` 在 `padding` 外侧，线才落在让出来的那条 1dp 里；
 * 反过来写（`padding` 后 `drawBehind`）会把线画进内容顶边。`heightIn` 由调用方接在这条链之后，
 * 于是最小内容高不被这条线吃掉（`EntryRow` 仍是 56dp）。线宽 1dp 居中于 y = 0.5dp，避免 1dp
 * 描边被上边缘切掉一半。[startIndent] 让线从图标/文字起点起笔（[BareRowDividerIndent]）。
 *
 * 这个助手**不是** `Modifier` 扩展：调用点既有 `hairlineTop(color)` 的写法，改成扩展就要
 * 在每个调用点前面补 `Modifier.`，收益为零。`internal` 只对模块内可见，`EntryRow` 与三档容器共用。
 */
internal fun hairlineTop(color: Color?, startIndent: Dp = 0.dp): Modifier = if (color == null) {
    Modifier
} else {
    Modifier
        .drawBehind {
            val thickness = 1.dp.toPx()
            val start = startIndent.toPx()
            drawLine(
                color = color,
                start = Offset(start, thickness / 2f),
                end = Offset(size.width, thickness / 2f),
                strokeWidth = thickness,
                cap = StrokeCap.Butt,
            )
        }
        .padding(top = 1.dp)
}
