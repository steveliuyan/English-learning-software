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

#### 5-A 实施时发现的两句假话副标题（用户已拍板改掉，2026-11-01 落地）

改名时逐字读到 hub 词汇三行的副标题，其中两句正是 `MyWordsScreenContractTest` 里那段「三句原型说明逐条核过生产逻辑」
的 KDoc 判定**在本仓不成立**、因此合并页整句不写的那两句说法
（重算：`grep -n "不成立" app/src/test/java/com/example/englishlearning/ui/MyWordsScreenContractTest.kt`）：

- 「生词」行：「明天的词单里排到前面」——新词顺序由 `NewWordOrdering`（词头三字母交错 + 内容哈希）决定，
  生词条目在计划生成里零参与；
- 「错词」行：「按答错次数排序 · 答对两次移出」——错词按**到期先后**排，且**一次**答对即移出。

同一句话在合并页被核过不成立、在 hub 却写给用户看，是比命名更硬的问题（说法本身就是错的），
但修它改的是文案本体而不是重复项，所以本批先登记。**用户 2026-11-01 拍板「改成核实过的说法」**，随即按 TDD 落地：

- 生词行副标题 → **「提交「不认识」后自动加入」**（生产核实：`WordCardViewModel.kt:216-217`，
  反馈为 `Unknown` 时 `vocabulary.add(..., "Again", ...)`——这句是落点真空做的事）；
- 错词行副标题 → **「标记「不认识」的词 · 答对即移出」**（生产核实：`core/storage/dao/InternalLearningStatsDao.kt:106-112`
  只取「最近一次反馈 = Again」的行、按 `COALESCE(MIN(nextReviewAt), 事件时间)` 升序，
  所以既不是按答错次数排、也不需要答对两次）；
- 收藏行那句「不排进复习 · 只做手动回看」**不动**——同一段 KDoc 判它成立（计划生成只由词书内容与
  `learning_events` 派生，收藏表零参与）。

措辞取自合并页那两段空态，**一条事实不留两种口径**；闸在
`WordListEntryNamesFollowBankSegmentsTest` 三条腿：两句假话按整份 `ProfileScreen.kt`（含注释）归零、
核实说法必须与该行 tag **同一行**（否则说法漂到别处照样绿）、落点那三段同义句子仍在（`each_hub_claim_is_backed_by_the_landing_page_sentence`）。
RED→GREEN 实测：改前该条红在「明天的词单…」现值 1（expected 0 but was 1），改后同批四枚 profile 闸绿。

同源未动的一条：`ui/WorksheetSettingsScreen.kt` 的默写纸范围「今天的易错词」保留原措辞——
它指的是**当天答错的计划项**（`learning/worksheet/BuildWorksheetContentUseCase.kt` 的
`DIFFICULT_TODAY` 分支按当次反馈 `Again`/`Hard` 筛），不是词库「错词」那一册，
属于另一个概念，换名反而把两个落点说成同一个。

### 6. U-9 本批只做「不再增加新的」，不做重排（U-6 已另行拍板落地，见 §8）

- **U-6**（「本机数据 / 卸载前导出」四屏四套措辞）：收口批当时**不动**，登记为下一批首项——
  理由是备份警告是用户做数据决策的依据，措辞得逐字校，不能和对话框族、改名族挤在同一次改动里顺手改。
  用户 2026-11-01 选它作下一项，形状与判据写在 §8。
- **U-9**（本周完成两套可视化）：属版面重排，且大字号下同屏可见性未取证（盘点表 §5），本批**不动**。

### 7. U-13（提醒与发音中转页）与 U-14（文档措辞）

- **U-13 收口批不动**（原裁定：结构重排不与文案收口混批），**2026-11-02 用户拍板拆解落地，口径见 §9**。
- **U-14 做**：纯文档澄清，同本报告一起落——批 5 落的是**页头齿轮**，「暂不加」指**卡内额外入口**，
  两篇文档补限定语，防止下一任把「文档说不加、代码有齿轮」读成漂移而"修掉"一个设计。
  **已落（2026-11-02）**：决策 `2026-11-03-today-article-direct-open-and-checkin-name.md` §4
  与报告 `2026-11-03-ia-direct-article-open-and-naming-report.md` §7.5 各补一段限定语，
  点名页头齿轮的行号、可及名与批 5 报告出处。

