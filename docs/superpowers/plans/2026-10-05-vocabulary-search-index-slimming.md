# 词条搜索索引字段瘦身实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `vocabulary_search_index` 每行存储的「整张词卡 JSON 快照」删掉，只保留查询与结果列表展示必需的列；点开某条结果时按 `cardId` 回源取完整词卡。

**Architecture:** 索引回归它本来的职责——**只做查询索引，不做数据副本**。查询路径仍然完全不碰词书包（`SearchVocabularyUseCase` 仍不依赖 `WordCardSource`）；只有「用户点开的那一条」才回源解析一次词书包，为此新增独立的 `OpenVocabularySearchResultUseCase`。词卡详情所需的释义分组、例句、派生词、短语、近义词、配图因此仍在详情页完整呈现。

**Tech Stack:** Kotlin、Room、Hilt、Coroutines、JUnit 5（`app/src/test`）、JUnit 4 + Compose（`app/src/androidTest`）、Room migration test。

**Spec:** `docs/superpowers/specs/2026-10-04-global-vocabulary-search-design.md`（§3.2 结果项、§5.1 全量搜索用例、§5.3 生产接线）
**前置计划:** `docs/superpowers/plans/2026-10-04-vocabulary-search-index.md`（其「已知性能边界」的收敛方向 ① 即本计划）

## 立论依据（真机实测，非估算）

对真机用户库副本（`outputs/verification-vocab-search-index-20261004/db-after-index-build`，`user_version = 24`）逐列统计：

| 列 | 合计字节 | 每行 |
|---|---|---|
| `cardJson` | 26,937,586 | 594.8 |
| `normalizedPhrases` | 1,346,450 | 29.7 |
| `cardId` | 819,995 | 18.1 |
| `dataVersion` | 724,640 | 16.0 |
| `wordBookId` | 476,783 | 10.5 |
| `lemma` / `normalizedLemma` | 297,922 各 | 6.6 各 |
| `wordBookName` | 251,015 | 5.5 |

- 行数 45,290；主表 dbstat payload 37,029,094 B / 10,230 页；三个索引合计 993 页。
- `cardJson` 占索引文本量的 **86.5%**。这些内容本就存在于 `assets` 词书包，等于复制了一份进数据库。
- 用户库因此从 270 KB 涨到 46,317,568 B。

**为什么必须改成「回源取卡」而不是「搜索时批量补全」**：词书包是**整册单文件**（`cet4/book.json` 3,916,130 B / 4,308 张卡），不存在「按 cardId 定位一张卡」的读法。一次搜索的结果常跨多本词书（`ability` 精确命中 9 本），若在搜索时批量补全，就要重新解析命中的每一册——正是索引要消除的开销。因此只在**用户点开的那一条**上回源。

## 实际落地的接口（实施后以此为准）

```kotlin
data class VocabularySearchIndexEntity(
    val wordBookId: String, val cardId: String,
    val wordBookName: String, val dataVersion: String,
    val lemma: String, val normalizedLemma: String,
    val normalizedPhrases: String,
    val ipa: String, val meaningZh: String,   // 结果列表要显示的仅这几列
)   // 主键仍为 (wordBookId, cardId)

data class VocabularySearchIndexEntry(
    val wordBookId: String, val cardId: String, val wordBookName: String,
    val lemma: String, val ipa: String, val meaningZh: String,
    /** 归一化检索词项：第一个是 lemma，其余是短语。评分只依赖它。 */
    val normalizedTerms: List<String>,
)

data class SearchVocabularyResult(
    val wordBookId: String, val cardId: String, val wordBookName: String,
    val lemma: String, val ipa: String, val meaningZh: String,
)   // 结果**摘要**，不再是完整 WordCard
```

`OpenVocabularySearchResultUseCase(cards: WordCardSource)`：`suspend operator fun invoke(result: SearchVocabularyResult): WordCard?`。
读不到返回 `null`（词书已删除/包损坏），调用方必须当成「这个词卡暂时打不开」，不得当成空卡片。

