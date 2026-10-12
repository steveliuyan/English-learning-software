# Kokoro 本地单词发音候选决策

## 背景

MiMo 等生成式 TTS 适合文章和句子朗读，但单独朗读词典单词时可能出现重音、音素或重复生成结果不稳定的问题。词典单词发音属于学习基础能力，应优先保证可重复、离线和不依赖 API Key。

## 候选方案

1. 继续使用 MiMo 朗读单词：自然度较好，但生成式模型可能自行判断发音，无法保证每次一致。
2. 使用系统 Android TTS：无需额外模型，包体积小，但不同设备的引擎、语言包和音质不一致。
3. 引入 Kokoro 本地模型：固定音色、支持美式和英式英语、可离线运行；但需要验证 Android ONNX 推理、模型体积、内存、耗时和模型/音色许可。

## 当前决定

将 Kokoro 作为“本地单词发音”的候选 Provider，先执行独立 Spike，不立即替换现有生产发音链路。

Spike 通过前必须满足：

- Android 真机能够完成美式和英式单词合成并播放；
- 首次加载、单词生成耗时和内存占用在可接受范围内；
- 模型、音色、推理运行时和附带 phonemizer 依赖的许可证与来源可以登记；
- 模型文件可通过固定版本和 SHA-256 校验，且不从未经核验的第三方站点下载；
- 失败时仍可回退到系统 TTS，不影响学习流程；
- 单词默认不再回退到 MiMo，MiMo 保留给文章和句子朗读。

## 影响

- 词典单词发音与文章朗读需要使用不同的能力路由；不能用同一个远程 Provider 覆盖两类场景。
- 正式接入前需要补充第三方依赖、模型和音色的许可证台账与 NOTICE。
- Kokoro 模型未完成真机验证前，不增加正式 APK 的模型资产，不改变当前默认 Provider。
- 若 Kokoro 不满足包体积或性能要求，保留系统 TTS 作为单词发音方案，取消 Kokoro 正式接入。

## 2026-10-06 Spike 结论

本次 Spike 结论为 `NO_GO_KEEP_SYSTEM_TTS`。社区 release 的 INT8 ONNX 与 voice pack 已在工作树外部完成独立文件大小和 SHA-256 核验，ONNX Runtime Android 1.29.0 AAR 也已核验 MIT 许可、Maven SHA-1 和本地 SHA-256；但这些文件仍是社区转换/打包产物，且官方 Misaki 0.9.4 仍是 Python G2P。后续通过固定 revision 的 `hf-mirror.com` 路径取得了 `Shusek00/kokoro-kmp-models` 的 Phonemis v2.1.0 实体证据：`SOURCES.lock.json`、`sources/phonemis-onnx-v2.1.0.json`、MIT LICENSE、en-us/en-gb frontend 均有独立大小与 SHA-256；两种 frontend 的 ONNX 图签名为 `input_ids int64[1,sequence_length] -> logits float32[1,output_length,116]`。

2026-10-07 的隔离桌面 CPU 预验证实际跑通了固定英语 lexicon -> v1.0 vocab -> 社区 NPZ voice -> INT8 Kokoro -> WAV 的局部适配路径：ORT 1.29.0 CPU session 加载 1,374.191 ms，12 个 WAV 均为非空、非静音、24 kHz 单声道 PCM16；同一 session 内 6 组重复摘要一致，schedule/record/apple 的英美摘要均不同。13 个 unittest 在独立变异后恢复为 13/13 GREEN，12 个生成文件经独立磁盘回读和 SHA-256/PCM 校验通过。该结果只证明词典命中桌面路径，不证明完整 Phonemis frontend、词性 tagger、未知词回退、Android/JVM 推理、真实播放或设备性能。

