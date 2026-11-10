# 2026-11-09 拼写测验接线 + 设计系统收口 工作报告

用户原话（m00649）：对「（1）把拼写判分写进复习排期、（2）给新屏加 connected Compose 测试、
（3）设计系统统一，先从 设置/我的 开始」这三项的回答是 **「全部都做」**；
随后 m01107「继续开发下一步」、在选定「收掉 我的 最后一处 M3 Card 漂移」后继续推进到底。

## 1. 本次目标

让 拼写测验 的判分真的影响复习排期（并改掉原来那句诚实但消极的界面文案）、补上该屏的
instrumented 测试、把 设置/我的 两页并回共享设计系统容器；最后把「逐屏不许碰 M3 Card」
从注释自述变成真正的契约断言。

## 2. 完成了什么

### 2.1 拼写判分写进复习排期

- 新增 `learning/spelling/RecordSpellingFeedbackUseCase.kt`：三档判分经 `toCardFeedback()`
  折成 V1 三档反馈，走**已有的** `SubmitCardFeedbackUseCase`（幂等 eventId / 版本号 / 事件表一字不另开）。
  planId 用合成前缀 `"spelling:" + profileId`（沿用「重学」`relearn:` 先例），
  **只推词卡排期，不冒充今日计划完成度**。结局只有 `Recorded(nextReviewAt)` / `StorageUnavailable`，
  同一 eventId 的重放归到同一档（AC1-04）。
- 新增 `ui/SpellingQuizViewModel.kt`（`@HiltViewModel`）：把每题判分交给上面那条用例，
  译成 `SpellingScheduleState`（`Idle / Saving / Recorded(whenLabel) / Failed`）。
  `whenLabel` 由调度器真实写入的 `nextReviewAt` 反推（复用 `receiptWhenLabel`），不写死「3 天后」。
  用 `sequence` 号作废旧的在途写入，防快速连答时慢的旧结果盖掉新题回执。
- `ui/SpellingQuizScreen.kt`：注入 `profileId` + `spellingViewModel`（缺一不可，缺任一仍是纯练习器）；
  每题下方新增回执行「已记入复习排期：这个词…再问一次。」/ 写库失败的真例外一行；
  结算卡原来那句「这一轮只在本屏结算，不改动复习排期」改成「每题结果已按三档记入复习排期：
  拼对当『认识』、差一点当『模糊』、拼错当『不认识』」并点名不改动今日计划完成度。
- `ui/ReviewQueueScreen.kt` + `AppScreen.kt`：复习队列 →「拼写测验」入口把 `profileId` 与
  `SpellingQuizViewModel` 一路带进那一屏（`AppScreen.kt` ReviewQueue 分支注释写明了这两个参数的作用）。
- 测试：`RecordSpellingFeedbackUseCaseTest`、`SpellingVerdictFeedbackMappingTest`、
  `SpellingQuizViewModelTest`（幂等 / 失败 / 乱序盖写）。

### 2.2 SpellingQuizScreen 的 connected 测试

- `app/src/androidTest/java/com/example/englishlearning/ui/SpellingQuizScreenTest.kt`：
  覆盖空题单（`spelling_quiz_summary` 直接给「没有要拼的词」）、逐题作答三档回执、
  「看本轮结果」结算卡、再来一遍、返回，以及 `SpellingScheduleState` 三态下的回执文案。
  该屏的可测性靠 `testTag`：`spelling_quiz_screen / _summary / _summary_detail / _restart / _back /
  _progress / _prompt / _answer / _submit / _next`。

### 2.3 设置 / 我的 并回共享设计系统

- `ui/components/SegmentedControl.kt`：新增可选参 `role: Role = Role.Tab` 与
  `itemTestTags: List<String>? = null`（默认值不变，既有调用点零改动）。
- `ui/SettingsScreen.kt`：删掉自建的 M3 `Card` + 私有 18dp 卡片圆角 / 11dp 段圆角，
  改挂 `AppCard` + 共享 `SegmentedControl(role = Role.RadioButton, itemTestTags = settings_theme_<storageKey>)`
  （主题档位是单选语义，故用 RadioButton 而非 Tab）。
- `ui/MyWordsScreen.kt`：私有 `MyWordsSectionCard` 的 M3 `Card` + `CardDefaults.cardColors` +
  `RoundedCornerShape(18.dp)` + 手写 `outlineSoft` 描边换成 `AppCard(contentPadding = 0.dp)`，
  内层保留 16/12dp 内距 + 4dp 节奏。全仓主代码里最后一处 `RoundedCornerShape(18.dp)` 就此消失。
- 登记文档：`docs/decisions/2026-11-09-mywords-section-card-on-shared-appcard.md`
  （含接受的可见偏差：18dp→20dp、`outlineSoft`→`outline`；并明确**不做**全局 20↔18dp 改判，
  那仍是 `docs/verification/visual-walkthrough-2026-11-08.md` B3 等用户拍板的事项）。
- `ContainerContractTest`：`screens` 表加入 `SettingsScreen.kt`、`MyWordsScreen.kt`；
  新增用例 `no listed screen reaches for the material3 card again`（逐屏 `\bCard(` 命中数必须 0）
  并配两条反空洞样本，防止正则退化成永不命中。

## 3. 验证记录

- 真机 E2E（小米 M2102J2SC bf353dda，adb `C:\adb\platform-tools\adb.exe`）——拼写→排期闭环：
  词卡 the 判「不认识」→ 卡上写「约 9 分钟后再问一次」；在 学习计划 里切词书再切回并保存
  （触发当日计划重建，见下 §5 已知问题）→ 首页 复习队列 1 个词到期 → 拼写测验答题 the →
  回执「拼对了：the」+「已记入复习排期：这个词约 23 小时后再问一次。」（10 分钟档被推到 23 小时）；
  再做同一轮切换 → 首页 复习队列 0 个词到期，证明 `next_review_at` 真的动了。
