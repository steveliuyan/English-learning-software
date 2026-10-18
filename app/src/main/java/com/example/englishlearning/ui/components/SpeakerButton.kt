package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 批 1 Task 12：喇叭药片（spec §3「喇叭药片放大、暂停/继续/停止文案一处定义」；
 * F5-03 移交账「喇叭药片放大 —— 触控不足」的组件侧还账，落地在批 2 的词卡屏与详情页）。
 *
 * 收的是现网两套**同构几何**（`WordCardScreen.kt:410-425` 的 `WordCardSpeakerButton` 与
 * `CardDetailScreen.kt` 的 `AccentSpeakerButton`，两处注释互认「同款」）：
 * 20dp 自绘 Canvas 喇叭 + 6dp padding ≈ 32dp 见方，读屏与粗手指都够不到 48dp 下限
 * （connected RED 实测 32.38dp 高，现场记在 `WordCardScreen.kt` 的 `minimumInteractiveComponentSize()` 注释里）。
 * 本组件把两套的文案与几何收成一处：文案在 [speakerContentDescription]、取图在 [speakerGlyph]、
 * 墨色在 [speakerButtonInk]，都是无 `@Composable` 的纯函数，所以三态取值能在 JVM 侧被钉死。
 *
 * 「停止」不在本组件的三态里：spec §3 那行写的是「暂停/继续/停止文案一处定义」，而计划 Task 12 的
 * Interfaces 逐字钉的是 `enum class SpeakerState { Idle, Playing, Paused }` 三态——
 * 停止是**播控组**（词卡屏流程）的职责，批 2 落地时按同一口径另加，不在药片本体上预埋第四态。
 * 三枚字形 `Speaker`/`Pause`/`Play` 都已在 `AppIcons.kt` 的 33 枚清单内，本文件不新增字形
 * （新增要重走图标决策 + 许可台账，Task 7 已钉死）。
 */

/** 播控三态：待播 / 正在播 / 已暂停。文案与取图都按这三态分派，别在调用点另写一套 when。 */
enum class SpeakerState { Idle, Playing, Paused }

/**
 * 三态朗读文案的**唯一**定义处（spec §3「文案一处定义」的落点）。
 *
 * 措辞按「点下去会发生什么」写，不按当前状态写：`Idle` → 播放发音、`Playing` → 暂停发音、
 * `Paused` → 继续发音。三句互不相同且都含「发音」，TalkBack 才分得出三态
 * （`WordRowSpeakerContractTest.each_playback_state_has_one_distinct_readout` 钉住这两条）。
 *
 * 这枚字符串**同时**是药片上的可见文字（见 [SpeakerButton]）：可见文字与朗读文案同源，
 * 就不会长出「屏上写一套、读屏念另一套」的第二份真值——那正是 spec §3 点名的重复源。
 * 改字要连同上面那条用例一起改。
 */
fun speakerContentDescription(state: SpeakerState): String = when (state) {
    SpeakerState.Idle -> "播放发音"
    SpeakerState.Playing -> "暂停发音"
    SpeakerState.Paused -> "继续发音"
}

/**
 * 三态取图：待播 = 喇叭（这枚药片的身份）、正在播 = 暂停（点下去的动作）、已暂停 = 继续（点下去的动作）。
 *
 * 抽成纯函数是为了让「三态各归哪枚字形」能在 JVM 侧被逐态点名（connected 读不到 `ImageVector`）；
 * 字形是装饰层（[AppGlyph] 内部 `contentDescription = null`），语义由 [speakerContentDescription] 承载。
 */
fun speakerGlyph(state: SpeakerState): AppIcon = when (state) {
    SpeakerState.Idle -> AppIcon.Speaker
    SpeakerState.Playing -> AppIcon.Pause
    SpeakerState.Paused -> AppIcon.Play
}

/**
 * 药片三枚墨：字形、文字、底色。
 *
 * 三字段具名（与 [EntryRowInk] / [NotConfiguredCardInk] 同口径），契约才能逐槽点名。
 */
data class SpeakerButtonInk(
    val glyph: Color,
    val label: Color,
    val backdrop: Color,
)

