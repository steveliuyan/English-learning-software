# F1-07 学习记录与词书单词列表完成计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成并验收首页「学习记录」入口、今日已学、跨词书历史记录、当前词书全部单词及状态筛选，并保证详情页只读、不改动学习数据。

**Architecture:** 沿用当前未提交实现：`LearningRecordRepository` 负责只读领域接口，Room DAO 提供事件/计划/复习状态查询，`LearningRecordsViewModel` 做按标签懒加载与筛选缓存，`LearningRecordsScreen` 负责 Compose 渲染，`AppScreen` 负责 overlay、返回键和 `CardDetailScreen` 复用。新对话首先审查现有实现与未提交改动，不得重复创建同名文件或重做已完成逻辑。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room, Hilt, Coroutines, JUnit 5 JVM tests, AndroidX instrumentation.

**Spec:** `docs/superpowers/specs/2026-09-30-learning-records-design.md`；对应阶段规格 `docs/specs/01-vocabulary-learning-and-review.md` 的 F1-07 与 AC1-14–AC1-19；设计决策 `docs/decisions/2026-09-30-learning-records.md`。

## Global Constraints

- 先阅读 `AGENTS.md`、本计划、F1-07 规格和当前工作树状态，再修改代码。
- 词书、学习事件、复习状态、计划仍以本地数据库为权威；学习记录页面只能读，不能写任何学习事件、复习状态、计划或完成计数。
- 不新增 Room 表或迁移；当前数据库版本已经是 v22，F1-07 只使用既有表。
- 历史记录范围是当前 profile 的所有词书；学习日必须来自 `today_plans.localDate`，不能把事件时间按当前时区重新换算。
- 状态边界固定：无 `card_review_states` = 未学；`nextReviewAtEpochMillis <= now` = 待复习；大于 now = 学习中。
- 今日记录与历史记录同一 `(planId, cardId)` 只保留最早事件；排序必须是历史日期倒序、同日事件时间正序。
- 词卡无法解析时保留记录，显示 `cardId` 最后一个冒号后的单词，置灰且不可打开详情。
- 标签首次进入才加载；切换标签不重复读取；失败显示可重试错误；空状态只显示一句说明，不显示空表头或「暂无」占位。
- `CardDetailScreen` 的「重新学习」只有提供具体 callback 时才显示；F1-07 调用方不传 callback。
- Gradle 统一使用 `ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache --no-daemon --no-build-cache --console=plain`；长构建必须后台运行。
- 真机只使用 `assembleDebug assembleDebugAndroidTest`、`adb install -r -t` 和 `adb shell am instrument`；禁止 `connectedDebugAndroidTest`、卸载应用和清除数据。
- 真机测试前后必须比对 `english-learning.db`、`english-learning.db-wal`、`english-learning.db-shm` 三件套 MD5。
- 新增断言必须先红、后绿，并至少做一轮针对关键逻辑的变异测试，证明错误实现会变红。
- 本轮不提交、不推送，除非用户在后续单独明确要求。

---

## 当前工作树事实

以下文件已经存在于当前未提交改动中，新对话必须先逐个审查其实际内容：

- `app/src/main/java/com/example/englishlearning/learning/LearningRecordRepository.kt`
- `app/src/main/java/com/example/englishlearning/learning/RoomLearningRecordRepository.kt`
- `app/src/main/java/com/example/englishlearning/ui/LearningRecordsViewModel.kt`
- `app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt`
- `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt`
- `app/src/androidTest/java/com/example/englishlearning/ui/LearningRecordsScreenTest.kt`
- `app/src/test/java/com/example/englishlearning/learning/LearningRecordModelsTest.kt`
- `app/src/test/java/com/example/englishlearning/learning/RoomLearningRecordRepositoryTest.kt`
- `app/src/test/java/com/example/englishlearning/ui/LearningRecordsViewModelTest.kt`
- `docs/superpowers/specs/2026-09-30-learning-records-design.md`
- `docs/superpowers/plans/2026-09-30-learning-records.md`

已接线但必须重新核对生产路径的文件：

