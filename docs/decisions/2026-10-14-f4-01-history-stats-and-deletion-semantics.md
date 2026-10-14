# F4-01 历史统计口径、删除语义与收藏/版本管理决策

- 日期：2026-10-14
- 状态：已决定。
- 依据：`docs/specs/04-history-reminders-backup-and-security.md` §F4-01（L13–18）与 AC4-01；`docs/decisions/2026-09-30-learning-records.md`（F1-07 逐词回看边界：F4-01 复用其查询，不另建一套）；实现计划 `docs/superpowers/plans/2026-10-14-f4-01-daily-history-and-collections.md`。

## 背景

F4-01 是阶段 4 遗留的未实现面：按日历史详展、基础统计（累计学习词数、累计复习数、连续学习天数、每日完成率）、收藏词与错词列表、文章版本管理、删除级联影响确认。走查同时确认现实现有一处 Spec 违规：`RoomLearningProfileRepository.deleteWordBook`（L32–46）在删除词书时物理删除 `learning_events` 与 `card_review_states`，直接违反 F4-01 L18「删除词书后仍保留学习历史和文章」。本决策把所有可争议的统计与删除口径一次性锁死，后续实现照抄，不再各自发明。

## 决定

### 1. 累计学习词数

**决定**：`SELECT COUNT(DISTINCT cardId) FROM learning_events WHERE profileId = :profileId`。

- 理由：`learning_events` 是只追加的权威事件表（F1-03），「有效反馈=完成」口径与学习闭环一致；cardId 带 `<词书id>:<词>` 前缀，跨词书天然不合并，符合 AGENTS.md「同一 lemma 跨词书默认不合并进度」。
- 备选被拒：按 `card_review_states` 行数统计——该表是调度态，会随词书清理/重建波动，且历史上从未反馈过的卡不在其中，口径不稳定。

### 2. 累计复习数

**决定**：JOIN `today_plan_tasks` 且 `taskKind='DUE'` 的有效反馈事件总数：

```sql
SELECT COUNT(*) FROM learning_events e
JOIN today_plan_tasks t ON t.planId = e.planId AND t.cardId = e.cardId AND t.taskKind = 'DUE'
WHERE e.profileId = :profileId
```

- 理由：复习数应以「计划任务类别」而非「cardId 曾否出现」定义；eventId 主键保证同一事件不重复计数（沿用 F1-04 语义），JOIN 到 DUE 任务行即精确剔除新词首学。
- 备选被拒：`COUNT(*) WHERE feedback…` 全部事件——把新词首学混入复习；按卡去重——丢失「同一卡多次复习」的次数语义。

### 3. 连续学习天数

**决定**：自今日（若今日快照未解锁则自昨日起）沿 `today_plans.localDate` 降序，连续满足「当日快照解锁条件」（`UnlockPolicy.isUnlocked(plan, completedCardIds, UnlockPolicy.modeForRuleVersion(plan.ruleVersion))`）的天数；当日无计划快照即断；不要求阅读文章。

- 理由：Spec L17 明文「连续学习以当日计划快照内的解锁条件满足为准，不要求阅读文章」；解锁条件在快照的 `ruleVersion` 里冻结（与 F1-02 不可改写一致），历史判定不随实时设置漂移。
- 备选被拒：按 `reading_completions` 计数（Spec 明确排除）；按「当日有任意学习事件」计数（低于解锁口径，会把只学一张卡的日子算成完整日，与完成率语义冲突）。
- 锚定与既有实现一致：今日/昨日按 `ClockProvider` 的本地日历日，不用时钟瞬时（D-5 锚定教训）。

### 4. 每日完成率

**决定**：沿用既有 `DailyLearningStats.completionRatio` =（新完成+复完成）/（新目标+复目标），目标为 0 的项按 `UnlockPolicy.progress` 口径 `coerceAtMost`。不新定义。

- 理由：既有口径已被阶段 1/4 测试固化；重复发明会造成两套完成率。

### 5. 错词列表