### 8. U-6 落地：「本机数据 / 卸载前导出」这条警告只留一处措辞（用户 2026-11-01 拍板为下一项）

改前的四套说法（**改前**实测，重算：`grep -rn "卸载" app/src/main/java/com/example/englishlearning`）：

| 落点 | 原句 |
|---|---|
| `notices/NoticesPrivacyStatement.kt` 第 1 条（喂许可页 `LicensesScreen`） | 「无账户：学习数据只保存在本机，不上传任何服务器。」 |
| `ui/BackupHubScreen.kt` 页尾常驻提示条 | 「所有数据默认只在本机；卸载 App 会一并删除，请先导出备份。」 |
| `ui/LearningRecordsScreen.kt` 页尾提示条 | 「记录按天聚合，只保存在本机；卸载 App 前记得导出备份。」 |
| `ui/BackupExportViewModel.kt` 的 `DEFAULT_DISCLOSURE` 末句 | 「卸载、清除数据或设备损坏前未导出的学习记录可能无法恢复；无账户、无云同步，请自行妥善保存备份文件。」 |

决定：

- **新增唯一措辞源 `notices/LocalDataNotice.kt`**（`object` + `const val`，与 `thirdPartyPrivacyStatements()` 同目录、同纪律）：
  三条事实各写成枚常量——「只保存在本机」「无账户、也不会自动上云」「卸载 App、清除数据或设备损坏会一并删除」，
  再由它们拼出三屏各自的整句；`DEFAULT_DISCLOSURE` 末句换成拼出来的 `ExportTail` + 一枚指向许可页的出口句
  （「完整隐私说明见「关于 → 开源许可与隐私说明」。」）。用 `+` 而不是字符串模板，`const val` 才成立。
- **三屏不再自写中文警告**，只写 `text = LocalDataNotice.BackupHubWarning` 这类引用；
  各屏仍只留一句短的，不复制许可页那五条全文。
- **许可页那一句不动**：它是阶段 5 Spec 发布五要点的逐条对应，由 `NoticesPrivacyStatementTest` 按关键词钉，
  属于「完整说明」那一头；本批把**警告族**收进一处并让它指回那一页，而不是把两处并成一处。
- **措辞按 AGENTS.md 补全**：原 hub 那句只说「卸载 App」，AGENTS.md「本地优先」要求的是
  「卸载、清除数据或设备损坏」三件都说；收源时统一到全列，顺带把这条漏项补上（不是换名，是把话说全）。
  落地时又纠了一次：第一版事实写成「…会一并删除」，拼成整句读出来才发现「设备损坏」不是「删除」而是「取不回」，
  改成 AGENTS.md 自己的口径「…后可能无法恢复」——**闸门钉的那枚事实串同批改**（GREEN 阶段的措辞纠偏，
  不是先写代码后补测试：整条判据在实现前已 RED 过，理由见本节末的实施记录）。
- **判据 `LocalDataNoticeSingleSourceTest`（6 条，两套口径分开写在各自消息里）**：
  ① 单一真源存在，四枚事实各恰写一遍且声明就是事实本体（不许在同枚 `const` 里塞第二件事）；
  ② 三枚整句必须由事实 `+` 拼出（写成整句字面量=第五套说法的温床），且各句拼进它该说的事实；
  ③ 三屏各恰一处吃 `LocalDataNotice.<对应枚>`，导出声明另带一枚指向许可页的出口句；
  ④ 三条旧整句在 `ui/` 子树**含注释**归零（旧句就是下一批的抄写模板）；
  ⑤ 棘轮：`ui/` **剥注释后**没有任何字符串字面量再写「卸载」——`ui/theme/` 等处合法引用 AGENTS.md 原话
  解释设计决定，那是引用不是措辞源，所以这一条必须剥注释，与 ④ 的含注释口径是**刻意相反**的两把尺；
  ⑥ 反空转夹具：旧句、重复事实、引用枚名混用三种形状都必须被对应计数抓到。

#### U-6 实施记录（2026-11-01）

