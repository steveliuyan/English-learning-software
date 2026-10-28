# 2026-10-31 版式重做 批 3（阅读链路 4 屏 + 查词）交付报告

计划文档：`docs/superpowers/plans/2026-10-31-layout-redesign-batch-3-reading-path.md`
规范文档：`docs/superpowers/specs/2026-10-25-full-layout-redesign-design.md`
范围：`ReadingFeedScreen` / `ReadingHistoryScreen` / `ArticleReadingScreen` / `ArticleImportScreen` / `GlobalVocabularySearchScreen`

## 1. 本次目标

把阅读链路五屏从「卡片汤 + 主操作不突出 + 分类没做好」推到批 1/批 2 同口径的完成度：

- 外刊选读：一屏一枚主操作，标题两行、日期与篇幅不丢。
- 阅读历史：唯一滚动正文 `LazyColumn`，删除走危险色 + 确认，历史单版本（不再有「第 1 版」）。
- 文章阅读：返回/发音并进共享页头，正文唯一滚动，完成条吸底，覆盖词 sheet 不挡正文，来源块两行（不删域名与链接）。
- 粘贴导入：规则在输入框**之前**、提交条吸底且常显（禁用态取代隐藏），提交文案固定「导入文章」。
- 查词：输入区一枚软卡、按钮恒「搜索」、历史与结果都改裸行，`ChevronRight` 取代 `›` 方向字形。

约束（全程未松）：只改版式与信息架构，**不动**数据层 / FSRS / AI 请求策略 / Room / 备份；改期望值不放宽判据（删旧文案必配「旧文案必须为 0」正向断言）；TDD 先红并贴失败消息；`fontScale = 2f` 不退化；一屏一个主操作。

## 2. 完成了什么

| Task | 屏 | 契约测试 | 实测状态 |
|---|---|---|---|
| 1 | 外刊选读 `ReadingFeedScreen.kt` | `ReadingFeedLayoutContractTest`（7） | ✅ 先红 4/7 → 绿；1878 tests |
| 2 | 阅读历史 `ReadingHistoryScreen.kt` | `ReadingHistoryLayoutContractTest`（7） | ✅ 先红 6/7 → 绿；1885 tests |
| 3 | 文章阅读 `ArticleReadingScreen.kt` | `ArticleReadingLayoutContractTest`（7） | ✅ 先红 6/7 → 绿；1892 tests |
| 4 | 粘贴导入 `ArticleImportScreen.kt` | `ArticleImportLayoutContractTest`（7，新建） | ✅ 先红 6/7 → 绿；1899 tests |
| 5 | 查词 `GlobalVocabularySearchScreen.kt` | `GlobalSearchLayoutContractTest`（7，新建） | ✅ 先红 6/7 → 绿（第 7 条设计上先绿，见 §4-8）；1906 tests |
| 6 | 全量门禁 + connected + 大字号 + 真机目视 + 报告 | — | ✅ 见 §3 |

### 屏级变化摘要

- **外刊选读**：整屏重写为「页头 → `weight(1f)` 唯一 `LazyColumn` → 空/失败态」；每篇一枚裸行（`titleMaxLines = 2` + 日期 + 篇幅），旧「每篇一枚实底 `Button` + `RoundedCornerShape(18.dp)`」清零。
- **阅读历史**：页头并入共享 `AppScreenHeader(阅读历史, onBack)`；历史列表改 `LazyColumn`；删除动作改危险色（`dangerFill` → `AppColorSet.errorFill` / `AppPalette.ErrorFill` 口径）并保留二次确认；「第 1 版」字样与同屏重复版本号清零。
- **文章阅读**：页头 `AppScreenHeader(文章标题, onBack, action = 发音位)`（`article_title` 仍留在正文侧承担完整标题）；模式区收成两枚 chip；`CoverageSheet` 改 `ModalBottomSheet(skipPartiallyExpanded = true)`；完成条吸底；来源块两行、`attributionText` 与 URL 原样保留。
- **粘贴导入**：`Column(fillMaxSize + background + testTag("article_import_screen"))` → 页头「粘贴导入」→ 可滚表单（`SoftCard` 规则卡，读 `ArticleQualityPolicy.forImported`）→ 吸底 `import_action_bar` 内唯一 `Button("导入文章", enabled = 标题/正文非空 && !submitting, AppShape.Button)`。全屏 `Button(` = 1、`TextButton(` = 0、`RoundedCornerShape(` = 0、`contentDescription = ` = 0。
- **查词**：`SoftCard` 包 `TextField(global_search_input)`（`leadingIcon` = `AppGlyph(AppIcon.Search)`、`trailingIcon` = `TextButton("清除")`、聚焦指示与光标取 `appDomain(AppDomain.Library).deep`）+ 恒文案 `Button(global_search_submit, "搜索")` + `when(state)` 五态；历史行与结果行改 `BareRow`（结果行显式传 `trailingTag`），`›` 方向字形换 `AppIcon.ChevronRight`，Mint 桥 40 读点 → 0，旧锚 `global_search_back` 退役。

