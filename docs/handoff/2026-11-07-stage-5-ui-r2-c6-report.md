# 2026-11-07 批 C-6（预加载特效收口：截图册 + connected + 首帧读数）工作报告

## 1. 本次目标

按 `docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md` 的 C-6 条目收口批 C：
三连门禁 + 双形态 connected + `scale=0` / `scale=1` 截图册 + 首帧耗时读数 + 本报告 + 分功能域提交，
然后按「先不推，等批 C 整批收口」提出整批推送请示。

## 2. 完成了什么

- **新增 connected 取证类** `app/src/androidTest/java/com/example/englishlearning/ui/components/SkeletonShimmerEvidenceTest.kt`
  （7 例 = 7 枚骨架规格：通栏四条 / 前导点行 / 无前导点 96dp / 无前导点 48dp / 带壳行 / 骨架卡 / 统计格）。
  宿主是 `ActivityScenario.launch(ComponentActivity)` + `ComposeView.setContent`，**不用** `createComposeRule()`——
  批 A 已证测试帧时钟替身会让循环相位恒 `0.0`，`scale=1` 那张会拍成静息骨架，取证直接失效。
  每例在 `onActivity` 里打 `QA-C6 spec=<id> scale=<现值> holdStartMs` 并开 6 秒观察窗，
  截图走会话侧 `exec-out screencap`（不落应用私有目录，因此不需要临时 `adb root`）。
  入参逐条镜像生产调用点（`ArticleReadingScreen` 0.72/26·0.46/12·0.34/12、`VocabularyRelearnScreen` 0.24/10 与
  `SkeletonStatGrid(cells = 3)`、`GlobalVocabularySearchScreen` 96dp + 0.28/0.66、`LearningRecordsScreen` 48dp + 0.34/0.68、
  `WordBookWordsScreen` `framed = true` + 0.30/0.62、`WordCardScreen` `cells = 3, rowHeight = 54.dp`），
  不是自造的装饰档。
- **截图册两档采集脚本**（`.git/qa-scratch/c6/`，不入库）：`capture.sh <0|1>`（两档各 7×2 帧）、
  `capture1burst.sh`（`scale=1` 每规格连拍 12 帧）、`pick_frames.py`（按像素选中段帧并誊成
  `<规格>-scale1-mid.png` 7 枚）、`probe.py`（逐点取样：容器底色、骨架外接框、通道差、WCAG 比 + 两帧逐像素差）、
  `firstframe.sh`（`am start -W` 五轮）、`connected.sh`（双形态全量 + 大字号腿）。
- **文档**：`docs/verification/ui-redesign-2026-10.md` 新增「批 C-6」一节（截图册七行表、两条取样纪律、
  观感对比度表与候选档现算、首帧读数表、待裁决项）；
  `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` §4 末补「C-6 实测回写」三条判定，
  §5 第一行那句「全站第一帧不再是空白」就地标注**只在结构层面成立**（原文不删，留下判断链）。
- **零生产代码改动**：本批没有动 `app/src/main` 任何一行；观感不达标那一条按裁决边界交回用户，未擅自改色档。

## 3. 验证记录

- **三连门禁**（`--offline --no-daemon --no-build-cache --rerun-tasks`，`BUILD SUCCESSFUL in 4m 11s`，83 任务）：
  `:app:assembleDebug` + `:app:assembleDebugAndroidTest` + `:app:testDebugUnitTest` +
  `:app:compileDebugAndroidTestKotlin` + `verifyThirdPartyNotices`，日志 `.git/qa-scratch/c6/gate.log`。
  五元组对照式：`classes=331 tests=2460 failures=0 errors=0 skipped=0` = 批 C-5 基线 331 类 / 2460 条 + 0，
  0 失败 0 跳过（本批只加 androidTest，JVM 侧不动）。重算：
  `grep -h -o 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/TEST-*.xml | grep -o '[0-9]*' | paste -sd+ | bc`。
- **密钥扫描**：`bash scripts/secret-scan.sh` exit 0（`.git/qa-scratch/c6/secret-scan.log`；该脚本无命中时零输出，
  空文件就是「没扫到」的样子，别当成没跑）。
