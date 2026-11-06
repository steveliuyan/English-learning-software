# UI 重设计轮·动效与自适应轮 批 A（动效基建与系统降级，2026-11-06）

## 0. 开工口径（10 行）

- 用户诉求：「可不可以做各个页面的动画动效以及预加载的动画特效」→「全部都做，然后横屏也要做适配，就是不是要硬横过来哦」→「可以，直接执行吧」。
- 三项拍板：起步＝动效基建 + 降级开关；降级口径＝**只跟随系统**（不做应用内开关、不加设置行）；批 B 野心＝全面自适应（含平板/折叠 + 侧边导航）。
- 拆批与前置：批 A 基建 → 批 B 自适应（先改 Spec 00/05 与性能阈值口径）→ 批 C 预加载特效（吃批 A 的循环动画约束）→ 批 D 转场（要 QA 第三轮走查通道）。
- 批 A 动手前先回答事实问题：**平台是否已按 `animator_duration_scale` 缩放**。答案是「已经」，所以本批的产物主要是**删代码 + 钉口径**，不是加开关。
- 硬结论：谁把旧的换算函数接回调用点，`scale=0.5` 就被平台乘一次、我们再乘一次 ⇒ 实际 0.25，属双重缩放（原 `AppMotionTest` 三枚用例钉的正是这条错口径）。
- 循环动画实测**不空转**（`scale=0` 落一个周期末尾值后不再要帧），所以批 C 不需要运行时开关，但必须把「周期末尾值」当静态外观设计。
- 本批唯一可见行为变化：`GlassOverlay` 退场由借用的入场曲线改回 `EasingIn`。观感只有人眼能判，已登目视项。
- 不合 main、不推送（等用户发话）；探针代码已删、不入库；设备侧 `animator_duration_scale` 与 `font_scale` 均已复原。
- 验收：三连门禁 + 矩阵校验 + secret-scan + 三枚浮层 connected 类实跑，全绿（见 §3）。

## 1. 证据先行：字节码 + 一次性探针

字节码（本机解包 `animation-core` / `ui-android` 1.10.0，`javap -c`）：

- `WindowRecomposer_androidKt` 读字面量 `animator_duration_scale`，装 `MotionDurationScaleImpl` 进 Recomposer 协程上下文；
- `SuspendAnimationKt.getDurationScale(CoroutineContext)` 取该元素（缺省 1f，负值抛 `negative scale factor`）；
- `doAnimationFrameWithScale` 等价于 `timeForPercent = if (durationScale == 0f) animation.durationNanos else (frameTimeNanos - startTimeNanos) / durationScale`。

设备实测（emulator-5554，观察窗 2500ms；宿主 `ActivityScenario.launch(ComponentActivity)` + `ComposeView.setContent`）：

| 规格 | `scale=1`（内对照） | `scale=0` |
|---|---|---|
| 有限 `animateFloatAsState(0f→1f, tween(600))` | 150 次重组 / 37 个不同值 | 3 次重组 / `[0.0, 0.0, 1.0]`，无中间值 |
| 循环 `infiniteRepeatable(tween(600), Restart)` | 150 次重组 / 148 个不同值，约 4 个周期 | 3 次重组 / `[0.0, 1.0, 1.0]`，此后再无新帧 |

**第一版探针量废的经过**（值得留档）：用 `createComposeRule()` 时 `waitForIdle()` 1ms 返回、宿主只重组 1 次、循环值恒 `0.0` —— Compose 测试环境自己替换了帧时钟，量到的是测试替身。**循环动画一律不得用 compose test rule 取证。**

读数原文留在 `.git/qa-scratch/motion-probe-readings-1106.md`（gitignored），并已誊进在册证据表（见 §3）。

## 2. 改了什么

代码（4 个生产文件 + 3 个测试文件）：

