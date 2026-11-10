# 2026-11-10 主题三态搬进「我的」（页头统计卡下方）+ 设置子页退役 + 三档评分条上移 工作报告

## 1. 本次目标

用户 m03373「把主题（浅色/深色/自动）从设置挪到我的」，m03681 在「删整套设置页」与「留空壳」之间答 **1**；
m04040 追加「外观要放在统计卡红线下方，不要放页末」。同一会话前半段还有 m03560：
「三档评分条位置太低，要放在单词下面 ≈ 屏幕居中」。m04089 收口指令：「全部都做」（真机复跑 / 文档清理 / 报告+提交）。

## 2. 完成了什么

**A. 三档评分条从吸底搬到卡面正下方（m03560）**
- `ui/WordCardScreen.kt`：`WordCardFooterSlot(...)` 由 Ready 分支尾部搬进滚动内容 Column 尾部；内容列补 `.padding(bottom = 12.dp)`；
  `WordCardFeedbackBar` / `WordCardReceiptBar` 去掉 `background(colors.surface)`、`hairlineTop(...)`、`navigationBarsPadding()`，
  改 `.fillMaxWidth().padding(vertical = 4.dp)`；`FeedbackTierContent` 行内副注由 `Arrangement.Center` 改
  `Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)`（走查发现「不认识想不起来」读成一串）。
- `ui/VocabularyRelearnScreen.kt`：孪生站点同口径搬到文档流（`RelearnFeedbackBar` 移入滚动内容列尾部）。
- 判据同步：`StickyFooterHairlineGuardTest` 三条台账 `paintsOwnFill = true → false`（自铺底色 13 → 10）；
  `WordCardLayoutContractTest` 断言改 8dp 气口；`RelearnLayoutContractTest` 用例改名并写明「不再吸底」的理由。

**B. 主题三态搬进「我的」页头统计卡之下，设置子页整体退役（m03373 / m03681 / m04040）**
- `ui/ProfileScreen.kt`：`SectionHeading("外观", testTag "profile_group_appearance")` + `ThemeModeRow(rememberAppThemeMode(), AppThemePreferences.write(...))`
  装在 `StrongCard(profile_stats)` 之后、`ProfileGroups.all.forEach` 之前（约 207–214 行）。
- 删除：`ui/SettingsScreen.kt` 整份、`AppOverlayRoute.Settings`、`showSettings`（宿主状态位 / BackHandler / 挂载分支 / resolver 实参）、
  `ProfileRow("设置", "profile_open_settings", …)`；四处「送去设置页」的兜底统一改 `selectedTab = AppTab.PROFILE`。
- 测试/文案随迁：`ProfileHubLedgerTest`、`ProfilePageContractTest`、`AppScreenTest`、`ProfileScreenTest`
  （用例改名 `theAppearanceSectionIsInlinedRightUnderTheStatsCard`）、`ContainerContractTest`、`SettingsScreenTest`/`SettingsLicensesEntryTest` 删除，
  「`ThemeModeRow` 是纯三态选择器」与「三档 TalkBack 单选语义」两条判据搬进 `ProfileScreenTest`。

**C. 文档**
- 新增决策：`docs/decisions/2026-11-10-theme-switch-moves-into-profile.md`（含 `## 位置补正（m04040）` 一节）。
- 本次清理（只动**活文档**，历史报告与计划一律不改）：
  - `docs/verification/ac-traceability-matrix.md` AC1-13 的 `androidTest ui.SettingsScreenTest` → `ui.LearningSetupLayoutContractTest`（三枚「也展开释义」开关现在住在学习起始页）。
  - `docs/design/2026-10-31-information-architecture-audit.md`、`docs/design/content-placement/G5-settings-data.md` 顶部各加一段「现状提示」，声明设置子页已退役、行号与入口全部失效、本文只作当时审计记录。
  - `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`、`2026-11-03-duplication-consolidation.md` 各加一行 `**后续（2026-11-10，m03681 / m04040）**` 指回新决策。

## 3. 验证记录

- JVM：`gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL（1m 4s）；
  结果目录 `app/build/test-results/testDebugUnitTest` 共 349 个 XML / **2610 例 / 0 失败**。
- connected（受影响的 5 类，走官方 UTP）：
  `gradlew.bat --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ui.ThemeModeSwitchTest,ui.ProfileScreenTest,ui.BackupEntryUnificationTest,ui.ScreenTransitionInstrumentedTest,ui.EntryRowTest`
  → **BUILD SUCCESSFUL in 1m 17s，Starting 34 tests … 0 failed**（M2102J2SC - 13）。
- adb 逐类复核（`am instrument -e class …`）：ProfileScreenTest 8 / AiFeatureScreenTest 8 / TodayPlanScreenTest 28 /
  ThemeModeSwitchTest 4 / ScreenTransitionInstrumentedTest 9 / BackupEntryUnificationTest 7 / EntryRowTest 6 —— 全 OK。
- `AppScreenTest`（66 例）单类整跑**不稳定**：第一次 4 例失败、第二次只剩 1 例且是**另一条**用例；5 条涉案用例单独跑全部 3s 内通过。
  判定为共享宿主 + 系统返回注入的**顺序/状态 flaky**，与外观改动无关（失败断言全部是 `profile_screen` / `wordbook_words_screen` /
  `today_plan_summary` / `card_detail_screen` 这类导航落点，且失败集合每次不同）。
- 真机：bf353dda（M2102J2SC / Android 13，1080×2340）装 debug APK，uiautomator dump 实测「我的」顺序 =
  XiaoMing / 连续打卡 … / 9 · 0 · 0 三格 / **外观 / 主题 / 跟随系统 / 浅色 / 深色** / … / 我的词汇 / 收藏；
  词卡屏三档评分条实测定点不认识 1085px、模糊 1256、认识 1427（旧值 1847/2018/2186，整组上移 ≈277dp，块心 ≈ 屏 54%），
  揭晓释义后三档 1008/1179/1350 仍在屏内、下方留白到 2100。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`（`.git/HEAD` 实读），**未提交、未推送**。
