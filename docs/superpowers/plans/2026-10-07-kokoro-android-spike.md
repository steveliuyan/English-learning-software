# Kokoro Android Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在测试专用边界内把桌面已验证的 Kokoro 单词链路推进到 Android Engine 和真机可审计验证，不改变正式发音路由。

**Architecture:** 先通过第三方台账门禁，再以构造注入的 `OnnxKokoroEngine` 隔离 ONNX session、固定资产和前处理；Android instrumentation 仅通过显式测试入口调用。模型和 voice/frontend 不进入正式 `main` 资源、不接入 Hilt 默认 Provider，所有准备与推理在后台线程执行。

**Tech Stack:** Kotlin、Android API 26+、ONNX Runtime Android 1.29.0、现有 `KokoroEngine`/`KokoroPronunciationProvider`、JUnit 4 instrumentation、现有 Gradle lockfile 和第三方 NOTICE 校验。

**Spec:** `docs/superpowers/specs/2026-10-07-kokoro-android-spike-design.md`

## Global Constraints

- 不修改 `PronunciationRouter`、Hilt 默认绑定、词卡行为、文章朗读、MiMo/OpenAI 路由或缓存键。
- 不使用 `connectedDebugAndroidTest`；真机只使用 `assembleDebug assembleDebugAndroidTest`、`adb install -r -t` 和 `am instrument`。
- 未通过许可证核验前，不修改 Gradle 依赖、不复制模型、不生成测试 APK。
- API Key、Room、网络请求和正式 APK 资源不参与 Kokoro Spike。
- 所有模型加载和推理必须在 `Dispatchers.IO` 或 `Dispatchers.Default` 执行；主线程调用不得同步加载。
- 真机前后核对 `english-learning.db`、`.db-shm`、`.db-wal` 三件套，不能用时间戳替代字节级核验。
- 用户已明确暂不提交、暂不推送；本计划不包含 commit/push。

---

### Task 1: 完成 Android Spike 许可证门禁

**Files:**
- Modify: `docs/third-party-notices.md`
- Read/verify: `tools/verify-third-party-notices.main.kts`
- Verify: `build.gradle.kts`, `gradle/libs.versions.toml`
- Evidence: `outputs/kokoro-spike-20261006/android-notice-verification.log`

**Interfaces:**
- Consumes: 固定 Kokoro/Phonemis/ONNX Runtime 资产清单及已有大小/SHA-256 证据。
- Produces: 每个待接入资产均包含名称、版本、许可证、用途、数据流、NOTICE 位置、替代方案、商业分发结论；校验命令 exit 0 后才允许 Task 2。

- [ ] **Step 1: Write the failing ledger fixture/check**

  在临时副本中删除一个必填字段，运行项目台账任务，确认它报告具体条目和缺失字段；不修改正式台账来制造假阳性。

- [ ] **Step 2: Run the checker and capture the RED evidence**

  Run from the isolated worktree:

  ```text
  ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat verifyThirdPartyNotices --no-daemon --no-build-cache --console=plain
  ```

  Expected: 若当前条目有缺项，exit non-zero 且输出缺失字段；若当前 Gradle 校验已通过，则记录该事实并继续补充 Kokoro 条目测试。

- [ ] **Step 3: Add only fixed Kokoro entries**

  添加 ONNX Runtime Android `1.29.0`、社区 Kokoro INT8/voice pack、Phonemis en-us/en-gb frontend，并保留社区转换代码 MIT、Kokoro 模型 Apache-2.0、Phonemis MIT 的边界；条目明确“当前仅 Spike 外部/测试资产，未进入正式 APK”。

- [ ] **Step 4: Run the checker GREEN**

  使用同一 Gradle 命令，期望输出 `Validated ... third-party notice entries.`、exit 0；把完整输出保存到 `outputs/kokoro-spike-20261006/android-notice-verification.log`。若 Kotlin script 独立命令仍缺 runtime，报告环境阻塞，不声称 script 通过。

- [ ] **Step 5: Verify dependency lock before code**

  确认 `app/gradle.lockfile` 在加入依赖前没有被改动并保存摘要。用户已批准固定测试依赖；只通过 Gradle 解析 androidTest 配置并生成锁，逐项审查增量，不手写或自动改写无关锁项。ORT 不在旧锁中是正常新增测试依赖，不是需要再次确认的阻塞。

---

### Task 2: 为 Android Engine 写失败测试契约

