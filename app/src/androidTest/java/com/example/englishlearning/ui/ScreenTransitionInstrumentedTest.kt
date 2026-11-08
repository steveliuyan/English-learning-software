package com.example.englishlearning.ui

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.PlaceholderWordCardSource
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
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

    // ---------- 宿主与夹具（形状逐字来自本包既有 connected 类，不是新造） ----------

    /**
     * 最小可跑宿主：`AppScreenOverlayContainerTest.kt:110-117` 的四参形状 + 判据 4 需要的长难句 VM、
     * 发音计数 provider 与可控词卡 VM（补口②：后两枚都是 `AppScreen` 既有参数，不新增接线）。
     */
    private fun renderAppScreen(
        sentenceAnalysisViewModel: SentenceAnalysisViewModel? = null,
        pronunciationProvider: PronunciationProvider? = null,
        wordCardViewModel: WordCardViewModel = wordCardFixtureViewModel(),
    ) {
        setAppScreenContent(sentenceAnalysisViewModel, pronunciationProvider, wordCardViewModel)
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
                todayPlanViewModel = TodayPlanViewModel({ placeholderCardPlan() }, FakeLearningProfileRepository()),
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
}
