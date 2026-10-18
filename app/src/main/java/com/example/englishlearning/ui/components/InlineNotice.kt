package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 批 1 Task 10（R-10.1 / R-10.1b / R-10.1c）：`InlineNotice` = 视觉稿 `.banner` 家族
 * （`docs/design/2026-10-17-ui-redesign-mockups.html:178-186`），把此前散落在 11 份私有实现里的
 * 「错误 / 提示 / 回执」行收成一个组件。
 *
 * 三态 = [InlineNoticeKind.Info] / [InlineNoticeKind.Error] / [InlineNoticeKind.Success]，
 * **不建 `Warning` 第四态**（R-10.1c）：稿另有 `.banner warn` 琥珀态，但它的两处出现分别是组件样张
 * 本身与 AI 屏「未配置」提示（归批 2 Task 11 的 `NotConfiguredCard`），而 `AppIcons.kt` 已把 `Warning`
 * 字形登记为 `InlineNoticeKind.Error` 用图；spec §2.1 的 token 表里也没有 warning 值——枚举加第四态
 * 会让图标登记与 `DesignTokenContractTest` 双双对不上，组件里私设琥珀色就是私设 token。
 * 若批 2 真出现「警示但不致命」的行，走 token 契约升级（spec §2.1 补值 + Task 2 契约同步），不在这里凑色。
 *
 * 墨色全部来自纯表 [noticeInk]（无 `@Composable`），所以每态取哪四枚 token 能在 JVM 侧被
 * `InlineNoticeKindTest` 钉死：connected 侧读不到颜色（Task 9 实测口径），这里不指望真机断言墨色。
 */
enum class InlineNoticeKind { Error, Info, Success }

/**
 * 一态的四枚墨色：前景（文案 + 字形）、底、描边、动作墨。
 *
 * 四字段而不是 `Pair`：稿 `.banner` 每个态钉的就是这四个值（`mockups.html:178-186`），
 * 只钉前两枚的表不叫纯表——描边与动作墨会成了各屏自由发挥的第二套真值。
 */
data class NoticeInk(
    val foreground: Color,
    val backdrop: Color,
    val border: Color,
    val action: Color,
)

/**
 * 三态 → token 的纯表（浅深两套各自成立，[set] 就是那两套之一）。
 *
 * 底色的 `lerp` 就是稿的 `color-mix(in srgb, X 12%, var(--surface))`：sRGB 逐通道线性插值，
 * 与 [androidx.compose.ui.graphics.lerp] 同口径（`IconBadge.kt` 那个私有 `mixArgb` 是 int 域实现，
 * 跨文件抄一份就是第二套口径，这里不抄）。描边的 `color-mix(… 34%/36%, transparent)` 等价写法就是 alpha。
 *
 * [NoticeInk.action] 一律取 `set.text1`，**这是 R-10.1b 登记的偏离**：稿 `.act` 写的是
 * `color: var(--d-ai)`（浅套 = AiSpeech 的 deep），它压浅套 `tint` 底不足正文 4.5，会被批 1 Task 1
 * 的低对比禁令契约当场挡下；`text1` 压三态底在两套主题里都富余，稿的 700 字重改由 `FontWeight.Bold`
 * 保「动作是一枚链接」的读感。深套稿值本可达标，但两套同值比「按主题分叉一个未裁决的强调色」便宜。
 * 这条裁决的依据本身也在 `InlineNoticeKindTest` 里承重（稿值压 tint 若哪天达标，那条断言会红，提示重议）。
 */
fun noticeInk(kind: InlineNoticeKind, set: AppColorSet): NoticeInk = when (kind) {
    InlineNoticeKind.Info -> NoticeInk(
        foreground = set.onMint,
        backdrop = set.tint,
        border = set.outline,
        action = set.text1,
    )

    InlineNoticeKind.Error -> NoticeInk(
        foreground = set.error,
        backdrop = lerp(set.surface, set.errorFill, 0.12f),
        border = set.errorFill.copy(alpha = 0.36f),
        action = set.text1,
    )

    InlineNoticeKind.Success -> NoticeInk(
        foreground = set.success,
        backdrop = lerp(set.surface, set.success, 0.12f),
        border = set.success.copy(alpha = 0.34f),
        action = set.text1,
    )
}

/**
 * 三态用图：逐枚取 `AppIcons.kt` 文件头清单里已登记的落点——Info→`Info`、Error→`Warning`（`:30` 钉死）、
 * Success→`Check`（`:5`）。
 */
