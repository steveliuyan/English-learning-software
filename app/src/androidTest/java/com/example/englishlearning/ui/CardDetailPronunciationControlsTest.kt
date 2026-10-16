package com.example.englishlearning.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.domain.WordCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F5-03 / F3-02（Task 9 修复轮 I-6）词卡详情四控播控组的 connected 行为验证。
 *
 * 补的是评审指出的证伪力缺口：修复前全仓（含 androidTest）对
 * `card_detail_pronunciation_toggle|stop|message`、`pronunciationControlsEnabled`、
 * `pauseResumeSupported` 零引用——把 `CardDetailScreen` 的播控块整删、把三处
 * `pronunciationControlsEnabled = true` 删掉、把 `if (pauseResumeSupported)` 写反都不会红。
 * 本类按 `WordCardPronunciationControlsTest` 的 ControlsHarness 形状补齐三例：
 *  1. 宿主没点头（默认 `pronunciationControlsEnabled = false`）时播控组**绝不渲染**——
 *     收藏浮层/搜索卡详情保持纯浏览（决策 §2），防「永远显示」的伪修；
 *  2. 系统 TTS（`pauseResumeSupported = false`）下只有「停止」，状态文案走
 *     `PronunciationStatus.message()` 映射（Playing→「正在播放发音…」）；
 *  3. 支持暂停时 toggle 的 contentDescription 在「暂停发音」/「继续播放发音」之间翻转，
 *     点停止后整组离开组合且委托恰为一次。
 *
 * Task 10（T9 复评 N-4）追加两例：发音状态文字改由**常驻 live region 容器**
 * `card_detail_status_live_region` 承载（决策 §4 详情页口径，与阅读页同形），
 * 断「无文案时容器仍在且为空」与「有文案时容器带 Polite、子节点恰为瞬时 Text 且不自带 LiveRegion」。
 *
 * 防假绿口径：呈现/消失用 `assertIsDisplayed()` 与 `assertDoesNotExist()` 断真实组合，
 * 委托次数用调用计数；文案断言用逐字常量（§12：`message()` 文案逐字用，不得改动）。
 */
@RunWith(AndroidJUnit4::class)
class CardDetailPronunciationControlsTest {
    @get:Rule val composeRule = createComposeRule()

    /** 状态放组合外 MutableState：测试改它，Compose 才真的重组（普通 var 不会）。 */
    private class ControlsHarness(
        val status: MutableState<PronunciationStatus>,
        val pauseResumeSupported: Boolean,
    ) {
        var pauseResumeCalls = 0
        var stopCalls = 0
    }

    private fun showControls(
        initialStatus: PronunciationStatus,
        pauseResumeSupported: Boolean,
    ): ControlsHarness {
        val harness = ControlsHarness(mutableStateOf(initialStatus), pauseResumeSupported)
        composeRule.setContent {
            CardDetailScreen(
                card = detailCard(),
                onBack = {},
                pronunciationStatus = harness.status.value,
                pronunciationControlsEnabled = true,
                pauseResumeSupported = harness.pauseResumeSupported,
                onPauseResume = {
                    harness.pauseResumeCalls++
                    harness.status.value =
                        if (harness.status.value == PronunciationStatus.Playing) PronunciationStatus.Paused
                        else PronunciationStatus.Playing
                },
                onStopPronunciation = {
                    harness.stopCalls++
                    harness.status.value = PronunciationStatus.Idle
                },
            )
        }
        return harness
    }

    /** 宿主闸门默认值钉桩：不传 `pronunciationControlsEnabled`（默认 false）时两个 tag 均不存在。 */
    @Test
    fun controlsStayHiddenWhenTheHostDoesNotOptIn() {
        composeRule.setContent {
            CardDetailScreen(
                card = detailCard(),
                onBack = {},
                pronunciationStatus = PronunciationStatus.Playing,
            )
        }

        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_stop").assertDoesNotExist()
    }

    /** 裁决 B2：系统 TTS 语境只呈现「停止」；状态文案走 `status.message()` 映射。 */
    @Test
    fun systemSpeechKeepsOnlyTheStopButtonAndShowsTheMappedMessage() {
        showControls(PronunciationStatus.Playing, pauseResumeSupported = false)

        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("暂停发音").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_stop").assertIsDisplayed()
        // CardDetailScreen 的文案映射（WordHeroCard 的 status.message()）：Playing → 逐字「正在播放发音…」。
        composeRule.onNodeWithTag("card_detail_pronunciation_message").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()
    }

