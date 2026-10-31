# 2026-11-02 批 5 修复（四栏底导 + 我的功能中心 + AI 页去渐变）交付报告

决策文档：`docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`（取代
`docs/decisions/2026-10-31-settings-as-fifth-tab.md`）
计划文档：`docs/superpowers/plans/2026-11-02-profile-hub-and-four-tabs.md`
分支：`stage-5-ui-r2-learning-path`

## 1. 本次目标

用户原话：「你好好整理一下它这个页面……就是我的跟设置这个东西怎么有重复的，一般来说应该在在我的
里面有个设置……你这个逻辑性不对啊……我给你这个就说明是我觉得之前那个版本不好，所以直接参照这种，
后来给你那个版本直接做就行了」，并另点一句「AI 助学那个表情给删掉吧」+「把那个 AI 助学这个那个颜色
给删掉，跟我之前不是给你一份参考嘛，里面就没有这种东西」。

即：撤回上一批「设置升第五栏」的加法式改版，**按参考原型重做**——底导回四栏、「我的」做功能入口
中心、设置降为其子页、AI 助学页删掉渐变头卡改普通页头。

## 2. 完成了什么

### 2.1 导航回四栏

- `AppTab.kt`：删 `SETTINGS` 成员，只剩 `LEARNING / READING / AI / PROFILE`（声明顺序即渲染顺序，
  `AppTabTest` 锁死）。
- `AppBottomBar.kt`：五格 → 四格，齿轮字形与「设置」标签退役（`AppBottomBarContractTest` 钉
  `Gear` 在底导 = 0）。
- `AppScreen.kt`：删 `AppTab.SETTINGS` 分支；`showSettings` 成为覆盖层状态位
  （`rememberSaveable` + `overlayOpen` + `BackHandler` + 挂载分支）。学习页齿轮、阅读/AI 的配置兜底
  一律改为 `showSettings = true`。

### 2.2 AI 助学页（按参考 `sAiHome` 重做）

- `AiLearningScreen.kt`：品牌渐变头卡 / 白胶囊状态 / 状态胶囊 / 「AI 助学如何起作用」折叠说明 /
  「查看词表」整块离场；留下参考的四件——普通页头（`AppScreenHeader`，eyebrow「AI 助学」+ 标题
  「把词用起来」+ 一句说明 + 行尾配置按钮）、双列已接通功能卡、「还在打磨」分组（未接通四项同样双列、
  **不挂 clickable**）、页末一条 `InlineNotice` 隐私提示。
- **与计划的偏差（用户裁定）**：计划 Step 2 写「`aiConfigured` 保留（用于配置按钮的副标题/可及名）」，
  实际**已删除该参数**（`grep -rn "aiConfigured" app/src/main/java/` = 0 命中）。配置按钮现在是
  `AiProfilesAction(onClick)`：48dp 药片 + 齿轮字形 + 可及名「AI 服务配置」，**不再按是否配好 Key 变色
  或改文案**。理由同用户否掉渐变头卡那句——参考里没有「在页头播报 AI 状态」这个东西；配置状态在
  AI 服务配置页本身呈现，页头只留出口。
- `AiFeature.kt`：`implemented` 真值表未动（仅 SENTENCE_ANALYSIS / PHOTO_TRANSLATION / IMAGE_STUDIO
  为真），三者的专属宿主分支继续优先于通用 `ai_feature_screen` 兜底页。

### 2.3 「我的」= 功能入口中心

- `ProfileScreen.kt`：新增 `ProfileGroups` **唯一台账**（恰 3 组 / 恰 13 行——我的词汇 3、学习 5、
  应用 5），形状逐字取自参考 `sMeHome`，本应用比参考多出的两行（生成默写纸 / 词书内容包）插在「学习」组。
  版面 = 档案头（名字 + 连续打卡/累计学过）→ 三格统计（全页唯一一枚强档卡）→ 三组软卡行。
  装配点按**行对象**匹配落点（不按 tag 字面量二次抄写），行恒为 enabled；副标题只读宿主传入真值，
  本页不读仓库、不持有 SAF 与密钥读写、不再内联设置内容。
