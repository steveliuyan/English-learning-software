package com.example.englishlearning.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.appColors

/**
 * 全站骨架族（批 C-1；口径见 `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 1）。
 *
 * **为什么收口成一份**：批 C 开工实测，生产里真正在画的骨架只有 `AppScreen` 的私有
 * `WordBookRowSkeleton`（选词书一屏），而本文件旧版的 `ListSkeleton` 一族**全仓零引用**——死代码，
 * 且它的 KDoc 声称「connected 判据依赖 `wordbook_placeholder_`」，实测那个 tag 只存在于该文件自身。
 * 于是「铺全站」有两个坑：一是屏侧各画一份私有骨架（真卡改版时要跟两处），二是照假文档保留两套 tag。
 * 本族一次堵掉：基本形状只许在这里定义一份（`SkeletonFamilyContractTest` 数整棵 ui 源码树），
 * 旧前缀 `wordbook_placeholder_` 随死代码一起退役。
 *
 * 五条共同约束：
 *  - **零文案、零数字**（F5-05 与 2026-11-06 占位诚实化决策同源）：占位行手里还没有数据，
 *    向 TalkBack 播报「已学 0 词 / 共 0 词」这类假数字比什么都不念更糟。这里只有色块。
 *  - **形状全部走形参**，组件不写死圆角与行高：调用方那屏的真实形状才是骨架该照的对象
 *    （词书卡的圆角与卡片行高各有档位，写死会让红底渗出或回填时高度跳）。
 *  - **颜色只走 `appColors()`**：禁裸 hex（`SkeletonFamilyContractTest` + 既有裸 hex 棘轮双重钉住）。
 *  - **只承诺横向起点对齐**，不承诺纵向总高：真卡带进度文本与进度条时更高，回填后会长——
 *    首帧要的是「立刻有东西且形状对」，不是逐像素复刻最终版面（沿用旧族那一条刻意取舍）。
 *  - 骨架**没有可点面**：它不是入口，读屏不该聚焦它，所以本族一律不挂 `selectable` / `clickable`。
 *
 * shimmer（循环微光）不在本文件：那是动效不是形状，见 `Shimmer.kt` 与批 C-1 的第二枚契约。
 */

/**
 * 一条文字占位条：宽按 [fraction] 取父容器比例，高 [height]，圆角 [shape]。
 *
 * 分数写法（0.42f / 0.24f 这类）是从选词书真实行的「书名 + 副行」两档宽度抄来的，
 * 不是随手取的装饰值——骨架条与真文本列宽接近，回填时视觉位置才不跳。
 *
 * 微光先 `.clip(shape)` 再上：不裁的话光带会在药丸条的两端画出方角，圆角档由调用方给的
 * [shape] 决定，本组件不许自己写死一档圆角（否则卡片类骨架的形状就跟着失真）。
 */
@Composable
fun SkeletonBar(
    fraction: Float,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = AppShape.Icon,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(shape)
            .background(SkeletonInk.fill(appColors()), shape)
            .skeletonShimmer(),
    )
}

/**
 * 一条通栏骨架行：与真行同线序、同内边距、同最小行高，但不写任何数字、也不画任何文案。
 *
 * 两条迁移期定死的细节（原 `AppScreen.WordBookRowSkeleton` 实测结论，逐字搬过来）：
 *  1. **testTag 挂在 `hairlineTop` 外侧**。`hairlineTop` 的 1dp 顶内距会裁掉内侧语义节点的 bounds
 *     （`PaddingModifier.semanticsBounds`）；真实行因为 `PickRow` 里那条更靠内的 `semantics{}` 也被裁掉
 *     1dp，骨架这一支没有内层语义节点——tag 一旦放进 padding 内侧，骨架量到的行框就比真实行**多出** 1dp，
 *     「骨架与真实行同形状」那条 connected 判据会自己骗自己（`WordbookPickerHeaderTest` 的行高用例）。
 *  2. **顶部分隔线归调用方**（[topDivider]）：原型 `.pick:first-child { border-top: 0 }`，只有首行不画线，
 *     线宽与线色和真行逐字相同（`outlineSoft` 1dp），回填时那道缝由真行的线承接，列表总高度不跳。
 *  3. **行首圆点可关**（[leading]，批 C-3 加）：默认那一枚对应 `PickRow` 的前导图标；而 `WordRow` 那一族
 *     真实行**没有前导图标**（词头在左列、状态词在右列），照抄时多画一枚圆点就是「形状不像」——
 *     F5-05 唯一的判据就是像不像，所以这不是装饰开关而是**镜像能力**（`LocalSubpageSkeletonContractTest`
 *     钉「族里恰两枚 `leading: Boolean`、且确有屏传 `leading = false`」，防止参数长出来没人用）。
 *  4. **行壳可换**（[framed]，批 C-3 加）：默认那一档是「通栏行 + 发丝线」，对应选词书与学习记录；
 *     `WordBookWordsScreen` 那一族的真实行是 `Card(surface + 1dp 描边)` 里套 `WordRow`、行间距 10dp 缝。
 *     [framed] 走那一档：壳、内边距（16/12）与缝都由本组件按真实形状给，屏侧不再自己包卡。
 */
