# 2026-11-09 批 D（页面转场）收口工作报告

分支 `stage-5-ui-r2-learning-path`，本席起点 HEAD `d347b86`（`git rev-parse --short HEAD` → `d347b86`），开工前 `git status --porcelain | wc -l` → `0`。
本席是批 D 的**收口席**，只做 `task-D4-brief.md` 的 **Step 5（真机走查交接准备）+ Step 6（矩阵登记）+ Step 7（secret-scan）+ Step 8（报告与提交）**；
Step 1/2 的读数来自取证席 `task-D4a-report.md`，Step 3/4 的读数来自 `task-D4b-report.md`，本席**未重做、未新跑任何设备或门禁腿**。
**推送不在本席职责内**（须用户点头），本报告 §4 的远端 tip 留空待回填。

标注沿用两取证席的口径：**【测】** = 命令原样读数（本席自跑或注明出处），**【推】** = 推断，**【算】** = 由规格常量算出、不是量出来的。

## 1. 本次目标

批 D 的用户目标（2026-11-08 拍板，原文口径见 `docs/decisions/2026-11-08-page-transition-routing-and-motion.md`）：
**把「一级 tab 切换 + 全屏子页进出 + 对话框浮层」这三类换屏的动效全部重做**——方向滑动 + 淡入淡出，规格集中在唯一落点、
判据集中在唯一解析器，并让「哪一屏可见」这一件事在全仓只有一处权威；本段（D-4）负责取证、矩阵登记与收口。

## 2. 完成了什么

批 D 四段的落地清单（逐段细节见 `.superpowers/sdd/2026-11-06-motion-and-adaptive-layout/task-D1…D4-report.md`；
本席实测 `git diff --stat b075060^..d347b86 -- app/src` → **21 个文件、3324 增 / 1406 删**，其中 `AppScreen.kt` 的 2847 行变更绝大多数是
链体入 `AnimatedContent` 后的**缩进位移**（`0e9b06a` 一段是纯缩进提交））：

- **D-1（`9ea2d72` + 修复轮 `fd3eeed`）纯函数层**：新增 `ui/AppOverlayRoute.kt`、`ui/AppOverlayRouteResolver.kt`（54 行，35 枚挂载判据逐字搬入、
  顺序照抄即 z 序与返回键优先级承诺）、`ui/AppMountFlags.kt`（35 枚形参）、`ui/ScreenTransitionDirection.kt`（方向由声明序号比较得出）；
  （2026-11-09 终评补口订正：方向判据今天比对的是同一张 route 表的 `transitionDepth` 列——默认等于声明序号、只在点名且有发起点证据的
  from→to 边上覆写，`TRANSITION_DEPTH_OVERRIDES` 现值 `ArticleReading = 28` / `LearningFlow = 6`；声明序号 `mountIndex` 只承担挂载与
  返回键优先级。覆写清单 == 命名边用例清单由契约闸双向钉。详见决策册「2026-11-09 修正」修正 1）；
  新增 JVM 测试 `ui/AppOverlayRouteResolverTest`（9 枚）、`ui/ScreenTransitionDirectionTest`（7 枚），表驱动逐行「除本支判据外还点亮紧随一支」，
  配两次变异探针。
  （2026-11-09 终评补口订正·枚数现算：`ui.AppOverlayRouteResolverTest` **9**、`ui.ScreenTransitionDirectionTest` **15**（补口第 1 条加 8 枚：
  4 对命名前进边 + 3 对反向边 + 覆写值不顶穿 tab 宿主）、`ui.ScreenTransitionContractTest` **17**（建类 6 枚 → 批 D 收口 11 枚 → 补口席 B1 +3、席 B2 +3）、
  新增 `ui.AppOverlayRouteNavigationIdentityTest` **13**（补口第 2 条）与 `ui.ScreenTransitionComposeFiredPronunciationContractTest` **4**（补口第 4 条）、
  `androidTest ui.ScreenTransitionInstrumentedTest` **9**（原 5 + 补口第 2 条 3 + 第 5 条 1）；命令 `grep -c '@Test' <文件>`，矩阵 AC5-07 行已同步）
- **D-2（`0f92e82`）链改为消费 `route`（行为等价、零动画）**：`AppScreen.kt` 链 35 枚挂载级判据全部改成读 `route`，
  链尾 tab 宿主腿收成唯一 `route == null`；新增源码契约闸 `ui/ScreenTransitionContractTest`（建类 6 枚，现 11 枚）；
  9 枚既有源扫契约测试重锚到新形状。**本段不是严格 TDD**，见 §5(b)。
- **D-3（`82a8171` + 修复轮 `c1952ce`/`0e9b06a`/`f8e288e`/`d347b86`）两站转场**：`ui/theme/AppTransition.kt`（58 行）成为时长/缓动/位移的唯一规格宿主
  （`AppMotion.Slow` 300 ms、入场 `EasingOut`／退场 `EasingIn`、`PageOffsetDenominator = 4` 即 ¼ 幅、crossfade 两腿无位移）；
  站 1（全屏子页链，`AppScreen.kt:776`）与站 2（tab 分发，`AppScreen.kt:2131`）各包一枚 `AnimatedContent`，退场腿在自己的 lambda 参数里读**冻结的旧 route**（2026-11-09 终评补口订正：站 1 今天落在 `AppScreen.kt:782` 与 `:2138`；准确说法是「**挂载**由解析结果选出」——渲染半边只在该腿确实读取的 payload 字段上是冻结的，腿体内仍有 20 处 `collectAsState()` 读实时 VM，属决策册「2026-11-09 修正」修正 3 记明的取舍，不是已兑现的承诺）；
  对话框/浮层并入批 A 口径表（无位移）；离场腿加 `invisibleToUser` 语义、入场朗读时机零改动；退场腿出站闸（F5-06 红线：转场不得产生第二次计费出站）；
  新增 connected `androidTest ui.ScreenTransitionInstrumentedTest`（5 枚）；`GlassOverlay` 的 `tween(` 并表后由 `MotionSpecContractTest` 重算锚点。
