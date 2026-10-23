# UI 重设计 · 批 1 验收证据（AC-R）

分支 `stage-5-ui-r1-design-system`。本文件是批 1（设计系统：令牌、图标、共享组件）的可核验证据表。

**口径**：
- 凡「共 N 处 / 多少枚」一律写成**可重算命令**，不在文档里固定绝对数（计划 §Global Constraints #16）。
  表内出现的当次实跑数字只作「本次输出快照」，判定以命令为准。
- 计数与用例数一律由命令现场产出；本文件不引用其它文档的陈旧计数。
- 截图、API Key、Keystore 材料、挑战码明文一律不入库（与 QR 证据同红线）。

## 门禁与基线（每次收口重跑）

重算命令（离线、无守护进程、无构建缓存）：

```bash
cd /h/EnglishLearningSoftware
rm -rf app/build/test-results/testDebugUnitTest
V=$(grep -oE 'gradle-[0-9]+\.[0-9]+' gradle/wrapper/gradle-wrapper.properties | head -1)
GRADLE=$(ls -1 "$HOME/.gradle/wrapper/dists/$V-bin/"*/"$V/bin/gradle.bat" | head -1)
"$GRADLE" :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin verifyThirdPartyNotices --offline --no-daemon --no-build-cache \
  2>&1 | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  app/build/test-results/testDebugUnitTest/TEST-*.xml \
  | awk -F'"' '{t+=$2;s+=$4;f+=$6;e+=$8} END{print "suites="NR" tests="t" skipped="s" failures="f" errors="e}'
git diff --name-only main..HEAD | grep -c lockfile   # 期望 0：依赖锁零变化
```

本次实跑输出（2026-10-20 批 2 收口 Task 18i，HEAD `0d60658`，`BUILD SUCCESSFUL in 4m 20s`，59/59 任务全执行；命令为上表三任务简版 `:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`，同带 `--rerun-tasks --no-build-cache --offline --no-daemon`，日志 `.superpowers/sdd/2026-10-17-ui-redesign/t18-gate-full.log`）：
`suites=231 tests=1708 skipped=0 failures=0 errors=0`，结果 XML 文件数与 suite 数同为 231（无孤儿文件，五元组不虚高）。
**批 2 关账基线：231 类 / 1708 用例 / 0 失败 / 0 错误 / 0 跳过，只准增不准减。**
**用例数只增不减**（批 2 实跑演进：14B `225/1666` → 14A-i/ii `226/1673` → 15 `227/1681` → 16 `228/1690` → 17A `230/1702` → 17B `231/1708`；批 1 历史值 223/1654），差值就是当轮新增的
JVM 用例数——本行是**运行日志**不是规范值，别把它当分母去核对文档里的其它数字。

三张棘轮 seed 现状（2026-10-20 Task 18i 实核，逐张确认、未迁移未改判据，绿由当次门控背书）：
- `RawColorLiteralRatchetTest`：`"AppScreen.kt" to 2` 在册，形状台账同文件两行在册（Switch `uncheckedTrackColor` 与 `inactiveTrackColor` 两枚 `Color(0xFFCDEFE1)` 折叠行）；
- `DarkTokenConsumptionRatchetTest`：`"AppScreen.kt" to 3` 在册（`OnMintPrimary` 归属债，批 3 Task 26 Step 3）；全表重算命令现跑：
  `sed -n '/internal val DarkTokenSeedAllowlist/,/^)/p' app/src/test/java/com/example/englishlearning/ui/DarkTokenConsumptionRatchetTest.kt | grep -oE '"[^"]+" to [0-9]+' | awk -F'to ' '{s+=$2; n++} END{print "files="n" hits="s}'`
  （本次输出快照 `files=29 hits=98`；判定以命令为准，本文档不写死站点数）；
- `SharedComponentUniquenessRatchetTest`：页头族收口判据成立——`AppScreen.kt` 的 `MaterialTheme.typography.headline` 读点现场 grep 为 0，`removing_a_file_from_the_header_seed_requires_its_headline_reads_to_be_cleared` 随门控绿。

**`rm -rf app/build/test-results/testDebugUnitTest` 只在「本机此刻只有你一个跑者」时使用**（批 1 终审 Seat D M-4 登记）：
它的用途是清掉改名/删除的测试类留下的陈旧 XML，否则五元组虚高。但多席/控制者并行时，别的会话正在读同一批
`TEST-*.xml` 取证，删目录会让它拿到空结果或半份结果，证据作废。并席期间的做法：不删目录，先 `ls app/build/test-results/testDebugUnitTest/TEST-*.xml`
与当前测试类清单对账，发现孤儿 XML 只记名字、等没有并发读者再删并重跑。另注意 `--no-build-cache` 不等于强制重跑：
`testDebugUnitTest` 仍可能 UP-TO-DATE 而使产物停留在上一轮，重算前确认它这次真的执行过（必要时 `--rerun-tasks`，
但那同样要先确认无并发读者）。

connected 侧只在 `emulator-5574` 上按类跑（**绝禁 `connectedDebugAndroidTest`**，它会清设备数据）：

```bash
export ANDROID_SERIAL=emulator-5574
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.englishlearning.ui.<类名> \
  com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner
```

2026-10-19 批 1 connected 清单（逐类实跑，合计 **27 用例 0 失败**）：
`AppScreenHeaderTest` OK (4)、`IconGalleryRenderTest` OK (3)、
`EntryRowTest` + `InlineNoticeTest` + `NotConfiguredCardTest` + `ConfirmDestructiveDialogTest` +
`OutboundConfirmDialogTest` OK (18)、`ThemeModeSwitchTest` OK (2)。
跑前先 `dumpsys user | grep State:` 确认 `RUNNING_UNLOCKED`（见「connected 环境债」）。

2026-10-20 批 2 收口 connected 清单（Task 18i，`emulator-5574`，双 APK `adb install -r` + `am instrument -e class`，逐类日志 `t18-connected-*.log`）：**25 类 / 278 用例 / 0 失败**。
**口径订正（终审席 D 登记，控制者复算）**：本段是「**批 2 改动触达的 25 类子集**」，不是 connected 全集——
现场 `app/src/androidTest` 下含 `@Test` 的类共 **97 个**（可重算：`grep -rl "@Test" app/src/androidTest/java --include=*.kt | wc -l`），
未跑的那 72 类是阶段 0–5 的业务/存储用例，本轮不拿它们给批 2 背书，标题也不得写成「全清单」。
同一轮点名在册但当时**未实跑**的是 `com.example.englishlearning.ui.WordbookPickerHeaderTest`（现场 **3 枚 `@Test`**，
可重算：`grep -cE "^[[:space:]]*@Test" app/src/androidTest/java/com/example/englishlearning/ui/WordbookPickerHeaderTest.kt`；
控制者一度把它数成 4，是把该类 KDoc 里「三条各一个 `@Test`」那句一起数了——数注解要用行首锚，别用裸字符串匹配），
它属页头族，批 2 的 17B 改过页头接入点，故必须补跑；未跑期间本行不虚报为「已覆盖」。
逐类末行：`AppScreenTest` OK (46)、`CardDetailScreenTest` OK (25)、`AiProfileSettingsScreenTest` OK (23)、`ArticleReadingScreenTest` OK (23)、`TodayPlanScreenTest` OK (21)、`WordCardScreenTest` OK (17)、`ReadingAccessScreenTest` OK (13)、`SpeechSettingsScreenTest` OK (13)、`AiLearningScreenTest` OK (12)、`ImageStudioScreenTest` OK (11)、`AppBottomBarTest` OK (8)、`WordAiQaScreenTest` OK (8)、`PillButtonTest` OK (7)、`CheckInScreenTest` OK (7)、`SentenceAnalysisScreenTest` OK (7)、`CardDetailPronunciationControlsTest` OK (5)、`ArticlePronunciationControlsTest` OK (5)、`WordCardRevealFocusTest` OK (4)、`AppScreenOverlayContainerTest` OK (4)、`ConfirmDestructiveDialogTest` OK (4)、`AppScreenHeaderTest` OK (4)、`ThemeModeSwitchTest` OK (4)、`OutboundConfirmDialogTest` OK (3)、`WordCardPronunciationControlsTest` OK (3)、`GlassDialogTest` OK (1)。
两条规程证据：`AppScreenOverlayContainerTest` 两枚大字号用例的**外部前置**为设备级 `settings put system font_scale 2.0`（该前置下整类 OK (4)；未设前置自然 scale 整类 `Tests run: 4, Failures: 2` 为登记在案的响亮红样，护栏不空转，两份日志均留档）；每轮前后 `settings get system font_scale` 已回显进各日志，收工值 `1.0`。
**陈旧枚数订正（批 3 Task 19 收口时核对，2026-10-21）**：上面 `OK (4)` 是 17B 当时那一批实跑的末行，不是现盘枚数——该类此后长出第 5 枚
`hostedWordBookExportPickerKeepsTheCancelPillReachableWhenDozensOfBooksOverflowAtDoubleFontScale`，大字号例由 2 枚变 **3 枚**，
可重算：`grep -cE "^[[:space:]]*@Test" app/src/androidTest/java/com/example/englishlearning/ui/AppScreenOverlayContainerTest.kt`（现盘 = 5）。
批 3 在 `3849907` 上该类前置 `font_scale=2.0` 整类实跑 **OK (5)**。引用在册枚数前先按行首锚重算现盘，别拿上一批的末行讲今天的成绩。
同一条订正在 `c33a3d8` 上双侧复测（2026-10-21，控制者本人，日志 `t19fix2-overlay-scaledrift.log` / `t19fix2-overlay-fontscale2.log`）：
自然 scale 1.0 整类 `Tests run: 5, Failures: 3`——三枚**全红在 [assertScaleTookEffect] 哨兵**（同一句「fontScale=2.0 未落进对话框窗口」），是设计好的响亮红样、不是缺陷；
置 `font_scale=2.0` 后整类 **OK (5)**，跑完回 `1.0`（前后值均已回显）。⇒ 「未设前置会红几枚」跟着大字号枚数走，写死数字必然过期，两侧都要现测。
`AppScreenTest:1686`（`mandatorySetupOffersNoCancelEntry`，已登记 known-flaky 注入抖动）本轮未复现，整类一次 `OK (46 tests)`。
brief 点名但现场无 connected 侧此类：`AppTabTest`（仅存 JVM 侧 `app/src/test/.../ui/AppTabTest.kt`，未拿别的类顶数）。

### 批 2 全分支终审与修复轮复跑（2026-10-20，控制者本人串行实跑）

终审六席判出的 Critical（玻璃容器在深色下仍是写死白）由用户拍板「同批迁容器」，落点与裁决记在计划 **R-18-R1**；
修复三笔依次是 `2c08792`（`GlassDialog` 容器 + 八枚消费者面板内前景 + `PillButton` 可点 `role`）、
`ce1e188`（`GlassOverlay` / `SectionCard` / `PreviewCard` 三块面板容器与前景同批迁）、
`3a2f0a2`→`0da5dc1`→`6b05e95`（浅套正文档域色墨按套归位到 `appColors().onMint`，见 AC-R-11 行）；
契约取样器补牙另有 `84464c8`（`I-1`…`I-5`，只动 JVM 测试面，零生产码）。

- **门控**（串行前台，`:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache --rerun-tasks`）：
  `BUILD SUCCESSFUL in 4m22s`，59/59 任务全执行；现盘重算 **232 suites / 1725 tests / 0 failures / 0 errors / 0 skipped**
  （批 2 收口基线 232/1719 ⇒ +6，只增不减）。
- **connected 复跑（修复轮受体面全清单）**：只 `emulator-5574`，跑前 `dumpsys user` 为 `RUNNING_UNLOCKED`，
  每轮前后 `settings get system font_scale` 回显入日志、收工值 `1.0`；双 APK `adb install -r` + `am instrument -e class`，
  `connectedDebugAndroidTest` 未使用，真机 `bf353dda` 全程未碰。日志 `fix18c-controller{1..4}-connected-*.log`，
  合并去重后的终态：**11 类 / 109 用例 / 0 失败**——
  `ui.AiProfileSettingsScreenTest` OK (23)、`ui.ArticleReadingScreenTest` OK (23)、`ui.glass.GlassPanelThemeInkTest` OK (8)、
  `ui.glass.PillButtonTest` OK (9)、`ui.ImageStudioScreenTest` OK (11)、`ui.ReadingAccessScreenTest` OK (13)、
  `ui.glass.GlassDialogThemeInkTest` OK (3)、`ui.glass.GlassDialogTest` OK (1)、`ui.WordAiQaScreenTest` OK (8)、
  `ui.SentenceAnalysisScreenTest` OK (7)、`ui.WordbookPickerHeaderTest` OK (3)（本类随之从「点名未实跑」改为已覆盖）。
  用例数较 18i 只增不减：`PillButtonTest` 由 7 → 9（A 席为 `role` 与生效点击链新增两枚），其余各类与 18i 逐类等数。
