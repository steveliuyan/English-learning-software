# Talkify 风格供应商选择与回退 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户按 Talkify 逻辑选择系统 TTS、OpenAI 或 MiMo，并持久化选择、绑定 Speech Profile，实现 OpenAI → MiMo → 系统 TTS 回退。

**Architecture:** 新增设备级 `speech_preferences` 表保存选择和 OpenAI/MiMo Profile ID；所有供应商可用性由 AI Profile 的 Speech capability 与 KeyStore 状态实时派生。Router 扩展为三段 fallback，但保持取消透传；OpenAI 与 MiMo 共享现有安全音频请求、传输和播放链。

**Tech Stack:** Kotlin, Room v13, Hilt, Coroutines, Jetpack Compose, Android KeyStore, MediaPlayer。

**Spec:** `docs/superpowers/specs/2026-09-26-talkify-provider-selection-design.md`

## Global Constraints

- 不在 Room、日志、UI、APK 或源码中保存或显示 API Key。
- `speech_preferences` 只保存 provider/profile ID，不保存 endpoint、模型、音色、secret alias 或派生状态。
- Room 必须从 v12 迁移至 v13，不允许 destructive migration。
- 系统 TTS 是默认与最终 fallback；取消必须原样透传。
- ZipVoice 保持未下载状态；其他 Talkify 厂商保持待接入且不可点击。
- 所有实现先写失败测试，再写最小生产代码。

---

### Task 1: 建立 SpeechPreference Room 持久化与 v13 迁移

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/language/domain/SpeechPreference.kt`
- Create: `app/src/main/java/com/example/englishlearning/language/SpeechPreferenceRepository.kt`
- Create: `app/src/main/java/com/example/englishlearning/language/RoomSpeechPreferenceRepository.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/entity/SpeechPreferenceEntity.kt`
- Create: `app/src/main/java/com/example/englishlearning/core/storage/dao/InternalSpeechPreferenceDao.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/AppDatabase.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Test: `app/src/test/java/com/example/englishlearning/language/RoomSpeechPreferenceRepositoryTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/core/storage/AppDatabaseMigrationTest.kt`

**Interfaces:**

```kotlin
interface SpeechPreferenceRepository {
    suspend fun get(): Result<SpeechPreference>
    suspend fun save(preference: SpeechPreference): Result<Unit>
}
```

- [ ] **Step 1: Write the failing repository and migration tests**

Test default empty table result, same-device upsert, profile-ID round trip, and that migration v12→v13 preserves an existing AI Profile while creating `speech_preferences` without key/endpoint fields.

- [ ] **Step 2: Run tests to verify failure**

Run the target JVM test and migration instrumentation build. Expected: missing repository/entity/migration symbols.

- [ ] **Step 3: Add domain/entity/DAO/repository and v12→v13 migration**

Use primary key `device`; default engine `SystemTts`; nullable OpenAI/MiMo profile IDs. Add v13 entity/DAO to AppDatabase and migration SQL creating only `preferenceId`, `selectedEngine`, `openAiProfileId`, `miMoProfileId`.

- [ ] **Step 4: Run tests to verify pass**

Run target JVM test and migration test/build. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest app/schemas
git commit -m "feat: persist talkify speech preferences"
```

---

### Task 2: 抽取 OpenAI-compatible 远程语音 Provider

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/language/infrastructure/OpenAiCompatiblePronunciationProvider.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/MiMoPronunciationProvider.kt`
- Create: `app/src/test/java/com/example/englishlearning/language/infrastructure/OpenAiCompatiblePronunciationProviderTest.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`

**Interfaces:**

```kotlin
fun interface OpenAiPronunciationProviderFactory {
    fun create(profileId: String): PronunciationProvider
}
```

The shared provider accepts `profileId`, `voice`, `responseFormat`, profile repository, secret use case, audio transport and player. MiMo factory uses voice `mimo`; OpenAI factory uses voice `alloy`.

- [ ] **Step 1: Write failing provider tests**

Test OpenAI Profile request voice `alloy`, successful audio playback, key clearing, HTTP/playback failure and cancellation propagation.

