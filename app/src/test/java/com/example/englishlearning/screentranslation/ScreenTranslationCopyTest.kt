package com.example.englishlearning.screentranslation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F3-05 的屏侧文案：这些字全部直接给用户看，所以按「能验收的句子」钉，而不是随便断言非空。
 *
 * 三条口径各有一条用例：OCR 不可用不许写成翻译失败；成本句必须能看出「发了几次、发给谁」；
 * 会话状态短语三档都要有话说。
 */
class ScreenTranslationCopyTest {
    @Test
    fun everyNoticeExplainsWhatWentWrongInPlainWords() {
        ScreenTranslationNotice.values().forEach { notice ->
            val text = ScreenTranslationCopy.noticeText(notice)
            assertTrue(text.isNotBlank(), "notice $notice 必须有话说")
        }
    }

    /** AC3-06 同一条线：本机识别不可用是「识别」的问题，绝不能说成「翻译失败」。 */
    @Test
    fun ocrUnavailableIsNeverWordedAsATranslationFailure() {
        val text = ScreenTranslationCopy.noticeText(ScreenTranslationNotice.OcrUnavailable)

        assertTrue(text.contains("不是翻译失败"), "要明确否认「翻译失败」：$text")
        assertFalse(text.contains("翻译请求发出去"), "不得让用户以为请求已经发出")
    }

    @Test
    fun projectionDeniedSaysTheAuthorisationWasNotGrantedAndCanBeRetried() {
        val text = ScreenTranslationCopy.noticeText(ScreenTranslationNotice.ProjectionDenied)

        assertTrue(text.contains("授权"), text)
        assertTrue(text.contains("重新") && text.contains("开始"), "要给出重试路径：$text")
    }

    /** 成本可见性（spec §3）：次数 + 模型名，明说按实际计费。 */
    @Test
    fun costLineShowsCountAndModelName() {
        val text = ScreenTranslationCopy.costLine(sentRequests = 3, modelName = "gpt-4o-mini")

        assertTrue(text.contains("3"), text)
        assertTrue(text.contains("gpt-4o-mini"), text)
        assertTrue(text.contains("次请求"), text)
        assertTrue(text.contains("实际计费"), text)
    }

    @Test
    fun costLineSaysTheModelIsUnsetInsteadOfFakingOne() {
        assertEquals(
            "本次会话已发 0 次请求 · 模型 未配置模型 · 按 AI 服务实际计费",
            ScreenTranslationCopy.costLine(sentRequests = 0, modelName = null),
        )
        assertEquals(
            "本次会话已发 1 次请求 · 模型 未配置模型 · 按 AI 服务实际计费",
            ScreenTranslationCopy.costLine(sentRequests = 1, modelName = "   "),
        )
    }

    @Test
    fun phaseLabelCoversAllThreePhases() {
        assertEquals("未开始", ScreenTranslationCopy.phaseLabel(ScreenTranslationPhase.Idle, ScreenTranslationMode.Off))
        assertEquals("已停止", ScreenTranslationCopy.phaseLabel(ScreenTranslationPhase.Stopped, ScreenTranslationMode.Off))
        assertEquals(
            "单次",
            ScreenTranslationCopy.phaseLabel(ScreenTranslationPhase.Running, ScreenTranslationMode.Once),
        )
        assertEquals(
            "连续",
            ScreenTranslationCopy.phaseLabel(ScreenTranslationPhase.Running, ScreenTranslationMode.Continuous),
        )
    }
}
