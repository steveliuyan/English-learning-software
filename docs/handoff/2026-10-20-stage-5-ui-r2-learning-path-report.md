# 2026-10-20 批 2（stage-5-ui-r2-learning-path）阶段工作报告

## 1. 本次目标

UI 重设计批 2「学习路径与壳」六席（14A-i / 14A-ii / 15 / 16 / 17A / 17B）全部关账后，做 Task 18 的可自动化那一半（Task 18-i）：真全量门控 + 棘轮 seed 逐张确认、批 2 受体面 connected 全清单（只 `emulator-5574`）、验证文档更新（门禁节 / AC-R-07 / AC-R-12 / AC-R-20）、结转账文档级核销与本阶段报告。真机目检十项与真 TalkBack 引擎实测归 18-ii（控制者与用户），本席未做也不替人眼判定。

## 2. 完成了什么

### 2.1 批 2 任务序列与提交（全在本分支；2026-10-21 经用户批准已推 `origin`，见 §4）

| 任务 | 交付 | 提交 | 验收锚 |
|---|---|---|---|
| 14B 今日计划屏重排（AC-R-20） | 8 枚整宽按钮 → 1 CTA + 入口行组；数字一处权威；查词出口四态常驻；排印覆写禁令 | `4d3c2e8`、`02f84da`（修复轮 I-1/I-2）、`920eabe`；裁决 `a68256e`（R-14.3） | `TodayPlanScreenTest` connected OK (21)；门控 225/1666 |
| 14A-i PillButton 三档配对 | 容器-前景按主题配对、域色静态形参改 `AppDomain`；组件体 ink 消费点源码门 | `42e9c14`、`51cf5f5`；配方订正文档 `4a42cb7`（R-14.4） | `PillButtonInkTest` 7/0；门控 226/1673 |
| 14A-ii PillButton 设备取色 | 三条设备取色用例上桩，两发动码变异证明有牙；陈旧断言历史订正 | `d4baf26`、`edacaae`；关闭记录 `ff3b69f`（R-14A-ii.1） | connected 14 类 / 176 用例 / 0 失败 |
| 15 词卡屏 | 回忆-揭晓流程接线、退出即停播、发音药片统一共享 | `369395b`、`bab0b45`、`badf707`（契约补钉） | `WordCardScreenTest` OK (17)、`AppScreenTest` OK (46)；门控 227/1681 |
| 16 词卡详情屏发音件归一 | 私有发音件删除换共享 `SpeakerButton`、页头/词行组件接入；尾缀槽墨补锚 | `0414334`、`4a53fb0` | `CardDetailScreenTest` OK (25)、`CardDetailPronunciationControlsTest` OK (5)；门控 228/1689→1690 |
| 17A 底导 duotone | 四 tab 换 duotone 字形、选中态主题化、标签墨锚体内夹击 | `0a5163b`、`0ed3984`、`7d2e299`、`7fbe99e`；拆席裁决 `c932067`（R-17.1） | `AppBottomBarTest` OK (8)、`AppScreenTest` OK (46)；门控 230/1702 |
| 17B AppScreen 浮层统一 | 六枚裸 `AlertDialog` 改接两路共享浮层档（路 A 4 枚接通、路 B 2 枚并玻璃档）；大字号护栏改设备级前置+行高哨兵 | `26a68b1`、`0d60658`；裁决 `c79c9f1`（R-17B.1） | `AppScreenOverlayContainerTest` font_scale=2.0 下 OK (4)；门控 231/1707→1708 |
| 18-i 批 2 收口（本席） | 门控重算 + connected 全清单 + 验证文档 + 结转账核销 + 本报告 | 本文档提交 | 见 §3 |

### 2.2 本席改动清单（全部文档级，零代码改动）

- `docs/verification/ui-redesign-2026-10.md`：门禁与基线节按 2026-10-20 实跑更新（含三张棘轮 seed 逐张确认）；connected 段新增批 2 全清单；AC-R-07 写实批 2 已覆盖屏证据并点名缺项；AC-R-12 写清「契约+走查已做、真引擎未做」；新增 AC-R-20 行。
- `docs/handoff/2026-10-20-stage-5-ui-r2-learning-path-report.md`：本报告。
- `.superpowers/sdd/2026-10-17-ui-redesign/task-16-report.md` §3：m-2 措辞订正——初版「提示条族 seed 未含本文件」失实，`CardDetailScreen.kt` 实际在错误提示条族 seed（`SharedComponentUniquenessRatchetTest:197`）；「未触该面、seed 零变动」结论不变。
- `docs/superpowers/plans/2026-10-17-ui-redesign.md:691`：`drawTabGlyph` 悬空引用订正为历史口径（该函数已随 Task 17A 删除，见同文件 :1739），只改文档。

