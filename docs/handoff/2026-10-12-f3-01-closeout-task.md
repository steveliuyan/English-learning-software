# 会话任务书：F3-01 离线查词收口

**分支：** `stage-3-f3-01-dictionary`（已从 `stage-2-f2-closeout` 创建并推送）
**日期：** 2026-10-12
**前置阅读（按顺序，全部必读）：**

1. `AGENTS.md`
2. `docs/handoff/2026-10-12-session-briefing.md`（环境坑与规则）
3. `docs/specs/03-dictionary-pronunciation-and-photo-translation.md` 的 **F3-01 节**（本任务唯一范围）
4. `docs/handoff/2026-10-12-stage-2-closeout-report.md`（上一会话交接）
5. `docs/handoff/REPORT-TEMPLATE.md`（结束时写报告）

## 任务目标

F3-01 的主体实现已存在且质量不错，本任务是**收口验收 + 补齐最后一处接线**，不是重写。

## 已核实的现状（2026-10-12 盘点）

**已实现（不要重复造）：**

- 搜索域层：`SearchVocabularyUseCase`（规范化/前缀包含打分/上限/跨词书保留）+ 11 个 JVM 用例；`VocabularySearchIndex*` 系列有索引、gram、历史、迁移的完整测试
- 全局搜索页 `GlobalVocabularySearchScreen` + ViewModel，含"仅覆盖已安装词书"提示、失败重试、历史（历史功能属已交付的超前实现，Spec 说"本轮不保存查询历史"——**不要删**，它在 UI 测试里已锚定，见遗留问题）
- 阅读页点词 → `onOpenDictionary(tapped)` 已接 `openSearch(tapped)`（AppScreen.kt ~905），未命中词预填查询已通
- 只读详情复用：搜索结果 → `selectedSearchCard` 详情层，发音状态带请求代次校验

**确认的缺口（本任务要做的）：**

1. **AC 级验收映射缺失**：Spec 03 F3-01 没有像阶段 1/2 那样的编号 AC 列表，需先从 F3-01 的 8 个要点提炼验收点（如：查询规范化、精确>前缀>包含、结果上限、后台+取消+代次、未命中提示、只读详情无学习记录写入、阅读位置与选词状态保留、无 AI 补充），逐条映射到现有测试；真缺的补测试
2. **"关闭搜索后回到原文章并保留阅读位置与选词状态"**：`articleScrollPosition` 机制已存在（AppScreen.kt ~930），但搜索关闭路径是否恢复位置+选词状态无测试锚定，查 `AppScreenTest`/`ArticleReadingScreenTest` 后补
3. **详情关闭后迟到发音状态不污染下一次详情**：确认 `searchPronunciationRequest` 代次校验在搜索→详情→关闭→再开路径上无泄漏（读 AppScreen 相关代码，必要时补 JVM 层状态机测试或 instrumentation）
4. **验收记录**：新建 `docs/handoff/2026-MM-DD-stage-3-f3-01-acceptance-record.md`，格式照抄阶段 2 的

## 工作规则（违反即返工）

- TDD；验收门禁 `:app:assembleDebug` + `:app:testDebugUnitTest` 全绿（当前基线 1026），detekt/ktlint 只报增量
- Gradle 用本机发行版路径（见简报）；提交身份 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`；Git 已配代理直接 push
- **范围红线**：不实现 F3-02 发音 Provider 收口、F3-03 OCR、AI 补充释义、查询历史/收藏的删除或新增；不动 Placeholder 排除逻辑之外的数据源
- UI 只做接线级修正，不做视觉精修（UI 重设计整体延后；theme/token 除外）

## 交付判定

1. F3-01 要点逐条映射测试证据，缺口补齐
2. 全量 JVM 绿 + androidTest 编译通过（同阶段 2 口径）
3. 验收记录 + 按 REPORT-TEMPLATE 写 `docs/handoff/2026-MM-DD-f3-01-closeout-report.md`
4. 提交推送

## 明确排除

F3-02/F3-03、sherpa-onnx/Piper、OCR、AI 补译、查询历史与收藏的取舍变更。
