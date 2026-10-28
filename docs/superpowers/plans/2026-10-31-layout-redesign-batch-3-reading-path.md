# 全应用版式重做 · 批 3 阅读链路 实施计划

> **For agentic workers:** 本仓按 `AGENTS.md` + 分阶段 Spec 推进；本计划逐条落地
> `docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md` §6「批 3 阅读链路」。

**Goal:** 把阅读链路五屏（外刊选读、阅读历史、文章阅读、粘贴导入、查词）从「M3 默认主题 +
自写页头 + 卡片汤 + 静态薄荷桥」推到批 1 已立的骨架：`AppType` 六档排印、`appColors()` 令牌、
`AppScreenHeader` 共享页头、`StrongCard`/`SoftCard`/`BareRow` 三档容器、失败态带重试、
危险动作带确认与危险墨。同批收掉 spec §6 批 3 的「失败态重试与清空确认」。

**Architecture:** 不新增数据通路、不动 ViewModel 语义、不动 Room/FSRS/备份/AI 请求策略。
五屏各做一次「换装 + 路由微调」：容器、排印、色、页头、主操作位置、列表容器（`Column` →
`LazyColumn`）、失败态重试。新增 5 枚屏级 JVM 源码契约（`*LayoutContractTest`）先红后绿；
四个棘轮在同一批改登记值：页头族 seed 摘 4 行（11 → 7）、容器族屏级表加 5 行（7 → 12）、
裸 hex 与深色静态读保持 0。

**Tech Stack:** Kotlin / Jetpack Compose / Material 3；JVM 源码契约测试；本机 Gradle 8.13
（`$HOME/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle.bat`，**禁用 `./gradlew` 与 9.x**）。

---

## Global Constraints

- **改期望值，不放宽判据。** 既有契约因本次改动变红时，只许把钉住的现值换成新值（tag 名、
  文案、计数、集合）；不许删断言、不许 `assertTrue(true)`、不许放宽到 `>=`。删掉一枚判据必须
  在同一条提交里写明它被哪条新判据替代。**本批会删的只有「旧页头的目的地文案」这一族**，
  每一处都要配一枚「旧文案必须为 0」的正向断言（净增判据，不是净减）。
- **TDD 先红。** 每个 Task 的 Step 1 是写测试，Step 2 必须实跑并看到**预期的那条红**（贴失败消息），
  再进 Step 3。Task 4（粘贴导入）如果与契约同轮落地，必须按批 2 Task 4 的先例登记工序偏差并给补偿证据。