- 落地位置：`app/src/main/java/com/example/englishlearning/notices/LocalDataNotice.kt`
  （四枚事实 + 三枚整句 + 一枚出口句，全部 `const val`，句子用 `+` 拼）。三屏与导出声明改吃引用后，
  用户实际读到的三句分别是（重算：读 `LocalDataNotice.kt` 的声明行按 `+` 串联）：
  hub 与学习记录各 60 字（只差主语），导出声明尾部 63 字 + 29 字出口句。
- **RED 实况**：闸先写、先跑，6 条里 5 条红、1 条（反空转夹具）本就绿；红的理由各不相同——
  源文件缺失 / 屏引用 `expected 1 but was 0` / 旧句 `expected 0 but was 1` /
  棘轮点名三处违规（`BackupExportViewModel.kt`、`BackupHubScreen.kt`、`LearningRecordsScreen.kt` 各 1 处），
  且**没有**点名 `ui/theme/` 那处合法引用，正好证明 ⑤ 的剥注释口径生效。
- **同批改的字面量闸**：`BackupHubLayoutContractTest` 第 ⑦ 组由「整句逐字 pin」换成「引用形状 pin」，
  其第 ⑥ 组注释里复述的旧句一并换掉（否则被本批 ④ 的口径判红）；
  `BackupPageNamesFollowHubRowsTest` 两处注释里那句「别的屏写了什么」的举例改成指向单一真源的说法
  ——它举的例子已经不在那两屏了，留着就是过期地图。
- **门禁现值（本批终局）**：JVM `:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`
  BUILD SUCCESSFUL，**307 个测试类 / 2257 条 / 0 失败 / 0 跳过**（收口批的 306/2251 + 本批新增 1 类 6 条）。
  connected 在 `emulator-5574` 走 `install -r` + `am instrument`：受影响的
  `BackupEntryUnificationTest` + `LearningRecordsScreenTest` + `LicensesScreenTest` **OK (15 tests)**，
  相邻的 `InlineNoticeTest` + `ProfileScreenTest` + `SettingsLicensesEntryTest` **OK (12 tests)**；
  真机 `bf353dda` 仅 `install -r`（Success，`lastUpdateTime` 见报告）。
- **同一趟盘点里发现的一条（当时登记未动，2026-11-01 用户拍板「现在修」后已落地）**：
  `ui/ReadingFeedScreen.kt` 的来源说明那句写给用户看的是
  「App 内的来源台账见「我的 · 开源许可与来源」」，而那一页在 U-5/U-12 已逐字改名「开源许可与隐私说明」
  （`AboutScreen.kt` 的入口名与 `LicensesScreen.kt` 的页头同句），即**屏上那句指着一个不存在的入口名**。
  改前实测：`grep -rn "开源许可与来源" app/src/main app/src/test`——除 `ReadingFeedScreen.kt` 那枚活文案外，
  另有同文件两行注释也把旧页名当「既有页」在描述、`AboutScreen.kt` 与其契约测试的 KDoc 各写着
  「『开源许可与来源』改名『开源许可与隐私说明』」这条 rename 记录（那两处是历史说明，合法）。
  它属 U-5 那一族（同一页对外只留一个名字）的漏网实例，但落点在本批没碰过的阅读流屏。

  **修复口径（§8 的延伸，不是新决策）**：句子换成「App 内的来源台账见「我的 → 关于 → 开源许可与隐私说明」」
  ——路径写到实际落地的那一跳（关于是中间层，U-12 裁定两跳保留），不再用省略中间层的「我的 · X」写法；
  同文件两行注释同步改掉旧名（**含注释归零**口径：旧名留在注释里就是下一批的抄写模板，
  rename 历史由决策文档承载，不在生产注释里复读）。判据放进 `ReadingFeedScreenContractTest` 两条：
  ① 死页名在本屏整份源码（含注释）归零，且来源说明**恰一处**逐字引用「我的 → 关于 → 」+ 许可页现名——
  现名不写死、从 `LicensesScreen` 页头现读现比，下次谁再改名这条会红着提醒两处以一致的方式改；
  ② 反空转：许可页 `AppScreenHeader(` 恰一处（多枚时「取第一枚」会静默抽错）、抽名正则对合成页头形状
  真取得出、合成 stale 句必被计到。**活名那条腿的 RED 被归零断言先红遮蔽**，实施时以「改生产为同义变体
  （「我的 · 」写法）→ 该腿独红 → 还原」的变异复跑补上了咬合证据。
  两处 KDoc（`ReadingFeedScreen.kt` 文件头、契约测试头）里的原型引用同步改述为「原型用旧名、本屏按现名」。
  JVM 门禁 **307 类 / 2259 条 / 0 失败**（U-6 收口的 307/2257 + 本条新增 2）；connected 见工作报告。

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
- **U-6 续批（2026-11-01 拍板后另一次改动）改生产**：新增 `notices/LocalDataNotice.kt`；
  `BackupHubScreen.kt`、`LearningRecordsScreen.kt`（含其 KDoc 里那句原样引用）、`BackupExportViewModel.kt` 的
  `DEFAULT_DISCLOSURE` 改吃单一真源。**改测试**：`BackupHubLayoutContractTest` 那条按整句 pin 的判据
  换成 pin 引用形状（其 KDoc 与注释里复述的旧句同批换掉，否则被本批自己的口径判红）。
