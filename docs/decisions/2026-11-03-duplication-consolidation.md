# 全站重复项整合：出站确认收口、指引地图与落点命名

- **日期：** 2026-11-03
- **状态：** 已接受（用户原话「你要看一下目前有哪些功能重复啊……你要整合合并整理」＝授权整合；
  具体范围由本决策按 `docs/design/2026-11-03-duplication-audit.md` 的 U-1..U-14 拍板，U-13 本批不动）
- **上游：** `docs/design/2026-11-03-duplication-audit.md`（盘点表，含每条的文件:行实测证据）
- **前置：** `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`（四栏 + 我的 hub 与 13 行台账）、
  `docs/decisions/2026-11-03-today-article-direct-open-and-checkin-name.md`（§1/§1-B「多入口 + 单真值」形状）

## 背景

盘点表收了 14 条重复。它们不是同一种重复，处置代价也差一个量级，所以先分类再定本批范围：

- **已经漂了的**（不说谎就活不下去的那类）：U-1/U-2/U-3——出站确认对话框有 5 份实现、同一条隐私规则
  有 4 套措辞、策略判定有 2 份权威（其中 UI 侧那份生产代码零调用却自称权威）；
  U-4——12+ 处指引写着「设置 · AI」，而设置子页自批 5 起只剩外观。
- **名字与落点不一致**：U-5/U-11/U-12/U-8。
- **同一屏两枚主操作 / 同一句话复述 4 遍 / 长得像可点的死行**：U-10/U-7。
- **纯中转层**：U-13（拆它要动 hub 台账与 4 条 connected，属结构重排）。
- **看着重复但该留**：盘点表 §3 六条（今日文章三枚入口、`.wbpack` vs `.elpbak`、
  `BackupExportScreen` 两进入点等）——本批明确不动，避免下一任当漏账重做。

## 决定

### 1. 出站确认：对话框、措辞与策略都只留一份权威（U-1 + U-2 + U-3）

- **对话框组件**只留 `ui/components/OutboundConfirmDialog.kt`。四屏
  （`SentenceAnalysisScreen.kt:253`、`PhotoTranslationScreen.kt:747`、`WordAiQaScreen.kt:284`、
  `ImageStudioScreen.kt:298`）里签名逐字相同的 `private fun OutboundConfirmationDialog(...)` 删除，
  改调共享件。`ImageStudio` 的两段式正文（图片 / 文本分开说）由共享件的 `payloadKind` 分派 +
  一枚可选 `note` 实参满足——**屏侧只许在配方之后追加一句自己的业务说明，不许覆写配方**
  （实施时把原稿的「正文覆写入参」收窄成「追加」：一旦允许整句覆写，U-2 的单一权威就当场作废）。
- **正文措辞**只由 `outboundConfirmBody(host, kind)` 生成。屏侧只允许决定「发出去的是哪种载荷」，
  不允许自己拼句子——现状 `SentenceAnalysisScreen.kt:255` 那一份漏了「并可能产生费用」，
  这正是四套措辞的必然结果。
- **策略判定只留域层** `ai/AiOutboundConfirmation.kt:51 requiredConfirmation(...)`（6 处调用、5 个用例类）。
  UI 侧 `components/OutboundConfirmDialog.kt:48 mustAskEveryTime(...)` 与 `PayloadKind`（`:35`）
  改为**基于** `AiPayloadKind`：枚举不再第二份，纯函数不再自称权威却零调用。
  若共享件的可见性确需一个 UI 侧便捷判定，它必须委托域层那枚，而不是平行再算一次。
- **配套闸（按实测形状落地）**：`SharedComponentUniquenessRatchetTest` 现有的 `(?m)^fun X(` 锚点只数顶层**公开**定义，
  四份缩进的私有同形态它数不到——所以它才漏掉本次事故。补的闸是 `OutboundDialogFamilyTest` 四条跨文件计数：
  标题「确认发送请求」全 main 恰一处且就在共享件、正文配方「与该服务的密钥将发送给」只许出现在共享件、
  四屏各**恰调一次** `OutboundConfirmDialog(`、屏侧 `fun OutboundConfirmationDialog(` / `fun ConfirmationDialog(`
  归零（计数**不锚进行首**，缩进的私写定义一样能数到，这正是不用「调用窗口内禁同名定义」那条窗形状判据的原因：
  窗口只对"框住的那一段"有约束力，第五份只要长在窗外就躲过去了）。
  措辞类判据走 `countMatches`（剥注释）：本仓的注释惯例是把权威措辞引一遍再解释为什么，
  按原文计数会把诚实的文档判成违规；§2 那类**改名**判据反之必须连注释一起扫。两种口径的差别写在各自的消息里。
