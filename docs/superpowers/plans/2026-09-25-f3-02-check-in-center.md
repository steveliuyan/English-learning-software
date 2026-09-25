# F3-02 打卡中心 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有四栏底部导航的前提下，增加从学习计划页进入的打卡中心，展示今日背词/阅读完成情况、完成词卡回览、本周完成任务柱状图、背词完成度和月历学习状态。

**Architecture:** 新增只读 `LearningStatsRepository` 作为统计边界，Room DAO 负责按 profile 与日期范围聚合 `learning_events`、`reading_completions` 和今日计划数据；`CheckInViewModel` 将统计转换为稳定的 `CheckInUiState`，`CheckInScreen` 只渲染状态并通过回调返回。`AppScreen` 保留 `LEARNING/READING/AI/SETTINGS` 四个一级目的地，在学习页内部用 overlay 状态承载打卡页。

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt, Room, kotlinx-coroutines-test, AndroidX Compose UI tests, JUnit 4/JUnit 5。

**Spec:** `docs/superpowers/plans/2026-09-25-f3-roadmap.md`（F3-02 打卡闭环族）及已批准的方案 B：打卡中心作为学习页二级页面，不新增一级 Tab。

## Global Constraints

- 保持 `AppTab` 四栏及其顺序不变：学习、阅读、AI 学、设置。
- 统计只读派生，不新增“打卡表”；使用 `learning_events`、`reading_completions`、`today_plan`/`today_plan_tasks`。
- 当前数据没有可靠学习时长语义；图表文案必须使用“本周完成任务”，不得伪造“学习时长”。
- 所有日期聚合按 `ClockProvider.zoneId()` 的本地日期计算，并显式传入 profileId，禁止跨 profile 串数据。
- 遵循 TDD：先写 RED，再最小实现 GREEN；关键断言做变异测试后恢复实现。
- 真机使用同批次主 APK/androidTest APK、`adb install -r -t` 与 `am instrument`；禁止 `connectedDebugAndroidTest`。
- 不把受保护的 `app/gradle.lockfile`、`core/error/AppError.kt`、`ui/AppViewModel.kt` 混入功能提交。
- 现有未跟踪 Room schema `app/schemas/.../11.json`、`12.json` 属于前批次产物；本计划不修改或删除它们。

## 文件结构

- Create: `app/src/main/java/com/example/englishlearning/learning/domain/DailyLearningStats.kt` — 单日统计模型及完成度计算。
- Create: `app/src/main/java/com/example/englishlearning/learning/LearningStatsRepository.kt` — 统计端口及结果语义。
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningStatsDao.kt` — Room 聚合查询。
- Create: `app/src/main/java/com/example/englishlearning/learning/RoomLearningStatsRepository.kt` — Room 行映射、日期范围和错误映射。
- Create: `app/src/main/java/com/example/englishlearning/ui/CheckInViewModel.kt` — 加载今日/周/月统计并管理 UI 状态。
- Create: `app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt` — 今日统计、完成词卡、周柱状图、完成度、月历。
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt` — 暴露统计 DAO，不改变数据库版本。
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt` — 提供统计 repository。
- Modify: `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt` — 增加打卡中心入口。
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt` — 打卡 overlay、返回处理、ViewModel 加载与接线。
- Test: `app/src/test/java/com/example/englishlearning/learning/RoomLearningStatsRepositoryTest.kt` 或纯聚合测试 — 统计边界与错误语义。
- Test: `app/src/test/java/com/example/englishlearning/ui/CheckInViewModelTest.kt` — 状态转换与失败路径。
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/TodayPlanScreenTest.kt` — 打卡入口点击。
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/CheckInScreenTest.kt` — 组件展示、月历和返回。
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/ArticleReadingScreenTest.kt` — 完成阅读按钮回调回归。

---

### Task 1: 定义统计领域模型与 repository 端口

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/domain/DailyLearningStats.kt`
- Create: `app/src/main/java/com/example/englishlearning/learning/LearningStatsRepository.kt`
- Test: `app/src/test/java/com/example/englishlearning/learning/LearningStatsRepositoryContractTest.kt`

