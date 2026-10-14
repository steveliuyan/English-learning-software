# 攒批真机走查任务书（2026-10-14）

## 0. 你是谁、要干什么

你是独立设备 QA 分对话。任务：对 main（tip ≥ `e052b23`）做一轮攒批真机走查，产出**缺陷清单 + 走查报告**并推送。**只走查、只写测试与文档**；发现的缺陷一律登记为 D-n（含复现步骤、严重级、建议修法），**不得自行修改业务代码**，由主对话裁决后另行安排修复。

开工前必读（按序）：
1. `AGENTS.md`（工作方式与强制产品约束）。
2. 本任务书。
3. `docs/handoff/2026-10-14-f3-03-merge-closeout-report.md`（遗留清单）。
4. `docs/handoff/2026-10-14-stage-3-device-qa-report.md` §5–§6（上一轮 QA 怪癖与遗留）。
5. `docs/handoff/2026-10-13-stage-4-f4-04-acceptance-record.md` 第 5 节（F4-04 走查清单主体）。
6. `docs/handoff/2026-10-14-wordcard-reveal-acceptance-record.md` §4（词卡新揭晓流程，TalkBack 新焦点项）。
7. `docs/specs/01-vocabulary-learning-and-review.md` F1-03 新文案（回忆-揭晓-评分，验收口径以新 Spec 为准）。

## 1. 范围裁决（用户已定，勿重开）

- F3-03 方案 A 许可决策已批准；D-1 已修；D-3/D-4 信息级不修。
- **D-2（全局无深色主题）不修**，归 UI 重设计轮；走查中遇到深色相关新问题只登记不修。
- B5 端到端、T2 内容级抓包**本轮仍为 blocked**（等用户配真实 AI Profile / 手动删 mitm CA），如实标注，不得标已验证。

## 2. 走查清单

### A 组：单机（bf353dda）立即可做

- A1 TalkBack 端到端走读核心学习流：今日计划 → 词卡（**重点：新「显示释义」按钮 `word_card_reveal` 的焦点顺序、朗读文案、揭晓后朗读播报**）→ 三档评分 → 详情页 → 学习记录三标签 → 设置 → 拍照翻译入口。发现即登记 D-n（截图 + TalkBack 朗读内容）。
- A2 认证导出页真机入口 + SAF 保存（F4-04 §5）：设置入口进入受保护导出页 → 认证 → 导出包生成 → SAF 选目标保存落盘；**取消必须零写入**、失败可重试。
- A3 本机认证恢复路径（API 33）：恢复流程中 BiometricPrompt 正常出弹窗、认证通过后进入下一步（不完整替换，替换端到端在 B 组）；用户拒绝/取消认证时必须零正式写入。
- A4 OCR 复走一遍 AC3-05 主链路（拍照或相册选图 → 识别 → 逐次确认 → 翻译展示 → 不保存），确认 D-1 修复后的拒绝权限文案与「去系统设置开启相机权限」真机可用（先用 `adb shell pm revoke` 造拒绝态，验完 `pm grant` 复原）。
- A5 mitm 抓包复核 OCR 零外联（隧道/流量级）：**先确认用户已删 mitmproxy 用户 CA**；未删则 A5 记 blocked，不要自行安装或删除证书。

### B 组：需要第二台设备/模拟器

- B1 双设备扫码配对端到端（旧机出挑战 QR、新机扫码、恢复成功）。
- B2 替换恢复端到端：DB 三件套交换、失败回滚点、进程内 Room 重开、恢复后要求重填 API Key。
- B3 BiometricPrompt 26–29 凭据降级分支（需 API 26–29 环境）。
- B4（可选）v2 Salvage 包真机导入必须得 `SalvageNotRestorable`。

执行方式：先探测本机 Android SDK 是否可起 AVD 模拟器（`emulator -list-avds` / `avdmanager list`）。可用则自建 AVD 跑 B1–B3；**不可用或环境搭建超过 ~30 分钟仍不通，直接标 blocked 并写清缺什么**，不要陷在装机里。模拟器摄像头弱，拍照翻译类用相册选图替代。

### C 组：blocked（如实标注即可，不投入时间）

- C1 B5 显式保存历史 → 备份 → 恢复端到端（等用户真实 AI Profile）。
- C2 T2 内容级解密（等用户删 CA 且隧道稳定）。

## 3. 红线（不可协商）

- 密码学参数全部钉死在 `BackupCryptoPolicy` / `MigrationChallengePolicy`，任何偏离先改决策文档再改码——走查对话无权改。
- 秘密永不进包/日志/错误文案：挑战 S 只在二维码、密码 CharArray 即用即清、pairing.json 只存 C=SHA256(S)。走查报告中**不得抄录任何密钥、口令、挑战值明文**。
- 不修改业务代码；新增仅允许 connected 测试与文档。若新增测试，门控必须全绿（见 §4）。
- 未实际执行且通过的项不得标"已验证"；部分验证要写清验证到了哪一层。

## 4. 环境与门控（本机怪癖，先读再用）

- Gradle：直接跑 `gradlew` 会因网络失败，用 `C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`。
- 门控三任务（改了任何 Kotlin/资源都必须跑）：`:app:assembleDebug` → `:app:testDebugUnitTest` → `:app:compileDebugAndroidTestKotlin`；基线 **1302 单测全绿，只增不减**。管道取退出码末尾加 `; exit ${PIPESTATUS[0]}`，grep 保留 `FAILED|^e:|BUILD`。
- Git 网络命令一律：`-c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false`；提交需 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`。推送挂起先 `taskkill //F //IM git.exe` 再重推；推送后必须 `ls-remote` 核验。
- 真机 bf353dda：MIUI u2000 会自动卸载 adb 安装的应用——装完立刻测，`adb install -r app/build/outputs/apk/debug/app-debug.apk`；权限可 `adb shell pm grant <pkg> <permission>` 预授。`adb shell input text` 会被 WeType 拦，先切 IME。
- 同一 Compose 测试内两次 `setContent` 真机必炸（上轮实测），写 connected 测试注意拆分。

## 5. 交付

1. 报告：`docs/handoff/2026-10-14-batched-device-qa-report.md`（参照 `docs/handoff/REPORT-TEMPLATE.md`），含每项验证层级、D-n 缺陷表、blocked 清单、TalkBack 走读记录。
2. 提交并推送到独立分支 `stage-qa-batch-2026-10-14`（不直接动 main），`ls-remote` 核验后在报告末尾附**可复制接续提示词**（若中断）。
3. 回报主对话：分支 tip、验证了几项、新缺陷几个、blocked 几个。
