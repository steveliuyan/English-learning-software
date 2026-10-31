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
  改调共享件。`ImageStudio` 的两段式正文（图片 / 文本分开说）通过给共享件加**一枚可选正文覆写入参**满足，
  不为此留一份私写对话框。
- **正文措辞**只由 `outboundConfirmBody(host, kind)` 生成。屏侧只允许决定「发出去的是哪种载荷」，
  不允许自己拼句子——现状 `SentenceAnalysisScreen.kt:255` 那一份漏了「并可能产生费用」，
  这正是四套措辞的必然结果。
- **策略判定只留域层** `ai/AiOutboundConfirmation.kt:51 requiredConfirmation(...)`（6 处调用、5 个用例类）。
  UI 侧 `components/OutboundConfirmDialog.kt:48 mustAskEveryTime(...)` 与 `PayloadKind`（`:35`）
  改为**基于** `AiPayloadKind`：枚举不再第二份，纯函数不再自称权威却零调用。
  若共享件的可见性确需一个 UI 侧便捷判定，它必须委托域层那枚，而不是平行再算一次。
- **配套闸**：`SharedComponentUniquenessRatchetTest` 现有的 `(?m)^fun X(` 锚点只数顶层**公开**定义，
  四份缩进的私有同形态它数不到——所以它才漏掉本次事故。补一枚「对话框族」窗形状判据
  （在 `OutboundConfirmDialog(` 的调用窗口内禁止再出现同名私写定义），否则下一批还能写第五份。

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

- **改生产**：`components/OutboundConfirmDialog.kt`（正文覆写入参 + 委托域层）、
  四屏删私写对话框、`ai/AiFailure.kt`、`ui/AiFeature.kt`、四个 AI 屏 + `PhotoTranslationScreen` 的文案、
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
