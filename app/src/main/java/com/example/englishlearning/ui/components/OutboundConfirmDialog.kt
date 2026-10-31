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
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 批 1 Task 11：出站确认对话框（spec §3「首次调用第三方 Endpoint 前展示域名并确认」）。
 *
 * U-1（决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §1）之后，这一枚是**全仓唯一**的
 * 出站确认对话框：阅读首页 `ReadingAccessScreen` 与四屏
 * （长难句 `SentenceAnalysisScreen`、拍照翻译 `PhotoTranslationScreen`、图片工坊 `ImageStudioScreen`、
 * 词书 AI 问答 `WordAiQaScreen`）都调它。此前那四屏各画一份同名私写对话框、标题逐字相同而正文各写各的
 * （长难句那一份就漏了「并可能产生费用」）——份数不再靠人肉对齐，改由 `OutboundDialogFamilyTest` 的
 * 跨文件计数钉住：标题「确认发送请求」与正文配方「与该服务的密钥将发送给」全 main 只许出现在本文件。
 *
 * 三件事（域名 + 密钥外发 + 发的是文本还是图片）由 [outboundConfirmBody] 一处生成；
 * 屏侧**只许追加一句自己的业务说明**（[note]，例如拍照翻译的「图片不会发送」、图片工坊的按张计费），
 * 不许改写配方——那是措辞漂移唯一的入口。
 *
 * 屏级 tag 由各屏经 [bodyTestTag] / [confirmTestTag] / [dismissTestTag] 传入：各屏版式契约与 connected
 * 都点名它们（`ReadingHomeLayoutContractTest` 的教训就是「接线时锚不许静默消失」），
 * 缺省仍落在共享的 `outbound_confirm_*` 三个 tag 上。
 *
 * 面板与按钮复用 [DialogPanel] / [DialogButton]（都在 `ConfirmDestructiveDialog.kt`）：
 * 两枚对话框不得各长一套卡面配方，确认档墨色也不得另立第二套取墨口径。
 */

/**
 * 出站确认正文的**唯一**措辞配方：点名域名 + 说明密钥会一起外发 + 说清发的是文本还是图片。
 *
 * 载荷档位直接用域层 [AiPayloadKind]（U-3）：UI 侧原来那枚 `PayloadKind` 与零调用的
 * `mustAskEveryTime` 是第二份权威——「图片每次都问 / 文本按域名记忆」的真值只由
 * `ai/AiOutboundConfirmation.kt` 的 `requiredConfirmation` 决定，组件再算一遍就等于允许两处不一致。
 *
 * 三件事各有出处：
 *  - 域名**逐字**出现在文案里（不是「该服务」这种含糊指代）——AGENTS.md 要求「首次调用第三方 Endpoint
 *    前展示域名」，展示别的域名等于没展示；
 *  - 密钥必须说，否则用户以为只发了内容；
 *  - 文本/图片两档措辞必须可区分（否则 [OutboundConfirmDialog] 的 `payloadKind` 形参就是装饰），
 *    图片档还得多说一句「每次都要确认」——它当场解释了这次确认为什么不能一劳永逸，
 *    与域层 `requiredConfirmation` 的 `Image` 格同口径。
 */
fun outboundConfirmBody(endpointHost: String, payloadKind: AiPayloadKind): String = when (payloadKind) {
    AiPayloadKind.Text -> "你的文本内容与该服务的密钥将发送给 $endpointHost，并可能产生费用。"
    AiPayloadKind.Image ->
        "图片内容与该服务的密钥将发送给 $endpointHost，并可能产生费用。图片发送每次都需要你确认。"
}

/**
 * 出站确认对话框。
 *
 * **显隐由调用方决定**：本组件不接记忆态——「要不要弹」是域层 `requiredConfirmation` 与 ViewModel 的判断，
 * 组件只负责「弹出来时必须说全三件事」。与 [ConfirmDestructiveDialog] 的 `state` 形参不同口径是刻意的：
 * 那边是纯显隐开关，这边的开关本身带规则。
 *
 * 确认按钮走 [DialogButtonRole.Confirm]（`onMint` 容器 + `background` 前景 = 装配点的 `primary`/`onPrimary`），
 * **不走** [DialogButtonRole.DestructiveConfirm]：出站确认是「同意一次外发」，不是「毁掉数据」，
 * 红色档留给后者，混用会让用户在两种确认之间读不出轻重。
 *
 * 整枚对话框恰两个可点节点，正文与面板都不是触控目标；正文与配方、追加说明合并成**一个** Text 节点，
 * 因为各屏的 `*_outbound_confirmation_text` 锚在 connected 里按文本断言（`assertTextContains`），
 * 拆成两段会让点名的那一半找不到。
 */
@Composable
fun OutboundConfirmDialog(
    endpointHost: String,
    payloadKind: AiPayloadKind,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
    bodyTestTag: String? = null,
    confirmTestTag: String? = null,
    dismissTestTag: String? = null,
) {
    val set = appColors()
    val body = outboundConfirmBody(endpointHost, payloadKind) + if (note != null) "\n$note" else ""
    DialogPanel(onDismissRequest = onDismiss) {
        Text(text = "确认发送请求", style = AppType.Title, color = set.text1)
        Text(
            text = body,
            style = AppType.Footnote,
            color = set.text2,
            modifier = Modifier
                .padding(top = 12.dp)
                .semantics { testTag = bodyTestTag ?: "outbound_confirm_body" },
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
                tag = dismissTestTag ?: "outbound_confirm_dismiss",
                modifier = Modifier.weight(1f),
            )
            DialogButton(
                role = DialogButtonRole.Confirm,
                label = "确认发送",
                onClick = onConfirm,
                tag = confirmTestTag ?: "outbound_confirm_confirm",
                modifier = Modifier.weight(1f),
            )
        }
    }
}
