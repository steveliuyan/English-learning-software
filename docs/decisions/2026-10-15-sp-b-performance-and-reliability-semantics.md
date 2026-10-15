# 2026-10-15 决策：SP-B 性能阈值、版本竞态与迁移失败恢复语义

## 背景

阶段 5 Spec F5-02/AC5-02 要求：六项性能目标须在阶段 0 口径的最低支持设备实测；所有 AI/网络操作可取消、带超时、不阻塞 UI，失败重试不得重复生成学习事件或损坏文章版本；数据库迁移失败须以可恢复错误呈现。SP-A 负向审计缺口 2 登记「迁移失败→可恢复呈现无自动化用例」归 SP-B。

开工查勘（详见计划文档「查勘结论」节）确认两个真缺口与一个已达标项：

- `RoomArticleRepository.saveNewVersion` 的「读 maxVersion→insert」无事务，`articles` 的 unique 索引 `index_articles_reuse_key` 使并发生成直接撞约束抛错——这就是 Spec 点名的「损坏文章版本」风险（`fix(reading)` 项，SP-B Task2）。
- `AppModule.provideDatabase` 对迁移异常零处理：进程启动即崩溃，违反「可恢复错误呈现」（SP-B Task6）。
- 「取消后不存半篇文章」（`GenerateArticleUseCaseTest#rethrowsCancellationWithoutStoring`）与「重试不重复学习事件」（`SubmitCardFeedbackUseCaseTest`，eventId + `insertEventIfAbsent`）**已有覆盖**——母计划 SP-B-2 点名的两项用例已达成，只补 TimedOut 零半写审计（Task3）。

## 决定

### 1. 性能阈值：先实测、后回填，禁止预填

- 测量口径：emulator-5574（API 30，最低支持口径代理设备）每项 5 次取**中位数 × 2**，向上取整到 50ms 档（<1s 项）或 500ms 档（≥1s 项）作为断言阈值。
- 首轮实现只设生存线断言（冷启动 <10s、单项 <5s）防挂死；阈值写进 `docs/verification/perf-baseline-2026-10.md` 后回填测试（Task4→Task5 顺序保证「阈值有据」）。
- 计时设施：`SystemClock.elapsedRealtime` + androidTest 夹具，**不引入 androidx.benchmark**（新依赖需重过锁与许可登记，收益不抵成本）。
- 真机 bf353dda 列 `TODO-USER`：不在当前会话设备清单，不以模拟器数据冒充真机证据。

### 2. saveNewVersion 事务化 + 有界重试

- 「读 maxVersion→insert」整体进 `database.withTransaction`；捕获 UNIQUE 冲突（`SQLiteConstraintException`）后**重读重试，最多 3 次**，超限按 `AppError.StorageUnavailable` 诚实失败。
- 候选「给生成链加互斥锁」被拒：跨进程/恢复写库仍有并发面，锁不收敛根因；事务+重试才是幂等正解。候选「撞了就报失败」被拒：用户视角=「文章生成失败」，可恢复竞态不该上浮为故障。

### 3. 迁移失败 = NeedsRecovery 全屏可恢复态，绝不 destructive

- `DatabaseStartup.open(context): Result<AppDatabase>` 包住 build + 首次打开；失败路径**不删除数据库文件、不 fallbackToDestructiveMigration、不静默清数据**（本地优先红线：数据在设备上就必须保留原样）。
- `AppUiState.NeedsRecovery(reason)` → RecoveryScreen：「数据库升级失败：<原因摘要>。你的学习数据仍在设备上。可重试；如反复失败，请先用另一台设备确认导出留档。」+「重试」按钮（testTag `recovery_retry`，v1 实现=清错误态 + `activity.recreate()`，理由：Room 失败实例不可复用，重建 Activity 即重建 DI 图，比手写重开器少一层状态泄漏）。
- 页面**不提供**「清除数据」按钮：诱导销毁数据不是「可恢复错误」；系统设置路径写进文案即可。
- 候选「onCreate try/catch 后弹 Toast 再崩」被拒：Toast 之后进程照死，等于没呈现。候选「自动回退旧 schema 副本」被拒：V1 无第二份库，凭空造备份管线越出范围。

### 4. AC5-02 离线验收口径

- 自动化子集（connected）：库层离线全链（学习/复习/缓存阅读/离线查词）+ AI 不可达时状态映射为 `AiUnavailable` 而非 loading 卡死。
- 设备级：`docs/verification/ac5-02-offline-script.md` 飞行模式手工六条链路清单（含系统 TTS、AI 入口文案、入口可点不卡）。
- 阶段 4 遗留承接：B4 Salvage 拒收=既有测试证据 + 一次手工核；**B5（真实 AI Profile 端到端）与 T2（内容级抓包）标 TODO-USER**，需用户配置与 CA 环境，未做不写「通过」。

### 5. 其余 F5-02 负项归属

- 存储空间不足：真机 fail-closed 归走查攒批（既有裁决）。模型下载中断、音频资源损坏：阶段 3 已有失败态与哈希校验覆盖，SP-B 报告引用证据即可，不重造。

## 影响

- `RoomArticleRepository` 行为变化仅并发竞态路径（单协程路径语义不变）；`ArticleVersionViewModelTest`/`RoomArticleVersionFlowTest` 不得回归。
- `AppModule.provideDatabase` 从「必成」变「可能失败并呈现」——AppViewModel 状态机新增一态，现有 `Loading/NeedsProfile/Ready` 测试不受影响。
- 新增 scripts/perf-baseline.sh 与 androidTest/perf 包；单测基线只增不减（当前 1388）。
- 本决策与 `docs/superpowers/plans/2026-10-15-stage-5-sp-b-performance-and-reliability.md` 的 Task2/4/5/6/7 测试名一一对应（`RoomArticleVersionRaceTest`、`PerfSixMetricsInstrumentedTest`、`MigrationFailureRecoveryTest`、`AppViewModelRecoveryTest`、`OfflineCorePathsInstrumentedTest`）。
