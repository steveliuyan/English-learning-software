# 2026-10-13 Stage 4 / F4-04 配对认证、备份密码与恢复 验收记录

分支：`stage-4-f4-04-paired-restore`。密码学/协议设计裁决见 `docs/decisions/2026-10-13-f4-04-crypto-and-pairing-design.md`（D1–D5 已获用户批准）。

## 1. 验收门禁

| 门禁 | 命令 | 结果 |
|---|---|---|
| 构建 | `:app:assembleDebug` | PASS |
| 单测 | `:app:testDebugUnitTest` | PASS：1206 用例、0 失败（基线 1118 + 本任务新增 88：配对 17、v3 校验器/受保护导出 54、恢复管线与提交网关 45 档中合并计数） |
| androidTest 编译 | `:app:compileDebugAndroidTestKotlin` | PASS |
| detekt/ktlint | 非门禁 | 存量债照旧，未当回归 |

## 2. AC4-04 → 测试证据

**AC4-04：新设备以有效的一次性配对挑战和正确密码（如设）恢复后，数据和选定资源完整可读，AI Key 不存在，提醒被重新安排。**

| 环节 | 证据 |
|---|---|
| 一次性配对挑战签发 | `MigrationChallengeIssuerTest`（17 用例：认证门控零发放、单活取代、过期、台账不含 S） |
| 挑战绑定校验 | `PairedMigrationChallengeVerifierTest`：code↔package 常时比较（pub/N/C/时间窗）、过期、签名 |
| 正确密码解密 | `BackupRestorePipelineTest`：`encrypted package requires the password and stages decrypted plaintext`（Argon2id 派生 → DEK 解包 → 逐条目 AEAD 解密，明文写入隔离暂存区，`RestoreEntityDecoder` 往返断言 JSON 字节级一致） |
| 资源完整可读 | 同文件 `resource mismatching the asset record digest is a resource relationship failure`（正向 = 摘要/尺寸逐条匹配） |
| AI Key 不存在（D4） | `AndroidPostRestoreActions.eraseAiCredentials()` 逐 Profile 调 `AiProfileSecretUseCase.deleteKey`；哨兵红线由 `BackupExportMustNotIncludeCredentialsTest` + `BackupExportProtectedCoordinatorTest` 的包字节扫描延续覆盖（新 serializer 无 credential 字段） |
| 提醒重新安排 | 同类 `rescheduleReminders()` → `ReminderScheduler.refresh()`；提交网关测试断言维护清单按序执行且任一失败回滚 |

## 3. AC4-05 负例矩阵 → 测试证据（全部 fail-closed + 正式数据零写入）

| 负例 | 类别 | 证据 |
|---|---|---|
| 篡改 payload 字节 | IntegrityFailure | `BackupPackageValidatorV3Test`（密文 sha 不匹配）+ `BackupRestorePipelineTest`（管线层拒绝、零暂存） |
| 篡改 resource 字节 | IntegrityFailure | 同上（摘要校验） |
| 篡改 manifest 字节 | PairingSignatureInvalid | `replaceInManifest(createdAt)` → 签名门失败 |
| 篡改 pairing.json 字段 | PairingMismatch | nonce 篡改 → `PairingRecordMismatch` 映射 |
| 过期配对码 | PairingExpired | `expired pairing code is rejected`（now > expiresAt） |
| 他人 nonce / 截图转发凭据（secret 不匹配） | PairingMismatch | `foreign pairing code (wrong nonce or secret) is a mismatch` |
| 外部密钥签名 | PairingSignatureInvalid | `signature produced by a foreign key is rejected` |
| 缺密码 | PasswordRequired | `encrypted package requires the password…` |
| 错误密码 | PasswordIncorrect | DEK 解包 AEAD 标签失败；密码 CharArray 立即清零（断言） |
| 条目密文篡改（密钥已确认） | DecryptionFailure | `entry tampering after key unwrap is a decryption failure` |
| 路径穿越包 | IntegrityFailure | `isAllowedPayloadPath` 白名单拒绝（`BackupPackageValidatorTest` 沿用 + 管线 raw 读取双重拒绝） |
| 资源缺失 | ResourceRelationshipInvalid | `asset record without its resource file…` |
| 空间不足模拟 | StorageInsufficient | `insufficient storage space rejects before any scratch write`（含暂存/提交两路径） |
| v2 Salvage 包 | SalvageNotRestorable | `v2 salvage package answers not restorable without entering the chain` |
| 未知版本（1/4） | UnsupportedRestoreVersion | `unknown format version is unsupported` + 校验器 UnsupportedFormat 映射 |
| 替换后 quick_check 失败 | RollbackVerification | `quick check failure after the swap rolls the formal root back byte for byte`（回滚点逐字节还原断言） |

