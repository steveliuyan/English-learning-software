# 学习记录与词书单词列表 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为首页增加只读“学习记录”入口，提供今日已学、跨词书历史记录和当前词书全部单词/复习状态三个标签，并让每行复用词卡详情；同时隐藏没有实际操作的“重新学习”按钮。

**Architecture:** 新增 `LearningRecordRepository` 作为只读数据端口，Room DAO 负责返回事件/计划/复习状态的平铺行，ViewModel 在 IO 协程中按标签懒加载并把数据转换成 UI 状态。`LearningRecordsScreen` 只负责渲染和回调；`AppScreen` 增加一个 overlay 入口并复用现有 `CardDetailScreen`，不改变学习事件或计划状态机。

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Kotlin Coroutines, JUnit 5 JVM tests, AndroidX Compose instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-09-30-learning-records-design.md`; requirements in `docs/specs/01-vocabulary-learning-and-review.md` F1-07 and AC1-14–AC1-19.

## Global Constraints

- Do not change the Room schema or migration version; this feature is read-only over schema version 18.
- Learning events remain append-only; the records screen never writes events, review states, plans, or completion counters.
- History covers the current profile across all word books; “all words” covers only the current active word book.
- Learning day comes from `today_plans.localDate`, never from converting event timestamps with the current timezone.
- Status boundary is exact: no review-state row = 未学; `nextReviewAtEpochMillis <= now` = 待复习; `> now` = 学习中.
- Every read runs on the IO dispatcher; cancellation is rethrown and storage errors become retryable UI state.
- Missing cards remain visible as disabled rows using the last `:`-separated card-id segment; they cannot open detail.
- Empty states contain one explanatory sentence and no empty table header or “暂无” placeholder.
- “重新学习” is shown only when a concrete callback is passed; current learning and records callers pass no callback.
- Do not implement homepage search, AI lookup, camera/OCR, or QR scanning in this plan.
- Do not run `connectedDebugAndroidTest`, uninstall the app, clear device data, commit unrelated files, or push until explicitly requested.

---

## File Map

- Create `app/src/main/java/com/example/englishlearning/learning/LearningRecordRepository.kt`: domain row types, tab data, status enum, and read-only repository interface.
- Create `app/src/main/java/com/example/englishlearning/learning/RoomLearningRecordRepository.kt`: Room implementation; maps DAO rows, word-card source results, and `ClockProvider` to domain data.
- Modify `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt`: add event-history and plan-scoped “today” projections.
- Modify `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalTodayPlanDao.kt`: add current-profile plan lookup needed by today/history reads.
- Modify `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningStatsDao.kt`: add review-state projection for all-word status, or keep the query in the event DAO only if the existing table ownership is verified during implementation.
- Modify `app/src/main/java/com/example/englishlearning/di/AppModule.kt`: bind the repository with existing database, clock, word-card source, and `@Named("io")` dispatcher.
- Create `app/src/main/java/com/example/englishlearning/ui/LearningRecordsViewModel.kt`: lazy tab loading, retry, filtering, grouping, and detail-row resolution.
- Create `app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt`: three tabs, loading/error/empty states, filter chips, disabled missing-card rows, and row callbacks.
- Modify `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`: add the “学习记录” button below progress and alongside the existing check-in action.
- Modify `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`: inject/observe the new ViewModel, add `showLearningRecords`, back handling, overlay rendering, and pass `CardDetailScreen` for selected rows.
- Modify `app/src/main/java/com/example/englishlearning/ui/CardDetailScreen.kt`: make `onRelearn` nullable and render the button only when non-null.
- Modify `app/src/main/java/com/example/englishlearning/di/AppModule.kt` only once for both bindings; do not create a second database or clock provider.
- Create `app/src/test/java/com/example/englishlearning/learning/LearningRecordModelsTest.kt`: pure status, missing-card-id, dedupe, and date-grouping tests.
- Create `app/src/test/java/com/example/englishlearning/learning/RoomLearningRecordRepositoryTest.kt`: repository contract tests with fake DAO/source/clock or the project’s existing in-memory Room test helper.
- Create `app/src/test/java/com/example/englishlearning/ui/LearningRecordsViewModelTest.kt`: lazy loading, caching, retry, and filter-count tests.
- Create `app/src/androidTest/java/com/example/englishlearning/ui/LearningRecordsScreenTest.kt`: Compose semantics and row/detail navigation tests.
- Modify `app/src/androidTest/java/com/example/englishlearning/ui/CardDetailScreenTest.kt`: replace the old “relearn callback fires” test with “relearn is absent when callback is not supplied.”

---

### Task 1: Define read-only records domain and pure transformations

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/learning/LearningRecordRepository.kt`
- Create: `app/src/test/java/com/example/englishlearning/learning/LearningRecordModelsTest.kt`

