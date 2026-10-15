# 阶段 5 SP-B 性能与可靠性 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落实 F5-02/AC5-02：建立六项性能基线并回填阈值、修复文章版本写入竞态（「损坏文章版本」负项）、数据库迁移失败从崩溃改为可恢复呈现、离线全链验收自动化+脚本，并承接阶段 4 遗留 B4/B5/T2。

**Architecture:** 性能测量=JVM 无、全部走 androidTest 计时用例 + `scripts/perf-baseline.sh`（`am start -W` 冷启动）；阈值先实测（5 次取中位数×2）再回填断言，禁止拍脑袋预填。迁移失败恢复=在 `provideDatabase` 接缝捕获启动异常 → `AppUiState.NeedsRecovery` 全屏诚实文案 + 重试（零数据删除、零破坏性 fallback）。版本竞态=saveNewVersion 进 `withTransaction` + UNIQUE 冲突有界重试。

**Tech Stack:** Kotlin、Room、Coroutines、androidx.test（am instrument）、bash（perf 脚本，沿用 secret-scan.sh 风格与 `.gitattributes` eol=lf）。

**Spec:** `docs/specs/05-release-quality-and-compliance.md`（F5-02、AC5-02、发布阻断条件）；阶段母计划 `docs/superpowers/plans/2026-10-14-stage-5-release-quality-and-compliance.md` §SP-B。

## Global Constraints

- 门禁=单测全绿（`assembleDebug→testDebugUnitTest→compileDebugAndroidTestKotlin`，本机 gradle.bat，管道带 `; exit ${PIPESTATUS[0]}`）；单测基线 1388 只增不减。
- connected 用 `adb install -r` + `am instrument`，禁 `connectedDebugAndroidTest`（清数据）。
- 不新增第三方依赖（计时用 `SystemClock.elapsedRealtime`，不引 benchmark 库）；不新增账号/云同步/远程代码。
- 真机 bf353dda 不在当前会话设备列表：凡「真机复测」项标注 TODO-USER，不以模拟器冒充真机证据。
- B5（真实 AI Profile 端到端）与 T2（内容级抓包）需用户配合，本计划只产出脚本与登记项。
- 报告/仓库零密钥；提交逐文件 add、带 git 身份参数；推送后 `ls-remote` 核验。

## 查勘结论（写死为事实，执行者不必重查）

- 超时/取消已存在：`UrlConnectionAiHttpTransport.kt:30-31`、`timeoutSeconds` 走 `AiRequestPolicy.kt:44` 白名单；四个调用点 rethrow Cancellation（`GenerateArticleUseCase.kt:118` 等）。
- 「取消后不存半篇文章」已有 = `GenerateArticleUseCaseTest#rethrowsCancellationWithoutStoring`（L400）；「重试不重复学习事件」已有 = `SubmitCardFeedbackUseCaseTest`（L68，eventId + `insertEventIfAbsent`/`@Transaction appendEvent`）。母计划 SP-B-2 的两项点名用例**已达成**，T3 只做审计补漏，不再重复造。
- 真缺口 A：`RoomArticleRepository.saveNewVersion`（L27-43）`maxVersion` 读 + `insert` 写**无事务**；`articles` 有 unique 索引 `index_articles_reuse_key`（profileId,localDate,activeWordBookId,articleType,lengthTier,version）→ 并发生成第二版本时撞 UNIQUE 直接抛，即 Spec「损坏文章版本」风险。
- 真缺口 B：`AppDatabase.create()`（L631-635）无 `fallbackToDestructiveMigration`（正确），但 `AppModule.provideDatabase` 无异常处理——迁移失败=进程启动即崩溃，违反「迁移失败以可恢复错误呈现」。`withMaintenanceDatabase` 已有 catch→false 先例。
- 计时基建=零：无 benchmark/measure/`am start -W` 脚本；androidTest 最大造数仅 25 行，无批量夹具。
- 备份校验入口：`BackupPackageValidator.validate(bytes)`（8 步）+ `readEntries`；恢复写库 `RoomRestoreDataWriter`。
- DB 构建点三处：`AppModule.kt:109`、`RoomRestoreDataWriter.kt:33`、`AndroidRestoreSupport.kt:146`。启动 Activity=`.MainActivity`（另有 BrandLaunchActivity 闪屏）。
- 最低口径设备：emulator-5574（API 30）；`scripts/` 现仅 secret-scan 两脚本。