- **截图册 `scale=0`**（7 规格 × 2 帧）：裁掉状态条/导航条后**每对逐字节相同**——
  「周期末尾 = 静息骨架、无光泽」这条设计目标拿到结构证据。整帧 md5 唯 `row-flat-96` 一对不同，
  逐像素定位后差异只有 334 个点、全在 bbox `(72,19)-(92,47)` 的灰阶区 = **状态条时钟走了一个字**。
- **截图册 `scale=1`**（7 规格 × 12 帧连拍）：有效帧 10–11 枚（先按页底 `#F1F4EF` 滤掉上一窗口与拆窗白屏），
  其中 5–9 帧与静息格不同 ⇒ 光带确实在扫；中段帧誊为 `<规格>-scale1-mid.png`。
  **踩到并已登记的取样坑**：第一版「+1.0s / +3.0s」两帧与 1200ms 周期混叠，七枚规格逐字节等于静息帧，
  差点误报成「光带没扫」；根因是七规格共用同一相位原点 + 轮询检测延迟恒定。这条纪律写进了取证类 KDoc。
- **观感读数（本批唯一不达标项）**：实机像素取样——通栏骨架条 `#F0F3EF` vs 页底 `#F1F4EF` 差 **1 级 / 1.008:1**，
  卡内骨架条 `#FBFCFB` vs 卡面 `#FFFFFF` 差 4 级 / 1.028:1，发丝线 3 级 / 1.020:1，
  卡壳 16 级 / 1.110:1（唯一看得见的一档），光带相对静息帧最多再差 1–3 级。
  候选档现算：`outline` 满值 20 级 / 1.159:1、`tint2` 满值 21 级 / 1.112:1、`outline@0.55` 11 级 / 1.082:1；
  深色档 `outlineSoft@0.22` 只有 1.048:1（算式核对，未拍册）。判据尺子是仓里既有的「图形非文本 ≥3:1」
  （`docs/design/visual-style-guide.md`），现值差了一个量级以上。**改与不改属色档裁决，本棒未动实现**。
- **首帧读数**（`am start -W .launch.BrandLaunchActivity`，五轮，装包后首轮含 dexopt 已丢弃）：
  闪屏 `TotalTime` 1898–2006 ms、`Displayed BrandLaunchActivity` +1.898~2.006s、
  `Displayed .MainActivity` 675 / 699 / 701 / 705 / 930 ms——与批 C-5 接线后区间同向，**不当成新性能证据**。
  未测一项：「首帧 → 今日计划可读」的骨架时长（`uiautomator dump` 在本 AVD 读不到 Compose `testTag`，
  需给 `TodayPlanViewModel` 的 `Ready` 落点插临时桩再走一轮双构建），按决策册 §4 口径如实标注未测。
- **connected 四格**（`emulator-5554`，`adb install -r` 两枚 APK，`am instrument`，日志 `.git/qa-scratch/c6/c6-*.log`，
  脚本 `connected2.sh`，汇总 `connected2.stdout`）：

  | 腿 | 形态（`wm size` / `wm density` / `font_scale`） | 范围 | 结果 |
  |---|---|---|---|
  | compact UI 全量 | 1080x1920 @420，无 Override，font=1.0 | `-e package com.example.englishlearning.ui`（66 枚类） | `Time: 506.052` `OK (588 tests)`，墙钟 509s |
  | wide UI 全量 | Override 2160x1600 @ Override 320，font=1.0 | 同上 | `Time: 662.658` `OK (588 tests)`，墙钟 665s |
  | wide 大字号 | Override 2160x1600 @ Override 320，font=2.0 | `AppScreenHeaderTest`+`NotConfiguredCardTest`+`AppScreenOverlayContainerTest` | `Time: 22.735` `OK (13 tests)` |
  | compact 大字号 | 1080x1920 @420，font=2.0 | 同上 | `Time: 23.118` `OK (13 tests)` |

  `SkeletonShimmerEvidenceTest` 在两枚全量腿里各跑 7 条（六点 + 结果行），即截图册那 7 枚规格也在 connected 判据内。
  收尾复原现值：`Physical size: 1080x1920` / `Physical density: 420` / `font=1.0` / `animator=1.0`。
  **口径**：本仓「双形态 connected」历来的可比范围是 **UI 包**，不是整套 androidTest（113 枚类）。
  本轮先按整套跑过一版（`connected.sh`），第一腿 761s 后仍未出结果被我中止——
  慢在 `core.storage.VocabularySearchGramIndexTest#realCorpusContainsResultsMatchTheFullTableScanExactly`
  （24MB 用户库拷进 cache + 逐行填 gram + 逐针全表 LIKE）。**中止的那腿既不算绿也不算红**，不要拿它当证据。
  588 vs 批 B 记录的 559 差 +29 枚，未逐条归因（批 B 的 `b-b3-*-full-after.log` 被 NUL 打坏成 4272 字节，
  只留 51 枚类名与 `Time: 406.62`，按它数类会把「UI 包范围」这条推断建立在残损文件上）——
  归因线索只有批 C-1..C-5 各批新增的 UI 类，够解释方向，不够解释精确枚数。
