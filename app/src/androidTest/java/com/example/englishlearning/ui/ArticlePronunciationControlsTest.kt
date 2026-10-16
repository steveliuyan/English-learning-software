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
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleDisplayMode
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F5-03 / F3-02（Task 9 修复轮 I-6 + I-4）阅读页播控组与常驻 liveRegion 容器的 connected 行为验证。
 *
 * I-6：修复前全仓对 `article_pronunciation_toggle|stop|message` 零引用，`ArticleReadingScreen`
 * 的播控块整删都不会红。按 `WordCardPronunciationControlsTest` 的 ControlsHarness 形状补齐三例，
 * 并因阅读页额外收 `pronunciationMessage` 而加断文案节点。
 *
 * I-4（决策 §4 + controller 裁决，Task 6 判例）：liveRegion 必须挂在**常驻容器**
 * `article_status_live_region` 上、靠子节点进出触发播报；瞬时 `Text` 自身不得携带 LiveRegion。
 * 旧形状（属性挂在随文案进出组合的 Text 上）在下面两条用例都必须红：
 *  ①无瞬时消息时容器仍在组合且携带 Polite、子节点恰为 0 个；
 *  ②有瞬时消息时容器仍 Polite、子节点恰为 `article_pronunciation_message` 且其自带 LiveRegion 为 null。
 * 断言口径与 `WordCardRevealFocusTest:115-145` 的三个私有断言件同形（本轮按 brief 允许复制；
 * 跨类共享 fixture 的抽公共文件归 T12，不得在此顺手改 T6 的类）。
 *
 * fixture：`ArticleReadingScreenTest` 的 article/state 构造为 private，按 §12 照抄不改既有类。
 */
@RunWith(AndroidJUnit4::class)
class ArticlePronunciationControlsTest {
    @get:Rule val composeRule = createComposeRule()

    private val appleCard = WordCard("c1", "cet4", "apple", "", "", "")
    private val bananaCard = WordCard("c2", "cet4", "banana", "", "", "")

    private class ControlsHarness(
        val status: MutableState<PronunciationStatus>,
        val pauseResumeSupported: Boolean,
    ) {
        var pauseResumeCalls = 0
        var stopCalls = 0
    }

    private fun article(
        englishText: String = "The apple grows in the garden",
        chineseText: String = "苹果长在园子里。",
        // 置空覆盖词：非空会弹「进文覆盖词」GlassDialog（AppScreen 阅读用例先关它），
        // 本类只验播控与 live region，弹窗与断言无关，直接从源头免掉。
        coveredLemmas: List<String> = emptyList(),
    ) = Article(
        articleId = "a1",
        profileId = "p1",
        localDate = "2026-09-24",
        activeWordBookId = "cet4",
        articleType = ArticleType.STORY,
        lengthTier = ArticleLengthTier.STANDARD,
        version = 1,
        title = "Apple Day",
        englishText = englishText,
        chineseText = chineseText,
        generatedAtEpochMillis = 1_760_000_000_000L,
        coveredLemmas = coveredLemmas,
        source = ArticleSource.AiGenerated(modelName = "gpt-x", parameterSummary = "model=gpt-x temperature=0.7"),
    )

    private fun state(article: Article): ArticleReadingUiState {
        val coverage = com.example.englishlearning.reading.ArticleHighlightPolicy.derive(article.englishText, article.coveredLemmas)
        return ArticleReadingUiState(
            article = article,
            mode = ArticleDisplayMode.BILINGUAL,
            translationExpanded = false,
            highlights = coverage.highlights,
            uncoveredLemmas = coverage.uncoveredLemmas,
            cards = listOf(appleCard, bananaCard),
        )
    }