- **文档面（D 批开工提交 `b075060`）**：`docs/decisions/2026-11-08-page-transition-routing-and-motion.md`、
  `docs/specs/05-release-quality-and-compliance.md` 补 F5-06 七条 + AC5-07、`docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md` 批 D 展开。
- **本席（收口）产出**：
  - `docs/verification/ac-traceability-matrix.md`：新增 **AC5-07** 行、补上批 C 漏登记的 **AC5-06** 行（两行的证据列点名 4 枚批 D 测试类 + 决策/计划/分段报告路径）。
  - `docs/verification/ui-redesign-2026-10.md`：续写「2026-11-09 批 D」一节（门禁对照式、双形态 connected、截图册、`clip` 取证、`gfxinfo` 同包 A/B、取样纪律、待用户目视）。
  - 本报告。

## 3. 验证记录

### 3.1 三连门禁（出处：`task-D4a-report.md` §一，本席未重跑）

命令顺序照简报原文（`assembleDebug` → `testDebugUnitTest --rerun-tasks` → `compileDebugAndroidTestKotlin`，`--no-daemon --no-build-cache`），
【测】`BUILD SUCCESSFUL in 4m 9s`、`GATE_EXIT=0`，过滤集合内无 `FAILED`、无 `e:` 行。
判据本体是本轮新生成的 XML（`find -name '*.xml' -newermt "2026-11-09 00:06:21" | wc -l` → 339 = 目录内 XML 总数，排除陈旧件）：

【测】**339 个测试类 / 2522 条 / 0 失败 / 0 错误 / 0 跳过**（`task-D4a-report.md` §一求和读数逐字）。

**对照式（基线只增不减）**：简报基线 `336 类 / 2494 条` + 本批逐段新增，逐段数字取自台账
`.superpowers/sdd/2026-11-06-motion-and-adaptive-layout/progress.md`（D-1、D-2「结果」两行）与 `task-D3-report.md:62`、`:179`，非本席现算：

| 段 | 提交 | 类 | 条 | 本段新增与内容 | 出处 |
|---|---|---|---|---|---|
| 批 D 开工基线 | `b075060` | 336 | 2494 | — | `task-D4-brief.md` Global Constraints |
| D-1 | `9ea2d72`（修复轮 `fd3eeed` 方法数净 0） | 338 | 2510 | **+2 类 / +16 条** = resolver 9 + direction 7 | `progress.md` D-1「结果」、冲突扫描 #6 |
| D-2 | `0f92e82` | 339 | 2516 | **+1 类 / +6 条** = `ScreenTransitionContractTest` 建类 6 枚 | `progress.md` D-2「结果」 |
| D-3 | `82a8171` → 修复轮 `d347b86` | 339 | 2522 | **+6 条**（`2516→2521` +5 枚契约方法、`d347b86` +1 枚判据④），类数不变 | `task-D3-report.md:62`、`:179` |
| D-4 收口复跑 | `d347b86` | 339 | 2522 | 0（与 D-3 终态持平） | `task-D4a-report.md` §一 |

算式自洽【推】：336 + 2 + 1 = **339**，2494 + 16 + 6 + 6 = **2522**，与门禁 XML 读数逐位相等。
detekt/ktlint 存量债（在册约 1580 条）按简报不当回归，本批未跑、未计入。

### 3.2 双形态 connected（出处：`task-D4a-report.md` §二，`adb install -r` 双包 + `am instrument`，全程未用 `connectedDebugAndroidTest`）

| 形态 | `ui.AppScreenTest` | `ui.ScreenTransitionInstrumentedTest` | `ui.RecoveryScreenTest` |
|---|---|---|---|
| Compact（`Physical 1080x1920 @ 420`，两条 `Override` 行 `grep -i override` 无匹配） | **首趟 `Time: 124.009` / `Tests run: 66, Failures: 1`**；**隔离单例 `Time: 3.757` / `OK (1 test)`**；**整类重跑 `Time: 114.387` / `OK (66 tests)`** | `Time: 11.339` / `OK (5 tests)` | `Time: 4.947` / `OK (5 tests)` |
| Wide（`Override size: 2160x1600` @ `Override density: 320`，sw=800dp） | `Time: 129.456` / `OK (66 tests)` | `Time: 11.068` / `OK (5 tests)` | `Time: 4.983` / `OK (5 tests)` |

**Compact 的 `AppScreenTest` 一腿必须写三条读数**：简报「两侧末行 `OK (n tests)` 原文入册」在这一腿**不成立**，首趟是红。
红点原文（`task-D4a-report.md` §2.1，逐字）：
`startLearningOpensTheWordCardAndBackReturnsToTheTodayPlan(com.example.englishlearning.ui.AppScreenTest)` →
`androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms`，抛在 `AppScreenTest.kt:2866`
的 `waitUntil(timeoutMillis = 5_000)`（等 `today_plan_summary` 出现）。
这是本仓在册最久的同一枚 flake（同用例同行号，≥6 份早期报告 + 本批 D-2 报告 `task-D2-report.md:118`）。
【推】D-4a 的判性：Wide 一次通过、Compact 重跑一次通过，形状是整类连跑的 settle/负载抖动（隔离态 3.757 s 已贴近 5 s 预算）；
但**本批确实改了返回键后的换屏路径**，故不销案，留 §5(a) 的真机目视①定它。
【测】`@Test` 计数与 `OK (n tests)` 的 n 吻合：66 / 5 / 5，无 `@Ignore`（`task-D4a-report.md` §2.3）。
【测】Wide 腿只覆盖 sw=800dp 这一档（`2560x1600` 被本 AVD 钳到 `2160x1600`，登记钳制值），真实平板/折叠与多窗口分屏未取证。

