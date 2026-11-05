# 2026-11-05 发音缓存「关联实体」升到主键级 工作报告

推进 UI 重设计/质量轮（分支 `stage-5-ui-r2-learning-path`）的第 N 批（N 用
`git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD` 现算，别抄本文件）。
上一批 `d3b0f60` 给两套 AI 音频缓存补了完整性台账，但「关联实体」只落到内容类别级。

## 1. 本次目标

用户对口径结转项答复「可以改造」，即：把 Spec 03 F3-02 与 AGENTS.md 要求的「关联实体」
从 `Word`/`Sentence` + 文本，升到真正的主键级——词卡 `cardId`、文章 `articleId`、
拍照翻译记录 id 落进台账。

## 2. 完成了什么

- 新增 `language/domain/PronunciationEntityRef.kt`：`kind` + 可空 `id`，配
  `wordCard()` / `article()` / `photoTranslation()` 构造与 `SentenceAnalysis` / `Preview` 两个无主键常量；
  类别枚举含 `Unattached`，专门给「确实没有实体可交代」的入口，禁止拿词目伪造主键（决策 D-9）。
- `language/domain/PronunciationAudioMetadata.kt`：台账多 `entityKind` / `entityId` 两枚，**都带默认值**
  （决策 D-12：旧 sidecar 照读，判它们缺失=不可信会把整座缓存打回重新合成，MiMo 路径每次都是真花钱的付费请求）。
  可信性判定新增：未知类别、空白主键一律不可信。
- 接口扩签名：`PronunciationProvider.speakWord/speakSentence` 各多一个 `entity` 可空参；
  新增五参 `speak`，**默认实现丢弃 entity 委托四参版**（决策 D-10）——因此开工时实测的 37 处
  `override suspend fun speak*`（11 个文件）里，生产代码需要动的只有三处：转发层 `PronunciationRouter`
  与两个真会写音频缓存的 provider（端侧 Kokoro、云端 MiMo）；系统 TTS、OpenAI 兼容实现与其余假件零改动。
  需要同步签名的另外三个测试假件是缓存 `write` 扩参所致，不是 provider 扩参所致。
  现值复算：`grep -rn "override suspend fun speak" app/src/main app/src/test app/src/androidTest --include=*.kt | wc -l`。
- `language/domain/PronunciationRouter.kt`：实体透传到端侧 Kokoro、云端 MiMo，**以及 MiMo 失败后的系统 TTS 降级**。
- `language/infrastructure/KokoroPronunciationProvider.kt`、`MiMoPronunciationProvider.kt`：
  五参 override 承担实际工作，写缓存时带上实体。
- `language/domain/PronunciationAudioCache.kt` + 两个文件实现（`FilePronunciationAudioCache`、
  `FileSynthesizedAudioCache`）：`put` / `putIfSuccessful` / `write` 接 `entity`，落进 sidecar。
- `ui/AppScreen.kt`：16 处发音调用点全部显式带 `entity=`；词卡屏的 `onSpeak` 回调只交出 lemma，
  主键改从当前 `cardState` 取，取不到就留 null。`ui/SpeechSettingsViewModel.kt` 的试听改走
  `speakSentence(text, entity = Preview)`，从而也落在调用点闸的管辖下。
- 新增/修改文档：决策 `docs/decisions/2026-11-05-pronunciation-entity-ref-threading.md`（D-7~D-13）、
  计划 `docs/superpowers/plans/2026-11-05-pronunciation-entity-ref-threading.md`、
  `docs/specs/03-…md` F3-02 指针改指新决策、`docs/verification/ac-traceability-matrix.md` AC3-03、
  `docs/verification/negative-test-audit.md` 第 6 行与 §2、并把前两批报告里
  「关联实体只到类别级/主键接线结转」两条遗留改写为已收口。
- **没动的**：缓存键语义（实体不进键，D-8）、备份/Room/导出面（D-11）、schemaVersion 仍为 1。

## 3. 验证记录

RED（先写测试，`.git/qa-scratch/`）：
- `r6-entity-red-msgs.txt`：`AudioCacheLedgerContractTest#everyAppScreenSpeakCallSiteNamesItsEntity`
  报「16 处发音调用点里有 16 处没给关联实体」；`PronunciationAudioMetadataTest#aLedgerThatNamesTheCardBehindItIsReadable` 红。
- 第一轮 RED 被我自己污染过：`legacyLedgerJson()` 的原始字符串以 `hello"""` 收尾，闭引号被分隔符吃掉，
  旧台账 JSON 畸形 → 「旧台账仍可信任」那条也一起红。改成完整对象 + `removeSuffix("}")` 造变体后重跑，
  `r6-entity-red2.log` 得到干净最小红：`8 tests completed, 1 failed`（只红在应该红的那条）。
  教训已用：红必须以「红得刚好」为准，不能只看有没有红。
- 台账类与 Router 实体类的新用例是在扩签名之后才获得编译可能，属 compile-driven，
  对应 `r6-compile1/2/3/4*.log`（含 JUnit5/JUnit4 断言参数顺序踩两次、类型推断需显式 `listOf<Pair<…>>` 三处）。