- **窄复评两席（独立只读席，额度恢复后落地 2026-10-21）**：P1（`app/src/main` + `docs`）**SPEC ✅ / Critical 0 / Important 1**，
  其 Important 抓的是控制者本人「文档里写了可重算命令却没跑命令」——残留静态域色墨文字站点实为 **12 枚**（本席先写 10 枚，
  漏 `AiLearningScreen.kt:177`、`CardDetailScreen.kt:598`），已当场复算订正；P2（`app/src/test` + `app/src/androidTest`）
  **SPEC ✅ / Critical 0 / Important 0**，并订正范围事实：`84464c8` 实改 **3** 枚 JVM 契约（任务书「四枚」不实，
  `AppScreenOverlayContainerContractTest.kt` 本 range 未触碰）。两席关键坐标由控制者逐条抽验成立
  （`BODY_AA_RATIO = 4.5`、`DECOR_AA_RATIO` 全 `app/src` 零命中、style 白名单门「离表即红且 `.copy` 挡在门外」、
  滚动兜底锚与 `Role.Button` 锚对现盘生产码在位）。详见交接报告 §8。
- **两次自摆乌龙，如实记**：
  ① 首跑 `GlassPanelThemeInkTest` 报 `Tests run: 8, Failures: 1`
  （`SectionCard 密钥态墨（浅）：期望 #FF188F76，实测 #FF174C44`）。根因不是代码——门控里的
  `:app:compileDebugAndroidTestKotlin` **只编译、不产 APK**，我装的是上一棒的陈旧 `app-debug-androidTest.apk`，
  于是「新 app 包 + 旧测试包」对打。补 `:app:assembleDebugAndroidTest` 重装后同一类立刻 OK (8)。
  口径入册：**复跑 connected 前必须看 `app/build/outputs/apk/androidTest/debug/` 的 mtime 是不是本次构建**，
  `compileDebugAndroidTestKotlin` 绿不等于测试包新。
  ② 另一轮 `GlassDialogTest` 报 `Tests run: 1, Failures: 1`，栈是
  `Failed loading specified test class 'com.example.englishlearning.ui.GlassDialogTest'` / `ClassNotFoundException`——
  这是 **instrumentation 的 `initializationError`，不是断言失败**，根因是我把 FQCN 少敲了一层子包：
  该类从建起（`aab770c`）就在 `ui.glass` 下（18i 的日志 `t18-connected-GlassDialogTest.log` 里跑的就是
  `com.example.englishlearning.ui.glass.GlassDialogTest`，当场可重算：`grep -n "^package" app/src/androidTest/java/com/example/englishlearning/ui/glass/GlassDialogTest.kt`）。
  按真名 `ui.glass.GlassDialogTest` 复跑即 OK (1)。**类没被改名或删掉**，18i 在册的那一枚依然存在，
  不得把这条控制者笔误写成「测试类失踪」。口径入册：`-e class` 的参数一律从 `androidTest` 现盘 `package` 行抄，不凭记忆拼。

## 证据表

