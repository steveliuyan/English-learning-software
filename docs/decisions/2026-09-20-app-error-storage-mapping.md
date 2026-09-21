# 资源层存储错误的映射与调用方取消的处理

- 日期：2026-09-20
- 状态：已接受（第一步；第二步已排期）
- 相关：`docs/decisions/2026-09-20-closed-database-contract.md`、`docs/specs/00-foundation-and-architecture.md`

## 背景

`docs/decisions/2026-09-20-closed-database-contract.md` 确立"关闭数据库"契约后，独立审查（quality-review 对提交 `b675875` 的审查）指出同一类缺陷在另外两个仓储未修完：

- `core/storage/AssetRepository.kt:12-20` 的 `get(id)` 用 `catch (_: Exception)` 兜底，存在两处问题：
  1. **吞掉调用方自身的取消**。`kotlinx.coroutines.CancellationException` 是 `Exception` 的子类，被该 catch 捕获后转成 `Result.failure`，调用方在被取消的 scope 上继续运行——违反结构化并发。
  2. **错误码误标**。任何真实存储故障都被报成 `AppError.DatabaseMigrationFailed`（"数据库迁移失败"），与"一次读取失败"无关。
- `profile/LocalProfileRepository.kt:12-13` 的 `save` / `getDefault` 返回 `Unit` / `LocalProfile?`，**根本没有错误通道**，修它属于接口变更。
- `core/error/AppError.kt` 中不存在通用存储错误成员，只有 `StorageInsufficient(requiredBytes)`（语义为磁盘空间不足）。

## 证据

- RED（生产**映射**改动前——枚举成员已先加入以便测试编译，`AssetRepository` 仍是旧实现）：`verification-logs/35a-red-asset-error-mapping.log:50,53` — `AssetRepositoryTest > get maps database failures without leaking storage details()` 与 `get maps internal-scope cancellation to a storage failure while the caller stays active()` 均以 `org.opentest4j.AssertionFailedError` 失败（同批次 `4 tests completed, 2 failed`）。失败语义即误标本身：旧实现返回 `DatabaseMigrationFailed`，新断言期望 `StorageUnavailable`。
  说明：该日志只记录异常类型与断言行号，**未内联断言消息原文**；此处不引用日志中不存在的文本（初次成文时曾误引，已按原始日志更正）。
- GREEN：`verification-logs/35-unit-asset-error-mapping.log` — `114 tests completed, 4 failed`，4 项为既有 Stage-0 遗留（`LogicalSnapshotSecurityTest` ×2、`ProviderContractTest` ×1、`ThirdPartyNoticesTest` ×1），与本次无关；新增用例通过。
- 提交 `29d4b1e`，改动 4 文件（+43/−2）。

## 候选方案

1. **只改 catch，不新增错误成员**（继续用 `DatabaseMigrationFailed`）——否决：错误码仍然误标，用户会看到与实际故障无关的提示。
2. **复用 `StorageInsufficient(0)`**——否决：其语义是磁盘空间不足，与读取失败无关；这个"传 0"的用法本身已是 `PrivateMediaStore.kt:63-64` 的待议误标，不宜扩散。
3. **新增 `AppError.StorageUnavailable` + `AppErrorUiText.StorageUnavailable`**——采用。
4. **同时给 `LocalProfileRepository` 引入错误通道**——推迟：需要先定接口语义（返回 `Result` 还是抛领域异常），波及 `CreateLocalProfileUseCase`、`AppViewModel`、`AppModule` 及相关测试，用户已同意拆成两步。

## 决定

- `core/error/AppError.kt` 新增 `data object StorageUnavailable : AppError`（`uiText = AppErrorUiText.StorageUnavailable`），并同步 `AppErrorUiText` 枚举。
- `core/storage/AssetRepository.kt` 采用项目既有判别式，替换单一 `catch (_: Exception)`：
  ```kotlin
  } catch (cancellation: CancellationException) {
      if (currentCoroutineContext().isActive) Result.failure(AppErrorException(AppError.StorageUnavailable))
      else throw cancellation
  } catch (_: Exception) {
      Result.failure(AppErrorException(AppError.StorageUnavailable))
  }
  ```
  函数签名与成功路径不变。
- `AppErrorTest` 的"每个错误映射到互不相同的 `AppErrorUiText`"不变量同步维护（该测试是 `AppErrorUiText` 在 `app/src/main` 之外唯一的消费者）。

## 影响

- **`AppErrorUiText` 目前没有任何渲染方**：`app/src/main` 中 8 处 `AppErrorUiText.*` 引用全部位于 `AppError.kt` 自身（7 个既有成员 + 本次新增）。因此本次是**模型层新增**，不涉及文案资源；待引入渲染层时，新成员会自然进入穷尽 `when`（编译器会强制补齐）。
- 对受保护文件 `core/error/AppError.kt` 的改动是**用户明确批准的有意改动**，不是无关混入；改动范围仅新增成员，未触碰既有成员语义。
- `AppError.DatabaseMigrationFailed` 在该站点已完全移除。

## 遗留（未纳入本次）

1. `profile/LocalProfileRepository.kt`：无错误通道，属第二步，需先定接口语义。
2. `PrivateMediaStore.kt:63-64`：`IOException` 与未知异常均映射为 `StorageInsufficient(0)`，疑为误标。
3. `CreateLocalProfileUseCase.kt:23`：未知 `ProfileException` 兜底为 `DatabaseMigrationFailed`。
4. `AssetRepository.get` 的"调用方已被取消 ⇒ 重抛"分支**尚无测试**：现有 JVM 夹具 mock `AppDatabase`/`InternalAssetDao`，无真实 Room 实例，无法构造真正的 open-then-close 读取；覆盖它需要新的 instrumented 测试。本次新增用例覆盖的是另一半（DAO 抛取消而调用方仍 active ⇒ 映射为 `StorageUnavailable`），已由 RED 证明其在旧实现下失败。

## 教训

这类缺陷的性质是"**同类缺陷只修了一个点**"：本轮先后修了 3 个 Room 仓储，却漏掉第 4 个访问同一数据库的仓储。今后凡修改一处资源层错误映射，必须同时产出一份**同类站点清单**（`grep` 该错误码/该 catch 形态的所有出现处）并逐一判定，否则必然留下不一致的裸点。
