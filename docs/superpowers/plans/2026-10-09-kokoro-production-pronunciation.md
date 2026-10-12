# Kokoro 生产单词发音与 MiMo 句子朗读实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已验证的 Kokoro Android 单词音频路径安全接入生产词卡/词典单词朗读，支持美式与英式；将 MiMo 固定用于例句/文章朗读；删除 OpenAI pronunciation route，同时保留其他非发音 OpenAI 能力。

**Architecture:** 生产发音按内容语义分为两个显式入口：`speakWord(word, accent)` 固定使用 Kokoro，`speakSentence(sentence, accent)` 使用 MiMo 并在句子/文章场景允许系统 TTS 最后兜底。Kokoro 使用真实 Android ONNX runtime 的挂起式准备/合成/关闭生命周期，由应用级依赖注入提供；若模型、voice、frontend、ORT 依赖或许可证不能形成可审计的 production APK 边界，则停止在交付门禁，不接入 Router/UI。

**Tech Stack:** Kotlin/JVM 17、Android、Hilt、Room、Kotlin coroutines、ONNX Runtime Android、JUnit 5 JVM tests、AndroidJUnit4 instrumentation、Kokoro 24 kHz mono PCM16 WAV。

**Spec:** `docs/superpowers/specs/2026-10-08-kokoro-android-explicit-verb-design.md`（Spike 证据与边界）；本计划新增的生产方向约束以本文件 Architecture 与 Global Constraints 为准。

## Global Constraints

- 不运行 `connectedDebugAndroidTest`；真机仅使用 `assembleDebug`/`assembleDebugAndroidTest`、`adb install -r -t` 和显式 `am instrument`。
- 不卸载应用、不清理数据、不回滚、不使用 destructive Git 操作、不提交、不推送。
- 不覆盖或清理工作树已有未提交/未跟踪改动；词书页面验收成果保持不变。
- Kokoro 单词失败时不得回退到 MiMo 或系统 TTS；否则会冒充权威单词音源。
- MiMo 仅用于例句、对话式内容和文章；句子/文章失败后才允许系统 TTS 兜底。
- OpenAI pronunciation route、对应 Router 分支、发音 DI 装配和语音设置候选必须移除；非发音 OpenAI AI Profile、文本/图片能力不得删除。
- API Key 仅通过现有 `SecretStore`/KeyStore 读取；不得进入 Room、日志、APK、截图、错误正文或持久化缓存。
- Kokoro 合成后的音频 ByteArray 在播放完成、失败或异常路径均必须清零；运行时关闭必须释放 native 资源。
- 真实 Kokoro 资源必须有来源、许可证、大小与 SHA-256 校验；不可凭记忆手写词典音素或替换未经审计的模型文件。
- 每个新行为先写独立 RED 测试并观察预期失败，再写最小 GREEN；新断言使用独立变异证明可变红。
- 真机验证前后必须拉取主库、`.db-shm`、`.db-wal` 并逐字节比较；任何差异阻止通过并调查。

---

## 文件与职责地图

- Modify: `app/build.gradle.kts` — 将受审计的生产 Kokoro source/dependency/resource 纳入生产编译；删除当前仅用于隔离的 debug exclude，但不把测试 fixture 带入正式 APK。
- Create/Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/KokoroAndroidEngine.kt` — 生产 Android 引擎契约与生命周期适配，保留真实 runtime 的 `prepare/synthesize/close` 语义。
- Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/KokoroPronunciationProvider.kt` — 生产 Kokoro Provider，负责 lazy prepare、US/GB accent、播放和音频清零。
- Create/Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationContent.kt` — 定义 word/sentence 语义入口或等价内容类型，禁止调用方用通用 `speak(text)` 混淆内容边界。
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationRouter.kt` — 固定单词 Kokoro、句子 MiMo→System fallback，删除 OpenAI 分支与 factory。
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/SpeechPreference.kt`、`RoomSpeechPreferenceRepository.kt`、`SpeechPreferenceEntity.kt` — 移除 OpenAI 发音选择，兼容旧值并规范化为 MiMo 或 System TTS，不删除历史 profile 数据。
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt` — 提供真实 Kokoro engine/provider、MiMo factory 和新 Router；不再提供 OpenAI pronunciation factory。
- Modify: `app/src/main/java/com/example/englishlearning/ui/SpeechSettingsViewModel.kt`、`SpeechSettingsScreen.kt`、`SettingsScreen.kt` — 只展示 System TTS/MiMo 句子设置，处理历史 OpenAI 值的规范化显示。
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`、`WordCardScreen.kt`、`CardDetailScreen.kt` — 单词按钮/自动朗读使用 word 入口；例句按钮使用 sentence 入口；不让词典单词走 MiMo/System。
- Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/OpenAiCompatiblePronunciationProvider.kt` — 仅在确认无其他发音消费者后移除生产发音装配或标记为非发音不可达；保留其他 OpenAI-compatible AI 功能文件。
- Create/Modify: `app/src/test/java/com/example/englishlearning/language/domain/PronunciationRouterTest.kt` — word/sentence/OpenAI legacy negative tests。
- Create/Modify: `app/src/test/java/com/example/englishlearning/language/infrastructure/KokoroPronunciationProviderTest.kt` — provider 生命周期、accent、失败与清零测试。
- Modify: `app/src/test/java/com/example/englishlearning/language/infrastructure/KokoroProductionBoundaryTest.kt` — 由“production 不含 Kokoro”改为“production 含允许的 Kokoro 类且不含 test fixture/完整 Spike 包”。
- Create/Modify: `app/src/androidTest/java/com/example/englishlearning/language/KokoroProductionPronunciationTest.kt` — 真机 US/GB 真实生成与播放、WAV、资源和失败证据。
- Create: `docs/decisions/2026-10-09-kokoro-production-pronunciation.md` — 记录生产边界、降级策略、资源审计和验收结论；不覆盖既有 Spike 决策。

