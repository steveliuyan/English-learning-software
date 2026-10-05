# 本地词条搜索索引实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将全量词汇搜索从每次扫描所有词书改为查询本地 Room 词条索引，并保证词书导入、更新、删除及生词操作不触发不必要的词书扫描。

**Architecture:** 新增独立的 `VocabularySearchIndexEntity` 与 DAO，以 `(wordBookId, cardId)` 唯一定位卡片，保存查询和展示所需的词条快照；词书安装/导入成功后由索引服务按词书原子重建，更新和删除同步维护。`SearchVocabularyUseCase` 只读取索引、学习状态和生词状态，不再调用 `WordCardSource`；旧 `WordBookSearchViewModel` 的生词操作只对当前结果做局部状态更新。

**Tech Stack:** Kotlin、Room、Hilt、Coroutines、JUnit 5、AndroidX Room migration tests。

**Spec:** `docs/superpowers/specs/2026-10-04-global-vocabulary-search-design.md`

## 实际落地的接口（实施后回填，以此为准）

索引**一次存储完整词卡快照**，查询路径因此完全不碰词书包：

```kotlin
data class VocabularySearchIndexEntity(
    val wordBookId: String, val cardId: String,
    val wordBookName: String, val dataVersion: String,
    val lemma: String, val normalizedLemma: String,
    val normalizedPhrases: String, val cardJson: String,
)

interface VocabularySearchIndexRepository {
    suspend fun replaceWordBook(wordBookId: String, wordBookName: String, dataVersion: String, cards: List<WordCard>): RepositoryResult<Unit>
    suspend fun deleteWordBook(wordBookId: String): RepositoryResult<Unit>
    suspend fun search(normalizedQuery: String, limit: Int): RepositoryResult<List<VocabularySearchIndexEntry>>
    suspend fun indexedVersions(): RepositoryResult<Map<String, String>>   // wordBookId -> dataVersion
    suspend fun deleteMissing(keepWordBookIds: Collection<String>): RepositoryResult<Unit>
}
```

`SearchVocabularyUseCase(history, index, clock, maxResults)` —— **不再持有 `LearningProfileRepository` 与 `WordCardSource`**。
词书可见性在建索引时已确定，查询侧不需要再列一遍词书；`SearchVocabularyUseCaseTest`
用反射断言构造器里不存在 `WordCardSource`，让「退回全量扫描」这件事变成编译期级别的红线。

`RefreshVocabularySearchIndexUseCase(profiles, cards, index, bundledIds, importedIds)` 是索引的**唯一建立与维护入口**：
按 `dataVersion` 判断过期，逐册原子重建，最后用 `deleteMissing` 清理已删除词书的残留行。

装配点**三处，缺一不可**（2026-10-05 真机实证修正）：

1. **`AppScreen` 进入 `AppUiState.Ready` 后按 `state.profile.id` 触发一次**（用例由 `MainActivity`
   `@Inject` 并作为可空参数传入）。**这是主入口**：老用户不会再打开学习设置页，若只挂在那里，
   索引永远为空——真机实测到 `user_version = 24`、表已建但 **0 行**，主页查词任何词都查不到。
2. `LearningSetupViewModel.load`：覆盖学习设置页里的词书新增/删除（用户主动改词书后的收敛）。
3. `WordBookTransferViewModel.importFrom`：导入成功即建索引（登记失败不建，否则会被下次刷新清掉）。

`WordCardSource`（`BundledWordBookSource` / `ImportedWordBookSource`）内部都已 `withContext(io)`，
所以在 Main 调度的协程里直接 `invoke()` 不会阻塞主线程。

## 已知性能边界（如实记录）

- **空间代价（2026-10-05 真机实测）**：索引每行存完整词卡 JSON 快照，45,290 行 / 平均 595 B /
  JSON 合计 **25.7 MB**；叠加 SQLite 页面与索引开销后**用户库从 270 KB 涨到 46.3 MB**。
  这些内容本就存在于 assets 词书包，等于复制了一份进数据库。**待决策**的收敛方向：
  ① 只存查询/展示必需字段，点开词卡按 id 回源取完整卡片（预计 ~8 MB 量级）；
  ② 索引放独立 DB 文件（或 `ATTACH`）便于整体重建；③ 接受现状。
