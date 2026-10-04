# Task 2 Delivery Report: Vocabulary Search History Persistence

## Implementation

- Added `VocabularySearchHistoryEntity` and DAO-backed `RoomVocabularySearchHistoryRepository` persistence for profile-scoped search history.
- Added Room schema version 23 and idempotent `MIGRATION_22_23`, creating the history table and recency index with `IF NOT EXISTS`.
- Repository records normalize blank/whitespace queries, increments repeated-query counts atomically inside a Room transaction, updates recency and representative, and maps entities to domain models.

## Review repairs

- Added JVM focused tests in `app/src/test/java/com/example/englishlearning/learning/RoomVocabularySearchHistoryRepositoryTest.kt`. They exercise the pure record transition and entity-to-domain mapping without requiring a real Room database.
- Strengthened `VocabularySearchHistoryMigrationTest` to insert and verify rows in the existing `schema_meta` and `word_books` tables, plus a pre-existing search-history row after the first migration. It invokes `MIGRATION_22_23` a second time and verifies the search-history row, existing rows, and recency index remain singular/intact, covering repeated `CREATE TABLE/INDEX IF NOT EXISTS` idempotence.

## Verification

- `:app:testDebugUnitTest --tests '*RoomVocabularySearchHistoryRepositoryTest' --no-daemon --no-build-cache --console=plain`: `BUILD SUCCESSFUL` (30 actionable tasks: 3 executed, 27 up-to-date).
- `:app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache --console=plain`: `BUILD SUCCESSFUL` (29 actionable tasks: 2 executed, 27 up-to-date; only existing `MigrationTestHelper` deprecation warning).
- A device/emulator was not available in this environment, so the instrumentation migration test was compiled but not executed. The prior claim of an executable instrumentation result is not made.

## TDD evidence correction

The earlier report's claimed RED/GREEN evidence was unrelated to this task and must not be treated as evidence for vocabulary search history. This repair report makes no claim that an initial failing test run was captured. The focused JVM tests and migration assertions are the actual review evidence added in this round. The initial attempted command from the worktree root (`./gradlew ...`) was not runnable under this shell (`No such file or directory`); the verified command uses the explicit worktree Gradle path and completed successfully.
