# 阶段 5 / F5-01 重点负向测试审计（SP-A 任务 3）

日期：2026-10-14。范围：`docs/specs/05-release-quality-and-compliance.md` F5-01 列举的十类重点负向场景，逐条映射到 `app/src/test`（JUnit5）、`app/src/androidTest` 与 QA/验收记录；同时复核 AC 追踪矩阵中三条 `[语义候选]`。所有 FQCN 省略前缀 `com.example.englishlearning`。

## 1. 十类场景结论表

| # | 场景 | 结论 | 最强证据 | 残留缺口 |
|---|---|---|---|---|
| 1 | 重复反馈 | ✅ 充分（应用层+DB 层+真机） | `learning.SubmitCardFeedbackUseCaseTest#replaying the same event id neither counts again nor adjusts the interval`；insert 竞争两用例；androidTest `learning.FsrsFeedbackFlowTest`、`learning.RoomLearningEventRepositoryTest` | 无 |
| 2 | 跨日/时区 | ✅ 含「学习日内时区中途改变」 | androidTest `learning.TodayPlanAcrossDaysTest#timezoneChangeWithinOneLearningDayDoesNotCreateASecondPlan`、`#nextLocalDateCreatesNewPlanAndLeavesFirstDaySnapshotIntact`、`#restartOnSameLocalDateReusesTheDay2PlanWithoutThirdRow`；JVM `learning.GetOrCreateTodayPlanUseCaseTest#local date regression reuses the anchored plan without writing a new one` | 无（本地 00:00 边界由 Shanghai +8 跨午夜 instant 间接覆盖；真机 `TimeZone.setDefault` 归设备走查攒批） |
| 3 | 断网 | ✅ 较充分，含真实 socket 映射 | `reading.FetchArticleUseCaseTest#fetchMapsANetworkFailureWithoutStoring`；`ui.ReadingAccessViewModelTest#unreachableFeedShowsExplicitError`、`#fetchFailureShowsExplicitErrorWithoutOpening`；`phototranslation.PhotoTranslationUseCaseTest#networkAndTimeoutFailuresSurfaceAsAiFailuresNotOcrFailures`；`ai.net.UrlConnectionAiHttpTransportTest#mapsAConnectionFailureToNetworkUnavailable`、`#mapsAReadTimeoutToTimedOut`（本地 FakeHttpServer 真实连接失败路径，本审计核验） | 无 |
| 4 | AI 错误不阻塞/保状态 | ✅ 充分 | `ai.AiFailureTest#everyFailureMapsToItsDocumentedAction`、`#cancellationIsNotReportedAsANetworkProblem`；`ui.ReadingAccessViewModelTest#failedGenerationShowsBannerAndKeepsTodayPlanUntouched`（AC2-10：`plans.saveIfAbsentCalls==0`）；`reading.GenerateArticleUseCaseTest#maps401ToUnauthorizedAndStoresNothing`；`ui.PhotoTranslationViewModelTest#translationFailureKeepsEditableOcrTextAndAllowsRetry` | 「AI 失败不回滚已落盘学习事件」为架构性保证：`SubmitCardFeedbackUseCase` 构造不含任何 AI/网络依赖（见 §3-补2） |
| 5 | AI Key 脱敏 | ✅ 日志/导出/文案/DB 四类均有 | `core.logging.SafeLoggerTest`（大小写混淆、Throwable、charArray 均不落日志）；`backup.BackupExportMustNotIncludeCredentialsTest`（逐字节扫描导出包）；`ai.AiFailureTest#userTextCarriesNoUrlKeyOrRawResponse`；`reading.FetchArticleUseCaseTest#fetchNeverStoresTheApiKey`；androidTest `core.security.SecretStoreDeviceTest` | 应用无第三方崩溃报告 SDK（本审计核验 `app/src/main` 无 UncaughtExceptionHandler/Crashlytics 集成），崩溃报告面为空；AC5-05 的「崩溃报告」一翼以「不存在该载体」满足，构建产物扫描由 SP-A 任务 4 `scripts/secret-scan.sh` 承接 |
| 6 | 缓存篡改 fail-closed | ✅ 含真机与导出侧 | androidTest `language.FilePronunciationAudioCacheTest#modifiedAudioIsRejectedAndRemoved`、`#modifiedChecksumIsRejectedAndRemoved`；`language.infrastructure.KokoroAssetMaterializerTest#a digest mismatch fails closed and leaves no half-written asset`；`backup.BackupRestorePipelineTest#resource mismatching the asset record digest is a resource relationship failure`；`wordbook.WordBookPackageIoTest#tamperedPackageIsRejectedWithoutWritingAnything`（本审计核验）；导出侧 `backup.FileOpsBackupMediaGatewayTest`（真实摘要校验→Mismatch）与 `backup.BackupExportCoordinatorTest#media byte mismatch fails with integrity category` | 注意 `language.infrastructure.FileSynthesizedAudioCacheTest` 是「损坏→未命中」尽力而为语义，不作为 fail-closed 证据 |
| 7 | 图片未确认上传 | ✅ 充分（首次/每次/拒绝/换 host 四闸） | `phototranslation.PhotoTranslationUseCaseTest#firstCallDemandsConfirmationBeforeAnyByteLeaves`、`#confirmationIsNeverRememberedAcrossCalls`；`ui.PhotoTranslationViewModelTest#translateFirstDemandsConfirmationAndDenialSendsNoBytes`；`ui.ReadingAccessViewModelTest#generateWithoutConfirmationStopsBeforeAnyRequest`；`reading.GenerateArticleUseCaseTest#asksAgainWhenTheEndpointHostChanged` | 无 |
| 8 | 备份认证失败→正式数据零写入 | ✅ 配对/签名/密码/过期/篡改五类充分 | `backup.BackupRestorePipelineTest`（`#wrong password fails closed…zeroizes input`、`#expired pairing code is rejected`、`#foreign pairing code…mismatch`、`#tampered payload byte fails integrity before staging`、`#insufficient storage space rejects before any scratch write`）；`backup.pairing.PairedMigrationChallengeVerifierTest`（过期边界精确到 expiresAt、7 组绑定变异全拒、异钥签名拒）；`backup.pairing.MigrationChallengeIssuerTest#failed or cancelled authentication records rejection and issues nothing`；`backup.BackupPackageValidatorV3Test`；androidTest `backup.restore.DevicePairedRestoreE2ETest#tamperedPackageIsRejectedWithoutTouchingFormalData`（真机正式 db 逐字节一致） | **「已使用」凭据去重为实现级语义缺口，见 §4-缺口1**：本地一次性配对无 used-nonce 台账，TTL 内同一挑战码可多次签发签名 |
| 9 | 导入原子性 | ✅ Zip Slip/条目数/大小/重复/截断/清理充分 | `backup.BackupPackageValidatorTest`（traversal 三态、record-id 路径段、条目数上限、单条与总解压字节上限、档案字节上限、重名、摘要不符、截断、中央目录伪造）；版本伪造 `BackupRestorePipelineTest#v2 salvage…not restorable`、`BackupPackageValidatorV3Test#downgrading a v3 package label…rejected`；临时区 `backup.FileRestoreStagingWorkspaceTest#clear removes the whole session directory`、`BackupRestorePipelineTest#commit failure clears the scratch session`；`language.infrastructure.TemporaryImageWorkspaceTest`（四种出口零残留+`#purgeAll sweeps crash residue`）；`ui.RestoreNoBypassContractTest` 源码级封死旁路入口 | 「解压比例」无显式比值断言（见 §3-补3 偏离说明）；`backup.restore.SnapshotSwapRestoreCommitGatewayTest#quick check failure after the swap rolls the formal root back byte for byte` 已覆盖回滚逐字节还原 |
| 10 | 其他本地优先负向 | ⚠️ 两项见缺口 | 存储不足：`BackupRestorePipelineTest#insufficient storage…`、`SubmitCardFeedbackUseCaseTest#storage failure is reported and nothing is recorded`、`GetOrCreateTodayPlanUseCaseTest#repository failures map to stable storage unavailable result`；迁移正确性：`core.storage.AppDatabaseMigrationTest#migrateAllHistoricalSchemasWithoutDestructiveFallback` 及 v21→v22/v24→v25 专项 | **缺口2**：「迁移执行失败→可恢复错误呈现」（F5-02）无自动化用例，归 SP-B；**缺口3**：真机磁盘写满 fail-closed 仅注入式假件，归设备走查攒批 |

