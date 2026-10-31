# 2026-11-03 全站重复项盘点（功能 / 选项 / 指引）

- **性质：** 只研究、未实施。每条证据都是**本轮实跑的 `grep` / 读码结果**（文件:行 + 原文片段），
  文末单列「实测 / 待设备核」口径。
- **上游：** `docs/design/2026-10-31-information-architecture-audit.md`（旧 D-x 表）、
  `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`（四栏 + hub 13 行台账）、
  `docs/handoff/2026-11-02-profile-hub-four-tabs-report.md` §5.3（明确把「关于/许可/备份结构性重排」推到新批次）。
- **用户原话：** 「你要看一下目前有哪些功能重复啊，或者说哪些页面有重复选项，或者是重复指引页面之类的，你要整合合并整理」。
- **接续点：** 旧 D-1（三处重复）、D-2（重复设置）、D-3（死行）、D-4（死文案）已在那两批部分收口；
  本表只收**那些文档之后仍然开着的账**，已修的不重复列（旧 D-5 今日文章直达见 2026-11-03 决策 §1/§1-B，三枚入口已并完）。

## 1. 主表

| 编号 | 重复的东西 | 类型 | 证据（实测） | 严重度 | 建议处置 |
|---|---|---|---|---|---|
| U-1 | **出站确认对话框五份实现**：共享件只被阅读页用，另四屏各画一份同名对话框 | 指引 + 组件 | 共享件唯一调用点 `ui/ReadingAccessScreen.kt:351 OutboundConfirmDialog(`；私写四份的标题全是「确认发送请求」：`SentenceAnalysisScreen.kt:253`、`PhotoTranslationScreen.kt:747`、`WordAiQaScreen.kt:284`、`ImageStudioScreen.kt:298`（前三份函数名同为 `private fun OutboundConfirmationDialog(host, onConfirmOutbound)`，签名逐字一致） | 高 | 四屏改调共享件；`ImageStudio` 的两段式正文给共享件加一枚正文覆写入参，别留私写 |
| U-2 | **同一条隐私规则已经漂了**：正文配方 `outboundConfirmBody()` 全站只被共享件自己调用 | 指引 | `grep outboundConfirmBody app/src/main/java` = 3 命中且**全在** `components/OutboundConfirmDialog.kt`（`:62` 定义、`:97` 自用）；实际文案：共享件「你的文本内容与该服务的密钥将发送给 X，并可能产生费用。」vs `SentenceAnalysisScreen.kt:255`「你的句子与该服务的密钥将发送给 $host。」（**漏「并可能产生费用」**）、`WordAiQaScreen.kt:286`「你的问题与…」、`PhotoTranslationScreen.kt:749`「你的识别文本（编辑后的版本）与…图片不会发送。」 | 高 | 与 U-1 同批：正文一律走 `outboundConfirmBody(host, kind)`，屏侧只许改「发的是什么」那一个词 |
| U-3 | **出站确认策略有两份权威 + 两枚同义枚举** | 入口/规则 | 域层真值 `ai/AiOutboundConfirmation.kt:51 requiredConfirmation(...)` 被 **6 处调用**（`ImageGenerationUseCase:68/155`、`PhotoTranslationUseCase:83`、`GenerateArticleUseCase:100`、`SentenceAnalysisUseCase:68`、`WordQaUseCase:69`，共 5 个用例类）；UI 层另有一枚 `components/OutboundConfirmDialog.kt:48 mustAskEveryTime(...)`，**生产代码零调用**（只被 `ConfirmDestructiveWordingTest` / `OutboundConfirmDialogTest` 读），而它自己的注释 `:71` 写着「显隐由调用方按 mustAskEveryTime 决定」；枚举也两份：`ai/AiOutboundConfirmation.kt:9 enum class AiPayloadKind` 与 `components/OutboundConfirmDialog.kt:35 enum class PayloadKind` | 高 | UI 侧枚举与纯函数改为**基于**域层类型（或直接把 `AiPayloadKind` 提为共用），删掉「注释说它是权威、代码里没人调」这一份 |
| U-4 | **12+ 处指引指向不存在的「设置 · AI」**（批 5 已把 AI 行搬进 hub，设置子页只剩外观） | 指引 | `grep -rn "设置 · AI" app/src/main/java` = 21 命中，其中**用户可见文案 17 处**：`ai/AiFailure.kt:57,60`、`ui/AiFeature.kt:104,131`、`ImageStudioScreen.kt:140,371,373,375`、`PhotoTranslationScreen.kt:790,792,794`、`SentenceAnalysisScreen.kt:296,298,300`、`WordAiQaScreen.kt:327,329,331`；另 4 处在注释里（`AiProfileSettingsScreen.kt:59,130`、`AiProfileSettingsViewModel.kt:99`、`components/NotConfiguredCard.kt:114`）。落点真值：`SettingsScreen.kt:83-92` 只有 `AppScreenHeader` + 一枚 `ThemeModeRow` + 一条说明，`:59` 的 KDoc 自陈「本页**不再**持有 …AI…任何入口」。**行为没错**：按钮走 `AppScreen.kt:207 openAiProfilesOrSettings` ⇒ 直达 AI 配置页——漂的只是文案地图 | 高 | 统一改成「我的 → AI 服务」；注释 4 处一并改，否则下一批又照旧抄 |
| U-5 | **同一个 AI 配置页对外三个名字** | 入口/指引 | hub 行与页标题都是「AI 服务」（`ProfileScreen.kt:84`、`AiProfileSettingsScreen.kt:133`）；发音屏内既写又点成「AI 服务与密钥」（`SpeechSettingsScreen.kt:129`、`:220`，另见 `SpeechSettingsViewModel.kt:161,192`）；AI 功能屏写「去配置 AI 服务」（`AiFeatureScreen.kt:114,124`）；AI 助学页页头那枚的可及名又是「AI 服务配置」（`AiLearningScreen.kt:192`） | 中 | 全站对齐「AI 服务」（入口名 = 页标题），可及名保留「AI 服务配置」这一层动作语义 |
| U-6 | **「本机数据 / 卸载前导出」同一条警告四屏各写一套措辞** | 指引 | 单一真源已在 `notices/NoticesPrivacyStatement.kt:8 thirdPartyPrivacyStatements()`（5 条，含「无账户：学习数据只保存在本机，不上传任何服务器。」），但它只喂许可页（`LicensesScreen.kt:140`）；其余屏各自转写：`BackupHubScreen.kt:183`「所有数据默认只在本机；卸载 App 会一并删除，请先导出备份。」、`LearningRecordsScreen.kt:149`「记录按天聚合，只保存在本机；卸载 App 前记得导出备份。」、`BackupExportViewModel.kt:146`「卸载、清除数据或设备损坏前未导出的学习记录可能无法恢复；无账户、无云同步…」 | 中 | 各屏留一句短的 + 出口指向许可页；措辞收成一处纯函数（同 `thirdPartyPrivacyStatements()` 的纪律），别再造第四套 |
| U-7 | **提醒规则同一句话在两屏出现 4 次**，外加一枚「长得像可点行」的只读死行 | 指引 + 选项 | `ReminderSettingsScreen.kt:85`、`:118`（未开/已开两态页头各写一遍「每天最多一次，系统可能稍有延迟」）、`:154`（同句 + 「（非精确提醒）」）；`ReminderSpeechScreen.kt:62` 第 4 遍「只在当天没完成计划时提醒 · 默认关闭（非精确、每天最多一次）」；死行 `ReminderSettingsScreen.kt:189-195`：`BareRow(title = "重复", subtitle = "周一到周日", trailing = "每天", showChevron = false)` 且**无 `onClick`**，注释自认「本仓没有按星期配置提醒的能力」 | 中 | 规则在该屏只讲一次（卡内），导航页副标题不再复读；「重复」行删掉（与已删的旧 D-3 死行同判据） |
| U-8 | **同一批词在三处三套命名** | 入口 | hub：`ProfileScreen.kt:76-78`「收藏的词 / 生词本 / 易错词」；分段 tab：`WordBankScreen.kt:33-35`「生词 / 错词 / 收藏」；合并页副标题：`MyWordsScreen.kt:159`「收藏 · 生词本 · 错词」 | 中 | 以分段 tab 的短名为准，三处逐字对齐；入口本身按旧 D-6/D-7 的裁定保留双入口 |
| U-9 | **「本周完成情况」画了两套** | 入口/指引 | 打卡屏：`CheckInScreen.kt:135 check_in_week_chart`（7 根柱）+ `:160` 起月历；学习记录屏：`LearningRecordsScreen.kt:179 WeekTicks(week.ticks)` + `:183 StatCell(value = "${week.doneDays}", label = "打卡天数")`（派生见 `LearningRecordsWeek.kt:28`） | 中 | 学习记录屏保留**数值**、删七格刻度；周视图归打卡屏一处 |
| U-10 | **同一屏两枚保存主操作**（学习计划页） | 选项/入口 | `AppScreen.kt:2483 action = { SetupHeaderSave(...) }`（定义 `:2640`，文字「保存」`:2643`）与 `:2610 SetupSaveBar(...)`（定义 `:2748`，吸底「保存计划」）同屏并立 | 中 | 留吸底那一枚（拇指区），页头只留返回；现网注释自己承认两枚靠不同 `contentDescription` 区分，是绕重排不是设计 |
| U-11 | **入口行名与落地页标题不一致** | 指引 | `BackupHubScreen.kt:85 title = "导出学习数据留档"` → 落地 `BackupExportScreen.kt:94/130 "导出备份"`（降级分支同名） | 低 | 落地页标题随行名改，或行名随标题；hub `:46` 自称「四枚行标题在本屏是唯一真值」，照这句话该改的是落地页 |
| U-12 | **hub 行「关于与许可」，许可仍在下一跳** | 入口 | `ProfileScreen.kt:88 ProfileRow("关于与许可", ...)` → `AboutScreen.kt:95 title = "开源许可与隐私说明"` + `:99 onClick = onOpenLicenses`（`AppScreen.kt` 里 `showAbout` → `showLicenses` 两跳） | 低 | 二选一：行名改「关于」，或该行直跳许可页（关于页里已不再有第三方清单本体） |
| U-13 | **「提醒与发音」是一枚不改任何状态的纯中转页** | 入口 | `ProfileScreen.kt:85` hub 一行 → `ReminderSpeechScreen.kt:58-76` 两枚 `EntryRow` → 提醒屏 / 发音屏。本层不读仓库、不写状态 | 低 | 可拆：hub 直接给「学习提醒」「语音朗读」两行（台账 13→14，`ProfileHubLedgerTest` 要同步）；**但这页有专门 connected 钉着**（`ReminderSpeechScreenTest` 4 条），动它得同批改测 |
| U-14 | 文档与代码口径含糊（**不是代码漂移**，纠正一条误判） | 账目 | `docs/decisions/2026-11-03…:89` 与 `docs/handoff/2026-11-03…` §7.5 写「AI 助学页的配置入口：本批仍不加」，而 `AiLearningScreen.kt:80 action = { AiProfilesAction(...) }`、`:185 private fun AiProfilesAction` 确实有一枚页头齿轮 | 低 | 澄清措辞：批 5 落的是**页头齿轮**（`2026-11-02` 报告 §2.2 与 §5.1 已写明「只经 hub 行与页头齿轮进入」），「暂不加」指的是**卡内额外入口**；两篇文档补一句限定即可 |

