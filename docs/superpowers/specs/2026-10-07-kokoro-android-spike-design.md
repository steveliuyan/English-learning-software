# Kokoro Android Spike 设计

## 状态

已获用户确认，范围为测试专用 Android Spike；不代表正式产品接入批准。

## 目标

在不改变默认发音路由、不把未完成验证的 Kokoro 资产纳入正式发布边界的前提下，将桌面已验证的单词发音链路迁移到 Android 测试路径，并在真实设备上取得可审计的构建、生成、播放、性能、内存、APK 增量和数据库零副作用证据。

## 非目标

- 不修改 `PronunciationRouter`、Hilt 默认绑定、词卡行为、文章朗读、MiMo/OpenAI 路由或缓存键。
- 不在本阶段对自动 HMM tagger、完整文本 G2P、长词分块或音质做产品承诺。
- 不在未通过许可证台账和真机门禁前生成正式 Kokoro Provider。
- 不使用 `connectedDebugAndroidTest`，不卸载应用，不清理用户数据。

## 资产与许可证边界

第一阶段先运行第三方台账检查并补齐 Kokoro/Phonemis/ONNX Runtime 的固定版本、许可证、来源、用途、数据流、NOTICE 位置、替代方案、商业分发结论、文件大小和 SHA-256。台账未通过时停止，不修改 Gradle 或复制模型。

验证通过后，模型和 voice/frontend 仅进入 Spike 测试资产路径，不进入 `app/src/main/assets`、release 资源或正式 Provider。所有加载前校验必须先验证文件大小和 SHA-256；任何失败都返回明确不可用状态并且不创建推理 session。

固定资产：

- ONNX Runtime Android `1.29.0`，CPU provider。
- Kokoro INT8 ONNX：`6e742170d309016e5891a994e1ce1559c702a2ccd0075e67ef7157974f6406cb`，92,361,271 bytes。
- Voice pack：`bca610b8308e8d99f32e6fe4197e7ec01679264efed0cac9140fe9c29f1fbf7d`，28,214,398 bytes。
- Phonemis en-us：`479cd9d89819a60c4f8609dbb116ae5b362d46c0c592b97605968cbaef1e7bc6`，7,105,527 bytes。
- Phonemis en-gb：`065baba624db8391905e53c78706a12c5f4cba12a77e62a90900b2113bdb17e7`，7,105,527 bytes。

## 组件边界

### `OnnxKokoroEngine`

Spike-only Android 实现，负责准备、资产校验、创建和释放 ONNX session，以及单词级合成。它不读取 Room、Profile、SecretStore 或网络配置。

建议接口：

```kotlin
interface KokoroAndroidEngine {
    suspend fun prepare(): Result<Unit>
    fun isReady(): Boolean
    suspend fun synthesize(text: String, accent: PronunciationAccent): Result<ByteArray>
    suspend fun close()
}
```

`prepare()` 和 `synthesize()` 必须在 `Dispatchers.Default` 或 `Dispatchers.IO` 上运行；调用方即使在主线程调用，也不得在主线程创建 session 或执行推理。`close()` 必须释放 session 和临时 native 资源，并可重复调用。

### 前处理

Android 使用与桌面相同的受限单词契约：trim/lower 后仅接受 1–20 个 ASCII 字母及内部 apostrophe。执行 grapheme 编码、Phonemis frontend、CTC 解码、Kokoro sparse vocabulary 编码。词典优先、显式 tag 和自动 tagger 不在本阶段 Android 入口中扩展；测试 fixture 使用固定目标词和未知词。

### `KokoroPronunciationProvider`

现有同步 Spike Provider/Engine 保持逐字节不变，不与新 suspend 生命周期强行接线。Android instrumentation 显式构造测试 Engine，拿到 WAV 后调用既有 `AndroidAudioPlayer`，播放结束或失败后 finally 清零临时 ByteArray。Engine 未准备或失败返回 Result.failure，取消重抛；不接入正式发音 Router。