---

### Task 1: 审计 Kokoro 生产资源与运行时交付门禁

**Files:**
- Read: `app/src/kokoroSpike/kotlin/com/example/englishlearning/language/spike/KokoroAndroidEngine.kt`
- Read: `app/src/androidTest/java/com/example/englishlearning/language/spike/OrtKokoroRuntime.kt`
- Read: `app/build.gradle.kts`
- Read: `app/src/androidTest/assets/kokoro-notices/SOURCES.txt`
- Create: `docs/decisions/2026-10-09-kokoro-production-pronunciation.md`
- Test: `app/src/test/java/com/example/englishlearning/language/infrastructure/KokoroProductionBoundaryTest.kt`

**Interfaces:**
- Consumes: 当前 Spike 资源 manifest、ORT 依赖、runtime factory、许可证与 APK 边界。
- Produces: 一份明确的生产资源清单和可执行边界断言；若资源/许可证/ORT 不能进入 production，则输出阻断结论而不继续接线。

- [ ] **Step 1: 写资源审计失败测试**

将 `KokoroProductionBoundaryTest` 从旧的“类不存在”改为独立检查：生产 debug class output 中必须有生产 Kokoro adapter；`androidTest/assets` 的 fixture、Spike 包名和审计样本不得出现在生产 APK 输入；生产资源每项必须存在且匹配 manifest 的 size/SHA-256。

- [ ] **Step 2: 运行门禁测试观察正确失败**

Run:
```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat --project-dir D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests '*KokoroProductionBoundaryTest*' --no-daemon --no-build-cache --console=plain
```
Expected: FAIL because production output still excludes Kokoro and no production resource/runtime adapter exists；不得接受编译错误替代行为失败。

- [ ] **Step 3: 固定生产资源清单与许可证证据**

只允许使用现有已核验的 model、voices、config、en-us frontend、en-gb frontend 资源。把每个相对路径、大小、SHA-256、来源 revision 和许可证写入生产 manifest；若资源只存在于 instrumentation asset，先停止本任务并记录缺失，不复制未知文件。

- [ ] **Step 4: 运行 APK 输入边界检查**

在不修改生产代码的情况下检查现有 APK ZIP 条目和 DEX：确认当前 debug APK 未含 Kokoro、ORT、Spike 实现；确认测试 APK 才含 Spike/ORT/fixture。保存命令输出到本轮验证目录，不覆盖旧证据。

- [ ] **Step 5: 更新决策记录**

记录“可生产化”或“门禁阻断”的单一结论。只有资源、许可证、ORT、模型体积与 runtime 生命周期均有明确证据，后续任务才允许把实现放入 production source set。

- [ ] **Step 6: 重新运行门禁测试**

Expected: 若交付门禁通过则 boundary test PASS；若资源仍不能受控交付，则保持 RED 并停止 Task 2 之后所有生产接线，向用户报告具体缺口。

---

### Task 2: 以 TDD 接入生产 Kokoro Android 生命周期

**Files:**
- Create/Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/KokoroAndroidEngine.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/infrastructure/KokoroPronunciationProvider.kt`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/example/englishlearning/language/infrastructure/KokoroPronunciationProviderTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/language/infrastructure/KokoroProductionBoundaryTest.kt`

