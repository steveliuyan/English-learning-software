package com.example.englishlearning.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
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
 * F5-03 / F3-02（Task 9）学习流词卡的「暂停-继续 / 停止」播控组 connected 行为验证。
 *
 * 三条只能在真机上证的行为：
 *  1. 按钮组**只在 Playing / Paused 窗口呈现**（决策 §2「暂停按钮仅在 Playing/Paused 窗口呈现」）：
 *     Idle / Played / Failed 下必须完全不存在，否则会给用户一排点了没反应的死按钮；
 *  2. 点按把动作**恰好一次**委托给宿主（`onPauseResume` / `onStopPronunciation`），
 *     UI 自己绝不改播放器状态——播控语义在 Task 7 的 Provider/Router 层；
 *  3. 系统 TTS 语境（`pauseResumeSupported = false`）下**只呈现「停止」**：
 *     平台事实是 `android.speech.tts.TextToSpeech` 没有公开 `pause()/resume()`，
 *     引擎侧恒返回 false（决策 §2 Task 7 取证 + controller 裁决 B2）。若呈现条件只看返回值，
 *     系统 TTS 下（恒 false）整组播控会不呈现，连始终可用的「停止」也一起消失，而朗读仍在继续。
 *     （M-1 订正：旧措辞写「点「暂停」会落 `else -> Idle` 让整组消失」——`else -> Idle` 已被决策 §2
 *     「播控失败的状态机口径」废除，五处宿主现均为 `else -> 原状态`，失败时保持原状态、整组与「停止」
 *     照常可达；准确口径如上，见 AppScreen.kt 的 N-3 注释与决策 §2。）
 *
 * 防假绿口径：呈现/消失用 `assertIsDisplayed()` 与 `assertDoesNotExist()` 断真实组合，
 * 委托次数用调用计数（不是「节点存在」这种恒真断言）；暂停态文案另断
 * `contentDescription = 继续播放发音`，写成「暂停发音」的单一常量过不去。
 *
 * fixture 复用 `LearningFlowFixtures.kt` 的 internal 顶层 `ready()`，与
 * `WordCardRevealFocusTest` 同一张占位词卡，避免两个测试族对同一个 Ready 态口径分叉。
 */
@RunWith(AndroidJUnit4::class)
class WordCardPronunciationControlsTest {
    @get:Rule val composeRule = createComposeRule()

    /** 状态放在组合外持有的 MutableState 里：测试要能改它并让 Compose 真的重组（普通 var 不会）。 */
    private fun showControls(
        initialStatus: PronunciationStatus,
        pauseResumeSupported: Boolean,
    ): ControlsHarness {
        val harness = ControlsHarness(mutableStateOf(initialStatus), pauseResumeSupported)
        composeRule.setContent {
            WordCardScreen(
                state = ready(),
                pronunciationStatus = harness.status.value,
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

    private class ControlsHarness(
        val status: MutableState<PronunciationStatus>,
        val pauseResumeSupported: Boolean,
    ) {
        var pauseResumeCalls = 0
        var stopCalls = 0
    }

    @Test
    fun controlsAppearOnlyWhilePlayingOrPausedAndDelegate() {
        val harness = showControls(PronunciationStatus.Idle, pauseResumeSupported = true)

        // Idle：一个控件都不出现（这是「只在播控窗口呈现」的前半）。
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertDoesNotExist()

        harness.status.value = PronunciationStatus.Playing
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertIsDisplayed()
        // M-1（B1 口径的证伪力）：播控组必须**在** liveRegion 常驻容器**之外**——Playing 且无瞬时
        // 文案时容器仍空。若有人把按钮组挪进 `word_card_status_live_region`，读屏会把「暂停/停止」
        // 念成状态播报的一部分，这里的孩子数即红。（此前只在 Idle 态借 WordCardRevealFocusTest:88
        // 背书，但 Idle 下按钮组根本不组合，证伪力为零。）
        composeRule.onNodeWithTag("word_card_status_live_region").onChildren().assertCountEquals(0)

        // 暂停：委托恰好一次，宿主把状态改成 Paused 后按钮文案翻成「继续播放发音」。
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").performClick()
        composeRule.waitForIdle()
        assertEquals(1, harness.pauseResumeCalls)
        assertEquals(PronunciationStatus.Paused, harness.status.value)
        composeRule.onNodeWithContentDescription("继续播放发音").assertIsDisplayed()

        // 再点一次是「继续」：仍走同一个 toggle，委托次数累加到 2，状态回 Playing。
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").performClick()
        composeRule.waitForIdle()
        assertEquals(2, harness.pauseResumeCalls)
        assertEquals(PronunciationStatus.Playing, harness.status.value)
        composeRule.onNodeWithContentDescription("暂停发音").assertIsDisplayed()

        // 停止：状态由宿主收回 Idle，整组随之离开组合（不是隐藏，是不存在）。
        composeRule.onNodeWithTag("word_card_pronunciation_stop").performClick()
        composeRule.waitForIdle()
        assertEquals(1, harness.stopCalls)
        assertEquals(PronunciationStatus.Idle, harness.status.value)
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertDoesNotExist()
    }

    /** 决策 §2 的播控窗口另一半：Paused 也属于呈现态，否则暂停后就再也出不来。 */
    @Test
    fun theStoppedCardDoesNotOfferPlaybackControlsAfterTheResultComesBack() {
        val harness = showControls(PronunciationStatus.Paused, pauseResumeSupported = true)

        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertIsDisplayed()

        // 播放器层把用户主动停止以 Played 收口（决策 §2），Played 不属于播控窗口。
        harness.status.value = PronunciationStatus.Played
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertDoesNotExist()
    }

    /**
     * 裁决 B2 的钉子用例：系统 TTS（引擎无公开 pause/resume）下不呈现暂停-继续，
     * 但「停止」必须照常可用——`stopPlayback()` 在系统 TTS 是真实可用的 `TextToSpeech.stop()`。
     * 把呈现条件写回「只看 pausePlayback() 返回值」的实现会在这里红：整组消失、停止不可点。
     */
    @Test
    fun systemSpeechKeepsOnlyTheStopButtonAvailable() {
        val harness = showControls(PronunciationStatus.Playing, pauseResumeSupported = false)

        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("暂停发音").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertIsDisplayed()

        composeRule.onNodeWithTag("word_card_pronunciation_stop").performClick()
        composeRule.waitForIdle()
        assertEquals(1, harness.stopCalls)
        assertEquals(PronunciationStatus.Idle, harness.status.value)
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertDoesNotExist()
    }
}