图名称/opset 差异表示需要适配，不足以单独判定不兼容。Task 2.6 已桌面验证 Phonemis 单词输入、116 类 CTC 解码、词典优先、显式词性和未知词回退，并补齐 `--mode frontend` 可复现入口与完整标签回归 fixture；默认 mode 保持 lexicon。最新 `cli-closeout-green.log` 为 45/45 GREEN（86.726 s），`cli-mutation-verification.json` 为 13 项独立内存变异均断言失败、errors=0，源码前后 SHA-256 均 `b92f7e4052e2c579832cd341d43b4d5822530d8d1748970ac3b1a5079a029c87`。新 CLI 输出 `cli-20261007` 的 32/32 WAV 回读通过、16 组重复摘要一致，与上一轮对应 32 文件摘要全部相同；显式 VB 不是自动 tagger。已补强有限性/batch/类别数测试，并以 RED→GREEN 修复量化后全静音 WAV 边界。

新 CLI 的历史真实运行记录：frontend 2.45–33.627 ms、Kokoro 合成 2.966–19.718 秒；session 加载 US 486.947 / GB 227.881 / Kokoro 7,442.406 ms。此次续接重新运行 unittest 和磁盘回读，没有重新推理，不混合历史批次统计，也不外推 Android 指标。自动 tagger、完整文本处理、Android/JVM、真实播放、设备性能/内存/APK 增量、人工听感和分发 NOTICE 仍未完成，因此保持 `NO_GO_KEEP_SYSTEM_TTS`，未将依赖或模型资产加入工程，未修改正式路由。正式接入需另立审批批次。

### 2026-10-08 Android 隔离验收补充

Android ORT 1.29.0 仅加入 `androidTestImplementation`，共享 Kotlin Spike 只映射到 JVM test 与 androidTest。Instrumentation adapter 校验 Phonemis/Kokoro 图契约，显式释放 ORT sessions/tensors/results，并复制输出至 Kotlin 数组。Android instrumentation 验收使用 `apple`、`schedule`、`record` 两种口音和两次重复生成；真实设备单类测试 `OK (1 test)`，耗时 23.761 秒。模型准备 1,216 ms；单词合成 999–1,854 ms，播放器调用 987–1,622 ms。以上为单设备、单次测量，不代表统计分位或人工音质验收。

debug APK 为 22,116,886 bytes / 212 ZIP entries，未含 ORT/Spike classes、native libraries 或 Kokoro 资产；本轮 androidTest APK 为 134,183,013 bytes / 105 entries，ORT 类和 native 库仅在测试 APK。2026-10-08 新增 100 ms 周期内存采样，256 个样本的观测峰值为 total PSS 312,133 KB、native PSS 191,801 KB、native heap 317,803,328 bytes、Java heap 9,904,280 bytes；均为单设备单次周期观测，不能排除间隔内尖峰或替代分位统计。新 Room 基线与正式测试后主库、SHM、WAL 均逐字节相同，证据见 `outputs/kokoro-spike-20261006/device-memory-peak-before/`、`memory-peak-device-logcat.txt`。六段当前默认读音 WAV（apple/schedule/record × US/GB）已导出至 `outputs/kokoro-spike-20261006/listening-samples/` 并校验 RIFF/WAVE、24 kHz mono PCM16 与 SHA-256；设备外部目录 `/sdcard/Android/data/com.example.englishlearning/files/kokoro-spike-listen/` 保留同一组文件。人工听感结论仍待用户评审，record 样本为名词。2026-10-08 后续在真机对未知词 `workbuddy` 的美式/英式神经前端回退均完成合成、24 kHz mono PCM16 WAV 校验与实际播放；Room 主库/SHM/WAL 前后逐字节一致。该单词听感尚未人工评审，且单词覆盖不能证明一般 G2P 准确度；显式动词 Android 验收、更广输入集和分发审核仍未完成。故只完成隔离 Spike 验收，仍为 `NO_GO_KEEP_SYSTEM_TTS`；生产路由与默认 Provider 不变。

### 2026-10-08 显式 VB APK 门禁失败：不进入设备阶段

这是阻塞记录而非新的真机验收。显式 `record` VB 的 Task4 fixture/parser/exporter/真实tokens与frontend断言/清零接线已独立审查；record异常资源释放缺陷经test-only修复、RED/GREEN、两项独立变异、SHA还原与复审通过。最终focused JVM共63项零失败/错误/跳过，instrumentation Kotlin编译和同批debug/androidTest构建成功；编译/构建不等于设备验收。