internal val InlineNoticeKind.glyph: AppIcon
    get() = when (this) {
        InlineNoticeKind.Info -> AppIcon.Info
        InlineNoticeKind.Error -> AppIcon.Warning
        InlineNoticeKind.Success -> AppIcon.Check
    }

/**
 * 层开关按两层豁免表（`StrokeOnlyIcons`）走：`Info` / `Warning` 是两层枚，用默认 [AppGlyphLayer.Duotone]；
 * `Check` 在豁免表里（开放折线、没有可闭合的内部区域），必须显式 [AppGlyphLayer.StrokeOnly]。
 */
internal val InlineNoticeKind.glyphLayer: AppGlyphLayer
    get() = when (this) {
        InlineNoticeKind.Success -> AppGlyphLayer.StrokeOnly
        InlineNoticeKind.Info, InlineNoticeKind.Error -> AppGlyphLayer.Duotone
    }

/**
 * 行内提示 / 回执条：几何按稿 `.banner`（`mockups.html:178-186`）——`Row` + 间隙 10dp +
 * `padding(horizontal = 14, vertical = 12)` + 1dp 描边 + 18dp 状态字形 + 13px 档文案。
 *
 * 圆角走 `MaterialTheme.shapes.medium`（装配点 = 稿 `--r-card` 的 18dp，见 `MintGlassTheme.kt:173`），
 * **不读 `AppShape.Card`**（20dp 旧档，新组件禁读）。
 *
 * 字形是装饰层（`AppGlyph` 内部已 `contentDescription = null`），语义由文案承载；
 * 文案取 `AppType.Footnote`（稿 13px）+ [NoticeInk.foreground]，**不给 `maxLines`**：
 * 提示文案被裁掉就是丢信息，与 [EntryRow] 的标题/副标题相反口径（那边是提示性内容、允许省略号）。
 * 这条方向差异由 `InlineNoticeTest.notice_text_wraps_instead_of_clipping_at_font_scale_2` 钉住。
 *
 * `action` 是尾部动作槽（稿 `.act` 是**无容器的文本链接**，所以这里不给 `PillButton`——那会把
 * 「一条提示」升级成「一个主操作」）。槽内容由调用方渲染成 `Text` + `clickable` +
 * `FontWeight.Bold`（稿 700），墨色取 [NoticeInk.action]（`noticeInk(kind, appColors()).action`）。
 * 本组件负责两件槽内拿不到的事：
 *  ① 把 F5-03 的 48dp 触控硬下限**只**派给动作节点——`heightIn(min = 48.dp)` 落在包裹层并用
 *     `propagateMinConstraints` 传进槽内，让槽内的 `clickable` 真填满 48dp 高（**不给整条提示**：
 *     无动作的 `.banner` 只有 12dp 上下 padding + 13sp 行高 ≈ 42dp，它是内容不是触控目标，
 *     把它钉成 ≥48dp 会造出一条稿里没有、F5-03 也没要求的水线）；
 *  ② 把 `testTag("inline_notice_action")` 与可点语义并到同一枚节点上（`mergeDescendants`），
 *     否则标签在父、click 在子，真机上 `onNodeWithTag("inline_notice_action").performClick()`
 *     会打在没有 click action 的节点上。
 *
 * 行本体标签 `inline_notice_${kind.name.lowercase()}`：三态各一条，供 connected 用例逐条 `assertExists`。
 *
 * 已登记的偏离（不是随手替换）：稿 `.err` 画的是「圆圈 + 叉」，本仓 B 案 31 枚字形里没有这一枚，
 * 扩一枚要重走图标决策 + 许可登记，故按 `AppIcons.kt:30` 的登记用 `Warning` 三角承载 Error 态；
 * Success 态稿面无样张，依据是 spec §组件表把本组件定义为「错误 / 回执行统一样式」+ `Check` 已登记为
 * 成功回显，墨色复用既有 `success` token、按稿 `.warn` 同式（12% 混底 + 34% alpha 边），不是新造色。
 */
@Composable
fun InlineNotice(
    text: String,
    kind: InlineNoticeKind,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    val set = appColors()
    val ink = noticeInk(kind, set)
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ink.backdrop)
            .border(1.dp, ink.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { testTag = "inline_notice_${kind.name.lowercase()}" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppGlyph(icon = kind.glyph, tint = ink.foreground, size = 18.dp, layer = kind.glyphLayer)
        Text(
            text = text,
            style = AppType.Footnote,
            color = ink.foreground,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp)
                    .semantics(mergeDescendants = true) { testTag = "inline_notice_action" },
                contentAlignment = Alignment.Center,
                propagateMinConstraints = true,
            ) {
                action()
            }
        }
    }
}