- `SettingsScreen.kt`：缩到「`AppScreenHeader("设置", onBack)` + 外观三选一行 + 参考范围说明」，
  九行清单退役（`git diff --numstat` 实测 **+28 / -427**；同批 `ProfileScreen.kt` +168 / -28、
  `AppScreen.kt` +86 / -136、`AiLearningScreen.kt` +118 / -217）。
- 新增 `ReminderSpeechScreen.kt`：一级子页，两行导航（学习提醒 / 语音朗读）；
  `SpeechEngineStatuses` 与 `SpeechSettingsUiState.engineStatuses()` 一并搬入本文件。
- 新增 `WordBookTransferScreen.kt`：一级子页，`.wbpack` 导入/导出、SAF、忙碌态、结果提示与
  「选择要导出的词书」对话框（单册直下 SAF，≥2 册出选择框；导出行
  `enabled = viewModel != null && !busy && books.isNotEmpty()` 且要求 `canExport`）。
- `AppScreen.kt` 三枚覆盖层（`showReminderSpeech` / `showWordBookTransfer` / `showSettings`）的接线，
  遵守 Compose 的两条相反优先律（`AppScreen.kt:538` 的既有注释是权威说法）：
  **BackHandler 后声明者优先**，所以层级越靠内越晚声明
  （656 `showSettings` → 659 `showReminderSpeech` → 661 `showWordBookTransfer` →
  664 起是它们内部的语音/提醒子屏）；
  **挂载 `else if` 先声明者优先**，所以子屏在前、父页在后（现网行号 1931 / 1947 / 1955）。
- `BackupHubScreen.kt`：入口改名后的文案同步（1 行）。
- `MintPalette.kt`：摘掉 `AiHeaderGradient` 持有符号；`DarkTokenConsumptionRatchetTest` 摘
  `AiLearningScreen.kt` 的 seed 行。

### 2.4 测试资产

- 新增 `ProfileHubLedgerTest`（8）——接管被删的 `SettingsNoDeveloperJargonTest`（134 行，
  程序员术语禁令继续覆盖 `ProfileScreen.kt` 与 `SettingsScreen.kt`）并钉台账形状。
- 新增 connected `ReminderSpeechScreenTest`（4）。
- 重写/改判据：`ProfilePageContractTest`（8，含本批新增的空白分支源闸，见 §3.2）、
  `AiLearningLayoutContractTest`（7）、`AppBottomBarContractTest`（5）、`AppTabTest`（6）、
  `SettingsPrototypeAlignmentContractTest`（6）、`MyWordsScreenContractTest`、
  `BackupEntryUnificationContractTest`、`AppScreenOverlayContainerContractTest`、
  `AccessibilityContrastContractTest`、`AiBadgeContrastFixTest`、`AiFeatureIconContractTest`、
  `Batch3AiScreensContractTest`、`ContainerContractTest`、`GlassPanelInkPairingContractTest`、
  `MintTokenIsComposableContractTest`。
- connected 侧按新拓扑重新归巢：`ProfileScreenTest`（6）、`SettingsScreenTest`（5）、
  `AppScreenTest`（58）、`AppScreenOverlayContainerTest`（6）、`AppBottomBarTest`、
  `AiLearningScreenTest`、`SettingsLicensesEntryTest`、`AiFeatureScreenTest`。

### 2.5 证据截图（`docs/design/`，全部本批新拍）

