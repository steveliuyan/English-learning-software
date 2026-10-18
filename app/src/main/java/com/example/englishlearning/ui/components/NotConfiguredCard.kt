package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 批 1 Task 11：AI 未配置卡（spec §3「一句现状 + 一个『去配置 ›』出口，**绝不再做只给文字的死屏**」）。
 *
 * 收的是现网 4 个消费屏各自的私有名（`SentenceAnalysisScreen`/`WordAiQaScreen`/`PhotoTranslationScreen`/
 * `ImageStudioScreen` 里各有一份私有 `NotConfiguredCard`，其中 `SentenceAnalysisScreen.kt` 那份与本组件同名，
 * 名字遮蔽只在该文件内成立，批 3 逐屏接线时删私有份）。四份的共同缺陷是「只给文字、不给出口」，
 * 于是死屏禁令在这里落成**签名形状**：[onConfigure] 必填、不可空、无默认值——
 * 由 `NotConfiguredCardContractTest.the_configure_exit_is_mandatory_and_is_the_only_touch_target` 钉住
 * （connected 用例只能证明「这一处渲染出了出口」，证明不了「组件不允许不渲染出口」）。
 * 屏侧「重排后必须调用 `NotConfiguredCard(`」那条断言按 brief Step 2 归批 3 收口。
 *
 * 墨色全部来自纯表 [notConfiguredCardInks]（无 `@Composable`），所以每槽取哪枚 token 能在 JVM 侧被钉死：
 * connected 读不到颜色（Task 9 实测口径），这里不指望真机断言墨色。
 */

/**
 * 四枚槽位墨色：现状句、补充说明、出口文案、尾部「›」。
 *
 * 四字段而不是 `Pair`：与 [EntryRowInk] / [NoticeInk] / [DialogButtonInk] 同口径，
 * 具名字段才能让「出口取哪枚」在契约里被点名断言。
 */
data class NotConfiguredCardInk(
    val title: Color,
    val detail: Color,
    val exit: Color,
    val chevron: Color,
)

/**
 * 两套主题 → 四枚槽位墨色的纯函数（[set] 就是浅套或深套那两套之一）。
 *
 * 槽位分配：现状句 `text1`（这张卡的主信息）、补充说明 `text2`（次要，与入口行副标题同档）、
 * 出口文案 `text1` + 组件侧 `FontWeight.Bold`、尾部「›」`text2`（与 [EntryRow] 的 chevron 同槽位同 token）。
 *
 * **出口墨取 `text1`、不取稿面强调色**（R-10.1b 同一条裁决）：稿 `.act` 那类「链接用域色 deep」的写法
 * 压浅套面板底不足正文 4.5（`InlineNotice` 的动作墨也是同一处置，见 [NoticeInk.action] 的 KDoc），
 * 批 1 Task 1 的低对比禁令会当场挡下；`text1` 两套都富余，「这是一枚链接」的读感改由字重承担。
 * 依据本身也在承重：`NotConfiguredCardContractTest` 把被拒候选（浅套 AiSpeech deep 压浅套 surface）
 * 当场判不合格，哪天它达标了那条会红、提示裁决重议。比值不抄在这里（抄了会随 token 取值漂移）。
 *
 * chevron 是装饰档，按非文本 3:1 **加余量**判（≥3.1，卡在边界不算合格——Task 1 裁决 #1 的口径）。
 */
fun notConfiguredCardInks(set: AppColorSet): NotConfiguredCardInk = NotConfiguredCardInk(
    title = set.text1,
    detail = set.text2,
    exit = set.text1,
    chevron = set.text2,
)

/**
 * 「一句现状」的措辞配方：点名功能 + 说清缺的是 AI 服务，**只有一句**（不换行、不塞第二件事）。
 *
 * 为什么点名功能：四个消费屏同屏出现时，「暂不可用」这种含糊说法让用户不知道是哪项能力没配。
 * 为什么点名 AI 服务：缺的就是这一件事，出口「去配置」指的也正是它，措辞与出口必须同一对象。
 * 措辞是契约面：改字要连 `NotConfiguredCardContractTest.status_line_names_the_feature_and_stays_one_sentence`
 * 一起改（那条钉了逐字等值）。
 */