@Composable
fun SkeletonRow(
    topDivider: Boolean,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    tag: String? = null,
    leading: Boolean = true,
    framed: Boolean = false,
    markSize: Dp = 22.dp,
    titleFraction: Float = 0.42f,
    titleHeight: Dp = 12.dp,
    subtitleFraction: Float = 0.24f,
    subtitleHeight: Dp = 10.dp,
) {
    val colors = appColors()
    // 两档外壳共用同一份内容：槽位顺序（标记 + 左列两条）、行内缝（12dp，与 `WordRow` 逐字相同）
    // 与 shimmer 都只有一份；分成两个分支体各写一遍，就是族里长出第二种「行内版式」。
    val bars: @Composable RowScope.() -> Unit = {
        if (leading) {
            Box(
                modifier = Modifier
                    .size(markSize)
                    .clip(CircleShape)
                    .background(SkeletonInk.fill(colors), CircleShape)
                    .skeletonShimmer(),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBar(fraction = titleFraction, height = titleHeight)
            SkeletonBar(fraction = subtitleFraction, height = subtitleHeight)
        }
    }
    if (framed) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
            shape = AppShape.Card,
            border = BorderStroke(1.dp, colors.outlineSoft),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = rowHeight)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                content = bars,
            )
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                // 线画在 padding 外侧：`hairlineTop` 自带 `padding(top = 1.dp)` 让出那道缝，最小行高不被 1dp 吃掉。
                .then(hairlineTop(if (topDivider) colors.outlineSoft else null, startIndent = 0.dp))
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .heightIn(min = rowHeight)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = bars,
        )
    }
}

/**
 * N 行通栏骨架（列表屏的首帧）。行序即列表序：只有首行不画线，其余每行一条 `outlineSoft` 细线。
 *
 * [rows] 必须由调用方读**生产常量**（例：`WORD_BOOK_PLACEHOLDER_ROWS`），不许在屏侧另写数字——
 * 骨架行数与真实列表的可视行数对不上时，回填会顶偏滚动位置，而那条判据读的是常量而不是字面量。
 * [tagPrefix] 由调用方给（例：`wordbook_placeholder` 风格的 `wordbook_row_skeleton_`），tag 落在这里
 * 而不是内部写死，是为了让屏侧的 testTag 锚点仍然只出现在那一屏的源码里一份。
 *
 * [titleFraction] / [subtitleFraction] 是「这一屏的真行有多宽」的镜像口：默认档抄自选词书行
 * （书名 + 副行），词表类行（词头短、释义行宽）要按那一屏的形状给档，否则回填时文字列宽整列跳。
 * [leading] / [framed] 透传给每一行（见 [SkeletonRow] 第 3、4 条）：无前导图标的那一族行传 `leading = false`，
 * 带壳的卡行传 `framed = true`——此时行间距换成 10dp 缝、发丝线退役，照的正是 `LazyColumn(spacedBy 10.dp)`
 * 里那种 `Card` 行的真实缝档。
 */
@Composable
fun SkeletonList(
    rows: Int,
    rowHeight: Dp,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    leading: Boolean = true,
    framed: Boolean = false,
    markSize: Dp = 22.dp,
    titleFraction: Float = 0.42f,
    subtitleFraction: Float = 0.24f,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = if (framed) Arrangement.spacedBy(10.dp) else Arrangement.spacedBy(0.dp),
    ) {
        repeat(rows) { index ->
            SkeletonRow(
                tag = "$tagPrefix$index",
                topDivider = !framed && index > 0,
                rowHeight = rowHeight,
                leading = leading,
                framed = framed,
                markSize = markSize,
                titleFraction = titleFraction,
                subtitleFraction = subtitleFraction,
            )
        }
    }
}

/**
 * 卡片块骨架（非列表屏：今日计划、词卡、档案格这类「一张卡里几行内容」的形状）。
 *
 * 与 [SkeletonRow] 的分工：那一族画「一行行」，这一族画「一块块」。[bars] 给的是卡内条数，
 * 首条按标题档（更宽更厚），其余按副行档——照的是真卡「标题 + 若干说明行」的形状。
 * [minHeight] 让调用方按真卡给下限，回填时卡片不至于一格都不变。
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    bars: Int = 3,
    minHeight: Dp = 92.dp,
    shape: Shape = AppShape.Card,
) {
    val colors = appColors()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            repeat(bars) { index ->
                if (index == 0) {
                    SkeletonBar(fraction = 0.42f, height = 14.dp)
                } else {
                    SkeletonBar(fraction = 0.72f, height = 10.dp)
                }
            }
        }
    }
}

/**
 * 分段计数格骨架（词库四段、我的页统计行这类「几个等宽格子并排」的形状）。
 *
 * 为什么单独一件：这一族的真实形状是「格子 + 数字 + 标签」，而**数字与标签都不能预先画**
 * ——把格子写成「0」就是 F1-01 那枚假数字。所以这里只给格子外壳，数字位留一条短横。
 * [cells] 由调用方按真实段数给（词库 4、统计行 3），段数与真值不符会让回填时格宽重排。
 */
@Composable
fun SkeletonStatGrid(
    cells: Int,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 64.dp,
) {
    val colors = appColors()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(cells) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = rowHeight),
                shape = AppShape.Small,
                colors = CardDefaults.cardColors(containerColor = colors.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SkeletonBar(fraction = 0.4f, height = 14.dp)
                    SkeletonBar(fraction = 0.7f, height = 10.dp)
                }
            }
        }
    }
}