**Files:**
- Create: `app/src/test/java/com/example/englishlearning/language/infrastructure/OnnxKokoroEngineTest.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/language/KokoroEngineAvailabilityTest.kt`
- Create: `app/src/kokoroSpike/kotlin/com/example/englishlearning/language/spike/KokoroAndroidEngine.kt`，同目录纯 Kotlin 逻辑只加入 test/androidTest source set；既有同步 Engine/Provider 保持不变

**Interfaces:**
- Consumes: 固定资产 manifest、现有 `PronunciationAccent`、现有 `AudioPlayer`、桌面 `desktop_spike.py` 的输入/输出契约。
- Produces: 可测试的 prepare/synthesize/close 行为；在未准备或资产失败时不创建 session；英美使用各自 frontend/voice；取消可传播。

- [ ] **Step 1: Write the smallest failing test for prepare lifecycle**

  测试期望构造 Engine 后 `isReady()` 为 false，`prepare()` 完成后才为 true，`close()` 后再次为 false；缺少实现时应因类型/方法不存在失败。

- [ ] **Step 2: Run the focused JVM test and verify RED**

  ```text
  ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:testDebugUnitTest --tests 'com.example.englishlearning.language.infrastructure.OnnxKokoroEngineTest' --no-daemon --no-build-cache --console=plain
  ```

  Expected: 因 `OnnxKokoroEngine` 尚不存在或契约不足而失败；若出现环境错误，先区分编译错误和 Gradle 输出锁，不修改测试掩盖失败。

- [ ] **Step 3: Add independent failure tests**

  覆盖：
  - 任一固定资产 size/SHA 错误时 `prepare()` 返回不可用且 session factory 构造次数为 0。
  - 未 prepare 的 `synthesize()` 返回 unavailable，不同步创建 session。
  - `prepare()`/`synthesize()` 的执行线程不是 main looper 线程。
  - `close()` 幂等且释放 session。
  - American/British 使用不同 frontend 和 voice manifest。
  - ONNX/前处理异常返回 failed，不写成功结果。
  - 协程取消继续抛出 `CancellationException`。

- [ ] **Step 4: Add instrumentation acceptance assertions**

  `KokoroEngineAvailabilityTest` 只通过显式测试 Engine 工厂运行，验证 `apple` 在两种口音生成非空 WAV、24 kHz mono PCM16 元数据、可交给 `AndroidAudioPlayer`，并记录 prepare/synthesis/playback 耗时；不读 Room、不输出模型内容。

- [ ] **Step 5: Run RED again and preserve logs**

  在实现前运行 focused JVM test 和 instrumentation 编译检查；将失败原因保存到 `outputs/kokoro-spike-20261006/android-engine-red.log`，确保失败来自缺失行为而非测试拼写错误。

---

### Task 3: 实现最小测试专用 Android Engine

**Files:**
- Create: `app/src/kokoroSpike/kotlin/com/example/englishlearning/language/spike/` 的纯 Kotlin Engine、资产校验、CTC、NPZ/WAV逻辑（test/androidTest共享，main/debug/release均不编译）
- Create: `app/src/androidTest/java/com/example/englishlearning/language/spike/OrtKokoroRuntime.kt`，仅此适配器引用 ORT
- Modify: `gradle/libs.versions.toml` only after Task 1 GREEN，固定 ORT 1.29.0
- Modify: `app/build.gradle.kts` only for androidTestImplementation ORT、共享测试源码目录和测试资产 source
- Generate with Gradle: `app/gradle.lockfile`，受控 androidTest 解析锁增量

**Interfaces:**
- Produces: `OnnxKokoroEngine.prepare(): Result<Unit>`, `isReady()`, `synthesize(text, accent): Result<ByteArray>`, `suspend close()`；Mutex 串行化 prepare/synthesize/close，关闭后不可重新打开，取消传播并关闭已创建资源；使用注入的 asset reader、session factory、dispatcher，便于不依赖真实 native runtime 的 JVM 测试。

- [ ] **Step 1: Implement asset manifest and hashing**

  用固定相对路径、字节大小、SHA-256 建立 manifest；读取完整文件前先检查 size，随后流式计算 SHA-256；错误返回不可用且不创建 session。

- [ ] **Step 2: Implement background prepare and idempotent close**

  在注入 dispatcher 上创建 frontend/Kokoro sessions，配置 ORT CPU provider 与 intra-op 2/inter-op 1；保存 ready 状态；重复 prepare 不重复创建；close 释放所有 sessions 并清理状态。

