package com.example.englishlearning.ui

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.ImportedWordBookIdSource
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.TodayPlanResult
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.learning.WordBookDeletionPreview
import com.example.englishlearning.learning.WordBookDeletionPreviewRepository
import com.example.englishlearning.learning.WordBookDeletionService
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.InMemoryLocalProfileRepository
import com.example.englishlearning.wordbook.WordBookTransferViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 17B 落点 3：AppScreen 那 6 枚浮层迁到两路共享档之后的**设备侧背书**。
 *
 * 为什么要这一类：迁移动力之一是「connected 覆盖 12/463 = 2.6%」——这 6 枚浮层在真机上
 * **一枚都没有**被驱动过（控制者实测 connected 覆盖实数 0）。JVM 侧的
 * `AppScreenOverlayContainerContractTest` 只能钉源码形状（份数、实参形状、逐字要素），
 * 钉不出「点下去真的画得出面板、面板里的确认键真的可点、取消真的不落库」。
 * 本类补的就是这三件事，且**全部走真实宿主**（`AppScreen` + 真实 ViewModel 状态机 +
 * 真实语义动作），不在用例里复刻任何生产分支：
 *  - 路 A：`LearningSetupScreen` 的词书卡「删除词书」**自定义无障碍动作**真实触发
 *    `LearningSetupViewModel.requestDelete` → `pendingDelete` → 面板组合（这条动作是
 *    `WordBookPickRowTest` I-A2 钉过的同一条真实入口，不是测试替身直接调 VM）；
 *  - 路 B：「我的」hub 的「词书内容包」行进入子页，册数 ≥ 2 时真实打开 `GlassDialog` 选择器，
 *    列表项 tag（`word_book_export_pick_*`）仍逐项可达——**只断存在与可点，绝不点击**
 *    （点击会拉起 SAF 系统界面，设备用例不该把系统文件选择器卷进来）。
 *    2026-11-02（决策 §4）这一族整条通路从「设置页折叠行 + 宿主持有选择器」改为
 *    「hub 一行 → 子页自持 SAF 与选择器」，驱动链因此多一站、tag 全部改名。
 *
 * 方法名带 `AtDoubleFontScale` 的那几例钉的是大字档下面板正文与按钮仍**整体在视口内**
 * （`assertIsDisplayed()` 要求节点 ≥90% 可见，正文被挤出屏幕或按钮被裁掉都会当场红），
 * 写法沿用本包先例：`LocalDensity provides Density(density, fontScale = 2f)`。
 * **但 `Dialog` 窗口的 density 取自窗口自身上下文**（17B 收口席实测：provider 覆盖只作用于宿主组成，
 * 窗口内短行在设备 scale 1.0 下以 `lineCount=1 + overflow=true` 亚像素假阳性判红），
 * 所以这几例的运行前置是设备级 `adb shell settings put system font_scale 2.0`（跑完必须回 `1.0`），
 * 并各带一枚 [assertScaleTookEffect] 哨兵防「scale 1.0 复读」式假绿。
 *
 * 每个方法**只调一次 `setContent`**（同方法内第二次 `setContent` 真机必崩，Task 9 实测），
 * 所以每例自带完整驱动链（建资料 → 进目标屏 → 打开浮层 → 断言）。
 */
@RunWith(AndroidJUnit4::class)
class AppScreenOverlayContainerTest {
    @get:Rule val composeRule = createComposeRule()

    // ---------- 路 A：删除词书（迁移前 AppScreen.kt:1873） ----------

