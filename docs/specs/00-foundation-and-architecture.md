# 阶段 0：工程基础、离线架构与安全基线 Spec

## 目标

建立可持续迭代的 Android 工程基线，让后续背词、AI、媒体和备份功能能在本地优先、安全可测的边界内开发。

## 范围

### F0-01 工程与架构

- 使用 Kotlin 与 Jetpack Compose 创建 Android 应用工程。
- 采用分层模块边界：`lexicon`、`learning`、`reading`、`language`、`ai`、`backup`、`notification` 与 `ui`；UI 不直接访问网络、密钥或数据库。
- 本地关系数据使用 Room；媒体资源存入独立应用私有目录，并通过资源 ID 和 SHA-256 与数据库关联。
- 建立依赖注入、错误模型、时间/时区抽象和可替换 Provider 接口。

### F0-02 本地数据与安全凭据

- 创建本地 Profile、词书元数据、单词、学习事件、进度、今日计划、文章、媒体和 AI Profile 的 schema 与迁移机制。
- API Key 仅写入 Keystore 支撑的 SecretStore；Room 仅保存不可逆的 key alias/reference。
- 建立日志脱敏规则：Authorization、API Key、图片二进制、备份明文、密码和私钥不得记录。
- SQLCipher 是否启用由基准测试与密钥生命周期设计决定；未决定前不得将数据库裸文件作为备份格式。

### F0-03 可替换服务边界

- `ReviewScheduler`：输入学习状态与反馈，输出新状态与下次复习时间。
- `AiProvider`：声明文本、视觉、语音能力；不向 UI 暴露供应商协议。
- `PronunciationProvider`：统一系统 TTS、端侧模型与云端语音。
- `OcrProvider`：只返回文字、坐标与置信度。
- `BackupProvider`：只接受逻辑快照与媒体清单，不接受数据库文件直拷贝。

### F0-04 依赖与许可台账

- 创建 `docs/third-party-notices.md`，登记所有依赖、数据集、模型的名称、版本、许可证、用途、网络/隐私数据流、NOTICE 位置、替代方案和商业分发结论。
- 未完成许可核验的资源不得进入正式发布包。

## 非功能要求

- 核心本地读写不得在主线程执行。
- 对无网络、存储空间不足、数据库迁移失败和 Keystore 不可用提供可测试错误状态。
- 适配小屏、动态字体、深色模式和 TalkBack 基础导航。

## 验收标准

- AC0-01：应用离线启动后可创建本地 Profile，重启后仍能读取，且日志中无敏感字段。
- AC0-02：AI Profile 的 Key 只可经 SecretStore 读取；导出 Room 逻辑数据时不存在 Key 明文或 Authorization Header。
- AC0-03：任一 UI ViewModel 不能直接引用 HTTP 客户端、SecretStore 或 Room DAO。
- AC0-04：媒体文件被删除或哈希不一致时，资源读取返回“不可用/可重建”，不导致应用崩溃。
- AC0-05：第三方台账中每一项都具备许可证、用途和数据流字段；缺字段时发布检查失败。

## 不在本阶段实现

词书内容、背词 UI、AI 实际调用、文章生成、OCR、提醒和备份恢复。
