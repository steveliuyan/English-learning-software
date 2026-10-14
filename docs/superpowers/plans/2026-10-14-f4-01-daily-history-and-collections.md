# F4-01 历史、收藏与统计 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐阶段 4 F4-01 未实现面：按日历史详展、基础统计（累计/连续天数/完成率）、收藏词、错词列表、文章版本管理、删除级联影响确认，并修正「删词书物理删学习历史」的 Spec 违规，闭环 AC4-01。

**Architecture:** 统计与详展全部从既有权威表（today_plans / today_plan_tasks / learning_events / reading_completions / articles / card_review_states）派生，扩展 `InternalLearningStatsDao` 聚合查询与 `LearningStatsRepository`；收藏是唯一新增业务表（v31，且必须进备份白名单）。删词书改「词书软删（`word_books.deleted` 标记）+ 内容清理 + 历史零触碰」。UI 全部挂在现有屏幕（LearningRecords HISTORY 页签、CheckIn、ReadingHistory、CardDetail），不新建导航体系（AppScreen 布尔叠层模式）。

**Tech Stack:** Kotlin、Jetpack Compose、Room（KSP，exportSchema）、Hilt、JUnit5+kotlin.test+runTest（JVM）、androidTest（MigrationTestHelper/inMemory Room）。

**Spec:** `docs/specs/04-history-reminders-backup-and-security.md` §F4-01（L13-18）与 AC4-01（L49）；边界见 `docs/decisions/2026-09-30-learning-records.md`（F1-07 逐词回看已存在，F4-01 复用其查询不另建）；收藏入备份依据 `docs/specs/04` F4-03 L29「导出范围：…收藏…」。

**分支基线：** 自 `stage-5-ac405-consume-on-issue` tip `feddcb0`（堆叠）切 `stage-5-f4-01-daily-history-and-collections`——该分支含 Room v30，F4-01 的 v31 迁移必须基于 v30，否则会双分支争改 `AppDatabase` version/MIGRATIONS。

## Global Constraints

- 单测基线 **1332 只增不减**；门禁 `:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`（本机 gradle-8.13，管道尾 `; exit ${PIPESTATUS[0]}`）。
- 红线文件零触碰：`BackupCryptoPolicy.kt`、`MigrationChallengePolicy`（MigrationChallenge.kt）、`AndroidMigrationKey.kt`。
- `kotlin.test` 断言布尔在前、消息在后。
- 新表/新列一律 Room 迁移 v30→v31，schema `31.json` 入库；MigrationTestHelper 校验列序。
- 新业务表默认**不入备份**，除非 Spec 明说（收藏明说）；`BackupSourceReadOnlyContractTest.forbiddenTables` 与 `BackupExportMustNotIncludeCredentialsTest` 是守门人。
- secret-scan 必须 exit 0；删除类操作必须显示级联影响条数 + AlertDialog 二次确认（Spec L18）。
- 连续学习天数判定 = 当日计划快照解锁条件满足（`UnlockPolicy.isUnlocked` 按 `modeForRuleVersion(plan.ruleVersion)`），**不要求阅读文章**（Spec L17）。
- 提交逐个 `git add`；推送带代理与 `credential.interactive=false` 并 `ls-remote` 核验。

---

### Task 1：口径决策文档（前置，锁死所有可争议定义）

**Files:**
- Create: `docs/decisions/2026-10-14-f4-01-history-stats-and-deletion-semantics.md`

**Interfaces:**
- Produces: 后续任务照抄的判定口径（不在此计划重复发明）。

