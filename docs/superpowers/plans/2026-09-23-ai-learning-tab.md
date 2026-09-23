# 四栏底部导航与「AI 学」页实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在应用底部建立四栏导航（学习 / 阅读 / AI 学 / 设置），并交付一个结构完整、文案与代码事实相符的「AI 学」页及其四个功能页面骨架。

**Architecture:** 引入两级导航——一级为显示底部导航的 tab 根页面，二级为覆盖全屏（含底导）的专注层。Tab 用纯 Kotlin 枚举 `AppTab` 表达，可在 JVM 锁定；底导图标用 Compose `Canvas` 自绘，避免新增图标库依赖（项目开启了 dependency locking）。「AI 学」页由数据驱动的功能清单渲染，每个功能页的状态文案由枚举携带，可被测试断言。

**Tech Stack:** Kotlin、Jetpack Compose、Material 3、Room（沿用）、Coroutines、JUnit4（JVM 与 instrumented）。

**Spec:** `docs/superpowers/specs/2026-09-23-ai-learning-tab-design.md`

## Global Constraints

- **不引入新依赖。** 项目开启 `dependencyLocking { lockAllConfigurations() }`，加依赖需同步落锁；图标一律 Compose `Canvas` 自绘。
- **不新增权限。** 本轮不加 `INTERNET`，不做任何网络调用。
- **沿用薄荷绿主题**，不得引入第二套配色（`MintPalette.kt` 里的色值即全部可用色）。
- **页面文案必须与代码事实相符。** 任何「已完成」措辞都必须对应真实存在的实现；未实现的必须写明未实现。
- **不得出现点了没反应的入口。** 每个可点元素都要有可见的状态变化或导航结果。
- **不得破坏既有测试。** 现有 179 个 JVM 测试与 92 个真机测试必须继续全绿。
- 每个 Task 独立提交；提交信息用 `feat(ui):` / `refactor(ui):` / `test(ui):` 前缀。
- 真机验证走 `adb install -r -t` + `am instrument`，**不得用 `connectedDebugAndroidTest`**（会卸载应用删数据）。

---

### Task 1: 锁定 tab 与 AI 功能清单的领域定义

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/AppTab.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/AiFeature.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/AppTabTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/AiFeatureTest.kt`

**Interfaces:**
- `enum class AppTab(val label: String, val contentDescription: String)`，取值 `LEARNING` / `READING` / `AI` / `SETTINGS`
- `enum class AiFeature(val key: String, val title: String, val summary: String, val status: String, val implemented: Boolean, val dependencies: List<String>)`，取值 `WORD_PASSAGE` / `CLOZE` / `LISTENING` / `COACH`
- 展示顺序即 `entries` 顺序，不另加 `ordered()` 包装，避免没有第二个使用者的间接层。

- [x] 写 `AppTabTest`：断言恰好 4 个取值、顺序为 LEARNING/READING/AI/SETTINGS、label 与 contentDescription 非空且 label 互不重复、`AI.label == "AI 学"`。
- [x] 写 `AiFeatureTest`：断言恰好 4 个功能、顺序固定、`key` 唯一且为 kebab-case、`title`/`summary`/`status` 非空、每个功能至少一条依赖说明。
- [x] 写**双向一致性断言**：`implemented == false` ⇔ `status` 以「未实现」开头。这样「接通了却忘改文案」和「只改文案没实现」两个方向都会失败，而不是只靠字符串包含关系。
- [x] 运行 `./gradlew :app:testDebugUnitTest --tests "*AppTabTest" --tests "*AiFeatureTest"` 确认 RED（枚举不存在）。
- [x] 实现两个枚举使测试转 GREEN。
- [x] 提交 `feat(ui): define the four app tabs and the AI feature catalogue`。

### Task 2: 自绘底部导航组件

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/AppBottomBar.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/AppBottomBarTest.kt`

**Interfaces:**
- `fun AppBottomBar(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier)`
- 每个 tab 节点 `testTag("app_tab_${tab.name.lowercase()}")`，`contentDescription` 取 `tab.contentDescription`

