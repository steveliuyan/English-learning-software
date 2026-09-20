package com.example.englishlearning.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.TodayPlan
import com.example.englishlearning.learning.TodayPlanResult
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
        composeRule.onNodeWithContentDescription("保存学习设置").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(2, invocations)
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

        composeRule.onNodeWithContentDescription("调整词书与目标").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()

        composeRule.onNodeWithContentDescription("保存学习设置").assertExists().performClick()
        composeRule.waitForIdle()
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
        composeRule.onNodeWithContentDescription("调整词书与目标").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()

        composeRule.onNodeWithContentDescription("返回今日计划").assertExists().performClick()
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
        composeRule.onNodeWithContentDescription("返回今日计划").assertDoesNotExist()
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

        composeRule.onNodeWithContentDescription("开始学习").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_card_screen").assertExists()
        composeRule.onNodeWithText("ability").assertExists()
        composeRule.onNodeWithContentDescription("不认识").assertExists()

        composeRule.onNodeWithContentDescription("返回今日计划").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_plan_summary").assertExists()
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
        return LearningSetupViewModel(
            repository,
            seedWordBooks,
            SelectWordBookAndSetDailyTargetUseCase(repository),
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
}
