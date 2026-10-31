# 2026-10-31 设置升第五栏 + 信息入口去重 工作报告

## 1. 本次目标

用户原话：「你把目前这APP上面的每个功能呢，以及位置啊，你再考虑一下……我在我的里面有一个关于跟开源许可与隐私说明，
那个开源许可与隐私说明，在关于里面也有一个叫开源许可与来源，这是重复的」。

在盘点出三处重复后，用户在三套方案中拍板「结构性重排（新批次）」：设置独立成第五个一级 tab，
「我的」只留个人进度；AI 助学页**暂不**加配置入口。决策见
`docs/decisions/2026-10-31-settings-as-fifth-tab.md`，计划见
`docs/superpowers/plans/2026-10-31-settings-tab-and-info-dedup.md`。

## 2. 完成了什么

**主源码**

- `AppTab.kt`：四栏 → 五栏 `LEARNING / READING / AI / PROFILE / SETTINGS`；
  `PROFILE`（我的）与 `SETTINGS`（设置）语义分开，不再让 `SETTINGS` 承担「我的」。
- `AppBottomBar.kt`：五格字形 学习=Book、阅读=Newspaper、AI 助学=Spark、我的=Person、设置=Gear；
  仍按屏宽均分，64dp 触达高度不变。
- `ProfileScreen.kt`：删除 `settingsContent` 槽（及其 `ColumnScope` 依赖），页面到「学习记录」行为止。
- `SettingsScreen.kt`：成为独立 tab 根页（页头「设置」+ 可滚动四组台账）；
  台账 4 组 **10 → 9 行**（删「开源许可与隐私说明」行），签名删 `onOpenLicenses` 只留 `onOpenAbout`。
- `AboutScreen.kt`：「说明」区只剩 **1 行**「开源许可与隐私说明」；删除不可点的「隐私说明」死行与
  「备份与恢复」行（备份唯一入口 = 设置 → 数据与安全）；签名删 `onOpenBackup`。
- `AppScreen.kt`：新增 `AppTab.PROFILE -> ProfileScreen(...)` 与 `AppTab.SETTINGS -> SettingsScreen(...)` 两个分支；
  关于分支删 `onOpenBackup` 接线；学习页齿轮落点改为设置 tab。

**测试**

- JVM 契约：`AppTabTest`（五栏顺序 + 新增「我的在设置之前」一条）、`AppBottomBarContractTest`（五字形各一次）、
  `ProfilePageContractTest`（我的页不再承载设置台账 + 关于/许可唯一通路）、`AboutScreenContractTest`
  （说明区恰一行且通许可）、`SettingsPrototypeAlignmentContractTest` / `SettingsNoDeveloperJargonTest`（4 组 9 行）、
  `MyWordsScreenContractTest`（宿主站点切片改到 `AppTab.SETTINGS`）、`LicensesLayoutContractTest`（入口名出处改到关于页）。
- connected：`AppBottomBarTest`（五槽）、`SettingsScreenTest`（9 行 / 可点行 11·13）、
  `SettingsLicensesEntryTest`（设置无许可行、关于进许可）、`ProfileScreenTest`（页尾止于学习记录、无内联设置）、
  `AppScreenTest` 与 `AppScreenOverlayContainerTest`（返回落点从我的页改到设置页）。

**文档 / 证据**

- 设计证据 8 张：`docs/design/app-bottombar-five-{light,dark}-20261031.png`、
  `app-profile-only-progress-light-20261031.png`、`app-settings-fifth-tab-{light,dark}-20261031.png`、
  `app-settings-no-license-row-light-20261031.png`、`app-about-single-explanation-row-light-20261031.png`、
  `app-licenses-from-about-light-20261031.png`。

## 3. 验证记录

**门禁（顺序：assembleDebug → testDebugUnitTest → compileDebugAndroidTestKotlin，`--offline --no-daemon --no-build-cache`）**

- 首轮 1 条红：`ProfilePageContractTest.about is reached from settings…`——断言串多带了一个尾空格
  （`tag = "settings_open_about", ` vs 源码 `,`）。属测试自身笔误，改正后复跑。
- 最终：**BUILD SUCCESSFUL**；JVM 汇总 XML **2210 tests / 0 failures / 0 errors / 0 skipped**。
  基线 2209（HEAD `097cbe3`）→ +1：`AppTabTest` 新增「profile comes before settings so the bar reads
  progress then tools」；其余 8 个 JVM 测试文件为改名/改写，未删用例。`assembleDebugAndroidTest` 也成功（24s）。

