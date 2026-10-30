# UI 原型对齐 · 进度与交接简报（2026-10-30）

> **状态更新（同日收尾 + 后半段续做）**：本简报的「本轮完成」「下一轮第一件事」两节已过时。
> 剩余 6 屏 🟡（#9/#12/#13/#14/#24/#25）已于同日后半段全部收口，对账小计为
> **✅ 36 · 🟡 0 · ⬜ 0**，全量门禁重跑通过（JVM 2221/0）。以
> `docs/handoff/2026-10-30-ui-prototype-alignment-completion-report.md` 为准；
> **批 4 全量 QA 已于 2026-10-31 完成**（门禁 BUILD SUCCESSFUL，JVM 2221/0；`ui` 包 561 条/失败 3 条
> 均为必须 `font_scale 2.0` 的双字号用例，该档 6/6 绿）；真机清单可代理项已在模拟器代跑（§9），
> 新发现 **F-1 插入页按钮标签对比度 1.10:1** 与 F-2~F-5 待下一轮开单，见
> `docs/handoff/2026-10-31-batch4-full-qa-report.md`；**推送待用户开代理**（远端落后 42 提交）。
>
> **2026-10-31 用户追加裁定**：删除「AI 助学」头卡表情 —— 组件 `ui/mascot/` 三源 + 两套测试已删，
> 棘轮 seed 清零，许可台账条目移除，规格 §4.5 作废（新增 §4.6 承接「动画时长缩放」结论）。
> 门禁 JVM **2209/0/0**（2221 − 12 条随组件删除的几何用例），connected `AiLearningScreenTest` **OK (19)**。
> 见 `docs/decisions/2026-10-31-remove-ai-mascot.md`。**信息架构去重盘点已提交待拍板**（我的 vs 关于两处重复出口）。

分支：`stage-5-ui-r2-learning-path` · 计划：`docs/superpowers/plans/2026-10-29-ui-prototype-alignment.md`
原型目录（**只读**）：`C:\Users\steveliuyan\AppData\Roaming\Open Design\namespaces\release-stable-win\data\projects\6a0aa00b-6bfc-43e7-b35f-13f7478d3147`

## 一、本轮完成（均已跑绿，未提交）

| 屏 | 状态 | 落点 |
|---|---|---|
| Task 19 今日学习空态 `sLearnEmpty` | ✅ | `TodayPlanScreen` Ready 分支 `todayAllDone` + 共享 `EmptyState` + 260dp 两枚出口；`onReviewWords` 下线 |
| Task 15 词书详情 `sBookDetail` | ✅ | **新建** `BookDetailScreen.kt`（词库页「词书」段默认面 ↔ 词单浏览两态） |
| Task 21-A 外刊选读 `sReadFeed` | ✅ | 尾部「来源说明」`QuietCard` |
| Task 21-B 阅读历史 `sReadHistory` | ✅ | 日期分节 + 尾部提示 |
| Task 21-C/D 今日阅读 + 正文/对照 | 🟡 | 今日卡「覆盖 N 词 · 第 v 版」+「本周阅读」派生统计卡（近 7 天篇数/字数）；正文元信息行；剩余项见台账 |
| Task 22-A 我的 `sMeHome` | 🟡 | `.head-sub`「连续打卡 N 天 · 累计学过 N 个词」 |
| Task 22-B 关于 `sMeAbout` | ✅ | **新建** `AboutScreen.kt` + 设置第 10 行入口（AC-R-21 行数 9→10，用户拍板） |

对账小计：**✅ 13 · 🟡 6 · ⬜ 17**（重算命令见计划文档「小计」段）。

## 二、门禁与验证状态

- JVM：`gradle --offline testDebugUnitTest` → **2181 条全绿**（含新增 5 个契约测试类：
  `TodayPlanEmptyStateContractTest`、`BookDetailScreenContractTest`、`ReadingFeedScreenContractTest`、
  `ReadingHistoryScreenContractTest`、`ReadingHomeContractTest`、`ArticleReadingScreenContractTest`、
  `ProfilePageHomeContractTest`、`AboutScreenContractTest`）。
- connected（模拟器 `emulator-5574`）：`TodayPlanScreenTest` 27/27、`AppScreenTest` 全绿、
  `BookDetailScreenTest` 5/5、`ReadingFeedScreenTest` 9/9、`ReadingHistoryScreenTest` 8/8、
  `ReadingAccessScreenTest` 18/18、`ArticleReadingScreenTest` 25/25、`SettingsScreenTest` 18/18、
  `ProfileScreenTest` 3/3、`BackupEntryUnificationTest` 全绿。
  - 双倍字号类用例需先 `adb shell settings put system font_scale 2.0`，跑完复原 1.0。

## 三、下一轮第一件事（按顺序）

1. **`sMeWords`（我的词 / 词库两屏对应）**：核 `MyWordsScreen` 与 `WordBankScreen` 与原型 `sMeWords`
   的对应关系（收藏 / 生词 / 易错三 tab ↔ 本仓四格分段），先写对应关系裁决再动版面。
2. `sMeRecords`（`LearningRecordsScreen`）、`sMeSettings`（`SettingsScreen`）、
   `sMeReminder`（`ReminderSettingsScreen` + `SpeechSettingsScreen`，合成还是分开待裁决）、
   `sMeLicenses`（`LicensesScreen`）、`sMeData`（`BackupHubScreen`）。
3. #23 启动/建档/错误/不可用 5 屏（含按原型新建 `sUnavailable`）。
4. #24 AI 链路 7 屏（含未接入控件禁用态）。
5. **#14 全量门禁与工作报告**（`assembleDebug → testDebugUnitTest → compileDebugAndroidTestKotlin` + 报告落 `docs/handoff/`）。

## 四、仍待注入的小项

- 「关于」页 `licenseCount` 目前传 `null`（行文案退成不带数字的短语）：可从 `LicensesScreen` 同一份
  markdown 的 `parseThirdPartyNotices(...).size` 注入。
- `sReadHome` 的「双语对照」独立入口按「阅读器内切换」记偏离（同类开源 LWT / LinguaCafe 同做法）。
- `sReadArticle` 的 `.prose` 字号/行距逐值与底部两枚并列出口形态待下一轮。

## 五、环境提醒

- 本机 Gradle：`C:/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`，
  管道需 `; exit ${PIPESTATUS[0]}`，输出经 `iconv -c -f utf-8 -t utf-8`。
- connected 循环：`assembleDebug` + `assembleDebugAndroidTest` → `adb -s emulator-5574 install -r` →
  `am instrument -w -e class <FQCN>`（不用 `connectedDebugAndroidTest`）。
- Kotlin 字符串里写 `${...}` 字面量用 `${'$'}`，否则 `\$` 是非法转义（本轮踩过 3 次）。
