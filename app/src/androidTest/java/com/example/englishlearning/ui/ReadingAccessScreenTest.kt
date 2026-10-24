package com.example.englishlearning.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.reading.FeedItem
import com.example.englishlearning.reading.NotConfiguredReason
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingAccessScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun lockedStateShowsReasonAndDisablesArticleChoices() {
        composeRule.setContent {
            ReadingAccessScreen(
                state = ReadingAccessUiState.Locked("还差 2 个新词、1 个复习词"),
                onSelectType = {},
                onOpenHistory = {},
            )
        }

        composeRule.onNodeWithTag("reading_access_locked_reason").assertExists()
        composeRule.onNodeWithTag("reading_type_story").assertExists().assertIsNotEnabled()
        composeRule.onNodeWithTag("reading_history_entry").assertExists().assertHasClickAction()
    }

    @Test
    fun lockedStateStillShowsTheOfflineHistoryCount() {
        // 锁定只限制「当天新文章的生成入口」；历史文章始终可重读，所以锁定态的历史入口
        // 必须带上真实条数——否则点进去只会看到「暂无本地文章」，而库里其实有文章。
        composeRule.setContent {
            ReadingAccessScreen(
                state = ReadingAccessUiState.Locked("还差 2 个新词、1 个复习词", listOf(historyArticle())),
                onSelectType = {},
                onOpenHistory = {},
            )
        }

        composeRule.onNodeWithText("（1）", substring = true).assertExists()
    }

    @Test
    fun unlockedStateShowsFourTypesPreferenceAndOfflineHistoryEntry() {
        var selected: ArticleType? = null
        var historyOpened = 0
        composeRule.setContent {
            ReadingAccessScreen(
                state = ReadingAccessUiState.Ready(
                    preference = ReadingPreference("p1", ArticleType.SCIENCE, ArticleLengthTier.LONG),
                    history = emptyList(),
                ),
                onSelectType = { selected = it },
                onOpenHistory = { historyOpened++ },
            )
        }

        // 裁决⑤ 第三件后，四枚类型行与偏好区随「生成前参数」出现；这里仍逐枚点数四枚，
        // 少一枚（搬家时裁掉类型）当场红——判据强度没降，只是多了一步展开。
        composeRule.onNodeWithTag("reading_generation_params_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reading_type_news").assertExists()
        composeRule.onNodeWithTag("reading_type_story").assertExists()
        // 大字号 2.0 实测：四枚类型行会落到视口外，而 `performClick()` 对未入视口的节点是把事件
        // 注入到窗口之外——**静默不触发**（判红只停在 `selected == null`，不抛「点击失败」）。
        // 故先滚进视口再点：点数四枚与「点中的那枚必须交给宿主」这两条判据一字未减。
        composeRule.onNodeWithTag("reading_type_science").performScrollTo().assertExists().performClick()
        composeRule.onNodeWithTag("reading_type_workplace").assertExists()
        composeRule.onNodeWithTag("reading_preference").assertExists()
        composeRule.onNodeWithTag("reading_history_entry").performScrollTo().assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(ArticleType.SCIENCE, selected)
        assertEquals(1, historyOpened)
    }

    @Test
    fun readyStateShowsThreeSourceEntriesAndTheCostHint() {
        composeRule.setContent { readyScreen() }

        composeRule.onNodeWithTag("reading_source_ai").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("reading_source_feed").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("reading_source_import").assertExists().assertHasClickAction()
        // 费用提示必须逐字可读；绝不能再出现「本阶段不会发起网络生成」这类假话。
        composeRule.onNodeWithText("每次生成都会真实调用你配置的 AI 服务并可能产生费用", substring = true).assertExists()
        composeRule.onNodeWithText("本阶段不会发起网络生成", substring = true).assertDoesNotExist()
    }

    @Test
    fun generateFailureShowsConstantMessageAndConfigureAction() {
        var settingsOpened = 0
        composeRule.setContent {
            readyScreen(
                generation = GenerationUiState.Failed(AiFailure.Unauthorized),
                onOpenAiSettings = { settingsOpened++ },
            )
        }

        composeRule.onNodeWithTag("generation_failure").assertExists()
        // 泄露哨兵用子串匹配：拼接出来的 endpoint/Key 片段必须被常量文案挡住。
        composeRule.onNodeWithText("api.test", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("密钥被拒绝", substring = true).assertExists()
        composeRule.onNodeWithTag("generation_failure_action").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun noDefaultProfileShowsSpecificConfigurationGuidance() {
        var settingsOpened = 0
        composeRule.setContent {
            readyScreen(
                generation = GenerationUiState.NotConfigured(NotConfiguredReason.NoDefaultProfile),
                onOpenAiSettings = { settingsOpened++ },
            )
        }

        composeRule.onNodeWithText("请先选择文章默认 AI 服务。", substring = true).assertExists()
        composeRule.onNodeWithTag("generation_failure_action").performClick()
        composeRule.waitForIdle()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun unavailableDefaultProfileShowsSpecificConfigurationGuidance() {
        composeRule.setContent {
            readyScreen(generation = GenerationUiState.NotConfigured(NotConfiguredReason.DefaultProfileUnavailable))
        }

        composeRule.onNodeWithText("文章默认 AI 服务已不可用，请重新选择。", substring = true).assertExists()
    }

    @Test
    fun needsConfirmationDialogNamesTheHostAndWiresBothAnswers() {
        var answered: Boolean? = null
        composeRule.setContent {
            readyScreen(
                generation = GenerationUiState.NeedsConfirmation("api.test"),
                onConfirmOutbound = { answered = it },
            )
        }

        // `outbound_confirmation_text` 在接共享件后指「本屏的出站确认对话框槽位」而非正文文本节点：
        // 正文节点的权威锚是共享件自带的 `outbound_confirm_body`，本条同时点名它，锚语义收窄但不脱凭。
        composeRule.onNodeWithTag("outbound_confirmation_text").assertExists()
        composeRule.onNodeWithTag("outbound_confirm_body").assertExists()
        composeRule.onNodeWithText("api.test", substring = true).assertExists()
        composeRule.onNodeWithText("密钥将发送给", substring = true).assertExists()
        composeRule.onNodeWithTag("outbound_confirm_dismiss").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(false, answered)
        composeRule.onNodeWithTag("outbound_confirm_confirm").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(true, answered)
    }

    @Test
    fun todayArticleExposesReadAndRegenerateButtons() {
        var opened = 0
        var regenerated = 0
        composeRule.setContent {
            readyScreen(
                todayArticle = todayArticle(),
                onOpenTodayArticle = { opened++ },
                onRegenerate = { regenerated++ },
            )
        }

        composeRule.onNodeWithTag("reading_open_today").assertExists().performClick()
        composeRule.onNodeWithTag("reading_regenerate").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        assertEquals(1, regenerated)
    }

    @Test
    fun feedEmptyStateIsNotPaintedOnReadingHome() {
        // 裁决⑤ 第二件：Feed 离开主页内联区。状态照常带进 Ready，但主页一枚都不许画——
        // 画了就把主任务挤出首屏。状态的正面证据在 ReadingFeedScreenTest。
        // 用例名（修复轮 1 F-3）：原判据反转成 assertDoesNotExist 后仍叫「…IsExplicit」，
        // 名字与断言相反会让后来人把这三枚当成冗余判据删掉——它们是「不许把 Feed 搬回主页」的唯一行为面护栏。
        composeRule.setContent { readyScreen(feed = FeedUiState.Empty) }
        composeRule.onNodeWithTag("feed_empty").assertDoesNotExist()
        composeRule.onNodeWithTag("reading_source_feed").assertExists().assertHasClickAction()
    }

    @Test
    fun feedUnreachableStateIsNotPaintedOnReadingHome() {
        var feedOpened = 0
        composeRule.setContent {
            readyScreen(feed = FeedUiState.Unreachable, onOpenFeed = { feedOpened++ })
        }
        composeRule.onNodeWithTag("feed_error").assertDoesNotExist()
        composeRule.onNodeWithTag("reading_source_feed").performClick()
        composeRule.waitForIdle()
        assertEquals(1, feedOpened)
    }

    @Test
    fun feedReadyItemsAndSourceCreditAreNotPaintedOnReadingHome() {
        composeRule.setContent {
            readyScreen(
                feed = FeedUiState.Ready(
                    items = listOf(FeedItem("A Robot Teacher", "https://learningenglish.voanews.com/a/x", 0L, "")),
                    sourceDisplayName = "VOA Learning English",
                ),
            )
        }
        // Feed  Ready 曾是最能把主页撑成一页流水账的一档：主页必须对它零渲染。
        composeRule.onNodeWithTag("feed_source").assertDoesNotExist()
        composeRule.onNodeWithTag("feed_item_0").assertDoesNotExist()
    }

    // ---------- Task 24（裁决⑤）：主任务上提第一屏 + 主 CTA 唯一 ----------

    @Test
    fun mainTaskCtaSitsAboveTheFoldWithoutScrolling() {
        // 裁决⑤ 第一件：不滚动即可见「今日文章」那枚主 CTA，且它落在首屏上半。
        // 阈值取根容器高度的一半（设备无关，不写死 dp）；`getBoundsInRoot()` 给 DpRect，比较按 Dp 走。
        // 修复轮 1 F-2：原来那句 `assertTrue(rootBottom > 0.dp)` 是自我确认型断言（根高只会为正），
        // 已删——真正有牙的是下面那条 `ctaTop < rootBottom / 2f`：根容器一旦塌成零高，它当场红。
        composeRule.setContent { readyScreen(todayArticle = todayArticle()) }
        val rootBottom = composeRule.onNodeWithTag("reading_access_screen")
            .getBoundsInRoot().bottom
        val ctaTop = composeRule.onNodeWithTag("reading_open_today").getBoundsInRoot().top
        composeRule.onNodeWithTag("reading_open_today").assertIsDisplayed()
        assertTrue(
            "今日文章主 CTA 的 top=$ctaTop 必须落在首屏上半（根高=$rootBottom 的一半以下）：主任务被埋进长列就是本件要修的形状",
            ctaTop < rootBottom / 2f,
        )
    }

    @Test
    fun mainGenerationTaskCtaSitsAboveTheFoldWithoutScrolling() {
        // 裁决⑤ 第一件的**另一支**（修复轮 1 F-2 补的腿）：没有当日文章时，主任务是
        // 「生成今天的文章」`reading_source_ai`，而不是阅读档。原批只量了有文章那一支，
        // 于是「把三枚来源入口重新提到主 CTA 上方」这一形状不会判红。
        // 阈值口径与上一枚逐字同形：根容器高度的一半（Dp 域，不写死 dp）。
        // 一支一次 `setContent`（本仓 connected 真机怪癖：同一枚用例两次 setContent 必崩），故另立用例。
        composeRule.setContent { readyScreen() }
        val rootBottom = composeRule.onNodeWithTag("reading_access_screen")
            .getBoundsInRoot().bottom
        val ctaTop = composeRule.onNodeWithTag("reading_source_ai").getBoundsInRoot().top
        composeRule.onNodeWithTag("reading_source_ai").assertIsDisplayed().assertHasClickAction()
        assertTrue(
            "无当日文章时生成档主 CTA 的 top=$ctaTop 必须落在首屏上半（根高=$rootBottom 的一半以下）：" +
                "把外刊/粘贴入口提到它上方，就是把这一支的主任务重新埋进长列",
            ctaTop < rootBottom / 2f,
        )
    }

    @Test
    fun primaryGenerationCtaAndRegenerateNeverShareTheScreen() {
        // 主 CTA 数 == 1：无当日文章时是「生成今天的文章」（reading_source_ai），
        // 已有当日文章时同一位置换成阅读 + 换一篇，两枚生成档锚永不并列。
        composeRule.setContent { readyScreen(todayArticle = todayArticle()) }
        composeRule.onNodeWithTag("reading_source_ai").assertDoesNotExist()
        composeRule.onNodeWithTag("reading_regenerate").assertExists().assertHasClickAction()
    }

    @Test
    fun regenerateCtaIsAbsentUntilTodayArticleExists() {
        composeRule.setContent { readyScreen() }
        composeRule.onNodeWithTag("reading_source_ai").assertExists().assertHasClickAction()
        composeRule.onNodeWithTag("reading_regenerate").assertDoesNotExist()
        composeRule.onNodeWithTag("reading_open_today").assertDoesNotExist()
    }

    @Test
    fun typeChoicesAndPreferenceOnlyAppearAfterTheGenerationParamsToggle() {
        // 裁决⑤ 第三件：类型与长度偏好不再常驻主页，随「生成前参数」这一次动作出现。
        var selected: ArticleType? = null
        composeRule.setContent {
            ReadingAccessScreen(
                state = ReadingAccessUiState.Ready(
                    preference = ReadingPreference("p1", ArticleType.SCIENCE, ArticleLengthTier.LONG),
                    history = emptyList(),
                ),
                onSelectType = { selected = it },
                onOpenHistory = {},
            )
        }
        composeRule.onNodeWithTag("reading_preference").assertDoesNotExist()
        composeRule.onNodeWithTag("reading_type_science").assertDoesNotExist()

        composeRule.onNodeWithTag("reading_generation_params_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reading_preference").assertExists()
        composeRule.onNodeWithTag("reading_type_science").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(ArticleType.SCIENCE, selected)
    }

    @Test
    fun readyStateHistoryEntryIsReachableByScrolling() {
        // 真机走查发现：Ready 态内容高于屏幕后，根 Column 没有 verticalScroll，
        // 历史入口被折叠在折叠线以下且无法滚动到——在这台设备上不可达。
        // 本测试要求根容器可滚动，历史入口必须能滚到并保持可点击。
        composeRule.setContent { readyScreen(todayArticle = todayArticle()) }
        composeRule.onNodeWithTag("reading_access_screen")
            .performScrollToNode(hasTestTag("reading_history_entry"))
        composeRule.onNodeWithTag("reading_history_entry").assertExists().assertHasClickAction()
    }

    @Composable
    private fun readyScreen(
        generation: GenerationUiState = GenerationUiState.Idle,
        feed: FeedUiState = FeedUiState.Idle,
        todayArticle: com.example.englishlearning.reading.domain.Article? = null,
        onGenerate: () -> Unit = {},
        onRegenerate: () -> Unit = {},
        onOpenTodayArticle: () -> Unit = {},
        onConfirmOutbound: (Boolean) -> Unit = {},
        onOpenAiSettings: () -> Unit = {},
        onOpenFeed: () -> Unit = {},
    ) = ReadingAccessScreen(
        state = ReadingAccessUiState.Ready(
            preference = ReadingPreference("p1", ArticleType.STORY, null),
            history = emptyList(),
            todayArticle = todayArticle,
            generation = generation,
            feed = feed,
        ),
        onSelectType = {},
        onOpenHistory = {},
        onGenerate = onGenerate,
        onRegenerate = onRegenerate,
        onOpenTodayArticle = onOpenTodayArticle,
        onConfirmOutbound = onConfirmOutbound,
        onOpenAiSettings = onOpenAiSettings,
        onOpenFeed = onOpenFeed,
    )

    private fun historyArticle() = com.example.englishlearning.reading.domain.Article(
        articleId = "a1", profileId = "p1", localDate = "2026-09-28", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = 1,
        title = "How Urban Trees Improve City Life", englishText = "text", chineseText = "译文",
        generatedAtEpochMillis = 1, coveredLemmas = emptyList(),
        source = ArticleSource.AiGenerated(modelName = "", parameterSummary = ""),
    )

    private fun todayArticle() = com.example.englishlearning.reading.domain.Article(
        articleId = "today-1", profileId = "p1", localDate = "2026-09-25", activeWordBookId = "cet4",
        articleType = ArticleType.STORY, lengthTier = ArticleLengthTier.STANDARD, version = 1,
        title = "Today", englishText = List(200) { "word" }.joinToString(" "), chineseText = "译文",
        generatedAtEpochMillis = 1, coveredLemmas = emptyList(),
        source = ArticleSource.AiGenerated(modelName = "gpt-x", parameterSummary = ""),
    )
}
