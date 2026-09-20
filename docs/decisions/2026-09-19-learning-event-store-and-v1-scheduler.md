# 决策：学习事件存储与 V1 调度器（F1-03 基础层）

- 日期：2026-09-19
- 阶段：Stage 1 / F1-03 词卡与反馈
- 状态：已决定

## 背景

F1-03 要求「每张计划卡提交一次有效反馈后才完成」，并明确约束：

- 三档反馈固定映射为 `不认识→Again`、`模糊→Hard`、`认识→Good`（V1 固定规则）。
- 每次提交生成唯一事件 ID；**重复提交同一 ID 不得重复计数或二次调整间隔**。
- 调度记录**算法版本、参数版本、评分、发生时间、调度前后状态**；算法升级只能重算未来的 `nextReviewAt`，**不得改写事件**。
- V1 不提供撤销已提交反馈，避免排程歧义。

当前仓库只有阶段 0 留下的边界 `learning/domain/ReviewScheduler.kt`（`ReviewScheduler` / `ReviewState` / `ReviewFeedback{Again,Hard,Good}` / `ScheduledReview`），**没有事件表、没有卡片状态表、没有可用实现**；Room 最新版本为 v4，`FixturePlanCardSource` 返回空列表。

## 候选方案

### A. 只存「卡片当前状态」，用状态推断完成数
- 优点：表最少，查询简单。
- 缺点：无法满足「不得改写事件」「记录评分与调度前后状态」「同 ID 重放一致」；一旦算法升级就丢失可审计历史。**否决**。

### B. 事件表为权威 + 派生卡片当前状态（选定）
- `learning_events` 以 `eventId` 为主键，只追加不修改；`card_review_states` 保存该卡当前调度状态，是事件重放得到的派生值。
- `eventId` 主键 + `INSERT OR IGNORE` 天然实现幂等：重复 ID 插入返回 `-1`，据此判定重复并**跳过**状态推进。
- 优点：满足全部 F1-03 约束；升级算法只需重算未来 `nextReviewAt`；幂等性由数据库约束保证，而非应用层先查后写（无竞态）。
- 缺点：需要一次 Room v5 迁移。**选定**。

### C. 事件存 JSON 文件 / 快照导出
- 与阶段 0「本地数据以 Room 为权威」的约定冲突，且无法用唯一索引保证幂等。**否决**。

## 决定

1. **Room v5**，新增两张表：
   - `learning_events`：主键 `eventId`；列 `profileId, planId, cardId, wordBookId, feedback, occurredAtEpochMillis, algorithmVersion, paramsVersion, dueBeforeEpochMillis(NULL), nextReviewAtEpochMillis`；索引 `(profileId, cardId)`、`(planId)`。调度「之后」的状态由 `nextReviewAtEpochMillis` 单独承载，不再另设 `dueAfter` 列，避免同一语义两处存储。
   - `card_review_states`：主键 `cardId`；列 `wordBookId, lastFeedback, lastReviewedAtEpochMillis, nextReviewAtEpochMillis`。
   - 迁移 `MIGRATION_4_5` 用 `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS`，与既有迁移风格一致；不配置破坏性迁移。
2. **幂等由唯一主键保证**：写入走 `@Insert(onConflict = IGNORE)`，返回 `-1` 即重复；重复提交**不推进**卡片状态、**不产生**第二次调度，直接返回既有事件。
3. **V1 调度器**为确定性实现，`ALGORITHM_VERSION = "v1"`、`PARAMS_VERSION = "v1"`，间隔规则 `Again = 10 分钟`、`Hard = 1 天`、`Good = 3 天`（相对 `now` 计算），保证 `Again ≤ Hard ≤ Good`（AC1-03）。它是 FSRS 接入前的占位实现，接口仍是 `ReviewScheduler`，替换实现不影响事件表。
4. **事件不可变**：不提供 `UPDATE`/`DELETE` 事件的 DAO 方法；算法升级只重算未来的 `nextReviewAt`。
5. **`ReviewScheduler` 边界不改签名**：版本号由调度器实现作为公开常量提供，由用例写入事件，避免为版本字段改动阶段 0 已有边界。

## 影响

- AC1-03（三档顺序）与 AC1-04（同 ID 重放一致）可在**本地单测**（Robolectric + 内存 Room）中验证，无需真机。
- 「同一事件不重复计数」由 `learning_events` 的行数天然决定，F1-04 的完成度统计可直接基于事件表计数，不需要额外的计数器表（避免双写不一致）。
- 后续 FSRS 接入时：新增 `FsrsV1ReviewScheduler`，把 `PARAMS_VERSION` 递增；历史事件保持原样，仅未来 `nextReviewAt` 重算。
- 词卡**内容**（lemma/IPA/词性/释义）不在本决策内：它依赖许可核验的词条数据，作为独立决策另行记录，UI 层通过内容源接缝消费。