实际APK审计 `verification-logs/kokoro-record-verb-20261008/apk-boundary.json` 为 `boundary_pass=false`、installation authorization false：当前22,116,886B production APK的 `classes8.dex` 真实class_defs包含既有 `language/infrastructure/KokoroEngine`、`KokoroPronunciationProvider` 和其 `speak$2`。本轮317个main文件与续接基线逐字节相同，不代表已有生产Kokoro类不存在。此处只纠正既有记录，不认定生产路由调用了这些类，不删除/搬移或更改已有Provider。

`DebugProbesKt.bin` 是后缀分类误报：1,728B、CAFEBABE魔数的协程探针class资源，不是Kokoro模型/voice；排除误报仍有真实Kokoro类，门禁仍失败。原审计JSON和脚本未覆盖，新 `apk-boundary-clarification.json` 记录归因。此前REPORT/旧ledger中的“没有Kokoro标识”过宽结论明确撤回；历史APK不得仅凭相同大小认定相同内容。

按计划停止Task6：本轮没有adb、安装、instrumentation、DB/SHM/WAL基线/逐字节比较，也没有四个noun/verb WAV设备导出/回读、耗时、内存或听感结果。历史noun/workbuddy设备数据不替代显式VB新证据。源fixture仍为固定Phonemis MIT revision `71eb1ce33bd586d38cbac037843b8539d7829c3b`，显式VB实际命中家族键 `record|VERB`，默认命中 `record`；自动词性标注、广泛准确度、完整词典分发和正式接入均未获证明。

决定不变：`NO_GO_KEEP_SYSTEM_TTS`。Task5整体验收阻塞，Task7仅做失败证据收尾，不标记Spike accepted。后续处理既有生产Kokoro类隔离边界须单独授权；本轮不得擅自改Provider/source set或放宽门禁，不提交、不推送、不清理dirty/untracked内容。

### 2026-10-08 显式 VB APK 与设备验收更新

经用户确认，采用最小隔离方案：既有 `app/src/main/.../KokoroEngine.kt` 与 `KokoroPronunciationProvider.kt` 保留原路径和内容，但从 production debug Kotlin 编译排除；JVM unit-test 编译显式加入，Spike 共享源只映射 test/androidTest。修订审计同时收窄 `.bin` 模型/音色匹配，保留原失败 JSON。

修订后生产 APK 边界通过：`apk-boundary-current.json` 的 `boundary_pass=true`，九项检查全 true；production 22,100,502 B / SHA-256 `12c2de55fb33f945125efc604b1e5542142398995ee1ad9c5655e12b0b95932f`，androidTest 134,134,276 B / SHA-256 `1791f05e2d7f8f67d1fa5211bc792d1e63a5e922a5f8cf9756f50dd06e48daca`。`DebugProbesKt.bin` 为 CAFEBABE 协程 class-format 资源，不是模型/voice；误报排除不改变真实边界检查结果。

随后在 bf353dda / Xiaomi M2102J2SC / Android 13 上，使用 `adb install -r -t` 与显式 `am instrument`，仅执行 `KokoroEngineAvailabilityTest#record_noun_and_explicit_vb_generate_distinct_us_and_gb_audio`，结果 `OK (1 test)` / 13.182 s。四个拉回 WAV 均为 RIFF/WAVE、PCM16、mono、24 kHz、非静音且 PCM payload 偶数；US noun/verb 与 GB noun/verb 摘要均不同。DB/SHM/WAL 测试前后逐字节一致，MD5 分别为 `5e2b3fb9fb833e1d2398fd794f67df45`、`f48f753c4f7f81bc8d6e339fb0f62746`、`c9ba783325e6b52160edf1d01c095d56`。

该设备证据仅证明固定 fixture 的显式 VB 路径；完整 logcat 没有预期的详细 token/frontend/buffer 应用日志，结论不扩展为额外日志证明。仍不证明自动 POS tagging、广泛发音准确度、人工听感或生产集成批准。决定保持 `NO_GO_KEEP_SYSTEM_TTS`。