| AC-R | 口径 | 证据 | 状态 |
|---|---|---|---|
| AC-R-01 | `MaterialTheme` 装配点存在，`AppType` 六档钉值与规格 §2.2 一致（契约测试读常量，非截图） | `DesignTokenContractTest` + `MaterialThemeAssemblyContractTest`（JVM 源码级契约，夹具 `SourceRatchet.kt`）。**批 1 终审 F4 追加的防死 token 闸**：`neutral token identical in both sets is a dead token unless exempted by name` 逐枚比浅/深两套同名字段，浅深逐字相同即判红，除非在 `crossSetIdenticalNeutralTokenExemptions` 里点名给依据；名单与实测同值集合**双向精确相等**（多一项判 stale、少一项判 unexempted）。**控制者追认（窄复评 N-1）：豁免基线 = `{onBrand}` 一项，修复任务书里「豁免名单当前为空」是控制者笔误**——`AppColors.kt` 浅/深两套 `onBrand` 均为 `#04241C`，依据是 spec §2.1「品牌渐变保持现状不动」＋裁决 3 未给深套另立值（可重算：`grep -n '"onBrand"' app/src/test/java/com/example/englishlearning/ui/DesignTokenContractTest.kt` 两处同值 + `grep -n 'onBrand' app/src/main/java/com/example/englishlearning/ui/theme/AppColors.kt`）；深色玻璃面板上前景的最终核验仍归 AC-R-11 真机实测 | 通过（本棒门禁 0 失败） |
| AC-R-02 | 全站 `ui/` 布局代码无裸 `Color(0xFF…)` 字面量 | `RawColorLiteralRatchetTest`：新增为 0，存量按 seed 台账「只减不增」，seed 枚数由该测试现场精确集合相等断言复算（清账归批 3） | 通过（新增 0；存量未清零，批 3 收） |
| AC-R-03 | 浅底/品牌渐变上的前景只允许 `on-mint`/`on-brand` 系，对比度按 WCAG 相对亮度计算 ≥4.5:1（图标按非文本 ≥3:1），禁止估 | `DesignTokenContractTest` 的允许配对与禁配表 + `IconBadgeContrastContractTest`（按 `DomainLight`/`DomainDark` 全部域 × `EntryIconTone` 两档 × 浅深两套主题逐组合复算；组合数由集合当场遍历得出，不写绝对数。域数可重算：`grep -o 'enum class AppDomain {[^}]*}' app/src/main/java/com/example/englishlearning/ui/theme/AppColors.kt \| grep -o '[A-Z][A-Za-z0-9]*' \| grep -vc -e '^AppDomain$'`） | 通过 |
| AC-R-04 | 所有可交互控件高度 ≥48.dp（`heightIn`/`defaultMinSize`，F5-03 口径） | connected：`AppScreenHeaderTest` **OK (4)** + `EntryRowTest`/`InlineNoticeTest`/`NotConfiguredCardTest`/`ConfirmDestructiveDialogTest`/`OutboundConfirmDialogTest` 合计 **OK (18)** + `ThemeModeSwitchTest` **OK (2)**（只跑 `emulator-5574`，命令见上；批 1 connected 合计 **27 用例 0 失败**）。<br>**批 1 终审修复轮复跑（2026-10-19，`emulator-5574`，`install -r` + `am instrument`）**：`AppScreenHeaderTest` **OK (4)**、`EntryRowTest` **OK (5)**，新增 `WordRowTest` **OK (1)**（大字号右列宽度上限；变异态先红：摘掉 `widthIn(max = 120.dp)` 后实测 meta 195.0476dp、lemma 177.52dp）。上表 27 是修复轮前的批 1 合计，修复轮另加 `WordRowTest` 1 条，不复用旧数。 | 通过（2026-10-19 复跑 + 修复轮复跑，环境根因见「connected 环境债」） |
| AC-R-05 | `verifyThirdPartyNotices` 通过，台账含 `material-symbols-icon-paths`（仅参考度量）、`app-authored-ui-icons`、`app-authored-ui-illustrations` 三条且每条的必填字段均非空，描述与 `AppIcons.kt` 实际入库内容一致 | `verifyThirdPartyNotices`（门禁内已跑）+ `docs/third-party-notices.md` 三条目；本棒把 `app-authored-ui-icons` 的「用途」字段补齐为含 `ui/components/AppIcons.kt`（路径数据与豁免表所在地）、`AppIcon.kt`（运行时组装）、`IconBadge.kt`（容器呈现）的实际使用位置。必填字段口径的**权威是根 `build.gradle.kts:12` 的 `requiredNoticeFields`**（八项：名称/版本/许可证/用途/数据流/NOTICE 位置/替代方案/商业分发结论；可重算：`sed -n '12,15p' build.gradle.kts`）——`verifyThirdPartyNotices` 任务就注册在那里、`doLast` 里内联了完整解析器，这才是门禁实际执行的那份。`tools/verify-third-party-notices.main.kts` 的 `requiredFields` 只是同一清单的镜像，且只能用 `kotlinc -script` 跑、本机无 kotlinc（见 `docs/handoff/` 环境记录），**拿它当权威会把「跑不到的脚本」写成验收依据**。两份当前逐字一致，改动时须同步。本行的「三条」用只数这三条的命令核：`grep -cE '^## (material-symbols-icon-paths\|app-authored-ui-icons\|app-authored-ui-illustrations)$' docs/third-party-notices.md` 应打印 3（`grep -c '^## '` 数的是全台账 76 条，与本行断言无关，别拿它当这条的证据） | 通过 |
| AC-R-05b | 除决策文档豁免表的 5 枚（Back/ChevronRight/ChevronDown/Close/Check，只描边不建填充层）外每枚两层；豁免枚不得进 ≥40dp 入口容器；清单以决策文档的 33 枚为准；`ui/` 下 `\bIcons\.` 引用为 0（由契约钉住）；不新增 `material-icons-extended` | **覆盖面口径**：两道契约都只扫 `ui/`（`SharedComponentUniquenessRatchetTest` 走 `uiFiles()`、`AppIconsStructureContractTest` 同），故「`ui/` 下 `\bIcons\.` 为 0」由契约当场钉住；全域覆盖靠可重算命令 `grep -rn '\bIcons\.' app/src/main/java \| wc -l`（本次实跑 0）。`AppIconsStructureContractTest`（结构 + 豁免表双向钉；禁令以 import 字符串形状钉）+ `SharedComponentUniquenessRatchetTest` 复述 `\bIcons\.` 用法口径 + lockfile 零变化；呈现侧 `IconGalleryRenderTest` connected **OK (3)**（2026-10-19，且在改动该类的 `b5947ef` **之后**重跑：先证 `packageDebugAndroidTest` 与 `compileDebugAndroidTestKotlin` 双双 UP-TO-DATE、设备侧 APK 输入与当前源码一致，再 `install -r` + `am instrument`，六张 PNG 全部按新断言重写），六张浅/深截图目视清单 ①–⑥ 全部见下表并已判 | JVM 与 connected 均通过；目视清单 ①–⑥ 已放行 |
| AC-R-06 | `AppTabTest` 与既有业务规则测试零修改通过；JVM 基线只增不减（每次重算，不得引用陈旧数） | 门禁命令 + 上面的 awk 重算。零修改核验三条（本次实跑：第 1、2 条为空，第 3 条只列出 `MainActivity.kt`）：<br>`git diff --name-only main..HEAD -- app/src/test/java/com/example/englishlearning/ui/AppTabTest.kt app/src/main/java/com/example/englishlearning/di app/src/main/java/com/example/englishlearning/learning`<br>`git diff --name-only main..HEAD -- 'app/src/test/java/**' \| grep -v '/ui/'`（测试侧改动应全在 `ui/` 下）<br>`git diff --name-only main..HEAD -- 'app/src/main/**' \| grep -v '/ui/'`（生产侧越出 `ui/` 只允许 `MainActivity.kt`，它是主题装配点） | 通过（本次实跑：AppTabTest/di/learning 零改动；测试侧改动全在 `ui/`；生产侧仅 `MainActivity.kt`） |
| AC-R-07 | `fontScale = 2.0` 的 connected 用例覆盖六屏重排后的标题与卡片 | **批 2 已覆盖（Task 18i 收口，2026-10-20 复跑在案）**：① `AppScreenOverlayContainerTest` 名带 `AtDoubleFontScale` 的大字号用例（17B 收口时 2 枚，现盘 3 枚，枚数与重算命令见上节「陈旧枚数订正」）——17B 修复轮口径：运行前置为**设备级** `font_scale=2.0` + 行高哨兵 `assertScaleTookEffect` 防「scale 1.0 空转」（自然 scale 整类 `Failures: 2` 响亮红样留档 `t18-connected-AppScreenOverlayContainerTest-scale1-naturalred.log`；前置 2.0 下整类 `OK (4 tests)`）；② `WordCardScreenTest.lemmaAndRevealedMeaningStayFullyVisibleAtMaximumLargeFontScale`（Task 15 Step 3 护栏，组合级 `fontScale = 2f` 下词卡主词与揭晓释义不被裁出卡片，`OK (17 tests)` 类内通过）；③ 护栏出处 `CheckInScreenTest.headerKeepsLinksReachableAndTitleReadableAtMaximumLargeFontScale`（`OK (7 tests)`）。**批 2 未覆盖，批 3 续**：今日计划屏主体卡、词卡详情屏正文、文章阅读、设置族、词典/词书族屏的大字号用例仍缺（本轮逐类 connected 全绿但未为其新增 scale 断言，不虚报） | 部分通过（批 2 覆盖屏证据齐；缺上列五族屏，批 3 续） |
| AC-R-08 | §3 每个共享组件有「全站唯一实现」的静态检查；被替换的私有份数降到 1 | `SharedComponentUniquenessRatchetTest`：四族（页头 / 错误提示条 / 未配置卡 / 词行）按形态规则逐族**精确集合相等**，seed 是债务台账（只减不增，接线一屏删一行）；批 1 组件文件按「点名文件必须存在 + 公开组件在 `ui/` 扫描面恰一处顶层定义」钉；预登记豁免（本棘轮不管「就地改既有组件取值面」，Task 14 改 `PillButton` 本体不受挡）由 `the_ratchet_blocks_a_second_component_but_never_an_in_place_value_edit` 双向证明。**批 1 终审 F7 登记的第三条边界**：族 1/2/4 的判定面各带「本文件调了共享组件即整体合规」出口，出口同时是**已知失明面**——接线后同屏残留的私有 forbidden 形态会整体隐形；`family_compliance_exits_are_a_declared_known_blindness_surface` 双向钉住该语义，批 2 的删 seed 判据因此是「该屏 forbidden 读点清零」而不是「补一行调用就准删」，收紧（seed 改读点计数）属计划修订事项 | 通过（存量私有份未清零，清零归批 2/批 3；见「本族规则形状的已知边界」） |
| AC-R-10 | 六屏 360dp 真机浅/深两套截图逐屏比对设计稿 | 批 2/批 3 逐屏补 | 未开始 |
| AC-R-11 | 玻璃层与域色容器在深色下文字可读性实测 | **覆盖方式（批 1 终审登记 → 批 2 收口兑现，裁决 R-18-R1）**：本行原本的实测面是全站 `AppPalette.GlassFill` 的四枚容器消费点（`glass/GlassDialog.kt`、`glass/GlassOverlay.kt`、`AiProfileSettingsScreen.kt` 的 `SectionCard`、`SpeechSettingsScreen.kt` 的 `PreviewCard`）。批 2 收口把「容器填充与面板内前景墨色同批迁」真的同批迁完：四枚填充改读 `appColors().surfaceGlass`，面板内排印点（A 席 8 枚消费者 16 处 + B 席三块面板内的墨）一并按套。现盘可重算：`grep -rn 'color *= *AppPalette\.GlassFill' app/src/main/java --include=*.kt` 为空（余下命中全在注释/KDoc）。浅套逐值不变有契约腿钉着（`surfaceGlass`=`0xEFFFFFFF`、`text1`=`0xFF1C1C1E`、`surface`=`0xFFFFFFFF`、`outlineSoft`=`0xFFE4EFE9`、`text2`=`0xFF4E756A` 五对与旧静态逐字相同）；`GlassHighlight` / `Scrim` 属主题无关蒙版族（`ThemeInvariantMaskMembers`），有意不迁。<br>**深套那侧的证据不再是纸面推断**：connected 取样类 `GlassPanelThemeInkTest`（8 例）与 `GlassDialogThemeInkTest`（3 例）在设备上取真实像素带，逐枚断言「容器 = 套内深玻璃合成底、前景 = 套内墨、且 WCAG 现算 ≥ `BODY_AA_RATIO`」，并各带反空转腿（拿迁移前那枚静态墨喂同一条闸必须判红）。<br>**八枚玻璃对话框消费者的 connected 面已随修复轮复跑**（`ArticleReadingScreenTest` 23、`ImageStudioScreenTest` 11、`ReadingAccessScreenTest` 13、`SentenceAnalysisScreenTest` 7、`WordAiQaScreenTest` 8、`AiProfileSettingsScreenTest` 23、`PillButtonTest` 9、`GlassDialogTest` 1，11 类 109 例 0 失败，逐类出处见上面「批 2 全分支终审与修复轮复跑」段）。<br>**本批顺带闭掉一枚既有 AA 债**：浅套 `AiSpeech.deep`（`#188F76`）压浅玻璃合成实底 `#FEFFFE` 现算只有 **4.006:1**，而它落的那两行是 `style = AppType.Body`（正文档，线是 4.5）——取样类一度用 `DECOR_AA_RATIO = 3.1` 把它钉成「合格」，那是按装饰档放行正文档。按房内既有先例 `AppBottomBar.kt:95`（17A 修复轮 `0ed3984`）的形状按套归位：浅套取 `appColors().onMint`（`#174C44` 压同底 **9.727:1**）、深套沿用 `deep`（`#35E0AC` 压 `#161F1B` **9.960:1**），站点是 `AiProfileSettingsScreen.kt` 的密钥态行与音色浮层选中行；`DECOR_AA_RATIO` 常量随之删除。浅套这一处观感会变（薄荷绿墨→深墨），属修 AA 失败，已列入 18-ii 用户目检。同族其余把 `AiSpeech.deep`／`Reading.deep` 用在**文字**上的静态读点（可重算：`grep -rn 'DomainColors\.\(AiSpeech\|Reading\)\.deep' app/src/main/java --include=*.kt` 现盘 **12 枚命中**，再逐枚看它的 `style` 与所在容器），批 3 迁按套时按同一条规则落 `onMint`（浅）／`appDomain(...).deep`（深）、**不得再迁成静态 `deep`**；非文字图形档不受本条约束（例如 `TodayPlanScreen.kt:127` 那枚加载进度环的 `color`，装饰档 ≥3.1 已达标）。<br>**这批残留站点的比值本席已现算入册（不是留给批 3 再发现）**：`AiSpeech.deep` `#188F76` 压浅页底 `#F1FBF5` = **3.797:1**、压浅玻璃 `#FEFFFE` = 4.006、压深卡底 `#17201C` = 4.151、压深页底 `#0E1512` = 4.609（三档浅侧与深卡底都不达正文 4.5）；`Reading.deep` `#C81E46` 压浅页底 = 5.324（达标）、压深页底 = **3.287**、压深卡底 = **2.961**（后者连装饰档 3.1 都不达）。落点面（现盘行号，随批 3 改动会漂）：`AiProfileSettingsScreen.kt:152/218/234/250/429/434`、`SpeechSettingsScreen.kt:111/142/181/312`、`AiLearningScreen.kt:177`、`CardDetailScreen.kt:598`（后者已在交接报告 §5.2.8 在册；`AiLearningScreen.kt:177` 是窄复评 P1 席用同一条命令数出来的——本席一度把这族写成「10 枚」，P1 席实跑得 12，订正见 §5.2.10。那一枚还带同族两笔：`else` 分支的 `AppPalette.TextSecondary` 静态浅墨与徽章底 `Color.White.copy(alpha = 0.85f)`（`:167`）），全是 `AppType.Label`／`AppType.Body`／`labelMedium`／`SpanStyle` 文字。**为什么本批不动**：这些行的容器底都不是批 2 迁的那四枚玻璃面板（`PreviewCard` 之外、`SectionCard` 块在 :423 已闭合），比值劣化不是批 2 造成的；且 `Mint*` 页底族与 `AppType` 直读墨的摘色本身就是批 3 的任务面（结转条目见交接报告 §5.2），在这里单点改墨会把批 3 的成套迁移拆成碎改。<br>**批 3 硬约束（本行当场钉为验收口径）**：批 3 迁完这一族后，上述每一枚文字站点必须 ≥4.5（浅深两套各自现算，取样用 `GlassPanelThemeInkTest` 同一条 `assertContrastAtLeast` 腿，不许再按 3.1 放行）。<br>**仍未做**：六屏浅/深两套真机截图与设计稿逐屏目视比对（那是 AC-R-10 的面，归 18-ii 用户侧 + 批 3），以及非玻璃容器族（`Mint*` 页底族）的深色实测。 | 进行中（迁移侧与设备侧取样实测已收口；人眼两套目视与剩余族未做） |
| AC-R-12 | TalkBack 走查重做一遍 | **批 2 做到的是「语义契约 + connected 走查」两件事**：① 语义契约——duotone 底导四 tab 的 `testTag("app_tab_*")` + `Role.Tab` + `stateDescription` 与字形锚由 JVM 契约钉（17A/17A 修复轮，`R-17A.2` 后升级为体内夹击形状）；② connected 走查——`AppBottomBarTest` `OK (8 tests)`、`AppScreenTest` `OK (46 tests)`（2026-10-20 Task 18i 批 2 子集清单复跑在案，口径见上文订正）。**真 TalkBack 引擎下朗读四 tab 仍未做**（17A 评审 ⚠️1：以上全是语义树断言，不是引擎实测），连同底导选中态观感、标签墨设备取样缺口、深色像素期望失真项一并归 18-ii 用户侧真机走查（清单见 `task-18-brief.md` §一）。本行不虚报为通过 | 进行中（契约与走查侧完成；引擎实测未做） |
| AC-R-20 | 今日计划屏「8 枚整宽按钮 → 1 CTA + 入口行组」重排（批 2 Task 14） | 实现 `4d3c2e8` + 修复轮 `02f84da`（I-1 查词出口回四态常驻、I-2 数字权威取样防空转）+ `920eabe`（排印覆写禁令堵 `copy(fontSize)` 间接升档）；数字一处权威判据防 tag 上提与第二枚大字号（`R-14.3`/`R-14.4` 裁决落地）。**现场证据（2026-10-20 Task 18i）**：`TodayPlanScreenTest` connected `OK (21 tests)`（`emulator-5574`，`t18-connected-TodayPlanScreenTest.log`）；相关词卡与详情改动见 Task 15（`369395b`，`WordCardScreenTest OK (17)`）与 Task 16（`0414334`，`CardDetailScreenTest OK (25)`）。入口组连排节奏与常驻查词卡的**观感判断归 18-ii 用户目检**（task-18-brief §一.2），本行不替人眼判定 | 通过（自动化侧；观感目检归用户侧） |

## 图标一览目视清单（AC-R-05b 的呈现侧，缺项不得放行）

呈现处只有一个：`app/src/androidTest/java/com/example/englishlearning/ui/IconGalleryRenderTest.kt`
（批 1 不把底导与业务屏换成 `AppGlyph`，真机上看到的仍是旧 Canvas 字形，故图标第一印象只能在这里取）。
三个视图各一个 `@Test`、每个 `@Test` 只 `setContent` 一次，浅色档先落 `ThemeMode.Light` 偏好再装宿主、
深色档只写 `ThemeMode.Dark` 靠 `rememberAppThemeMode()` 活链路重绘，同一次测试内两次 `captureToImage`。

产物写到应用外部文件目录（`getExternalFilesDir`，与 `SetupScreenScreenshotTest` 同处），路径打在
instrumentation 输出（`ICON_GALLERY_SAVED <绝对路径>`）。**API 30 上 shell 直接读 `Android/data` 被政策挡
（`Permission denied`），`adb pull` 取不到**，本仓实测可用的是 `su 0 cat` + 重定向到仓库外临时目录：

```bash
mkdir -p /tmp/ui-icons                      # 必须是仓库外目录
for f in appglyph entrybadge inlinebadge; do for m in light dark; do
  adb -s emulator-5574 exec-out \
    "su 0 cat /sdcard/Android/data/com.example.englishlearning/files/icon-gallery-$f-$m.png" \
    > /tmp/ui-icons/icon-gallery-$f-$m.png
done; done
ls -la /tmp/ui-icons                        # 六张字节数均非 0
```

看完即删，图片一律不入库（`git status` 必须保持干净）。排列口径：按 `AppIcon` 枚举序左到右、上到下，
每行 8 枚（枚数与清单本身由 `AppIconsStructureContractTest` 与决策文档钉，本文件不写绝对数）。