- [ ] **Step 1: 写决策文档**，必须逐条给出「决定 + 理由 + 备选被拒」：
  1. **累计学习词数** = `SELECT COUNT(DISTINCT cardId) FROM learning_events WHERE profileId=:p`（跨词书去重按卡 id 天然分册，`<册>:<词>` 前缀保证不合并）。
  2. **累计复习数** = JOIN `today_plan_tasks.taskKind='DUE'` 的有效反馈事件总数（一次完成=一行，不重复计数沿用 F1-04 语义）。
  3. **连续学习天数** = 自今日（若今日未解锁则自昨日起）沿 `localDate` 降序连续满足「当日快照解锁条件」的天数；无计划日=断。
  4. **每日完成率** = `DailyLearningStats.completionRatio`（已有，(新+复完成)/(新+复目标)）。
  5. **错词** = `card_review_states.lastFeedback = 'Again'`（当前状态口径；备选「历史曾 Again」被拒——会永远积压已挽回的词）。
  6. **删词书保留面** = `learning_events`、`card_review_states`、`word_ai_notes`、`asset_records`、文章与计划全部保留；只删 `vocabulary_entries`（词条内容随册走）+ 迁移审计 + 导入目录；`word_books` 行改**软删**（`deleted INTEGER NOT NULL DEFAULT 0`），一切词书列表查询过滤 `deleted=0`，历史详展 JOIN 名称不受影响。
  7. **收藏** = 新表 `favorite_words(profileId, cardId, wordBookId, lemma, addedAtEpochMillis)`，`PRIMARY KEY(profileId, cardId)`；lemma 冗余存储保证删册后收藏列表仍可显示词面。入备份白名单（Spec F4-03）。
  8. **文章版本管理** = 只读版本列表 + 删除单个版本（不允许删除某日唯一版本；级联提示 reading_completions 与 asset 音频影响）；不做「激活/重排」（无字段支持，YAGNI）。

- [ ] **Step 2: 提交** `git add docs/decisions/2026-10-14-f4-01-history-stats-and-deletion-semantics.md` → `docs(f4-01): 历史统计口径与删除语义决策`。

---

