# 新词交错顺序 + 词书页渐进发布 实现计划

**Goal:** 消灭「背完整本 A 才见 B」的字母序死啃，并让词书选取页首帧不再空白后满屏蹦出。

**Architecture:** 顺序改动落在 `learning` 域的纯函数与新词取词端口实现上，UI 零改动；预加载改动落在 `LearningSetupViewModel` 的状态发布节奏 + `AppScreen` 的占位呈现上，域层零改动。两者共享「不动 Room schema、不动复习排程」的边界。

**Tech Stack:** Kotlin、Jetpack Compose、Room（本批不迁移）、JUnit 5 + kotlinx-coroutines-test（JVM 单测）。

**Spec:** `docs/specs/01-vocabulary-learning-and-review.md` F1-01/F1-02（本批已修订，新增 AC1-23～AC1-27）
**决策:** `docs/decisions/2026-10-17-new-word-ordering-and-setup-preload.md`

## Global Constraints

- 验收门禁 = `:app:testDebugUnitTest` 全绿且基线只增不减（detekt/ktlint 不是门禁）。
- 单测基线：起点 **1471**（main tip `045c316`），只增不减。
- 不做 Room 迁移、不加 schema 列、不改备份实体（决策「明确不做」）。
- 不改 `dueCardIds`、`SubmitCardFeedbackUseCase`、`UnlockPolicy`、`GetOrCreateTodayPlanUseCase` 的快照与排程语义。
- TDD：每条新测试先观察 RED（失败原因必须是「功能缺失」），再写最小实现。
- 提交逐文件 `git add`，带 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`，禁 `-A`/`.`/`--amend`/`--no-verify`。
- 真机 `bf353dda` 只装不测；connected 只在 `emulator-5574`，绝禁 `connectedDebugAndroidTest`。

---

### Task 1: 决策文档 + Spec 修订 + 矩阵证据行（纯文档，先落地）

**Files:**
- Create: `docs/decisions/2026-10-17-new-word-ordering-and-setup-preload.md`
- Modify: `docs/specs/01-vocabulary-learning-and-review.md`（F1-01 渐进发布、F1-02 交错序、AC1-23～AC1-27）
- Modify: `docs/verification/ac-traceability-matrix.md`（AC1-23～AC1-27 五行，证据列写本计划将要产出的测试 FQCN#method）
- Create: `docs/superpowers/plans/2026-10-17-word-ordering-and-setup-preload.md`（本文件）

**Interfaces:**
- Produces: AC1-23～AC1-27 的验收口径，以及测试方法名契约（Task 2/3 必须逐名兑现）。

- [ ] **Step 1**：跑 `verifyAcTraceabilityMatrix`，确认新增 5 行让根任务转绿（specs 新增 id 都有证据行）。
- [ ] **Step 2**：提交四个文档文件（`docs(ordering): 决策 + Spec 交错序与渐进发布 + 矩阵 AC1-23~27`）。

### Task 2: 确定性交错序纯函数（全 JVM TDD）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/NewWordOrdering.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/StoredPlanCardSource.kt:30-36`
- Create: `app/src/test/java/com/example/englishlearning/learning/NewWordOrderingTest.kt`
- Modify: `app/src/test/java/com/example/englishlearning/learning/StoredPlanCardSourceTest.kt`（旧字母序断言改写）

**Interfaces:**
- Produces: `object NewWordOrdering { fun order(cardIds: List<String>): List<String> }`
  —— 输入任意顺序、输出置换；`StoredPlanCardSource.newCardIds` = `order(all).filterNot(reviewed).take(limit)`。
- Consumes: `WordCardSource.cardIds`、`LearningEventRepository.reviewedCardIds`（签名不变）。

- [ ] **Step 1：写 RED 测试**（`NewWordOrderingTest`，方法名须与矩阵证据列逐字一致）
  - `order is deterministic across calls and a permutation of input`：同一册两次调用逐位相等；`sorted()==sorted(input)`、`distinct().size==input.size`。
  - `order differs from the given order and spaces shared-prefix entries`：对一份 40 词、覆盖 ≥8 个前 3 字母组的样本，断言 `order(input) != input`，且对任意两个前 3 字母相同的 id，`abs(pos(a)-pos(b)) >= groups-1`。
  - 另加：`order ignores the incoming order`（把输入 reverse 后结果与正序输入一致 → 证明顺序由内容而非入参位置决定）、`empty and single entry books are returned unchanged`。
- [ ] **Step 2：跑红**
  `"$GRADLE" :app:testDebugUnitTest --tests '*NewWordOrderingTest' --no-daemon --rerun-tasks` → 编译失败/未定义即 RED（记录原始报错行）。
- [ ] **Step 3：最小实现**
  FNV-1a 64 位哈希（纯 Kotlin，不用 `Random`、不用 `String.hashCode`）；`lemma = id.substringAfter(':')`，`prefix = lemma.take(3)`（不足 3 字符取全词、转小写）；先按 `(hash(prefix), hash(id), id)` 排序，再按 prefix 分组轮转展开，组顺序取各组首元素 `(hash(prefix), prefix)` 序。
