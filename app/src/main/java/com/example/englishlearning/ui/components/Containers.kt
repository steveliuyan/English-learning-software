package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.LocalIsDark
import com.example.englishlearning.ui.theme.appColors

/**
 * 原型对齐批 0 的四种承载面 + 裸行（原型 `assets/app.css` `.card` / `.card-soft` / `.card-quiet` /
 * `.strong-card` / `.row`）。
 *
 * 四档分工不是四种皮肤，而是「一屏之内的层次」：
 *  - [AppCard]：白底 + 1dp 发丝描边，主内容卡（原型 `.card`，54 处）；
 *  - [SoftCard]：`tint` 实底、**不描边**，次级分组（原型 `.card-soft`）；
 *  - [QuietCard]：不铺底、只描边，夹在已有底上的安静分组（原型 `.card-quiet`，7 处）；
 *  - [StrongCard]：一屏至多一枚的深色主卡（原型 `.strong-card`，3 处）。
 * 裸行 [BareRow] 靠 1dp `line-soft` 顶线表达「同组」，几何按原型 `.row`（上下 12dp、无横向内距、
 * 48dp 触控下限）。卡片内距统一 16dp，横向留白归宿主卡片，行本身不再自带 15dp 内距。
 *
 * 份数由 `SharedComponentUniquenessRatchetTest` 的 `canonicalDefinitions` 守着；
 * 几何/取色由 `SharedComponentContractTest` 对着原型逐条钉住。
 */

/**
 * 强档卡：圆角 26 + 内边距 20 + 右上径向薄荷光（原型 `.strong-card`）。
 *
 * **浅套的底与墨保留 2026-10-31 用户评审 ②③ 的落点**（`tint2` + `text1`），不照搬原型浅套的深底：
 * 那次评审明确「浅套深墨实底太重、喧宾夺主」，是比原型更晚的用户决定；改回深底是一行翻转，
 * 但必须先过用户。深套仍是 `strongSurface` + `onStrong`，与原型一致。该偏离在批 0 报告点名。
 *
 * 右上光斑按原型 `.strong-card::after`：168dp 圆、圆心在卡片右上 35%/35%、有效半径 68%，
 * 颜色 = `brand` 30% → 透明。用 `drawBehind` 画在底与内容之间，`clip` 已经把圆裁进卡内。
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
    contentPadding: PaddingValues = PaddingValues(20.dp),
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
                .clip(RoundedCornerShape(26.dp))
                .background(fill)
                .drawBehind {
                    val glowRadius = 114.dp.toPx()
                    val glowCenter = Offset(size.width - 149.dp.toPx(), 3.dp.toPx())
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(colors.brand.copy(alpha = 0.30f), Color.Transparent),
                            center = glowCenter,
                            radius = glowRadius,
                        ),
                        radius = glowRadius,
                        center = glowCenter,
                    )
                }
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * 主内容卡：`surface` 实底 + 1dp `outline` 描边 + 圆角 20 + 内边距 16（原型 `.card`）。
 *
 * 与 [SoftCard] 的分工是原型里最常用的两档：白卡承载「一条可读内容」，薄荷卡承载「一组次级状态」。
 * 两者圆角与内距相同，只差底色与有无描边——这正是原型 `.card` / `.card-soft` 的唯一差异。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShape.Card)
            .background(colors.surface)
            .border(1.dp, colors.outline, AppShape.Card)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 软档卡：`tint` 实底 + 圆角 20 + 内边距 16，**不描边**（原型 `.card-soft`）。
 *
 * 层次由 `tint` 与页面 `background` 的明度差表达；一旦加回描边，「卡片汤」就回来了
 * ——`ContainerContractTest` 的 `the soft card carries no border` 钉着这一条。
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
            .clip(AppShape.Card)
            .background(colors.tint)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 安静卡：**不铺底**、只留 1dp `outline` 描边 + 圆角 20 + 内边距 16（原型 `.card-quiet`）。
 *
 * 用在已经有底色的区域里（强卡内、页面分段背景上）——再铺一层 `surface` 会把两档明度压平。
 * 不画底色的另一个后果是它不能承接正文级的「块」语义：层次只靠描边与留白，正文仍归 [AppCard]。
 */
@Composable
fun QuietCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShape.Card)
            .border(1.dp, colors.outline, AppShape.Card)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 裸行：不套卡片，只有「贴纸/图标 + 标题 + 可选副标题 + 尾部值 + chevron」的一行（原型 `.row`）。
 *
 * 几何：上下 12dp、**无横向内距**（横向留白归宿主卡片，原型 `.row` 就是 `padding: var(--s3) 0`）、
 * 行内间隙 12dp、最小高 48dp（F5-03 无障碍下限；原型自然高约 45–52dp，行高决定）。顶线从行**外侧**
 * 起笔、全宽（原型 `border-top` 无缩进），`topDivider = true` 时画 1dp `line-soft`。
 *
 * 字：标题 15sp/550/-0.005em（原型 `.row-title`），副行 12sp `ink-3`（`.row-sub`），尾部值 13sp
 * `ink-3`（`.row-value`），chevron 18dp `ink-3`。副行两行上限，标题默认一行、可由长标题调用点显式放宽。
 *
 * 链序里 `clickable` 排在 `padding` **之前**（与 `EntryRow` 同一条裁决）：可点面是整盒，不是内容盒，
 * 否则真机上量到的可点高度会掉到 40dp 一带、撞 F5-03 的 48dp 下限。
 *
 * 只给 `onClick != null` 的行挂 `clickable`：纯信息行（统计、只读摘要）不是「假入口」，
 * 也不该进 TalkBack 的可点序列（原型对应 `.row-flat`）。
 *
 * [trailingTag] 是尾部值的测试标识（默认 `null` = 不打标签），既有调用点的标签面与树形不变。
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
            .then(hairlineTop(if (topDivider) colors.outlineSoft else null, startIndent = 0.dp))
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = AppType.Body,
                color = colors.text1,
                fontWeight = FontWeight(550),
                letterSpacing = (-0.075).sp,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = AppType.Caption,
                    color = colors.text3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = AppType.Meta,
                color = colors.text3,
                maxLines = 1,
                modifier = if (trailingTag == null) Modifier else Modifier.testTag(trailingTag),
            )
        }
        if (showChevron && onClick != null) {
            AppGlyph(icon = AppIcon.ChevronRight, tint = colors.text3, size = 18.dp, layer = AppGlyphLayer.StrokeOnly)
        }
    }
}

/**
 * 顶线装饰：墨色为 `null` 时返回空链（不占位、不画线），非空时「先画线、再让出 1dp」。
 *
 * 两步的**顺序**就是契约：`drawBehind` 在 `padding` 外侧，线才落在让出来的那条 1dp 里；
 * 反过来写（`padding` 后 `drawBehind`）会把线画进内容顶边。`heightIn` 由调用方接在这条链之后，
 * 于是最小内容高不被这条线吃掉。线宽 1dp 居中于 y = 0.5dp，避免 1dp 描边被上边缘切掉一半。
 * [startIndent] 默认 0（原型 `.row` 顶线全宽）；只有需要「从文字起点起笔」的旧版式才显式传缩进。
 *
 * 这个助手**不是** `Modifier` 扩展：调用点既有 `hairlineTop(color)` 的写法，改成扩展就要
 * 在每个调用点前面补 `Modifier.`，收益为零。`internal` 只对模块内可见，`EntryRow` 与卡片共用。
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
