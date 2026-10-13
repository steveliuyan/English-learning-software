# 会话任务书：F4-04 配对认证、备份密码与恢复

**分支：** `stage-4-f4-04-paired-restore`（已从 main `c64b0e7` 创建，main = 全部已完成交付，1118 单测全绿）
**日期：** 2026-10-13
**前置阅读（按顺序，全部必读）：**

1. `AGENTS.md`（重点：备份与恢复六条强制约束、AI 与隐私安全）
2. `docs/handoff/2026-10-12-session-briefing.md`（环境坑与规则）
3. `docs/specs/04-history-reminders-backup-and-security.md` 的 **F4-04 节 + AC4-04/05/06**（本任务唯一范围）
4. `docs/decisions/2026-10-13-f4-03-unprotected-export-scope.md` 与 `docs/handoff/2026-10-13-f4-03-closeout-report.md`（第 6 节尤其重要）
5. `docs/superpowers/specs/2026-10-11-logical-backup-export-design.md`（威胁模型与"不得自创密码学"约束）
6. `docs/handoff/2026-10-13-main-merge-closeout-report.md`（上一会话交接）
7. `docs/handoff/REPORT-TEMPLATE.md`（结束时写报告）

## 任务目标

实现换机安全恢复全链：旧设备认证配对（一次性挑战响应）→ 可选备份密码保护导出（受审 KDF+AEAD）→ 新设备隔离导入 → fail-closed 验证 → 二次确认后"替换本机数据"+ 回滚点。这是阶段 4 最重的安全任务，**分两大步：先交密码学/协议设计决策文档，经用户批准后才写实现代码**。

## 已核实的现状（2026-10-13 盘点，勿重复造）

**导出侧地基完整（F4-03 已合入 main）：**

- 格式 v2：`BackupPackageFormat`（CURRENT_VERSION=2、16 类型白名单、路径契约、`signature=not-protected/authentication=not-supported/encryption=none` 三个诚实状态字段——**校验器现在拒绝一切其他取值**）
- `BackupPackageValidator`：全副武装（未知版本 fail-closed、限额/摘要/中央目录结构/Zip Slip/路径白名单）；`ZipBackupPackageWriter`、`ZipPackagePackager`
- 快照组装：`BackupSnapshot`（逐实体版本化 DTO、字节级确定性）、`BackupExportCoordinator` + `InternalBackupSourceDao`（16 条 SELECT-only）+ `RoomBackupRowReader` + `FileOpsBackupMediaGateway`（媒体 size+SHA-256 预验证）
- UI：`BackupExportViewModel`（prepare→披露→SAF 两段式、取消零写入）+ `BackupExportScreen` + `ContentResolverBackupDestinationWriter`；入口在设置页"数据备份"
- 备份相关测试 8 个类约 90 用例，含 `BackupExportMustNotIncludeCredentialsTest` 哨兵排除
- 安全基建可复用：`core/security/AndroidKeyStoreSecretStore`、`KeyAliasEntity`、`AiProfileSecretUseCase`（Keystore 惯例参考）
- 提醒重排接口：F4-02 的 `ReminderScheduler`（恢复后重建提醒直接调用）

**确认的缺口（本任务要做的）：**

