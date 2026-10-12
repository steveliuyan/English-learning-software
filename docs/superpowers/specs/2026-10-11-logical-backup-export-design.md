# 逻辑备份包与导出设计

日期：2026-10-11
状态：内部容器格式部分已实施；"不向用户开放导出"的限制经 2026-10-13 用户裁决放宽为无认证 Salvage 包导出，见 `docs/decisions/2026-10-13-f4-03-unprotected-export-scope.md`。不构成恢复协议或密码学批准。

## 目标

实现可测试的本地逻辑备份包基础：明确版本化包契约、逻辑快照序列化、条目限制与完整性校验，并为后续安全恢复协议提供 writer/validator 边界。不复制 Room 数据库文件，不导出凭据或临时内容。由于 F4-03 要求迁移认证/签名元数据，在安全签名能力接入前不向用户开放可携带的正式备份导出，不把未签名文件称为备份。

## 范围

本子项目只交付内部逻辑快照类型、版本化容器格式、writer/validator 与合成 fixture 上的 round-trip 测试。它不连接真实用户数据源，不生成或持久化可携带的用户备份，不暴露 SAF/UI 导出入口，不实现旧设备配对、签名认证、备份密码、加密、导入、恢复、覆盖本机数据或自动云同步。容器只在测试中使用合成数据；代码不得调用正式 Room DAO 或私有媒体仓库。后续只有完成认证、完整性/真实性验证与恢复失败安全设计，并明确批准可见导出流程后，才允许接入真实数据。测试容器不得显示“可恢复”，不得作为 AC4-03/04/05 完成证据。

## 当前基线

- `app/src/main/java/com/example/englishlearning/backup/domain/BackupProvider.kt` 目前仅定义 `export(snapshot, media)` 与 `BackupExport(formatVersion)`；源码注释明确 file format、encryption、restore 未实现。
- `app/src/main/java/com/example/englishlearning/core/export/LogicalSnapshot.kt` 仅包含 formatVersion、ExportProfileRecord 列表及 MediaManifestItem；它不是完整 F4 业务快照。
- F4-03 要求学习档案、词书进度、学习事件、今日计划、文章、收藏、查询历史，及可选 AI 音频/用户保存的拍照翻译；明确排除 API Key、Keystore 密钥、系统 TTS、临时图片和调试日志。
- 目标格式必须从逻辑数据生成，禁止拷贝 `english-learning.db`、WAL 或 SHM。

## 设计

### 1. 包模型与文件结构

输出采用一个自描述 ZIP 容器，内部只允许固定路径与固定类型条目。初版路径契约：

- `manifest.json`：包格式版本、创建时间、包含范围、实体条目清单、媒体条目清单、每条目的未压缩长度和 SHA-256、整体 payload 摘要。
- `records/<stable-type>/<stable-id>.json`：逻辑实体的规范化 UTF-8 JSON。类型名白名单；ID 必须通过安全字符规则，不能构造路径。
- `resources/<asset-id>`：明确允许导出的二进制媒体；asset ID 需规范化并在 manifest 唯一。

压缩条目顺序确定；manifest 使用稳定字段顺序与规范化 JSON 编码。包中不包含数据库文件、日志、缓存、临时目录、API Key、Authorization、SecretStore 值、Keystore 私钥或系统 TTS 音频。模型和 Kokoro 固定 APK 资源不是用户备份媒体，不进入包。

此阶段 manifest 的签名/配对/加密字段使用明确的 `not-protected` 状态，不能伪装成已签名或可认证。摘要只用于发现意外字节损坏，不抵御恶意篡改。UI 和 API 的安全文案必须准确说明这一点；恢复流程完成前此包不可被应用恢复。

### 2. 逻辑快照来源

在 `backup` 边界内增加显式白名单快照契约，不以反射或“导出所有 DAO 行”实现。每类实体需提供版本化 serializer 和稳定 ID。快照聚合只接受允许导出的领域记录与显式选择的资源。

开始编码前必须对照当前 Room entities、`LogicalSnapshot`、`MediaManifestItem`、F4-01/03，列出数据覆盖表。任何当前尚不存在的数据域（如收藏、用户保存的翻译记录）在本子项目中标记为不适用/未实现，不能生成空记录冒充已备份。初始可实现现有本地数据；AC4-03 在所有 F4 规定范围都可被表示前不得标为通过。

### 3. 内部构建流程

本期仅在 JVM test 使用合成 `LogicalSnapshot` 和内存资源构造容器，执行 serializer → writer → validator → reader 的 round-trip。生产代码只提供纯数据/流式边界，不接入真实 DAO、媒体仓库、SAF、ViewModel、导航或设置入口；也不写本地可分享文件。

### 4. 威胁模型与兼容性

- 保护目标：避免意外纳入凭据/敏感字段；避免格式歧义、资源耗尽和路径穿越；稳定识别意外损坏；保证未来恢复端可以拒绝不支持或不完整包。
- 非目标：本阶段容器没有真实性、抗恶意篡改、来源认证、机密性、重放防护或恢复授权能力。SHA-256 无密钥，不得用作签名替代。
- 攻击者能力：能够修改、截断、重排、重复 ZIP 条目；构造超大声明尺寸/高解压比数据；提交未知版本、重复实体 ID、畸形 UTF-8/JSON、路径逃逸名或不匹配摘要。validator 必须在有界资源内拒绝。
- 兼容性：每个格式主版本拥有不可变 schema；未知主版本 fail closed。未来变更使用新版本并提供独立 parser/fixture，不能静默迁移未知字段。测试覆盖同版本 round-trip 与未知版本拒绝。