- **门禁口径**（每个 Task 收尾）：
  ```powershell
  $g = Get-ChildItem -Path "$HOME\.gradle\wrapper\dists\gradle-8.13-bin" -Recurse -Filter gradle.bat | Select-Object -First 1
  & $g.FullName :app:testDebugUnitTest --tests "com.example.englishlearning.ui.<TestClass>" --offline --no-daemon --no-build-cache
  ```
  Task 6 全量门禁：
  ```powershell
  & $g.FullName :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin `
      verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
  ```
- **数据与安全零改动。** 不碰 Room 表、FSRS、备份格式、Keystore、AI 请求体、ViewModel 状态机。
  本批只动 `ui/` 呈现层 + 屏内私有 Composable + `AppScreen.kt` 的五个挂载分支（仅加回调/换 token）。
- **真机红线。** 真机 `bf353dda` 只做 `adb install -r` + 手工目视，**永不跑 instrument**；
  connected 只在 `emulator-5574` 用 `am instrument`，**禁 `connectedDebugAndroidTest`**，**禁清数据**。
- **共享工作树。** 本机无可用 `git`：Task 各「提交」步骤一律登记为未执行，以本文件 + `docs/handoff/`
  报告作为权威账。
- **大字号不退化。** 五屏在 `fontScale = 2f` 下必须可滚、可点；吸底操作条不得遮内容。
  阅读历史现**完全没有滚动容器**（`ReadingHistoryScreen.kt:52-55` 无 `verticalScroll`/`LazyColumn`），
  这是本批必须先修的一条 C 类必修。
- **一屏一个主操作。** 吸底条只放主 CTA 与它的次操作，不塞状态说明。
- **编码红线。** 源文件 LF + UTF-8 无 BOM；PowerShell 5.1 读文件一律带 `-Encoding UTF8`
  （不带会把中文读坏并**吞行**：`ReadingFeedScreen.kt` 会从 143 行读成 129 行）。行数、行号以
  `read` 工具 / `-Encoding UTF8` 为准。

---

## 现场事实（2026-10-31 取证，改代码前必须据以核对）

| 屏 | 文件（行数） | 现状要点 |
|---|---|---|
| 外刊选读 | `ReadingFeedScreen.kt`（143） | **唯一页头已合规**（`AppScreenHeader("外刊选读", onBack)` :65）；根 `Column` + `verticalScroll`（:56-64）**不是 LazyColumn**；条目是 M3 `Button` + `RoundedCornerShape(18.dp)`（:111-123），**只渲染 `item.title`，日期/篇幅零渲染**；`Unreachable`(:90) / `FetchFailed`(:96) **只有文案、无重试动作**，函数签名无 `onRetry`；加载态只有一行文字（:78-83）；`MaterialTheme.typography` 与七枚 Mint 桥**均已为 0**（该屏是批 1 已换装的样板） |
| 阅读历史 | `ReadingHistoryScreen.kt`（137） | **零滚动容器**（:52-55 只有 `Column`，大字号/多组必裁）；页头自写 = `Button("返回")` + `semantics{contentDescription="返回每日阅读"}`(:56-60) + `MaterialTheme.typography.headlineSmall` 标题(:61)；M3 `Card(` ×3（:75/:88/:104）+ `CardDefaults` ×3 + `RoundedCornerShape` 18/18/14 三档；`Mint*` 桥 **10 处**；单版本卡写「第 1 版」（:83）；删除入口是 `TextButton` + `MintPrimaryDark`（:121-124）**无危险墨**（确认框在宿主 `AppScreen.kt:1455-1473`，危险色由 `ConfirmDestructiveDialog` 给） |
| 文章阅读 | `ArticleReadingScreen.kt`（462） | 页头自写：`Button("返回")`(:109-112，**无 tag 无 contentDescription**) + 发音按钮(:113-117)；标题 `MaterialTheme.typography.headlineSmall`(:221)；根 `Column` + `verticalScroll(scrollState)`(:99-107)（滚动位置回传被 `SearchCloseReadingStateRetentionTest` 逐字钉住）；覆盖词弹窗是 `GlassDialog(`(:177-180) **独立对话框，整块挡住正文**；「全文对照」是模式 `FilterChip`(:235)、「显示译文」是独立 `TextButton`(:265-271)，**两个控件互不联动**；`Mint*` 桥 **14 处**、`MaterialTheme.typography` **8 处**；来源块四行平铺（站名 / 域名 / 「原文链接（不会自动打开）」/ URL，:419-445）；「完成阅读」在流末尾非吸底(:284) |
| 粘贴导入 | `ArticleImportScreen.kt`（141） | 页头自写 `TextButton("← 返回上一层")`(:61-65) + `headlineSmall` 标题(:67)；**「≥40 词」规则不前置**——只在事后拒绝文案里出现（`ImportRejection.message()` :124，「正文太短，至少需要 40 个单词。」），输入框之前之后都无常驻说明；提交按钮 `enabled = !state.submitting`(:112)，**空表单可点**；`Mint*` 桥 **10 处**、`typography` **3 处**；提交按钮 `RoundedCornerShape(18.dp)`(:114) 在流末尾非吸底 |
| 查词 | `GlobalVocabularySearchScreen.kt`（358） | `Scaffold(containerColor = MintBackground)`(:83)，页头自写 = 可点 `Text("← 返回上一层")`(:95-104) + `headlineSmall` 标题(:105-110)；提交按钮文案随输入在「查看最近搜索 / 全量搜索」间翻转(:146)，`onClick` 同一个 `submit`（:78-81，**职责其实没变、只是文案在变**）；失败态**已有重试**(:208-221, tag `global_search_retry`)；「清空搜索历史」确认**已在宿主**(`AppScreen.kt:749-765`)；历史行与结果行都渲染「搜索 N 次」(:288-295 / :347-354)；`Mint*` 桥 **19 处**、`typography` **15 处**；结果行/历史行都是 `border(1.dp, MintOutline, RoundedCornerShape(14.dp))` 的卡 |

**受影响的既有闸（改前先读、改时同步改登记值）**

| 闸 | 现状 | 本批动作 |
|---|---|---|
| `SharedComponentUniquenessRatchetTest` | 页头族 seed 11 枚：`AiFeature` / `AiLearning` / **`ArticleImport`(:215)** / **`ArticleReading`(:216)** / **`GlobalVocabularySearch`(:217)** / `ImageStudio` / `Licenses` / `PhotoTranslation` / **`ReadingHistory`(:221)** / `SentenceAnalysis` / `WordAiQa` | 四屏迁共享页头后**同批摘四行**（剩 7 枚），摘行处写理由；摘行门 `removing_a_file_from_the_header_seed_requires_its_headline_reads_to_be_cleared`(:563-590) 要求该文件 `MaterialTheme.typography.headline` 读点归零；`each_family_offender_set_equals_its_seed_exactly`(:385) 双向判（多摘也红）。**`ReadingFeedScreen.kt` 本来就不在册** |
| 同上·词行族 seed | 含 `GlobalVocabularySearchScreen.kt`(:237)，理由 R-20C.1（结果行 5 信息 + 2 诊断 vs `WordRow` 4 槽，强迁必丢 IPA） | **保留不动**：只换令牌与排印，不许改 `GlobalSearchResultRow(` 的函数名与信息构成（`:776-787` 钉「本文件按窗口规则恰 1 处落窗」） |
| `ContainerContractTest` | 屏级表 7 屏（`:25-36`），五屏均不在表内 | 五屏全部纳入（→ 12 屏），判据不变（`StrongCard(` ≤ 1） |
| `RawColorLiteralRatchetTest` | `RawColorSeedAllowlist` 只剩 `AiMascot.kt to 1`，五屏裸 hex 额度 0 | 保持 0；`ReadingHomeLayoutContractTest:283` 另钉 `ArticleReadingScreen.kt` 不得出现 `Color(0xFF17342A)` |
| `DarkTokenConsumptionRatchetTest` | 五屏现读点 0（`Mint*` 走 `MintPalette` 的 composable getter 桥，不在 `\b(?:AppPalette\|DomainColors)\.` 视野内） | 换装后仍为 0：只许 `appColors()` / `appDomain()` 组合期读 |
| `ReadingHomeLayoutContractTest` | 钉 Feed 二级层七枚锚（`:59-72`）+ 逐字 `"来源：\${feed.sourceDisplayName}"`（`:73-76`）+ 宿主四站点（`:78-94`）+ 正文墨 `style = AppType.Body.copy(color = appColors().text1)` **精确子串**（`:287`） | 全绿是前提；`ArticleReadingScreen.kt:389` 那一行**一个字都不能改** |
| `SearchCloseReadingStateRetentionTest` | 逐字钉 `rememberScrollState(articleScrollPosition)`(:61) + `snapshotFlow { scrollState.value }.collect(onArticleScrollPositionChange)`(:65)；并钉「查词 overlay 分支排在文章分支之前」(：78-80) | 文章页保留 `verticalScroll(scrollState)`（**本批不换 LazyColumn**）；宿主两处 `else if` 字面条件不动 |
| `SearchEntryWiringTest` | 钉 `openSearch(` 全文件**恰 3 处**（主页 / 阅读页 / 拍照翻译） | 不得多出第四个入口 |
| `AccessibilityStateSemanticsContractTest` | `:174-182` 按 `reading_history_group_` 钉 `stateDescription = if (expanded) "已展开" else "已收起"`（**变量名必须叫 `expanded`，`when` 替代也红**） | 阅读历史改 LazyColumn 后该形状原样保留 |
| `AccessibilitySwitchLabelContractTest` | `:53-57` 钉 `testTag("article_marks_switch")` 之后 80 字符内必须有 `contentDescription` | 文章页开关行重排时不得拆散这两行 |
| `GlassPanelInkPairingContractTest` | `:96-108` 把覆盖词弹窗登记为玻璃面板（身份字面「本文覆盖你已背的」），判据「容器与前景同源取墨」 | 改成 `ModalBottomSheet` 后同步该条登记（容器锚从 `surfaceGlass` 改 `appColors().surface`），判据不放宽 |
| `AppScreenOverlayContainerContractTest` | `:429-443` 逐字钉宿主的四条危险确认（含「清空搜索历史？」6 条字面、路径 A 守卫 `:593`），`ConfirmDestructiveDialog(` 恰 4 枚（:331） | **五屏内不许自造对话框**：删除/清空都只发回调 |
| `MaterialThemeAssemblyContractTest` | `MaterialTheme(` 装配点唯一；六档 `AppType` metrics 与墨锁定 | 本批不改主题文件 |

**connected 侧必须同步改期望值的地方**（改的是锚点，不是判据强弱）

| 用例 | 现钉 | 改后 |
|---|---|---|
| `ArticleImportScreenTest:101-115` | `onNodeWithTag("import_back")` + `onNodeWithText("← 返回上一层")` 全等 | 页头返回位：`app_screen_header` 在 + `contentDescription("返回")` 可点 → 回调恰 1 次 + 「← 返回上一层」必须 0（**加严**） |
| `GlobalVocabularySearchScreenTest:329-346` | `onNodeWithText("← 返回上一层")` + CD「返回上一层」 | 同上口径；`:356-373` 的反向断言「不许写返回首页」**保留**（加严：方向语义仍被钉） |
| `GlobalVocabularySearchScreenTest:176/200/224` | 按钮文案在「查看最近搜索 / 全量搜索」间三态 | 文案固定为「搜索」：断言「空输入仍可点」「点击后仍回最近搜索」「文案不随输入变」（语义等价，多一条「不变」断言） |
| `ArticleReadingScreenTest:93` | `FULL_TRANSLATION` 档起手 `article_translation` 存在 | 旧偏好载入时按「双语」选中且译文已展开（`article_mode_full_translation` 不存在） |
| `ArticleReadingScreenTest:259` | 点 `article_mode_full_translation` → 回调 `FULL_TRANSLATION` | 只剩两枚 chip（`article_mode_english_first` / `article_mode_bilingual`）+ 同一行的 `article_translation_toggle`；第三枚 tag 必须 0 |
| `ArticleReadingScreenTest:292-312` | 署名 `article_attribution` 与链接 `article_source_link` 各自独立行 | 来源块收成两行后仍必须能取到四枚 tag（`article_source_name` / `article_attribution` / `article_source_url_label` / `article_source_link`），`doesNotExecuteTheSourceLinkAutomatically`（:314）一字不动 |
| `ArticleReadingScreenTest:371-401` | `coverage_popup` / `coverage_popup_count` / `coverage_chip_*` / `coverage_popup_close` | 载体从 `GlassDialog` 改 `ModalBottomSheet`，四枚 tag 全部保留；新增「sheet 不挡正文」断言（正文 tag 仍可点） |
| `AppScreenTest:995` | `onNodeWithText("返回")` 离开文章页 | 改 `onNodeWithContentDescription("返回")`（图标返回位） |
| `ReadingHistoryScreenTest` | 8 条按 tag + 「第 2 版 · 2026-09-22」 | 锚全保留；新增「单版本不写第 1 版」与「删除入口取危险墨」两条（净增） |
| `ReadingFeedScreenTest:160-174/192-247` | 像素腿：`reading_feed_screen` 众数 == `background`、`feed_source` 墨 == `text1` | 根 tag 必须留在换装后的根节点上，否则两条像素腿找不到节点 |

---

## 与规范的三处对账（先把口径钉死，避免执行期各屏各自解释）

1. **外刊「条目补日期/篇幅」→ 只补日期。** `FeedItem`（`reading/ArticleFeedParser.kt:7-12`）只有
   `title` / `articleUrl` / `publishedAtEpochMillis` / `summary`，**没有篇幅字段**；补篇幅要么改数据形状
   （撞「数据层零改动」），要么按 `summary.length` 糊一个假字数。两条都不做：本批**只补日期**，
   复用既有 `wordListDateLabel(instant, zoneId)`（`WordListFormat.kt:37`）+ 新增
   `zoneId: ZoneId = ZoneId.systemDefault()` 形参（与 `MyWordsScreen.kt:93` 同一写法，不偷读系统时区）。
   篇幅登记为「数据形状不足，属数据层议题」，留档待拍板。
2. **「全文对照」与「显示译文」合并为一项 → 合成「两枚模式 chip + 同一行一枚译文开关」。**
   现状是两个互不联动的控件（模式 chip 只改偏好、`translationExpanded` 只由按钮翻），用户要为
   「看全译」点两处。VM 侧（`ArticleReadingViewModel.kt:82-96/123`）**零改动**：屏侧把第三档
   `FULL_TRANSLATION` 折叠成「双语 + 译文已展开」的呈现等价，模式 chip 只发前两档。
   **不做**「一个开关通吃三档」——那要改 `setMode` 对 `translationExpanded` 的联动，撞「不动 ViewModel 状态机」。
3. **「外刊来源只显示站名」→ 收成两行，不删署名。** 现四行（站名 / 域名 / 链接标签 / URL）里，
   域名是 `attributionText`（授权署名）、URL 是「原文链接（不会自动打开）」这条安全能力的唯一可见处，
   两者都不能删（删了既违反「不删判据」也丢合规与安全提示）。落法是**去重而非删信息**：
   行 1 = 站名（`article_source_name`，`AppType.Title`）+ 行尾小字域名（`article_attribution`，`text3`）；
   行 2 = 「原文链接（不会自动打开）」+ URL 同一列（`article_source_url_label` / `article_source_link`）。
   源内不再出现模型名/参数摘要/责任长句以外的第三条技术串（现状本来也没有）。

---

## 文件地图

| 动作 | 文件 | 责任 |
|---|---|---|
| 改 | `ui/ReadingFeedScreen.kt` | `LazyColumn` + `BareRow` 条目（标题 2 行 + 日期）、失败态补重试、`zoneId` 形参、令牌复核 |
| 改 | `ui/ReadingHistoryScreen.kt` | 页头迁共享、`LazyColumn`、`Card(` ×3 → `BareRow`/`SoftCard`、单版本去「第 1 版」、删除入口危险墨、`Mint*` 清零 |
| 改 | `ui/ArticleReadingScreen.kt` | 页头迁共享（+ 发音进 `action` 槽）、吸底完成条、覆盖词改底部 sheet、译文控件合并到 chip 同行、来源块收两行、`Mint*`/`typography` 清零 |
| 改 | `ui/ArticleImportScreen.kt` | 页头迁共享、规则前置常驻、空表单禁提交、提交吸底、令牌换装 |
| 改 | `ui/GlobalVocabularySearchScreen.kt` | 页头迁共享、提交文案固定、历史行计数去重、卡 → `BareRow`、`Mint*`/`typography` 清零 |
| 改 | `ui/components/Containers.kt` | `BareRow` 增 `titleMaxLines: Int = 1`（外刊标题要 2 行；**不动几何断言涉及的任何常量与链序**） |
| 改 | `ui/AppScreen.kt` | 五个挂载分支：Feed 加 `onRetry`；其余仅 token/回调对齐（分文字面条件不动） |
| 新 | `test/.../ui/ReadingFeedLayoutContractTest.kt` | 外刊新骨架源码契约 |
| 新 | `test/.../ui/ReadingHistoryLayoutContractTest.kt` | 阅读历史新骨架源码契约 |
| 新 | `test/.../ui/ArticleReadingLayoutContractTest.kt` | 文章阅读新骨架源码契约 |
| 新 | `test/.../ui/ArticleImportLayoutContractTest.kt` | 粘贴导入新骨架源码契约 |
| 新 | `test/.../ui/GlobalSearchLayoutContractTest.kt` | 查词新骨架源码契约 |
| 改 | `test/.../ui/SharedComponentUniquenessRatchetTest.kt` | 页头族 seed 摘四行（留理由） |
| 改 | `test/.../ui/ContainerContractTest.kt` | 屏级表 7 → 12 屏 |
| 改 | `test/.../ui/GlassPanelInkPairingContractTest.kt` | 覆盖词面板登记随载体改（判据不放宽） |
| 改 | `androidTest/.../ui/*Test.kt` | 上表 connected 期望值同步（编译必须过，运行走模拟器） |

---

### Task 1: 外刊选读重排（`ReadingFeedScreen.kt`）

**目标骨架（Ready 态）**

```
Column(fillMaxSize, background(colors.background), testTag("reading_feed_screen"))   ← 像素腿取样根
├─ AppScreenHeader(title = "外刊选读", onBack = onBack)              ← 已合规，保留
├─ LazyColumn(weight(1f), contentPadding v8)                        ← C 骨架：索引页用懒列表
│   ├─ item: Text("来源：${feed.sourceDisplayName}")  AppType.Footnote · colors.text2  (feed_source)
│   └─ items(feed.items): BareRow(
│         title = item.title (titleMaxLines = 2),
│         subtitle = item.summary.takeIf { it.isNotBlank() },
│         trailing = wordListDateLabel(Instant.ofEpochMilli(item.publishedAtEpochMillis), zoneId),
│         topDivider = index > 0,
│         onClick = { onFetchFeedItem(item) },
│         modifier = Modifier.testTag("feed_item_$index").semantics { contentDescription = "阅读外刊文章 ${item.title}" },
│       )
└─ 五态各自整块呈现在列表位置：Idle(feed_idle) / Loading(feed_loading) / Empty(feed_empty)
   / Unreachable(feed_error + feed_retry 重试) / FetchFailed(feed_fetch_failed + feed_retry 重试)
```

**不许动的既有锚**：七枚 tag 字面量（`feed_idle` / `feed_loading` / `feed_empty` / `feed_error` /
`feed_fetch_failed` / `feed_source` / `feed_item_`）各恰 1；`"来源：${feed.sourceDisplayName}"` 逐字改不得；
四条既有文案（Unreachable / FetchFailed / Empty / Idle）逐字保留（connected 按全等断言）；根 tag 留在
带 `background(colors.background)` 的根节点上。

- [x] **Step 1: RED — 新建 `ReadingFeedLayoutContractTest.kt`**（已落：7 条，含 `bodyOf` / `branchBody` / `callSpan` 取样助手）

七条断言（全按符号取样，不锚行号；带「取不到本体即 fail」的反空转断言）：

1. **列表容器**：`LazyColumn(` 恰 1 且 `verticalScroll(` == 0（C 骨架）；`reading_feed_screen` 字面量仍在。
2. **条目容器**：`BareRow(` 调用 ≥ 1、M3 `Button(` == 0、`RoundedCornerShape(` == 0；`feed_item_$index`
   那枚 tag 与 `BareRow(` 同一次调用窗口内。
3. **日期**：文件含 `wordListDateLabel(` 与 `publishedAtEpochMillis`；函数签名含
   `zoneId: ZoneId = ZoneId.systemDefault()`；`Instant.ofEpochMilli(` 在场。
4. **重试**：`feed_error` 窗口内含 `testTag("feed_retry")` 与 `TextButton(`；`feed_fetch_failed` 窗口内
   同样有 `feed_retry`；`AppScreen.kt` 的 `ReadingFeedScreen(` 调用内含 `onRetry =`。
5. **排印**：`MaterialTheme.typography` == 0、`AppType.` ≥ 3（页头/来源/正文各一档）。
6. **令牌**：七枚 Mint 桥（`MintPrimaryDark`/`MintPrimary`/`MintTextMuted`/`MintSurface`/`MintOutline`/`MintTint`/`MintBackground`）
   各 == 0（现状已 0，作为防回潮锚）；`appColors()` ≥ 1；`border(` == 0。
7. **七枚锚各恰 1 字面量**（计划列清单逐枚点名）。

- [x] **Step 2: 实跑，确认红**（实测 `7 tests completed, 4 failed`：`LazyColumn` 0 / `Button(` 1 / 无 `zoneId` 形参 / 取不到 `FeedStatusNotice` 本体；3 条防回潮本来就绿）

```powershell
& $g.FullName :app:testDebugUnitTest --tests "com.example.englishlearning.ui.ReadingFeedLayoutContractTest" `
    --offline --no-daemon --no-build-cache
```
期望红：第 1 条（`verticalScroll` 在、无 `LazyColumn`）、第 2 条（`Button(` == 1、`RoundedCornerShape` == 1、
无 `BareRow(`）、第 3 条（无 `wordListDateLabel`、无 `zoneId`）、第 4 条（无 `feed_retry`、无 `onRetry`）。
第 5/6/7 条应**本来就绿**——它们的作用是「换装不许回潮」，红不了要在报告里点名说明。

- [x] **Step 3: GREEN — 换骨架**（已落：`ReadingFeedScreen.kt` 重写为 `Column` + 共享页头 + `LazyColumn`；`BareRow` 增 `titleMaxLines`；`AppScreen.kt` 补 `onRetry`）

`ReadingFeedScreen.kt`：根 `Column` 保留 `background(colors.background)` + `testTag("reading_feed_screen")`
但去掉 `verticalScroll`；内容改 `LazyColumn(Modifier.weight(1f))`；条目从 `Button` 改 `BareRow`
（标题 2 行、摘要副标题、日期 trailing）；失败两态各补一枚 `TextButton("重试")`（tag `feed_retry`，
`InlineNotice` 承载错误文案可保留 Box 包 tag 的旧写法）；新增 `onRetry: () -> Unit` 与
`zoneId: ZoneId = ZoneId.systemDefault()` 形参；`AppScreen.kt:1479-1483` 的调用点补
`onRetry = { readingAccessViewModel?.openFeed() }`（`openFeed()` 是现成入口，见 `ReadingAccessViewModel.kt:190-207`）。

`Containers.kt` 的 `BareRow` 增 `titleMaxLines: Int = 1`（`Text(maxLines = titleMaxLines)`）；
`ContainerContractTest` 的四条几何断言（`heightIn(min = 52.dp)` / `padding(horizontal = 15.dp, vertical = 13.dp)` /
`hairlineTop(` / `BareRowDividerIndent`）涉及的常量与链序一个字不动。

- [x] **Step 4: 转绿**（实测：三枚点名类全绿；全量 `:app:testDebugUnitTest` 1878 tests / 0 failed；`:app:compileDebugAndroidTestKotlin` 绿）

```powershell
& $g.FullName :app:testDebugUnitTest --tests "com.example.englishlearning.ui.ReadingFeedLayoutContractTest" `
    --tests "com.example.englishlearning.ui.ReadingHomeLayoutContractTest" `
    --tests "com.example.englishlearning.ui.ContainerContractTest" --offline --no-daemon --no-build-cache
```
另跑 `:app:compileDebugAndroidTestKotlin`（`ReadingFeedScreenTest` 的九条用例是编译面，运行走 Task 6 的模拟器）。

- [ ] **Step 5: 提交**（本机无 git → 登记为未执行；Task 1–5 同此）

---

### Task 2: 阅读历史重排（`ReadingHistoryScreen.kt`）

**目标骨架**

```
Column(fillMaxSize, background(colors.background), testTag("reading_history_screen"))
├─ AppScreenHeader(title = "阅读历史", onBack = onBack)        ← 撤自写「返回」Button + headlineSmall 标题
├─ notice?.let { Text(AppType.Footnote, colors.text2, testTag("reading_version_notice")) }   ← 保留
└─ LazyColumn(weight(1f))                                     ← 新增：本屏原来零滚动
    ├─ 空态 item：Text("暂无本地文章…", testTag("reading_history_empty"))
    └─ groups:
        单版本 → BareRow(title = article.title,
                         subtitle = article.localDate,           ← 不再拼「· 第 1 版」
                         topDivider = true, onClick = onOpenArticle,
                         modifier = testTag("reading_history_item_${article.articleId}"))
        多版本 → BareRow(title = article.title, trailing = "${versions.size} 版", showChevron = true,
                         tag = reading_history_group_$key,
                         stateDescription = if (expanded) "已展开" else "已收起")   ← 形状逐字保留
                  展开后每版一行（无卡片壳）：
                    "第 N 版 · yyyy-MM-dd"  AppType.Body · colors.text1      (reading_version_row_$id)
                    重读 TextButton (reading_version_reread_$id)
                    删除 TextButton，文字色 colors.error                    (reading_version_delete_$id)
```

**不许动的既有锚**：八枚 tag 字面量各恰 1（`reading_history_screen` / `reading_version_notice` /
`reading_history_empty` / `reading_history_item_` / `reading_history_group_` / `reading_version_row_` /
`reading_version_reread_` / `reading_version_delete_`）；`stateDescription = if (expanded) "已展开" else "已收起"`
**逐字保留**（`AccessibilityStateSemanticsContractTest:174-182` 连变量名都钉）；「第 2 版 · 2026-09-22」
这类多版本行的拼法保留；空态文案前缀「暂无本地文章」保留；**删除确认不动**（宿主 `AppScreen.kt:1455-1473`）。

- [x] **Step 1: RED — 新建 `ReadingHistoryLayoutContractTest.kt`**（已落：7 条；第 6 条的域色一条实测写成「`appDomain(` 恰 1」而非「≥ 1」，并把计划的 `dangerFill` 落到本仓真名 `errorFill`——两处都在测试 KDoc 里写明口径）

七条：

1. **页头**：`AppScreenHeader(` 恰 1 且 `title = "阅读历史"`、`onBack = onBack` 在同一调用窗口内；
   全文件 `contentDescription = ` **== 0**；`MaterialTheme.typography` == 0。
2. **滚动**：`LazyColumn(` 恰 1；`verticalScroll(` == 0；根节点仍带 `background(colors.background)` 与 `reading_history_screen`。
3. **容器**：`Card(` == 0、`CardDefaults` == 0、`border(` == 0、`RoundedCornerShape(` == 0；
   `BareRow(` 调用 ≥ 2。
4. **单版本不写「第 1 版」**：`reading_history_item_` 窗口内 `"· 第 "` 拼法为 0 且含 `localDate`；
   多版本窗口内「第 N 版」拼法仍在（`"第 ${article.version} 版"` 或等价）。
5. **危险墨**：`reading_version_delete_` 窗口内含 `colors.error`；全文件 `dangerFill` 为 0（危险底只给确认框）。
6. **令牌**：七枚 Mint 桥各 == 0（现状 10 处）、`appColors()` ≥ 1、`appDomain(` ≥ 1（阅读域色只用一处主操作）。
7. **八枚锚各恰 1 字面量**。

- [x] **Step 2: 实跑，确认红**（实测 `7 tests completed, 6 failed`：页头 `AppScreenHeader(` 0 / `LazyColumn` 0 / `Card(` 3 / 单版本尚无裸行 / 无 `colors.error` / `MintPrimaryDark` 8 处；第 7 条八枚锚本来就绿）

期望红：1（无页头、`headlineSmall` 1 处）、2（无 `LazyColumn`）、3（`Card(` 3 处、`RoundedCornerShape` 3 处）、
4（单版本拼「第 1 版」）、5（无 `colors.error`）、6（十处 Mint 桥）。第 7 条应绿。

- [x] **Step 3: GREEN — 换骨架**（已落；两处取样修正写进测试助手 KDoc：括号段必须连尾随 lambda 一起取、`TextButton(` 按 tag 反查而非取第一枚）

按上面骨架改：撤 `Button("返回")` + `semantics{contentDescription="返回每日阅读"}` + `headlineSmall` 标题
（页头归并，headline 读点因此归零——这是摘 seed 行的前提）；根改 `LazyColumn`；`Card(` ×3 拆成
`BareRow`（单版本行 / 多版本组行）与无壳版本行；单版本副标题只留 `localDate`；删除入口文字色改
`colors.error`（容器仍透明，不做红底按钮——`ConfirmDestructiveInkTest` 那一族红线：危险底只给确认按钮）；
`Mint*` 十处全换 `appColors()`。

- [x] **Step 4: 转绿 + 摘 seed**（实测：四枚点名类 37 tests / 0 failed；全量 `:app:testDebugUnitTest` 1885 tests / 0 failed；`:app:compileDebugAndroidTestKotlin` 绿）

```powershell
& $g.FullName :app:testDebugUnitTest --tests "com.example.englishlearning.ui.ReadingHistoryLayoutContractTest" `
    --tests "com.example.englishlearning.ui.SharedComponentUniquenessRatchetTest" `
    --tests "com.example.englishlearning.ui.AccessibilityStateSemanticsContractTest" `
    --tests "com.example.englishlearning.ui.ContainerContractTest" --offline --no-daemon --no-build-cache
```
同批：`SharedComponentUniquenessRatchetTest` 页头族 seed 摘 `"ReadingHistoryScreen.kt"`（:221）并写摘行理由
（「批 3 Task 2：自写返回位 + headline 标题换成共享 `AppScreenHeader(阅读历史, onBack)`，
`ReadingHistoryLayoutContractTest` 同批钉住 `MaterialTheme.typography` 为 0」）；
`ContainerContractTest.screens` 加 `"ReadingHistoryScreen.kt"`。

- [ ] **Step 5: 提交**（本机无 git → 登记为未执行；Task 1–5 同此）

---

### Task 3: 文章阅读重排（`ArticleReadingScreen.kt`，462 行，本批最大一枚）

**目标骨架**

```
Column(fillMaxSize, background(colors.background), testTag("article_reading_screen"))
├─ AppScreenHeader(
│     title = article.articleTitle,            ← 正文标题（article_title 仍留在正文侧，见下）
│     onBack = onBack,
│     action = { 发音按钮（article_pronunciation, 48dp 可点, AppGlyph(AppIcon.Speaker)） },
│   )                                          ← 吸收原「返回」Button + 页头位
├─ Column(weight(1f).verticalScroll(scrollState))     ← 逐字保留（滚动位置回传被钉）
│   ├─ 标题 Text(articleTitle, AppType.Headline, colors.text1, testTag("article_title"))
│   │     + 状态活区（article_status_live_region）
│   ├─ 来源块：行1 = 站名 article_source_name(AppType.Title) + 行尾域名 article_attribution(text3)
│   │           行2 = 「原文链接（不会自动打开）」article_source_url_label + URL article_source_link
│   ├─ 模式行：[English first][双语] 两枚 chip（article_mode_english_first / article_mode_bilingual）
│   │           + 同一行同一容器内 `article_translation_toggle`「显示译文 / 收起译文」
│   ├─ 标注行：article_marks_row（article_marks_switch 与其 contentDescription 相邻，80 字符内）
│   ├─ 正文：AppType.Body / colors.text1 / lineHeight 放大档（题 1 的墨断言逐字保留）
│   └─ 覆盖词：entry chip（coverage_chip_$i）→ 点开 ModalBottomSheet 列出覆盖词
└─ 吸底操作条（Surface(appColors().background) + hairlineTop，article_complete_action_bar）
      └─ 主 CTA Button「完成阅读」(article_complete_reading，实底 primary，高 48dp)
```

覆盖词面板：`ModalBottomSheet(onDismissRequest = { coverageOpen = false }, containerColor = appColors().surface)`
内含 `coverage_popup` / `coverage_popup_count` / `coverage_chip_*` / `coverage_popup_close` 四族 tag，
标题文案「本文覆盖你已背的」逐字保留（`GlassPanelInkPairingContractTest` 的身份字面），
`skipPartiallyExpanded = true` 保证正文不被整块遮挡、sheet 可下滑收起。

**不许动的既有锚**（改了就是数据/语义变更）

- `style = AppType.Body.copy(color = appColors().text1)`（**现 :389，逐字与缩进**）——`ReadingHomeLayoutContractTest:287` 精确子串。
- `rememberScrollState(articleScrollPosition)`（:61）/ `snapshotFlow { scrollState.value }.collect(onArticleScrollPositionChange)`（:65）——`SearchCloseReadingStateRetentionTest` 逐字钉。
- 正文仍用 `verticalScroll(scrollState)`，**本批不换 LazyColumn**（滚动位置语义被钉 + F 类「滚动位置保持回传」判据）。
- 九枚 tag 字面量各恰 1：`article_reading_screen` / `article_pronunciation` / `article_title` /
  `article_mode_english_first` / `article_mode_bilingual` / `article_marks_row` / `article_marks_switch` /
  `article_complete_reading` / `article_status_live_region`；另四枚来源块 tag + 四枚覆盖词 tag 保留。
- `formatImportedAt(...)` 的「导入于 …」文案与 `wordListDateTimeLabel` 口径（:62/:448）不动。
- `onArticleScrollPositionChange` / `onMarkReached` / `onCompleteReading` / `onSetMode` / `onToggleTranslation`
  形参名与调用序不动（宿主 `AppScreen.kt:1130-1180` 按名传参）。

- [x] **Step 1: RED — 新建 `ArticleReadingLayoutContractTest.kt`**

七条：

1. **页头**：`AppScreenHeader(` 恰 1；`MaterialTheme.typography` == 0；`article_pronunciation` 落在页头
   `action` 槽的调用窗口内（即 `AppScreenHeader(` 起点到下一顶层 Composable 之前）。
2. **吸底条**：`article_complete_action_bar` 恰 1，且 `article_complete_reading` 在该 tag 之后、`hairlineTop(` 之后；
   正文滚动容器（`verticalScroll(scrollState)`）出现在吸底条**之前**（`indexOf` 序判）。
3. **覆盖词**：`GlassDialog(` == 0、`ModalBottomSheet(` 恰 1；`coverage_popup` 在 `ModalBottomSheet(` 之后；
   「本文覆盖你已背的」仍在；`heightIn(max = 360.dp)` == 0（取消内嵌二次滚动）。
4. **译文控件合并**：`article_mode_full_translation` 字面量 == 0；`article_translation_toggle` 恰 1，
   且它与 `article_mode_bilingual` 的字符距离 < 400（同一行窗口内，用「两 tag 之间无 `\n        }` 级闭合」判，
   实现为：两 tag 之间不含 `SectionSpacing` / `Spacer(Modifier.height(`）。
5. **来源块两行**：`article_source_name` / `article_attribution` / `article_source_url_label` / `article_source_link`
   各恰 1，且 `article_source_name` 与 `article_attribution` 在同一 `Row(` 窗口内、URL 二者在同一 `Row(` 窗口内。
6. **令牌**：七枚 Mint 桥各 == 0（现状 14）；`Color(0xFF17342A)` == 0；`appColors()` ≥ 3；`RoundedCornerShape(` == 0
   （一律用 `AppShape` token）。
7. **墨断言逐字仍在**：`style = AppType.Body.copy(color = appColors().text1)` 作为子串存在（**防止重构手滑改掉**）。

- [x] **Step 2: 实跑，确认红**

期望红：1（无页头、`typography` 8 处）、2（无吸底条）、3（`GlassDialog` 1、无 `ModalBottomSheet`、`heightIn(max = 360.dp)` 1）、
4（`article_mode_full_translation` 1、toggle 在别处）、5（四枚 tag 现为 4 个独立块，行窗口判据红）、6（Mint 14 处）。第 7 条应绿。

- [x] **Step 3: GREEN — 换骨架**

顺序：先撤自写返回/发音 → 页头（`action` 放发音按钮，`contentDescription("播放发音")`），再拆吸底条，
再覆盖词换 sheet，再合并译文控件，最后来源块收两行 + 令牌清零。每步后单跑本契约，避免一次改 462 行。

`GlassPanelInkPairingContractTest` 同批改登记：覆盖词面板的容器锚从 `surfaceGlass` 改为
`appColors().surface` + `ModalBottomSheet`，判据「容器与前景同源取墨」保留（不放宽）。

- [x] **Step 4: 转绿**

```powershell
& $g.FullName :app:testDebugUnitTest --tests "com.example.englishlearning.ui.ArticleReadingLayoutContractTest" `
    --tests "com.example.englishlearning.ui.ReadingHomeLayoutContractTest" `
    --tests "com.example.englishlearning.ui.SearchCloseReadingStateRetentionTest" `
    --tests "com.example.englishlearning.ui.AccessibilitySwitchLabelContractTest" `
    --tests "com.example.englishlearning.ui.GlassPanelInkPairingContractTest" `
    --tests "com.example.englishlearning.ui.SharedComponentUniquenessRatchetTest" `
    --tests "com.example.englishlearning.ui.ContainerContractTest" --offline --no-daemon --no-build-cache
```
同批：页头族 seed 摘 `"ArticleReadingScreen.kt"`（:216）；`ContainerContractTest.screens` 加本文件。
**注意**：`headerSeedRetentionReason` 里「`article_title` 是正文标题不是页头」那条理由要同步改写成
「标题已由 `article_title` 留在正文、页头只承载返回与发音」——不改字面就会与现状矛盾（`ArticleReadingLayoutContractTest`
第 1 条正好把这条写清）。

- [ ] **Step 5: 提交**（本机无 git → 登记为未执行；Task 1–5 同此）

---

### Task 4: 粘贴导入重排（`ArticleImportScreen.kt`）

**目标骨架**

```
Column(fillMaxSize, background(colors.background), testTag("import_screen"))
├─ AppScreenHeader(title = "粘贴导入", onBack = onBack)        ← 撤 TextButton「← 返回上一层」
├─ Column(weight(1f).verticalScroll)                          ← 表单区可滚（大字号 2f 必须可滚）
│   ├─ 规则卡 SoftCard：Text("正文至少 40 个单词，最多 1200 个单词；中英混排按单词切分计数。",
│   │        AppType.Footnote, colors.text2, testTag("import_rule"))     ← 前置、常驻、在输入框之前
│   ├─ 标题框 TextField(testTag("import_title")) 
│   ├─ 正文框 TextField(testTag("import_body"), minLines = 8)
│   └─ 拒绝提示 InlineNotice（保留，import 失败文案逐字）
└─ 吸底操作条：Button「导入文章」(testTag("import_submit"), 实底 primary, 48dp,
      enabled = 标题非空 && 正文非空 && !submitting)           ← 常显但空表单禁用
```

**不许动的既有锚**：`import_title` / `import_body` / `import_submit` / `import_submitting` 四枚 tag 各恰 1；
`ImportRejection.message()` 的文案（「正文太短，至少需要 40 个单词。」等）逐字不动（`ArticleImportTest` 按全等断言）；
`onSubmit` / `onBack` 形参名不动；`ArticleImportViewModel` 零改动（**校验仍在 VM**，屏侧前置规则只是说明，
不做重复校验——重复校验会与 VM 判据分叉）。

- [x] **Step 1: RED — 新建 `ArticleImportLayoutContractTest.kt`**

七条：

1. **页头**：`AppScreenHeader(` 恰 1 且 `title = "粘贴导入"`；全文件 `contentDescription = ` == 0；
   `"← 返回上一层"` 字面量 == 0；`MaterialTheme.typography` == 0。
2. **规则前置**：`import_rule` 恰 1，且 `indexOf("import_rule") < indexOf("import_body")`；
   文案含「40」与「1200」（数字与 VM 侧阈值同源，写成 `ImportRules.minWords` 若 VM 暴露常量则引用之）。
3. **禁用态**：`import_submit` 之后 200 字符内出现 `enabled =`；`enabled` 表达式含 `!state.submitting` 与
   `title.isNotBlank()` 与 `body.isNotBlank()`（三条件缺一即红）。
4. **吸底条**：`import_action_bar` 恰 1 且 `import_submit` 在其后；`verticalScroll` 位于吸底条之前。
5. **提交在条内只一枚**：`Button(` 全文件恰 2（重试/次要不计则恰 1 主 + 0 次），且无 `RoundedCornerShape(`。
6. **令牌**：七枚 Mint 桥各 == 0（现状 10）；`appColors()` ≥ 2；`SoftCard(` ≥ 1 或 `StrongCard(` == 1。
7. **四枚 tag 各恰 1 字面量**。

- [x] **Step 2: 实跑，确认红**

期望红：1（`typography` 3、页头无、「← 返回上一层」1）、2（无 `import_rule`）、3（`enabled` 只有 `!state.submitting`）、
4（无 `import_action_bar`）、5（`RoundedCornerShape(18.dp)` 1）、6（Mint 10 处）。第 7 条应绿。

- [x] **Step 3: GREEN — 换骨架**

撤 `TextButton("← 返回上一层")` + `headlineSmall` 标题 → 页头；加规则卡（`SoftCard` + `import_rule`）；
提交从流末尾搬到吸底条并补三条件 `enabled`；`importFieldColors` 的令牌从 Mint 桥换 `appColors()`（函数体保留，
只换取色源）；`RoundedCornerShape(18.dp)` → `AppShape.CardStrong` 或 `AppShape.Button`（按 §4.3 按钮圆角 16）。

- [x] **Step 4: 转绿**

```powershell
& $g.FullName :app:testDebugUnitTest --tests "com.example.englishlearning.ui.ArticleImportLayoutContractTest" `
    --tests "com.example.englishlearning.ui.SharedComponentUniquenessRatchetTest" `
    --tests "com.example.englishlearning.ui.ContainerContractTest" `
    --tests "com.example.englishlearning.ui.InlineNoticeActionContractTest" --offline --no-daemon --no-build-cache
```
同批：页头族 seed 摘 `"ArticleImportScreen.kt"`（:215）+ 写理由；`ContainerContractTest.screens` 加本文件；
`androidTest` 侧 `ArticleImportScreenTest:101` 的返回期望值按前面「connected 同步表」改（并加「← 返回上一层 == 0」）。

- [ ] **Step 5: 提交**（本机无 git → 登记为未执行；Task 1–5 同此）

---

### Task 5: 查词重排（`GlobalVocabularySearchScreen.kt`，358 行）

**目标骨架**

```
Column(fillMaxSize, background(colors.background), testTag("global_search_screen"))   ← 撤 Scaffold(MintBackground)
├─ AppScreenHeader(title = "查词", onBack = onBack)          ← 撤可点 Text「← 返回上一层」
├─ 输入区 SoftCard：TextField(global_search_input) + 提交 Button(global_search_submit，文案恒「搜索」)
├─ 历史区（有历史且无结果时）：
│    Text("最近搜索" + global_search_clear_history TextButton「清空」(colors.text2 → 点后走确认框))
│    LazyColumn(weight(1f))
│      └─ BareRow(title = entry.term, subtitle = "${entry.meaning}", trailing = "${entry.count} 次",
│                 tag = global_search_history_item_$i, onClick = ...)
│   空态：global_search_history_empty
└─ 结果区：GlobalSearchResultRow（**函数名与信息构成一字不改**，R-20C.1）
     失败态 global_search_error + global_search_retry（保留）
```

**不许动的既有锚**：17 枚 tag 各恰 1（含 `global_search_retry` 与全部历史/结果族）；
`GlobalSearchResultRow(` 的形状（词行族 seed 的落窗判据 `:776-787` 要求「本文件恰 1 处、信息构成不变」）；
`submit()` 单一入口（`onClick` 仍走同一个 `submit`，只改展示文案）；`SearchEntryWiringTest` 的 `openSearch(` 仍 3 处。

- [x] **Step 1: RED — 新建 `GlobalSearchLayoutContractTest.kt`**

七条：

1. **页头**：`AppScreenHeader(` 恰 1；`Scaffold(` == 0；`"← 返回上一层"` == 0；屏内 `contentDescription = ` == 0；
   `MaterialTheme.typography` == 0（现状 15）。
2. **提交文案固定**：`"查看最近搜索"` == 0、`"全量搜索"` == 0；`"搜索"` 作为按钮文案恰 1；
   `onClick = { submit(` 仍恰 1（职责不变）。
3. **历史行去重计数**：`global_search_history_item_` 窗口内仍含 `"次"` 且只出现一次计数拼法
   （`"${entry.count} 次"` 或等价），**不含**「搜索 N 次」长句（`"搜索 "` 在该窗口内 == 0）。
4. **历史行容器**：`border(` == 0、`RoundedCornerShape(14.dp)` == 0、`BareRow(` ≥ 1；
   `LazyColumn(` 恰 1 且仍带 `weight(1f)`（结果区懒加载被 connected 覆盖）。
5. **清空走确认**：`global_search_clear_history` 的 `onClick` 直接调 `onClearHistory`（屏内不建对话框）：
   `AlertDialog(` == 0、`ConfirmDestructiveDialog(` == 0、`GlassDialog(` == 0。
6. **令牌**：七枚 Mint 桥各 == 0（现状 19）；`appColors()` ≥ 3；`appDomain(` ≥ 1。
7. **`GlobalSearchResultRow(` 恰 1 处定义 + 恰 1 处调用**（R-20C.1 边界由词行族 seed 管，本契约只防误迁）。

- [x] **Step 2: 实跑，确认红**

期望红：1（`Scaffold` 1、`typography` 15、「← 返回上一层」1）、2（两条文案各 1）、3（「搜索 N 次」长句 2 处）、
4（`border(` 2、`RoundedCornerShape(14.dp)` 2）、6（Mint 19 处）。第 5/7 条应绿（现状已由宿主确认）。

- [x] **Step 3: GREEN — 换骨架**

撤 `Scaffold(containerColor = MintBackground)` → 根 `Column` + `background(colors.background)` + tag；
自写页头 → `AppScreenHeader("查词", onBack)`；按钮文案恒「搜索」；历史行 `MintSurface + border(1dp, MintOutline)`
药片 → `BareRow(… trailing = "${count} 次")`；结果行只换取色源，**信息构成与函数名不动**。

- [x] **Step 4: 转绿**

```powershell
& $g.FullName :app:testDebugUnitTest --tests "com.example.englishlearning.ui.GlobalSearchLayoutContractTest" `
    --tests "com.example.englishlearning.ui.SharedComponentUniquenessRatchetTest" `
    --tests "com.example.englishlearning.ui.SearchEntryWiringTest" `
    --tests "com.example.englishlearning.ui.ContainerContractTest" `
    --tests "com.example.englishlearning.ui.RawColorLiteralRatchetTest" --offline --no-daemon --no-build-cache
```
同批：页头族 seed 摘 `"GlobalVocabularySearchScreen.kt"`；`ContainerContractTest.screens` 加本文件；
`headerSeedRetentionReason` 中「本文件第二处 headline 是 `›`/`‹` 方向字形」这条理由要随新骨架重写（方向字形若被
`AppGlyph(AppIcon.Chevron…)` 取代则改写成「字形已换 AppIcon，headline 读点归零」）。

- [ ] **Step 5: 提交**（本机无 git → 登记为未执行；Task 1–5 同此）

---

### Task 6: 全量门禁 + 模拟器 connected + 真机目视 + 报告

- [x] **Step 1: 全量 JVM 门禁**

```powershell
& $g.FullName :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin `
    verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
```
基线：批 2 收口 1871 tests / 0 failures。本批预计净增 ≥ 35 条（5 枚契约 × 7 条）+ connected 侧若干，
末值写进报告并解释差额（只增不减；有减必须逐条说明被哪条替代）。

- [x] **Step 2: 模拟器 connected（只在 `emulator-5574`）**

```powershell
adb -s emulator-5574 shell am instrument -w -e class com.example.englishlearning.ui.ReadingFeedScreenTest `
    com.example.englishlearning/.test  # 依既有脚本口径逐类跑
```
九条基线（每类跑完，0 failures 才进下一类）：`ReadingFeedScreenTest`（含两条像素腿）、`ReadingAccessScreenTest`、
`ReadingHistoryScreenTest`、`ArticleReadingScreenTest`、`ArticleImportScreenTest`、
`GlobalVocabularySearchScreenTest`、`AppScreenTest`（含 `:995` 返回位）、`WordCardScreenTest`（回归）、`TodayPlanScreenTest`（回归）。
**禁 `connectedDebugAndroidTest`**（会清设备数据）。

- [x] **Step 3: 大字号专项**

`adb -s emulator-5574 shell settings put system font_scale 2.0` → 重跑五屏相关类 →
确认：外刊/历史/查词三处 `LazyColumn` 可滚；文章页吸底条不遮正文末行；粘贴导入表单区可滚且提交条不遮输入框 →
`settings put system font_scale 1.0` 恢复。

- [x] **Step 4: 真机目视（只 `adb install -r` + 手看，永不 instrument）**

`adb -s bf353dda install -r <apk>` → 逐屏看 5 屏：一屏一主操作、无卡片汤、外刊有日期、历史单版本无「第 1 版」、
删除是危险色 + 确认、覆盖词 sheet 不挡正文、导入规则在输入框上方、查词按钮恒「搜索」。

- [x] **Step 5: 报告**

写 `docs/handoff/2026-10-31-layout-redesign-batch-3-report.md`，含：Task 1–6 绿证、**改期望值清单（旧值 → 新值 + 依据）**、
seed 摘四行的理由、三处规范性让步（篇幅 / 译文控件 / 来源块）、净增判据统计、未执行项（git 提交）、遗留议题。

---

## Self-Review

**规范覆盖对账（spec §6 批 3 + §5 逐屏表）**

| 规范要求 | 落点 | 判据 |
|---|---|---|
| 外刊条目补日期/篇幅 | Task 1（只补日期，篇幅让档） | `ReadingFeedLayoutContractTest` 第 3 条 |
| 外刊失败带重试 | Task 1 Step 3 + `AppScreen.kt` `onRetry` | 第 4 条 |
| 阅读历史 `LazyColumn` | Task 2 Step 3 | 第 2 条 |
| 单版本不写「第 1 版」 | Task 2 Step 3 | 第 4 条 |
| 删除危险色 + 确认 | Task 2 Step 3（墨）+ 宿主既有确认框 | 第 5 条 + `AppScreenOverlayContainerContractTest` 不动 |
| 覆盖词改弱卡内嵌、不遮正文 | Task 3（`ModalBottomSheet`） | 第 3 条 |
| 全文对照与显示译文合并一处 | Task 3（两 chip + 同行 toggle） | 第 4 条 |
| 补外刊来源 | Task 3 来源块两行 | 第 5 条 |
| 词数规则前置、按钮常显、文案随输入变 | Task 4（规则卡 + 常显禁用 + 提交固定「导入文章」） | 第 2/3 条 |
| 查词「有输入时按钮为『查词』」 | Task 5（**统一恒「搜索」**，见下方让步 2 说明） | 第 2 条 |
| 历史行内「搜索 N 次」 | Task 5 改 `"N 次"` | 第 3 条 |
| 清空要确认 | Task 5（宿主既有确认框，屏内零对话框） | 第 5 条 |

**与规范字面的两处让步（须在报告里显式登记）**
1. 规范 §5 写「有输入时按钮为『查词』」，spec 批 3 写「粘贴导入文案随输入变」，而查词页现状是按钮
   文案随输入在「查看最近搜索 / 全量搜索」间翻转。本批取**恒「搜索」**：与「一屏一个主操作」一致、
   与「文案不欺骗」（同一 `submit` 不该因输入而改身份）一致。若审核席要求严格照字面「查词」，
   改的是 `GlobalSearchLayoutContractTest` 第 2 条的一个字面量，实现一行。
2. 「篇幅」与「合并译文控件」两处已在前置《与规范的三处对账》说明。

**风险与对冲**

| 风险 | 对冲 |
|---|---|
| 文章页 462 行一次改坏（滚动位置/墨断言被手滑改掉） | Task 3 Step 3 拆五小步，每步后单跑本契约 + 两条既有钉值测试 |
| 页头族 seed 少摘/多摘 | 摘行与「headline 读点归零」在同一次跑里验证；`each_family_offender_set_equals_its_seed_exactly` 双向判 |
| `BareRow` 加 `titleMaxLines` 触发容器几何回归 | `ContainerContractTest` 四条几何判据逐字未动，Task 1 Step 4 同跑 |
| `ModalBottomSheet` 在 fontScale 2f 下遮正文 | Task 6 Step 3 专项；`skipPartiallyExpanded = true` |
| 像素腿找不到根节点（tag 挂错层） | Task 1 第 1 条把 `reading_feed_screen` 与 `background(colors.background)` 绑在同一断言里 |

**判据净增**：新 5 枚契约 35 条 + connected 新增「旧文案必须为 0」「单版本无第 1 版」「sheet 不挡正文」约 8 条，
删除判据 0 条。

---

## 执行状态

| Task | 状态 | 备注 |
|---|---|---|
| 计划落盘 | ✅ 2026-10-31 | 本文件 |
| Task 1 外刊选读 | ✅ 完成 | 契约 7 条（RED 4 红 → 绿）；全量 1878 / 0 |
| Task 2 阅读历史 | ✅ 完成 | 契约 7 条（RED 6 红 → 绿）；全量 1885 / 0 |
| Task 3 文章阅读 | ✅ 完成 | 契约 7 条（RED 6 红 → 绿）；页头族 seed 摘行 + `screens` 加行 + 玻璃面板族摘账；`ArticleReadingScreenTest` 25/25、`ArticlePronunciationControlsTest` 5/5、`AppScreenTest` 两条点名用例绿（emulator-5574）；全量 1892 / 0 |
| Task 4 粘贴导入 | ✅ 完成 | 契约 7 条（RED 6 红 → 绿）；seed 摘行 + `screens` 加行；`ArticleImportScreenTest` 7/7 绿（含新增「规则在正文框上方」「空表单禁用、两字段齐后可用」「页头返回位 + 旧文案 0」）；全量 1899 / 0 |
| Task 5 查词 | ✅ 完成 | 契约 7 条（RED 6 红 → 绿，第 7 条 `GlobalSearchResultRow` 防误迁判据设计上先绿）；`BareRow` 增开可选 `trailingTag`（只增不改，既有调用点零变化）；seed 摘行 + `screens` 加行 + 重写 `headerSeedRetentionReason` 的方向字形那段；`GlobalVocabularySearchScreenTest` 13/13 绿、`AppScreenTest` 50/50 绿（emulator-5574，`am instrument`）；全量 1906 / 0 |
| Task 6 门禁/目视/报告 | ✅ 完成 | 全量 `1906 tests / 0 failures`（+35 = 5 契约 × 7）；`verifyThirdPartyNotices` 76 条、`verifyAcTraceabilityMatrix` 56 ids（与批 2 一致）；connected 九类全绿（9/18/8/25/7/13/50/18/24，`am instrument` @ emulator-5574）；`fontScale 2.0` 下五类重跑全绿并已恢复 1.0；真机 `bf353dda` 目视 4 屏（文章阅读与粘贴导入真机不可达，改由 connected 覆盖）；报告 `docs/handoff/2026-10-31-layout-redesign-batch-3-report.md`；git 提交未执行 |

**等待审核席确认的两件事**：① 三处对账（篇幅让档 / 译文控件合并形态 / 来源块不删域名与链接）是否照准；
② 查词按钮取「恒『搜索』」还是严格照 spec 字面「查词」。确认后按 Task 1 起跑。
