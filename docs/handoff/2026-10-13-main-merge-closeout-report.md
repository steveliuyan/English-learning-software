# 2026-10-13 真机 QA 分支合并 main 收口报告

## 1. 本次目标

用户原话："负责把真机 QA 分支 stage-4-device-qa-fixes 合并回 main……合并全程不允许 force 推 main；快进路径不成立就停下报告"。两处需拍板项（打卡线取舍、README 以哪份为准）用户回复"你决定就好"，授权本会话决定。

## 2. 完成了什么

**拓扑复核（接手先核实，全部属实）**

- `origin/main` 确为孤立单提交 `1567401`（"Initial commit"，仅薄 README），与交付历史无共同祖先。
- 孤儿打卡线 tip `132f507` 本地完整，与主线 merge-base 为 `f49a8a8`（11 个功能提交 + 1 文档提交）。
- QA 分支头含合并提交 `6135812`（父：`3d9a4ca` + `1567401`），`1567401` 是其祖先 → 快进路径成立（main 落后 240，领先 0）。

**打卡线重叠比对（拍板依据）**

- `f49a8a8..132f507` 共碰 18 个文件（CheckInScreen/ViewModel、LearningStatsRepository/Room 实现、InternalLearningStatsDao、DailyLearningStats、AppScreen/MainActivity/DI、androidTest 与 JVM 测试）。
- 逐一核对：这些文件在当前主线全部存在且**更新**（主线另多出 `RoomLearningStatsRepositoryTest`、`CheckInViewModelTest` 演进版等）。判定孤儿线无独占内容。

**两项决定（用户授权本会话拍板）**

1. 打卡线：不合入，打轻量存档 tag `archive/checkin-line-132f507` 指向 `132f507` 并推送 GitHub 存档。
2. README：以正式版为准。`README.md` 由占位内容（"# - / 个人自用的学习软件"）替换为项目概览（使命、功能概览、开发验收顺序、许可台账指引），提交 `a8380ca`。

**合并与推送（全程无 force）**

- 本地新建 `main` 指向 `1567401` → `git merge --ff-only stage-4-device-qa-fixes` 快进到 `a8380ca` → `git push origin main`（`1567401..a8380ca`，正常 fast-forward）。

**合并后门禁暴露并修复一个真缺陷（autocrlf 字节腐败）**

- 首跑 `KokoroProductionBoundaryTest` 失败：`phonemis-audit/kmp-runtime-kokoro-v1.0-config.json` 磁盘 2500B vs 清单声明 2351B。
- 排查：内容与 git blob 逐行一致，仅换行符差异——本机 `core.autocrlf=true` 在跨分支 checkout 重铸工作树时把该 JSON 从 LF 转成 CRLF（149 行 +149 字节），与按 LF 记录的 `manifest.sha256` 大小/哈希不符。QA 分支当初绿是因其工作树文件恰好未被重写；**任何 Windows 全新克隆都会在 main 上挂此门禁**，属可复现性缺陷而非代码回归。
- 修复：仓库根新增 `.gitattributes`，对 `app/src/main/assets/kokoro-production/**` 禁用换行/合并转换（`-text -diff`）；资源文件从 git blob 重铸回 LF 并验证字节与哈希匹配清单。提交 `bf53a85` 已推送 main。未改任何用户 git 配置。

## 3. 验证记录

- 修复后完整门禁（顺序执行，main 上）：
  - `:app:assembleDebug` ✅（重建 APK，确保打入 LF 字节资源）
  - `:app:testDebugUnitTest` ✅ **1118 用例，0 失败 0 错误**（从结果 XML 汇总核对）
  - `:app:compileDebugAndroidTestKotlin` ✅
- 修复前那次失败仅 1 例（Kokoro 边界测试），其余 1117 全绿；确认失败面与根因一一对应。
- detekt/ktlint：本轮无源码改动（README/tag/.gitattributes），无增量，不作门禁。

## 4. Git 状态

- `main` = `bf53a85`，已推送 GitHub（`1567401..a8380ca..bf53a85`，两次均快进，无 force）。包含：完整交付历史（240+ 提交）、两轮真机 QA 修复、F4-02 提醒、F4-03 备份导出、README 正式版、`.gitattributes` 修复。
- tag `archive/checkin-line-132f507` 已推送。
- `origin/stage-4-device-qa-fixes` 头 `a8380ca`（含 README 提交），已推送。
- 工作树干净（`.workbuddy/` 工具私有数据未动、未提交）。

## 5. 未完成 / 遗留问题

- **笔记展示栏新版面待用户真机复测**（例句下方展示栏 + 弹窗管理，QA 第二轮改动，功能报告第 5 节）。
- **F4-03 §6 SAF 备份导出真机走查仍开放**（保存 .elpbak + 取消零写入）。
- 攒批真机走查：阶段 1/2、F3-01、F4-02 各项目（见各验收记录）。
- 遗留拓扑注意：QA/F4 系列分支已全部并入 main；旧分支引用仍在远端（stage-1-f1-02-today-plan-ui、stage-1-f1-04-unlock-verify、stage-1-f1-05 等），是否清理未拍板，**未动**。`origin/HEAD` 仍指向 stage-1-f1-05，建议改指 main，同样未动等用户确认。
- detekt 基线债（~1710）治理、sherpa/Piper→Kokoro 替代链合规决策文档：仍未排期。

## 6. 下一任接手须知

- 下一步：**F4-04 配对认证、备份密码与恢复**。先读 `docs/handoff/2026-10-13-f4-03-closeout-report.md` 第 6 节与 `docs/specs/04-history-reminders-backup-and-security.md` F4-04 节，按任务书惯例从 main 切新分支（如 `stage-4-f4-04-paired-restore`）并写交接任务书。
- 工作基线：main = `bf53a85`，JVM 单测 1118 全绿。
- 新坑（已验证）：本机 `core.autocrlf=true` 会把文本资源 checkout 成 CRLF——字节完整性类测试（manifest/sha 比对）再报"大小不匹配但内容相同"时，先查换行符；`.gitattributes` 已保护 kokoro-production 目录，其他字节敏感资源若入 git 需同步加规则。
- git 网络偶发 "Empty reply / SSL handshake" 瞬断（代理 127.0.0.1:3067），重试即可。
- 环境坑不变：Gradle 本地 8.13 路径；提交显式身份；先 assembleDebug 再单测。

## 7. 用户确认过的决策

- 合并方式：本会话（主对话）执行合并回 main；QA 会话不直接推 main（沿袭设备 QA 交接文档第 1 节）。
- 全程不允许 force 推 main；快进路径不成立即停报（实际成立，未触发停止条件）。
- 两项待拍板（打卡线 132f507 存档 tag、README 以哪份为准）：用户回复"你决定就好"，最终决定 = tag 存档不合入 + README 替换为正式版。
- 合并后重跑门禁顺序：assembleDebug → testDebugUnitTest（应 1118 全绿）→ compileDebugAndroidTestKotlin。
- 下一任务：进入 F4-04 配对认证、备份密码与恢复（用户明示）。