- **androidTest 编译门禁**：`$GRADLE :app:compileDebugAndroidTestKotlin --offline --no-daemon --no-build-cache`
  exit 0，日志 `.git/qa-scratch/c6/androidtest-compile3.log`——覆盖取证类两处 KDoc 订正后的编译。
- **detekt/ktlint 增量**：门禁不含这两项（`docs/decisions/2026-09-19-static-analysis-and-test-baseline.md` 定的口径），
  本批新增一枚 androidTest 文件，未跑静态分析，无增量可记。

## 4. Git 状态

- 分支 `stage-5-ui-r2-learning-path`（按在册口径**不合 `main`**）。
- 本批两枚提交，按功能域分开：`7a85d47`（test(ui)：取证类 `SkeletonShimmerEvidenceTest.kt`，唯一新增生产侧可见文件）
  → 本报告所在 docs 枚（验证册批 C-6 那节 + 决策册 C-6 回写与色档裁决 + 本报告），零 `app/src/main` 改动。
- 推送：用户在本棒收口时改口「推送」，覆盖此前「先不推，等批 C 整批收口」那条在册口径。
  批 C-4c + C-5 的 7 枚提交先推，`ls-remote` 实测远端 tip = 本地 `302100c`（ahead 0，核验通过）；
  本批 2 枚随后推送，推送后以 `git -c credential.interactive=false ls-remote origin stage-5-ui-r2-learning-path`
  现算远端 tip，与本地 HEAD 一致才算备份完成（结果写在下一棒或本棒收尾回复里，不在这里预填哈希）。
- `origin` = `https://github.com/steveliuyan/English-learning-software.git`，走全局 git 代理 `http://127.0.0.1:3067`，
  `-c credential.interactive=false` 防挂起。
- 工作区除上述文档外无遗留；`.git/qa-scratch/c6/`（截图册 40+ 枚 PNG、`capture*.sh`、`pick_frames.py`、`probe.py`、
  `firstframe.sh`、`connected*.sh/stdout`、门禁与编译日志、`secret-scan2.log`）不入库。

## 5. 未完成 / 遗留问题

- **【已拍板 → 批 C-7】骨架与光带的色档**：见 §3 观感读数。三条路线（提填充到 `outline`/`tint2` 档、把光带取色换成比容器亮的色、
  或承认骨架只承担「形状预留」不承担「可见占位」并改口径），本棒不替设计选。
  2026-11-07 用户裁决 **「提到可见档」**（= 前两条一起走：填充提档 + 光带比容器亮），执行归批 C-7；
  深浅两档都要重拍截图册，`ShimmerSpec.peakAlpha` 与骨架填充那两枚 alpha 同处复核。本棒实现零改动。
- **端到端「首帧 → 今日计划可读」仍未量**（需临时桩 + 双构建），归目视。
- **累计待用户目视**（本棒不替人眼判，逐条结转）：C-4b 两问（取消 pill 像浮白盒、18dp/2dp 静帧像小弧，五屏一致 ⇒ 改是改组件）、
  C-4c 按张计费句不在生图等待面、批 B 四项、C-5 闪屏后进今日页骨架是否更短 / 有无「闪一下就变内容」，
  本批新增：光带在白色卡面上是**画暗**而非提亮，观感是否接受。
