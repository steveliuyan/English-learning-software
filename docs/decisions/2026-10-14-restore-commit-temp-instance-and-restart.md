# 2026-10-14 决策：恢复替换提交后维护用临时数据库实例 + 进程重启

## 背景

QA 第二轮 D-12（blocker）：真机端到端恢复在 commit 阶段必失败并回滚。根因是 Room 2.8.4 的
`RoomDatabase.close()` 会取消该实例内部的 `coroutineScope`，且重开后不会重建——恢复流程在
原子切换前调用 `checkpointAndClose()` 关闭了进程级单例数据库，切换后的维护步骤
（重建搜索索引、重排提醒、清除 AI 凭据）全部通过 suspend DAO 走这个已关闭实例，
必抛 `JobCancellationException`（`CancellationException` 的内部子类），导致
`postActions` 判定失败、走回滚，`RestoreCommitResult.Failed(RollbackVerification)`。
数据层面回滚协议本身工作正常（旧数据完整恢复），但正向恢复永远无法成功。

## 候选方案

- **A. 复用进程级单例，改为 `openHelper.close()`（不关 Room 外壳）**：设备实测证伪。
  Room 的 `PassthroughConnectionPool` 把 `SQLiteConnection` 缓存进
  `kotlin.Lazy<SQLiteConnection>`，关闭后该 Lazy 永不复位；重开时 suspend DAO 抛
  `android.database.SQLException: Error code: 21, connection is closed`
  （emulator-5576 实测 + javap 反编译 room-runtime.aar 证实字段结构）。
  同时 `database.close()` 取消 coroutineScope 的语义仍要求完全避开 Room 外壳复用。
  **拒绝。**
- **B. 全局 DatabaseHolder/重开框架重构**：让所有数据库消费者通过 holder 在关闭后透明拿到
  新实例。波及约 26 个注入 `AppDatabase` 的文件与全部 ViewModel 生命周期，风险与范围远超
  本缺陷。 **拒绝。**
- **C. 维护步骤使用按同一 Schema 新开的临时数据库实例（提交侧），UI 侧要求重启进程**：
  `RoomFormalDataHandle.checkpointAndClose()` 保持完整 `database.close()`（关库时序与回滚
  协议语义不变）；`AndroidPostRestoreActions` 每次调用自建临时 `AppDatabase`
  （`Room.databaseBuilder` → 用 → `close()` → 删 `-wal`/`-shm`），与仓内既有
  `RoomRestoreDataWriter.writeReplacement` 的临时实例先例一致。替换提交一旦开始，进程级
  单例即成僵尸，恢复页在 Done 阶段明示"需重启应用"并提供重启入口（PendingIntent 重新
  拉起 + `killProcess`）。 **采纳。**
- **D. 恢复放到独立进程执行**：需要跨进程 DI、单实例锁与 WorkManager 协作的大改，
  V1 范围不需要。 **拒绝。**

## 决定

采用 C。要点：

1. `AppDatabase` 增加伴生工厂 `create(context, file)`（含 MIGRATIONS 与
   CONSTRAINT_CALLBACK），由 `AppModule`、`RoomRestoreDataWriter`、临时实例维护器共用，
   保证三处 Schema 一致。
2. `AndroidPostRestoreActions` 不再注入长生命周期 `AppDatabase`，改为注入
   `@ApplicationContext` 与无数据库依赖的协作者（词卡源、词书 ID 源、时钟、
   `AiProfileSecretUseCase`、IO dispatcher），在临时实例上现场重建
   `RefreshVocabularySearchIndexUseCase` / `WorkManagerReminderScheduler` /
   `RoomAiProfileRepository`。
3. `BackupRestoreViewModel` 增加 `appRestartRequired`：只要尝试过 commit（成功或失败回滚）
   即为真；Done 阶段 UI 提供重启入口。
4. 阶段 4 Spec F4-04 增补"切换后维护用新开实例 + 提交阶段后当前进程数据访问终止"约束，
   新增 AC4-07。

## 影响

- 恢复成功后当前界面数据不可信直到重启；文案与测试（含
  `DevicePairedRestoreE2ETest` 的生产装配复刻）随之更新。
- 红线不变：`BackupCryptoPolicy` / `MigrationChallengePolicy` / `AndroidMigrationKey`
  零触碰；checkpointAndClose 的时序（先 checkpoint 再 close）与回滚协议语义不动。
- 单测契约：JVM 侧以源码契约测试钉住"`checkpointAndClose` 必须完整
  `database.close()`"与"`AndroidPostRestoreActions` 不得持有注入的长生命周期
  `AppDatabase` 字段"。
