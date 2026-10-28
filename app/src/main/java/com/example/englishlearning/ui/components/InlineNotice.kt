package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 行内提示 / 回执条（原型 `assets/app.css` `.notice` / `.notice-error` / `.notice-info`）。
 *
 * 三态 = [InlineNoticeKind.Info] / [InlineNoticeKind.Error] / [InlineNoticeKind.Success]。
 *
 * 几何逐条对位原型：内距 16×12（`padding: var(--s3) var(--s4)`）、圆角 14（`--r-md`）、**无描边**
 * （原型 `.notice` 没有 border）、字形与文案间隙 12dp（`gap: var(--s3)`）、状态字形 17dp
 * （`icon(ic, 17)`）、文案 12sp（`font-size: var(--fs-caption)`）。
 *
 * 底色按原型三档：`.notice` 本体 `tint` + `ink-2`；`.notice-error` `danger-tint` + `danger`；
 * `.notice-info` `surface-2` + `ink-2`。本仓 Info 取 `.notice` 本体那一档（tint + ink-2）——
 * 原型两种信息底色都合法，选本体是因为它同时是组件样张里出现最多的那一枚。
 *
 * **Success 是原型没画的状态**：它的存在依据是 spec §组件表把本组件定义为「错误 / 回执行统一样式」
 * 与既有屏幕的完成回执；墨色不新造色（`success` token 早已登记），底取 `.notice` 本体的 `tint`，
 * 与 Info 共用几何、只换墨。该状态在批 0 报告里点名，等原型补样张时再对齐。
 * **不建 `Warning` 第四态**：原型没有 `.notice-warn`，`AppIcons.kt` 也已把 `Warning` 字形登记给
 * Error 态；要加第四态得先有样张与 token。
 *
 * 墨色全部来自纯表 [noticeInk]（无 `@Composable`），每态取哪几枚 token 由 `InlineNoticeKindTest`
 * 在 JVM 侧钉死：connected 侧读不到颜色，这里不指望真机断言墨色。
 */
enum class InlineNoticeKind { Error, Info, Success }

/**
 * 一态的墨色：前景（文案 + 字形）、底、动作墨。
 *
 * 原型 `.notice` 没有描边，所以表里也没有 border 字段——留一个空槽只会让屏侧长出「自己给提示条
 * 描边」的第二套形状。
 */
data class NoticeInk(
    val foreground: Color,
    val backdrop: Color,
    val action: Color,
)

/**
 * 三态 → token 的纯表（浅深两套各自成立，[set] 就是那两套之一）。
 *
 * [NoticeInk.action] 一律取 `set.text1`，**这是 R-10.1b 登记的偏离**：原型的动作链接写域色，
 * 域色浅端压浅套提示底不足正文 4.5，会被批 1 Task 1 的低对比禁令契约当场挡下；`text1` 压三态底
 * 在两套主题里都富余，稿的 700 字重改由 `FontWeight.Bold` 保「动作是一枚链接」的读感。
 * 这条裁决的依据本身也在 `InlineNoticeKindTest` 里承重。
 */
fun noticeInk(kind: InlineNoticeKind, set: AppColorSet): NoticeInk = when (kind) {
    InlineNoticeKind.Info -> NoticeInk(foreground = set.text2, backdrop = set.tint, action = set.text1)

    InlineNoticeKind.Error -> NoticeInk(foreground = set.error, backdrop = set.dangerTint, action = set.text1)

    InlineNoticeKind.Success -> NoticeInk(foreground = set.success, backdrop = set.tint, action = set.text1)
}

/**
 * 三态用图：逐枚取 `AppIcons.kt` 文件头清单里已登记的落点——Info→`Info`、
 * Error→`Warning`（登记用途「警示与破坏性操作行」）、Success→`Check`（登记用途「完成与成功回显对勾」）。
 */
internal val InlineNoticeKind.glyph: AppIcon
    get() = when (this) {
        InlineNoticeKind.Info -> AppIcon.Info
        InlineNoticeKind.Error -> AppIcon.Warning
        InlineNoticeKind.Success -> AppIcon.Check
    }