### Task 2：按日详展与统计的域模型 + 聚合 DAO（JVM 红→绿）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/learning/domain/DailyLearningStats.kt`（新数据类同文件追加）
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningStatsDao.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/LearningStatsRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/RoomLearningStatsRepository.kt`
- Test: `app/src/test/java/com/example/englishlearning/learning/LearningHistoryDomainTest.kt`（新建）、既有 `RoomLearningStatsRepositoryTest.kt` 扩展

**Interfaces:**
- Produces:
  ```kotlin
  data class DailyLearningHistory(
      val localDate: LocalDate,
      val activeWordBookId: String,
      val activeWordBookName: String,   // JOIN word_books.displayName
      val newDone: Int, val newTarget: Int,
      val dueDone: Int, val dueTarget: Int,
      val unlockMode: UnlockMode,       // UnlockPolicy.modeForRuleVersion(ruleVersion)
      val unlocked: Boolean,            // UnlockPolicy.isUnlocked
      val articleGenerated: Boolean,    // articles 存在 (profileId, localDate, activeWordBookId) 任一行
      val articleRead: Boolean,         // reading_completions 命中该 localDate
  )
  data class LearningTotals(val learnedWordCount: Int, val reviewEventCount: Int)
  ```
  `LearningStatsRepository` 新增：`historyRange(profileId, from, to): Result<List<DailyLearningHistory>>`、`totals(profileId): Result<LearningTotals>`、`streak(profileId, today: LocalDate): Result<Int>`。
  DAO 新增（照抄进实现）：
  ```kotlin
  @Query("SELECT l.localDate, l.activeWordBookId, w.displayName, l.newTarget, l.dueTarget, l.ruleVersion, " +
      "(SELECT COUNT(*) FROM articles a WHERE a.profileId = l.profileId AND a.localDate = l.localDate AND a.activeWordBookId = l.activeWordBookId) > 0 AS articleGenerated " +
      "FROM today_plans l LEFT JOIN word_books w ON w.id = l.activeWordBookId " +
      "WHERE l.profileId = :profileId AND l.localDate >= :fromDate AND l.localDate <= :toDate ORDER BY l.localDate")
  suspend fun planRows(profileId: String, fromDate: String, toDate: String): List<PlanRow>

  @Query("SELECT l.localDate, t.taskKind, COUNT(DISTINCT t.cardId) AS done FROM today_plan_tasks t " +
      "JOIN learning_events e ON e.planId = t.planId AND e.cardId = t.cardId " +
      "JOIN today_plans l ON l.planId = t.planId " +
      "WHERE e.profileId = :profileId AND l.localDate >= :fromDate AND l.localDate <= :toDate " +
      "GROUP BY l.localDate, t.taskKind")
  suspend fun dailyDoneByTaskKind(profileId: String, fromDate: String, toDate: String): List<TaskKindDoneRow>

  @Query("SELECT COUNT(DISTINCT cardId) FROM learning_events WHERE profileId = :profileId") suspend fun totalLearnedCards(profileId: String): Int
  @Query("SELECT COUNT(*) FROM learning_events e JOIN today_plan_tasks t ON t.planId = e.planId AND t.cardId = e.cardId AND t.taskKind = 'DUE' WHERE e.profileId = :profileId") suspend fun totalReviewEvents(profileId: String): Int
  ```
  （reading_completions 复用既有 `countReadings`。`PlanRow/TaskKindDoneRow` 为 internal data class，风格同 `DateCountRow`。）

- [ ] **Step 1: 先写失败测试**：`LearningHistoryDomainTest`——`streak 计算函数`（把「降序连续解锁」抽成纯函数 `fun computeStreak(unlockedByDate: Map<LocalDate, Boolean>, today: LocalDate): Int` 放 `DailyLearningStats.kt` 同包）：连续 3 日解锁、今日未解锁从昨日起算、无计划日断、空 map=0。`RoomLearningStatsRepositoryTest` 增 `historyRange 返回详展字段且新词/复习 done 不超 target`、`totals 两口径`、`streak 走快照解锁不要求阅读`（沿用该测试类既有替身基建）。
- [ ] **Step 2: 跑测试确认 RED**（`--tests` 过滤目标类，符号不存在→编译失败即红，留 `e:` 行）。
- [ ] **Step 3: 实现**：DAO 查询 + `RoomLearningStatsRepository` 组装（done 用 `UnlockPolicy.progress(plan…)` 口径：以 completedCardIds∩plan.newCardIds/dueCardIds 计，`coerceAtMost(target)`——`dailyDoneByTaskKind` 只作快速路径，解锁判定必须走 `UnlockPolicy`，两口径一致性写测试钉住）。
- [ ] **Step 4: 跑目标测试 GREEN**。
- [ ] **Step 5: 提交**（domain + dao + repo + 测试，逐个 add，`feat(learning): F4-01 按日详展与累计/连续统计端口`）。

---

### Task 3：AC4-01 闭环 connected 测试 + 学习记录页 HISTORY 详展

**Files:**
- Modify: `app/src/androidTest/java/com/example/englishlearning/learning/RoomTodayPlanRepositoryTest.kt` 或新建 `app/src/androidTest/java/com/example/englishlearning/learning/DailyHistoryAcrossDaysTest.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningRecordsViewModel.kt`、`LearningRecordsScreen.kt`（HISTORY 页签 HistoryHeader L167-177）
- Test: `app/src/test/java/com/example/englishlearning/ui/LearningRecordsViewModelTest.kt` 扩展

**Interfaces:**
- Consumes: Task 2 `historyRange`。
- Produces: `LearningRecordsUiState.historyStats: List<DailyLearningHistory>`；HISTORY 页签分组头显示「日期 · 词书名 · 新词 a/b · 复习 c/d · 已解锁/未解锁 · 文章已生成/已读」。

- [ ] **Step 1: RED**——`DailyHistoryAcrossDaysTest`（androidTest，inMemory Room）：造两日完整数据（计划+反馈+文章+完成阅读），断言 day1 详展在 day2 写入后**逐字段不变**（AC4-01「第二天不改写第一天」）；`LearningRecordsViewModelTest` 断言 HISTORY 页签加载后 `historyStats` 填充。
- [ ] **Step 2: 跑 JVM 部分确认 RED**；androidTest 编译进 `compileDebugAndroidTestKotlin`。
- [ ] **Step 3: 实现 ViewModel 接线 + Screen 渲染**（Compose 文案中文，DynamicText 友好，无固定宽高）。
- [ ] **Step 4: 全量 JVM GREEN**；androidTest 用 `adb install -r` + `am instrument` 在 emulator 实跑一次（禁 connectedDebugAndroidTest）。
- [ ] **Step 5: 提交推送**；报告注明 connected 实跑结果。

---

### Task 4：CheckIn 页累计/连续/完成率

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/CheckInViewModel.kt`、`CheckInScreen.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/CheckInViewModelTest.kt` 扩展

