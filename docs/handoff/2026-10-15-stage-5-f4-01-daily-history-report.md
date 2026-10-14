# F4-01 每日历史、收藏与删除语义 工作报告（阶段 5 · SP-A 尾段）

## 1. 本次目标

关闭 negative-test-audit 缺口1a：在分支 `stage-5-f4-01-daily-history-and-collections` 上补齐阶段 4 Spec F4-01 的四个已批准档位——①核心按日统计+按日详展 ②收藏词+错词列表 ③文章版本管理 UI ④删除级联影响确认（含修正「删词书物理删历史」的 Spec 违规），并使 AC4-01/AC4-03 矩阵证据诚实可关联。

## 2. 完成了什么

按提交序（均在本分支）：

- `f3a5aa5` 决策文档：历史统计口径与删除语义八条锁死（后补 §9「删除全部学习数据」与搜索历史清空口径）。
- `a6cc567` 按日详展与累计/连续统计端口：`InternalLearningStatsDao` 真 SQL、`LearningStatsRepository` 端口、UnlockPolicy 权威判定（`RoomLearningStatsRepositoryTest`、`LearningHistoryDomainTest`）。
- `d053bbf` CheckIn 三卡：累计学习/连续天数/近 7 日完成率，任一不可得整页降级不展示半真数据。
- `098a10a` 学习记录 HISTORY 页签按日详展 + `DailyHistoryAcrossDaysTest`（connected：第二天零改写第一天）。
- `0139c04` Room v31：词书软删标记 + `favorite_words` 表。
- `80af04b` 决策⑦收藏全链路（词卡星标、收藏页、仓储）；`a8e420e` 决策⑤错词本（`learning_events` 派生，无新表——与 Spec 原文「错词表」的记录偏离见决策文档）。
- `139b71c` Task9 收藏入备份白名单（17→18 源）。
- `fa1e830` Task10a 文章版本管理：版本历史可见、删除唯一版本拒删、busy 闩锁、删除成功重放历史加载（`ArticleVersionViewModelTest` 7 用例、`RoomArticleVersionRepositoryTest`、connected `RoomArticleVersionFlowTest`）。
- `0cedd38` Task10b-1 删词书确认框语义反转：明示保留学习历史与文章，确认框数字来自真 SQL（`RoomWordBookDeletionPreviewTest`）。
- `fcb254f` Task10b-2 清空搜索历史：真实级联条数走 `SELECT COUNT(*)`（不是列表 limit 条数），未确认零写入，读数失败仍开双确认框（诚实文案）。
- `50eee91` Task10b-3 「删除全部学习数据」入口（决策 §9）：五类学习事实按档案清（事件/调度态整表/计划+任务/阅读完成/迁移审计），保留文章、收藏、词书、搜索历史与设置；预览→二次确认→删除三步状态机，回执数字=同一批真数；成功后重放今日页/打卡/学习记录/错词加载；SettingsScreen 新增「危险操作」分组与 `Danger` 域色 token。
- 本提交：AC 矩阵 AC4-01/AC4-03 证据行回填、negative-test-audit 缺口1a→已修、本报告。

## 3. 验证记录

- 三任务门控（顺序固定）：`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache` → **BUILD SUCCESSFUL**（`50eee91` 工作区，即含全部 UI 接线）。
- JVM 单测：**1388 用例 / 0 失败**（XML 计数）。基线：AC4-05 收口时 1332 → 现 1388（+56，只增不减成立）。
- `verifyAcTraceabilityMatrix` + 矩阵改动后复跑 `testDebugUnitTest`：见本报告提交前的后台核验输出（矩阵覆盖全部 AC id、零空证据）。
- connected（emulator-5576，`adb install -r` + `am instrument`，未用 connectedDebugAndroidTest、设备数据零损失），本轮实跑并全 OK：
  - `RoomLearningDataDeletionTest`（删五类、保留面、别档案零牵连）
  - `RoomVocabularySearchHistoryRepositoryTest`（5 用例，含 count 真实总行数超展示上限）
  - `RoomWordBookDeletionPreviewTest`、`RoomFavoritesFlowTest`、`RoomWrongWordsDerivationTest`、`DailyHistoryAcrossDaysTest`、`reading.RoomArticleVersionFlowTest`
- TDD 证据：各 Task 均先落 RED（未实现端口/未定义类导致编译失败或断言失败），再最小实现转 GREEN；VM 状态机用 JVM 假仓储记数钉「确认前零写入」。
- secret-scan：`bash scripts/secret-scan.sh` 结果见推送前核验（本轮新增文件无密钥/挑战码/绝对隐私内容）。

## 4. Git 状态

- 分支 `stage-5-f4-01-daily-history-and-collections`，代码 tip `50eee91`，文档收口提交 `75b4bcf`。推送后 `ls-remote` 实测远端 tip = `75b4bcf61bc150f56acc01706df8839d499a111c`，与本地 HEAD 一致，备份成立（其后的报告修订提交只动本节，不再另记）。
- 工作区在每次提交前 `git status` 复核：仅本功能文件，无临时产物、无密钥类文件混入；逐文件 `git add`。
- 推送后远端 tip 以 `ls-remote` 实测核验值为准（推送命令与核验结果在会话收口回复中给出；未核验不宣称备份完成）。

## 5. 未完成 / 遗留问题

- 「删除全部学习数据」「清空搜索历史」「删词书」三个新确认框的真机 TalkBack/小屏走查未做 → 归设备 QA 攒批清单（含新 `settings_delete_all_learning_data`、`delete_all_learning_data_confirm/cancel`、`search_history_clear_*` testTag）。
- 错词本口径偏离（事件派生、无独立错词表）已记录在决策文档，若验收要求物理表需另行裁决。
- `card_review_states` 整表清绑定「单档案设备」前提（DAO 契约注释+测试钉死）；多档案功能上线时必须改造。
- detekt/ktlint 存量债不变（门禁口径见决策 2026-09-19），本轮只报增量：新增文件均按现有风格。
- 阶段 5 剩余：SP-B 性能与可靠性 → SP-C/D/E（用户已定序：SP-B 先行）。

## 6. 下一任接手须知

- 基线：本分支合入 main 前，远端 tip 见 §4 核验值；门控与 connected 复跑命令照 `android-repo-session-handoff` skill。
- F4-01 的口径唯一来源是 `docs/decisions/2026-10-14-f4-01-history-stats-and-deletion-semantics.md`（§1–§9），改删除/统计语义先改决策再改码。
- 下一步按计划先 SP-B（`docs/superpowers/plans/2026-10-14-stage-5-release-quality-and-compliance.md` 性能与可靠性段），含 negative-test-audit 缺口2（迁移失败注入）。
- 新对话框宿主模式：确认框与刷新重放都挂在 AppScreen（`versionDeletions`/`dataDeletions` 计数事件 + `LaunchedEffect`），加同类入口照 `LearningDataDeletionViewModel` 三步状态机。

## 7. 用户确认过的决策

- 档位①–④全量补实现（否决裁剪 Spec / 并入 UI 重设计轮两个备选）。
- 删除语义反转：删词书/删学习数据保留学习历史与文章；「删除全部学习数据」不含收藏、文章、词书、搜索历史、设置与 AI 配置（决策 §9）。
- 错词本改为 `learning_events` 派生（无新表）。
- 搜索历史级联条数用 `SELECT COUNT(*)` 真实总数，而非展示 limit 内条数。
- 未新增账号、云同步、订阅、广告或任何 AI 请求覆盖能力。
