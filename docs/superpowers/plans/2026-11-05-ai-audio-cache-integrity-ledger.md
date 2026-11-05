# 实现计划：AI 音频缓存完整性台账（2026-11-05）

依据：`docs/decisions/2026-11-05-ai-audio-cache-integrity-ledger.md`（D-1…D-6）
与 `docs/specs/03-dictionary-pronunciation-and-photo-translation.md` F3-02 第 30 行、AC3-03。
分支 `stage-5-ui-r2-learning-path`；本批不合 main、不推送（推送等用户发话）。

## 范围

补 `AGENTS.md`/Spec 03 已规定的五个字段（SHA-256、大小、来源、创建时间、关联实体），
并修掉端侧 Kokoro 缓存的 fail-open（无内容摘要即可播放）。
**不做**：Room 迁移、备份白名单变更、`PronunciationProvider` 接口加实体主键、用户可见文案。

## 任务

### T1 元数据类型与编解码（GREEN 的第一块，先写红）

新增 `language/domain/PronunciationAudioMetadata.kt`：`@Serializable` 类，字段按 D-1；
`toJson()` / `fromJson(String)`（畸形、缺字段、`schemaVersion` 不符 ⇒ `null` 或 `failure`，绝不抛给调用方）；
`matches(bytes)`：同时校验 `byteSize` 与内容 SHA-256。

红测（JVM）：
- 往返一致；
- 五个 Spec 字段在序列化结果里逐枚可见（不靠反射，按 JSON 键名断言）；
- 截断/非 JSON/未知 `schemaVersion` ⇒ 判定为无效；
- 大小与摘要任一不符 ⇒ `matches` 为假；
- 序列化结果不含 Key/Endpoint 形状的字串（结构性守卫：类型压根没有那两枚字段）。

### T2 端侧 Kokoro 缓存 fail-closed

`FileSynthesizedAudioCache`：`write` 原子发布 `.wav` + `.json`（任一步失败 ⇒ 两份都不留）；
`read` 必须先拿到并校验 sidecar 才交字节，不符即删两份并返回 null；
`enforceBudget` 按 D-4 改成"完整条目"计数、连带逐出 `.json`、清理孤儿 sidecar。

红测（JVM，`@TempDir`）：
- 篡改 WAV 一个字节 ⇒ `read` 返回 null 且两份文件均被删除（**这条就是今天会红的真缺陷**）；
- 截断 ⇒ 同上；sidecar 缺失 ⇒ 同上；sidecar 畸形 ⇒ 同上；
- 正常写入后 `read` 仍返回逐字节相同内容（保住既有"命中播放"语义）；
- 预算逐出后不留孤儿 sidecar，且被逐条目 `read` 为 null；
- 「读失败绝不抛」「写失败不打断播放」两条既有语义保持（原 4 条用例不许改弱）。

### T3 云端 MiMo 缓存补台账

`FilePronunciationAudioCache`：`.sha256` 由 sidecar 取代（内容摘要进 sidecar）；
新增可注入 root `File` 的构造，Context 构造保留为委托；`contentKind = Sentence`。
`app/src/androidTest/…/FilePronunciationAudioCacheTest.kt` 的两条篡改用例改按 sidecar 对象下手，
`tearDown` 清理新增文件；完整性主证据移到 T2/T3 的 JVM 用例。

红测：与 T2 同形（在 JVM 侧用 `PronunciationCacheKey` 构造），并钉住
"缓存键的 providerId/model/voice/accent/requestVersion 全部落进 sidecar"。

### T4 装配与防漂移闸

`di/AppModule.kt`：给 Kokoro 缓存注入 `providerId = KOKORO_ON_DEVICE`、`model`、`voiceByAccent`；
`MiMoPronunciationProvider` 不变（来源已在 key 内）。
新增 source-as-contract 判据（D-5 + 禁裸 WAV 缓存）：
- 注入的 `model`/`voice` 字面量必须逐字出现在 `KokoroAndroidEngine.kt`；
- 任何 `*AudioCache` 生产实现必须引用共享校验器类型；
- 双向 synthetic must-flag + 「解析不到即报闸空转」的响守卫（仓库口径）。

### T5 门禁、取证与收口

1. 固定离线三门禁：`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
   （`--offline --no-daemon`，管道后 `; exit ${PIPESTATUS[0]}`），读数用
   `python .git/qa-scratch/tally.py <log> <out>` 现算，不引用历史基线。
2. `verifyAcTraceabilityMatrix` 与 `scripts/secret-scan.sh`（新文档先 `git add` 再扫，否则 `git grep` 扫不到）。
3. emulator-5554 用 `am instrument` 只跑受影响的 `FilePronunciationAudioCacheTest`（禁用 `connectedDebugAndroidTest`）。
4. `docs/verification/ac-traceability-matrix.md` AC3-03 行补证：按 D-6 口径写，
   不把 `asset_records` 通道算作发音缓存的证据；「关联实体」标注为内容类别级、主键结转。
5. `docs/handoff/2026-11-05-ai-audio-cache-ledger-report.md`（REPORT-TEMPLATE 七节）+ 结转待办更新。
6. 本地提交（`git -c user.name=steveliuyan -c user.email=2021250388@qq.com`），**不推送**。

## 验收

- T2 第一条用例在改动前必须红（证伪"今天篡改可播"），改动后绿；
- 全部门禁 rc=0、单测全绿（验收口径是单测，不是 detekt/ktlint 存量债）；
- 报告写明：备份结构/数据库版本零改动、真机侧「篡改后不播」未做设备验证的原因。