- **不动**：hub 台账行数（U-13）、周视图版面（U-9）、
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

## 9. U-13 落地：拆「提醒与发音」纯中转页，hub 直达两子屏（用户 2026-11-02 拍板）

改前的导航形状（**改前**实测，重算：`grep -n "ReminderSpeech\|reminder_speech" app/src/main`）：
hub 一行「提醒与发音」（`ProfileScreen.kt:97`，tag `profile_open_reminder_speech`）→
`ReminderSpeechScreen.kt`（页头 + 两枚 `EntryRow`，本层不读仓库、不写状态）→
`ReminderSettingsScreen` / `SpeechSettingsScreen`。用户到提醒设置要点两跳、到发音设置要点两跳，
中转层唯一承重的信息是发音行副标题那句「当前供应商：X」。

决定：

- **hub 拆一行成两行直达**：「提醒与发音」行退役。「学习提醒」（tag `profile_open_reminder`，
  副标题「只在当天没完成计划时提醒 · 默认关闭」，副标题真值与 U-7 同源）进**学习组**——
  提醒本来就是学习安排的一部分；「语音朗读」（tag `profile_open_speech`，副标题「当前供应商：X」，
  吃 `speechState.engineStatuses().currentProvider`）进**应用组**挨着「AI 服务」——
  发音走 MiMo 时依赖那里配的密钥，两行是近邻。台账 **13 → 14 行**，组行数 3/5/5 → 3/6/5。
  两行副标题沿用中转页上已校过的句子（U-7 裁定过「导航页那行只说何时提醒 + 默认关闭」，
  拆解后这两句话各自落到 hub 行上，规则仍只讲一遍）。
- **`SpeechEngineStatuses` + `engineStatuses()` 搬家**：它们不是中转页的私产——
  发音屏摘要（`SpeechSettingsScreen.kt:134`）与 hub「语音朗读」行副标题都要吃这一份真值。
  从 `ReminderSpeechScreen.kt` 搬到 `SpeechSettingsScreen.kt`（既有消费点，同包移动零 import 改动），
  KDoc 的「原住 SettingsScreen」沿革补一句去向。
- **中转页整页退役**：`ReminderSpeechScreen.kt` 删除；`AppScreen.kt` 的 `showReminderSpeech` 状态、
  BackHandler、挂载分支与 `ProfileScreen` 的 `onOpenReminderSpeech` 接线拆除，
  两枚 hub 行直连 `showReminderSettings` / `showSpeechSettings`（VM 缺席兜底口径照抄 AI 配置那枚）。
