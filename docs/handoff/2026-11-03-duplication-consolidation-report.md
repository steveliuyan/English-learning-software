# 2026-11-03 重复项整合（U-1..U-12 采纳项 + U-14 文档澄清）交付报告

决策文档：`docs/decisions/2026-11-03-duplication-consolidation.md`（含 §5-A 挂账与「实施记录」）
计划文档：`docs/superpowers/plans/2026-11-03-duplication-consolidation.md`
盘点依据：`docs/design/2026-11-03-duplication-audit.md`（U-1..U-14 的实测证据）
分支：`stage-5-ui-r2-learning-path`（上一批：`docs/handoff/2026-11-03-ia-direct-article-open-and-naming-report.md`）

## 1. 本次目标

用户原话：**「并回来，然后你要看一下目前有哪些功能重复啊，或者说哪些页面有重复选项，或者是重复指引页面之类的，你要整合合并整理」**。

- 「并回来」（收尾屏第三枚「去读今日文章」入口并入同一条直达判定）已在上一批落进
  `a1a8ff8` 并记入批 6 报告 §8，本批不重做。
- 后半句 = 全站重复项盘点并按拍板范围整合。盘点收了 14 条，**本批采纳 U-1..U-5、U-7、U-8、U-10、U-11、U-12、U-14**；
  **U-6 / U-9 / U-13 明确不动**（理由写在决策 §6/§7，不是遗漏）。

## 2. 完成了什么

前五枚提交（决策 / 计划两份文档含在 `0f07b54` 里）净 `58 files changed, 1501 insertions(+), 492 deletions(-)`，
重算：`git diff --shortstat 0f07b54~1 9432635`；本报告与决策文档 §5-A/实施记录在其后的文档腿提交里（纯文档，不进这份短统计）。

### 2.1 出站确认：对话框、措辞、策略各只留一份权威（U-1 + U-2 + U-3，`25b9a58`）

- 四屏（句子分析 / 拍照翻译 / 词库 AI 问答 / 图片工坊）里签名逐字相同的私有 `OutboundConfirmationDialog` 删除，
  改调共享件 `ui/components/OutboundConfirmDialog.kt`；图片工坊的两段式正文由共享件 `payloadKind` 分派 +
  一枚可选 `note` **追加**实参满足——实施时把原稿的「正文覆写入参」收窄成「追加」，因为一旦允许整句覆写，
  单一措辞权威当场作废。
- 正文只由 `outboundConfirmBody(host, kind)` 生成；改前句子分析那份漏了「并可能产生费用」，
  这就是四套措辞的必然结果。
- 策略判定只留域层 `ai/AiOutboundConfirmation.kt requiredConfirmation(...)`；共享件那份纯函数与
  第二份 `PayloadKind` 枚举退役、改吃域层 `AiPayloadKind`（UI 侧不再自称权威却零调用）。
- 两枚连带后果按决策记账：对话框内按钮墨色改由 `ConfirmDestructiveDialog` 的角色表决定（不再按能力域取色，
  `DangerInkPlacementContractTest` 改成钉三件事）；玻璃面板花名册少四行（`GlassDialog` → `OutboundConfirmDialog`）。

### 2.2 指引地图指向真存在的落点 + 对外只留一个名字（U-4 + U-5，`bd020e8`）

- 「设置 · AI」全站改为 **「我的 → AI 服务」**（含注释——不改注释就等于给下一批留一份可抄的旧地图）。
- `openAiProfilesOrSettings` 的 `showSettings = true` 兜底在 profile 装载失败时会把用户送到只剩「外观」的设置页，
  改成落到「我的」hub，不留「文案说 hub、代码送设置页」的新漂移。
- 发音屏那枚「AI 服务与密钥」对外名收成「AI 服务」；「去配置 AI 服务」这类「动词 + 名」与 TalkBack 可及名保留动作语义。

### 2.3 规则只讲一遍、死行删掉、两枚主操作留一枚（U-7 + U-10，`5357a96`）