## 2. 我建议本批就动的（低风险、纯收口）

1. **U-4 + U-5**（改名与改口径，17 + 4 处文案）——不改行为、不改版面，代价最低、用户可见收益最直接。
2. **U-1 + U-2**（四屏私写对话框并入共享件，正文走唯一配方）——这是隐私安全面的**已发生漂移**，越晚并越贵。
3. **U-7**（提醒规则一次讲清 + 删「重复」死行）——死行是旧 D-3 的同类回归风险。
4. **U-10 + U-11 + U-12**（同屏两枚主操作、行名与页标题、关于/许可跳数）——三处都是"名字与落点不一致"，一次改完。

## 3. 看起来重复但该保留的（写清理由，别一刀切）

- **阅读历史 / 词库的 hub 格 + tab 内双入口**：同一份数据两个语境，两处落点行为一致（旧 D-6/D-7 原判仍成立）。
- **`BackupExportScreen` 从备份 hub 与 `RecoveryScreen` 两处进入**：两者是互斥状态（正常 / 迁移失败恢复），不是并列入口。
- **今日文章三枚入口**：已按 2026-11-03 决策 §1/§1-B 共用 `canOpenTodayArticleDirectly` 一枚判定——**多入口 + 单真值**是这一轮要推广的形状，不是要消灭的重复。
- **词书内容包 `.wbpack` 与备份 `.elpbak`**：两套数据、两种扩展名、两套校验，合并只会把边界搅浑。
- **`TodaySummaryScreen` 三枚退出**（页头 ← / 页头「完成」/ 吸底「回到今日」）共用同一条 `exitLearning`（`AppScreen.kt:1051-1052`，吸底那枚同接 `onDone`）：同源同行为，保留。
- **`VocabularyRelearnScreen` 两个挂载分支**（整册队列 / 单卡）：两种模式确有别，登记为「同屏两态」，不合并。