## 3. 验证记录（2026-10-20，Task 18i 现场实跑）

### 3.1 门控（真全量、串行前台）

- 命令：`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --rerun-tasks --no-build-cache --offline --no-daemon` → `t18-gate-full.log`，`BUILD SUCCESSFUL in 4m 20s`，59/59 任务全执行（增量跑不作计数背书）。
- 计数读盘（grep/awk，禁 python heredoc）：**231 suites / 1708 tests / 0 failures / 0 errors / 0 skipped**；XML 文件数 231 与 suite 数相等（无孤儿）。与 17B 关账基线逐位相等，「只准增不准减」成立。
- 批 2 演进：225/1666（14B）→ 226/1673（14A-i/ii）→ 227/1681（15）→ 228/1690（16）→ 230/1702（17A）→ **231/1708**（17B/本席）。
- 三张棘轮 seed 逐张确认（不迁、不改判据，绿由本次门控背书）：
  - `RawColorLiteralRatchetTest`：`"AppScreen.kt" to 2` 在册，形状台账同文件两行在册（两枚 `Color(0xFFCDEFE1)` 折叠行）。
  - `DarkTokenConsumptionRatchetTest`：`"AppScreen.kt" to 3` 在册；全表重算命令现跑输出快照 `files=29 hits=98`（文档不写死站点数，命令为准）。
  - `SharedComponentUniquenessRatchetTest`：页头族收口判据成立——`AppScreen.kt` headline 读点现场 grep 为 0。

### 3.2 connected 清单（只 `emulator-5574`，双 APK `adb install -r` + `am instrument -e class`）

**25 类 / 278 用例 / 0 失败**——口径订正：这是「批 2 改动触达的 25 类子集」，**不是 connected 全集**（现场 `app/src/androidTest` 含 `@Test` 的类共 97 个，可重算 `grep -rl "@Test" app/src/androidTest/java --include=*.kt | wc -l`）；点名在册但**本席未实跑**的是 `WordbookPickerHeaderTest`（现场 3 枚 `@Test`，行首锚可重算 `grep -cE "^[[:space:]]*@Test" app/src/androidTest/java/com/example/englishlearning/ui/WordbookPickerHeaderTest.kt`；控制者一度数成 4，是把该类 KDoc 里「三条各一个 `@Test`」一起数了），页头族，批 2 的 17B 改过页头接入点，须补跑——**控制者已于 2026-10-20 补跑 `OK (3 tests)`**（`fix18c-controller2-connected-WordbookPickerHeaderTest.log`）。（日志 `t18-connected-*.log`，逐类末行已贴进验证文档）。规程执行：每轮前后 `settings get system font_scale` 回显入日志，收工值 `1.0`；跑前 `dumpsys user` 为 `RUNNING_UNLOCKED`；真机 `bf353dda` 全程未碰、未装测试包、零 instrument、截图零入库；`connectedDebugAndroidTest` 未使用。