`MIGRATION_24_25`：`DROP TABLE` + 重建空表 + 重建两个索引。**不做数据搬运**——旧行的
`ipa`/`meaningZh` 只存在于 `cardJson` 内部，搬它们要么解析 JSON（把迁移安全性押在平台的 JSON1 上），
要么再复制一遍快照。索引是派生数据，`RefreshVocabularySearchIndexUseCase` 会按 `dataVersion`
在下一次刷新时整册重建，重建后与原状等价。

## Global Constraints

- 查询阶段仍不得调用 `WordCardSource.cardIds()` / `cards()`；`SearchVocabularyUseCase` 构造器里
  不得出现 `WordCardSource`（既有反射红线测试继续生效）。
- 索引不改变学习事件、FSRS、今日计划或搜索历史语义。
- 现有 Room v24 数据必须保留；索引自身允许重建。
- 所有新行为先写测试并确认 RED，再写最小实现；关键语义做可变红验证。
- 真机只允许 `adb install -r -t` 与 `am instrument`；禁止 `connectedDebugAndroidTest`、卸载和清除用户数据。
- 不提交、不推送，除非用户另行明确要求。

---

### Task 1: 索引模型与仓储瘦身（JVM）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/entity/VocabularySearchIndexEntity.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/VocabularySearchIndex.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/RoomVocabularySearchIndexRepository.kt`
- Modify: `app/src/test/java/com/example/englishlearning/learning/VocabularySearchIndexTest.kt`

- [ ] **Step 1: 改 RED 测试**：`VocabularySearchIndexEntry` 断言改为摘要字段（`lemma`/`ipa`/`meaningZh`），
      并新增 `normalizedTerms` 首项为 lemma、其余为短语的断言。
- [ ] **Step 2: 确认 RED**（focused JVM）。
- [ ] **Step 3: 实现**：entity 去 `cardJson` 增 `ipa`/`meaningZh`；仓储不再序列化 JSON，
      `normalizedPhrases` 用 `\u0001` 拆回词项；ranker 去重键改 `entry.cardId`。
- [ ] **Step 4: GREEN + 变异**：把 `normalizedTerms` 只放 lemma（丢掉短语）→ 短语命中断言必须变红；恢复。

### Task 2: 迁移 v24→v25

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseMigrationTest.kt`

- [ ] **Step 1: 加 RED 迁移用例**：从 v24 fixture 打开 v25，断言 `vocabulary_search_index` 列集合恰为
      瘦身后 9 列、无 `cardJson`、既有 `vocabulary_search_history` / `learning_settings` 数据保留。
- [ ] **Step 2: 确认 RED**（instrumentation 需先出 APK；先做 JVM 编译检查）。
- [ ] **Step 3: 实现 `MIGRATION_24_25` 并加入 `MIGRATIONS`**，导出 `25.json`。
- [ ] **Step 4: GREEN + 变异**：把 `MIGRATION_24_25` 从数组里去掉 → 迁移用例必须红；恢复。

### Task 3: 搜索用例输出摘要

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/learning/SearchVocabularyUseCase.kt`
- Modify: `app/src/test/java/com/example/englishlearning/learning/SearchVocabularyUseCaseTest.kt`

- [ ] **Step 1: 改 RED 测试**：断言 `SearchVocabularyResult` 带 `cardId`/`lemma`/`ipa`/`meaningZh`/`wordBookName`，
      排序与 `maxResults` 语义不变，反射红线测试保留。
- [ ] **Step 2: 确认 RED**。
- [ ] **Step 3: 实现**：`matchScore` 改为对 `entry.normalizedTerms` 打分（与旧规则逐条等价）。
- [ ] **Step 4: GREEN + 变异**：等级顺序对调 → 「精确优先」断言必须红；恢复。

### Task 4: 点开结果回源取完整词卡

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/OpenVocabularySearchResultUseCase.kt`
- Create: `app/src/test/java/com/example/englishlearning/learning/OpenVocabularySearchResultUseCaseTest.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`

- [ ] **Step 1: 写 RED 测试**：只请求一条 `cardId`；返回的卡带齐 `senses`/`example`/`phrases`；
      源里没有该 id 时返回 `null`；源抛异常时不崩、返回 `null`（由调用方决定文案）。
- [ ] **Step 2: 确认 RED**。
- [ ] **Step 3: 实现最小用例 + Hilt provider**。
- [ ] **Step 4: GREEN + 变异**：让用例返回只带 cardId 的空壳卡 → `senses` 断言必须红；恢复。

### Task 5: ViewModel 暴露「已打开的词卡」

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/GlobalVocabularySearchViewModel.kt`
- Modify: `app/src/test/java/com/example/englishlearning/ui/GlobalVocabularySearchViewModelTest.kt`