## 2. 语义候选复核结果（AC 矩阵三条）

- **AC3-02（发音失败给安装/下载/重试提示且词卡可继续）→ 判定：达意，保留。** 证据链：`ui.PronunciationStatusTest`（Unavailable→「请检查语音设置」、Failed→「请重试」两类提示词；不泄露 provider 原因）、androidTest `language.KokoroEngineAvailabilityTest`（端侧模型缺失→不可用态）、`infrastructure.AndroidTextToSpeechProviderTest#initialization failure`。备注：Unavailable 文案是「检查语音设置」的间接指引（设置页含离线语音包「未下载」入口），非逐引擎的「安装离线包」硬编码文案——文案精细化归 UI 重设计轮，不构成验收失败。
- **AC3-03（缓存键同输入命中/改任一入新条/篡改后播放与导出均失败）→ 判定：达意，且证据增强。** `language.domain.PronunciationCacheKeyTest` 四条用例正面命中键语义（text/accent/provider/model/voice/requestVersion 任一变化→新键，键为无明文 SHA-256）；篡改播放失败由真机 `FilePronunciationAudioCacheTest` 两用例覆盖；**本审计补齐导出失败**：`backup.FileOpsBackupMediaGatewayTest`（真实字节摘要/尺寸不符→Mismatch）+ `backup.BackupExportCoordinatorTest#media byte mismatch fails with integrity category`（导出 fail-closed 不静默跳媒体）。矩阵去掉 [语义候选] 标记并补这两条证据。
- **AC4-01（两日历史分别显示计划、完成数、文章状态；次日不改写首日）→ 判定：词卡级历史达意，但整条 AC 存在功能级缺口。** 现有证据（`learning.LearningRecordModelsTest` 分组/去重/边界、`RoomLearningRecordRepositoryTest`、`ui.LearningRecordsViewModelTest`、真机走查 §3.1）只覆盖「按日词级记录、只读不重写」。「按日期展示新词/复习完成数、解锁规则、文章生成/阅读状态」与「累计词数/连续天数/完成率统计」的按日历史统计视图未实现（F4-01 剩余范围），收藏与错词列表亦未检索到实现。详见 §4-缺口1，待用户拍板。