    /**
     * 钉的破坏：
     *  ① 面板没接上共享档（点了删除什么都不出现 / 长出第二份私有对话框）；
     *  ② 三要素缺一件或**顺序颠倒**（级联条数必须在保留面之前、不可撤销声明在最后）；
     *  ③ 确认键丢标签、丢 `Role.Button`、丢朗读文本，或触控面不足 48dp；
     *  ④ 「取消」把删除真的落库（零写入红线）。
     */
    @Test
    fun hostedWordBookDeleteConfirmationStatesCascadeThenKeepThenIrreversibleAndCancelWritesNothing() {
        val repository = RecordingProfileRepository()
        val previews = RecordingDeletionPreview(WordBookDeletionPreview(entryCount = 3, keptEventCount = 10, keptArticleCount = 2))
        val deleteCallsAtOpen = repository.deleteCalls.size
        composeRule.setContent {
            AppScreen(
                viewModel = appViewModel(),
                learningSetupViewModel = setupViewModel(repository, previews),
                todayPlanViewModel = TodayPlanViewModel({ TodayPlanResult.MissingLearningSetup }, RecordingProfileRepository()),
                wordCardViewModel = wordCardFixtureViewModel(),
            )
        }
        createProfileAndOpenSetup()
        requestWordBookDeleteThroughTheAccessibilityAction()

        // 面板出现 + 标题逐字（词书名来自 pendingDelete，不是测试自己拼的）。
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("删除《我的词书》？").fetchSemanticsNodes().isNotEmpty()
        }
        // 三要素逐字在树（条数来自 deletePreview，句式与站点逐字一致）。
        val cascade = "将删除这册导入词书的词卡文件与词条 3 条。"
        val keep = "学习历史与文章保留：该册学习记录 10 条、文章 2 篇仍可翻阅（复习状态与生词笔记同保留）。"
        val otherKeep = "其他词书不受影响。"
        val irreversible = "此操作不可撤销。"
        val title = composeRule.onNodeWithText("删除《我的词书》？")
        // 带数字的那一行要等级联预览回填（回填前站点画的是「暂时读不到条数」那一档兜底文案）。
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(cascade).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "正文里的条数必须来自级联预览这一路（不是站点写死的常数）",
            listOf("my-pack"),
            previews.calls.map { it.second }.distinct(),
        )
        listOf(cascade, keep, otherKeep, irreversible).forEach { line ->
            composeRule.onNodeWithText(line).assertIsDisplayed()
        }
        // 顺序：标题 → 级联 → 保留面（两行）→ 不可撤销。倒序 = destructiveDialogBody 配方被绕过。
        val topOf = mapOf(
            "title" to title.fetchSemanticsNode().boundsInRoot.top,
            "cascade" to composeRule.onNodeWithText(cascade).fetchSemanticsNode().boundsInRoot.top,
            "keep" to composeRule.onNodeWithText(keep).fetchSemanticsNode().boundsInRoot.top,
            "otherKeep" to composeRule.onNodeWithText(otherKeep).fetchSemanticsNode().boundsInRoot.top,
            "irreversible" to composeRule.onNodeWithText(irreversible).fetchSemanticsNode().boundsInRoot.top,
        )
        assertTrue("正文必须按级联 → 保留面 → 不可撤销排（实测 $topOf）", topOf.getValue("title") < topOf.getValue("cascade"))
        assertTrue("级联条数必须在保留面之前（实测 $topOf）", topOf.getValue("cascade") < topOf.getValue("keep"))
        assertTrue("两条保留面必须自上而下（实测 $topOf）", topOf.getValue("keep") < topOf.getValue("otherKeep"))
        assertTrue("不可撤销声明必须收尾（实测 $topOf）", topOf.getValue("otherKeep") < topOf.getValue("irreversible"))

        // 确认键：组件固定 tag + 可点 + Role.Button + 站点朗读文本「删除」+ ≥48dp 触控面。
        // 出口先**滚到可见**再判：设备字号 2.0 下这一屏的自然高本来就超过对话框窗口（2026-11-06 实测两轮——
        // 上限取 `Configuration.screenHeightDp` 与等效无上限的 `10000.dp`，两枚出口都仍 is not displayed），
        // 「不滚就落在视口里」在 2.0 不可满足；本批评的是「滚一下就到、到了仍可点、仍 ≥48dp」这条出路，
        // 与本类 12 册导出选择器那条 `performScrollTo()` 的先例同口径（正文四行的可见性由
        // `hostedWordBookDeletePanelStaysFullyReadableAndTappableAtDoubleFontScale` 按档位钉，不在这儿重复）。
        val confirm = composeRule.onNodeWithTag("confirm_destructive_confirm")
        confirm.performScrollTo()
        confirm.assertIsDisplayed()
        confirm.assertHasClickAction()
        confirm.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        confirm.assertTextEquals("删除")
        confirm.assertHeightIsAtLeast(48.dp)
        val dismiss = composeRule.onNodeWithTag("confirm_destructive_dismiss")
        dismiss.assertIsDisplayed()
        dismiss.assertHasClickAction()
        dismiss.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        dismiss.assertHeightIsAtLeast(48.dp)

        // 取消：面板收走，仓储**一次删除都没发生**（零写入红线）。
        dismiss.performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("confirm_destructive_confirm").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("删除《我的词书》？").assertDoesNotExist()
        assertEquals("取消不得落库：deleteWordBook 调用数必须不变", deleteCallsAtOpen, repository.deleteCalls.size)
        composeRule.onNodeWithTag("wordbook_pick_my-pack").assertExists()
    }

    /**
     * 钉的破坏：大字号档下这一屏的**安全说明被裁**或**出口点不着**（「看得见说得出、点不着」是 F5-03 那一族缺陷）。
     *
     * 两段的口径不一样，是 2026-11-06 在这台设备上量出来的：
     *  - **正文五行（标题 + 级联 + 两条保留面 + 不可撤销）不滚就该全在首屏**——逐条 `assertBodyLineSurvivesTheScale`
     *    （内含 `assertIsDisplayed` + 两腿真截断信号）。这一条没放松，也不可能靠滚动来满足：说明被切走就是切走。
     *  - **两枚出口按钮滚到可见后**必须可见且触控面 ≥48dp。这一屏在 `font_scale = 2.0` 下的自然高**超过**
     *    对话框窗口（实测：`DialogPanel` 的上限取 `Configuration.screenHeightDp`、乃至取等效无上限的 10000.dp
     *    两轮复跑，`confirm_destructive_*` 都仍 is not displayed），所以「不滚就在视口里」在这里不可满足，
     *    可满足的是「被钉在窗口内 + 滚动可达」，与本类 12 册导出选择器那条同口径（先例 `performScrollTo()`）。
     *
     * 按钮 48dp 与 `ConfirmDestructiveDialogTest` 的组件级护栏同口径；这里跑**真实宿主 + 真实文案**
     * （站点给的级联与保留面比组件级夹具更长，只有宿主侧才量得到实际高度）。
     */
    @Test
    fun hostedWordBookDeletePanelStaysFullyReadableAndTappableAtDoubleFontScale() {
        requireDeviceDoubleFontScale()
        val repository = RecordingProfileRepository()
        val previews = RecordingDeletionPreview(WordBookDeletionPreview(entryCount = 3, keptEventCount = 10, keptArticleCount = 2))
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                AppScreen(
                    viewModel = appViewModel(),
                    learningSetupViewModel = setupViewModel(repository, previews),
                    todayPlanViewModel = TodayPlanViewModel({ TodayPlanResult.MissingLearningSetup }, RecordingProfileRepository()),
                    wordCardViewModel = wordCardFixtureViewModel(),
                )
            }
        }
        createProfileAndOpenSetup()
        requestWordBookDeleteThroughTheAccessibilityAction()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("删除《我的词书》？").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("将删除这册导入词书的词卡文件与词条 3 条。").fetchSemanticsNodes().isNotEmpty()
        }
        // 哨兵先行：证 scale 2.0 真的落进对话框窗口，护栏不在 scale 1.0 下空转（见 assertScaleTookEffect KDoc）。
        assertScaleTookEffect("其他词书不受影响。", expectMinHeightDp = 26)
        assertBodyLineSurvivesTheScale("将删除这册导入词书的词卡文件与词条 3 条。")
        assertBodyLineSurvivesTheScale(
            "学习历史与文章保留：该册学习记录 10 条、文章 2 篇仍可翻阅（复习状态与生词笔记同保留）。",
        )
        assertBodyLineSurvivesTheScale("其他词书不受影响。")
        assertBodyLineSurvivesTheScale("此操作不可撤销。")
        assertBodyLineSurvivesTheScale("删除《我的词书》？")
        // 出口两枚：滚到可见后仍必须可见 + ≥48dp（2.0 下这一屏的自然高超过对话框窗口，实测与先例口径
        // 记在上面那条 `performScrollTo()` 的注释里）。正文五行不滚就该全在首屏——上面五行逐条判过，
        // 这里不许倒退成「滚了才判正文」。
        composeRule.onNodeWithTag("confirm_destructive_dismiss").performScrollTo()
        composeRule.onNodeWithTag("confirm_destructive_dismiss").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("confirm_destructive_confirm").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    // ---------- 路 B：导出词书选择器（迁移前 AppScreen.kt:1608） ----------

    /**
     * 钉的破坏：选择器换成共享玻璃档后列表项 tag 被改掉（`word_book_export_pick_*` 是这枚自渲染
     * 列表项唯一的取样面），或「取消」不再是共享药片、点了不收场、甚至把导出真的发出去。
     *
     * 只断**存在 + 可点 + 取消收场**，绝不点列表项：那会拉起 SAF 系统文件选择界面。
     */
    @Test
    fun hostedWordBookExportPickerKeepsEveryImportedBookRowReachableAndClosesThroughTheSharedPill() {
        val books = prepareImportedWordBookDirectories("overlay-pack-a", "overlay-pack-b")
        try {
            val repository = RecordingProfileRepository()
            composeRule.setContent {
                AppScreen(
                    viewModel = appViewModel(),
                    learningSetupViewModel = setupViewModel(repository, RecordingDeletionPreview(WordBookDeletionPreview(1, 1, 1))),
                    todayPlanViewModel = TodayPlanViewModel({ placeholderCardPlan() }, RecordingProfileRepository()),
                    wordCardViewModel = wordCardFixtureViewModel(),
                    wordBookTransferViewModel = transferViewModel(repository),
                )
            }
            createProfileAndOpenTransferPage()
            openExportPicker()
            // 两册都在，且逐项可点（tag 口径随子页改名：`word_book_export_pick_*`）。
            listOf("overlay-pack-a", "overlay-pack-b").forEach { bookId ->
                composeRule.onNodeWithTag("word_book_export_pick_$bookId")
                    .assertIsDisplayed()
                    .assertHasClickAction()
            }
            // 取消是共享药片（tag + 可点 + 朗读文本），且真的收场。
            val cancel = composeRule.onNodeWithTag("word_book_export_cancel")
            cancel.assertHasClickAction()
            cancel.assertTextEquals("取消")
            cancel.performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("选择要导出的词书").fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("word_book_export_pick_overlay-pack-a").assertDoesNotExist()
            // 选择器挂在子页之上：关掉后落回「词书内容包」子页，而不是整层退回 hub。
            composeRule.onNodeWithTag("word_book_transfer_screen").assertExists()
        } finally {
            books.forEach { it.deleteRecursively() }
        }
    }

    /**
     * 路 B 的大字档护栏：玻璃面板的标题、两册列表项与「取消」药片在 `fontScale 2.0` 下都必须在视口内。
     * 与路 A 那例同一判据（`assertIsDisplayed()`），因为玻璃档同样不带内部滚动，
     * 一旦内容超出屏幕就是「按钮被裁掉」。
     */
    @Test
    fun hostedWordBookExportPickerStaysFullyReadableAtDoubleFontScale() {
        requireDeviceDoubleFontScale()
        val books = prepareImportedWordBookDirectories("overlay-pack-a", "overlay-pack-b")
        try {
            val repository = RecordingProfileRepository()
            composeRule.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    AppScreen(
                        viewModel = appViewModel(),
                        learningSetupViewModel = setupViewModel(repository, RecordingDeletionPreview(WordBookDeletionPreview(1, 1, 1))),
                        todayPlanViewModel = TodayPlanViewModel({ placeholderCardPlan() }, RecordingProfileRepository()),
                        wordCardViewModel = wordCardFixtureViewModel(),
                        wordBookTransferViewModel = transferViewModel(repository),
                    )
                }
            }
            createProfileAndOpenTransferPage()
            openExportPicker()
            // 哨兵先行：Headline 标题 scale 1.0 行高 73px、scale 2.0 折两行 ≥150px，取 100 为界。
            assertScaleTookEffect("选择要导出的词书", expectMinHeightDp = 32)
            assertBodyLineSurvivesTheScale("选择要导出的词书")
            composeRule.onNodeWithTag("word_book_export_pick_overlay-pack-a").assertIsDisplayed()
            composeRule.onNodeWithTag("word_book_export_pick_overlay-pack-b").assertIsDisplayed()
            composeRule.onNodeWithTag("word_book_export_cancel").assertIsDisplayed().assertHasClickAction()
        } finally {
            books.forEach { it.deleteRecursively() }
        }
    }

    /**
     * 席 A 落点 1 的**宿主级第二腿**：十二册导入词书 + 设备字号 2.0 时，面板底部的「取消」仍必须可达。
     *
     * 与上面那条两册短列表用例的分工：两册在 560.dp 上限之内，**根本不触发滚动**，
     * 所以它只证「短列表一屏可见」，证不了兜底；十二册 × 2.0 缩放行高远超上限，
     * 修复前（`GlassDialog` 内容无高度上限、无滚动）面板按内容长到窗口外，
     * 「取消」既不在视口内、树侧也到不了（`performScrollTo()` 找不到可滚祖先会当场抛）——
     * 这正是 F5-03 那一族「看得见点不着」的宿主侧现形。
     *
     * 列表项**只断存在、绝不点击**：`word_book_export_pick_*` 一点就拉起 SAF 系统文件选择界面
     * （同一口径见本类路 B 的既有说明）。收场只点「取消」。
     */
    @Test
    fun hostedWordBookExportPickerKeepsTheCancelPillReachableWhenDozensOfBooksOverflowAtDoubleFontScale() {
        requireDeviceDoubleFontScale()
        val bookIds = (1..12).map { index -> "overlay-pack-%02d".format(index) }
        val books = prepareImportedWordBookDirectories(*bookIds.toTypedArray())
        try {
            val repository = RecordingProfileRepository()
            composeRule.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    AppScreen(
                        viewModel = appViewModel(),
                        learningSetupViewModel = setupViewModel(repository, RecordingDeletionPreview(WordBookDeletionPreview(1, 1, 1))),
                        todayPlanViewModel = TodayPlanViewModel({ placeholderCardPlan() }, RecordingProfileRepository()),
                        wordCardViewModel = wordCardFixtureViewModel(),
                        wordBookTransferViewModel = transferViewModel(repository),
                    )
                }
            }
            createProfileAndOpenTransferPage()
            openExportPicker()
            // 哨兵先行：设备字号没真的到 2.0 就当场红，护栏不许退化成 scale 1.0 复读。
            assertScaleTookEffect("选择要导出的词书", expectMinHeightDp = 32)
            // 十二册逐项在树（只证存在，绝不点击）；首册必须落在面板首屏内。
            bookIds.forEach { bookId -> composeRule.onNodeWithTag("word_book_export_pick_$bookId").assertExists() }
            composeRule.onNodeWithTag("word_book_export_pick_${bookIds.first()}").assertIsDisplayed()
            // 「取消」被内容推出视口后必须靠滚动可达（兜底的唯一判据），且仍可点。
            val cancel = composeRule.onNodeWithTag("word_book_export_cancel")
            cancel.performScrollTo()
            cancel.assertIsDisplayed().assertHasClickAction()
            // 收场：点取消关掉选择器（不碰 SAF）。
            cancel.performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("选择要导出的词书").fetchSemanticsNodes().isEmpty()
            }
        } finally {
            books.forEach { it.deleteRecursively() }
        }
    }

    /**
     * Task 25（裁决⑥）+ 2026-11-02（决策 §2）宿主编排腿：**「我的」hub 那一行真的导航到备份子页，
     * 返回键真的回到 hub**。
     *
     * 子页直测（`BackupEntryUnificationTest`）只证子页自身的形状；这里证宿主那三件接线：
     *  ① `profile_open_backup` 点击把 `showBackupHub` 置真（不在 hub 就地展开，也不回设置子页）；
     *  ② hub 是全屏层——底导整棵不组合。注意这只由挂载链决定（`showBackupHub` 的 `else if`
     *     排在 Scaffold 的 `else` 之前），**不是** `overlayOpen` 的功劳：`overlayOpen` 全仓唯一
     *     消费者是全局返回键（AppScreen.kt 里那一处）；`overlayOpen` 挂没挂 hub 由 JVM 源契约
     *     `BackupEntryUnificationContractTest#host_wires_backup_hub_at_every_station` 承重；
     *  ③ `BackHandler` 把返回键交回「我的」hub（从哪来回哪去）。
     *
     * 这条**不**证子屏返回栈（导出 / 认证导出 / 恢复三枚子屏需要各自的真实 ViewModel 装配，
     * 本类夹具没有那套依赖）；子屏返回栈的缺口已在 Task 25 报告「未覆盖」里登记。
     */
    @Test
    fun hostedBackupRowOpensTheHubAndBackReturnsToTheProfileHub() {
        composeRule.setContent {
            AppScreen(
                viewModel = appViewModel(),
                learningSetupViewModel = setupViewModel(
                    RecordingProfileRepository(),
                    RecordingDeletionPreview(WordBookDeletionPreview(1, 1, 1)),
                ),
                todayPlanViewModel = TodayPlanViewModel({ placeholderCardPlan() }, RecordingProfileRepository()),
                wordCardViewModel = wordCardFixtureViewModel(),
            )
        }
        createProfile()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("app_tab_profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("app_tab_profile").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("profile_open_backup").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("profile_open_backup").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("backup_hub_screen").fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue(
            "子页打开时底导必须不可见（hub 分支排在 Scaffold 的 else 之前，若被顶掉底导会与子页同时在场）",
            composeRule.onAllNodesWithTag("app_tab_profile").fetchSemanticsNodes().isEmpty(),
        )

        pressSystemBack()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("backup_hub_screen").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("profile_screen").assertExists()
        composeRule.onNodeWithTag("app_tab_profile").assertExists()
    }

    // ---------- 宿主驱动 ----------

    /**
     * 建资料。落点屏由宿主按今日计划的状态决定，所以这里**不等具体节点**，
     * 两路各自等自己那一站：路 A 等 `MissingLearningSetup` 逼出的必填选取屏，
     * 路 B 等 `placeholderCardPlan()` 给出的今日计划栏（底导四栏都在，才能切「我的」栏）。
     */
    private fun createProfile() {
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
    }

    /**
     * 路 B 驱动链（2026-11-02 决策 §4 起）：建资料 → 切到「我的」栏 → 点 hub 的「词书内容包」行 → 子页在场。
     *
     * 驱动链比旧口径多一站：从前「设置页折叠行 + 宿主持有选择器」，点一下就在同一屏长出子行；
     * 现在 SAF、状态与选择器全归 `WordBookTransferScreen`，所以必须先进子页再谈导出。
     */
    private fun createProfileAndOpenTransferPage() {
        createProfile()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("app_tab_profile").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("app_tab_profile").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("profile_open_word_book_transfer").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("profile_open_word_book_transfer").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_book_transfer_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * 在子页里打开「选择要导出的词书」玻璃面板。册数 ≥ 2 时导出行才走选择器（单册直发 SAF），
     * 所以调用方一律先 `prepareImportedWordBookDirectories` 铺两册或十二册。
     *
     * 点击前 `assertIsEnabled()` 先行：导出行在无 ViewModel / 在途 / 零册时是**真禁用**
     * （`EntryRow(enabled = false)`），禁用态点下去静默无回执——那样这里的等待会变成一个
     * 与「可达性」无关的超时，判据当场失真。禁用态本身的可点性判据在
     * `WordBookTransferScreenTest`，本类只走启用态那一条通路。
     */
    private fun openExportPicker() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("word_book_transfer_export").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("word_book_transfer_export")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("选择要导出的词书").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * 真实系统返回（Task 25 的 hub 返回腿）：`Espresso.pressBack()` 在本仓 no compile classpath，
     * 走 instrumentation 注入 key event——与 `AppScreenTest.pressSystemBack()` 同一口径。
     *
     * **先等窗口真拿到焦点再注入**：`install -r` 之后的第一枚用例里，组合树已经可查
     * （`profile_open_backup`/`backup_hub_screen` 都在），但测试活动的窗口焦点还没落定，
     * 此时 `sendKeyDownUpSync` 会落到别的窗口、静默丢键（2026-10-24 实测两次，
     * 都发生在装包后首跑；紧接着重跑同一条即绿）。焦点没到不许猜——等到为止。
     */
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
        composeRule.waitForIdle()
    }

    /** 路 A 驱动链：建资料 → 必填的「调整词书与目标」屏把导入册画出来。 */
    private fun createProfileAndOpenSetup() {
        createProfile()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("wordbook_pick_my-pack").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * 用**真实无障碍动作**触发删除请求：TalkBack 用户走的就是这条「删除词书」自定义动作
     * （同 `WordBookPickRowTest` 的 I-A2 取证口径），不是测试替身直接调 `requestDelete`。
     */
    private fun requestWordBookDeleteThroughTheAccessibilityAction() {
        val nodes = composeRule.onAllNodes(clickableWithDeleteWordBookAction()).fetchSemanticsNodes()
        assertEquals("「删除词书」动作必须只挂在导入册那一枚可点节点上，命中 ${nodes.size} 枚", 1, nodes.size)
        val action = nodes.single().config.getOrNull(SemanticsActions.CustomActions)
            ?.single { it.label == "删除词书" }
            ?: error("导入词书卡上没有「删除词书」自定义动作")
        assertEquals(true, action.action())
    }

    private fun clickableWithDeleteWordBookAction() = SemanticsMatcher("可点击且带「删除词书」自定义动作") { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null &&
            node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "删除词书" } == true
    }

    /**
     * 大字档（`fontScale = 2.0`）下单行文字的两条护栏：**在视口内** + **无截断**。
     *
     * 只问「节点在不在树上」不够——`Clip` 与 `Ellipsis` 都不改语义文本。截断信号在 17B 收口席
     * 重定标过一次：房先例（`AppScreenHeaderTest.header_survives_font_scale_2_without_clipping_the_title`）
     * 的 `!hasVisualOverflow` 口径只在**折行文本**（行宽 < 盒宽）上可靠；收口取证
     * （emulator-5574、设备 `font_scale=2.0`）实测「其他词书不受影响。」这类**行宽恰撞内容宽**的
     * 单行会出亚像素假阳性——`lineRight=585.0 == size.width` 仍报 `true`，而 `getLineEnd` 证明
     * 9/9 字符全部在布局内、节点完整在视口内，画面上没有任何一个字被吃掉。所以这里判**两枚真截断信号**，
     * 两腿各管各的方向：
     *  ① **纵向腿** `getLineEnd(末行) == text.length`——maxLines/Ellipsis 截断时末行必然布局不到文本末尾；
     *  ② **横向腿（修复轮 1 起逐行）** 每一行都判 `getLineRight(line) <= 节点盒宽 + 1dp`——
     *     任何一行伸出自己的盒就是真裁切。旧版只取末行：ragged-right 正文的末行是**最短**的一行，
     *     多行正文上这条腿近乎恒真（红样探针 `fix17b-item1-oldshape-passes-probe.log` 演示了
     *     「首行伸出 40dp 盒、末行稳稳在盒内」被旧判据原样放过）；且收口取证显示它是靠**等号**过的
     *     （`lineRight=585.0 == 盒宽 585.0`，零余量，而 `hasVisualOverflow` 假阳性的根因正是分数
     *     advance 向下整化——同一边界，换设备/换密度极可能反向假红）。
     * 加 1dp 容差**不算放宽**：纵向腿独立承担「有字没画出来」，横向腿容差挡的是整化噪声，
     * 而「只查末行」变「逐行查」是净收紧。容差按 dp 写：节点 bounds 与行坐标都是窗口像素，
     * Dialog 窗口与宿主共用同一 dp→px 基准密度（窗口间只差 fontScale，不动 density 分量），
     * 故 `displayMetrics.density` 即窗口 `LocalDensity.density`，换算成 **1dp** 而不是裸像素。
     * 「被挤出屏幕」那半边仍由 `assertIsDisplayed()`（节点 ≥90% 落在视口内、且已按父级裁剪算过）承担。
     */
    private fun assertBodyLineSurvivesTheScale(text: String) {
        val node = composeRule.onNodeWithText(text)
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val tolerancePx = with(Density(density, fontScale = 1f)) { 1.dp.toPx() }
        assertEquals(
            "fontScale=2.0 下「$text」必须整句被布局（末行没到文本末尾 = 有字没画出来）：" +
                "lineCount=${layout.lineCount} overflow=${layout.hasVisualOverflow} size=${layout.size}",
            text.length,
            layout.getLineEnd(layout.lineCount - 1),
        )
        for (line in 0 until layout.lineCount) {
            assertTrue(
                "fontScale=2.0 下「$text」第 $line 行不得伸出文本盒 +1dp（逐行真裁切信号）：" +
                    "lineRight=${layout.getLineRight(line)} lineCount=${layout.lineCount} " +
                    "boxWidth=${bounds.width} tolerancePx=$tolerancePx size=${layout.size} bounds=$bounds",
                layout.getLineRight(line) <= bounds.width + tolerancePx,
            )
        }
    }

    /**
     * 「字号真的落进了对话框窗口」哨兵：`Dialog` 窗口的 `LocalDensity` 取自**窗口自身上下文**，
     * 调用方的 `LocalDensity` 覆盖只对宿主组成生效、对窗口内不生效（17B 收口席实测：设备
     * `font_scale=1.0` 时窗口内 `13sp` 行高恒为 40px 单行，还会以 `lineCount=1 + overflow=true`
     * 的亚像素假阳性刺穿截断判据）。判据取**行高**而不是折行：实测同一枚 Footnote 行
     * scale 1.0 = 40px、scale 2.0 = 77px，而「其他词书不受影响。」在 scale 2.0 下 585px
     * 恰可容进内容宽 588px、**单行并不折**——用「必折行」当哨兵会把合法渲染判红。
     * 因此 [expectMinHeightDp] 按档位取 scale-1 与 scale-2 行高之间的阈值（**dp 口径**，
     * 2026-11-07 批 B-3 改：原 60px / 100px 是 420dpi 手机竖屏上的读数，同一枚文本在平板形窗口
     * （`wm size 2560x1600` + `wm density 320`）里 px 值更小、dp 值不变，裸 px 阈值会把
     * 「scale 真的生效」判成没生效；改 dp 后两形态共用一条阈值——Footnote 26dp 夹在
     * scale1 的 21.3/22.0dp 与 scale2 的 29.3/37.5dp 之间，标题 32dp 夹在 24.4/24.5dp 与
     * 38/42dp 之间，两侧都留 ≥4dp 余量）：设备字号没到 2.0 就当场红，
     * 护栏不许退化成「scale 1.0 复读」的假绿。
     * 运行前置：`adb -s emulator-5574 shell settings put system font_scale 2.0`（跑完必须回 `1.0`）。
     */
    /**
     * 设备前置闸（批 B-3 §10.4，2026-11-07）：本组三枚大字号护栏判的是**对话框窗口**里的排印，
     * 而 `Dialog` 窗口的 `LocalDensity` 取自窗口自身上下文——调用方 `LocalDensity provides fontScale = 2f`
     * 对窗口内不生效（见 [assertScaleTookEffect] KDoc 的 17B 收口席实测）。于是这三枚的可执行前提是
     * **设备 `font_scale` 真的置到 2.0**：
     *
     * ```
     * adb -s emulator-5554 shell settings put system font_scale 2.0   # 跑完必须回 1.0
     * ```
     *
     * 处置口径（决策 §10.4）：设备不在 2.0 档时**如实跳过**而不是红——红会把「设备前置没做」
     * 和「大字号排印真回归」混成一类，前置跑法与逐形态读数见
     * `docs/decisions/2026-11-06-adaptive-window-layout.md` §10.4。跳过不等于放宽判据：
     * 一旦设备在 2.0 档，[assertScaleTookEffect] 与逐行裁切判据照旧全数生效。
     */
    private fun requireDeviceDoubleFontScale() {
        val deviceFontScale = InstrumentationRegistry.getInstrumentation().targetContext
            .resources.configuration.fontScale
        assumeTrue(
            "设备 font_scale=${'$'}deviceFontScale，本组护栏的前提不成立（需 2.0 档，" +
                "跑法：adb shell settings put system font_scale 2.0，验完回 1.0）——跳过，不是判绿",
            deviceFontScale >= 1.99f,
        )
    }

    private fun assertScaleTookEffect(sample: String, expectMinHeightDp: Int) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(sample)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        // 阈值按 **dp** 而不是裸 px：同一枚文本行高在两种窗口形态下 px 不同、dp 相同
        // （批 B-3 实测：Footnote 样本 scale 1.0 = 56px/2.625 = 21.3dp（手机竖屏 420dpi）
        // 与 44px/2.0 = 22.0dp（平板形 320dpi）；标题样本 64px→24.4dp 与 49px→24.5dp）。
        // 沿用本类既有的 `displayMetrics.density` 换算腿，把阈值折成当前形态的 px 再比。
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val thresholdPx = with(Density(density, fontScale = 1f)) { expectMinHeightDp.dp.toPx() }
        assertTrue(
            "fontScale=2.0 未落进对话框窗口（「$sample」行高应 ≥${expectMinHeightDp}dp = ${thresholdPx}px，" +
                "实测 size=${layout.size} density=$density）：" +
                "connected 侧需先把设备 font_scale 置 2.0，护栏不许在 scale 1.0 下空转",
            layout.size.height >= thresholdPx,
        )
    }

    private fun appViewModel(): AppViewModel {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)
        return AppViewModel(repository, CreateLocalProfileUseCase(repository, clock), AndroidTestHealthyDatabaseStartupProbe)
    }

    /**
     * 一内置册 + 一导入册：只有导入册进 `canDelete`，所以「删除词书」动作恰好挂在它身上
     * （份数判据才有牙齿——内置册长出删除入口也会红）。
     *
     * `deletion` 用 `File("")` 这个空根目录：本类**从不**点确认，删除服务一次都不会被调用，
     * 「取消零写入」由 [RecordingProfileRepository.deleteCalls] 直接钉。
     */
    private fun setupViewModel(
        repository: RecordingProfileRepository,
        previews: RecordingDeletionPreview,
    ): LearningSetupViewModel = LearningSetupViewModel(
        repository,
        SeedWordBooksUseCase({ "[]" }, repository),
        SelectWordBookAndSetDailyTargetUseCase(repository),
        GetLearningSettingsUseCase(NoopLearningSettingsRepository()),
        SaveLearningSettingsUseCase(NoopLearningSettingsRepository()),
        bundledIds = BundledWordBookIdSource { setOf("cet4") },
        importedIds = ImportedWordBookIdSource { setOf("my-pack") },
        deletion = WordBookDeletionService(repository, File("")) { setOf("cet4") },
        deletePreview = previews,
    )

    /** 词书包迁移 ViewModel：`refresh()` 直接读 `filesDir/wordbooks` 下的目录，两册才有选择器（一册直接进 SAF）。 */
    private fun transferViewModel(repository: RecordingProfileRepository): WordBookTransferViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return WordBookTransferViewModel(context, repository, Dispatchers.IO)
    }

    /** 造两册已导入词书的目录（只建目录，不写内容：`refresh()` 只列目录名）。 */
    private fun prepareImportedWordBookDirectories(vararg names: String): List<File> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.filesDir, "wordbooks")
        root.mkdirs()
        return names.map { File(root, it).apply { mkdirs() } }
    }

    // ---------- 替身 ----------

    /**
     * 记录型档案仓储：`deleteWordBook` **计入**调用清单（共享那份 `FakeLearningProfileRepository`
     * 不记这件事，而「取消零写入」这条判据必须数得出删除调用，所以在本类自带一份）。
     */
    private class RecordingProfileRepository : LearningProfileRepository {
        val books = mutableListOf(
            WordBook("cet4", "四级词书", "CET-4", 1, "v1", "ngsl-nawl-1.2"),
            WordBook("my-pack", "我的词书", "自定义", 100, "v1", "user-provided-xlsx"),
        )
        val deleteCalls = mutableListOf<Pair<String, String>>()

        override suspend fun current(profileId: String) = RepositoryResult.Success<LearningProfile?>(null)

        override suspend fun save(profile: LearningProfile) = RepositoryResult.Success(Unit)

        override suspend fun listWordBooks() = RepositoryResult.Success(books.toList())

        override suspend fun findWordBook(id: String) = RepositoryResult.Success(books.find { it.id == id })

        override suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit> {
            books.removeAll { it.id == wordBook.id }
            books += wordBook
            return RepositoryResult.Success(Unit)
        }

        override suspend fun deleteWordBook(profileId: String, wordBookId: String): RepositoryResult<Unit> {
            deleteCalls += profileId to wordBookId
            books.removeAll { it.id == wordBookId }
            return RepositoryResult.Success(Unit)
        }
    }

    /** 级联条数预览替身：返回固定条数，同时记录「哪个 profileId 问的哪一册」，供正文数字逐字钉。 */
    private class RecordingDeletionPreview(private val value: WordBookDeletionPreview) : WordBookDeletionPreviewRepository {
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun preview(profileId: String, wordBookId: String): Result<WordBookDeletionPreview> {
            calls += profileId to wordBookId
            return Result.success(value)
        }
    }
}
