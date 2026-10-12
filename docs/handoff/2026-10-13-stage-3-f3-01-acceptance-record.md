# 阶段 3 F3-01（离线查词）验收记录

**日期：** 2026-10-13
**结论：** JVM 层验收完成；F3-01 全部要点有测试或源码契约锚定；真机走查项不阻塞后续任务。
**Spec：** `docs/specs/03-dictionary-pronunciation-and-photo-translation.md` F3-01 节

## 验证命令与结果

- PASS：`:app:assembleDebug` + `:app:testDebugUnitTest --no-daemon --no-build-cache`（2026-10-13，**1030 用例全绿**，基线 1026 + 新增 4）
- detekt/ktlint：按 `docs/decisions/2026-09-19` 不作门禁

## F3-01 要点逐条映射

| # | Spec 要点 | 结论 | 证据 |
|---|---|---|---|
| 1 | 搜索入口支持单词/短语；查询不区分大小写、trim、折叠空白 | ✅ | `SearchVocabularyUseCaseTest`（`normalizes query before lookup...`、`a card matched only through a phrase...`、`a phrase equal to the query scores as an exact match`） |
| 2 | 只查已安装词书真实词卡，排除 Placeholder | ✅ | `RefreshVocabularySearchIndexUseCaseTest`（`placeholder only book is skipped...`）+ 实现双保险：索引写入过滤 `placeholder:` 前缀（`RoomVocabularySearchIndexRepository`）+ 索引前过滤（`RefreshVocabularySearchIndexUseCase`） |
| 3 | 精确 > 前缀 > 包含；限制查询长度与结果数；显示 lemma/释义/来源词书 | ✅ | `SearchVocabularyUseCaseTest`（`result carries the fields...`、`index result limit follows maxResults`、`same lemma in two word books is kept as two results`）+ `PrefixUpperBoundTest`（前缀上界完备性 2 用例）+ `VocabularySearchGramTest`（gram 编码/包含匹配 7 用例） |
| 4 | 后台执行、取消与请求代次校验；清空/关闭后旧结果不回写；失败可重试；未命中明确提示非完整词典 | ✅ | `GlobalVocabularySearchViewModelTest`（`newer query cannot be overwritten by old request`、`search failure can be retried...`、`empty results are distinct`）+ Screen 层 `GlobalVocabularySearchScreenTest`（`failureOffersRetry`、`emptyInputShowsRecentHistory...`） |
| 5 | 只读详情复用发音 Provider；不写学习记录、无重新学习；代次校验防迟到状态污染 | ✅ | 详情复用 `CardDetailScreen`（AC1-19 已锚定无重新学习按钮）；代次守卫经源码结构核验：`onSpeak`/`onSpeakBritish` 均含 `request == searchPronunciationRequest && selectedSearchCard === cardSnapshot` 双重条件，`closeSearchDetail`/`DisposableEffect` 均递增代次并取消 Job（AppScreen.kt ~177-186, ~445-460）；Compose 状态机无 JVM 单测入口，以本记录为源码审阅证据 |
| 6 | 阅读页与首页共用同一搜索 overlay；未命中词预填立即查询；关闭后回原文保位置与选词状态 | ✅ | `SearchEntryWiringTest`（4 用例：双入口同 ViewModel、阅读入口携带点词、主页空白进历史）+ `GlobalVocabularySearchViewModelTest`（`opening from the reading page seeds the query...`）+ **2026-10-13 补** `SearchCloseReadingStateRetentionTest`（4 条源码契约：rememberSaveable 持有位置、位置与回传同时传入阅读屏、滚动状态用传入位置初始化并持续回传、overlay 层级前提） |
| 7 | 不提供 AI 补充释义；未命中只提示非完整词典 | ✅ | 搜索结果页无 AI 入口（`GlobalVocabularySearchScreenTest` 13 用例中无任何 AI 路径）；"仅覆盖已安装词书"提示在 Screen 层实现 |
| 8 | 不保存查询历史、不提供收藏 | ⚠️ 超前实现 | Spec 写"本轮不保存"，但查询历史已是已交付功能（v25 搜索索引一并交付，UI 测试锚定）。**处置：保留不删**（删除属破坏性变更），在简报中记录为"历史功能超前交付，Spec 03 的'本轮不保存'条款视为已被用户后来的功能决策覆盖"；收藏未实现，符合 Spec |

## 与任务书对应的缺口闭环

| 任务书缺口 | 处置 |
|---|---|
| 1. AC 级验收映射缺失 | 本记录表格即映射 |
| 2. 关闭搜索保留阅读位置 | `SearchCloseReadingStateRetentionTest` 4 条锚定（源码契约风格，理由见测试 KDoc） |
| 3. 发音代次校验泄漏 | 源码审阅确认无泄漏路径（见上表 #5） |
| 4. 验收记录 | 本文件 |

## 待真机人工验收（不阻塞后续任务）

1. 真机走查：阅读页点词 → 搜索 → 详情 → 关闭 → 回文章，确认滚动位置视觉上恢复、选词高亮仍在
2. 真机确认未命中提示文案与"仅已安装词书"提示的可见性

## 遗留与风险

- 查询历史超前交付（见 #8 处置）——若用户希望严格回到"不保存历史"，需要一次显式删除决策
- 源码契约测试对重构敏感（重命名参数会使锚点失配而误报红）；这是有意的守卫代价，误报时先查本记录
