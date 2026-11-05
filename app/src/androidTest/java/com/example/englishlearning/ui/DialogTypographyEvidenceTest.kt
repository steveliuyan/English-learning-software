package com.example.englishlearning.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.ui.components.ConfirmDestructiveDialog
import com.example.englishlearning.ui.components.DIALOG_PANEL_TAG
import com.example.englishlearning.ui.components.OutboundConfirmDialog
import com.example.englishlearning.ui.components.destructiveDialogBody
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 弹窗排印统一批（决策 `docs/decisions/2026-11-05-dialog-typography-and-wordbook-token-cleanup.md` §1–3）
 * 的 **connected 取证 + 档位护栏**。
 *
 * 为什么这条走设备而不是源码正则：本批判的是「用户看得见、点得到」的口径（标题档 / 正文档 / 按钮档
 * 三档统一 + 面板高度上限与滚动出路）。源码里 `AppType.X` 出现几次，证不了渲染面真按那一档画。
 * 决策文档 §6 因此把这一批的取证定为 **connected + 截图**，不再新增源码闸。
 *
 * 钉的破坏：
 *  - **三档比**：标题字号 / 正文字号 == `17/15`，按钮标签 / 正文字号 == `1`，字重 650 / Normal / 600。
 *    任一档退回 Material 默认字，或正文被改回 13sp 的 `Footnote`，比值与字重当场不对。
 *    **取比值而不是绝对 sp**：`textLayout().layoutInput.style` 在设备侧可能已是 density × fontScale
 *    解析后的 px 口径，绝对值在 `font_scale = 2.0` 那轮不可依赖，比值两种口径都成立。
 *  - **面板出路**：内容超高时 [DIALOG_PANEL_TAG] 那枚面板高度 ≤ 560dp、带竖向滚动动作，
 *    滑到底后出口按钮仍可见（正面护栏「模态窗口里唯一的出路消失」）。
 *  - **共享件真的被复用**：`CardDetailScreen` 的管理笔记框里必须出现同一枚面板锚——私搓一份卡面时锚会消失。
 *
 * 截图（供目视）落 `targetContext.getExternalFilesDir(null)/dialog-typography-evidence/<name>.png`，
 * **不入库**；`font_scale` 1.0 与 2.0 各跑一遍本类，两轮 PNG 分别 pull 到 `.git/qa-scratch/`。
 *
 * 怪癖口径沿用本包先例：**每个方法只调一次 `setContent`**（同方法内第二次真机必崩），
 * 两个版面就拆两个方法（`destructive-short` / `destructive-tall`）。
 */
