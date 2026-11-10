# 决策：当日计划的到期集合维持「不可变快照」（不自动补入当日新到期词）

日期：2026-11-09　范围：`learning/GetOrCreateTodayPlanUseCase.kt`（不改代码，仅裁定）

## 背景

设备 E2E（2026-11-09，m00649 第 1 项收尾）暴露一个「像 bug 的行为」：词卡上把 `the` 判「不认识」后，
回执写「约 9 分钟后再问一次」，但用户回到首页时**今日计划的复习队列仍是 0**；要让这个词当天进队列，
必须去「学习计划」换一次词书并保存（`LearningSetupViewModel.save` → 更新 `activeWordBookId`），
再换回来保存，计划才会带着重算后的 `dueCardIds` 重建。

顺带的第二个现象：拼写测验答对后，回执写「约 23 小时后再问一次」，但「复习 0/1」不动——这是
`learning/spelling/RecordSpellingFeedbackUseCase.kt` 只用合成 planId（`"spelling:" + profileId`）移动
`CardReviewState`，并不算当日计划任务，属设计如此。

直接原因在 `GetOrCreateTodayPlanUseCase.kt:32-37`：

```kotlin
if (latest != null) {
    if (localDate.isBefore(latest.plan.localDate)) return latest
    if (localDate == latest.plan.localDate && latest.plan.activeWordBookId == profile.activeWordBookId) {
        return latest
    }
}
```

即：同一天 + 同一词书 → 直接吃缓存，`dueCardIds` 不再重算。重算只在①新的一天、②词书变更时发生；
同日重建（`latest.plan.localDate == localDate`）走 `todayPlanRepository.replaceForDate(plan)`（第 61-62 行）。

「在 `return latest` 之前顺手重算 `dueCardIds` 并 `replaceForDate`」是很诱人的一改，但它**违反产品硬约束**。

## 硬约束原文（`AGENTS.md` ·《学习与解锁规则》）

- 「每日计划在首次进入学习时，按设备本地时区的 `00:00` 与活动词书生成不可变快照：`newTarget`、`dueTarget`、
  任务集合和解锁规则。」
- 「当日计划生成后的新到期词不加入该计划；历史已解锁文章始终可重读。」
- 「设置默认从次日生效；当天未开始时可由用户明确选择重建计划，已提交反馈后不得静默重建。」

## 决定

**维持现状。不实现「当日自动补充新到期词」。**被否决的三种改法：

1. 在 `GetOrCreateTodayPlanUseCase` 里对同日计划重算到期集合并 `replaceForDate`——
   直接违反「不可变快照」，且 `dueTarget` 会在用户操作中途变动，使严格模式的解锁判据
   （`dueDone == dueTarget`）与首页「今日目标 N 个词」在用户眼皮下改数字，学习进度会突然退回未解锁。
2. 只把「到期词」塞进复习队列 UI（不动计划）——队列与 `dueTarget` 脱钩后，「复习 1/1」
   与「队列里还有词」同时出现，等于两套事实。
3. 到点后静默重建计划——命中「已提交反馈后不得静默重建」。

## 用户显式重建路径（正解，已在设备上验证）

「学习计划」里换词书 → 保存（`LearningSetupViewModel.save` → `persistSelection` 写入新的
`activeWordBookId`）→ 下次进入学习时 `GetOrCreateTodayPlanUseCase` 发现 `activeWordBookId` 不匹配 →
按当前时刻重算 `dueCardIds`/`newCardIds` 并（同日）`replaceForDate`。

设备实测链条（Xiaomi M2102J2SC `bf353dda`）：21:13:09 判「不认识」→ 21:24:49 切「初中英语词汇」保存 →
21:25:35 切回「小学英语词汇」保存 ⇒ 首页「复习队列 1 个词到期 / 复习 0/1 / 今日目标 11 个词」；
拼写测验答对后回执「约 23 小时」；21:27:40 再走一轮切换 ⇒ 队列回到 0。

这是「用户明确选择重建计划」的合法通道，不需要改代码——但**当前 UI 没有把它讲明白**：
用户不知道要切词书。

## 后续（需要用户拍板才能做）

- 建议（未实施）：在首页复习队列入口的说明里加一句人话，例如「今日计划是当天固定的；
  若想按现在的到期情况重排，请到「学习计划」重新选择词书并保存」。这是**文案**改动，不改规则。
- 若要真正的「当日实时补充到期词」，必须**先改 `AGENTS.md` 硬约束**，并同时给出解锁规则的重算草案
  （`dueTarget` 变动后严格/宽松模式的完成判据怎么算），否则只是把矛盾从数据层挪到界面层。
- 已知且不改：复习队列浮层是「打开那一刻的快照」，答完题后不会自己刷新；拼写测验不推进当日计划进度。
