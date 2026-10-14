# AC 追踪矩阵（阶段 0–4 → 证据）

> AC5-01：每行必须有非空「证据」列（自动化测试 FQCN#method、docs/verification 或 docs/handoff 记录路径）。
> 由 root 任务 `verifyAcTraceabilityMatrix` 校验：specs 00–04 中每个 AC 编号在此出现且证据非空，否则发布门禁失败。

| AC | 摘要 | 验证方式 | 证据 |
|---|---|---|---|
| AC0-01 | 应用离线启动后可创建本地 Profile，重启后仍能读取，且日志中无敏感字段。 | 自动化+设备 | ui.AppViewModelTest#create profile persists and reloads without network; core.logging.SafeLoggerTest; androidTest profile.RoomLocalProfileRepositoryTest |
| AC0-02 | AI Profile 的 Key 只可经 SecretStore 读取；导出 R… | 自动化+设备 | ai.AiProfileSecretUseCaseTest; core.security.AndroidKeyStoreSecretStoreTest; androidTest core.security.SecretStoreDeviceTest; backup.BackupExportMustNotIncludeCredentialsTest |
| AC0-03 | 任一 UI ViewModel 不能直接引用 HTTP 客户端、SecretSt… | 自动化 | ui.AppViewModelTest（禁用引用扫描）; contracts.ProviderContractTest; contracts.LogicalSnapshotSecurityTest |
| AC0-04 | 媒体文件被删除或哈希不一致时，资源读取返回“不可用/可重建”，不导致应用崩溃。 | 自动化 | core.storage.PrivateMediaStoreTest; core.storage.AssetRepositoryTest; language.infrastructure.KokoroAssetMaterializerTest#digest mismatch fails closed |
| AC0-05 | 第三方台账中每一项都具备许可证、用途和数据流字段；缺字段时发布检查失败。 | 自动化 | notices.ThirdPartyNoticesTest; root 任务 verifyThirdPartyNotices（check 依赖） |
| AC1-01 | 首次选词书、设置每日目标后，离线生成今日计划；退出重开后计划及进度不变。 | 自动化 | learning.GetOrCreateTodayPlanUseCaseTest; ui.TodayPlanViewModelTest；docs/handoff/2026-10-12-stage-1-acceptance-record.md |
| AC1-02 | 今日 `newTarget=10,dueTarget=0` 时，严格模式完成 1… | 自动化 | learning.UnlockPolicyTest（零目标视为满足、严格双条件） |
| AC1-03 | 同一初始进度提交 Again、Hard、Good，生成的下一复习时间满足 Aga… | 自动化 | learning.FsrsReviewSchedulerTest; learning.CardFeedbackMappingTest; androidTest learning.FsrsFeedbackFlowTest; docs/verification/f1-03/README.md §AC1-03 |
| AC1-04 | 同一事件 ID 重放两次，新增/复习完成数和 `nextReviewAt` 与单… | 自动化 | learning.SubmitCardFeedbackUseCaseTest（同事件重放）; androidTest learning.RoomLearningEventRepositoryTest; docs/verification/f1-03/README.md §AC1-04 |
| AC1-05 | 计划生成后又有单词到期时，该单词不进入已有计划；次日计划才纳入。 | 自动化 | learning.GetOrCreateTodayPlanUseCaseTest（计划锚定当日，新到期次日纳入） |
| AC1-06 | 切换到另一词书后原词书进度保留；相同拼写但不同词书的单词默认分别计进各自进度。 | 自动化 | learning.SelectWordBookAndSetDailyTargetUseCaseTest（源码注释 AC1-06）; learning.WordBookProgressTest; learning.WordBookProgressMigrationTest |
| AC1-07 | 模拟跨日、时区改变和应用重启，不产生重复新词计划或丢失已提交的反馈。 | 自动化+设备 | androidTest learning.TodayPlanAcrossDaysTest（KDoc AC1-07）; learning.GetOrCreateTodayPlanUseCaseTest; docs/verification/f1-05-device/README.md |
| AC1-08 | 提交「模糊」反馈且设置中「模糊」开关为开时，进入当前词卡详情页；详情页展示该词的… | 自动化 | androidTest ui.CardDetailScreenTest; ui.WordCardViewModelTest#submitting…opens detail（按开关）; docs/verification/f1-06/README.md |
| AC1-09 | 设置默认值为认识 = 关、模糊 = 开、忘记了 = 开；提交「认识」后不进入详情… | 自动化 | ui.WordCardViewModelTest#known 不开详情/fuzzy、unknown 默认开（开关默认 关/开/开） |
| AC1-10 | 在设置页关闭某一档开关后，该档反馈提交后不再进入详情页，改为直接推进；改动立即生… | 自动化 | ui.WordCardViewModelTest#known opens detail only when enabled; 阶段1记录（androidTest CardDetailScreenTest 22 用例） |
| AC1-11 | 进入并退出详情页不改变当日完成度与解锁判定——详情页进出前后「新增 x/y、复习… | 自动化 | ui.WordCardViewModelTest#clearing the detail page does not append a learning event; docs/verification/f1-06/README.md §AC1-11 |
| AC1-12 | 对无说明图资源的词条、以及无数据的任一内容模块，详情页隐藏对应区域，不出现空标题… | 自动化+设备 | androidTest ui.CardDetailScreenTest#illustrationHiddenForUnknownLemma; ui.CardDetailResourcesTest; ui.CardDetailTextTest; docs/verification/f1-06/README.md |
| AC1-13 | 三个开关的取值在同一 profile 内持久化，应用重启后保持；不同 profi… | 自动化 | ui.WordCardViewModelTest（按 profileId 读写设置）; androidTest ui.SettingsScreenTest; 阶段1记录 F1-06 Task3 |
| AC1-14 | 完成两天学习后，「历史记录」分两段显示两个学习日，最新在前，每段词数与该日提交数… | 自动化 | learning.LearningRecordModelsTest#history groups dates descending; learning.RoomLearningRecordRepositoryTest; ui.LearningRecordsViewModelTest; batched-device-qa-report §3.1 |
| AC1-15 | 「今日已学」只包含今日计划内已提交的词，按提交先后排列，反馈与提交时一致。 | 自动化 | learning.LearningRecordModelsTest（段内升序/同计划去重）; ui.LearningRecordsViewModelTest; batched-device-qa-report §3.1 |
| AC1-16 | 「全部单词」的状态满足：无复习状态为未学；下次复习时间等于当前时刻为待复习；晚于… | 自动化 | learning.LearningRecordModelsTest#review status exact now boundary; ui.WordBookWordsViewModelTest#filter narrows groups; androidTest ui.WordBookWordsScreenTest |
| AC1-17 | 从三个标签任一行打开详情再返回，学习事件表、复习状态表与当日完成度、解锁状态均不… | 自动化 | learning.LearningRecordModelsTest#same plan and card keeps earliest event; ui.LearningRecordsViewModelTest（懒加载不重写） |
| AC1-18 | 学习记录中词卡已无法解析的行仍显示单词部分，置灰且不可打开；应用不崩溃。 | 自动化 | learning.LearningRecordModelsTest#missing card fallback…cannot open; androidTest ui.LearningRecordsScreenTest#missingRecordIsVisibleButNotClickable |
| AC1-19 | 详情页在未提供重新学习操作时不显示「重新学习」按钮。 | 自动化 | androidTest ui.CardDetailScreenTest（无重新学习不显示按钮）; 阶段3-f3-01验收记录 §5（只读锚定） |
| AC1-20 | 词卡正面在揭晓前不显示中文释义与词形变化，只显示单词、IPA、词性（及有资源时的… | 自动化+设备 | ui.WordCardViewModelTest#advancing/re-entering hides meaning; wordcard-reveal-acceptance-record §3（真机 38/38） |
| AC1-21 | 只点击「显示释义」而不提交反馈时，学习事件表不新增记录，当日「新增 x/y、复习… | 自动化 | ui.WordCardViewModelTest#revealing records no event and keeps card pending |
| AC1-22 | 新增词卡与到期复习卡均按相同的回忆-揭晓-评分流程运行；揭晓前后三档评分按钮均可… | 自动化+设备 | ui.WordCardViewModelTest#three tiers forwarded as fixed ratings; wordcard-reveal-acceptance-record（真机 38/38）; batched-device-qa-report §3.1 |
| AC2-01 | 严格模式未满足 TodayPlan 解锁条件时，文章生成入口不可用并显示新增/复… | 自动化 | ui.ReadingAccessViewModelTest#lockedProfile…/strictLocked…; androidTest ui.ReadingAccessScreenTest#lockedState…; docs/verification/f2-03-04/README.md |
| AC2-02 | AI 生成成功后文章含标题、英文、中文翻译、派生高亮与未覆盖词；文本不执行脚本或… | 自动化 | reading.GenerateArticleUseCaseTest; reading.ArticleHighlightPolicyTest; androidTest ui.ArticleReadingScreenTest#scriptTextIsRenderedAsPlainText |
| AC2-03 | 同日同类型同长度重复进入复用同一文章；用户触发换一篇后生成新版本，旧版本仍可读。 | 自动化 | ui.ArticleReadingViewModelTest#reusesTheStoredArticle…/#regeneratingSavesANewVersion…；阶段2验收记录 |
| AC2-04 | 未配置 Key、HTTP Endpoint、私网 Endpoint、401、42… | 自动化 | ai.AiFailureTest; reading.GenerateArticleUseCaseTest（NotConfigured）; ui.ReadingAccessViewModelTest#failedGenerationKeepsTodayPlan; docs/verification/f2-02/README.md |
| AC2-05 | 断网后已缓存文章可阅读；不可产出新文章且不会损坏今日计划。 | 自动化 | ui.ReadingAccessViewModelTest#existingTodayArticleIsExposed; reading.FetchArticleUseCaseTest#fetchMapsANetworkFailure; androidTest ui.ReadingAccessScreenTest 离线态 |
| AC2-06 | 备份逻辑快照与日志中只保存 Profile 非敏感字段和脱敏诊断，不含 API … | 自动化 | ai.AiProfileSecretUseCaseTest; reading.FetchArticleUseCaseTest#fetchNeverStoresTheApiKey; ai.AiFailureTest#userTextCarriesNoUrlKeyOrRawResponse; backup.BackupExportMustNotIncludeCredentialsTest |
| AC2-07 | 阅读页与历史列表显示来源标识。AI 生成显示模型名，外刊抓取显示来源名称、署名与… | 设备 | androidTest ui.ArticleReadingScreenTest（5 用例）；阶段2记录列真机截图走查待办（SP-C 闭环） |
| AC2-08 | 抓取请求只能发往白名单内的来源域名；白名单之外的 URL 或用户自定义抓取目标一… | 自动化 | reading.FetchArticleUseCaseTest#refusesNonWhitelisted/#refusesHttp; reading.ArticleSourceRegistryTest（白名单封闭） |
| AC2-09 | 用户粘贴导入的内容经过与 AI 输出相同的不可信输入校验；超长、非英文、含脚本或… | 自动化 | reading.ImportArticleUseCaseTest（脚本/中文/过短/超长/空标题全拒）; reading.ArticleQualityPolicyTest#rejectsRawHtmlAndScript |
| AC2-10 | 抓取或导入失败（来源不可达、解析不出正文、正文不合法、无网络）时保留今日已完成状… | 自动化 | ui.ReadingAccessViewModelTest#completionFailureLeavesStateUnchanged（源码注释 AC2-10）; reading.FetchArticleUseCaseTest#rejectsBodyFailingQuality; reading.ImportArticleUseCaseTest |
| AC3-01 | 断网、无 AI Profile 时，离线词典命中的单词可显示并由系统 TTS 发… | 自动化+设备 | learning.SearchVocabularyUseCaseTest; language.domain.PronunciationRouterTest; infrastructure.AndroidTextToSpeechProviderTest; 阶段4设备QA报告 §3.1 |
| AC3-02 | 无英语离线包、端侧模型缺失或 AI TTS 失败时显示对应安装/下载/重试提示，… | 自动化 | [语义候选] language.domain.PronunciationProviderContractTest; ui.PronunciationStatusTest; infrastructure.AndroidTextToSpeechProviderTest#initialization failure; androidTest language.KokoroEngineAvailabilityTest |
| AC3-03 | 同一文本、语音参数和模型版本命中同一 AI 音频缓存；修改任一输入生成新缓存。篡… | 自动化 | [语义候选] language.domain.PronunciationCacheKeyTest; PronunciationAudioCacheTest; infrastructure.FileSynthesizedAudioCacheTest#broken cache degrades; KokoroAssetMaterializerTest#digest mismatch; androidTest language.FilePronunciationAudioCacheTest |
| AC3-04 | 无 GMS 时自动尝试 Tesseract；两个 OCR Provider 不可… | 自动化+设备 | language.domain.OcrProviderChainTest（源码注释 AC3-04）; ui.PhotoTranslationViewModelTest#bothProvidersUnavailableIsAnOcrIssue; f3-03验收记录 §2; 阶段3设备QA报告 §4 |
| AC3-05 | 未经确认不上传图像或 OCR 文本；用户未保存时，完成/取消/失败后不残留原图于… | 自动化+设备 | language.infrastructure.TemporaryImageWorkspaceTest（KDoc AC3-05）; phototranslation.PhotoTranslationUseCaseTest#firstCallDemandsConfirmation; f3-03验收记录 §3; 阶段3设备QA报告（B5 端到端经用户裁决跳过） |
| AC3-06 | 用户编辑 OCR 文本后，AI 翻译只接收编辑后的文本；失败后文本仍可复制与重试… | 自动化+设备 | phototranslation.PhotoTranslationUseCaseTest（KDoc AC3-06）; ui.PhotoTranslationViewModelTest#editedTextIsWhatGetsTranslated; f3-03验收记录 §4; 阶段3设备QA报告 §4（真机已验证） |
| AC4-01 | 完成两天学习后历史分别显示两日的计划、完成数、文章状态；第二天不改写第一天数据。 | 自动化 | [语义候选] learning.LearningRecordModelsTest#history groups; learning.RoomLearningRecordRepositoryTest; ui.LearningRecordsViewModelTest; androidTest learning.RoomTodayPlanRepositoryTest——「历史含文章状态」子项待 SP-A 任务3 补专项 |
| AC4-02 | 启用提醒后，未完成日最多收到一次提醒；完成、关闭提醒、变更时间、重启和时区变化均… | 自动化 | notification.domain.ReminderSchedulingPolicyTest（7 用例）; ui.ReminderSettingsViewModelTest; f4-02验收记录（POST_NOTIFICATIONS 拒绝无循环弹窗） |
| AC4-03 | 正常导出后，manifest 能列出所有导出实体和资源；包内不存在 API Ke… | 自动化+设备 | backup.BackupSnapshotBuilderTest; BackupPackageValidatorTest; BackupExportMustNotIncludeCredentialsTest; ZipBackupPackageWriterTest; BackupSourceReadOnlyContractTest; ui.BackupExportViewModelTest; f4-03验收记录; batched-device-qa-report §3.2 |
| AC4-04 | 新设备以有效的一次性配对挑战和正确密码（如设）恢复后，数据和选定资源完整可读，A… | 自动化+设备 | backup.BackupRestorePipelineTest（KDoc AC4-04/05）; pairing.MigrationChallengeIssuerTest; PairedMigrationChallengeVerifierTest; androidTest backup.restore.DevicePairedRestoreE2ETest; batched-device-qa-report §R7（端到端复验通过） |
| AC4-05 | 篡改任一 payload/resource/manifest 字节、使用过期/已… | 自动化 | backup.BackupPackageValidatorV3Test; BackupRestorePipelineTest（篡改/过期/nonce/密码/路径穿越/空间负例矩阵 fail-closed）; f4-04验收记录 §3 |
| AC4-06 | 受保护备份在旧设备不可用或密码遗忘时不可恢复；UI 不提供跳过认证或强制导入入口… | 自动化 | ui.RestoreNoBypassContractTest（KDoc AC4-06）; pairing.MigrationChallengeIssuerTest; f4-04验收记录 §4 |
| AC4-07 | 进入替换提交阶段后（无论恢复成功还是失败已回滚），旧进程内对正式数据库的复用不再… | 自动化+设备 | backup.device.BackupCloseSemanticsContractTest（AC4-07 重启约束）; ui.BackupRestoreViewModelTest; SnapshotSwapRestoreCommitGatewayTest; androidTest backup.device.RoomFormalDataHandleCloseSemanticsTest; batched-device-qa-report §R7 |
| AC4-08 | 旧设备导出页的配对 QR 可被标准解码器（如 zxing）从屏幕截图解码，内容与… | 自动化+设备 | ui.PairingExportQrFlowContractTest（AC4-08）; ui.BackupProtectedExportViewModelTest; pairing.MigrationCodeCodecTest; batched-device-qa-report §R7（方形 QR zxing 直解+重显同码） |