- `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`
- `app/src/main/java/com/example/englishlearning/ui/CardDetailScreen.kt`
- `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- `app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt`
- `app/src/androidTest/java/com/example/englishlearning/ui/CardDetailScreenTest.kt`

---

### Task 1: 审查现有实现并建立失败基线

**Files:**
- Read: `AGENTS.md`
- Read: `docs/specs/01-vocabulary-learning-and-review.md`
- Read: `docs/superpowers/specs/2026-09-30-learning-records-design.md`
- Read: 所有“当前工作树事实”中的实现与测试文件
- Modify: 不修改生产代码
- Test: 当前已有的 F1-07 测试文件

**Interfaces:**
- 消费：当前未提交实现及测试。
- 产出：一份明确的缺口清单，按“编译错误 / 逻辑错误 / 生产接线错误 / 测试缺口 / 真机未验收”分类。

- [ ] **Step 1: 记录工作树基线**

运行：

```bash
cd /d/EnglishLearningWorktrees/f1-04-unlock-verify
git rev-parse --show-toplevel
git rev-parse HEAD
git status --short
```

不得丢弃或覆盖现有未提交改动。

- [ ] **Step 2: 检查生产组装点**

确认 `MainActivity` 传入 `hiltViewModel<LearningRecordsViewModel>()`，并确认 `AppScreen` 的 ViewModel 参数、入口回调、overlay 分支和 BackHandler 均在生产路径传递；测试自行注入 ViewModel 不能替代生产接线验证。

- [ ] **Step 3: 运行当前 F1-07 JVM 测试**

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:testDebugUnitTest --tests '*LearningRecord*' --tests '*LearningRecords*' --no-daemon --no-build-cache --console=plain
```

记录真实结果。若已有测试全绿，不得据此宣布完成，继续检查规格覆盖和变异能力。

- [ ] **Step 4: 检查现有测试是否覆盖以下边界**

必须明确看到断言：`next == now` 为待复习、`next == now + 1ms` 为学习中、无状态为未学、同计划同词保留首次事件、历史日期使用计划日期、缺卡不可点、ViewModel 标签懒加载和失败重试。

---

### Task 2: 补齐领域转换与 Room 仓库契约

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/learning/LearningRecordRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/RoomLearningRecordRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt`
- Modify: 仅在实际需要时修改 `InternalTodayPlanDao.kt` 或 `InternalLearningStatsDao.kt`
- Modify: `app/src/test/java/com/example/englishlearning/learning/LearningRecordModelsTest.kt`
- Modify: `app/src/test/java/com/example/englishlearning/learning/RoomLearningRecordRepositoryTest.kt`

**Interfaces:**
- 保持 `LearningRecordRepository.today(profileId, planId)`、`history(profileId)`、`allWords(wordBookId, now)` 签名稳定，除非现有代码与规格发生明确冲突。
- 保持 `WordBookRecordStatus.Unlearned/Learning/Due` 与 `reviewStatus(nextReviewAt, now)` 的边界语义稳定。
- Room projection 只能向领域层输出必要字段，不把 DAO entity 暴露到 UI。

- [ ] **Step 1: 先补缺失的失败测试**

至少加入这些测试：

```kotlin
@Test
fun next_review_at_equal_to_now_is_due() {
    val now = Instant.parse("2026-10-04T02:00:00Z")
    assertEquals(WordBookRecordStatus.Due, reviewStatus(now, now))
}

@Test
fun next_review_at_one_millisecond_after_now_is_learning() {
    val now = Instant.parse("2026-10-04T02:00:00Z")
    assertEquals(WordBookRecordStatus.Learning, reviewStatus(now.plusMillis(1), now))
}

@Test
fun duplicate_same_plan_and_card_keeps_earliest_event() { /* 断言保留最早 occurredAt */ }
```

测试代码必须使用当前项目 JUnit 5 导入：`org.junit.jupiter.api.Test` 与 `org.junit.jupiter.api.Assertions.*`。

- [ ] **Step 2: 运行测试确认能够变红**

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:testDebugUnitTest --tests '*LearningRecordModelsTest' --tests '*RoomLearningRecordRepositoryTest' --no-daemon --no-build-cache --console=plain
```

- [ ] **Step 3: 修正最小领域实现**

确保：

```kotlin
fun reviewStatus(nextReviewAt: Instant?, now: Instant): WordBookRecordStatus = when {
    nextReviewAt == null -> WordBookRecordStatus.Unlearned
    nextReviewAt <= now -> WordBookRecordStatus.Due
    else -> WordBookRecordStatus.Learning
}
```

事件解析失败不能展示半份数据；未知 feedback 映射应进入仓库失败结果，而不是静默转成某个合法反馈。

- [ ] **Step 4: 修正 SQL projection**

今日与历史查询都必须 join `learning_events` 与 `today_plans`，使用 `today_plans.localDate`；历史查询按 `localDate DESC, occurredAtEpochMillis ASC`；全部单词读取当前词书 card id 顺序与对应复习状态。

- [ ] **Step 5: 验证存储异常与取消语义**