**Interfaces:**
- Consumes: Task 1 通过的资源 manifest、生产 runtime factory 和 ONNX Runtime 依赖。
- Produces: 应用级可注入的 `KokoroAndroidEngine`，至少提供 `suspend fun prepare(): Result<Unit>`, `fun isReady(): Boolean`, `suspend fun synthesize(text: String, accent: PronunciationAccent): Result<ByteArray>`, `suspend fun close()`；Provider 提供 `PronunciationProvider`。

- [ ] **Step 1: 写 lifecycle RED 测试**

覆盖：未准备时单词请求触发一次 prepare；prepare 失败返回 `Unavailable`；American/British 分别传给 engine；合成返回空/异常时不播放；播放成功或失败后 ByteArray 全零；取消异常重新抛出。取消一次请求不得永久关闭应用级共享 engine：合成取消由 engine 释放当前 runtime，播放取消由 player 释放当前播放器；同一 Provider 以请求级 mutex 串行 readiness/prepare/synthesize/play，替代请求等待旧请求清理后重新检查 readiness。Kokoro 当前无音频缓存，普通播放与 forceRegenerate 均重新合成，两种口音及 Router 标志透传需行为回归。

- [ ] **Step 2: 运行单测确认失败原因正确**

Run:
```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat --project-dir D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests '*KokoroPronunciationProviderTest*' --no-daemon --no-build-cache --console=plain
```
Expected: 新增 lifecycle 断言 FAIL；已有 provider 合约测试不因断言顺序错误而遮蔽后续失败。

- [ ] **Step 3: 写最小 production adapter**

将 Spike 的 `KokoroAndroidEngine`/`OnnxKokoroEngine` 逻辑以生产包名、生产资源 root 和受控 runtime factory 接入；不得把测试 fixture、导出器或完整 Spike 测试辅助类复制进 main。Provider 用互斥/单次准备保护 runtime，所有合成结果和播放临时缓冲在 `finally` 清零。

- [ ] **Step 4: 调整 Gradle 生产输入**

删除仅针对 `compileDebugKotlin` 的 Kokoro exclude；将确实属于生产的 Kotlin 文件、ORT Android dependency、受审计资源和 license notice 加入对应 source/resource set；保持 `androidTest` 的测试资产独立。

- [ ] **Step 5: 运行 GREEN 与既有回归**

Run:
```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat --project-dir D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests '*KokoroPronunciationProviderTest*' --tests '*KokoroProductionBoundaryTest*' --no-daemon --no-build-cache --console=plain
```
Expected: 新增 lifecycle 测试与既有 Kokoro provider 测试全部通过；生产 boundary 只允许声明的 classes/resources。

- [ ] **Step 6: 对清零和 accent 做独立变异**

分别把 `audio.fill(0)` 移除、把 British 映射成 American、把 prepare 失败改成继续播放；每次只运行对应测试并确认变红，再恢复源码并重跑 GREEN。

---

### Task 3: 建立 word/sentence 语义路由并删除 OpenAI pronunciation route

**Files:**
- Create/Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationContent.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationRouter.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/PronunciationProvider.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/domain/SpeechPreference.kt`
- Modify: `app/src/main/java/com/example/englishlearning/language/RoomSpeechPreferenceRepository.kt`
- Modify: `app/src/main/java/com/example/englishlearning/core/storage/entity/SpeechPreferenceEntity.kt`
- Test: `app/src/test/java/com/example/englishlearning/language/domain/PronunciationRouterTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `KokoroPronunciationProvider`、现有 MiMo provider factory、System TTS provider。
- Produces: `suspend fun speakWord(word: String, accent: PronunciationAccent): PronunciationResult`；`suspend fun speakSentence(sentence: String, accent: PronunciationAccent = American, forceRegenerate: Boolean = false): PronunciationResult`。`PronunciationEngine` 不再包含 `OpenAi`；可保留 `SystemTts`/`MiMo` 仅作为句子设置语义。

- [ ] **Step 1: 写独立 RED 测试**

至少新增：
```kotlin
@Test
fun `american word uses kokoro only`()

@Test
fun `british word uses kokoro only`()

@Test
fun `sentence uses mimo and system is only final fallback`()

@Test
fun `kokoro word failure never falls back to mimo or system`()

@Test
fun `legacy openai preference never creates or calls openai provider`()
```

每个测试使用独立 recording provider/factory，并断言调用顺序、accent、profileId 和 fallback 边界；禁止只断言最终 `Played`。