### 3.3 截图册（出处：`task-D4b-report.md` §一，产物在 `.git/qa-scratch/d4b/`，不入库）

- 册 A `animator_duration_scale=1.0`：【测】5 次驱动合计 **132 帧**（12+11+35+10+64），其中 **12 帧**为可判定的两屏共存中间帧（逐驱动 1 / 3 / 3 / 3 / 2）。
- 册 B `animator_duration_scale=0`：【测】13 帧里 **0 帧**共存中间态；学习页三处稳态 md5 逐字节相同（`ae7317ac5c3675a835bbd6f78efeab57`），
  残差那一帧（对 before 18.38 / 对 after 5.38）差集只在 `y[308..713] x[53..1026]`，目视是**完整新屏 + 骨架条**（批 C 的加载形态）⇒
  【推】「系统减动效即定格」在模拟器侧成立，且该残差属数据加载、不是动画中间态。
- 位移口径：270 px = 1080 ÷ 4【算】（`ui/theme/AppTransition.kt:29 PageOffsetDenominator = 4`）、宽形态 540 px = 2160 ÷ 4【算】、300 ms = `AppMotion.Slow`【算】。
  【测】实测到的最大可见位移 **208–232 px**（出屏 −208/−202、入屏 +232/+152/+44，粉色地标法 +116/+130），方向与 ¼ 幅一致；
  **270 px 的峰值瞬间未被 `scale=1` 的任何一帧命中**——本席与 D-4b 都**不宣称「量到 270」**，该口径由
  `ScreenTransitionContractTest.pageDisplacementIsQuarterAmplitude` 钉住（【测】单类跑 `BUILD SUCCESSFUL in 16s` → `tests="11" failures="0" errors="0" skipped="0"`）。
- 采样率是这批读数的天花板：【测】`screencap`（raw）≈195 ms/帧、`screencap -p` ≈389 ms/帧、`screenrecord` 在本 AVD 只编 4–8 帧/0.7–0.9 s。
  【推】300 ms 窗口平均只落 1 帧，抓不到峰值属采样率限制，不能读成「动效没跑」。

### 3.4 `clip = true` 取证（出处：`task-D4b-report.md` §二，结转清单第 4 条②的模拟器侧旁证）

- 【测】宽形态 `scale=1` **不可稳定复现中间帧**：`V/wide_sub_open` 源片只编 4 帧（`f025` before=2.70 稳态、`f027` after=2.76 收尾）；
  `W/w2/W2`（screencap 连拍 12 帧）**0 个共存帧**，cap03→cap04 的 219 ms 空隙把整个 300 ms 窗口吞掉。
- 【测】故按简报的「辅助」口径改取 `animator_duration_scale=5.0`（1500 ms，**位移口径不变**）：`W/w3_5x/W3` 10 帧、中间帧 4 枚，
  最佳 `W3_05`（MAD before=11.86 / after=7.97）。入屏绿 CTA 右缘：稳态 `x=[40..2119]` → `W3_05` `x=[192..2159]`，
  左缘 +152 与 `shift.py` 的入屏 `dx=+152`（MAD 4.82，可信）独立吻合。
- **判读**：按 +152 px 位移，未裁时右缘应在 2271，帧里却止于屏末列 2159 ⇒ 该瞬间约 **111 px** 贴边内容（CTA 右端、行分隔线、右对齐「待学」列）
  被容器边界**硬切**；出屏导轨同瞬间未被切（最左列 41→14，仍在窗口内）；所有帧两层都止于窗口边界，**无画到容器外的涂抹**。
  竖屏同一行为已有直接旁证（`V/sub_open f009`、`A_Learn2Read/B1_04` 出屏左缘文字被屏幕左缘齐切）。
- 【推】这条读数是 `clip=true` 的**容器裁切行为本身**，属设计内（不越界、不外溢），**不是版面缺陷**；但「观感能否接受」只有人眼能判 ⇒ 进 §5(a) 真机目视②。
  注意：**111 px 这一枚测在 5× 辅助帧上**（宽形态 `scale=1` 中间帧不可复现），时长与相位不作结论。

### 3.5 `gfxinfo` 掉帧对照（出处：`task-D4b-report.md` §三；**裁定：同一安装包 `scale=1` vs `scale=0` A/B**，不构建、不安装旧包）

裁定理由与效力边界：`animator_duration_scale=0` 即「批 D 那两站 `AnimatedContent` 被平台抑制」的对照腿，
两臂唯一变量就是批 D 加的转场。每臂 15 步驱动（5 次 tab 切换 + 5 次子页开合），【测】驱动序列逐字相同（去数字后 `diff` 无输出，脚本回显 `DRIVE-SEQUENCES-IDENTICAL`）。

| 读数【测】 | 臂1 `scale=1`（转场在放） | 臂2 `scale=0`（转场被抑制） | 差 |
|---|---|---|---|
| Janky frames | **1532 (73.69%)** | **1567 (74.02%)** | −0.33 pp |
| Total frames rendered | 2079 | 2117 | −38（−1.8%） |
| 50th / 90th / 95th | 19 / 34 / 69 ms | 19 / 38 / 77 ms | 0 / −4 / −8 ms |
| Missed Vsync / Slow UI thread | 47 / 119 | 50 / 136 | −3 / −17 |
| Slow bitmap uploads / Slow issue draw | 3 / 69 | 3 / 69 | 0 / 0 |
| gpu 50th / 90th / 95th | 7 / 11 / 12 ms | 7 / 11 / 12 ms | 0 |

