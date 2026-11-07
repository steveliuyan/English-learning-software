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
3. 现网 `AndroidManifest.xml` **没有** `screenOrientation` 也没 `configChanges` ⇒ 应用本来就在转，只是没做版面。2026-10-31 的三条裁决（`docs/decisions/2026-10-31-ui-review-three-rulings.md`）记下过 29 枚横屏假红（节点被裁成 `Rect(0,0,0,0)`），当时的处置是**把设备锁竖屏**——那是环境规避，不是产品修复，批 B 要把它还清。**还清方式（决策 §10.2 定稿）**：不是给手机横屏重写判据，而是把「手机形窗口锁竖屏」做成产品侧的方向策略（`values` / `values-sw600dp` 的 `allow_landscape_layout` + `AppOrientationPolicy` 运行时取 `requestedOrientation`），大屏才开放自适应；Manifest 里仍然不写 `screenOrientation`，口径唯一落点在资源限定符。
4. connected 断言的竖屏前提要逐类解除——**范围自 §10.4 起收窄**：只处理与窗口形态无关的那一类（设备 `font_scale` 前置），形态相关的两档取证改成「手机竖屏（Compact）+ 平板形窗口（Wide）」两档，手机横屏那 32 枚红随形态退场、不逐条改造。判据：`adb -s emulator-5554 shell settings put global animator_duration_scale` 复原为默认后，两档各跑同一批 connected 类，两侧末行都留 `OK (n tests)` 原文；大字号三条另按「设备 `font_scale` 实际值」`assumeTrue` 声明前置，并在 2.0 档各补一次取证跑（读数表见决策 §10.4）。

**版面**：`WindowSizeClass` 三档——compact 保持底栏四栏；medium（平板竖屏、折叠屏内屏）主内容两栏（列表 + 详情或阅读 + 词条）；wide（平板横屏、折叠屏展开态）侧边 `NavigationRail` 替代底栏，页头 hero 降级为普通页头。骨架落在 `AppScreen.kt` 的 tab 宿主层，各屏只声明「能在多宽的面板里活下来」，不在每屏复制分支。**进入 medium / wide 的前置是 `smallestScreenWidthDp ≥ 600`**：手机横屏（实测 `w731dp h339dp`，宽度够 medium、高度只有竖屏的 45%）不再作为版面形态开放，见决策 §10。

## 批 C：预加载特效（骨架铺全站 + shimmer + 真预取）

**前置（先改文档再动代码，AGENTS.md 硬要求）**：`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md`（四条决定 + 现状证据 + 验证边界）与 `docs/specs/05` 的 F5-05 / AC5-06 已落盘。用户 2026-11-07 三项拍板：铺面＝全站都铺；`scale=0` 定格值＝末尾即静息骨架、无光泽；预取深度＝闪屏窗口 + tab 预读，硬约束「只用本地、不为预取新增任何网络或 AI 出站」。

依赖批 A 的 §3.4：循环动画在 `scale=0` 下定格在**周期末尾值**，所以 shimmer 的末尾相位必须设计成静息外观（决定 2），并各配一张 `animator_duration_scale=0` 的截图。骨架现状（批 C 开工实测）：真骨架只有 `AppScreen` 私有 `WordBookRowSkeleton` 一处，`ui/components/Skeleton.kt` 的 `ListSkeleton` 是零引用死代码且 KDoc 里「connected 判据依赖 `wordbook_placeholder_`」为假；等待指示器 10 文件 15 处；`AiLearningScreen` 与 `ProfileScreen` 连 Loading 分支都没有。