- `ui/theme/AppMotion.kt`：退回**纯 token 表**。删除 `Easing` 裸别名、`durationMs(base, scale)`、`animatorDurationScale(context)` 及 `Context`/`Settings` 依赖；KDoc 换成实测读数 + 双重缩放禁令 + 用途口径表。
- `ui/components/glass/GlassOverlay.kt`：`fadeOut`/`scaleOut` 改 `EasingIn`（本批唯一可见变化），`fadeIn`/`scaleIn` 写明 `EasingOut`。
- `ui/components/glass/GlassButton.kt`、`ui/SpeechSettingsScreen.kt`（两处 `animateContentSize`）：裸别名读点写明方向。
- 新增 `ui/MotionSpecContractTest.kt`（6 枚）：R1 禁 `durationMs`/`animatorDurationScale` 回流；R2 禁 `AppMotion.kt` 里出现 `scale`（剥注释后）；R3 每枚 `tween(` 显式带 token 缓动；R4 `fadeIn(`/`scaleIn(`→`EasingOut`、`fadeOut(`/`scaleOut(`→`EasingIn。响亮守卫齐套：路径常量 + 缺失目录必红 + 覆盖面下限（含 `tween(` 的宿主文件数 ≥6）+ 合成 must-flag/must-not-flag + 真实文件零 offender。
- 删除 `ui/theme/AppMotionTest.kt`（3 枚，钉的是 `250×0.5=125` 这条被推翻的口径）。
- `PrototypeTokenContractTest`：别名断言由「必须保留」翻转为「不得再有裸 `val Easing =`」，并在失败信息里点名换算入口已删、由新闸接住。

文档：`docs/decisions/2026-11-06-motion-foundation-and-system-reduced-motion.md`（四项决定 + 用途口径表 + 验证边界）、`docs/superpowers/plans/2026-11-06-motion-and-adaptive-layout.md`（批 A–D，含批 B 的三条前置：Spec 00 平台矩阵、Spec 05 与 `perf-baseline-2026-10.md` 阈值口径、解除 connected 断言的竖屏前提——2026-10-31 那次「29 枚横屏假红」的处置是把设备锁竖屏，属环境规避，批 B 要还清）、`docs/verification/ui-redesign-2026-10.md` 新增「2026-11-06 批 A」节（读数、字节码、两条在册约束、现算口径、新增目视项）。

## 3. 验证记录

| 项 | 命令 | 结果 |
|---|---|---|
| 三连门禁 | `gradle.bat --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest --rerun :app:compileDebugAndroidTestKotlin verifyAcTraceabilityMatrix` | `BUILD SUCCESSFUL in 1m 27s`，exit 0（日志 `.git/qa-scratch/gate-1106f.log`） |
| JVM 单测（强制重跑，非 UP-TO-DATE） | `python .git/qa-scratch/tally.py .git/qa-scratch/gate-1106f.tally` | `classes=322 tests=2384 failures=0 errors=0 skipped=0`；新 XML mtime 22:17:44 现证（`MotionSpecContractTest.xml` 单类 `tests="6" failures="0"`） |
| 矩阵校验 | 同上任务的 `:verifyAcTraceabilityMatrix` | `AC matrix covered 61 ids with evidence.` |
| secret-scan | `bash scripts/secret-scan.sh` | exit 0，零命中（`.git/qa-scratch/secret-1106f.txt`） |
| connected（emulator-5554，`install -r` 两枚 APK + `am instrument`） | 三类合计 `…glass.GlassOverlayTest, …glass.GlassDialogTest, …AppScreenOverlayContainerTest` @ `font_scale 2.0` | `OK (9 tests)`（`.git/qa-scratch/instr-1106f-b.txt`）；分跑：glass 两类 `OK (3 tests)`、容器类 6 枚在 2.0 档同绿 |
| 环境复原 | `settings get system font_scale` → `1.0`；`settings get global animator_duration_scale` → `null` | 均已复原 |

一处**先红后绿**的定性说明：第一次 connected 报 `Tests run: 8, Failures: 5`，两类是 `ClassNotFoundException`（我把类名按文件名写成 `ui.GlassOverlayTest`，真身在 `ui.glass` 包），三枚是 `AppScreenOverlayContainerTest` 的 `assertScaleTookEffect` 护栏在 `font_scale 1.0` 下**故意报红**（在册旧账，非缺陷）。换正确 FQCN + 置 2.0 后 `OK (9 tests)`，跑完把 `font_scale` 复原 1.0。

里程碑真机装机：`adb -s bf353dda install -r` → `Success`，`pm path` 确认在位，monkey 启动后 `mCurrentFocus=…MainActivity`；**真机零 instrument**。

## 4. Git 状态

分支 `stage-5-ui-r2-learning-path`，未合 main、未推送。本批三枚提交（代码+测试 / 决策+计划+证据 / 本报告）。远端领先枚数现算，别引用本文写死的数：

```bash
git rev-list --count origin/stage-5-ui-r2-learning-path..HEAD   # 若远端分支不存在则先 git fetch --dry-run 看凭据
git status --short | wc -l
```

## 5. 未完成 / 待拍板

- 待用户目视（新增一项）：`GlassOverlay` 浮层**关闭**时的收拢观感（曲线由 `cubic-bezier(.2,.7,.3,1)` 改 `cubic-bezier(.4,0,.8,.3)`，真机 `bf353dda` 已装本批包）。
- 批 B 的三条前置未做（Spec 00 平台支持矩阵、Spec 05 + 性能阈值口径、connected 竖屏前提解除），动版面之前必须先落文档。
- 既有待拍板账未清（占位批与更早批次留下的）：真实例句三条路线、`DialogPanel` 粘底动作、四枚未实现的 AI 能力卡取哪几枚、生成图是否入收藏、「按域名一次」下的图片口径、每日文章生成上限、`fontScale 2.0` 下评分按钮不等高、F4 改名逃逸残形、`VocabularyScreen`/`LearningRecordsScreen` 孤儿文件与私有词行下线。
- **推送请示**：本批与前面若干批的提交都还在本地，是否推送等用户发话。

## 6. 下一任接手须知

1. **别把「减弱动效」当成还没做**。平台已负责，判据在 `MotionSpecContractTest`；新写的动画只要 `tween(` 显式带 `AppMotion.EasingOut`/`EasingIn` 就自动跟随系统设置。若你需要「按 scale 缩短时长」的冲动，先读决策文档 §3.2——那就是双重缩放。
2. **循环动画（批 C shimmer）两条硬约束**：`scale=0` 会停在**周期末尾值**，所以静态外观要照此设计；每条循环规格交付时配一张 `animator_duration_scale=0` 的截图。取证不得用 `createComposeRule()`，用 `ActivityScenario` + `ComposeView`。
3. **connected 类名按包不文件名取**：`GlassOverlayTest`/`GlassDialogTest` 在 `com.example.englishlearning.ui.glass`；`AppScreenOverlayContainerTest` 的三枚 `…AtDoubleFontScale` 在 `font_scale 1.0` 下报红是护栏，不是回归——先置 2.0 再判，跑完复原。
4. **批 B 的第一步不是写代码**：改 `docs/specs/00`、`docs/specs/05` 与 `docs/verification/perf-baseline-2026-10.md`，再解 connected 断言的竖屏前提；`AndroidManifest.xml` 今天不锁方向，应用本来就会转，别把「允许横屏」当成已完成。
5. 台账：`.superpowers/sdd/2026-11-06-motion-and-adaptive/progress.md`（gitignored，接手先读）。