**裁定表述**：两臂差在 ±0.33 pp / ±8 ms 内，且**转场档没有更差**（90th/95th 反而低 4/8 ms）；73–74% 是本 AVD 软件渲染栈的基线水位
⇒ 在这台模拟器、这套驱动序列、这一构建上，批 D 两站转场**没有引入可测出的掉帧回归**。这不等于「转场零成本」，真机读数留给 §5(a)。

### 3.6 矩阵校验与密钥扫描（本席自跑）

- Step 6【测】`"$GRADLE" verifyAcTraceabilityMatrix --no-daemon` → `> Task :verifyAcTraceabilityMatrix` /
  `AC matrix covered 62 ids with evidence.` / `BUILD SUCCESSFUL in 9s`，**exit 0**。
  Gradle 发现方式照简报两行（`V=gradle-8.13`、`GRADLE=/c/Users/steveliuyan/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle.bat`，本地 `-bin` 发行版，未用 `gradlew`）。
  **如实写明**：该校验器按任务描述只覆盖 specs 00–04 的 `AC[0-4]-\d{2}`，那 62 枚 id 里**不含** AC5-xx；
  矩阵的「阶段 5 AC」一节（含本批新登记的 AC5-06/AC5-07）是**人工追踪台账**，其通过与否不由自动化门禁背书。
- Step 7【测】`bash scripts/secret-scan.sh > /tmp/ss.out 2>&1; echo $?` → **`EXIT=0`，输出 0 字节 / 0 行**。
  脚本静默即通过（`scripts/secret-scan.sh` 只在命中或环境异常时打印；本次扫描面含 git 跟踪文件与已构建的 `app/build/outputs/apk/debug/app-debug.apk`）。

### 3.7 2026-11-09 控制器补记：终评补口后的 connected 双形态复跑（原矩阵 AC5-07 的「待办」在此关闭）

补口各席只跑过单形态（Compact），矩阵 AC5-07 那行留下一条待办：9 枚全类的 Compact/Wide 双形态重跑。现已在最终内容上跑完，
并把中途观察到的一趟偶发红按「只加守卫、不放宽断言」硬化掉。全部走 `adb install -r` 双包 + `am instrument -e class`，
未用 `connectedDebugAndroidTest`，设备数据零清除；`bf353dda` 零命令。

读数（均为 `com.example.englishlearning.ui.ScreenTransitionInstrumentedTest` 全类，`-e class` 单类）：

| # | HEAD 内容 | 形态 | 结果 |
|---|---|---|---|
| 1 | `f3cc0c3`（补口前最后一次代码侧收口，硬化未加） | Wide（`wm size 2560x1600`，落为 2160x1600 override） | `FAILURES!!! Tests run: 9, Failures: 1` — 红点是 `transitionsProduceNoExtraBillableOutbound` 里 `pumpUntil` 的 3s 超时（`.superpowers/sdd/2026-11-06-motion-and-adaptive-layout/b4-wide-connected.log`），不是出站计数断言 |
| 2 | `ff4177c` 内容（加两道守卫） | Compact（1080x1920，无 override） | `OK (9 tests)` 21.75s |
| 3 | 同上 | Wide | `OK (9 tests)` 22.57s |
| 4–5 | 同上 | Wide | `OK (9 tests)` 21.03s / `OK (9 tests)` 20.34s |
| 6 | `ff4177c`（注释订正后重装） | Compact | `OK (9 tests)` 19.57s |

即 shipped 内容上 **Compact 2 趟 + Wide 3 趟 = 5 趟全类连绿**；唯一一趟红出现在硬化之前、且红在靶面守卫而非判据本身。

硬化内容（`ff4177c`，纯 androidTest，零生产码）：①按返回键前先断言词卡腿恰有一枚在场，把「转场没开播」与「驱动走空」分开；
②`pumpUntil` 超时文本带现场读数（站 1 腿数、各腿 tag、`word_card_screen` 在场枚数）。
同时订正一处旧定性：席 B2 报告 §connected 读数表第 2 行那趟红是在**摘掉 `contentKey` 的变异探针配置**里观察到的，
本就不该记成 shipped 配置的 flake（订正写进用例注释，B2 报告原文不改）。

门禁同步复跑（同一棵树）：`BUILD SUCCESSFUL in 4m 5s`，`341 类 / 2553 条 / 0 失败 / 0 错误 / 0 跳过`
（`--no-daemon --no-build-cache --rerun-tasks`，341 份结果 XML 全部为本次新落盘，按 mtime 计数而非沿用旧文件）。
设备收口状态：`wm size reset` → 1080x1920 / density 420 / `font_scale` 1.0 / `animator_duration_scale` 1.0，两枚 APK 在装。

## 4. Git 状态

- 【测】分支：`stage-5-ui-r2-learning-path`；本席开工 HEAD `d347b86`（`git rev-parse --short HEAD`）。
- 【测】远端跟踪 ref 实际存在的是 `origin/stage-5-ui-r2-learning-path` → `5d93cdc`（`git rev-parse --short origin/stage-5-ui-r2-learning-path`），
  且 `git merge-base --is-ancestor 5d93cdc HEAD` 通过（快进关系，无需 rebase）。
  未推送提交数：【测】`git log --oneline origin/stage-5-ui-r2-learning-path..HEAD | wc -l` → **16**
  （其中批 D 自身 10 枚：`b075060`…`d347b86`；其余 6 枚是 N-1 批次，已在另一席登记）。
  说明：台账 `progress.md` D-3 行写的「ahead 14」是该段收口时点的读数，与本席现算口径（对 `origin/<branch>` 现值）不同，**以本席现算的 16 为准**。
