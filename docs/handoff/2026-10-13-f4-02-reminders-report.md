# 2026-10-13 F4-02 本地提醒工作报告

> 按 `docs/handoff/REPORT-TEMPLATE.md` 撰写。

## 1. 本次目标

实现 F4-02 本地提醒（Spec 04）：默认关闭的每日提醒，非精确调度，完成即不发。分支 `stage-4-f4-02-reminders`。

## 2. 完成了什么

- **域层（TDD）**：`ReminderSchedulingPolicy` 纯函数决策 + 7 用例；复用 `UnlockPolicy` 判定当日完成（严格/宽松随规则版本）
- **持久层**：`reminder_preferences` 单行表，DB v27→v28 迁移，Room 仓储
- **基建**：WorkManager 2.10.0（已登记依赖台账）；`WorkManagerReminderScheduler` unique+replace 幂等调度；`ReminderNotificationWorker` 到点复查；通知渠道；POST_NOTIFICATIONS 权限流程（拒绝不重复弹）
- **UI**：`ReminderSettingsViewModel`/`ReminderSettingsScreen` + 设置页入口 + AppScreen/MainActivity/DI 接线
- **验收记录**：`docs/handoff/2026-10-13-stage-4-f4-02-acceptance-record.md`（AC4-02 逐条映射）

## 3. 验证记录

- RED→GREEN：域层先测后实现；VM 测试抓到**真实竞态缺陷**（迟到 load 覆盖用户输入）并修复
- 门禁：`:app:assembleDebug` + `:app:testDebugUnitTest` **1041 用例全绿**（基线 1030 + 11）
- 新依赖锁文件更新：`app/gradle.lockfile`（work-runtime/startup-runtime 等 5 条）

## 4. Git 状态

- 分支 `stage-4-f4-02-reminders`（自 `stage-3-f3-01-dictionary` 创建），本次提交后推送
- 工作树干净

## 5. 未完成 / 遗留问题

1. 真机走查三项（通知弹窗/权限拒绝/重启恢复），见验收记录，不阻塞
2. 时间选择为固定槽位（V1 简化），自由时间选择器归入 UI 重设计轮
3. detekt 1580 条先存债治理仍未排期

## 6. 下一任接手须知

- 下一候选：F3-03 拍照 OCR（需先核验 ML Kit 许可与 GMS 降级策略）→ 备份接真实数据（F4-03）
- WorkManager 调度核心是**决策纯函数 + unique replace**，改调度语义先读 `ReminderSchedulingPolicyTest`
- 环境坑：新依赖必须 `--write-locks` 更新 `app/gradle.lockfile`（两条 classpath 都要），否则 `checkDebugAarMetadata` 失败
- 其余环境坑见简报第三节

## 7. 用户确认过的决策

- 直接开发 F4-02（本会话口头确认）；F3-01 查询历史保留不回退沿用上会话决策