## 3. 验证记录

### 3.1 全量门禁（Step 1）

```
& $g :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin `
    verifyThirdPartyNotices verifyAcTraceabilityMatrix --offline --no-daemon --no-build-cache
```

结果：**BUILD SUCCESSFUL**

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest` | **1906 tests / 0 failures / 0 errors / 0 skipped**（259 个 suite xml 聚合实测） |
| `:app:assembleDebug` | 通过 |
| `:app:compileDebugAndroidTestKotlin` | 通过 |
| `verifyThirdPartyNotices` | `Validated 76 third-party notice entries.`（与批 2 一致，本批未引入依赖） |
| `verifyAcTraceabilityMatrix` | `AC matrix covered 56 ids with evidence.`（与批 2 一致） |

条数轨迹（每步都是实跑数）：

| 里程碑 | tests | 增量 |
|---|---|---|
| 批 2 收口 | 1871 | — |
| Task 1 后 | 1878 | +7 |
| Task 2 后 | 1885 | +7 |
| Task 3 后 | 1892 | +7 |
| Task 4 后 | 1899 | +7 |
| Task 5 后 | 1906 | +7 |

### 3.2 模拟器 connected（只 `emulator-5574`，`am instrument` 逐类跑）

九条基线全绿（**未使用** `connectedDebugAndroidTest`）：

| 类 | 结果 |
|---|---|
| `ReadingFeedScreenTest`（含两条像素腿） | OK (9 tests) |
| `ReadingAccessScreenTest` | OK (18 tests) |
| `ReadingHistoryScreenTest` | OK (8 tests) |
| `ArticleReadingScreenTest` | OK (25 tests) |
| `ArticleImportScreenTest` | OK (7 tests) |
| `GlobalVocabularySearchScreenTest` | OK (13 tests) |
| `AppScreenTest`（含 `:995` 返回位） | OK (50 tests) |
| `WordCardScreenTest`（回归） | OK (18 tests) |
| `TodayPlanScreenTest`（回归） | OK (24 tests) |

另：Task 3 期间 `ArticlePronunciationControlsTest` 5/5 绿。

### 3.3 大字号专项（Step 3）

`settings put system font_scale 2.0` → 五屏相关类全部重跑：

| 类 | fontScale 2.0 结果 |
|---|---|
| `ReadingFeedScreenTest` | OK (9) |
| `ReadingHistoryScreenTest` | OK (8) |
| `GlobalVocabularySearchScreenTest` | OK (13) |
| `ArticleReadingScreenTest` | OK (25) |
| `ArticleImportScreenTest` | OK (7) |

三处 `LazyColumn`（外刊/历史/查词结果）滚动断言、吸底条不遮正文末行、导入表单可滚且提交条不遮输入框，全部在 2.0 下通过；跑完恢复 `font_scale 1.0`（已复核 `= 1.0`）。

### 3.4 真机目视（Step 4，`bf353dda`，只 `adb install -r` + 手看，未 instrument）

安装 `app-debug.apk`（`Success`），逐屏看：