**Interfaces:**
- Produces `DailyLearningStats(localDate: LocalDate, reviewedWordCount: Int, completedReadingCount: Int, completedTaskCount: Int, targetTaskCount: Int)`。
- Produces `LearningStatsRepository.today(profileId: String, localDate: LocalDate): Result<DailyLearningStats>`。
- Produces `LearningStatsRepository.range(profileId: String, from: LocalDate, to: LocalDate): Result<List<DailyLearningStats>>`，结果按日期升序且包含范围内无数据日期的零值。

- [ ] **Step 1: Write the failing tests**

测试构造跨日期、跨 profile 的 fake repository，断言 `DailyLearningStats.completionRatio` 在目标为零时为 `0f`，目标大于零时限制在 `0f..1f`；断言 range 的契约要求调用方收到升序连续日期。

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :app:testDebugUnitTest --tests '*LearningStatsRepositoryContractTest*' --no-daemon --no-build-cache --console=plain`
Expected: FAIL because the new types do not exist。

- [ ] **Step 3: Write the minimal model and port**

实现不可变 data class；`completionRatio` 用 `targetTaskCount <= 0` 返回 `0f`，否则 `(completedTaskCount.toFloat() / targetTaskCount).coerceIn(0f, 1f)`。端口只暴露 `today` 与 `range`，不暴露 Room 类型。

- [ ] **Step 4: Run tests to verify they pass**

Run the same command; Expected: PASS。

- [ ] **Step 5: Commit**

`git add app/src/main/java/.../DailyLearningStats.kt app/src/main/java/.../LearningStatsRepository.kt app/src/test/.../LearningStatsRepositoryContractTest.kt && git commit -m "feat: define learning stats contract"`

### Task 2: 增加 Room 聚合查询与 repository 实现

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningStatsDao.kt`
- Create: `app/src/main/java/com/example/englishlearning/learning/RoomLearningStatsRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Test: `app/src/test/java/com/example/englishlearning/learning/RoomLearningStatsRepositoryTest.kt`

**Interfaces:**
- `InternalLearningStatsDao.countReviewedCards(profileId: String, fromEpochMillis: Long, toExclusiveEpochMillis: Long): Int`。
- `InternalLearningStatsDao.countReadings(profileId: String, fromDate: String, toDate: String): List<DateCountRow>`。
- `RoomLearningStatsRepository` uses `ClockProvider.zoneId()` only to convert `LocalDate` boundaries to instants and maps all exceptions to `Result.failure(AppErrorException(AppError.StorageUnavailable))`。

- [ ] **Step 1: Write failing tests**

插入同一 profile 的两天 learning events、同日重复阅读完成记录、另一个 profile 的记录，断言 `today` 与 `range` 只计算指定 profile、日期范围采用 `[from, toExclusive)`，并补齐空日期。

- [ ] **Step 2: Run RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests '*RoomLearningStatsRepositoryTest*' --no-daemon --no-build-cache --console=plain`
Expected: FAIL until DAO/repository and test database fixture exist。

- [ ] **Step 3: Implement minimal SQL and mapping**

DAO 只做聚合查询；learning events 使用 `COUNT(DISTINCT cardId)`，reading completions 使用 `COUNT(*)`（表的 `articleId` 主键保证幂等）；repository 负责组合两类结果、补零日期，并从 `TodayPlanRepository` 或现有 plan DAO 读取当天 target。不要新增表或迁移。

- [ ] **Step 4: Register DAO/provider and run GREEN**

在 `AppDatabase` 增加 abstract DAO getter，在 `AppModule` 提供 singleton `LearningStatsRepository`；运行 Task 2 测试并确认 PASS。