- 【测】本席新建的分功能域提交（文件清单见 §2 收口产出；两枚都只 `git add` 显式路径，未用 `git add -A`/`.`，
  都带 `-c user.name=steveliuyan -c user.email=2021250388@qq.com`）：
  - `5dc252c` `docs(verification): 批 D AC5-06/AC5-07 矩阵登记与转场读数表` → `git commit` 回执 `2 files changed, 122 insertions(+)`
    （`docs/verification/ac-traceability-matrix.md`、`docs/verification/ui-redesign-2026-10.md`）
  - `docs(handoff): 批 D 页面转场收口工作报告` → 本文件 `docs/handoff/2026-11-09-batch-d-page-transition-report.md`；
    这一枚的哈希无法写在自己的正文里，收工读数见文末「提交回执」。
  两枚落库后未推送提交数应为 **18**（上条现算的 16 + 本席 2 枚），收工复核 = `git log --oneline origin/stage-5-ui-r2-learning-path..HEAD | wc -l`。
- **远端 tip：待推送后回填**。本席**未执行任何 `git push`**（简报 Global Constraints：`git push` 逐批请示，绝不自动；推送是控制器的步骤，不是本席的）。
- 未提交的遗留文件：无。取证原始件全部在跟踪树之外的 `.git/qa-scratch/d4a/`、`.git/qa-scratch/d4b/`（277 个文件 / 56M，按简报「不入库」处置）。

**2026-11-09 终评补口收口补记（控制器现算，本席 D-4 那段数字是当时点、不回头改）**：

- 【测】`git rev-parse --short HEAD` → `ff4177c`；【测】`git log --oneline origin/stage-5-ui-r2-learning-path..HEAD | wc -l` → **33**
  （origin tip 仍是 `5d93cdc`；`git merge-base --is-ancestor 5d93cdc HEAD` 通过，快进关系）。
- 终评补口 7 条落成的 14 枚提交（旧→新）：`18945d0` `06f6d05` `046a9ea`（席 A：Spec/决策册）→
  `b1fd4bb`（第 1 条：层深列 `transitionDepth`）→ `46f4e9b` `45c9f59`（第 2、3 条：身份列 + `contentKey`、摘零读字段）→
  `8a24854` `2cfd11d`（第 5、4 条：状态 tag 无障碍判据、闭集发音闸）→
  `3fbe426` `46d6814` `5a30b81` `0c8cc2c`（第 6、7 条 + 26 处旧口径活文档）→ `f3cc0c3`（第 7 条代码侧收口）→
  `ff4177c`（connected 硬化 + 双形态复跑读数）。
- **仍未推送**：本批全程零 `git push`（用户规则：推送逐批请示）。推送后的远端 tip 待回填此处。
- 不合 `main`，分支停在 `stage-5-ui-r2-learning-path`。

## 5. 未完成 / 遗留问题

**(a) 真机目视 —— 由用户执行，本席零 adb 命令打到 `bf353dda`**

终评补口 第 5 条裁定：§5(a) 的真机目视按 D-4 Step 5 恢复成**全量五条**（TalkBack 只读一屏 / 转场跟不跟手 /
底栏随旧屏滑出 / 中途连点连按返回 / `clip` 贴边裁切），并把终评补口 第 1 条的 29→32 复现步骤列为**第一条目视项**。
模拟器侧只能给旁证（connected、截图册、gfxinfo），主证据在人眼：

1. **阅读历史点开一篇文章——第 1 条的 29→32 方向复现**。终评补口 第 1 条把 `ArticleReading` 的层深列覆写在三个列表臂
   （`ReadingHistory` 29 / `ReadingFeed` 30 / `ArticleImport` 31）之上（`transitionDepth = 28 < 29`），使「列表→文章」被判为**前进**。
   请在阅读 tab 打开阅读历史、点一篇文章：文章阅读屏应**从右侧滑入**，返回时**向左滑出**；若仍从左侧进场、或当场翻转不滑，
   则层深覆写未生效。命名边用例 `namedEdgeReadingHistory/ReadingFeed/ArticleImportToArticleReadingIsForward` 已钉住方向判据，此项验的是**观感**。
2. **TalkBack 是否只读一屏**。overlap 窗口内离场腿带 `invisibleToUser`、在场腿不带——站 1 由 connected 判据 2b
   `outgoingOverlayLegIsHiddenFromAccessibility` 点名被隐的是**离场腿自己那一枚** tag，站 2 由判据 2 点名 `tab_transition_leg_LEARNING`。
   请开 TalkBack 在换屏中途实测读屏焦点是否只落在一屏上（语义层已被测钉住，此项验读屏器的真实可达面）。
3. **转场跟不跟手**。¼ 幅方向滑动 + 300 ms 淡入淡出在真机上是否顺滑跟手。§3.5 的 `gfxinfo` 对照显示这台模拟器软件渲染栈
   在 `scale=1` vs `scale=0` 两臂掉帧差在 ±0.33 pp / ±8 ms 内、转场档未更差，但**真机读数与手感不在其中**，需人眼与手指判定。
4. **底栏随旧屏滑出**（决策 §3.4.1 的明示变化）。底栏（及 Wide 形态的侧栏 `AppSideNav`）写在站 2 退场腿里，tab 切换时它会**随旧屏一起滑出**——
   这是批 D 明示的形态变化，请在真机确认观感能否接受。
   （2026-11-09 终评补口订正·落点与触发都改了口径：底栏/侧栏**不在站 2 的退场腿里**，而在**站 1 的 tab 宿主腿**里
   （现算 `AppScreen.kt:2100` `} else if (route == null) {` → `:2111 Scaffold(` → `:2116 AppBottomBar` → `:2122 AppSideNav`，
   站 2 的 `AnimatedContent :2138` 在 Scaffold 的 content 之内）；因此**tab 切换时底栏/侧栏不动**（Spec 05 F5-06 line 58 的
   「tab 切换只动内容盒」），滑出发生在**全屏子页进出**那一站——退的是整支 tab 宿主腿。Wide 档侧栏与底栏同属这一支腿、同进同出，
   是同一条明示变化而非第二个变化，也不算缺陷（决策册「2026-11-09 修正」修正 4）。）
