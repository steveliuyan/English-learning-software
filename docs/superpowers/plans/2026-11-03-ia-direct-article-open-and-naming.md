# 批 6：学习页今日文章直达 + 打卡屏改名

- **日期：** 2026-11-03
- **依据：** `docs/decisions/2026-11-03-today-article-direct-open-and-checkin-name.md`
  （收口 `docs/design/2026-10-31-information-architecture-audit.md` 方案 C 的 D-5、D-8 两条）
- **前置：** `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`（四栏 + 我的 hub）
- **分支：** `stage-5-ui-r2-learning-path`
- **用户拍板：** 本批范围 = 「IA 收尾两件」；命名 = 「学习打卡」→「打卡与连续」，「学习记录」不动。

## 验收门禁

1. 本地 Gradle 8.13（`--offline --no-daemon --no-build-cache`）依次
   `:app:assembleDebug` → `:app:testDebugUnitTest` → `:app:compileDebugAndroidTestKotlin` →
   `:app:assembleDebugAndroidTest`。
2. `emulator-5574` 用 `adb install -r` + `am instrument` 跑受影响 connected 类（font_scale 1.0；
   `AppScreenOverlayContainerTest` 仍按设备 `font_scale 2.0` 前提跑）。**不使用** `connectedDebugAndroidTest`。
3. 真机 `bf353dda` 只 `adb install -r` + 手工走查（学完今日计划后点「今日文章」是否直达文章）。
4. `docs/design/` 取证：学习页文章行落点（文章屏覆盖层，浅/深）、打卡屏新页名（浅/深）。
5. 报告按 `docs/handoff/REPORT-TEMPLATE.md`；逐域提交；**推送前请示**，推送后 `git ls-remote` 核对。

## 步骤

### 1. 落点判定（TDD 先红）

- 新建 JVM 用例 `TodayArticleLandingTest`，逐态钉 `ReadingAccessUiState.canOpenTodayArticleDirectly()`：
  `Ready + todayArticle != null` → `true`；`Ready + todayArticle == null`、`Locked`（带历史）、
  `Loading`、`Unavailable` → `false`。
- 实现该纯函数（`Ready` 才可能真，其余一律假），**不**在宿主再写第二份 `as? Ready`。

### 2. 宿主接线

- `AppScreen.kt`：学习页 `onOpenReading` 换成一条共用 lambda
  （真 → `readingAccessViewModel?.openTodayArticle()`，不切 tab；假 → `selectedTab = AppTab.READING`）。
  两枚入口（`today_plan_reading_status` 裸行、空态 `today_plan_read_article`）都走它。
- 加载触发 `AppScreen.kt:411` 条件加 `|| selectedTab == AppTab.LEARNING`，并改写上面对那枚
  「只在进阅读 tab 才取数」的注释：学习页的直达判定要读这份真值，停在 Loading 上就是假按钮。
- 契约测试（源闸）：学习页 `onOpenReading` 不得再是无条件 `selectedTab = AppTab.READING`；
  判定只从 `canOpenTodayArticleDirectly()` 取。

### 3. 打卡屏改名

- `CheckInScreen.kt:65` 页名 → 「打卡与连续」。
- 同步 connected `CheckInScreenTest`（2 处文字锚）与 JVM `CheckInScreenHeaderShapeContractTest`
  （4 处样本串）；`学习记录` 相关串一字不动。
- 源闸：全站用户可见串里「学习打卡」归零（文档与历史报告不覆写）。

### 4. connected 端到端

- 已解锁且有今日文章（常规分支，`today_plan_reading_status`）：点行 → `article_english` 出现，
  退出文章回到学习页（`today_plan_screen` 在位、`reading_access_screen` 不在、
  `app_tab_learning` 的 stateDescription 仍是「已选中」）。
- 已解锁且有今日文章（空态分支，`today_plan_read_article`）：用户报的那条路径，同一条判定第二枚锚点。
- 未解锁（`readingFixture(unlocked = false)`）：点同一行 → `reading_access_screen` 在位、
  文章节点不在、`reading_access_locked_reason` 可见。
- 已解锁但今天没文章（`readingFixture(withTodayArticle = false)`）：落 `reading_access_screen`，
  生成入口 `reading_source_ai` 在场（`ReadingAccessScreen.kt:313-316` 那一支），文章节点不在。
- **实施时补的一条形状**（connected 首轮实测）：文章首进会自动展开 F3-01B 覆盖词面板
  （`ModalBottomSheet` 独立窗），系统返回先收面板。两条直达用例都先点 `coverage_popup_close`
  再按返回，否则钉的是面板的返回优先级、不是落点；`ArticleReadingScreen.kt:200-202` 是依据。

### 5. 门禁与交付

- 全量门控 + 双档 connected + 截图 + 真机复装 + 报告 + 逐域提交；**不推送，等用户说「推送」**。

## 不做

- 不加 AI 助学页配置入口（用户 2026-11-02 裁定「暂不加」，方案 C 第 2 条继续挂账）。
- 不改学习/阅读两个 tab 的版式与内容；不改解锁规则、FSRS、文章生成与网络策略。
- 不重命名「学习记录」「阅读历史」，不动 hub 三格「连续天数」入口名。

## 追加步骤 6：第三枚入口并回（用户同日令「并回来」，决策 §1-B）

- 范围：`TodaySummaryScreen`（学完末张卡后的「今日完成」收尾屏）那枚「去读今日文章」并入同一条判定；
  判定本体仍只此一处，新 lambda `openTodayArticleFromSummary` 只做「先 `exitLearning()` 再走共用落点」。
- 顺序约束由挂载链决定（文章覆盖层的 `else if` 在 `showLearning` 之后，先声明者赢），
  已用**变异复验**实测：删掉 `exitLearning()` 后 JVM 形状闸与 connected 直达腿双双变红。
- 门禁：`assembleDebug` → `testDebugUnitTest`（300 / 2225 / 0）→ `compileDebugAndroidTestKotlin`；
  connected 新增 2 腿（直达 + 锁定落首页）并跑全类 `AppScreenTest` `OK (64 tests)`。
- 同批交付：`docs/design/2026-11-03-duplication-audit.md`（全站重复项盘点，只研究未实施，待用户拍板范围）。
- **不推送**，等用户说「推送」。
