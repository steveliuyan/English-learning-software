# F2-01 阅读访问与文章状态验收

## 范围

F2-01 实现本地优先的文章状态与阅读访问，不包含真实 AI 网络请求。覆盖：

- 文章类型：新闻、故事、科普、职场
- 文章长度：短篇、标准、长篇；按词书等级提供默认词数范围，并保留上下 10% 验收容差
- 阅读偏好：按 profile 保存默认文章类型和可选长度档位
- 文章版本：同一 profile、日期、词书、类型、长度键下版本递增，历史版本保留
- TodayPlan 严格/宽松解锁门禁
- 本地阅读入口与离线历史列表

## 提交

- `add7cf4`：文章类型、长度策略与决策
- `a150043`：文章与阅读偏好 Room schema、6→7 migration
- `754f9d2`：文章版本仓储、纯文本校验与离线历史查询
- `6e3a0ff`：TodayPlan 阅读访问门禁
- `7d3c1bf`：阅读访问页、偏好仓储、入口接入
- `4715749`：离线阅读历史页和历史状态接入

## 新增测试验证

### JVM

命令：

```text
:app:testDebugUnitTest --tests ArticleLengthPolicyTest --tests ReadingAccessUseCaseTest --tests ReadingAccessViewModelTest
```

证据：`verification-logs/57-f2-01-new-jvm.log`

结果：`BUILD SUCCESSFUL`。

### 真机

设备：M2102J2SC / Android 13，序列号 `bf353dda`。

前置：锁定竖屏；设置 MIUI instrumentation 所需 appop 10021；使用 `leaveApksInstalledAfterRun=true` 保留应用数据。

命令筛选：

- `AppDatabaseMigrationTest`
- `RoomArticleRepositoryTest`
- `RoomReadingPreferenceRepositoryTest`
- `ReadingAccessScreenTest`
- `ReadingHistoryScreenTest`
- `TodayPlanScreenTest`

证据：`verification-logs/58-f2-01-device-regression.log`

结果：

```text
Starting 27 tests on M2102J2SC - 13
Finished 27 tests on M2102J2SC - 13
BUILD SUCCESSFUL
```

覆盖结果包括：

- 6→7 Room migration
- 文章 version 1/2、历史隔离和危险文本拒绝
- 阅读偏好默认值、保存、覆盖和 profile 隔离
- 锁定状态与四类文章选择
- 离线历史非空和空状态
- 今日计划既有 UI 回归

## 全量回归与已知排除项

命令：`:app:testDebugUnitTest`

证据：`verification-logs/56-f2-01-full-jvm.log`

结果：`149 tests completed, 4 failed`。4 个失败均为阶段 0 既有测试：

- `LogicalSnapshotSecurityTest` 2 项
- `ProviderContractTest` 1 项
- `ThirdPartyNoticesTest` 1 项

这些失败不引用或执行 F2-01 新增代码，且新增长度、门禁、ViewModel 测试均已独立通过。它们保留为阶段 0 合规收尾事项，不在本阶段修改范围内。

## 范围边界

- F2-01 不发起 HTTP/HTTPS AI 请求。
- F2-01 不保存或读取 API Key。
- 当前历史页展示本地文章摘要列表，不包含全文详情路由或分页。
- “换一篇”次数与文章生成入口属于后续生成用例；本阶段只完成版本化仓储基础和本地访问。
- 未完成的 Stage-0 合规测试不影响 F2-01 新增功能测试，但在发布前必须单独收口。
