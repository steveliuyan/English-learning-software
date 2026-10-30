package com.example.englishlearning.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.InMemoryLocalProfileRepository
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the word-book setup screen on a real device and exports a PNG so the
 * visual style can be reviewed without going through the profile-creation gate.
 *
 * Limitation: [LearningSetupViewModel.load] dispatches through `viewModelScope`, and
 * that dispatch is never executed by `waitForIdle()` / `mainClock.advanceTimeByFrame()`
 * in this harness. The asynchronously seeded word-book list is therefore absent from
 * this artifact, and only the statically composed structure is asserted here.
 * Authoritative visual evidence for the word-book list comes from the real device
 * screenshots `verification-logs/real-0*.png`.
 *
 * Not a pixel guard (R-13.1 / I-2): this class asserts node existence and writes one PNG for a
 * human to look at. It compares against no baseline, so it cannot prove the light palette stayed
 * byte-identical. The light-side equivalence chain is `DesignTokenContractTest` plus the
 * `MintPalette.kt` getter bridge (`Mint*` reading `LocalAppColors`) and the `AppColors.kt` values
 * that equal the deleted bare hexes; the header *layout* does change on purpose (24sp Bold ->
 * `AppType.Title` 17sp SemiBold, bodyMedium -> `AppType.Footnote`).
 */
@RunWith(AndroidJUnit4::class)
class SetupScreenScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun setupScreenRendersAndExportsPng() {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC)
        val vm = AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
        composeRule.setContent {
            AppScreen(
                viewModel = vm,
                learningSetupViewModel = setupViewModel(),
                todayPlanViewModel = TodayPlanViewModel({ TodayPlanResult.MissingLearningSetup }, FakeLearningProfileRepository()),
                wordCardViewModel = wordCardFixtureViewModel(),
            )
        }
        // R-13.1 / C-1：首启创建资料页的页头同样由统一组件承载（这一屏与词书选取屏同属核心学习路径，
        // 页头族 seed 摘行的判据是「本文件 headline 读点清零」，残余裸页头必须一并换掉）。
        // 断言按新组件形状走：testTag 是 `AppScreenHeader` 的承载证据，两行文案逐字命中。
        composeRule.onNodeWithTag("app_screen_header").assertExists()
        composeRule.onNodeWithText("先认识一下你").assertExists()
        composeRule.onNodeWithText("填写一个称呼，学习记录只保存在这台设备上。").assertExists()
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
        composeRule.waitForIdle()
        // 批 1 Task 12：合并屏页名取原型 `#/plan-edit` 的「学习计划」，旧页名整文件清零；
        // 「每日新增」现在是四枚 chip（`DailyTargetChips`），节标题文案不变所以锚不动。
        composeRule.onNodeWithText("学习计划").assertExists()
        composeRule.onNodeWithText("选择词书").assertExists()
        composeRule.onNodeWithText("每日新增").assertExists()
        composeRule.onNodeWithContentDescription("保存学习设置").assertExists()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = context.getExternalFilesDir(null) ?: error("no external files dir")
        val file = File(dir, "setup-screen.png")
        FileOutputStream(file).use { out ->
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        check(file.length() > 0) { "screenshot was not written" }
    }

    private fun setupViewModel(): LearningSetupViewModel {
        val repository = FakeLearningProfileRepository()
        val seedWordBooks = SeedWordBooksUseCase(
            { "[{\"id\":\"cet4\",\"displayName\":\"大学英语四级\",\"level\":\"CET-4\",\"totalWords\":1,\"dataVersion\":\"v1\",\"sourceId\":\"ngsl-nawl-1.2\",\"sourcePolicy\":\"应用内学习分组，不是官方考试大纲词表。\"},{\"id\":\"cet6\",\"displayName\":\"大学英语六级\",\"level\":\"CET-6\",\"totalWords\":1,\"dataVersion\":\"v1\",\"sourceId\":\"ngsl-nawl-1.2\",\"sourcePolicy\":\"应用内学习分组，不是官方考试大纲词表。\"},{\"id\":\"kaoyan\",\"displayName\":\"考研英语\",\"level\":\"Postgraduate\",\"totalWords\":1,\"dataVersion\":\"v1\",\"sourceId\":\"ngsl-nawl-1.2\",\"sourcePolicy\":\"应用内学习分组，不是官方考试大纲词表。\"}]" },
            repository,
        )
        val settingsRepo = FakeLearningSettingsRepository()
        return LearningSetupViewModel(
            repository = repository,
            seedWordBooks = seedWordBooks,
            selectWordBook = SelectWordBookAndSetDailyTargetUseCase(repository),
            getSettings = GetLearningSettingsUseCase(settingsRepo),
            saveSettings = SaveLearningSettingsUseCase(settingsRepo),
            // 这三册是测试种子里的「内置册」，不声明就会被可见性策略过滤掉。
            bundledIds = com.example.englishlearning.learning.BundledWordBookIdSource {
                setOf("cet4", "cet6", "kaoyan")
            },
        )
    }
}
