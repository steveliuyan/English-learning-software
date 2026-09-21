# 学习日身份与时钟/时区回退语义

- 日期：2026-09-21
- 状态：已接受
- 相关规格：`docs/specs/01-vocabulary-learning-and-review.md` AC1-07a~e

## 背景

原实现每次以当前 `ClockProvider` 的 `instant` 和 `zoneId` 计算 `localDate`，然后只按 `(profileId, localDate)` 查找计划。设备时区向西切换或系统时钟回拨时，本地日期可能回到更早日期；若该日期没有计划，系统会错误地生成第二个计划并再次发放新词，违反计划快照不可变及禁止重复发放的要求。

## 候选方案

1. **计划日期锚定、日期单调前进（采用）**：学习日身份是该 profile 已有计划中 `localDate` 最大的日期。当前本地日期不大于锚定日期时复用该计划；只有严格大于锚定日期时才生成新计划。
2. 日历日期定位并由反馈状态门控：日期回退时，只有最新计划没有反馈才允许创建新计划。该方案允许未学习前的时区纠错，但状态依赖多、行为不确定，可能产生分叉。

## 决定

采用方案 1：

- 无历史计划：按当前本地日期首次生成。
- 当前日期等于锚定日期：复用不可变快照。
- 当前日期早于锚定日期：视为时区/时钟回退，静默复用锚定快照，不新建、不改写 `zoneId`、`generatedAt`、任务或反馈。
- 当前日期严格晚于锚定日期：生成新快照；不限制一次跳过的天数，跨多日与正常数日未打开应用保持一致。
- 计划锚定使用 `localDate` 而不是 `generatedAtEpochMillis`。后者依赖系统时钟单调，而本功能需要处理系统时钟回拨，不能用可能被篡改的时钟瞬间定义身份。
- 回退时静默处理，不增加提示或设置入口；V1 不增加“重建今日计划”入口。

## 实现边界

- DAO 新增按 `profileId` 查询 `localDate DESC LIMIT 1` 的方法；未改表结构、索引或迁移。
- 仓储新增 `findLatest`，复用既有任务装载与 `CancellationException` 行为判别式。
- Use case 先查询锚定计划，再决定复用或生成；原有首次生成、卡片来源、`saveIfAbsent` 幂等冲突处理保持不变。

## 验收证据

- JVM：`verification-logs/36-unit-day-identity.log`，118 tests completed，4 项为既有 Stage-0 失败，其余通过。
- 真机核心路径：`verification-logs/37f-day-identity-focused.log`，Starting 4 tests / Finished 4 tests / 0 skipped / BUILD SUCCESSFUL。
- 全量真机：`verification-logs/37c-device-day-identity.log` 已启动 60 tests、0 skipped，但在未执行完毕时 `AppScreenTest` instrumentation process crashed；该失败不归因于 AC1-07，不能以全量 0 失败宣称本变更已完全验收。隔离日志 `verification-logs/37e-appscreen-isolated-after-adb-ready.log` 同样记录第一个 AppScreen 用例后 instrumentation process crashed。

## 未纳入

- 时钟大幅前跳的限制与提示：按严格日期前进直接生成，不加阈值。
- 回退提示：静默复用。
- 设备测试宿主崩溃：作为独立的 AppScreen/instrumentation 环境问题处理，不修改 AC1-07 业务实现。