- `AppScreenOverlayContainerTest` 两枚大字号用例的运行前置是**设备级** `font_scale=2.0`：未设前置自然 scale 整类 `Tests run: 4, Failures: 2` 为登记在案的响亮红样（护栏不空转，两份日志均留档），置 2.0 后整类 `OK (4 tests)`。
- `AppScreenTest` 整类一次 `OK (46 tests)`；已登记 known-flaky 的 `AppScreenTest:1686`（`mandatorySetupOffersNoCancelEntry` 注入抖动）本轮**未复现**。
- brief 点名但现场无 connected 侧此类：`AppTabTest`（仅存 JVM 侧，未拿别的类顶数）。
- 用例数无下降类：14A-ii 的 14 类 176 例全部原样复跑通过；本席 25 类 278 例的增量 102 例来自后续受体面（`AppScreenTest` 46、WordCard 族 24、`AppBottomBarTest` 8、`CheckInScreenTest` 7、浮层类 4、三档对话框族 8、`ArticlePronunciationControlsTest` 5）。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`；本席接手 HEAD `0d60658`，工作树接手时干净。
- 本席提交：本报告的 docs 提交（逐文件 `git add`：验证文档、本报告、task-16-report 订正、计划 :691 订正），带 git 身份参数。
- **不 push、不合 main**（用户明令「推送前先问我」，覆盖计划 Task 18 Step 3 的推送动作）；批 2 段提交（含裁决文档与修复轮）在关账前**仅本地、未推送**（`44d81f8` 里「19 枚仅本地」是当时口径，收口时以 `git log` 现场为准）。
- **批 2 关账后已按请示推送（2026-10-21）**：用户批准「可以」后方推，目标 `origin` = `https://github.com/steveliuyan/English-learning-software.git`，分支 `stage-5-ui-r2-learning-path`，命令 `git push -u origin stage-5-ui-r2-learning-path`（**新建远端分支**，非 force、未碰 `main`）。
  推送后核验：`git ls-remote origin refs/heads/stage-5-ui-r2-learning-path` 回 `62a23b43e286ba032e7d00d1fa01012f4a789671`，与 `git rev-parse HEAD` 逐字相等；`git rev-list --left-right --count origin/stage-5-ui-r2-learning-path...HEAD` 回 `0 0`。相对 `origin/main` 的 120 枚提交全部落到远端分支作异地备份。合并 `main` 仍待用户另行拍板。
- 日志与证据文件均在 `.superpowers/sdd/2026-10-17-ui-redesign/`（非入库面），`.git/qa-scratch/` 零密钥/挑战明文，本报告与日志零 API Key。

## 5. 结转批 3 全表与未完成项

### 5.1 结转账核销结论（本席，文档级，不动码）

- **spec §3「两套同构几何」记账**：按控制者裁定入账——**几何口径关闭、色档债续存**：17B 后 `AppScreen.kt` 浮层一律经两枚共享档、页头经 `AppScreenHeader`，两套同构成立；残口是路 B 玻璃档 `GlassFill` 静态浅底（批 3 族 2）与路 A 面板 busy 期无置灰（批 3 组件裁决，候选 `confirmEnabled: Boolean = true`）。
- **房内裸 `AlertDialog` 实测正确数**（当场 grep 重算，订正评审 Minor-11 的算术口径）：`app/src/main/java` 下非共享组件的 `AlertDialog(` 调用点恰 **4 枚**——`VocabularyScreen.kt:84/:92/:104` 三枚 + `CardDetailScreen.kt:477` 一枚（`ConfirmDestructiveDialog.kt` 内是 `Dialog`，不计入 `AlertDialog(`）。结转批 3。
- `AppIcons.kt` 悬空 KDoc（m-3）17B 已订正；本席把计划 :691 的同一 `drawTabGlyph` 悬空引用一并订正（只改文档）。
- `task-16-report.md` §3 提示条族 seed 措辞（m-2）已订正（见 §2.2）。

### 5.2 结转批 3（续存债）