## 数据流

```text
fixed test asset
  -> size/SHA-256 gate
  -> background ONNX session prepare
  -> single-word validation
  -> accent-specific Phonemis frontend
  -> CTC dense 116 labels
  -> Kokoro tokens + voice style + speed
  -> INT8 Kokoro audio
  -> 24 kHz mono PCM16 WAV
  -> AndroidAudioPlayer
```

本阶段不写 Room、不写媒体缓存、不保存 API Key、不发网络请求。测试只输出脱敏的状态、耗时、音频字节数、格式、内存和哈希。

## Android 测试 APK 边界

如果固定资产必须随测试运行时提供，则使用明确的 test-only asset source 或 instrumentation 专用外部文件注入，并在构建报告中分别列出 debug APK、androidTest APK 与资产体积。不得把资产悄悄放入正式 `main` source set。若当前 Gradle/AGP 无法保持该边界，则停止并报告阻塞，不用临时复制规避。

共享纯 Kotlin 实现放在 `app/src/kokoroSpike/kotlin/`，仅映射给 test/androidTest；ORT 适配器仅在 androidTest，依赖仅 androidTestImplementation `com.microsoft.onnxruntime:onnxruntime-android:1.29.0`。校验台账后用 Gradle 生成受控测试配置锁。测试资产为五项：INT8、完整 NPZ voice、US/GB frontend、固定 vocab JSON；暂不引入 lexicon/tagger。NPZ 只读 ZIP 中 af_heart.npy/bf_emma.npy，严格检查 little-endian float32、C-order、shape [510,1,256] 和有限值，使用 tokenCount 行。模型加载前五项全部 size/SHA 通过；116 dense labels取固定审计的上游常量，与 sparse vocab 独立。保留许可证/版权文本于测试资源。不得引入动态版本、额外 G2P 运行时或网络下载器。

## 测试顺序

1. 许可证台账 RED/GREEN：缺项时校验失败，补齐后 exit 0。
2. Android Engine JVM/构造边界测试：资产错误、未 prepare、线程、accent、失败清理、close 幂等。
3. 构建 debug 与 androidTest APK，记录 APK 条目和体积增量；不得运行 connected task。
4. 真机建立 Room 三件套前置 MD5，使用 `adb install -r -t` 安装两个 APK，必要时设置 MIUI `appops 10021 allow`。
5. 通过 `am instrument` 运行 focused instrumentation：prepare、英美 `apple/schedule/record`、播放、重复生成、失败路径、内存和耗时。
6. 测试后重新拉取 Room 三件套并逐字节比较；任何变化都阻止 GO。
7. 用独立工具回读设备侧音频/日志，形成报告；只有全部门禁通过才讨论正式接入。

## 失败与安全要求

- 任一资产摘要不匹配：拒绝创建 session，状态为不可用。
- prepare 未完成：合成返回 unavailable，不隐式同步加载。
- ONNX/前处理/播放异常：返回 failed，释放 session 或临时资源，不写 Room。
- 协程取消：释放当前 session/播放器资源并重新抛出取消异常，不伪造成普通失败。
- 任何日志不得包含模型 payload、API Key、Authorization、用户数据库内容或完整音频字节。

## 退出标准

本阶段仅在以下条件全部有证据时才允许把结论从 Spike NO-GO 改为“可评审”：

- 许可证台账和 NOTICE 校验通过。
- Android debug/androidTest 构建成功，资产边界和包体增量可审计。
- 真机英美目标词生成非空、格式正确并能播放。
- 模型准备、生成、播放均不阻塞主线程。
- 记录首次加载、单词生成、播放耗时和内存峰值。
- 测试前后 Room 三件套逐字节一致。
- 失败路径可回退，不影响当前系统 TTS。

即使全部通过，也只允许进入独立正式接入评审；本设计本身不授权修改生产路由。