- **连带后果：出站确认两枚按钮的墨色权威搬家（改的是 Task 27 的判据本体，故在此记账）**。
  Task 27（裁决 22-2）把四屏的出站确认按钮从 `Learn` 绿改成 `AiSpeech` 域色，立论是「按钮与入口图标同色」。
  并入共享件后，那两枚按钮由屏**画不出来**了——它们来自 `DialogButton(role = Confirm / Dismiss)`，
  取墨只由 `components/ConfirmDestructiveDialog.kt` 的 `dialogButtonInk` 角色表决定
  （确认档 = `onMint` 容器 + `background` 前景，即装配点已钉的 `primary`/`onPrimary`）。
  本批**接受**这个形状，不为此在共享件上开一枚 `domain` 形参：对话框内需要读出来的是
  「这是本对话框的主操作」，而不是「这属于哪个能力域」——同一枚对话框的标题、正文、取消键都不带域色，
  只给按钮染域色反而把主次读反；而「和入口图标同色」这条从来只是修 `Learn` 绿的顺带说法。
  `DangerInkPlacementContractTest` 因此改成钉三件事：屏侧仍禁 `Learn` 绿（Task 27 的病灶一字未放宽）、
  屏侧确认调用块内零 `AppDomain.` 取墨点、共享件按 `Confirm`/`Dismiss` 各恰一枚且自己也不按域取墨。
- **连带后果：玻璃面板花名册少四行**。四屏的私写对话框用的是 `GlassDialog`，退役后
  `GlassPanelInkPairingContractTest.expectedPanels` 从 6 枚减到 2 枚（`WordBookTransferScreen` + `AppScreen`）。
  判据没放宽，只是换了执行者：面板份数由该花名册 + `OutboundDialogFamilyTest` 逐屏恰一处调用共同承重，
  确认框两枚按钮的墨色由上一条的共享件腿承重。各屏版式契约里的 `GlassDialog( == 1` 同批改成
  `OutboundConfirmDialog( == 1` + `GlassDialog( == 0`（`ImageStudio` / `PhotoTranslation` / `WordQa` 三处），
  `SentenceAnalysisLayoutContractTest` 的药片计数随之从 5/2/1 改为 3/1/0。

### 2. 指引地图改指向真存在的落点（U-4）

`grep -rn "设置 · AI" app/src/main/java` 现值 21 命中（用户可见 17 + 注释 4）。全部改为 **「我的 → AI 服务」**：
批 5 之后 AI 配置的入口在「我的」hub（`ProfileScreen.kt:84`），设置子页 `SettingsScreen.kt:59` 的 KDoc
自陈「本页不再持有 …AI…任何入口」。行为不改——按钮仍走 `AppScreen.kt:207 openAiProfilesOrSettings`。

顺手收掉同一处的一条已知的半成品：`openAiProfilesOrSettings` 的 `showSettings = true` 兜底（`:213`）
在 profile 装载失败时会把用户送到只剩「外观」的设置页。兜底改为落到「我的」hub——
与 U-4 同源，改文案时一并改，不留「文案说 hub、代码送设置页」的新漂移。

注释里的 4 处同批改：不改注释就等于给下一批留一份可抄的旧地图。

### 3. 同一页对外只留一个名字（U-5）

入口名 = 页标题 = **「AI 服务」**。要收的是发音屏那枚「AI 服务与密钥」
（`SpeechSettingsScreen.kt:220`、`SpeechSettingsViewModel.kt:161,192` 与 `:129` 那行说明）。
`AiFeatureScreen.kt:114/124` 的「去配置 AI 服务」是「动词 + 名」，名字已经对齐，保留；
`AiLearningScreen.kt:192` 的可及名「AI 服务配置」保留动作语义（TalkBack 需要它说清这是配置入口）。

### 4. 规则只讲一遍、死行删掉、两枚主操作留一枚（U-7 + U-10）

- **U-7**：「每天最多一次 / 非精确提醒」在提醒屏 3 处 + 导航页 1 处共 4 遍。收成规则**在提醒屏卡内讲一次**，
  `ReminderSpeechScreen.kt:62` 那枚副标题不再复读同一句。
  `ReminderSettingsScreen.kt:189-195` 的「重复 / 周一到周日 / 每天」行无 `onClick`、`showChevron = false`，
  注释自认本仓没有按星期配置的能力——按旧 D-3 死行同判据**删除**。
- **U-10**：学习计划页同屏并立页头 `SetupHeaderSave`（`AppScreen.kt:2483`/`:2640`）与吸底 `SetupSaveBar`
  （`:2610`/`:2748`）。留**吸底那一枚**（拇指区、与全站吸底主操作一致），页头只留返回。
  现网注释靠不同 `contentDescription` 区分两枚，那是绕重排不是设计。

### 5. 名字与落点对齐（U-11 + U-12 + U-8）