1. **密码学/配对设计决策文档（第一步，独立提交，等用户批准再继续）**：新建 `docs/decisions/2026-10-XX-f4-04-crypto-and-pairing-design.md`。必须先调研受维护标准库及其 Android API 26+ 支持度（候选方向：签名用 Android Keystore ECDSA-P256；AEAD 用 AES-256-GCM 或 XChaCha20-Poly1305 的受维护实现；KDF 用带盐内存硬的 scrypt/Argon2id——**库选型、算法、参数上下限、密钥生命周期、抗重放与升级策略全部写清**）。禁止自创算法/参数；新依赖须过 lockfile 三配置 `--write-locks` + `docs/third-party-notices.md` 八字段登记。
2. **配对协议（域层纯状态机 + UI）**：旧设备经本地解锁/生物识别/系统凭据确认（BiometricPrompt，AllowedAuthenticators 含 DEVICE_CREDENTIAL 降级）后生成一次性、短时有效挑战（二维码/迁移码）；新设备扫码/输入后完成挑战响应。**静态码、截图转发、仅包内公钥均不构成认证**——状态机测试要锚定过期、重放、单次使用。真机双设备走查攒批。
3. **格式 v3 + 受保护导出**：为 `signature/authentication/encryption` 新增真实状态取值并同步校验器（新格式版本承载，**勿静默放宽 v2 的强制**）；旧设备签名覆盖 manifest 规范摘要；可选备份密码走决策文档选定的 KDF+AEAD，加密元数据入 manifest；导出 UI 增加保护选项（是否设密码、配对状态展示）。v2 Salvage 包保持只读兼容：导入时对 v2 明确回"不支持认证恢复"固定类别。
4. **导入/恢复管线（核心，JVM 全链可测）**：隔离临时目录解包（大小/文件数/解压比/路径限制沿用校验器）→ **按 Spec 顺序**验证：支持版本→manifest schema→配对挑战→签名覆盖范围→可选密码→文件哈希→整体哈希→资源关系→实体引用→存储空间 → 任一失败 fail-closed：清理临时文件、正式数据零写入、仅非敏感失败类别。全部通过后仅"替换本机数据"：先建可验证回滚点（正式 DB+资源的本机私有区快照），用户二次确认后原子切换数据与资源，重建提醒（`ReminderScheduler`）与搜索索引，所有 AI Profile 置"需重新填写 Key"（不得自动迁移任何 Key）。
5. **恢复 UI**：新设备入口（选择备份文件→扫码/输入配对码→密码输入（如设）→验证进度与固定类别错误→替换二次确认→结果页）；**无旧设备/配对失败/密码遗忘 → 明示不可恢复，不提供任何跳过校验或强制导入入口**（AC4-06，源码契约测试锚定 UI 无旁路按钮）。
6. **验收记录**：新建 `docs/handoff/2026-MM-DD-stage-4-f4-04-acceptance-record.md`，AC4-04/05/06 逐条映射测试证据；AC4-05 负例矩阵必须全（篡改任一 payload/resource/manifest 字节、过期/已用/截图转发凭据、错误密码、路径穿越包、资源缺失、空间不足模拟、版本不支持）且断言导入前正式数据逐字节不变。

## 建议提交切分

1. `docs(decisions)`: 密码学/配对设计文档（**停下，向用户报告并等批准**；批准后如有依赖新增一并锁+台账）
2. `feat(backup)`: 配对协议域层状态机 + 测试
3. `feat(backup)`: v3 格式/校验器扩展 + 受保护导出（签名+AEAD）+ 测试
4. `feat(backup)`: 导入验证管线 + 回滚点 + 原子替换 + 测试（负例矩阵重点在这步）
5. `feat(ui)`: 配对/恢复/二次确认接线（沿用 F4-03 的两段式与降级模式惯例）
6. `docs`: 验收记录 + 收口报告

## 工作规则（违反即返工）

- TDD：先 RED 后 GREEN；密码学部分**只用决策文档批准的库与参数**，实现不得偏离；偏离先改文档再改码
- 验收门禁：`:app:assembleDebug` 先跑 → `:app:testDebugUnitTest` 全绿（基线 1118）→ `:app:compileDebugAndroidTestKotlin` 编译过；detekt/ktlint 只报增量
- 提交身份 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`；git 走代理 127.0.0.1:3067（偶发瞬断重试）；每步提交推送
- **范围红线**：不做合并恢复/云同步/WebDAV/跨设备自动同步；不保存明文密码或挑战于包内/日志/错误文案；Key/Keystore 机密永不进包（哨兵测试扩展到新 serializer）；不改 F4-03 Salvage 导出既有行为；不实现 F3-03 OCR；UI 接线级不做视觉精修
- 诚实文案：受保护包的密码遗忘不可恢复必须写明；错误只出固定类别（沿用 sealed failure 不携带原始 message 的惯例）

## 交付判定

1. 决策文档落档且获用户批准（未批准前实现代码不得合入）
2. 配对状态机 + v3 受保护导出 + 导入验证管线 + 替换恢复全链 JVM 测试锚定，AC4-04/05/06 逐条映射
3. 全量 JVM 绿 + androidTest 编译通过；真机双设备走查列入攒批待办
4. 验收记录 + REPORT-TEMPLATE 报告 + 提交推送

## 明确排除

合并恢复、云端传输、F3-03/收藏/拍照翻译功能实现（备份白名单等其落地再扩）、真机走查执行、UI 视觉打磨、detekt 存量债。