5. **转场中途连点 / 连按返回**。机制在册：退场腿在那 ~300 ms 的两屏 overlap 窗口内**仍可命中指针**——`invisibleToUser` 是
   **纯语义属性**（只影响 TalkBack 的可达面，不拦触摸），且腿内自声明的 `BackHandler` **按声明顺序后声明者优先**。
   请在转场进行中连点两下、或连按返回键，看是否出现「点进了正在退场的那一屏」或返回落点异常，据此决定**是否需要输入闸**。
   这条与 §3.2 那枚 Compact 返回后 5 秒超时红同源，其实测结果会影响那枚 flake 是否重新定性。
6. **`AnimatedContent` 默认 `clip = true` 的贴边裁切**。请在转场中间帧看两栏宿主与贴边内容（右对齐列、吸底 CTA、行分隔线）
   被硬切是否可接受；模拟器旁证（宽形态 5× 辅助帧上约 111 px 的贴边内容被切、竖屏出屏左缘齐切）已在 §3.4 与读数册入册。

装机命令（用户执行，**只 `install -r`，不跑 instrumented**）：

```bash
adb -s bf353dda install -r app/build/outputs/apk/debug/app-debug.apk
```

MIUI 注意：该机 MIUI 会在一段时间不用后**静默卸载/禁用**已装的 debug 包，装机后若桌面找不到图标，先重新执行上面这条命令，
再确认 `adb -s bf353dda shell pm list packages | grep englishlearning` 有回显；另沿用在册提醒——
MIUI 的「USB 安装」开关与息屏吞输入会影响走查，必要时先 `adb -s bf353dda shell svc power stayon true`。

**(b) D-2 不是严格 TDD（如实声明，不得记成逐条红-绿）**

三条链改造判据**没有跑在「未改造的链」上的真 RED，未留原始输出**。替代证据三项（`task-D2-report.md` §3、§4；`progress.md` D-2 已申报偏离行）：
①改链后、重锚前的**真实 11 枚红**——`d2gate1.log`：`2516 tests completed, 11 failed`，全是既有契约测试的锚失配；
②**四次变异探针**（每条判据各产出一枚对应的真红，再各自手工改回绿）；
③**HEAD 文字读数**（`} else if (` 34 枚 + 链头 1 枚 = 35 枚挂载级原判据、`is AppOverlayRoute.` 0 枚）——按判据定义，这三条在 HEAD 上必然全红。
代价已登记：本段不算严格 TDD，批 D 报告与矩阵行都不得宣称「先红后绿」；D-1、D-3 两段的 RED 有原始输出在册。

**(c) 三条 Minor，按裁定搁置，本段不修**

1. **`MotionSpecContractTest` 失败文案仍枚举已下线的 `GlassOverlay`**（`components/glass/GlassOverlay.kt` 的 `tween(` 在 D-3 并表后已清零）。
   文案陈旧会让红点误指，但判据本体（宿主文件集合与 `MIN_TWEEN_HOST_FILES` 计数）是正确的。
2. **按位置匹配的盲区**：契约/规格闸对 `tween(AppMotion.Slow` 这类**写法字面量**做定位匹配，改成等价写法（如先取局部变量再传入）能绕过。
   裁定：不加富余、不塞假 `tween(` 凑数（`progress.md` 冲突扫描 #9），风险由 `pageDisplacementIsQuarterAmplitude` 等值判据分担。
3. **`invisibleToUser` 弃用告警**在 `AppScreen.kt:2323`、`AppScreen.kt:2341` 与
   `ScreenTransitionInstrumentedTest.kt:88` 三处仍在。这是**有意保留**的写法：本仓锁定的 `ui-android:1.10.0` 只提供
   `SemanticsPropertyReceiver.invisibleToUser()`（即 `Modifier.semantics { invisibleToUser() }`），**不存在** `Modifier.invisibleToUser()`，
   而 Spec F5-06 锁死了这一语义写法。升级 Compose 时再按新 API 换形。
   （本席【测】只复核到行号与写法：`grep -n invisibleToUser` → `AppScreen.kt:2323`、`:2341` 两处 `Modifier.semantics { invisibleToUser() }`，
   `ScreenTransitionInstrumentedTest.kt:88` 是 `SemanticsProperties.InvisibleToUser` 引用；本席未跑编译器逐条取告警原文，
   D-4a 也只证「门禁过滤集合内无 `e:` 编译错误行」——两者都不等于告警已消失。）

**(d) 结转清单其余条目**

- **KDoc 活文档债**：`ui/AppOverlayRouteResolver.kt:6` 承诺「与 `AppScreen.kt` 链逐字一致」——今日由 `ScreenTransitionContractTest`
  的判据 1（正向白名单：链区每条顶层 `if (`/`} else if (` 必含 `route is AppOverlayRoute.` 或 `route == null`）与判据 3
  （链上 `is` 序列 == resolver 分支序列 == `mountIndex` 升序）兜住，属 D-3 评审点名在册的那句，**不必改码**。
  （2026-11-09 终评补口订正：这条判据今天钉的仍是**优先级**序列 == `mountIndex` 升序，`mountIndex` 不再被当作层深；
  层深另由同一张 route 表的 `transitionDepth` 列承担，并由「覆写清单 == 命名边用例清单」双向钉住，两枚判据互不替代。
  另：`AppOverlayRouteResolver.kt:6` 那句 KDoc 已于同日补上「序号不承担层深」，矩阵 AC5-07 行的在册债同步收口）
