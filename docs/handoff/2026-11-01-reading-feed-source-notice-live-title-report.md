# 2026-11-01 外刊选读来源说明改指许可页现名（整合追补）工作报告

关联决策：`docs/decisions/2026-11-03-duplication-consolidation.md` §8 末条（含实施记录）
关联计划：`docs/superpowers/plans/2026-11-03-duplication-consolidation.md`
上一份报告：`docs/handoff/2026-11-01-local-data-warning-single-source-report.md`（U-6 收源批，本条是它 §5 登记的遗留项）

## 1. 本次目标

U-6 收口盘点发现、用户拍板「现在修」：`ReadingFeedScreen`（外刊选读）的来源说明写着
「App 内的来源台账见「我的 · 开源许可与来源」」，而那一页早已逐字改名「开源许可与隐私说明」——
屏上那句把用户指向一间不存在的房间。本批把它改回真话。

## 2. 完成了什么

- **生产一处**：`ui/ReadingFeedScreen.kt` 来源说明整句改为
  「…App 内的来源台账见「我的 → 关于 → 开源许可与隐私说明」」。路径写到实际落地的那一跳
  （关于是中间层，U-12 裁定两跳保留），不再用省略中间层的「我的 · X」写法。
- **注释两处同步**：同文件文件头 KDoc 与来源说明行旁注释里的旧名一并清掉（含注释归零口径：
  旧名留在注释里就是下一批的抄写模板；rename 历史由决策文档承载）。
- **新闸两条**（`ReadingFeedScreenContractTest`）：
  - `the source notice names the licenses page by its live title`——① 死页名「开源许可与来源」在
    `ReadingFeedScreen.kt` 整份源码（**含注释**）归零；② 来源说明**恰一处**逐字引用
    「我的 → 关于 → 」+ 许可页现名。**现名不写死**：判据从 `LicensesScreen` 页头的
    `AppScreenHeader(title = …)` 现读现比，下次谁再改名这条会红着提醒两处以一致的方式改。
  - `the live-title gate reads the name from the licenses screen and is not vacuous`——反空转：
    许可页 `AppScreenHeader(` 恰一处（多枚时「取第一枚」会静默抽错）、抽名正则对合成页头形状
    真取得出、合成 stale 句必被计到。
- 两处 KDoc 里的原型引用同步改述为「原型用旧名、本屏按现名」。

## 3. 验证记录

- **RED（活名那条腿）被归零断言先红遮蔽**：先跑 RED 时旧句还在生产里，归零断言（第一条腿）已经红，
  活名断言同红但被它盖住。实施后以**变异复跑**补咬合证据：把生产句改成同义变体
  （「我的 · 」省略中间层写法）→ 活名断言**独红**（归零腿绿，证明它能分辨「旧名未清」与「路径没写到落地跳」
  两种失败）→ 还原。