- 提醒屏「每天最多一次 / 系统可能稍有延迟」原讲四遍（两分支页头副标题 + 卡内说明 + 导航页复读），
  权威留在卡内那句并**用位置窗钉住**「说明坐在 `SoftCard` 里、紧挨 `Switch(` 900 字符内」；
  导航页只留「何时提醒 + 默认关闭」两条屏侧事实。
- 原型抄来的「重复 / 周一到周日 / 每天」只读行整行删除：无 `onClick`、`showChevron = false` 的一行
  读起来像「这里能改重复频率」，而本仓 `ReminderSchedulingPolicy` 固定每天最多一次、没有按星期配置的能力——
  **把固定行为摆成设置项就是谎称可改**，它承载的信息本来就在同一张卡的那句说明里。
- 学习计划页同屏并立页头保存与吸底「保存计划」：留吸底（拇指区、与全站吸底主操作一致），页头只留返回，
  `SetupHeaderSave` 组件整段退役 + 随之失效的 `clickable` import 删除；提示语同步为「改完点屏底『保存计划』」。

### 2.4 一屏一名，hub 行名跟落点对齐（U-11 + U-12 + U-8，`9432635`）

- **U-11**：备份 hub「导出学习数据留档」那一点进去页头是另一个名字。hub `BackupHubScreen` 的 KDoc 自称
  「四枚行标题在本屏是唯一真值」，就照这句话**改落地页**（导出页两处标题统一，旧页名整份文件归零含注释）；
  另两行按「hub 带括号限定、页面取括号前那半」的既有形状对齐。
- **U-12**：「我的」hub 行「关于与许可」把两跳说成一跳（第三方清单不住在关于页里）。行名收窄为「关于」，
  **两跳保留**——直跳许可页会让「关于」这层没入口，删掉关于页属结构重排（同 U-13，本批不动）。
- **U-8**：同一批词三套名字（hub「收藏的词 / 生词本 / 易错词」、词库分段「收藏 / 生词 / 错词」、
  合并页副标题与段卡标题）。以**分段短名**为准，hub 三行 + 合并页副标题 + 段卡标题逐字对齐；
  入口仍按旧 D-6/D-7 保留双入口（收的是名字不是路径），「加入/移出生词本」这类功能动词与收藏段说明句原样保留。
- 连带：`ProfileHubLedgerTest` 13 行台账换名；`AppTab` 屏名列表同步；
  `ProfilePageHomeContractTest`「统计格不许长出一枚收藏标签」的扫描面从整份源码**收到三行 `ProfileStatCell(`**
  （另加「恰 3 行」防空转）——这是判据范围修正，不是放宽。

### 2.5 测试资产

- **新增 6 枚 JVM 闸**：`OutboundDialogFamilyTest`（跨文件份数与措辞）、`AiGuidanceMapNamingTest`（U-4 地图）、
  `ReminderRuleOnceContractTest`（U-7）、`BackupPageNamesFollowHubRowsTest`（U-11）、
  `AboutRowNameIsJustAboutTest`（U-12）、`WordListEntryNamesFollowBankSegmentsTest`（U-8，
  期望名从 `WordBankSection` 枚举体**解析**而非写死，分段改名时闸跟着走）。
- **改的既有 JVM 闸**：`DangerInkPlacementContractTest`、`GlassPanelInkPairingContractTest`、`ConfirmDestructiveWordingTest`、
  三屏版式契约的 `GlassDialog( == 1` → `OutboundConfirmDialog( == 1` + `GlassDialog( == 0`
  （`ImageStudio` / `PhotoTranslation` / `WordQa`）；`SentenceAnalysisLayoutContractTest` 的药片计数随私写对话框
  退役下调（现值：全屏 3 枚 = 主操作 1 + 复制/朗读 2，取消/确认两枚归共享件）；
  `LearningSetupLayoutContractTest` 页头保存窗口判据换成「`action =` 归零 + `SetupSaveBar(` 恰一枚 + 组件整份退役」；
  `ReminderSpeechPrototypeAlignmentContractTest` 改钉死行四枚字面量归零。
  `SharedComponentUniquenessRatchetTest` **未改**：它的 `(?m)^fun X(` 只数顶层公开定义（缩进过的私有同形态
  本来就在它 KDoc 里写明归族规则管），所以补的跨文件份数闸落在 `OutboundDialogFamilyTest`，
  取舍理由记在决策 §1「配套闸」。