- **#14 真机四项目视**、**QA 第三轮整轮未执行**（`docs/handoff/2026-10-15-qa-round3-task.md`）——批 D 需要它的取证通道。
- 批 D（页面转场）未开工。

## 6. 下一任接手须知

- 第一步：**批 C 已整批收口，色档裁决已下（「提到可见档」），下一棒是批 C-7 色档整改**，不是批 D。
  读本报告 §3 的观感读数与 §5 第一条——**批 C 的结构判据全绿，但感知判据不过**，
  别把「门禁 2460 全绿 + connected 全绿」当成「预加载特效做完了」。
  C-7 按 TDD 走：先改契约闸（`SkeletonFamilyContractTest` / `ShimmerSpecContractTest` 里那两枚 alpha 与光带取色锚）拿到 RED，
  再动 `outlineSoft.copy(alpha = 0.22f)` 与 `ShimmerSpec` 的取色；改完深浅两档各重拍一次截图册（`capture.sh 0|1` 现有脚本可复用），
  并跑一次既有 ≥3:1 对比度契约套件确认没连带破。批 D 页面转场排在 C-7 之后（用户已选）。
- 取证类**逐例单跑**：`am instrument -w -e class <FQCN>#<方法> com.example.englishlearning.test/androidx.test.runner.AndroidJUnitRunner`
  （漏掉 `/androidx.test.runner.AndroidJUnitRunner` 组件段，设备侧 `am` 只打印 usage，PNG 全成 0 字节空证）。
- 四个环境坑（都实测过）：① PATH 首枚 adb 是 `C:\Windows\adb` 1.0.26.29，**没有 `exec-out`**，一律用 `/c/adb/platform-tools/adb`；
  新旧客户端共用 5037 会「server version mismatch」，需 `taskkill //F //PID <旧 server>` 后由新版重拉。
  ② 换 `animator_duration_scale` 档后要 `am force-stop` 应用——`WindowRecomposer` 在建窗时读这枚键。
  ③ 循环里每条 adb 都要 `< /dev/null`，否则 `adb shell` 吞掉 `while read` 的标准输入，第一轮之后剩下的规格全被吃掉。
  ④ 不能按「发出 instrument 后固定 sleep」截：冷启要 5~6 秒，截到的是上一个画面（实测截到过今日计划成品页，看着像证据其实是空证）；
  一律以 logcat `QA-C6 …holdStart` 为对齐点。
- `animator_duration_scale` 在本 AVD 落在 **global** 命名空间（`settings get system` 读不到）；
  本轮实测开工前现值是 `1.0`，收尾按 `1.0` 复原（批 A 那节的「现算口径」写的是期望 `null`，两者行为等价，
  差异已在此登记，别当故障找）。
- 同一测试方法内第二次 `setContent` 真机必崩（本包既有怪癖），七个规格因此是七个方法。
- 相关文档：`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md`（§4 末 C-6 回写）、
  `docs/verification/ui-redesign-2026-10.md`（批 C-6 那节是全部读数与重算命令的唯一落点）、
  `.superpowers/sdd/2026-11-06-motion-and-adaptive/progress.md`（gitignored 台账）。

## 7. 用户确认过的决策

本棒沿用批 C 既有口径收口取证；**收口后新增两枚裁决**（用户 2026-11-07 现拍）：
色档走 **「提到可见档」**（填充提到 `outline` / `tint2` 一档 + 光带取色比容器更亮，执行归批 C-7），
批 C 收口后下一批做 **「批 D 页面转场」**。结转的在册拍板（避免下轮重问）：
「先不推，等批 C 整批收口」→ 本棒收口后用户改口「推送」，已推并核验；铺面「全站都铺」；shimmer 关动画「末尾 = 静息骨架、无光泽」；
预取「闪屏窗口 + tab 预读」且「只用本地、不为预取新增任何网络或 AI 出站」「预取不得让任何一次点击『本来不发请求』变成『已经发过请求』」；
「全部都做，然后横屏也要做适配，就是不是要硬横过来哦」+「手机锁竖屏，大屏才自适应」；
分支不合 main、推送逐批请示。本批推送请示与实际推送见 §4。
