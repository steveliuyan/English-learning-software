# 全量词汇搜索实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在主页提供不受活动词书限制的全量词汇搜索，并本地保存搜索历史、累计搜索次数及其显示开关。

**Architecture:** 复用现有 `WordCardSource`、词书可见性和 `CardDetailScreen`，新增独立的搜索历史 Room 表与仓储；用例负责全量扫描、规范化、排序和计数关联，ViewModel 负责取消旧请求、历史状态和 UI 交互，`AppScreen` 负责主页入口与详情跳转。

**Tech Stack:** Kotlin、Jetpack Compose、Room、Hilt、Kotlin Coroutines、JUnit 5 JVM tests、AndroidX Compose instrumentation。

**Spec:** `docs/superpowers/specs/2026-10-04-global-vocabulary-search-design.md`

## Global Constraints

- 搜索范围覆盖所有可用词汇，不受当前活动词书限制。
- 内置正式词书与用户导入词书统一搜索；占位卡片不参与结果。
- 搜索历史仅本地保存，不写入学习事件，不修改 FSRS 或今日计划。
- 搜索次数关闭只隐藏文案，不删除或重置统计数据。
- Room 数据库由 v22 升级至 v23，迁移保留既有数据。
- 新增行为必须先测试红灯，再写最小实现；关键语义必须做变异测试。
- 真机只允许 `adb install -r -t` 与 `am instrument`，禁止 `connectedDebugAndroidTest`、卸载和清除用户数据。

---

### Task 1: 定义搜索历史领域模型与失败安全接口

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/VocabularySearchHistory.kt`
- Create: `app/src/test/java/com/example/englishlearning/learning/VocabularySearchHistoryTest.kt`

**Interfaces:**
- Produces `VocabularySearchHistory`, `SearchRepresentative`, `VocabularySearchHistoryRepository`。
- `record` 必须以规范化查询作为唯一键语义；失败使用既有 `RepositoryResult`。

- [ ] **Step 1: Write the failing test**

覆盖：首次记录为 1 次；重复记录累加；同一 profile 隔离；空/空白查询不构造有效记录；关闭显示配置不改变统计模型。

- [ ] **Step 2: Run focused JVM test and verify red**

运行 `:app:testDebugUnitTest --tests '*VocabularySearchHistoryTest'`，预期因领域类型和接口不存在失败。

- [ ] **Step 3: Write minimal model and repository interface**

定义不可变数据类、`RepositoryResult` 返回类型和四个挂起方法：`list`、`record`、`find`、`clear`。规范化函数保持为纯 Kotlin 可测试函数。

- [ ] **Step 4: Run focused test and verify green**

确认领域模型测试通过，并执行一次变异：将累计逻辑预期改为覆盖 1，重复搜索断言必须失败。

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/example/englishlearning/learning/VocabularySearchHistory.kt app/src/test/java/com/example/englishlearning/learning/VocabularySearchHistoryTest.kt && git commit -m "feat: define vocabulary search history domain"`