| # | 目视项 | 判据 | 本轮结论（2026-10-19，六张齐） |
|---|---|---|---|
| ① | `Spark` 伴星补回尾段后是四芒 | 第 2 行第 4 枚，不得读成带竖直尖刺的坏形 | 通过：浅/深两张都是「大四芒 + 右下一枚小四芒」，无竖直尖刺 |
| ② | `Book`（合上）与 `BookOpen`（摊开）在 24dp 下可区分 | 第 1 行第 7、8 枚；`Book` 左半填充不得与 `BookOpen` 左页填充读成同一枚 | 通过：`BookOpen` 读到中缝与两页外扩，`Book` 读到封面 + 书脊一条竖线，24dp 下不混淆 |
| ③ | `Camera` 镜头环在 17dp 行内不与机身墨迹糊成一团 | 第 3 行第 5 枚，看 InlineIconBadge 视图 | 通过：17dp 下机身、取景凸台、镜头环三者分离，环内留白未被填充填满 |
| ④ | `Trash` 桶身填充与骨架的同心差；`Spark`/`Download` 笔画出安全区后在容器内不裁 | `Trash` 第 4 行第 7 枚、`Download` 第 5 行第 1 枚，看 EntryIconBadge 视图 | 通过：`Trash` 填充明显内缩于骨架；`Download` 在容器内四边留白，`Spark` 伴星未被容器切到 |
| ⑤ | 豁免 5 枚（Back/ChevronRight/ChevronDown/Close/Check）在 17dp 与 24dp 下均可辨 | 它们只出现在 AppGlyph 与 InlineIconBadge 两个视图（EntryIconBadge 的 `require` 拦死，跳过关系由该用例按 `StrokeOnlyIcons` 集合的双向断言钉住：进容器的枚集合 == 全部枚 − `StrokeOnlyIcons`） | 通过：两视图 5 枚齐全且形态互不混淆；EntryIconBadge 视图首枚即 `Search`（无豁免枚），与断言一致 |
| ⑥ | 深色档填充层（`DuotoneFillAlpha.Dark`）在深底容器上仍读得出层次 | 对比同一视图浅/深两张图的填充与描边层次 | 通过：三视图深底均铺到主题底色（`appColors().background`，本轮修的证据缺陷），描边与填充分层可读。**观察（非批 1 缺陷）**：域色里的灰色容器在深色下是这套里对比最低的一档，批 2 若把它用于承载正文级信息需回看 `IconBadgeContrastContractTest` 的实算值 |

机器可判的两条（已写进用例，不需要人眼）：非底色像素占比 > 0（防空绘制）；浅/深两轮整图像素哈希不相等
（证明「写 Dark」真的走到了装配点）。connected 读不到颜色，故本类不断言任何具体色值。

## 词行族规则形状的已知边界（Task 12 修复轮 1 的 Fix 3 裁决，正式登记）

- 现场：`ui/GlobalVocabularySearchScreen.kt` 里词行形态有两处，但按「顶层私有 `*Row*` 函数体窗口」的规则**只有一处落窗**
  （`GlobalSearchResultRow`：体内 lemma 词头 Text + 释义 Text）。
- 第二处是 `GlobalSearchHistory` 里一枚**无名内联 `Row { … }`**（写 `item.history.displayQuery` + 「搜索 N 次」），
  它不是顶层私有 `*Row*` 函数，结构上落在窗口规则之外——**规则看不见它，这是本族规则形状的已知边界，不是漏检的 bug 复现**。
- 处置：`SharedComponentUniquenessRatchetTest` 把该文件的落窗数钉成 1，并在 seed 台账里按**文件**计
  （接线时两枚变体在同一行里一并消失）。**不得**为了凑数把窗口放宽成「任何 `Row {`」——
  那会立刻误伤 `CardDetailScreen.kt` 的 `SenseRow` 与 `AppScreen.kt` 的 `DetailToggleRow`（两枚都不是词行），
  使词行族 seed 凭空变大。
- ~~兑现时机：批 3 接线该屏时，两处一起改调 `WordRow`；届时同步删 seed 行并复核这条边界是否仍在。~~
  **已被裁定 R-20C.1 作废（2026-10-21）**：词典页两处形态**不改调 `WordRow`**，该文件在词行族台账里保留为
  已裁定的长期债务；理由与代价见下一节。本条登记的「无名内联 `Row` 落窗规则之外」仍是现行边界，未变。

## 词行族台账递减、词典行保留理由与右列宽度策略（Task 20-B / 20-C，2026-10-21 登记）

台账现况的重算命令（逐枚打印实际违规文件集与 seed 的双向差集，不抄文档里的数）：
`"$GRADLE" :app:testDebugUnitTest --offline --no-daemon --no-build-cache --tests com.example.englishlearning.ui.SharedComponentUniquenessRatchetTest`

- **R-20B.1（范围裁定）**：计划 Task 20 Step 4 收窄为三屏（`FavoritesScreen` / `WrongWordsScreen` /
  `WordBookWordsScreen`），已在提交 `4198136` 落地；词典页移出本批。
- **R-20B.2（棘轮纪律的必要偏离，追认）**：`SharedComponentUniquenessRatchetTest` 里原
  `word_row_family_names_the_six_spec_files_…` 把 seed 硬钉成「恰等于 spec §3 六枚名单」，
  与「接线一屏删一行」的台账口径**构造性互斥**（不删 → `each_family_offender_set_equals_its_seed_exactly` 红；
  删 → 它红）。该用例改为「名单来源 + 摘行举证」两层：seed 必须等于「spec §3 六枚 − 已摘行屏」，
  且每枚已摘屏必须 ① 出现共享 `WordRow(` 调用、② 本族落窗形状读点为 0。
  断言未删、阈值未放宽、该类用例数不变。**这条裁决的效力**：后续批次合法递减词行族 seed 时同步改该用例，
  不算违反「棘轮一字不动」纪律；反之，只删 seed 行而不给①②举证，该用例当场红。
- **R-20C.1（词典行保留理由）**：`GlobalVocabularySearchScreen.kt` 的结果行有**五枚用户信息**
  （词头 / IPA / 释义 / `来源：词书名` / `搜索 N 次`）加两枚测试诊断标签，`WordRow` 只有四枚文本槽且右列
  `widthIn(max = 120.dp)` + `maxLines = 1` + Ellipsis。强迁的确定后果：IPA 无处放、词书名被省略号截断
  （用户靠它区分跨词书同名结果）、两枚标签失去挂点。三条出路均已核过代价：收窄族 4 规则形状 = 给棘轮开第二只
  失明眼（该族已有一只「本文件调共享即整体豁免」）；另立组件登记新族 = 只有一个站点，「同一形态不得有第二份」的
  立论不成立；给 `WordRow` 加槽 = 签名口袋化并牵动取色表与逐槽对比契约。**决定**：三者都不做，
  走计划 Task 26 Step 3 授权的第二出口「seed 归零**或写明保留理由**」，本文件即那份「保留理由」。
  **代价**：词典搜索结果行保留一份私有版式，本轮 UI 不收敛；批 4 重做查词域组件时必须重新裁决。
- **F2 / 右列宽度策略（Task 20-D 已落地，裁定 R-20D.2；原稿「挂 Task 20-D」的陈述作废）**：
  `WordRow` 右列的 120dp 上限 + 单行省略，对「时间戳/原因串」是有损承载位（收藏页 `收藏于 yyyy-MM-dd`
  在放大字号下即触顶；与合并页同槽同源）。**不得单屏改**（会让合并页与收藏页分叉），故本项按组件级裁决一次性定：
  **`meta` 的 `maxLines` 由 1 改为 2**（保留 `TextOverflow.Ellipsis` 作第三行兜底）、**`status` 维持单行 + Ellipsis**
  （短状态标签）、**右列 `widthIn(max = 120.dp)` 不加宽**（加宽挤压左列 `lemma`/`gloss` 阅读主体，比截断更坏）、
  **不改签名不增槽**（`WordRowSpeakerContractTest` 取色表与 `SharedComponentGeometryContractTest` 枚数因此无需同步，
  重算命令 `grep -n "maxLines" app/src/test/java/com/example/englishlearning/ui/WordRowSpeakerContractTest.kt app/src/test/java/com/example/englishlearning/ui/SharedComponentGeometryContractTest.kt`
  ——命中的是 `lemma`/`gloss` 两枚正则与 `EntryRow.kt` 的枚数，均不在本组件）。
  **代价（照实记）**：① `meta` 换行时该词行行高增高、右列不再与左列首行等高（`verticalAlignment = CenterVertically`
  下整行一起长）；② `fontScale 2.0` 档下两行预算**推断**装不下 `加入于 <日期时间>` 全串（推断依据：右列
  `widthIn(max = 120.dp)` 是 dp 上限、不随 sp 放大，而该串在 2.0 档需 >2 行；**未实测**——2.0 档判据故意不判整句可见，
  故没有留下一枚「在 2f 断整句可见并见其红」的探针），按组件既有口径以省略号退化
  （`WordRow.kt` KDoc「fontScale 2.0 下靠省略号退化，不靠裁切」），所以大字号轮判**策略腿**（额度 ≥2、上限 ≤120dp、
  第二行真被占上、逐行不伸出文本盒 + 字号哨兵）而不判「整句可见」，整句可见腿只在组合级钉 `fontScale = 1f` 的判据里判；
  ③ 验收一律以布局级判据为准，不以观感为准。取证与改动分两枚提交（取证先行）：
  `91a57ec`（四站点布局级判据 + 收藏/错词独立屏冒烟，天然红 `t20D-RED-*.log`）→ `efd08e1`（`WordRow.kt` 一枚 `maxLines`）。
- **R-20D.1（影响面取证口径实核，2026-10-21 更正本文件与计划 Step 4c 的原稿）**：
  ① 真正受右列 120dp 影响的是**两文件四站点**，不是「三屏同改」：`FavoritesScreen.kt:144`、
  `MyWordsScreen.kt:167`、`MyWordsScreen.kt:224`、`MyWordsScreen.kt:234`
  （前两枚 `收藏于 yyyy-MM-dd`，后两枚 `加入于 <日期时间>`）。
  `WrongWordsScreen.kt:127-131` 只传 `lemma`+`gloss`、**不传 `meta`/`status`** ⇒ 不受右列影响，本任务不需要改它；
  `WordBookWordsScreen.kt` 传的是短状态标签（`status = row.status.statusLabel()`，属「右列只放短标签」的正例，
  留作策略选定后的对照；该行号随 Task 21 与本批注释已漂，按锚点重算
  `grep -n "status = row.status.statusLabel()" app/src/main/java/com/example/englishlearning/ui/WordBookWordsScreen.kt`）。
  重算命令：`grep -rn "meta = " app/src/main/java/com/example/englishlearning/ui/{FavoritesScreen,MyWordsScreen,WrongWordsScreen,WordBookWordsScreen}.kt`
  ② **取证缺口的准确形状**（原稿「收藏页与错词页没有任何用例渲染它们」只对了一半，且漏了合并页的另一半缺口；
  本条描述的是**补取证之前的现盘**，现状重算 `grep -rln "FavoritesScreen\|WrongWordsScreen" app/src/androidTest`
  已命中新立的 `FavoritesScreenTest.kt`/`WrongWordsScreenTest.kt`）：
  收藏页与错词页的**独立屏**当时确实零渲染级用例——上面那条重算命令只命中 `MyWordsScreenTest.kt`，
  而它的 `:160`/`:214` 两处命中**都在注释里**（描述缺陷形状，不是挂屏）；
  但**合并页 `MyWordsScreen` 是挂着的**（`:226-231` 一次 `setContent` 喂三段真实 `UiState`，
  `:234` 断 `收藏于 2026-10-19` 存在、`:235` 断带时分的旧档不存在、`:237` 反向控 `加入于 2026-10-19 00:30`）。
  ⇒ 合并页的缺口不是「没渲染」而是**「渲染了但只到文本层」**：`onNodeWithText` 匹配的是语义全文，
  节点被 Ellipsis 截断时它照样绿，所以四站点全仓**没有任何布局级不截断判据**
  （**下述取证落地前的现盘形状**；现盘重算命令 `grep -n "textLayout\|maxLines\|fontScale" app/src/androidTest/java/com/example/englishlearning/ui/MyWordsScreenTest.kt`
  已有多枚命中，登记值 `现盘零命中` 只作历史口径保留，该类也没有 `font_scale 2.0` 轮）。
  ③ **前置取证已按这枚定形落地（Task 20-D，取证提交先于组件提交）**：四站点各补一枚**布局级**判据
  （`textLayout()` 读宽度与 `maxLines`，形状取自 `b76f133` 的 `disabledRowShowsTheUnavailableNoticeInFull`；
  合并页三站点在 `MyWordsScreenTest` 内同一次 `setContent` 逐枚判，收藏站点在 `FavoritesScreenTest`）；
  收藏页/错词页独立屏**仍在导航树上**（`AppScreen.kt:1125`/`:1132`，打卡页两枚入口可达，重算
  `grep -n "FavoritesScreen(\|WrongWordsScreen(" app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`）
  ⇒ 走「各补一枚渲染级冒烟用例」这一支，新立 `FavoritesScreenTest`（`OK (2 tests)`）与 `WrongWordsScreenTest`（`OK (2 tests)`）；
  合并页类由 12 枚增至 14 枚（`OK (14 tests)`，枚数重算 `grep -c "    @Test" app/src/androidTest/java/com/example/englishlearning/ui/MyWordsScreenTest.kt`）；
  `font_scale 2.0` 轮已跑（设备级 `settings put system font_scale 2.0` 前后各回显、收工回 `1.0`，
  新判据另在组合级各钉 `1f`/`2f`，见上一条 F2 的代价②）。天然红样留档 `t20D-RED-*.log`（实测 `maxLines=1`）。