- 设计系统：`:app:testDebugUnitTest --offline` 全绿（348 个测试类 / **2601** 例 / 0 skipped / 0 失败，
  XML 时间戳全部落在该次运行）；RED 先验：`--tests "*ContainerContractTest*" --rerun-tasks` 一次运行
  **1 失败**且失败点正是 MyWordsScreen 的 M3 `Card(`，改完转 GREEN。
- connected：`:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<测试类>`
  ——`SettingsScreenTest` 5/5 绿（Role.RadioButton + 选中态 + 正好 4 个可点节点 +
  `settings_theme_<storageKey>` tag）；`SpellingQuizScreenTest` 绿。
  批跑 `ProfileScreenTest + AppScreenOverlayContainerTest` 时曾有一次
  `hostedBackupRowOpensTheHubAndBackReturnsToTheProfileHub` 超时（`AppScreenOverlayContainerTest.kt:422`），
  单跑该用例即绿 ⇒ 批跑时序 flake，与本轮改动无关（该路径不经过设置屏）。
- 真机冒烟：`:app:assembleDebug` 后 `install -r`（保留数据）→ 今日学习 / 我的 hub / 词库「收藏」段
  全部正常渲染（收藏段正是 `MyWordsSectionCard` 经 `visibleSections = listOf(Favorites)`、
  `showHeader = false` 挂进词库壳的那一面），`my_words_*` tag 与文案不变，logcat 无崩溃。

## 4. Git 状态

- 本执行环境 **无 git 可执行文件**（`where git` 空），未提交、未推送。
  待用户在有 git 的机器上提交，建议 message：
  `feat(spelling): 判分写进复习排期 + 设计系统收口（设置/我的） + 逐屏零 M3 Card 契约`。
- 涉及文件：`ui/components/SegmentedControl.kt`、`ui/SettingsScreen.kt`、`ui/MyWordsScreen.kt`、
  `ui/ReviewQueueScreen.kt`、`ui/SpellingQuizScreen.kt`（+ 新 `ui/SpellingQuizViewModel.kt`）、
  `learning/spelling/RecordSpellingFeedbackUseCase.kt`、`ui/AppScreen.kt`、`di/AppModule.kt`、
  测试 `ContainerContractTest.kt`、`RecordSpellingFeedbackUseCaseTest.kt`、
  `SpellingVerdictFeedbackMappingTest.kt`、`SpellingQuizViewModelTest.kt`、
  `androidTest/.../SpellingQuizScreenTest.kt`、`androidTest/.../SettingsScreenTest.kt`（更新），
  文档 `docs/decisions/2026-11-09-mywords-section-card-on-shared-appcard.md` 与本报告。

## 5. 未完成 / 遗留问题

- **`GetOrCreateTodayPlanUseCase` 的当日计划缓存**：只要 `localDate` + `activeWordBookId` 没变就早返回，
  当日 `dueCardIds` 只在计划被创建/替换时重算，于是「先判不认识、同一天再回首页」看不到该词进复习队列
  （本轮真机验证靠「切词书 → 保存 → 切回 → 保存」绕过）。这是产品行为问题，未改：
  是否要「进入首页时按当前时刻重算当日到期集合」需要用户拍板（会改今日计划快照的不变量语义）。
- **全局卡片圆角 20↔18dp**：仍是 `docs/verification/visual-walkthrough-2026-11-08.md` B3 的待拍板项；
  `MyWords` 已按 20dp 并轨，其余屏不再各自漂移。
- **`WordBankScreen` 手写分段控件**（`RoundedCornerShape(13.dp)` / `10.dp`）可换共享 `SegmentedControl`，
  但轨道会从圆角矩形变成 `AppShape.Pill`，属可见改版，需用户签字。
- `ReminderSettingsScreen` 仍保留 M3 `AlertDialog` + Material TimePicker，作为「平台选择器例外」记录在案。
- 拼写判分**不推进今日计划进度**（`复习 0/1` 不动），这是设计决定的（只动 `CardReviewState`）；
  `复习队列` 浮层是打开时的快照，所以本轮内它会一直显示「1 个词在等你」直到重开。

## 6. 下一任接手须知

- 先读 `docs/decisions/2026-11-09-mywords-section-card-on-shared-appcard.md`，再看
  `docs/verification/visual-walkthrough-2026-11-08.md`（B3 等用户项）。
- 改屏前先确认它是否已在 `ContainerContractTest.screens` 表里：不在表里的屏等于没人看管。
- 检查 RED 必须带 `--rerun-tasks`，否则 Gradle 可能直接回放上一次的绿结果；
  判绿要核对 `app/build/test-results/testDebugUnitTest` 的类数/例数/时间戳，而不是只看 BUILD SUCCESSFUL。
- 设备输入坑：第三方输入法（如微信输入法）激活时 `adb shell input text` **静默无效**，
  需先 `adb shell ime set com.sohu.inputmethod.sogou.xiaomi/.SogouIME`；测完记得切回原输入法。

## 7. 用户确认过的决策

- m00649「全部都做」= 三项一起做（拼写接线 / connected 测试 / 设计系统统一从 设置·我的 起）。
- 拼写判分写排期时**不**冒充今日计划任务完成（沿用「重学」合成 planId 先例）。
- 设计系统统一以「共享容器 + 令牌」为准，不为单个屏改公共默认值。
