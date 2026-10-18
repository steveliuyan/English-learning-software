package com.example.englishlearning.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 批 1 Task 10（R-10.4）：入口行的五枚墨色是**纯表**，不是组件里的临场凑色。
 *
 * 为什么这张表只在 JVM 侧钉：connected 读不到颜色（Task 9 实测口径——`AppGlyph`/`Text` 的着色不是
 * 语义节点能取的东西），所以「标题取哪枚 token」这类裁决必须由 [entryRowInks] 承载、由
 * `EntryRowInkTest` 逐槽复算，本组件只读这张表。
 */
data class EntryRowInk(
    val title: Color,
    val subtitle: Color,
    val trailing: Color,
    val chevron: Color,
    val divider: Color,
)

/**
 * 两套主题 → 五枚槽位墨色的纯函数（无 `@Composable`，[set] 就是浅套或深套那两套之一）。
 *
 * 槽位分配（R-10.4，稿 `.en`/`.ed`/`.val`/`.chev`/`.entry + .entry`）：标题 `text1`、副标题与尾部值
 * `text2`、行间发丝线 `outlineSoft`。**chevron 取 `text2`、不取稿写的 `text-3`**：`text3` 压浅套 `tint`
 * 恰好落在非文本 3:1 线上（边界上的合格不算合格），批 1 Task 1 的裁决 #1 又禁止 `text-3` 作前景；
 * 这条偏离连同「不得回到 text3」由 `EntryRowInkTest.chevron_is_pinned_to_text2_and_explicitly_not_to_text3`
 * 双向守着。比值与等值都不写在注释里（抄了会随 token 取值漂移），算式在测试端复算。
 */
fun entryRowInks(set: AppColorSet): EntryRowInk = EntryRowInk(
    title = set.text1,
    subtitle = set.text2,
    trailing = set.text2,
    chevron = set.text2,
    divider = set.outlineSoft,
)

/**
 * 统一入口行（稿 §入口行 `mockups.html:150-160`）：40dp 彩色图标容器 + 标题/副标题 + 尾部值 +
 * 尾部 chevron，整行一个可点目标。
 *
 * 几何（逐条对应稿）：`Row` 间隙 12dp（稿 `.en` 与 `.ic` 的 margin）、`heightIn(min = 56.dp)`
 * （稿 `.entry { min-height:56px }`）、`padding(vertical = 8.dp)`、整行 `clickable(role = Role.Button)`
 * ——行内徽标与 chevron 都是装饰层，不得成为第二个可点源（connected 用例
 * `entry_row_is_one_click_target_whole_row_with_button_role` 钉可点节点数恰为 1）。
 * 图标容器走 [EntryIconBadge] 的两档 [EntryIconTone]（R-10.2：批 2「今日计划」的三条入口行全部用弱档）。
 *
 * **修饰符顺序就是契约，别按稿面顺序「顺手调回去」**：`clickable` 排在 `fillMaxWidth()` 之后、
 * 顶线与 `heightIn`/`padding` 之前（即整条链的最外层可点面）。计划原文写的
 * `.heightIn(...).padding(...).clickable(...)` 在真机上量到的是 **40dp** 的可点面（padding 与线被留在
 * 可点面**之外**，只剩内容盒），直接撞 F5-03 的 48dp 下限——`entry_row_clears_the_48dp_touch_floor`
 * 就是抓这条的 connected 用例（首次实现按计划原文写，真机实测 `Actual height is 40.0.dp` 红，
 * 改成本顺序后 56dp 通过）。几何等价：整盒仍是 1dp 线 + 56dp（内含 8+8 padding、内容下限 40dp）。
 *
 * 排印按六档 token 落，两处差异按 **token 近似**登记（spec §2.2 六档锁：不引新档、不
 * `copy(fontWeight = …)`）：稿 `.en` 是 15px、字重 600，[AppType.Body] 是 15sp/400；稿 `.ed` 是 12px/400，
 * 六档没有 12sp Regular 档，取同族 `Footnote` 13sp/400（与 Task 9 副标题同一处置）。
 * 标题与副标题都给 `maxLines = 1` + [TextOverflow.Ellipsis]：入口行的标题/副标题是**提示性内容**
 * （行本身才是信息载体，点进去才读得到全量），允许省略号——这与 [InlineNotice] 的提示文案相反口径
 * （那边裁掉就是丢信息，故不给 `maxLines`）。判定方向由
 * `entry_row_truncates_title_and_subtitle_with_ellipsis_not_clipping` 钉：这里
 * `hasVisualOverflow == true` 是**期望结果**（省略号生效），别照页头用例把它「改」成 `!hasVisualOverflow`。
 *
 * `testTag` 由调用方经 [modifier] 传入（本组件不私设行标签）。唯一例外是徽标位的
 * `entry_row_icon`：[EntryIconBadge] 与 [AppGlyph] 都是无语义的装饰节点，connected 侧没有任何语义
 * 节点能量到那枚 40dp 容器，R-10.2 的「两档同容器尺寸」就失去可执行的测量口——故在行内给徽标加一枚
 * 纯定位用的 `testTag`（不配音义、不改视觉），用例以 `useUnmergedTree = true` 取它
 * （行是 `clickable` 的合并节点，子节点不进合并树，本仓先例见 `GlobalVocabularySearchScreenTest`）。
 *
 * [topDivider]（R-10.5）：稿 `.entry + .entry { border-top:1px outline-soft }` 是「除首行外每行一条
 * 顶线」。不新增 `EntryRowGroup`（撞组件份数棘轮）、也不留给各屏手搓 `HorizontalDivider`（批 2 有
 * 5 屏 × 3~8 行要抄 30 余次）。线画在内容**外侧**：先 `drawBehind` 出 1dp 发丝线、再 `padding(top = 1.dp)`
 * 把内容整块推下去，`heightIn(min = 56.dp)` 落在这条 padding 的**内侧**——所以 56dp 的最小内容高
 * 一动不动（R-10.5 禁的正是「把 56 吃成 55/57」），行的整体外框才是 56+1。
 *
 * [onClick] 是最后一个必填形参：调用方用尾随 lambda 写法 `EntryRow(icon, domain, "今日计划") { … }`
 * 即可，其余可选项一律具名传。
 */
