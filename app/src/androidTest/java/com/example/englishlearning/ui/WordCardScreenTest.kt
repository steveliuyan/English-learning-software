package com.example.englishlearning.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performScrollTo

import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
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

    /** 原型 `feedbackBlock()`：三档各带一枚副标，让「选哪档」不只靠一个词猜。 */
    @Test
    fun eachFeedbackTierShowsItsPrototypeSubCaption() {
        composeRule.setContent { WordCardScreen(state = ready()) }

        composeRule.onNodeWithText("想不起来").assertExists()
        composeRule.onNodeWithText("差点没想起").assertExists()
        composeRule.onNodeWithText("脱口而出").assertExists()
    }

    /**
     * 批 1 Task 10：评分后停在原卡上看回执（原型 `sSession` 的 `recap()` + 「下一个词」）。
     *
     * 互斥性只能在这里量：回执态若还留着三档按钮，用户会在同一条回执上再写第二个事件。
     */
    @Test
    fun theReceiptReplacesTheFeedbackBarAndForwardsTheNextActionOnce() {
        var advanced = 0
        composeRule.setContent {
            WordCardScreen(
                state = ready(
                    meaningRevealed = true,
                    receipt = FeedbackReceipt(CardFeedback.Known, java.time.Instant.EPOCH, "3 天后再问一次", hasNext = true),
                ),
                onNext = { advanced++ },
            )
        }

        composeRule.onNodeWithTag("word_card_recap").assertExists()
        composeRule.onNodeWithText("记为「认识」").assertExists()
        composeRule.onNodeWithText("3 天后再问一次").assertExists()
        composeRule.onNodeWithTag("word_card_feedback_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_feedback_known").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_next").assertExists().assertHasClickAction().performClick()
        composeRule.waitForIdle()

        assertEquals("回执态的主操作每点一次只前进一次", 1, advanced)
    }

    @Test
    fun theLastCardsReceiptOffersCompletingTheRound() {
        composeRule.setContent {
            WordCardScreen(
                state = ready(
                    meaningRevealed = true,
                    receipt = FeedbackReceipt(CardFeedback.Fuzzy, java.time.Instant.EPOCH, "明天再问一次", hasNext = false),
                ),
            )
        }

        composeRule.onNodeWithText("完成本轮").assertExists()
        composeRule.onNodeWithText("下一个词").assertDoesNotExist()
    }

    /** 原型揭晓态脚下那句唯一的、两套流程都成立的评分提示。 */
    @Test
    fun theRevealedCardShowsTheHonestRatingCaption() {
        composeRule.setContent { WordCardScreen(state = ready(meaningRevealed = true)) }

        composeRule.onNodeWithTag("word_card_feedback_caption").assertExists()
        composeRule.onNodeWithText("看过释义也可以照实选，这一档决定它下次什么时候再出现。").assertExists()
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

    /**
     * 批 1 Task 18 的回归位：本轮收尾**不再由本屏呈现**。
     *
     * 原型在末张卡推进后走的是 `go('learn-summary')`（`assets/app.js:1688` / `:1701`），收尾画面归新建的
     * `TodaySummaryScreen`；宿主按 `WordCardUiState.AllDone` 直接挂那一屏（`AppScreen.kt`），本屏的 `AllDone`
     * 分支只剩穷尽性、不画版面。所以这里钉的是「本屏不出现收尾屏的任何锚点」——若有人把「今天的词都过了一遍」
     * 或统计卡搬回词卡屏（同一状态两处渲染），下面的 `assertDoesNotExist` 当场红。
     * 「返回今日计划」仍由 NoCards / ReviewCompleted 两态提供（`noCardsKeepsTheWayBackToTheTodayPlan` 钉着），
     * 本态不给第二枚出口：出口在收尾屏的三个位上。
     */
    @Test
    fun allDoneIsNoLongerRenderedByTheCardScreen() {
        var returned = 0
        composeRule.setContent {
            WordCardScreen(state = WordCardUiState.AllDone(total = 3, completedCount = 3), onBackToPlan = { returned++ })
        }

        composeRule.onNodeWithTag("word_card_screen").assertExists()
        composeRule.onNodeWithTag("word_card_all_done").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_completion_breakdown").assertDoesNotExist()
        composeRule.onNodeWithTag("today_summary_screen").assertDoesNotExist()
        composeRule.onNodeWithText("今天的词都过了一遍").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回今日计划").assertDoesNotExist()
        assertEquals("收尾态不给词卡屏留第二枚出口", 0, returned)
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

    /**
     * QA3 A2.5（用户 2026-11-08 拍板「全部都做」＝立为缺陷修）：Android「字体大小」顶档 3.0 下，
     * 回忆态那张卡的**唯一出口**「显示释义」必须在首帧就在可点区内，不许藏在折叠线之下。
     *
     * 先把走查册那句原话证伪一半（本轮在 emulator-5554 用真机路径复现：`settings put system font_scale 3.0`
     * → 今日计划 → 开始学习 → 词卡，节点树与截图落 `.git/qa-scratch/qa3-repro/`）：
     * 「滑动不产生位移、节点树原地不动」**不成立**——两下 `input swipe` 后词头从 `[100,358][980,1113]`
     * 移到 `[100,311][985,838]`，「显示释义」升到 `[100,742][980,920]`（可点高 178px ≈ 68dp）并点得动，
     * 点完释义自动进视口（F5-03 的焦点迁移顺带把它滚进来了）。
     * 但**这条链仍然要修**：首帧画面是「一个占满视口的词头 + 一整排吸底评分按钮」，
     * 出口在折叠线之下且没有任何提示，顶档用户面对的是「只能评分、看不见还能揭晓」——
     * AC5-03 承诺的是关键流程支持动态字体，不是"肯滑两下就能走完"。
     *
     * 所以判据钉在**首帧、零滚动**：`getUnclippedBoundsInRoot()` 一上来就量，不许先 `performScrollTo()`
     * 把按钮搬进视口再断言（那样等于把缺陷本身当成了前置步骤）。
     *
     * 可视区的界取**吸底评分条的上沿**而不是屏根下沿：本屏是「顶栏 + 可滚内容（`weight(1f)`）+ 吸底条」，
     * 内容视口的实际底边就是评分条的顶边。只比屏根会放过「出口排到了评分条身后」这一档。
     *
     * 宿主必须是**带状态的真流程**（`onReveal` 翻 `meaningRevealed`，同 `WordCardRevealFocusTest` 的夹具写法）：
     * 用默认空回调时释义节点根本不进组合，那时断的是「我的夹具没接上」，不是「屏有缺陷」。
     * 揭晓之后释义仍按**内容**对待——允许滚一下才读到（`performScrollTo()` 在这一步是正当的），
     * 但评分三档必须还在屏上：揭晓不许把下一步挤出画面。
     */
    @Test
    fun revealActionIsOnScreenAtTopFontScaleWithoutScrolling() {
        val revealed = mutableStateOf(false)
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 3f)) {
                WordCardScreen(
                    state = ready(meaningRevealed = revealed.value),
                    onReveal = { revealed.value = true },
                )
            }
        }

        val root = composeRule.onNodeWithTag("word_card_screen").getUnclippedBoundsInRoot()
        val barTop = composeRule.onNodeWithTag("word_card_feedback_bar").getUnclippedBoundsInRoot().top
        val reveal = composeRule.onNodeWithTag("word_card_reveal")
        val firstFrame = reveal.getUnclippedBoundsInRoot()
        assertTrue(
            "fontScale=3.0 首帧「显示释义」不在可点区（F1-03 回忆→揭晓的唯一出口藏在折叠线下）：" +
                "按钮=$firstFrame 屏根=$root 评分条顶=$barTop",
            firstFrame.top >= root.top && firstFrame.bottom <= minOf(root.bottom, barTop) + 0.5.dp,
        )
        reveal.assertIsDisplayed()

        reveal.performClick()
        composeRule.waitForIdle()

        val meaning = composeRule.onNodeWithTag("word_card_meaning")
        meaning.assertExists()
        meaning.performScrollTo()
        val meaningBounds = meaning.getUnclippedBoundsInRoot()
        assertTrue(
            "fontScale=3.0 下点了「显示释义」却读不到释义（流程在此断裂）：" +
                "释义=$meaningBounds 屏根=$root 评分条顶=$barTop",
            meaningBounds.top >= root.top && meaningBounds.bottom <= minOf(root.bottom, barTop) + 0.5.dp,
        )
        listOf("word_card_feedback_unknown", "word_card_feedback_fuzzy", "word_card_feedback_known")
            .forEach { tag ->
                composeRule.onNodeWithTag(tag).assertIsDisplayed()
            }
    }

    /**
     * QA3 A2.3（用户 2026-11-08 拍板「全部都做」＝立为缺陷修）：顶档 3.0 下词头必须**整词排完**。
     *
     * 走查册那句是「词头尾字母被裁、无省略号」。设备复现（`.git/qa-scratch/qa3/fs300-20-wordcard.png`，
     * 真机路径 `confidence`）按像素量过：词头占两行，第一行 4 个墨团（`c`+`o`+`n`+`fi` 粘连）、
     * 第二行 4 个（`denc`），两行都止于 x≈847，而卡片可视底边在 y=1098——
     * 第三行那颗 `e` 既没画出来、也没被省略号替代，用户看到的是「confidenc」。
     *
     * 判据取两条互补的口径，因为「丢字」有两种成因，只看一种会漏：
     *  - **排版侧**：`GetTextLayoutResult` 的最后一行行尾必须等于词头长度——行被排出来却没画，
     *    与压根没排出来，在截图上长得一样，只有排版结果分得开；
     *  - **绘制侧**：词头的真实占位（`getUnclippedBoundsInRoot`）下沿必须落在可滚视口内——
     *    越过视口下沿的那一截被 `verticalScroll` 的父级裁掉，而词头没设 `maxLines`、不产生省略号，
     *    用户看到的就是「confidenc」。
     *
     * 绘制侧一律用 Dp 比 Dp：`getUnclippedBoundsInRoot()` 回 `DpRect`，`fetchSemanticsNode().boundsInRoot`
     * 回的是像素 `Rect`，两者直接判等永远红（本条首版就红在这一句上，不是缺陷红，是单位红）。
     *
     * 词头用 `confidence`（10 字母）而不是夹具默认的 `ability`（7 字母）：顶档一行只站得住 5 个字母，
     * 7 个字母两行就排完了，缺陷在这一枚词上根本不出现，拿它跑绿等于什么都没验。
     */
    @Test
    fun lemmaKeepsEveryLetterLaidOutAndVisibleAtTopFontScale() {
        val lemma = "confidence"
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 3f)) {
                WordCardScreen(state = ready(card = card().copy(lemma = lemma)))
            }
        }

        val node = composeRule.onNodeWithTag("word_card_lemma")
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("word_card_lemma 的 GetTextLayoutResult 产出 ${layouts.size} 份布局（期望恰 1 份）", 1, layouts.size)
        val layout = layouts.single()
        val lastLine = layout.lineCount - 1
        assertEquals(
            "fontScale=3.0 下词头「$lemma」只排到第 ${layout.getLineEnd(lastLine)} 个字符就结束了" +
                "（共 ${layout.lineCount} 行）：剩下的字母既没画也没换成省略号，屏幕上是一截残缺词（QA3 A2.3）",
            lemma.length,
            layout.getLineEnd(lastLine),
        )

        val root = composeRule.onNodeWithTag("word_card_screen").getUnclippedBoundsInRoot()
        // 顶档下「显示释义」被常驻在吸底区（A2.5），可滚视口的下沿因此由它决定而不是评分条；
        // 两个 tag 都量一遍取较紧的那条，判据就不依赖「这一档到底 pin 不 pin」这个还会变的事实。
        val viewportBottom = listOf("word_card_reveal", "word_card_feedback_bar")
            .map { composeRule.onNodeWithTag(it).getUnclippedBoundsInRoot().top }
            .minOf { it }
        val unclipped = node.getUnclippedBoundsInRoot()
        assertTrue(
            "fontScale=3.0 下词头画出可滚视口、被裁在卡片中途：词头=$unclipped 屏根=$root 视口下沿=$viewportBottom（QA3 A2.3）",
            unclipped.top >= root.top && unclipped.bottom <= viewportBottom + 0.5.dp,
        )
    }

    /**
     * QA3 A2.8（收口复测实拍发现，用户 2026-11-08 拍板「立缺陷修，同批口径」）：
     * 顶档 3.0 下音标必须**整串一行**，喇叭药片不许与它抢同一行的宽。
     *
     * 设备复现（`.git/qa-scratch/qa3-a2-final/fs300-a23-card-scrolled.png`，真机路径 `progressive`）：
     * `/prəˈɡresɪv/` 被排成 `/prə` `ˈgres` `ɪv/` 三行（节点 `[100,354][335,806]`，宽只剩 235px = 89.6dp），
     * 而喇叭药片那一枚占 `[466,556][938,727]` = 472px = 180dp。成因是
     * `Row { Column(weight(1f)){ 音标 ; 词性 } ; SpeakerButton }` 这一行里，药片的**可见文字**与朗读文案同源
     * （`SpeakerButton.kt` 的「一处定义」口径），顶档下那四个字连字形一起长到 180dp，把 `weight(1f)` 那一侧挤没。
     * 1.0 同屏对照：音标一行 `[100,518][275,558]`、药片 156px、词性一行到位。
     *
     * 为什么音标单独判、词性不判：音标是「怎么念」的权威写法，断在音节中间等于把读音也弄错了观感，
     * 是这一条的缺陷本体；词性掉出首帧属于**可滚**（内容段带 `verticalScroll`），与 A2.6③ 同档，
     * 按已定口径只登记不钉成「首帧必须可见」那种做不到的约束。
     *
     * 夹具词换成 `progressive`：默认那枚 `ability` 的音标 `/əˈbɪləti/` 在这一档本来就排得下一行，
     * 拿它跑绿等于什么都没验（与 A2.3 换 `confidence` 同一个理由）。
     */
    @Test
    fun pronunciationKeepsTheIpaOnOneLineAtTopFontScale() {
        val ipa = "/prəˈɡresɪv/"
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 3f)) {
                WordCardScreen(state = ready(card = card().copy(lemma = "progressive", ipa = ipa)))
            }
        }

        val node = composeRule.onNodeWithTag("word_card_ipa", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("word_card_ipa 的字级节点产出 ${layouts.size} 份布局（期望恰 1 份）", 1, layouts.size)
        val layout = layouts.single()
        assertEquals(
            "fontScale=3.0 下音标「$ipa」只排到第 ${layout.getLineEnd(layout.lineCount - 1)} 个字符就结束了" +
                "（共 ${layout.lineCount} 行）：剩下的音标字符被挤到下一行，屏幕上是一截断字（QA3 A2.8）",
            ipa.length,
            layout.getLineEnd(layout.lineCount - 1),
        )
        assertEquals(
            "fontScale=3.0 下音标「$ipa」排成 ${layout.lineCount} 行：喇叭药片与它抢同一行的宽，" +
                "音标被切成三截的音节（QA3 A2.8）",
            1,
            layout.lineCount,
        )
    }

    /**
     * QA3 A2.4（同上拍板）：顶档 3.0 下三档评分按钮不许把吸底区撑破。
     *
     * 设备复现（同一张截图）：三枚各拿 `weight(1f)` ≈ 110dp 宽，而这一档 `AppType.Label` 是 12sp×3=36sp、
     * `AppType.Caption` 12sp×3 同值——「不认识」三字就 ≈110dp，于是左格排成「不认/识/想不/起来」四行，
     * 整枚按钮底边越过窗口下沿（像素上被导航条那一带切断），中间那格同样四行。
     * 走查册记的「折行撑破卡片」是这件事的人话版本。
     *
     * 判据分两条，各管这件缺陷的两半：
     *  - **每一句文案排成一行**（`GetTextLayoutResult.lineCount == 1`）：顶档这条摆法的承诺就是
     *    「三档一眼分得开、且不靠行高换可读性」。句数用 `useUnmergedTree = true` 点名到字级节点——
     *    按钮父节点的合并语义会把三句并成一枚 `Text`，行数量不到真实排版。
     *  - **整格落在窗口之内**（真实占位下沿 ≤ 屏根下沿）：折行一旦把吸底区撑到 226dp，
     *    卡面就只剩评分条（A2.3 的另一半），所以「撑破」必须在边界上证伪。
     *
     * 两条都是 Dp 比 Dp / 纯计数，不混用 `boundsInRoot`（像素 `Rect`）与 `getUnclippedBoundsInRoot()`
     * （`DpRect`）——那对比较恒不等，红的是单位不是版面。
     */
    @Test
    fun feedbackTiersStayWholeAndInsideTheWindowAtTopFontScale() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 3f)) {
                WordCardScreen(state = ready())
            }
        }

        val root = composeRule.onNodeWithTag("word_card_screen").getUnclippedBoundsInRoot()
        listOf(
            "word_card_feedback_unknown" to listOf("不认识", "想不起来"),
            "word_card_feedback_fuzzy" to listOf("模糊", "差点没想起"),
            "word_card_feedback_known" to listOf("认识", "脱口而出"),
        ).forEach { (tag, texts) ->
            val unclipped = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(
                "fontScale=3.0 下「${texts.first()}」那格画到窗口之外：节点=$unclipped 屏根=$root（QA3 A2.4）",
                unclipped.top >= root.top && unclipped.bottom <= root.bottom + 0.5.dp,
            )
            texts.forEach { text ->
                val layouts = mutableListOf<TextLayoutResult>()
                composeRule.onNodeWithText(text, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals("「$text」的字级节点产出 ${layouts.size} 份布局（期望恰 1 份）", 1, layouts.size)
                assertEquals(
                    "fontScale=3.0 下「$text」排成 ${layouts.single().lineCount} 行：折行把整条吸底区撑高，" +
                        "卡面被压到只剩评分（QA3 A2.4）",
                    1,
                    layouts.single().lineCount,
                )
            }
        }
    }

    /**
     * F5-05 / 批 C-3（分类 1「等数据」）：词卡流首帧画的是**这一屏的两件真实形状**——
     * 内容强卡（`SoftCard` contentPadding 18：词头 Display + 若干说明行）与底部三枚等宽动作按钮
     * （`weight(1f)` + `spacedBy(10.dp)` + `heightIn(min = 54.dp)`），不再是 48dp 转圈加一句进行中文案。
     *
     * 「第 N / M 张」与进度条**刻意不镜像**：那两个是计数与进度，首帧一个都不该有（F1-01 假数字同源），
     * 所以这里同时判「真内容的节点一个都不提前组合」——回填是落进形状里，不是挤开占位块。
     * 尺寸阈值与上面那条大字号用例无关：骨架不含任何文字，字号缩放不改它的下限（`minHeight = 180.dp` 是形状自己给的）。
     */
    @Test
    fun loadingDrawsCardAndThreeActionShapesWithoutCaptionsOrCounters() {
        composeRule.setContent { WordCardScreen(state = WordCardUiState.Loading) }

        composeRule.onNodeWithTag("word_card_loading").assertExists()
        composeRule.onNodeWithText("正在", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("第", substring = true).assertDoesNotExist()
        listOf("word_card_lemma", "word_card_ipa", "word_card_progress", "word_card_reveal").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
        composeRule.onNodeWithTag("word_card_loading").assertHeightIsAtLeast(180.dp)
    }
}
