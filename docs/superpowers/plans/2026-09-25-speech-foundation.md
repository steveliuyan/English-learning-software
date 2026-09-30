# Talkify 风格语音基础接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立可替换的语音播放基础，使词卡和阅读点词能够通过统一接口播放，并为 Talkify 风格的 MiMo、OpenAI、本地 ZipVoice 及其他供应商预留清晰边界。

**Architecture:** 先扩展领域层 `PronunciationProvider` 为最小可替换播放契约，使用 fake 完成 JVM contract 测试；再加入 Android 系统 TTS 适配器作为当前可运行的临时实现，确保 UI 不直接依赖 Android API；最后把 CardDetailScreen 与 AppScreen 的现有发音回调显式接到用例/Provider。远程音频、本地 ZipVoice 下载和供应商设置页作为后续独立切片，不在本计划内伪造完成。

**Tech Stack:** Kotlin, Coroutines, Hilt, Jetpack Compose, Android TextToSpeech, JUnit4。

**Spec:** 本计划依据已确认的 Talkify 聚合语音方向及 `2026-09-25` 讨论结论。

## Global Constraints

- 不在源码、日志、APK 或普通数据库中写入任何 API Key。
- 不把 ZipVoice/Emilia 模型权重提交到 GitHub 或打进 APK。
- 当前本地模型仅作为后续可选下载能力，许可证按上游声明处理。
- 未真实实现的 Azure、火山、腾讯、阿里云百炼、MiniMax 只能标记为待接入。
- 新行为必须先写失败测试，再写最小生产代码。
- 不修改受保护文件 `app/gradle.lockfile`、`core/error/AppError.kt`、`ui/AppViewModel.kt`。

---

### Task 1: 扩展语音领域契约

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationProvider.kt`
- Create: `app/src/test/java/com/example/englishlearning/language/domain/PronunciationProviderContractTest.kt`

**Interfaces:**
- Produces `suspend fun speak(text: String): PronunciationResult`。
- `PronunciationResult` 至少包含 `Played`、`Unavailable(reason)`、`Failed` 三种可测试结果，不暴露 Android `TextToSpeech` 类型。

- [ ] **Step 1: Write the failing test**

测试应创建一个最小 fake provider，调用 `speak("ability")`，断言返回成功并记录文本；再断言空白文本返回不可用结果。

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests com.example.englishlearning.language.domain.PronunciationProviderContractTest --no-daemon --no-build-cache --console=plain`

Expected: 编译失败，因为契约尚未提供 `speak` 和结果类型。

- [ ] **Step 3: Write minimal implementation**

在领域层增加不依赖平台的结果类型和播放方法；保留既有 `capabilities()`，避免击穿现有调用方。

- [ ] **Step 4: Run test to verify it passes**

同上命令，Expected: PASS。

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/example/englishlearning/language/domain/PronunciationProvider.kt app/src/test/java/com/example/englishlearning/language/domain/PronunciationProviderContractTest.kt && git commit -m "feat: define pronunciation playback contract"`

---

### Task 2: 实现 Android TTS 适配器

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/language/infrastructure/AndroidTextToSpeechProvider.kt`
- Create: `app/src/test/java/com/example/englishlearning/language/infrastructure/AndroidTextToSpeechProviderTest.kt`
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`

**Interfaces:**
- Consumes `PronunciationProvider` contract from Task 1。
- Produces Hilt singleton binding for the provider。

- [ ] **Step 1: Write the failing test**

测试使用注入的 engine seam，不直接依赖设备 TTS：初始化失败返回 `Unavailable`；空白文本不调用 engine；正常文本传递原文。

- [ ] **Step 2: Run test to verify it fails**

运行该测试，Expected: 适配器和 engine seam 尚不存在而失败。

- [ ] **Step 3: Write minimal implementation**

封装 `TextToSpeech` 初始化、英文 Locale、`speak`、停止和释放；平台回调通过内部 seam 转为领域结果。不得在领域层引入 Android 类型。

- [ ] **Step 4: Run test to verify it passes**

运行 JVM 测试并执行 `:app:compileDebugKotlin`。

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/example/englishlearning/language/infrastructure/AndroidTextToSpeechProvider.kt app/src/main/java/com/example/englishlearning/di/AppModule.kt app/src/test/java/com/example/englishlearning/language/infrastructure/AndroidTextToSpeechProviderTest.kt && git commit -m "feat: add injectable android pronunciation provider"`

---

### Task 3: 接通词卡与阅读点词播放

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/CardDetailScreen.kt` only if semantics need no-op removal
- Modify: `app/src/main/java/com/example/englishlearning/ui/ArticleReadingScreen.kt` only for real word playback callback
- Create/Modify: matching `app/src/test` and `app/src/androidTest` UI tests

**Interfaces:**
- AppScreen obtains injected provider/use case and passes explicit `onSpeak` callbacks.
- Card detail playback remains UI callback based; no Android API enters composables.

- [ ] **Step 1: Write the failing test**

新增回归测试：学习词卡详情点击“播放发音”后调用真实接线回调；阅读词卡详情同样调用；未接入的全文按钮继续显示待接入状态，不得误报成功。

- [ ] **Step 2: Run test to verify it fails**

运行对应 JVM/Compose 测试，Expected: 当前 AppScreen 未传 `onSpeak`，计数保持 0。

- [ ] **Step 3: Write minimal implementation**

在 AppScreen 通过已有 Hilt 依赖获取播放用例或 provider，显式传递 `onSpeak = { ... }`；播放失败只更新可见错误状态，不改变学习反馈状态机。

- [ ] **Step 4: Run test to verify it passes**

运行目标 JVM 测试、`:app:assembleDebug` 和 `:app:assembleDebugAndroidTest`。

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/example/englishlearning/ui app/src/test app/src/androidTest && git commit -m "feat: wire pronunciation into word card details"`

---

### Task 4: 增加 Talkify 风格语音设置占位区

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt`
- Modify: Settings/AppScreen routing files identified by existing settings navigation
- Create: matching Compose test

**Interfaces:**
- UI only consumes immutable provider rows with `available`, `configured`, `downloaded`, `comingSoon` status。
- MiMo/OpenAI/ZipVoice rows可进入后续配置；其他供应商明确显示“待接入”。

- [ ] **Step 1: Write the failing test**

断言设置页显示 MiMo、OpenAI、本地 ZipVoice、Azure、火山引擎、腾讯云、阿里云百炼、MiniMax；待接入项不可点击且显示“待接入”。

- [ ] **Step 2: Run test to verify it fails**

运行 SettingsScreen Compose 测试，Expected: 当前页面没有语音组。

- [ ] **Step 3: Write minimal implementation**

增加独立“语音合成”分组和状态行，不实现虚假的网络配置或模型下载；本地 ZipVoice 行展示“模型未下载”和许可入口占位。

- [ ] **Step 4: Run test to verify it passes**

运行目标 Compose 测试与 JVM 全量测试。

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt app/src/androidTest && git commit -m "feat: add talkify-style speech provider settings"`

---

## 验收命令

```text
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --no-daemon --no-build-cache --console=plain
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache gradlew.bat -p D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-build-cache --console=plain
```

真机验证继续遵循项目约定：只使用 `adb install -r -t` + `am instrument`，不使用 `connectedDebugAndroidTest`，并核对数据库三件套 MD5。