    /** 暂停/继续文案翻转 + 停止收口：委托计数、整组离开组合、文案节点一并断言。 */
    @Test
    fun toggleFlipsBetweenPauseAndResumeAndStopClosesTheGroupOnce() {
        val harness = showControls(PronunciationStatus.Playing, pauseResumeSupported = true)

        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("暂停发音").assertIsDisplayed()

        // 点一次暂停：宿主（harness）把状态推进到 Paused，文案随之翻转。
        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").performClick()
        composeRule.waitForIdle()
        assertEquals(1, harness.pauseResumeCalls)
        assertEquals(PronunciationStatus.Paused, harness.status.value)
        composeRule.onNodeWithContentDescription("继续播放发音").assertIsDisplayed()

        composeRule.onNodeWithTag("card_detail_pronunciation_stop").performClick()
        composeRule.waitForIdle()
        assertEquals(1, harness.stopCalls)
        assertEquals(PronunciationStatus.Idle, harness.status.value)
        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_stop").assertDoesNotExist()
        // Idle 的 message() 为 null：文案节点也必须离开组合，不能滞留旧文案。
        composeRule.onNodeWithTag("card_detail_pronunciation_message").assertDoesNotExist()
    }

    /**
     * N-4（T9 复评 Minor，决策 §4 详情页口径 / 提交 `0c014cb`）：
     * `Idle`（无文案）时 live region **容器必须仍在组合里**且携带 Polite、无子节点。
     *
     * 修复前详情页只有瞬时 `Text`（自带 contentDescription，无 liveRegion，也没有常驻容器），
     * 这个 tag 压根不存在——读屏器没有可监听的区域，节点属性存在 ≠ 会播报，
     * 「状态文字随文案一起进出组合」的旧形状在本例直接红。
     */
    @Test
    fun liveRegionContainerPersistsWhileNoPronunciationMessageIsShown() {
        showControls(PronunciationStatus.Idle, pauseResumeSupported = true)

        val container = liveRegionContainer()
        container.assert(politeLiveRegion())
        container.onChildren().assertCountEquals(0)
        composeRule.onNodeWithTag("card_detail_pronunciation_message").assertDoesNotExist()
    }

    /**
     * N-4 第二例：`Playing` 时容器仍在树里、仍带 Polite，瞬时文案作为**唯一子节点**进出，
     * 且子节点自身**不**携带 LiveRegion——属性只允许挂在常驻容器一处，
     * 否则就是回归到「瞬时 Text 自己当 live region」的旧形状。
     * 播报口径（contentDescription 逐字「正在播放发音…」）不因本次修正丢失，一并钉住。
     */
    @Test
    fun thePronunciationStatusIsAnnouncedThroughThePersistentLiveRegion() {
        showControls(PronunciationStatus.Playing, pauseResumeSupported = false)

        val container = liveRegionContainer()
        container.assert(politeLiveRegion())
        composeRule.onNodeWithTag("card_detail_pronunciation_message").assertExists()
        container.assertChildrenAreTransientStatusesOf("card_detail_pronunciation_message")
        composeRule.onNodeWithContentDescription("正在播放发音…").assertExists()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()
    }

    /** 与 `WordCardRevealFocusTest` 同形（按其私有实现复制，不跨类复用）。 */
    private fun liveRegionContainer(): SemanticsNodeInteraction =
        composeRule.onNodeWithTag("card_detail_status_live_region")

    private fun politeLiveRegion(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    /**
     * 断言容器当前的瞬时子节点集合恰为 [expectedTags]（顺序即组合顺序），并逐个断言子节点
     * **不**自带 LiveRegion 属性（属性只允许存在于常驻容器）。
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

    /** 与 `CardDetailScreenTest` 私有 `card()` 同值的占位词卡（brief §12：不跨类复用 private 成员，照抄构造）。 */
    private fun detailCard() = WordCard(
        cardId = "placeholder:cet4:ability",
        wordBookId = "cet4",
        lemma = "ability",
        ipa = "əˈbɪləti",
        partOfSpeech = "n.",
        meaningZh = "能力；才能",
        example = "She has the ability to explain complex ideas simply.",
        inflections = emptyList(),
    )
}