- **F4（改名逃逸残形，登记不修）**：族 4 的 forbidden 形状就是「顶层私有 `*Row*` 函数体窗口」，
  把私有行函数改成不含 `Row` 的名字即可离开窗口规则；R-20B.2 撤销绝对 pin 后，这条残形不再被任何断言挡
  （它本来也只被「seed 恰等于六枚」顺带挡住，而那条与本族递减互斥）。口径：这是 R-13.1 计划本身定义的残形，
  收法是「每屏 forbidden 读点计数」的收紧（计划里已列为批 3 之后的修订事项），本轮不做。
- **F5（更正一条被反复引用的旧口径）**：「设备侧读不到颜色」在本仓**不绝对**——语义树读不到颜色，
  但文本节点可以经 `textLayout().layoutInput.style` 读到（现场：`WordBookWordsScreenTest` 的
  `activeLetterUsesADifferentColor`）。既往按「读不到颜色」豁免的断言，今后要按「可测而不测」重新表述。

## 打卡页版面收敛与生词页老路由摘除（Task 22-A，2026-10-22 登记）

**分工口径（R-22.1，控制者按用户 2026-10-22 拍板拆分）**：设计规格 §6 表格 ③「打卡页回归统计职责，
头部两条裸文字链移出」这一裁决**拆 22-A / 22-B 两席执行**——
22-A = 打卡页版面收敛（统一页头 + 裸 hex token 化）+ 摘生词页老路由 + 补第六枚端到端判据；
22-B = 摘打卡页那两条裸文字链 + 收藏页/错词页两屏下线。
**本节不宣称「Task 22 已全部完成」**；22-B 未跑，另两条老路由与两屏仍在册。
（规格 §6③ 本体需要同一句拆分注记，但 `docs/superpowers/specs/2026-10-17-ui-redesign-design.md`
在本次收口时正被另一席（图标识别度与入口配色，2026-10-22）改写、工作树不干净，本席不代提交，
移交收口席补那一行。）

### 已落地面

- **统一页头**：`CheckInScreen.kt` 的私有页头 `Row`（`fillMaxWidth().horizontalScroll(…)` + 文字「返回」）
  换成共享 `AppScreenHeader(title = "学习打卡", onBack = onBack, action = { 收藏链 + 错词链 })`；
  两条裸文字链的 testTag / contentDescription / `defaultMinSize(48.dp)` / `padding(10.dp)` 逐字未改，
  只从私有 Row 搬进组件的 `action` 槽。重算：
  `grep -c "AppScreenHeader(" app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt`（应为 1）、
  `grep -c "check_in_open_favorites\|check_in_open_wrong_words" app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt`
  （**必须 > 0**，证两条链未被 22-A 提前摘，归 22-B）。
- **L-T11-1 结清**：屏侧那段自认「真实的大字号症状…归 UI 重设计轮」的注释随私有页头一起改述，
  `horizontalScroll` 反例形态的教训迁进 `AppScreenHeader` 自己的 KDoc（组件 `Row` 显式 `fillMaxWidth()`）；
  屏侧不留「本轮未收口」的陈旧自述。
- **返回位交接**：屏侧 `check_in_back` 那枚文字返回节点删除，返回位由组件的 48dp 图标位承载
  （组件返回位**无 testTag**，只有 `contentDescription = "返回"`）。判据侧因此把
  `CheckInScreenTest.back_invokes_callback` 与 `AppScreenTest.checkInEntryOpensOverlayAndBackReturnsToTodayPlan`
  的选取器从 `onNodeWithTag("check_in_back")` 换成 `onNodeWithContentDescription("返回")`——语义不变
  （点返回位、回调恰一次），未放宽。48dp 与可读描述改由
  `AppScreenHeaderTest.header_back_is_a_48dp_target_with_a_readable_description` 承重，护栏 KDoc 里点名了这次交接。
  新立 `CheckInScreenTest.headerIsCarriedByTheSharedAppScreenHeader` 钉「组件节点在册 + 旧 tag 必须消失」。
- **headline 读点清零**（R-22.3）：整文件 `MaterialTheme.typography.headline` 读点归零——页头那一枚随私有
  `Row` 消失，环内完成度数字那一枚从 `style = MaterialTheme.typography.headlineMedium` 改写为
  `style = AppType.Headline`。**这是等价改写、零视觉变化**（修复轮 1 更正）：`headlineMedium` 在批 1 装配点
  早已映射为 `AppType.Headline`（24sp），改前后读的是同一枚样式对象，**席位自述的那次数位收小从未发生**。
  映射链三段重算（逐条命中即为成立）：
  `grep -n "Headline = TextStyle\|headlineMedium = AppType.Headline" app/src/main/java/com/example/englishlearning/ui/theme/AppType.kt`
  （同一文件里 `Headline` 定义为 `24.sp`、`appTypography()` 里 `headlineMedium = AppType.Headline.neutralForTheme()`）、
  `grep -n "typography = appTypography()" app/src/main/java/com/example/englishlearning/ui/theme/MintGlassTheme.kt`
  （M3 装配点）、`grep -n "\"headlineMedium\" to \"Headline\"" app/src/test/java/com/example/englishlearning/ui/MaterialThemeAssemblyContractTest.kt`
  （该映射由在册 JVM 判据钉住，不可能漂移）。清零读数重算：
  `grep -cE "MaterialTheme\.typography\.headline" app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt`
  收工为 `0`。这是删页头族 seed 行的前置判据。
- **裸 hex token 化**：`drawArc` 的轨道底色 `Color(0xFFFFF1DB)` 改为复用现成语义槽 `appColors().tint`，
  **未新增** `AppColorSet` 槽。重算：`grep -c "0xFFFFF1DB" app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt` = `0`。
- **生词页老路由摘除**：`today_plan_open_vocabulary` 入口行、`onOpenVocabulary` / `vocabularyCount` 形参链、
  `AppScreen.kt` 的 `showVocabulary` 态 / `BackHandler` / `LaunchedEffect` / `else if (showVocabulary) { VocabularyScreen(...) }`
  挂载分支一整条摘掉。`showVocabularyRelearn`（合并页整册重学）与 `vocabularyViewModel` 本体**未动**——
  合并页生词本段靠它；该段的装载另有两枚站点（LEARNING tab 与切到 Wordbook 段）。重算：
  生产侧凭据（两条读数都应为 `0`；`grep -c` 无命中时退出码是 1，取数值不取退出码）：
  `grep -cE "(^|[^A-Za-z0-9_])VocabularyScreen\(|else if \(showVocabulary\)|showVocabulary = " app/src/main/java/com/example/englishlearning/ui/AppScreen.kt` = 0
  （挂载分支、状态位与 `BackHandler` 一起消失；`showVocabularyRelearn` 不在该式命中范围内，它仍在册）；
  `grep -c "today_plan_open_vocabulary" app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt` = 0
  （入口行与形参链一起消失）。判据字面量侧
  `grep -rn "\"today_plan_open_vocabulary\"" app/src/test app/src/androidTest` = 零命中
  （余下只余注释/KDoc 里「该行已于 22-A 摘除」的过去时陈述，不是判据字面量）。
  同步的六处：`TodayPlanScreenTest` 闭合集合减一行、`TodayPlanNumberAuthorityTest` 入口标签白名单删行、
  `TodayPlanScreen` 入口组注释改述（该文件 KDoc 明写「本注释不抄枚数」，故 plan Step 1 的「KDoc 枚数自述」对本屏不适用）、
  `AppScreen.kt` 的「只建不摘」注释改述为「三条里 `showVocabulary` 已于 22-A 摘除，另两条归 22-B」、
  `MyWordsScreenContractTest` 两份反向控清单各删一枚（另两枚留给 22-B，断言本体保留）。
  另按同一口径把 `MyWordsScreen.kt` KDoc 里「三条老路由仍在册」那句改述为拆分现状（本屏不在禁改清单内，
  且留着它就是陈旧陈述）。
- **第六枚判据（plan R2-1 硬项，与摘路由同批）**：新立 connected
  `AppScreenTest.mergedPageMasteryResultDialogIsDrivenByTheHostAndClosesOnAck`——经**宿主**打开「我的词」
  生词本段 → 逐行「标记为已掌握」→ 二次确认 → 断 `Success` 结果框可见 → 点「知道了」后断其消失且不残留。
  它补的正是「宿主真的把 `vocabularyMasteryState` / `clearMasteryMessage` 接进合并页」这半边设备侧零判据的缺口
  （原盘重算 `grep -icE "mastery" app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt`
  在本席之前为 `0`，现算 `grep -icE "mastery" app/src/androidTest/java/com/example/englishlearning/ui/AppScreenTest.kt` 应 > 0）。

### 两枚独立的台账动作（不是一句「seed 递减」）

1. `SharedComponentUniquenessRatchetTest` 页头族 seed **只删 `"CheckInScreen.kt"` 这一行**，该族其余行与
   其他三族一字不动。删行的合法性由该测试当场复核：
   `removing_a_file_from_the_header_seed_requires_its_headline_reads_to_be_cleared` 在「已接线但 `:153`
   仍读 `headlineMedium`」的中间态**实跑判红**（天然红样 `t22A-RED-02-jvm-header-seedgate-headline-uncleared.log`，
   失败点即该用例的 `notCleared` 断言），清零后才绿。
   重算：`grep -n "CheckInScreen.kt" app/src/test/java/com/example/englishlearning/ui/SharedComponentUniquenessRatchetTest.kt` = 零命中。
2. `RawColorLiteralRatchetTest` 只删与 `CheckInScreen.kt` 直接相关的**两枚站点**（计数表 `"CheckInScreen.kt" to 1`
   与逐字样本行），两表同进同退；该文件没有把 `CheckInScreen.kt` 计入的全局合计断言，故无第三处可动。
   天然红样：token 化之后、减 seed 之前跑，`per-file hit count equals the seed count in both directions` 判红
   （`t22A-RED-03-jvm-rawcolor-seed-stale.log`），即「债减了而台账不跟着减」这一方向被当场挡住。
   重算：`grep -n "CheckInScreen" app/src/test/java/com/example/englishlearning/ui/RawColorLiteralRatchetTest.kt` = 零命中。
3. `DarkTokenConsumptionRatchetTest` **一字不动**。控制者实核口径复核：
   `grep -cE "\b(AppPalette|DomainColors)\.[A-Za-z0-9_]+" app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt`
   开工为 7、收工仍为 7（页头区零命中，`AppType.Headline` 不落入该正则），该文件里 `CheckInScreen.kt` 的登记值仍为 7。

### 代价（观感变化，登记为 Task 26 目视项，不宣称「未变」）

- **R-22.2 环色**：完成度环的轨道从奶油 `#FFF1DB` 换成 `tint`——明档 `#DDF7E8`、暗档 `#1C2C26`
  （`theme/AppColors.kt`）。观感从「奶油环」变「薄荷环」，且**暗档下轨道由奶油变深**
  （此前一枚裸 hex 在明暗两档是同一枚奶油）。本席未为此新增 token、未改任何棘轮。
- **本屏字号未变**（装配点早已把 `headlineMedium` 映射为 `AppType.Headline`），R-22.3 收的是 `MaterialTheme.typography`
  读点归零，不是观感。原登记的「**完成度数位收小**：环内百分比 28sp → 24sp」与「不取 `AppType.Display`（34sp）
  因净空不足」两条**一并撤销**——它们描述的动作从未发生（等价改写，见上节三条重算命令）。
  本屏真实成立的观感代价只有 R-22.2 完成环轨道色那一条（目视清单⑤）。
  留名：`3eb703b` 提交消息里那句字号代价**不可改**（提交已入库），以本节更正为准。
- **打卡页华丽动效**：本轮**未做**（spec §1 不做的事 #5，不引入动画代码）——华丽动效属另批范围。

### `fontScale 2.0` 设备档护栏与本席的边界

本席按纪律跑了设备级 `font_scale = 2.0` 轮（跑前回显 `1.0`、置档后回显 `2.0`、收工回 `1.0`）：
`CheckInScreenTest` `OK (8 tests)` 与 `AppScreenHeaderTest` `OK (4 tests)` 在 2.0 下**全绿**——这正是本席
改动面的 AC-R-07 判据。同档下 `TodayPlanScreenTest` 有 3 枚、`MyWordsScreenTest` 有 1 枚失败；本席把
`app/src` 整体 stash 回 BASE 后在同一设备、同一 2.0 档复跑，得到**逐枚同名同因**的 3+1 失败
（留档 `t22A-baseline-fontscale2.0-probe.log`），据此判定那是**既存**的设备级大字号脆弱面，不由 22-A 引入，
本席不扩面修，登记给 Task 26（症状：设备档下未滚动节点被判不可显示、注脚档比值随换行漂移）。

