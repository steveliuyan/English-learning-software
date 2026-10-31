# 批 5 修复：四栏底导 + 我的功能中心 + AI 页去渐变

- **日期：** 2026-11-02
- **依据：** `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`（取代
  `2026-10-31-settings-as-fifth-tab.md`）
- **分支：** `stage-5-ui-r2-learning-path`
- **目标：** 撤回「设置第五栏」的加法式改版，按参考重做「我的」入口页与 AI 助学页页头。

## 验收门禁

1. 本地 Gradle 8.13（`--offline --no-daemon --no-build-cache`）依次：
   `:app:assembleDebug` → `:app:testDebugUnitTest` → `:app:compileDebugAndroidTestKotlin`。
2. `:app:assembleDebugAndroidTest` 后，在 `emulator-5574` 按 `adb install -r` + `am instrument`
   跑受影响的 connected 类（font_scale 1.0 与 2.0）。**不使用** `connectedDebugAndroidTest`。
3. 真机 `bf353dda` 只做 `adb install -r` + 手工走查。
4. 截图落 `docs/design/`：四栏底导（浅/深）、我的 hub（浅/深）、设置子页、AI 页（浅/深）。
5. 报告按 `docs/handoff/REPORT-TEMPLATE.md`；按功能域提交；推送后 `git ls-remote` 核对远端 tip。

## 步骤

### 1. 导航回四栏

- 先改测试到新期望并看红：
  - `AppTabTest`：恰四栏 `LEARNING/READING/AI/PROFILE`，`SETTINGS` 不存在。
  - `AppBottomBarTest`：四槽、四标签（无「设置」）、逐格点击。
  - `AppBottomBarContractTest`：四枚字形映射（Book/Newspaper/Spark/Person），`Gear` 归零。
  - `MyWordsScreenContractTest`：词库打开通路按 `onOpenWordBankSection` 单点接线。
  - `BackupEntryUnificationContractTest`：备份入口从 `SettingsScreen` 移到 `ProfileScreen`。
- 实现：
  - `AppTab` 删 `SETTINGS`；`AppBottomBar` 删齿轮映射与五栏文案。
  - `AppScreen`：删 `AppTab.SETTINGS` 分支；新增 `showSettings` 状态位 + `overlayOpen` +
    `BackHandler` + 挂载分支；学习页齿轮与阅读/AI 配置兜底改 `showSettings = true`。
  - `SettingsScreen` 先临时保留内容（下一步重写），确保编译可分步验证。

### 2. AI 助学页去渐变

- 先改测试看红：
  - `AiLearningLayoutContractTest`：删渐变/白胶囊/状态胶囊判据；改钉 `AppScreenHeader`、
    双列卡、「还在打磨」分组、页末提示、无 `ai_learning_header` / `ai_learning_status_badge`。
  - `AiBadgeContrastFixTest`：删 `the AI learning hero prints on brand ink…`、
    `the theme invariant white pill…`、AI 页静态读点与 seed 断言；保留 `AiFeatureScreen` 的渐变/亮底判据。
  - `AiFeatureIconContractTest`：功能卡仍逐枚读 `feature.icon`。
  - `AiLearningScreenTest`：删渐变头卡与胶囊像素/语义用例；钉普通页头、配置按钮、双列卡与「还在打磨」。
  - `AppScreenTest.aiTabOpensTheAiLearningScreen`：改按新页头/卡片定位。
  - `MintTokenIsComposableContractTest` 摘 `AiHeaderGradient` 持有符号；
    `DarkTokenConsumptionRatchetTest` 摘 `AiLearningScreen.kt` seed 行。
- 实现 `AiLearningScreen`：
  - 参数：`aiConfigured` 保留（用于配置按钮的副标题/可及名，不承诺生成）、`onOpenFeature`、
    `onOpenAiProfiles`；删除 `todayWordCount` / `dueWordCount` / `onOpenWordList`。
  - `AppScreenHeader(eyebrow = "AI 助学", title = "把词用起来", subtitle = 参考句, action = 配置按钮)`。
  - 双列卡（`AppCard` + `chunked(2)`）：已实现在前；未实现收「还在打磨」分组，卡上带「即将可用」。
  - 页末 `InlineNotice` 保留参考句；删除 `AiHeaderGradient` / `AiHeaderCard` /
    `ai_learning_unreleased_toggle` / `ai_learning_explainer_toggle` / `ai_learning_nav_row`。

### 3. 我的 hub 与设置子页

- 先改测试看红：
  - `ProfilePageContractTest`：重写为 hub 契约（三组、每组行清单、`ProfileGroups` 台账唯一真值、
    我的页不挂设置内容、所有回调接线）。
  - `ProfileScreenTest`：三格深链 + 词汇/学习/应用每一行点击到各自回调 + 大字号可滚。
  - `SettingsScreenTest`：只钉「外观 + 说明 + 返回」，不再有九行。
  - `SettingsNoDeveloperJargonTest` → `ProfileHubLedgerTest`：三组 15 行标题/tag 逐条钉；
    程序员术语禁令继续覆盖 `ProfileScreen.kt` 与 `SettingsScreen.kt`。
  - `SettingsLicensesEntryTest` / `AppScreenTest` / `AppScreenOverlayContainerTest`：入口路径改为
    「我的 → 行」。
- 实现：
  - `ProfileScreen`：`ProfileGroups` 台账 + 三组软卡行 + 档案头 + 三格统计；参数按目的地拆开。
  - `SettingsScreen`：`AppScreenHeader("设置", onBack)` + 外观（`ThemeModeRow`）+ 说明。
  - 新增 `ReminderSpeechScreen`：两行导航（学习提醒 / 语音朗读）。
  - 新增 `WordBookTransferScreen`：导入/导出 `.wbpack`、SAF、忙碌态、结果提示与选册对话框。
  - `AppScreen` 接线：`showSettings` / `showReminderSpeech` / `showWordBookTransfer` 三枚覆盖层，
    按「子屏后声明、父页先声明」排 BackHandler；挂载分支按「子屏先真先赢」排。

### 4. 门禁与交付

- `assembleDebug` → `testDebugUnitTest`（记录 JVM 数量变化原因：删除渐变/白胶囊用例）→
  `compileDebugAndroidTestKotlin` → `assembleDebugAndroidTest`。
- connected 1.0 / 2.0：`AppScreenTest`、`AppScreenOverlayContainerTest`、`ProfileScreenTest`、
  `SettingsScreenTest`、`AiLearningScreenTest`、`AppBottomBarTest`、`SettingsLicensesEntryTest`、
  `AiFeatureScreenTest`（确认详情页渐变未受影响）。
- 截图、真机、报告、逐域提交、推送并 `ls-remote` 核对。

## 不做

- 不重写 `ReminderSettingsScreen` / `SpeechSettingsScreen` 两屏本体。
- 不重写 `BackupHubScreen` / `AboutScreen` / `LicensesScreen`。
- 不改学习、阅读两个一级 tab 的内容。
- 不新增账号、云同步或任何 AI 请求能力。
