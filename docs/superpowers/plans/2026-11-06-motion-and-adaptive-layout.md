# 实现计划：动效与自适应轮（批 A–D）

日期：2026-11-06
分支：`stage-5-ui-r2-learning-path`（用户明令不合 main、推送逐批请示）
用户拍板：范围「全部都做」；横屏「不是硬横过来，要真适配」；批 A 起步「动效基建 + 降级开关」；降级口径「只跟随系统」；批 B 野心「全面自适应（含平板/折叠 + 侧边导航）」。

每批一个闭环：口径 → TDD/截图取证 → 一次三连门禁（`assembleDebug` → `testDebugUnitTest` → `compileDebugAndroidTestKotlin`，收口才跑，不逐项跑）+ 矩阵校验 + secret-scan → 报告 → 分功能域提交。验收口径是单测全绿，不是 detekt/ktlint（存量基线债，勿当回归）。

## 批 A：动效基建与系统降级（本批）

事实先行：平台是否已经遵守 `animator_duration_scale` 决定这一批到底要写代码还是要删代码。字节码 + emulator-5554 实测的读数、四条决定、用途口径表见 `docs/decisions/2026-11-06-motion-foundation-and-system-reduced-motion.md`。

交付：`AppMotion` 退回纯 token 表（删 `Easing` 别名与两枚时长换算入口）、7 个裸别名读点写明入场/退场方向、新增 `MotionSpecContractTest`（四条禁令 + 响亮守卫 + 合成双边）、删除钉住旧换算口径的 `AppMotionTest`。

## 批 B：全面自适应（含平板/折叠与侧边导航）

**前置（先改文档再动代码，AGENTS.md 硬要求）**

1. `docs/specs/00-foundation-and-architecture.md` 的平台支持口径目前只写「适配小屏、动态字体、深色模式和 TalkBack 基础导航」（§范围，第 42 行附近），没有横屏、平板、折叠屏。批 B 开工前把它补成三档窗口类别的支持矩阵（compact / medium / wide 各自的版面承诺），并写清不做的事（不做桌面多窗口专属排版、不做横屏下的第二套导航语义）。
2. `docs/specs/05-release-quality-and-compliance.md` 的无障碍与性能验收口径按同一矩阵改写；`docs/verification/perf-baseline-2026-10.md` 的阈值全部在竖屏模拟器采得，宽窗口必须重采或如实标注「竖屏阈值不适用」，不得沿用。
3. 现网 `AndroidManifest.xml` **没有** `screenOrientation` 也没 `configChanges` ⇒ 应用本来就在转，只是没做版面。2026-10-31 的三条裁决（`docs/decisions/2026-10-31-ui-review-three-rulings.md`）记下过 29 枚横屏假红（节点被裁成 `Rect(0,0,0,0)`），当时的处置是**把设备锁竖屏**——那是环境规避，不是产品修复，批 B 要把它还清。
4. connected 断言的竖屏前提要逐类解除：`asserter` 里按 y 坐标排序、按 `bounds.height` 断言可见性、`performScrollTo()` 依赖单列滚动的用例，都要改成「按当前窗口类别分支」或改用语义锚点。判据：`adb -s emulator-5554 shell settings put global animator_duration_scale` 复原为默认后，横竖各跑一遍同一批 connected 类，两侧末行都留 `OK (n tests)` 原文。

**版面**：`WindowSizeClass` 三档——compact 保持底栏四栏；medium（横屏手机/小折叠）主内容两栏（列表 + 详情或阅读 + 词条）；wide（平板）侧边 `NavigationRail` 替代底栏，页头 hero 降级为普通页头。骨架落在 `AppScreen.kt` 的 tab 宿主层，各屏只声明「能在多宽的面板里活下来」，不在每屏复制分支。

## 批 C：预加载特效（骨架铺全站 + shimmer + 真预取）

依赖批 A 的 §3.4：循环动画在 `scale=0` 下定格在**周期末尾值**，所以每条 shimmer 规格要把静态外观当设计对象，并各配一张 `animator_duration_scale=0` 的截图。骨架目前只在少数页有，批 C 铺到全部一级页与三枚慢加载子页；`shimmer` 用 `Modifier.drawBehind` + 渐变偏移，禁裸 hex（走 `appColors().tint` 族）。真预取只扩已有 `LaunchedEffect` 装载点（词库四段已做），不得为了「预取」引入新的网络或 AI 出站。

## 批 D：页面转场动效

与 QA 第三轮设备走查同批或其后：转场会改 TalkBack 的焦点顺序与朗读时机，`docs/handoff/2026-10-15-qa-round3-task.md` 那份任务书至今未执行，批 D 需要它的取证通道。落点是 `AppScreen.kt` 的浮层 `else if` 链（本仓既有约束「先声明先赢 / `BackHandler` 后声明者优先」），转场档位取批 A 口径表的 `Slow` + 分向缓动。

## 台账

进度与裁定记在 `.superpowers/sdd/2026-11-06-motion-and-adaptive/progress.md`（gitignored，接手先读它）；证据表续写 `docs/verification/ui-redesign-2026-10.md`；截图与日志一律落 `.git/qa-scratch/`，不入库。
