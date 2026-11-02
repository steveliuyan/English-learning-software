# 2026-11-03 批 6（IA 收尾两件：学习页「今日文章」直达 + 打卡屏改名）交付报告

决策文档：`docs/decisions/2026-11-03-today-article-direct-open-and-checkin-name.md`
计划文档：`docs/superpowers/plans/2026-11-03-ia-direct-article-open-and-naming.md`
分支：`stage-5-ui-r2-learning-path`（上一批：`docs/handoff/2026-11-02-profile-hub-four-tabs-report.md`）

## 1. 本次目标

用户原话是「先继续开发下一步」，范围用问答钉下来：**本批 = 信息架构盘点的收尾两件**，
命名口径 = **「学习打卡」→「打卡与连续」**。即
`docs/design/2026-10-31-information-architecture-audit.md` 方案 C 的第 3、4 条：

- **D-5**：学习页那两枚写着「今日文章」的入口只把用户送到阅读 tab，还得再点一次才读到文章——
  标签承诺文章，落点是阅读首页。
- **D-8**：「学习打卡」（讲连续天数与周/月视图）与「学习记录」（讲逐条完成事件）两个名字都像记录。

## 2. 完成了什么

### 2.1 落点按真值判，且判定只此一处（D-5）

- `ReadingAccessScreen.kt:103` 新增 `val ReadingAccessUiState.canOpenTodayArticleDirectly: Boolean`——
  与 `historyOrEmpty` 并排，**穷举 `when`**（`Ready -> todayArticle != null`，其余三态 `false`），
  新增状态时编译器会逼着表态，不用 `this is Ready && …` 那种会静默放过新态的写法。
- `AppScreen.kt:538` 新增宿主 lambda `openTodayArticleFromLearningPage`：判定为真 ⇒
  `readingAccessViewModel?.openTodayArticle()`（文章以覆盖层挂在当前 tab 之上，**不切 tab**）；
  其余 ⇒ `selectedTab = AppTab.READING`，由阅读首页交代原因（锁定原因 / 生成入口）。
  `AppScreen.kt:1997` 学习页 `onOpenReading = openTodayArticleFromLearningPage`，替换掉原来那行
  `{ selectedTab = AppTab.READING }`。
- `AppScreen.kt:413` 阅读取数触发条件加上 `AppTab.LEARNING`（原来是 `READING || PROFILE`）。
  **不加就是假话**：停留学习 tab 时 `readingState` 恒为 `Loading` ⇒ 判定永远走「切 tab」那一支。
  代价照记：学习 tab 首帧多三次本地 Room 读，全在 ViewModel 协程里，不进 UI 线程。
- `TodayPlanScreen.kt` **无行为改动**（+6 行全是 `onOpenReading` 的 KDoc：本屏不知道阅读状态，
  也不自己猜，落点差异收在宿主一处）。入口文案与 tag 一字未动。

### 2.2 只改撞车的那一枚名字（D-8）

- `CheckInScreen.kt:68` 页名 `title = "打卡与连续"`。这是 main 源码里「学习打卡」最后一处用户可见串。
- 「学习记录」「我的」hub 三格「连续天数」「阅读历史」**均不改名**（理由见决策 §3）。

### 2.3 测试资产

- 新增 JVM `TodayArticleLandingTest`（7 条）：4 条逐态钉判定（`Ready` 有/无文章、`Locked`、
  `Loading`/`Unavailable`）+ 3 条宿主形状闸——学习页不得再留「一行切 tab」的旧写法、
  `onOpenReading = openTodayArticleFromLearningPage` 恰 1 处、宿主里 `canOpenTodayArticleDirectly`
  恰 1 处（防第二份权威）、以及取数触发窗口内 `LEARNING/READING/PROFILE` 各恰 1 处。
- 改 JVM `CheckInScreenHeaderShapeContractTest`（+1 条跨文件命名闸）：旧名「学习打卡」在
  `ui` main 全部源文件里必须为 0 命中（残留按 `file ×count` 报出），新名在 `CheckInScreen.kt`
  恰 1 处；另 4 处合成样本串随改名更新。用 `OLD_CHECK_IN_NAME` / `NEW_CHECK_IN_NAME` 两枚常量，
  避免被批量替换把自己的判据也改掉。