### 登记给 Task 26 的两笔设备面缺口（修复轮 1 补落点，此前只活在测试 KDoc 与评审报告里）

1. **共享页头组件返回位在 `fontScale 2.0` 档尚未被钉**：`AppScreenHeader` 的返回位是
   `.semantics { contentDescription = "返回" }`（**无 testTag**），其 48dp 只由
   `AppScreenHeaderTest.header_back_is_a_48dp_target_with_a_readable_description` 在**默认 `fontScale`** 下实测；
   同类的 `header_survives_font_scale_2_without_clipping_the_title` 量的是**标题 Text 的宽度**，不是可点区
   ⇒ 大字号档下返回可点区全仓无测（Task 22-B 摘掉屏侧文字返回节点后，打卡页页头唯一可点节点就是它）。
   Task 26 应补「组件返回位在 `fontScale 2.0` 下两轴 ≥48dp」的判据。重算：
   `grep -n "contentDescription = \"返回\"" app/src/main/java/com/example/englishlearning/ui/components/AppScreenHeader.kt`（命中即无 testTag 那一枚）、
   `grep -n "fun header_" app/src/androidTest/java/com/example/englishlearning/ui/AppScreenHeaderTest.kt`（两枚分工同上）。
2. **`font_scale` 大档轮的凭据必须落日志**：Task 22-A 那轮跑前/置档/收工三次 `settings get system font_scale` 的
   **回显原文没有进任何日志文件**，只余本节那句转述 ⇒ 该轮的置档证据不可复算。Task 26 的口径：
   设备级大字号轮必须把三次回显原文写进当轮日志（跑前、置档后、复位后各一枚），
   并注意「未设置该键时回显 `null`，`null` 不等于 `1.0`」（Task 22-B 已就这点纠正过一次口径）。重算（现盘零命中，即本条登记的依据）：
   `grep -l "settings get system font_scale" .superpowers/sdd/2026-10-17-ui-redesign/t22A-*.log`

## 收藏页与错词页下线及打卡页两条裸文字链摘除（Task 22-B，2026-10-24 登记）

**执行归属（如实登记，关系到下面每条凭据的可信度）**：本节点不是实现席一次跑完的。
第一席派发后静默终止（约 11.5 小时零提交、零报告），接续席撞 `Agent` 工具 150 回合硬上限、
零提交并留下十七枚脏文件；最终由**控制者本人**完成遗作审计、缺陷修复、门控复跑与提交。
故本节凭据均为控制者亲跑日志（文件名见末段），不是席位转述。

### 已落地面

- **两屏下线**：删除 `ui/FavoritesScreen.kt`、`ui/WrongWordsScreen.kt` 与两屏的 androidTest 冒烟类
  `FavoritesScreenTest.kt`、`WrongWordsScreenTest.kt`。收藏与错词的展示/操作职责由 Task 20 已上线的
  合并页 `MyWordsScreen`（分段：收藏 / 错词 / 生词本 / 词书）承接。重算（三条都为预期值）：
  `grep -rln "FavoritesScreen\|WrongWordsScreen" app/src/main/java | wc -l` = `0`（屏名在生产侧彻底消失）；
  `ls app/src/main/java/com/example/englishlearning/ui | grep -cE "^(Favorites|WrongWords)ViewModel\.kt$"` = `2`
  （两枚 ViewModel **保留**——它们是合并页两段的宿主数据源，不是孤儿类，也不许顺手删）；
  `ls app/src/androidTest/java/com/example/englishlearning/ui | grep -cE "^(Favorites|WrongWords)ScreenTest\.kt$"` = `0`。
- **两条宿主路由下线**：`AppScreen.kt` 的 `showFavorites` / `showWrongWords` 状态位、对应 `setValue` 点、
  两枚 `BackHandler` 与两个挂载分支整体摘除；`LaunchedEffect(showMyWords, …)` 由双条件回到单条件。
  数据侧只改装载条件、不改数据源：错词数据原按 `showWrongWords || showMyWords` 共享，现只跟 `showMyWords`；
  删词后回刷可见屏的那处同理去掉 `showWrongWords`。收藏详情三枚宿主变量保留（合并页收藏段复用）。重算：
  `grep -cE "^[[:space:]]*(val )?show(Favorites|WrongWords)\b" app/src/main/java/com/example/englishlearning/ui/AppScreen.kt` = `0`
  （只判**声明**；该文件仍有若干**注释**提到旧状态位名，是过去时陈述，不是代码）。
- **合并页端到端承接（plan R2-1 同一口径的第七枚判据）**：新立 connected
  `AppScreenTest.mergedPageSectionsReachFavoriteDetailUnfavoriteAndWrongWordRelearnThroughTheHost`——经**宿主**
  打开「我的词」，走「收藏段 → 收藏详情 → 取消收藏」与「错词段 → 重学」两条链，钉两屏下线后职责确实被合并页接住
  （不是只删不接）。它带着两枚宿主侧 ViewModel 构造助手（各seed 一枚词），零放宽既有断言。
- **打卡页两条裸文字链摘除**：`CheckInScreen.kt` 的两枚形参 `onOpenFavorites` / `onOpenWrongWords`、
  `AppScreenHeader(action = { 两条裸文字链 })` 的整个 `action` 槽、以及随之失去引用的
  `clickable` / `defaultMinSize` import 一并摘除；页头只剩 `title` + `onBack`。重算：
  `grep -c "check_in_open_favorites\|check_in_open_wrong_words" app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt` = `0`；
  `grep -c "AppScreenHeader(" app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt` = `1`（统一页头仍在册，22-A 的成果未回退）。
- **判据侧同步（删判据必须换成正判据，不许留空）**：
  `CheckInScreenTest` 里钉 48dp 触控的 `headerTextLinksMeetTheMinimumTouchTarget` 与其私有
  `assertMinTouchTarget` **随链一起删除**（被断言的节点已不存在，留着就是测空气），
  新立反向判据 `openFavoritesAndWrongWordsLinksAreNotRenderedOnCheckInHeader`（两枚 tag 各 `assertDoesNotExist`）；
  原 `fontScale 2.0` 那枚更名 `headerTitleStaysReadableAtMaximumLargeFontScale`，
  并**保留**「标题列 ≥48dp」那条证伪腿（触控护栏没有因为链的消失而丢）。
  `MyWordsScreenContractTest` 的两份反向控清单（22-A 留的两枚）去掉，改立两枚正判据：
  `the_host_holds_no_favorites_or_wrong_words_routes`（按标识符逐枚数 `Regex("""\b$identifier\b""")` 命中为 0）与
  `the_two_legacy_screens_are_off_the_production_tree`（`File(...).exists()` 为假 + 扫 `uiFiles()` 点名任何仍含旧屏字样的生产文件）。
- **取样面减少、断言本体逐字未松**：`WordListFormatTest` 的三元素名单去掉 `FavoritesScreen.kt`、
  「两页同文对照」去掉第二页——该判据此后成为合并页单侧口径闸（「收藏于」只许读成日期档），
  文本规则一字未改。`VocabularyScreen.kt` 按 R-22.4 留册未动。
- **KDoc-only 改述（判定为合规，不含行为改动）**：`MyWordsScreen.kt`、`WordListFormat.kt`、
  `components/WordRow.kt`、`MyWordsScreenTest.kt`、以及规格 `docs/superpowers/specs/2026-10-17-ui-redesign-design.md`
  §3 `WordRow` 行「证据」列——把「三页并存」的旧陈述改成下线现状，避免留下指向已删文件的陈旧行号。

### 台账动作（三张棘轮逐张交代）

1. `SharedComponentUniquenessRatchetTest` **页头族** seed 删 `"FavoritesScreen.kt"`、`"WrongWordsScreen.kt"` 两行，
   **词行族** `specWordRowFiles` 同批摘同名两枚——两处都留了注释点名「随 22-B 下线」，族内其余行与其他两族一字未动。
2. 本节点有一枚**控制者修复**必须留名：接续席在临终末笔把 `"MyWordsScreen.kt"` **误登记为页头债务**（该屏早已接线
   `AppScreenHeader(`，属「把唯一份登记成债务」的反向错形）。控制者现跑
   `each_family_offender_set_equals_its_seed_exactly` **判红**（`t22B-cont-controller-ratchet-probe.log:48` 点名该用例 FAILED），
   差集内容（cleared 侧的 `MyWordsScreen.kt`）由控制者当场读该次的 JUnit 报告得出——
   **gradle 的控制台日志只点名到用例一级，不含差集字面量，别把这句当日志字面量凭据**；
   删掉该行后 focused 复跑转绿
   （`t22B-cont-controller-ratchet-fixed.log`，`BUILD SUCCESSFUL in 30s`）。
   教训已进台账：席位的 GATE 绿灯**不覆盖其绿灯之后的末笔编辑**，收口前必须重跑。
3. `RawColorLiteralRatchetTest` 与 `DarkTokenConsumptionRatchetTest` **一字不动**。
   后者对 `CheckInScreen.kt` 的登记值仍成立：
   `grep -cE "\b(AppPalette|DomainColors)\.[A-Za-z0-9_]+" app/src/main/java/com/example/englishlearning/ui/CheckInScreen.kt` = `7`
   （摘掉的两条链是纯文字 `Text`，本来就不读令牌）。

### 代价（可达性与观感变化，登记为目视项，不宣称「未变」）

- **入口少了一处**：打卡页页头不再有「收藏」「错词」两条直达文字链。用户此后须经底部「我的词」进入合并页分段。
  这是 spec §6 表格 ③「打卡页回归统计职责」的**正面执行**，但确实改变了两条老路径的可达性——见「仍待用户目视」⑦。
- **返回栈少两层**：`showFavorites` / `showWrongWords` 叠层消失后，从「我的词」返回只退一层。
- **删判据的账面**：`CheckInScreenTest` 用例数净变化为 0（删一枚 48dp 触控、加一枚反向不渲染）。
  触控承重的**准确表述**（评审席 A-4 纠正本席原话「护栏未丢」过强）：链摘除后打卡页页头唯一可点节点是共享组件的返回位，
  其 48dp 由 `AppScreenHeaderTest:53-63` 在**默认 `fontScale`** 下实测两轴承住；`CheckInScreenTest` 保留的那条大字号腿
  量的是**标题 Text 的宽度**（不是可点区）。**`fontScale 2.0` 档下页头可点区的 48dp 全仓无测**——这是被删用例
  KDoc 早已点名给 Task 26 的既存债，不由 22-B 引入，但也不由 22-B 补齐。
- **一度丢失的说明文案（评审席 A-1 抓到；控制者裁定逐字补回，裁定号 R-22B.3）**：旧错词页在列表非空时有一行排序说明
  「最近一次标记为「不认识」的词，按到期先后排列。」（摘除前在册的 `:97`），22-B 摘屏时随屏消失且未进本段台账
  ——合并页错词段只搬了**空态**文案，没搬这一行。裁定依据是同文件既有先例（生词本段空态 `readingDescription`
  的注：「逐字搬旧页那条，缺它即比旧页少一层」），**不登记成永久代价**。
  **修复轮 1 已落地**（`c2f4a81`）：合并页错词段非空腿补该行，形状为 `AppType.Footnote` + `colors.text2`
  （与收藏段 notice 那行同规格；旧页的 `labelMedium` + `Mint` 系快捷色名是批 3 令牌迁移前的形状，**没有搬回来**），
  新锚 `my_words_wrong_order_hint`。判据 connected `MyWordsScreenTest.wrongWordsSectionKeepsTheOrderingHintFromTheLegacyPage`
  三条腿：逐字 `assertTextEquals`（润色即红）+ 词行 `my_words_wrong_row_0` 同屏在册（挡「用说明替换掉列表」的假修）
  + 说明行矩形必须落在 `my_words_section_wrong` 容器内（挡挂到别的段）。
  重算：`git grep -n "按到期先后排列" HEAD -- app/src` 现在**生产侧与判据侧各有命中**（22-B 收工时此处为零）。
- **修复轮 1 的另三件（`353ff25` / `abba685` / `7afa858` / `492c42d`）**：
  ① 新立 JVM 源码形状契约 `CheckInScreenHeaderShapeContractTest`（立时 4 枚，修复轮 2 后 6 枚；
  重算 `grep -c "@Test" app/src/test/java/com/example/englishlearning/ui/CheckInScreenHeaderShapeContractTest.kt`）——钉 `CheckInScreen.kt` 恰一处
  `AppScreenHeader(` 且**那次调用的实参区间**不含 `action`；两枚反事实腿把 `action = { … }` 与「只在注释里的 `action`」
  分别喂给同一提取器判红/判绿，实参括号配不平时**响亮失败而不是返回空串**（空串会让「不含 action」假绿，是该夹具最坏的失效形状）。
  补这条的理由：摘链的 `openFavoritesAndWrongWordsLinksAreNotRenderedOnCheckInHeader` 在 `androidTest`，
  而门控第三项 `compileDebugAndroidTestKotlin` **只编译不跑** ⇒ 链复活在门控里本来是不可见的。
  ② 两枚 22-B 闭集判据各补覆盖面边界 KDoc（R-22B.5）：**本条钉「本批下线动作不被回滚」，不钉「此后不许有第二个收藏/错词目的地」**；
  以新名重建第二目的地时两枚全绿，那条结构级缺口按 R-22B.8 路由 Task 26。
  ③ connected 判据 `favorite_timestamp_shows_the_same_date_tier_as_the_favorites_page` 更名为
  `favoriteTimestampUsesTheSharedDateTier`（R-22B.6，断言本体一字不动）——旧名把已不存在的屏写成对照物，属假线索。
