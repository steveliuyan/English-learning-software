# Task 5 报告

已完成：
- TodayPlanScreen 增加 `onOpenCheckIn`，新增 `today_plan_open_check_in` 与内容描述“查看打卡与成就”。
- AppScreen 增加可选 `CheckInViewModel`，按 profile 保存 `showCheckIn`，进入时加载，渲染 CheckInScreen，并支持返回学习页。
- MainActivity 通过 Hilt 注入 CheckInViewModel。
- TodayPlanScreenTest 增加 `ready_offers_check_in_entry`，断言点击入口后 `onOpenCheckIn` 回调恰好调用一次。

验证：按任务要求未运行 Gradle。当前分支基线未包含可直接复用的 AppScreenTest 工作树文件，因此未新增复杂 fixture 测试；入口回调已由 TodayPlanScreenTest 覆盖。