模拟器：`app-bottombar-four-{light,dark}-20261102.png`、`app-profile-hub-{light,dark}-20261102.png`、
`app-profile-hub-learning-rows-light-20261102.png`、`app-profile-hub-app-rows-light-20261102.png`、
`app-settings-appearance-subpage-light-20261102.png`、`app-reminder-speech-subpage-light-20261102.png`、
`app-word-book-transfer-subpage-light-20261102.png`、`app-ai-learning-plain-header-{light,dark}-20261102.png`。
真机 `bf353dda`（带回真实学习数据）：`app-realdevice-today-plan-light-20261102.png`、
`app-realdevice-profile-hub-light-20261102.png`——档案头显示「Steve / 连续打卡 0 天 · 累计学过 2 个词」，
学习计划行副标题显示「四级英语词汇 · 换词书与每日目标」，证明 hub 的副标题是真值而非占位串。

## 3. 验证记录

### 3.1 TDD 与 JVM 门禁

先红后绿：四栏（`AppTabTest` / `AppBottomBarContractTest` 首跑点名 `SETTINGS` 与第五格文案）、
AI 页去渐变（`AiLearningLayoutContractTest` 首跑钉 `AppScreenHeader` 与「还在打磨」报红）、
hub 台账（`ProfileHubLedgerTest` 首跑 8 红）；改判据未放宽阈值（例如未实现功能改为「不可点」后，
判据从「点得开」换成「卡面照画 + 不挂 clickable」，不是删断言）。

本地 Gradle 8.13（`--offline --no-daemon --no-build-cache`，禁 `./gradlew`）：
`:app:assembleDebug` → `:app:testDebugUnitTest` → `:app:compileDebugAndroidTestKotlin` →
`:app:assembleDebugAndroidTest` 均 BUILD SUCCESSFUL。

JVM 汇总（遍历 `app/build/test-results/testDebugUnitTest/*.xml` 累加实测）：
**299 XML / 2216 tests / 0 failures / 0 errors / 0 skipped**
（上一批记录 2210 tests，本批净 +6：新增台账/空白分支源闸/子页契约 > 删除的渐变与白胶囊用例）。
重算命令：

```bash
python -c "
import glob,xml.etree.ElementTree as ET
fs=glob.glob('app/build/test-results/testDebugUnitTest/*.xml')
t=f=e=s=0
for p in fs:
    r=ET.parse(p).getroot()
    t+=int(r.get('tests',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0)); s+=int(r.get('skipped',0))
print('files',len(fs),'tests',t,'failures',f,'errors',e,'skipped',s)"
```

detekt/ktlint：未作为本批门禁（存量基线债 1500+ 条，见 `project-acceptance-gate` 记录），
本批未新增静态检查项。

提交后再跑一次 `:app:testDebugUnitTest`：`BUILD SUCCESSFUL in 15s / 32 actionable tasks: 32 up-to-date`
——即被验证的输入与提交后的树一致，汇总数字不需要重算。

### 3.2 本批发现并修复的真实缺陷：点「设置」得到一整屏空白

上一批留下的 `AppScreen.kt` 里有一支**只剩注释**的 `else if (showSettings)`。
`if / else if` 先声明先赢，于是点 hub 的「设置」行确实把 `showSettings` 置了真、挂载链也确实命中
那一支，可那一支什么都不画——用户看到的是一整屏空白。真机/截图走查时暴露，修法是真删掉那支注释分支
（现网只剩 1955 行那支，body 为 `SettingsScreen(onBack = { showSettings = false })`）。

为防复发，新增源码形状闸 `ProfilePageContractTest.each subpage flag is mounted exactly once and with a real body`：
对 `showReminderSpeech` / `showWordBookTransfer` / `showSettings` 各断言
「挂载分支恰 1 处」（0 = 路由没有落点，2 = 先声明的那支顶掉后一支）**且**
「分支体不得只剩注释」（`stripComments` 后匹配 `else if (flag) { }`）。

### 3.3 connected（`emulator-5574`，`adb install -r` + `am instrument`，禁 `connectedDebugAndroidTest`）

本轮为写报告**重跑**，非沿用会话早期结果：