/**
 * 两套主题 → 药片三枚墨的纯函数（[set] 就是浅套或深套那两套之一）。
 *
 * 底色取 `tint`（浅薄荷底档，与现网两套药片同族），前景因此**必须**取 `onMint`：
 * Global Constraint 6 的硬规则是「浅薄荷底（`tint`/`tint-2`/`tint-3`）上禁止白字，前景取 `on-mint`」，
 * 深套的 `onMint` 是浅薄荷（`AppColorDark.onMint`），压深套 `tint` 同样成立——所以两套用同一枚 token，
 * 不需要按主题分支。文字是正文级（≥4.5:1）、字形是非文本级（≥3:1，按 Task 1 裁决 #1 **加余量**判 ≥3.1），
 * 两条都由 `WordRowSpeakerContractTest` 当场按 WCAG 相对亮度复算，并把被拒候选（白字压 `tint`）
 * 当场判不合格——比值不抄在这里（抄了会随 token 取值漂移）。
 */
fun speakerButtonInk(set: AppColorSet): SpeakerButtonInk = SpeakerButtonInk(
    glyph = set.onMint,
    label = set.onMint,
    backdrop = set.tint,
)

/**
 * 喇叭药片。
 *
 * 触控（F5-03 的还账面）：`heightIn(min = 48.dp)` + 横向 16dp 内边距，48dp 由两条**共同**交付——
 * ① 链上存在 `heightIn(min = 48.dp)`（禁靠 padding 凑）；② `clickable` 排在任何 `padding` **之前**。
 * 本文件的顺序是 `heightIn → clip → background → clickable → padding`：
 * `clickable` 在 `background` 之后（水波纹画在底色之上才看得见）、在 `padding` 之前
 * （可点面 = 整枚药片，含那 16dp 内边距）。把 `padding` 挪到 `clickable` 前面，真机上量到的就是
 * 「假面」48dp 以下（`EntryRow` 量到 40dp、旧喇叭药片量到 32.38dp 都是这一族成因）。
 * `heightIn` 与 `clickable` 的先后不影响可点面尺寸（`clickable` 不参与测量）。
 *
 * `testTag` 由调用方经 [modifier] 传入：[modifier] 是链的第一段，与 `clickable` 同在**同一枚节点**上，
 * 所以 `onNodeWithTag(...).performClick()` 打得中；`clickable` 自身会给该节点写
 * `MergeDescendants = true`，药片里的文字与字形因此并进同一枚语义节点，TalkBack 一次念完
 * [speakerContentDescription]，不需要再另设 `contentDescription`（另设会与可见文字重复朗读）。
 *
 * 圆角取 [AppShape.Pill]（spec §2.3 的 `pill 999dp` 档，`percent = 50` 的等价写法）：
 * 药片不是卡片，所以**不读** `MaterialTheme.shapes.medium`，也**禁读** `AppShape.Card`（20dp 旧档，新组件禁读）。
 * 字形 18dp 单层描边（与 [InlineNotice] 的状态字形同档；浅底上实心填充层只会把字形糊成一坨，
 * 口径同 [InlineIconBadge]），文字 [AppType.Body]。
 *
 * [onClick] 收**当前状态**（`(SpeakerState) -> Unit`）：调用方拿到的是「用户点的是哪一枚态」，
 * 不必再在屏侧读一遍状态机，也不会长出「屏侧 when(state) 决定回调」的第二份分派。
 */
@Composable
fun SpeakerButton(
    state: SpeakerState,
    onClick: (SpeakerState) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 三枚墨一律从纯表取（源码契约钉「本文件恰一处 speakerButtonInk(appColors())」）。
    val ink = speakerButtonInk(appColors())
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(AppShape.Pill)
            .background(ink.backdrop)
            .clickable(role = Role.Button, onClick = { onClick(state) })
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(
            icon = speakerGlyph(state),
            tint = ink.glyph,
            size = 18.dp,
            layer = AppGlyphLayer.StrokeOnly,
        )
        Text(
            text = speakerContentDescription(state),
            style = AppType.Body,
            color = ink.label,
        )
    }
}
