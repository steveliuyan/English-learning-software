# 2026-10-15 阶段 5 SP-B（性能与可靠性）工作报告

## 1. 本次目标

在分支 `stage-5-sp-b-performance-and-reliability`（自 main tip `dca576e` 切出）执行
`docs/superpowers/plans/2026-10-15-stage-5-sp-b-performance-and-reliability.md` 的 Task 1–8：
关闭 F5-02 三个真缺口——saveNewVersion 并发竞态「损坏文章版本」、迁移失败零呈现（负向审计缺口2）、
六项性能无实测阈值——并落 AC5-02 离线验收自动化子集+设备脚本，收口推送。

## 2. 完成了什么

- **Task 1**：语义决策 `docs/decisions/2026-10-15-sp-b-performance-and-reliability-semantics.md`（阈值口径=emulator-5574 中位数×2 取整、事务+有界重试、NeedsRecovery 禁 destructive、AC5-02 双层口径、其余负项归属）。提交 `a729614`（含计划）。
- **Task 2**：`RoomArticleRepository.saveNewVersion` 读 maxVersion→insert 收进 `database.withTransaction`，UNIQUE 冲突有界重试 ≤3（K2 的 `while(true)` 推断问题拆出 `insertNextVersion`）。androidTest `RoomArticleVersionRaceTest` 真库 8 路并发。提交 `4aa7a0f`。
- **Task 3**：TimedOut/取消零半写审计——`GenerateArticleUseCaseTest#rethrowsCancellationWithoutStoring`、`SubmitCardFeedbackUseCaseTest`（eventId+insertEventIfAbsent）既有覆盖充分，零缺口，按规格不造用例（记录于提交信息）。
- **Task 4–5**：androidTest `perf/`（SeedData 夹具 + `PerfSixMetricsInstrumentedTest` 六项）+ `scripts/perf-baseline.sh` + `docs/verification/perf-baseline-2026-10.md`（实测中位数与回填阈值）+ `SeedData.thresholdMillis` 回填断言。提交 `d91f3b6`。
- **Task 6**：`DatabaseStartup.open`（build+首次打开，失败绝不删库/绝不 destructive fallback）→ `RoomDatabaseStartupProbe` 端口 → `AppUiState.NeedsRecovery` → `RecoveryScreen`（决策 §3 固定文案 + `recovery_retry` 唯一入口、无清除数据按钮；重试=探针重开+reload，叠加 `activity.recreate()`）→ `AppModule.provideDatabase` 失败呈现化。JVM `AppViewModelRecoveryTest` 3 用例；androidTest `MigrationFailureRecoveryTest`（user_version=999999 注入未来版本失败：Result.failure+文件存活+哨兵数据可复读）与 `RecoveryScreenTest`。提交 `8b1e44e`。
- **Task 7**：androidTest `OfflineCorePathsInstrumentedTest` 4 用例（离线反馈重放/到期查询、缓存文章回读、离线词条检索、AI 不可达→`Failed(NetworkUnavailable)` 零写入——规格别名「AiUnavailable」的真实落点在测试 KDoc 写明）；`docs/verification/ac5-02-offline-script.md`（飞行模式手工六条链路+B4 证据登记+B5/T2 TODO-USER）；负向审计缺口2→已修；矩阵新增「阶段 5 AC」节 AC5-02 证据行（校验器 `AC[0-4]` 口径不变）。提交 `d189403`。

## 3. 验证记录

- 门禁（本机 Gradle 8.13，`assembleDebug → testDebugUnitTest → compileDebugAndroidTestKotlin`，`--no-daemon --no-build-cache`）：**BUILD SUCCESSFUL**；单测 **1391** 用例 0 失败（基线 1388 → +3，AppViewModelRecoveryTest；只增不减成立）。
- `verifyAcTraceabilityMatrix`：51 ids 全覆盖证据非空；`scripts/secret-scan.sh`：exit 0 零命中。
- connected 实跑清单（`adb install -r` + `am instrument`，数据零清空）：
  - emulator-5576：`RoomArticleVersionRaceTest`（先 RED 复现 UNIQUE 碰撞，事务化后两次 GREEN）；`MigrationFailureRecoveryTest`+`RecoveryScreenTest` OK (3 tests)；`OfflineCorePathsInstrumentedTest` OK (4 tests)。
  - emulator-5574（基线机）：`PerfSixMetricsInstrumentedTest` 6/6 达标。实测中位数→阈值：冷启动 2277→5000ms、plan_read 10→50、card_paging 4→50、word_lookup 8→50、event_append_100 324→650、backup_validate_500 230→500、restore_write_500 156→350。