/**
 * 层开关按三层结构豁免表（`StrokeOnlyIcons`）走：`Info` / `Warning` 是三层枚，用默认 [AppGlyphLayer.Duotone]；
 * `Check` 在豁免表里（开放折线、没有可闭合的内部区域），必须显式 [AppGlyphLayer.StrokeOnly]。
 */
internal val InlineNoticeKind.glyphLayer: AppGlyphLayer
    get() = when (this) {
        InlineNoticeKind.Success -> AppGlyphLayer.StrokeOnly
        InlineNoticeKind.Info, InlineNoticeKind.Error -> AppGlyphLayer.Duotone
    }

/**
 * 行内提示 / 回执条：几何按原型 `.notice`（16×12 内距、14 圆角、无描边、12dp 间隙、17dp 字形、
 * 12sp 文案）。圆角走共享 [AppShape.Button]（14dp）——原型 `--r-md` 就是这一档。
 *
 * 字形是装饰层（[AppGlyph] 内部已 `contentDescription = null`），语义由文案承载；
 * 文案取 [AppType.Caption]，**不给 `maxLines`**：提示文案被裁掉就是丢信息。
 *
 * `action` 是尾部动作槽（原型里没有动作链接，这是本仓回执行的既有能力，配方见 [InlineNoticeAction]）。
 * 本组件负责两件槽内拿不到的事：
 *  ① 把 F5-03 的 48dp 触控硬下限**只**派给动作节点——`heightIn(min = 48.dp)` 落在包裹层并用
 *     `propagateMinConstraints` 传进槽内，让槽内的 `clickable` 真填满 48dp 高（**不给整条提示**：
 *     无动作的提示只有 12dp 上下内距 + 12sp 行高，它是内容不是触控目标）；
 *  ② 把 `testTag("inline_notice_action")` 与可点语义并到同一枚节点上（`mergeDescendants`）。
 *
 * 行本体标签 `inline_notice_${kind.name.lowercase()}`：三态各一条，供 connected 用例逐条 `assertExists`。
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShape.Button)
            .background(ink.backdrop)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { testTag = "inline_notice_${kind.name.lowercase()}" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppGlyph(icon = kind.glyph, tint = ink.foreground, size = 17.dp, layer = kind.glyphLayer)
        Text(
            text = text,
            style = AppType.Caption,
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

/**
 * 提示条动作槽里的**唯一**链接配方（裁决 R-11.0）：`Text` + `clickable` + `FontWeight.Bold`
 * + [NoticeInk.action] 的墨色——只在这里写一遍，屏侧不许自排。
 *
 * 调用方写法（批 2/3 十几处一律照此）：
 * ```
 * InlineNotice(text = "语音评测暂时不可用", kind = InlineNoticeKind.Error,
 *     action = { InlineNoticeAction(InlineNoticeKind.Error, "重试", onClick = { retry() }) })
 * ```
 * **回调必须具名传 `onClick =`，尾随 lambda 写法编译不过**：签名末位是 `modifier`（不是函数类型），
 * 而 Kotlin 的尾随 lambda 只绑末位形参。签名形状由 `InlineNoticeActionContractTest` 逐字钉住。
 *
 * **触控面不在这里钉**：48dp 的交付机制是 [InlineNotice] 动作槽包裹层上的
 * `heightIn(min = 48.dp)` + `propagateMinConstraints = true`。此处若再写 `fillMaxHeight()` 就会
 * 吃最大高约束（有界宿主里把可点面撑到半屏、无界宿主里是空操作），故不写，并由
 * `InlineNoticeActionContractTest` 把「本文件 0 处 fillMaxHeight」钉成禁形。
 */
@Composable
fun InlineNoticeAction(
    kind: InlineNoticeKind,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = noticeInk(kind, appColors())
    Text(
        text = label,
        style = AppType.Caption,
        color = ink.action,
        fontWeight = FontWeight.Bold,
        modifier = modifier.clickable(onClick = onClick),
    )
}