- **一条被判据当场教会的口径（R-22B.9，登记给后续所有席位）**：`the_two_legacy_screens_are_off_the_production_tree`
  的扫描面是 `ui/` 源码**全文含注释**（`readText()` 不剥注释）⇒ **生产注释里点名已下线屏的文件名会进 offenders、判据当场红**。
  修复席踩中的两次**只第一次是这条判据抓的**（窄复评席抓到我把两次都写成「判据当场红」，订正登记为 R-22B.13）：
  ① `c2f4a81` 给新行补写的注释点名旧屏文件名 → **门禁判红**，`7afa858` 改写注释转绿，断言与判据一字未动；
  ② `492c42d` 改的是 `androidTest` 侧判据自身的 KDoc——该目录**不在本条扫描面内**（扫描根 `src/main/…/ui`，`SourceRatchet.kt:34`），
  门禁不会红，席位是按本类既有「只用中文页名 + 摘除前行号指代旧页」的一致性口径**自查后**改述的。
  两次处置都追认：它与 A-6「方法名点名已下线屏 = 假线索」是同一个方向的闸。与页头族那条**先剥注释**的口径不一致属**有意**：
  债务棘轮数的是「还欠着的读点」（注释不算欠），本条数的是「死引用」（注释也算）。
  **R-22B.11：扫描面不扩到测试树**——`src/test` 现盘 4 处合法历史引用（闭集判据自己的名单 + 三处历史叙述；
  重算 `grep -rn "WrongWordsScreen.kt\|FavoritesScreen.kt" app/src/test app/src/androidTest | wc -l`），
  扩面只会让判据永远红、逼人放宽它；测试树侧的死引用由 A-6 那类人工闸管。
- **修复轮 2（窄复评席的 1 枚 major + 2 枚 minor ⇒ 裁定 R-22B.10 / R-22B.12；提交 `66dfc29` + `df80ac1`）**：
  复评席抓到的漏检形状是真的——`components/AppScreenHeader.kt:72` 的 `action: @Composable (() -> Unit)? = null` 是**末位形参**，
  于是 `AppScreenHeader(title = …, onBack = onBack) { Text("我的收藏") }` 这种**尾随 lambda** 写法合法编译、
  实参区间一个 `action` 词元都没有，修复轮 1 那两枚 JVM 腿会全绿。
  落地形状：判定入口收成 `headerCallHangsActionSlot` = 实参词元 **∪** 尾随 lambda；
  姊妹夹具从**配平的右括号下标**起只跳分隔空白、只看首个非空白字符是否 `{`——
  写成「在剩下的文本里找第一个 `{`」会当场假红，因为真实调用后面紧跟的是 `when (state) {`，
  故另立一枚 `aFollowingWhenBlockIsNotMistakenForATrailingLambda` 做**假阳对照腿**。
  覆盖面措辞同时改述到位：本类钉的是「共享页头那一次调用的两种挂槽形状」，
  **不钉**「干脆不用共享页头、把私有 `Row` 页头长回来」（那归页头族 + `checkInScreenMountsTheSharedHeaderExactlyOnce`），
  类 KDoc 与失败消息里的「任何……都在本条红」全称句已删。
  F2 那枚 connected 判据补第四腿（说明行 `boundsInRoot().top` ≤ 第一枚词行 top，容差沿用本方法既有的 `±1f`，未新造阈值、未改前三腿）。
- **控制者代跑的变异取证（席位只读禁跑，本控制者执行）**：
  ① **真源码挂尾随 lambda**（把两条链以 `) { Text("我的收藏") … }` 形式补回 `CheckInScreen.kt:75`）
  → `checkInHeaderCallCarriesNoActionSlot` **FAILED**，`6 tests completed, 1 failed`（`t22B-fix2-cont-mutation1-trailing-lambda.log`）
  ⇒ R-22B.10 的 major 就此闭合，不是靠措辞而是靠实测。
  ③ **整枚页头调用连锚点一起注释掉** → **两枚**红（恰一枚 + 实参腿的「找不到锚点」响亮失败），
  证明锚点缺失不会被当成「不含 action」的假绿（`t22B-fix2-cont-mutation3-header-call-removed.log`）。
  ⑧ **把排序说明行挪到 `forEachIndexed` 之后**（生产码临时改、跑完复原）→ 唯一红的是新第四腿，
  其失败消息逐字点名「前三腿全部测不出来，本腿红」；复原后 `MyWordsScreenTest` `OK (15 tests)`
  （`t22B-fix2-cont-mutation8-connected.log` / `t22B-fix2-cont-restore-connected.log`，全程只 `emulator-5574`，真机未触碰）。
  其余五条（复评席待取证的 ②④⑤⑥⑦）不外跑：②④⑤ 的形状已由该类**自身的合成反事实腿**在同一提取器上自证，
  ⑥⑦ 分别被逐字 `assertTextEquals` 与「落在 `my_words_section_wrong` 容器内」那腿直接覆盖——
  登记为「类内自证 + 逻辑覆盖」，不等价于外部变异，Task 26 若要补齐全谱按此口径补。

### 凭据（控制者亲跑，HEAD `3f989db`）

- 门控 `:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache --rerun-tasks`：
  `BUILD SUCCESSFUL in 3m 36s`，XML 现算 `suites=236 tests=1750 skipped=0 failures=0 errors=0`
  （分母出自 `task-22B-brief.md` §四.2 写给席位的开工基线 1748，**本档不另立该记录**；+2 枚 JVM 闭集判据；
  本行是运行日志不是规范值，重算命令见本文开头「门禁与基线」）——`t22B-cont-controller-gate.log`。
- connected（仅 `emulator-5574`，`adb install -r` + `am instrument`，未用 `connectedDebugAndroidTest`）：
  `CheckInScreenTest OK (8)`、`MyWordsScreenTest OK (14)`、`AppScreenTest OK (50)`、`TodayPlanScreenTest OK (21)`
  ——`t22B-cont-controller-connected.log`。首跑 `AppScreenTest` 有 1 枚 flake
  （`startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan`），单方法复跑 OK、全类复跑 OK(50)，控制者本人再复跑亦 OK(50)。
- `font_scale = 2.0` 设备档轮：跑前回显 **`null`**（该 setting 键未设置，等价于系统默认档，**不等于回显 `1.0`**——
  评审席 B-1 纠正本席原话）、置档回显 `2.0`（`t22B-cont-fontscale2.0-echo.log`）、收工复位回显 `1.0`
  （`t22B-cont-fontscale-restore-echo.log`）；2.0 档下实跑的是三类：
  `CheckInScreenTest OK (8)`（`t22B-cont-connected-5-*.log`）、`MyWordsScreenTest OK (14)`（`-6-*.log`）、
  `AppScreenTest` 合并页承接链**子集** `OK (4)`（`-7-*.log`；该类整类是 50 枚，本档只跑了其中 4 枚，
  日志按当时命令只记到「类 + 例数」一级，方法列表未落进该文件）。
  共享页头组件在大字号档的证据 `AppScreenHeaderTest OK (4)` 出自 **Task 22-A** 那轮
  （`t22A-connected-fontscale2.0.log`），22-B **未重跑该组件类**——评审席 B-2 纠正本席原话。
  22-B 改动面在本席这几轮里无大字号破口。
- **修复轮 1 + 2 合并态的控制者全量门**（HEAD `df80ac1`，同一条三段门控带 `--offline`）：
  `BUILD SUCCESSFUL in 3m 40s`、`59 actionable tasks: 59 executed`，XML 现算
  `suites=237 tests=1756 skipped=0 failures=0 errors=0`（`t22B-fix2-controller-gate.log`）。
  为什么还要本控制者再跑一次：修复席自己的门控原文（`t22B-fix1-gate.log`）早于其后三枚末笔提交、
  修复轮 2 的收工读数只覆盖它那一枚提交——**席位绿灯不覆盖其后落库的编辑**，这条口径已登记在 R-22B.2 与其后的复跑里。
  22-A 修复轮那一轮是 JVM 门（`:app:testDebugUnitTest` + `:app:compileDebugAndroidTestKotlin`，零 adb，
  `BUILD SUCCESSFUL in 3m 4s`、`40 actionable tasks: 40 executed`，日志 `t22A-fix1-gate.log` 里 `FAILED` 零命中；
  该席自报收工计数与开工同形 `237/1754` ⇒ 它确实一枚 `@Test` 未增删，而合并态由上面那条全量门背书）。
- 真机 `bf353dda` 本节点未触碰。

## AI 主页默认展开「现在能用」（Task 23，2026-10-24 登记）

范围 = 裁决④ / AC-R-23 的两屏（`ui/AiLearningScreen.kt` 一级页 + `ui/AiFeatureScreen.kt` 二级骨架页）。
派工块：`.superpowers/sdd/2026-10-17-ui-redesign/task-23-brief.md`；报告：同目录 `task-23-report.md`。

### 已落地面

- **默认展开的是能用的**：`implemented = true` 的三枚（长难句分析 / 拍照翻译 / AI 生图）进默认树，
  区段标题「现在能用（3 个）」；`implemented = false` 的四枚压成一条 `EntryRow` 折叠行，
  标题「未上线功能（4 个）」、尾部值就是徽章词「未上线」，点开才逐枚列出。
  旧那句「AI 功能（7 个）展开 ˅」把可用与未上线混成一枚总数，随本批作废（R-23.8 ③）。
- **文字不再当图标**：两屏的 `Text(feature.glyph)`（文/填/听/讲/析/拍/画）换 `AppGlyph(AppIcon.Spark)`，
  一级页行尾 `Text("›")` 换 `AppGlyph(AppIcon.ChevronRight, 16.dp, StrokeOnly)`；
  契约 `AiBadgeContrastFixTest` 把禁令取样切片**限定在这两枚 AI 文件内**（`Text("›")` 在
  `GlobalVocabularySearchScreen.kt` 另有站外站点，不属本批）。
- **徽章两面分开**：徽章面唯一产出 `featureStatusBadge(implemented)` → 「现在能用」/「未上线」，
  落在 `ui/AiFeature.kt` 末尾（R-23.9）；目录原文 `AiFeature.status`（「已可用：…」/「未实现：…」）
  一字未改，由 `AiFeatureTest` 与 `AiFeatureScreenTest` 各自钉一面。
- **一级页行内补状态**：行尾显示徽章词，`contentDescription = "<能力名>，状态：<徽章词>"`，
  TalkBack 读一行即读到「能不能用」（connected `each_feature_row_is_announced_with_its_own_status`）。
- 页头**未迁**（R-23.7）：两屏 `AppScreenHeader(` 计数仍为 0，`SharedComponentUniquenessRatchetTest`
  页头族两行 seed 一字未动，归 Task 26 收口账。

### 台账动作（三张棘轮逐张交代）

- `DarkTokenConsumptionRatchetTest`：`"AiFeatureScreen.kt" to 3` 与 `"AiLearningScreen.kt" to 2`
  **两行整行删除**（五枚逐成员计数全部迁完，减到 0 即摘行，不把数字改成 0）。顺序 = **先迁生产码断掉引用、
  同一工作树内再摘 seed 行**，两枚改动同批提交（`ae082e6` 生产 / `27002e1` 台账），
  因此不存在「seed 先减、生产未迁」的中间绿，也不存在「生产已迁、seed 未减」的中间红被提交。
- `RawColorLiteralRatchetTest`：一字未动（本批未新增裸 hex；亮度断言全部读活 token）。
- `SharedComponentUniquenessRatchetTest`：一字未动（页头族两行仍在册）。

### 代价（深色档三处变色 + 一处浅档变色，登记为目视项）