- **改的既有 connected**：`OutboundConfirmDialogTest`、`ImageStudioScreenTest`、`SentenceAnalysisScreenTest`、
  `WordAiQaScreenTest`、`NotConfiguredCardTest` 按新对话框形状/新名同步（明细见决策「影响」）。
  另有 JVM 侧 `SpeechSettingsViewModelTest`（U-5 对外名）同批改字面量。
- **connected 改名腿**（5 个类）：`ProfileScreenTest` / `SettingsScreenTest` / `SettingsLicensesEntryTest` /
  `MyWordsScreenTest` / `AppScreenTest`。`SettingsLicensesEntryTest` 那条按文字的反向钉换成新名，
  否则改名后旧名全站不存在、判据变**永真空转**。

### 2.6 追补：§5-A 两句假话副标题已按拍板改掉（收口后同会话）

收口问答里用户选「**改成核实过的说法**」，随即按 TDD 落地（RED 实测：闸红在「明天的词单…」现值 1，
`expected 0 but was 1`；改后同批四枚 profile 闸绿）：

- 生词行副标题 → **「提交「不认识」后自动加入」**。核实出处 `ui/WordCardViewModel.kt:216-217`：
  反馈为 `Unknown` 时 `vocabulary.add(..., "Again", ...)`。
- 错词行副标题 → **「标记「不认识」的词 · 答对即移出」**。核实出处
  `core/storage/dao/InternalLearningStatsDao.kt:106-112`：只取「最近一次反馈 = `Again`」的行、
  按 `COALESCE(MIN(nextReviewAt), 事件时间)` 升序 ⇒ 既不按答错次数排，也不需要答对两次。
- 收藏行「不排进复习 · 只做手动回看」**不动**（同一段 KDoc 判它成立）。

闸在 `WordListEntryNamesFollowBankSegmentsTest` 三条新腿：假话按整份 `ProfileScreen.kt`（含注释）归零、
核实说法必须与该行 tag **同一行**（防说法漂到别处照样绿）、落点那三段同义句子仍在。
JVM 读数因此从 2248 → **2251 条 / 0 失败**（306 类不变）；`emulator-5574` 复装新包后
`ProfileScreenTest` **OK (6 tests)**；真机 `bf353dda` 同包复装。

## 3. 验证记录

### 3.1 TDD 与 RED→GREEN 实况（五条值得记的）

- U-12 首跑被**自己的新注释**判红：`ProfileScreen.kt` 的 U-12 说明里引用了许可入口那句原话，
  而改名类闸按整份源码扫（注释里复述也算命中）。改文档写法绕开原句，并在闸消息里把这条口径写明。
- U-7 的「说明坐在卡里」最初按 `SoftCard(` 计数恒 0——本屏是尾随 lambda 写法 `SoftCard {`，
  换成按位置取窗口（`lastIndexOf("SoftCard {", noticeAt)`）。
- U-8 的枚举解析正则最初写成三引号原始串，把测试文件自身解析搞坏；换成转义普通串后 RED 才成立。
- `ProfileStatCell` 行窗口取法用 `(?m)^\s*` 会吞换行导致起点漂到空行，改 `(?m)^[ \t]*` 并要求「恰 3 行」防空转。
- §5-A 落地时的 RED 里发现一条自己的坑：`Regex.escape(x)` 得到的是 `\Qx\E` **模式串**，
  拿它当 `String.contains` 的参数永远不命中 ⇒ 「tag 与说法同一行」的计数恒 0、判据会**空转**。
  按子串取就写普通字面量，按正则取才用 `Regex(Regex.escape(...))`（本仓夹具 `occurrences` 走后者）。