## 4. 核账前提（动之前必须先读的闸）

字面量被测试钉着的条目，改文案 = 同批改测，否则当场红：

| 条目 | 会被这些闸抓住 |
|---|---|
| U-4 / U-5 | `NotConfiguredCardTest`、`SentenceAnalysisScreenTest`、`ImageStudioScreenTest`、`WordAiQaScreenTest`、`SpeechSettingsViewModelTest`、`AppScreenTest` |
| U-8 | `MyWordsScreenContractTest`、`MyWordsScreenTest`、`ProfileScreenTest` |
| U-11 | `BackupHubLayoutContractTest`、`BackupEntryUnificationTest` |
| U-12 | `ProfileHubLedgerTest`、`ProfilePageContractTest`、`ProfilePageHomeContractTest`、`SettingsLicensesEntryTest`、`SettingsScreenTest` |
| U-13 | `ProfileHubLedgerTest`（恰 3 组 / 恰 13 行）、connected `ReminderSpeechScreenTest` |
| U-1 / U-2 | `components/OutboundConfirmDialog.kt` 的份数棘轮只管**顶层公开定义**（`SharedComponentUniquenessRatchetTest:338/:370` 把它登记为唯一件，`:418` 的锚点是 `(?m)^fun X(`），**四份缩进的私有同形态它数不到**——所以并入共享件后必须补一枚「对话框族」窗形状判据，否则下一批又能私写第五份 |