| font_scale | 类 | 结果 |
|---|---|---|
| 1.0 | `ProfileScreenTest` `SettingsScreenTest` `ReminderSpeechScreenTest` `SettingsLicensesEntryTest` `AppBottomBarTest` `AiLearningScreenTest` `AiFeatureScreenTest` `AppScreenTest` | **OK (102 tests)** |
| 1.0 | `glass.GlassPanelThemeInkTest` `SpeechSettingsScreenTest` `ReminderSettingsScreenTest` `AiProfileSettingsScreenTest` `WorksheetSettingsScreenTest` `ThemeModeSwitchTest` | **OK (55 tests)** |
| 1.0 | `SetupScreenScreenshotTest` `BackupEntryUnificationTest` | **OK (8 tests)** |
| 2.0 | `AppScreenOverlayContainerTest` | **OK (6 tests)** |
| 2.0 | `ProfileScreenTest` `SettingsScreenTest` `ReminderSpeechScreenTest` `AppBottomBarTest` `AiLearningScreenTest` `AppScreenTest` | **OK (91 tests)** |

合计 262 次用例执行，0 失败。跑完把设备 `font_scale` 复位 1.0 并 `am force-stop`。

**运行前提（务必记）：** `AppScreenOverlayContainerTest` 的两条大字号哨兵断言
（`assertScaleTookEffect`：「选择要导出的词书」≥100px、「其他词书不受影响。」≥60px）
读的是**设备级** `font_scale`，而 Compose Dialog 窗口不继承 composition 期
`LocalDensity provides Density(density, fontScale = 2f)`。设备为 1.0 时该 6 条会红 3 条；
`adb -s emulator-5574 shell settings put system font_scale 2.0` + `am force-stop` 后 6/6 绿。
这是环境前提，不是版式缺陷。

### 3.4 本批修正的一处自己写坏的用例

`AppScreenTest.openingAnAiFeatureHidesTheBottomBarAndBackReturnsToTheAiTab` 曾在批 22 失败：
驱动改点 `ai_feature_sentence-analysis`，断言仍找通用页 `ai_feature_screen`。sentence-analysis 有专属
宿主分支（专属分支优先于通用兜底页），所以断言应为 `sentence_analysis_screen`。
用 `git show HEAD:` 核对过：HEAD 版走的是 `ai_feature_cloze`，且藏在已退役的折叠开关后面——
即这是**本批自己引入的错**，不是存量失败；已按真实路由改正。

### 3.5 真机