- [ ] **Step 1: RED**：VM 测试三断言——`totalsText == "累计学习 X 词 · 复习 Y 次"`、`streakText == "连续学习 N 天"`、近 7 日完成率条（数据取 `range()` 的 completionRatio）；空数据=「暂无学习记录」。
- [ ] **Step 2: 确认 RED**。
- [ ] **Step 3: 实现**（复用 Task 2 端口，`streak(today)` 由 VM 传 `clock.today()`）。
- [ ] **Step 4: GREEN** + 全量单测。
- [ ] **Step 5: 提交**。

---

### Task 5：删词书保留历史（Spec 违规修复，TDD 语义反转）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/learning/RoomLearningProfileRepository.kt:32-46`
- Modify: `app/src/main/java/com/example/englishlearning/learning/WordBookDeletionService.kt`（结果类型加级联计数或另加预览端口，见 Task 9 汇合）
- Modify: 词书列表查询过滤软删（`InternalWordBookDao.listAll/findById` 语义：listAll 过滤 `deleted=0`，findById 保留读全量供历史 JOIN）
- Test: `app/src/test/java/com/example/englishlearning/learning/WordBookDeletionServiceTest.kt`（断言反转）、`RoomLearningProfileRepository` JVM 替身测试、androidTest 迁移见 Task 6

**Interfaces:**
- Produces: `deleteWordBook` 新事务体（照抄）：
  ```kotlin
  database.internalWordBookDao().markDeleted(wordBookId)          // UPDATE word_books SET deleted=1
  database.internalVocabularyEntryDao().deleteForWordBook(wordBookId)
  database.internalWordBookProgressMigrationAuditDao().deleteForWordBook(wordBookId)
  // learning_events / card_review_states / word_ai_notes / asset_records / today_plans / articles：零触碰
  ```

- [ ] **Step 1: RED**——把 `WordBookDeletionServiceTest` 中「删词书连带删事件」断言改为「learning_events 与 card_review_states 行数不变、word_books 行仍在但 deleted=1、词书列表不再含该册」；新增「删册后该册历史仍能按原词书名展示（JOIN 不悬挂）」。
- [ ] **Step 2: 确认 RED**（旧实现必红）。
- [ ] **Step 3: 最小实现**（含 DI 处 `AppModule.kt:127` 服务签名不变）。
- [ ] **Step 4: GREEN**，全量单测确认 1332 基线不降（此任务删的是行为固化测试的反向，用例数只增不减）。
- [ ] **Step 5: 提交**。

---

### Task 6：Room v31 —— word_books.deleted + favorite_words 表 + 迁移

**Files:**
- Modify: `core/storage/entity/WordBookEntity.kt`（`val deleted: Int = 0`）、新建 `core/storage/entity/FavoriteWordEntity.kt`
- Create: `core/storage/dao/InternalFavoriteWordDao.kt`（`@Upsert`/`@Delete`/`@Query` by profileId，JOIN learning 不需要）
- Modify: `core/storage/AppDatabase.kt`（entities+version=31+`internalFavoriteWordDao()`+`MIGRATION_30_31`+MIGRATIONS 数组）
- Create schema: `app/schemas/.../31.json`（KSP 生成入库）
- Test: `AppDatabaseMigrationTest` 增 `migrateV30ToV31_...`（androidTest）