**Interfaces:**
- Produces `LearningRecordRepository` with `suspend fun today(profileId: String, planId: String): RepositoryResult<List<LearningRecord>>`, `suspend fun history(profileId: String): RepositoryResult<List<LearningHistoryGroup>>`, and `suspend fun allWords(wordBookId: String, now: Instant): RepositoryResult<List<WordBookRecord>>`.
- Produces `LearningRecord`, `LearningHistoryGroup`, `WordBookRecord`, `LearningRecordFeedback`, and `WordBookRecordStatus` domain types.
- `LearningRecord` must carry `cardId`, display lemma fallback, optional `WordCard`, feedback, occurred timestamp, local date, and `canOpenDetail`.

- [ ] **Step 1: Write failing pure tests** for status boundaries, `cardId.substringAfterLast(':')` fallback, same-plan/card deduplication keeping earliest event, and date-group ordering.
- [ ] **Step 2: Run the focused JVM tests**.

Run:
```text
D:/EnglishLearningWorktrees/f1-04-unlock-verify/gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests com.example.englishlearning.learning.LearningRecordModelsTest --no-daemon --no-build-cache --console=plain
```

Expected: FAIL because the domain types and transformation functions do not exist.
- [ ] **Step 3: Implement the smallest immutable data classes and internal pure functions** with exact rules from the global constraints.
- [ ] **Step 4: Re-run the focused tests** and expect all cases green.
- [ ] **Step 5: Commit only the new domain file and its test** with `feat: define learning record domain`.

---

### Task 2: Add Room projections and repository reads

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningEventDao.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalTodayPlanDao.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalLearningStatsDao.kt` (only if review confirms it owns review-state read queries)
- Create: `app/src/main/java/com/example/englishlearning/learning/RoomLearningRecordRepository.kt`
- Create: `app/src/test/java/com/example/englishlearning/learning/RoomLearningRecordRepositoryTest.kt`

**Interfaces:**
- DAO projections must be package-private/internal data classes, not exposed through UI.
- Event projection fields: `profileId`, `planId`, `cardId`, `wordBookId`, `feedback`, `occurredAtEpochMillis`, `localDate` from the joined plan.
- Review projection fields: `cardId`, `wordBookId`, `nextReviewAtEpochMillis`.
- Repository consumes `AppDatabase`, `WordCardSource`, `ClockProvider`, and `CoroutineDispatcher`; it produces the domain types from Task 1.

- [ ] **Step 1: Write repository tests first** using an in-memory Room database or the existing test database helper. Cover today query, all-profile history across two word books, plan-date grouping, review boundaries, missing card resolution, and storage failure mapping.
- [ ] **Step 2: Run the repository tests** and verify they fail for missing DAO methods/repository implementation.
- [ ] **Step 3: Add one SQL projection per read path**:
  - today: join `learning_events` to `today_plans` by `planId`, filter `profileId` and `planId`, order timestamp ascending;
  - history: same join, filter profile, order `localDate DESC, occurredAtEpochMillis ASC`;
  - reviews: select review states for one word book.
- [ ] **Step 4: Implement `RoomLearningRecordRepository`** inside `withContext(ioDispatcher)`, rethrow cancellation, map storage exceptions to `RepositoryResult.Failure`, resolve cards through `WordCardSource.cards(cardIds)`, preserve missing rows, and apply Task 1 transformations.
- [ ] **Step 5: Run repository tests** and expect green.
- [ ] **Step 6: Run existing `wordbook.*` and learning JVM tests** to ensure the new read queries do not affect append-only learning behavior.
- [ ] **Step 7: Commit** with `feat: add learning record repository queries`.

---

### Task 3: Wire repository through Hilt and build the ViewModel state contract

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/LearningRecordsViewModel.kt`
- Create: `app/src/test/java/com/example/englishlearning/ui/LearningRecordsViewModelTest.kt`

