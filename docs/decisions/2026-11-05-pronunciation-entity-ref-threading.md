# 决策：把发音缓存的「关联实体」从内容类别级升到主键级（2026-11-05）

## 1. 背景

上一批（`docs/decisions/2026-11-05-ai-audio-cache-integrity-ledger.md`）给两套 AI 音频缓存各配了完整性台账，
但它的 D-3 明确留了一条欠账：台账里的「关联实体」只到 `contentKind`（Word/Sentence）+ 朗读文本，
**没有主键**，因为发音接口全链只传文本。报告里我写了「接主键要改 Provider 签名与调用点，是独立批次」，
用户在 2026-11-05 回「可以改造」——本批就是这条接线的活。

## 2. 实况（本批开工时读码 + grep 实测，非推断）

**主键其实一直在调用点手边，只是接口不收。**

| 事实 | 证据位置 |
|---|---|
| `PronunciationProvider` 的入口只有文本：`speakWord(text, accent, forceRegenerate, rate)`、`speakSentence(text, accent, forceRegenerate)` 与四个 `speak` 重载 | `language/domain/PronunciationProvider.kt:16-49` |
| `WordCard` 自带主键 `cardId: String` | `learning/domain/WordCard.kt:50` |
| `Article` 自带主键 `articleId: String`；`ArticleReadingUiState.article` 在屏层可取 | `reading/domain/Article.kt:4`、`ui/ArticleReadingViewModel.kt:23` |
| 拍照翻译有 `lastSavedRecordId: String?`（保存前为 null） | `ui/PhotoTranslationViewModel.kt:62` |
| 全树 `override suspend fun speak*` 共 **37** 处，分布在 11 个文件（含测试假件） | `grep -rn "override suspend fun speak" app/src \| wc -l` |
| `ui/AppScreen.kt` 里 `pronunciationProvider?.speakWord/speakSentence` 调用点 **16** 处 | `grep -c` 同文件 |
| 真正写台账的只有两处：`KokoroPronunciationProvider.kt:87` 的 `audioCache?.write(text, accent, audio)` 与 `MiMoPronunciationProvider.kt:102` 的 `cache?.put(cacheKey, wav)` | 源码 |
| 另有 `ui/SpeechSettingsViewModel.kt:206` 走兼容入口 `pronunciation.speak(text)`（语音设置试听），它手上没有任何实体 | 源码 |

结论：要记主键，必须让**调用点已知的 id**穿过 Router 到达那两个 `write/put`。

## 3. 规范依据

- `AGENTS.md`：「AI 音频缓存需记录内容哈希、来源、模型版本和**关联实体**」。
- `docs/specs/03-dictionary-pronunciation-and-photo-translation.md` F3-02：「保存 SHA-256、大小、来源、创建时间与**关联实体**」。

本批不新增需求，只把上一批自己承认「没做到」的那半句做到。

## 4. 候选方案

**A. 给全部 `speak` 重载加实体参数。** 37 处 override 全改，而其中系统 TTS、OpenAI 兼容发音、
以及测试假件根本没有缓存可记（系统 TTS 按 AGENTS.md 不生成可导出音频）。**否决**：改动面是收益的十倍。

**B. 只在语义入口带实体，配一个"不感知就丢弃"的默认实现（本批采用）。**
`speakWord`/`speakSentence` 各多一个 `entity: PronunciationEntityRef? = null`；
新增一个带 `entity` 的 `speak` 重载，**接口默认实现丢掉 entity 委托旧重载**——
于是只有真正会写台账的 Kokoro 与 MiMo 需要覆盖它，其余 35 处 override 一行不动。
Router 作为 UI 唯一持有的实现负责转发。改动面：接口 + Router + 2 个 provider + 2 个缓存接口 + 元数据 + 16 处调用点。

**C. 建 Room 关联表（cacheId → 实体）。** 能做"一条音频关联多个实体"的完整账，
但代价是数据库版本迁移 + DAO + 备份白名单口径（AGENTS.md 备份清单要不要多一行），
与上一批否决方案 B 同类的风险。**本批否决**，并把它列为「将来真要按实体清理音频时」的选项。

**D. 让 UI 层直接写台账。** 屏层不知道 `cacheId()` 的输入（providerId/model/voice 在 provider 内部才齐），
把缓存键规则复制到第二处只会漂移。**否决**。

## 5. 决定

### D-7 实体引用的形状

```
PronunciationEntityRef(kind: PronunciationEntityKind, id: String?)
kind ∈ { WordCard, Article, PhotoTranslation, SentenceAnalysis, Preview, Unattached }
```

台账落两枚字段：`entityKind`（枚举名，**恒非空**——调用方传 null 时缓存层记 `Unattached`）与
`entityId`（**可空**）。
`WordCard`→`cardId`；`Article`→`articleId`；`PhotoTranslation`→`lastSavedRecordId`（未保存即 null）；
`SentenceAnalysis`（长难句）没有主键，恒 null；`Preview`（语音设置试听）本批显式改成传 `Preview` 而不是留 null——
留 null 会让它与"某个忘了接线的调用点"在台账里长成同一个样子。
kind 记的是**这条音频从哪个屏出来**，id 记**哪一条**——两者分开，缺 id 不影响 kind 的取证价值。