- **每 arm × 字段的消费点计数收紧**：现判据只到「每枚 route 字段至少一个生产读者」（`everyRoutePayloadFieldHasAProductionReader`），
  评审建议的「每枚 arm × 每枚字段」矩阵收紧**未拍板实施**，留作后续可选项，本段未动手。
- **异步屏以骨架退场（D-4b 新观察，登记未定性）**：站 2 的退场腿会重新组合，`remember` 态不跨腿保留 ⇒ 异步加载的屏在 300 ms 里
  以骨架形态退场（`V/tab_read2learn f021`），本地读盘屏以真实内容退场（`A_Learn2Read/B1_04`、`V/sub_open f009`）。
  目前判为**观感现象、不是缺陷**（无判据违例、无多余出站），交 §5(a) 真机目视与用户定；若判为缺陷，修法方向是退场腿复用旧屏的状态持有者。
  （2026-11-09 终评补口订正：补口第 2 条落地后，站 1 的**同身份数据刷新不再起腿**，这条现象在数据刷新路径上已消解，只剩真换屏时起腿；
  同一条裁定还立了出站红线的另一半边——**一次数据刷新不得重放「组合即发起」的出站**，今天由闭集源码闸
  `ui.ScreenTransitionComposeFiredPronunciationContractTest`（4 例）与 `transitionsProduceNoExtraBillableOutbound` 的 transport 计数腿分担。）

**(e) 其他在册遗留**

- **推送待请示**：16 枚未推送提交（见 §4），须用户点头后由控制器执行 `git -c credential.interactive=false push origin stage-5-ui-r2-learning-path`
  并用 `git ls-remote` 对 `rev-parse` 核验，再把远端 tip 回填本报告 §4 与 `progress.md`。
- **设备侧留了一处状态**：`emulator-5554` 上 `svc power stayon true` 未回写 false（D-4a 有意交给 D-4b 的常亮，
  沿用 D-3 处置防息屏吞输入）。若要出厂态，后续席位显式 `adb -s emulator-5554 shell svc power stayon false`。
- **Wide 形态只到 sw=800dp 一档**：`2560x1600` 被本 AVD 钳到 `2160x1600 @ 320`；真实平板/折叠更大 sw 与多窗口分屏未取证，
  凡声称「Wide 全档」的措辞都要加限定。
- **`app-debug-androidTest.apk` 时间戳停在 `2026-11-08 23:32:05`**（`packageDebugAndroidTest` 判 UP-TO-DATE 未重写）：
  D-4a 已用源码 mtime + 干净工作树双证与 `d347b86` 同源。**下一任若改 androidTest 源码，重装前必须复核这枚时间戳。**

## 6. 下一任接手须知

1. **接手第一步**：读本文件 + `docs/handoff/` 最新简报 + `.superpowers/sdd/2026-11-06-motion-and-adaptive-layout/progress.md`（批 D 正式台账；
   计划「## 台账」指向的旧册 `.superpowers/sdd/2026-11-06-motion-and-adaptive/progress.md` 只管批 A/B/C，**两份并存、不搬不删**）。
2. **门禁三连（顺序不可颠倒，`--rerun-tasks` 才算真跑）**：
   ```bash
   V=$(grep -oE 'gradle-[0-9]+\.[0-9]+' gradle/wrapper/gradle-wrapper.properties | head -1)
   GRADLE=$(find "$HOME/.gradle/wrapper/dists/$V-bin" -name gradle.bat -path "*/$V/bin/*" | head -1)
   "$GRADLE" :app:assembleDebug :app:testDebugUnitTest --rerun-tasks :app:compileDebugAndroidTestKotlin \
     --no-daemon --no-build-cache 2>&1 | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
   ```
   Gradle 从 wrapper properties 发现、必须是本地 `-bin` 发行版；**永远不要用 `gradlew`**（它会尝试联网下载并失败）。
   JVM 基线现为 **339 类 / 2522 条**，只增不减；detekt/ktlint 存量债不当回归。
3. **设备铁律**：instrumented 只在 `emulator-5554`，用 `adb install -r` + `am instrument`（**禁用 `connectedDebugAndroidTest`**，它会清空设备数据）；
   **真机 `bf353dda` 只做用户手工装机目视，AI 席位不得对它下任何 adb 命令**。
4. **两处站点锚点**（改动转场只在这两站，别处长第三站）：`AppScreen.kt:776` = 站 1 全屏子页链（`AnimatedContent` 包住整条 35 腿链，
   退场腿在 lambda 参数里读旧 `route`）；`AppScreen.kt:2131` = 站 2 tab 分发（`when (selectedTab)` 那一层）。
   链头解析点【测】在 `AppScreen.kt:734`（`val overlayRoute = AppOverlayRouteResolver.resolve(`），紧邻的 `:733` 注释解释了
   D-3 之后为什么链体 lambda 自己接名（`val route = overlayRoute` 那枚局部已按裁定删除）；
5. **规格唯一宿主**：时长/缓动/位移只在 `ui/theme/AppTransition.kt`（`PageOffsetDenominator = 4`、`AppMotion.Slow`）——
   **任何屏不得自带转场规格、不提供逐屏逃生口**，裸 `tween(` 不得散落在转场腿上；`AppTransition.contentTransform(direction)` 是唯一分派入口。
   判据（哪一屏可见）只在 `ui/AppOverlayRouteResolver.kt`，方向只在 `ui/ScreenTransitionDirection.kt`。
