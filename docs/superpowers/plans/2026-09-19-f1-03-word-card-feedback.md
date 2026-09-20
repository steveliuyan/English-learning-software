# 阶段 1 / F1-03：词卡与三档反馈 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付离线可用的「词卡展示 → 三档反馈提交 → 事件落库 → 排程推进」闭环，使每张计划卡在且仅在提交一次有效反馈后完成，并保证同一事件 ID 重放不重复计数、不二次调整间隔。

**Architecture:** 事件日志（`learning_events`）是排程权威，`card_review_states` 只是可重建的派生缓存。提交反馈经 `SubmitCardFeedbackUseCase` 编排「查重 → 读派生状态 → 调度器决策 → 原子追加事件 + 推进派生状态」，幂等由 `eventId` 主键 + `INSERT OR IGNORE` 保证，而非应用层先查后写。词卡**内容**经 `WordCardSource` 接缝注入，V1 使用明确标注的应用自撰占位内容，与计划卡 ID 来源解耦，便于日后替换为许可核验后的数据集。

**Tech Stack:** Kotlin、Jetpack Compose(Material3)、Room v5、Hilt、JUnit 5、Robolectric、kotlinx-coroutines-test、Compose UI Test、detekt、ktlint。

**Spec:** `docs/specs/01-vocabulary-learning-and-review.md`（F1-03）、`docs/specs/00-foundation-and-architecture.md`
**决策记录:** `docs/decisions/2026-09-19-learning-event-store-and-v1-scheduler.md`、`docs/decisions/2026-09-19-placeholder-word-card-content.md`
**上游:** F1-01 词书选择（提交 `564a8c7`）、F1-02 今日计划 UI（提交 `0b86e9c`）

## Global Constraints

- 三档映射为 V1 固定规则，不得按卡或按词书调整：`不认识 → Again`、`模糊 → Hard`、`认识 → Good`。
- 每张计划卡提交一次有效反馈即完成；同一事件 ID 重放不得重复计数，也不得二次调整间隔。
- 事件不可变：不提供 UPDATE/DELETE 事件的 DAO 方法；算法升级只重算未来 `nextReviewAt`。事件必须记录算法版本、参数版本、评分、发生时间、调度前后状态。
- V1 不提供撤销已提交反馈，避免排程歧义。
- 用户可以随时离开学习流程；未提交反馈的卡片保持未完成，不得被隐式标记完成。
- 学习反馈必须先成功写入本地；存储失败必须返回可行动错误，不得静默吞掉或假装成功。
- 词卡必须展示单词、IPA、词性、中文释义；例句与词形变化仅在内容源提供时渲染。
- **许可约束**：未完成逐项许可核验的词书、词典、音频或模型不得打包。本任务不引入任何第三方词条正文或中文释义；占位内容为应用自撰，须在 `docs/third-party-notices.md` 登记并明确标注发布前必须替换。
- 不提前实现：FSRS 调度算法、网络/AI、TTS/OCR、提醒、备份迁移、F1-04 的解锁判定与解释性完成状态、「重建当天计划」入口。
- 每一项实现先写测试并确认失败，再写最小实现使其通过。

---

## Planned File Structure

- `docs/decisions/2026-09-19-learning-event-store-and-v1-scheduler.md`：事件表 + V1 调度器决策（已落地）。
- `docs/decisions/2026-09-19-placeholder-word-card-content.md`：占位词卡内容与许可边界决策。
- `app/src/main/java/com/example/englishlearning/learning/domain/CardFeedback.kt`：用户可见三档反馈与固定映射。
- `app/src/main/java/com/example/englishlearning/learning/domain/LearningEvent.kt`：不可变事件与派生卡片状态。
- `app/src/main/java/com/example/englishlearning/learning/domain/V1ReviewScheduler.kt`：FSRS 接入前的确定性调度器。
- `app/src/main/java/com/example/englishlearning/learning/domain/WordCard.kt`：词卡展示模型。
- `app/src/main/java/com/example/englishlearning/learning/WordCardSource.kt`：词卡内容源接缝。
- `app/src/main/java/com/example/englishlearning/learning/PlaceholderWordCardSource.kt`：应用自撰占位内容实现。
- `app/src/main/java/com/example/englishlearning/core/storage/entity/{LearningEventEntity,CardReviewStateEntity}.kt`：Room v5 两张表。
- `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt`：仅追加的 DAO。
- `app/src/main/java/com/example/englishlearning/learning/{LearningEventRepository,RoomLearningEventRepository}.kt`：事件日志端口与 Room 实现。
- `app/src/main/java/com/example/englishlearning/learning/SubmitCardFeedbackUseCase.kt`：一次有效反馈的提交编排。
- `app/src/main/java/com/example/englishlearning/ui/WordCardScreen.kt`：词卡与三档反馈界面。
- `app/src/main/java/com/example/englishlearning/ui/WordCardViewModel.kt`：学习会话状态机。
- `app/src/main/java/com/example/englishlearning/di/AppModule.kt`：唯一组装根，接线事件仓库、用例与内容源。
- `app/src/test/java/com/example/englishlearning/learning/`：领域/存储/用例单测。
- `app/src/test/java/com/example/englishlearning/ui/`：会话状态机与 Compose 语义测试。

