# 2026-11-10 返回保持屏内状态：只给只读浏览型覆盖层开白名单

用户当批反馈（m05053 第一项，走查原话）：

> 「有些选项打开之后再返回，它就不在那个位置上，就直接从头开始了」

指的是覆盖层（全屏子页）的**屏内**滚动位置：导航位本身早就 `rememberSaveable` 住，丢的是
`rememberLazyListState` / `rememberScrollState` 的 Saver——转场结束后那条腿被 `AnimatedContent` 整棵
dispose，没人替它存。修法是在宿主放一枚 `rememberSaveableStateHolder()`，腿根包一层
`SaveableStateProvider(key) { content() }`。本文只记**范围**这一件事：包哪些屏。

## 1. 全量包会连产品规则一起存回去（两轮实测）

第一轮「两条腿都包」的做法在设备上撞了两次墙，两次都不是实现瑕疵，是**范围**错：

| 轮次 | 做法 | connected 读数 | 红点归属 |
|---|---|---|---|
| 1 | 两站（overlay + tab）全量包 | `AppScreenTest` 28 红，抛 `IllegalArgumentException: Type of the key ReadingHistory is not supported` | 键类型：`navigationIdentity` 是 `Any`（`data object` 的 `this` 或 `mountIndex to 主键` 的 `Pair`），Bundle 装不下 |
| 2 | 两站全量包 + 键折字符串 | 253 tests / 8 红，全在 `AppScreenTest` | 见 §2 |

键这一层只要折成「路线类名 + 业务主键」的 `String` 就了了（`data object` 的 `toString()` 是对象名，
`Pair` 的 `toString()` 形如 `(3, abc-123)`，前缀类名再挡理论碰撞）。真正的分歧在范围。

## 2. 8 红逐条归因（bisect：先关 tab 腿，再关 overlay 腿）

基线对照：`git stash` 掉改动、在 HEAD（完全没有 provider）上跑 `AppScreenTest` 66 例 → 2 红，且与 8 红
不重叠（`systemBackOnTheTodaySummary…` / `wordBankEntryOpensTheFourSegmentPage…`，都是返回键注入族）。
所以 8 红确实是「屏内状态全量保留」引入的，不是既有红。

关掉 **tab 腿**的保留（只留 overlay 腿）后再跑这 8 条：只剩 3 红，`returningFromLearningReloadsTodayPlan`、
`leavingAndReenteringLearningClearsTheWordCardPronunciationState`、`theReviewQueueEntry…`、
`theWordListEntry…` 等 5 条当场转绿。⇒ 5 红由 tab 腿引起，剩下 3 红由 overlay 腿里两屏引起：

| 屏 | 机制 | 结论 |
|---|---|---|
| `ArticleReading` | `ArticleReadingScreen.kt:197` `var coverageOpen by rememberSaveable(current.article.articleId) { true }`——弹窗口径是「**每次进组合重弹一次**」，状态存回去后同一篇再进就不弹，`coverage_popup_close` 点不到 | 产品规则，不许存回 |
| `SpeechSettings` / `AiProfiles` | 设置表单的二级菜单展开态被存回去后，「返回后候选必须刷新」那两条用例再点一次折叠按钮反而把菜单收起（`speech_profile_mimo` 取不到） | 表单态，不许存回 |
| 站 2（主 tab）整站 | 主 tab 的重进语义被产品规则钉死：回学习 tab 必须**重算今日计划**（F1-04）、离开再进学习流必须**清空词卡语播呈现**（I-1） | 整站不保 |

## 3. 结论（已落 `ui/AppScreen.kt`）

1. **站 2 一律不包**：`TabLeg` 退回只 `content()`，与站 1 刻意不对称，理由写进 KDoc（F1-04 / I-1）。
2. **站 1 走白名单**：`overlayRoutesKeepingInScreenState` 只收「只读浏览型」8 枚——
   `GlobalSearch` / `VocabularyRelearnList` / `LearningRecords` / `ReviewQueue` / `TodayWordList` /
   `WordBank` / `ReadingHistory` / `ReadingFeed`。其余（学习流程、学习设置、工作纸、打卡、备份、AI 功能页、
   关于/许可 ……）逐字保持改动前行为。
3. 白名单按**类**比（`route::class in setOf(AppOverlayRoute.X::class)`）而不是按实例比：`ReviewQueue` /
   `TodayWordList` 是带 payload 的 `data class`，实例相等会随计划变化。
4. 白名单**不用 `is` 分支写**：`ScreenTransitionContractTest` 按 `is AppOverlayRoute.<变体>` 的枚数钉
   「每枚 route 恰有一处挂载点、总数 = route 表枚数」，写成 `is` 会立刻把那些判据撑红——挂载口径真的只能有一处，
   所以判据是对的，写法让路。

## 4. 验收读数与残留红的口径

- `:app:testDebugUnitTest`：2610 用例全绿（含上面那条契约）。
- 定向 18 类 connected（253 tests / 3 skipped）跑三趟：屏内状态相关的 8 红**再未出现**；每趟残留 2–3 红，
  全部落在「系统返回键注入」与 5s `waitUntil` 这一族（`systemBackFromTheLicensesPage…`、
  `hostedBackupRowOpensTheHub…`、`wordBankEntryOpensTheFourSegmentPage…`、`leavingLearningStopsTheStillPlayingAudio…`、
  `returningFromAiProfilesRefreshesRemovedKey…` 等），且**逐条单独跑均通过**（同一环境同一 APK，各跑 1–2 趟）。
  这些红在 HEAD 基线上同样出现过（基线那一趟就 2 红同族），故记为环境/注入侧偶发，不算本次回归。
- 白名单本身的钉子用例已在仓：`AppScreenTest.wordBookSystemBackRetainsTheNonzeroScrollOffsetWithinW`
  （滚到 W 组内 47px 偏移 → 点开词卡详情 → 系统返回，断言像素偏移与 rail 选中态都原样）。

## 5. 复现命令

```
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.englishlearning.ui.AppScreenTest"
```

红点定位看 `app/build/outputs/androidTest-results/connected/debug/TEST-*.xml`（`<failure>` 里带行号）。
