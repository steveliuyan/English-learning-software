# 默认 AI Profile 选择实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为文章生成增加设备级、显式且可验证的默认文本 AI Profile 选择，替换“第一套有 Key”临时策略，并在 Profile/Key 失效时安全清除绑定。

**Architecture:** 新增设备级 `ai_preferences` 单行表，仅保存 `defaultTextProfileId`；通过 `DefaultTextProfileSelector` 集中验证默认 Profile 的文本能力与 KeyStore 状态。设置页负责显式选择和状态展示，文章生成只消费显式默认 Profile，历史文章本地复用优先且不要求默认配置。

**Tech Stack:** Kotlin、Jetpack Compose、Room v16、Hilt、Coroutines、Android KeyStore、JUnit4/JUnit5、Compose instrumentation。

**Spec:** `docs/superpowers/specs/2026-09-27-default-ai-profile-selection-design.md`

## Global Constraints

- 默认 Profile 是设备级文本 AI 选择，与学习 `profileId` 和语音 `speech_preferences` 独立。
- 升级后不自动选择第一套有 Key 的 Profile；没有显式默认时不得发起新 AI 请求。
- 只有 `AiCapability.Text` 且 `AiProfileSecretUseCase.hasKey()` 成功为真的 Profile 才可设为默认。
- `ai_preferences` 只保存固定主键 `device` 和 `defaultTextProfileId`；不得保存 Key、secret alias、Endpoint、模型、音色或派生状态。
- 默认 Profile 被删除或其 Key 被清除后必须清除绑定；新建/编辑 Profile 不自动成为默认。
- 本地文章复用先于默认配置检查；历史文章复用不要求默认 Profile。
- API Key 只经 SecretStore/`AiProfileSecretUseCase` 使用；不得进入 UI 状态、日志、文章元数据、请求 body 或测试诊断输出。
- Room 从 v15 迁移到 v16，禁止 destructive migration；schema JSON 必须更新。
- 每个行为严格执行 RED → 确认失败原因 → GREEN → 回归；未获用户另行确认不提交、不推送。
- 真机只用 `assembleDebug`、`assembleDebugAndroidTest`、`adb install -r -t`、`am instrument -w`；禁止 `connectedDebugAndroidTest`、卸载和清除数据。

---

### Task 1: 建立 AI 偏好领域模型与 Room v16 持久化

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ai/domain/AiPreference.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/AiPreferenceRepository.kt`
- Create: `app/src/main/java/com/example/englishlearning/ai/RoomAiPreferenceRepository.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/AiPreferenceEntity.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalAiPreferenceDao.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Test: `app/src/test/java/com/example/englishlearning/ai/RoomAiPreferenceRepositoryTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseMigrationTest.kt`

**Interfaces:**

```kotlin
data class AiPreference(
    val preferenceId: String = "device",
    val defaultTextProfileId: String? = null,
)

interface AiPreferenceRepository {
    suspend fun get(): Result<AiPreference>
    suspend fun save(preference: AiPreference): Result<Unit>
}
```

- [ ] **Step 1: Write the failing JVM repository test**

```kotlin
@Test
fun emptyStoreReturnsAnUnselectedDevicePreference() = runTest {
    val repository = RoomAiPreferenceRepository(fakeDaoReturningNull(), UnconfinedTestDispatcher())
    assertEquals(AiPreference(), repository.get().getOrThrow())
}

@Test
fun saveAndGetRoundTripOnlyTheDefaultProfileId() = runTest {
    val repository = RoomAiPreferenceRepository(fakeDao(), UnconfinedTestDispatcher())
    repository.save(AiPreference(defaultTextProfileId = "p-text")).getOrThrow()
    assertEquals("p-text", repository.get().getOrThrow().defaultTextProfileId)
}
```

Use the repository’s existing `Result`/storage-error pattern; the fake DAO is test-only and must not introduce production abstractions beyond the declared interface.

- [ ] **Step 2: Run the target JVM test and confirm the expected RED**

Run from the worktree:

```bash
D:/Android/gradle/gradle-*/bin/gradle.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests '*RoomAiPreferenceRepositoryTest' --no-daemon --no-build-cache --console=plain
```

If the repository test cannot compile because the production symbols do not exist, record that as the expected feature-missing failure; do not add production code before this RED check.

- [ ] **Step 3: Write the failing v15→v16 migration test**

