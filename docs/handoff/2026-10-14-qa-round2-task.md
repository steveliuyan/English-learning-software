# 设备 QA 第二轮任务书（2026-10-14，D-5 修复后回归）

## 0. 你是谁、要干什么

你是独立设备 QA 分对话。基线：main tip `85da4fb`（含 D-5 修复 `734c7a3` 与认证宿主矩阵守卫 `85da4fb`，**1304 单测全绿**；`BrandLaunchActivity` 已改 FragmentActivity，主对话真机冒烟闪屏→MainActivity 已通过）。任务：**回归上轮被 D-5 阻塞的 B 组 + 追回丢失的回归文档**，产出更新报告并推送。**只走查、只写文档与 connected 测试**；缺陷登记 D-n，不得修改业务代码。

开工前必读（按序）：
1. `AGENTS.md`。
2. 本任务书。
3. `docs/handoff/2026-10-14-batched-device-qa-task.md`（上轮任务书，范围与红线延续）。
4. `docs/handoff/2026-10-14-batched-device-qa-report.md`（上轮报告；注意其远端分支内容止于 `fce2845`）。
5. `docs/handoff/2026-10-13-stage-4-f4-04-acceptance-record.md` 第 5 节（B 组清单主体）。

## 1. 主对话事实裁决（勿重开）

- **D-6「BackupRestoreActivity 是 ComponentActivity、恢复页 BiometricPrompt 不可达」为误报**：全仓不存在 `BackupRestoreActivity`；BiometricPrompt 仅 `BackupPairingExportScreen.kt:82` 一处；恢复页是 composable `BackupRestoreScreen`，宿主即 MainActivity（已 FragmentActivity）。该风险族已被 `BiometricHostActivityContractTest`（全仓 cast 屏幕 × Manifest 全部 Activity 矩阵）钉死，BrandLaunchActivity 已顺手收编。若仍持有 D-6 证据，须给出精确文件路径与行号重审，否则按此裁决登记。
- **上轮回归文档批次丢失**：`1a493e0` / `43b4643` 从未存在于本地对象库与远端（推测 push 挂起未成）。远端分支 `stage-qa-batch-2026-10-14` 已验证完全合入 main 后删除。本轮以新分支重做并**推送成功 + ls-remote 核验**为准。

## 2. 任务清单

### R1 文档追回（最高优先）

在 `docs/handoff/2026-10-14-batched-device-qa-report.md` 追加回归轮章节，如实分层：
- A2 认证导出：模拟器（API 30 AVD，`adb emu ... finger touch 1`）端到端已验证；真机认证弹窗可达 + 取消零写入已验证。
- A3 真机认证完成 = 系统级不可行（设备未录备用指纹/PIN、无设备内嵌指纹），登记「环境限制非产品缺陷」；模拟器 fingerTouch 手段可用。
- D-5 修复有效记录（引用 main `734c7a3`、矩阵 `85da4fb`）。
- 怪癖注记：MIUI `am start` Binder 异常改用 monkey；`adb shell cat /sdcard/...` 被 Git Bash 转路径改用 `adb pull`；模拟器 documentsui SAF 弹窗对 adb input 无响应；MIUI「显示备份密码」弹窗期间 `adb input` 非法；u2000 自动卸载 adb 安装应用。

### R2 B 组回归（上轮被 D-5 连锁阻塞，现已解锁）

- B1 双设备扫码配对端到端：真机 bf353dda 出挑战 QR ↔ API 30 模拟器扫码恢复（或双模拟器），走到恢复成功并要求重填 API Key。
- B2 替换恢复端到端：DB 三件套交换、失败回滚点、进程内 Room 重开。
- B3 BiometricPrompt 26–29 凭据降级分支：无现成 AVD 可尝试新建；环境搭建超 ~30 分钟直接标 blocked 写清缺什么。
- B4（可选）v2 Salvage 包导入必须得 `SalvageNotRestorable`。

### R3 闪屏宿主复核

`BrandLaunchActivity`（新 FragmentActivity）真机与模拟器冷启动无崩溃、正常转发 MainActivity；有 logcat/截图证据即可，能加 connected 测试更佳（加测必须门控全绿）。

### R4 A5 mitm 复核（条件项）

先确认用户已删 mitmproxy 用户 CA；已删则复核 OCR 零外联（隧道/流量级），未删如实标 blocked，**不自行装/删证书**。

### C 组（blocked，如实标注不投入）

C1 B5 端到端（等用户真实 AI Profile）；C2 T2 内容级解密。

## 3. 红线（不可协商，与上轮相同）

- `BackupCryptoPolicy` / `MigrationChallengePolicy` / `AndroidMigrationKey` 零改动；偏离先改决策文档。
- v2 Salvage 包导入必须 `SalvageNotRestorable`。
- 秘密永不进包/日志/错误文案/报告：挑战 S 只在二维码、密码 CharArray 即用即清、pairing.json 只存 C=SHA256(S)；报告不得抄录任何密钥、口令、挑战明文。
- 不修改业务代码；新增仅限 connected 测试与文档。
- 未实际执行且通过不得标"已验证"；部分验证写清验证到哪一层。

## 4. 环境与门控

- Gradle 用本机发行版：`C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`。
- 门控三任务：`:app:assembleDebug` → `:app:testDebugUnitTest` → `:app:compileDebugAndroidTestKotlin`；基线 **1304 全绿，只增不减**。管道末尾 `; exit ${PIPESTATUS[0]}`，grep 保留 `FAILED|^e:|BUILD`。
- Git 网络命令一律 `-c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false`；提交 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`；推送挂起 `taskkill //F //IM git.exe` 后重推；推后必须 `ls-remote` 核验。
- 真机 bf353dda：`adb install -r app/build/outputs/apk/debug/app-debug.apk`；权限 `adb shell pm grant`；`am start` 用 monkey 替代。模拟器资产：API 30 AVD（android-30 google_apis x86_64）已建，指纹认证用 `adb emu ... finger touch 1`。
- 同一 Compose 测试内两次 `setContent` 真机必炸，拆分测试。

## 5. 交付

1. 更新后的报告文档（R1–R4 各条含验证层级、D-n 表、blocked 清单）。
2. 推送独立分支 `stage-qa-round-2-2026-10-14`（不直接动 main），`ls-remote` 核验；报告末尾附可复制接续提示词。
3. 回报主对话：分支 tip、验证项数、新缺陷数、blocked 数；**特别确认 R1 推送成功**（上轮即栽在推送环节）。
