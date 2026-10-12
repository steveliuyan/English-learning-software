# 阶段 4 F4-02（本地提醒）实现与验收记录

**日期：** 2026-10-13
**结论：** JVM 层实现与验收完成；真机走查（通知弹窗、权限拒绝流程）不阻塞后续任务。
**Spec：** `docs/specs/04-history-reminders-backup-and-security.md` F4-02 节

## 实现（全部在本分支 `stage-4-f4-02-reminders`）

| 层 | 文件 | 说明 |
|---|---|---|
| 域 | `notification/domain/ReminderSchedulingPolicy.kt` | 纯函数决策：disabled/已完成 → Cancel；否则唯一一次 Schedule（下一次严格晚于 now → 不补发）。完成判定复用 `UnlockPolicy`（规则版本决定严格/宽松） |
| 域 | `notification/domain/ReminderPreferenceRepository.kt` | 偏好持久化边界 + 分钟数转换 |
| 持久 | `core/storage/entity/ReminderPreferenceEntity.kt` + DAO + **DB v28 迁移** | 单行表，默认关、默认 20:00 |
| 持久 | `notification/RoomReminderPreferenceRepository.kt` | Room 实现，错误映射 StorageUnavailable |
| 基建 | `notification/WorkManagerReminderScheduler.kt` | WorkManager 非精确调度；`UNIQUE_WORK_NAME` + REPLACE = "取消旧任务并重排唯一一次"；重启自动恢复（无需 BOOT 接线）；`ReminderNotificationWorker` 到点复查决策；`ReminderNotifications` 渠道 + Android 13+ 权限静默跳过 |
| UI | `ui/ReminderSettingsViewModel.kt` / `ReminderSettingsScreen.kt` | 开关+时间选择，改动立即落库并 refresh；非精确说明与隐私说明固定展示 |
| 接线 | `AppScreen.kt`（入口 row + overlay + 权限请求）、`MainActivity.kt`、`AppModule.kt`、`AndroidManifest.xml`（POST_NOTIFICATIONS）、`EnglishLearningApplication.kt`（渠道创建） | |

## 验证

- TDD RED→GREEN：`ReminderSchedulingPolicyTest` 7 用例（RED 确认后实现）；`ReminderSettingsViewModelTest` 4 用例
- **TDD 捕获真实缺陷并修复**：ViewModel 的 load 协程迟到时整体覆盖状态，会把用户刚改的开关打回存储值（loading 竞态）。修复：用户改动即清 loading，load 不再覆盖已编辑状态
- 门禁：`:app:assembleDebug` + `:app:testDebugUnitTest` **1041 用例全绿**（基线 1030 + 11）
- 依赖台账：WorkManager 2.10.0 已登记 `docs/third-party-notices.md`（Apache-2.0）

## AC4-02 逐条映射

| 要求 | 结论 | 证据 |
|---|---|---|
| 默认关闭 | ✅ | DB 列默认 false + `ReminderPreferenceRepositoryFake`/Entity 默认值；域测试 `disabled reminder never schedules` |
| 未完成日最多一次提醒 | ✅ | 域层输出单次 Schedule；WorkManager unique+replace 幂等（源码契约） |
| 完成/关闭/改时/重启/时区变化不留幽灵通知 | ✅ JVM 层 | 完成与关闭→Cancel（域测试）；改时/时区→重排单次（域测试）；重启→WorkManager 持久化恢复+到点复查（源码契约） |
| 拒绝权限后无循环弹窗 | ✅ | AppScreen 权限判定仅在点开关时发起一次请求；Worker 无权限静默跳过；真机复核待做 |
| 通知不含 Key/原图/OCR/文章内容 | ✅ | 文案常量 `ReminderMessageText`，域测试 `message text never carries learning content` 锚定 |

## 待真机人工验收（不阻塞）

1. 开启提醒 → 到点收到通知；完成当日计划 → 到点不收到
2. Android 13+ 拒绝权限 → 无重复弹窗；设置页说明可见
3. 重启设备后提醒仍生效

## 遗留与风险

- 提醒时间选择为固定槽位（V1 简化）；自由时间选择器留待 UI 重设计轮
- `WorkManagerReminderScheduler` 的 profileId 为 "default"（单 profile 假设，与阶段 0 决策一致）；多 profile 属后续范围