---

### Task 1: SP-B 语义决策文档

**Files:**
- Create: `docs/decisions/2026-10-15-sp-b-performance-and-reliability-semantics.md`
- Test: 无（文档），但 T2-T5 的断言以它为准

**Interfaces:**
- Produces: 阈值回填规则（中位数×2 向上取整到 50ms/100ms 档）、saveNewVersion 事务+有界重试（≤3 次，超限抛 `AppError.StorageUnavailable`）语义、迁移失败 `NeedsRecovery` 文案与动作边界（不删数据、不提供清数据按钮以外的破坏操作）、AC5-02 离线验收步骤口径。

- [ ] Step 1: 按模板写背景/候选/决定/影响四段；明确否决 `fallbackToDestructiveMigration`（违反本地优先）与「重启自愈静默重试」（掩盖故障）。
- [ ] Step 2: 与 T2/T4/T6 的测试名互相引用（接口块对齐）。
- [ ] Step 3: 提交 `docs(decisions): SP-B 性能阈值与迁移失败恢复语义`。

### Task 2: saveNewVersion 事务化 + 并发竞态回归（真缺口 A）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/reading/RoomArticleRepository.kt:27-43`
- Test: `app/src/androidTest/java/com/example/englishlearning/reading/RoomArticleVersionRaceTest.kt`（新建）

**Interfaces:**
- Consumes: `AppDatabase`（文件库，仿 `RoomLearningDataDeletionTest` 的 builder）、`Article` domain。
- Produces: `saveNewVersion` 并发安全契约：同组两篇并发生成 → 版本号 {1,2} 无失败；仓储内部 `withTransaction` 每次「读 maxVersion→insert」，UNIQUE 冲突有界重试 ≤3。

- [ ] Step 1: RED——写 `RoomArticleVersionRaceTest.twoConcurrentGeneratorsGetDistinctVersions`：`coroutineScope { 两个 async 同时 saveNewVersion(同组 article) }`，断言两都 `isSuccess` 且版本集合 == {1,2}。当前实现预期一失败（UNIQUE 抛→Result.failure）。
- [ ] Step 2: 跑 `am instrument` 确认 RED（失败信息含 UNIQUE constraint failed: articles.index_articles_reuse_key）。
- [ ] Step 3: GREEN——`saveNewVersion` 体包进 `database.withTransaction { }`，捕获 `SQLiteConstraintException` 重试（最多 3 次，仍失败返回原 failure 由 runStorage 映射 StorageUnavailable）。
- [ ] Step 4: connected 复跑 OK(2)（含既有 flow 测试不回归）；三门禁绿。
- [ ] Step 5: 提交 `fix(reading): saveNewVersion 事务化+UNIQUE 有界重试（F5-02 损坏文章版本负项）`。

### Task 3: 取消/超时契约审计补漏

**Files:**
- Audit: 查勘结论所列既有 6 个用例（只读）。
- Test: `app/src/test/java/com/example/englishlearning/ai/ArticleTimeoutLeavesNoVersionTest.kt`（新建，仅当审计发现缺口才建）

**Interfaces:**
- Consumes: `GenerateArticleUseCase`（假 transport 返回 `AiHttpResult.TimedOut`/`Cancelled`），假 `ArticleRepository` 记 insert 调用数。
- Produces: 「TimedOut 同样零写库」用例（现有 rethrowsCancellationWithoutStoring 只覆盖取消；超时路径若未钉则补）。

- [ ] Step 1: 读 `GenerateArticleUseCaseTest` L380-420，确认 TimedOut 是否已有零写入断言；缺则 RED 写 `timedOutStoresNothing`（assert repository.insertCalls==0 且抛 AiUnavailable）。
- [ ] Step 2: 实现侧若已满足则测试直接绿（审计型用例允许「验证既有行为」——KDoc 标 F5-02；此类用例不伪装新行为）。
- [ ] Step 3: 三门禁绿，提交 `test(ai): F5-02 取消/超时零半写契约审计用例补齐`（若审计零缺口，本 task 只交审计记录进阶段报告，不造空提交）。

### Task 4: 性能基线夹具 + 六项计时用例

