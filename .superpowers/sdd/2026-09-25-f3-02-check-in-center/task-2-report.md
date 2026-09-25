# F3-02 Task 2 修正报告

## 修改内容

- 将 `LearningStatsRepository.today/range` 改为 `suspend` 契约。
- `RoomLearningStatsRepository` 注入 `CoroutineDispatcher`，并在 `today/range` 中通过 `withContext(ioDispatcher)` 执行 Room 查询。
- `AppModule` 为统计 Repository 注入 `@Named("io")` dispatcher。
- 更新 `LearningStatsRepositoryContractTest` fake 实现为 suspend，并使用 `runBlocking` 调用协程契约。

## 测试说明

Task 2 原实现没有针对 Room 聚合 Repository 的新增测试；本次仅更新既有契约测试以匹配 suspend API，未新增复杂测试。

## 取消传播修正

- `today/range` 不再使用 `runCatching/recoverCatching`，避免吞掉协程取消异常。
- 在 `withContext(ioDispatcher)` 内显式原样重新抛出 `CancellationException`；其他异常映射为 `AppError.StorageUnavailable`。