| 屏 | 结论 |
|---|---|
| 今日学习（回归） | 一屏一枚主操作（进度卡），查词入口为整行搜索条，无卡片汤 |
| 每日阅读（Task 1） | 未解锁态 = 一句状态 + 一句去向 + **单枚**主操作「查看本地阅读历史（0）」，无按钮阵列 |
| 阅读历史（Task 2） | 共享页头（‹ + 阅读历史），空态一句说明，无「第 1 版」字样 |
| 查词 · 空历史（Task 5） | 页头「查词」+ 软卡输入框（放大镜 + 「清除」）+ 整行「搜索」CTA |
| 查词 · 提交后 | 「找到 30 个结果」+ 裸行 `ability /əˈbɪləti/ · 能力，本领；才能，才干 · 来源：六级英语词汇` + `ChevronRight`，行间仅发丝线 |
| 查词 · 重进 | 节标题「最近搜索」+「清空」，历史行 `ability … 1 次 ›`（`"N 次"` 生效） |
| 文章阅读 / 粘贴导入 | 真机**不可达**（需先解锁/生成文章、或阅读入口 Ready 态），版式证据取 connected：`ArticleReadingScreenTest` 25 条（含像素腿）+ `ArticleImportScreenTest` 7 条 |

焦点指示与光标在输入框上呈 Library 域深紫（`appDomain(AppDomain.Library).deep`），与「域色只用于聚焦指示」的口径一致。

## 4. 改期望值清单（旧值 → 新值 + 依据）

> 只登记**判据本身变了**的地方；新增判据见 §7。本机无 `git`（不在 PATH），无法出 diff，故依据取各测试文件内的同批留档注释（每条改动都写在被改断言的上方）。

| # | 文件 | 旧值 | 新值 | 依据 |
|---|---|---|---|---|
| 1 | `SharedComponentUniquenessRatchetTest.headerSeed` | 页头族 seed 含 `ReadingHistoryScreen.kt` / `ArticleReadingScreen.kt` / `ArticleImportScreen.kt` / `GlobalVocabularySearchScreen.kt` | 四行全部摘除（seed 现存 9 行） | 四屏 headline/`contentDescription = ` 读点归零，逐屏由各自契约第 1 条钉住 |
| 2 | `SharedComponentUniquenessRatchetTest.headerSeedRetentionReason` | 「`GlobalVocabularySearchScreen.kt` 第二处、`LicensesScreen.kt` 第一处：`›`/`‹` 是方向字形，借 headline 档」 | 只留 `LicensesScreen.kt`；另加一段说明查词两枚方向字形已换 `AppIcon.ChevronRight`、读点归零 | 实现改动（方向字形不再借 headline 档）；理由文本与 seed 必须同步，否则留档撒谎 |
| 3 | `ContainerContractTest.screens` | 7 行 | 12 行（+`ReadingFeedScreen.kt` / `ReadingHistoryScreen.kt` / `ArticleReadingScreen.kt` / `ArticleImportScreen.kt` / `GlobalVocabularySearchScreen.kt`） | 本批五屏纳入容器三档判据；三条几何判据（`BareRowDividerIndent = 52.dp`、体内引用、三档各恰一份定义）逐字未动 |
| 4 | `components/Containers.kt` `BareRow` | 9 参（…, `titleMaxLines`, `onClick`） | 10 参（新增 `trailingTag: String? = null`，挂到尾部 `Text` 的 `Modifier.testTag`） | 查词历史行要把「N 次」当独立节点读（关闭次数显示时 connected 断言它不存在）；默认 `null` ⇒ 既有调用点标签面与树形零变化，属「只增不改」 |
| 5 | `GlassPanelInkPairingContractTest.expectedPanels` | 含文章页面板账 | 摘除该行 | 面板改 `ModalBottomSheet`，墨色对由 `ArticleReadingLayoutContractTest` 第 3 条接管 |
| 6 | `ArticleReadingScreenTest`（模式区） | 「全译对照」开关 + 旧标识符 `article_mode_*` 成对断言 | 两枚 chip + 同一行的开关，旧标识符断言为 0 | 版式收口：模式只留两枚 chip（`article_mode_full_translation` 仍在）；旧标识符为 0 是防「新旧并存」 |
| 7 | `ArticleImportScreenTest`（返回位） | `onNodeWithText("← 返回上一层")` 可点 + `import_back` tag | `onNodeWithTag("app_screen_header")` + `onNodeWithContentDescription("返回").performClick()`，并新增「← 返回上一层」为 0 | 共享页头的返回位固定「返回」+ `app_screen_header`，无参数化返回文案；旧锚随自写按钮退役 |
| 8 | `GlobalVocabularySearchScreenTest`（返回位） | `backPointerSaysUpOneLevel`：断言「← 返回上一层」`Text` 与同名可及名存在 | `backSlotIsTheSharedHeaderAndStillWorks`：页头在树上 + 按内容描述「返回」点击 + 断言 `onBack` 触发 1 次 | 同上（页头契约） |
| 9 | `GlobalVocabularySearchScreenTest`（提交文案） | 空输入读「查看最近搜索」、非空读「全量搜索」（同一枚按钮两个身份） | 恒「搜索」，两条用例分别更名 `blank_query_keeps_the_submit_action_available_and_the_label_never_changes` / `non_blank_query_keeps_the_same_submit_label` | 「一屏一主操作」+「文案不欺骗」；见 §6 让步 1 |
| 10 | `GlobalVocabularySearchScreenTest`（旧文案哨兵） | `non_blank_query_never_says_recent_searches`（只钉「查看最近搜索」为 0） | `the_old_two_state_submit_labels_are_gone`（「查看最近搜索」与「全量搜索」**都**为 0） | 旧文案有两套，只钉一套等于漏一半 |
| 11 | `GlobalVocabularySearchScreenTest`（返回旧文案） | `backPointerNeverPointsAtTheAppHome`（只钉「返回首页」为 0） | `theOldBackCopyIsGone`（「返回首页」与「← 返回上一层」都为 0，文本与可及名各一条） | 返回位换形后旧文案整条退役，两套都得钉 |
| 12 | `GlobalVocabularySearchScreen.kt` Mint 桥 | 40 读点（`MintPrimaryDark` 10 / `MintTextMuted` 10 / `MintPrimary` 9 / `MintSurface` 5 / `MintOutline` 4 / `MintBackground` 2） | 0（`appColors()` 取 `AppColorSet`） | 批 2/3 统一令牌口径；第 6 条判据同批把 `MaterialTheme.typography` / `border(` / `RoundedCornerShape(` / `contentDescription = ` 一并钉 0 |