### 3.2 门禁（终局树重跑）

- `:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` **BUILD SUCCESSFUL**（2m45s，
  `compileDebugKotlin` 为 executed 非 UP-TO-DATE，即 `clickable` import 删除后确实重编过）。
  日志 `.git/qa-scratch/final-jvm-gate-2026-11-01.log`。
- JVM 现值：**306 个测试类 / 2248 条 / 0 失败 / 0 跳过**（§2.6 追补后为 **2251 条**，同一日志口径的
  `.git/qa-scratch/subtitle-fix-jvm-gate.log`）。重算：`python` 汇总
  `app/build/test-results/testDebugUnitTest/*.xml` 的 `tests/failures/errors/skipped`。
- detekt/ktlint 按仓库口径**不作闸**（存量基线债 1500+，非本批引入）。

### 3.3 connected（只在 `emulator-5574`，`adb install -r` + `am instrument`）

- 全量 **714 条 / 11 失败**（`.git/qa-scratch/connected-full-2026-11-01.log`）。11 条**全部是设备前置条件缺失**，
  与本批改动的屏无交集：
  - 2 条 `DevicePairedRestoreE2ETest`——`/data/local/tmp/qa-b1-package.zip` 已按红线清掉（该目录只作临时输入通道）；
  - 2 条 `KokoroEngineAvailabilityTest`——Kokoro 外部模型资产未推送到设备；
  - 3 条 `AppScreenOverlayContainerTest`——设备 `font_scale` 现为 1.0，护栏自带「scale 未生效即红」的反空转；
  - 4 条 `WordBookPackTest` / `WordCardFieldsProbeTest` / `WordMeaningProbeTest`——设备未配默认文本服务，
    且**不授权**为跑测试发付费 AI 请求。
- 把 `font_scale` 置 2.0 单跑 `AppScreenOverlayContainerTest` ⇒ **OK (6 tests)**
  （`.git/qa-scratch/connected-overlay-fontscale2.log`），跑完已复位为 1.0——
  同 3 条失败在 10-31 的 `batch4-ui-package-1.0.log` 里就是同样形状，证伪「本批改坏了它」。
- 本批改到的 5 个 connected 类改名腿全过（点号无 F/E）：AppScreenTest 64 / MyWordsScreenTest 17 /
  ProfileScreenTest 6 / SettingsLicensesEntryTest 3 / SettingsScreenTest 5。
- **真机 `bf353dda`**：`adb install -r` **Success**（345 MB / 72.5s），仅复装待手工试用，未 instrument。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`；本批代码腿 5 枚提交：
  `0f07b54`（决策 + 计划 + U-14 澄清）、`bd020e8`（U-4 + U-5）、`25b9a58`（U-1..U-3）、
  `5357a96`（U-7 + U-10）、`9432635`（U-11 + U-12 + U-8）；文档腿是本报告 + 决策 §5-A/实施记录，
  外加两枚纯订正（本报告 Git 账的数字口径）。
- **未推送**。领先远端的枚数用
  `git rev-list --left-right --count origin/stage-5-ui-r2-learning-path...HEAD`
  现算为准（文档腿每多一枚，写死的数字就漂一枚；本段落笔时读到 20 枚）。
  按红线**不合 `main`、不推送**，等用户说「推送」；用户已交代推送失败时等他开代理再推。
- 提交全部带 git 身份 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`。
- 工作区无其他未提交遗留；QA 日志与截图都在 `.git/qa-scratch/`（不入库）。

## 5. 未完成 / 遗留问题

- ~~**等用户拍板**：hub 词汇三行里两句副标题是**错的**~~ —— **已拍板并改掉**（见 §2.6，同会话追补）：
  生词行换成「提交「不认识」后自动加入」、错词行换成「标记「不认识」的词 · 答对即移出」，
  两句的核实出处分别是 `WordCardViewModel.kt:216-217` 与 `InternalLearningStatsDao.kt:106-112`。
  默写纸范围「今天的易错词」**仍保留**——按实测它是另一概念（`BuildWorksheetContentUseCase` 的
  `DIFFICULT_TODAY` 筛当天 `Again`/`Hard` 的计划项），不是词库「错词」那一册。
