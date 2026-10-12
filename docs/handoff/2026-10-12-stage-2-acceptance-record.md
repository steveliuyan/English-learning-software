# 阶段 2（每日文章、双语阅读与自定义 AI）验收记录

**日期：** 2026-10-12
**结论：** JVM 层验收全部通过；三项 instrumentation 缺口补齐待真机执行；真机走查项不阻塞阶段 3。
**Spec：** `docs/specs/02-reading-and-ai-content.md`

## 验证命令与结果

- PASS：`:app:assembleDebug` + `:app:testDebugUnitTest --no-daemon --no-build-cache` → **1026 用例全绿，0 失败**（2026-10-12）
- PASS：`:app:compileDebugAndroidTestKotlin`（新增 displayMode 测试编译通过；与该文件既有用例同批，执行需真机 instrumentation）
- detekt/ktlint：按 `docs/decisions/2026-09-19-static-analysis-and-test-baseline.md` 不作门禁

## 本次新增测试

`RoomReadingPreferenceRepositoryTest`（androidTest，+3 用例）：

- `displayModeDefaultsToEnglishFirstWhenMissing` — 缺省回退 ENGLISH_FIRST
- `displayModePersistsAcrossAllThreeModes` — 三模式各自持久化
- `displayModeOverwriteDropsBackToDefaultWhenNeverWrittenForNewProfile` — 新 profile 默认值

## 验收标准逐条映射

| AC | 结论 | 证据 |
|---|---|---|
| AC2-01 | ✅ | `ReadingAccessViewModelTest.lockedProfileShowsReasonWithoutExposingArticleChoices`（未解锁显示原因、不暴露文章选项）、`strictLockedReportsMissingNewAndDue`；androidTest `lockedStateShowsReasonAndDisablesArticleChoices` |
| AC2-02 | ✅ | `GenerateArticleUseCaseTest`（生成含标题/正文/译文）、`ArticleHighlightPolicyTest`（派生高亮，不信模型坐标）、`ArticleReadingScreenTest.scriptTextIsRenderedAsPlainText` |
| AC2-03 | ✅ | `reusesTheStoredArticleWithoutCallingTheAi`、`regeneratingSavesANewVersionAndKeepsTheOldOneReadable` |
| AC2-04 | ✅ | `AiFailureTest`（7 用例：每类失败独立文案/可行动/不带 URL Key 原始响应/取消不当网络错误）、GenerateArticleUseCaseTest 的 NotConfigured×4；学习状态保持由 `failedGenerationShowsBannerAndKeepsTodayPlanUntouched` 锚定 |
| AC2-05 | ✅ | `existingTodayArticleIsExposedForTheReadButton`（缓存可读）、`lockedProfileStillExposesTheOfflineHistory` + `historyOrEmpty` 正交语义、`fetchMapsANetworkFailureWithoutStoring`；androidTest `feedUnreachableStateIsExplicit`/`feedEmptyStateIsExplicit` |
| AC2-06 | ✅ | `AiProfileSecretUseCaseTest`（Key 只入 SecretStore）、`fetchNeverStoresTheApiKey`、`userTextCarriesNoUrlKeyOrRawResponse` |
| AC2-07 | ✅ | androidTest `showsTheModelNameForAGeneratedArticle`、`showsTheAttributionAndLinkForAFetchedArticle`、`showsTheImportedNoticeAndTimestampForAnImportedArticle`、`neverShowsTheEndpointOrKeyForAnySource`、`doesNotExecuteTheSourceLinkAutomatically` |
| AC2-08 | ✅ | `FetchArticleUseCaseTest.fetchRefusesAFeedItemWhoseUrlIsNotWhitelisted`、`fetchRefusesAnHttpFeedItem`、`listReturnsTheFeedItemsOfAWhitelistedSource`（白名单封闭） |
| AC2-09 | ✅ | `ImportArticleUseCaseTest`（脚本标签/中文正文/过短/超长/空标题全拒绝且不写入）、`ArticleQualityPolicyTest.rejectsRawHtmlAndScript` |
| AC2-10 | ✅ | `completionFailureLeavesTheStateUnchangedForRetry`、`fetchMapsANetworkFailureWithoutStoring`、`fetchRejectsABodyThatFailsQuality`、`importRejectionShowsTheSpecificReason` |

## 待真机人工验收（不阻塞阶段 3）

1. 新增的 3 个 displayMode Room 用例随下一次真机 instrumentation 执行
2. 阅读页三来源标识、译文切换、词典占位入口的真机走查截图（AC2-07 的视觉证据）

## 遗留与风险

- `onOpenDictionary` 为占位回调——AC2-04 的"离线词典入口"只验收了入口存在与跳转意图，词典内容由 F3-01 交付
- 对无译文来源的 AI 补译属 Spec 明确排除项，未实现（正确）
- 任务书预估的"缺口 2/3"经核实已在此前提交中覆盖，未重复造测试；详见工作报告