Add a test beside the existing migration tests that creates a v15 database, inserts an AI Profile, a speech preference and an article, migrates with `AppDatabase.MIGRATION_15_16`, and asserts:

```kotlin
query("SELECT displayName FROM ai_profiles WHERE profileId = 'existing-profile'")
query("SELECT selectedEngine FROM speech_preferences WHERE preferenceId = 'device'")
query("SELECT title FROM articles WHERE articleId = 'article-1'")
query("PRAGMA table_info(ai_preferences)") // preferenceId, defaultTextProfileId only
```

Also assert the new table has no column whose name contains `key`, `secret`, `endpoint`, or `model`. The v15 fixture must explicitly provide every non-null `ai_profiles` column, including `providerKind` and `voice`, because migration SQL defaults are not represented in exported schema fixtures.

- [ ] **Step 4: Add the minimum domain/entity/DAO/repository implementation and migration**

Implement `AiPreferenceEntity(preferenceId: String, defaultTextProfileId: String?)`, a fixed-key DAO query/upsert, and `RoomAiPreferenceRepository` using the same dispatcher and storage error mapping as `RoomAiProfileRepository`. `get()` returns `AiPreference()` when the row is absent. Add the entity/DAO accessor to `AppDatabase`, change version to `16`, add `MIGRATION_15_16` with:

```sql
CREATE TABLE IF NOT EXISTS `ai_preferences` (
  `preferenceId` TEXT NOT NULL,
  `defaultTextProfileId` TEXT,
  PRIMARY KEY(`preferenceId`)
)
```

Append the migration to `MIGRATIONS`; do not alter earlier migration SQL. Add the Hilt binding in `AppModule`.

- [ ] **Step 5: Run repository and migration tests to verify GREEN**

Run the JVM target and the migration instrumentation build/test target. Expected: repository round-trip and v15→v16 preservation pass, with no credential columns.

- [ ] **Step 6: Export and inspect Room schema**

Build the schema-producing task used by this repository, locate the generated v16 JSON under `app/schemas`, and verify it contains `ai_preferences` with only the two declared columns. Do not overwrite unrelated schema files or any pre-existing untracked files.

---

### Task 2: Centralize default text Profile eligibility and invalid-binding cleanup

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ai/DefaultTextProfileSelector.kt`
- Create: `app/src/test/java/com/example/englishlearning/ai/DefaultTextProfileSelectorTest.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`

**Interfaces:**

```kotlin
sealed interface DefaultTextProfileResult {
    data class Selected(val profile: AiProfile) : DefaultTextProfileResult
    data object NoSelection : DefaultTextProfileResult
    data object Unavailable : DefaultTextProfileResult
    data object StorageUnavailable : DefaultTextProfileResult
}