- [ ] **Step 1: 写失败迁移测试**：createDatabase(30)→runMigrationsAndValidate(31,true,MIGRATION_30_31)：`ALTER TABLE word_books ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0`、`favorite_words` 表存在且列序 `[profileId, cardId, wordBookId, lemma, addedAtEpochMillis]`、老 word_books 行 deleted=0。
- [ ] **Step 2: 确认 RED**（androidTest 编译失败/运行失败留证）。
- [ ] **Step 3: 实体+迁移+dao 实现**；`assembleDebug` 生成 31.json。
- [ ] **Step 4: GREEN**（JVM 全量 + androidTest 编译 + 设备实跑该迁移用例）。
- [ ] **Step 5: 提交**（31.json 必须入库）。

---

### Task 7：收藏仓储 + 词卡/列表 UI 开关

**Files:**
- Create: `learning/FavoriteWordRepository.kt`（domain：`FavoriteWord(profileId, cardId, wordBookId, lemma, addedAt)`；接口 `toggle/isFavorite/listFavorites`）+ `RoomFavoriteWordRepository.kt`
- Modify: `di/AppModule.kt` provider；`ui/CardDetailScreen.kt`（收藏开关：卡片头部星标）与其宿主 ViewModel 接线；`ui/AppScreen.kt` 新叠层 `showFavorites` + Create `ui/FavoritesScreen.kt`（列表、点开只读详情、取消收藏即时生效）
- Test: JVM `RoomFavoriteWordRepositoryTest`（沿用既有仓储测试替身模式）、`FavoritesViewModelTest`

- [ ] **Step 1: RED**：toggle 两次=收藏→取消；list 按 addedAt 降序；删册后（deleted=1）收藏列表仍显示 lemma（Spec「保留」语义）。
- [ ] **Step 2: 确认 RED** → **Step 3: 实现** → **Step 4: GREEN** → **Step 5: 提交**。

---

### Task 8：错词列表（Again 派生，无新表）

**Files:**
- Modify: `core/storage/dao/InternalLearningStatsDao.kt`：`@Query("SELECT s.cardId, s.wordBookId FROM card_review_states s WHERE s.lastFeedback = 'Again' ORDER BY s.nextReviewAtEpochMillis") suspend fun againCards(): List<AgainRow>`（profileId 语义：card_review_states 无 profileId——按现有表结构核实后如缺列则改用 learning_events 最近一次反馈派生，以实表为准并在计划执行时记录偏离）
- Create: `ui/WrongWordsViewModel.kt` + `WrongWordsScreen.kt`；AppScreen 叠层入口（放 CheckIn/学习记录页）
- Test: `WrongWordsViewModelTest`（Again 出现且挽回为 Good 后消失）

- [ ] **Step 1–5:** RED→GREEN→提交（列表点开复用 CardDetail 只读详情，收藏开关天然可用）。

---

### Task 9：收藏入备份导出与恢复（F4-03 白名单联动）

**Files:**
- Modify: `backup/domain/BackupSnapshot.kt`（`BackupSourceRows` 加 `favorites: List<FavoriteWordEntity>`）、`backup/infrastructure/InternalBackupSourceDao.kt`（第 18 条 SELECT `FROM favorite_words`）、`RoomBackupRowReader.kt`、序列化/反序列化（`RestoreEntityDecoder`、恢复写入 `RoomRestoreDataWriter`）、`backup/device/RoomRestoreDataWriter` 表清单
- Test: `BackupSourceReadOnlyContractTest`（白名单表集合加 `favorite_words`、查询数 17→18）、`BackupExportMustNotIncludeCredentialsTest.allowedRowTypes` 加 `FavoriteWordEntity::class`、`BackupSnapshotBuilderTest`/`BackupRestorePipelineTest` 增收藏行往返、`BackupExportCoordinatorTest` 路径断言 `records/favorites.json`