### Task 2: 增加 Room v23 搜索历史表与迁移

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/VocabularySearchHistoryEntity.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalVocabularySearchHistoryDao.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/RoomVocabularySearchHistoryRepository.kt`
- Create: `app/src/test/java/com/example/englishlearning/learning/RoomVocabularySearchHistoryRepositoryTest.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseSearchHistoryMigrationTest.kt`
- Create: `app/schemas/com.example.englishlearning.core.storage.AppDatabase/23.json`

**Interfaces:**
- Consumes Task 1 repository interface.
- Produces Room implementation with atomic upsert/count update, profile-scoped list/clear, and `MIGRATION_22_23`.

- [ ] **Step 1: Write failing repository tests**

使用 Room in-memory database 或现有测试工厂，断言首次 `record` 的 `searchCount=1`，第二次同 profile 同 normalized query 为 2，其他 profile 不可见，`clear(profile)` 不影响其他 profile。

- [ ] **Step 2: Run tests and verify red**

预期 Room entity、DAO、repository 和 database version 尚不存在。

- [ ] **Step 3: Implement entity, DAO, repository, migration**

DAO 提供按 profile 最近时间查询、按主键查找、upsert、按 profile 删除；repository 在 IO dispatcher 中统一映射存储异常为失败。数据库版本升至 23，迁移只 `CREATE TABLE IF NOT EXISTS` 和索引，不触碰既有表。

- [ ] **Step 4: Add migration test before declaring green**

从 v22 fixture 打开到 v23，断言既有表和行保留、新表存在、重复迁移不破坏数据。

- [ ] **Step 5: Run repository and migration tests**

运行 focused JVM 与 focused instrumentation；迁移测试必须独立库名，不能访问用户库。

- [ ] **Step 6: Commit**

`git add app/src/main app/src/test app/src/androidTest app/schemas && git commit -m "feat: persist vocabulary search history"`

### Task 3: 扩展学习设置中的搜索次数显示开关

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/learning/LearningSettings.kt` or current settings model location
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/entity/LearningSettingsEntity.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/LearningSettingsRepository.kt`
- Modify: existing settings ViewModel and screen files
- Test: existing learning settings JVM and Compose tests

**Interfaces:**
- Produces `LearningSettings.showVocabularySearchCount: Boolean`, default `true`.
- Existing callers remain source-compatible through default value or explicit migration mapping.

- [ ] **Step 1: Write failing setting tests**

断言默认开启；保存关闭后重新读取仍为关闭；关闭设置不删除搜索历史记录。

- [ ] **Step 2: Run focused settings tests and verify red**

预期新属性尚不存在或 round-trip 断言失败。

- [ ] **Step 3: Implement minimal setting persistence**

按现有学习设置迁移策略增加字段和默认值；若实体 schema 变更已由数据库 v23 承载，使用同一迁移增加非空列并填写默认值。

- [ ] **Step 4: Add settings UI and verify green**

设置页加入“显示搜索次数”开关，保存失败保持原值并显示现有错误状态。

- [ ] **Step 5: Commit**

`git add app/src/main app/src/test app/src/androidTest app/schemas && git commit -m "feat: add search count visibility setting"`

### Task 4: 实现全量搜索用例

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/SearchVocabularyUseCase.kt`
- Create: `app/src/test/java/com/example/englishlearning/learning/SearchVocabularyUseCaseTest.kt`

**Interfaces:**
- Consumes `LearningProfileRepository`, `WordCardSource`, bundled/imported ID sources, `ClockProvider`, `VocabularySearchHistoryRepository`。
- Produces `SearchVocabularyResult`, `SearchVocabularyItem` 和 `suspend operator fun invoke(profileId: String, query: String): RepositoryResult<...>`。

- [ ] **Step 1: Write failing tests**

提供两本词书（其中一本不是活动词书）、同 lemma 的不同词书卡片和前缀/包含候选，断言全量结果、词书上下文、精确优先、大小写/空白规范化、占位卡排除及结果上限。

- [ ] **Step 2: Run focused test and verify red**

预期 use case 和结果类型不存在。

- [ ] **Step 3: Implement minimal full scan and ranking**

遍历可见词书，调用 `cardIds` 与 `cards`，拒绝占位 ID 和不完整内容；使用匹配等级 0/1/2 排序，保留同 lemma 的多词书结果，最多返回 30 条。

- [ ] **Step 4: Integrate history lookup and record**

成功搜索后关联历史次数并记录一次；历史写入失败不得抹掉已经得到的结果，返回结果时携带可行动的 history-save failure 状态。

- [ ] **Step 5: Run tests and mutation checks**

把全量书遍历改为活动书过滤，测试必须失败；去掉规范化或将计数重置为 1，相关断言必须失败。

- [ ] **Step 6: Commit**

`git add app/src/main/java/com/example/englishlearning/learning/SearchVocabularyUseCase.kt app/src/test/java/com/example/englishlearning/learning/SearchVocabularyUseCaseTest.kt && git commit -m "feat: search all vocabulary"`

### Task 5: 实现搜索 ViewModel 与搜索页面

**Files:**
- Create or modify: `app/src/main/java/com/example/englishlearning/ui/GlobalVocabularySearchViewModel.kt`
- Create or modify: `app/src/main/java/com/example/englishlearning/ui/GlobalVocabularySearchScreen.kt`
- Create: `app/src/test/java/com/example/englishlearning/ui/GlobalVocabularySearchViewModelTest.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/ui/GlobalVocabularySearchScreenTest.kt`

