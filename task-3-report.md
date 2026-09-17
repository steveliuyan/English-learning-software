# Stage 0 Task 3 Report

## Implemented slices

- Added `SecretReference` and a deliberately write/delete/exists-only `SecretStore` contract; no plaintext retrieval API exists.
- Added Android Keystore-backed AES-GCM credential ciphertext storage. Provider failures map to `AppError.KeyStoreUnavailable`, and ciphertext is removed on failure.
- Added injectable `FileOps`, app-private `assets` implementation, verified temporary writes, SHA-256 checks, and atomic publication.
- Added `MediaAvailability`: missing or mismatched files are rebuildable.
- Added JVM fake coverage for mismatch cleanup, availability, and insufficient storage; added Android instrumented credential tests including provider failure cleanup.

## TDD record

- RED: `:app:testDebugUnitTest --tests '*PrivateMediaStoreTest'` failed at Kotlin compilation because `PrivateMediaStore` and `FileOps` did not exist.
- GREEN: the same command passed after the smallest media implementation.

## Test commands and results

- PASS: `GRADLE_USER_HOME=D:/Android/GradleCache gradlew -p D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:testDebugUnitTest --tests '*PrivateMediaStoreTest' --no-daemon --no-build-cache`
- PASS: `GRADLE_USER_HOME=D:/Android/GradleCache gradlew -p D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:testDebugUnitTest --no-daemon --no-build-cache`
- Initially failed: `:app:check` on detekt/Ktlint formatting; source was fixed/formatted.
- PASS: `GRADLE_USER_HOME=D:/Android/GradleCache gradlew -p D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:check --no-daemon --no-build-cache` (includes notice validation, detekt, ktlint, JVM tests, lint).
- PASS: `GRADLE_USER_HOME=D:/Android/GradleCache gradlew -p D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:assembleDebug --no-daemon --no-build-cache`.
- Not run: connected Android instrumentation; no device/emulator was available in this session.

## Design deviations / follow-up

- `AssetRecord` uses `String` id for task-3 isolation; Task 4 must align it to the persisted UUID asset schema.
- AndroidKeyStore implementation stores ciphertext but intentionally exposes no decryption API. Future authenticated consumers need a narrowly scoped use-case boundary rather than expanding `SecretStore` with plaintext reads.
- The storage insufficiency mapping currently reports `requiredBytes = 0` because `FileOps` has no free-space/required-byte metadata. Task 4 or a follow-up should enrich FileOps exception data if UI needs an estimate.
- No dependencies were added.

## Fix round 1 — review recovery

### Implemented slices

- Added Android instrumentation dependencies through the version catalog: AndroidX Test Core and AndroidX Test Ext JUnit. The instrumented test now uses the runner-compatible `org.junit.Test` / JUnit assertions; both dependencies are recorded in the third-party-notices ledger.
- Extended the Keystore provider-failure regression to retain the caller `CharArray` and assert it is zeroed. `AndroidKeyStoreSecretStore.save` now uses an outer `finally` so the caller buffer is wiped after every path, including destination/key lookup/provider failure; the transient encoded byte array is wiped in its own `finally`.
- Added focused JVM regression coverage for an existing final asset during a write failure and for a hash failure after `exists` succeeds. Failed writes now remove both final and temporary names; availability fails closed to `UnavailableRebuildable` for `IOException` and `SecurityException` file/hash races.

### TDD / investigation record

- Recovery inspection confirmed the working-tree tests and minimal production changes already represented the review reproductions. The pre-fix review recorded the required RED states: Android-test compilation failed for missing dependencies, provider failure did not clear a retained caller buffer, `sha256` errors escaped `availability`, and a pre-existing final survived a failed write.
- During recovery verification, the full `:app:check` initially failed only on KtLint import ordering in the changed Android test. The root cause was JUnit imports preceding `java`/`javax`; reordering them was the minimal formatting repair. The next full check passed.

### Verification results

- PASS: `GRADLE_USER_HOME=D:/Android/GradleCache gradlew --project-dir D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache`.
- PASS: `GRADLE_USER_HOME=D:/Android/GradleCache gradlew --project-dir D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:testDebugUnitTest --tests '*PrivateMediaStoreTest' --no-daemon --no-build-cache`.
- PASS: `GRADLE_USER_HOME=D:/Android/GradleCache gradlew --project-dir D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:check --no-daemon --no-build-cache` (notice validation, detekt, KtLint, JVM tests, lint).
- PASS after one prescribed daemon-stop retry: `:app:assembleDebug --no-daemon --no-build-cache`. The first attempt failed at `mergeDebugJavaResource` with Windows access denied on `app/build/intermediates/incremental/debug-mergeJavaRes/zip-cache/...`; `gradlew --stop` reported no running daemons, and the single retry succeeded.
- BLOCKED externally: `:app:connectedDebugAndroidTest --no-daemon --no-build-cache` compiled and packaged the test APK but ran 0 tests because the attached `M2102J2SC - 13` rejected app installation: `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. An initial attempt to pass `--tests` to this Android task also failed because the task does not support that command-line option; it was not a product/test failure.
- PASS: `git diff --check`.

### Remaining follow-up

- Re-run connected instrumentation after device installation is allowed. No code defect or compiler failure remains in the instrumented source.