**Interfaces:**
- Hilt binding: `@Provides @Singleton fun provideLearningRecordRepository(database: AppDatabase, cards: WordCardSource, clock: ClockProvider, @Named("io") dispatcher: CoroutineDispatcher): LearningRecordRepository`.
- ViewModel constructor consumes `LearningRecordRepository` and `ClockProvider` only.
- `LearningRecordsTab`: `TODAY`, `HISTORY`, `ALL_WORDS`.
- `LearningRecordsUiState`: selected tab, loading flags per tab, error per tab, today rows, history groups, all-word rows, selected filter, and selected detail card.
- Public methods: `selectTab(tab)`, `retry()`, `selectAllWordsFilter(filter)`, `openRow(row)`, `clearDetail()`, `load(profileId, planId?, activeWordBookId?)`.

- [ ] **Step 1: Write failing ViewModel tests** for lazy first load, no duplicate load on tab switches, retry after failure, filter counts matching rows, and missing-card rows not opening detail.
- [ ] **Step 2: Run focused tests** and verify expected failures.
- [ ] **Step 3: Implement tab load guards** with a per-tab loaded flag; launch in `viewModelScope`, let repository own IO, and preserve cancellation.
- [ ] **Step 4: Implement filter derivation** from the cached all-word list without another repository call.
- [ ] **Step 5: Implement row open behavior**: valid `WordCard` opens detail; missing card only sets a non-clickable reason state.
- [ ] **Step 6: Run focused ViewModel tests** and expect green.
- [ ] **Step 7: Commit** with `feat: add learning records view model`.

---

### Task 4: Build the records Compose screen and pure UI tests

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/LearningRecordsScreen.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/ui/LearningRecordsScreenTest.kt`

**Interfaces:**
- `LearningRecordsScreen(state: LearningRecordsUiState, onBack, onSelectTab, onRetry, onFilter, onOpenRow)`.
- Stable semantics: `learning_records_screen`, `learning_records_back`, `learning_records_tab_today`, `learning_records_tab_history`, `learning_records_tab_all_words`, `learning_records_loading`, `learning_records_unavailable`, `learning_records_empty`, `learning_records_filter_all`, `learning_records_filter_unlearned`, `learning_records_filter_learning`, `learning_records_filter_due`.
- Each clickable row gets `learning_record_row_<safe card id>`; missing rows get `learning_record_missing_<safe card id>` and no click action.

- [ ] **Step 1: Write instrumentation tests** for the three tabs, empty state, retry, filter counts, clickable row, and disabled missing row.
- [ ] **Step 2: Run the focused instrumentation tests against the current app** and confirm they fail because the screen/semantics do not exist.
- [ ] **Step 3: Implement the screen** with `TabRow`, lazy column/group sections, loading/error/empty branches, and compact row cards. Keep all text in Chinese and use existing mint theme tokens.
- [ ] **Step 4: Run the focused instrumentation tests** and expect green without touching the real database; use fake state supplied by the test.
- [ ] **Step 5: Commit** with `feat: add learning records screen`.

---

### Task 5: Add the homepage entry and overlay/detail navigation

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/CardDetailScreen.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/CardDetailScreenTest.kt`
- Modify/create: `app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt` (only the focused entry assertion)