- [ ] **Step 5: Run mutation checks**

临时将 SQL 的 profile 条件或日期上界移除，确认跨 profile/边界测试变红；恢复实现后再次运行测试。

- [ ] **Step 6: Commit**

`git add app/src/main/java app/src/test && git commit -m "feat: aggregate daily learning stats"`

### Task 3: 实现 CheckInViewModel

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/CheckInViewModel.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/CheckInViewModelTest.kt`

**Interfaces:**
- `CheckInUiState.Loading`、`CheckInUiState.Ready(today: DailyLearningStats, week: List<DailyLearningStats>, month: List<DailyLearningStats>, completed: Boolean)`、`CheckInUiState.Unavailable`。
- `CheckInViewModel.load(profileId: String)`。
- ViewModel 通过注入 `ClockProvider` 获取当前日期，通过 repository 一次读取当前月范围并派生本周/今日；不得在 UI 中读取系统时间。

- [ ] **Step 1: Write failing tests**

覆盖：加载后产生连续七天 week；today 指向当前日期；month 包含当月完整日期；今日完成任务达到目标且有阅读时 `completed=true`；repository 任一失败进入 `Unavailable`；重复 load 重置为 Loading。

- [ ] **Step 2: Run RED**

Run: `gradlew.bat :app:testDebugUnitTest --tests '*CheckInViewModelTest*' --no-daemon --no-build-cache --console=plain`
Expected: FAIL because state and ViewModel do not exist。

- [ ] **Step 3: Implement minimal state machine**

`load` 在 `viewModelScope` 中先发 Loading，再调用 repository.range；按 `ClockProvider.zoneId()` 派生 month/week/today；完成条件只表示“当天至少完成一项背词或阅读”，不引入连续打卡语义。

- [ ] **Step 4: Run GREEN and mutation check**

运行测试；临时删除失败映射或把当前日期改为 UTC，确认日期边界测试变红；恢复后保持全绿。

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/example/englishlearning/ui/CheckInViewModel.kt app/src/test/java/com/example/englishlearning/ui/CheckInViewModelTest.kt && git commit -m "feat: load check-in statistics"`

### Task 4: 实现 CheckInScreen 组件

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/CheckInScreenTest.kt`

**Interfaces:**
- `CheckInScreen(state: CheckInUiState, onBack: () -> Unit, onRetry: () -> Unit = {})`。
- Stable tags: `check_in_screen`, `check_in_today`, `check_in_week_chart`, `check_in_completion_ratio`, `check_in_calendar`, `check_in_back`, `check_in_unavailable`。

- [ ] **Step 1: Write failing Compose tests**

断言 Ready 展示今日完成数量、七个周数据节点、完成度区域和当月日历；Unavailable 展示重试；返回按钮触发 `onBack`；目标为零不崩溃且显示 0%。

- [ ] **Step 2: Run RED**

Run: `gradlew.bat :app:assembleDebugAndroidTest --no-daemon --no-build-cache --console=plain`
Expected: compile/test failure because screen is absent。

- [ ] **Step 3: Implement minimal accessible screen**

使用现有 Mint 主题和可滚动 Column；柱状图用 Compose `Canvas` 或简单带比例高度的 Box；完成度环使用 `Canvas`；月历按 LocalDate 网格渲染，完成日期用明确 contentDescription；不加入动画依赖或网络调用。

- [ ] **Step 4: Run focused Compose tests**

安装同批次 APK 后仅执行 `CheckInScreenTest`，Expected: PASS。

- [ ] **Step 5: Commit**

`git add app/src/main/java/.../CheckInScreen.kt app/src/androidTest/.../CheckInScreenTest.kt && git commit -m "feat: add check-in statistics screen"`

### Task 5: 接入学习页、AppScreen 与 Hilt

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: Hilt root/activity creation file that supplies ViewModels
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/TodayPlanScreenTest.kt`
- Test: existing `AppScreenTest.kt`