| 站点 | 旧（静态浅值） | 新（按档取值） | 浅档 | 深档 |
|---|---|---|---|---|
| `AiFeatureScreen` 头部 44dp 品牌渐变徽标前景 | `AppPalette.OnMintPrimary` #174C44 | `appColors().onBrand` #04241C | **变深**（裁决 #3 要求） | 同值 #04241C（两套同三段） |
| `AiFeatureScreen`「去配置 AI 服务」按钮 `contentColor` | `AppPalette.OnMintPrimary` #174C44 | `appColors().onMint` | 逐值不变 | **变浅** #DFF6EC 压深底 |
| `AiLearningScreen` 头卡状态徽章（已配置档） | `DomainColors.AiSpeech.deep` #188F76 | `appDomain(AiSpeech).deep` | 逐值不变 | **变亮** #35E0AC |
| `AiLearningScreen` 头卡状态徽章（未配置档） | `AppPalette.TextSecondary` #4E756A | `appColors().text2` | 逐值不变 | **变亮** #9FB3AA |

另两处不是「迁令牌」而是本批版面本身：一级页新增的 `EntryRow` 折叠行带来 40dp 域渐变徽标 +
`text1/text2/outlineSoft` 三枚主题墨色；一级页行尾新增徽章文字取 `MintPrimaryDark`（批 1 前的裸常量，
R-23.11 不扩面）。以上均需真机深浅两档目视，本席不替用户判定。

### `fontScale 2.0` 大字号轮（本批改版面，必跑）

三次回显原文全部落档（`.superpowers/sdd/2026-10-17-ui-redesign/`）：
开工 `t23-fontscale-00-before.log`（`null` = 系统默认 1.0）、
置档与跑批 `t23-connected-fs2.0-ai-screens.log`（置档回显 `2.0`）、
收工 `t23-connected-fs2.0-appscreen-and-restore.log`（回档后回显 `1.0`）。
2.0 档下 AI 两屏 25 枚、`AppScreenTest` 50 枚全绿，未见折叠行与行尾徽章被挤出或裁切。

### 凭据

- 三枚 RED：`t23-RED-1-jvm.log`（3 红：禁字徽标 / Spark 字形缺席 / 静态读点未清）、
  `t23-RED-2-jvm.log`（1 红：徽章产出缺失与屏侧字面量）、
  `t23-RED-3-connected.log`（`Tests run: 14, Failures: 5`，全部为 `AssertionError` 级判据红，非编译错）。
- 门控收工：`t23-gate-final.log` → `suites=238 tests=1764 skipped=0 failures=0 errors=0`
  （基线 237/1756，净 +1 类 +8 枚，`@Test` 只增不减）。
- connected 收工：`t23-connected-fs1.0-ai-screens.log`（25 OK）、
  `t23-connected-fs1.0-appscreen.log`（首跑 50 中 1 枚 `startLearning…` `waitUntil` 5s 超时，
  单跑 OK、整类复跑 50 OK ⇒ 判为顺序相关偶发，且该用例走背词链路不触 AI 面）、
  `t23-connected-fs2.0-ai-screens.log`（25 OK）、`t23-connected-fs2.0-appscreen-and-restore.log`（50 OK）。
- 渲染取证：`t23-render-evidence-icon-gallery.log` + 三张 `icon-gallery-*.png`
  （写到设备外部文件目录，**不入库**，pull 到仓库外临时目录目视后删除）。

### 登记给后续批次的两笔

- **R-23.11 未做面**：两屏 `Mint*` 裸常量各 23 处（非 import/注释行；记号总出现数 33 / 29），
  `DarkTokenConsumptionRatchetTest` 不计它们、plan 未授权本席扩面，留给 Task 26。
  重算命令：`grep -oE "\bMint[A-Za-z0-9_]+" app/src/main/java/com/example/englishlearning/ui/AiFeatureScreen.kt | wc -l`
  （另一枚同形，把文件名换成 `AiLearningScreen.kt`）。
- **AppIcons 台账里等 Task 23 的另一件事没做**：`components/AppIcons.kt` 文件头第 19/21/22/23 行写着
  「七功能映射由 Task 23 钉定」（Mic/Camera/Image/Document 各有所指），但本席派工块 R-23.10 只裁决了
  「徽标换 `AppIcon.Spark`」这一枚形状，**未授权**七枚逐功能分形。该映射需要一份决策文档再落码，
  现登记为缺口，不当作已完成。

## 剥注释夹具的债（Task 12 修复轮 1 的 Fix 1，登记口径）

`app/src/test/java/com/example/englishlearning/ui/SourceRatchet.kt` 的 `stripComments` 现为**字符串感知**扫描实现：
字符串字面量（单行双引号串、三引号原始串、字符字面量）整段保留，只在字符串外剥行注释与块注释。
旧的正则版会把 SAF MIME 字面量里的「斜杠紧跟星号」当块注释起始，一路吞到下一条行尾注释的闭合序列，
把中间几十行真代码从棘轮视野里删掉（现场：`ui/BackupRestoreScreen.kt` 里裸用错误色渲染提示的那一处）。
两条钉：`stripComments_keeps_code_after_a_string_literal_that_contains_a_comment_marker`（合成输入，同时钉「真注释仍被剥」）
与 `notice_family_ledger_pins_the_backup_restore_site_that_comment_stripping_used_to_swallow`（真实文件锚 + seed 台账对齐）。
规则一律未放宽、seed 一律未加注（该文件本就在提示条族的台账里）。

## connected 环境债（曾挡下全部 compose 用例，2026-10-19 已定位并恢复）

修复轮 1 报「所有 compose connected 用例同报一条
`java.lang.RuntimeException: Unable to resolve activity for: Intent { … cmp=com.example.englishlearning.test/androidx.activity.ComponentActivity }`」。
**根因不在代码**：`app/build.gradle.kts` 与 `app/gradle.lockfile` 在本轮区间零改动，
`aapt2 dump xmltree --file AndroidManifest.xml app/build/outputs/apk/debug/app-debug.apk` 现场证明
`androidx.activity.ComponentActivity` 就在**应用包**清单里且 `exported=true`
（`debugImplementation(ui-test-manifest)` 供给的是应用包，测试包清单里 0 处属正常；
测试包 `instrumentation targetPackage` 也确实是 `com.example.englishlearning`）。真因在设备侧：
AVD 的临时 `/data` 镜像元数据加密挂载失败（logcat：`[libfs_mgr] skipping mount due to invalid magic, mountpoint=/data`
→ `vold: fscrypt_unlock_user_key 0 serial=0 token_present=0` → `StorageManagerService: …UserController.unlockUserCleared`），
用户 0 停在 `RUNNING_LOCKED`，于是所有 CE 存储里的包组件一律解析不到——连有 MAIN/LAUNCHER 过滤器的
`BrandLaunchActivity` 也用 `am start -n` 报 `Error type 3 … does not exist`、`monkey` 报 `No activities found to run`，
而 `dumpsys package` 的 Activity Resolver Table 里明明列着它。`sys.boot_completed=1` 照常置位，是假信号。

诊断与处置（已同步补进 `android-connected-qa-loop` 怪癖表）：

```bash
adb -s emulator-5574 shell dumpsys user | grep 'State:'      # 判据：RUNNING_LOCKED 即此症
# 无效尝试（别再走）：keyevent 82 / wm dismiss-keyguard / 上滑 / 唤醒 / 整机重启 / 加减 -read-only
emulator -avd xtool-s04-api30 -no-window -no-audio -no-boot-anim -no-snapshot -wipe-data -port 5574
adb -s emulator-5574 shell dumpsys user | grep 'State:'      # 期望 RUNNING_UNLOCKED
```

`-wipe-data` 在本仓判为非破坏性：五枚 AVD 的 `config.ini` 均写 `disk.dataPartition.path = <temp>`，
数据盘按设计每次开机即弃，无可丢的用户数据；真机 `bf353dda` 全程未碰（只按里程碑装过 debug 包，
未跑 `am instrument`、未装测试包）。恢复后按类实跑见上表 AC-R-04/05b 与门禁节，合计 27 用例 0 失败。

## 仍待用户目视（只有人眼能判的项，本棒不替用户判定；枚数以本清单在册行现算，不写死）

① 六档 `AppType` 未写 `lineHeight` 后正文是否偏紧（判「太紧」则触发批 2 在装配点补，并先把取值写进规格 §2.2）；
② 排印换档的整体观感（逐屏看有没有被撑破或挤扁的头部）；
③ M3 控件默认色由库紫转薄荷 + Card 圆角 20→18dp、`colorScheme.outline` 换成 `text2` 是否可接受；
④ 跟随系统的深色档与 `fontScale 2.0` 下底导与设置页不裁；
⑤ 打卡页完成度环的轨道色（Task 22-A / R-22.2：奶油 `#FFF1DB` → 语义槽 `tint`，明档变薄荷、暗档变深）是否可接受，
   还是要为「奶油轨道」单开一枚令牌（那属计划修订，不属实现席自决）；
⑦ 打卡页页头两条裸文字链摘除后（Task 22-B），从打卡页**找不到**收藏与错词的直达入口——须确认
   「经底部『我的词』进合并页分段」这条替代路径在人手里可达且可接受（自动化只证明合并页能走通，证明不了「用户知道要去这儿」）。

序号口径：原⑥「打卡页环内完成度数位是否偏小」随本清单的虚假代价登记一并撤销（该改写是等价改写，
本屏字号未变，见「打卡页版面收敛」节 R-22.3 条与「代价」节）；圈号按稳定 ID 使用，**空出的⑥不复用、⑦及以后不重编号**，
在册枚数以下面那条命令现算。

重算本清单在册枚数：`sed -n '/^## 仍待用户目视/,/^## /p' docs/verification/ui-redesign-2026-10.md | grep -cE '^[①-⑩]'`。

## 真机深色实勘（2026-10-19 批 1 里程碑装机，`bf353dda`）

装的是本分支 debug 包（`adb install -r`，未跑 `am instrument`、未装测试包），系统深色用 `cmd uimode night yes` 切换、
看完即恢复 `night no`。**结论：主题层深色链路在真机上成立**（根底、页头、卡片容器随系统深色整体翻深，
`rememberAppThemeMode()` 活链路无需重启），但**业务屏处于半迁移态，今日计划屏深色下有一处可读性缺陷**：

- **现象**：「今日学习进度」卡深色下呈**白底 + 极浅文字**，三行文案几乎看不清；同屏「今日新增/今日复习」两枚计数卡仍是白底深字（浅档外观）。
- **机理（已核到定义行）**：Task 3 把 `Mint*` 中性令牌改成组合期读取（`MintPalette.kt:58` 的 `MintPrimaryDark` 现在是带 getter 的动态值），
  于是**墨色侧跟主题走了**；而该屏容器仍写 `AppPalette.Surface`，它是 `AppPalette.kt:12` 的**静态** `Color(0xFFFFFFFF)`，不跟主题走
  → 深色下变成「浅容器 + 主题浅墨」。可重算：
  `grep -n 'val Surface' app/src/main/java/com/example/englishlearning/ui/theme/AppPalette.kt`（应为静态 `Color(0xFFFFFFFF)`）、
  `grep -n 'AppPalette\.Surface' app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt`（该屏容器读点）。
- **口径**：这正是 `DarkTokenConsumptionRatchetTest` 台账在册的深色令牌消费债，也是 AC-R-11 当时判「未开始」的原因 —— 批 1 只交付底座，
  逐屏容器迁移按计划在批 2（今日计划屏 = Task 14）/批 3 收。**批 1 不越界补迁移**（那会同时改动 seed 台账与业务屏版面，属批 2 的评审面）。
- **后续兑现（2026-10-20 追记，不改写上面的历史判断）**：本节点名的这族缺陷已在本批收掉——今日计划屏容器现读
  `appColors().surface`（可重算：`grep -n 'AppPalette\.Surface' app/src/main/java/com/example/englishlearning/ui/TodayPlanScreen.kt` 为空），
  `AppPalette.Surface` 在 `app/src/main` 的实读点也已归零（仅剩 `components/ScreenUnavailable.kt` 一条过期注释在册，见阶段报告 §5.2.9）；
  玻璃层那四枚容器与面板内前景按 R-18-R1 同批迁完，AC-R-11 现判「进行中（迁移与设备侧取样实测已收，人眼两套目视未做）」。
  「开系统深色会看到半深色界面」这条对用户的实际影响，核心屏一侧已随批 2 消退，剩余族（`Mint*` 页底、未迁屏）仍按批 3 收。
- **对用户的实际影响（必须知道再决定推进顺序）**：**开系统深色的用户在批 2 收完核心屏之前会看到半深色的界面**，
  浅色档无此问题。合 main 与对外分发前，要么先完成批 2，要么把「全站静态容器读点一次性改读 `appColors()`」提到批 2 最前面做（控制者建议后者，见交接报告 §5）。
- 截图证据按红线**不入库**（含真实词书与进度数据），留在本机忽略区；本节的判定以「现象 + 定义行 + 重算命令」为准，不依赖截图。
