# 2026-10-12 阶段 2 收口（F2-04/05/06）工作报告

## 1. 本次目标

按 `docs/handoff/2026-10-12-stage-2-closeout-task.md` 对阶段 2 做验收收口：补缺口测试 → 跑绿 → 逐 AC 归档验收记录，不做新功能。

## 2. 完成了什么

- 核对任务书列出的 3 个缺口，实际结论：
  - **缺口 1（显示模式持久化）部分成立**：ViewModel 层已有完整覆盖（`changingTheModePersistsThePreference`、`changingTheModeDoesNotRewriteTheArticleText` 等 14 用例）；Room 层 `displayMode` 字段已持久化但零测试 → **补 3 个 androidTest 用例**
  - **缺口 2（F2-05 离线降级）不成立**：ReadingAccessViewModelTest（14 用例）+ ReadingAccessScreenTest（13 个 instrumentation）+ FetchArticleUseCaseTest 已全面覆盖重试/检查配置/历史入口/不伪造四要素。任务书盘点时该覆盖尚未提交到盘点者的视野，实际此前的提交已含
  - **缺口 3（来源可追溯）不成立**：`RoomArticleRepositoryTest.everySourceBranchSurvivesARoundTripThroughStorage` + `unknownSourceTypeFailsTheReadInsteadOfBecomingUserImported` 已覆盖
- 新建 `docs/handoff/2026-10-12-stage-2-acceptance-record.md`：AC2-01~10 逐条测试证据映射
- 更新本报告

## 3. 验证记录

- PASS：`:app:assembleDebug :app:testDebugUnitTest` → 1026 用例全绿（新增 0 个 JVM 失败，新增 3 个 androidTest 用例编译通过、待真机执行）
- detekt/ktlint 增量：本任务仅改测试文件，新增 +0 条 JVM 静态检查违规（androidTest 源集不在 detekt 主门禁范围）

## 4. Git 状态

- 分支 `stage-2-f2-closeout`，本次收口提交待推送（见提交哈希）
- 未提交遗留：无（工作树干净后提交）

## 5. 未完成 / 遗留问题

- 3 个新 displayMode 用例需真机 instrumentation 才会执行（该测试类既有 4 个用例同样如此）
- AC2-07 的真机视觉走查（三来源标识截图）未做
- 阶段 1 的两项真机走查仍未做（见阶段 1 验收记录），可与之合并为一次真机 session

## 6. 下一任接手须知

- 阶段 2 收口完成，**阶段 3 可以开始**：推荐顺序 F3-01 离线词典 → F4-02 提醒 → F3-03 拍照 OCR
- 阅读页 `onOpenDictionary` 是占位回调，F3-01 实现时在此接线（ArticleReadingScreen.kt:74）
- 环境坑照旧读简报第三节；新增提醒：androidTest 源集的测试 compile 通过≠已执行，必须真机 `am instrument`
-kokoro 模型二进制在 .gitignore，新 clone 后需按 `docs/decisions/2026-10-06-kokoro-word-pronunciation.md` 恢复，否则 Kokoro 边界测试红

## 7. 用户确认过的决策

- 功能期不做 UI 精修，只允许 theme/token 级修正（UI 重设计整体延后）
- 阶段 1 两项真机走查不阻塞后续阶段开发
