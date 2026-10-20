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
| AC-R-07 | `fontScale = 2.0` 的 connected 用例覆盖六屏重排后的标题与卡片 | **批 2 已覆盖（Task 18i 收口，2026-10-20 复跑在案）**：① `AppScreenOverlayContainerTest` 两枚大字号用例 `hostedWordBookDeletePanel…` / `hostedWordBookExportPicker…`——17B 修复轮口径：运行前置为**设备级** `font_scale=2.0` + 行高哨兵 `assertScaleTookEffect` 防「scale 1.0 空转」（自然 scale 整类 `Failures: 2` 响亮红样留档 `t18-connected-AppScreenOverlayContainerTest-scale1-naturalred.log`；前置 2.0 下整类 `OK (4 tests)`）；② `WordCardScreenTest.lemmaAndRevealedMeaningStayFullyVisibleAtMaximumLargeFontScale`（Task 15 Step 3 护栏，组合级 `fontScale = 2f` 下词卡主词与揭晓释义不被裁出卡片，`OK (17 tests)` 类内通过）；③ 护栏出处 `CheckInScreenTest.headerKeepsLinksReachableAndTitleReadableAtMaximumLargeFontScale`（`OK (7 tests)`）。**批 2 未覆盖，批 3 续**：今日计划屏主体卡、词卡详情屏正文、文章阅读、设置族、词典/词书族屏的大字号用例仍缺（本轮逐类 connected 全绿但未为其新增 scale 断言，不虚报） | 部分通过（批 2 覆盖屏证据齐；缺上列五族屏，批 3 续） |
| AC-R-08 | §3 每个共享组件有「全站唯一实现」的静态检查；被替换的私有份数降到 1 | `SharedComponentUniquenessRatchetTest`：四族（页头 / 错误提示条 / 未配置卡 / 词行）按形态规则逐族**精确集合相等**，seed 是债务台账（只减不增，接线一屏删一行）；批 1 组件文件按「点名文件必须存在 + 公开组件在 `ui/` 扫描面恰一处顶层定义」钉；预登记豁免（本棘轮不管「就地改既有组件取值面」，Task 14 改 `PillButton` 本体不受挡）由 `the_ratchet_blocks_a_second_component_but_never_an_in_place_value_edit` 双向证明。**批 1 终审 F7 登记的第三条边界**：族 1/2/4 的判定面各带「本文件调了共享组件即整体合规」出口，出口同时是**已知失明面**——接线后同屏残留的私有 forbidden 形态会整体隐形；`family_compliance_exits_are_a_declared_known_blindness_surface` 双向钉住该语义，批 2 的删 seed 判据因此是「该屏 forbidden 读点清零」而不是「补一行调用就准删」，收紧（seed 改读点计数）属计划修订事项 | 通过（存量私有份未清零，清零归批 2/批 3；见「本族规则形状的已知边界」） |
| AC-R-10 | 六屏 360dp 真机浅/深两套截图逐屏比对设计稿 | 批 2/批 3 逐屏补 | 未开始 |
| AC-R-11 | 玻璃层与域色容器在深色下文字可读性实测 | **覆盖方式（批 1 终审登记 → 批 2 收口兑现，裁决 R-18-R1）**：本行原本的实测面是全站 `AppPalette.GlassFill` 的四枚容器消费点（`glass/GlassDialog.kt`、`glass/GlassOverlay.kt`、`AiProfileSettingsScreen.kt` 的 `SectionCard`、`SpeechSettingsScreen.kt` 的 `PreviewCard`）。批 2 收口把「容器填充与面板内前景墨色同批迁」真的同批迁完：四枚填充改读 `appColors().surfaceGlass`，面板内排印点（A 席 8 枚消费者 16 处 + B 席三块面板内的墨）一并按套。现盘可重算：`grep -rn 'color *= *AppPalette\.GlassFill' app/src/main/java --include=*.kt` 为空（余下命中全在注释/KDoc）。浅套逐值不变有契约腿钉着（`surfaceGlass`=`0xEFFFFFFF`、`text1`=`0xFF1C1C1E`、`surface`=`0xFFFFFFFF`、`outlineSoft`=`0xFFE4EFE9`、`text2`=`0xFF4E756A` 五对与旧静态逐字相同）；`GlassHighlight` / `Scrim` 属主题无关蒙版族（`ThemeInvariantMaskMembers`），有意不迁。<br>**深套那侧的证据不再是纸面推断**：connected 取样类 `GlassPanelThemeInkTest`（8 例）与 `GlassDialogThemeInkTest`（3 例）在设备上取真实像素带，逐枚断言「容器 = 套内深玻璃合成底、前景 = 套内墨、且 WCAG 现算 ≥ `BODY_AA_RATIO`」，并各带反空转腿（拿迁移前那枚静态墨喂同一条闸必须判红）。<br>**八枚玻璃对话框消费者的 connected 面已随修复轮复跑**（`ArticleReadingScreenTest` 23、`ImageStudioScreenTest` 11、`ReadingAccessScreenTest` 13、`SentenceAnalysisScreenTest` 7、`WordAiQaScreenTest` 8、`AiProfileSettingsScreenTest` 23、`PillButtonTest` 9、`GlassDialogTest` 1，11 类 109 例 0 失败，逐类出处见上面「批 2 全分支终审与修复轮复跑」段）。<br>**本批顺带闭掉一枚既有 AA 债**：浅套 `AiSpeech.deep`（`#188F76`）压浅玻璃合成实底 `#FEFFFE` 现算只有 **4.006:1**，而它落的那两行是 `style = AppType.Body`（正文档，线是 4.5）——取样类一度用 `DECOR_AA_RATIO = 3.1` 把它钉成「合格」，那是按装饰档放行正文档。按房内既有先例 `AppBottomBar.kt:95`（17A 修复轮 `0ed3984`）的形状按套归位：浅套取 `appColors().onMint`（`#174C44` 压同底 **9.727:1**）、深套沿用 `deep`（`#35E0AC` 压 `#161F1B` **9.960:1**），站点是 `AiProfileSettingsScreen.kt` 的密钥态行与音色浮层选中行；`DECOR_AA_RATIO` 常量随之删除。浅套这一处观感会变（薄荷绿墨→深墨），属修 AA 失败，已列入 18-ii 用户目检。同族其余把 `AiSpeech.deep` 用在**文字**上的静态读点（可重算：`grep -rn 'DomainColors\.\(AiSpeech\|Reading\)\.deep' app/src/main/java --include=*.kt`，再逐枚看它的 `style` 与所在容器），批 3 迁按套时按同一条规则落 `onMint`（浅）／`appDomain(...).deep`（深）、**不得再迁成静态 `deep`**；非文字图形档不受本条约束（例如 `TodayPlanScreen.kt:127` 那枚加载进度环的 `color`，装饰档 ≥3.1 已达标）。<br>**这批残留站点的比值本席已现算入册（不是留给批 3 再发现）**：`AiSpeech.deep` `#188F76` 压浅页底 `#F1FBF5` = **3.797:1**、压浅玻璃 `#FEFFFE` = 4.006、压深卡底 `#17201C` = 4.151、压深页底 `#0E1512` = 4.609（三档浅侧与深卡底都不达正文 4.5）；`Reading.deep` `#C81E46` 压浅页底 = 5.324（达标）、压深页底 = **3.287**、压深卡底 = **2.961**（后者连装饰档 3.1 都不达）。落点面（现盘行号，随批 3 改动会漂）：`AiProfileSettingsScreen.kt:152/218/234/250/429/434`、`SpeechSettingsScreen.kt:111/142/181/312`，全部是 `AppType.Label`／`AppType.Body` 文字。**为什么本批不动**：这些行的容器底都不是批 2 迁的那四枚玻璃面板（`PreviewCard` 之外、`SectionCard` 块在 :423 已闭合），比值劣化不是批 2 造成的；且 `Mint*` 页底族与 `AppType` 直读墨的摘色本身就是批 3 的任务面（结转条目见交接报告 §5.2），在这里单点改墨会把批 3 的成套迁移拆成碎改。<br>**批 3 硬约束（本行当场钉为验收口径）**：批 3 迁完这一族后，上述每一枚文字站点必须 ≥4.5（浅深两套各自现算，取样用 `GlassPanelThemeInkTest` 同一条 `assertContrastAtLeast` 腿，不许再按 3.1 放行）。<br>**仍未做**：六屏浅/深两套真机截图与设计稿逐屏目视比对（那是 AC-R-10 的面，归 18-ii 用户侧 + 批 3），以及非玻璃容器族（`Mint*` 页底族）的深色实测。 | 进行中（迁移侧与设备侧取样实测已收口；人眼两套目视与剩余族未做） |
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
- 兑现时机：批 3 接线该屏时，两处一起改调 `WordRow`；届时同步删 seed 行并复核这条边界是否仍在。

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

## 仍待用户目视（只有人眼能判的四项，本棒不替用户判定）

① 六档 `AppType` 未写 `lineHeight` 后正文是否偏紧（判「太紧」则触发批 2 在装配点补，并先把取值写进规格 §2.2）；
② 排印换档的整体观感（逐屏看有没有被撑破或挤扁的头部）；
③ M3 控件默认色由库紫转薄荷 + Card 圆角 20→18dp、`colorScheme.outline` 换成 `text2` 是否可接受；
④ 跟随系统的深色档与 `fontScale 2.0` 下底导与设置页不裁。

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