- [ ] **Step 2: 运行 RED**

Run:
```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat --project-dir D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests '*PronunciationRouterTest*' --no-daemon --no-build-cache --console=plain
```
Expected: 新语义测试因缺少 word/sentence API 或仍调用旧 OpenAI 分支而失败；既有无关测试结果单独记录。

- [ ] **Step 3: 实现最小语义 Router**

`word` 入口直接调用 Kokoro provider；不得读取 MiMo/OpenAI profile 来替换单词音源。`sentence` 入口读取规范化后的句子偏好：MiMo 成功即返回，失败才调用 System TTS；System 设置直接调用 System。删除 OpenAI factory 类型、Router 构造参数与分支。

- [ ] **Step 4: 兼容旧数据库值**

Room 读取 `selectedEngine` 时对旧字符串 `OpenAi` 做兼容映射：若存在有效 MiMo profile 则映射 `MiMo`，否则映射 `SystemTts`；保存时不再写入 `OpenAi`。保留 `openAiProfileId` 数据字段只为非发音/历史数据兼容，且不再被 pronunciation Router 读取；不删除 profile/key。

- [ ] **Step 5: 运行 GREEN 与 repository 回归**

Run:
```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat --project-dir D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests '*PronunciationRouterTest*' --tests '*RoomSpeechPreferenceRepositoryTest*' --no-daemon --no-build-cache --console=plain
```
Expected: 新旧语义测试全部通过；OpenAI provider factory 不再出现在 Router 构造或调用链中。

- [ ] **Step 6: 做 OpenAI 删除负向变异**

恢复一个临时 OpenAI branch/factory 或把 legacy `OpenAi` 值强制保留；对应 negative test 必须失败。恢复后重跑全 Router 测试。

---

