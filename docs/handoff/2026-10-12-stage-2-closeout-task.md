# 会话任务书：阶段 2 收口（F2-04 / F2-05 / F2-06）

**分支：** `stage-2-f2-closeout`（已从 `stage-1-f1-05-fsrs-scheduling` 创建并推送）
**日期：** 2026-10-12
**前置阅读（按顺序，全部必读）：**

1. `AGENTS.md` — 强制产品约束与工作方式
2. `docs/handoff/2026-10-12-session-briefing.md` — 环境坑（Gradle 路径、Git 代理、提交身份）与项目现状
3. `docs/specs/02-reading-and-ai-content.md` — 本任务的范围定义
4. `docs/handoff/REPORT-TEMPLATE.md` — 结束时按此写报告

## 任务目标

阶段 2 的功能代码已基本就位（阅读页、高亮、译文切换、多来源封闭类型、抓取白名单、失败映射），本任务**不做新功能**，只做验收收口：补缺口测试 → 跑绿 → 归档验收记录。范围严格限定在 F2-04/05/06，不碰阶段 3+。

## 已核实的现状（2026-10-12 盘点）

**已实现（不要重复造）：**

- `ArticleReadingScreen.kt`：英文原文、译文展开/收起、双语模式 Chip、未覆盖词列表、`onOpenDictionary` 回调（占位入口）、完成阅读幂等按钮
- `Article.kt`：`chineseText` 随文章缓存；`ArticleSource` 封闭类型（AI/抓取带许可署名/用户导入），编译期拒绝缺许可的抓取来源
- `ArticleHighlightPolicy.kt`：高亮从 coveredLemmas 读时派生
- 测试：`FetchArticleUseCaseTest`（14 用例，含缓存复用、网络失败映射、不存 Key）、`ArticleReadingScreenTest`（22 用例）、`ArticleReadingViewModelTest`（14 用例，含失败重试）

**已确认的缺口（本任务要补的）：**

1. **F2-04 显示模式持久化**：`ArticleDisplayMode` 有三个模式，但"切换只改展示不改写文章"和"默认模式可设置"缺验收测试（`ReadingPreferenceRepository` 是否已覆盖三种模式的读写待查证，先查 `RoomReadingPreferenceRepository` 测试）
2. **F2-05 离线降级验收**：Spec 要求"无网络/未配 Key/AI 失败时显示重试/检查配置/历史文章入口，不伪造文章"。现有测试只有 `completionFailureLeavesTheStateUnchangedForRetry` 和 fetch 层的网络失败映射，**ViewModel/Screen 层的离线状态展示（错误条 + 重试 + 历史入口）没有直接测试**
3. **F2-06 来源元数据验收**：`ArticleSourceRegistryTest` 覆盖了白名单，但"历史文章可追溯来源"（从仓储读出后 source 类型保留）缺断言，查 `RoomArticleRepository` 测试后补
4. **逐条 AC 映射记录**：Spec 02 的验收标准尚未像阶段 1 那样逐条归档（参照 `docs/handoff/2026-10-12-stage-1-acceptance-record.md` 的格式）

## 工作规则（违反即返工）

- TDD：先写测试（确认 RED）→ 最小实现（GREEN）。纯补测试的项先证明现有实现能跑绿；若跑不绿才是缺陷，修复它
- 验收门禁：`:app:testDebugUnitTest` 全绿（基线 1026 + 你新增的），detekt/ktlint 只报增量
- 验证顺序：先 `:app:assembleDebug` 再 `:app:testDebugUnitTest`（Kokoro 边界测试需要 APK 产物）
- Gradle 命令用本机发行版路径（见简报），直接 `gradlew` 会因网络失败
- 范围变化必须先改 Spec 再动代码；新决策写 `docs/decisions/2026-MM-DD-<topic>.md`
- 注意：`onOpenDictionary` 目前是占位回调（F3-01 离线词典未实现），F2-04 的"点击词离线词典入口"只验收入口存在与点击跳转意图，不验收词典内容

## 交付判定

1. F2-04/05/06 每条验收标准有测试映射，缺口 1~3 补齐
2. `:app:testDebugUnitTest` 全绿（报告具体数字）
3. 创建 `docs/handoff/2026-MM-DD-stage-2-acceptance-record.md`，格式照抄阶段 1 的验收记录
4. 按 REPORT-TEMPLATE 写工作报告
5. 提交并推送（Git 已配代理，直接 push 即可；用户名邮箱用 `-c` 参数，见简报）

## 明确排除

- F3-01 离线词典实现（下一任务）
- 阶段 3/4/5 任何内容
- UI 视觉精修（用户将在功能全部完成后统一做 UI 重设计；本次只允许 theme/token 方式的代码级修正）