6. **不给 `AppScreen` 加参数**（`AppScreenProductionWiringTest` 是接线闸），**不新增应用内动效开关**（只跟随系统 `animator_duration_scale`），
   **转场不得产生第二次计费出站**（F5-06 红线，退场腿出站闸在 `AppScreen.kt:792` 附近，早于计数器自增 `:794`）。
   （2026-11-09 终评补口订正：闸的形状已从「只在这一枚附近」扩到**五处**且都早于任何发起——`grep -n "if (route != overlayRoute) return@LaunchedEffect"`
   现算 `AppScreen.kt:799 / :1094 / :1109 / :1304 / :1744`；其中只有 `:799`（查词详情腿 `speakWord`）与 `:1109`（词卡腿 `speakWord`）护的是计费发音，
   另三枚护的是本地取数与系统分享面板。补口第 4 条另立闭集源码闸锁「组合即发起发音」的发起点恰两枚；
   **一次数据刷新不得重放这些「组合即发起」的出站**——同身份换值不再起腿，因此不再重启 `LaunchedEffect`，这条与红线本身按原文同时成立。）
7. **取证环境的坑**（详见 `docs/verification/ui-redesign-2026-10.md` §2026-11-09 批 D「取样纪律」）：screencap/screenrecord 对 300 ms 转场是稀疏采样、
   每次驱动前必读 `mCurrentFocus`（本批两批帧因此作废）、H.264 噪声底 MAD≈2.4–2.9、`screencap` raw 带 16 字节头、`wm size 2560x1600` 会被钳到 2160x1600。
8. **中文注释的读取纪律**：源码与文档一律用 Read 工具读，**不要用 `cat`/`sed`/`head`/python 打印**——本仓控制台会腐坏中文注释，已造成过假证据。

## 7. 用户确认过的决策

批 D 全程由用户拍板、后续席位不得重问（原文落点 `docs/decisions/2026-11-08-page-transition-routing-and-motion.md` 与
`docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md`）：

- **范围**：一级 tab 切换 + 全屏子页进出 + 对话框浮层，**三类全部重做**（对话框不位移，只走批 A 的展开/收起口径）。
- **形态**：方向滑动 + 淡入淡出（前进从右来、返回从左去；同层互换只淡，不假装判得出方向）。
  （2026-11-09 终评补口订正：「同层互换只淡」在新口径下只对**身份不同**的同支互换生效——`navigationIdentity` 相同的换 payload
  是就地更新、根本不起转场腿，因此也谈不上淡；见决策册「2026-11-09 修正」修正 2 的口径连带段。）
- **架构**：**方案 1**——「哪一屏可见」由纯函数解析器集中给出，链上不留任何挂载级判据；屏渲染只认解析结果携带的冻结值。
  （2026-11-09 终评补口订正：后半句今天只兑现到**挂载**半边 + 该腿确实读取的 payload 字段。站 1 腿体内 `collectAsState()`
  现算仍是 20 处（`grep -n "collectAsState()" app/src/main/java/com/example/englishlearning/ui/AppScreen.kt | awk -F: '$1>=782 && $1<=2137' | wc -l`），
  退场那 300 ms 里旧屏内容仍可能变化，这是决策册 §4 与「2026-11-09 修正」修正 3 记明的取舍；「冻了却没人读」的那一支
  （`ArticleReading.historyArticle`）已按裁定摘除，零读者登记项现值只剩 `featureKey`。）
- **位移幅度**：**¼ 屏宽**（`PageOffsetDenominator = 4`），不整幅平移。
- **应用内动效开关**：**不做**；只跟随系统 `animator_duration_scale`，应用侧不新增降级分支或时长换算。
- **红线**：转场不得产生第二次计费出站；`animator_duration_scale=0` 下定格须与改动前静态版面逐字相同；
  顶档 3.0 字号与三档窗口类别的既有版面判据在动画结束后逐字复现。
  （2026-11-09 终评补口订正·两句：① 出站红线补上另一半边——**一次数据刷新不得重放「组合即发起」的出站**，
  同身份换值不再起腿、不再重启 `LaunchedEffect`，另有闭集源码闸 `ui.ScreenTransitionComposeFiredPronunciationContractTest`（4 例）
  与 transport 计数腿分担；② 「顶档 3.0 字号…逐字复现」**降为推定**：本批 connected 证据跑的是 `font_scale=1.0`
  （见 `.superpowers/sdd/…/task-D4a-report.md`、`task-D4b-report.md`），而仪器化断的是腿数与出站次数、3.0 档重跑也测不到排印，
  该档留待真机目视/走查册补证（走查册 §2026-11-09 批 D「待用户目视」已列第④条）。Spec 05 AC5-07 与矩阵 AC5-07 行同批改口。）
- **分支与推送**：**不合 main**；**逐批请示后才推送**（本批未推送，见 §4/§5(e)）。
- **`gfxinfo` 对照口径**（控制器裁定、用户未反对的取证裁定）：**同一安装包 `scale=1` vs `scale=0` 作 A/B**，不构建、不安装转场前的旧包。

## 提交回执

- `5dc252c` — `docs(verification): 批 D AC5-06/AC5-07 矩阵登记与转场读数表`
  （`docs/verification/ac-traceability-matrix.md` + `docs/verification/ui-redesign-2026-10.md`，`2 files changed, 122 insertions(+)`）
- 本文件所在的第二枚 — `docs(handoff): 批 D 页面转场收口工作报告`（哈希 = `git log -1 --format=%h` 在本提交后的读数；
  推送前的远端 tip 仍是 `5d93cdc`，推送与 `ls-remote` 核验由控制器在用户点头后执行，回执写进 `progress.md` 与本文件 §4）。
