# 2026-10-13 F4-04 合并收口报告（主对话）

分支：`stage-4-f4-04-paired-restore` 已快进合入 main（无 force），main tip `6c93fc7`，已推送 GitHub。

## 1. 对分对话四项请示的裁决

1. **真机走查**：继续攒批，不在合并前做。双设备扫码配对需要第二台设备/模拟器，属独立 QA 轮；合并后派专门设备 QA 分对话统一执行（清单：验收记录第 5 节 + F4-02/F4-03 历史攒批项）。
2. **两个代码遗留**：SAF 落盘接线合并前必修（否则受保护导出端到端不可用）——本次已完成（见第 2 节）；恢复后进程内 Room 衔接归入真机走查项，代码侧暂不变更（必要时走查中确认是否加「恢复后请重启应用」提示）。
3. **合并时机**：SAF 修复验证通过后立即快进合 main（分支基于 c64b0e7 线性无分叉，`merge --ff-only` 成立，远端 main 未被改动）。
4. **下一功能**：确认 F3-03 拍照 OCR；许可核验（ML Kit 主路径 / Tesseract 降级）为硬前置第一任务，未通过不得引入依赖或下载任何模型/训练数据。

## 2. 本次主对话侧修复（提交 6c93fc7）

TDD 先红后绿（新增 7 用例），两处缺口一并闭环：

- **SAF 落盘两段式**：`BackupProtectedExportViewModel` 注入 `BackupDestinationWriter`，导出成功只在内存持有待写包并给出确定性 `.elpbak` 文件名；`CreateDocument` launcher 回调接 `onDocumentPicked`——选中位置才写入、取消零写入、写入失败保留待写包允许换位置重试（与 F4-03 同语义）。重新认证/新一轮导出会使旧待写包作废（单活取代语义）。
- **页面可达性缺口（新发现）**：`BackupPairingExportScreen` 此前在生产路径无任何调用点（AppScreen 无路由、设置页无入口），即使接完 SAF 也无法使用。本次接入：设置「数据备份」组新增「认证导出（可用于新设备恢复）」行（`settings_open_backup_pairing_export`）、AppScreen 路由 + BackHandler + overlay 清单、MainActivity `hiltViewModel` 接线（满足 `AppScreenProductionWiringTest`）。

## 3. 验证记录

- 全门禁 `:app:assembleDebug → :app:testDebugUnitTest → :app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL；**1213 用例、0 失败 0 错误**（1206 基线 + 7 新增，`test-results` XML 汇总核验）。
- RED 证据：接线前新测试引用不存在的 API（`onDocumentPicked`/`packageReady`/`fileName`/双参构造）编译失败，实现后转绿。
- 快进核验：合并前 `merge-base --is-ancestor main stage-4-f4-04-paired-restore` 成立；推送后 `ls-remote` 复核 origin/main == `6c93fc7`。
- detekt/ktlint 非门禁，未跑增量（存量债照旧）。

## 4. 遗留与交接

- 真机走查攒批（派独立设备 QA 分对话）：双设备配对扫码、替换恢复端到端（DB 三件套交换/回滚/进程内重开）、BiometricPrompt 全矩阵、恢复失败逐字节比对、本次新增「认证导出页真机入口 + SAF 保存」项。
- 下一步：F3-03 拍照 OCR，任务书 `docs/handoff/2026-10-13-f3-03-photo-ocr-task.md`，许可核验先行。
- 环境坑延续：推送挂起 → `taskkill //F //IM git.exe` 后重推；git 走代理 127.0.0.1:3067；`--write-locks` 三任务一次写全。