- **U-11**：hub 行「导出学习数据留档」（`BackupHubScreen.kt:85`）→ 落地页标题「导出备份」
  （`BackupExportScreen.kt:94/130`）。hub `:46` 自称「四枚行标题在本屏是唯一真值」，照这句话改**落地页标题**。
- **U-12**：hub 行「关于与许可」（`ProfileScreen.kt:88`）但许可仍在下一跳（关于页 → 许可页两跳）。
  本批选择**行名改「关于」**、两跳保留：关于页里已不再有第三方清单本体，直跳许可页会让「关于」这一层没入口。
  （与 U-13 的「纯中转该不该存在」是同一个问题的两端，本批把结论写在文档里，不靠再删一屏来表达。）
- **U-8**：同一批词三套命名——hub「收藏的词 / 生词本 / 易错词」（`ProfileScreen.kt:76-78`）、
  分段 tab「生词 / 错词 / 收藏」（`WordBankScreen.kt:33-35`）、合并页副标题「收藏 · 生词本 · 错词」
  （`MyWordsScreen.kt:159`）。以**分段 tab 的短名为准**（收藏 / 生词 / 错词），三处逐字对齐；
  入口本身按旧 D-6/D-7 裁定保留双入口。

#### 5-A 实施时发现的两句假话副标题（本批未动，登记待拍板）

改名时逐字读到 hub 词汇三行的副标题，其中两句正是 `MyWordsScreenContractTest` 里那段「三句原型说明逐条核过生产逻辑」
的 KDoc 判定**在本仓不成立**、因此合并页整句不写的那两句说法
（重算：`grep -n "不成立" app/src/test/java/com/example/englishlearning/ui/MyWordsScreenContractTest.kt`）：

- 「生词」行：「明天的词单里排到前面」——新词顺序由 `NewWordOrdering`（词头三字母交错 + 内容哈希）决定，
  生词条目在计划生成里零参与；
- 「错词」行：「按答错次数排序 · 答对两次移出」——错词按**到期先后**排，且**一次**答对即移出。

同一句话在合并页被核过不成立、在 hub 却写给用户看，是比命名更硬的问题（说法本身就是错的），
但修它改的是文案本体而不是重复项，超出本批「收名字」的范围，且两句 honest 替代在本仓已有成文出处
（`MyWordsScreen.kt` 两段空态：生词「还没有生词，提交“不认识”后会自动加入。」、
错词「…复习中标记「不认识」的词会出现在这里，重新答对后自动消失。」）。
本批**登记不动**，等用户点头再按 TDD 改（改法：两枚副标题换成上面两句，`ProfileHubLedgerTest` 与
`WordListEntryNamesFollowBankSegmentsTest` 同批加一句形状钉）。

同源未动的一条：`ui/WorksheetSettingsScreen.kt` 的默写纸范围「今天的易错词」保留原措辞——
它指的是**当天答错的计划项**（`learning/worksheet/BuildWorksheetContentUseCase.kt` 的
`DIFFICULT_TODAY` 分支按当次反馈 `Again`/`Hard` 筛），不是词库「错词」那一册，
属于另一个概念，换名反而把两个落点说成同一个。

### 6. U-6 / U-9 本批只做「不再增加新的」，不做重排

- **U-6**（「本机数据 / 卸载前导出」四屏四套措辞）：单一真源 `thirdPartyPrivacyStatements()` 已存在但只喂许可页，
  其余三屏各写一套。收成一处纯函数涉及备份与记录两屏的文案契约与 connected 断言，
  与 §1 的对话框族是同类工程但风险面不同（备份警告是用户做数据决策的依据，措辞得逐字校）。
  本批**不动**，登记为下一批首项。
- **U-9**（本周完成两套可视化）：属版面重排，且大字号下同屏可见性未取证（盘点表 §5），本批**不动**。

### 7. U-13（提醒与发音中转页）与 U-14（文档措辞）

- **U-13 不做**：拆它 = hub 台账 13→14（`ProfileHubLedgerTest` 恰 13 行要同步）+ connected
  `ReminderSpeechScreenTest` 4 条整条改测。这是结构重排，与本批「文案与组件收口」不是同一种改动，
  混在一批会让 §1 的安全面收口被版面争议拖住。等用户在盘点表上单独拍板。
- **U-14 做**：纯文档澄清，同本报告一起落——批 5 落的是**页头齿轮**，「暂不加」指**卡内额外入口**，
  两篇文档补限定语，防止下一任把「文档说不加、代码有齿轮」读成漂移而"修掉"一个设计。

## 影响

