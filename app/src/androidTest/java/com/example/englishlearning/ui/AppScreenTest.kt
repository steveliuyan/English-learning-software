package com.example.englishlearning.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ai.AiPreferenceRepository
import com.example.englishlearning.ai.DefaultImageProfileResult
import com.example.englishlearning.ai.DefaultImageProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.imagegen.DrawingPromptUseCase
import com.example.englishlearning.imagegen.GeneratedImageStore
import com.example.englishlearning.imagegen.ImageGenerationUseCase
import com.example.englishlearning.learning.LearningEventRepository
import com.example.englishlearning.learning.LearningHistoryGroup
import com.example.englishlearning.learning.LearningRecord
import com.example.englishlearning.learning.LearningRecordRepository
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.PlaceholderWordCardSource
import com.example.englishlearning.learning.WordBookRecord
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.reading.ArticleIdFactory
import com.example.englishlearning.reading.ArticleRepository
import com.example.englishlearning.reading.FetchArticleUseCase
import com.example.englishlearning.reading.GenerateArticleUseCase
import com.example.englishlearning.reading.ImportArticleUseCase
import com.example.englishlearning.reading.ReadingCompletionRepository
import com.example.englishlearning.reading.ReadingPreferenceRepository
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import com.example.englishlearning.reading.domain.ReadingPreference
import com.example.englishlearning.ai.AiProfileIdFactory
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiPreference
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.domain.AiProviderKind
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.language.SpeechPreferenceRepository
import com.example.englishlearning.language.domain.PronunciationCapability
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.language.domain.PronunciationProvider
import com.example.englishlearning.language.domain.PronunciationResult
import com.example.englishlearning.language.domain.SpeechPreference
import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.InMemoryLocalProfileRepository
import com.example.englishlearning.wordqa.WordAiNote
import com.example.englishlearning.wordqa.WordAiNoteRepository
import com.example.englishlearning.wordqa.WordQaUseCase
import com.example.englishlearning.sentence.SentenceAnalysisUseCase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun profileCreationControlsHaveSemanticLabelsAndShowReady() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlanViewModel(), wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()
        composeRule.onNodeWithContentDescription("保存学习设置").assertExists()
    }

    @Test
    fun errorScreenRendersSafeStableTextOnly() {
        composeRule.setContent { ErrorScreen(onRetry = {}) }
        composeRule.onNodeWithText("无法创建资料，请检查姓名").assertExists()
        composeRule.onNodeWithText("Exception").assertDoesNotExist()
        composeRule.onNodeWithText("/secret").assertDoesNotExist()
    }

    @Test
    fun errorScreenOffersReturnActionSoUserIsNotStuck() {
        var returned = false
        composeRule.setContent { ErrorScreen(onRetry = { returned = true }) }
        composeRule.onNodeWithContentDescription("返回重新填写").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(true, returned)
    }

    @Test
    fun blankNameDisablesCreateAndDoesNotReloadTodayPlan() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        var invocations = 0
        val todayPlan = TodayPlanViewModel(
            { invocations++; TodayPlanResult.MissingLearningSetup },
            FakeLearningProfileRepository(),
        )
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlan, wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("创建资料").assertExists().assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("请输入名字提示").assertExists()
        assertEquals(0, invocations)
    }

    @Test
    fun savingSetupReloadsTodayPlan() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        var invocations = 0
        val todayPlan = TodayPlanViewModel(
            { invocations++; TodayPlanResult.MissingLearningSetup },
            FakeLearningProfileRepository(),
        )
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlan, wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("测试词书").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("保存学习设置").assertExists().performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { invocations == 2 }
        assertEquals(2, invocations)
    }

    /**
     * 用户要求「在应用底下加个 AI 学」。这里断言四栏都真的出现在应用外壳里，而不只是
     * 组件级测试通过——底导没被接进 AppScreen 是很容易发生的回归。
     */
    @Test
    fun readyPlanShowsTheFourSlotBottomNavigation() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        AppTab.entries.forEach { tab ->
            composeRule.onNodeWithTag("app_tab_${tab.name.lowercase()}").assertExists()
        }
        composeRule.onNodeWithContentDescription("AI 学").assertExists()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
    }

    @Test
    fun wordBookReturnsToTheLetterAndScrollPositionUsedToOpenWordDetail() {
        val wordsViewModel = wordBookWordsViewModelWithAlphabetThroughW()
        composeRule.setContent {
            readyAppScreen(
                todayPlanViewModel = TodayPlanViewModel({ readyPlanResult() }, FakeLearningProfileRepository()),
                wordBookWordsViewModel = wordsViewModel,
                learningRecordsViewModel = learningRecordsViewModelWithAbilityRecord(),
            )
        }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_wordbook_words").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_screen").assertExists()
        composeRule.onNodeWithTag("wordbook_words_row_aword").assertExists()
        composeRule.onNodeWithTag("wordbook_words_rail_W").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_rail_active_W").assertExists()
        composeRule.onNodeWithTag("wordbook_words_row_wonder").performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()
        composeRule.onNodeWithText("wonder").assertExists()

        // Task 16：详情私有页头已归一到共享 AppScreenHeader——`card_detail_back` 这枚屏侧 tag
        // 随私有件一起删除，返回位只由组件的「返回」朗读定语承载（全页唯一节点）。
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("wordbook_words_screen").assertExists()
        composeRule.onNodeWithTag("wordbook_words_rail_active_W").assertExists()
        composeRule.onNodeWithTag("wordbook_words_row_wonder").assertExists()
        composeRule.onNodeWithTag("wordbook_words_row_aword").assertDoesNotExist()
    }

    @Test
    fun wordBookNewBrowsingSessionStartsAtAAfterLeavingW() {
        val wordsViewModel = wordBookWordsViewModelWithAlphabetThroughW()
        composeRule.setContent {
            readyAppScreen(
                todayPlanViewModel = TodayPlanViewModel({ readyPlanResult() }, FakeLearningProfileRepository()),
                wordBookWordsViewModel = wordsViewModel,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_wordbook_words").performScrollTo().performClick()
        composeRule.onNodeWithTag("wordbook_words_rail_W").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_rail_active_W").assertExists()
        composeRule.onNodeWithTag("wordbook_words_back").performClick()
        composeRule.onNodeWithTag("today_plan_open_wordbook_words").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("wordbook_words_rail_active_A").assertExists()
        composeRule.onNodeWithTag("wordbook_words_row_aword").assertExists()
    }

    @Test
    fun wordBookSystemBackRetainsTheNonzeroScrollOffsetWithinW() {
        val wordsViewModel = wordBookWordsViewModelWithAlphabetThroughW(wRows = 12)
        composeRule.setContent {
            readyAppScreen(
                todayPlanViewModel = TodayPlanViewModel({ readyPlanResult() }, FakeLearningProfileRepository()),
                wordBookWordsViewModel = wordsViewModel,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_wordbook_words").performScrollTo().performClick()
        composeRule.onNodeWithTag("wordbook_words_rail_W").performClick()
        composeRule.waitForIdle()
        val row = composeRule.onNodeWithTag("wordbook_words_row_wonder")
        val topAtHeader = row.fetchSemanticsNode().boundsInRoot.top
        composeRule.onNode(hasScrollToIndexAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 47f)
        }
        composeRule.waitForIdle()
        val topBeforeDetail = row.fetchSemanticsNode().boundsInRoot.top
        assertEquals("夹具必须实际产生非零滚动偏移", 47f, topAtHeader - topBeforeDetail, 1f)
        row.performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()
        pressSystemBack()
        composeRule.onNodeWithTag("wordbook_words_screen").assertExists()
        assertEquals(
            "系统返回必须保留 W 组内原有像素偏移",
            topBeforeDetail,
            row.fetchSemanticsNode().boundsInRoot.top,
            1f,
        )
        composeRule.onNodeWithTag("wordbook_words_rail_active_W").assertExists()
    }

    @Test
    fun checkInEntryOpensOverlayAndBackReturnsToTodayPlan() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("today_plan_open_check_in")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("check_in_screen").assertExists()
        composeRule.onNodeWithTag("check_in_back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
        composeRule.onNodeWithTag("check_in_screen").assertDoesNotExist()
        AppTab.entries.forEach { tab ->
            composeRule.onNodeWithTag("app_tab_${tab.name.lowercase()}").assertExists()
        }
    }

    @Test
    fun aiTabOpensTheAiLearningScreen() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onNodeWithTag("ai_learning_header").assertExists()
        // 功能列表默认收起在展开键后面，先展开再断言入口存在。
        composeRule.onNodeWithTag("ai_learning_features_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        AiFeature.entries.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").assertExists()
        }
        // 换 tab 之后学习页应当已经让位，不能被压在下面继续占位。
        composeRule.onNodeWithTag("today_plan_summary").assertDoesNotExist()
    }

    @Test
    fun settingsTabOpensTheSettingsScreenAndKeepsTheWorksheetEntry() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertExists()
        composeRule.onNodeWithTag("settings_open_worksheet").assertExists().assertHasClickAction()
    }

    @Test
    fun speechStatusUsesLiveSpeechKeyStateAndShowsCurrentProvider() {
        val fixture = speechFixture()
        composeRule.setContent { readyAppScreen(fixture.speech, fixture.ai) }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo()
        composeRule.onNodeWithText("当前供应商：小米 MiMo").assertExists()
        // 新层级：一级设置页不再出现引擎状态行与「待接入」厂商占位行。
        composeRule.onNodeWithTag("settings_speech_openai").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_speech_mimo").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_speech_zipvoice").assertDoesNotExist()
        composeRule.onNodeWithText("当前供应商 · 已配置").assertDoesNotExist()
        composeRule.onNodeWithText("待接入").assertDoesNotExist()
    }

    /**
     * M6（Task 9 必须项）：语音设置页的语速行**不是静默失效**。
     *
     * 为什么断在 AppScreen 这一层而不是只断 `SpeechSettingsScreen`：
     * `SpeechSettingsScreen` 与 `AppScreen` 两侧的 `onCycleSpeechRate` 都带默认空 lambda
     * （`AppScreen.kt` 里那一行 `onCycleSpeechRate = { speechSettingsViewModel?.cycleSpeechRate() }`
     * 被整条删掉，屏级用例照旧全绿，用户点语速却永远不变）。本用例走真实宿主，
     * 断言因此落在这条接线上：删掉它本用例必须红（Task 9 报告的删行探针即以此取证）。
     *
     * 「恰好一次点按前进一档」钉的是宿主接线：`AppScreen` 把 `onCycleSpeechRate` 接到
     * `viewModel::cycleSpeechRate`，且每次一点只前进一步（持久化本身由
     * `SpeechSettingsViewModelTest` 覆盖——本 fixture 的偏好仓储 `save` 是空实现，不能在这里
     * 声称「值来自持久化偏好」）。若宿主或屏幕重复派发，会直接跳到 1.5x。
     * 四档循环口径 {0.75x, 1.0x, 1.25x, 1.5x} 见决策 2026-10-15 §2。
     */
    @Test
    fun speechRateRowOnTheHostedSpeechPageCyclesOneStepPerPress() {
        val fixture = speechFixture()
        composeRule.setContent { readyAppScreen(fixture.speech, fixture.ai) }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()

        // 未合并树：当前倍速文本在可点击 Row 内会被语义合并（与 speech_auto_speak_state 同一口径）。
        // 滚动只作用在可点击 Row 上（未合并子节点不带滚动语义）。
        composeRule.onNodeWithTag("speech_rate_toggle").performScrollTo()
        composeRule.onNodeWithTag("speech_rate_value", useUnmergedTree = true).assertTextEquals("1x")
        composeRule.onNodeWithTag("speech_rate_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("speech_rate_value", useUnmergedTree = true).assertTextEquals("1.25x")
    }

    @Test
    fun aiProfilesOpenedFromSpeechReturnToSpeechWithRefreshedCandidates() {
        val fixture = speechFixture()
        fixture.keys.clear()
        composeRule.setContent { readyAppScreen(fixture.speech, fixture.ai) }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()
        // 候选配置现在是折叠二级菜单：先展开再操作。
        // v32 语速行让本页变高，折叠按钮会落在视口外：performClick 前必须 performScrollTo，
        // 否则点击命中别处、展开态不变（与本页其它 scroll+click 用例同一写法）。
        composeRule.onNodeWithTag("speech_toggle_candidates").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_open_ai_profiles").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_profiles_screen").assertExists()
        fixture.keys += "mimo"
        composeRule.onNodeWithContentDescription("返回上一层").performClick()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()
        composeRule.onNodeWithTag("speech_toggle_candidates").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_profile_mimo").performScrollTo()
        composeRule.onNodeWithText("可用").assertExists()
        composeRule.onNodeWithTag("speech_bound_profile_mimo", useUnmergedTree = true).assertExists()
        pressSystemBack()
        composeRule.onNodeWithTag("settings_screen").assertExists()
    }

    @Test
    fun returningFromAiProfilesOpenedDirectlyFromSettingsRefreshesSpeechSummary() {
        val fixture = speechFixture()
        composeRule.setContent { readyAppScreen(fixture.speech, fixture.ai) }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo()
        composeRule.onNodeWithText("当前供应商：小米 MiMo").assertExists()
        composeRule.onNodeWithTag("settings_open_ai_profiles").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_profiles_screen").assertExists()
        fixture.keys.clear()
        pressSystemBack()
        composeRule.onNodeWithTag("settings_screen").assertExists()
        // 一级页不再有引擎状态行；刷新语义下沉到语音二级页——直接进入 AI Profile 改完 Key
        // 返回后再进语音设置，状态必须是新鲜的。
        composeRule.onNodeWithTag("settings_speech_openai").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()
        // 引擎行详情是「配置名 · 密钥状态」格式；Key 被删后必须立刻反映。
        composeRule.onNodeWithTag("speech_engine_status_mimo", useUnmergedTree = true).assertTextEquals("测试语音 · 缺少密钥")
    }

    @Test
    fun returningFromAiProfilesRefreshesRemovedKeyWithoutDroppingSpeechLayer() {
        val fixture = speechFixture()
        composeRule.setContent { readyAppScreen(fixture.speech, fixture.ai) }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_toggle_candidates").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_profile_mimo").performScrollTo()
        composeRule.onNodeWithText("可用").assertExists()
        composeRule.onNodeWithTag("speech_open_ai_profiles").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_profiles_screen").assertExists()
        fixture.keys.clear()
        pressSystemBack()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()
        composeRule.onNodeWithTag("speech_toggle_candidates").performScrollTo().performClick()
        composeRule.onNodeWithTag("speech_profile_mimo").performScrollTo()
        composeRule.onNodeWithTag("speech_engine_status_mimo", useUnmergedTree = true).assertTextEquals("测试语音 · 缺少密钥")
        composeRule.onNodeWithTag("speech_bound_profile_mimo", useUnmergedTree = true).assertExists()
        pressSystemBack()
        composeRule.onNodeWithTag("settings_screen").assertExists()
        composeRule.onNodeWithTag("settings_open_speech").performScrollTo()
        composeRule.onNodeWithText("当前供应商：小米 MiMo").assertExists()
    }

    @Test
    fun readingTabOpensTheReadingScreenWithoutItsOwnBackButton() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("reading_access_screen").assertExists()
        // 一级 tab 没有「上一层」可回，页面上不该出现任何返回入口。
        composeRule.onNodeWithTag("reading_access_back").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回上一层").assertDoesNotExist()
    }

    @Test
    fun openingAnAiFeatureHidesTheBottomBarAndBackReturnsToTheAiTab() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()
        // 功能列表默认收起，先展开才能点具体功能。
        composeRule.onNodeWithTag("ai_learning_features_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_feature_cloze").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_feature_screen").assertExists()
        composeRule.onNodeWithTag("ai_feature_title").assertExists()
        composeRule.onNodeWithTag("app_tab_ai").assertDoesNotExist()

        pressSystemBack()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onNodeWithTag("app_tab_ai").assertExists()
    }

    @Test
    fun systemBackFromANonLearningTabReturnsToTheLearningTab() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertExists()

        pressSystemBack()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
    }

    @Test
    fun startLearningStillHidesTheBottomBarSoTheCardGetsTheWholeScreen() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithContentDescription("开始学习").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_screen").assertExists()
        composeRule.onNodeWithTag("app_tab_learning").assertDoesNotExist()
    }

    @Test
    fun recordingProviderKeepsLegacyWordAndSentenceVerbsDistinct() = kotlinx.coroutines.runBlocking {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)

        provider.speak("legacy")
        provider.speakWord("ability", com.example.englishlearning.language.domain.PronunciationAccent.American)
        provider.speakSentence("Birds fly.", com.example.englishlearning.language.domain.PronunciationAccent.British)

        assertEquals(listOf("legacy"), provider.legacyCalls)
        assertEquals(listOf(Triple("ability", com.example.englishlearning.language.domain.PronunciationAccent.American, false)), provider.wordCalls)
        assertEquals(listOf(Triple("Birds fly.", com.example.englishlearning.language.domain.PronunciationAccent.British, false)), provider.sentenceCalls)
    }

    /** D1 新契约：词卡就绪自动朗读 1 次，用户点击再 1 次，两者共存互不取消。 */
    @Test
    fun learningCardPronunciationRoutesTheVisibleLemmaToTheProvider() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider) }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.isNotEmpty() }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.size == 2 }

        assertEquals(listOf("ability", "ability"), provider.spokenTexts)
        composeRule.onNodeWithTag("word_card_pronunciation_message").assertExists()
        composeRule.onNodeWithText("发音已播放。").assertExists()
    }

    /** D1：自动发音同样走安全文案——失败在词卡就绪时就显示，不泄露 endpoint/密钥。 */
    @Test
    fun learningCardPronunciationFailureShowsSafeMessageAndKeepsFeedbackActions() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Failed(IllegalStateException("secret endpoint")))
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider) }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("发音播放失败，请重试。").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.size == 2 }

        composeRule.onNodeWithText("发音播放失败，请重试。").assertExists()
        composeRule.onNodeWithText("secret endpoint").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("不认识").assertHasClickAction()
        composeRule.onNodeWithContentDescription("模糊").assertHasClickAction()
        composeRule.onNodeWithContentDescription("认识").assertHasClickAction()
    }

    /** D1：词卡就绪自动 1 次 + 详情美式/英式各点击 1 次，全部是同一个 lemma；详情页自身不自动发音。 */
    @Test
    fun submittedCardDetailPronunciationRoutesTheHeldLemmaToTheProvider() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider) }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.isNotEmpty() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_feedback_fuzzy").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_feedback_fuzzy").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("播放 ability 发音").performClick()
        composeRule.onNodeWithContentDescription("播放 ability 英式发音").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.size == 3 }

        assertEquals(listOf("ability", "ability", "ability"), provider.spokenTexts)
        assertEquals(
            listOf(
                com.example.englishlearning.language.domain.PronunciationAccent.American,
                com.example.englishlearning.language.domain.PronunciationAccent.American,
                com.example.englishlearning.language.domain.PronunciationAccent.British,
            ),
            provider.wordCalls.map { it.second },
        )
        composeRule.onNodeWithText("发音已播放。").assertExists()
    }

    /** D1：开关关闭后词卡就绪不自动发音，点击仍恰好触发一次。 */
    @Test
    fun learningAutoSpeakHonoursTheOffToggleAndClickStillSpeaksOnce() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent {
            readyAppScreen(pronunciationProvider = provider, speechSettingsViewModel = speech.speech)
        }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), provider.spokenTexts)

        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.size == 1 }
        assertEquals(listOf("ability"), provider.spokenTexts)
        composeRule.onNodeWithText("发音已播放。").assertExists()
    }

    @Test
    fun learningRecordDetailPronunciationRoutesTheHeldLemmaToTheProvider() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                learningRecordsViewModel = learningRecordsViewModelWithAbilityRecord(),
            )
        }
        createProfile()

        composeRule.onNodeWithContentDescription("学习记录").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("learning_record_row_ability").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("learning_record_row_ability").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("播放 ability 发音").performClick()
        composeRule.onNodeWithContentDescription("播放 ability 英式发音").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts.size == 2 }

        assertEquals(listOf("ability", "ability"), provider.spokenTexts)
        assertEquals(
            listOf(
                com.example.englishlearning.language.domain.PronunciationAccent.American,
                com.example.englishlearning.language.domain.PronunciationAccent.British,
            ),
            provider.wordCalls.map { it.second },
        )
        composeRule.onNodeWithText("发音已播放。").assertExists()
    }

    @Test
    fun readingHighlightDetailPronunciationRoutesSelectedLemmaToProvider() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Failed(IllegalStateException("private endpoint")))
        val reading = readingFixture()
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_open_today").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reading_open_today").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_english").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coverage_popup_close").performClick()
        composeRule.onNodeWithTag("article_english").performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()
        composeRule.onNodeWithContentDescription("播放 apple 发音").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("发音播放失败，请重试。").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("apple"), provider.spokenTexts)
        composeRule.onNodeWithText("private endpoint", substring = true).assertDoesNotExist()
    }

    // ------------------------------------------------------ Task 9 修复轮 1：发音清场与失败保持（I-1/I-2/I-3/I-5/I-6）

    /**
     * I-1：退出学习流（exitLearning）必须清零词卡发音状态。
     * 复现路径：点喇叭播一个挂起音 → 返回今日计划 → 再点「开始学习」。
     * 旧形状里 exitLearning 只清 message 不清 status（仍 Playing），新卡会立刻长出「暂停/停止」行、
     * 但文案节点不出现（message 已 null）——读屏用户听到的是上一张卡的状态语义。
     * 备注：Ready 首卡没有「返回今日计划」按钮（WordCardScreen 只在 NoCards/ReviewCompleted/AllDone
     * 给该按钮），退出走系统返回 → BackHandler(showLearning) → 同一个 exitLearning，路径等价。
     */
    @Test
    fun leavingAndReenteringLearningClearsTheWordCardPronunciationState() {
        val provider = HangingPronunciationProvider()
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent {
            readyAppScreen(pronunciationProvider = provider, speechSettingsViewModel = speech.speech)
        }
        createProfile()
        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_pronunciation_stop").fetchSemanticsNodes().isNotEmpty()
        }

        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_summary").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertDoesNotExist()
        composeRule.onNodeWithText("正在播放发音…").assertDoesNotExist()
        provider.release()
    }

    /**
     * Task 15 Step 1（F5-03 移交账「退出学习不停播」在此关闭）：朗读仍在进行时退出学习流，
     * 停播必须打到 provider 上**恰好一次**，且退出后屏上不残留任何语播控件。
     *
     * 与 I-1（`leavingAndReenteringLearningClearsTheWordCardPronunciationState`）的分工：I-1 只清**呈现状态**
     * （status/message 归 Idle/null），旧形状下退出后界面是干净的、声音却还在放——那正是移交账点名的缺陷。
     * 本用例断播放器层真收到停播：`HangingPronunciationProvider.stopCalls` 是计数假播放器，
     * 「停播逻辑存在但永不触发」那一族变异（写进永假分支、挪去别处、只清状态不碰播放器）都会在这里红；
     * 屏侧的形状与每轮组合次数由 `WordCardScreenTest.leavingTheScreenForwardsTheStopActionExactlyOnce` 负责。
     *
     * 全程走真实导航与真实宿主：自动发音关掉 → 点药片 → provider 挂起在发声里（呈现态钉在 Playing）→
     * 系统返回（`BackHandler(enabled = showLearning)` → `exitLearning` → `showLearning = false` →
     * 词卡屏离开组合 → 退出停播守卫转调宿主的 onStopPronunciation → 播放器收到停播）。
     */
    @Test
    fun leavingLearningStopsTheStillPlayingAudioAtTheProvider() {
        val provider = HangingPronunciationProvider()
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider, speechSettingsViewModel = speech.speech) }
        createProfile()
        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_pronunciation_stop").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("退出之前不该有任何停播，否则本用例断的是别处的停播", 0, provider.stopCalls)

        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_summary").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals("退出学习流必须让仍在播的音停下来，且恰好一次", 1, provider.stopCalls)
        // F5-03「退出后无语播焦点」：三枚语播控件与药片朗读文字都不许残留。
        composeRule.onNodeWithTag("word_card_speak").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertDoesNotExist()
        composeRule.onNodeWithText("播放发音").assertDoesNotExist()
        provider.release()
    }

    /**
     * I-2：阅读页三元组（message/status/request）在**任何**退出路径都必须清场。
     * 三条路径逐一验证：①屏内「返回」按钮（onBack）；②系统返回退今日文章
     * （BackHandler readingTarget → closeArticle）；③系统返回退历史旧版（BackHandler historyArticle）。
     * 旧形状里三条全不清：重进文章（或另一篇）立刻长出上一次的播控行与文案，且代次不自增还挡不住
     * 迟到的 Played。每轮清场后重进都断三个 tag 不存在。
     */
    @Test
    fun leavingTheArticleThroughEveryExitPathClearsItsPronunciationPresentation() {
        val provider = HangingPronunciationProvider()
        val reading = readingFixture()
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_open_today").fetchSemanticsNodes().isNotEmpty()
        }

        // 轮 1：屏内「返回」按钮退出。
        openTodayArticle()
        speakArticleAndAwaitControls()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_access_screen").fetchSemanticsNodes().isNotEmpty()
        }
        openTodayArticle()
        assertNoArticlePronunciationNodes()

        // 轮 2：系统返回键退今日文章（BackHandler :493 closeArticle 分支）。
        speakArticleAndAwaitControls()
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_access_screen").fetchSemanticsNodes().isNotEmpty()
        }
        openTodayArticle()
        assertNoArticlePronunciationNodes()

        // 轮 3：先退出仍开着的文章，再走历史旧版（historyArticle）用系统返回退出
        // （BackHandler :489 分支）。
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_access_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reading_history_entry").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_history_item_a1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reading_history_item_a1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_english").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coverage_popup_close").performClick()
        speakArticleAndAwaitControls()
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_history_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reading_history_item_a1").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_english").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coverage_popup_close").performClick()
        assertNoArticlePronunciationNodes()
        provider.release()
    }

    /** 今日文章入口：开文章并关掉进文覆盖词弹窗（弹窗每次进组合都会重弹，见 rememberSaveable 口径）。 */
    private fun openTodayArticle() {
        composeRule.onNodeWithTag("reading_open_today").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_english").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coverage_popup_close").performClick()
    }

    /** 点「发音」朗读整篇（挂起 provider 钉住 Playing），直到瞬时消息节点出现。 */
    private fun speakArticleAndAwaitControls() {
        composeRule.onNodeWithTag("article_pronunciation").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_pronunciation_message").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNoArticlePronunciationNodes() {
        composeRule.onNodeWithTag("article_pronunciation_message").assertDoesNotExist()
        composeRule.onNodeWithTag("article_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("article_pronunciation_stop").assertDoesNotExist()
    }

    /**
     * I-3：系统返回键关闭阅读生词详情时必须清**共享详情状态**（detailPronunciation*）。
     * 屏内 onBack/onNext 与记录宿主都清了，唯独 BackHandler(selectedArticleCard) 漏了。
     * 「另一处详情」选学习流宿主：记录/词书宿主打开详情时会顺手复位（掩盖缺陷），
     * 学习流详情则是纯消费者——旧形状下它会把上一词残留的 Playing 原样端出来。
     */
    @Test
    fun systemBackClosingAReadingWordDetailClearsTheSharedDetailPronunciationState() {
        val provider = HangingPronunciationProvider()
        val reading = readingFixture()
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        openReadingWordDetail()
        composeRule.onNodeWithContentDescription("播放 apple 发音").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_pronunciation_message").fetchSemanticsNodes().isNotEmpty()
        }

        // 系统返回键关详情（BackHandler :494——本轮修复的收口点）。
        pressSystemBack()
        composeRule.onNodeWithTag("card_detail_screen").assertDoesNotExist()

        // 退文章 → 今日计划 → 进学习流，提交一张卡打开「下一处详情」（学习流宿主）。
        pressSystemBack()
        composeRule.onNodeWithTag("app_tab_learning").performClick()
        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_feedback_fuzzy").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_feedback_fuzzy").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_stop").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_message").assertDoesNotExist()
        provider.release()
    }

    /**
     * I-5（决策 §2「播控失败的状态机口径」）：暂停未被引擎接受（pausePlayback() 返回 false，
     * 即系统 TTS 无公开 pause()/resume() 的平台事实）时状态必须**保持 Playing**——
     * 落 Idle 会让整组控件（含唯一可用的「停止」）在朗读仍在继续时消失，与「停止始终可达」相悖。
     * 走真实宿主（AppScreen 词卡 onPauseResume）：旧形状 `else -> Idle` 下本用例必红。
     * 说明：brief 曾允许「组件级 harness 复刻 when」，但 harness 复刻对 AppScreen 真实代码恒绿、
     * 不具证伪力，按「必须 RED」的硬口径上收为宿主级用例。
     */
    @Test
    fun aPauseRejectedByTheEngineKeepsPlayingSoStopStaysReachable() {
        val provider = HangingPronunciationProvider()
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent {
            readyAppScreen(pronunciationProvider = provider, speechSettingsViewModel = speech.speech)
        }
        createProfile()
        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_pronunciation_toggle").fetchSemanticsNodes().isNotEmpty()
        }

        // 点暂停：provider.pausePlayback() 返回 false（未被引擎接受）→ 状态保持 Playing。
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_pronunciation_stop").assertIsDisplayed()
        composeRule.onNodeWithTag("word_card_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()
        provider.release()
    }

    /**
     * N-1（T9 复评 Minor）：I-5 的「暂停被拒 → 保持原状态」在决策 §2 下有**五处**宿主
     * （记录详情 / 词卡 / 收藏详情 / 阅读整篇 / 阅读点词详情，均写作 `else -> 原状态`），
     * 修复轮只钉了词卡宿主一处。本例把**阅读整篇宿主**与下一例的**阅读点词详情宿主**补上，
     * 形状与 I-5 完全一致：恒返回 false 的挂起 provider + 真实宿主导航 + 点暂停后断
     * 「停止/暂停仍 `assertIsDisplayed()`」且状态文案仍是「正在播放发音…」
     * （落 Paused 会变「已暂停播放…」，落 Idle 则整组与文案一起消失）。
     * 宿主行号不写死（派发实测锚点已随注释漂移，一律以 grep `else -> ` 为准）。
     * 证伪口径（伪修反证，报告 §N-1）：把整篇宿主的 `else -> articlePronunciationStatus` 改成
     * `else -> PronunciationStatus.Idle` 后本例必红——失败信息即「整组消失」。
     */
    @Test
    fun anArticlePauseRejectedByTheEngineKeepsPlayingSoStopStaysReachable() {
        val provider = HangingPronunciationProvider()
        val reading = readingFixture()
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                speechSettingsViewModel = speech.speech,
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_open_today").fetchSemanticsNodes().isNotEmpty()
        }
        openTodayArticle()
        speakArticleAndAwaitControls()

        composeRule.onNodeWithTag("article_pronunciation_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("article_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("article_pronunciation_stop").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()
        provider.release()
    }

    /**
     * N-1 第二处：阅读页点词详情宿主（`AppScreen.kt:1472` `else -> held`）。
     * 走真实导航（今日文章 → 关覆盖窗 → 点正文词 → 详情喇叭），点暂停后播控组与 Playing 文案都必须在；
     * 该宿主与整篇宿主**不共用状态变量**（详情用 `detailPronunciationMessage`），故必须单独钉。
     */
    @Test
    fun aReadingWordDetailPauseRejectedByTheEngineKeepsPlayingSoStopStaysReachable() {
        val provider = HangingPronunciationProvider()
        val reading = readingFixture()
        val speech = speechFixture(autoSpeakOnCardReady = false)
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                speechSettingsViewModel = speech.speech,
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        openReadingWordDetail()
        composeRule.onNodeWithContentDescription("播放 apple 发音").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_pronunciation_toggle").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("card_detail_pronunciation_stop").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()
        provider.release()
    }

    /**
     * I-6 宿主级接线钉：详情三宿主的 `pronunciationControlsEnabled = true` 之一（记录详情）+
     * 停止收口。Playing 下播控组存在（MiMo 引擎 → 暂停可用），点停止后整组与文案离开组合、
     * provider.stopPlayback 恰调用一次。
     */
    @Test
    fun hostedRecordDetailShowsControlsWhilePlayingAndStopClosesTheGroup() {
        val provider = HangingPronunciationProvider()
        val speech = speechFixture()
        composeRule.setContent {
            readyAppScreen(
                pronunciationProvider = provider,
                speechSettingsViewModel = speech.speech,
                learningRecordsViewModel = learningRecordsViewModelWithAbilityRecord(),
            )
        }
        createProfile()
        composeRule.onNodeWithContentDescription("学习记录").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("learning_record_row_ability").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("learning_record_row_ability").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("播放 ability 发音").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_pronunciation_stop").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertIsDisplayed()
        composeRule.onNodeWithText("正在播放发音…").assertIsDisplayed()

        composeRule.onNodeWithTag("card_detail_pronunciation_stop").performClick()
        composeRule.waitForIdle()
        assertEquals(1, provider.stopCalls)
        composeRule.onNodeWithTag("card_detail_pronunciation_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_stop").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_pronunciation_message").assertDoesNotExist()
        provider.release()
    }

    @Test
    fun wordQaOverlayOpensFromReadingCardDetailAndAsksThroughViewModel() {
        val reading = readingFixture()
        composeRule.setContent {
            readyAppScreen(
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
                wordQaViewModel = wordQaViewModelWith(DefaultTextProfileResult.NoSelection),
            )
        }
        openReadingWordDetail()

        composeRule.onNodeWithContentDescription("问 AI（apple）").performClick()
        composeRule.onNodeWithTag("word_qa_screen").assertExists()
        composeRule.onNodeWithTag("word_qa_chip_Sentence").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_qa_not_configured").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_qa_back").performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()
        assert(composeRule.onAllNodesWithTag("word_qa_screen").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun wordQaOutboundConfirmationWiredThroughOverlayBeforeAnyNetwork() {
        val reading = readingFixture()
        composeRule.setContent {
            readyAppScreen(
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
                wordQaViewModel = wordQaViewModelWith(DefaultTextProfileResult.Selected(wordQaTextProfile())),
            )
        }
        openReadingWordDetail()

        composeRule.onNodeWithContentDescription("问 AI（apple）").performClick()
        composeRule.onNodeWithTag("word_qa_chip_Mnemonic").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_qa_outbound_confirmation_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("api.example.com", substring = true).assertExists()
        composeRule.onNodeWithTag("word_qa_decline_outbound").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_qa_chip_Mnemonic").assertIsEnabled()
        assert(composeRule.onAllNodesWithTag("word_qa_outbound_confirmation_text").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun lockedReadingTabStillListsTheLocalHistory() {
        // 真机实证的缺陷：今日计划未完成时 readingState=Locked，而 AppScreen 只用
        // `(readingState as? Ready)?.history` 取历史 → 点「查看本地阅读历史」恒显示
        // 「暂无本地文章」，可同一个库里确有文章。AGENTS.md：历史已解锁文章始终可重读，
        // 未解锁只限制「当天新文章生成入口」。
        val reading = readingFixture(unlocked = false)
        composeRule.setContent {
            readyAppScreen(
                readingAccessViewModel = reading.access,
                articleReadingViewModel = reading.article,
                todayPlanViewModel = reading.todayPlan,
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_access_locked_reason").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("reading_history_entry").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("reading_history_screen").assertExists()
        composeRule.onNodeWithTag("reading_history_item_a1").assertExists().assertHasClickAction()
        composeRule.onNodeWithText("Apple Day", substring = true).assertExists()
        composeRule.onNodeWithText("暂无本地文章", substring = true).assertDoesNotExist()
    }

    /** 阅读流导航到点词详情：profile → 阅读 tab → 今日文章 → 关覆盖窗 → 点正文词。 */
    private fun openReadingWordDetail() {
        createProfile()
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("reading_open_today").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reading_open_today").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("article_english").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coverage_popup_close").performClick()
        composeRule.onNodeWithTag("article_english").performClick()
        composeRule.onNodeWithTag("card_detail_screen").assertExists()
    }

    /** 词问答 ViewModel 夹具：transport 一旦被调用就失败——出站确认前零字节的证明。 */
    private fun wordQaViewModelWith(selection: DefaultTextProfileResult): WordAiQaViewModel {
        val transport = object : AiHttpTransport {
            override suspend fun send(request: AiHttpRequest) =
                error("word QA wiring test must not perform network requests")
        }
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf('k'))
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(true)
        })
        val notes = object : WordAiNoteRepository {
            override suspend fun save(note: WordAiNote) = Result.success(Unit)
            override suspend fun list(profileId: String, lemma: String, wordBookId: String, cardId: String) = Result.success(emptyList<WordAiNote>())
            override suspend fun delete(profileId: String, noteId: String) = Result.success(Unit)
        }
        return WordAiQaViewModel(
            WordQaUseCase(DefaultTextProfileResolver { selection }, secrets, transport),
            notes,
            WordNoteIdFactory { "note-1" },
            FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC),
        )
    }

    private fun wordQaTextProfile() = AiProfile(
        profileId = "qa",
        displayName = "问答配置",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "gpt-test",
        capabilities = setOf(AiCapability.Text),
        secretReference = AiProfileSecretUseCase.referenceFor("qa"),
    )

    @Test
    fun sentenceAnalysisFeatureRoutesToItsRealScreenAndAsksThroughViewModel() {
        composeRule.setContent {
            readyAppScreen(
                sentenceAnalysisViewModel = sentenceAnalysisViewModelWith(DefaultTextProfileResult.NoSelection),
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_learning_features_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_feature_sentence-analysis").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sentence_analysis_screen").assertExists()
        composeRule.onNodeWithTag("app_tab_ai").assertDoesNotExist()

        composeRule.onNodeWithTag("sentence_analysis_input").performTextInput("Birds fly.")
        composeRule.onNodeWithTag("sentence_analysis_analyze").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("sentence_analysis_not_configured").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sentence_analysis_back").performClick()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onAllNodesWithTag("sentence_analysis_screen").fetchSemanticsNodes().isEmpty()
    }

    /** 长难句分析 ViewModel 夹具：transport 一旦被调用就失败——出站确认前零字节的证明。 */
    private fun sentenceAnalysisViewModelWith(selection: DefaultTextProfileResult): SentenceAnalysisViewModel {
        val transport = object : AiHttpTransport {
            override suspend fun send(request: AiHttpRequest) =
                error("sentence analysis wiring test must not perform network requests")
        }
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf('k'))
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(true)
        })
        return SentenceAnalysisViewModel(
            SentenceAnalysisUseCase(DefaultTextProfileResolver { selection }, secrets, transport),
        )
    }

    @Test
    fun imageStudioFeatureRoutesToItsRealScreenAndAsksThroughViewModel() {
        composeRule.setContent {
            readyAppScreen(
                imageStudioViewModel = imageStudioViewModelWith(DefaultTextProfileResult.NoSelection),
            )
        }
        createProfile()
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_learning_features_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_feature_image-studio").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("image_studio_screen").assertExists()
        composeRule.onNodeWithTag("app_tab_ai").assertDoesNotExist()

        composeRule.onNodeWithTag("image_studio_subject_input").performTextInput("苹果")
        composeRule.onNodeWithTag("image_studio_generate_prompt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("image_studio_not_configured").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("image_studio_back").performClick()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onAllNodesWithTag("image_studio_screen").fetchSemanticsNodes().isEmpty()
    }

    /** 生图 ViewModel 夹具：两个出站通道一旦被调用就失败——确认前零字节的证明。 */
    private fun imageStudioViewModelWith(selection: DefaultTextProfileResult): ImageStudioViewModel {
        val transport = object : AiHttpTransport {
            override suspend fun send(request: AiHttpRequest) =
                error("image studio wiring test must not perform network requests")
        }
        val audioTransport = object : AudioHttpTransport {
            override suspend fun send(request: AudioHttpRequest) =
                error("image studio wiring test must not download images")
        }
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf('k'))
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(true)
        })
        val directory = java.io.File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "image-studio-wiring",
        )
        return ImageStudioViewModel(
            drawPrompt = DrawingPromptUseCase(DefaultTextProfileResolver { selection }, secrets, transport),
            generateImage = ImageGenerationUseCase(
                DefaultImageProfileResolver { DefaultImageProfileResult.NoSelection },
                secrets,
                transport,
            ),
            store = GeneratedImageStore(audioTransport, directory),
        )
    }

    private data class ReadingFixture(        val access: ReadingAccessViewModel,
        val article: ArticleReadingViewModel,
        val todayPlan: TodayPlanViewModel,
    )

    private fun readingFixture(unlocked: Boolean = true): ReadingFixture {
        // 固定时钟锚定「今日」：Asia/Shanghai 下 Instant.EPOCH 的本地日就是 1970-01-01，
        // 计划 localDate 与文章 localDate 必须等于它。仓储 fake 按真实检索键校验，
        // 键不匹配一律返回空——无条件返回文章会把生产代码的日期/身份错配掩盖成绿灯。
        val plan = TodayPlan(
            planId = "plan-1",
            profileId = "default",
            localDate = java.time.LocalDate.of(1970, 1, 1),
            zoneId = "Asia/Shanghai",
            activeWordBookId = "cet4",
            newTarget = 1,
            dueTarget = 0,
            newCardIds = listOf("apple-id"),
            dueCardIds = emptyList(),
            ruleVersion = "v1",
            generatedAt = java.time.Instant.EPOCH,
        )
        val article = Article(
            articleId = "a1", profileId = "default", localDate = plan.localDate.toString(),
            activeWordBookId = plan.activeWordBookId, articleType = ArticleType.STORY,
            lengthTier = ArticleLengthTier.STANDARD, version = 1, title = "Apple Day",
            englishText = "apple", chineseText = "苹果", generatedAtEpochMillis = 1,
            coveredLemmas = listOf("apple"), source = ArticleSource.UserImported,
        )
        // unlocked=false 时今日计划一个词都没完成（newTarget=1）→ UnlockPolicy 判锁定。
        // 锁定与否只改完成集合，不改历史来源：两种情况都得能读到那篇文章。
        val completed = if (unlocked) listOf("apple-id") else emptyList()
        val events = object : LearningEventRepository by NoopLearningEventRepository() {
            override suspend fun completedCardIds(planId: String) =
                RepositoryResult.Success(if (planId == plan.planId) completed else emptyList())
        }
        val plans = object : TodayPlanRepository {
            override suspend fun find(profileId: String, localDate: java.time.LocalDate) =
                if (profileId == plan.profileId && localDate == plan.localDate) TodayPlanResult.Ready(plan)
                else TodayPlanResult.NotFound
            override suspend fun findLatest(profileId: String) =
                if (profileId == plan.profileId) TodayPlanResult.Ready(plan) else TodayPlanResult.NotFound
            override suspend fun saveIfAbsent(plan: TodayPlan) = TodayPlanResult.Ready(plan)
        }
        val articles = object : ArticleRepository {
            override suspend fun saveNewVersion(article: Article) = Result.success(article)
            override suspend fun findLatest(
                profileId: String, localDate: String, activeWordBookId: String,
                articleType: ArticleType, lengthTier: ArticleLengthTier,
            ) = if (profileId == plan.profileId && localDate == plan.localDate.toString() &&
                activeWordBookId == plan.activeWordBookId && articleType == ArticleType.STORY &&
                lengthTier == ArticleLengthTier.STANDARD
            ) {
                Result.success(article)
            } else {
                Result.success(null)
            }
            override suspend fun findHistory(profileId: String) = Result.success(listOf(article))
            override suspend fun findBySourceUrl(url: String) = Result.success<Article?>(null)
        }
        val preferences = object : ReadingPreferenceRepository {
            override suspend fun getPreference(profileId: String) =
                if (profileId == plan.profileId) Result.success(ReadingPreference(profileId))
                else Result.failure(IllegalStateException("unexpected reading profileId"))
            override suspend fun savePreference(preference: ReadingPreference) = Result.success(Unit)
        }
        val cards = object : com.example.englishlearning.learning.WordCardSource {
            override suspend fun cardIds(wordBookId: String) =
                if (wordBookId == plan.activeWordBookId) listOf("apple-id") else emptyList()
            override suspend fun cards(cardIds: List<String>) = cardIds.mapNotNull { id ->
                if (id == "apple-id") com.example.englishlearning.learning.domain.WordCard(id, "cet4", "apple", "", "", "苹果") else null
            }
        }
        val clock = FixedClockProvider(plan.generatedAt, java.time.ZoneId.of(plan.zoneId))
        val ids = ArticleIdFactory { "new-article" }
        val offline = object : AiHttpTransport {
            override suspend fun send(request: AiHttpRequest): com.example.englishlearning.ai.net.AiHttpResult =
                error("Reading pronunciation test must not perform network requests")
        }
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf())
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(false)
        })
        val generated = GenerateArticleUseCase(
            DefaultTextProfileResolver { DefaultTextProfileResult.NoSelection }, secrets, offline, articles, ids, { clock.instant() },
        )
        return ReadingFixture(
            ReadingAccessViewModel(
                articles, preferences, plans, events, cards, generated,
                FetchArticleUseCase(offline, articles, ids, { clock.instant() }),
                ImportArticleUseCase(articles, plans, events, cards, ids, { clock.instant() }), clock,
            ),
            ArticleReadingViewModel(preferences, object : ReadingCompletionRepository {
                override suspend fun record(article: Article) = Result.success(true)
                override suspend fun completionsToday(profileId: String) = Result.success(0)
            }),
            TodayPlanViewModel({ TodayPlanResult.Ready(plan) }, FakeLearningProfileRepository(), events),
        )
    }

    private class RecordingPronunciationProvider(
        private val result: PronunciationResult,
    ) : PronunciationProvider {
        val spokenTexts = mutableListOf<String>()
        val wordCalls = mutableListOf<Triple<String, com.example.englishlearning.language.domain.PronunciationAccent, Boolean>>()
        val sentenceCalls = mutableListOf<Triple<String, com.example.englishlearning.language.domain.PronunciationAccent, Boolean>>()
        val legacyCalls = mutableListOf<String>()

        override fun capabilities() = setOf(PronunciationCapability.RemoteAudio)

        override suspend fun speak(text: String): PronunciationResult {
            legacyCalls += text
            return result
        }

        override suspend fun speakWord(
            text: String,
            accent: com.example.englishlearning.language.domain.PronunciationAccent,
            forceRegenerate: Boolean,
            rate: Float,
        ): PronunciationResult {
            spokenTexts += text
            wordCalls += Triple(text, accent, forceRegenerate)
            return result
        }

        override suspend fun speakSentence(
            text: String,
            accent: com.example.englishlearning.language.domain.PronunciationAccent,
            forceRegenerate: Boolean,
        ): PronunciationResult {
            sentenceCalls += Triple(text, accent, forceRegenerate)
            return result
        }
    }

    /**
     * 可控挂起发音 provider：`speak*` 一路挂到 `release()` 为止——设备用例用它把呈现状态钉在
     * Playing（speak 前置），从而能在「仍在播」窗口里断清场与失败保持；迟到结果即使 release
     * 也被各宿主的代次/引用守卫挡住，不会污染断言。
     * `pausePlayback()/resumePlayback()` 恒返回 false——正是决策 §2 记录的平台事实
     * （`android.speech.tts.TextToSpeech` 无公开 pause()/resume()，引擎未接受）；
     * `stopPlayback()` 计数，供宿主级停止收口用例断恰好一次。
     */
    private class HangingPronunciationProvider : PronunciationProvider {
        private val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var stopCalls = 0
            private set

        override fun capabilities() = setOf(PronunciationCapability.RemoteAudio)

        override suspend fun speak(text: String): PronunciationResult {
            gate.await()
            return PronunciationResult.Played
        }

        override fun pausePlayback(): Boolean = false

        override fun resumePlayback(): Boolean = false

        override fun stopPlayback() {
            stopCalls++
        }

        fun release() {
            gate.complete(Unit)
        }
    }

    private data class SpeechFixture(
        val speech: SpeechSettingsViewModel,
        val ai: AiProfileSettingsViewModel,
        val keys: MutableSet<String>,
    )

    private fun speechFixture(autoSpeakOnCardReady: Boolean = true): SpeechFixture {
        val keys = mutableSetOf<String>()
        // OpenAI 引擎选项已移除：fixture 改用 MiMo 引擎 + XIAOMI_MIMO 协议 Profile，
        // 与真实存储路径（语音页只能选中系统 TTS / MiMo）保持一致。
        val profile = AiProfile(
            profileId = "mimo",
            displayName = "测试语音",
            websiteUrl = "https://example.com",
            endpoint = "https://api.example.com/v1",
            model = "tts-1",
            capabilities = setOf(AiCapability.Speech),
            secretReference = AiProfileSecretUseCase.referenceFor("mimo"),
            providerKind = AiProviderKind.XIAOMI_MIMO,
        )
        val profiles = object : AiProfileRepository {
            override suspend fun list() = Result.success(listOf(profile))
            override suspend fun find(profileId: String) = Result.success(profile.takeIf { it.profileId == profileId })
            override suspend fun save(profile: AiProfile) = Result.success(Unit)
            override suspend fun delete(profileId: String) = Result.success(Unit)
        }
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf())
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(reference.alias.removePrefix("ai-profile-") in keys)
        })
        val preferences = object : SpeechPreferenceRepository {
            override suspend fun get() = Result.success(
                SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "mimo", autoSpeakOnCardReady = autoSpeakOnCardReady),
            )
            override suspend fun save(preference: SpeechPreference) = Result.success(Unit)
        }
        return SpeechFixture(
            SpeechSettingsViewModel(
                profiles,
                secrets,
                preferences,
                AiProfileIdFactory { "new-id" },
                object : PronunciationProvider {
                    override fun capabilities() = setOf(PronunciationCapability.RemoteAudio)
                    override suspend fun speak(text: String) = PronunciationResult.Played
                },
            ),
            AiProfileSettingsViewModel(profiles, secrets, AiProfileIdFactory { "new-id" }, object : AiPreferenceRepository {
                override suspend fun get() = Result.success(AiPreference())
                override suspend fun save(preference: AiPreference) = Result.success(Unit)
            }),
            keys,
        ).also { it.keys += "mimo" }
    }

    private fun wordBookWordsViewModelWithAlphabetThroughW(wRows: Int = 1): WordBookWordsViewModel {
        val lemmas = ('A'..'V').map { "${it.lowercaseChar()}word" } +
            listOf("wonder") + (2..wRows).map { "wword$it" }
        val words = lemmas.map { lemma ->
            val cardId = "test-book:$lemma"
            WordBookRecord(
                card = com.example.englishlearning.learning.domain.WordCard(cardId, "test-book", lemma, "", "", "测试释义"),
                cardId = cardId,
                displayLemma = lemma,
                status = com.example.englishlearning.learning.WordBookRecordStatus.Unlearned,
            )
        }
        val repository = object : LearningRecordRepository {
            override suspend fun today(profileId: String, planId: String) = RepositoryResult.Success(emptyList<LearningRecord>())
            override suspend fun history(profileId: String) = RepositoryResult.Success(emptyList<LearningHistoryGroup>())
            override suspend fun allWords(wordBookId: String, now: java.time.Instant) =
                if (wordBookId == "test-book") RepositoryResult.Success(words)
                else RepositoryResult.Success(emptyList<WordBookRecord>())
        }
        return WordBookWordsViewModel(repository, FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC))
    }

    private fun learningRecordsViewModelWithAbilityRecord(): LearningRecordsViewModel {
        val cardId = PlaceholderWordCardSource.cardId("cet4", "ability")
        val card = kotlinx.coroutines.runBlocking { PlaceholderWordCardSource().cards(listOf(cardId)) }.first()
        val record = LearningRecord(
            cardId = card.cardId,
            displayLemma = card.lemma,
            wordCard = card,
            feedback = CardFeedback.Fuzzy,
            occurredAt = java.time.Instant.EPOCH,
            localDate = java.time.LocalDate.of(1970, 1, 1),
            planId = "plan-1",
        )
        val repository = object : LearningRecordRepository {
            override suspend fun today(profileId: String, planId: String) =
                if (planId == "plan-1") RepositoryResult.Success(listOf(record))
                else RepositoryResult.Success(emptyList())
            override suspend fun history(profileId: String) =
                RepositoryResult.Success(emptyList<LearningHistoryGroup>())
            override suspend fun allWords(wordBookId: String, now: java.time.Instant) =
                RepositoryResult.Success(emptyList<WordBookRecord>())
        }
        val stats = object : com.example.englishlearning.learning.LearningStatsRepository {
            override suspend fun today(profileId: String, localDate: java.time.LocalDate) =
                Result.failure<com.example.englishlearning.learning.domain.DailyLearningStats>(UnsupportedOperationException())
            override suspend fun range(profileId: String, from: java.time.LocalDate, to: java.time.LocalDate) =
                Result.failure<List<com.example.englishlearning.learning.domain.DailyLearningStats>>(UnsupportedOperationException())
            override suspend fun historyRange(profileId: String, from: java.time.LocalDate, to: java.time.LocalDate) =
                Result.success(emptyList<com.example.englishlearning.learning.domain.DailyLearningHistory>())
            override suspend fun totals(profileId: String) =
                Result.failure<com.example.englishlearning.learning.domain.LearningTotals>(UnsupportedOperationException())
            override suspend fun streak(profileId: String, today: java.time.LocalDate) =
                Result.failure<Int>(UnsupportedOperationException())
        }
        return LearningRecordsViewModel(repository, stats, FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC))
    }

    @Composable
    private fun readyAppScreen(
        speechSettingsViewModel: SpeechSettingsViewModel? = null,
        aiProfileViewModel: AiProfileSettingsViewModel? = null,
        pronunciationProvider: PronunciationProvider? = null,
        todayPlanViewModel: TodayPlanViewModel? = null,
        readingAccessViewModel: ReadingAccessViewModel? = null,
        articleReadingViewModel: ArticleReadingViewModel? = null,
        wordQaViewModel: WordAiQaViewModel? = null,
        sentenceAnalysisViewModel: SentenceAnalysisViewModel? = null,
        imageStudioViewModel: ImageStudioViewModel? = null,
        learningRecordsViewModel: LearningRecordsViewModel? = null,
        wordBookWordsViewModel: WordBookWordsViewModel? = null,
    ) {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        AppScreen(
            viewModel = vm,
            learningSetupViewModel = setupViewModel(),
            todayPlanViewModel = todayPlanViewModel ?: TodayPlanViewModel({ placeholderCardPlan() }, FakeLearningProfileRepository()),
            wordCardViewModel = wordCardFixtureViewModel(),
            readingAccessViewModel = readingAccessViewModel,
            articleReadingViewModel = articleReadingViewModel,
            speechSettingsViewModel = speechSettingsViewModel,
            aiProfileViewModel = aiProfileViewModel,
            pronunciationProvider = pronunciationProvider,
            wordQaViewModel = wordQaViewModel,
            sentenceAnalysisViewModel = sentenceAnalysisViewModel,
            imageStudioViewModel = imageStudioViewModel,
            learningRecordsViewModel = learningRecordsViewModel,
            wordBookWordsViewModel = wordBookWordsViewModel,
        )
    }

    private fun createProfile() {
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
    }

    private fun todayPlanViewModel(): TodayPlanViewModel = TodayPlanViewModel({ TodayPlanResult.MissingLearningSetup }, FakeLearningProfileRepository())

    @Test
    fun readyPlanEntryReopensSetupAndSavingReturnsToTodayPlan() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        var invocations = 0
        val todayPlan = TodayPlanViewModel({ invocations++; readyPlanResult() }, FakeLearningProfileRepository())
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlan, wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()

        composeRule.onNodeWithContentDescription("调整词书与目标").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()

        composeRule.onNodeWithContentDescription("保存学习设置").assertExists().performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
        assertEquals(2, invocations)
    }

    @Test
    fun optionalSetupCanBeCancelledBackToTodayPlanWithoutReloading() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        var invocations = 0
        val todayPlan = TodayPlanViewModel({ invocations++; readyPlanResult() }, FakeLearningProfileRepository())
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlan, wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("调整词书与目标").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()

        composeRule.onNodeWithContentDescription("返回上一层").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
        assertEquals(1, invocations)
    }

    @Test
    fun mandatorySetupOffersNoCancelEntry() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        composeRule.setContent { AppScreen(viewModel = vm, learningSetupViewModel = setupViewModel(), todayPlanViewModel = todayPlanViewModel(), wordCardViewModel = wordCardFixtureViewModel()) }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()
        composeRule.onNodeWithContentDescription("返回上一层").assertDoesNotExist()
    }

    @Test
    fun startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        val todayPlan = TodayPlanViewModel({ placeholderCardPlan() }, FakeLearningProfileRepository())
        composeRule.setContent {
            AppScreen(
                viewModel = vm,
                learningSetupViewModel = setupViewModel(),
                todayPlanViewModel = todayPlan,
                wordCardViewModel = wordCardFixtureViewModel(),
            )
        }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("开始学习").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_screen").assertExists()
        composeRule.onNodeWithText("ability").assertExists()
        composeRule.onNodeWithContentDescription("不认识").assertExists()

        // The card is on its first (Ready) card, which offers no "back to plan" button; leaving
        // the flow is a system-back action handled by AppScreen's BackHandler.
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
    }

    @Test
    fun returningFromLearningReloadsTodayPlan() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        var invocations = 0
        val todayPlan = TodayPlanViewModel({ invocations++; placeholderCardPlan() }, FakeLearningProfileRepository())
        composeRule.setContent {
            AppScreen(
                viewModel = vm,
                learningSetupViewModel = setupViewModel(),
                todayPlanViewModel = todayPlan,
                wordCardViewModel = wordCardFixtureViewModel(),
            )
        }
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        // Initial load once the profile becomes Ready.
        assertEquals(1, invocations)

        composeRule.onNodeWithContentDescription("开始学习").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_screen").assertExists()

        // Leaving via system back must recompute today's progress (F1-04: re-entering the today
        // page recomputes state), otherwise the plan stays stale after reviewing cards.
        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("today_plan_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
        assertEquals(2, invocations)
    }

    /**
     * Drives a real system back on the host Activity, exercising the same
     * OnBackPressedDispatcher path that AppScreen's `BackHandler` listens on. `Espresso.pressBack()`
     * is not usable here (espresso-core is only on the androidTest runtime classpath, not the
     * compile classpath), so the key event is injected directly through the instrumentation.
     */
    private fun pressSystemBack() {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()
    }

    private fun readyPlanResult(): TodayPlanResult.Ready = readyPlanResult("test-book")

    private fun readyPlanResult(wordBookId: String): TodayPlanResult.Ready = TodayPlanResult.Ready(
        TodayPlan(
            planId = "plan-1",
            profileId = "profile-1",
            localDate = java.time.LocalDate.of(2026, 9, 19),
            zoneId = "Asia/Shanghai",
            activeWordBookId = wordBookId,
            newTarget = 10,
            dueTarget = 0,
            newCardIds = emptyList(),
            dueCardIds = emptyList(),
            ruleVersion = "v1",
            generatedAt = java.time.Instant.EPOCH,
        ),
    )

    private fun setupViewModel(): LearningSetupViewModel {
        val repository = FakeLearningProfileRepository()
        val seedWordBooks = SeedWordBooksUseCase(
            { "[{\"id\":\"test-book\",\"displayName\":\"测试词书\",\"level\":\"Test\",\"totalWords\":1,\"dataVersion\":\"v1\",\"sourceId\":\"ngsl-nawl-1.2\",\"sourcePolicy\":\"应用内学习分组，不是官方考试大纲词表。词条尚未随本任务打包。\"}]" },
            repository,
        )
        val settingsRepo = FakeLearningSettingsRepository()
        return LearningSetupViewModel(
            repository,
            seedWordBooks,
            SelectWordBookAndSetDailyTargetUseCase(repository),
            GetLearningSettingsUseCase(settingsRepo),
            SaveLearningSettingsUseCase(settingsRepo),
            // `test-book` 是种子里的内置册，不声明就会被可见性策略过滤掉。
            bundledIds = com.example.englishlearning.learning.BundledWordBookIdSource { setOf("test-book") },
        )
    }

    private class FakeLearningProfileRepository : LearningProfileRepository {
        private var profile: LearningProfile? = null
        private val wordBooks = mutableListOf<WordBook>()

        override suspend fun current(profileId: String) = RepositoryResult.Success(profile)

        override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> {
            this.profile = profile
            return RepositoryResult.Success(Unit)
        }

        override suspend fun listWordBooks() = RepositoryResult.Success(wordBooks)

        override suspend fun findWordBook(id: String) = RepositoryResult.Success(wordBooks.find { it.id == id })

        override suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit> {
            wordBooks.removeAll { it.id == wordBook.id }
            wordBooks += wordBook
            return RepositoryResult.Success(Unit)
        }
    }

    private class FakeLearningSettingsRepository : LearningSettingsRepository {
        private val stored = mutableMapOf<String, LearningSettings>()

        override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> =
            LearningSettingsRepositoryResult.Success(stored[profileId])

        override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> {
            stored[settings.profileId] = settings
            return LearningSettingsRepositoryResult.Success(Unit)
        }
    }
}