**Task 1 / Task 2 未改任何既有断言**：两屏的 connected 测试文件内没有任何旧→新改值的留档注释（`ReadingFeedScreenTest.kt` / `ReadingHistoryScreenTest.kt` 中 `批 3` 只见于新增判据），本批对它们只做**新增**（§7）。

### 4-8 关于 `GlobalSearchLayoutContractTest` 第 7 条先绿

第 7 条钉的是 **`GlobalSearchResultRow` 不得被强迁 `WordRow`**（R-20C.1 裁决）：定义与调用各恰 1、五枚信息（lemma / IPA / 释义 / 来源词书 / 次数）不缺。它守的是「不做某个重构」，与旧实现在这一点上本就一致，所以 RED 阶段即绿——这是判据性质决定，不是漏红。另 6 条（页头、提交文案、历史裸行、无卡壳、清空入口、令牌读点）在 RED 时全红，失败消息为：`AppScreenHeader(` 期望 1 实 0（:73）、「查看最近搜索」期望 0 实 1（:123）、窗口内 `BareRow(` 期望 ≥1 实 0（:166）、`border(` 期望 0 实 2（:203）、「清空」期望 1 实 0（:259）、`MintPrimaryDark` 期望 0 实 10（:284）。

## 5. 页头族 seed 摘四行的理由

摘行不是「补一行 `AppScreenHeader(` 就准删」：`each_family_offender_set_equals_its_seed_exactly` 做双向判，摘行必须与「该文件 `MaterialTheme.typography.headline` 读点归零」在同一次跑里同时成立。

| 文件 | 摘行理由 |
|---|---|
| `ReadingHistoryScreen.kt`（Task 2） | 自写「返回」实底按钮 + `headlineSmall` 标题 → 共享 `AppScreenHeader(阅读历史, onBack)`；契约钉 `MaterialTheme.typography` 为 0 |
| `ArticleReadingScreen.kt`（Task 3） | 自写「返回」+「发音」两枚实底 `Button` 与 `headlineSmall` 标题 → 页头承担返回与发音（正文标题另留 `article_title`）；契约钉 0 读点与「`Text("返回")` 必须为 0」 |
| `ArticleImportScreen.kt`（Task 4） | 自写 `TextButton("← 返回上一层")` + `headlineSmall` → `AppScreenHeader(粘贴导入, onBack)`；契约钉 0 读点与「← 返回上一层」为 0 |
| `GlobalVocabularySearchScreen.kt`（Task 5） | 自写「← 返回上一层」可点文字 + `headlineSmall` 标题 + 两枚 `›` 方向字形（借 headline 档凑字号）→ `AppScreenHeader(查词, onBack)` + `AppIcon.ChevronRight`；契约钉 0 读点、「← 返回上一层」为 0、`border(` 为 0 |