- 完全匹配与前缀匹配走 `normalizedLemma` 上的 B 树，是真索引命中。
- **包含匹配仍是 `LIKE '%q%'` 的表扫描**，扫的是索引表而不是词书包——省掉了逐册解析词书包与图片哈希，
  但不是 O(log n)。若后续要连包含匹配也走索引，需引入 SQLite FTS5。
- 短语「恰好等于查询词」的卡片由包含级查询带回，因此当包含级候选超过 `limit` 时，
  它可能排在截断之外。旧实现（全量扫描后统一排序）没有这个边界。取舍理由：为短语单独建表
  会把复杂度抬高一个量级，而短语精确等于查询是极罕见输入。详见下方 Task 4 的说明。

## Global Constraints

- 搜索覆盖所有可见内置词书和导入词书，不受活动词书限制。
- 查询阶段不得调用 `WordCardSource.cardIds()` 或 `WordCardSource.cards()`。
- 索引不改变学习事件、FSRS、今日计划或搜索历史语义。
- 词条快照只保存非敏感词书/卡片内容；不保存 API Key 或任意网络数据。
- 现有 Room v23 数据必须保留；索引新增使用 v23→v24 迁移。
- 所有新行为先写测试并确认 RED，再写最小实现；关键语义做可变红验证。
- 真机只允许 `adb install -r -t` 与 `am instrument`；禁止 `connectedDebugAndroidTest`、卸载和清除用户数据。
- 不提交、不推送，除非用户另行明确要求。

---

### Task 1: 定义词条索引模型、DAO 和索引服务接口

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/VocabularySearchIndex.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/VocabularySearchIndexEntity.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalVocabularySearchIndexDao.kt`
- Create: `app/src/test/java/com/example/englishlearning/learning/VocabularySearchIndexTest.kt`

**Interfaces:**
- `VocabularySearchIndexEntry(wordBookId: String, wordBookName: String, card: WordCard)`。
- `VocabularySearchIndexRepository`：
  - `suspend fun replaceWordBook(wordBookId: String, wordBookName: String, cards: List<WordCard>): RepositoryResult<Unit>`
  - `suspend fun deleteWordBook(wordBookId: String): RepositoryResult<Unit>`
  - `suspend fun search(normalizedQuery: String, limit: Int): RepositoryResult<List<VocabularySearchIndexEntry>>`
- DAO 必须提供按词书删除、批量插入、按标准化 lemma 读取候选和按 `(wordBookId, cardId)` 查询展示数据的能力。

- [ ] **Step 1: 写 RED 测试**
  - 用 fake DAO/repository 验证 `replaceWordBook` 先清除旧词条再写入新快照。
  - 验证同一词书更新后旧 cardId 不再可检索。
  - 验证 `deleteWordBook` 只删除目标词书。
  - 验证查询结果保留两本词书中相同 lemma 的两个上下文。

- [ ] **Step 2: 运行 focused JVM 测试确认 RED**
  - 运行：`./gradlew.bat :app:testDebugUnitTest --tests '*VocabularySearchIndexTest*' --no-daemon --no-build-cache --console=plain`
  - 预期：索引模型/接口不存在或行为断言失败，而非测试代码编译错误。

- [ ] **Step 3: 实现最小模型、DAO 契约和 repository**
  - Entity 保存 `wordBookId`、`cardId`、`wordBookName`、`lemma`、`normalizedLemma`、`ipa`、`partOfSpeech`、`meaning`、`phrases` 等当前 `WordCard` 展示所需字段。
  - 主键使用 `(wordBookId, cardId)`；为 `normalizedLemma` 建索引，并为 `(wordBookId, normalizedLemma)` 建复合索引。
  - `replaceWordBook` 在同一 Room 事务中执行 delete + insert；空卡片合法，表示该词书索引被清空。
  - 对 placeholder cardId 拒绝写入。

- [ ] **Step 4: 运行 focused 测试确认 GREEN**
  - 运行同一 focused 命令，确认全部通过。
  - 变异验证：去掉 replace 前的 delete，旧 cardId 断言必须失败；恢复实现并确认通过。

---

### Task 2: 接入 Room v24 与迁移测试

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/RoomVocabularySearchIndexRepository.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseVocabularySearchIndexMigrationTest.kt`
- Create: `app/schemas/com.example.englishlearning.core.storage.AppDatabase/24.json`
- Modify: existing database test factory/provider files as required

