# F1-05 FSRS 调度接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改写既有学习事件的前提下，为阶段 1 接入可替换的 FSRS 复习调度实现，并保持 F1-03 的反馈、幂等和 F1-04 完成度兼容。

**Architecture:** 保留 `ReviewScheduler` 作为唯一调度边界。新增纯 Kotlin 的 FSRS V1 实现，输入当前派生复习状态、反馈和时间，输出下一状态与下一次复习时间；历史 `LearningEvent` 永远只读，算法版本和参数版本写入新事件。现有 V1 固定间隔调度继续作为兼容实现和迁移基线，不新增完成计数表，不把 FSRS 状态复制到 UI。

**Tech Stack:** Kotlin、Java Time、JUnit 5、Jetpack Room、现有 Compose/Android instrumentation 测试。

**Spec:** `docs/specs/01-vocabulary-learning-and-review.md`，并遵守 `AGENTS.md` 的本地优先、事件不可变、幂等反馈与阶段范围约束。

## Global Constraints

- 反馈必须先成功写入本地，调度计算不得依赖网络或 AI。
- 历史事件不可更新或删除；算法升级只影响未来排程。
- 同一 `eventId` 重放不得重复调整复习状态；同一计划卡不得重复完成。
- 词书之间的复习状态隔离；不得按 lemma 合并。
- 不引入网络、AI、TTS/OCR、提醒、备份、账号或云同步。
- 不修改 Stage 0 遗留受保护文件：`app/gradle.lockfile`、`core/error/AppError.kt`、`ui/AppViewModel.kt`，除非规格明确要求。
- 真机 instrumentation 必须使用 `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`；未经明确确认不得卸载应用、清除数据或删除数据库。

---

### Task 1: 锁定 FSRS 领域输入输出契约

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/learning/domain/ReviewScheduler.kt`
- Create: `app/src/test/java/com/example/englishlearning/learning/domain/FsrsReviewSchedulerTest.kt`

**Interfaces:**
- Consumes: `ReviewState(cardId, dueAt)`、`ReviewFeedback`、`Instant`。
- Produces: 扩展后的调度状态必须仍通过 `ReviewScheduler.schedule(state, feedback, now): ScheduledReview` 暴露；不得让 UI 或 Room 依赖 FSRS 内部参数。

- [ ] **Step 1: Write the failing test**

在 `FsrsReviewSchedulerTest` 先固定以下行为：

```kotlin
@Test
fun `first good review creates deterministic future due date`() {
    val now = Instant.parse("2026-09-20T04:00:00Z")
    val result = FsrsReviewScheduler().schedule(
        ReviewState(cardId = "card-1", dueAt = null),
        ReviewFeedback.Good,
        now,
    )
    assertEquals(now.plusSeconds(86_400), result.nextReviewAt)
    assertEquals("fsrs-v1", result.algorithmVersion)
}
```

同时为 `Again <= Hard <= Good`、重复输入确定性、已有 due 状态单调推进和异常状态保守处理各增加一个独立测试。若当前 `ScheduledReview` 没有 `algorithmVersion`，先在测试中定义希望的最小字段，再让编译失败成为契约信号。

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd /d/EnglishLearningWorktrees/f1-04-unlock-verify
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.learning.domain.FsrsReviewSchedulerTest" --no-daemon --no-build-cache --console=plain
```

Expected: FAIL because `FsrsReviewScheduler` and any newly required FSRS result metadata do not exist. Do not retain a test that passes before production implementation.

- [ ] **Step 3: Write minimal implementation**

Implement only the smallest deterministic FSRS-compatible transition required by the tests. Keep all calculations in the domain package, use `Duration`/`Instant`, clamp invalid intervals to a safe positive interval, and expose algorithm/parameter versions through the scheduler/result contract only if the existing feedback pipeline needs them. Do not add configurable weights, network-loaded parameters, or UI settings in this task.

- [ ] **Step 4: Run test to verify it passes**

Run the same targeted Gradle command. Expected: all FSRS domain tests pass and the existing `V1ReviewSchedulerTest` remains green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/englishlearning/learning/domain/ReviewScheduler.kt app/src/main/java/com/example/englishlearning/learning/domain/FsrsReviewScheduler.kt app/src/test/java/com/example/englishlearning/learning/domain/FsrsReviewSchedulerTest.kt
git commit -m "feat(learning): add deterministic fsrs scheduler contract"
```

### Task 2: 接入反馈提交并保留历史事件兼容

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/learning/SubmitCardFeedbackUseCase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/learning/RoomLearningEventRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/entity/LearningEventEntity.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt`
- Test: `app/src/test/java/com/example/englishlearning/learning/SubmitCardFeedbackUseCaseTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/learning/RoomLearningEventRepositoryTest.kt`

**Interfaces:**
- Consumes: the scheduler from Task 1 and existing `SubmitCardFeedbackUseCase` inputs.
- Produces: each newly appended event records the selected algorithm/parameter versions; duplicate `eventId` returns the existing result without a second state update; existing `v1` events remain readable.

- [ ] **Step 1: Write the failing test**

