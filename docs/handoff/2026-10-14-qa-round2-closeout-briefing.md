# 2026-10-14 第二轮设备 QA 交接简报（给主对话）

> 来源：QA 分对话（基线 main tip `37d8c92`）。完整实测记录见
> `docs/handoff/2026-10-14-batched-device-qa-report.md` §R2–R6，本文只做交接摘要。
> 分支 `stage-qa-round-2-2026-10-14`，远端 tip 以 `git ls-remote` 为准（推送后核验值见文末）。

## 1. 一句话结论

F4-04 认证恢复链路的**密码学与数据完整性全部实测通过**，但存在 3 个叠加的 blocker
（D-10/D-11/D-12），当前版本"旧设备导出 → 新设备认证恢复"在真实世界**端到端不可达**；
D-12 使 commit 段即使绕过扫码也必回滚。建议修复顺序：**D-12 → D-10 → D-11**（D-10/D-11
同属导出页 UI，可一并重设计）。

## 2. 缺陷清单（本轮新登记，均待主对话裁决）

| ID | 级别 | 摘要 | 关键证据 |
|---|---|---|---|
| D-7 | major | `ensureKey()` 失败静默（Idle 按钮无反馈） | Screen:86-90 + 截图 r2-export-keyfail.png |
| D-8 | major | Active 页 Column 不可滚动，QR 把导出按钮挤出屏幕（违反小屏约束） | 1080×1920 实测 |
| D-9 | major | Keystore 30s 认证窗口 vs 无重新认证入口 + 误导文案「加密组件不可用」 | 超窗失败/窗口内成功对照 |
| D-10 | **blocker** | 文本块 QR（█/空格 7.sp）zxing 不可解码；同矩阵方形 PNG 可解 → 渲染破坏、payload 有效 | 报告 §R2-b |
| D-11 | **blocker** | 流程死锁：挑战码只在导出前显示，Done 不再显示；恢复要求先选包；验证器强制包↔码绑定 → happy path 不可达 | 两 VM 状态机走查 + 设备复现 |
| D-12 | **blocker** | Room 2.8.4 `close()` 取消 `coroutineScope`，重开不重建 → swap 后 suspend DAO 必抛 `JobCancellationException` → postActions 失败 → commit 必回滚 | connected 测试 logcat（QA-B1 标签） |

D-6 维持误报裁决，未重开。红线文件（BackupCryptoPolicy / MigrationChallengePolicy /
AndroidMigrationKey）本轮零触碰；报告与仓库不含任何密钥/挑战明文。

## 3. 已验证（可信资产，修复后需保持绿）

- 认证导出 e2e（模拟器 API 30）：BiometricPrompt→签发→导出 23 条→SAF 落盘（30s 窗口内）。
- **prepare 全链**：真实包+真实码 → 结构校验、包↔码绑定、ECDSA 验签、有效期、隔离暂存全过。
- **篡改拒绝**：翻一字节 → Failed 且正式 DB SHA-256 逐字节不变。
- **回滚机制**：D-12 触发回滚后正式数据完好可续用（多次）。
- R3：BrandLaunchActivity 冷启动真机+模拟器双端正常。

## 4. 复验工具（QA 已备好，修完直接用）

`app/src/androidTest/.../DevicePairedRestoreE2ETest.kt`（复刻 AppModule 生产装配 + 阶段日志装饰器）：

1. 模拟器 A 认证导出（30s 内完成认证→dump QR→导出→SAF 保存）。
2. `adb pull` 包 + uiautomator dump 提取矩阵 → 方形 PNG → zxing 解码得挑战码
   （脚本在 `.git/qa-scratch/`：MatrixToPng16.java / QrDecode.java，不入库）。
3. 两输入 push 到 B 的 `/data/local/tmp/qa-b1-package.zip`、`qa-b1-code.txt`。
4. **不要**用 `connectedDebugAndroidTest`（跑全设备且卸载清空数据）；用
   `adb install -r` + `am instrument -w -e class ...#realPackageWithRealChallengeRestoresEndToEnd
   com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner`。
5. D-12 修复后期望：`commit is Restored`、rollback 点含旧 DB 三件套、重开查询通过。

挑战码 30 分钟过期；测试断言 `recordCount==23` 基于当前 A 演示数据，重新 onboarding 需同步。

## 5. 修复时的硬约束提醒

- D-12 修法候选（swap 后重建 Room 实例 / postActions 用 blocking DAO / 提交后重启进程）
  不得触碰 `checkpointAndClose` 的关库时序语义与回滚协议语义；先写暴露测试再修（TDD）。
- D-10/D-11 属导出页 UI/状态机重设计：QR 需方形高分辨率渲染且 Done 后仍可展示/重放挑战码
  （或改为"包文件+码同屏"配对）；改 UI 时保留 testTag 与 30s 窗口内重新认证入口（D-9 顺带解）。
- 按 AGENTS.md：范围变化先更新对应 Spec（阶段 4）再实施；决策写入 `docs/decisions/`。
- 未来 UI 重设计排期在功能完成后——本轮 D-8/D-10 修复建议只做最小版面修正，不做大改。

## 6. 环境现场

- 模拟器 A（emulator-5574）/B（emulator-5576）均在线、已 onboarding、应用为 `37d8c92` 构建；
  A 字体已复原 1.0；B 的 `/data/local/tmp` 有一次性 QA 输入（过期码，可覆盖）。
- 真机 bf353dda 数据未受影响（connected 测试从未在真机成功写入）。
- 更多怪癖（PIN keyguard 解锁、Gboard 吞字、AOSP 相机劫持焦点、SAF D-pad 导航等）见报告 §R5。

## 附：推送核验

`git ls-remote origin stage-qa-round-2-2026-10-14` →
`de8c42d4e006cc4e4bc78fd8d29fee17e216a58a`（本简报提交；其下为实测提交 `0a526e2`、追回文档 `44307d5`）。

```
你是 H:\EnglishLearningSoftware 的主对话。第二轮设备 QA 分对话已收工，交付在分支
stage-qa-round-2-2026-10-14（远端 tip 以
git -c http.proxy=http://127.0.0.1:3067 -c https.proxy=http://127.0.0.1:3067 -c credential.interactive=false ls-remote origin stage-qa-round-2-2026-10-14
输出为准，最后核验值 de8c42d）。请：
1) git fetch 后读 docs/handoff/2026-10-14-qa-round2-closeout-briefing.md（交接摘要）
   与 2026-10-14-batched-device-qa-report.md §R2–R6（完整实测）。
2) 裁决并按 TDD 修复三个 blocker，顺序 D-12 → D-10 → D-11（D-9 可并入 D-10/D-11 的
   导出页改造；D-7/D-8 为 major 随后）。D-12 根因：Room 2.8.4 close() 取消
   coroutineScope，swap 后 suspend DAO 必抛 JobCancellationException → commit 必回滚；
   修法候选见简报 §5，checkpointAndClose 关库时序与回滚协议语义不得动，
   BackupCryptoPolicy / MigrationChallengePolicy / AndroidMigrationKey 红线零触碰。
3) 修复后用简报 §4 的 connected 测试复验（勿用 connectedDebugAndroidTest，
   用 adb install -r + am instrument；30 分钟挑战过期需重新导出）。
4) 范围变化先更新阶段 4 Spec，决策写 docs/decisions/；单测基线 1304 只增不减。
提交身份 -c user.name=steveliuyan -c user.email=2021250388@qq.com。
```