- **AppScreenTest 既有 flaky（非本次回归，有对照证据）**：整类 37 用例中 8 个 back/导航族失败（ComposeTimeoutException、`settings_screen`/`today_plan_summary` 节点缺失）。用 `git stash` 回退到分支基线 `d91f3b6` 重构建重跑，**同样 8 个用例同名失败** → 判定为设备环境不稳定，归设备走查攒批，不阻塞 SP-B 门禁（门禁=单测）。

## 4. Git 状态

- 分支：`stage-5-sp-b-performance-and-reliability`，本地 tip `d189403`（提交链 a729614 → 4aa7a0f → d91f3b6 → 8b1e44e → d189403，基线 main `dca576e`）。
- 远端：推送后以 `ls-remote` 核验，远端 tip 哈希=本地 HEAD（见推送记录，代理 127.0.0.1:3067 本弧不可用，走 `-c http.proxy= -c https.proxy=` 直连）。
- 工作区：收口报告提交后干净；未混入密钥/QA 临时产物。

## 5. 未完成 / 遗留问题

- **TODO-USER（真机侧）**：bf353dda 性能复测（阈值表已留复测命令）；ac5-02 脚本的飞行模式手工六条链路执行与 B4 恢复页拒收 `.elpbak` 一次手工核；B5 真实 AI Profile 端到端；T2 内容级抓包（需用户配置与 CA 环境，未做不写通过）。
- AppScreenTest 8 个 back/导航族 flaky → 设备走查攒批清单（有基线对照证据，非回归）。
- `RoomDatabaseStartupProbe.retry()/record()` 依赖 Android Context，无 JVM 直测；由 `MigrationFailureRecoveryTest`（真实 open 失败面）+ `AppViewModelRecoveryTest`（状态机语义）合围覆盖，报告如实登记。
- F5-02 其余负项（模型下载中断、音频资源损坏）按决策 §5 引用阶段 3 既有覆盖；存储空间不足真机 fail-closed 维持攒批裁决。
- detekt/ktlint 存量债不属门禁（2026-09-19 基线裁决）。

## 6. 下一任接手须知

- 分支 tip `d189403` 已推送即备份完成；是否 FF 合 main 听用户拍板（惯例：验收后合）。
- SP-C/D/E（无障碍/隐私合规/许可证等，见阶段 5 计划母文档）按母计划顺序接续；AC5 证据行统一追加到矩阵「阶段 5 AC」节（校验器不识别 AC5，勿改动 `AC[0-4]` 正则口径除非同步改 AcIdPatternTest）。
- 阈值回填规则：只准「实测→中位数×2→回填」，禁止预填；换基线设备需整表重测。
- 迁移失败红线复述：失败路径绝不删库、绝不 `fallbackToDestructiveMigration`、RecoveryScreen 无清数据入口——任何后续改动必须保持 `MigrationFailureRecoveryTest`/`RecoveryScreenTest` 绿。

## 7. 用户确认过的决策

- 分支策略：F4-01 先 FF 合 main，再切 SP-B（用户选择「先 FF 合回 main，再切 SP-B（推荐）」）。
- 「继续开发」指令覆盖 SP-B 全计划；语义细节（阈值口径、事务+重试、NeedsRecovery 禁 destructive、AC5-02 双层口径、B5/T2 TODO-USER 不伪造）均先行写入决策文档按计划执行，无临场扩权：未新增账号/云同步/订阅/广告/远程代码执行，API Key 未入库入日志入报告。
- 偏离记录：Task 6 重试在决策 §3「清错误态+activity.recreate()」基础上增加 `probe.retry()` 主动重开（Room 失败实例按惰性访问自然恢复，重开校验实例即关，无状态泄漏）；recreate 保留。AppScreenTest flaky 以基线对照法判定非回归，未擅自改测试断言。