**Interfaces:**
- Produces `AppDatabase` version 24, `MIGRATION_23_24`, DAO accessor and singleton `VocabularySearchIndexRepository` provider。

- [ ] **Step 1: 写迁移和 Room repository RED 测试**
  - 从 v23 fixture 打开 v24，断言既有 `vocabulary_search_history` 和 `learning_settings` 数据保留。
  - 断言索引表、主键和索引存在。
  - 使用独立数据库名，不访问 `english-learning.db`。

- [ ] **Step 2: 运行 focused migration 测试确认 RED**
  - 运行 `:app:assembleDebugAndroidTest` 和指定 `am instrument` 类；若尚无 APK，先只运行 JVM 编译检查。

- [ ] **Step 3: 实现 v23→v24 migration 与 DI**
  - migration 使用 `CREATE TABLE IF NOT EXISTS` 创建索引表和 Room 需要的普通/唯一索引。
  - 将 migration 加入 `MIGRATIONS` 数组，更新 schema JSON。
  - Repository 在 `io` dispatcher 中映射异常为 `RepositoryResult.Failure`，不吞掉取消异常。

- [ ] **Step 4: 运行迁移测试确认 GREEN**
  - 断言重复迁移不会破坏历史和索引数据。
  - 变异验证：移除 migration 数组中的 `MIGRATION_23_24`，迁移测试必须失败；恢复后通过。

---

### Task 3: 建立词书索引构建与维护链路

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/BuildVocabularySearchIndexUseCase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/SeedWordBooksUseCase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/WordBookImportUseCase.kt` or actual import use case discovered in repository
- Modify: `app/src/main/java/com/example/englishlearning/learning/WordBookDeletionService.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Create/modify: corresponding JVM tests

**Interfaces:**
- `BuildVocabularySearchIndexUseCase`：`suspend fun rebuild(wordBook: WordBook): RepositoryResult<Unit>`，内部读取该词书卡片、过滤 placeholder、调用 `replaceWordBook`。
- 删除服务必须在词书元数据删除成功后删除对应索引；若索引删除失败，返回失败且不宣称完整删除。

- [ ] **Step 1: 根据当前实际导入入口补写 RED 测试**
  - 读取现有导入和内置词书注册流程，定位真实成功提交点。
  - 测试内置词书注册后索引构建一次。
  - 测试导入词书成功后建立索引，导入失败不产生索引。
  - 测试同一词书更新重建后不残留旧卡片。
  - 测试删除词书同步删除索引。

- [ ] **Step 2: 运行 focused 测试确认 RED**
  - 运行对应测试类；确认失败原因是缺少索引接线，而不是 fake 配置错误。

- [ ] **Step 3: 实现最小构建与维护接线**
  - 保持现有词书解析与完整性校验作为唯一内容来源。
  - 构建成功后一次性写入索引，不在每次查询时解析包。
  - 任何内容不完整、placeholder-only 或解析失败都返回失败并保留旧索引，避免把不完整内容覆盖进可用索引。
  - 删除和更新按现有事务/失败语义接线，不扩大到无关数据。

- [ ] **Step 4: 运行测试并做变异验证**
  - 变异：跳过导入后的 rebuild，导入后可检索测试必须失败。
  - 变异：删除服务跳过 index delete，删除后不可检索测试必须失败。
  - 恢复并确认 focused 全绿。

---

### Task 4: 将 `SearchVocabularyUseCase` 切换为索引查询

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/learning/SearchVocabularyUseCase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Modify: `app/src/test/java/com/example/englishlearning/learning/SearchVocabularyUseCaseTest.kt`

**Interfaces:**
- Use case 新增依赖 `VocabularySearchIndexRepository`。
- `SearchVocabularyResult` 继续提供完整 `WordCard`、`wordBookName` 和搜索次数所需代表信息，调用方无需感知索引表。

- [ ] **Step 1: 先补 RED 测试**
  - fake `WordCardSource` 在 `cardIds/cards` 被调用时抛出异常或记录次数。
  - fake index 返回精确、前缀、包含及两个同 lemma 词书结果。
  - 断言搜索结果正确排序、最多 30 条、保留词书上下文、占位卡不返回。
  - 断言历史记录和已有搜索次数语义保持不变。