## 5. 实测 / 待核口径

- 本表全部**读码实测**（`grep -rn` + 逐行读原文），行号本轮实跑所得；后续批次动文件后会漂，重算命令用
  `grep -rn "设置 · AI" app/src/main/java --include=*.kt` 这类现算式，别把行号当口径。
- **未跑测试、未上设备**：U-1/U-2 的屏上实际弹出时机、U-9 两屏周视图在 `fontScale 2.0` 下是否同屏可见、
  U-7 死行在 TalkBack 下是否被读成可点——都需要 connected/设备目视取证，属实施批的事，本表不下结论。
- **U-3 的一处推断**：`openAiProfilesOrSettings` 的 `showSettings = true` 兜底（`AppScreen.kt:213`）
  在真机上大概永不触发（`aiProfileViewModel` 由 Hilt 注入），我只核到代码形状，属推断；
  但它现在会把用户送到只剩「外观」的设置页，与 U-4 同源，改文案时顺手把兜底改成 hub。
- **U-2 的一条更正**：`ImageStudioScreen.kt:301` 说「同一域名只需确认一次。」——它对应的是**文本**载荷
  （`ImageGenerationUseCase.kt:68` 用 `AiPayloadKind.Text`），域层对图片载荷恒为每次确认
  （`AiOutboundConfirmation.kt:60`），所以**不构成假话**；但这句和共享件的图片档口径读起来相向，
  并入共享件时应让 `PayloadKind` 决定这句话，别手写在屏里。