- **判据（同批改的闸 + 新增闸）**：
  - `ProfileHubLedgerTest`：13 行清单改 14 行（行名、tag、组行数 3/6/5 全部逐字同步）；
  - `ProfilePageContractTest`：`onOpenReminderSpeech` 接线行换成两枚新行的直连接线；
  - `ReminderRuleOnceContractTest`：`hub` 腿现读的文件从 `ReminderSpeechScreen.kt` 换成
    `ProfileScreen.kt`——「导航页不复读调度上限」的判据**语义不变**（现在钉的是 hub 行副标题），
    「两条屏侧事实必须留着」同理；
  - 新增 `ReminderSpeechTransitRemovedTest`：`ReminderSpeechScreen.kt` 文件不存在、
    `reminder_speech` 家族 tag（`reminder_speech_screen` / `reminder_speech_open_*`）与
    `onOpenReminderSpeech` / `showReminderSpeech` 在 main 全树归零、
    「提醒与发音」这个页名在 main 剥注释后归零（设置页那句「都在「我的」里」的说明句同批换名）；
  - connected：`ReminderSpeechScreenTest`（4 条）删除，**它的承重不丢**——
    ① 两行各归各的子屏（少接一环 = 死行）由 `ProfileScreenTest` 两条新用例从 hub 直达两子屏承重；
    ② 发音行副标题报真实当前供应商 → 挪进 `SpeechSettingsScreenTest`（真值源同屏）；
    ③ 「待接入」厂商名不回潮 → 与 ② 同文件承重。大字号档（`fontScale = 2f`）那一条随中转页退役，
    hub 行的字号护栏由 `AppScreenOverlayContainerTest` 家族的既有双字号档覆盖；
  - `AppScreenTest` 里 7 处经中转页的跳转链（`profile_open_reminder_speech` → `reminder_speech_open_*`）
    全部改直跳。
- **口径申明**：这不是「删功能」——两屏本体、两套 ViewModel、权限时序一字未动；
  退役的只是那层既不读仓库也不写状态的纯导航壳。盘点表 §3 的保留清单不受影响。

#### U-13 实施记录（2026-11-02）

- **RED 实况**：先写判据先跑——新闸 `ReminderSpeechTransitRemovedTest` 4 条里 3 红
  （文件在场 / 页名散在 / 真值派生还住中转页），反空转夹具本就绿；
  台账 13→14、宿主接线、hub 腿改址三条旧闸各自红在预期行。反空转夹具第一版把
  `onOpenReminderSpeech` 期望写 1（旧形状里参数定义 + lambda 引用天然 2 处），
  改判据的错判据同批修掉，不放宽。
- **判据搬家**：`ProfileHubLedgerTest` 的「两行导航页」整条删除（它的存在前提没了），
  「宿主四处站点」收窄到词书内容包一枚；`ReminderRuleOnceContractTest` 的 hub 腿
  从 `uiScreenSource("ReminderSpeechScreen.kt")` 改成现读 `ProfileScreen.kt` 剥注释——
  「导航层不复读调度上限」语义不变。
- **connected 侧同批改**：`ReminderSpeechScreenTest` 4 条整删，承重按 §9 分配落地
  （`ProfileScreenTest` 行清单改两枚新 tag 直达；供应商真值两条归 `AppScreenTest`）；
  `AppScreenTest` 删中转页驱动 `openReminderSpeechPage` 及零调用的 `systemBackToTheHub`。
- **GREEN 期自查出的两处测试侧错改**（隔离复跑定位，均非生产缺陷）：
  ① 删驱动时残留一环——某用例在建档后（学习 tab）直接点 hub 行，少了切 tab 一步，
     且更早一版误留了「无覆盖层时按系统返回」的调用（直接把 Activity 退了）；
  ② 旧版「回 hub 再点」的用例里那枚回退动作在 U-13 后无覆盖层可退，同因失活。
  两条均在 `emulator-5554` 隔离复跑 OK 后并入全量复跑。
- **门禁现值（本批终局树，`.git/qa-scratch/final-jvm-gate-2026-11-02-u13.log`）**：
  `assembleDebug` + `testDebugUnitTest` + `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL，
  **308 类 / 2262 条 / 0 失败 0 跳过**（收口基线 307/2259：+1 新闸类 4 条，删旧中转页闸 1 条）。
  connected 四类 `ProfileScreenTest` + `AppScreenTest` + `SettingsScreenTest` + `SettingsLicensesEntryTest`
  **OK (78 tests)**；真机 `bf353dda` `install -r` Success（`lastUpdateTime` 见报告）。