@Composable
fun EntryRow(
    icon: AppIcon,
    domain: AppDomain,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    showChevron: Boolean = true,
    topDivider: Boolean = false,
    iconTone: EntryIconTone = EntryIconTone.Gradient,
    onClick: () -> Unit,
) {
    val inks = entryRowInks(appColors())
    val dividerModifier = hairlineTop(if (topDivider) inks.divider else null)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .then(dividerModifier)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryIconBadge(
            icon = icon,
            domain = domain,
            modifier = Modifier.testTag("entry_row_icon"),
            tone = iconTone,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppType.Body,
                color = inks.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppType.Footnote,
                    color = inks.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = AppType.Footnote,
                color = inks.trailing,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            AppGlyph(icon = AppIcon.ChevronRight, tint = inks.chevron, size = 16.dp, layer = AppGlyphLayer.StrokeOnly)
        }
    }
}

/**
 * 顶线装饰：墨色为 `null` 时返回空链（不占位、不画线），非空时「先画线、再让出 1dp」。
 *
 * 两步的**顺序**就是契约：`drawBehind` 在 `padding` 外侧，线才落在让出来的那条 1dp 里；
 * 反过来写（`padding` 后 `drawBehind`）会把线画进内容顶边。`heightIn` 由调用方接在这条链之后，
 * 于是最小内容高仍是 56dp（R-10.5）。线宽 1dp 居中于 y = 0.5dp，避免 1dp 描边被上边缘切掉一半。
 */
private fun hairlineTop(color: Color?): Modifier = if (color == null) {
    Modifier
} else {
    Modifier
        .drawBehind {
            val thickness = 1.dp.toPx()
            drawLine(
                color = color,
                start = Offset(0f, thickness / 2f),
                end = Offset(size.width, thickness / 2f),
                strokeWidth = thickness,
                cap = StrokeCap.Butt,
            )
        }
        .padding(top = 1.dp)
}