- [ ] **Step 1: RED**（快照/往返/禁列三处）。
- [ ] **Step 2: 确认 RED** → **Step 3: 实现** → **Step 4: GREEN** → **Step 5: 提交**。

---

### Task 10：文章版本管理 + 删除级联影响确认（收口档）

**Files:**
- Modify: `ui/ReadingHistoryScreen.kt`（点开=版本列表：`第 N 版 · 日期 · [重读][删除此版]`）；`ArticleRepository` 增 `versions(profileId, localDate, wordBookId, type, tier)`、`deleteVersion(articleId): Result<VersionDeleteImpact>`（拒绝删唯一版本）
- Create: `backup|reading/DeletionImpact.kt` 域类型 `data class DeletionImpact(articleCount, completionCount, audioAssetCount)` + 聚合查询
- Modify: 词书删除确认框（`AppScreen.kt:1405-1423`）文案改为「保留学习历史与文章」+ 级联条数（事件 N 条保留、词条 M 条删除、目录回收）；新增搜索历史清空（级联条数+双确认）与「删除全部学习数据」入口（双确认 + 明确不可撤销、不含收藏/文章的口径按决策文档）
- Test: JVM `ArticleVersionManagementTest`（仓储层：版本列表/删唯一被拒/完成后 reading_completions 级联计数）+ VM 级联预览测试 + `ReadingHistoryScreenTest` androidTest 扩展

- [ ] **Step 1: RED**（版本删除拒绝唯一版本；impact 计数正确；确认框未点「确认」零写入）。
- [ ] **Step 2: 确认 RED** → **Step 3: 实现** → **Step 4: GREEN** → **Step 5: 提交**。

---

### Task 11：AC4-01/矩阵/审计收口 + 阶段报告

**Files:**
- Modify: `docs/verification/ac-traceability-matrix.md` AC4-01 行（详展+connected 证据）、AC4-03 行（收藏入包证据）
- Modify: `docs/verification/negative-test-audit.md` 缺口 1a → 已修
- Modify: `docs/specs/04`（若 Task 8 表结构偏离需澄清「错词」实现口径——范围变化先改 Spec）
- Create: `docs/handoff/2026-10-14-stage-5-f4-01-daily-history-report.md`（REPORT-TEMPLATE 七节）

- [ ] **Step 1:** 三门禁 + `verifyAcTraceabilityMatrix` + `scripts/secret-scan.sh` 全绿（新表无密钥；报告零隐私明文）。
- [ ] **Step 2:** 写报告（用例数差值、connected 实跑清单、偏离记录）。
- [ ] **Step 3:** 提交 → 推送 `stage-5-f4-01-daily-history-and-collections` → `ls-remote` 核验并写入报告 §4。

---

## Self-Review（写作时自查结论）

1. **Spec 覆盖**：F4-01 L15 详展→Task 2/3；L16 版本管理→Task 10、收藏→Task 6/7/9、错词→Task 8、查询历史→复用 F1-07 既有（不改）；L17 统计四项→Task 1/2/4；L18 删册保史→Task 5、级联确认→Task 5/10；AC4-01→Task 3；F4-03 收藏联动→Task 9。无遗漏。
2. **占位符**：聚合 SQL、迁移 DDL、结果语义均已给出实码或精确行为；执行中唯一「以实表为准」的核验点在 Task 8（card_review_states 是否含 profileId），已给出偏离处置路径，不是 TBD。
3. **类型一致性**：`DailyLearningHistory`/`LearningTotals`/`DeletionImpact` 定义与引用处一致；`UnlockMode`/`UnlockPolicy` 复用现名。