---

## Task 1: 词卡内容源接缝与占位内容

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/WordCardSource.kt`
- Create: `app/src/main/java/com/example/englishlearning/learning/PlaceholderWordCardSource.kt`
- Create: `docs/decisions/2026-09-20-placeholder-word-card-content.md`
- Create: `app/src/test/java/com/example/englishlearning/learning/PlaceholderWordCardSourceTest.kt`
- Modify: `docs/third-party-notices.md`

**Interfaces:**
- `interface WordCardSource { suspend fun cards(cardIds: List<String>): List<WordCard>; suspend fun cardIds(wordBookId: String): List<String> }`
- `class PlaceholderWordCardSource(private val entries: List<WordCard> = PLACEHOLDER_ENTRIES) : WordCardSource`

**验收：** 每个内置词书 ID 都能取到非空占位卡片；`cards()` 保持请求顺序、丢弃未知 ID；同一 lemma 在不同词书下卡 ID 不同（对应 AC1-06 的词书隔离）。

- [ ] 写 `PlaceholderWordCardSourceTest`（六本词书均有内容、顺序保持、未知 ID 丢弃、卡 ID 含词书面量）
- [ ] 运行并确认失败
- [ ] 实现 `WordCardSource` 与 `PlaceholderWordCardSource`（12 个基础词，自撰例句与中文释义）
- [ ] 运行并确认通过
- [ ] 写决策文档并在许可台账登记「应用自撰、无第三方许可依赖、发布前必须替换」

## Task 2: 计划卡来源改用事件存储（真实新词/到期划分）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/entity/CardReviewStateEntity.kt`（补 `(wordBookId, nextReviewAtEpochMillis)` 索引）
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`（`MIGRATION_4_5` 同步建索引）
- Modify: `app/src/main/java/com/example/englishlearning/learning/LearningEventRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/RoomLearningEventRepository.kt`
- Create: `app/src/main/java/com/example/englishlearning/learning/StoredPlanCardSource.kt`
- Create: `app/src/test/java/com/example/englishlearning/learning/StoredPlanCardSourceTest.kt`

**Interfaces:**
- `LearningEventRepository.dueCardIds(wordBookId, now): RepositoryResult<List<String>>`
- `LearningEventRepository.reviewedCardIds(wordBookId): RepositoryResult<List<String>>`
- `class StoredPlanCardSource(private val content: WordCardSource, private val events: LearningEventRepository) : PlanCardSource`
  - `dueCardIds` = 派生状态中 `nextReviewAt <= now` 的卡，按到期时间升序
  - `newCardIds` = 词书占位卡 ID 中尚未出现在派生状态的卡，取前 `limit` 个

**验收：** 新词与到期不重叠；已提交反馈的卡不再作为新词重复发放；到期查询按时间升序且只取已到期。

- [ ] 写 `StoredPlanCardSourceTest`（新词排除已复习、到期只含已到期并升序、存储失败向上传递）
- [ ] 运行并确认失败
- [ ] 实现 DAO 查询、仓库方法与 `StoredPlanCardSource`
- [ ] 运行并确认通过

## Task 3: 学习会话状态机

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/WordCardViewModel.kt`
- Create: `app/src/test/java/com/example/englishlearning/ui/WordCardViewModelTest.kt`

**Interfaces:**
- `data class WordCardUiState(val planId: String, val cards: List<WordCard>, val index: Int, val completedCardIds: Set<String>, val submitting: Boolean, val message: String?)`
- `class WordCardViewModel(...) : ViewModel`，暴露 `submit(feedback: CardFeedback)`、`skip()`、`load()`

**验收：** 提交成功后当前卡进入完成集合且推进到下一张；存储失败不推进、返回可行动错误；会话内同一卡不重复提交（已提交的卡不可再次提交）；离开再进入时已完成的卡仍完成、未提交的卡仍未完成。

- [ ] 写 `WordCardViewModelTest`（首卡展示、提交推进、失败不推进、重复提交被忽略、重入保留完成集合）
- [ ] 运行并确认失败
- [ ] 实现 `WordCardViewModel`（骨架屏 → 加载 → 词卡 → 完成提示的分支）
- [ ] 运行并确认通过

