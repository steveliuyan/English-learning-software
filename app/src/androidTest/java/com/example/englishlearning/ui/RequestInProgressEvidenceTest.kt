package com.example.englishlearning.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 批 C-4 进行中面的 **connected 取证 + 读屏顺序判据**（决策
 * `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` §4 末「观感与读屏顺序归截图册」那半条）。
 *
 * 为什么这条走设备而不是源码正则：源码闸能钉「臂调了组件、给了 onCancel、文案含 AI 服务」，
 * 钉不出**渲染出来是不是三件齐**（活动指示画没画、取消键够不够高、那句取消陈述有没有被挤出卡面），
 * 也钉不出读屏从上到下的顺序。那两件只能量节点。
 *
 * 钉的破坏：
 *  - **三件齐**：状态句、取消键、取消陈述都在树上，且纵向顺序是「先说在做什么 → 再给出路 → 最后说清
 *    出路是什么」。顺序反了（陈述压在最上面）读屏会先念一句承诺再问你在干嘛。
 *  - **截图**：整屏 PNG 落 `getExternalFilesDir(null)/c4-in-progress-evidence/<name>.png`，**不入库**，
 *    由会话侧 pull 到 `.git/qa-scratch/` 并登记进 `docs/verification/ui-redesign-2026-10.md`。
 *
 * 怪癖口径沿用本包先例：每个方法只调一次 `setContent`（同方法内第二次真机必崩），两屏就拆两个方法。
 * 取 `boundsInRoot` 顶边而不是语义树索引：版面纵向顺序就是 TalkBack 在本仓既有走查里遍历的顺序，
 * 而语义树索引在合并节点上没有稳定可读的形状。
 */
@RunWith(AndroidJUnit4::class)
class RequestInProgressEvidenceTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun word_qa_waiting_face_shows_the_three_parts_in_reading_order() {
        composeRule.setContent {
            WordAiQaScreen(
                lemma = "apple",
                state = WordAiQaUiState.Asking,
                onAsk = {}, onCancelAsk = {}, onConfirmOutbound = {}, onSaveNote = {},
                onBack = {}, onOpenAiSettings = {},
            )
        }

        assertReadingOrder(
            label = "AI 问词",
            status = composeRule.onNodeWithTag("word_qa_asking").also {
                composeRule.onNodeWithText("正在向你配置的 AI 服务问这个词").assertExists()
            },
            cancel = composeRule.onNodeWithTag("word_qa_cancel"),
            statement = composeRule.onNodeWithText("取消只是不再等待这次结果", substring = true),
        )
        saveScreenshot("word-qa-asking", "word_qa_screen")
    }

    @Test
    fun sentence_waiting_face_shows_the_three_parts_in_reading_order() {
        composeRule.setContent {
            SentenceAnalysisScreen(
                state = SentenceAnalysisUiState.Analyzing,
                onAnalyze = {}, onCancelAnalysis = {}, onConfirmOutbound = {}, onBack = {},
                onOpenAiSettings = {}, speakMessage = null, onSpeak = {},
            )
        }

        assertReadingOrder(
            label = "长难句分析",
            status = composeRule.onNodeWithTag("sentence_analysis_analyzing").also {
                composeRule.onNodeWithText("正在向你配置的 AI 服务分析这句英文").assertExists()
            },
            cancel = composeRule.onNodeWithTag("sentence_analysis_cancel"),
            statement = composeRule.onNodeWithText("取消只是不再等待这次结果", substring = true),
        )
        saveScreenshot("sentence-analysis-analyzing", "sentence_analysis_screen")
    }

    /** 三件齐 + 自上而下：状态句 < 取消键 < 取消陈述。 */
    private fun assertReadingOrder(
        label: String,
        status: SemanticsNodeInteraction,
        cancel: SemanticsNodeInteraction,
        statement: SemanticsNodeInteraction,
    ) {
        status.assertExists()
        cancel.assertExists()
        statement.assertExists()
        val statusTop: Float = status.fetchSemanticsNode().boundsInRoot.top
        val cancelTop: Float = cancel.fetchSemanticsNode().boundsInRoot.top
        val statementTop: Float = statement.fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "$label：状态句($statusTop) 必须在取消键($cancelTop) 之上——先说在做什么，再给出路",
            statusTop < cancelTop,
        )
        assertTrue(
            "$label：取消陈述($statementTop) 必须在取消键($cancelTop) 之下——那句是对出路的补充，不是开场白",
            cancelTop < statementTop,
        )
    }

    private fun saveScreenshot(name: String, rootTag: String) {
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "c4-in-progress-evidence",
        )
        output.mkdirs()
        val bitmap = composeRule.onNodeWithTag(rootTag).captureToImage().asAndroidBitmap()
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
