# Talkify 风格语音基础设计

## 背景

英语学习 App 需要参考 Talkify 的多供应商体验，支持小米 MiMo、OpenAI、本地 ZipVoice-Distill，并为 Azure、火山引擎、腾讯云、阿里云百炼、MiniMax 预留入口。用户明确不希望 Android 系统 TTS 作为主方案；未完成的供应商不能伪装成可用。

## 目标

第一阶段只建立真实、可测试的语音领域边界和 Talkify 风格设置入口，接通现有词卡播放回调的统一路由；不在本阶段伪造远程请求、不提交或内置 ZipVoice 权重、不实现未确认的供应商。

## 非目标

- 本阶段不实现 MiMo/OpenAI 的真实网络请求。
- 本阶段不下载或打包 ZipVoice/Emilia 模型权重。
- 本阶段不接入 Android 系统 TTS 作为替代实现。
- 本阶段不重构阅读 UI 和长按句子翻译。

## 架构

```text
CardDetailScreen / ArticleReadingScreen
              ↓ callbacks
        SpeechEngine
              ↓
       SpeechProviderRegistry
       ├── MiMo       可配置/未接入
       ├── OpenAI     可配置/未接入
       ├── ZipVoice   可下载/未下载
       ├── Azure      待接入
       ├── Volcengine 待接入
       ├── Tencent    待接入
       ├── Bailian    待接入
       └── MiniMax   待接入
```

领域层只定义文本、供应商标识、能力和结果，不出现 Android、HTTP 或 API Key 类型。远程密钥继续复用现有 AI Profile 的安全存储边界，不能写入源码、日志、APK 或普通数据库。

## 本地模型声明边界

源码可以按 Talkify 的 MIT 方式保留版权和许可证说明；ZipVoice-Distill/Emilia 权重按上游非商业许可单独声明。模型权重不进入 GitHub 仓库、Release 或 APK，只保留用户主动下载的入口和来源链接。应用内显示个人学习、非商业用途和第三方许可提示。

## 验收

- JVM 测试覆盖供应商 ID、状态、选择和未实现供应商不可用。
- Compose 测试覆盖设置页完整供应商列表、待接入状态和本地模型未下载状态。
- 词卡播放按钮只经过统一 SpeechEngine 回调，不直接绑定某个供应商。
- 现有测试和编译保持通过。