- [ ] **Step 1: 写 RED 测试**：`open(item)` 成功后 `openedCard` 是完整卡；`consumeOpenedCard()` 之后为 `null`
      （否则第二次点同一张卡「点了没反应」）；读不到时进入 `Ready.openFailed = true` 且已有结果不丢；
      连续点两条时只有后一条生效（旧任务不得覆盖新选择）。
- [ ] **Step 2: 确认 RED**。
- [ ] **Step 3: 实现**（`openJob` + `openGeneration` 守卫，风格对齐既有 `generation`）。
- [ ] **Step 4: GREEN + 变异**：去掉 `consumeOpenedCard()` 的置空 → 「重复点同一张卡」断言必须红；恢复。

### Task 6: 屏幕与生产接线

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/GlobalVocabularySearchScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/GlobalVocabularySearchScreenTest.kt`

- [ ] **Step 1: 改 RED 测试**：结果行改吃摘要字段；新增「打开失败提示」用例；主页仍不得出现「浏览词书」。
- [ ] **Step 2: 确认 RED**（`assembleDebugAndroidTest` 编译期即会红）。
- [ ] **Step 3: 实现**：
  - 屏幕：结果行读摘要字段；`Ready.openFailed` 时在结果上方显示提示（`global_search_open_failed`）。
  - `AppScreen`：`LaunchedEffect(openedCard)` 把完整卡交给 `selectedSearchCard`，随后 `consumeOpenedCard()`；
    `onSelect` 改为 `globalSearch.open(item)`；`detailSource` 由裸 `String` 改为私有枚举，
    **并修掉跨列表导航缺陷**：主页搜索的「下一个」此前误用旧词书搜索页的结果列表。
- [ ] **Step 4: GREEN + 变异**：把 `onSelect` 换回直接 `selectedSearchCard = ...`（摘要当完整卡）→
      「详情显示完整释义分组」断言必须红；恢复。

### Task 7: 全量回归、APK 与真机取证

**Files:**
- Create: `outputs/index-slimming-20261005/REPORT.md`
- Modify: `D:/workbjddy-project/手机学习英语软件/.workbuddy/memory/2026-10-05.md`

- [ ] **Step 1: 边界检查**：`git status --short`；不触碰受保护文件 `app/gradle.lockfile`、`core/error/AppError.kt`、`ui/AppViewModel.kt`。
- [ ] **Step 2: JVM 全量回归**（后台）。
- [ ] **Step 3: `assembleDebug` + `assembleDebugAndroidTest`**（后台，`--continue`）。
- [ ] **Step 4: 真机 focused**：先读三件套基线 → `install -r -t` 两个 APK → `am instrument` 迁移 + 主页搜索 focused → 再读三件套逐字节比对。
- [ ] **Step 5: 报告**：命令、通过数、变异红灯、APK 路径、设备、三件套 MD5 前后值、数据库体积前后对比、未闭合项。

## 已知遗留（本计划不闭合，如实记录）

- `BundledWordBookSource.ensureInstalled` 每次读卡都调用 `WordBookPackageParser.parse(target)` 做一次
  **完整解析校验**，与紧随其后的 `WordBookPackageCache` 校验重复。瘦身后「点开结果」会走到这里，
  于是每次点开都多付一次整包解析（`cet4` 3.9 MB JSON）。`ImportedWordBookSource` 没有这个重复。
  处置方向：让 `cache.get(target) != null` 成为唯一可用性判据，去掉重复解析。
  **本计划不做**：该改动无法在合理成本内做出可变红断言（缓存与解析次数不可从外部观测），
  按「不做无法自证的改动」原则留待后续单独处理。
- 包含匹配仍是 `LIKE '%q%'` 表扫描，非 O(log n)；彻底索引化需 SQLite FTS5。
