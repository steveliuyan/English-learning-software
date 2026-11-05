# 2026-11-05 AI 音频缓存完整性台账 工作报告

## 1. 本次目标

用户上一条是「可以按你推荐的走，继续下一步」——我把下一批点给了结转清单里的
「AI 音频缓存补『大小/来源/创建时间/关联实体』」，理由写在决策文档 §1：它是 Spec 与 AGENTS.md
**已经规定**的欠账，不需要先做产品取舍，也不需要他本人在设备上做。**这是我从结转清单里挑的方向，不是他点的名**，
想换成生图落盘 / 每日次数上限 / 设备走查任意一条，直接说，我改道。

动手后性质变了：这条欠账里混着一个**真缺陷**（端侧合成音频缓存替换一字节照样能播），
所以本批的主产出是修 fail-open，补字段是它的副产品。

## 2. 完成了什么

产品码（`app/src/main`）：

- 新增 `language/domain/PronunciationAudioMetadata.kt`：`@Serializable` 完整性台账，
  字段面 = Spec 03 F3-02 那五枚（内容摘要 / 大小 / 创建时间 / 来源 / 关联实体的内容类别）+ 口音 + 请求版本 + 文本；
  `fromJson` 对畸形、缺字段、未知 `schemaVersion`、空白来源、非 64 位十六进制摘要一律判「不可信」返回 null；
  `matches()` 要求大小与摘要**两枚**都对上才可播。同文件带 `SynthesizedAudioProvenance`（端侧来源标识）。
  类型里**没有** Endpoint/Key 字段——缓存层拿不到 `AiProfile`，写不出那两样东西。
- `language/infrastructure/FileSynthesizedAudioCache.kt`（端侧 Kokoro 词发音，`filesDir/kokoro-audio-cache`）：
  读侧先解台账、后判可播，任一不符即删 WAV + 台账并返回 null（仍是「绝不抛」）；
  写侧两份文件同批原子发布，发布失败两份都不留；预算逐出改为**成对**逐出并清孤儿台账。
- `language/infrastructure/FilePronunciationAudioCache.kt`（云端 MiMo，`filesDir/pronunciation-cache`）：
  裸 `.sha256` 由台账取代；新增 `internal constructor(root: File)`（Context 构造保留并委托），
  完整性逻辑从此可在 JVM 临时目录里测，不必只能上设备。
- `di/AppModule.kt`：Kokoro 缓存显式注入 `provenance =`（来源字面量集中在 `SynthesizedAudioProvenance.KokoroOnDevice`）。

测试（`app/src/test`，JVM）：

- `FileSynthesizedAudioCacheTest` 4→9 用例；新增 `FilePronunciationAudioCacheLedgerTest`（5）、
  `PronunciationAudioMetadataTest`（6）、`AudioCacheLedgerContractTest`（4，源码闸，双向 must-flag + 识别面精确性）。
- `app/src/androidTest/…/FilePronunciationAudioCacheTest.kt`：`modifiedChecksumIsRejectedAndRemoved`
  改名 `tamperedLedgerIsRejectedAndRemoved`（篡改对象换成台账），`tearDown` 连带清 `.json`/`.tmp`。

文档：

- 新建 `docs/decisions/2026-11-05-ai-audio-cache-integrity-ledger.md`（实况、三候选、D-1…D-6、影响）。
- 新建本计划/报告：`docs/superpowers/plans/2026-11-05-ai-audio-cache-integrity-ledger.md`、本报告。
- `docs/specs/03-…md` F3-02 那条需求**文字未改**，只加了指向决策的实现层级说明。
- `docs/verification/ac-traceability-matrix.md` AC3-03 行重写（含导出半边的诚实口径）；
  `docs/verification/negative-test-audit.md` §2 第 6 行重写，并**撤回**旧文里
  「`FileSynthesizedAudioCacheTest` 是尽力而为语义，不作为 fail-closed 证据」那句——它现在正是证据。

## 3. 验证记录

RED（改动前，同一批用例对着旧实现跑，`am` 无关，纯 JVM）：

```
FileSynthesizedAudioCacheTest          tests=9 failures=5
FilePronunciationAudioCacheLedgerTest  tests=5 failures=3
```

最关键那条原文（`.git/qa-scratch/r5-audio-cache-red-msgs.txt`）：

> `磁盘上的 WAV 被替换一个字节后仍被当成命中交给播放层——AGENTS.md 明令禁止 ==> expected: <null> but was: <[1, 2, 3, 9]>`

对照组也在同一次跑里绿着：云端缓存的「篡改→拒绝」在改动前就是绿的（它一直有摘要），
这把「两套缓存的差别」钉死在证据里，而不是靠我说。

GREEN 与门禁（固定离线三门禁）：

```
GRADLE=$(find "$HOME/.gradle/wrapper/dists/gradle-8.13-bin" -name gradle.bat)   # 本机解压版
"$GRADLE" --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
```

- rc=0，`BUILD SUCCESSFUL`；日志 `.git/qa-scratch/r5-audio-cache-gate2.log`。
- 读数**现算**（不引用历史基线；注意 `tally.py` 的第一个参数是**写出读数的目标文件**，不是日志）：
  `python .git/qa-scratch/tally.py .git/qa-scratch/r5-audio-cache-gate-tally.txt`
  → `classes=320 tests=2357 failures=0 errors=0 skipped=0`（本批 +3 类 +20 条，与上一批 `317/2337` 对得上）。
