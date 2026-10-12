# 阶段 1（词书、背词与间隔复习）验收记录

**日期：** 2026-10-12
**结论：** JVM 层验收全部通过；真机人工走查项标记为待执行，不阻塞阶段 2 开发。
**Spec：** `docs/specs/01-vocabulary-learning-and-review.md`

## 验证命令与结果

- PASS：`:app:assembleDebug`（2026-10-12，Kokoro 边界测试前置）
- PASS：`:app:testDebugUnitTest --no-daemon --no-build-cache` → **1026 用例全绿，0 失败**
- detekt/ktlint：按 `docs/decisions/2026-09-19-static-analysis-and-test-baseline.md` 不作门禁

## 验收标准逐条映射

| AC | 结论 | 证据 |
|---|---|---|
| AC1-01 | ✅ | `GetOrCreateTodayPlanUseCaseTest`（同日复用快照）、`TodayPlanViewModelTest` |
| AC1-02 | ✅ | `UnlockPolicyTest`（零目标视为满足、双条件） |
| AC1-03 | ✅ | `FsrsReviewSchedulerTest`（`again is not later than hard and hard is not later than good`）、`CardFeedbackMappingTest` |
| AC1-04 | ✅ | `SubmitCardFeedbackUseCaseTest`（同事件 ID 重放不重复计数/不改间隔）、`RoomLearningEventRepositoryTest` |
| AC1-05 | ✅ | `GetOrCreateTodayPlanUseCaseTest`（计划锚定当日，新到期词次日纳入） |
| AC1-06 | ✅ | `WordBookProgressTest`（跨词书分别计数）、`WordBookProgressMigrationTest`、**2026-10-12 补** `SelectWordBookAndSetDailyTargetUseCaseTest.switching wordbook overwrites only the active pointer...`（切换不重写词书记录） |
| AC1-07~07e | ✅ | `GetOrCreateTodayPlanUseCaseTest`（12 用例：回退复用、同日复用、严格晚新建、首建、zoneId 保持）+ androidTest `TodayPlanAcrossDaysTest`（5 用例，含 zoneId 不改写） |
| AC1-08~12 | ✅ | androidTest `CardDetailScreenTest`（22 用例）、`CardDetailResourcesTest`/`CardDetailTextTest`、决策 `2026-09-21-card-detail-page.md`、说明图决策 `2026-09-22-card-illustration-assets.md` |
| AC1-13 | ✅ | F1-06 Task 3 交付（LearningSettings 仓储测试，决策文档记录 profile 隔离与持久化） |
| AC1-14/15/17 | ✅ JVM 层 | `LearningRecordModelsTest`（分段降序/段内升序/同计划去重取最早）、`RoomLearningRecordRepositoryTest`、`LearningRecordsViewModelTest`（懒加载/重试/刷新）。DB MD5 字节级对照待真机走查 |
| AC1-16 | ✅ | `reviewStatus` 精确边界测试（等于 now=待复习、+1ms=学习中）、`WordBookWordsViewModelTest.filter narrows groups locally without reloading`、androidTest `WordBookWordsScreenTest`（20 用例） |
| AC1-18 | ✅ | `LearningRecordModelsTest`（缺卡行置灰不可开）+ androidTest `LearningRecordsScreenTest.missingRecordIsVisibleButNotClickable` |
| AC1-19 | ✅ | `CardDetailScreenTest`（无重新学习操作时不显示按钮） |

## 待真机人工验收（不阻塞阶段 2）

1. F1-07 三标签真机走查 + 学习记录只读操作前后 DB/WAL/SHM 三件套 MD5 逐字节对照（AC1-14/15/17 的最终证据，`docs/superpowers/plans/2026-10-04-f1-07-learning-records-completion.md` Step 5）
2. F1-06 三档反馈开关真机路径走查（AC1-08~10 的屏幕截图证据）

## 遗留与风险

- `LearningRecordsScreenTest` 仅 2 个用例，深度依赖 ViewModel 层测试与领域函数测试兜底；真机走查时顺带复核
- detekt 1580 条为全仓先存债（含阶段 1 各功能包报告过的保留项），治理工作包仍未排期
