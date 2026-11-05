# 决策：AI 音频缓存的完整性台账（大小/来源/创建时间落盘 + 播放前置校验）（2026-11-05）

## 1. 背景

`docs/handoff/2026-11-05-ai-outbound-granularity-and-network-baseline-report.md` 的结转清单里挂着一条
「AI 音频缓存补『大小/来源/创建时间/关联实体』」。用户在 2026-11-05 把下一步的选择权交给我
（「可以按你推荐的走，继续下一步」），我挑了这条——理由是它是**已被 Spec 与 AGENTS.md 规定的欠账**，
不像生图落盘、每日次数上限那样需要先做产品取舍，也不像设备走查那样必须先由他本人在真机上做。

动手前把现树的两条 AI 音频缓存实现读完了，结论是：**这条欠账里混着一个真缺陷**，
不是单纯的字段没写。

## 2. 实况（读码 + grep 实测，非推断）

仓库里有**两套**AI 音频缓存，都在生产装配上（`di/AppModule.kt` 的 `@Provides @Singleton`）：

| 缓存 | 目录 | 谁在用 | 内容完整性 | 元数据 |
|---|---|---|---|---|
| `FileSynthesizedAudioCache` | `filesDir/kokoro-audio-cache` | 端侧 Kokoro **单词**发音（词卡路径的默认出声，`PronunciationRouter.kt:54`） | **无**。`read()` 只判 `isFile && length()!=0` 就把字节交给 `player.play` | 目录里只有 `.wav`，没有任何 sidecar |
| `FilePronunciationAudioCache` | `filesDir/pronunciation-cache` | MiMo **句子**朗读（`MiMoPronunciationProvider.kt:71/102`） | **有**：`.sha256` sidecar，摘要不符即删两份文件并当未命中 | 只有摘要一枚，其余字段全缺 |

两处都要补，但性质不同：

- **缺陷（前者）**：文件名是 `SHA-256(accent + text)`——那是**键**的摘要，不是**内容**的摘要。
  于是「缓存文件被替换/截断后不得播放」这条硬约束在词卡路径上完全落空：磁盘上任何同名 WAV 都会被当成缓存命中直接播。
  违反 `AGENTS.md`「损坏或替换后不得播放或导出」与 `docs/specs/03-…md:30`「哈希不一致时拒绝播放/导出」。
- **欠账（后者）**：`.wav` + `.sha256` 之外什么都没有。来源信息（providerId/model/voice/accent/requestVersion）
  只活在内存里的 `PronunciationCacheKey` 对象上，而 `cacheId()` 是单向哈希——落盘之后，
  「这段音频是哪个服务、哪个模型、哪个音色、哪天生成的」在设备上**不可回答**。
  这既是 Spec 03 要求的字段缺口，也让"重新生成"和排障没有依据。

两者都没有「关联实体」。另外「导出」这一半目前**根本没有入口**：发音音频不在备份白名单里
（`BackupExportCoordinator` 只搬 `asset_records` 里的行，拍照翻译原图才登记进去），所以它今天不可能被导出。

## 3. 规范依据

- `AGENTS.md` 发音、OCR 与资源：「AI 音频缓存需记录内容哈希、来源、模型版本和关联实体；损坏或替换后不得播放或导出。」
- `docs/specs/03-dictionary-pronunciation-and-photo-translation.md:30`：「AI 音频缓存键为规范化文本、语音参数与模型版本；
  保存 SHA-256、大小、来源、创建时间与关联实体。哈希不一致时拒绝播放/导出并允许重新生成。」
- `docs/specs/03-…md:50`（AC3-03）：「篡改缓存文件后播放与导出均失败。」
- `AGENTS.md` 备份与恢复：备份「可包含…可选 AI 音频」——是**可**，不是**必须**。

⇒ 本批不是新增需求，是**把已规定的字段补齐 + 修掉前者那个 fail-open**。

## 4. 候选方案

**A. sidecar 元数据 + 读取前置校验（本批采用）**
每条缓存旁放一个 `PronunciationAudioMetadata`（JSON，`.json` 同名 sidecar），
`read()/get()` 必须先解析它、比对大小与内容摘要，才允许把字节交给播放层；任一不符即删两份文件、按未命中处理，
上层照旧重新合成/重新出网。Room 与备份语义零改动。

**B. 迁进 Room `asset_records` 或新表**
好处是台账可查询、天然进备份；代价是数据库版本迁移 + DAO + 级联删除口径，
而且一旦登记进 `asset_records`，`BackupExportCoordinator.collectEntries` 就会真的把这些 WAV 打进备份包
（AGENTS.md 是「可选」，但今天用户没有开关），包体与隐私面同时变化。为了 5 个字段引入一次 schema 迁移并顺带改变备份内容，
收益不抵风险。**否决**。

**C. 只补 `.sha256`，不做 sidecar**
把前者从"无校验"升到"有校验"是够的，但「来源/模型版本/创建时间」仍然无处可写，Spec 的五个字段只满足两个。
**否决**。

选 A 的核心理由：**这套东西是可再生的合成产物，不是用户内容**——
`FileSynthesizedAudioCache` 现有 KDoc 与 `docs/handoff/2026-10-13-stage-4-device-qa-fixes-report.md` §58 都写明
「不进备份、不参与完整性台账」。本批保持这个定性（方案 B 会把它变成用户内容），只把"完整性台账"从
**备份台账**收窄为**缓存自身的 sidecar**。

## 5. 决定

### D-1 sidecar 字段（五枚，逐一对应 Spec 03:30）

