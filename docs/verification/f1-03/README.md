# F1-03 词卡与三档反馈 —— 真机验证记录

**日期：** 2026-09-20（设备本地时间）
**基线提交：** `0b86e9c`（F1-02 今日计划 UI）+ F1-03 未提交工作树
**产物：** `app-debug.apk`，12 987 167 字节，md5 `5d575158b5656ab147dda4c515c22c0d`

## 环境

| 项 | 值 |
|---|---|
| 设备 | `bf353dda` / Xiaomi M2102J2SC |
| Android | 13（API 33） |
| 安装方式 | `adb install -r`（保留应用数据） |
| 屏幕 | 1080×2340 @440dpi，验证期间锁定竖屏 |

## 1. Room v4→v5 迁移在既有数据上成功

安装新包后直接读取设备数据库（`run-as` 拉取），确认迁移在**已有数据的库**上执行且未丢数据：

```
user_version: 5
local_profiles:    1 行  ('default', 'ly', 1789827443589)
word_books:        6 行  (小学/初中/高中/大学英语四级/大学英语六级/考研)
learning_profiles: 1 行  ('default', 'cet4', 10)      -- dailyNewTarget = 10
learning_events:   表已建立，0 行
card_review_states:表已建立，0 行
```

结论：`MIGRATION_4_5` 建表与建索引在真机生效，既有 Profile / 词书 / 学习设置全部保留。

## 2. 当天计划的重建（验证前置步骤）

当天（`2026-09-20`）的计划快照是在**内容源尚为空**时生成的，落库为 `newTarget=0, dueTarget=0`；规格要求计划快照不可变，且本阶段不提供「重建当天计划」入口，因此安装后直接进入学习只会得到「今天暂无学习任务」，无法验证词卡。

为使验证走**应用自身的正常逻辑**，执行了以下受控步骤：

1. `am force-stop` 后完整备份设备数据库（`english-learning.db` / `-wal` / `-shm`）到工作区外目录；
2. 仅删除 `today_plans` 中 `localDate='2026-09-20'` 的一行及其孤立任务行，`learning_profiles`（词书与每日目标）与 `local_profiles` 原样保留；
3. 以 base64 方式推回（`adb shell` 的 stdin 直接管道会损坏二进制，实测导致 371 字节的坏文件；改用 `base64 -d` 后回拉校验 md5 与本地一致：`645996bf76dea58194a04f1f93c5e3e7`）。

应用重新启动后按 `GetOrCreateTodayPlanUseCase` 正常重新生成计划，未改动任何应用代码或数据语义。

## 3. 端到端闭环与观测

| 步骤 | 操作 | 观测结果 | 截图 |
|---|---|---|---|
| 1 | 启动应用进入今日计划 | 大学英语四级 / 计划日期 2026-09-20 / **今日新增 10 词** / 今日复习 0 词 / 今日计划共 10 项 | `01-today-plan-regenerated.png` |
| 2 | 点「开始学习」 | 词卡页：`词卡学习`、`第 1 / 10 张`、**ability / əˈbɪləti / n. / 能力；才能** + 例句；三档按钮 `不认识` / `模糊` / `认识`；**无任何撤销入口** | `02-word-card-first.png` |
| 3 | 点「不认识」 | 推进到 `第 2 / 10 张` = achieve；该卡额外渲染 `词形变化：achieved、achieving、achieves` | `03-submitted-unknown.png` |
| 4 | 点「模糊」 | 推进到 `第 3 / 10 张` = benefit | `04-submitted-fuzzy.png` |
| 5 | 点「认识」 | 推进到 `第 4 / 10 张` = climate；该卡无 `inflections`，**词形变化行不渲染** | `05-submitted-known.png` |
| 6 | `am force-stop` 后重启应用并再进学习 | 直接回到 `第 4 / 10 张` = climate：已提交的 3 张未被重新发放，完成状态由事件日志恢复而非内存 | `06-resume-after-process-kill.png` |
| 7 | 对其余 7 张点「认识」 | `今天的词卡都提交完了`、`共完成 10 / 10 张` | `07-all-cards-done.png` |
| 8 | 点「返回今日计划」 | 回到今日计划页，计划项数仍为 10（快照不变） | `08-back-to-today-plan.png` |

## 4. 落库证据（真机数据库）

```sql
SELECT cardId, feedback, occurredAtEpochMillis, nextReviewAtEpochMillis,
       algorithmVersion, paramsVersion FROM learning_events;
```

结果：**10 条事件，10 个互不相同的 eventId，10 行派生状态**，全部为 `algorithmVersion='v1'`、`paramsVersion='v1'`、`dueBeforeEpochMillis=null`（首次复习）。

三档映射与 V1 间隔实测：

| 卡片 | 提交档位 | 落库 `feedback` | 实测间隔 | 期望 |
|---|---|---|---|---|
| `placeholder:cet4:ability` | 不认识 | `Again` | 600 000 ms = 10 分钟 | 10 分钟 |
| `placeholder:cet4:achieve` | 模糊 | `Hard` | 86 400 000 ms = 1 440 分钟（1 天） | 1 天 |
| `placeholder:cet4:benefit` | 认识 | `Good` | 259 200 000 ms = 4 320 分钟（3 天） | 3 天 |
| 其余 7 张 | 认识 | `Good` | 均 4 320 分钟 | 3 天 |

`today_plan_tasks` 为 10 条 `NEW` 任务，卡 ID 形如 `placeholder:cet4:<lemma>`，与 `PlaceholderWordCardSource` 的 ID 规则一致。

## 5. 覆盖的验收点

- **AC1-03**：三档顺序与映射固定为 `不认识→Again`、`模糊→Hard`、`认识→Good`，间隔单调（10 分钟 < 1 天 < 3 天）——真机实测。
- **每卡一次有效反馈**：10 张卡各产生 1 条事件，事件数与派生状态行数一致，无重复计数。
- **提交成功后完成状态可恢复**：杀进程重启后从事件日志恢复完成集（步骤 6）。
- **事件记录完整性**：算法版本、参数版本、评分、发生时间、调度前后状态（首次 `dueBefore=null`）均已落库。
- **词卡四要素 + 可选字段条件渲染**：lemma / IPA / 词性 / 中文释义恒显示；例句恒显示；词形变化仅在有数据时渲染（步骤 3 vs 步骤 5）。
- **无撤销入口**：词卡页与完成页均无撤销控件。

## 6. 限制与未覆盖项

- **AC1-04（同事件 ID 重放幂等）** 未在真机复现：UI 不提供重放入口，该路径由 `SubmitCardFeedbackUseCaseTest`（含「重放时故意改用不同评分仍不得移动排程」用例）与 `RoomLearningEventRepositoryTest` 的 `INSERT OR IGNORE` 用例在自动化测试中覆盖。
- **AC1-06（同拼写跨词书独立计进度）** 真机只覆盖了同一词书内；跨词书由 `PlaceholderWordCardSourceTest` 与仓储测试覆盖。
- **到期复习路径**：验证时无到期卡（`dueTarget=0`），`dueCardIds` 查询由 `RoomLearningEventRepositoryTest` 覆盖。
- 验证期间为获取稳定坐标曾临时关闭设备自动旋转，验证结束已恢复。
- 当天计划的重建步骤（第 2 节）是**验证前置操作**，不代表产品行为；「重建当天计划」入口属于后续阶段范围。