1. 裸 hex 清零：`RawColorSeedAllowlist` 现存 5 行（快照：AppScreen 2、ArticleReadingScreen 1、CheckInScreen 1、glass/GlassButton 1、mascot/AiMascot 1；枚数以该测试精确集合断言现场复算为准）→ AC-R-02 批 3 收。
2. 深色令牌消费债：`DarkTokenSeedAllowlist` 现跑快照 `files=29 hits=98`；`AppPalette.OnMintPrimary` 归属债（`GlassButton.kt` 1 枚 + `AppScreen.kt` 3 枚 = DarkToken seed 3）**确认归批 3 Task 26 Step 3**，seed 与站点数一致性本席已核（门控绿背书）。
3. `Mint*` 遗留族（重算 `grep -o 'Mint[A-Z][A-Za-z]*' AppScreen.kt | wc -l`，本次输出快照 69；台账旧记 66 为历史快照，以命令为准）、`MintBackground` 页底族、`AppType` 摘色 → 批 3。原列在同一条里的「`GlassFill` 同批」已在批 2 收口兑现，见 §5.2.6 与 **R-18-R1**。
4. 锚族清算（批 3 AC-R-12 名下集中登记）：decoy 吃锚族（`if (false)` 死分支 / `.takeIf{false}?.let` 窗外假调用 / 同名 shadow——Task 15 m-1、Task 16 复评、17A 轮 2 concern、R-17A.2 影副本四处同源）、I-2 窗外族、`remember {` 形状锚、Task 16 残余边界措辞收紧、17B 复评三 Minor（`branchKeyword` 名单不含 `return@label`/`throw`/`error(...)`；`tolerancePx` 取 `displayMetrics.density` 而非 `LocalDensity`；`blockText` 只截外层单层守卫块）、17A 复评 M-1（17dp 锚空白容忍与批 1 正则统一口径）。
5. 17A 复评 M-2（测试方法名 `clears 3 to 1` 与 ≥3.1 判据不同步）：台账写「Task 18 顺手改名」，但本席任务书口径为**零代码改动**，未执行 → 批 3 顺手项续存（连同 §5.2.4 锚族同批）。
6. AC-R-07 缺项：今日计划屏主体卡、词卡详情正文、文章阅读、设置族、词典/词书族屏的大字号用例批 3 续；AC-R-10 六屏 360dp 截图比对未开始。
   **AC-R-11 的 `GlassFill` 一族已在批 2 收口兑现，不再列批 3**（裁决 **R-18-R1**，计划 Task 18 段）：四枚容器消费点（`glass/GlassDialog.kt`、`glass/GlassOverlay.kt`、`AiProfileSettingsScreen.kt` 的 `SectionCard`、`SpeechSettingsScreen.kt` 的 `PreviewCard`）同批迁 `appColors().surfaceGlass`，面板内前景随容器同批按套；`app/src/main` 里 `AppPalette.GlassFill` 的**实读点归零**（可重算 `grep -rn 'color *= *AppPalette\.GlassFill' app/src/main/java --include=*.kt` 为空，剩余命中全在注释/KDoc），`GlassFillBackdropDebtLedger` 随之清空并留合成探针防「空表即空闸」。深色侧证据为 connected 取样类（`GlassDialogThemeInkTest`、`GlassPanelThemeInkTest`）+ WCAG 现算，**人眼与真机像素比对仍归 18-ii**，本行不替人眼判定。同批顺带把浅套两枚正文档域色墨按套归位（4.006:1 → 9.727:1，见 §5.3.⑪），取样类里那条「按装饰档 3.1 放行正文档」的 `DECOR_AA_RATIO` 已删。
   批 3 续存的深色实测面改为：`GlassHighlight` / `Scrim` 之外的其余静态容器与墨色族，枚数以 `DarkTokenConsumptionRatchetTest` 现场命令为准。
7. `PhotoTranslationScreen` 无 connected UI 类（14A-ii 登记缺口）与 `Text` 档设备侧独立取色用例缺失 → 归 18-ii 目检或批 3 补类。
8. 路 A 面板 busy 期置灰（组件裁决候选 `confirmEnabled`）；`CardDetailScreen.kt` 的 `SenseRow` `.background(MintTint)` 静态底、例句高亮 `DomainColors.AiSpeech.deep`（seed 1 在册）、播控组「停止」屏侧 CD 形状（F3-02 既有）→ 批 3。
9. **过期注释**（控制者批 2 收口时现场发现，未动码）：`components/ScreenUnavailable.kt:42-46` 的 KDoc 仍写「`PillButton` 的 Secondary 档容器/文字/描边读的是**静态浅套值**（`AppPalette.Surface`、`AppPalette.Separator`），不随主题翻色」——`pillButtonInk` 已在 Task 14 按套取 `set.surface`/`set.outlineSoft`，该段描述与现码不符；`app/src/main` 里 `AppPalette.Surface` 的实读点也已归零（仅剩这条注释命中）。批 3 顺手改注释，勿当行为缺陷重报。