- **改生产**：`components/OutboundConfirmDialog.kt`（`note` 追加入参 + 三枚 tag 实参 + 改吃域层 `AiPayloadKind`）、
  四屏删私写对话框（含三处随之失效的 `GlassDialog` import）、
  `ReadingAccessScreen.kt`（档位实参换成 `AiPayloadKind`）、
  `ai/AiFailure.kt`、`ui/AiFeature.kt`、四个 AI 屏 + `PhotoTranslationScreen` 的文案、
  `SpeechSettingsScreen/ViewModel`、`ReminderSettingsScreen`（删死行 + 规则一次）、`ReminderSpeechScreen`、
  `AppScreen.kt`（学习计划页头保存摘除 + `openAiProfilesOrSettings` 兜底）、`BackupExportScreen`、
  `ProfileScreen`、`WordBankScreen`、`MyWordsScreen`、`BackupHubScreen`。
- **改测试**：字面量闸必须同批改，否则当场红——
  `ImageStudioScreenTest:219`、`NotConfiguredCardTest:103/138`、`SentenceAnalysisScreenTest:183`、
  `WordAiQaScreenTest:172`（U-4）；`SpeechSettingsViewModelTest:388`（U-5）；
  `MyWordsScreenContractTest` / `MyWordsScreenTest` / `ProfileScreenTest`（U-8）；
  `BackupHubLayoutContractTest` / `BackupEntryUnificationTest`（U-11）；
  `ProfileHubLedgerTest` / `ProfilePageContractTest` / `ProfilePageHomeContractTest` /
  `SettingsLicensesEntryTest` / `SettingsScreenTest`（U-12）；
  `OutboundConfirmDialogTest` / `ConfirmDestructiveWordingTest` + 新增族棘轮（U-1..U-3）。
- **不动**：hub 台账行数（U-13）、备份警告措辞源（U-6）、周视图版面（U-9）、
  盘点表 §3 六条「该保留的重复」。
- **验收**：门禁仍是单测（detekt/ktlint 存量基线债 1500+，不作闸）；
  connected 只在 `emulator-5574` 走 `adb install -r` + `am instrument`，真机 `bf353dda` 仅手工试用复装。
- **推送**：不合 `main`、不推送，等用户说「推送」。

## 实施记录（收口时补，2026-11-01 本地门禁）

- **新增的四枚闸**：`OutboundDialogFamilyTest`（U-1..U-3 跨文件份数与措辞）、
  `ReminderRuleOnceContractTest`（U-7 规则只讲一遍 + 死行归零 + 「说明必须坐在卡里紧挨开关」的位置窗）、
  `BackupPageNamesFollowHubRowsTest`（U-11 落地页标题跟 hub 行名）、
  `WordListEntryNamesFollowBankSegmentsTest`（U-8 三处名字跟 `WordBankSection` 枚举体走，期望名**解析**而非写死）。
- **改名判据连注释一起扫**（U-8 / U-11 / U-12 三条），措辞判据走 `countMatches`（剥注释）——
  两套口径的区别写在各自消息里；`AboutRowNameIsJustAboutTest` 的「hub 不许复述许可入口那句」
  因此**连注释里复述也算命中**，实现侧文档写法要绕开原句而不是引用它（本轮实测被自己这条判红过一次）。
- **U-8 的连带收窄**：`ProfilePageHomeContractTest` 原按整份 `ProfileScreen.kt` 扫 `"收藏"` 并要求归零，
  改名后 hub 行本体就是「收藏」——该条讲的是「统计格不许长出一枚收藏标签」，扫描面据此收到三行
  `ProfileStatCell(`（另加「恰 3 行」防空转）。这是**判据范围**修正，不是放宽。
- **U-12 的 connected 腿**：`SettingsLicensesEntryTest` 里那条按文字反钉（`onNodeWithText(...)` 断旧行名不在设置页）
  改名后旧名全站不存在，判据会变成永真空转，故同步换成新名「关于」。
- **门禁现值**（2026-11-01 终局树重跑，`.git/qa-scratch/final-jvm-gate-2026-11-01.log`）：
  `assembleDebug` + `testDebugUnitTest` + `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL，
  JVM 306 类 / 2248 条 / 0 失败 0 跳过。connected 全量 714 条 / 11 失败，
  11 条**全是设备前置条件缺失**（`/data/local/tmp/qa-b1-package.zip` 已清 → 认证恢复 e2e 2 条、
  Kokoro 外部模型资产未推 → 2 条、设备 `font_scale` 现为 1.0 → 双字号护栏 3 条、
  设备未配默认文本服务且不授权为跑测试发付费请求 → 词书包与探针 4 条），
  与本批改动的屏无交集；把 `font_scale` 置 2.0 单跑 `AppScreenOverlayContainerTest` 得 **OK (6 tests)**
  （`.git/qa-scratch/connected-overlay-fontscale2.log`），证伪"本批改坏了它"。
  本批改到的 5 个 connected 类在按名字定位的改名腿上全过
  （AppScreenTest 64 / MyWordsScreenTest 17 / ProfileScreenTest 6 / SettingsLicensesEntryTest 3 / SettingsScreenTest 5）。
