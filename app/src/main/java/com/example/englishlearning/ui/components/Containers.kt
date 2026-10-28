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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.LocalIsDark
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
 * 强档卡：圆角 28 + 内边距 18，**底与墨两套分取**（spec §4.1；2026-10-31 用户评审 ②③ 改浅套落点）。
 *
 * 浅套底取 [com.example.englishlearning.ui.theme.AppColorSet.tint2]（浅薄荷一档）、墨取
 * [com.example.englishlearning.ui.theme.AppColorSet.text1]；深套底仍是
 * [com.example.englishlearning.ui.theme.AppColorSet.strongSurface]、墨仍是
 * [com.example.englishlearning.ui.theme.AppColorSet.onStrong]（`#E9F2EC`）。
 *
 * 为什么分套而不是把 `strongSurface` 这个 token 改浅：它同时被主按钮、开关轨道、阅读页提交键消费，
 * 改 token 会把那些「实心深色操作面」一起洗淡——要降的是**卡片**的层次，不是操作面。
 * 深套不动：那套 `strongSurface` 与页面背景只差一档明度，深底浅字本来就是对的层次。
 *
 * `LocalContentColor` 必须提供分套后的那枚墨：卡内未显式着色的 `Text`（图标旁的计数一类）会落到
 * 容器内容色上。但 `LocalContentColor` 拦不住 [AppType] 六档 getter 自带的墨，所以卡内正文仍须
 * 显式写 `color = LocalContentColor.current`——**不要**在卡内写死 `onStrong`，那会在浅套里
 * 落到一枚浅薄荷底上的近白字（实测不可读，`ProfilePageContractTest` / `TodayPlanNumberAuthorityTest`
 * 各有一条把「卡内写死 onStrong」判红的用例）。
 */
@Composable
fun StrongCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    val isDark = LocalIsDark.current
    val fill = if (isDark) colors.strongSurface else colors.tint2
    val ink = if (isDark) colors.onStrong else colors.text1
    CompositionLocalProvider(LocalContentColor provides ink) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(fill)
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
 *
 * [titleMaxLines] 是批 3 Task 1 为外刊标题加的第二档：索引页的标题是「内容本身」（长标题截成半句
 * 就读不出是什么），而入口行的标题是「去哪」（单行更省、也更好扫）。默认 1 行，即既有各屏的几何
 * 与观感一字不动；两行只由需要它的调用方显式传。副标题仍是 2 行上限，不与这一档联动。
 *
 * [trailingTag] 是批 3 Task 5 加的第二档（同 [titleMaxLines] 的加法，只增不改）：尾部值由本组件渲染，
 * 而查词页的历史行要把「N 次」单独当成一枚节点来读（关闭次数显示时 connected 断言它不存在）。
 * 默认 `null` = 不打标签，既有调用点的标签面与树形一字不动。
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
    titleMaxLines: Int = 1,
    trailingTag: String? = null,
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
                maxLines = titleMaxLines,
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
                modifier = if (trailingTag == null) Modifier else Modifier.testTag(trailingTag),
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
