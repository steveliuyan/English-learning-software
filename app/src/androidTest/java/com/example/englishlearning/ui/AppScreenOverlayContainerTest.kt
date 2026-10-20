package com.example.englishlearning.ui

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
 *    `WordBookSwipeRowTest` I-A2 钉过的同一条真实入口，不是测试替身直接调 VM）；
 *  - 路 B：设置页「导出词书」入口在册数 ≥ 2 时真实打开 `GlassDialog` 选择器，
 *    列表项 tag（`settings_export_book_*`）仍逐项可达——**只断存在与可点，绝不点击**
 *    （点击会拉起 SAF 系统界面，设备用例不该把系统文件选择器卷进来）。
 *
 * `fontScale = 2.0` 那两例钉的是大字档下面板正文与按钮仍**整体在视口内**
 * （`assertIsDisplayed()` 要求节点 ≥90% 可见，正文被挤出屏幕或按钮被裁掉都会当场红），
 * 写法沿用本包先例：`LocalDensity provides Density(density, fontScale = 2f)`。
 * **但 `Dialog` 窗口的 density 取自窗口自身上下文**（17B 收口席实测：provider 覆盖只作用于宿主组成，
 * 窗口内短行在设备 scale 1.0 下以 `lineCount=1 + overflow=true` 亚像素假阳性判红），
 * 所以这两例的运行前置是设备级 `adb shell settings put system font_scale 2.0`（跑完必须回 `1.0`），
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
        val confirm = composeRule.onNodeWithTag("confirm_destructive_confirm")
        confirm.assertHasClickAction()
        confirm.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        confirm.assertTextEquals("删除")
        confirm.assertHeightIsAtLeast(48.dp)
        val dismiss = composeRule.onNodeWithTag("confirm_destructive_dismiss")
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
        composeRule.onNodeWithTag("wordbook_swipe_my-pack").assertExists()
    }

    /**
     * 钉的破坏：大字号档下面板被撑出视口——正文最后一行或两枚按钮里的任意一枚被裁到屏幕外
     * （「看得见说得出、点不着」是 F5-03 那一族缺陷）。
     *
     * 判据打在 `assertIsDisplayed()`（节点 ≥90% 落在视口内）+ 按钮触控面仍 ≥48dp 上，
     * 与 `ConfirmDestructiveDialogTest` 的组件级 48dp 护栏同口径，只是这里跑的是**真实宿主 + 真实文案**
     * （站点给的级联与保留面比组件级夹具更长，只有宿主侧才量得到实际高度）。
     */
    @Test
    fun hostedWordBookDeletePanelStaysFullyReadableAndTappableAtDoubleFontScale() {
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
        assertScaleTookEffect("其他词书不受影响。", expectMinHeightPx = 60)
        assertBodyLineSurvivesTheScale("将删除这册导入词书的词卡文件与词条 3 条。")
        assertBodyLineSurvivesTheScale(
            "学习历史与文章保留：该册学习记录 10 条、文章 2 篇仍可翻阅（复习状态与生词笔记同保留）。",
        )
        assertBodyLineSurvivesTheScale("其他词书不受影响。")
        assertBodyLineSurvivesTheScale("此操作不可撤销。")
        assertBodyLineSurvivesTheScale("删除《我的词书》？")
        composeRule.onNodeWithTag("confirm_destructive_dismiss").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("confirm_destructive_confirm").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    // ---------- 路 B：导出词书选择器（迁移前 AppScreen.kt:1608） ----------

    /**
     * 钉的破坏：选择器换成共享玻璃档后列表项 tag 被改掉（`settings_export_book_*` 是这枚自渲染
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
            createProfile()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("app_tab_settings").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("app_tab_settings").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("settings_export_word_book").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("settings_export_word_book").performScrollTo().assertIsEnabled().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("选择要导出的词书").fetchSemanticsNodes().isNotEmpty()
            }
            // 两册都在，且逐项可点（tag 口径与迁移前一致）。
            listOf("overlay-pack-a", "overlay-pack-b").forEach { bookId ->
                composeRule.onNodeWithTag("settings_export_book_$bookId")
                    .assertIsDisplayed()
                    .assertHasClickAction()
            }
            // 取消是共享药片（tag + 可点 + 朗读文本），且真的收场。
            val cancel = composeRule.onNodeWithTag("wordbook_export_cancel")
            cancel.assertHasClickAction()
            cancel.assertTextEquals("取消")
            cancel.performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("选择要导出的词书").fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("settings_export_book_overlay-pack-a").assertDoesNotExist()
            composeRule.onNodeWithTag("settings_screen").assertExists()
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
            createProfile()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("app_tab_settings").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("app_tab_settings").performClick()
            composeRule.onNodeWithTag("settings_export_word_book").performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("选择要导出的词书").fetchSemanticsNodes().isNotEmpty()
            }
            // 哨兵先行：Headline 标题 scale 1.0 行高 73px、scale 2.0 折两行 ≥150px，取 100 为界。
            assertScaleTookEffect("选择要导出的词书", expectMinHeightPx = 100)
            assertBodyLineSurvivesTheScale("选择要导出的词书")
            composeRule.onNodeWithTag("settings_export_book_overlay-pack-a").assertIsDisplayed()
            composeRule.onNodeWithTag("settings_export_book_overlay-pack-b").assertIsDisplayed()
            composeRule.onNodeWithTag("wordbook_export_cancel").assertIsDisplayed().assertHasClickAction()
        } finally {
            books.forEach { it.deleteRecursively() }
        }
    }

    // ---------- 宿主驱动 ----------

    /**
     * 建资料。落点屏由宿主按今日计划的状态决定，所以这里**不等具体节点**，
     * 两路各自等自己那一站：路 A 等 `MissingLearningSetup` 逼出的必填选取屏，
     * 路 B 等 `placeholderCardPlan()` 给出的今日计划栏（底导四栏都在，才能切设置栏）。
     */
    private fun createProfile() {
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
    }

    /** 路 A 驱动链：建资料 → 必填的「调整词书与目标」屏把导入册画出来。 */
    private fun createProfileAndOpenSetup() {
        createProfile()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("wordbook_swipe_my-pack").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * 用**真实无障碍动作**触发删除请求：TalkBack 用户走的就是这条「删除词书」自定义动作
     * （同 `WordBookSwipeRowTest` 的 I-A2 取证口径），不是测试替身直接调 `requestDelete`。
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
     * 9/9 字符全部在布局内、节点完整在视口内，画面上没有任何一个字被吃掉。所以这里判**两枚真截断信号**：
     *  ① `getLineEnd(末行) == text.length`——maxLines/Ellipsis 截断时末行必然布局不到文本末尾；
     *  ② `getLineRight(末行) <= 节点盒宽`——任何一行伸出自己的盒就是真裁切。
     * 「被挤出屏幕」那半边仍由 `assertIsDisplayed()`（节点 ≥90% 落在视口内、且已按父级裁剪算过）承担。
     */
    private fun assertBodyLineSurvivesTheScale(text: String) {
        val node = composeRule.onNodeWithText(text)
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertEquals(
            "fontScale=2.0 下「$text」必须整句被布局（末行没到文本末尾 = 有字没画出来）：" +
                "lineCount=${layout.lineCount} overflow=${layout.hasVisualOverflow} size=${layout.size}",
            text.length,
            layout.getLineEnd(layout.lineCount - 1),
        )
        assertTrue(
            "fontScale=2.0 下「$text」末行不得伸出文本盒（真裁切信号）：" +
                "lineRight=${layout.getLineRight(layout.lineCount - 1)} size=${layout.size} bounds=$bounds",
            layout.getLineRight(layout.lineCount - 1) <= bounds.width,
        )
    }

    /**
     * 「字号真的落进了对话框窗口」哨兵：`Dialog` 窗口的 `LocalDensity` 取自**窗口自身上下文**，
     * 调用方的 `LocalDensity` 覆盖只对宿主组成生效、对窗口内不生效（17B 收口席实测：设备
     * `font_scale=1.0` 时窗口内 `13sp` 行高恒为 40px 单行，还会以 `lineCount=1 + overflow=true`
     * 的亚像素假阳性刺穿截断判据）。判据取**行高**而不是折行：实测同一枚 Footnote 行
     * scale 1.0 = 40px、scale 2.0 = 77px，而「其他词书不受影响。」在 scale 2.0 下 585px
     * 恰可容进内容宽 588px、**单行并不折**——用「必折行」当哨兵会把合法渲染判红。
     * 因此 [expectMinHeightPx] 按档位取 scale-1 与 scale-2 行高之间的阈值：设备字号没到 2.0 就当场红，
     * 护栏不许退化成「scale 1.0 复读」的假绿。
     * 运行前置：`adb -s emulator-5574 shell settings put system font_scale 2.0`（跑完必须回 `1.0`）。
     */
    private fun assertScaleTookEffect(sample: String, expectMinHeightPx: Int) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(sample)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue(
            "fontScale=2.0 未落进对话框窗口（「$sample」行高应 ≥${expectMinHeightPx}px，实测 size=${layout.size}）：" +
                "connected 侧需先把设备 font_scale 置 2.0，护栏不许在 scale 1.0 下空转",
            layout.size.height >= expectMinHeightPx,
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
