package com.example.englishlearning.ui

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.InMemoryLocalProfileRepository
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
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
            composeRule.onAllNodesWithTag("tab_transition_leg").fetchSemanticsNodes().size,
        )
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithTag("tab_transition_leg").fetchSemanticsNodes().size)
    }

    /** 判据 2：退场腿对无障碍隐身，入场腿不隐身。 */
    @Test
    fun outgoingLegIsHiddenFromAccessibility() {
        renderReadyAppOnLearningTab()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        val legs = composeRule.onAllNodesWithTag("tab_transition_leg").fetchSemanticsNodes()
        assertEquals(2, legs.size)
        val hidden = legs.count { it.config.contains(SemanticsProperties.InvisibleToUser) }
        assertEquals("两腿里必须恰好一枚带 invisibleToUser，且是离场那枚", 1, hidden)
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
            composeRule.onAllNodesWithTag("overlay_transition_leg").fetchSemanticsNodes().size,
        )
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertExists()
        assertEquals(1, composeRule.onAllNodesWithTag("overlay_transition_leg").fetchSemanticsNodes().size)
    }

    /**
     * 判据 4（本批最贵）：整段转场零额外计费出站。
     *
     * **仓内没有现成的计数 transport 可复用**（现算：`grep -rn "AiHttpTransport" app/src/androidTest | grep -v "error("`
     * 只命中 `AppScreenTest.kt` 那六枚「一调就 error」的禁调假件；`RequestInProgressEvidenceTest` 根本不接传输层，
     * 它是纯渲染取证类），所以本类自带一枚计数假件，形状照 `AppScreenTest.kt:1949-1964`
     * 的 `sentenceAnalysisViewModelWith`（含 `AiProfileSecretUseCase` 的内存 `SecretStore`），
     * 只把 `error(...)` 换成「记下请求、回 `NetworkUnavailable`」——回失败而不是回假的 200，
     * 因为本条判据只数次数，不验结果内容（编一份能过解析的响应体就是把判据建立在假数据上）。
     *
     * 牙齿在哪：`DefaultTextProfileResult.Selected(...)` + `has(reference) = success(true)` 让配置是**齐的**，
     * 于是「转场把入场腿重新组合、把某条 `LaunchedEffect` 再点一次」这条风险一旦为真，计数就会跳；
     * 而不是像「空配置」那样无论怎么改都恒为 0。
     * 纯导航（开子页 / 退回基座 / 再开）按产品口径**一次出站都不该有**，所以断言是 `isEmpty()`。
     */
    @Test
    fun transitionsProduceNoExtraBillableOutbound() {
        val transport = CountingAiHttpTransport()
        renderAppScreen(sentenceAnalysisViewModel = sentenceAnalysisViewModelWith(transport))
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()

        repeat(2) {
            composeRule.onNodeWithTag("ai_feature_sentence-analysis").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sentence_analysis_screen").assertExists()
            pressSystemBack()
            composeRule.waitForIdle()
        }

        assertEquals(
            "开/关子页各两次共四段转场，出站必须为 0（转场只搬画面，不替用户重发请求）：${transport.requests}",
            0,
            transport.requests.size,
        )
    }

    /**
     * 判据 5：冷启动首帧不播。
     * 测试形状修正（本批已实测）：夹具 `renderReadyAppOnLearningTab()` 内部要先跑完 setContent + createProfile +
     * `waitUntil` + `waitForIdle`，屏幕才会 settle 到学习 tab；若在它之前就把 `autoAdvance = false`，`waitUntil`
     * 得不到推进帧，直接 5000ms `ComposeTimeoutException`（红的不是「首帧播了转场」这条判据，而是夹具跑不完）。
     * 所以先让夹具跑完，再关自动推进取「当前只有一腿」这一帧——断言的东西没变（冷启动恰 1 腿、无退场腿），
     * 只是把「关推进」的时机挪到夹具之后。
     */
    @Test
    fun firstCompositionDoesNotPlayATransition() {
        renderReadyAppOnLearningTab()   // 夹具先跑完（autoAdvance 仍开着），setContent/建资料/waitUntil 全部 settle
        composeRule.mainClock.autoAdvance = false
        assertEquals(1, composeRule.onAllNodesWithTag("tab_transition_leg").fetchSemanticsNodes().size)
        composeRule.mainClock.autoAdvance = true
    }

    // ---------- 宿主与夹具（形状逐字来自本包既有 connected 类，不是新造） ----------

    /** 最小可跑宿主：`AppScreenOverlayContainerTest.kt:110-117` 的四参形状 + 判据 4 需要的长难句 VM。 */
    private fun renderAppScreen(sentenceAnalysisViewModel: SentenceAnalysisViewModel? = null) {
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
                wordCardViewModel = wordCardFixtureViewModel(),
                sentenceAnalysisViewModel = sentenceAnalysisViewModel,
            )
        }
        createProfile()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("app_tab_learning").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
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
