# Talkify 风格供应商选择与回退设计

## 目标

将语音能力从“默认系统 TTS”升级为 Talkify 风格的供应商选择：用户可选择系统 TTS、OpenAI 或小米 MiMo；OpenAI 失败时回退 MiMo，再失败时回退系统 TTS。用户配置继续复用现有 AI Profile 与 Android KeyStore 密钥，不新增明文 API Key 存储。

## 范围

本阶段实现供应商选择、两个云端供应商 Profile 绑定、Room 持久化、OpenAI-compatible 远程 Provider、三段回退与设置页状态。ZipVoice 继续只显示“未下载”；Azure、火山引擎、腾讯云、阿里云百炼、MiniMax 继续显示“待接入”，不提供可用选择。

## 持久化模型

新增设备级单行表 `speech_preferences`，使用固定主键 `device`：

```kotlin
data class SpeechPreference(
    val preferenceId: String = "device",
    val selectedEngine: PronunciationEngine = PronunciationEngine.SystemTts,
    val openAiProfileId: String? = null,
    val miMoProfileId: String? = null,
)
```

表只保存当前供应商与两个 AI Profile ID。它不得保存 API Key、secret alias、endpoint、模型副本、音色副本或“已配置”状态。状态必须由 AI Profile、Speech capability 和 KeyStore 的 `hasKey()` 实时派生。

Room 从 v12 升级到 v13，必须提供 `MIGRATION_12_13`，不得使用 destructive migration。原有数据必须保持完整。

## 供应商与回退策略

```text
系统 TTS：系统 TTS
MiMo：MiMo Profile → 系统 TTS
OpenAI：OpenAI Profile → MiMo Profile → 系统 TTS
```

只在用户显式选择相应引擎时使用远程供应商。Profile 不存在、没有 Speech capability、密钥不可读、网络失败、HTTP 错误、空音频或播放失败均视为该供应商失败，继续下一个 fallback。`CancellationException` 必须原样抛出，不能触发 fallback。

OpenAI 和 MiMo 都复用 `/audio/speech`、安全 `TtsRequestBuilder`、`AudioHttpTransport`、`AudioPlayer` 与 KeyStore 边界；仅 profile ID 与默认音色不同。音频字节在播放完成后清零。

## 设置页

语音合成区必须显示当前供应商、OpenAI 状态、MiMo 状态、本地 ZipVoice 状态和待接入厂商。系统 TTS、OpenAI 和 MiMo 可选；选择 OpenAI/MiMo 时仅显示带 Speech capability 的现有 AI Profile，并以 KeyStore 状态派生“可用/缺少密钥/未配置”。不得显示 API Key。

选择保存失败时不得乐观显示已切换。被选择的 Profile 删除或密钥失效后，设置页显示不可用，播放时依规则自动 fallback。

## 安全与验证

- API Key 仅经 `AiProfileSecretUseCase` 读取，使用后立即清零。
- 不记录 key、Authorization header、完整请求体或音频正文。
- 云端请求必须继续使用 HTTPS、安全 endpoint 校验、无重定向与响应大小上限。
- 每个功能先写失败测试；必须覆盖 Room v12→v13 迁移、偏好往返、OpenAI→MiMo→系统回退、取消透传、Profile/Key 失效、设置页无密钥泄露。
- 真机验证沿用项目的 `adb install -r -t` + `am instrument` 路线，不使用 `connectedDebugAndroidTest`。