**Files:**
- Create: `app/src/androidTest/java/com/example/englishlearning/perf/PerfSixMetricsInstrumentedTest.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/perf/SeedData.kt`（批量夹具 helper）
- Modify: 无生产码。

**Interfaces:**
- Consumes: `SeedData.vocabularyEntries(n)`（DAO 直插 500 词条）、`learningEvents(n)`（1000 事件 + 计划快照）、`backupBytes(nEntries)`（构造 N 条 manifest 的合法包字节，复用 backup 测试夹具风格）。
- Produces: 每项打印 `PERF|<metric>|<medianMillis>|<runs>` 行到 logcat/instrument 输出；首轮断言只设生存线（冷启动<10s、其余单项<5s）防挂死，阈值回填在 Task 5 报告后。

**测量六项（Spec 点名）:** ①离线冷启动=Task 5 脚本 `am start -W`（不在本类）②今日计划读取=`TodayPlanRepository.load` 500 词条册快照中位数 ③词卡翻页=同快照 `toCard` 连续 20 卡映射 ④单词查询=`VocabularySearchIndex` 查 "ability"（1000 事件+500 词条后）⑤学习事件写入=100 次 `appendEvent` 提交链（含 FSRS 计算）⑥大型备份校验/恢复=`BackupPackageValidator.validate(bytes(500))` + `RoomRestoreDataWriter` 写入临时库。

- [ ] Step 1: 写 `SeedData`（每 helper 幂等、nanoTime 命名隔离库文件，仿 `RoomLearningDataDeletionTest` 的 try/finally 清库）。
- [ ] Step 2: 写计时循环 helper：`measureMedian(runs=5, block: ()->Unit): Long`（elapsedRealtime，预热 1 次不计）。
- [ ] Step 3: 六用例逐条落地，每条断言生存线 + `Log.i("PERF", ...)` 行。
- [ ] Step 4: `am instrument` 于 emulator-5574 实跑（造数大，单类首跑可能分钟级；FAILURES 先修夹具再谈阈值）。
- [ ] Step 5: 三门禁 + 提交 `test(perf): F5-02 六项性能基线计时用例与批量夹具（阈值待实测回填）`。

### Task 5: perf-baseline.sh + 基线报告 + 阈值回填

**Files:**
- Create: `scripts/perf-baseline.sh`
- Create: `docs/verification/perf-baseline-2026-10.md`
- Modify: `PerfSixMetricsInstrumentedTest.kt`（生存线换实测中位数×2 阈值）。

- [ ] Step 1: 脚本：清后台→`am start -W -n com.example.englishlearning/.MainActivity`（先 `am force-stop`）×5 取 TotalTime 中位数；触发 Perf 类 instrument 抓 `PERF|` 行；输出 markdown 表（设备、API、每次值、中位数）。设备参数化 `-s`，禁 connectedDebugAndroidTest。
- [ ] Step 2: emulator-5574 跑通脚本，生成报告（真机 bf353dda 列留 `TODO-USER：真机接入后补测`）。
- [ ] Step 3: 按决策规则（中位数×2 取整）回填测试断言；重跑全绿即「阈值有据」。
- [ ] Step 4: 三门禁 + 提交 `docs(verification): 性能基线报告+阈值回填（F5-02 六项，中位数×2）`。

### Task 6: 迁移失败 → 可恢复呈现（真缺口 B）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/core/storage/DatabaseStartup.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`（provideDatabase）、`ui/AppViewModel.kt`（+`NeedsRecovery` 态）、`ui/AppScreen.kt`（渲染分支）、`ui/RecoveryScreen.kt`（Create，小屏 compose）。
- Test: `app/src/test/java/com/example/englishlearning/ui/AppViewModelRecoveryTest.kt`；`app/src/androidTest/java/com/example/englishlearning/core/storage/MigrationFailureRecoveryTest.kt`。

**Interfaces:**
- Consumes: `DatabaseStartup.open(context): Result<AppDatabase>`——捕获 `Room.databaseBuilder(...).build()` 与首次 `openReadWriteDatabase()` 的 Throwable，失败路径必须 `deleteDatabase`？**否**（决策：不删任何文件，重试保留原库）。
- Produces: `AppUiState.NeedsRecovery(reason: String)`；RecoveryScreen 文案「数据库升级失败：<原因摘要>。你的学习数据仍在设备上。可重试；如反复失败，请先用另一台设备确认导出留档。」+ 按钮 重试（testTag `recovery_retry`）。**没有**清数据入口（避免诱导销毁数据；系统级清数据在文案说明即可）。

