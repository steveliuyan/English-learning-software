package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F5-03（Task 6）词卡揭晓焦点与瞬时状态 live region 的 connected 行为验证。
 *
 * 覆盖三条只能真机验证的行为：
 *  1. 学习者点「显示释义」后**焦点必须落到释义节点**：`word_card_meaning` 是随
 *     `meaningRevealed` 翻真才进组合的新节点，不做焦点迁移时焦点留在刚离开组合的按钮原位
 *     （或落回容器），TalkBack/键盘用户下一步要从卡片顶部重新念，等于跳过刚揭晓的释义；
 *  2. 「正在保存…」「正在播放发音…」这类瞬时状态要由 **Polite live region** 播报，
 *     即决策 §4 的「不打断当前朗读的瞬时播报」。
 *
 * live region 的断言口径（fix1 的关键修正）：**属性断在常驻容器 `word_card_status_live_region` 上，
 * 并额外断「没有瞬时消息时容器依然存在且携带 Polite」**。Compose 的 live region 是「容器留在树里、
 * 子节点进出」的机制；把属性挂在随文案一起进出组合的 Text 自身上，读屏器没有可监听的区域，
 * 节点属性存在 ≠ 会播报。因此下面三个 liveRegion 用例分别证：
 *  - 空容器仍在树里（旧形状此处连容器都不存在，RED 就是这个性质缺失）；
 *  - 提交中/发音态时容器仍在树里、瞬时 Text 成为它的子节点；
 *  - 瞬时子节点自身不再携带该属性（属性只在容器一处，防止回归到旧形状）。
 *
 * 防假绿：焦点断 `SemanticsProperties.Focused` 的**值**（`isFocused()` / `assertIsFocused()` 读这个键，
 * 由 Compose 焦点系统在当前持有焦点的节点上置真），live region 断 `LiveRegionMode.Polite` 的**值**，
 * 写成 `Assertive` 或只加个空 semantics 块都过不去；焦点用例还把「当前所有获焦节点的 testTag」
 * 列成表比对，RED 时失败信息直接指出焦点实际停在哪个节点。
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
    fun theStatusLiveRegionContainerPersistsWhileNoTransientMessageIsShown() {
        composeRule.setContent { WordCardScreen(state = ready()) }

        // 瞬时消息全都没出现时，live region 容器**必须仍在组合里**——这是「常驻容器」的定义性
        // 证据，也是把属性挂在瞬时 Text 上的旧形状无法通过的一条：旧代码里这个 tag 根本不存在。
        val container = liveRegionContainer()
        container.assert(politeLiveRegion())
        container.onChildren().assertCountEquals(0)
        composeRule.onNodeWithTag("word_card_submitting").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_message").assertDoesNotExist()
    }

    @Test
    fun theSubmittingStatusIsAnnouncedThroughThePersistentLiveRegion() {
        composeRule.setContent { WordCardScreen(state = ready(submitting = true)) }

        // 容器还在树里（同上一个用例），只是多了一个瞬时子节点；属性仍只在容器上。
        val container = liveRegionContainer()
        container.assert(politeLiveRegion())
        composeRule.onNodeWithTag("word_card_submitting").assertExists()
        container.assertChildrenAreTransientStatusesOf("word_card_submitting")
    }

    @Test
    fun thePronunciationStatusIsAnnouncedThroughThePersistentLiveRegion() {
        composeRule.setContent { WordCardScreen(state = ready(), pronunciationMessage = "正在播放发音…") }

        val container = liveRegionContainer()
        container.assert(politeLiveRegion())
        composeRule.onNodeWithTag("word_card_pronunciation_message").assertExists()
        container.assertChildrenAreTransientStatusesOf("word_card_pronunciation_message")
        // contentDescription 是播报文本的口径，仍在瞬时子节点上，不随本次修正丢失。
        composeRule.onNodeWithContentDescription("正在播放发音…").assertExists()
    }

    private fun liveRegionContainer(): SemanticsNodeInteraction =
        composeRule.onNodeWithTag("word_card_status_live_region")

    private fun politeLiveRegion(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    /**
     * 断言 live region 容器当前的瞬时子节点集合恰为 [expectedTags]（顺序即组合顺序），
     * 并逐个断言子节点**不**自带 LiveRegion 属性：该属性只允许存在于常驻容器，
     * 否则就是回归到「瞬时 Text 自己当 live region」的旧形状。
     */
    private fun SemanticsNodeInteraction.assertChildrenAreTransientStatusesOf(
        vararg expectedTags: String,
    ) {
        val children = onChildren().fetchSemanticsNodes()
        assertEquals(
            "瞬时状态应作为常驻 live region 容器的子节点出现",
            expectedTags.toList(),
            children.map { it.config.getOrNull(SemanticsProperties.TestTag) ?: "<无 tag>" },
        )
        children.forEach { child ->
            assertEquals(
                "live region 属性只应挂在容器上，子节点 " +
                    "${child.config.getOrNull(SemanticsProperties.TestTag) ?: "<无 tag>"} 不应自带",
                null,
                child.config.getOrNull(SemanticsProperties.LiveRegion),
            )
        }
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