Add tests that submit the same feedback twice with the same `eventId`, assert one event and one state transition, then submit with FSRS and assert the new event carries `algorithmVersion = "fsrs-v1"` while the old event remains `algorithmVersion = "v1"`. Add a Room test that reads both versions after reopening the database.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd /d/EnglishLearningWorktrees/f1-04-unlock-verify
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:testDebugUnitTest --tests "com.example.englishlearning.learning.SubmitCardFeedbackUseCaseTest" --no-daemon --no-build-cache --console=plain
```

Expected: FAIL because the current submission path still selects the fixed V1 scheduler or lacks FSRS version persistence.

- [ ] **Step 3: Write minimal implementation**

Inject `ReviewScheduler` through the existing dependency boundary, select the FSRS implementation for new submissions, retain the current V1 implementation for compatibility where existing fixtures require it, and keep event append plus derived state update inside the existing transaction/atomic path. Do not change event IDs or reprocess old rows.

- [ ] **Step 4: Run test to verify it passes**

Run targeted JVM tests and the Room repository instrumentation test. Confirm XML counts and `failures/errors/skipped`, not only Gradle exit status.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/englishlearning/learning/SubmitCardFeedbackUseCase.kt app/src/main/java/com/example/englishlearning/learning/RoomLearningEventRepository.kt app/src/main/java/com/example/englishlearning/core/storage/entity/LearningEventEntity.kt app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt app/src/test/java/com/example/englishlearning/learning/SubmitCardFeedbackUseCaseTest.kt app/src/androidTest/java/com/example/englishlearning/learning/RoomLearningEventRepositoryTest.kt
git commit -m "feat(learning): persist fsrs scheduling metadata"
```

### Task 3: 验证幂等、迁移和 F1-04 回归

**Files:**
- Modify: `app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseMigrationTest.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/WordCardScreenTest.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/TodayPlanScreenTest.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/learning/FsrsFeedbackFlowTest.kt`

**Interfaces:**
- Consumes: FSRS scheduler and feedback path from Tasks 1–2.
- Produces: Android-level evidence that Room reopen, event replay, duplicate event IDs, plan completion and word-book isolation remain correct.

- [ ] **Step 1: Write the failing test**

Create an instrumentation flow that:

```kotlin
@Test
fun `fsrs feedback survives reopen and duplicate event does not double advance`() {
    // create a plan/card in the existing fixture
    // submit one Good event with a fixed eventId
    // submit the same eventId again
    // assert one event, one derived state, and unchanged nextReviewAt
}
```

Add migration assertions for existing `v5` data and F1-04 assertions that completion remains based on plan events, not the new scheduler state.

- [ ] **Step 2: Run test to verify it fails**

Run with the mandatory preservation flag:

```bash
cd /d/EnglishLearningWorktrees/f1-04-unlock-verify
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:connectedDebugAndroidTest --tests "com.example.englishlearning.learning.FsrsFeedbackFlowTest" -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --no-build-cache --console=plain
```

Expected: FAIL until the FSRS metadata and migration/flow integration are complete. Before any device run, confirm `adb -s bf353dda shell pm path com.example.englishlearning` and back up any existing database; do not clear or uninstall data.

- [ ] **Step 3: Write minimal implementation/fix**

Fix only integration defects revealed by the tests: schema version/migration, DAO mapping, event idempotency, or fixture wiring. Preserve all F1-04 UI behavior and the existing three feedback labels.

- [ ] **Step 4: Run test to verify it passes**

Read instrumentation XML results and report tests/failures/errors/skipped. After the run, verify the app remains installed with `adb -s bf353dda shell pm path com.example.englishlearning`; the preservation flag must remain in the command.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseMigrationTest.kt app/src/androidTest/java/com/example/englishlearning/ui/WordCardScreenTest.kt app/src/androidTest/java/com/example/englishlearning/ui/TodayPlanScreenTest.kt app/src/androidTest/java/com/example/englishlearning/learning/FsrsFeedbackFlowTest.kt
git commit -m "test(learning): verify fsrs feedback persistence and idempotency"
```

### Task 4: 阶段构建与交付审查

**Files:**
- Modify: `docs/decisions/2026-09-20-fsrs-scheduling.md`
- Modify: `docs/superpowers/plans/2026-09-20-stage-1-f1-05-fsrs-scheduling.md`

**Interfaces:**
- Consumes: all implementation and test changes from Tasks 1–3.
- Produces: documented algorithm/version decision, clean validation evidence, one final delivery commit and pushed feature branch.

- [ ] **Step 1: Run complete JVM and compile checks**

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug --no-daemon --no-build-cache --console=plain
```

Read every relevant JUnit XML file and explicitly report existing baseline failures separately from F1-05 failures.

- [ ] **Step 2: Run the mandatory quality checks**

Check for modifications to protected files, verify Room schema/lockfile changes are intentional, and inspect the final diff for network/AI/scope creep. Do not claim complete if any F1-05 target test fails.

- [ ] **Step 3: Commit and push**

```bash
git add docs/decisions/2026-09-20-fsrs-scheduling.md docs/superpowers/plans/2026-09-20-stage-1-f1-05-fsrs-scheduling.md
git commit -m "docs(learning): record fsrs scheduling decision"
git push -u origin stage-1-f1-05-fsrs-scheduling
```

Verify:

```bash
local_head=$(git rev-parse HEAD)
remote_head=$(git ls-remote origin refs/heads/stage-1-f1-05-fsrs-scheduling | cut -f1)
printf 'LOCAL_HEAD=%s\nREMOTE_HEAD=%s\n' "$local_head" "$remote_head"
test "$local_head" = "$remote_head"
```

## Self-review

- F1-03 event immutability, idempotency and feedback labels are covered by Tasks 2–3.
- F1-04 completion remains derived from plan membership and events; Task 3 explicitly prevents scheduler state from changing unlock counts.
- No task adds network, AI, TTS/OCR, reminders, backup, account, or cloud sync.
- New algorithm and parameter versions are explicit; old `v1` events remain readable.
- Real-device verification is conditional and preserves installed APK/data; no destructive device command is included.
- Any schema change must use an explicit migration and lock update only after a successful build.
- The plan intentionally does not address unrelated baseline quality debt.
