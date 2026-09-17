# Task 2 Delivery Report: Core Error, Time, and Safe Logging Boundaries

## Commit

- `b4d9caf feat: add stage zero core error and safe logging boundaries`

## Implementation slices

1. Added a sealed `AppError` model with stable UI text identifiers for network, storage, database migration, Keystore, integrity, and pairing failures. It intentionally carries no exception messages, keys, paths, aliases, or hashes.
2. Added the replaceable `ClockProvider` interface plus system and fixed implementations. The fixed implementation returns its configured `Instant` and `ZoneId` deterministically.
3. Added `SafeLogger` and `SanitizingSafeLogger`. It emits event names plus a deliberately small allowlist (`profileId`, `attempt`) of scalar attributes; it rejects sensitive key fragments case-insensitively and never serializes byte arrays, character arrays, throwables, or arbitrary object values.

## Changed files

- `app/src/main/java/com/example/englishlearning/core/error/AppError.kt`
- `app/src/main/java/com/example/englishlearning/core/time/ClockProvider.kt`
- `app/src/main/java/com/example/englishlearning/core/logging/SafeLogger.kt`
- `app/src/test/java/com/example/englishlearning/core/logging/SafeLoggerTest.kt`
- `app/src/test/java/com/example/englishlearning/core/time/ClockProviderTest.kt`

## TDD evidence

### RED

Command:

```text
GRADLE_USER_HOME=D:/Android/GradleCache gradlew.bat -p D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:testDebugUnitTest --tests '*SafeLoggerTest' --tests '*ClockProviderTest'
```

Result: failed as expected before production code existed. Kotlin compilation reported unresolved references for `SanitizingSafeLogger`, `LogSink`, and `FixedClockProvider`.

A second RED check added an unapproved scalar attribute. The logger test failed at `SafeLoggerTest.kt:35`, proving that a denylist-only logger was insufficient for the explicit allowlist contract.

### GREEN

Command:

```text
GRADLE_USER_HOME=D:/Android/GradleCache gradlew.bat -p D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:testDebugUnitTest --tests '*SafeLoggerTest' --tests '*ClockProviderTest'
```

Result: `BUILD SUCCESSFUL` (28 tasks; 9 executed, 19 up-to-date).

Covered behavior:

- Keeps `profileId` and `attempt` only when scalar and allowed.
- Does not emit Authorization, apiKey, password, private key, image bytes, Throwable message, CharArray, path, hash, alias, or unapproved values.
- Returns configured fixed clock instant and zone.

## Full validation

Commands:

```text
GRADLE_USER_HOME=D:/Android/GradleCache gradlew.bat -p D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:check
GRADLE_USER_HOME=D:/Android/GradleCache gradlew.bat -p D:/EnglishLearningWorktrees/stage-0-foundation-verify :app:assembleDebug
```

Results:

- `:app:check`: `BUILD SUCCESSFUL in 52s`; includes third-party notice validation, detekt, ktlint, unit tests, and lint.
- `:app:assembleDebug`: `BUILD SUCCESSFUL in 36s`.
- Both commands showed the pre-existing Android Gradle warning that `android.overridePathCheck=true` is experimental; it did not fail validation.

No dependencies were added, so no third-party ledger update was needed.

## Design deviations and remaining risks

- The plan asks for stable UI resource IDs. This foundation contains no Android string-resource layer yet, so `AppErrorUiText` is a stable non-sensitive identifier enum for later UI mapping; no error payload contains raw cause content.
- The sink abstraction intentionally has no production Android logging backend in Task 2. A later composition-root task must bind it to an Android-safe backend.
- The checked-in working tree still has pre-existing untracked `task-1-final-review.md` and `task-1-rereview.md`; neither was staged or committed.