## 3. 审计过程中的补充判定

1. 崩溃报告：全 main 源码无崩溃采集 SDK、无自定义 UncaughtExceptionHandler → AC5-05「崩溃报告扫描」载体不存在；日志脱敏已由 `SafeLoggerTest` 钉死，构建产物由 SP-A 任务 4 secret-scan 扫描。
2. 「反馈先落盘、AI 失败不连带回滚」：`SubmitCardFeedbackUseCase` 及其测试不引用任何 AI/网络端口（`reading`/`ai` 包零 import），跨切面组合用例无生产对象可测；以架构事实 + 第 1、4 类测试共同满足，登记为「架构性保证」而非补测对象。
3. 解压比例：实现采用「单条解压后上限 + 总解压字节上限 + 档案总字节上限」三重绝对封顶，强于比值限制（高比值炸弹必撞绝对上限）。无显式 ratio 用例判定为可接受偏离；建议在阶段 5 spec 或决策文档把「限制解压比」澄清为「以绝对上限实施」。

## 4. 缺口登记与去向

| 缺口 | 级别 | 处置建议 |
|---|---|---|
| 缺口1a：F4-01 按日历史统计（完成数/解锁规则/文章生成与阅读状态/累计/连续天数/完成率）未实现，收藏与错词列表未见实现 → AC4-01 无法诚实关联任何自动化或手工脚本 | 功能级（阶段 4 遗留，SP-A 审计暴露） | **需用户拍板**：补实现（新分支，Spec 已载）/ 裁剪范围并修订阶段 4 Spec 与 AC4-01 / 并入 UI 重设计轮 |
| 缺口1b：AC4-05 列举「已使用」配对凭据应拒绝，但实现无 used-nonce 消费台账，TTL 30 分钟内同一挑战码可重复签发签名 | 安全语义级 | **已修（2026-10-14）**：用户拍板补实现，消费点定在「产包成功即消费」（非签发瞬间，保 D-9 失败重试与 AC4-08 重显）；Room `migration_pairing_ledger` 只存 nonce 的 SHA-256 摘要，读/写 fail-closed。见决策 `2026-10-14-pairing-challenge-consume-on-export.md` 与 AC4-05 矩阵行证据 |
| 缺口2：数据库迁移执行失败→可恢复错误呈现（F5-02）无自动化负向用例 | 测试级 | 归 SP-B（性能与可靠性）任务，含 Room 迁移失败注入或手工验证脚本 |
| 缺口3：真机磁盘写满 fail-closed、真机 `TimeZone.setDefault` 全局切换 | 设备级 | 归设备走查攒批清单 |

## 5. 门禁口径

本文档 + AC 追踪矩阵由 root `verifyAcTraceabilityMatrix` 校验行级非空；「AC5-01 每条 AC 关联至少一项可运行验证」的最终判定以缺口 1/2 关闭为准，矩阵在缺口关闭前不得用于宣称发布就绪。
