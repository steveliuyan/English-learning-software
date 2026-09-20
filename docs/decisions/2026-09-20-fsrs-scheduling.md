# F1-05 FSRS 调度决策

## 背景

F1-03 已建立不可变学习事件、幂等事件 ID 和可替换的 `ReviewScheduler` 边界。F1-04 的今日计划完成度只依赖计划快照与学习事件，不能被复习算法的派生状态污染。

## 决定

- 新反馈链路继续通过 `ReviewScheduler` 计算未来 `nextReviewAt`。
- 新增 `FsrsReviewScheduler` 作为本地、确定性、无网络依赖的 FSRS-compatible V1 调度实现。
- FSRS 事件写入 `algorithmVersion=fsrs-v1`、`paramsVersion=fsrs-v1-default`。
- `AppModule` 生产依赖图显式选择 `FsrsReviewScheduler`；测试和兼容调用仍可显式注入 `V1ReviewScheduler`。
- 历史事件不更新、不删除、不重算；事件中的算法版本和参数版本作为审计信息保留。
- `eventId` 唯一约束和现有 append 幂等路径保持不变，重复提交不重复调整状态。
- 不新增 completion_state 表或网络/AI 能力，不改变 F1-04 解锁判定。

## 影响

- F1-03 既有 V1 单测和显式 V1 测试夹具保持兼容。
- F1-04 今日计划仍从计划任务集合与事件派生完成度。
- Room schema 不需迁移，因为事件版本字段已由 F1-03 提供。
- 真实设备 instrumentation 测试已补充，但本轮未执行 connected 任务，以避免在未确认数据可保留时卸载应用。
- 当前 `FsrsReviewScheduler` 是确定性 FSRS-compatible V1 边界实现；更完整的参数化 FSRS 模型可在后续独立迭代，不应回写既有事件。