fun notConfiguredStatusLine(featureLabel: String): String = "「$featureLabel」还没有可用的 AI 服务"

/**
 * AI 未配置卡。
 *
 * 几何：卡 = `surface` 底 + 1dp `outlineSoft` 描边 + 装配档圆角 + 14dp 内边距（与 [InlineNotice] 同一
 * 14dp 口径，稿 `.banner` 家族）；圆角走 `MaterialTheme.shapes.medium`（装配点 = 稿 `--r-card` 18dp），
 * **不读 `AppShape.Card`**（20dp 旧档，新组件禁读）。
 *
 * 行内徽标走 [InlineIconBadge]（30dp tint 底 + 17dp 单层描边字形），**不手搓第二份容器**：
 * 手搓会撞 Task 12 的组件份数棘轮。图标取 `AppIcon.Spark`、域取 [AppDomain.AiSpeech]——
 * 缺的就是 AI 服务，与四个消费屏的域归属一致；徽标是装饰层（`AppGlyph` 内部已 `contentDescription = null`），
 * 语义由现状句承载。
 *
 * 文案**不给 `maxLines`**：现状句被裁掉就是丢信息（spec §3 的 fontScale 2.0 口径），
 * 与 [EntryRow] 的标题/副标题相反（那边是提示性内容、允许省略号）。
 *
 * 出口是这张卡**唯一**的触控目标：整行 `clickable(role = Role.Button)`，`heightIn(min = 48.dp)` 只落在
 * 出口这一枚（卡本体是内容不是触控目标，给它钉 48dp 等于造一条稿里没有的水线）。
 * 48dp 触控面由「链上存在 `heightIn(min = 48.dp)`」+「`clickable` 排在任何 `padding` 之前」两条共同交付；
 * `heightIn` 与 `clickable` 的先后**不影响**可点面尺寸（`DialogButton` 是 heightIn 在前、这里是 heightIn
 * 在后，两处 connected 都实测 ≥48dp）。`clickable` 紧跟 `fillMaxWidth()` 这一形状仍由源码契约正则钉住，
 * 但它是**形状锚**（保证 `fillMaxWidth` 不被漏掉、可点面是整宽），不是高度机制；`EntryRow` 真机量到 40dp
 * 的「假面」成因是 `padding` 排在 `clickable` 之前（见 `EntryRow.kt:70-75`），与本处顺序无关。
 * `testTag("not_configured_goto")` 与可点语义并到
 * 同一枚节点（`mergeDescendants = true`），否则标签在父、click 在子，真机上
 * `onNodeWithTag("not_configured_goto").performClick()` 会打在没有 click action 的节点上。
 *
 * [detail] 是可选的第二行（例如「去『设置 · AI』添加一套并设为默认」这类补路指引），
 * 传 `null` 就只剩一句现状——**但出口始终在**，这正是死屏禁令的落点。
 *
 * [onConfigure] 是必填回调：可空或带默认值都等于允许「渲染一张没有出口的卡」，
 * 那正是 spec §3 点名的死屏，故源码契约把两种后门形状都钉成 0 处。
 */
@Composable
fun NotConfiguredCard(
    featureLabel: String,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    // 四枚墨一律从纯表取（源码契约钉「本文件恰一处 notConfiguredCardInks(appColors())」）；
    // 卡面底与描边读同一套 token，所以再取一次当前套——纯表只承载四枚槽位墨，不代管容器色。
    val inks = notConfiguredCardInks(appColors())
    val set = appColors()
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(set.surface)
            .border(1.dp, set.outlineSoft, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineIconBadge(icon = AppIcon.Spark, domain = AppDomain.AiSpeech)
            Text(
                text = notConfiguredStatusLine(featureLabel),
                style = AppType.Body,
                color = inks.title,
                modifier = Modifier.weight(1f),
            )
        }
        if (detail != null) {
            Text(text = detail, style = AppType.Footnote, color = inks.detail)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onConfigure)
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) { testTag = "not_configured_goto" },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "去配置",
                style = AppType.Body,
                color = inks.exit,
                fontWeight = FontWeight.Bold,
            )
            AppGlyph(icon = AppIcon.ChevronRight, tint = inks.chevron, size = 16.dp, layer = AppGlyphLayer.StrokeOnly)
        }
    }
}