- [x] 写 `AppBottomBarTest`：四栏节点全部存在；点击「AI 学」回调收到 `AppTab.AI`；点击「设置」回调收到 `AppTab.SETTINGS`；选中项与其他项的节点都可被断言（用 `testTag`）。
- [x] 实现 `AppBottomBar`：`Surface` + `Row` 等宽四栏；每栏 `Column`（自绘图标 + 文字）；选中态颜色 `MintPrimary`，未选中 `MintTextMuted`；高度 64dp + `navigationBarsPadding()`。
- [x] 实现四个自绘图标（`Canvas` + `Path`，24dp 画布）：
  - 学习：圆角矩形书脊 + 中间竖线
  - 阅读：三条横线（首条稍短）
  - AI 学：四角星（sparkle）
  - 设置：三条带圆点的滑杆
- [x] 提交 `feat(ui): add a self-drawn four-slot bottom navigation bar`。

### Task 3: 「AI 学」页

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/AiLearningScreen.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/AiLearningScreenTest.kt`

**Interfaces:**
- `fun AiLearningScreen(todayWordCount: Int?, dueWordCount: Int?, aiConfigured: Boolean, onOpenFeature: (AiFeature) -> Unit, onOpenWordList: () -> Unit)`

- [x] 写 `AiLearningScreenTest`：`ai_learning_screen` 根节点存在；四个功能行节点（`ai_feature_${key}`）都存在且可点；点头文串学回调收到 `AiFeature.WORD_PASSAGE`；`ai_learning_nav_card` 存在；`查看详情` 可点并触发 `onOpenWordList`；断言「AI 尚未接通」徽章在 `aiConfigured = false` 时出现、`true` 时消失。
- [x] 实现页头渐变卡（复用 `AppleMintGradient`）+ 状态徽章。
- [x] 实现四个功能行（`AiFeature.entries` 驱动，不硬编码顺序）。
- [x] 实现「AI 学习导航」卡与渐变边框「查看词表」卡，卡内显示 `todayWordCount`/`dueWordCount` 真实数字。
- [x] 实现「AI 学如何起作用」说明区（可折叠，默认收起）。
- [x] 提交 `feat(ui): build the AI learning tab with four feature entries`。

### Task 4: 四个功能的页面骨架

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/AiFeatureScreen.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/AiFeatureScreenTest.kt`

**Interfaces:**
- `fun AiFeatureScreen(feature: AiFeature, todayWordCount: Int?, dueWordCount: Int?, onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenLearning: () -> Unit)`

- [x] 写 `AiFeatureScreenTest`：四个功能各自渲染时标题正确、「当前进度」块存在、依赖清单条数与 `feature.dependencies.size` 一致、「先去配置 AI」可点并触发 `onOpenSettings`、`onBack` 可点。
- [x] 实现单页骨架，五个区块按 Spec 第 5 章排列。
- [x] 提交 `feat(ui): add honest skeletons for the four AI features`。

### Task 5: 「设置」页并收纳学习工具

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningToolsScreen.kt`（内容并入后删除该文件与其测试，或保留为内部 section）
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/SettingsScreenTest.kt`

**Interfaces:**
- `fun SettingsScreen(profileName: String, wordBookName: String?, dailyNewTarget: Int?, onOpenSetup: () -> Unit, onOpenWorksheet: () -> Unit)`

- [x] 写 `SettingsScreenTest`：`settings_screen` 存在；四个分组标题（学习 / 阅读 / AI / 账户）存在；「生成默写纸」仍在且可点；「调整词书与目标」可点并触发 `onOpenSetup`。
- [x] 实现设置页：顶部资料卡（昵称 + 词书 + 每日目标）+ 四个分组；已实现项可点，未实现项显示为禁用态并标注「后续版本」，不得做成可点无反应。
- [x] 提交 `feat(ui): add a settings tab that absorbs the learning tools entry`。

### Task 6: 把底导接进 AppScreen

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReadingAccessScreen.kt`（`onBack` 改为可空，tab 内不显示返回按钮）
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt`（补充）