### Task 4: 更新 Hilt、设置页与词卡/词典 UI 接线

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/di/AppModule.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/SpeechSettingsViewModel.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/SpeechSettingsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/WordCardScreen.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/CardDetailScreen.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/SpeechSettingsViewModelTest.kt`
- Test: `app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt`

**Interfaces:**
- Consumes: Task 3 的 word/sentence Router API、Task 2 的生产 Kokoro engine/provider。
- Produces: Hilt 可构建的 production graph；词卡自动/点击单词走 Kokoro；CardDetail 美式/英式按钮走 Kokoro；例句按钮（若本轮已有 UI 容器）走 MiMo sentence；设置页不再提供 OpenAI pronunciation 选项。

- [ ] **Step 1: 写 UI/DI RED 测试**

覆盖：Hilt production graph 不需要 OpenAI pronunciation factory；学习词卡点击传 lemma 到 word 入口；CardDetail American/British 分别传 accent；例句点击传 sentence 入口；设置页历史 OpenAI 显示为规范化后的 MiMo/System，保存后不再写 OpenAI。

- [ ] **Step 2: 运行 RED**

Run:
```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat --project-dir D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:testDebugUnitTest --tests '*SpeechSettingsViewModelTest*' --no-daemon --no-build-cache --console=plain
```
以及在构建 instrumentation APK 后运行对应 AppScreen focused class。Expected: 断言因旧通用 `speak` 接线或 OpenAI 设置分支而失败。

- [ ] **Step 3: 更新 Hilt graph**

提供 production Kokoro engine/provider 和新的 Router 参数；移除 `provideOpenAiPronunciationProviderFactory` 及其 import；确认 `OpenAiCompatiblePronunciationProvider` 不再由 Hilt 发音 graph 引用。

- [ ] **Step 4: 更新 settings 兼容**

`SpeechSettingsUiState` 只保留 System/MiMo 句子设置；历史 OpenAI 值读取时展示规范化结果并保存规范化偏好；保留 AI Profile 列表里的非发音 OpenAI profile，不在通用 AI 设置中删除。

- [ ] **Step 5: 更新调用点**

将 `AppScreen` 中 lemma 的自动朗读、词卡点击、CardDetail US/GB 重新生成改为 `speakWord`；文章/例句路径改为 `speakSentence`。本任务不得把文章占位按钮误接为 Kokoro；若例句 UI 尚无独立按钮，只新增最小回调/按钮，不做无关阅读功能扩展。

- [ ] **Step 6: 运行 GREEN 与 Compose 回归**

Run JVM SpeechSettings/AppScreen tests；构建 `assembleDebug` 与 `assembleDebugAndroidTest`；执行已有词卡、CardDetail、SpeechSettings focused instrumentation。Expected: 词书 37 项历史验收相关代码不被改动，既有测试保持通过。

- [ ] **Step 7: 做调用偷换变异**

分别将 word 调用替换为 MiMo、将 British 替换为 American、将 sentence fallback 直接短路；对应独立测试必须红，随后恢复并复测。

---

### Task 5: 生产 APK、真机 Kokoro US/GB 与安全证据

**Files:**
- Modify: `app/src/androidTest/java/com/example/englishlearning/language/KokoroProductionPronunciationTest.kt`
- Create: `verification-logs/kokoro-production-20261009/REPORT.md`
- Create: `verification-logs/kokoro-production-20261009/verification-evidence.json`
- Create: `outputs/kokoro-production-20261009/`（仅生成新证据，不覆盖旧目录）

**Interfaces:**
- Consumes: Task 1–4 的 production APK、Router、Kokoro runtime、MiMo path 和 tests。
- Produces: 可复核的 JVM/DEX/APK/真机/数据库证据，以及未提交未推送的最终工作树状态。

- [ ] **Step 1: 记录设备与数据库基线**

确认 `bf353dda` 连接且包已安装；使用 `MSYS_NO_PATHCONV=1 adb exec-out run-as ... cat` 拉取主库、`.db-shm`、`.db-wal` 到新证据目录并记录 MD5、大小、时间。不得写回设备。

- [ ] **Step 2: 构建 production 与 instrumentation APK**

Run:
```bash
ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat --project-dir D:/EnglishLearningWorktrees/f1-04-unlock-verify :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --no-daemon --no-build-cache --console=plain
```
Expected: JVM 0 failures/0 errors；两个 APK 生成；APK ZIP 条目与 DEX 边界符合 Task 1。

- [ ] **Step 3: 原地安装并设置 MIUI 测试权限**

使用 `adb install -r -t` 安装两个 APK；不使用 connected task；必要时执行 `adb shell appops set com.example.englishlearning 10021 allow`。

- [ ] **Step 4: 运行真实 Kokoro focused instrumentation**

显式执行生产 Kokoro 测试类，验证 American/British：模型准备成功、WAV 为 RIFF/WAVE、24 kHz、mono、PCM16、非空、播放器返回 Played；验证同一词 US/GB 音频摘要不同且 provider 没有调用 MiMo/System。

- [ ] **Step 5: 验证失败与安全边界**

运行无模型/播放失败 fixture（仅测试注入，不改生产数据）验证 `Unavailable/Failed` 提示；从 logcat、APK、Room 导出和缓存扫描 Key、请求 body、音频残留、模型路径泄露；确认音频 ByteArray 播放后清零、KeyStore 外无 key。

- [ ] **Step 6: 数据库三件套复核**

测试结束再次拉取 DB/SHM/WAL，逐字节 `cmp`/SHA-256 对比；任何差异都停止并归因。记录 instrumentation 退出码和测试总数。

- [ ] **Step 7: 生成报告并核验工作树**

报告必须明确：通过的测试数、失败/跳过数、APK 边界、US/GB 音频摘要、失败提示、安全扫描、数据库比较、未提交/未推送状态。不得把旧 Spike 报告当作本轮生产证据。

---

## 验收矩阵

| 能力 | 必须通过的证据 | 失败时行为 |
|---|---|---|
| 单词 American | Router recording + Kokoro real engine + WAV + Played | 明确 Unavailable/Failed，不调用 MiMo/System |
| 单词 British | Router recording + Kokoro real engine + WAV + Played + accent British | 同上 |
| 例句/文章 | Router recording + MiMo profile + fallback order | MiMo 失败后才 System TTS |
| OpenAI 发音删除 | enum/Router/DI/settings/negative tests + legacy preference normalization | 不创建/不调用 OpenAI pronunciation provider |
| 音频安全 | provider tests + log/APK/cache scan | 任何残留阻止通过 |
| 数据安全 | DB/SHM/WAL 前后逐字节一致 | 停止验收并调查 |
| 生产边界 | APK ZIP/DEX/resource manifest | Spike/test fixture 进入 production 即阻断 |

## 执行前检查

- [ ] Task 1 资源/许可证/ORT 门禁已明确为 GO。
- [ ] Task 2 production Kokoro lifecycle 测试先 RED 后 GREEN。
- [ ] Task 3 word/sentence/OpenAI negative tests 先 RED 后 GREEN。
- [ ] Task 4 Hilt/settings/UI focused tests 通过。
- [ ] Task 5 JVM、APK、真机、数据库和安全证据齐全。
- [ ] 全程未提交、未推送、未卸载、未清数据。
