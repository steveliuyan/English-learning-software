# 会话交接简报：F4-04 配对认证、备份密码与恢复（已完成，待真机走查与合 main）

**日期：** 2026-10-13
**分支：** `stage-4-f4-04-paired-restore`（自 main `c64b0e7`，全部提交已推送 GitHub，tip `e7ca959`）
**状态：** ✅ 开发完成，1206 单测全绿，真机 APK 已装（设备 bf353dda），**尚未合入 main**

## 一句话结论

F4-04 全链（配对认证 → 受保护导出 → 隔离导入 → 替换恢复+回滚）按批准决策文档交付完毕，AC4-04/05/06 全部有测试证据映射；剩真机走查攒批 + 两个小遗留，走查通过后可合 main。

## 必读文档（按序）

1. `docs/handoff/2026-10-13-f4-04-paired-restore-report.md` — 收口报告（本次交付全貌、遗留、环境坑）
2. `docs/handoff/2026-10-13-stage-4-f4-04-acceptance-record.md` — 验收记录（AC 逐条映射 + 负例矩阵 + 5 项实现澄清 + 真机走查清单）
3. `docs/decisions/2026-10-13-f4-04-crypto-and-pairing-design.md` — 密码学/配对设计（D1–D5 用户已批准，勿改参数）

## 提交清单（8 个）

| 提交 | 内容 |
|---|---|
| ccc32fa | 决策文档（用户批准） |
| 6618b04 | 配对协议域层（状态机/QR 编解码/校验器） |
| de90603 | v3 格式/校验器 + 受保护导出（签名+AEAD） |
| 053ace6 | 导入验证管线 + 暂存区 + 回滚点/原子替换 |
| 25171d2 | D1 依赖（biometric/CameraX/zxing）+ lockfile + 台账 |
| 7e0de3b | UI/设备接线 + AC4-06 契约测试 |
| bc2a56d | 验收记录 + 收口报告 |
| e7ca959 | 真机走查修复①：CAMERA 权限未声明导致授权弹窗无效 + 授权后 UI 刷新 |

## 遗留（合 main 前处理）

1. **真机走查（攒批）**：双设备扫码配对端到端、替换恢复端到端（DB 三件套交换/回滚/恢复后进程内 Room 重开）、BiometricPrompt 全矩阵（API 26–29 凭据降级边界）、恢复失败正式数据逐字节比对。清单在验收记录第 5 节。
2. **受保护导出页 SAF 落盘未接完**：`BackupPairingExportScreen` 的 `CreateDocument` launcher 已建但 pendingBytes 写盘路径没接（仿 F4-03 两段式补一段即可）；包生成与校验链路本身完整。
3. 走查通过 → 快进合 main（本线基于 c64b0e7，无分叉，可直接 ff）。

## 环境坑（本会话实测）

- **推送偶发挂起**：多个 git 进程僵死互等锁，`taskkill //F //IM git.exe` 后重推即恢复；git 须走代理 127.0.0.1:3067。
- **`--write-locks` 陷阱**：新增依赖必须 `assembleDebug + testDebugUnitTest + compileDebugAndroidTestKotlin` 三任务一次性 `--write-locks`，否则 `debugUnitTestCompileClasspath`/AAR 元数据配置会报 lock 状态错。
- **AppScreen 接线防回归**：`AppScreenProductionWiringTest` 强制每个可选参数在 MainActivity 接线；新增 ViewModel 参数漏接即挂。
- 合同测试读源码时注意：`"*/"` 字符串字面量会干扰块注释剥离正则（勿用"先剥注释再断言"的写法断言字符串常量）。

## 安全红线提醒（对下任 AI）

- 密码学参数全部钉死在 `BackupCryptoPolicy`/`MigrationChallengePolicy`，任何偏离先改决策文档再改码。
- v2 Salvage 包导入必须答 `SalvageNotRestorable`，不进入验证链。
- 秘密永不进包/日志/错误文案：挑战 S 只在二维码、密码 CharArray 即用即清、pairing.json 只存 C=SHA256(S)。

## 建议下一步

真机走查攒批（含本线 + F4-02/F4-03 历史清单）→ 合 main → 开 **F3-03 拍照 OCR**（ML Kit 主 / Tesseract 降级；许可核验硬前置）→ 阶段 5 + UI 重设计。