- 新增 connected `AppScreenTest` 4 条（`:1658 / :1699 / :1725 / :1746`）：
  ① 未学完那档的裸行 `today_plan_reading_status` 直开文章、退出回学习页、底导仍在「学习」；
  ② 全完成空态那枚 `today_plan_read_article` 同一条落点；③ 未解锁 ⇒ 落阅读首页且见 `reading_access_locked_reason`；
  ④ 已解锁但今天没文章 ⇒ 落阅读首页见 `reading_source_ai`、`reading_open_today` 不在场。
  `readingFixture` 加 `withTodayArticle` 开关（关掉时 `findLatest` 仍给历史，只空「今日」那一格）。
- 改 connected `CheckInScreenTest` 2 处文字锚（按文字定位页名，不是删断言）。

## 3. 验证记录

### 3.1 TDD 与 JVM 门禁

先红后绿：`TodayArticleLandingTest` 首跑 5 处 `Unresolved reference 'canOpenTodayArticleDirectly'`
⇒ 加判定与接线 ⇒ 7/7 绿；命名闸首跑报「学习打卡」在 `CheckInScreen.kt` 1 命中 ⇒ 改页名 ⇒ 绿。

本地 Gradle 8.13（`--offline --no-daemon --no-build-cache`，禁 `./gradlew`）：
`:app:assembleDebug` → `:app:testDebugUnitTest` → `:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL。

JVM 汇总（遍历 `app/build/test-results/testDebugUnitTest/*.xml` 累加，报告前重算）：
**300 XML / 2224 tests / 0 failures / 0 errors / 0 skipped**
（上一批 299 / 2216 ⇒ 本批 +1 类 / +8 条 = `TodayArticleLandingTest` 7 + 命名闸 1，对得上）。
重算命令同上一批 §3.1。
**追加后的最终数见 §8（同一条命令重算：300 / 2225 / 0 / 0 / 0）。**

detekt/ktlint 未作门禁（存量基线债 1500+ 条，见 `project-acceptance-gate`），本批未新增静态检查项。

### 3.2 connected（`emulator-5574`，`adb install -r` + `am instrument`，禁 `connectedDebugAndroidTest`）

| font_scale | 类 / 范围 | 结果 |
|---|---|---|
| 1.0 | `CheckInScreenTest` + `TodayPlanScreenTest` | **OK (35 tests)** |
| 1.0 | 本批新增 4 条 D-5 用例 | 首跑 **2 红**（两处自己写坏，见 §3.3）⇒ 修正后 **OK (4 tests)** |
| 1.0 | `AppScreenTest` 全类 | **OK (62 tests)**，无 ANR |
| 2.0 | `CheckInScreenTest` + `AppScreenOverlayContainerTest` | 首跑 14 跑 **1 红**（冷重启后对话框量到 `585 x 0`）⇒ 单跑 **OK (6)** ⇒ 同序重跑 **OK (14)**，归类见 §3.4 |

跑完把设备 `font_scale` 复位 1.0、`ui_night_mode` 复位 no、`am force-stop`。

### 3.3 本批踩到的两件事（都是自己的错，已改）

1. **F3-01B 覆盖词面板吃掉第一次系统返回键**。直达用例里我按「系统返回 = 关文章」断言，
   而 `ArticleReadingScreen.kt:200-202` 首次进文会 `rememberSaveable(articleId)` 自动展开那枚
   `ModalBottomSheet`——它是独立窗口，返回键先收面板。失败留下面板常开，**下一条用例直接
   `keyDispatchingTimedOut` ANR**（`INSTRUMENTATION_CODE: 0`），一开始被误读成宿主接线的问题。
   修法：断言退出前先点 `coverage_popup_close` + `waitForIdle()`。这条已写进决策风险 #1：
   直达后用户要按两次返回才离开文章，是 F3-01B 的面板策略，不是本批落点判定的缺陷；嫌麻烦另开账。
2. **`assertEquals` 参数序**。本仓约定消息在**第一**位（`AppBottomBarTest` 同一条），
   我把消息写在第三位 ⇒ 消息被当成 `actual`，得到
   `expected:<[已选中]> but was:<[直达文章不该把底导挪到阅读 tab…]>`。同批还有一处用错锚点：
   今天没文章时 `reading_today_article` 整块不进组合，改钉 `reading_source_ai` 在场 +
   `reading_open_today` 不在场。

### 3.4 大字号那一条红的归类

`AppScreenOverlayContainerTest.hostedWordBookDeletePanelStaysFullyReadableAndTappableAtDoubleFontScale`
在冷重启模拟器后的第一次跑里报「行高应 ≥60px，实测 size=585 x 0」。**高度是 0 而不是 1.0 档的高度**，
且同一序列随后单跑 6/6 绿、按同一顺序（`CheckInScreenTest` 在前）重跑 14/14 绿；本批代码没碰那面板。
判为「冷启动后首帧对话框尚未完成测量」的环境读数，不是回归。下一任再遇到同一条，先单跑一次再定性。

### 3.5 证据截图与取数方式（重要口径）

直达需要两个本地条件：今日计划已解锁 + 今天有文章。模拟器上今天（设备日 2026-10-31）
计划是 0/10、`articles` 表为空，而**生成文章要真调 AI 并产生费用**——未经用户同意不能点。
所以取证走本地种子：`learning_events` 插 10 条今日完成事件（把 `newDone` 顶到 `newTarget`）+
`articles` 插 1 条 `localDate=2026-10-31 / STORY / STANDARD / USER_IMPORTED` 的短文（正文自己写，
`sourceType` 如实写 `USER_IMPORTED`，页面上显示「手动导入」，不冒充 AI 产物）。
**拍完全部删除并冷重启核验**：`learning_events` 回到 16 条、`articles` 回到 0 条、
学习页回到「连续打卡 1 天 · 今日目标 10 个词」——设备数据与本批开始前一致。
写库经 `adb root` + 设备侧 `sqlite3`，事后 `chown u0_a230:u0_a230` + `restorecon -R`，
adbd 已降回非 root（`service.adb.root 0` + 重启 adbd，模拟器随后冷重启一次恢复传输）。

`docs/design/`（本批 7 张，模拟器 `emulator-5574`，浅/深）：

- `app-batch6-reading-direct-sheet-light-20261103.png`——直达后进文即弹的覆盖词面板（F3-01B）
- `app-batch6-reading-direct-article-{light,dark}-20261103.png`——文章覆盖层本体（已背词高亮在深色下可读）
- `app-batch6-reading-direct-back-learning-{light,dark}-20261103.png`——**退出落回「今日学习」，底导选中仍是「学习」**（D-5 的目视凭据）
- `app-batch6-checkin-renamed-{light,dark}-20261103.png`——页头「打卡与连续」（D-8）

原始带用户数据的抓屏留在 `.git/qa-scratch/`，不入库。

### 3.6 真机

`bf353dda` 只做 `adb install -r`（Success，345MB / 85s）供用户手工试用，**未在真机跑 instrument**。
本批没有真机截图（直达需要当天解锁 + 当天有文章，真机当天未满足，且不为取证触发 AI）。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`，本批 5 个提交（依次）：
  - `f4d1690` `feat(ui)`：D-5 直达（`ReadingAccessScreen.kt` + `AppScreen.kt` + `TodayPlanScreen.kt` KDoc +
    `TodayArticleLandingTest.kt` + `AppScreenTest.kt` 4 条 connected）
  - `51db04e` `feat(ui)`：D-8 改名（`CheckInScreen.kt` + 两处测试锚 + 跨文件命名闸）
  - `fd57872` `docs(ia)`：决策 + 本批计划
  - `018d2a6` `docs(design)`：7 张证据截图
  - 本报告（`docs(ui)`）
- **未推送**：等用户说「推送」（代理可用）再推，推送后 `git ls-remote` 核对远端 tip。不合 `main`。
- 工作树应只剩本报告与证据外的东西：`git status --porcelain` 在提交后需为空。

## 5. 未完成 / 遗留问题

1. ~~**第三枚「去读今日文章」没在本批范围内**~~ —— **已并回**（用户同日令「并回来」，见 §8 与决策 §1-B）：
   收尾屏那枚出口现在走同一条判定，先 `exitLearning()` 再落点，次序由变异复验实测钉住。
2. **直达后需按两次返回**（面板 + 文章），见 §3.3 第 1 条与决策风险 #1——F3-01B 面板策略，待用户定。
3. AI 助学页配置入口：用户已裁定「暂不加」，继续挂账（盘点表方案 C 第 2 条）。
4. `Gradient` 摘档、Task 42、`MyWordsScreenTest` 在 `font_scale 2.0` 下 `performScrollTo()` 的存量债——
   均沿上一批台账，本批未动。
5. **日期口径**：机器时钟 2026-11-01、设备 2026-10-31，而本批文档沿用 `2026-11-03` 编号
   （与上一批 `2026-11-02` 的批链连续性一致）。证据文件名与文档同编号，避免两套日期互指。

## 6. 下一任接手须知

- 接手第一步：读本报告 + `docs/decisions/2026-11-03-today-article-direct-open-and-checkin-name.md`，
  再回看 `docs/handoff/2026-11-02-profile-hub-four-tabs-report.md` §6（覆盖层两条优先律、
  connected 纪律、Windows/Git Bash 坑，仍然全部有效）。
- **落点判定只有一个真值**：`ReadingAccessUiState.canOpenTodayArticleDirectly`（`ReadingAccessScreen.kt:103`）。
  三枚入口（学习页裸行 / 全完成空态 / 收尾屏，见 §8）都调同一处；再加同类入口就调它，
  别在页面或宿主里再写一份 `as? Ready`——`TodayArticleLandingTest` 的宿主形状闸会抓住第二份权威。
  新入口若住在覆盖层里（像收尾屏那样），**先退出再走判定**，顺序由那条用例与 `AppScreenTest` 的直达腿钉着。
- 学习页直达依赖「学习 tab 也在取数」（`AppScreen.kt:413`）。若日后把取数收回阅读 tab，
  直达会静默退化成切 tab——那条条件由 `TodayArticleLandingTest` 的窗口断言钉着。
- 文章页首进会弹覆盖词面板：**connected 里要退出文章前先 `coverage_popup_close` + `waitForIdle()`**，
  否则面板常开会把下一条用例拖成 `keyDispatchingTimedOut` ANR（本批真实踩到）。
- `uiautomator dump` / `adb pull /sdcard/...` 一律带 `MSYS_NO_PATHCONV=1`；本批另踩一条：
  漏了它会把 dump 写到改写路径，随后 `pull` 报 `remote object ... does not exist`。
- 模拟器上 `settings put secure ui_night_mode 2` 可能不生效（本批 `mNightMode` 仍为 1），
  换深色取证用 `adb shell cmd uimode night yes`，拍完 `night no` 复位。
- 需要"今天有文章"的取证时优先本地种子（§3.5 全流程），**不要为了截图触发 AI 生成**。
- 门禁顺序与 Gradle 路径见 `project-environment-quirks`；管道退出码用 `; exit ${PIPESTATUS[0]}`。

## 7. 用户确认过的决策

1. 本批范围 = **IA 收尾两件**（D-5 直达 + D-8 改名），不是整轮 IA 重排。
2. 命名口径 = **「学习打卡」→「打卡与连续」**；「学习记录」保持原名，hub 三格「连续天数」不动，
   「阅读历史」不动。
3. 直达判定 = **按真值判，不按 tab 判**，且判定只此一处（其余状态交阅读首页交代原因）。
4. 学习 tab 提前取数 = **接受**（代价：首帧多三次本地 Room 读；收益：直达不需要"先访问过阅读页"的隐性前置）。
5. AI 助学页配置入口 = **本批仍不加**（沿 2026-11-02 裁定）。
   **限定语（2026-11-03 U-14 澄清）**：「不加」指**功能卡内**再放一枚配置入口；
   页头齿轮（`AiLearningScreen.kt:80`，可及名「AI 服务配置」）是批 5 已落的设计，不是漂移
   （见 `docs/handoff/2026-11-02-profile-hub-four-tabs-report.md` §2.2 与 §5.1）。
6. 交付节奏 = 不合 `main`、**逐批请示推送**、代理未开不推；不为取证调用会计费的 AI 生成。
7. 追加裁定 = **第三枚入口「并回来」**（用户原话），并入同一条判定而非新写一份；
   同一条消息里另派「全仓重复功能盘点 + 整合」，见 `docs/design/2026-11-03-duplication-audit.md`。

## 8. 追加：第三枚入口并回（用户同日令「并回来」，决策 §1-B）

§5.1 那条挂在账上的第三枚入口（`TodaySummaryScreen` 的「去读今日文章」）已并入同一条判定。

### 8.1 改了什么

- `AppScreen.kt:550` 新增 `openTodayArticleFromSummary = { exitLearning(); openTodayArticleFromLearningPage() }`
  ——**不重读判定**，只包一层退出，所以宿主里 `canOpenTodayArticleDirectly` 仍恰一处（`:539` 那一处不变）。
- `AppScreen.kt:1053` 收尾屏的 `onOpenReading` 从 `{ exitLearning(); selectedTab = AppTab.READING }`
  换成 `openTodayArticleFromSummary`。
- `AppScreen.kt:1031` 起那段收尾屏接线注释随实改正：四枚行里三枚是退出通路，第四枚走 §1-B 的落点。
- `TodaySummaryScreen.kt` 的类 KDoc 尾段改口径（本屏只留回调、判定不在屏里）。

### 8.2 验证（先红后绿 + 变异复验）

- RED：`TodayArticleLandingTest.theSummaryScreenExitIsMergedIntoTheSameDecision()`（`:132`）首跑报
  `expected: <1> but was: <0>`（收尾屏那枚 `onOpenReading` 还没接线）⇒ 加 lambda 与接线 ⇒ 8/8 绿。
- JVM 门禁重跑（`--offline --no-daemon --no-build-cache`，`assembleDebug` → `testDebugUnitTest`）：
  **300 XML / 2225 tests / 0 failures / 0 errors / 0 skipped**（2224 + 本条）。
- connected 新增 2 条（`emulator-5574`，`adb install -r` + `am instrument`）：
  `AppScreenTest.summaryScreenArticleRowOpensTodaysArticleWithoutSwitchingTabs`（`:1777`，
  宽松档 `planUnlockedWithPendingReview()` ⇒ 解锁且今天有文章：进复习流→末卡推进→收尾屏→
  点那一行 ⇒ 文章覆盖层在场、收尾屏离场、系统返回后落 `today_plan_screen` 且底导「学习」仍 `已选中`）、
  `…FallsToTheReadingHomeWhenLocked`（`:1826`，锁定档 ⇒ 落 `reading_access_screen` 见
  `reading_access_locked_reason`、`article_english` 不在场）。两条 **`OK (2 tests)`**。
- **变异复验**（把 §1-B 那条顺序约束从推断变成实测）：删掉 lambda 里的 `exitLearning()` 一行后，
  JVM 形状闸与本条 connected 直达用例**双双变红**（connected 报
  `ComposeTimeoutException: Condition still not satisfied after 5000 ms`，卡在等 `article_english`——
  即「`showLearning` 还成立 ⇒ 文章分支在 `else if` 链上轮不到」这一支真的开不出文章）；
  装回原实现后两枚复绿。
- 全类回归：`AppScreenTest` 整类 **`OK (64 tests)`**（原 62 + 本批 2）。设备 `font_scale` 保持 1.0。
- 真机：§1-B 的实现提交（`a1a8ff8`，01:36:04）之后另做了一次 `adb install -r bf353dda`，收工时核对
  `dumpsys package com.example.englishlearning` 得 `lastUpdateTime=2026-11-01 01:37:56`、`versionName=0.0.1`
  ——即带这枚改动的包在真机上，供用户手工试用；**未在真机跑 instrument**（沿 §3.6 口径）。

### 8.3 Git 状态（覆盖 §4 的「5 个提交」）

§4 写的是并回之前的账。并回与盘点另加 3 枚：`a1a8ff8`（实现）、`972cbe9`（决策 §1-B + 计划步骤 6 + 本报告 §8）、
`ed0f676`（`docs/design/2026-11-03-duplication-audit.md` 盘点），本批累计 8 枚、分支相对
`origin/stage-5-ui-r2-learning-path` 领先 13 枚（批 5 五枚 + 批 6 五枚 + §1-B 三枚）；
本枚补记提交后为 9 枚 / 13+1 枚。**仍然未推送**，等用户说「推送」；不合 `main`。
