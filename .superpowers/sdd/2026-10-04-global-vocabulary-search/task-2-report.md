# Task 2 报告：Room v23 搜索历史表与迁移

## 变更
- 新增 `VocabularySearchHistoryEntity`：复合主键 `(profileId, normalizedQuery)`，索引 `(profileId, lastSearchedAtEpochMillis)`。
- 新增 `InternalVocabularySearchHistoryDao`：按 profile 倒序分页、复合键查询、upsert、清空 profile。
- 新增 `RoomVocabularySearchHistoryRepository`：实体/领域映射；所有存储操作运行于注入 dispatcher；异常转换为 `RepositoryResult.Failure(StorageUnavailable)`；record 在 Room transaction 内累计次数并保留首次时间。
- `AppDatabase` 升级至 v23，注册 `MIGRATION_22_23`，迁移仅创建搜索历史表及索引。
- 新增 Repository Android 测试与 v22→v23 migration 测试。
- Room KSP 自动生成 `app/schemas/com.example.englishlearning.core.storage.AppDatabase/23.json`。

## 测试命令与输出
- 红灯尝试：`gradlew --project-dir D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:compileDebugAndroidTestKotlin --tests ...`；任务不支持 `--tests`，命令行参数错误（预期实现缺失也会导致测试无法编译）。
- `gradlew --project-dir D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:compileDebugAndroidTestKotlin`：`BUILD SUCCESSFUL`。

## 遗留问题
- 未运行 connectedDebugAndroidTest（按简报要求）。