class DefaultTextProfileSelector(
    private val preferences: AiPreferenceRepository,
    private val profiles: AiProfileRepository,
    private val secrets: AiProfileSecretUseCase,
) {
    suspend fun select(): DefaultTextProfileResult
}
```

- [ ] **Step 1: Write focused failing selector tests**

Cover one behavior per test:

```kotlin
@Test fun noSavedSelectionDoesNotInspectOtherProfilesOrKeys()
@Test fun selectedTextProfileWithKeyIsReturned()
@Test fun selectedProfileWithoutTextCapabilityIsClearedAndReportedUnavailable()
@Test fun selectedProfileWithoutKeyIsClearedAndReportedUnavailable()
@Test fun selectedMissingProfileIsClearedAndReportedUnavailable()
@Test fun preferenceReadFailureIsStorageUnavailableAndDoesNotClear()
@Test fun profileReadFailureIsStorageUnavailableAndDoesNotClear()
```

The fake secret store must record only `hasKey` calls and ensure `loadKey` is never used. Assert that invalid selection calls `save(AiPreference())` exactly once and never scans fallback Profiles. Use opaque test key markers only in the fake store; never include them in failure messages.

- [ ] **Step 2: Run selector tests and verify RED**

Run:

```bash
gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests '*DefaultTextProfileSelectorTest' --no-daemon --no-build-cache --console=plain
```

Expected failure: selector/result types are absent or behavior is not implemented. Fix test setup errors until the failure is specifically feature-missing.

- [ ] **Step 3: Implement the minimal selector**

Read the preference first. For `defaultTextProfileId == null`, return `NoSelection` without listing Profiles or checking Keys. For a selected ID, call `profiles.find(id)`; a missing profile, a Profile without `AiCapability.Text`, or `hasKey(profile) != true` calls `preferences.save(AiPreference())` and returns `Unavailable`. A repository failure returns `StorageUnavailable` and never clears the binding. A successful eligible Profile returns `Selected(profile)`. Do not load or retain the secret.

- [ ] **Step 4: Run selector tests and verify GREEN**

Run the target test again and then the existing AI profile unit tests. Expected: all selector cases pass and no existing KeyStore ordering tests regress.

- [ ] **Step 5: Add the Hilt provider**

Bind `DefaultTextProfileSelector` as a singleton using the existing repository and secret use case. Keep the selector independent from UI and HTTP classes.

---

### Task 3: Replace article-generation fallback selection with explicit default selection

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/reading/GenerateArticleUseCase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Modify: `app/src/test/java/com/example/englishlearning/reading/GenerateArticleUseCaseTest.kt`

**Interfaces:**

Change the use case dependency from direct Profile-list scanning to:

```kotlin
class GenerateArticleUseCase(
    private val defaultTextProfile: DefaultTextProfileSelector,
    private val secrets: AiProfileSecretUseCase,
    // existing transport/articles/ids/clock dependencies
)
```

Extend `NotConfiguredReason` with `NoDefaultProfile` and `DefaultProfileUnavailable` only if the caller needs to distinguish those states; preserve old enum members for source compatibility until all callers/tests are updated.

- [ ] **Step 1: Add failing generation tests**

Add these tests before changing production code:

```kotlin
@Test fun generatesUsingOnlyTheExplicitDefaultEvenWhenAnotherProfileHasAKey()
@Test fun reportsNoDefaultAndSendsNoRequestWhenNoDefaultIsSaved()
@Test fun doesNotFallBackToAnotherKeyedProfileWhenTheDefaultIsInvalid()
@Test fun reusesLocalArticleWithoutReadingTheDefaultProfile()
```

The explicit-default fake selector should return a selected Profile or the corresponding result. The no-default and invalid-default tests must assert zero transport calls and zero calls to any fallback profile secret. Retain the existing test for “first keyed profile” only temporarily as the RED mutation target, then replace its expected behavior with explicit default semantics rather than preserving the old requirement.

- [ ] **Step 2: Run the target generation tests and verify RED**

Run:

```bash
gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests '*GenerateArticleUseCaseTest' --no-daemon --no-build-cache --console=plain
```

Expected: constructor mismatch and/or the old first-key behavior fails the new explicit-default assertions. Do not alter production code until this failure is observed.

- [ ] **Step 3: Implement explicit selection while preserving request security**

Keep the current local-reuse check as the first operation. After a cache miss, call `DefaultTextProfileSelector.select()`. Map `NoSelection` to `NotConfigured(NoDefaultProfile)`, invalid binding to `NotConfigured(DefaultProfileUnavailable)` (or the selected stable equivalent), and storage failure to the existing profile-unreadable/configuration failure. Only the returned Profile’s Key may be read. Keep confirmation before transport, keep `finally { key.fill('\u0000') }`, preserve endpoint validation, response validation, provenance summary, and all existing failure mappings.

- [ ] **Step 4: Update Hilt construction and run generation tests**

Pass the selector from `AppModule`. Run all `GenerateArticleUseCaseTest` cases and the full JVM unit suite. Expected: no-default never sends, invalid default never falls through, cache reuse still succeeds without a default, and successful generation uses only the selected Profile.

- [ ] **Step 5: Run mutation checks for the security assertions**

Temporarily mutate the implementation in a disposable working copy or controlled local edit so that it scans the first keyed Profile or checks the default after `transport.send`; confirm the new tests turn red, then restore the intended implementation. Do not leave mutation edits in the worktree.

---

### Task 4: Add explicit default selection to the AI Profile ViewModel

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AiProfileSettingsViewModel.kt`
- Modify: `app/src/test/java/com/example/englishlearning/ui/AiProfileSettingsViewModelTest.kt`

**Interfaces:**

Extend `AiProfileListItem`:

```kotlin
data class AiProfileListItem(
    val profile: AiProfile,
    val hasKey: Boolean,
    val isDefaultTextProfile: Boolean = false,
    val canBeDefaultTextProfile: Boolean = false,
)
```

Add:

```kotlin
fun setDefaultTextProfile(profileId: String)
```

- [ ] **Step 1: Add failing ViewModel tests**

Add tests for:

```kotlin
@Test fun loadMarksOnlyTheValidSavedDefault()
@Test fun loadClearsAnInvalidSavedDefault()
@Test fun settingAKeyedTextProfilePersistsItsId()
@Test fun settingANonTextProfileDoesNotWritePreferences()
@Test fun settingAProfileWithoutAKeyDoesNotWritePreferences()
@Test fun failedDefaultSaveKeepsThePreviousDefaultMarker()
@Test fun deletingTheDefaultProfileClearsThePreferenceAfterKeyAndMetadataDeletion()
@Test fun deletingTheDefaultKeyClearsThePreference()
@Test fun deletingANonDefaultKeyLeavesTheDefaultPreference()
@Test fun creatingOrEditingAProfileDoesNotMakeItDefault()
```

Update the fake repository harness to include `AiPreferenceRepository`, recording preference writes separately from Profile/Secret writes. Assert deletion write order remains secret first, metadata second, followed by preference clear where applicable; if preference clearing fails, the Profile deletion result remains true but the list exposes a storage message.

- [ ] **Step 2: Run ViewModel tests and verify RED**

Run:

```bash
gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests '*AiProfileSettingsViewModelTest' --no-daemon --no-build-cache --console=plain
```

Expected: constructor/API mismatch and missing default marker/actions. Resolve only test harness compilation issues needed to obtain a feature-specific failure.

- [ ] **Step 3: Implement preference-aware list refresh**

Load Profiles and `AiPreference` together. Derive `hasKey`, text capability, `isDefaultTextProfile`, and `canBeDefaultTextProfile` without loading secret values. If a saved ID is invalid, clear it once and render no default marker. On storage read failure, return the existing `Unavailable` state rather than showing an invented default.

- [ ] **Step 4: Implement explicit selection and lifecycle cleanup**

`setDefaultTextProfile` must find the Profile, require Text capability and `hasKey == true`, then save `AiPreference(defaultTextProfileId = profileId)`. Do not alter list state before save succeeds. After success refresh. In `deleteProfile`, retain KeyStore-first then metadata deletion; after successful metadata deletion, clear the preference only if that Profile was default. In `deleteKey`, clear the preference only if that Profile was default and Key deletion succeeded. Save/edit paths must not assign defaults.

- [ ] **Step 5: Run ViewModel tests and verify GREEN**

Run the targeted tests and the full JVM suite. Confirm existing secret ordering, MiMo provider preservation, no-key behavior, and editor no-echo tests remain green.

- [ ] **Step 6: Mutation-test selection guardrails**

Temporarily remove the Text-capability guard and the `hasKey` guard one at a time; confirm the corresponding tests fail. Restore the guards and rerun the targeted suite.

---

### Task 5: Expose default state and action in the AI Profile Compose screen

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AiProfileSettingsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/AiProfileSettingsScreenTest.kt`
- Modify: `app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt` only if constructor wiring requires it

**Interfaces:**

Add `onSetDefaultTextProfile: (String) -> Unit` to `AiProfileSettingsScreen`; pass it from `AppScreen` to the ViewModel. Preserve all existing parameters, callbacks, return behavior, test tags, and editor semantics.

- [ ] **Step 1: Add failing Compose tests**

Add tests using `useUnmergedTree = true` where the row semantics merge:

```kotlin
@Test fun list_marksExactlyOneValidDefaultProfile()
@Test fun keyedTextProfileOffersSetDefaultAction()
@Test fun profileWithoutKeyDoesNotOfferEnabledDefaultAction()
@Test fun nonTextProfileDoesNotOfferDefaultAction()
@Test fun tappingSetDefaultReportsTheProfileId()
@Test fun defaultActionAndStatusNeverExposeSecretTextOrSecretAlias()
```

Use stable tags such as `ai_profile_default_state_<id>` and `ai_profile_set_default_<id>`. Assert “文章默认” appears only for the selected item and that unavailable items show explanatory non-action text rather than a clickable action.

- [ ] **Step 2: Build the Android test source and verify RED**

Run:

```bash
gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache --console=plain
```

Expected: missing callback and tags. If the old screen test helper no longer compiles after the callback is added, update only the helper with a no-op callback before rerunning RED.

- [ ] **Step 3: Implement minimal list-row default UI**

Render a non-secret status marker for `isDefaultTextProfile`. Render an enabled secondary action only when `canBeDefaultTextProfile && !isDefaultTextProfile`; render no clickable action for missing Key or missing Text capability. Keep the row itself editable, and ensure the secondary action does not accidentally trigger row edit by using its own click target and semantics.

- [ ] **Step 4: Wire callback and verify Compose tests**

Pass `onSetDefaultTextProfile` through `AppScreen`. Run targeted Compose tests, then the existing AI Profile screen tests. Confirm no text contains API Key, secret alias, Authorization, or full Endpoint beyond the existing masked/domain display contract.

- [ ] **Step 5: Mutation-test the disabled states**

Temporarily render the action for a no-Key item and confirm the no-Key test turns red; temporarily render a second default marker and confirm the uniqueness test turns red. Restore the correct predicates.

---

### Task 6: Surface no-default and invalid-default article-generation guidance

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/ReadingAccessViewModel.kt`
- Modify: the existing reading access screen file that renders `GenerationUiState.Failed`
- Modify: relevant JVM/Compose tests for reading access