**connected（`emulator-5574`，`adb install -r` + `am instrument`，未用 `connectedDebugAndroidTest`）**

| 档位 | 范围 | 结果 |
|---|---|---|
| font_scale 1.0 | `AppBottomBarTest` + `SettingsScreenTest` + `SettingsLicensesEntryTest` + `ProfileScreenTest` | **OK (32 tests)** |
| font_scale 1.0 | `AppScreenTest` 单独 | **OK (58 tests)** |
| font_scale 1.0 | `AppScreenOverlayContainerTest`（合并跑时） | 3 条 `*AtDoubleFontScale` 失败——**按设计只在 2.0 档跑**，与批 4 同口径 |
| font_scale 2.0 | 上述四类 + `AppScreenOverlayContainerTest` | **OK (38 tests)** |
| font_scale 2.0 | `AppScreenTest` | **OK (58 tests)** |

- 合并跑（64 条）时 `AppScreenTest#startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan` 曾超时一次：
  单条复跑 **OK**、整类单独复跑 **OK (58)**——判为跨类同进程的顺序相关 flake，非本次改动回归；
  该用例不触碰 tab/设置/关于任何一条改动路径。
- 手动走查（模拟器，1.0 + 浅色/深色）：五格底导顺序与字形正确；「我的」只剩档案头 + 三格 + 学习记录；
  设置页四组、无许可行；关于页说明区仅一行；点该行进入许可页；返回 关于→设置→学习 逐级正确。

**真机**：`bf353dda` `adb install -r` Success（仅手工安装，未跑 instrument）。

**detekt/ktlint**：未跑（验收门禁是单测，见 `2026-09-19-static-analysis-and-test-baseline.md`），无新增依赖。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`，基线 `097cbe3`。
- 本批三个本地提交：
  `8f3c7b4 feat(ui): 设置升为第五栏并收束关于/许可/备份入口` →
  `20bb361 test(ui): connected 用例跟随五栏与去重后的入口分工` →
  `1f85f95 docs(ui): 五栏与去重的工作报告及浅/深色真机证据`。
- **推送：失败（未备份到远端）**。`git -c credential.interactive=false push origin
  stage-5-ui-r2-learning-path` 报 `Failed to connect to github.com port 443 via 127.0.0.1
  after 2051 ms`——全局代理 `http://127.0.0.1:3067` 已配置但代理服务当时未启动。
  本地提交保留，待用户打开代理后重推并用 `git ls-remote` 核验远端 tip。

## 5. 未完成 / 遗留问题

- 五栏在 360dp 窄屏上的标签折行未逐机型实测（风险已在决策文档登记，退路须再经用户定名，不得自行改名）。
- 合并跑 connected 时的顺序相关 flake 未根治（单跑稳定），建议后续把 `AppScreenTest` 与
  `AppScreenOverlayContainerTest` 分进程跑。
- AI 助学页配置入口按用户裁定「暂不加」；学习记录/打卡命名统一等审计建议项仍未开单。

## 6. 下一任接手须知

- 接手第一步：读 `docs/decisions/2026-10-31-settings-as-fifth-tab.md` + 本报告，再跑一次门禁确认基线。
- 改动 `ProfileScreen` 时记住它**不再**接受 `settingsContent`；设置页只有在 `AppScreen` 的
  `AppTab.SETTINGS` 分支里挂载，别再加回「我的」页。
- 许可唯一入口 = 关于 →「开源许可与隐私说明」；备份唯一入口 = 设置 → 数据与安全 → 备份与恢复。
  在任一页补第二入口都会撞 `ProfilePageContractTest` / `BackupEntryUnificationContractTest`。
- JVM 契约测试大量按「剥注释后源码包含」取样：改行内容时先看断言串的精确形态（本批踩过一次尾空格）。
- 相关路径：`docs/design/2026-10-31-information-architecture-audit.md`、
  `docs/superpowers/plans/2026-10-31-settings-tab-and-info-dedup.md`、
  `docs/superpowers/specs/2026-09-23-ai-learning-tab-design.md`（已同步五栏）。

## 7. 用户确认过的决策

- 三处重复采用**结构性重排**：设置独立为第五个一级 tab，「我的」回归个人进度（而非最小去重）。
- AI 助学页**暂不**增加配置入口。
- 底导顺序固定：学习 / 阅读 / AI 助学 / 我的 / 设置。