存储异常映射为 `RepositoryResult.Failure`；协程取消时只有当前协程仍 active 才映射存储失败，否则重新抛出取消异常。

- [ ] **Step 6: 运行领域和仓库测试至绿**

记录测试总数和失败数，不使用缓存结果冒充首次执行。

---

### Task 3: 修正 ViewModel 的懒加载、缓存和筛选

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningRecordsViewModel.kt`
- Modify: `app/src/test/java/com/example/englishlearning/ui/LearningRecordsViewModelTest.kt`

**Interfaces:**
- 保持 `selectTab(tab)`, `retry()`, `selectAllWordsFilter(filter)`, `openRow(row)`, `clearDetail()`, `load(profileId, planId?, activeWordBookId?)`。
- `LearningRecordsUiState` 必须区分每个 tab 的 loaded/loading/error，且筛选只从已缓存的全部单词列表派生。

- [ ] **Step 1: 写或补 ViewModel 失败测试**

覆盖：

1. `load` 只加载默认 tab；
2. 第一次切换到历史/全部单词才读取对应数据；
3. 重复切换已加载 tab 不重复读取；
4. 失败后 `retry` 只重试当前 tab；
5. 全部单词筛选不触发第二次仓库读取；
6. 缺卡行调用 `openRow` 后不产生 selected detail；
7. 新上下文加载会取消旧请求，旧结果不能覆盖新 profile/新词书状态。

- [ ] **Step 2: 运行测试确认缺口可变红**

使用 focused JVM 测试，确认至少一条新增断言在错误实现下失败。

- [ ] **Step 3: 实现最小修复**

不得把 IO dispatcher 重复塞进 ViewModel；仓库负责 IO，ViewModel 只负责状态机和生命周期。加载结果必须通过 generation/context 守卫，避免旧请求覆盖新页面。

- [ ] **Step 4: 运行 ViewModel 测试至绿**

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:testDebugUnitTest --tests '*LearningRecordsViewModelTest' --no-daemon --no-build-cache --console=plain
```

---

### Task 4: 完成 Compose 页面和详情导航验收

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/CardDetailScreen.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/LearningRecordsScreenTest.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/CardDetailScreenTest.kt`
- Modify: `app/src/test/java/com/example/englishlearning/ui/AppScreenProductionWiringTest.kt`（如现有签名守卫需要同步）

**Interfaces:**
- 页面入口语义：`today_plan_open_learning_records`。
- 页面语义：`learning_records_screen`, `learning_records_back`, `learning_records_tab_today`, `learning_records_tab_history`, `learning_records_tab_all_words`, `learning_records_loading`, `learning_records_unavailable`, `learning_records_retry`。
- 详情页 `onRelearn: (() -> Unit)? = null`；F1-07 调用方不传该 callback。

- [ ] **Step 1: 先补 instrumentation 失败断言**

至少覆盖：

1. 三个 tab 可切换；
2. 空状态只出现一句说明；
3. 加载态和错误态可见，点击重试触发回调；
4. 全部单词四个筛选及数量正确；
5. 合法行可点击，缺卡行置灰且不可点；
6. 传入 `onRelearn = null` 时「重新学习」不存在；显式传 callback 时仍存在；
7. 首页点击「学习记录」进入 overlay，Back 返回首页；
8. overlay 内打开词卡详情，Back 先返回记录页，再返回首页。

- [ ] **Step 2: 编译并确认失败原因属于缺失行为**

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:assembleDebugAndroidTest --offline --no-daemon --console=plain
```

- [ ] **Step 3: 修正 Compose 语义和渲染**

保持中文文案、现有 mint 主题和页面层级；不要引入搜索、收藏、统计、AI 查词或重新学习真实动作。

- [ ] **Step 4: 修正生产接线**

确认 `MainActivity` 传入 `hiltViewModel<LearningRecordsViewModel>()`，并同步更新任何签名白名单测试。测试夹具不得掩盖生产 ViewModel 缺失。

- [ ] **Step 5: 运行 focused instrumentation**

使用 `adb shell am instrument -w -e class ...`，禁止 `connectedDebugAndroidTest`。测试数据库必须是 fake/in-memory，不接触用户库。

---

### Task 5: 关键逻辑变异验证与全量 JVM 回归

**Files:**
- Modify: 仅在变异恢复时修改实现文件；不保留变异
- Evidence: `verification-logs/f1-07-learning-records-<date>/`

- [ ] **Step 1: 对状态边界打变异**

至少执行一个：把 `nextReviewAt <= now` 改成 `< now`，预期 `next == now` 测试变红。