- **U-6**（「本机数据 / 卸载前导出」四屏四套措辞）登记为下一批首项——备份警告是用户做数据决策的依据，措辞得逐字校。
  **已落地**（2026-11-01，见 `2026-11-01-local-data-warning-single-source-report.md` 与决策 §8）。
- **U-9**（本周完成两套可视化）属版面重排，大字号下同屏可见性未取证，未动。
- **U-13**（「提醒与发音」中转页）未动：拆它 = hub 台账 13→14 + connected `ReminderSpeechScreenTest` 4 条整条改测，
  属结构重排，等用户在盘点表上单独拍板。
- **不得重做**：盘点表 §3 六条「看着重复但该留」（今日文章三枚入口、`.wbpack` vs `.elpbak`、
  `BackupExportScreen` 两进入点等）。
- 更早批次的挂账仍在：Gradient 摘档、Task 42、`MyWordsScreenTest` 的 font_scale 2.0 债、QA 第三轮任务书、
  AI 能力缺口批（每次弹确认 / Vision 空转 / 生图不落盘 / 缺连接测试与 nsc）。

## 6. 下一任接手须知

1. 先读 `AGENTS.md` → `docs/decisions/2026-11-03-duplication-consolidation.md`（含实施记录）→ 本报告 →
   `docs/design/2026-11-03-duplication-audit.md`（哪些已收、哪些是**有意保留**）。
2. **两套扫描口径**（写在各自测试消息里，别混）：措辞/单一权威类走 `countMatches`（剥注释，
   本仓惯例是把权威措辞引一遍再解释为什么）；**改名类必须连注释一起扫**（`File.readText()` +
   `Regex.escape` 计数）——注释里的旧名就是下一批会照抄的模板。推论：改名类闸的说明文字里**不许拼出被禁字面量**。
3. connected 的 11 条红是**环境缺项**，不是回归。要全绿需先补：`/data/local/tmp` 种子包（认证恢复 e2e）、
   Kokoro 外部资产、`settings put system font_scale 2.0`（跑完复位）、设备上的默认 AI 文本服务（付费，需授权）。
4. 构建：本机 Gradle 8.13（`--offline --no-daemon --no-build-cache`，从 wrapper 路径取 `gradle.bat`）；
   **禁止** `connectedDebugAndroidTest`（清设备数据），**禁止**在真机 `bf353dda` instrument；
   原型目录 `C:\Users\steveliuyan\AppData\Roaming\Open Design\...` 只读。
5. 坑：`grep -E` 里带 `(` 会让 Git Bash 断，用 Grep 工具或 `grep -F`；管道退出码要 `; exit ${PIPESTATUS[0]}`。

## 7. 用户确认过的决策

- 「并回来」+「你要看一下目前有哪些功能重复……你要整合合并整理」= 授权整合；具体范围由决策文档按 U-1..U-14 拍板。
- **U-13 本批不做**（结构重排另议）；**U-6 / U-9 本批不做**（只要求不再增加新的）。
- U-11 的方向 = **改落地页标题**（信 hub 那句「本屏是唯一真值」），不改 hub。
- U-12 的方向 = **改行名为「关于」并保留两跳**，不直跳许可页、不下线关于页。
- U-8 的方向 = **以词库分段短名为准**（收藏 / 生词 / 错词），双入口保留。
- §5-A 两句错副标题 = 用户选 **「改成核实过的说法」**（否决「留现状」与「只删副标题」），
  下一项整合用户选 **U-6**（否决「先推送」「先真机走查」「转 AI 缺口批」）。
- U-10 的方向 = **留吸底那一枚**提交入口，页头只留返回。
- 盘点表 §3 六条「该保留的重复」不动；§5-A 那两句错副标题**等用户点头**再改。
- 不合 `main`、不推送，等「推送」；推送失败等用户开代理后再推。
