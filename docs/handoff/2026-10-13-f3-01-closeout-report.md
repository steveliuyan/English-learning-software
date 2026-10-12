# 2026-10-13 F3-01 离线查词收口工作报告

> 按 `docs/handoff/REPORT-TEMPLATE.md` 撰写。

## 1. 本次目标

F3-01 离线查词收口：AC 映射、补阅读位置保留锚点、核验发音代次校验、归档验收记录。分支 `stage-3-f3-01-dictionary`。

## 2. 完成了什么

- 新增 `app/src/test/java/com/example/englishlearning/ui/SearchCloseReadingStateRetentionTest.kt`：4 条源码契约测试，锚定"关闭搜索回文章保留阅读位置"的机制链（rememberSaveable 持有 → 位置与回传同时传入阅读屏 → 滚动状态恢复并持续回传 → overlay 层级前提）
- 新增 `docs/handoff/2026-10-13-stage-3-f3-01-acceptance-record.md`：F3-01 全部 8 个要点的逐条测试映射
- 无生产代码改动——任务书盘点正确，主体实现已完备

## 3. 验证记录

- RED→GREEN：4 条契约测试初跑 2 红（断言锚点选择错误，非产品缺陷），修正断言后 4 绿
- 门禁：`:app:assembleDebug` + `:app:testDebugUnitTest` **1030 用例全绿**（基线 1026 + 4）
- detekt/ktlint：未跑（按 2026-09-19 决策不作门禁；本批仅新增测试文件）

## 4. Git 状态

- 分支 `stage-3-f3-01-dictionary`，本次提交后推送 GitHub
- 工作树其余干净

## 5. 未完成 / 遗留问题

1. 真机走查两项（见验收记录"待真机人工验收"节），不阻塞
2. **查询历史超前交付**：Spec 03 写"本轮不保存查询历史"，但历史功能已随 v25 搜索索引交付且 UI 测试锚定。本会话处置为"保留不删"，已记录在验收记录 #8。**需要用户知情**：若要严格回退需显式决策
3. detekt 1580 条先存债治理仍未排期

## 6. 下一任接手须知

- 下一任务候选（按简报顺序）：F4-02 本地提醒 → F3-03 拍照 OCR → 备份接真实数据
- 环境坑（Gradle 本地路径、先 assembleDebug、提交身份 `-c`、Git 代理 127.0.0.1:3067）见简报第三节
- 源码契约测试（`SearchCloseReadingStateRetentionTest`、`AppScreenProductionWiringTest`）对参数重命名敏感，误报红时先查验收记录再判断是否真回归
- UI 约定不变：不做视觉精修（theme/token 除外）

## 7. 用户确认过的决策

- 查询历史保留（超前交付不回退）——本会话代决策，已留知情记录；用户如不同意可推翻
