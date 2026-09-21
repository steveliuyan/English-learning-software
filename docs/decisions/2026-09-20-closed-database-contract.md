# 关闭数据库上的读/写契约（Room 2.8.4）

- 日期：2026-09-20
- 状态：已接受
- 相关规格：`docs/specs/01-vocabulary-learning-and-review.md`（本地优先、反馈先落盘）、`docs/decisions/2026-09-19-learning-event-store-and-v1-scheduler.md`

## 背景

Stage 1 收尾时，`RoomLearningEventRepository`、`RoomTodayPlanRepository`、`RoomLearningProfileRepository` 需要一条稳定的存储错误契约：存储不可用时返回对应的 `StorageUnavailable`，而不是泄漏异常或谎报成功。

首轮真机全量运行暴露了一个现象：**关闭的数据库上执行写操作返回成功**。当时将其判定为"静默假成功（返回成功但零落盘）"，并据此在 `f5e2a2e` 为三个仓储统一加入 `if (!database.isOpen) return <StorageUnavailable>` 守卫。

该守卫在真机上立即暴露为回归：57 用例中 12 个正常路径用例失败，例如 `missingPlanReturnsNotFound` 期望 `NotFound` 实得 `StorageUnavailable`。根因是 `RoomDatabase.isOpen` 对**从未打开过的惰性库**同样返回 `false`——它无法区分"从未打开"与"已关闭"，因此该守卫不是判别式。

## 证据（真机 instrumented）

来源：`verification-logs/30c-closed-db-probe-isolated.log`（每个场景独立数据库、独立 job）：

| 探针 | 结果 |
|---|---|
| `P1_CLOSED_APPEND` | `Appended(duplicate=false)`，`stillActive=true`，且 `persistedAfter=Success(value=LearningEvent(eventId=event-1, ...))` |
| `P2_CLOSED_READ` | `Failure(StorageUnavailable)`，`stillActive=true` |
| `P3_CLOSED_DIRECT_INSERT` | `throw:kotlinx.coroutines.JobCancellationException`，`stillActive=true` |
| `P4_CLOSED_SAVE_IF_ABSENT` | `Ready(plan=...)` |
| `P5_FRESH_APPEND` | `Appended(duplicate=false)` |
| `P6_CLOSED_APPEND_X3` | `first=Appended(duplicate=false)`、`second=Appended(duplicate=false)`（日志只记到两次） |

辅助证据：`verification-logs/30b-closed-db-probe.log` 证明 `database.openHelper.writableDatabase` 对"已关闭"与"从未打开"两种状态**都返回 OK**，不构成判别器；`verification-logs/30d-closed-db-readback-probe.log` 证明同一关闭实例上的读会返回 `StorageUnavailable`，因此落盘校验必须经**重新打开**的实例。

结论：关闭库上的挂起 `@Transaction` 写会重新打开数据库文件并**真正持久化**，返回的成功是真话；只有非事务 DAO 直调才会抛 `JobCancellationException`。原先判定的"零落盘假成功"不成立。

## 候选方案

1. **保留 `isOpen` 守卫** —— 否决。无法区分"从未打开"，破坏 12 个正常路径。
2. **用 `openHelper.writableDatabase` 探针作守卫** —— 否决。真机证明两种状态表现一致（`30b`）。
3. **覆写 `close()` 打粘滞标记** —— 否决。Room 会透明重开文件，粘滞标记将拒绝本可成功的写入，并破坏 `recordedEventAndDerivedStateSurviveAReopen` 这类关闭后重开流程；以更大的正确性风险换一个不可达场景的防御，不划算。
4. **以行为判别式定义契约** —— 采用。

## 决定

- **移除全部 `isOpen` 守卫**（三个仓储共 5 处）。
- **统一采用行为判别式**：捕获 `CancellationException` 时，若 `currentCoroutineContext().isActive` 为真，则判定取消来自存储层并映射为 `StorageUnavailable`；否则原样抛出（调用方自身被取消时不得吞掉）。
  - 读路径在关闭库上走此分支返回 `StorageUnavailable`。
  - 两条写路径（`append`、`saveIfAbsent`）此前是无条件 `throw cancellation`，现补齐同一判别式，避免异常泄漏。
- **契约表述为真话不变量**：报成功 ⇒ 数据可读回；报 `StorageUnavailable` ⇒ 数据未落盘。
- **不人为制造"关闭库写入必须失败"**：那要求主动阻止一次 Room 能够完成、且对用户有益的写入，并把一个已实证否定的判别式当作安全机制。

## 影响

- 测试改写：
  - `RoomLearningEventRepositoryTest.closedDatabaseNeverReportsAWriteThatDidNotPersist`
  - `RoomTodayPlanRepositoryTest.closedDatabaseNeverReportsAPlanThatWasNotPersisted`
  - 两者按结果分支断言，任一支都非空断言；落盘校验经重新打开的实例。
  - 关闭库**读**用例保持断言 `StorageUnavailable`（未被削弱）。
- 生产环境该场景不可达：`AppModule.provideDatabase` 为 `@Singleton`，未配置 `autoClose`，`app/src/main` 中不存在对数据库的 `close()` 调用。契约的收益在于防御性正确性与回归保护，而非覆盖现网路径。
- 遗留缺口（**未纳入本次**，另立专项）：`profile/LocalProfileRepository.kt` 与 `core/storage/AssetRepository.kt` 的返回类型不同（非 `Result` / `kotlin.Result`），其失败语义尚未纳入同一条不变量。
- 验收证据：`verification-logs/32-repos-after-contract-alignment.log`（14/14）、`verification-logs/33-full-device-suite.log`（Starting 57 tests，0 failures，1 个刻意 `@Ignore`：AC1-07 时区用例）。提交 `b675875`，已推送 `origin/stage-1-f1-05-fsrs-scheduling`。

## 教训

在 Room 2.8.4 上，"数据库是否可用"不能由 `isOpen` 这类状态位推断，只能由**操作的实际行为**推断；读与写在关闭实例上的行为并不对称。以状态位代替行为判别，会在"未初始化"这一常见状态上产生误判，且误判方向恰好是阻断正常路径。