- **门禁（终局树）**：`gradle --offline --no-daemon --no-build-cache :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
  **BUILD SUCCESSFUL**；JVM **307 类 / 2259 条 / 0 失败 / 0 错误 / 0 跳过**（python 汇总
  `app/build/test-results/testDebugUnitTest/*.xml`；U-6 批 307/2257 + 本批 +2）。
- **connected（`emulator-5554`，`adb install -r` + `am instrument`，不用 `connectedDebugAndroidTest`）**：
  `ReadingFeedScreenTest` → **OK (9 tests)**。
  本批两 APK 重装时模拟器（`xtool-s04-api30`，PID 87096，00:38 起挂着的快照）僵死
  （`echo` 超时、`emu kill` 也超时），强杀进程后 `-no-snapshot-load` 冷启动恢复，
  两个包（主 + test）都在，instrument 一次通过。
- **真机 `bf353dda`**：仅 `adb install -r`（Success，`dumpsys package` 的
  `lastUpdateTime=2026-11-02 01:13:20`），未 instrument；
  来源说明那句由「我的 · 开源许可与来源」（25 字）变「我的 → 关于 → 开源许可与隐私说明」（33 字），
  在 `verticalScroll` 列表尾部，无高度钉。**用户目视仍是待办**（见 §5）。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`，**未推送**（等用户说「推送」）。
- 本批两腿：代码腿 **`55ef8c5`**（生产 1 处 + 同文件 2 注释 + 契约测试 2 条，2 文件 +76/-4），
  文档腿＝本报告这枚提交（决策 §8 末条从「登记待拍板」改写为「已落地 + 实施记录」）。
- 无未提交的遗留文件；QA 取证只在 `.git/qa-scratch/`（不入版本库）。

## 5. 未完成 / 遗留问题

- 真机目视一项：外刊选读列表尾部来源说明那句改写后（33 字），请在真机上翻到列表尾看一眼换行/截断。
- **U-13**（「提醒与发音」纯中转页）：仍未动。本报告撰写期间已顺手摸清改动面，下一批计划可直接引用：
  - 中转页本体：`ReminderSpeechScreen.kt` 删除（连同 KDoc 原型说明），`AppScreen.kt` 的
    `showReminderSpeech` 状态、BackHandler（`:684`）、挂载分支（`:1955-1969`）与
    `ProfileScreen` 的 `onOpenReminderSpeech` 接线（`:2091`）拆除；
  - **搬家件**：`SpeechEngineStatuses` + `SpeechSettingsUiState.engineStatuses()` 不是纯中转——
    它是发音行副标题的真值派生，要搬去别处（建议 `SpeechSettingsScreen.kt`，它已有一处消费）；
    hub 新「语音朗读」行副标题直接吃 `speechState.engineStatuses().currentProvider`；
  - hub 台账 13→14：`ProfileGroups` 拆 `ReminderSpeech` 为「学习提醒」「语音朗读」两行
    （tag 建议 `profile_open_reminder` / `profile_open_speech`），`ProfileHubLedgerTest` 的
    13 行清单、tag 清单、`ProfilePageContractTest` 的 `onOpenReminderSpeech` 接线行、
    `ReminderRuleOnceContractTest` 的 `hub` 腿（现读中转页文件）都要同步；
  - connected 整条改测：`ReminderSpeechScreenTest`（4 条）删，替换为 hub 行直达两子屏的判据
    （可进 `ProfileScreenTest` 或新文件）；`AppScreenTest` 里 7 处经中转页的跳转链改直跳；
  - 其他关联：`SettingsScreen.kt:91` 的说明句、`AppTab.kt:15` 与 `LearningSetupLayoutContractTest:524/538`
    的注释、「提醒与发音」名称在 `SettingsPrototypeAlignmentContractTest` 的原型对照口径。
- **U-9**（本周完成两套可视化）：仍未动。
- 沿账：Gradient 摘档、Task 42、`MyWordsScreenTest` 的 `font_scale 2.0` 债、QA 第三轮任务书、AI 缺口批。

## 6. 下一任接手须知

- 「活名判据」形状（判据不写死期望名、从权威源现读现比 + 反空转夹具）是本仓第一次落，
  下次再遇到「屏上引用另一页的名字」时照抄：写死期望值会让第二处副本跟着漂。
- **RED 被同文件另一条断言遮蔽时**，用变异复跑（改生产为同义变体 → 目标断言独红 → 还原）补咬合证据，
  本批有先例。
- 模拟器怪癖补充进 `android-connected-qa-loop` skill 与项目记忆：快照挂起会僵到 `emu kill` 都超时，
  处置是强杀 `emulator.exe`/`qemu-system-x86_64-headless.exe` 进程后
  `emulator -avd <name> -no-snapshot-load` 冷启动——数据分区在磁盘映像里，已装的包不丢。
- 其余 connected 怪癖与设备口径统一记在 `android-connected-qa-loop` skill 与项目记忆，不在本文重复。

## 7. 用户确认过的决策

- 用户原话「你要看一下目前有哪些功能重复啊……你要整合合并整理」（U-6 批）之外，本条的具体修法
  （路径写到落地跳、现名现读、注释归零）沿用 U-5/U-12 既有裁定，作技术裁量写在决策 §8，供用户复核。
- 「现在修」是用户对 U-6 批报告 §5 遗留项的拍板（转述见决策 §8 末条）。
- 本批沿用既有授权：不合 `main`、不推送（等「推送」二字）、真机只手工试用、不为截图触发付费 AI 调用。