GREEN / 门禁（全部 `--offline --no-daemon`）：
- `gradle.bat --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
  → `RC=0`、`BUILD SUCCESSFUL`（`r6-gate2.log`；androidTest 编译在修掉
  `AppScreenTest.kt` 的 JUnit4 message-first 后由 FAILED 转绿）。
- 枚数现算：`python .git/qa-scratch/tally.py .git/qa-scratch/r6-tally.txt`
  → `classes=321 tests=2370 failures=0 errors=0 skipped=0`（上一批 `d3b0f60` 为 320/2357，本批 +1 类 +13 用例）。
  注意：门禁里 `:app:testDebugUnitTest` 对最终这次运行是 UP-TO-DATE，XML 全部为同日 14:41 那一次全量运行产出，
  此后只改过 androidTest 源，故计数有效。
- `gradle.bat --offline --no-daemon verifyAcTraceabilityMatrix` → `AC matrix covered 61 ids with evidence.`（`r6-acmat.log`）。
- `bash scripts/secret-scan.sh` → `RC=0`、零输出即干净（跟踪内容 + 已构建 APK；本批文档已先 `git add`）。
- 模拟器 `emulator-5554`（`adb install -r`，不清数据）`am instrument`
  `ui.AppScreenTest#learningCardPronunciationRoutesTheVisibleLemmaToTheProvider` +
  `language.FilePronunciationAudioCacheTest` → 末行 `OK (4 tests)`（`r6-entity-instr.txt`）。
  前者本轮新钉「两次发音都带 `WordCard` 类别且主键非空」。**真机 `bf353dda` 未碰**（禁 instrument）。
- detekt/ktlint：未跑。验收口径是单测全绿；存量基线债 1500+ 条，本批不计增量、也不当回归。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`；本批提交哈希提交后 `git log -1 --format=%h` 现读。
- 相对远端未提交批次数：`git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD` 现算（含本批）。
- **未推送、未合 main**——推送等用户发话。
- 截图/日志/临时 diff 全在 `.git/qa-scratch/`，不入库；工作区无未提交的遗留源文件。

## 5. 未完成 / 遗留问题

- **台账能回答「哪张卡/哪篇文章触发了这次合成」，不回答「哪些实体复用过多音频」**（决策 D-8/D-13 的取舍：
  记全部复用者=写放大）。要「复用者名单」是另一个决定，需重开决策文档。
- **端侧仍无「注入篡改后真机不播」的取证**：需要 run-as/root 往 `filesDir` 写坏 WAV，归设备走查攒批。
- **用户目视累计十一项**（任务 #14，需用户眼睛）：上一批四项 + 本批未新增目视项，
  但发音路径改动落在词卡/文章/拍照三屏，用户试用时顺带留意出声与状态文案有无退化。
- 结转清单照旧等点单：生图结果落盘、文章生成每日次数上限、QA 第三轮设备走查。
- `WordBookPackTest` 的操作者工具用例仍是 opt-in（`-e operatorTool true` 才跑），不要为凑绿去跑。
- 无真实出网验证：本轮全部证据为 JVM 临时目录 + 模拟器假件；探活/付费请求未被用作取证手段。

## 6. 下一任接手须知

- 第一步：读本文件 + `docs/decisions/2026-11-05-pronunciation-entity-ref-threading.md`（D-7~D-13 是本批全部口径）
  + 本阶段 spec `docs/specs/03-…md` F3-02。
- **坑 1**：新增 provider 若只覆盖四参 `speak`，五参默认实现会**静默丢掉实体**（决策 D-10）。
  调用点闸 `AudioCacheLedgerContractTest#everyAppScreenSpeakCallSiteNamesItsEntity` 只管 `ui/AppScreen.kt`，
  新屏幕自己加的调用点同样受它管；若把发音调用搬出 AppScreen，得同步扩闸的扫描路径。
- **坑 2**：`PronunciationAudioMetadata` 的新字段必须保持带默认值。把它们改成必填 = 判所有旧台账不可信
  = 缓存整体作废 = MiMo 路径按命中次数发付费请求。
- **坑 3**：实体绝不能进 `PronunciationCacheKey`。进了同一个词会按卡碎裂，复用与「同一输入命中同一缓存」一起崩，
  并重复烧推理/付费请求。已有用例钉住（`KokoroPronunciationProviderTest#the card that triggered synthesis is the one the cache is told about`）。
- **坑 4**：单测 JUnit5（`kotlin.test`，message 放**最后**）、androidTest JUnit4（`org.junit.Assert`，message 放**最前**），
  本轮在这个顺序上编译失败过两次。
- **坑 5**：改完 androidTest 源，`compileDebugAndroidTestKotlin` 绿 ≠ 测试 APK 新；上模拟器前需
  `:app:assembleDebugAndroidTest` 再 `adb install -r` 两个 APK。
- 门禁与设备纪律见 `AGENTS.md`、`android-repo-session-handoff` / `android-connected-qa-loop` 技能；
  Gradle 路径 `/c/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`。

## 7. 用户确认过的决策

- 「可以改造」——批准把关联实体从类别级升到主键级（本批唯一范围输入）。
- 沿用既有口径：本批不合 main、不推送；验收口径=单测全绿；真机只手工试用、instrument 只在模拟器；
  不授权为跑测试发付费 AI 请求；文档不写会随自身提交漂移的计数，改写为可复算命令。
- 以下为本批自主决定，供复核（未逐条问）：D-7 实体引用形状与 `Unattached`；D-8 实体不进键、台账只记触发生成的实体；
  D-9 无主键不伪造；D-10 默认实现丢实体 + 配调用点源闸；D-12 旧台账照读、schemaVersion 仍为 1；
  D-13 验收口径上限写进矩阵。若要「复用者名单」或把试听也并入更严格口径，需重开决策。