**正式数据零写入锚**：管线所有失败路径在进入提交网关之前返回（网关未被调用、暂存区为空）；提交网关在快照建立前失败时 DB 从未 checkpoint/关闭；快照后任何失败自动回滚并断言 formal 内容逐字节等于恢复前。设备级逐字节比对属真机走查项（第 5 节）。

## 4. AC4-06 → 测试证据

**AC4-06：受保护备份在旧设备不可用或密码遗忘时不可恢复；UI 不提供跳过认证或强制导入入口。**

- 旧设备不可用：挑战签发强制 Keystore 密钥 + 系统认证；`KeyUnavailable`/`AuthenticationRejected` 均不签发挑战（`MigrationChallengeIssuerTest` 认证门控零发放）。
- 密码遗忘：Argon2id+AES-GCM 无找回路径；导出/恢复双端固定文案「密码遗忘则备份无法恢复」（`RestoreNoBypassContractTest.honest messaging…` 锚定）。
- 无旁路：`RestoreNoBypassContractTest` 3 用例——ViewModel 代码无 bypass/forceImport/skipValidation 标识符、Screen 无对应 testTag、唯一提交路径是 `backup_restore_confirm_replace` 且必须先经暂存会话；D2 相机实时扫码（无手输/相册识码标识符）。
- 静态码/仅包内公钥不构成认证：配对码含 QR-only `S`（包内只有 C=SHA256(S)），`PairedMigrationChallengeVerifier` 常时绑定校验；重放由一次性挑战+TTL+单活语义覆盖（`MigrationChallengeIssuerTest`）。

## 5. 实现澄清（与决策文档的两处偏差说明 + 边界）

1. **`pairing.json` 不进入 manifest `entries` 与 payload 摘要**（§3.2 字段清单一致，防签名↔摘要循环依赖）：其 7 个字段与签名 manifest 的 pairing 块逐一交叉核对，`derSignature` 对 `elpair-v3/`+manifest 原始字节验签。
2. **`dekWrapAad` 排除 wrap IV**（§5.1 说 AAD=encryption 块规范字节）：IV 自引用会破坏 AAD 构造；GCM 标签已保护 IV/密文本身，AAD 绑定全部 KDF/AEAD 参数与盐，参数替换攻击面不变。
3. **加密包 manifest `entries[].byteSize/sha256` 记录密文实际字节**（§5.1 明示）；§5.2「文件哈希/整体哈希作用于明文」按「结构（密文）哈希验证 + AEAD 解密认证等价满足」实现，恢复管线无需明文摘要比对。
4. **提交阶段固定类别**：§9.2 清单 + §9.5 `RollbackVerification`；暂存写失败映射 `StorageInsufficient`（诚实：私有分区容量不足）。
5. **密钥清理**：密码/KEK/DEK 全路径 `fill(0)`（导出与恢复双侧，测试断言）。

**未验证：真机**（攒批走查清单）：

- Keystore EC 密钥生成 + BiometricPrompt 全矩阵（含 API 26–29 凭据降级边界）。
- 双设备扫码配对 + 相机 QR 实测（CameraX 预览/权限流）。
- 替换恢复端到端（含 WAL 三件套 snapshot/swap/quick_check、回滚点还原、恢复后进程内数据库重开）。
- 恢复失败后正式数据逐字节快照比对（设备级）。

## 6. 依赖与台账

D1 新增运行时依赖（`app/gradle.lockfile` 三配置 `--write-locks` 锁定）：androidx.biometric 1.1.0、androidx.camera core/camera2/lifecycle/view 1.6.1、com.google.zxing:core 3.5.3（维护模式为已知妥协）。`docs/third-party-notices.md` 八字段条目已按 catalog alias 登记（`ThirdPartyNoticesTest` 锚定）。

## 7. 交付清单（提交）

| 提交 | 内容 |
|---|---|
| ccc32fa | 决策文档（用户批准） |
| 6618b04 | 配对协议域层（状态机/QR 编解码/校验器） |
| de90603 | v3 格式/校验器分派 + 受保护导出（签名+AEAD） |
| 053ace6 | 导入验证管线 + 暂存区 + 回滚点/原子替换 |
| 25171d2 | D1 依赖 + lockfile + 台账 |
| 7e0de3b | 配对/扫码/恢复 UI 接线 + 设备层实现 + AC4-06 契约测试 |