- [ ] **Step 2: 运行 focused 测试确认 RED**
  - 运行 `:app:testDebugUnitTest --tests com.example.englishlearning.learning.SearchVocabularyUseCaseTest`。
  - 预期旧实现因调用 `WordCardSource` 或无法注入 index fake 而失败。

- [ ] **Step 3: 实现索引查询和状态补充**
  - 查询前规范化；空查询直接返回空且不访问 index、profiles、cards 或 history.record。
  - 通过 index 得到候选后计算 0/1/2 匹配等级；对 card lemma/phrases 保留现有规则。
  - 通过 events 补充学习状态、vocabulary.contains 补充生词状态；这些读取不重新加载卡片。
  - 成功结果继续记录历史；历史写入失败按现有已批准语义返回可重试失败状态，不伪造计数。

- [ ] **Step 4: 运行 focused 测试和四类变异**
  - 活动词书过滤变异必须使“非活动词书可搜索”失败。
  - 去掉大小写/空白规范化必须使规范化测试失败。
  - 将候选查询替换为 `WordCardSource` 扫描必须使 no-scan 断言失败。
  - 恢复后 focused 全绿。

---

### Task 5: 保持旧词书搜索页的职责边界并完成生词回归

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordBookSearchViewModel.kt`
- Modify: `app/src/test/java/com/example/englishlearning/ui/WordBookSearchViewModelVocabularyTest.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordBookSearchScreen.kt` only if主页全量搜索复用了旧入口

**Interfaces:**
- `toggleVocabulary(result, now)` 只调用 `VocabularyRepository.add/remove`，并局部更新当前 `Ready.results`。
- 主页全量搜索不提供旧页面的 `browse()`；浏览词书仅保留在明确的旧词书页面语义中。

- [ ] **Step 1: 保留并扩展 RED 断言**
  - 已有测试必须断言加入和移除均不调用 `cardIds/cards`。
  - 增加 profile、wordBookId、cardId 精确传递断言。
  - 增加“状态只更新目标卡片，不重跑查询”的断言。

- [ ] **Step 2: 运行 focused 测试确认旧行为可被捕获**
  - 临时恢复 `lastRequest?.let(::runQuery)`，测试必须出现 card source 调用；随后立即恢复修复。

- [ ] **Step 3: 清理主页全量搜索中的 browse 文案/入口**
  - 只移除主页全量搜索中的“浏览词书”入口和说明，不删除旧词书页面必要的浏览能力。
  - 增加页面级断言，避免主页出现“浏览词书”。

- [ ] **Step 4: 运行测试确认 GREEN**
  - focused JVM 全绿；不触碰无关导航和详情接线。

---

### Task 6: 全量回归、APK 构建与真机安全验证

**Files:**
- Create: `outputs/global-vocabulary-search-index-20261004/REPORT.md`
- Modify: `D:/workbjddy-project/手机学习英语软件/.workbuddy/memory/2026-10-04.md`

- [ ] **Step 1: 检查工作树和差异边界**
  - 运行 `git status --short`、`git diff --check`。
  - 确认不覆盖受保护文件 `app/gradle.lockfile`、`core/error/AppError.kt`、`ui/AppViewModel.kt` 及无关改动。

- [ ] **Step 2: 运行 JVM 回归**
  - 后台运行 `:app:testDebugUnitTest --continue --no-daemon --no-build-cache --console=plain`。
  - 记录真实执行与缓存命中任务，不把缓存误报为重新执行。

- [ ] **Step 3: 构建两个 APK**
  - 后台运行 `:app:assembleDebug :app:assembleDebugAndroidTest --continue --no-daemon --no-build-cache --console=plain`。
  - 不运行 `connectedDebugAndroidTest`。

- [ ] **Step 4: 运行真机 focused instrumentation**
  - 先用 `MSYS_NO_PATHCONV=1 adb exec-out run-as ... cat` 读取用户数据库三件套基线。
  - 使用 `adb install -r -t` 安装两个 APK，不卸载、不清除数据。
  - 使用 `am instrument -w -e class ...` 执行迁移/搜索 focused 测试。
  - 读取三件套并逐字节比对；若 `.db-shm` 受 SQLite 共享内存影响，重复稳定读取后再作结论。

- [ ] **Step 5: 形成报告**
  - 报告包含：测试命令、通过数量、变异红灯证据、APK 路径、真机设备、数据库 MD5 前后值、任何未完成项。
  - 明确当前不提交、不推送。