- [ ] **Step 2: Run tests to verify failure**

Run target test. Expected: OpenAI-compatible provider/factory absent.

- [ ] **Step 3: Implement shared provider and thin MiMo wrapper/factory**

Reuse `TtsRequestBuilder`, `AudioHttpTransport`, `AudioPlayer`; clear response bytes after playback and key chars in finally. Do not duplicate HTTP or key logic.

- [ ] **Step 4: Run tests to verify pass**

Run provider tests. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add openai compatible pronunciation provider"
```

---

### Task 3: 扩展 Router 为 OpenAI → MiMo → 系统 TTS

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationRouter.kt`
- Modify: `app/src/test/java/com/example/englishlearning/language/domain/PronunciationRouterTest.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`

**Interfaces:** Router consumes a `SpeechPreference` loaded before playback, plus `OpenAiPronunciationProviderFactory` and `MiMoPronunciationProviderFactory`.

- [ ] **Step 1: Write failing router tests**

Cover OpenAI success; OpenAI unavailable/failure then MiMo success; OpenAI and MiMo failure then system success; missing profile IDs skip that remote provider; cancellation at either remote provider rethrows; explicit SystemTts does not create remote providers.

- [ ] **Step 2: Run tests to verify failure**

Run router test. Expected: missing OpenAi engine/factory and preference-aware route.

- [ ] **Step 3: Implement the minimal fallback chain**

Extend `PronunciationEngine` with `OpenAi`; chain providers based on selected engine and profile IDs; preserve `CancellationException`; only invoke next provider after unavailable/failed.

- [ ] **Step 4: Run tests to verify pass**

Run router tests. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: route openai pronunciation through mimo fallback"
```

---

### Task 4: 让 Router 读取持久化偏好并接通现有发音入口

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationRouter.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/MainActivity.kt` if constructor injection needs adjustment
- Test: `app/src/test/java/com/example/englishlearning/language/domain/PronunciationRouterTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/CardDetailScreenTest.kt`

- [ ] **Step 1: Write failing tests**

Test no-argument UI playback reads default `SystemTts`; a saved MiMo preference routes to that profile; a saved OpenAI preference uses OpenAI then fallback. Assert no UI state contains key text.

- [ ] **Step 2: Run tests to verify failure**

Run targeted tests. Expected: router defaults ignore repository.

- [ ] **Step 3: Inject SpeechPreferenceRepository and load preference before routing**

On repository failure use `SpeechPreference()` default. Do not expose preference/key objects to CardDetailScreen; it continues only sending text to `PronunciationProvider`.

- [ ] **Step 4: Run tests to verify pass**

Run targeted unit and Compose tests. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: apply saved speech provider preference"
```

---

### Task 5: 完成 Talkify 风格设置页选择与状态

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/SpeechSettingsViewModel.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/SpeechSettingsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/MainActivity.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/SpeechSettingsViewModelTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/SpeechSettingsScreenTest.kt`

- [ ] **Step 1: Write failing tests**

Test default system selection, candidate filtering to Speech profiles, missing key status, explicit save of OpenAI/MiMo profile IDs, failed save retaining previous UI selection, and no displayed secret. Compose-test current supplier marker and all pending providers with no click action.

- [ ] **Step 2: Run tests to verify failure**

Run target tests. Expected: missing ViewModel/screen/navigation.

- [ ] **Step 3: Implement ViewModel/screen/navigation**

Use `AiProfileRepository.list()` and `AiProfileSecretUseCase.hasKey()` to derive provider status. Provide selectable System TTS, OpenAI and MiMo. Give “AI 服务与密钥” a navigation action for adding missing profiles; retain ZipVoice as “未下载” and other providers as disabled “待接入”.

- [ ] **Step 4: Run tests to verify pass**

Run target tests and compile Android tests. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add talkify speech provider settings"
```

## Final Verification

```text
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --no-daemon --no-build-cache --console=plain
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-build-cache --console=plain
```

Install and instrument only through `adb install -r -t` and `am instrument`; do not use `connectedDebugAndroidTest`. Verify db/wal/shm MD5 before/after device tests.