10. **残留静态域色墨文字站点（批 2 终审盘点时现算入册，批 3 摘色族必须一起收）**：`grep -rn 'DomainColors\.\(AiSpeech\|Reading\)\.deep' app/src/main/java --include=*.kt` 现盘 **12 枚命中**（窄复评 P1 席抓出本席一度写成「10 枚」，漏了下面点名的两枚；命令与枚数当场可重算），逐枚落点：`AiProfileSettingsScreen.kt:152/218/234/250/429/434`、`SpeechSettingsScreen.kt:111/142/181/312`、`AiLearningScreen.kt:177`、`CardDetailScreen.kt:598`（后两枚此前分别**账外**与已在 §5.2.8 登记）。全是文字档（`AppType.Label`／`AppType.Body`／`MaterialTheme.typography.labelMedium` 徽章／`SpanStyle` 例句高亮），不是图形档。本席 WCAG 现算（awk，可重算）：`#188F76` 压浅页底 `#F1FBF5` **3.797:1**、压浅玻璃 `#FEFFFE` 4.006、压深卡底 `#17201C` **4.151**、压深页底 `#0E1512` 4.609；`#C81E46` 压浅页底 5.324（达标）、压深页底 **3.287**、压深卡底 **2.961**（后者连装饰档 3.1 都不达）。`AiLearningScreen.kt:177` 那一枚还额外带两个同族债：`else` 分支的 `AppPalette.TextSecondary` 是浅套静态墨（深档直接发暗），徽章底是 `Color.White.copy(alpha = 0.85f)`（`:167`）压在品牌渐变上，`#188F76` 落在这层近白上仍是 4.0 档 ⇒ 批 3 摘色时这三处一起收，别只改 `color` 一行。**归批 3 而非批 2 的理由**：这些行的容器底都不是批 2 迁的四枚玻璃面板（`PreviewCard` 之外的行；`SectionCard` 块在 :423 已闭合），比值劣化不是批 2 造成的，而 `Mint*` 页底族 + `AppType` 直读墨的摘色本来就是批 3 任务面（§5.2.3）。批 3 落值规则与验证文档 AC-R-11 行同一条：浅侧文字取 `appColors().onMint` 系、深侧取 `appDomain(...).deep`，**不得再迁成静态 `deep`**，收完每枚站点浅深两套各自 ≥4.5（用 `GlassPanelThemeInkTest` 的 `assertContrastAtLeast` 同款腿，禁止按 3.1 放行正文）。


### 5.3 仍待用户目视（18-ii，十项，本席未判）

① `ProfileCreationScreen` 页头 14dp→2dp；② 今日计划常驻查词卡+入口组连排节奏；③ `PillButton` Secondary 浅套发丝线观感；④ `PhotoTranslationScreen` 目检或补类二选一；⑤ 底导选中层级（浅套 `onMint` 9.751 vs `text2` 5.151 明度差小，选中感由药片+粗体承载）；⑥ 选中 17dp vs 未选 22dp 字形尺寸跳动；⑦ 17B 后 6 枚浮层路 A/路 B 深浅两套可读性；⑧ `AppBottomBarTest` 标签墨设备取样缺口；⑨ **真 TalkBack 引擎朗读四 tab（AC-R-12 未完成部分）**；⑩ 深色下底导 connected 像素期望失真项（`AppBottomBarTest:53-69` 浅套静态值）；⑪ **浅套 AI 配置页两处文字观感变化**（终审修复轮 C/C-1 按 R-18-R1 附带裁决归位）：「已设置密钥 / 还没有设置密钥」那行与音色浮层的选中行，浅套墨由薄荷绿 `#188F76`（压浅玻璃现算 4.006:1，正文档不达 4.5）改为 `appColors().onMint` `#174C44`（≈9.727:1）——像素确实会变，是否读作「更稳重」还是「丢了域色识别」归人眼判。另：F5-03 焦点级直采补一次真机走查（task-18-brief §二）；busy 期「点了没反应」是否被读作卡死（17B Important-2 交用户知情）。

## 6. 下一任接手须知

- 第一步：读本报告 + `docs/verification/ui-redesign-2026-10.md`（门禁节为最新口径）+ 批 3 对应 spec/plan Task 19 起；推送请示在控制者手里，实施席不 push。
- 坑：`AppScreenOverlayContainerTest` 大字号两枚**必须**先 `adb shell settings put system font_scale 2.0` 再跑、收工回 `1.0`（否则整类 2 红是预期哨兵不是缺陷）；`AppScreenTest:1686` known-flaky 复现时复跑一次留两版日志；绝禁 `connectedDebugAndroidTest`（清设备数据）；模拟器全类同报「Unable to resolve activity」先查 `dumpsys user` 是否 `USER_RUNNING_LOCKED`（见验证文档「connected 环境债」）；真机 `bf353dda` 只手工试用（MIUI 会自动卸载 adb 装的包，装完即测）。
- 基线交接：JVM **231/1708/0/0/0**（只准增不准减，增量跑不作背书；**终审修复轮后现盘已推进到 232/1725/0/0/0**，见 §8）；connected **25 类/278 用例/0 失败**（emulator-5574，批 2 子集而非全集，口径见 §3.2）。

