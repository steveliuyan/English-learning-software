package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.domain.CardFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Semantics of the F1-03 word card screen.
 *
 * Only static structure and input-injected behaviour are asserted: under `createComposeRule()`
 * the `viewModelScope` coroutines of a real ViewModel are not driven by `waitForIdle()`, so
 * asynchronous loading is covered by `WordCardViewModelTest` instead.
 */
@RunWith(AndroidJUnit4::class)
class WordCardScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun readyHidesMeaningAndInflectionsUntilTheRevealAction() {
        composeRule.setContent { WordCardScreen(state = ready()) }

        composeRule.onNodeWithTag("word_card_screen").assertExists()
        composeRule.onNodeWithTag("word_card_lemma").assertExists()
        composeRule.onNodeWithText("ability").assertExists()
        composeRule.onNodeWithTag("word_card_ipa").assertExists()
        composeRule.onNodeWithText("əˈbɪləti").assertExists()
        composeRule.onNodeWithTag("word_card_part_of_speech").assertExists()
        composeRule.onNodeWithText("n.").assertExists()
        composeRule.onNodeWithTag("word_card_progress").assertExists()
        composeRule.onNodeWithTag("word_card_meaning").assertDoesNotExist()
        composeRule.onNodeWithText("能力；才能").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_inflections").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_reveal").assertExists().assertHasClickAction()
    }

    @Test
    fun revealActionIsForwardedOncePerTap() {
        var revealed = 0
        composeRule.setContent {
            WordCardScreen(state = ready(), onReveal = { revealed++ })
        }

        composeRule.onNodeWithTag("word_card_reveal").performClick()
        composeRule.waitForIdle()
        assertEquals("the reveal button must forward exactly one action per tap", 1, revealed)
    }

    @Test
    fun revealedStateRendersMeaningAndInflectionsAndHidesTheRevealButton() {
        composeRule.setContent {
            WordCardScreen(
                state = ready(
                    card(
                        example = "She has the ability to explain ideas.",
                        inflections = listOf("abilities"),
                    ),
                    meaningRevealed = true,
                ),
            )
        }
        composeRule.onNodeWithTag("word_card_meaning").assertExists()
        composeRule.onNodeWithText("能力；才能").assertExists()
        composeRule.onNodeWithTag("word_card_inflections").assertExists()
        composeRule.onNodeWithText("词形变化：abilities").assertExists()
        composeRule.onNodeWithTag("word_card_reveal").assertDoesNotExist()
    }

    @Test
    fun readyOffersPronunciationAndPassesTheVisibleLemmaToTheCallback() {
        var spoken = ""
        composeRule.setContent {
            WordCardScreen(
                state = ready(),
                onSpeak = { spoken = it },
            )
        }

        composeRule.onNodeWithTag("word_card_speak").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals("ability", spoken)
    }

    /**
     * F5-03 / Task 10（B1 图标按钮）：词卡发音喇叭的触控目标必须 ≥48dp 见方。
     *
     * 两条口径（dispatch-notes §B）：brief 的 `assertHeightIsAtLeast/assertWidthIsAtLeast` 读裁剪后的
     * boundsInRoot——本屏根容器是 `fillMaxSize` 的 Column，**没有滚动祖先**（`performScrollTo()` 在这里
     * 会自己报错），所以改用 `assertIsDisplayed()` 先钉住「节点确实在视口内、矩形未被裁剪」，再跑尺寸断言；
     * 另用 `getUnclippedBoundsInRoot` 断真实尺寸（同 `CardDetailScreenTest.backSharesTheTitleRowWithAFullTouchTarget` 先例）。
     */
    @Test
    fun speakerButtonMeetsTheMinimumTouchTarget() {
        composeRule.setContent { WordCardScreen(state = ready(), onSpeak = {}) }

        composeRule.onNodeWithTag("word_card_speak").assertIsDisplayed()
        composeRule.onNodeWithTag("word_card_speak").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("word_card_speak").assertWidthIsAtLeast(48.dp)

        val bounds = composeRule.onNodeWithTag("word_card_speak").getUnclippedBoundsInRoot()
        assertTrue(
            "word_card_speak 的触控目标应至少 48dp 高，实际=$bounds",
            bounds.bottom - bounds.top >= 48.dp,
        )
        assertTrue(
            "word_card_speak 的触控目标应至少 48dp 宽，实际=$bounds",
            bounds.right - bounds.left >= 48.dp,
        )
    }

    @Test
    fun feedback_actions_remain_reachable_at_large_font_scale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                WordCardScreen(state = ready())
            }
        }

        // 批 2 Task 1：三档反馈改成吸底条（不再是滚动正文的尾部），所以这里既不该也不能再
        // `performScrollTo()`——整屏根 Column 已无滚动祖先，旧写法会直接报「no parent layout with a
        // Scroll SemanticsAction」。判据换成更严的一条：2.0 字号下**不滚动就可见**（吸底条自己撑高、
        // 内容区让位，不被裁切），这才是吸底布局该保证的事。
        composeRule.onNodeWithTag("word_card_feedback_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("word_card_feedback_known").assertIsDisplayed()
    }

    @Test
    fun pronunciationFailureMessageDoesNotChangeFeedbackActions() {
        composeRule.setContent {
            WordCardScreen(state = ready(), pronunciationMessage = "发音播放失败，请重试。")
        }

        composeRule.onNodeWithTag("word_card_pronunciation_message").assertExists()
        composeRule.onNodeWithContentDescription("发音播放失败，请重试。").assertExists()
        composeRule.onNodeWithContentDescription("不认识").assertHasClickAction()
        composeRule.onNodeWithContentDescription("模糊").assertHasClickAction()
        composeRule.onNodeWithContentDescription("认识").assertHasClickAction()
    }

    @Test
    fun theThreeTiersReportTheirFixedFeedback() {
        val reported = mutableListOf<CardFeedback>()
        composeRule.setContent { WordCardScreen(state = ready(), onSubmit = { reported += it }) }

        composeRule.onNodeWithContentDescription("不认识").assertExists().assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("模糊").assertExists().assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("认识").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(CardFeedback.Unknown, CardFeedback.Fuzzy, CardFeedback.Known),
            reported,
        )
    }

    @Test
    fun englishExampleStaysVisibleBeforeRevealAsRecallContext() {
        composeRule.setContent {
            WordCardScreen(
                state = ready(card(example = "She has the ability to explain ideas.", inflections = listOf("abilities"))),
            )
        }

        composeRule.onNodeWithTag("word_card_example").assertExists()
        composeRule.onNodeWithText("She has the ability to explain ideas.").assertExists()
        composeRule.onNodeWithTag("word_card_inflections").assertDoesNotExist()
    }

    @Test
    fun optionalExampleAndInflectionsAreAbsentWhenNotProvided() {
        composeRule.setContent {
            WordCardScreen(state = ready(card(example = null, inflections = emptyList()), meaningRevealed = true))
        }

        composeRule.onNodeWithTag("word_card_example").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_inflections").assertDoesNotExist()
    }

    @Test
    fun noUndoEntryIsOfferedForASubmittedFeedback() {
        composeRule.setContent { WordCardScreen(state = ready()) }

        composeRule.onNodeWithText("撤销").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("撤销").assertDoesNotExist()
        composeRule.onNodeWithText("重做").assertDoesNotExist()
    }

    @Test
    fun savingDisablesTheFeedbackActions() {
        composeRule.setContent { WordCardScreen(state = ready(submitting = true)) }

        composeRule.onNodeWithTag("word_card_submitting").assertExists()
        composeRule.onNodeWithTag("word_card_feedback_unknown").assertIsNotEnabled()
        composeRule.onNodeWithTag("word_card_feedback_fuzzy").assertIsNotEnabled()
        composeRule.onNodeWithTag("word_card_feedback_known").assertIsNotEnabled()
    }

    @Test
    fun aFailedSaveKeepsTheCardVisibleAndShowsAnActionableMessage() {
        composeRule.setContent { WordCardScreen(state = ready(message = "反馈未保存成功，请重试")) }

        composeRule.onNodeWithTag("word_card_lemma").assertExists()
        composeRule.onNodeWithTag("word_card_message").assertExists()
        composeRule.onNodeWithContentDescription("反馈未保存成功，请重试").assertExists()
        composeRule.onNodeWithContentDescription("认识").assertExists()
    }

    @Test
    fun unavailableOffersRetry() {
        var retried = 0
        composeRule.setContent { WordCardScreen(state = WordCardUiState.Unavailable, onRetry = { retried++ }) }

        composeRule.onNodeWithTag("word_card_unavailable").assertExists()
        composeRule.onNodeWithContentDescription("重试").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, retried)
    }

    @Test
    fun allDoneOffersTheWayBackToTheTodayPlan() {
        var returned = 0
        composeRule.setContent {
            WordCardScreen(state = WordCardUiState.AllDone(total = 3, completedCount = 3), onBackToPlan = { returned++ })
        }

        composeRule.onNodeWithTag("word_card_all_done").assertExists()
        composeRule.onNodeWithContentDescription("返回今日计划").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals(1, returned)
    }

    @Test
    fun noCardsKeepsTheWayBackToTheTodayPlan() {
        composeRule.setContent { WordCardScreen(state = WordCardUiState.NoCards) }

        composeRule.onNodeWithTag("word_card_no_cards").assertExists()
        composeRule.onNodeWithContentDescription("返回今日计划").assertExists()
    }

    /**
     * Task 15 Step 1（F5-03 移交账「退出学习不停播」的屏侧证据）：屏幕离开组合时，
     * 必须把停播**恰好一次**委托给宿主（`onStopPronunciation`），且组合期一次都不许误转。
     *
     * 为什么在屏侧也钉一条（宿主侧另有 `AppScreenTest.leavingLearningStopsTheStillPlayingAudioAtTheProvider`）：
     * 屏侧这条把「谁该收到 stop」与「几轮组合该收几次」钉成契约——守卫若做成跨组合共享的单例，
     * 重进学习流时第二次离开就不再补停播（读屏用户重新进屏后照样听得到上一轮的音），
     * 本用例最后两步即红；停播若被写进 `when (state)` 的某个分支里，切卡/翻态会误停播，
     * 第一步的 `assertEquals(0, stopCalls)` 即红。计数断言不可被「节点存在」这类恒真形状替代。
     *
     * 离开组合由 `showCard` 翻假驱动（本屏没有滚动祖先，`setContent` 的语义也不该被依赖来 dispose 子树）。
     */
    @Test
    fun leavingTheScreenForwardsTheStopActionExactlyOnce() {
        val showCard = mutableStateOf(true)
        var stopCalls = 0
        composeRule.setContent {
            if (showCard.value) {
                WordCardScreen(state = ready(), onStopPronunciation = { stopCalls++ })
            } else {
                Box(Modifier.testTag("word_card_screen_gone"))
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("word_card_screen").assertIsDisplayed()
        assertEquals("停播只能发生在离开组合时：组合期一次都不许转给宿主", 0, stopCalls)

        showCard.value = false
        composeRule.waitForIdle()

        assertEquals("离开组合必须把停播恰好委托给宿主一次", 1, stopCalls)
        composeRule.onNodeWithTag("word_card_speak").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_screen").assertDoesNotExist()

        showCard.value = true
        composeRule.waitForIdle()
        assertEquals("重进屏幕不该补停播", 1, stopCalls)

        showCard.value = false
        composeRule.waitForIdle()
        assertEquals("每轮组合是一枚新守卫：重进后再离开仍要补一次停播", 2, stopCalls)
    }

    /**
     * Task 15 Step 3 大字号护栏：Android「字体大小」上限档（`fontScale = 2.0`）下，
     * 主词与已揭晓释义必须**完整可见**（写法口径照 `CheckInScreenTest.headerKeepsLinksReachableAndTitleReadableAtMaximumLargeFontScale`）。
     *
     * 两条取样纪律（14B 复评 N-2 的教训：connected 取样串没有换行余量时会假红）：
     *  - 本屏根容器是 `fillMaxSize` 的 Column、**没有滚动祖先**，`performScrollTo()` 在这里会自己报错，
     *    所以只用「可见性 + 边界包含」，不引入 `assertHeightIsAtLeast` 那类尺寸阈值；
     *  - 主词在 2.0 下**会换行**（displaySmall 翻倍后一行站不住七个字母），故判「没被裁掉」而不是「单行高度」——
     *    裸高度阈值会把合法换行判成假红。
     * `assertIsDisplayed()` 只保证「有像素在屏上」，`getUnclippedBoundsInRoot()` 对比屏幕根才判得出「完整」，
     * 两条并立（释义被挤出可视区时前者照绿、后者必红）。
     */
    @Test
    fun lemmaAndRevealedMeaningStayFullyVisibleAtMaximumLargeFontScale() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2f)) {
                WordCardScreen(state = ready(meaningRevealed = true))
            }
        }

        val root = composeRule.onNodeWithTag("word_card_screen").getUnclippedBoundsInRoot()
        listOf("word_card_lemma" to "ability", "word_card_meaning" to "能力；才能").forEach { (tag, text) ->
            val node = composeRule.onNodeWithTag(tag)
            node.assertIsDisplayed()
            node.assertTextContains(text)
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$tag 在 fontScale=2.0 下被纵向裁出屏幕：节点=$bounds 屏幕根=$root", bounds.top >= root.top && bounds.bottom <= root.bottom)
            assertTrue("$tag 在 fontScale=2.0 下被横向裁出卡片：节点=$bounds 屏幕根=$root", bounds.left >= root.left && bounds.right <= root.right)
        }
    }
}