`bf353dda` 只做 `adb install -r app/build/outputs/apk/debug/app-debug.apk`（Success）+ 手工走查，
未在真机跑 instrument。启动用 `monkey -p com.example.englishlearning -c android.intent.category.LAUNCHER 1`
（MIUI 对未导出 Activity 的 `am start -n` 会抛 SecurityException）。走查确认：四栏底导、
今日计划真实数据（「0 / 28 个词」「四级英语词汇 · 已学 2 / 4308」）、「我的」hub 13 行与三格统计、
设置子页只剩外观 + 说明、AI 页无渐变无表情。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`，本批 5 个提交（依次）：
  - `2a57a3f` docs(ia)：决策 + 本批计划
  - `a73bbde` feat(ui)：四栏 + hub + 三枚子页 + AI 去渐变 + 空白分支修复（main 与 JVM 契约同批，
    因宿主覆盖层接线与 AI 页签名互相咬合，拆开提交会留下编译不过的一刀）
  - `e53d739` test(ui)：connected 归巢 + 本批自坏用例修正 + 大字号运行前提
  - `2ec0579` docs(design)：13 张证据截图
  - 本报告（docs(ui)）
- **未推送**：等用户代理可用后由用户说「推送」再推，推送后 `git ls-remote` 核对远端 tip。
- 未入库的工作产物：`.git/qa-scratch/`（走查脚本与原始截图，含带真实学习数据的抓屏，永不提交）。

## 5. 未完成 / 遗留问题

1. **AI 页配置入口**：用户已裁定「暂不加」，AI 服务配置只经 hub「AI 服务」行与页头齿轮进入；
   若后续要加，需先更新决策文档。
2. **AI 现状缺口**（沿 `project-ai-feature-page-direction` 台账，本批未动）：每次弹确认、Vision 空转、
   生图不落盘、缺连接测试与 nsc 例外——属功能批次，不属版式批次。
3. **三处入口重复**（关于/许可/备份）按用户选的「结构性重排（新批次）」处理；本批只做到
   hub 13 行台账唯一 + 设置子页去许可行，`LicensesScreen` / `AboutScreen` / `BackupHubScreen` 本体未重写。
4. **深套开关/滑杆与默写纸设置页**的真机目视取证仍缺（沿上一批 §11.4 挂账）。
5. 真机数据仍是轻档（2 个词），长书名折行、进度条满档等大列表版式需导入 `.wbpack` 后复看。

## 6. 下一任接手须知

- 接手第一步：读 `docs/handoff/2026-11-03-ui-redesign-and-effect-sheet-handoff.md` 与本报告，
  再读 `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`（现行 IA 唯一真值）。
- 加/删「我的」任何行：只改 `ProfileGroups` 台账，落点在 `ProfileScreen` 装配点按**行对象**匹配，
  不要在渲染侧或宿主侧再抄 tag 字面量；`ProfileHubLedgerTest` / `ProfilePageContractTest` 会双向对齐。
- `AppScreen.kt` 覆盖层的两条优先律方向相反，必须同时满足：BackHandler **后声明者优先**
  （靠内的屏声明得晚，才拿得到返回键）；挂载 `else if` **先声明者优先**（靠内的屏挂在前面，
  父页才不会被自己顶掉）。新增子屏时：BackHandler 加在既有链的**靠后**位置、挂载分支加在
  `showSettings` 分支（1955）**之前**；写完跑 `ProfilePageContractTest`
  （空白分支源闸会抓住「只剩注释」的挂载分支，就是本批那个整屏空白缺陷的形状）。
- connected 纪律：一个 `@Test` 只 `setContent` 一次；SAF 行只断言可达、不点击；
  `GlassPanelThemeInkTest` 的包名是 `...ui.glass.`；`BackupEntryUnificationTest` 在 `...ui.`；
  类名猜错会得到 `ClassNotFoundException` 而不是 0 命中。
- 大字号用例先看设备 `font_scale`（§3.3 的运行前提），别当回归修。
- Windows/Git Bash：`adb shell uiautomator dump /sdcard/x.xml` 与 `adb pull /sdcard/x` 必须
  `MSYS_NO_PATHCONV=1`，否则会写到被改写的路径并读到**旧 dump**（本批踩过一次，误判成改版前的文案）；
  Compose 的 TextView 节点 dump 出 `bounds="[0,0][0,0]"`，底导 tab 要按坐标点。
- 门禁顺序与 Gradle 路径见 `project-environment-quirks`；管道退出码用 `; exit ${PIPESTATUS[0]}`。

## 7. 用户确认过的决策

1. 「我的 / 设置」= **按参考重做我的入口页**（底导回四栏，设置降为「我的」子页），
   取代上一批「设置升第五栏」。
2. AI 助学页那块绿色渐变头卡 = **整块删掉，按参考改普通页头**。
3. AI 助学页的表情（emoji）= **删掉**（上一批已落，本批未回潮）。
4. 三处重复（关于/许可/备份）= **结构性重排，另起新批次**，不在本批顺手改。
5. AI 页要不要补配置入口 = **暂不加**。
6. 「参考没有的两件（词书内容包 / 提醒与发音）本应用已实现，不能砍」——两行留在 hub 台账里。
7. 交付节奏：不合 `main`，**推送逐批请示**；代理未开时不推。