@RunWith(AndroidJUnit4::class)
class DialogTypographyEvidenceTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * 破坏性确认框（短内容档）：三档齐备、正文各行在树上、PNG `destructive-short`。
     */
    @Test
    fun destructive_dialog_uses_the_three_tier_split() {
        val cascade = listOf("会同时删除 12 条复习记录", "会删除 3 张卡片配图")
        val keep = listOf("其他词书不受影响")
        composeRule.setContent {
            MintGlassTheme {
                ConfirmDestructiveDialog(
                    state = true,
                    title = "删除词书《核心 1000》",
                    cascadeLines = cascade,
                    keepLines = keep,
                    irreversible = true,
                    confirmLabel = "删除词书",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        val body = destructiveDialogBody(cascade, keep, irreversible = true)
        body.forEach { line -> composeRule.onNodeWithText(line).assertExists() }
        assertThreeTiers(
            title = composeRule.onNodeWithText("删除词书《核心 1000》"),
            body = composeRule.onNodeWithText(body.first()),
            buttonLabel = composeRule.onNodeWithText("删除词书", useUnmergedTree = true),
        )
        saveScreenshot("destructive-short")
    }

    /**
     * 破坏性确认框（长内容档）：面板 ≤ 560dp + 有滚动动作 + 滑到底出口按钮可见 + PNG `destructive-tall`。
     *
     * 立项理由即这条：原先面板无上限，级联行数由调用方逐字给（各屏接自己的确认态后行数不可控），
     * 设备字号放大后底部两枚按钮被顶出窗口且滚不回来——那是模态里唯一出路的消失。
     */
    @Test
    fun tall_dialog_content_is_capped_and_scrollable() {
        val many = (1..24).map { "会删除第 $it 条学习记录" }
        composeRule.setContent {
            MintGlassTheme {
                ConfirmDestructiveDialog(
                    state = true,
                    title = "清空学习记录",
                    cascadeLines = many,
                    keepLines = listOf("词书与收藏不受影响"),
                    irreversible = true,
                    confirmLabel = "清空",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        val panel = composeRule.onNodeWithTag(DIALOG_PANEL_TAG)
        // 版本口径：这一版 `androidx.compose.ui.test` 只有 `assertHeightIsAtLeast`，没有 `AtMost` 一档，
        // 故按界取高度自证（+0.5dp 只吸收浮点与像素取整，放不下一档字号的漂移）。
        val panelHeight = panel.getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue(
            "内容超高时面板必须被压在 560dp 上限内（实际 $panelHeight）：否则设备字号放大后出口按钮被顶出窗口",
            panelHeight <= 560.5.dp,
        )
        panel.assert(hasScrollAction()) { "面板必须带竖向滚动动作：上限把出口挤出视口时，滚动是唯一出路" }
        // 滚到出口可见为止（上限内的内容长度随字体档变，2.0 档下一屏装不下 24 行级联，需要多推几次）。
        // 出口真的滚不到时，循环上限会先把「推了 8 次仍不可见」报成红，而不是静默通过。
        var swipes = 0
        while (swipes < 8 && !confirmExitIsDisplayed()) {
            panel.performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            swipes++
        }
        composeRule.onNodeWithTag("confirm_destructive_confirm").assertIsDisplayed()
        saveScreenshot("destructive-tall")
    }

    /**
     * 出口按钮当前是否在窗口内可见。
     *
     * 用 `assertIsDisplayed` 的成功/失败而不是 `SemanticsNode.isVisible`：后者只看节点自身边界与图层，
     * **不算滚动容器的裁剪**——被上限裁掉的按钮在它眼里照样「visible」，那条护栏会变成假绿。
     */
    private fun confirmExitIsDisplayed(): Boolean =
        runCatching { composeRule.onNodeWithTag("confirm_destructive_confirm").assertIsDisplayed() }.isSuccess

    /**
     * 出站确认框：标题「确认发送请求」+ 正文（含屏侧追加的 note）+ 出口按钮，同一套三档比 + PNG `outbound`。
     */
    @Test
    fun outbound_dialog_uses_the_three_tier_split() {
        composeRule.setContent {
            MintGlassTheme {
                OutboundConfirmDialog(
                    endpointHost = "api.example.com",
                    payloadKind = AiPayloadKind.Text,
                    note = "本次只发送这一段文本。",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("outbound_confirm_body").assertIsDisplayed()
        assertThreeTiers(
            title = composeRule.onNodeWithText("确认发送请求"),
            body = composeRule.onNodeWithTag("outbound_confirm_body"),
            buttonLabel = composeRule.onNodeWithText("确认发送", useUnmergedTree = true),
        )
        saveScreenshot("outbound")
    }

    /**
     * 词卡「管理笔记」框（本批从私搓 `AlertDialog` 改成共享件）：面板锚在树上 + 三档比 + PNG `notes`。
     *
     * 走真屏路径（点 `card_detail_notes_manage`）而不是直接组合私有 composable——
     * 屏外组合证不了「这一屏真接了共享件」。正文取样用**空笔记**那一句：
     * 有笔记时同一串文案在屏上展示栏也出现一次，`onNodeWithText` 会撞多节点。
     */
    @Test
    fun card_detail_notes_dialog_reuses_the_shared_panel() {
        composeRule.setContent {
            MintGlassTheme {
                CardDetailScreen(
                    card = noteEvidenceCard(),
                    onBack = {},
                    notes = emptyList(),
                    onSavePersonalNote = {},
                )
            }
        }
        composeRule.onNodeWithTag("card_detail_notes_manage").performScrollTo().performClick()
        val panel = composeRule.onNodeWithTag(DIALOG_PANEL_TAG)
        panel.assertIsDisplayed()
        assertThreeTiers(
            title = composeRule.onNodeWithText("管理笔记"),
            body = composeRule.onNodeWithTag("card_detail_notes_empty"),
            buttonLabel = composeRule.onNodeWithText("关闭", useUnmergedTree = true),
        )
        saveScreenshot("notes")
    }

    // ---------- 取样与落盘 ----------

    /**
     * 三档比：标题 = 正文 × 17/15，按钮标签 = 正文 × 1（同字号、靠字重分档），字重 650 / Normal / 600。
     *
     * ±0.02 / ±0.01 只吸收 px↔sp 往返的浮点噪声，不放档位：标题若退回 `Headline`（24/15 = 1.6）
     * 或正文若退回 `Footnote`（17/13 ≈ 1.3077）都越界。
     */
    private fun assertThreeTiers(
        title: SemanticsNodeInteraction,
        body: SemanticsNodeInteraction,
        buttonLabel: SemanticsNodeInteraction,
    ) {
        val titleStyle = title.textStyle()
        val bodyStyle = body.textStyle()
        val buttonStyle = buttonLabel.textStyle()
        val titleRatio = (titleStyle.fontSize.value / bodyStyle.fontSize.value).toDouble()
        val buttonRatio = (buttonStyle.fontSize.value / bodyStyle.fontSize.value).toDouble()
        assertEquals(
            "标题必须比正文大一档（Title 17 / Body 15），实际比值 $titleRatio",
            17.0 / 15.0,
            titleRatio,
            0.02,
        )
        assertEquals(
            "按钮标签与正文同字号（ButtonLabel 15 / Body 15），靠字重分档；实际比值 $buttonRatio",
            1.0,
            buttonRatio,
            0.01,
        )
        assertEquals("标题档字重 650", FontWeight(650), titleStyle.fontWeight)
        assertEquals("正文档字重 Normal", FontWeight.Normal, bodyStyle.fontWeight)
        assertEquals("按钮档字重 600", FontWeight(600), buttonStyle.fontWeight)
    }

    /**
     * 从语义树取文本节点的字形样式。
     * 读法先例：`WordBookWordsScreenTest.activeLetterUsesADifferentColor`（经 `GetTextLayoutResult` 拿
     * `layoutInput.style`，能读到字号与字重）。取样节点必须是**字形节点本身**（合并后的可点容器没有这条动作），
     * 所以按钮一律用 `useUnmergedTree = true` 取内层 `Text`。
     */
    private fun SemanticsNodeInteraction.textStyle(): TextStyle {
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().layoutInput.style
    }

    /** 面板整层位图落盘（`captureToImage()` 只截该节点自身图层；面板底是不透明 `surface`，故 PNG 有底）。 */
    private fun saveScreenshot(name: String) {
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "dialog-typography-evidence",
        )
        output.mkdirs()
        val bitmap = composeRule.onNodeWithTag(DIALOG_PANEL_TAG).captureToImage().asAndroidBitmap()
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun noteEvidenceCard() = WordCard(
        cardId = "placeholder:cet4:ability",
        wordBookId = "cet4",
        lemma = "ability",
        ipa = "əˈbɪləti",
        partOfSpeech = "n.",
        meaningZh = "能力；才能",
        example = "She has the ability to explain complex ideas simply.",
        inflections = emptyList(),
        imagePath = null,
        senses = emptyList(),
        exampleZh = null,
        derived = emptyList(),
        phrases = emptyList(),
        synonyms = emptyList(),
    )
}
