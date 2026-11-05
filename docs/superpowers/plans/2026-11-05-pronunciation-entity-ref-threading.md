# 实现计划：发音缓存「关联实体」升到主键级（2026-11-05，分支 `stage-5-ui-r2-learning-path`）

依据：`docs/decisions/2026-11-05-pronunciation-entity-ref-threading.md`（D-7…D-13）
与 `AGENTS.md`「AI 音频缓存需记录…关联实体」、`docs/specs/03-…md` F3-02 / AC3-03。

## Seams（先写清在哪测，再写测试）

1. **`SynthesizedAudioCache.write/read` 与 `PronunciationAudioCache.put/get`**（JVM，注入 root `File`）——
   测「台账里能不能答出是哪张卡/哪篇文章触发的」「无主键时 kind 落 `Unattached`、id 为空」「同一文本换实体只有一条条目」。
   测不到：真机磁盘、Compose 层。
2. **`PronunciationRouter` → provider → 缓存 的转发链**（JVM 假件）——
   测「entity 从 `speakWord` 一路走到 `write`，中途不掉」。
   测不到：真 UI 调用点是否都传了（那是第 3 条闸的活）。
3. **`ui/AppScreen.kt` 源码本身**（source-as-contract）——测「每个发音调用点都显式给了 `entity =`」。
   测不到：传进去的 id 是不是对的那张卡（靠 Compose 测试成本过高，交给人工走查 + 上面第 2 条）。

## T1 RED：实体级台账（先红）

- `FileSynthesizedAudioCacheTest`：`write` 带 `WordCard("card-42")` ⇒ `.json` 里 `entityKind=WordCard`、`entityId=card-42`；
  同一文本用另一张卡再写 ⇒ 磁盘仍只有**一对**文件，台账仍是第一张卡（D-8 的"触发生成"口径钉死）。
- `FilePronunciationAudioCacheLedgerTest`：句子带 `Article("art-7")` ⇒ 台账可见；不带实体 ⇒ `entityKind=Unattached` 且 id 为空。
- `PronunciationAudioMetadataTest`：`entityKind` 属于枚举、非空白串；`entityId` 允许 null 但拒绝空白；
  字段面守卫（无凭据/端点）对新字段同样成立。
- `PronunciationRouterEntityTest`（新）：假 provider 记录收到的 entity；
  `speakWord(text, entity=WordCard(id))` 与 `speakSentence(text, entity=Article(id))` 都必须把同一个 ref 交到 provider，
  而**不感知实体的 provider**（只覆盖旧重载）必须仍能出声——默认丢弃不许抛。
  证据文件落 `.git/qa-scratch/`。

## T2 GREEN：域类型与接口带实体

- 新增 `language/domain/PronunciationEntityRef.kt`（枚举 6 值 + 可空 id + `wordCard()/article()` 便捷构造）。
- `PronunciationAudioMetadata`：加 `entityKind: String` / `entityId: String?`，`of()` 收 ref，
  `isTrustworthy()` 校验 kind 属于枚举、id 非空白。
- `PronunciationProvider`：`speakWord`/`speakSentence` 多 `entity: PronunciationEntityRef? = null`；
  新增 `speak(text, accent, forceRegenerate, rate, entity)` 默认委托四参版（D-10）。
- `PronunciationRouter`：全程转发（含 `routeSentence`/`speakMiMoOrSystem`）。
- `KokoroPronunciationProvider`/`MiMoPronunciationProvider`：覆盖新重载，把 ref 交给 `write`/`put`。
- `SynthesizedAudioCache.write`、`PronunciationAudioCache.put/putIfSuccessful`：带默认值的 entity 参数，
  `InMemory` 与既有假件零改动。

## T3 GREEN：调用点接线（16 + 1 处）

- 词卡类（今日计划/复习/收藏/学习记录/查词详情/文章点词）⇒ `WordCard(card.cardId)`；
  词卡屏 `onSpeak = { lemma -> }` 处从 `cardState` 取 `cardId`。
- 文章整篇/句 ⇒ `Article(articleState?.article?.articleId)`；
- 拍照翻译 ⇒ `PhotoTranslation(photoState?.lastSavedRecordId)`；长难句 ⇒ `SentenceAnalysis(null)`；
- `SpeechSettingsViewModel.kt:206` 试听 ⇒ 显式 `Preview(null)`。

## T4 契约闸

- `AudioCacheLedgerContractTest` 增一条判据：`ui/AppScreen.kt` 的 `speakWord(`/`speakSentence(` 调用点数
  与 `entity =` 实参个数一一对应；配 synthetic must-flag（造一个漏传 entity 的假屏源码必须被判红）
  与「解析为空即空转」守卫。既有三条判据（裸 WAV、来源漂移、`.sha256` 回归）不许松。

## T5 门禁、取证、文档、提交（不推送）

- 固定离线门禁：`gradle.bat --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
  （管道后带 `; exit ${PIPESTATUS[0]}`），日志与 `tally.py` 输出落 `.git/qa-scratch/`，枚数用现算值写文档。
- `verifyAcTraceabilityMatrix`、`scripts/secret-scan.sh`。
- 模拟器 `emulator-5554` 用 `am instrument` 跑改动的 androidTest 类；真机 `bf353dda` 不碰（禁 instrument）。
- 更新 `docs/verification/ac-traceability-matrix.md` AC3-03 与 `docs/verification/negative-test-audit.md` 第 6 行，
  把「关联实体只到类别级」的限制改成 D-8/D-12 的准确口径；`docs/specs/03-…md` F3-02 的指针行补指新决策。
- 上一批报告的结转条目收口。
- 按 `docs/handoff/REPORT-TEMPLATE.md` 写报告；带 git 身份提交；**不合 main、不推送**（等用户发话）。
