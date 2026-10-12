# Kokoro 本地单词发音 Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有生产发音链路的前提下，验证 Kokoro ONNX 是否适合作为 Android 端离线单词发音 Provider。

**Architecture:** 先建立模型资产与许可证核验边界，再实现一个独立、可替换的 `KokoroPronunciationProvider` 适配层。Spike 只允许由显式测试入口调用，不接入默认 Hilt 路由、不改变词卡行为；真机测试记录模型加载、生成、播放、内存和 APK 增量数据。Spike 通过后再单独立项接入正式单词路由。

**Tech Stack:** Kotlin、Android API 26+、现有 `PronunciationProvider`/`AudioPlayer` 边界、ONNX Runtime Android（仅在依赖和许可证核验后加入）、Kokoro 固定版本模型与美式/英式音色资产。

**Spec:** `docs/decisions/2026-10-06-kokoro-word-pronunciation.md`

## Global Constraints

- 不使用 `connectedDebugAndroidTest`；真机使用 `assembleDebug assembleDebugAndroidTest`、`adb install -r -t` 和 `am instrument`。
- 未完成模型、音色、phonemizer、ONNX Runtime 的许可证和 SHA-256 核验前，不把模型资产放入正式 APK。
- 不修改当前默认 Provider，不改变学习词卡、详情页、文章朗读和 MiMo 路由行为。
- API Key 不参与 Kokoro；不得新增 Key 存储、日志或网络请求。
- Kokoro 失败必须可报告为 `Unavailable`/`Failed`，不能阻塞学习流程。
- 所有模型推理必须在非 UI 线程执行；不得在主线程加载模型或生成音频。
- 真机取证前后核对 `english-learning.db`、`.db-shm`、`.db-wal` 三件套，不卸载应用、不清理数据。
- 每个新增第三方依赖、模型或数据文件必须登记版本、许可证、用途、数据流、NOTICE 位置和替代方案。

---

### Task 1: 固定 Spike 资产与许可核验边界

**Files:**
- Create: `docs/decisions/2026-10-06-kokoro-word-pronunciation.md`
- Modify: `docs/third-party-notices.md` only after official model/runtime/license evidence is collected
- Test: `app/src/test/java/com/example/englishlearning/notices/ThirdPartyNoticesTest.kt` only if a catalog dependency is added

**Interfaces:**
- Consumes: 官方 Kokoro 仓库/model card、ONNX Runtime Android 官方发布信息、模型和音色文件 SHA-256。
- Produces: 可审计的固定模型版本、音色选择、依赖版本、许可证结论和“不准进入正式 APK”的 Spike 边界。

- [ ] **Step 1: 记录候选版本和来源**

  记录 Kokoro 模型、ONNX 转换/发布来源、英美音色文件、phonemizer 运行时和 ONNX Runtime Android 的精确版本、官方 URL、下载文件名和 SHA-256。禁止使用只写“latest”的依赖或模型地址。

- [ ] **Step 2: 做许可证审查**

  为模型权重、音色、phonemizer、ONNX Runtime 分别记录许可证和商业分发限制。任何许可证无法确认的文件保持在 Spike 外部目录，不复制到 `app/src/main` 或 `app/src/main/assets`。

- [ ] **Step 3: 设定 Spike 退出标准**

  至少记录：美式单词 `schedule`、英式单词 `schedule`、重音不同的 `record`、普通单词 `apple`；要求生成可播放 WAV/PCM，失败时有明确状态，不触碰 Room 数据。

- [ ] **Step 4: 运行许可台账检查**

  Run: `kotlinc -script tools/verify-third-party-notices.main.kts docs/third-party-notices.md`

  Expected: 当前台账验证通过；若新增依赖别名，必须先补齐对应台账条目再继续构建。

---