**Interfaces:**

Map `GenerateArticleResult.NotConfigured` reasons to stable user-facing UI states. Add a distinct actionable state only if the current UI cannot explain “请先选择文章默认 AI 服务”; preserve the existing navigation callback into AI Profile settings.

- [ ] **Step 1: Add failing ViewModel/UI tests**

Assert `NoDefaultProfile` and invalid-default results produce a configuration-guidance state, do not show generic network failure, do not open an article, and expose the existing “去配置 AI 服务” action. Assert a local reusable article still opens without the default.

- [ ] **Step 2: Run targeted reading tests and verify RED**

Run the existing reading ViewModel and Compose test classes. Expected: the new reason currently maps to the generic or missing state.

- [ ] **Step 3: Implement stable mapping and copy**

Use concise Chinese copy: “请先选择文章默认 AI 服务。” for no selection and “文章默认 AI 服务已不可用，请重新选择。” for invalid binding. Do not include Profile ID, Endpoint, model, secret alias, or Key. Keep learning completion state untouched.

- [ ] **Step 4: Run targeted and full JVM tests**

Expected: new guidance tests pass and existing failure mappings remain unchanged.

---

### Task 7: Run full verification with protected real-device data

**Files:**
- No new production files.
- Verify changed files, `app/schemas/16.json`, tests, and the two design/plan documents.

- [ ] **Step 1: Check diff boundaries before building**

Run:

```bash
git -C D:/EnglishLearningWorktrees/f1-04-unlock-verify status --short
git -C D:/EnglishLearningWorktrees/f1-04-unlock-verify diff --check
git -C D:/EnglishLearningWorktrees/f1-04-unlock-verify diff --stat
```

Confirm unrelated pre-existing UI modifications, untracked outputs, speech plans/specs, and protected files are not overwritten or staged.

- [ ] **Step 2: Run the complete JVM suite**

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --no-daemon --no-build-cache --console=plain
```

Expected: all unit tests pass, including selector, generation, ViewModel, existing security, MiMo, and speech tests.

- [ ] **Step 3: Build both APKs**

```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-build-cache --console=plain
```

Do not run `connectedDebugAndroidTest`.

- [ ] **Step 4: Capture valid database baseline**

On device `bf353dda`, use the exact database filename `english-learning.db` and `MSYS_NO_PATHCONV=1` to pull `english-learning.db`, `english-learning.db-wal`, and `english-learning.db-shm` before installation/testing. Confirm pulled SQLite files are not error text and record MD5 values.

- [ ] **Step 5: Install in place and run instrumentation**

Install both freshly built APKs using `adb install -r -t`; never uninstall or clear data. Set MIUI appops if needed, then run targeted default-profile tests and the complete instrumentation suite with `am instrument -w`. Launch through the exported `BrandLaunchActivity` only when a manual screenshot/inspection is required.

- [ ] **Step 6: Capture post-test database evidence and compare**

Pull the same three files again and compare each MD5 byte-for-byte with the pre-test baseline. If any file differs, stop and report the discrepancy; do not claim data safety.

- [ ] **Step 7: Review final diff and leave changes uncommitted**

Verify the implementation and tests are complete, but do not create a commit or push. Report test counts, APK build status, schema migration result, database MD5 comparison, and any unresolved issue.