- [ ] **Step 4：接 `StoredPlanCardSource.newCardIds`** 并改写既有三条顺序断言：
  `new cards skip the ones already reviewed` → 断言结果是 `order(ids)` 去掉已学词后的**前缀序列**（子序列 + 数量），不再断言字母序；
  `new cards are capped at the daily target` / `ten word sample fills target ten and target three truncates` → 同法改为对齐 `NewWordOrdering.order`。
  新增 `new cards are the interleaved order minus reviewed ones as a subsequence` 与
  `short word book returns every remaining card and a zero target returns none`（与矩阵证据列同名）。
- [ ] **Step 5：跑绿并核对全量基线**（`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin verifyAcTraceabilityMatrix verifyThirdPartyNotices`）。
- [ ] **Step 6：提交**（`feat(learning): 新词取词改为确定性前缀交错序`，含实现 + 两个测试文件）。

### Task 3: 词书选取页渐进发布（ViewModel TDD + UI 占位）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/LearningSetupViewModel.kt`（`LearningSetupUiState` 增 `wordBooksLoading`/`progressLoading`；`load()` 分段发布）
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（`LearningSetupScreen` 占位行与「进度计算中」呈现）
- Modify: `app/src/test/java/com/example/englishlearning/ui/LearningSetupViewModelTest.kt`

**Interfaces:**
- Produces: `LearningSetupUiState.wordBooksLoading: Boolean`、`progressLoading: Boolean`（默认均为 `true`）。
- Consumes: 现有 `seedWordBooks`、`repository.listWordBooks`、`cards.cardIds`、`events.reviewedCardIds`、`refreshIndex`（可空）签名不变。

- [ ] **Step 1：写 RED 测试**（方法名与矩阵证据列逐字一致）
  - `load marks loading on the first frame`：调用 `load()` 后**不推进调度器**，立刻断言 `uiState.value.wordBooksLoading == true`（当前实现此处必红——`load()` 前 state 是默认值且无该字段）。
  - `book list publishes before progress and backfills per book`：两册 + 可控 `FakeCards`（第一册 `cardIds` 挂起直到显式放行），断言存在一个中间帧：`wordBooks.size == 2 && progressByBook.isEmpty() && !wordBooksLoading`；放行后进度逐册出现（记录 `progressByBook.size` 的单调增长序列）。
  - `storage failure shows the load error without a half-filled list`：`listWordBooks` 返回 Failure → `message == "暂时无法加载学习设置"` 且 `wordBooks.isEmpty()` 且 `wordBooksLoading == false`。
  - `index refresh is optional and does not gate the list`：`refreshIndex = null` 时列表与进度照常发布。
- [ ] **Step 2：跑红**（记录失败消息，确认是缺字段/缺发布而非笔误）。
- [ ] **Step 3：实现**——`load()` 开头同步 `copy(wordBooksLoading = true, progressLoading = true, message = null)`；
  种子 + 册列表 + 设置到位后发布一次带 `wordBooks` 且 `wordBooksLoading = false`、`progressByBook = emptyMap()`、`progressLoading = true` 的 state；
  然后逐册计算进度、每册一次 `copy(progressByBook = 累加)`；全部完成后 `progressLoading = false`；
  `refreshIndex?.invoke()` 移到进度回填之后（`dataVersion` 未变时它本身几乎免费，且不该挡首帧）；
  失败路径保持既有文案与「不残留半份列表」。
- [ ] **Step 4：UI 呈现**——`wordBooksLoading` 时显示 3 行占位（复用现有玻璃容器与 `AppShape`，不得引入裸 `Color(0x…)`，不得用 tag 当 `contentDescription`）；
  `progressLoading && progressByBook[book.id] == null` 的册显示「进度计算中」。无障碍语义：占位行不给会播报的假数据。
- [ ] **Step 5：跑绿全量门禁 + 既有 connected 类不退红**（不新跑 connected；本批不碰被 connected 钉住的版面契约，若 T 类红则先查再判）。
- [ ] **Step 6：提交**（`feat(ui): 词书选取页首帧加载态与进度逐册回填`）。

### Task 4: 收口——验证记录、报告、推送、装机

- [ ] `verifyAcTraceabilityMatrix` 的 5 行证据逐条与实际测试方法名核对（不一致就改文档，不改测试名迁就）。
- [ ] 单测计数：python 逐份累加 `app/build/test-results/testDebugUnitTest/*.xml`，报告 §3 写成对照式（1471 → N，0 失败 0 跳过）。
- [ ] 交错序对真实内置册做一次离线核验脚本（cet4/cet6 全量：确认 `order` 后前 3 字母同组间距达标、且首 20 词不再连续同前缀），结论进验证文档。
- [ ] 写 `docs/verification/f1-02-word-ordering-and-setup-preload-2026-10.md` + `docs/handoff/2026-10-17-...-report.md`（七节模板）。
- [ ] 推送 `-c credential.interactive=false push origin <branch>` → `ls-remote` 核验远端 tip == 本地 HEAD，结果写报告 §4。
- [ ] 合 main 由用户拍板；装机 `adb -s bf353dda install -r app-debug.apk`（只装不测，装完提示尽快试用）。