`LicensesScreen.kt`（`‹` 方向字形）与 `AiFeatureScreen.kt`（能力卡标题）**仍留档**：它们的 headline 读点不是页头，换 `AppScreenHeader` 会改字形/语义。`GlobalVocabularySearchScreen.kt` 在**词行族** seed 里仍在册（R-20C.1：结果行五枚信息塞不进 `WordRow` 四槽），与页头族无关——两族的留档各自成立。

## 6. 三处规范性让步（须显式登记）

1. **查词按钮取「恒『搜索』」而非 spec 字面「有输入时按钮为『查词』」**：同一枚 `submit` 不该因输入改身份（现状是在「查看最近搜索 / 全量搜索」间翻转，两套都删）。若审核席要求严格照字面，改的是 `GlobalSearchLayoutContractTest` 第 2 条的一个字面量 + 实现一行。
2. **篇幅让档**（Task 1）：外刊列表的篇幅显示按「扫读优先」让档，详细信息回到文章页（见前置《与规范的三处对账》）。
3. **译文控件合并形态 + 来源块不删域名与链接**（Task 3）：模式区两枚 chip + 同行开关取代「全译对照」独立区；来源块只重排版式，`attributionText` 与 URL 原样保留（去版权/删信息来源属功能删减，B3 不做）。

## 7. 净增判据统计

| 来源 | 条数 | 说明 |
|---|---|---|
| 五枚新契约 | +35 | `ReadingFeedLayoutContractTest` 7、`ReadingHistoryLayoutContractTest` 7、`ArticleReadingLayoutContractTest` 7、`ArticleImportLayoutContractTest` 7、`GlobalSearchLayoutContractTest` 7 |
| `ArticleImportScreenTest` | +2（→7） | 新增「规则卡在正文框上方」「空表单禁用、两字段齐后可用」，返回位改按内容描述 |
| `GlobalVocabularySearchScreenTest` | +1（→13） | 新增「非空输入仍是同一句『搜索』」，另两处旧文案哨兵扩面（不算新增条数） |
| **JVM 合计** | **1871 → 1906（+35）** | 与「5 × 7」完全吻合；connected 侧另行统计 |
| 删除判据 | **0** | 无 `assertTrue(true)`、无放宽到 `>=`、无删断言 |

## 8. 未执行项

- **git 提交**：Task 1–5 的 Step 5 全部登记为未执行——本机 `git` 不在 PATH，无法暂存/提交/出 diff。
- **文章阅读与粘贴导入的真机目视**：真机上两屏不可达（文章需先解锁/生成，导入入口在阅读入口 Ready 态的「更多」组内），改用 connected 断言 + 契约判据覆盖；若要有真机像素证据，需先造一篇本地文章或临时插入一个 Ready 态测试入口（会动数据/入口，B3 不做）。

## 9. 遗留议题

1. **查词首次进入显示「未找到匹配词卡」**：`GlobalVocabularySearchViewModel:277` 在历史为空时把状态置 `Empty`，于是 `Idle`（提示语）只在加载完成前短暂可见。属 ViewModel 语义（数据层），B3 明令不动；若审核席认为「空历史首屏应读作提示而非未找到」，应作为数据层任务另开。
2. **`BareRow` 参数化债务**：本批给它加了 `titleMaxLines`（批 3 Task 1）与 `trailingTag`（Task 5）两枚可选参数。都在「只增不改 + 默认值保持既有调用点零变化」的边界内，但两枚都是**为单个屏加的**——若后续再出现第三枚，应收口成一个 `trailingContent`/`RowSlots` 形态的重构，而不是继续加参数。
3. **`WordRow` vs `GlobalSearchResultRow` 的双实现**：R-20C.1 已裁决不合并，但它意味着词行族长期有两份布局代码；任何一次视觉口径调整都要改两处（`SharedComponentUniquenessRatchetTest` 的词行族 seed 会持续提醒这件事）。
4. **`headerSeedRetentionReason` 已改成「叙述历史」**：它现在同时承担「为什么这几屏还没迁」与「哪几屏本批迁完了」两种信息，随批次数增长会继续变长；下一批建议把「已摘行」的理由整体挪进 md 文档，seed 只留当前留档理由。
