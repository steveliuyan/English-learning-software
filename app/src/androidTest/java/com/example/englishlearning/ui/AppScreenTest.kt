package com.example.englishlearning.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHasClickAction
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
import com.example.englishlearning.core.time.FixedClockProvider
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
    fun aiTabOpensTheAiLearningScreen() {
        composeRule.setContent { readyAppScreen() }
        createProfile()
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onNodeWithTag("ai_learning_header").assertExists()
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

    @Composable
    private fun readyAppScreen() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock))
        AppScreen(
            viewModel = vm,
            learningSetupViewModel = setupViewModel(),
            todayPlanViewModel = TodayPlanViewModel({ placeholderCardPlan() }, FakeLearningProfileRepository()),
            wordCardViewModel = wordCardFixtureViewModel(),
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