- C-1 基建：`Skeleton.kt` 重写为骨架族（`SkeletonBar`/`SkeletonRow`/`SkeletonList`/`SkeletonCard`/`SkeletonStatGrid`），`Shimmer.kt` 落 `drawBehind` + 相位渐变（末尾=静息），`AppScreen` 私有骨架迁入且 `wordbook_row_skeleton_` / `WORD_BOOK_PLACEHOLDER_ROWS` 两枚既有闸不动；契约闸 `SkeletonFamilyContractTest` + `ShimmerSpecContractTest`（唯一性、定格=静息的纯函数判据、禁裸 hex、禁时长换算入口、骨架零 `Text`）。
- C-2 一级页：`TodayPlanScreen` / `ReadingAccessScreen` 换骨架；`AiLearningScreen` / `ProfileScreen` 先补 Loading 分支再铺（补的是「本地读盘中」，不新增状态机语义）。
- C-3 本地读盘子页：今日计划词卡流、查词、学习记录、词书单词、生词复习、试卷预览、备份本地校验段——转圈换同形状骨架，等待指示器调用点计数只减不增。
  ——C-3 交付实况：换了六屏（上述前五屏的 `Loading` + `ArticleReadingScreen` 那句裸 `Text("正在加载…")`，本屏没有转圈所以原清单没点到它，按「本地读盘」补入）；
  **试卷预览与备份三处不换骨架**——实测它们的等待是「动作进行中」（生成 PDF / 认证中 / 导出中 / 恢复中），接下来落的是回执而不是同形状内容，
  分类与逐条豁免理由见决策册决定 3 第 2 类与 `docs/specs/05` F5-05 新增那条；`GlobalVocabularySearchScreen` 只换 `Loading` 那一支，`ClearingHistory` 保留指示。
  转圈台账因此 9 行 → 5 行（剩四文件五处全部为豁免站点）。
- C-4 网络/AI 等待屏：文章生成、AI 问答、拍照翻译、生图、句法分析改成进行中语义（说清在请求谁 + 可取消 + 超时），禁止假结果骨架与占位内容。
  开工实测把「超时」那一件改判：`timeout_seconds` 早已在 `AiRequestPolicy` → `AiFailure.Timeout` 有唯一映射与真话文案，
  等待面再写一个数就是第二时长权威，所以本条的「超时」= **不自立时长口径**，而不是加倒计时。
  实测另发现两处不是美化的缺陷（`docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` §2.6）：
  拍照翻译的 AI 翻译阶段在 UI 上完全不可见（`busy` 只由识别路径写），而按钮照旧可点、再点即 `translateJob?.cancel()` 重发；
  文章生成的 CTA 在 `Generating` 时不禁用且 `runGeneration` 不持旧句柄 = **一次点击一次真实计费的两份请求**。
  四 VM 零 Job 句柄（只有拍照翻译持 Job），所以「可取消」必须先在 VM 层把在飞的那次攥住，屏侧才有东西可取消。
- C-5 真预取：`StartupLocalDataWarmer`（形状照抄 `StartupPronunciationWarmer`，只做开库，把 `DatabaseStartup.open` 挪进闪屏那 1.2 秒）+ tab 预读只补本地装载点；零出站探针与装载点对账是硬判据。
- C-6 收口：三连门禁 + 双形态 connected + `scale=0` / `scale=1` 截图册 + 首帧耗时读数 + 按 `REPORT-TEMPLATE` 报告 + 分功能域提交、推送请示。
- **C-7 色档整改（C-6 收口后按用户裁决「提到可见档」新增）**：骨架填充从 `outlineSoft.copy(alpha = 0.22f)` 提到可见档，
  光带改为**比骨架条亮**（机制走提亮混合，不新增 palette token、不按主题分支）；
  判据先在 JVM 侧落 `SkeletonVisibilityContrastTest`（相对页底/卡面/骨架条三向 ≥16 个通道级，16 = C-6 实测唯一看得见的卡壳档），
  拿到 RED 再改实现；随后**深浅两档各重拍一次截图册**（`scale=0` 定格全同 + `scale=1` 光带在扫 + 像素取样读数），
  并复核既有 ≥3:1 图标对比度套件与 `ShimmerSpecContractTest` 的取色口径未被连带放宽。
  口径落 `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` §6，读数落 `docs/verification/ui-redesign-2026-10.md` 批 C-7 那节。

## 批 D：页面转场动效

与 QA 第三轮设备走查同批或其后：转场会改 TalkBack 的焦点顺序与朗读时机，`docs/handoff/2026-10-15-qa-round3-task.md` 那份任务书至今未执行，批 D 需要它的取证通道。落点是 `AppScreen.kt` 的浮层 `else if` 链（本仓既有约束「先声明先赢 / `BackHandler` 后声明者优先」），转场档位取批 A 口径表的 `Slow` + 分向缓动。

## 台账

进度与裁定记在 `.superpowers/sdd/2026-11-06-motion-and-adaptive/progress.md`（gitignored，接手先读它）；证据表续写 `docs/verification/ui-redesign-2026-10.md`；截图与日志一律落 `.git/qa-scratch/`，不入库。