**决定**：错词 = 该 profile 下**最近一次学习事件反馈为 Again（不认识）**的卡。实现从 `learning_events` 派生每卡最近事件（计划执行时已实测核验：`card_review_states` 表无 `profileId` 列，不能直接承载 profile 口径——此为对计划 Task 8 预设查询的偏离，按计划约定以实表为准并在此记录）。排序用 `card_review_states.nextReviewAtEpochMillis`（无状态行则退回事件时间）。挽回为其他反馈后自动从列表消失，与「当前状态口径」一致。

- 理由：错词的目的是「现在还在错」，当前态最有用；`learning_events` 有 profileId 且只追加，是唯一的按 profile 可审计来源。
- 备选被拒：「历史曾 Again」——会永远积压已挽回的词，列表只增不减，不可用。

### 6. 删除词书的保留面（修复 Spec 违规）

**决定**：`deleteWordBook` 新事务体只做四件事：

```kotlin
database.internalWordBookDao().markDeleted(wordBookId)              // UPDATE word_books SET deleted=1
database.internalVocabularyEntryDao().deleteForWordBook(wordBookId) // 词条内容随册走
database.internalWordBookProgressMigrationAuditDao().deleteForWordBook(wordBookId)
// 导入目录清理（如有）
```

`learning_events`、`card_review_states`、`word_ai_notes`、`asset_records`、`today_plans`、`articles`、`reading_completions` 全部零触碰。`word_books` 行改软删（新列 `deleted INTEGER NOT NULL DEFAULT 0`，Room v31）：一切词书列表查询（`listAll`）过滤 `deleted=0`；`findById` 保留读全量，保证历史详展 JOIN 词书名不悬挂。

- 理由：Spec L18 明文「删除词书后仍保留学习历史和文章」；软删保住按日详展与文章重读所需的册名与内容引用；词条内容属于词书本体，删除后不应再可浏览，故物理删。
- 备选被拒：连 `word_ai_notes`/`asset_records` 一并删（现行 L42–43 行为）——它们按 Spec 属保留面；整册连计划带文章全删——直接违反「历史文章始终可重读」。

### 7. 收藏

**决定**：新表 `favorite_words(profileId, cardId, wordBookId, lemma, addedAtEpochMillis)`，`PRIMARY KEY(profileId, cardId)`。`lemma` 冗余存储，保证词书删除后收藏列表仍可显示词面。收藏**入备份白名单**（Spec F4-03 L29 导出范围明列「收藏」），导出路径 `records/favorites.json`。卡片详情提供收藏开关；学习记录页新增收藏列表叠层，点开复用只读详情。

- 理由：收藏是用户数据不是机密，且 Spec 点名导出；冗余 lemma 是删册保留语义（决定 6）的直接推论。
- 备选被拒：只存 cardId 查询时 JOIN 取词面——删册后词条行已物理删，收藏列表会显示空白。

### 8. 文章版本管理

**决定**：只读版本列表 + 删除单个版本；拒绝删除某日（该 profile+localDate+册）的唯一版本；删除前显示级联影响条数（该版阅读完成数与关联 AI 音频资产数）并二次确认。不做「激活/设为当前/重排」——现表无对应字段，YAGNI。

- 理由：Spec L16「可管理文章版本」的最小有意义解释是「看得见、删得掉、删不空」；历史已解锁文章始终可重读要求每日至少存一版。
- 备选被拒：任意删除（可能把某日删空，违反「历史文章可离线重读」）；版本激活（无字段支持，需要 schema 扩张与排序语义，超出 F4-01 范围）。

## 影响

- 数据层：Room v30→v31（`word_books.deleted` 列 + `favorite_words` 表），`31.json` 入库；`favorite_words` 加入备份导出白名单（源查询 17→18 条），`migration_pairing_ledger` 维持禁入。
- 行为反转：`WordBookDeletionService`/`RoomLearningProfileRepositoryTest` 中固化「连带删事件」的断言全部改为保留断言；删除确认框文案改为「保留学习历史与文章」并显示级联条数。
- UI：学习记录 HISTORY 页签按日详展；CheckIn 三卡（累计/连续/完成率）；词卡详情收藏开关；收藏/错词列表叠层；阅读历史版本列表；删除类操作统一「级联条数 + AlertDialog 二次确认」（Spec L18）。
- 文档：AC 矩阵 AC4-01/AC4-03 行补证据；negative-test-audit 缺口 1a 关闭。