- 阻塞原因：**本机没有 git 可执行文件**（`where git` 无输出；`C:\Program Files\Git\cmd\git.exe`、`C:\Program Files (x86)\…`、`C:\Git\…` 均不存在）。
  `.git` 目录本身完好（remote `https://github.com/steveliuyan/English-learning-software.git`）。
- 待提交清单（本批）：
  - 源码：`app/src/main/java/com/example/englishlearning/ui/{ProfileScreen.kt, WordCardScreen.kt, VocabularyRelearnScreen.kt, SettingsScreen.kt(删), AppScreen.kt, AppOverlayRoute.kt, AppOverlayRouteResolver.kt, AppMountFlags.kt, components/SegmentedControl.kt, LicensesScreen.kt}`
  - 测试：`app/src/test/…/ui/{ProfileHubLedgerTest, ProfilePageContractTest, ContainerContractTest, ScreenTransitionContractTest, ScreenTransitionDirectionTest, AppOverlayRouteNavigationIdentityTest, AppOverlayRouteResolverTest, AiGuidanceMapNamingTest, BackupEntryUnificationContractTest, WordCardLayoutContractTest, StickyFooterHairlineGuardTest, RelearnLayoutContractTest}.kt`、
    `SettingsPrototypeAlignmentContractTest.kt(删)`；`app/src/androidTest/…/ui/{ProfileScreenTest, AppScreenTest, ScreenTransitionInstrumentedTest, BackupEntryUnificationTest, EntryRowTest}.kt`、`SettingsScreenTest.kt / SettingsLicensesEntryTest.kt(删)`
  - 文档：`docs/decisions/2026-11-10-theme-switch-moves-into-profile.md`(新)、`docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`、`docs/decisions/2026-11-03-duplication-consolidation.md`、`docs/verification/ac-traceability-matrix.md`、`docs/design/2026-10-31-information-architecture-audit.md`、`docs/design/content-placement/G5-settings-data.md`、本报告
  - 建议顺手清掉仓库根的临时物：`connected.log`、`connected.err.log`、`java_pid*.hprof`

## 5. 未完成 / 遗留问题

1. **真提交**：等用户在 PATH 里给一个 git（或告知 git 安装路径）后执行提交 + 推送。
2. **`AppScreenTest` 单类整跑 flaky**：66 例里每次 1–4 例悬挂在导航落点断言上，单跑全绿。需要一轮「何时清理宿主状态」的治理（或在该类里给需要干净宿主的用例加 `@Before` 复位），本批未动。
3. **真机 instrumented 未全量**：本批只跑了受影响的 5 类 + AppScreenTest 的涉案 5 条；全仓 100+ 个 connected 类未重跑。
4. **待目视项**：收藏图标「已收藏」态观感（m03293 第三项）；词书「更换词书」流程、收藏空态、认证导出/恢复 SAF、词义详情「长难句分析 / 问 AI」入口的真人视角走查。
5. 文档残影已按「活文档修、历史记录留」处理；`docs/handoff/` 与 `docs/superpowers/plans/` 里的旧 `SettingsScreenTest` 引用属历史记录，未改。

## 6. 下一任接手须知

- 第一步：拿到 git 后按 §4 清单提交（历史惯例 `git -c user.name=steveliuyan -c user.email=2021250388@qq.com`）。
- 跑 connected 的坑：
  - UTP 残留 `app/build/outputs/androidTest-results/connected/debug/<device>/utp.0.log.lck` 会让 gradle 报
    `Cannot access output property 'resultsDir'`；先删 `androidTest-results` 与 `*.lck` 再跑。
  - gradle connected **跑完会把 app 与 test APK 卸掉**；之后 `am instrument` 报 `Unable to find instrumentation target package` 时先
    `adb shell pm list packages | grep englishlearning`，缺主 app 就重装 `app-debug.apk`（346MB）。
  - 嵌入式 pwsh 有 120s 硬超时：长任务用 `cmd /c 'start /b cmd /c "… > connected.log 2>&1"'` 后台跑再轮询日志
    （`Start-Process` 在本机会因 `NO_PROXY`/`no_proxy` 重复键抛 ArgumentException）。
- 看手机：本会话没有 `tools.view_image`；`tools.screen_view({})` 截桌面。scrcpy 窗口被主窗遮住时先
  `WScript.Shell AppActivate('手机投屏')` 提前台。
- 相关文档：`docs/decisions/2026-11-10-theme-switch-moves-into-profile.md`（本次拍板与位置补正）、
  `docs/decisions/2026-11-09-word-card-footer-toolbar-and-detail-overlay.md`（评分条所属的页脚/浮层形状）。

## 7. 用户确认过的决策

- m03560：三档评分条不吸底，放到词卡正下方 ≈ 屏幕居中（人机工程：底部拇指够不着）。已真机确认。
- m03681：主题搬进「我的」时 **选 1** —— 删「设置」行 + 整页，不留空壳设置页。
- m04040：外观段从页末上提到**页头统计卡正下方**（「放在图片红线下方」）。
- m04089：「全部都做」= ① 真机 connected 复跑 ② 文档清理 ③ 报告 + 提交。