- connected（只上 emulator-5554，`am instrument`，未碰真机、未用 `connectedDebugAndroidTest`）：
  `adb -s emulator-5554 shell am instrument -w -e class com.example.englishlearning.language.FilePronunciationAudioCacheTest com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner`
  → 末行 `OK (3 tests)`（`.git/qa-scratch/r5-audio-cache-instrument.log`）。
- `verifyAcTraceabilityMatrix` 与 `scripts/secret-scan.sh` 结果见 §4（新文档先 `git add` 再扫，否则 `git grep` 扫不到）。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`；上一提交 `c81fe0f`（验证债收口），再往前 `ca3aeec`（AI 缺口批第 2 批）。
- 本批提交哈希：提交后 `git log -1 --format=%h` 现读（不写死，避免随自身漂移）。
- **未推送**：推送等用户发话；本批也不合 main。
- 截图/日志全在 `.git/qa-scratch/`，不入库。

## 5. 未完成 / 遗留问题

- ~~**关联实体只到内容类别级**（`Word`/`Sentence` + 文本）。主键级（wordBookId + wordId / articleId）
  要改 `PronunciationProvider` 接口与 `ui/AppScreen.kt` 十几处调用点，独立批次；决策 D-3 已写明，矩阵也没吹。~~
  **本项已于同日第二批收口**：16 处调用点全部显式带实体，词卡 `cardId` / 文章 `articleId` / 拍照记录 id
  落进台账，接口只扩了两个方法签名（其余 35 处 `override` 靠默认实现免改）。见
  `docs/decisions/2026-11-05-pronunciation-entity-ref-threading.md` 与本目录同日实体接线报告。
- **真机侧「替换 filesDir 里的 WAV 后不播」未验**：需要 run-as 或 root 注入，本批以 JVM 临时目录用例为主证据。
  归设备走查攒批。
- **备份语义零改动**（决策 D-4 选 sidecar 而非 Room 的代价就是它不进备份）：
  如果产品上想要「可选 AI 音频备份」，那是另一个决定，得重开决策文档。
- 结转清单其余各项照旧等用户点单：生图结果落盘、文章生成每日次数上限、QA 第三轮设备走查、
  用户目视累计七项 + 上一批四项（任务 #14）。
- 上一批遗留：`WordBookPackTest.kt:306` 的 `confirmedTextHosts` 只有编译级证据（跑它=几百条付费请求）。

## 6. 下一任接手须知

- 接手第一步仍按 `android-repo-session-handoff`：读 `docs/handoff/` 最新简报 + 本报告 + 本批决策文档。
- **`C:\Windows\adb.exe` 才是本机该用的 adb**（server 版本 26，已在 PID 33068 上跑着）；
  `C:\adb\platform-tools\adb.exe`（client 41）会先 kill 掉那个 server 再起不来，`adb devices` 直接空。
  本批我不小心用了一次带全路径的 41 版，教训记这儿：裸 `adb`（走 PATH）才对。
- `tally.py` 的 argv[1] 是**输出文件**，它自己 glob `app/build/test-results/testDebugUnitTest/*.xml`。
  本批我按「日志 输出」两参数传，把一份门禁日志覆盖成了一行——门禁日志请在 `.git/qa-scratch/` 里另存，
  并且**每次收工都重跑一遍门禁**，别让唯一一份日志被脚本吃掉。
- 两套 AI 音频缓存是**不同东西**：端侧合成（`SynthesizedAudioCache`/`FileSynthesizedAudioCache`，词）与
  云端发音（`PronunciationAudioCache`/`FilePronunciationAudioCache`，句）。
  `AudioCacheLedgerContractTest` 只扫 `language/infrastructure`，识别面靠「超类型位紧跟 `{`」的正则；
  新增缓存实现时先确认它会被扫到，否则那条闸会空转（它有响守卫，但别赌）。
- 台账 JSON 用 `Json { encodeDefaults = true }`：`schemaVersion`/`requestVersion` 有默认值，
  若有人改回默认实例，这两枚字段会从落盘结果里消失（`FilePronunciationAudioCacheLedgerTest` 会红，别看漏）。
- 环境老规矩：Gradle 本机解压版、`--offline --no-daemon`、门禁后 `; exit ${PIPESTATUS[0]}`、
  控制台中文 GBK 乱码（验证文本先写 UTF-8 再 Read）、`adb pull` 加 `MSYS_NO_PATHCONV=1`、
  验收口径是单测全绿（detekt/ktlint 存量债不算回归）。

## 7. 用户确认过的决策

本批**没有新的用户拍板**，沿用既有约束：

- 「可以按你推荐的走，继续下一步」——授权我选方向（我选了音频缓存台账，见 §1）。
- 本批不合 main、**不推送**（推送等他发话）。
- 不为跑测试发付费 AI 请求；探活/真实请求不作为取证手段。
- 真机只手工试用；instrument 只在 emulator-5554。
- 收工按本模板写报告、带 git 身份提交、文档不写会随自身提交漂移的计数。