- [x] 先跑现有 `AppScreenTest` 记录基线，确认修改前全绿。
- [x] 加 `selectedFeatureKey` 的 `rememberSaveable`（存 key 而非枚举实例）与 `selectedTab` 的 `rememberSaveable`。
- [x] 按 Spec 3.5 排列 `BackHandler` 链，新增「非学习 tab → 回学习 tab」与「AI 功能层 → 回 AI tab」两条。
- [x] 把 `else`（原今日计划分支）改为按 `selectedTab` 分发四个根页面，外层套 `Scaffold(bottomBar = { AppBottomBar(...) })`。
- [x] 二级全屏层保持覆盖在 `Scaffold` 之上（不进 `content`），确保底导隐藏。
- [x] 切换「阅读」tab 时触发 `readingAccessViewModel.load(...)`，入参取 `todayState` 的 `isUnlocked` / `unlockReason`。
- [x] 补 `AppScreenTest`：断言底导四栏存在；从今日计划切到 AI 学后 `ai_learning_screen` 存在；切到设置后 `settings_screen` 存在。
- [x] 提交 `feat(ui): wire the four-slot bottom navigation into the app shell`。

### Task 7: 真机验收与证据归档

**Files:**
- Create: `docs/verification/ai-learning-tab/README.md`
- Create: `docs/verification/ai-learning-tab/*.png`

- [x] 构建：`./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`（工作树外跑需禁用沙箱）。
- [x] 跑全量 JVM 测试，记录通过数与失败数。
- [x] `adb install -r -t` 安装主 APK 与测试 APK。
- [x] 跑全量 instrumented 测试：`adb shell am instrument -w com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner`。
- [x] `adb shell run-as <pkg> ls -la .../databases` 比对跑测前后的 `english-learning.db` / `-wal` / `-shm` 时间戳与大小，证明数据未被清空。
- [x] 跑测前锁竖屏；跑测期间不触碰手机。
- [x] adb 走查并截图归档：四栏底导、AI 学页头卡与四个功能行、四个功能页（逐个）、设置页、全屏层出现时底导隐藏、返回后回到原 tab。
- [x] 写 `docs/verification/ai-learning-tab/README.md`：逐条对应 Spec 第 6 章 AC-1~AC-9，附命令与截图文件名。
- [x] 提交 `docs(verification): record the AI learning tab on-device walkthrough`。

### Task 8: 文档与记忆回写

**Files:**
- Modify: `docs/superpowers/specs/2026-09-17-android-ai-english-vocabulary-app-prd.md`（信息架构表补「AI 学」入口）
- Modify: `.workbuddy/memory/MEMORY.md`、`.workbuddy/memory/2026-09-23.md`
- Modify: `C:/Users/20212/.workbuddy/MEMORY.md`（若产生新的本机/工具坑）

- [ ] 若过程中发现新工具坑，按既有格式补进用户级 `MEMORY.md`；没有则不写。
- [ ] 项目 `MEMORY.md` 的「当前产品待办」中，把「三栏底部导航」更新为已完成的四栏，并记入「AI 学」页的状态。
- [ ] 追加当日 memory：交付了什么、四个功能的真实状态、下一步依赖。
- [ ] 若「自绘 tab 图标以避免 dependency locking 冲突」「全屏层覆盖 Scaffold 以保证底导隐藏」形成可复用套路，考虑沉淀为 skill。
- [ ] 提交 `docs: update the roadmap notes for the AI learning tab`。

---

## 执行记录与计划偏差

以下是与计划不一致的地方，按事实记录，供后续复用：

1. **JVM 测试源集是 JUnit 5，不是 JUnit 4。** `app/build.gradle.kts` 里挂的是 `libs.junit.jupiter` 与 `libs.kotlin.test.junit5`；`androidTest` 才是 JUnit 4。计划里没写清，第一版用了 `org.junit.Assert` / `org.junit.Test` 直接编译失败。JVM 测试统一用：
   ```kotlin
   import kotlin.test.assertEquals
   import kotlin.test.assertTrue
   import org.junit.jupiter.api.Test
   ```
2. **`kotlin.test` 断言的参数顺序是「实际值在前、消息在后」**，与 JUnit 4 的 `assertEquals(message, expected, actual)` 相反。写反后报的是
   `None of the following candidates is applicable` / `Argument type mismatch: actual type is 'String', but 'Double' was expected`，不好一眼看出是参数顺序问题。