- [ ] Step 1: RED JVM：`AppViewModelRecoveryTest.databaseOpenFailureShowsNeedsRecoveryWithRetry`（fake open 返回 failure → 断言 state；retry 成功后回 Ready；reason 不含堆栈/路径）。
- [ ] Step 2: RED androidTest：`MigrationFailureRecoveryTest`——建 v29 老库后注入故意失败的 `Migration(29→30)`（自建 builder，不用 MIGRATIONS 全集），`DatabaseStartup.open` 返回 `Result.failure` 且库文件仍存在（`context.getDatabasePath(name).exists()`）。
- [ ] Step 3: GREEN：`DatabaseStartup.open` 实现；AppModule 走 open 失败→把 `StateFlow<Throwable?>` 暴露给 AppViewModel（或经一个 `DatabaseStartupState` 单例端口，避免 Hilt 构造环）；AppScreen 按 state 渲染 RecoveryScreen；重试用 Hilt 入口重建（v1 简化=重试按钮 `activity.recreate()` + startup 清错误态重开一次）。
- [ ] Step 4: 三门禁 + RecoveryScreen testTag 齐全；connected 跑注入用例 OK。
- [ ] Step 5: 提交 `feat(storage): 迁移失败呈现可恢复错误与重试（F5-02，AC5-02 相关；决策：禁 destructive fallback）`。

### Task 7: AC5-02 离线验收（connected + 脚本 + B4/B5/T2 登记）

**Files:**
- Create: `app/src/androidTest/java/com/example/englishlearning/perf/OfflineCorePathsInstrumentedTest.kt`
- Create: `docs/verification/ac5-02-offline-script.md`
- Modify: `docs/verification/negative-test-audit.md`（缺口2→已修）、`docs/verification/ac-traceability-matrix.md`（AC5-02 证据行）。

- [ ] Step 1: connected 自动化子集：库层离线全通（学习/复习/缓存阅读/离线查词，飞行模式无关的进程内断言）+ AI 入口态映射（fake endpoint 不可达 → `AiUnavailable`，UI 态非 loading 卡死）。设备级 airplane：脚本 `adb shell cmd connectivity airplane-mode enable` → 手工六条链路清单（含系统 TTS），逐条勾。
- [ ] Step 2: B4 Salvage 拒绝：定位既有 JVM/connected 证据写入脚本清单（应已有 `BackupRestorePipelineTest` 系），手工核恢复页拒收 `.elpbak` 一次并记录。
- [ ] Step 3: B5/T2 登记为 `TODO-USER`（需用户真实 Profile/CA 环境），不伪造通过。
- [ ] Step 4: 矩阵 AC5-02 行 + 缺口2 行更新；三门禁+`verifyAcTraceabilityMatrix`+secret-scan。
- [ ] Step 5: 提交（测试+脚本）与（矩阵）可并一：`test(offline): AC5-02 离线核心链 connected 用例与验收脚本；缺口2 关闭登记`。

### Task 8: SP-B 收口报告 + 推送核验

- [ ] 按 REPORT-TEMPLATE 七节写 `docs/handoff/2026-10-15-stage-5-sp-b-performance-and-reliability-report.md`：单测基线差值、connected 实跑清单、阈值回填数值、TODO-USER（bf353dda 复测、B5、T2）、偏离（如重试实现取 recreate 方案的记录）。
- [ ] 提交 → 推送分支 → `ls-remote` 核验远端 tip 写进回复。合 main 与否听用户。

---

## Self-Review

1. Spec 覆盖：六项测量→T4/T5；可取消/超时/不阻塞+重试不损坏→T2/T3；迁移失败可恢复→T6；空间不足/模型中断/资源损坏（其余三类）→既有覆盖或攒批（报告 §5 记录归属）；AC5-02→T7；B4/B5/T2→T7。
2. 占位符扫描：阈值数字全部「实测后回填」且有规则（中位数×2），非 TBD；RecoveryScreen 文案已给全文。
3. 类型一致性：`DatabaseStartup.open` 签名、`AppUiState.NeedsRecovery(reason)`、`PERF|` 行格式、testTag `recovery_retry` 在各 task 接口块中同名。