```
schemaVersion / sha256(内容摘要) / byteSize / createdAtEpochMillis
source: providerId + model + voice + accent + requestVersion
entity: contentKind(Word|Sentence) + text
```

- 沿用仓库既有的 `kotlinx.serialization` 口径（`BackupSnapshot`、`WordCard` 同为 `@Serializable`）。
- **绝不写入** Endpoint URL、模型文件路径以外的任何机密，API Key 更是结构上拿不到（缓存层没有 `AiProfile`）。
  `source.providerId` 存的是 `AiProviderKind` 枚举名（如 `XIAOMI_MIMO`）或 `KOKORO_ON_DEVICE`，
  与 `PronunciationCacheKey.cacheId()` 已用的输入同源，不引入新的敏感面。
- `entity.text` 就是朗读文本（词或句）。它是**用户正在学的英文内容**，只落在应用私有目录，
  不进 Room 业务表、不进日志、不进导出包。

### D-2 播放 fail-closed 与「尽力而为」如何共存

`FileSynthesizedAudioCache` 的契约注释写着「读失败 = 未命中（绝不抛）；写失败 = 静默（绝不打断播放）」——
本批**保留**这两条（不让一次 IO 故障变成用户面前的红框），但把"未命中"的含义收紧：

- 摘要缺失/不符、`byteSize` 与实际不符、sidecar 缺失或畸形 ⇒ **一律返回 null**，并顺手删除该条的两份文件，
  让下一次请求走重新合成。**这是拒绝播放，不是失败播放**；调用方看到 null 就照常合成，用户只是多等一次推理。
- 「写失败静默」继续成立：写 sidecar 失败 ⇒ 这次没有缓存，但本次播放不受影响。
  反过来**不允许**出现"只有 WAV、没有 sidecar"的状态——所以两份文件必须同批原子发布，
  且任一份发布失败都要把另一份删掉。

### D-3 「关联实体」本轮只做到内容类别级

`PronunciationProvider.speak*/speakWord/speakSentence` 全链只传文本，没有 wordBookId/wordId/articleId
（实测调用点：`ui/AppScreen.kt` 词卡处 8 处、句处 3 处，以及 `SpeechSettingsViewModel.kt:206`；
它们手上**有** card 对象，但接口不收）。本批记 `contentKind` + `text`：

- 端侧 Kokoro 缓存 ⇒ `Word`（它只被 `PronunciationRouter.speakWord` 使用）；
- MiMo 缓存 ⇒ `Sentence`（Router 只在 `routeSentence` 里用它）。

把主键接进去要改 Provider 接口与 `AppScreen.kt` 十几处调用点，是一批独立的接线活，
**结转**并在 `docs/handoff/2026-11-05-ai-audio-cache-ledger-report.md` §待办写明。
本批不声称 AC3-03 的「关联实体」已满足到主键级。

### D-4 逐出与残留清理必须连带 sidecar

`FileSynthesizedAudioCache.enforceBudget` 今天按 `.wav` 计数并留 `.tmp` 清理；加 sidecar 后必须
（一）把 `.json` 一起逐出，（二）删掉没有同名 WAV 的孤儿 sidecar，否则预算账本与实际字节脱钩。
预算统计口径改为**只按可播放的完整条目**（WAV + 有效 sidecar）计字节，孤儿不计。

### D-5 端侧来源常量的防漂移闸

Kokoro 的 `providerId/model/voice` 由 `di/AppModule.kt` 注入，而这三枚值的事实来源是
`language/infrastructure/KokoroAndroidEngine.kt` 里的私有常量（`model-files-v1.0/…`、`af_heart.npy`、`bf_emma.npy`）。
本批不去改那个 ONNX 文件（避免碰推理路径），改为加一条 source-as-contract 判据：
注入的 `model` 与 `voice` 字面量必须在该引擎源码里逐字出现，且配手写 synthetic must-flag——
将来升级模型时若只改引擎不改台账，闸会红。

### D-6 「拒绝导出」这一半的诚实口径

发音音频不在备份白名单内，所以它今天**不可能**被导出，AC3-03 的导出半边由"没有通道"保证，
而不是由本批的校验器保证。矩阵与报告里都按这个口径写，不许把 `FileOpsBackupMediaGateway` 的既有
`asset_records` 校验算作发音缓存的证据（那是拍照翻译原图的通道）。

## 6. 影响

- `language/infrastructure/`：两个缓存实现都要改；新增 `@Serializable` 元数据类型与编解码/校验器。
- `language/domain/PronunciationAudioCache.kt`：`FilePronunciationAudioCache` 的 `.sha256` 被 sidecar 取代，
  `app/src/androidTest/…/FilePronunciationAudioCacheTest.kt` 里那两条「篡改 sidecar/篡改音频」用例的篡改对象要跟着换，
  并给它加一个注入 root `File` 的构造，好让完整性逻辑在 JVM 侧可测（不再只能上设备）。
- `di/AppModule.kt`：Kokoro 缓存多三个来源常量；两处装配仍 `@Singleton`。
- 磁盘：每条缓存多一个约 200–300 B 的 JSON；预算与逐出按 D-4 重算。
- 不影响备份包结构、不影响数据库版本、不改任何用户可见文案。
- 真机验证边界：词卡路径的合成音频要在真机上才能验「篡改后不播」，但篡改设备私有目录需要 run-as/root；
  本批以 JVM 临时目录用例为主证据，真机侧只做正常播放回归，并在报告里写清这条限制。