3. **RED 未逐任务单独观察。** 枚举与测试是同批写下的，「确认 RED」这几步实际上没有记录到预期原因的失败（记录的失败来自上面 1、2 两条环境/写法问题）。后续若要真正走 TDD 节奏，应先只写测试跑一次再实现。
4. **`AiFeature` 多了一个 `glyph` 字段**（计划里只列了 key/title/summary/status/implemented/dependencies）。原因是列表左侧需要四个互不相同的单字标记来替代图标库，四个标题里有两条都以「单词」开头，靠首字推导会撞车。测试断言了 glyph 唯一且长度为 1。
5. **`ReadingAccessScreen.onBack` 改成可空**（`(() -> Unit)? = null`），为 `null` 时不渲染返回按钮。既有测试传的是 lambda，签名兼容，不需要改动。
6. **`TodayPlanScreen` 加了 `verticalScroll`。** 底导吃掉 64dp 后，小屏上最后一排按钮会被裁掉。连带把既有 `AppScreenTest` 里点「开始学习」「调整词书与目标」的地方补了 `performScrollTo()`，否则点击可能落在可视区外。
7. **删除了 `LearningToolsScreen.kt`**（内容并入「设置」tab），`showLearningTools` 状态一并移除。`TodayPlanScreen` 上的入口按钮改名为「学习工具与设置」，点击改为切到设置 tab。
8. **修正了一处既有返回键优先级缺陷。** `BackHandler` 是「后声明者优先」，原代码把 `showWorksheetSettings` 声明在 `showWorksheetPreview` 之后，两者同时为真（从设置页点进预览）时返回键会先关设置页而不是预览页。现在按优先级从低到高排列，并在代码里写了注释说明顺序不能随手重排。
9. **`aiConfigured` 目前硬编码 `false`。** 因为 AI 网关（F2-03）未实现、也没有任何创建 AI 配置的界面，恒为假是当前事实。已在代码里留下显式注释：F2-02 的配置界面落地后必须改为读 `AiProfileRepository`，否则徽章会撒谎。
10. **真机走查抓到两处「文案说谎」，已修。** 这类缺陷组件级测试抓不到，只有把页面放到真机上看才会暴露：
    - **设置页资料卡写了「大学英语四级 · 每日新增 0 词」。** 实情是该词书新词已学完，当日计划的 `newTarget` 因此为 0（PRD FR-01 第 8 条：剩余新词少于目标时以实际剩余为准），而用户配置的每日目标并非 0。把计划数说成配置目标就是错的。已把 `dailyNewTarget` 改名为 `todayNewTarget` / `todayDueTarget`，文案改成「今日计划：新增 X · 复习 Y」，并加了一条专门的回归测试锁住「不得再出现『每日新增 N 词』」。
    - **默写纸设置页返回按钮写着「← 返回学习工具」**，但「学习工具」页已在本轮被删除、入口搬到了「设置」栏。已改为「← 返回设置」，并让 `WorksheetSettingsScreenTest` 断言旧文案不再存在。
11. **顺手清掉一处死在代码里的返回入口。** `ReadingAccessScreen` 的 `onBack` 当时没有任何生产调用方（它只在「阅读」栏当根页面用），却保留着「返回今日计划」的语义标注。一级 tab 出现「返回上一层」本身就是层级错误，于是删掉了这个参数与按钮，并同步改掉两处测试调用。

### 从这一轮得到的操作规律

- **文案准确性只能靠真机走查兜住。** `assertExists` 类测试关心的是「节点在不在」，不关心「写的话对不对」。凡是把数字、层级、页面名写进用户可见文案的地方，都要按真机截图逐句读一遍。
- **走查脚本值得留下。** `verification-logs/walk.sh shot|dump|tapname|texts <name>` 把 `screencap`、`uiautomator dump`、按 `content-desc` 取中心点点击、提取全部文本四件事包好了，后面几轮可直接复用；注意传 Android 绝对路径时必须 `MSYS_NO_PATHCONV=1`，且脚本里写死的输出目录不能再传 `/tmp`（Python 是 Windows 程序，不认 MSYS 路径）。