### Task 2: 设计并测试 Kokoro Provider 的最小边界

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/language/infrastructure/KokoroPronunciationProvider.kt`
- Create: `app/src/main/java/com/example/englishlearning/language/infrastructure/KokoroEngine.kt`
- Create: `app/src/test/java/com/example/englishlearning/language/infrastructure/KokoroPronunciationProviderTest.kt`

**Interfaces:**
- Consumes: 现有 `PronunciationProvider`、`PronunciationAccent`、`PronunciationResult`、`AudioPlayer`。
- Produces:
  - `KokoroPronunciationProvider.speak(text: String)` 默认美式；
  - `KokoroPronunciationProvider.speak(text: String, accent: PronunciationAccent)`；
  - `KokoroEngine.synthesize(text: String, accent: PronunciationAccent): ByteArray`；
  - `KokoroEngine.isReady(): Boolean`。

- [ ] **Step 1: 写失败测试**

  测试以下行为：

  ```kotlin
  @Test
  fun americanAndBritishUseDifferentVoiceProfiles() = runTest {
      val engine = RecordingKokoroEngine()
      val player = RecordingAudioPlayer()
      val provider = KokoroPronunciationProvider(engine, player)

      provider.speak("schedule", PronunciationAccent.American)
      provider.speak("schedule", PronunciationAccent.British)

      assertEquals(
          listOf(PronunciationAccent.American, PronunciationAccent.British),
          engine.accents,
      )
      assertEquals(2, player.played.size)
  }
  ```

  另加：空白文本返回 `Unavailable`；引擎未就绪返回 `Unavailable`；引擎异常返回 `Failed`；引擎生成成功但播放失败不写缓存（如果 Spike Provider 接入缓存）。

- [ ] **Step 2: 运行测试确认 RED**

  Run: `./gradlew.bat :app:testDebugUnitTest --tests 'com.example.englishlearning.language.infrastructure.KokoroPronunciationProviderTest' --no-daemon --no-build-cache --console=plain`

  Expected: 因 Provider/Engine 尚不存在而失败；若测试直接通过，说明测试没有覆盖新行为，先修正测试。

- [ ] **Step 3: 写最小实现**

  Provider 只负责输入校验、准备状态检查、调用引擎、把生成的音频交给 `AudioPlayer.play(bytes, "wav")`，并清零临时 `ByteArray`。它不读取 Profile、不读取 API Key、不发网络请求、不修改 Room。

- [ ] **Step 4: 运行测试确认 GREEN**

  Run: 同 Step 2。

  Expected: Provider 单元测试全部通过；现有 JVM 回归保持通过。

- [ ] **Step 5: 做独立变异验证**

  临时将 American 和 British 都映射到同一个音色，运行口音隔离测试，确认测试失败；恢复实现后再次通过。变异前后保存并比较源码 SHA-256，不回滚其他未提交改动。

---

### Task 2.5: 外部桌面 CPU 契约预验证（2026-10-07）

在 Task 3 之前，用 `outputs/kokoro-spike-20261006/` 下独立 Python 脚本与 unittest 验证已核验英语 lexicon -> Kokoro v1.0 vocab -> 社区 INT8 ONNX -> WAV。该脚本不进入 Android 编译，不写 Room，不改变 Gradle、路由或 APK 资源；依赖仅安装到已有隔离 Python venv。

- 先 RED，再最小 GREEN，独立变异验证 asset hash、口音选择、边界 token、未知音素拒绝、voice 行选择、WAV 格式；恢复后重新运行。
- 社区 NPZ voice pack 的形状是 `[510,1,256]`，上游 kokoro-onnx 0.4.9 使用 row `len(tokens)`；本 Spike 仅接受 1–509 个可识别音素，510 会越过该容器行边界。KMP raw voice 的 `count - 1` 约定不能套用在此容器。
- 只测试固定 lexicon 命中，不宣称已实现完整 Phonemis 神经前端、词性 tagger 或未知词回退。`record` 仅测试 lexicon 默认名词读音；动词重音留待上下文策略。
- CPU 使用固定 ONNX Runtime 1.29.0，生成两种口音的 schedule、record、apple，并核对非空、有限、非静音的 24 kHz 单声道 PCM16 WAV；记录加载/生成耗时与采样数，明确这些不是 Android 真机指标。
- 图名称和 opset 差异仅是适配契约，不是不可兼容的证明；桌面实际运行结果决定这条局部路径是否成立。仍保持 NO_GO，直到 Task 3–5 全部通过。

### Task 2.6: Phonemis 神经前端与显式词性桌面验证（2026-10-07）

- 在 Task 2.5 的外部 Python 适配器/测试上增量实现，不改 Android/Gradle/正式资产。
- 固定 Phonemis 上游 revision `71eb1ce33bd586d38cbac037843b8539d7829c3b` 的 nn/constants.h、neural_phonemizer.cpp 契约：英语 grapheme apostrophe=1、a–z=2–27；不添加 Kokoro BOS/EOS；输出 116 类，argmax 后先去连续重复、再去 blank=0，按连续 phone 标签映射为符号，再用 Kokoro vocab 编码。
- 该单词探针只接受 trim/lower 后 1–20 个 ASCII 字母/内部 apostrophe，拒绝不支持字符、超长单词和句子，不能沿用上游静默跳过字符或擅自截断。全语言输入/长词分块另验。
- 词典命中保持优先；显式 Penn tag 优先精确 `word|tag`，再查 NN*/VB*/JJ*/RB* 等父类 key，最后 default。没有 tag 时 record 使用既有 default；不硬编码 record 音素、不宣称自动 tagger 完成。
- 测试先 RED，输入 dtype/shape/字符、CTC 顺序/标签/非法 logits、显式词性选择逐项独立变异，再恢复全量 GREEN。
- 固定 en-us/en-gb frontend 大小/SHA-256 核验后，ORT 1.29 CPU 验证目标词强制 neural 与词典输出并列、未知词 workbuddy 的实际未命中回退、record 默认/显式 VERB；保存来源标记、耗时、WAV并独立回读。
- 未知词可生成只证明链路运行，不证明发音正确；完整 HMM tagger/文本处理/Android/播放/性能仍为门禁，继续 NO_GO。
- [x] 完成完整 116 类标签回归 fixture 与 `--mode frontend` 可复现入口，默认 mode 保持 lexicon。
- [x] 检查 7 个固定资产和 runtime/graph 契约；非空输出目录拒绝，推理异常传播，全部配置成功后才写结果 JSON。
- [x] 独立验证 repeat `[1,2]`、实际 voice/style/tokens、来源标记、session 构造前完整性拒绝；13 项 CLI 内存变异均触发断言，errors=0，前后源码 SHA-256 相同。
- [x] 新鲜全量回归 45/45（`cli-closeout-green.log`，86.726 s）；`cli-20261007` 的 32 条真实 WAV 独立回读、16 组重复与上一轮 32 个摘要一致。此次续接只重跑测试/回读，没有重新推理。
- [ ] 许可台账、最终分发 NOTICE、人工听感、Android/JVM 与真机验收；不从桌面结果推断通过。

### Task 3: 接入 ONNX Runtime 与 Kokoro Engine（Spike-only）

**Files:**
- Modify: `gradle/libs.versions.toml` only if the exact ONNX Runtime Android version is selected
- Modify: `app/build.gradle.kts` to add the dependency in a Spike-specific configuration
- Create: `app/src/main/java/com/example/englishlearning/language/infrastructure/OnnxKokoroEngine.kt`
- Create: `app/src/androidTest/java/com/example/englishlearning/language/KokoroEngineAvailabilityTest.kt`
- Modify: `docs/third-party-notices.md` with verified dependency/model entries

**Interfaces:**
- Consumes: Task 2 `KokoroEngine` contract, fixed ONNX model, voice assets and phonemizer configuration.
- Produces: Android implementation that loads assets off the main thread and returns deterministic PCM/WAV bytes for American/British voices.

- [ ] **Step 1: Add only the verified dependency**

  Add the selected ONNX Runtime Android artifact using a pinned version and matching notice entry. Do not add a dynamic version. Do not add model files until their source, hash and license are recorded.

- [ ] **Step 2: Add asset integrity checks**

  Before loading each model/voice asset, calculate SHA-256 and compare against the pinned manifest. A mismatch returns `Unavailable("model asset integrity check failed")`; do not attempt inference with a mismatched file.

- [ ] **Step 3: Implement background loading**

  `OnnxKokoroEngine` must expose a suspend `prepare()` or equivalent readiness operation that runs on `Dispatchers.Default`/`Dispatchers.IO`; `synthesize()` must reject calls before readiness instead of loading synchronously on the UI thread.

- [ ] **Step 4: Add instrumentation availability test**

  The test must prepare the engine, synthesize `apple` in both accents, assert non-empty audio and expected sample format metadata, then release the ONNX session. It must not access Room or print model contents.

- [ ] **Step 5: Build the test APK**

  Run: `ANDROID_HOME=D:/Android/Sdk GRADLE_USER_HOME=D:/Android/GradleCache ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-build-cache --console=plain`

  Expected: both APKs build successfully with no unresolved dependency or missing asset errors.

---

### Task 4: 真机音频、性能和内存 Spike

**Files:**
- Modify: `app/src/androidTest/java/com/example/englishlearning/language/KokoroEngineAvailabilityTest.kt`
- Create: `outputs/kokoro-spike-20261006/REPORT.md`
- Create: `outputs/kokoro-spike-20261006/` only for generated evidence, not source assets

**Interfaces:**
- Consumes: Task 3 Android engine and fixed test words.
- Produces: 真机可重复的音频生成/播放/性能证据和明确的 go/no-go 结论。

- [ ] **Step 1: Capture database baseline**

  Run: `MSYS_NO_PATHCONV=1 adb exec-out run-as com.example.englishlearning md5sum /data/data/com.example.englishlearning/databases/english-learning.db /data/data/com.example.englishlearning/databases/english-learning.db-shm /data/data/com.example.englishlearning/databases/english-learning.db-wal`

  Save the three hashes in the report before the test.

- [ ] **Step 2: Install without uninstalling**

  Run `assembleDebug assembleDebugAndroidTest`, then `adb install -r -t` for both APKs. Never use `connectedDebugAndroidTest` and never clear application data.

- [ ] **Step 3: Run focused instrumentation**

  Run: `MSYS_NO_PATHCONV=1 adb shell am instrument -w -e class com.example.englishlearning.language.KokoroEngineAvailabilityTest com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner`

  Record test count, generation time for each word/accent, first-load time, audio byte size, heap delta, and playback result. Do not print keys or full model payloads.

- [ ] **Step 4: Verify audio behavior**

  Confirm both voices are non-empty and playable, American/British output uses distinct configured voice profiles, repeated synthesis with the same model/voice is deterministic enough for caching, and `record` does not silently use the wrong accent profile.

- [ ] **Step 5: Verify database non-interference**

  Re-run the three-file MD5 command. Any change must be investigated before a go decision; do not claim zero side effects from file timestamps alone.

- [ ] **Step 6: Write the report**

  Report model/runtime versions, hashes, licenses, device model/API, first-load time, per-word latency, audio sizes, memory observations, test result, database hashes and decision: `GO_TO_FORMAL_INTEGRATION` or `NO_GO_KEEP_SYSTEM_TTS`.

---

### Task 5: Spike review gate

**Files:**
- Modify: `docs/decisions/2026-10-06-kokoro-word-pronunciation.md`
- Modify: `outputs/kokoro-spike-20261006/REPORT.md`

**Interfaces:**
- Consumes: Tasks 1–4 evidence.
- Produces: Formal integration decision; no production routing change unless all exit criteria pass.

- [ ] **Step 1: Evaluate exit criteria**

  `GO` requires: licenses complete, hashes pinned, both accents synthesize/play on the target device, no main-thread inference, acceptable latency/memory/package increment, and database unchanged.

- [ ] **Step 2: Record fallback decision**

  If any criterion fails, keep current system TTS/MiMo routing and document the exact blocker. Do not leave a half-integrated Provider in the production graph.

- [ ] **Step 3: Run final verification**

  Run focused JVM tests, focused instrumentation, APK build and notice validation again. Record exact pass counts and exit codes in the report.

- [ ] **Step 4: Stop before formal integration**

  Formal Hilt routing, word-card default changes, cache-key migration and UI changes require a separate approved implementation batch after the Spike report is reviewed.