**Interfaces:**
- Add `TodayPlanScreen(onOpenLearningRecords: () -> Unit = {})` and a stable `today_plan_open_learning_records` tag/content description `学习记录`.
- `AppScreen` creates `showLearningRecords`, obtains `LearningRecordsViewModel`, loads it when the overlay opens, handles Back before root-tab navigation, and passes the profile/plan/active book IDs.
- The screen’s row callback assigns a selected `WordCard?`; `AppScreen` renders `CardDetailScreen` with `onBack` clearing selection, pronunciation callback wired exactly like existing article detail, and no `onRelearn` callback.
- `CardDetailScreen.onRelearn` becomes `(() -> Unit)? = null`; render “重新学习” only when non-null.

- [ ] **Step 1: Update `CardDetailScreenTest` first**: replace the old callback test with `onNodeWithContentDescription("重新学习 ability")` absent when no callback; add a positive test that the button still appears and invokes when a callback is explicitly provided.
- [ ] **Step 2: Run focused instrumentation tests** and confirm the absent-button test fails on the current non-null-default implementation.
- [ ] **Step 3: Implement nullable callback rendering** and pass no callback from learning/article/records callers.
- [ ] **Step 4: Add the homepage entry test** and run it to confirm the new callback/semantics are missing before implementation.
- [ ] **Step 5: Add the button, overlay state, BackHandler priority, ViewModel load, and detail navigation** in `AppScreen`/`TodayPlanScreen`.
- [ ] **Step 6: Run focused instrumentation tests** (`CardDetailScreenTest`, `AppScreenTest`, `LearningRecordsScreenTest`) and expect green.
- [ ] **Step 7: Commit** with `feat: add learning records navigation`.

---

### Task 6: Full verification and documentation alignment

**Files:**
- Modify: none unless a test exposes a real contract mismatch.
- Test results: `app/build/test-results/testDebugUnitTest/*.xml` and instrumentation output outside tracked source.

- [ ] **Step 1: Run all JVM tests**:
```text
D:/EnglishLearningWorktrees/f1-04-unlock-verify/gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --rerun --no-daemon --no-build-cache --console=plain
```
Expected: all suites pass, including the existing full suite and new records tests.
- [ ] **Step 2: Build both APKs**:
```text
D:/EnglishLearningWorktrees/f1-04-unlock-verify/gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks --no-daemon --no-build-cache --console=plain
```
Expected: exit code 0; no SIGTERM.
- [ ] **Step 3: Ask for explicit device authorization** before any `adb install` or `adb shell am instrument`; never run `connectedDebugAndroidTest`.
- [ ] **Step 4: Before device tests, copy database three-piece MD5 and do not clear/uninstall; use an in-memory DB for DAO tests.**
- [ ] **Step 5: Run focused instrumentation with `am instrument`**, then verify database MD5 unchanged for read-only records and detail navigation.
- [ ] **Step 6: Manually verify on device**: today rows include the two reviewed words, history has the two learning dates, all-words has the three active-book cards and correct filters.
- [ ] **Step 7: Inspect every fresh XML and command exit code; report tests/builds exactly as run. Do not use old XML.
- [ ] **Step 8: Only after the user separately asks, stage the exact feature files, review secrets, commit, push, and verify remote SHA.

---

## Self-review

- Spec coverage: F1-07 entry/tabs/row detail is Task 5; all-word states and all-profile history are Tasks 1–3; lazy/error/empty behavior is Tasks 3–4; missing-card behavior is Tasks 1, 3, 4; no-op relearn is Task 5; read-only/device acceptance is Task 6.
- Search/AI search, camera, OCR, and QR scanning are explicitly excluded from every task and remain separate future specs.
- No Room migration is planned; all reads use existing schema version 18.
- Placeholder scan: no `TBD`, `TODO`, “implement later”, or undefined interfaces remain. The phrase “only if review confirms” is an implementation boundary, not an unresolved product requirement; Task 2 requires checking DAO ownership before editing.
- Type consistency: Task 1’s `LearningRecordRepository` methods are the exact inputs consumed by Task 3; Task 3’s `LearningRecordsUiState` and callback names are the exact inputs consumed by Task 4/5.
