package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F5-03（Task 6）词卡揭晓焦点与瞬时状态的 connected 行为验证。
 *
 * 覆盖三条只能真机验证的行为：
 *  1. 学习者点「显示释义」后**焦点必须落到释义节点**：`word_card_meaning` 是随
 *     `meaningRevealed` 翻真才进组合的新节点，不做焦点迁移时焦点留在刚离开组合的按钮原位
 *     （或落回容器），TalkBack/键盘用户下一步要从卡片顶部重新念，等于跳过刚揭晓的释义；
 *  2. 「正在保存…」是提交后才出现的瞬时提示，须标 `liveRegion = Polite`，否则读屏器不知道发生过保存；
 *  3. 发音状态消息同理是瞬时状态。
 *
 * 断言口径（防假绿）：焦点断的是 `SemanticsProperties.Focused` 的**值**（`isFocused()` /
 * `assertIsFocused()` 都读这个键，该键由 Compose 焦点系统在当前持有焦点的节点上置真），
 * 而不是断「焦点相关 action 存在」；liveRegion 同样断值 `LiveRegionMode.Polite`，
 * 写成 `Im polite`/`Assertive` 或只加个空 semantics 块都过不去。
 * 第一个断言把「当前所有获焦节点的 testTag」列成表比对，RED 时失败信息直接指出焦点实际停在
 * 哪个节点，不必为取证临时改测试。
 */
@RunWith(AndroidJUnit4::class)
class WordCardRevealFocusTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun revealingTheMeaningMovesFocusOntoTheMeaningNode() {
        composeRule.setContent {
            var revealed by remember { mutableStateOf(false) }
            WordCardScreen(
                state = ready(meaningRevealed = revealed),
                onReveal = { revealed = true },
            )
        }
        // 揭晓前：焦点不在释义节点上（该节点还不存在），确保下面的断言测的是迁移而不是残留。
        composeRule.onAllNodes(isFocused()).assertNoneFocused()

        composeRule.onNodeWithTag("word_card_reveal").performClick()
        composeRule.waitForIdle()

        assertEquals(
            "揭晓后唯一获焦节点应是 word_card_meaning；实际获焦节点见本消息尾部",
            listOf("word_card_meaning"),
            composeRule.onAllNodes(isFocused()).fetchSemanticsNodes()
                .map { it.config.getOrNull(SemanticsProperties.TestTag) ?: "<无 tag>" }
                .distinct(),
        )
        composeRule.onNodeWithTag("word_card_meaning").assertIsFocused()
    }

    @Test
    fun theSubmittingStatusIsAnnouncedAsAPoliteLiveRegion() {
        composeRule.setContent { WordCardScreen(state = ready(submitting = true)) }

        composeRule.onNodeWithTag("word_card_submitting")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun thePronunciationStatusIsAnnouncedAsAPoliteLiveRegion() {
        composeRule.setContent { WordCardScreen(state = ready(), pronunciationMessage = "正在播放发音…") }

        composeRule.onNodeWithTag("word_card_pronunciation_message")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onNodeWithContentDescription("正在播放发音…").assertExists()
    }

    /** 「没有任何节点获焦」的显式断言：用节点数而不是断言不存在，失败时报出真实获焦节点。 */
    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertNoneFocused() {
        assertEquals(
            "揭晓前不应有任何节点持有焦点，实际获焦节点见本消息尾部",
            emptyList<String>(),
            fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) ?: "<无 tag>" },
        )
    }
}