    /**
     * 状态放组合外 MutableState；文案按宿主「同源」口径由状态映射（AppScreen 里
     * message 始终写 `status.message()`），这样 message 断言钉的也是映射而不是自由字符串。
     */
    private fun showControls(
        initialStatus: PronunciationStatus,
        pauseResumeSupported: Boolean,
    ): ControlsHarness {
        val harness = ControlsHarness(mutableStateOf(initialStatus), pauseResumeSupported)
        composeRule.setContent {
            ArticleReadingScreen(
                state = state(article()),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
                pronunciationMessage = harness.status.value.message(),
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

    /** 播控窗口闸门：默认参数（Idle / 不支持暂停）下不得渲染任何播控控件。 */
    @Test
    fun controlsStayHiddenOutsideThePlayingOrPausedWindow() {
        composeRule.setContent {
            ArticleReadingScreen(
                state = state(article()),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
            )
        }

        composeRule.onNodeWithTag("article_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("article_pronunciation_stop").assertDoesNotExist()
    }

    /** 裁决 B2：系统 TTS 语境只呈现「停止」；message 节点走同源文案。 */
    @Test
    fun systemSpeechKeepsOnlyTheStopButtonAndShowsTheMessage() {
        showControls(PronunciationStatus.Playing, pauseResumeSupported = false)

        composeRule.onNodeWithTag("article_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("暂停发音").assertDoesNotExist()
        composeRule.onNodeWithTag("article_pronunciation_stop").assertIsDisplayed()
        composeRule.onNodeWithTag("article_pronunciation_message").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()
    }

    /** 暂停/继续文案翻转 + 停止委托恰一次并收口整组与文案。 */
    @Test
    fun toggleFlipsBetweenPauseAndResumeAndStopClosesTheGroupOnce() {
        val harness = showControls(PronunciationStatus.Playing, pauseResumeSupported = true)

        composeRule.onNodeWithTag("article_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("暂停发音").assertIsDisplayed()

        composeRule.onNodeWithTag("article_pronunciation_toggle").performClick()
        composeRule.waitForIdle()
        assertEquals(1, harness.pauseResumeCalls)
        assertEquals(PronunciationStatus.Paused, harness.status.value)
        composeRule.onNodeWithContentDescription("继续播放发音").assertIsDisplayed()
        composeRule.onNodeWithText("已暂停播放，再次点按可继续。").assertIsDisplayed()

        composeRule.onNodeWithTag("article_pronunciation_stop").performClick()
        composeRule.waitForIdle()
        assertEquals(1, harness.stopCalls)
        assertEquals(PronunciationStatus.Idle, harness.status.value)
        composeRule.onNodeWithTag("article_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("article_pronunciation_stop").assertDoesNotExist()
        composeRule.onNodeWithTag("article_pronunciation_message").assertDoesNotExist()
    }

    /** 决策 §4/①：无瞬时消息时 liveRegion 容器必须仍在组合（旧形状此处连容器都不存在→RED）。 */
    @Test
    fun theStatusLiveRegionContainerPersistsWhileNoTransientMessageIsShown() {
        composeRule.setContent {
            ArticleReadingScreen(
                state = state(article()),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
                pronunciationMessage = null,
            )
        }

        val container = liveRegionContainer()
        container.assert(politeLiveRegion())
        container.onChildren().assertCountEquals(0)
        composeRule.onNodeWithTag("article_pronunciation_message").assertDoesNotExist()
    }

    /**
     * 决策 §4/②：有瞬时消息时容器仍常驻且 Polite，子节点恰为 `article_pronunciation_message`
     * 且自带 LiveRegion 为 null（属性只允许挂在容器一处，防回退旧形状）。
     */
    @Test
    fun thePronunciationStatusIsAnnouncedThroughThePersistentLiveRegion() {
        composeRule.setContent {
            ArticleReadingScreen(
                state = state(article()),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
                pronunciationMessage = "正在播放发音…",
                pronunciationStatus = PronunciationStatus.Playing,
            )
        }

        val container = liveRegionContainer()
        container.assert(politeLiveRegion())
        composeRule.onNodeWithTag("article_pronunciation_message").assertExists()
        container.assertChildrenAreTransientStatusesOf("article_pronunciation_message")
        // contentDescription 是播报文本口径，仍在瞬时子节点上（与 T6 同形，不随本次改造丢失）。
        composeRule.onNodeWithContentDescription("正在播放发音…").assertExists()
    }

    private fun liveRegionContainer(): SemanticsNodeInteraction =
        composeRule.onNodeWithTag("article_status_live_region")

    private fun politeLiveRegion(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    /** 照抄 `WordCardRevealFocusTest` 的私有断言件（本轮按 brief 允许复制，抽公共归 T12）。 */
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
}
