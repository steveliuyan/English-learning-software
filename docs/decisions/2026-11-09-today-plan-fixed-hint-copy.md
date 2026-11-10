# 决策：首页补一句「今日计划当天固定」的解释（只加文案，不动规则）

日期：2026-11-09　范围：`ui/TodayPlanScreen.kt`（+ `test/.../ui/TodayPlanFixedHintContractTest.kt`）

## 背景

[2026-11-09-today-plan-same-day-immutable-ruling.md](2026-11-09-today-plan-same-day-immutable-ruling.md) 把「当日计划的
到期集合是不可变快照」定了下来：当天首次进入后，新到期的词不加入计划；已提交反馈后不得静默重建。
规则本身是对的（否则 `dueTarget` 会在用户学到一半时改数，强卡的「x / N」与解锁判据一起撕裂），
但它有一个**用户看不见的前提**：想按「现在真正到期的词」重排，得自己到「学习计划」重选词书并保存——
那是 `GetOrCreateTodayPlanUseCase` 唯一认的显式重建通道（`activeWordBookId` 变化）。

设备走查里这必然表现为困惑：词卡上刚把 `the` 判「不认识」、回执写「约 9 分钟后再问一次」，
回到首页「复习队列」仍是 0 个词到期。用户会以为功能坏了，而实际上什么都没坏——是没有人告诉他规则和出口。

## 决定

在首页「接下来」那张卡（`today_plan_secondary_card`，内含 今日词单 / 复习队列 / 今日文章 / 词书 四条入口）**之后**，
补一枚 Footnote 级补白文案（`testTag("today_plan_fixed_hint")`、`AppType.Footnote`、`colors.text2`）：

　　「今日计划当天固定：新到期的词不会自动加入。要按现在的到期情况重排，到「学习计划」重新选择词书并保存。」

这条文案必须同时说清三件事，缺一件就不对（已写成契约）：

1. **这是规则**（「当天固定」），不是一次故障；
2. **规则的内容**（「新到期的词不会自动加入」）——用户看到「复习队列还是 0」时的唯一解释；
3. **唯一出口**（「到「学习计划」重新选择词书并保存」）——不给出口就是把用户堵在原地。

反面清单（同样写进契约，出现即判红）：不许出现「分钟 / 小时 / 遗忘」——本仓没有遗忘曲线模型。
词卡回执里的「约 9 分钟后再问一次」「约 23 小时后再问一次」是排期器按 FSRS 给的时间，不是可解释的预测；
在首页复述它等于凭空造一个承诺。

## 落点为什么在卡外、卡后

- **卡内不行**：那张软档卡是四条**可点入口**逐条相接的分组（connected 测试按行高之和 = 卡高验证过），
  塞一段静态文本进去，读到的是「第五条入口」，且会破坏「卡高 = 各行之和」这条断言。
- **卡前不行**：卡前是强卡与注脚区（`today_plan_progress` / `today_plan_empty`），
  在 `totalTarget == 0` 与「今天没有待办」分支里会被一起带出来，变成「今天没有待办」下面的一句规则说明，读起来莫名其妙。
- **卡后**：它正好是「接下来」这一组的脚注，与上面的复习队列行同屏可对照；页面本身可滚动（`verticalScroll`），
  设备实测（Xiaomi M2102J2SC · bf353dda）两行 50 字、左边距与卡内文字同一条 20dp 竖线，不与底部导航重叠。

## 被否决的替代方案

1. **做「手动刷新到期集合」按钮**：那是「重建计划」的入口，`AGENTS.md` 只允许在「当天未开始」时由用户显式重建；
   已提交反馈后再给按钮，等于把「不得静默重建」放宽成「不得静默、但可以点一下重建」，规则的实际约束会被绕开。
   （真要给，是产品层面的规则变更，得先改 `AGENTS.md`。）
2. **改首页的「复习队列」行，把「0 个词到期」改成「今天不再补词」之类的状态词**：这一行是原型 `app.js:343` 的落点、
   副标题由 `state.dueTarget` 现算，改文案会把「数字的权威」和状态混在一起；补一句独立脚注更干净。
3. **在词卡/拼写回执里加一句「今天不会再加入队列」**：回执是提交反馈的即时反馈，用户此刻关心的是「下次什么时候问」，
   在那里解释计划不可变会稀释回执。

## 影响面

- `app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`：`is Ready` → 未学完分支内，
  `SoftCard(today_plan_secondary_card)` 收尾大括号之后、分支收尾之前，+7 行注释 + 1 枚 `Text`（现第 517-529 行）。
- `app/src/test/java/com/example/englishlearning/ui/TodayPlanFixedHintContractTest.kt`（新建，3 用例）：形状+内容钉、落点枚数钉、反空转样本。
- 既有四份今日计划契约（Layout / BareRow / EmptyState / NumberAuthority）**不受影响**：它们钉的是既有的
  `todayPlanRemainingMovesBelowCount(` / `PlanRemainingCount(` / 各 `testTag` 的枚数，新增文本不改变任何被钉的数字。
- connected `TodayPlanScreenTest`（28 条）不涉及：唯一「枚数」判据是 `onAllNodes(hasClickAction())` 的闭合集合，
  静态文本没有点击语义、不进该集合。

## 验证

- `:app:testDebugUnitTest --offline` → BUILD SUCCESSFUL；349 个测试类 / 2606 用例，0 失败 0 跳过。
- 设备：首页提示两行渲染、位置与颜色如上；截图 `.workbuddy\home_hint.png`（未入库）。

## 用户确认

2026-11-09（m01571）用户对「在首页复习队列处加一句人话文案」的建议回「**可以**」——**只加文案、不动规则**。
若将来要真正改变「当日计划不可变」，须先推翻 [2026-11-09-today-plan-same-day-immutable-ruling.md](2026-11-09-today-plan-same-day-immutable-ruling.md)，
而不是改这句话。