## Task 4: 词卡界面与三档反馈控件

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/WordCardScreen.kt`
- Create: `app/src/test/java/com/example/englishlearning/ui/WordCardScreenTest.kt`

**Interfaces:** `@Composable fun WordCardScreen(state: WordCardUiState, onSubmit: (CardFeedback) -> Unit, onSkip: () -> Unit)`

**验收：** 渲染 lemma / IPA / 词性 / 中文释义；三档按钮语义为 `不认识` / `模糊` / `认识` 且分别回传 `Unknown` / `Fuzzy` / `Known`；提供内容时渲染例句与词形变化，未提供时不渲染；不提供任何撤销入口；沿用薄荷绿视觉规范（Mint 令牌）与 48dp 最小点击区。

- [ ] 写 `WordCardScreenTest`（四要素渲染、三档语义与回传、可选字段条件渲染、无撤销入口）
- [ ] 运行并确认失败
- [ ] 实现 `WordCardScreen`
- [ ] 运行并确认通过

## Task 5: Hilt 接线与导航接入

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/test/java/com/example/englishlearning/ui/AppScreenTest.kt`

**验收：** 今日计划页可进入学习流程；学习页返回今日计划后状态一致；`PlanCardSource` 由 `StoredPlanCardSource` 提供而非空实现。

- [ ] 写 `AppScreenTest` 用例（进入学习、返回今日计划）
- [ ] 运行并确认失败
- [ ] 接线 DI 并加入导航状态
- [ ] 运行并确认通过

## Task 6: 全量验证与交付（待提交推送）

**Files:**
- Create: `docs/verification/f1-03/README.md` 与真机截图
- Create: `docs/decisions/2026-09-19-static-analysis-and-test-baseline.md`

**验收（已按实测修订）：** `:app:testDebugUnitTest` **不得超出基线失败集合**（基线 `0b86e9c` 为 54 tests / 11 failed，F1-03 最新复核为 90 tests / 8 failed，8 例全部是基线 11 例的子集）；`detekt`/`ktlintCheck` **不得引入新的失败类别**，新增项逐条处理或说明理由（基线 detekt 98 条、ktlint 610 条本身即失败，见决策记录）；真机完成「今日计划 → 词卡 → 三档反馈 → 杀进程重进 → 全部完成 → 返回」端到端验证并留截图与落库证据；代码提交并推送作为异地备份。

**修订依据：** `docs/decisions/2026-09-19-static-analysis-and-test-baseline.md`（原验收标准要求两个静态检查"通过"，实测在基线即不成立）。

- [x] 运行全量单测与静态检查（最新单测 90/8，均在基线失败集合内；detekt 129→99，基线 98；调色板去重后净消除 14 条）
- [x] `assembleDebug` + `assembleDebugAndroidTest` 成功，安装到真机 `bf353dda`（`install -r`，应用数据保留，Room v4→v5 迁移在既有数据上成功）
- [x] 真机走通学习闭环（10/10 提交完成），截图存 `docs/verification/f1-03/`，落库证据见同目录 `README.md`
- [x] 独立质量审查，修复阻塞项
- [ ] 提交并推送，验证 `LOCAL_HEAD == REMOTE_HEAD`

---

## 执行结果摘要

| Task | 内容 | 状态 |
|---|---|---|
| 1 | 词卡内容源接缝 + 应用自撰占位内容 + 许可台账登记 | 完成 |
| 2 | 计划卡来源改用事件存储（真实新词/到期划分） | 完成 |
| 3 | 学习会话状态机（幂等重试、失败不推进、重入保留完成） | 完成 |
| 4 | 词卡界面与三档反馈控件（无撤销入口） | 完成 |
| 5 | Hilt 接线与导航接入 | 完成 |
| 6 | 全量验证与交付 | 验证完成，待提交推送 |

**真机结论：** 三档映射与 V1 间隔实测为 `不认识→Again→10 分钟`、`模糊→Hard→1 天`、`认识→Good→3 天`；10 张卡产生 10 条互不相同的事件与 10 行派生状态，全部带 `algorithmVersion/paramsVersion` 与调度前后状态；杀进程重启后会话从事件日志恢复到第 4 张，已完成的卡未被重新发放。细节见 `docs/verification/f1-03/README.md`。

**未覆盖项（已在验证记录中列明）：** AC1-04 同事件 ID 重放、AC1-06 跨词书独立进度、到期复习路径未在真机复现，由对应自动化用例覆盖。

---

## Plan Self-Review

- **AC1-03 / AC1-04 覆盖**：Task 2 与 Task 3 的单测覆盖「三档顺序」与「同 ID 重放一致」；Task 3 增加「重放时故意改用不同评分仍不得移动排程」的更强用例。
- **许可风险**：Task 1 把内容源做成接缝，占位内容与代码同仓但以 `PLACEHOLDER_` 命名并在台账登记，替换真实数据集时不需要改动 UI 或存储层。
- **范围边界**：F1-04 的解锁判定、完成度统计与解释性状态不在本计划内；本计划只保证「每卡一次有效反馈」与排程记录正确，完成度展示留给 F1-04。
- **已知夹具限制**：`createComposeRule()` 下 `viewModelScope` 协程不被 `waitForIdle()` 驱动，Task 4 的 Compose 测试只断言静态结构与输入注入，异步加载行为由 `WordCardViewModelTest` 与真机截图共同覆盖。
