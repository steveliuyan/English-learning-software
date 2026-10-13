# English Learning（Android 英语背词应用）

本地优先的 Android 英语学习应用：词书学习 + FSRS 间隔复习 + 每日 AI 语境短文 + 离线查词与发音 + 拍照翻译 + 学习提醒 + 逻辑备份导出。面向大学生、四六级与考研用户；无账户、无云同步，AI 服务由用户自行配置（兼容 OpenAI 接口）。

## 功能概览

- 词书学习与今日计划：新词/到期复习目标、不可变每日计划快照、严格/宽松解锁模式
- FSRS 间隔复习：保留原始学习事件，算法升级只影响未来安排
- 每日 AI 文章：双语阅读、受控的第三方 AI 请求（HTTPS-only、Key 存于 Android Keystore、发送前确认）
- 离线查词与发音：已安装词书检索、系统 TTS 优先、Kokoro 端侧发音合成与缓存
- 拍照 OCR 与翻译、学习提醒（WorkManager 非精确调度，默认关闭）
- 逻辑备份导出（版本化容器、完整性校验；不含 API Key、Keystore 密钥、临时图片）

## 开发

- 工程与治理规则见 `AGENTS.md`；分阶段规格在 `docs/specs/`，阶段交接与验收记录在 `docs/handoff/`
- 技术栈：Kotlin、Jetpack Compose、Room、Hilt、WorkManager
- 构建验收：`:app:assembleDebug` → `:app:testDebugUnitTest` → `:app:compileDebugAndroidTestKotlin`
- Gradle 使用本地发行版（wrapper 下载受网络限制），环境说明见 `docs/handoff/2026-10-12-session-briefing.md`

## 数据来源与许可

第三方依赖、数据集与模型的许可台账见 `docs/third-party-notices.md`；未通过许可核验的资源不打包、不发布。