**Interfaces:**
- ViewModel 提供 `query`, `state`, `setProfile`, `updateQuery`, `search`, `openHistory`, `retry`, `clearHistory`。
- Screen 接收 state 与回调，不直接访问 Room。

- [ ] **Step 1: Write failing ViewModel tests**

覆盖空输入展示历史、查询取消旧任务、失败重试、清空历史失败保留列表和 profile 切换隔离。

- [ ] **Step 2: Run focused JVM test and verify red**

预期 ViewModel 不存在或状态不完整。

- [ ] **Step 3: Implement state machine**

使用 generation token 和可取消 Job，保证旧请求不能覆盖新请求；空输入只取历史；结果保留词书名与次数显示模型。

- [ ] **Step 4: Implement Compose screen**

实现输入框、历史列表、结果列表、加载、空结果、失败/重试和清空历史；次数文案由 `showVocabularySearchCount` 决定。

- [ ] **Step 5: Run JVM and Compose tests**

确认三种状态、开关文案和点击回调均通过。

- [ ] **Step 6: Commit**

`git add app/src/main app/src/test app/src/androidTest && git commit -m "feat: add global vocabulary search screen"`

### Task 6: 接入主页、详情、Hilt 与设置入口

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/MainActivity.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: existing settings screen/ViewModel
- Modify: production wiring tests and `AppScreenTest.kt`

**Interfaces:**
- Hilt supplies repository, use case, ViewModel and setting dependencies.
- AppScreen search result callback sets existing `selectedSearchCard` and opens `CardDetailScreen`.

- [ ] **Step 1: Write failing production wiring and Compose tests**

断言 MainActivity 传入全量搜索 ViewModel；主页入口可打开；结果点击进入详情；返回键关闭搜索 overlay；设置开关可见。

- [ ] **Step 2: Run tests and verify red**

预期参数或入口不存在。

- [ ] **Step 3: Implement minimal production wiring**

沿现有 Hilt provider、overlay 和 BackHandler 模式接线；不要复制词卡详情逻辑；主页只负责打开搜索和接收选中词卡。

- [ ] **Step 4: Run focused instrumentation**

使用 `am instrument -e class ...`，不使用 connected task；修复真实生产组装点问题。

- [ ] **Step 5: Commit**

`git add app/src/main app/src/test app/src/androidTest && git commit -m "feat: wire global search into app"`

### Task 7: 全量回归、变异验证与真机交付证据

**Files:**
- Create: `outputs/global-vocabulary-search-20261004/REPORT.md`
- Modify: `D:/workbjddy-project/手机学习英语软件/.workbuddy/memory/2026-10-04.md`

- [ ] **Step 1: Run static checks**

运行 `git diff --check`，确认受保护文件和无关改动未被覆盖。

- [ ] **Step 2: Run JVM regression and APK builds**

后台运行 `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest`，均加 `--continue --no-daemon --no-build-cache --console=plain`。

- [ ] **Step 3: Run mutation tests**

分别或一次性注入并记录：活动词书过滤、规范化、累计计数、开关删除历史四类变异；每个必须得到可归因红灯，然后恢复并重新确认绿灯。

- [ ] **Step 4: Capture database baseline**

使用 `MSYS_NO_PATHCONV=1 adb exec-out run-as ... cat` 读取 `.db/.db-wal/.db-shm`，记录 SHA-256。

- [ ] **Step 5: Install and run focused instrumentation**

使用 `adb install -r -t` 安装两个 APK，运行主页搜索、设置和迁移 focused classes；不卸载、不清除数据。

- [ ] **Step 6: Capture post-test database and compare**

再次读取三件套，逐字节比对；搜索功能的测试库使用独立数据库，不能污染用户数据库。

- [ ] **Step 7: Write report and update memory**

报告记录测试命令、结果、变异红灯、APK 构建、真机用例和三件套哈希。只在所有验收通过后标记功能完成。

- [ ] **Step 8: Commit**

`git add outputs/global-vocabulary-search-20261004/REPORT.md && git commit -m "test: verify global vocabulary search"`
