# 2026-10-13 F4-04 配对认证、备份密码与恢复 工作报告

## 1. 本次目标

按任务书 `docs/handoff/2026-10-13-f4-04-paired-restore-task.md` 交付 F4-04 全链：旧设备认证配对 → 可选密码受保护导出 → 新设备隔离导入 fail-closed 验证 → 二次确认"替换本机数据"+ 回滚点/原子替换 → 恢复后固定动作。

## 2. 完成了什么

按批准的提交切分交付 6 个提交（全部已推送 `origin/stage-4-f4-04-paired-restore`）：

- **决策文档**（ccc32fa，用户批准 D1–D5）：`docs/decisions/2026-10-13-f4-04-crypto-and-pairing-design.md`。
- **配对协议域层**（6618b04）：`MigrationChallengeIssuer` 状态机（认证门控/单活/取代/台账无 S）、`MigrationCodeCodec`（ELPAIR1 QR 编解码）、`PairedMigrationChallengeVerifier`（常时绑定→承诺→过期→签名）。
- **v3 格式 + 受保护导出**（de90603）：`BackupPackageFormat` v2/v3 分派与状态白名单、`BackupCryptoPolicy`（Argon2id 65536/3/1、AES-256-GCM 12/16、P-256/SHA256withECDSA、AAD 构造）、`BackupPackageValidator` v3 门（配对交叉核对+验签+参数钉死）、`ProtectedZipBackupPackageWriter`、BC/Tink 实现、`BackupExportCoordinator.prepareProtected`。
- **导入/恢复管线**（053ace6）：`BackupRestoreCoordinator`（§5.2 顺序 fail-closed、密码/解密、codec、资源/实体引用门、容量门、暂存）、`FileRestoreStagingWorkspace`、`SnapshotSwapRestoreCommitGateway`（回滚点+SHA-256 台账+多目标 rename 交换+quick_check+回滚）、`RestoreFailure` 14 固定类别。
- **D1 依赖**（25171d2）：biometric 1.1.0 / CameraX 1.6.1 ×4 / zxing 3.5.3，lockfile 三配置锁定 + 台账八字段。
- **UI/设备接线**（7e0de3b）：Keystore EC 迁移密钥（用户认证绑定）、BiometricPrompt 门控、受保护导出页（QR 显示/密码选项）、新设备恢复页（CameraX+ZXing 实时扫码 D2、密码、二次确认、结果页）、`RoomRestoreDataWriter`（隔离区重建 DB+资源）、`RoomFormalDataHandle`（checkpoint/quick_check）、`AndroidPostRestoreActions`（索引/提醒/Key 清除 D4/审计）、AC4-06 源码契约测试、设置/主屏接线。
- **验收记录**：`docs/handoff/2026-10-13-stage-4-f4-04-acceptance-record.md`（AC4-04/05/06 逐条映射 + 5 项实现澄清）。

## 3. 验证记录

- 全门禁 `:app:assembleDebug → :app:testDebugUnitTest → :app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL；1206 用例全绿（基线 1118 + 新增 88，提交内含中间态轮次）。
- TDD 全程 RED→GREEN（恢复管线负例矩阵 16 用例先红后绿；提交网关 `maintenance failure` 用例抓出 swappedOk 未重置导致不回滚的真实缺陷后修复）。
- detekt/ktlint 非门禁，未跑增量。

## 4. Git 状态

- 分支 `stage-4-f4-04-paired-restore`，tip `7e0de3b`，已推送 GitHub（25171d2..7e0de3b）。
- 无未提交遗留文件。
- 环境坑复现：推送偶发挂起（多个 git 进程僵死互相等锁），`taskkill //F //IM git.exe` 后重推即恢复；git 走代理 127.0.0.1:3067。

## 5. 未完成 / 遗留问题

- **真机走查（攒批）**：双设备扫码配对、BiometricPrompt 全矩阵（含 26–29 凭据降级边界）、替换恢复端到端（WAL 三件套交换、回滚点还原、恢复后进程内重开）、设备级逐字节零写入比对。见验收记录第 5 节清单。
- 受保护导出页的 SAF 写盘（`CreateDocument` launcher 已建但 `pendingBytes` 写入路径未接完）——导出确认后的落盘需补一段与 F4-03 相同的两段式接线；当前受保护包生成与校验链路完整。
- 恢复成功后应用进程内已打开的 Room 单例与交换后文件的衔接：`RoomFormalDataHandle` 会 close/reopen，但消费方持有旧 DAO 的情况需真机验证；必要时加"恢复后重启应用"提示。
- F3-03 OCR 未动（明确排除）。

## 6. 下一任接手须知

- 接手第一步：读本报告 + 验收记录 → 合并分支回 main 或先补真机走查（建议先走查再合并）。
- 坑：`--write-locks` 新增依赖时必须跑 assemble+test+androidTest 三任务一次性写全，否则 `debugUnitTestCompileClasspath`/AAR 元数据配置会报 lock 状态错误；AppScreen 生产接线有防回归测试（`AppScreenProductionWiringTest`），新增可选参数必须同时接 MainActivity。
- 关键文档：决策文档 §2–§6/§9（协议与不变量）、`backup/restore/`（管线）、`backup/pairing/`（协议）、验收记录。

## 7. 用户确认过的决策

- D1–D5 全部批准（决策文档 §11）：依赖面（Tink/BC/zxing/CameraX/biometric）、仅相机实时扫码（D2）、30 分钟配对会话语义（D3）、恢复后删除全部 AI Key 密文（D4）、全部密码学参数固化（D5）。
- 包 3 提交（de90603）时的两处实现澄清（pairing.json 不入摘要、dekWrapAad 排除 IV）已记入验收记录第 5 节，属决策文档字段清单内的一致化，非参数偏离。
