package com.example.englishlearning.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
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
import com.example.englishlearning.learning.TodayPlanRepository
import com.example.englishlearning.learning.PlaceholderWordCardSource
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
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))
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
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))
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
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))
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
        composeRule.onNodeWithTag("speech_toggle_candidates").performClick()
        composeRule.onNodeWithTag("speech_open_ai_profiles").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_profiles_screen").assertExists()
        fixture.keys += "mimo"
        composeRule.onNodeWithContentDescription("返回上一层").performClick()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()
        composeRule.onNodeWithTag("speech_toggle_candidates").performClick()
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
        composeRule.onNodeWithTag("speech_toggle_candidates").performClick()
        composeRule.onNodeWithTag("speech_profile_mimo").performScrollTo()
        composeRule.onNodeWithText("可用").assertExists()
        composeRule.onNodeWithTag("speech_open_ai_profiles").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_profiles_screen").assertExists()
        fixture.keys.clear()
        pressSystemBack()
        composeRule.onNodeWithTag("speech_settings_screen").assertExists()
        composeRule.onNodeWithTag("speech_toggle_candidates").performClick()
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
    fun learningCardPronunciationRoutesTheVisibleLemmaToTheProvider() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider) }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts == listOf("ability") }

        assertEquals(listOf("ability"), provider.spokenTexts)
        composeRule.onNodeWithTag("word_card_pronunciation_message").assertExists()
        composeRule.onNodeWithText("发音已播放。").assertExists()
    }

    @Test
    fun learningCardPronunciationFailureShowsSafeMessageAndKeepsFeedbackActions() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Failed(IllegalStateException("secret endpoint")))
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider) }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_speak").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_speak").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("发音播放失败，请重试。").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("发音播放失败，请重试。").assertExists()
        composeRule.onNodeWithText("secret endpoint").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("不认识").assertHasClickAction()
        composeRule.onNodeWithContentDescription("模糊").assertHasClickAction()
        composeRule.onNodeWithContentDescription("认识").assertHasClickAction()
    }

    @Test
    fun submittedCardDetailPronunciationRoutesTheHeldLemmaToTheProvider() {
        val provider = RecordingPronunciationProvider(PronunciationResult.Played)
        composeRule.setContent { readyAppScreen(pronunciationProvider = provider) }
        createProfile()

        composeRule.onNodeWithContentDescription("开始学习").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_card_feedback_fuzzy").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_card_feedback_fuzzy").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("card_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("播放 ability 发音").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { provider.spokenTexts == listOf("ability") }

        assertEquals(listOf("ability"), provider.spokenTexts)
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

    private fun readingFixture(): ReadingFixture {
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
        val events = object : LearningEventRepository by NoopLearningEventRepository() {
            override suspend fun completedCardIds(planId: String) =
                RepositoryResult.Success(if (planId == plan.planId) listOf("apple-id") else emptyList())
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

        override fun capabilities() = setOf(PronunciationCapability.RemoteAudio)

        override suspend fun speak(text: String): PronunciationResult {
            spokenTexts += text
            return result
        }
    }

    private data class SpeechFixture(
        val speech: SpeechSettingsViewModel,
        val ai: AiProfileSettingsViewModel,
        val keys: MutableSet<String>,
    )

    private fun speechFixture(): SpeechFixture {
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
            override suspend fun get() = Result.success(SpeechPreference(selectedEngine = PronunciationEngine.MiMo, miMoProfileId = "mimo"))
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
    ) {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))
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
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))
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
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))
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
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))
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
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))
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
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))
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

    private fun readyPlanResult(): TodayPlanResult = TodayPlanResult.Ready(
        TodayPlan(
            planId = "plan-1",
            profileId = "profile-1",
            localDate = java.time.LocalDate.of(2026, 9, 19),
            zoneId = "Asia/Shanghai",
            activeWordBookId = "test-book",
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
