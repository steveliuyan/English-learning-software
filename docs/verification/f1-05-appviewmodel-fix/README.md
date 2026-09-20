# AppViewModel 失败修复与验证记录

**日期：** 2026-09-20
**分支：** `stage-1-f1-05-fsrs-scheduling`
**涉及提交：** `dc1c8e2`（测试夹具）、`3d30fc1`（生产缺陷）
**修复起点：** `666cea3`

## 背景

F1-05 交付时，全量单测 106 项中有 8 项失败。其中 4 项被归类为「`AppViewModel` 基线质量债」并原样保留。本轮复核确认：这 4 项实际是**两类不同缺陷叠加**，其中一类是真实的生产缺陷，不应继续挂账。

## 缺陷一：JVM 测试未绑定 Main 调度器（测试夹具）

- **现象：** `AppViewModelTest` 2 项、`AppViewModelFailureTest` 1 项断言失败，实际状态停在 `NeedsProfile` / `Loading`。
- **根因：** `AppViewModel` 的 `init { reload() }` 与 `createProfile()` 均通过 `viewModelScope.launch` 执行。`AppViewModelTest` / `AppViewModelFailureTest` 未像同仓库的 `LearningSetupViewModelTest` 那样把 `Dispatchers.Main` 绑定到测试调度器，导致 `advanceUntilIdle()` 无法驱动 `viewModelScope`，协程从未运行。
- **修复（`dc1c8e2`）：** 两个测试类补 `StandardTestDispatcher` 字段、`@BeforeEach Dispatchers.setMain`、`@AfterEach resetMain`，并将协程用例改为 `runTest(dispatcher)`。仅改测试，未动生产代码。

## 缺陷二：创建档案失败时真实错误被吞掉（生产缺陷）

- **现象：** `known app error is preserved` 用例期望 `Error(StorageInsufficient(10))`，实际得到 `Error(DatabaseMigrationFailed)`。
- **根因：** `AppViewModel.createProfile()` 原实现为

  ```kotlin
  runCatching { create(name) }
      .getOrNull()
      ?.onSuccess { _uiState.value = AppUiState.Ready(it) }
      ?.onFailure { error -> _uiState.value = AppUiState.Error(error.toSafeAppError()) }
      ?: run { _uiState.value = AppUiState.Error(AppError.DatabaseMigrationFailed) }
  ```

  `CreateLocalProfileUseCase.invoke` 内部 `repository.save()` 会**抛出** `ProfileException`，被外层 `runCatching` 捕获后成为 outer failure，`getOrNull()` 返回 `null`，于是落到 `?: DatabaseMigrationFailed` 分支——真实 `AppError` 被丢弃并统一降级。

  注意这里存在两条语义不同的失败通道，容易误读：
  - **抛异常通道**（本缺陷所在）：`create` 抛出 → outer `runCatching` 捕获 → `getOrNull()` 为 `null` → 错误被吞。
  - **返回 `Result.failure` 通道**（原本正常）：空名时 `create` 返回 `Result.failure(ProfileException(InvalidProfileName))` → `?.onFailure` 正确映射为 `InvalidProfileName`。

- **修复（`3d30fc1`）：** 改为 `fold`，两条通道都交给 `toSafeAppError()`：

  ```kotlin
  runCatching { create(name) }
      .fold(
          onSuccess = { result ->
              result
                  .onSuccess { _uiState.value = AppUiState.Ready(it) }
                  .onFailure { error -> _uiState.value = AppUiState.Error(error.toSafeAppError()) }
          },
          onFailure = { error -> _uiState.value = AppUiState.Error(error.toSafeAppError()) },
      )
  ```

  语义保持：`ProfileException` 无损映射为对应 `AppError`；非 `ProfileException` 仍安全降级为 `DatabaseMigrationFailed`。

## 验证证据

| 项目 | 结果 |
|---|---|
| `AppViewModelTest` | 3/3 通过（`failures=0 errors=0`） |
| `AppViewModelFailureTest` | 3/3 通过（`failures=0 errors=0`） |
| 全量单测 `testDebugUnitTest` | 106 项，失败 **8 → 4** |
| `compileDebugAndroidTestKotlin` | 成功 |
| `assembleDebug` | 成功，`app-debug.apk` 已生成 |
| `ExperimentalCoroutinesApi` 警告 | 0 |

命令（工作树根目录）：

```
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache \
  ./gradlew.bat :app:testDebugUnitTest --tests 'com.example.englishlearning.ui.AppViewModelFailureTest' \
  --tests 'com.example.englishlearning.ui.AppViewModelTest' --no-daemon --no-build-cache --console=plain
```

**剩余 4 项失败**均为 Stage 0 遗留、与本次改动无关：

- `LogicalSnapshotSecurityTest` × 2
- `ProviderContractTest` × 1
- `ThirdPartyNoticesTest` × 1

## 独立复核结论

由独立审查者（只读）复核 `3d30fc1`，结论：**可交付，无阻塞项**。要点：

- 两条失败通道（抛异常 / 返回 `Result.failure`）在 `fold` 下均无损映射；非 `ProfileException` 仍安全降级。
- 全仓 `main` 源码中已无 `getOrNull()` 吞错残留；其余 `runCatching` 用法（`reload()`、`AndroidKeyStoreSecretStore`）不涉及吞错。
- 无测试断言旧的错误映射，无回归；UI 侧 `ErrorScreen` 仅渲染固定文案，用户可见像素不变，改变的是 `AppUiState.Error.error` 的语义值（即规格与测试约束的契约）。
- 独立复跑 `AppViewModelTest` 3/3、`AppViewModelFailureTest` 3/3 通过。

## 遗留问题（既有缺陷，非本次引入）

1. **`AppViewModel` 状态竞态**：`init { reload() }` 与 `createProfile()` 都是独立的 `viewModelScope.launch` 且直接写 `_uiState`，无串行化，存在 last-writer-wins；迟到的 `reload()` 可能把 `Ready` 覆盖回 `NeedsProfile`。来源为 `ca59553` 之前的实现，现实风险低（UI 仅在 `NeedsProfile` 下才暴露创建入口）。建议单开任务，用 `Mutex` 或取消在途 job 串行化。
2. **`toSafeAppError()` 未识别 `AppErrorException`**（`PrivateMediaStore.kt:58`）：当前 profile 路径不可达，属潜在硬化点。
3. **测试覆盖小缺口**：无「`create` 返回 `Result.failure(非 ProfileException)`」用例来锁定 `fold` 的兜底分支；当前生产代码不产生该形态。

以上均不构成本次交付的门槛。
