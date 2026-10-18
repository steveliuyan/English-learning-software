package com.example.englishlearning.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.OutboundConfirmDialog
import com.example.englishlearning.ui.components.PayloadKind
import com.example.englishlearning.ui.components.outboundConfirmBody
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 批 1 Task 11 的组件级 connected 护栏：[OutboundConfirmDialog]
 * （spec §3「首次调用第三方 Endpoint 前展示域名并确认」）。
 *
 * 分工：「图片每次都问、文本按域名记忆」是**纯函数**规则（[com.example.englishlearning.ui.components.mustAskEveryTime]），
 * 由 `ConfirmDestructiveWordingTest.image_payload_always_asks_even_when_the_host_is_remembered` 四格全钉；
 * 本组件**不接** `rememberedForHost`——「要不要弹」是调用方的业务判断，组件只保证「弹出来必须说全三件事」。
 * 措辞三要素（域名 / 密钥一起外发 / 文本还是图片）同样由那份 JVM 契约钉；本类只钉渲染面：
 * 正文真的把域名画在树上、两档正文可区分、两枚按钮各恰一枚可点节点、48dp 触控下限、回调真的接上。
 *
 * 正文期望值一律由纯函数 [outboundConfirmBody] 现算，不在用例里重抄文案（抄两遍 = 改一次要改两处）。
 * 墨色不在这里断（connected 读不到颜色，Task 9 实测口径），确认档走 `DialogButtonRole.Confirm`
 * 而不是破坏性档这件事由 `ConfirmDestructiveInkTest` 的纯表腿 + 本文件的按钮标签共同承载。
 */
class OutboundConfirmDialogTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * 钉的破坏：域名没被画出来（「展示域名」是 AGENTS.md 硬要求）、密钥外发那句丢了、
     * 按钮丢标签或回调、面板长出第三枚可点源。
     */
    @Test
    fun text_payload_dialog_names_the_host_and_offers_two_exits() {
        var confirmed = false
        var dismissed = false
        val host = "api.example.com"
        composeRule.setContent {
            MintGlassTheme {
                OutboundConfirmDialog(
                    endpointHost = host,
                    payloadKind = PayloadKind.Text,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }
        val body = outboundConfirmBody(host, PayloadKind.Text)
        assertTrue("正文必须点名域名（含糊指代等于没展示）：$body", body.contains(host))
        assertTrue("正文必须说明密钥会一起外发：$body", body.contains("密钥"))
        composeRule.onNodeWithText(body).assertExists()
        composeRule.onNodeWithTag("outbound_confirm_body").assertExists()

        val confirm = composeRule.onNodeWithTag("outbound_confirm_confirm")
        confirm.assertHasClickAction()
        confirm.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithText("确认发送").assertExists()
        composeRule.onNodeWithTag("outbound_confirm_dismiss").assertHasClickAction()

        assertEquals(
            "对话框恰两枚可点目标（确认发送 + 取消）：面板与正文都不是触控目标",
            2,
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size,
        )
        assertEquals(
            "未合并树里也只能有两枚可点节点（口径同 EntryRowTest R-10.7②）",
            2,
            composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
        )

        confirm.performClick()
        assertTrue("确认发送必须回调 onConfirm", confirmed)
        composeRule.onNodeWithTag("outbound_confirm_dismiss").performClick()
        assertTrue("取消必须回调 onDismiss", dismissed)
    }

    /**
     * 钉的破坏：图片档退化成文本档措辞（两档逐字相同 = `payloadKind` 形参成了装饰），
     * 或图片档丢了「每次都要确认」那句——它当场解释了这次确认为什么不能一劳永逸，
     * 与 `mustAskEveryTime` 的 `Image` 格（恒为 true）同口径。
     */
    @Test
    fun image_payload_body_is_distinguishable_and_says_every_time() {
        val host = "vision.example.com"
        composeRule.setContent {
            MintGlassTheme {
                OutboundConfirmDialog(
                    endpointHost = host,
                    payloadKind = PayloadKind.Image,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        val imageBody = outboundConfirmBody(host, PayloadKind.Image)
        val textBody = outboundConfirmBody(host, PayloadKind.Text)
        assertTrue("图片档必须说外发的是图片：$imageBody", imageBody.contains("图片"))
        assertTrue("图片档必须当场告知每次都要确认：$imageBody", imageBody.contains("每次"))
        assertTrue("图片档必须点名域名：$imageBody", imageBody.contains(host))
        assertTrue("两档措辞不得逐字相同（否则 payloadKind 是装饰）：$imageBody", imageBody != textBody)
        composeRule.onNodeWithText(imageBody).assertExists()
        composeRule.onNodeWithText(textBody).assertDoesNotExist()
        assertEquals(
            "图片档同样只有两枚可点目标",
            2,
            composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    /**
     * 钉的破坏：两枚按钮被压回 F5-03 之下。按 R-10.7① 打在**可点节点**上逐枚量
     * （量标签层在标签与 clickable 同节点时是自证）。
     */
    @Test
    fun both_buttons_clear_the_48dp_touch_floor() {
        composeRule.setContent {
            MintGlassTheme {
                OutboundConfirmDialog(
                    endpointHost = "api.example.com",
                    payloadKind = PayloadKind.Text,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        val clickables = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true)
        assertEquals("恰两枚可点按钮", 2, clickables.fetchSemanticsNodes().size)
        clickables[0].assertHeightIsAtLeast(48.dp)
        clickables[1].assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("outbound_confirm_confirm").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("outbound_confirm_dismiss").assertHeightIsAtLeast(48.dp)
    }
}