### D-8 实体不进缓存键；台账记「触发生成的实体」

`PronunciationCacheKey` 与 `FileSynthesizedAudioCache` 的文件名保持现状（规范化文本 + 口音 + provider + 模型 + 音色 + 版本）。
**加入实体 id 会让同一个词的音频按卡号裂成多份**，直接推翻上一批钉死的「同一输入命中同一缓存」判据，也白费推理与出网。
因此缓存是内容级共享的，台账记的是**第一次触发生成那次调用的实体**；后来别的卡复用同一段字节不改写台账。
取舍理由：改台账=改磁盘写（每次命中都写），为的是一条"谁听过"的名单——写放大换取证价值不划算，
且"由谁生成"对排障（哪次请求、哪个模型版本）已经够用。这条口径必须在矩阵与报告里写明白，不许含糊成"完整关联"。

### D-9 无主键就填 null，禁止伪造

拿不到 `cardId` 不许退化成用 `lemma` 或文本当 id（那是把键伪装成关联，`contentKind`+`text` 已经记了同一件事）。
`isTrustworthy()` 要求 `entityKind` 非空且属于已知枚举，`entityId` 允许 null 但不允许空白串。

### D-10 默认实现会丢弃 entity，所以配一道调用点闸

接口默认 `speak(…, entity) = speak(…)` 意味着"provider 没覆盖 ⇒ 实体静默丢失"。
为防这条链将来被悄悄接错，加 source-as-contract 判据：`ui/AppScreen.kt` 里每个 `speakWord(`/`speakSentence(` 调用点
必须显式出现 `entity =` 实参（数量比对，配 synthetic must-flag 与"解析为空即闸空转"守卫）。
覆盖写进契约测试而不是靠人记住。`ui/SpeechSettingsViewModel.kt:206` 的试听不在那 16 处里（它走 `speak(text)`），
本批改为显式调用 `speakSentence(text, entity = Preview)`，让它进闸的覆盖面而不是继续当漏网的兼容入口。

### D-11 隐私与落盘面不变

`entityId` 是本地生成的 UUID 主键，与台账其余字段同批写入应用私有目录的 `.json` sidecar：
不进 Room 业务表、不进日志、不进导出包、不参与备份白名单——与上一批 D-1/D-6 完全同口径，
本批只是那枚 `entity` 字段从"类别"升到"类别 + 主键"。

### D-12 旧台账照读，不因缺实体字段而作废

`entityKind` 取值默认 `"Unattached"`、`entityId` 默认 null——**读侧缺这两枚不判不可信**。
理由：上一批刚上线的台账就是这个形状，它们的内容摘要与大小仍然有效、字节可播；
把"缺实体"当成"不可信"等于把整座缓存判死，代价是**每次命中都变成一次重新合成或重新出网**
（MiMo 那条路径是真花钱的付费请求），而换来的只是台账更完整——完整性没提升，成本却是用户的钱。
所以：**写侧一律写全，读侧向后兼容**，`schemaVersion` 保持 1。

### D-13 验收口径

AC3-03 的「关联实体」本批升到**主键级**：给定 `filesDir/kokoro-audio-cache/<id>.json`，
能回答"这段音频由哪张词卡/哪篇文章的哪次发声触发"；仍**不**声称能回答"这条音频被哪些实体复用听过"（D-8），
也仍**没有**真机侧的篡改注入验证（私有目录需 run-as/root，证据仍是 JVM + 模拟器）。

## 6. 影响

- `language/domain/PronunciationProvider.kt`：`speakWord`/`speakSentence` 多一个可空参数，新增带 entity 的 `speak` 默认重载。
- `language/domain/PronunciationRouter.kt`：speakWord/speakSentence/routeSentence/speakMiMoOrSystem 全程转发 entity。
- `language/infrastructure/KokoroPronunciationProvider.kt`、`MiMoPronunciationProvider.kt`：覆盖新重载，把 entity 交给缓存。
- `language/infrastructure/FileSynthesizedAudioCache.kt` 的 `SynthesizedAudioCache.write`、
  `language/domain/PronunciationAudioCache.kt` 的 `put`/`putIfSuccessful`：多一个带默认值的 entity 参数（假件与 `InMemory` 实现零改动）。
- `language/domain/PronunciationAudioMetadata.kt`：`entityKind`/`entityId` 两枚字段 + `of()`/`isTrustworthy()` 相应收紧。
- `ui/AppScreen.kt`：16 处调用点接线（词卡类用 `cardId`，文章句用 `articleId`，拍照用 `lastSavedRecordId`，长难句给 kind 不给 id）。
- 磁盘：每条台账 JSON 多约 40–60 B；缓存键、文件名、逐出预算口径均不变。
- 不改数据库版本、不改备份包结构、不改任何用户可见文案。