**Interfaces:**
- Today plan adds `onOpenCheckIn: () -> Unit = {}` and tag `today_plan_open_check_in` with contentDescription `查看打卡与成就`。
- AppScreen receives `CheckInViewModel`, stores `showCheckIn` per profile, loads it on entry, renders it before root tabs, and handles BackHandler `check-in -> learning`。

- [ ] **Step 1: Write failing UI/navigation tests**

TodayPlan test clicks the new entry and asserts callback once. AppScreen test opens it from learning tab, finds `check_in_screen`, presses the labelled back action, and asserts the today plan is visible again. Assert `AppTab.entries` remains unchanged.

- [ ] **Step 2: Run RED**

Run focused Android tests; Expected: compile failure for missing callback/argument and failing navigation assertions。

- [ ] **Step 3: Implement minimal wiring**

Add nullable/required ViewModel following existing `todayPlanViewModel` injection pattern; include `showCheckIn` in overlayOpen and BackHandler ordering; do not alter bottom bar enum or reading access logic. Add button callback in TodayPlan ready state.

- [ ] **Step 4: Run GREEN and mutation check**

Run focused tests. Temporarily move check-in branch below root-tab rendering to prove navigation test fails; restore ordering and rerun.

- [ ] **Step 5: Commit**

`git add app/src/main/java app/src/androidTest && git commit -m "feat: wire check-in center from learning plan"`

### Task 6: 补齐完成阅读按钮回归与全量验证

**Files:**
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/ArticleReadingScreenTest.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/TodayPlanScreenTest.kt`
- Modify: `docs/superpowers/plans/2026-09-25-f3-roadmap.md` — 勾选 F3-02 打卡页/成就页完成项。

- [ ] **Step 1: Add missing tests**

新增 `completeReadingButtonInvokesCallback`，点击 `article_complete_reading` 后断言回调一次；新增 TodayPlan `openCheckIn` 点击断言；保留 existing completion state tests。

- [ ] **Step 2: Run focused tests and mutation checks**

先确认新增断言在删除回调接线时变红，再恢复并通过。

- [ ] **Step 3: Run JVM suite**

Run: `gradlew.bat :app:testDebugUnitTest --no-daemon --no-build-cache --console=plain`
Expected: all existing and new JVM tests PASS。

- [ ] **Step 4: Build both APKs**

Run with `ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache`: `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks --no-daemon --no-build-cache --console=plain`。

- [ ] **Step 5: Install and run instrumentation safely**

确认设备 `bf353dda` 在线；保存数据库三件套 MD5；`adb install -r -t` 两个 APK；必要时设置 `appops 10021 allow`；使用 `am instrument -w` 执行 focused 与全量测试；启动正常 Activity 后再拉取数据库三件套并比较。

- [ ] **Step 6: Verify and commit**

确认构建无错误、测试计数对应当前 APK、数据库变化仅为预期统计/阅读完成记录；更新路线图后提交 `git status` 中本批次文件，并按项目凭据命令推送；逐字核对本地 HEAD 与远端分支。

---

## Self-review

- **Spec coverage:** 保留四栏导航、学习页二级入口、今日/周/月统计、完成度、月历、TDD、真机数据保护均由 Task 1–6 覆盖。
- **Placeholder scan:** 计划没有使用 TBD/TODO 或“适当处理”等模糊实现指令；每个任务均列出接口、测试和命令。
- **Type consistency:** Task 1 定义 `DailyLearningStats` 与 `LearningStatsRepository`；Task 3 使用其 `today/range` 语义；Task 4 使用 Task 3 的 `CheckInUiState`；Task 5 使用 `CheckInViewModel.load` 与 `CheckInScreen` 签名。
- **Scope check:** 仅覆盖 F3-02 打卡中心；连续打卡、真实学习时长、成就动画和词典等后续功能不纳入本计划。
