package com.example.englishlearning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 批 1 Task 11：出站确认对话框（spec §3「首次调用第三方 Endpoint 前展示域名并确认」）。
 *
 * 收的是现网各屏私有的出站确认（长难句 `SentenceAnalysisScreen`、拍照翻译 `PhotoTranslationScreen`、
 * 图片工坊 `ImageStudioScreen`、词书 AI 问答 `WordAiQaScreen`、阅读解锁 `ReadingAccessScreen`；
 * 份数会随批 3 收口归零，故不写死——重算：`grep -rln '密钥将发送给' app/src/main/java/com/example/englishlearning/ui | grep -v components/`）。
 * 这些站点的现状是「域名 + 密钥会一起外发 + 发的是文本还是图片」三件事各屏各写，
 * 措辞漂一条没人发现，所以正文收成纯函数 [outboundConfirmBody]、
 * 「图片每次都问 / 文本按域名记忆」收成纯函数 [mustAskEveryTime]，
 * 两条都由 `ConfirmDestructiveWordingTest` 逐格断言。
 *
 * 面板与按钮复用 [DialogPanel] / [DialogButton]（都在 `ConfirmDestructiveDialog.kt`）：
 * 两枚对话框不得各长一套卡面配方，确认档墨色也不得另立第二套取墨口径。
 */

/**
 * 出站载荷的两档。只有两档：现网 5 处出站确认发的就是文本或图片，音频一类没有出现，
 * 加档要连 `mustAskEveryTime` 的真值表与措辞表一起改（`ConfirmDestructiveWordingTest` 会当场点名）。
 */
enum class PayloadKind { Text, Image }

/**
 * AGENTS.md 硬要求的纯函数化：**图片每次都问，文本按域名记忆**。
 *
 * 写成纯函数是为了让「记住这个域名」这个便利功能在图片上永远开不了口子：
 * `Image` 那一格与 [rememberedForHost] 无关，恒为 `true`。这是隐私安全面，UI 轮只能加强不能软化——
 * 四格真值表由 `ConfirmDestructiveWordingTest.image_payload_always_asks_even_when_the_host_is_remembered`
 * 全钉（重点是「Image + 已记住域名」那一格）。
 *
 * [rememberedForHost] 由调用方给（该域名是否已被用户确认过）：本组件不知道记忆存在哪里，
 * 也不该知道——记忆是 ViewModel/持久化的事，规则是纯函数的事。
 */
fun mustAskEveryTime(kind: PayloadKind, rememberedForHost: Boolean): Boolean =
    kind == PayloadKind.Image || !rememberedForHost

/**
 * 出站确认正文的**唯一**措辞配方：点名域名 + 说明密钥会一起外发 + 说清发的是文本还是图片。
 *
 * 三件事各有出处：
 *  - 域名**逐字**出现在文案里（不是「该服务」这种含糊指代）——AGENTS.md 要求「首次调用第三方 Endpoint
 *    前展示域名」，展示别的域名等于没展示；
 *  - 密钥必须说，否则用户以为只发了内容；
 *  - 文本/图片两档措辞必须可区分（否则 [OutboundConfirmDialog] 的 `payloadKind` 形参就是装饰），
 *    图片档还得多说一句「每次都要确认」——它当场解释了这次确认为什么不能一劳永逸，
 *    与 [mustAskEveryTime] 的 `Image` 格同口径。
 */
fun outboundConfirmBody(endpointHost: String, payloadKind: PayloadKind): String = when (payloadKind) {
    PayloadKind.Text -> "你的文本内容与该服务的密钥将发送给 $endpointHost，并可能产生费用。"
    PayloadKind.Image ->
        "图片内容与该服务的密钥将发送给 $endpointHost，并可能产生费用。图片发送每次都需要你确认。"
}

/**
 * 出站确认对话框。
 *
 * **显隐由调用方按 [mustAskEveryTime] 决定**：本组件不接 `rememberedForHost`，
 * 因为「要不要弹」是调用方的业务判断（记忆态在 ViewModel 里），组件只负责「弹出来时必须说全三件事」。
 * 与 [ConfirmDestructiveDialog] 的 `state` 形参不同口径是刻意的：那边是纯显隐开关，这边的开关本身带规则。
 *
 * 确认按钮走 [DialogButtonRole.Confirm]（`onMint` 容器 + `background` 前景 = 装配点的 `primary`/`onPrimary`），
 * **不走** [DialogButtonRole.DestructiveConfirm]：出站确认是「同意一次外发」，不是「毁掉数据」，
 * 红色档留给后者，混用会让用户在两种确认之间读不出轻重。
 *
 * 两枚按钮的标签是 `outbound_confirm_confirm` / `outbound_confirm_dismiss`，正文另带
 * `outbound_confirm_body`（批 2/3 各屏与 Task 12 的份数棘轮按这三个 tag 定位）。
 * 整枚对话框恰两个可点节点，正文与面板都不是触控目标。
 *
 * 现网各屏的私有标签（`sentence_analysis_confirm_outbound` 一族）在批 3 逐屏接线时由调用方经
 * `Modifier.semantics` 之外另行保留与否——本组件不私设屏级 tag，屏级 tag 属屏。
 */
@Composable
fun OutboundConfirmDialog(
    endpointHost: String,
    payloadKind: PayloadKind,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val set = appColors()
    DialogPanel(onDismissRequest = onDismiss) {
        Text(text = "确认发送请求", style = AppType.Title, color = set.text1)
        Text(
            text = outboundConfirmBody(endpointHost, payloadKind),
            style = AppType.Footnote,
            color = set.text2,
            modifier = Modifier
                .padding(top = 12.dp)
                .semantics { testTag = "outbound_confirm_body" },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DialogButton(
                role = DialogButtonRole.Dismiss,
                label = "取消",
                onClick = onDismiss,
                tag = "outbound_confirm_dismiss",
                modifier = Modifier.weight(1f),
            )
            DialogButton(
                role = DialogButtonRole.Confirm,
                label = "确认发送",
                onClick = onConfirm,
                tag = "outbound_confirm_confirm",
                modifier = Modifier.weight(1f),
            )
        }
    }
}
