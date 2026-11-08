package com.example.englishlearning.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult
import com.example.englishlearning.learning.AppendEventResult
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.PlaceholderWordCardSource
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.CardReviewState
import com.example.englishlearning.learning.domain.LearningEvent
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.InMemoryLocalProfileRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTransitionInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    /** 判据 1：切 tab 的半程里两腿同时存在；settle 后只剩一腿。 */
    @Test
    fun tabSwitchKeepsBothLegsMountedMidTransition() {
        renderReadyAppOnLearningTab()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        assertEquals(
            "转场半程只有一腿 ⇒ AnimatedContent 没在播退场，链仍然当场翻转",
            2,
            tabLegNodes().size,
        )
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(1, tabLegNodes().size)
    }

    /**
     * 判据 2：退场腿对无障碍隐身，入场腿不隐身。
     * D-3 补口⑤：旧形状只数「恰一枚带 InvisibleToUser」，不验是**哪**一枚——极性反过来（隐藏入场腿、
     * 暴露退场腿）也照样过。腿 tag 现按状态分态（`tab_transition_leg_<TAB>`，AppScreen 站 2 容器），
     * 这里点名「被隐掉的那枚必须是离场 LEARNING 腿」，一枚都不许多、一枚都不许少。
     */
    @Test
    fun outgoingLegIsHiddenFromAccessibility() {
        renderReadyAppOnLearningTab()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        val legs = tabLegNodes()
        assertEquals(2, legs.size)
        val hiddenTags = legs
            .filter { it.config.contains(SemanticsProperties.InvisibleToUser) }
            .map { it.config[SemanticsProperties.TestTag] }
        assertEquals(
            "两腿里必须恰好一枚带 invisibleToUser，且必须是离场那枚（LEARNING），入场腿不许隐身",
            listOf("tab_transition_leg_LEARNING"),
            hiddenTags,
        )
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    /**
     * 判据 3：开全屏子页（站 1）同样两腿共存。
     * 入口与锚点全部现读，不是编的：`app_tab_ai` 与 `profile_open_settings` 分别是
     * `AppBottomBar.kt:187`（`app_tab_${tab.name.lowercase()}`）与 `ProfileScreen.kt:104` 的既有 tag，
     * 点击配方照 `AppScreenTest.kt:617`（`performScrollTo().performClick()`）逐字抄。
     */
    @Test
    fun openingFullScreenSubPageKeepsBothLegsMountedMidTransition() {
        renderReadyAppOnProfileTab()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("profile_open_settings").fetchSemanticsNodes().isNotEmpty()
        }
        // 测试形状修正（本批已实测）：`performScrollTo` 需要推进帧把「设置」行组合进布局；若在它之前关掉
        // autoAdvance，LazyColumn 滚不到目标、performScrollTo 永远等不到 ⇒ 用例 8 分钟挂死（红的不是判据而是夹具跑不动）。
        // 先在推进开着时把该行滚入并 settle，再关推进，手动停在转场半程。断言（半程 2 腿、settle 后 1 腿）不变。
        composeRule.onNodeWithTag("profile_open_settings").performScrollTo()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("profile_open_settings").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        assertEquals(
            "开设置子页的半程只有一腿 ⇒ 站 1 没在播退场，链仍按标志位当场翻转",
            2,
            overlayLegNodes().size,
        )
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertExists()
        assertEquals(1, overlayLegNodes().size)
    }

    /**
     * 判据 4（F5-06 红线，D-3 补口②重造）：转场不得多一次计费出站/发音。
     *
     * 旧形状只驱动 SentenceAnalysis 腿（route idx 24）——评审实测该腿没有组合触发的出站，
     * 计数在补闸前后都恒为 0，等于判据失明。现改驱动**词卡腿**（LearningFlow，route idx 8）：
     * 该腿的 `LaunchedEffect(cardState.cardId)`（AppScreen 词卡腿效果体）在组合时自动发音一次，
     * key 读实时 VM，正是「退场腿重启」的风险形状；发音计数 provider 与 AI 出站计数 transport
     * 同时接进宿主（都走 `AppScreen` 既有参数，不新增接线）。
     *
     * 判据 = 一次进出转场的 overlap 窗口内，两者都不许多于「不开转场的同一段导航」的基线：
     * 进入词卡腿 = 1 次自动发音（`AppScreenTest.learningCardPronunciationRoutesTheVisibleLemmaToTheProvider`
     * 已钉死同一基线），纯学习导航 AI 出站 = 0。窗口中途用 runOnMainSync 把实时 VM 换成下一张卡：
     * 补口① 的退场闸在场时退场腿重启即早退（GREEN）；摘掉闸后退场腿会替正在离开的屏再发音一次（RED）。
     * 中间的「半程 2 腿」断言是牙齿守卫：窗口若没开着，本用例当场报「失去靶面」而不是空转放行。
     */
    @Test
    fun transitionsProduceNoExtraBillableOutbound() {
        val transport = CountingAiHttpTransport()
        val pronunciation = CountingPronunciationProvider()
        var currentLemma = "ability"
        val wordCardViewModel = wordCardFixtureViewModel(planResult = { cardPlanFor(currentLemma) })
        renderAppScreen(
            sentenceAnalysisViewModel = sentenceAnalysisViewModelWith(transport),
            pronunciationProvider = pronunciation,
            wordCardViewModel = wordCardViewModel,
        )

        // 补口②（评审 Minor 9）：performScrollTo 前补 waitUntil（与同文件判据 3 的姊妹形状一致），
        // 把「设备得保持清醒」从口头前提变成代码前提。
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("开始学习").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitForIdle()
        // 进入词卡腿：组合触发自动发音恰一次——这就是同一段导航在「没有转场」时的基线。
        assertEquals(
            "进入词卡腿的自动发音基线必须恰一次（多一次=重复触发，少一次=夹具没走到词卡腿）：${pronunciation.spokenTexts}",
            1,
            pronunciation.spokenTexts.size,
        )
        assertEquals(listOf("ability"), pronunciation.spokenTexts)

        // 退场：词卡腿进入 300ms overlap 窗口；窗口中途把实时 VM 换成下一张卡（achieve）。
        composeRule.mainClock.autoAdvance = false
        pressSystemBack()
        var pump = 0
        while (pump < 30 && overlayLegNodes().size != 2) {
            composeRule.mainClock.advanceTimeBy(16)
            pump++
        }
        assertEquals("退场半程必须两腿共存，否则 overlap 窗口没开着、本判据失去靶面", 2, overlayLegNodes().size)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            currentLemma = "achieve"
            wordCardViewModel.load("profile-transition")
        }
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(
            "进出转场的 overlap 窗口内，退场腿不得替用户重发：发音基线 1 次，AI 出站基线 0 次。" +
                "多出来的那次发音来自退场腿重启的自动发音效果：${pronunciation.spokenTexts}",
            1,
            pronunciation.spokenTexts.size,
        )
        assertEquals(
            "纯学习导航的整段转场必须零 AI 计费出站：${transport.requests}",
            0,
            transport.requests.size,
        )
    }

    /**
     * 判据 5：冷启动首帧不播。
     * D-3 补口⑤：旧形状让夹具在 autoAdvance 开着的状态下一路跑到 `waitForIdle()`，之后才冻时钟数腿——
     * 转场（若有）早已 settle，「恰 1 腿」有无缺陷都成立，判据空转。现按要求改成
     * **组合之前就装好 `mainClock.autoAdvance = false`**，手动步进帧直到 tab 宿主**首次出现的那一帧**
     * 当场断言只有一枚腿；`finally` 恢复 autoAdvance，后面的用例不继承冻住的时钟。
     */
    @Test
    fun firstCompositionDoesNotPlayATransition() {
        composeRule.mainClock.autoAdvance = false
        try {
            setAppScreenContent()
            // 首帧：应用还在 splash/建资料屏，先步进到建档表单出现，再步进过「创建资料」那一下点击。
            pumpUntil(timeoutMillis = 8_000) {
                composeRule.onAllNodesWithContentDescription("姓名输入").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithContentDescription("姓名输入")[0].performTextInput("学习者")
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.onAllNodesWithContentDescription("创建资料")[0].performClick()
            // 步进到 Ready 分支（tab 宿主）出现的那一帧立即停：此刻若首组合被误播成转场，两腿会同时在场。
            pumpUntil(timeoutMillis = 8_000) {
                composeRule.onAllNodesWithTag("app_tab_learning").fetchSemanticsNodes().isNotEmpty() ||
                    tabLegNodes().isNotEmpty()
            }
            assertTrue(
                "手动步进帧内 tab 宿主必须出现",
                composeRule.onAllNodesWithTag("app_tab_learning").fetchSemanticsNodes().isNotEmpty(),
            )
            assertEquals(
                "冷启动首帧只该有入场腿；两腿共存 = 首组合被当成转场播了一次",
                1,
                tabLegNodes().size,
            )
        } finally {
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
        }
    }

    // ---------- 终评补口 第 2 条（席 B2）：导航身份与「同 key 换值」实测 ----------

    /**
     * 判据 6（第 2 条三层判据的 connected 那一层，emulator-5554）：挂着「复习队列」子页推一份
     * **只改 `newDone`** 的 `Ready`（三档反馈后的今日计划刷新就是这个形状），站 1 不许播转场。
     *
     * 缺陷形状：`ReviewQueue(plan)` 冻的是实时派生数据，route 又是 `data class` ⇒ 旧口径下
     * `AnimatedContent` 没有 contentKey，新 `plan` = 新 route = 「换了一屏」⇒ 一次刷新起一轮转场。
     * 两枚读数都取自缺陷的直接后果：
     *  - 逐帧腿数：转场一旦开播，overlap 窗口里 `overlay_transition_leg` 会是 2 枚；
     *  - 屏内 `remember` 存活：`ReviewQueueScreen.kt:107` 的 `verticalScroll(rememberScrollState())`
     *    滚动位就住在该腿的 remember 槽里，腿重新组合 ⇒ 滚回顶部 ⇒ `review_queue_summary` 重新可见。
     *
     * 牙齿守卫：刷新必须真的落到 route 上。判据 7 用同一套夹具把「payload 换了值」做成屏幕文案读得出来的形状，
     * 本条的 `assertEquals(1, 腿数.max())` 在摘掉 contentKey 的构建上实测为 2（RED 原文见本席报告）。
     */
    @Test
    fun sameIdentityProgressRefreshDoesNotPlayTransitionOrResetScroll() {
        val events = MutableCompletedEvents()
        val cards = QueueCardSource()
        val viewModel = TodayPlanViewModel(
            { reviewQueuePlan(newCards = 1, dueCards = 12) },
            FakeLearningProfileRepository(),
            events,
            cards = cards,
        )
        renderReviewQueueHost(viewModel)

        // 屏内 remember 态的observable：把滚动面滚到底，顶部的强卡（`review_queue_summary`）应当离开视口。
        composeRule.onNodeWithTag("review_queue_mode_card").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("review_queue_summary").assertIsNotDisplayed()

        // 刷新：`completedCardIds` 多一张新卡 ⇒ `UnlockPolicy.progress` 把 newDone 从 0 抬到 1，
        // planId/dueQueue 逐字不变（route 的新实例只在派生字段上不等）。
        composeRule.mainClock.autoAdvance = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            events.completed += PlaceholderWordCardSource.cardId("cet4", "ability")
            viewModel.load("profile-transition")
        }
        val legCounts = mutableListOf<Int>()
        composeRule.waitForIdle()
        repeat(25) {
            composeRule.mainClock.advanceTimeBy(16)
            legCounts += overlayLegNodes().size
        }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(
            "只改 newDone 的刷新被站 1 读成了一次导航（overlap 窗口里两腿共存 = 转场开播）；逐帧腿数 = $legCounts",
            1,
            legCounts.max(),
        )
        composeRule.onNodeWithTag("review_queue_screen").assertExists()
        // `remember` 存活：腿没重新组合，滚动位就还在底部。
        composeRule.onNodeWithTag("review_queue_summary").assertIsNotDisplayed()
    }

    /**
     * 判据 7（第 2 条裁定点名的实测）：**同 key 换值**时 Compose 到底把不把新值送进 content lambda？
     * 站 1 加 `contentKey = { it?.navigationIdentity }` 之后，「同一屏换数据」不再起转场，但那一腿的
     * `route` 形参还是不是新 payload，是裁定里唯一没被实测钉住的一环：
     *  - 送 ⇒ 冻进 payload 的渲染输入在新口径下仍然新鲜，不需要回退；
     *  - 不送 ⇒ 按裁定回退「payload 只装身份、渲染腿按现行口径读实时 VM」，并写进决策文档。
     *
     * 读数取屏幕上「N 个词在等你」那一行（`ReviewQueueScreen.kt:123`，直接由 `route.plan.dueQueue.size` 驱动）：
     * 到期队列 12 → 13 而 planId 不变 ⇒ 身份不变 ⇒ 若 lambda 收到新值，文案必须跟着变。
     */
    @Test
    fun sameKeyPayloadRefreshFeedsTheNewQueueIntoTheContentLambda() {
        val events = MutableCompletedEvents()
        val cards = QueueCardSource()
        var dueCards = 12
        val viewModel = TodayPlanViewModel(
            { reviewQueuePlan(newCards = 1, dueCards = dueCards) },
            FakeLearningProfileRepository(),
            events,
            cards = cards,
        )
        renderReviewQueueHost(viewModel)
        assertEquals("基线读数", listOf("12 个词在等你"), dueQueueLines())

        dueCards = 13
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModel.load("profile-transition") }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()

        assertEquals(
            "「同 key 换值」读数：contentKey（navigationIdentity）不变时，content lambda 有没有收到新的 plan？" +
                "屏幕上「个词在等你」那一行停留在旧读数 ⇒ Compose 没把新值送进 lambda，" +
                "按裁定须回退「payload 只装身份、渲染腿读实时 VM」",
            listOf("13 个词在等你"),
            dueQueueLines(),
        )
    }

    /**
     * 判据 7 的最小对照（不带宿主，只留 `AnimatedContent` 本体）：把 content lambda 收到的实参序列记下来。
     *
     * **读数（2026-11-09，emulator-5554，Compose BOM 2025.12.00）**：同 key 换值时 lambda 收到的是**新值**——
     * 首帧序列 `[first]`，换值后序列尾部为 `second`（每次重组各追加一枚，屏上文本节点也从 `first` 变成 `second`，
     * 旧文本节点归零）。RED 原文（未接线时的对照形状）与这条读数一并见本席报告「同 key 换值实测结论」一节。
     * 结论：Compose **会**把新值送进同 key 的 content lambda ⇒ 冻进 payload 的渲染输入在新口径下仍然新鲜
     * ⇒ 裁定给的回退支（payload 只装身份、渲染腿读实时 VM）**不启用**，无需改决策文档。
     */
    @Test
    fun contentLambdaReceivesTheNewValueUnderUnchangedContentKey() {
        val lambdaArguments = mutableListOf<String>()
        var payload by mutableStateOf("first")
        composeRule.setContent {
            AnimatedContent(
                targetState = "same-key" to payload,
                contentKey = { it.first },
            ) { state ->
                lambdaArguments += state.second
                Text(state.second)
            }
        }
        composeRule.waitForIdle()
        assertEquals("基线读数：lambda 只该见过 first", listOf("first"), lambdaArguments.toList())

        payload = "second"
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()

        assertEquals(
            "「同 key 换值」读数：contentKey 恒定（身份没变）时 lambda 的最新实参 = ${lambdaArguments.lastOrNull()}，" +
                "全部实参序列 = $lambdaArguments",
            "second",
            lambdaArguments.last(),
        )
        composeRule.onAllNodesWithText("second").assertCountEquals(1)
        composeRule.onAllNodesWithText("first").assertCountEquals(0)
    }

    // ---------- 宿主与夹具（形状逐字来自本包既有 connected 类，不是新造） ----------

    /**
     * 最小可跑宿主：`AppScreenOverlayContainerTest.kt:110-117` 的四参形状 + 判据 4 需要的长难句 VM、
     * 发音计数 provider 与可控词卡 VM（补口②：后两枚都是 `AppScreen` 既有参数，不新增接线）。
     */
    private fun renderAppScreen(
        sentenceAnalysisViewModel: SentenceAnalysisViewModel? = null,
        pronunciationProvider: PronunciationProvider? = null,
        wordCardViewModel: WordCardViewModel = wordCardFixtureViewModel(),
        todayPlanViewModel: TodayPlanViewModel? = null,
    ) {
        setAppScreenContent(sentenceAnalysisViewModel, pronunciationProvider, wordCardViewModel, todayPlanViewModel)
        createProfile()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("app_tab_learning").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun setAppScreenContent(
        sentenceAnalysisViewModel: SentenceAnalysisViewModel? = null,
        pronunciationProvider: PronunciationProvider? = null,
        wordCardViewModel: WordCardViewModel = wordCardFixtureViewModel(),
        todayPlanViewModel: TodayPlanViewModel? = null,
    ) {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)
        composeRule.setContent {
            AppScreen(
                // 与 AppScreenTest.kt:2623-2625 同形：AppViewModel 与 CreateLocalProfileUseCase 共用一份资料仓库，
                // 否则 createProfile() 建完的资料读不回来，宿主永远停在「建资料」那一屏。
                viewModel = AppViewModel(
                    repository,
                    CreateLocalProfileUseCase(repository, clock),
                    AndroidTestHealthyDatabaseStartupProbe,
                ),
                learningSetupViewModel = setupViewModel(),
                todayPlanViewModel = todayPlanViewModel
                    ?: TodayPlanViewModel({ placeholderCardPlan() }, FakeLearningProfileRepository()),
                wordCardViewModel = wordCardViewModel,
                sentenceAnalysisViewModel = sentenceAnalysisViewModel,
                pronunciationProvider = pronunciationProvider,
            )
        }
    }

    private fun renderReadyAppOnLearningTab() = renderAppScreen()

    private fun renderReadyAppOnProfileTab() {
        renderAppScreen()
        composeRule.onNodeWithTag("app_tab_profile").performClick()
        composeRule.waitForIdle()
    }

    /** 建资料两步，逐字取自 `AppScreenOverlayContainerTest.kt:436-439`。 */
    private fun createProfile() {
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
    }

    /** 系统返回键，逐字取自 `AppScreenOverlayContainerTest.kt:493-506`（先等窗口有焦点再发键）。 */
    private fun pressSystemBack() {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            var focused = false
            instrumentation.runOnMainSync {
                focused = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .any { it.hasWindowFocus() }
            }
            focused
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    /** 学习设置 VM：形状取自 `AppScreenTest.kt:2937-2953`（`test-book` 必须声明为内置册，否则可见性策略把它滤掉）。 */
    private fun setupViewModel(): LearningSetupViewModel {
        val repository = FakeLearningProfileRepository()
        val settingsRepository = FakeLearningSettingsRepository()
        return LearningSetupViewModel(
            repository,
            SeedWordBooksUseCase(
                { "[{\"id\":\"test-book\",\"displayName\":\"测试词书\",\"level\":\"Test\",\"totalWords\":1,\"dataVersion\":\"v1\",\"sourceId\":\"ngsl-nawl-1.2\",\"sourcePolicy\":\"应用内学习分组，不是官方考试大纲词表。词条尚未随本任务打包。\"}]" },
                repository,
            ),
            SelectWordBookAndSetDailyTargetUseCase(repository),
            GetLearningSettingsUseCase(settingsRepository),
            SaveLearningSettingsUseCase(settingsRepository),
            bundledIds = BundledWordBookIdSource { setOf("test-book") },
        )
    }

    /** 站 2 腿节点：tag 补口⑤ 起按 tab 分态（`tab_transition_leg_<TAB>`），前缀匹配把四枚一起数。 */
    private fun tabLegNodes() = composeRule.onAllNodes(
        SemanticsMatcher("tab_transition_leg_<TAB>") { node ->
            node.config.contains(SemanticsProperties.TestTag) &&
                node.config[SemanticsProperties.TestTag].startsWith("tab_transition_leg_")
        },
    ).fetchSemanticsNodes()

    /** 站 1 腿节点：tag 不分态（判据 3 沿用）。 */
    private fun overlayLegNodes() =
        composeRule.onAllNodesWithTag("overlay_transition_leg").fetchSemanticsNodes()

    /**
     * 手动时钟下的 waitUntil 替身：以 16ms 步进帧直到条件成立（autoAdvance=false 时 waitUntil 拿不到
     * 推进帧，判据 5 用它把「首帧」钉在真实的第一批帧上），超时如实报错。
     */
    private fun pumpUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            composeRule.mainClock.advanceTimeBy(16)
        }
        assertTrue("手动步进 ${timeoutMillis}ms 内条件仍未成立", condition())
    }

    /**
     * 出站计数假件：形状照 `AppScreenTest.kt:1949-1951` 的匿名 transport，
     * 只把 `error(...)` 换成「记下 + 回 `NetworkUnavailable`」（不编造能过解析的响应体）。
     */
    private class CountingAiHttpTransport : AiHttpTransport {
        val requests = mutableListOf<AiHttpRequest>()

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            requests += request
            return AiHttpResult.NetworkUnavailable
        }
    }

    /**
     * 发音计数假件：形状照 `AppScreenTest.kt:2304-2345` 的 `RecordingPronunciationProvider`，
     * 只保留「记下文本、回 Played」。判据 4 用它把「退场腿多出来的那次发音」变成可断言的数。
     */
    private class CountingPronunciationProvider : PronunciationProvider {
        val spokenTexts = mutableListOf<String>()

        override fun capabilities() = setOf(PronunciationCapability.RemoteAudio)

        override suspend fun speak(text: String): PronunciationResult {
            spokenTexts += text
            return PronunciationResult.Played
        }

        override suspend fun speakWord(
            text: String,
            accent: com.example.englishlearning.language.domain.PronunciationAccent,
            forceRegenerate: Boolean,
            rate: Float,
            entity: com.example.englishlearning.language.domain.PronunciationEntityRef?,
        ): PronunciationResult {
            spokenTexts += text
            return PronunciationResult.Played
        }

        override suspend fun speakSentence(
            text: String,
            accent: com.example.englishlearning.language.domain.PronunciationAccent,
            forceRegenerate: Boolean,
            entity: com.example.englishlearning.language.domain.PronunciationEntityRef?,
        ): PronunciationResult {
            spokenTexts += text
            return PronunciationResult.Played
        }
    }

    /** 词卡腿夹具的单卡计划：卡面 lemma 可控，判据 4 用它把实时 cardId 从 ability 换到 achieve。 */
    private fun cardPlanFor(lemma: String): TodayPlanResult =
        TodayPlanResult.Ready(
            TodayPlan(
                planId = "plan-1",
                profileId = "profile-1",
                localDate = LocalDate.of(2026, 9, 19),
                zoneId = "Asia/Shanghai",
                activeWordBookId = "cet4",
                newTarget = 1,
                dueTarget = 0,
                newCardIds = listOf(PlaceholderWordCardSource.cardId("cet4", lemma)),
                dueCardIds = emptyList(),
                ruleVersion = "v1",
                generatedAt = Instant.EPOCH,
            ),
        )

    /** 长难句 VM 夹具：配置齐全（Selected + 有 Key），所以「多出来的那次出站」必然落到计数假件上。 */
    private fun sentenceAnalysisViewModelWith(
        transport: AiHttpTransport,
    ): SentenceAnalysisViewModel {
        val profile = AiProfile(
            profileId = "transition",
            displayName = "转场配置",
            websiteUrl = "https://example.com",
            endpoint = "https://api.example.com/v1",
            model = "gpt-test",
            capabilities = setOf(AiCapability.Text),
            secretReference = AiProfileSecretUseCase.referenceFor("transition"),
        )
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf('k'))
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(true)
        })
        return SentenceAnalysisViewModel(
            com.example.englishlearning.sentence.SentenceAnalysisUseCase(
                DefaultTextProfileResolver { DefaultTextProfileResult.Selected(profile) },
                secrets,
                transport,
            ),
            com.example.englishlearning.ai.AiOutboundGrantStore(),
        )
    }

    // ---------- 终评补口 第 2 条（席 B2）夹具：可控的「只改派生字段」刷新 ----------

    /**
     * 今日计划假事件源：`completedCardIds` 是唯一可变量 ⇒ `UnlockPolicy.progress`（UnlockPolicy.kt:13）
     * 把 `newDone` 抬起来，而 `planId` / `dueCardIds` 逐字不动——正是「三档反馈后刷新一次」的形状。
     */
    private class MutableCompletedEvents : LearningEventRepository {
        val completed = mutableListOf<String>()

        override suspend fun append(event: LearningEvent, nextState: CardReviewState) =
            AppendEventResult.Appended(duplicate = false)

        override suspend fun findEvent(eventId: String) = RepositoryResult.Success<LearningEvent?>(null)

        override suspend fun findCardState(cardId: String) = RepositoryResult.Success<CardReviewState?>(null)

        override suspend fun countEventsForCard(planId: String, cardId: String) = RepositoryResult.Success(0)

        override suspend fun completedCardIds(planId: String) = RepositoryResult.Success(completed.toList())

        override suspend fun reviewedCardIds(wordBookId: String) = RepositoryResult.Success(completed.toList())

        override suspend fun dueCardIds(wordBookId: String, now: Instant) = RepositoryResult.Success(emptyList<String>())
    }

    /** 词面假来源：按 id 造一张占位卡，队列长度 = 屏上「N 个词在等你」的 N（判据 7 的读数口径）。 */
    private class QueueCardSource : WordCardSource {
        override suspend fun cardIds(wordBookId: String) = emptyList<String>()

        override suspend fun cards(cardIds: List<String>) = cardIds.map { id ->
            WordCard(
                cardId = id,
                wordBookId = "cet4",
                lemma = "word-$id",
                ipa = "",
                partOfSpeech = "n.",
                meaningZh = "释义 $id",
            )
        }
    }

    /** 一份「1 枚新词 + N 枚到期词」的今日计划：planId 恒定，队列规模可控。 */
    private fun reviewQueuePlan(newCards: Int, dueCards: Int): TodayPlanResult =
        TodayPlanResult.Ready(
            TodayPlan(
                planId = "plan-review-queue",
                profileId = "profile-1",
                localDate = LocalDate.of(2026, 9, 19),
                zoneId = "Asia/Shanghai",
                activeWordBookId = "cet4",
                newTarget = newCards,
                dueTarget = dueCards,
                newCardIds = (1..newCards).map { PlaceholderWordCardSource.cardId("cet4", "ability") },
                dueCardIds = (1..dueCards).map { "due-$it" },
                ruleVersion = "v1",
                generatedAt = Instant.EPOCH,
            ),
        )

    /** 宿主立起来后把「复习队列」点开（入口 tag 与 AppScreenTest.kt:854 同一枚）。 */
    private fun renderReviewQueueHost(viewModel: TodayPlanViewModel) {
        renderAppScreen(todayPlanViewModel = viewModel)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_open_review_queue").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today_plan_open_review_queue").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("review_queue_screen").assertExists()
        composeRule.onNodeWithTag("review_queue_summary").assertIsDisplayed()
    }

    /** 屏上「N 个词在等你」那一行的读数（`ReviewQueueScreen.kt:123`，直接由 `route.plan.dueQueue.size` 驱动）。 */
    private fun dueQueueLines(): List<String> = composeRule.onAllNodes(
        SemanticsMatcher("含「个词在等你」的文本节点") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { "个词在等你" in it } == true
        },
    ).fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
}