- [ ] **Step 3: Implement restricted word frontend and CTC decode**

  复用桌面已验证契约：trim/lower、1–20 ASCII graphemes/internal apostrophe、116 类 argmax、连续重复合并、blank=0 移除、固定 PHONE_LABELS 映射；拒绝非法 shape、NaN/Inf、全 blank 和不支持字符。

- [ ] **Step 4: Implement Kokoro input and WAV output**

  使用匹配 accent 的 voice，构造双零 token 边界、`style=voice[tokenCount]`、`speed=1.0`；验证音频有限且量化后非静音，编码为 24 kHz mono PCM16 WAV；不返回模型内部 buffer。

- [ ] **Step 5: Run focused JVM tests GREEN**

  运行 Task 2 的 focused test；再运行既有 `KokoroPronunciationProviderTest`。期待零失败、零错误；失败时只修实现，不放宽断言。

---

### Task 4: 构建测试 APK 并进行真机取证

**Files:**
- Modify: `app/src/androidTest/java/com/example/englishlearning/language/KokoroEngineAvailabilityTest.kt`
- Create: `outputs/kokoro-spike-20261006/android-build-and-device.md`
- Create: `outputs/kokoro-spike-20261006/device-before/` and `device-after/` only for evidence

**Interfaces:**
- Consumes: Task 3 Engine、固定测试资产和现有设备取证脚本约定。
- Produces: debug/androidTest APK 构建日志、APK 条目大小/增量、真机模型加载/生成/播放/内存数据、Room 三件套前后逐字节证据。

- [ ] **Step 1: Build both APKs without connected task**

  ```text
  ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-build-cache --console=plain
  ```

  保存 exit code、APK 路径、ZIP 条目和体积；禁止 `connectedDebugAndroidTest`。

- [ ] **Step 2: Capture device database baseline**

  用 `MSYS_NO_PATHCONV=1 adb exec-out run-as ...` 拉取主库、`.db-shm`、`.db-wal` 的字节副本并计算 MD5；设备测试前后使用同一方法。

- [ ] **Step 3: Install in place and set MIUI permission if needed**

  使用 `adb install -r -t` 安装两个 APK，不卸载、不清数据；Compose/Activity instrumentation 前按既有设备约定设置 `adb shell appops set com.example.englishlearning 10021 allow`。

- [ ] **Step 4: Run focused instrumentation manually**

  ```text
  MSYS_NO_PATHCONV=1 adb shell am instrument -w -e class com.example.englishlearning.language.KokoroEngineAvailabilityTest com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
  ```

  记录每个 accent/word 的 prepare、frontend、Kokoro、播放耗时、成功状态、音频 SHA-256 和 native/Java heap 观察值；日志禁止模型 payload、用户数据和凭据。

- [ ] **Step 5: Verify database and package boundaries**

  重新拉取 Room 三件套并逐字节比较；解析 debug/androidTest APK，确认模型是否仅在测试 APK/外部测试资产中，报告正式 debug APK 的增量。任一数据库字节变化或资产边界不清均阻止 GO。

- [ ] **Step 6: Write device report and retain NO_GO until review**

  报告必须同时列出通过项、未测项、设备型号/API、APK 体积、内存、播放结果、Room 哈希以及 `NO_GO_KEEP_SYSTEM_TTS`；真机成功不自动授权正式路由接入。

---

### Task 5: Final review gate

**Files:**
- Modify: `docs/decisions/2026-10-06-kokoro-word-pronunciation.md`
- Modify: `outputs/kokoro-spike-20261006/REPORT.md`
- Modify: `.workbuddy/memory/2026-10-07.md` in the main project workspace only after evidence exists

**Interfaces:**
- Consumes: Tasks 1–4 evidence.
- Produces: 明确的 `NO_GO_KEEP_SYSTEM_TTS` 或“可进入正式接入评审”；不修改正式路由。

- [ ] **Step 1: Run full verification**

  运行许可证校验、focused JVM、assemble 两个 APK 和 focused instrumentation；记录每个命令的 exit code，不复用旧日志冒充本轮结果。

- [ ] **Step 2: Independent review**

  复核资产哈希、APK 边界、线程证据、播放结果、内存、Room 三件套和报告术语；不能把桌面耗时外推成 Android 通过。

- [ ] **Step 3: Stop before production integration**

  即使全部 Spike 门禁通过，也只形成独立正式接入评审材料；生产 Hilt 路由、词卡默认行为、缓存键和 UI 变更另立审批批次。