- [ ] **Step 2: 对历史日期打变异**

把 SQL 使用计划 `localDate` 的字段替换为事件时间换算或让 join 条件失效，预期跨时区/日期分组测试变红。

- [ ] **Step 3: 对懒加载打变异**

移除 loaded guard 或让 `retry` 不 force reload，预期 ViewModel 调用次数测试变红。

- [ ] **Step 4: 还原并逐字节核对**

保存每个变异前的 md5；变异结束后 `diff` 与 md5 必须恢复，无变异代码进入后续构建。

- [ ] **Step 5: 执行全量 JVM 回归**

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:testDebugUnitTest --offline --no-daemon --no-build-cache --console=plain
```

预期：既有全量测试与 F1-07 新增测试全部通过，具体总数以实际输出为准。

---

### Task 6: APK 构建、真机只读验收和数据库证据

**Files:**
- Create: `verification-logs/f1-07-learning-records-<date>/` 下的构建日志、instrumentation 日志、截图和数据库快照
- Modify: 不修改生产代码，除非真机发现可复现的真实缺陷

- [ ] **Step 1: 构建两个 APK**

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --continue --offline --no-daemon --console=plain
```

- [ ] **Step 2: 取真机数据库三件套基线**

设备固定为 `bf353dda`；使用 `MSYS_NO_PATHCONV=1 adb exec-out run-as ... cat ...` 拉回 db/wal/shm，记录 MD5、大小和 `PRAGMA user_version`。不得卸载或清除应用。

- [ ] **Step 3: 安装并运行 focused instrumentation**

```bash
MSYS_NO_PATHCONV=1 adb install -r -t app/build/outputs/apk/debug/app-debug.apk
MSYS_NO_PATHCONV=1 adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
MSYS_NO_PATHCONV=1 adb shell appops set com.example.englishlearning 10021 allow
MSYS_NO_PATHCONV=1 adb shell am instrument -w -e class com.example.englishlearning.ui.LearningRecordsScreenTest,com.example.englishlearning.ui.CardDetailScreenTest,com.example.englishlearning.ui.AppScreenTest com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
```

实际执行前确认测试类存在并按当前包名调整；不要运行整包连接测试任务。

- [ ] **Step 4: 人工真机走查**

按顺序确认：

1. 首页点击「学习记录」进入页面；
2. 今日已学显示当前计划已提交词及反馈；
3. 历史记录跨至少两个学习日分组，日期来自计划日期；
4. 全部单词按当前词书顺序展示；
5. 全部/未学/学习中/待复习筛选数量与行数一致；
6. 点击正常词行打开纯查看详情；详情前后不改变学习计数；
7. 缺卡行置灰、不可打开；
8. 返回键顺序为详情 → 学习记录 → 首页。

- [ ] **Step 5: 取证数据库未被只读页面修改**

打开学习记录、切换三个 tab、打开和退出详情后，再次拉取三件套。db/wal/shm MD5 必须逐字节一致；若不一致，停止宣布完成并定位写入来源。

- [ ] **Step 6: 归档结果**

报告必须包含：代码文件、JVM 总数、变异 RED 结果、APK 构建结果、真机测试总数、真机 UI 走查结果、三件套 MD5 前后对照、未覆盖项和剩余风险。

---

## 交付判定

只有同时满足以下条件，才能在新对话中说 F1-07 完成：

1. F1-07 规格 AC1-14–AC1-19 全部有代码和测试映射；
2. 全量 JVM 测试通过；
3. 关键状态、历史日期、懒加载逻辑的变异均能使错误实现变红，且已还原；
4. APK 与 androidTest APK 构建成功；
5. focused instrumentation 全绿；
6. 真机人工走查通过；
7. 学习记录只读操作前后用户库三件套 MD5 一致；
8. 未运行 `connectedDebugAndroidTest`，未卸载应用，未清除用户数据；
9. 本轮未提交、未推送，等待用户单独确认。

## 计划自审

- 规格覆盖：Task 2 覆盖历史/状态/缺卡/去重，Task 3 覆盖懒加载/缓存/错误重试，Task 4 覆盖三 tab/筛选/详情/返回，Task 5 覆盖变异，Task 6 覆盖真机只读验收。
- 已排除：搜索、离线查词、AI 查词、拍照翻译、统计、收藏、错词本、重新学习真实动作。
- 无新增 Room schema 或迁移。
- 无 `TBD`、`TODO` 或未定义接口占位；所有下一步均指向现有文件和明确命令。
- 现有实现已部分接线，因此执行者必须先审查实际 diff，不能按“新建全部文件”执行。