## 7. 用户确认过的决策

- 不合并 main、推送前先请示（批 2 收口沿用，覆盖计划 Task 18 Step 3 推送动作）。
- 裁决清单（本批落地并沿用至批 3）：**R-14A-i.1**（ink 反向判据扩面拦 `Color(` 全构造器）、**R-14A-ii.1**（历史订正：token 迁移未回写测试的陈旧断言，清点即取证）、**R-17.1**（拆席 17A/17B，沿用 R-14.2 口径）、**R-17B.1**（浮层路由：路 A `ConfirmDestructiveDialog`/路 B `GlassDialog`，破坏性确认不迁玻璃档，沿用 **R-11.1** 墨色裁决）、**R-17A.2**（影副本入 decoy 吃锚族，批 3 清算）；两套同构几何按「几何口径关闭、色档债续存」入账（§5.1）。
- 深色半迁移前置处置（12.5 新增批 2 前置）已兑现，批 2 无遗留半迁移态。

## 8. 批 2 全分支终审与修复轮（控制者追加，2026-10-20）

- **终审**：六席只读评审（A 拆 A1/A2，`b3f54b8..04b6398`，40 提交/47 文件）→ 控制者逐条物证复核，
  5 条承重发现坐实、1 条算术驳回（驳回的是**控制者自己的数**，见验证文档 AC-R-11 行与本节末条）。
  Critical 一条 = 玻璃容器深色下仍写死白；用户拍板「同批迁容器」，落为计划裁决 **R-18-R1**。
- **修复轮三笔 + 契约一笔**（全在 `stage-5-ui-r2-learning-path`，2026-10-21 随批 2 关账一并推送）：
  `2c08792`（`GlassDialog` 容器 + 8 枚消费者面板内 16 处前景 + `PillButton` 可点 `role`）、
  `ce1e188`（`GlassOverlay` / `SectionCard` / `PreviewCard` 三块面板容器与前景同批迁）、
  `3a2f0a2`→`0da5dc1`→`6b05e95`（浅套两枚正文档域色墨按 `AppBottomBar.kt:95` 的形状按套归位到 `onMint`，
  取样类 `DECOR_AA_RATIO` 删除并补「修复前墨必须不达 4.5」的反空转腿）、
  `84464c8`（终审 C1 席 5 条 Important：契约取样器按花括号配平闭窗、正控改异写、锚点在位守卫、
  Ready 分支绑体、style 实参白名单门；纯 JVM 测试面，零生产码）。
- **控制者串行复跑**：门控 `BUILD SUCCESSFUL 4m22s`、现盘 **232/1725/0/0/0**；
  connected（`emulator-5574`，`RUNNING_UNLOCKED`，`font_scale` 前后 `1.0`，双 APK `install -r` + `am instrument`）
  修复轮受体面全清单 **11 类 / 109 用例 / 0 失败**——
  `AiProfileSettingsScreenTest` 23、`ArticleReadingScreenTest` 23、`GlassPanelThemeInkTest` 8、`PillButtonTest` 9、
  `ImageStudioScreenTest` 11、`ReadingAccessScreenTest` 13、`GlassDialogThemeInkTest` 3、`GlassDialogTest` 1、
  `WordAiQaScreenTest` 8、`SentenceAnalysisScreenTest` 7、`WordbookPickerHeaderTest` 3
  （最后一类从「点名未实跑」转为已覆盖；`PillButtonTest` 7 → 9 是 A 席为 `role` 新增两枚，无类用例数下降）。
