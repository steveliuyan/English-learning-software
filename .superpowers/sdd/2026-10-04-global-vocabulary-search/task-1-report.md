# Task 1 报告：定义搜索历史领域模型与失败安全接口

## 变更文件
- `app/src/main/java/com/example/englishlearning/learning/VocabularySearchHistory.kt`
  - 新增不可变数据类 `VocabularySearchHistory`、`SearchRepresentative`。
  - 新增 `VocabularySearchHistoryRepository`，提供 `list`、`record`、`find`、`clear` 四个挂起方法，统一返回既有 `RepositoryResult`。
  - 新增纯 Kotlin 规范化函数 `normalizeVocabularySearchQuery`：去除首尾空白、合并连续空白、按 `Locale.ROOT` 转小写；空查询返回 `null`。
- `app/src/test/java/com/example/englishlearning/learning/VocabularySearchHistoryTest.kt`
  - 覆盖首次/重复累计、profile 隔离、空查询、规范化和显示开关不影响统计模型。

## 测试命令与输出
1. 红灯验证（类型和接口尚不存在）：
   - 命令：`./gradlew :app:testDebugUnitTest --tests '*VocabularySearchHistoryTest'`
   - 输出：`compileDebugUnitTestKotlin FAILED`，缺少 `SearchRepresentative`、`VocabularySearchHistory`、`VocabularySearchHistoryRepository` 与规范化函数等预期符号。
2. 修复后 focused JVM 测试：
   - 命令：`cd D:/EnglishLearningWorktrees/f1-04-unlock-verify && ./gradlew :app:testDebugUnitTest --tests '*VocabularySearchHistoryTest' --no-daemon --no-build-cache`
   - 当前结果：未能执行到测试运行阶段；仓库中并行 Task 2 的 `RoomVocabularySearchHistoryRepositoryTest.kt` 仍引用未定义的 `nextVocabularySearchHistoryEntity` 与 `toVocabularySearchHistory`，导致 `compileDebugUnitTestKotlin FAILED`。该失败与本次文件无关。
3. 本次补测内容：
   - 空/空白查询通过 `recordSearch` 的可观察 fake 验证 `recordCallCount == 0`，并验证 profile 历史为空；这明确区分“未调用 record”和“record 内部忽略空值”。
   - `showCount=false` 在独立设置替身上切换后重新读取同一仓储，断言整个历史对象不变且 `searchCount == 2`，证明关闭展示不会清除或重置统计。
   - 对 `list`、`record`、`find`、`clear` 的失败替身均断言返回 `RepositoryResult.Failure(StorageUnavailable)`。
4. 变异逻辑核对命令：
   - `git diff -- app/src/test/java/com/example/englishlearning/learning/VocabularySearchHistoryTest.kt` 可审阅新增断言。
   - 可复现累计变异：临时将首次测试中的 `existing.copy(searchCount = existing.searchCount + 1, ...)` 改为 `existing.copy(searchCount = 1, ...)`，运行 focused 测试后首个测试应因期望 `2` 而失败；随后恢复改动。当前由于上述 Task 2 编译阻塞，需先修复其未定义 helper 后运行。

## 提交
- Commit: `d6c483a` (`feat: define vocabulary search history domain`)

## 遗留问题
- 仅完成领域模型和仓储接口；Room entity/DAO、迁移和生产实现由后续 Task 处理。
- 工作树中存在其他任务预先产生的未提交变更，本次提交仅包含上述两个任务文件。