安全密码学选择不在本期决定。进入认证恢复前，必须基于受维护标准库及其 Android API/设备支持做文档调研，选成熟签名、AEAD 和内存硬 KDF，并由安全架构审查其密钥生命周期、认证绑定、抗重放、参数下限/上限与升级策略；不得手写算法或用无密钥哈希冒充真实性。

### 4. 验证与错误

同一个纯 Kotlin 验证器验证准备写出的容器：白名单路径、版本、重复条目、条目数/总长度限制、实体 ID、媒体清单引用、各 entry 长度和 SHA-256、整体 payload 摘要。ZIP entry 名不允许绝对路径、反斜杠、`.`/`..` 路径段、重复规范化名称或未知根目录。导出生成器的结果必须经过该验证器后才可写出。

失败类型使用无自由文本的 sealed 结果（如 `InvalidSnapshot`、`UnsupportedFormat`、`ResourceUnavailable`、`IntegrityFailure`、`LimitExceeded`、`DestinationFailure`、`Cancelled`）；不携带原始 exception message、路径、Endpoint 或资源内容。UI 映射成固定可操作文案；日志只记录安全错误分类和随机操作 ID。

### 5. 安全不变量

- 备份数据源为逻辑快照，永不读取数据库文件字节作为 payload。
- 仅白名单领域类型和显式媒体可进入输出。
- 任何 Key/Authorization/Keystore 密钥/临时图像/调试日志都不可被 serializer 表达。
- 媒体在导出前验证其资源 ID、大小和 SHA-256；不一致时整个导出失败，不静默跳过。
- 生成包可检测偶然损坏，但在签名与迁移认证实施前不具备真实性/防恶意篡改保证。
- 写包流程只写用户所选目标，不改正式 Room 数据、复习状态、文章或媒体源。
- 所有错误文案、日志与导出统计不泄露实体内容或凭据。

## 文件职责（实施时预计）

- `backup/domain/BackupProvider.kt`：把占位返回契约替换为明确的 export request/result 与包元数据，不负责 UI 或密码学。
- `backup/domain/BackupPackageFormat.kt`（新）：稳定路径、版本、大小/数量限制和 manifest 数据类型。
- `backup/domain/BackupPackageValidator.kt`（新）：纯 Kotlin 流式验证合成容器的结构、entry 清单和摘要。
- `backup/infrastructure/ZipBackupPackageWriter.kt`（新）：确定性 ZIP 写入与有界流处理；只接收调用方传入的合成/显式逻辑对象，不访问 DAO、媒体仓库或文件系统。
- `app/src/test/java/com/example/englishlearning/backup/`（新）：manifest/限制/重复路径/哈希/恶意输入/未知版本/round-trip 用例。

本子项目不新增 BackupSnapshotSource、SAF Destination、ViewModel、screen、导航或真实数据库集成。只有认证恢复协议独立设计、审阅且获批准后，才讨论将该内部格式连接到用户数据。

实现文件归属需在计划阶段对照实际仓库结构再精确化；本设计没有覆盖恢复端。

## 验收与测试

- 单元 RED/GREEN：空快照、边界大小、未知类型、重复 ID、路径穿越条目、错误长度/哈希、媒体缺失或被篡改、取消、目标写失败各自有独立断言。
- 安全排除：以哨兵凭据和临时媒体内容构造数据库/模型，断言导出所有 entry 字节、manifest 和错误对象不包含哨兵；断言系统 TTS/Kokoro 固定资源不在包内。
- 属性/稳定性：同一快照在同一创建时间下生成相同 manifest、条目顺序与摘要；不同内容变更摘要。
- 流式限制：大媒体通过 bounded stream 处理；验证最大总大小、最大条目数和最大单文件长度，失败不继续写目标。
- JVM fixture round-trip：生成后独立解包与逐项重算 SHA-256；验证 fixture 内容与序列化稳定性。不得为测试写出用户真实快照或连接真实用户数据库。
- 本阶段不运行设备导出走查，因为没有生产入口，也不接触用户 DB/媒体。后续端到端导出获批准后，必须通过受控测试数据或用户另行授权的设备测试，并前后核对 DB、SHM、WAL 三件套。
- 阶段边界：本子项目不声称 AC4-03 通过，直至完整 F4 导出范围能表示并验证；不声称 AC4-04/05/06 通过，因为尚无认证恢复实现。
- 全量回归按项目既定 Gradle 参数串行运行；真机只用 `adb install -r -t` 与显式 `am instrument -w`，不得卸载、清数据或用 `connectedDebugAndroidTest`。

## 依赖与待审决策

1. 备份内容实体清单及 F4-01 相关数据（收藏、查询历史、拍照翻译保存记录）与现有 Room schema 的映射；仅为本期内部格式规划，不连接数据源。
2. 测试格式的 ZIP 与 JSON 版本策略及未来升级兼容策略。
3. 认证恢复阶段应采用的受维护安全库/签名、AEAD 与内存硬 KDF 方案；必须先核验 Android 支持和安全审查，不在本设计中指定算法参数。
4. SAF 原子输出、媒体许可与可选导出项留给未来用户可见导出项目决策；本期不调用 SAF、不读取真实媒体。

## 不在本设计范围

挑战响应配对、生物识别、签名身份密钥、密码 KDF/AEAD、备份加密、导入解包、隔离恢复、回滚点、原子切换、提醒重建、AI Key 重新配置、云备份、账号同步、合并恢复。