- **控制者三条自纠**：① 我按「≈3.61/3.19」驳回 B 席的 4.01 是**我自己算错了**，席报值与实测像素都对；
  裁决仍成立，但理由换成「正文档不得按装饰档 3.1 放行」。② 门控里的 `:app:compileDebugAndroidTestKotlin` **只编译不产 APK**，
  我第一次 connected 复跑装的是上一棒的陈旧测试包，导致「新 app 包 + 旧测试包」自打自红；
  补 `:app:assembleDebugAndroidTest` 重装后立刻绿。复跑 connected 前先看 `app/build/outputs/apk/androidTest/debug/` 的 mtime。
  ③ 我把 `GlassDialogTest` 的 FQCN 少敲一层子包（`ui.` 而非 `ui.glass.`），instrument 回 `ClassNotFoundException`
  形状的 `initializationError`，一度被我当成「类失踪」；按现盘 `package` 行取真名复跑即 `OK (1)`——
  该类自建类提交 `aab770c` 起就在 `ui.glass` 下，18i 日志跑的也是这个全名。`-e class` 参数一律从现盘抄，不凭记忆拼。
- **窄复评两席（额度恢复后真落地，2026-10-21）**：两份评审包（`review-fix-P1.md` 104KB = `app/src/main` + `docs`，
  `review-fix-P2.md` 236KB = `app/src/test` + `app/src/androidTest`，BASE `04b6398`）第一轮派发时命中 CLI 日额度上限
  （`You've reached your daily usage limit for Chat.`），当时**只有控制者本人按同一清单代跑**（当时已在下面这条交代过独立性受限）；
  额度恢复后补派两席独立只读复评，现均已回话：
  - **P1 席 SPEC ✅、Critical 0、Important 1、Minor 1**。它把 A 迁移完整性、B 浅色侧逐值不变、C 归位墨判据三项
    独立复算为真（四枚容器实读点全在 `appColors().surfaceGlass`、浅套五对逐字相同、4.006/9.727/9.960 三枚比值与控制者自算逐位吻合）。
    其 Important 打的是控制者本人：**文档里写了可重算命令却没跑命令**——§5.2.10/AC-R-11 把残留静态域色墨文字站点写成「10 枚」，
    该命令实跑 **12**（漏 `AiLearningScreen.kt:177`、`CardDetailScreen.kt:598`）。已当场复算确认并订正（提交 `7973da0`）。
    其 Minor 是「带 Scrim 底」两枚比值推导口径不成立（`AppPalette.Scrim` alpha≈0.4，src-over 浅玻璃得 ≈`#989898` 而非 `#F5F5F5`），
    同笔删去无源注记。
  - **P2 席 SPEC ✅、Critical 0、Important 0、Minor 1**，并给出一条**范围事实订正**：`84464c8` 实改 **3** 枚 JVM 契约
    （`git show --stat 84464c8` = 3 files, +560/−38），任务书「四枚」不实——`AppScreenOverlayContainerContractTest.kt`
    本 range 未触碰（是既有金标准参照），第四处取样器改动走 `0da5dc1`/`3a2f0a2` 的 `GlassPanelInkPairingContractTest`。
    控制者对其关键坐标逐条抽验成立：`BODY_AA_RATIO = 4.5`（`GlassPanelThemeInkTest.kt:635`）、`DECOR_AA_RATIO` 全 `app/src` 零命中、
    style 白名单门为「`styleArgs > 0` 反空转 + `assertEquals(styleArgs, whitelistedStyleArgs)` 离表即红且 `.copy` 被 `(?![.\w])` 挡在门外」、
    滚动兜底锚对 `GlassDialog.kt:67-69` 在位、`Role.Button` 锚按「实参括号内」取形。其唯一 Minor 是正控样片字符串内容仍由探针
    pattern 反解（弱自指），但负控③④把宽窄两头夹住 ⇒ 不产生假绿，登记批 3 夹具归并时一并收。
  - **控制者自纠（派席纪律）**：P2 后台席未回话时，本席又补派了一枚同范围重跑席，该席撞 `Agent` 150 轮硬上限空手而归
    （零改动、零提交）。与本批早前「并发两枚实现席」同源，属第二次犯；入台账为规程：**同一范围的后台席未回话前不得补派**。
- **注释订正后的门控复跑**：`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache --rerun-tasks`
  ⇒ `BUILD SUCCESSFUL in 4m6s`、59/59 全执行、现盘 **232/1725/0/0/0**（与基线逐位相等，日志 `fix18c-controller-gate-final.log`）。
  connected **未重跑**：`7973da0` 只删注释文本与文档枚数，契约夹具本就剥注释、取样类期望墨未动 ⇒ 拿旧日志冒充新改动才是虚报，此处如实标注未跑。
