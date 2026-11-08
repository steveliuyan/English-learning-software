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

## 批 D：页面转场动效（D-1…D-4 展开）

与 QA 第三轮设备走查同批或其后：转场会改 TalkBack 的焦点顺序与朗读时机，`docs/handoff/2026-10-15-qa-round3-task.md` 那份任务书至今未执行，批 D 需要它的取证通道。落点是 `AppScreen.kt` 的浮层 `else if` 链（本仓既有约束「先声明先赢 / `BackHandler` 后声明者优先」），转场档位取批 A 口径表的 `Slow` + 分向缓动。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现本批。步骤用 `- [ ]` 复选框跟踪，每段末尾跑一次门禁再提交。

**Goal:** 给「一级 tab 切换」与「35 支全屏子页进出」两条腿装上方向滑动 + 淡入淡出转场，并把对话框浮层的四枚规格并表，全程零新增网络/AI 出站。

**Architecture:** 挂载判据从 `AppScreen` 的 `if/else if` 链**整体搬进**纯函数 `AppOverlayRouteResolver`，产出一枚携带冻结 payload 的 `AppOverlayRoute`；`AnimatedContent` 的 content lambda 按**传入的 route** 渲染，退场腿因此能被组合（今天的链做不到这一点，这是本批唯一的结构性障碍）。方向由链声明序号（= 层级）比较得出，时长/缓动/位移只在 `ui/theme/AppTransition.kt` 一处。

**Tech Stack:** Kotlin、Jetpack Compose（`androidx.compose.animation` `AnimatedContent` / `ContentTransform` / `togetherWith`，BOM `2025.12.00`）、JVM 单测 + androidTest（`createComposeRule` + `mainClock`）。

**Spec / 判据来源（必读，二者与本判决书同批交付）**

- 规格：`docs/specs/05-release-quality-and-compliance.md` 的 `F5-06 页面转场` 七条与 `AC5-07`
- 决策：`docs/decisions/2026-11-08-page-transition-routing-and-motion.md`（§1 现状读数、§1.3 链边三处相邻事实、§2 API 实测、§3 五段决定、§4 五条风险、§5 验证口径）
- 批次口径：`docs/decisions/2026-11-06-motion-foundation-and-system-reduced-motion.md` §3.3 口径表「页面转场 = `Slow` 300 / 入 `EasingOut` / 退 `EasingIn`」与 §4「批 D 的转场判据兜住间接传参」

### Global Constraints（每支任务都受这些条款约束）

- 分支 `stage-5-ui-r2-learning-path`；**不合 main**；`git push` 逐批请示，绝不自动。
- 门禁三连（顺序不可颠倒，Kokoro 边界测试读 APK 产物）：
  ```bash
  V=$(grep -oE 'gradle-[0-9]+\.[0-9]+' gradle/wrapper/gradle-wrapper.properties | head -1)
  GRADLE=$(find "$HOME/.gradle/wrapper/dists/$V-bin" -name gradle.bat -path "*/$V/bin/*" | head -1)
  "$GRADLE" :app:assembleDebug :app:testDebugUnitTest --rerun-tasks :app:compileDebugAndroidTestKotlin \
    --no-daemon --no-build-cache 2>&1 | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
  ```
  `--rerun-tasks` 才算真跑；验收线是 JVM 单测全绿（基线 336 类 / 2494 条，只增不减），detekt/ktlint 存量债不当回归。
- instrumented 只在 emulator-5554，用 `adb install -r` + `am instrument`（**禁用 `connectedDebugAndroidTest`**，它会清空设备数据）；真机 `bf353dda` 只做里程碑手工装机。
- 转场不得产生第二次计费出站（Spec 05 F5-06 红线）；不新增应用内动效开关；不给 `AppScreen` 加参数（`AppScreenProductionWiringTest` 是接线闸）。
- 判据只搬不译：链的 35 枚条件**逐字**进 resolver，**顺序逐字照抄**（顺序就是 z 序与返回键优先级承诺）。
- 源码契约四条纪律齐备：路径常量 + 文件数响亮守卫 + 合成 must-flag/must-not-flag 双边 + 反空转消费点计数；扫描交给 `SourceRatchet.kt:122` 的 `stripComments`，**不得二次剥注释**。

### route 表：35 支链 = 35 枚 route，链声明序 = `mountIndex`

| idx | `AppOverlayRoute` 变体 | 链上原文判据（逐字） | `AppScreen.kt` 行 | 冻结进 route 的 payload |
|---|---|---|---|---|
| 0 | `VocabularyDetail(card)` | `selectedSearchCard != null` | 728 | `WordCard` |
| 1 | `GlobalSearch` | `showGlobalVocabularySearch` | 807 | — |
| 2 | `RelearnCard(card)` | `selectedRelearnCard != null` | 848 | `WordCard` |
| 3 | `LearningRecordDetail(card)` | `selectedLearningRecordCard != null` | 860 | `WordCard` |
| 4 | `FavoriteDetail(card)` | `selectedFavoriteCard != null` | 941 | `WordCard` |
| 5 | `VocabularyRelearnList` | `showVocabularyRelearn` | 990 | — |
| 6 | `LearningRecords` | `showLearningRecords` | 1001 | — |
| 7 | `LearningSetup` | `setupRequired \|\| showSetup` | 1015 | — |
| 8 | `LearningFlow` | `showLearning` | 1025 | — |
| 9 | `WorksheetPreview` | `showWorksheetPreview` | 1229 | — |
| 10 | `WorksheetSettings` | `showWorksheetSettings` | 1262 | — |
| 11 | `ReviewQueue(plan)` | `showReviewQueue && todayReady != null` | 1281 | `TodayPlanUiState.Ready` |
| 12 | `TodayWordList(plan)` | `showTodayWordList && todayReady != null` | 1299 | `TodayPlanUiState.Ready` |
| 13 | `CheckIn` | `showCheckIn` | 1321 | — |
| 14 | `WordBank` | `showWordBank` | 1328 | — |
| 15 | `AiProfiles` | `showAiProfiles` | 1443 | — |
| 16 | `SpeechSettings` | `showSpeechSettings` | 1510 | — |
| 17 | `ReminderSettings` | `showReminderSettings` | 1528 | — |
| 18 | `BackupExport` | `showBackupExport` | 1559 | — |
| 19 | `BackupRestore` | `showBackupRestore` | 1565 | — |
| 20 | `BackupPairingExport` | `showBackupPairingExport` | 1571 | — |
| 21 | `BackupHub` | `showBackupHub` | 1577 | — |
| 22 | `Licenses` | `showLicenses` | 1590 | — |
| 23 | `About` | `showAbout` | 1612 | — |
| 24 | `SentenceAnalysis` | `selectedFeature == AiFeature.SENTENCE_ANALYSIS` | 1625 | — |
| 25 | `PhotoTranslation` | `selectedFeature == AiFeature.PHOTO_TRANSLATION` | 1663 | — |
| 26 | `ImageStudio` | `selectedFeature == AiFeature.IMAGE_STUDIO` | 1740 | — |
| 27 | `AiFeatureScaffold(feature)` | `selectedFeature != null` | 1756 | `AiFeature` |
| 28 | `UnknownAiFeature(featureKey)` | `selectedFeatureKey != null` | 1776 | `String` |
| 29 | `ReadingHistory` | `showReadingHistory && historyArticle == null` | 1791 | — |
| 30 | `ReadingFeed` | `showReadingFeed && readingTarget == null && historyArticle == null` | 1821 | — |
| 31 | `ArticleImport` | `showArticleImport` | 1835 | — |
| 32 | `ArticleReading(readingTarget, historyArticle)` | `readingTarget != null \|\| historyArticle != null` | 1844 | `ReadingTarget?` + `Article?`（两枚都要，`fromHistory` 判据靠它） |
| 33 | `WordBookTransfer` | `showWordBookTransfer` | 2012 | — |
| 34 | `Settings` | `showSettings` | 2020 | — |

tab 宿主（今天的 `} else {`，`AppScreen.kt:2026`）不是 route，用 `null` 表示，`AppOverlayRouteResolver.TAB_HOST_INDEX = 35`。
表里 35 行由 D-1 的两条闸（route 枚数 == 35、每枚 route 在 `AppScreen.kt` 恰好一个 `is` 消费点）与 D-3 的站点数闸共同钉住，不靠人工核对。

---

### D-1：挂载权威与方向判据（纯函数层，零 UI 改动）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/AppOverlayRoute.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/AppMountFlags.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/AppOverlayRouteResolver.kt`
- Create: `app/src/main/java/com/example/englishlearning/ui/ScreenTransitionDirection.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/AppOverlayRouteResolverTest.kt`
- Test: `app/src/test/java/com/example/englishlearning/ui/ScreenTransitionDirectionTest.kt`

**Interfaces:**
- Consumes: 同包既有类型 `WordCard`（`learning.domain.WordCard`）、`TodayPlanUiState`（`ui/TodayPlanViewModel.kt:60`）、`ReadingTarget`（`ui/ReadingAccessViewModel.kt:81`，同包无需 import）、`Article`（`reading.domain.Article`）、`AiFeature`（`ai.domain.AiFeature`）、`AppTab`（`ui/AppTab.kt:18`，ordinal 序 LEARNING/READING/AI/PROFILE）。
- Produces（D-2/D-3 逐字按此签名消费，不得改名）：
  - `sealed interface AppOverlayRoute { val mountIndex: Int }` + 上表 35 枚变体
  - `data class AppMountFlags(... 35 枚入参，全部带默认值 ...)`
  - `object AppOverlayRouteResolver { const val TAB_HOST_INDEX = 35; fun resolve(flags: AppMountFlags): AppOverlayRoute? }`
  - `enum class ScreenTransitionDirection { Forward, Backward, Crossfade }`
  - `object ScreenTransitionDirectionPolicy { fun overlay(from: AppOverlayRoute?, to: AppOverlayRoute?): ScreenTransitionDirection; fun tab(from: AppTab, to: AppTab): ScreenTransitionDirection; fun mountIndex(route: AppOverlayRoute?): Int }`

- [ ] **Step 1：写 RED 用例（先红在「类型还不存在」）**

`AppOverlayRouteResolverTest.kt`（节选即完整判据，`AppMountFlags` 全默认 ⇒ 每例只点亮相关位）：

```kotlin
package com.example.englishlearning.ui

import com.example.englishlearning.ai.domain.AiFeature
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 批 D-1 挂载权威（判据：`docs/decisions/2026-11-08-page-transition-routing-and-motion.md` §3.1）。
 * 三条纪律：① 判据逐字搬自链，多真取**先声明**那支；② payload 冻进 route，退场腿不回读实时状态；
 * ③ 顺序承诺是语义不是风格——QA3 A1「许可页必须排在关于页之前」在这里钉一次。
 */
class AppOverlayRouteResolverTest {
    private val card = WordCard("c1", "cet4", "apple", "", "", "")
    private val plan = TodayPlanUiState.Ready(
        wordBookName = "四级核心词", localDateLabel = "11 月 8 日",
        newTarget = 10, dueTarget = 5, totalTasks = 15,
    )

    @Test
    fun noFlagsMeansTabHost() = assertNull(AppOverlayRouteResolver.resolve(AppMountFlags()))

    @Test
    fun firstDeclaredWinsWhenSeveralAreTrue() {
        val route = AppOverlayRouteResolver.resolve(
            AppMountFlags(selectedSearchCard = card, showSettings = true, showAbout = true),
        )
        assertEquals(AppOverlayRoute.VocabularyDetail(card), route)
    }

    @Test
    fun licensesOutranksAboutExactlyAsTheChainDoes() {
        val route = AppOverlayRouteResolver.resolve(AppMountFlags(showLicenses = true, showAbout = true))
        assertTrue(route is AppOverlayRoute.Licenses, "A1 顺序承诺被搬反：$route")
        assertEquals(22, route.mountIndex)
        assertEquals(23, AppOverlayRouteResolver.resolve(AppMountFlags(showAbout = true))!!.mountIndex)
    }

    @Test
    fun guardedBranchesFallToTheShallowerHostWhenTheGuardFails() {
        // 链原文 `showReviewQueue && todayReady != null`：ready 缺席时这一支根本不挂，
        // 必须落回浅层（null = tab 宿主），而不是挂一支没有 plan 的 route。
        val route = AppOverlayRouteResolver.resolve(AppMountFlags(showReviewQueue = true, todayReady = null))
        assertNull(route)
        val withPlan = AppOverlayRouteResolver.resolve(AppMountFlags(showReviewQueue = true, todayReady = plan))
        assertEquals(AppOverlayRoute.ReviewQueue(plan), withPlan)
    }

    @Test
    fun articleLayerOutranksHistoryAndFeedBoth() {
        // 链原文：`showReadingHistory && historyArticle == null` / `showReadingFeed && readingTarget == null
        // && historyArticle == null` / `readingTarget != null || historyArticle != null`
        assertEquals(
            AppOverlayRoute.ReadingHistory,
            AppOverlayRouteResolver.resolve(AppMountFlags(showReadingHistory = true, historyArticle = null)),
        )
        assertEquals(
            AppOverlayRoute.ArticleReading(null, cardArticle()),
            AppOverlayRouteResolver.resolve(AppMountFlags(showReadingHistory = true, historyArticle = cardArticle())),
        )
        assertEquals(
            AppOverlayRoute.ReadingFeed,
            AppOverlayRouteResolver.resolve(
                AppMountFlags(showReadingFeed = true, readingTarget = null, historyArticle = null),
            ),
        )
    }

    @Test
    fun payloadIsFrozenIntoTheRouteNotReadBackFromLiveState() {
        val route = AppOverlayRouteResolver.resolve(AppMountFlags(selectedRelearnCard = card))
        assertEquals(AppOverlayRoute.RelearnCard(card), route)
        assertEquals(card, (route as AppOverlayRoute.RelearnCard).card)
    }

    @Test
    fun unknownFeatureKeyFallsToTheScaffoldAndNotToTheUnavailableScreen() {
        // 链原文两支：`selectedFeature != null`（27）在前、`selectedFeatureKey != null`（28）在后。
        val known = AppMountFlags(
            selectedFeature = AiFeature.SENTENCE_ANALYSIS,
            selectedFeatureKey = AiFeature.SENTENCE_ANALYSIS.key,
        )
        assertEquals(AppOverlayRoute.SentenceAnalysis, AppOverlayRouteResolver.resolve(known))
        assertEquals(
            AppOverlayRoute.UnknownAiFeature("a-key-nobody-registered"),
            AppOverlayRouteResolver.resolve(AppMountFlags(selectedFeatureKey = "a-key-nobody-registered")),
        )
    }

    @Test
    fun everyRouteVariantCarriesItsTableIndex() {
        val indices = listOf(
            AppMountFlags(selectedSearchCard = card) to 0, AppMountFlags(showGlobalVocabularySearch = true) to 1,
            AppMountFlags(selectedRelearnCard = card) to 2, AppMountFlags(selectedLearningRecordCard = card) to 3,
            AppMountFlags(selectedFavoriteCard = card) to 4, AppMountFlags(showVocabularyRelearn = true) to 5,
            AppMountFlags(showLearningRecords = true) to 6, AppMountFlags(showSetup = true) to 7,
            AppMountFlags(showLearning = true) to 8, AppMountFlags(showWorksheetPreview = true) to 9,
            AppMountFlags(showWorksheetSettings = true) to 10,
            AppMountFlags(showReviewQueue = true, todayReady = plan) to 11,
            AppMountFlags(showTodayWordList = true, todayReady = plan) to 12,
            AppMountFlags(showCheckIn = true) to 13, AppMountFlags(showWordBank = true) to 14,
            AppMountFlags(showAiProfiles = true) to 15, AppMountFlags(showSpeechSettings = true) to 16,
            AppMountFlags(showReminderSettings = true) to 17, AppMountFlags(showBackupExport = true) to 18,
            AppMountFlags(showBackupRestore = true) to 19, AppMountFlags(showBackupPairingExport = true) to 20,
            AppMountFlags(showBackupHub = true) to 21, AppMountFlags(showLicenses = true) to 22,
            AppMountFlags(showAbout = true) to 23, AppMountFlags(showArticleImport = true) to 31,
            AppMountFlags(showWordBookTransfer = true) to 33, AppMountFlags(showSettings = true) to 34,
        )
        indices.forEach { (flags, expected) ->
            assertEquals(expected, AppOverlayRouteResolver.resolve(flags)?.mountIndex, "flags → $flags")
        }
    }

    @Test
    fun setupRequiredCountsWithoutTheUserOpeningIt() {
        assertEquals(
            AppOverlayRoute.LearningSetup,
            AppOverlayRouteResolver.resolve(AppMountFlags(setupRequired = true, showSetup = false)),
        )
    }

    /** 构造形状逐字取自 `app/src/test/java/com/example/englishlearning/reading/GenerateArticleUseCaseTest.kt:175`。 */
    private fun cardArticle(): Article = Article(
        articleId = "a1",
        profileId = "p1",
        localDate = "2026-11-08",
        activeWordBookId = "cet4",
        articleType = ArticleType.STORY,
        lengthTier = ArticleLengthTier.STANDARD,
        version = 1,
        title = "Old",
        englishText = "Some english text.",
        chineseText = "旧译文。",
        generatedAtEpochMillis = 0L,
        coveredLemmas = listOf("apple"),
        source = ArticleSource.UserImported,
    )
}
```

（`ArticleType` / `ArticleLengthTier` / `ArticleSource` 三个 import 与 `GenerateArticleUseCaseTest` 同源：`reading.domain`。）

`ScreenTransitionDirectionTest.kt`：

```kotlin
package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** 批 D-1 方向判据（决策 §3.2）：链声明序就是层深，不建第二张深度表。 */
class ScreenTransitionDirectionTest {
    private val settings = AppOverlayRoute.Settings
    private val about = AppOverlayRoute.About
    private val detail = AppOverlayRoute.VocabularyDetail(WordCard("c1", "cet4", "apple", "", "", ""))

    @Test
    fun baseToSubPageIsForward() = assertEquals(
        ScreenTransitionDirection.Forward,
        ScreenTransitionDirectionPolicy.overlay(from = null, to = settings),
    )

    @Test
    fun subPageBackToBaseIsBackward() = assertEquals(
        ScreenTransitionDirection.Backward,
        ScreenTransitionDirectionPolicy.overlay(from = settings, to = null),
    )

    @Test
    fun shallowerToDeeperIsForward() = assertEquals(
        ScreenTransitionDirection.Forward,
        ScreenTransitionDirectionPolicy.overlay(from = settings, to = about),
    )

    @Test
    fun deeperToShallowerIsBackward() = assertEquals(
        ScreenTransitionDirection.Backward,
        ScreenTransitionDirectionPolicy.overlay(from = about, to = settings),
    )

    @Test
    fun sameLayerSwapOnlyCrossfades() = assertEquals(
        ScreenTransitionDirection.Crossfade,
        ScreenTransitionDirectionPolicy.overlay(
            from = AppOverlayRoute.VocabularyDetail(WordCard("c1", "cet4", "apple", "", "", "")),
            to = AppOverlayRoute.VocabularyDetail(WordCard("c2", "cet4", "banana", "", "", "")),
        ),
    )

    @Test
    fun tabOrderDecidesLateralDirection() {
        assertEquals(ScreenTransitionDirection.Forward, ScreenTransitionDirectionPolicy.tab(AppTab.LEARNING, AppTab.PROFILE))
        assertEquals(ScreenTransitionDirection.Backward, ScreenTransitionDirectionPolicy.tab(AppTab.PROFILE, AppTab.LEARNING))
        assertEquals(ScreenTransitionDirection.Crossfade, ScreenTransitionDirectionPolicy.tab(AppTab.READING, AppTab.READING))
    }

    @Test
    fun tabHostIsTheShallowestLayer() = assertEquals(
        AppOverlayRouteResolver.TAB_HOST_INDEX,
        ScreenTransitionDirectionPolicy.mountIndex(null),
    )
}
```

- [ ] **Step 2：跑 RED，确认红在「类型还不存在」**

```bash
"$GRADLE" :app:testDebugUnitTest --rerun-tasks --tests "*AppOverlayRouteResolverTest" --tests "*ScreenTransitionDirectionTest" 2>&1 | grep -E "^e:|FAILED|BUILD|unresolved" ; exit ${PIPESTATUS[0]}
```
Expected: 编译失败 `Unresolved reference: AppOverlayRoute` 一类——**必须**是这个红，不是断言红。

- [ ] **Step 3：写 `AppOverlayRoute.kt`（全量）**

```kotlin
package com.example.englishlearning.ui

import com.example.englishlearning.ai.domain.AiFeature
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article

/**
 * 覆盖全屏的二级层「现在挂的是哪一支」——挂载判据的唯一载体（批 D-1，决策 §3.1）。
 *
 * `mountIndex` 就是它在 `AppScreen` 原链里的声明序号（0..34，见计划批 D 节 route 表），
 * tab 宿主 = [AppOverlayRouteResolver.TAB_HOST_INDEX]。序号即层级：越小越深、越先赢，
 * 所以方向判据（[ScreenTransitionDirectionPolicy]）只比序号，不需要第二张深度表。
 *
 * payload 为什么冻在这里而不是让渲染腿回读实时状态：`AnimatedContent` 的退场腿在标志位翻转之后
 * 还要继续组合 300ms，回读实时状态会让旧屏在半秒里被换掉内容甚至直接 NPE
 * （原链 849 行 `selectedRelearnCard!!` 就是那种写法）。
 */
sealed interface AppOverlayRoute {
    val mountIndex: Int

    data class VocabularyDetail(val card: WordCard) : AppOverlayRoute { override val mountIndex = 0 }
    data object GlobalSearch : AppOverlayRoute { override val mountIndex = 1 }
    data class RelearnCard(val card: WordCard) : AppOverlayRoute { override val mountIndex = 2 }
    data class LearningRecordDetail(val card: WordCard) : AppOverlayRoute { override val mountIndex = 3 }
    data class FavoriteDetail(val card: WordCard) : AppOverlayRoute { override val mountIndex = 4 }
    data object VocabularyRelearnList : AppOverlayRoute { override val mountIndex = 5 }
    data object LearningRecords : AppOverlayRoute { override val mountIndex = 6 }
    data object LearningSetup : AppOverlayRoute { override val mountIndex = 7 }
    data object LearningFlow : AppOverlayRoute { override val mountIndex = 8 }
    data object WorksheetPreview : AppOverlayRoute { override val mountIndex = 9 }
    data object WorksheetSettings : AppOverlayRoute { override val mountIndex = 10 }
    data class ReviewQueue(val plan: TodayPlanUiState.Ready) : AppOverlayRoute { override val mountIndex = 11 }
    data class TodayWordList(val plan: TodayPlanUiState.Ready) : AppOverlayRoute { override val mountIndex = 12 }
    data object CheckIn : AppOverlayRoute { override val mountIndex = 13 }
    data object WordBank : AppOverlayRoute { override val mountIndex = 14 }
    data object AiProfiles : AppOverlayRoute { override val mountIndex = 15 }
    data object SpeechSettings : AppOverlayRoute { override val mountIndex = 16 }
    data object ReminderSettings : AppOverlayRoute { override val mountIndex = 17 }
    data object BackupExport : AppOverlayRoute { override val mountIndex = 18 }
    data object BackupRestore : AppOverlayRoute { override val mountIndex = 19 }
    data object BackupPairingExport : AppOverlayRoute { override val mountIndex = 20 }
    data object BackupHub : AppOverlayRoute { override val mountIndex = 21 }
    data object Licenses : AppOverlayRoute { override val mountIndex = 22 }
    data object About : AppOverlayRoute { override val mountIndex = 23 }
    data object SentenceAnalysis : AppOverlayRoute { override val mountIndex = 24 }
    data object PhotoTranslation : AppOverlayRoute { override val mountIndex = 25 }
    data object ImageStudio : AppOverlayRoute { override val mountIndex = 26 }
    data class AiFeatureScaffold(val feature: AiFeature) : AppOverlayRoute { override val mountIndex = 27 }
    data class UnknownAiFeature(val featureKey: String) : AppOverlayRoute { override val mountIndex = 28 }
    data object ReadingHistory : AppOverlayRoute { override val mountIndex = 29 }
    data object ReadingFeed : AppOverlayRoute { override val mountIndex = 30 }
    data object ArticleImport : AppOverlayRoute { override val mountIndex = 31 }

    /** 两枚都进 route：`AppScreen.kt:1847` 的 `fromHistory = readingTarget == null` 靠它们判来源，不能回读实时状态。 */
    data class ArticleReading(val readingTarget: ReadingTarget?, val historyArticle: Article?) : AppOverlayRoute {
        override val mountIndex = 32
    }

    data object WordBookTransfer : AppOverlayRoute { override val mountIndex = 33 }
    data object Settings : AppOverlayRoute { override val mountIndex = 34 }
}
```

- [ ] **Step 4：写 `AppMountFlags.kt`（35 枚入参，逐字对齐链的读点）**

```kotlin
package com.example.englishlearning.ui

import com.example.englishlearning.ai.domain.AiFeature
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article

/**
 * [AppOverlayRouteResolver] 的唯一入参：把散在 `AppScreen` 里 170–440 行的标志位与派生值收成一份值对象。
 *
 * **全部带默认值**——生产侧逐一点亮（不新增状态、不改任何写入点），测试侧只写相关位。
 * `setupRequired` / `todayReady` / `selectedFeature` 三枚本来就是 `AppScreen` 里的派生值
 * （395 / 394 / 369 行），照原样传进来；resolver 不重新推导，避免长出第二份派生口径。
 */
data class AppMountFlags(
    val selectedSearchCard: WordCard? = null,
    val showGlobalVocabularySearch: Boolean = false,
    val selectedRelearnCard: WordCard? = null,
    val selectedLearningRecordCard: WordCard? = null,
    val selectedFavoriteCard: WordCard? = null,
    val showVocabularyRelearn: Boolean = false,
    val showLearningRecords: Boolean = false,
    val setupRequired: Boolean = false,
    val showSetup: Boolean = false,
    val showLearning: Boolean = false,
    val showWorksheetPreview: Boolean = false,
    val showWorksheetSettings: Boolean = false,
    val showReviewQueue: Boolean = false,
    val todayReady: TodayPlanUiState.Ready? = null,
    val showTodayWordList: Boolean = false,
    val showCheckIn: Boolean = false,
    val showWordBank: Boolean = false,
    val showAiProfiles: Boolean = false,
    val showSpeechSettings: Boolean = false,
    val showReminderSettings: Boolean = false,
    val showBackupExport: Boolean = false,
    val showBackupRestore: Boolean = false,
    val showBackupPairingExport: Boolean = false,
    val showBackupHub: Boolean = false,
    val showLicenses: Boolean = false,
    val showAbout: Boolean = false,
    val selectedFeature: AiFeature? = null,
    val selectedFeatureKey: String? = null,
    val showReadingHistory: Boolean = false,
    val historyArticle: Article? = null,
    val showReadingFeed: Boolean = false,
    val readingTarget: ReadingTarget? = null,
    val showArticleImport: Boolean = false,
    val showWordBookTransfer: Boolean = false,
    val showSettings: Boolean = false,
)
```

- [ ] **Step 5：写 `AppOverlayRouteResolver.kt`（判据逐字搬自链）**

```kotlin
package com.example.englishlearning.ui

import com.example.englishlearning.ai.domain.AiFeature

/**
 * 挂载权威：`AppMountFlags` → 当前该挂的那一支全屏覆盖层（`null` = tab 宿主）。
 *
 * 下面 35 枚条件与 `AppScreen.kt:728`–`2020` 原链**逐字一致、顺序逐字照抄**：
 * 「先声明先赢」在原文件里既是 z 序也是返回键优先级承诺（QA3 A1 的许可/关于顺序就是这条），
 * 所以这里改顺序 = 改产品行为，必须回到决策文档重开，不许顺手「整理」。
 * 由 `ScreenTransitionContractTest` 双向钉：链上不许残留挂载级判据，本文件不许漏支。
 */
object AppOverlayRouteResolver {
    /** 链末 `} else {`（tab 宿主）的层级序号：比 34 支都浅，比任何 route 都浅。 */
    const val TAB_HOST_INDEX = 35

    fun resolve(flags: AppMountFlags): AppOverlayRoute? = when {
        flags.selectedSearchCard != null -> AppOverlayRoute.VocabularyDetail(flags.selectedSearchCard)
        flags.showGlobalVocabularySearch -> AppOverlayRoute.GlobalSearch
        flags.selectedRelearnCard != null -> AppOverlayRoute.RelearnCard(flags.selectedRelearnCard)
        flags.selectedLearningRecordCard != null -> AppOverlayRoute.LearningRecordDetail(flags.selectedLearningRecordCard)
        flags.selectedFavoriteCard != null -> AppOverlayRoute.FavoriteDetail(flags.selectedFavoriteCard)
        flags.showVocabularyRelearn -> AppOverlayRoute.VocabularyRelearnList
        flags.showLearningRecords -> AppOverlayRoute.LearningRecords
        flags.setupRequired || flags.showSetup -> AppOverlayRoute.LearningSetup
        flags.showLearning -> AppOverlayRoute.LearningFlow
        flags.showWorksheetPreview -> AppOverlayRoute.WorksheetPreview
        flags.showWorksheetSettings -> AppOverlayRoute.WorksheetSettings
        flags.showReviewQueue && flags.todayReady != null -> AppOverlayRoute.ReviewQueue(flags.todayReady)
        flags.showTodayWordList && flags.todayReady != null -> AppOverlayRoute.TodayWordList(flags.todayReady)
        flags.showCheckIn -> AppOverlayRoute.CheckIn
        flags.showWordBank -> AppOverlayRoute.WordBank
        flags.showAiProfiles -> AppOverlayRoute.AiProfiles
        flags.showSpeechSettings -> AppOverlayRoute.SpeechSettings
        flags.showReminderSettings -> AppOverlayRoute.ReminderSettings
        flags.showBackupExport -> AppOverlayRoute.BackupExport
        flags.showBackupRestore -> AppOverlayRoute.BackupRestore
        flags.showBackupPairingExport -> AppOverlayRoute.BackupPairingExport
        flags.showBackupHub -> AppOverlayRoute.BackupHub
        flags.showLicenses -> AppOverlayRoute.Licenses
        flags.showAbout -> AppOverlayRoute.About
        flags.selectedFeature == AiFeature.SENTENCE_ANALYSIS -> AppOverlayRoute.SentenceAnalysis
        flags.selectedFeature == AiFeature.PHOTO_TRANSLATION -> AppOverlayRoute.PhotoTranslation
        flags.selectedFeature == AiFeature.IMAGE_STUDIO -> AppOverlayRoute.ImageStudio
        flags.selectedFeature != null -> AppOverlayRoute.AiFeatureScaffold(flags.selectedFeature)
        flags.selectedFeatureKey != null -> AppOverlayRoute.UnknownAiFeature(flags.selectedFeatureKey)
        flags.showReadingHistory && flags.historyArticle == null -> AppOverlayRoute.ReadingHistory
        flags.showReadingFeed && flags.readingTarget == null && flags.historyArticle == null -> AppOverlayRoute.ReadingFeed
        flags.showArticleImport -> AppOverlayRoute.ArticleImport
        flags.readingTarget != null || flags.historyArticle != null ->
            AppOverlayRoute.ArticleReading(flags.readingTarget, flags.historyArticle)
        flags.showWordBookTransfer -> AppOverlayRoute.WordBookTransfer
        flags.showSettings -> AppOverlayRoute.Settings
        else -> null
    }
}
```

> 编译期若报 smart-cast（`flags.selectedSearchCard` 是 data class 属性，跨 `!= null` 判定不智能转换），
> 处置是在函数开头 `val x = flags.selectedSearchCard` 取局部，**不许**改成 `!!`——保持判据字面不动。

- [ ] **Step 6：写 `ScreenTransitionDirection.kt`**

```kotlin
package com.example.englishlearning.ui

/** 转场方向（决策 §3.2）。三形就够：更深 = Forward，更浅 = Backward，同层 = Crossfade。 */
enum class ScreenTransitionDirection { Forward, Backward, Crossfade }

/**
 * 方向判据：链声明序就是层深，tab 宿主最浅（[AppOverlayRouteResolver.TAB_HOST_INDEX]）。
 * 因此「基座 → 子页」「子页 → 更深子页」「子页 → 返回」三种腿共用一条比较。
 * tab 之间按 `AppTab.ordinal`（LEARNING 0 / READING 1 / AI 2 / PROFILE 3）：序号增大 = Forward。
 * 首次组合不播由 `AnimatedContent` 自己的语义保证（只有状态变化后才启动 transition），这里不写 `initial()`。
 */
object ScreenTransitionDirectionPolicy {
    fun mountIndex(route: AppOverlayRoute?): Int = route?.mountIndex ?: AppOverlayRouteResolver.TAB_HOST_INDEX

    fun overlay(from: AppOverlayRoute?, to: AppOverlayRoute?): ScreenTransitionDirection = when {
        mountIndex(to) < mountIndex(from) -> ScreenTransitionDirection.Forward
        mountIndex(to) > mountIndex(from) -> ScreenTransitionDirection.Backward
        else -> ScreenTransitionDirection.Crossfade
    }

    fun tab(from: AppTab, to: AppTab): ScreenTransitionDirection = when {
        to.ordinal > from.ordinal -> ScreenTransitionDirection.Forward
        to.ordinal < from.ordinal -> ScreenTransitionDirection.Backward
        else -> ScreenTransitionDirection.Crossfade
    }
}
```

- [ ] **Step 7：跑 GREEN + 门禁**（Step 2 同一条命令，Expected `BUILD SUCCESSFUL`；再跑完整三连门禁，JVM 基线 2494 → ≥2504，0 失败 0 跳过）

- [ ] **Step 8：Commit**

```bash
git add app/src/main/java/com/example/englishlearning/ui/AppOverlayRoute.kt \
        app/src/main/java/com/example/englishlearning/ui/AppMountFlags.kt \
        app/src/main/java/com/example/englishlearning/ui/AppOverlayRouteResolver.kt \
        app/src/main/java/com/example/englishlearning/ui/ScreenTransitionDirection.kt \
        app/src/test/java/com/example/englishlearning/ui/AppOverlayRouteResolverTest.kt \
        app/src/test/java/com/example/englishlearning/ui/ScreenTransitionDirectionTest.kt
git -c user.name=steveliuyan -c user.email=2021250388@qq.com commit -m "feat(ui): 批 D-1 挂载权威与方向判据收进纯函数"
```

---

### D-2：把链改成消费 route（行为等价重构，本段零动画）

**Files:**
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（728–2194 的链、35 枚条件行、payload 读点）
- Test: `app/src/test/java/com/example/englishlearning/ui/ScreenTransitionContractTest.kt`（本段先落「判据不残留」+「route 全覆盖」两条，转场站点条留到 D-3）

**Interfaces:**
- Consumes: D-1 全部产物。
- Produces: `AppScreen` 内一条 `if (route is AppOverlayRoute.X) … else if (route == null)` 链；
  局部 `val overlayRoute = AppOverlayRouteResolver.resolve(AppMountFlags(...))` 与 `val route = overlayRoute`；
  D-3 直接把这段整体塞进 `AnimatedContent` 的 lambda，**链体文字一字不改**。

- [ ] **Step 1：RED——契约闸的两条判据先写先红**

在 `ScreenTransitionContractTest.kt` 写（扫描纪律照 `MotionSpecContractTest`：路径常量 + 文件数守卫 + 合成双边 + 消费点计数）：

```kotlin
private const val APP_SCREEN_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/AppScreen.kt"
private const val ROUTE_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/AppOverlayRoute.kt"
private const val RESOLVER_SOURCE_PATH = "src/main/java/com/example/englishlearning/ui/AppOverlayRouteResolver.kt"
private const val OVERLAY_ROUTE_COUNT = 35
```

两条判据（都要先看红）：
1. `chainHasNoMountLevelFlagConditionsAnymore`：`AppScreen.kt` 剥注释后不得再出现
   Regex `"""\b\} else if \((?:show[A-Z]\w*|selected[A-Z]\w* \!= null|setupRequired)"""` 之类的**挂载级**原判据
   （即链上必须只剩 `is AppOverlayRoute.`，一处原标志位判定都不许留）。
   已知豁免名单**只有一条**且必须写明理由：`AppScreen.kt:580`–`584` 的 `overlayOpen`——
   它是「有覆盖层标志位亮着」（含两枚不在链上的页内浮层位 `selectedArticleCard` / `showWordQaCard`，
   且不含 `&& todayReady != null` 那类守卫），消费者是 :587 的 `BackHandler`，与「挂载了哪一支」是两个不同问题
   （决策 §1.3-1）。名单按文件名 + 行区间钉死，超出一律判红。
2. `everyRouteVariantIsConsumedByExactlyOneBranch`：从 `AppOverlayRoute.kt` 解析出 35 个变体名，
   每个在 `AppScreen.kt` 剥注释后恰好出现一次 `is AppOverlayRoute.<Name>`；
   枚数 != `OVERLAY_ROUTE_COUNT` 直接报「route 表被改而本契约未同步」（反空转：解析到 0 枚必须判红）。

- [ ] **Step 2：跑 RED**
  `"$GRADLE" :app:testDebugUnitTest --rerun-tasks --tests "*ScreenTransitionContractTest"`
  Expected: 两条都红——①「链上仍残留 34 枚原判据」，②「`is AppOverlayRoute.` 出现 0 次」。

- [ ] **Step 3：链头接线**（在 `AppScreen.kt` 原 727 行 `BackHandler(enabled = selectedFeatureKey != null) { … }` 之后、728 行之前插入）

```kotlin
            val overlayRoute = AppOverlayRouteResolver.resolve(
                AppMountFlags(
                    selectedSearchCard = selectedSearchCard,
                    showGlobalVocabularySearch = showGlobalVocabularySearch,
                    selectedRelearnCard = selectedRelearnCard,
                    selectedLearningRecordCard = selectedLearningRecordCard,
                    selectedFavoriteCard = selectedFavoriteCard,
                    showVocabularyRelearn = showVocabularyRelearn,
                    showLearningRecords = showLearningRecords,
                    setupRequired = setupRequired,
                    showSetup = showSetup,
                    showLearning = showLearning,
                    showWorksheetPreview = showWorksheetPreview,
                    showWorksheetSettings = showWorksheetSettings,
                    showReviewQueue = showReviewQueue,
                    todayReady = todayReady,
                    showTodayWordList = showTodayWordList,
                    showCheckIn = showCheckIn,
                    showWordBank = showWordBank,
                    showAiProfiles = showAiProfiles,
                    showSpeechSettings = showSpeechSettings,
                    showReminderSettings = showReminderSettings,
                    showBackupExport = showBackupExport,
                    showBackupRestore = showBackupRestore,
                    showBackupPairingExport = showBackupPairingExport,
                    showBackupHub = showBackupHub,
                    showLicenses = showLicenses,
                    showAbout = showAbout,
                    selectedFeature = selectedFeature,
                    selectedFeatureKey = selectedFeatureKey,
                    showReadingHistory = showReadingHistory,
                    historyArticle = historyArticle,
                    showReadingFeed = showReadingFeed,
                    readingTarget = readingTarget,
                    showArticleImport = showArticleImport,
                    showWordBookTransfer = showWordBookTransfer,
                    showSettings = showSettings,
                ),
            )
            val route = overlayRoute
```

- [ ] **Step 4：35 枚条件行逐字替换（每行一处改动，其余文字与缩进不动）**

对 route 表逐行执行「条件行整行替换」：

```kotlin
// idx 0，原 728 行
            if (route is AppOverlayRoute.VocabularyDetail) {
// 其余 34 枚同形
            } else if (route is AppOverlayRoute.GlobalSearch) {
// idx 34
            } else if (route is AppOverlayRoute.Settings) {
// 原 2026 行 `} else {`（tab 宿主）
            } else if (route == null) {
```

payload 读点只改**本支的挂载 payload**（其余读点一字不动）。全量清单已用上面那条命令实测，逐行如下：

| 支 | 行号与原文 | 改成 |
|---|---|---|
| 0 | 729 `val card = selectedSearchCard` + 730 `if (card != null) {` | `val card = route.card`，删掉这层 `if (card != null)` 包裹（其内部整体反缩进一级）；**738 行 `selectedSearchCard === cardSnapshot` 竞态守卫保持原样**（它读的正是「实时状态还是不是这张卡」） |
| 2 | 849 `val card = selectedRelearnCard!!` | `val card = route.card`（`!!` 随之下线——这正是决策 §3.1 说的退场腿 NPE 形状） |
| 3 | 861 `val card = selectedLearningRecordCard`（连同其 `if (card != null)` 包裹） | `val card = route.card` |
| 4 | 942 `val card = selectedFavoriteCard`（同上包裹） | `val card = route.card` |
| 11 | 1287 `state = todayReady,` | `state = route.plan,` |
| 12 | 1303 `state = todayReady,` | `state = route.plan,` |
| 27 | 1758 `feature = selectedFeature,` | `feature = route.feature,` |
| 32 | 1847 `val fromHistory = readingTarget == null` | `val fromHistory = route.readingTarget == null`；`historyArticle` 在本支只出现在 1854 的 `onBack` **写回**里，不动 |

**不动的清单同样明确**：1656 / 1659 / 1712 / 1715 / 1750 / 1752 / 1759 / 1761 / 1772 / 1781 / 1783 / 1787 全是
`selectedFeatureKey = null` 这类 `onBack` 写回（写状态位是意图，不是渲染输入）；
857 `vocabularyRelearnViewModel?.load(state.profile.id, todayReady?.activeWordBookId)` 这类**跨支取上下文**的读点也不动
（它不是本支的挂载 payload）。24 / 25 / 26 三支没有 payload 读点（判别式本身就是枚举）。

逐条落实前先用命令把候选行列出来（**不许凭记忆**）：

```bash
awk 'NR>=728 && NR<=2194' app/src/main/java/com/example/englishlearning/ui/AppScreen.kt \
  | grep -nE "selectedSearchCard|selectedRelearnCard|selectedLearningRecordCard|selectedFavoriteCard|todayReady|selectedFeature|readingTarget|historyArticle" \
  | awk -v OFS=":" '{print NR+727, $0}'
```

- [ ] **Step 5：GREEN + 既有 connected 全量不许变**

```bash
"$GRADLE" :app:assembleDebug :app:testDebugUnitTest --rerun-tasks :app:compileDebugAndroidTestKotlin --no-daemon --no-build-cache 2>&1 \
  | grep -E 'FAILED|^e:|BUILD|tests completed' ; exit ${PIPESTATUS[0]}
```
connected 侧（`adb install -r` 双 APK 后跑 `AppScreenTest` 全类 + `RecoveryScreenTest`）：末行必须仍是 `OK (66 tests)` / 原有读数，
任何一屏「打不开」都说明判据搬错——**改实现，不许改测试**。

- [ ] **Step 6：Commit** — `git add app/src/main/java/com/example/englishlearning/ui/AppScreen.kt app/src/test/java/com/example/englishlearning/ui/ScreenTransitionContractTest.kt` + `refactor(ui): 批 D-2 链改为消费 AppOverlayRoute（行为等价，零动画）`

---

### D-3：两站 AnimatedContent + `AppTransition` 规格表（本批唯一的可见变化）

**Files:**
- Create: `app/src/main/java/com/example/englishlearning/ui/theme/AppTransition.kt`
- Modify: `app/src/main/java/com/example/englishlearning/ui/AppScreen.kt`（包住链 + `when (selectedTab)` 那一站 + 退场腿语义）
- Modify: `app/src/main/java/com/example/englishlearning/ui/components/glass/GlassOverlay.kt:58`–`64`（四枚 `tween(` 并表）
- Modify: `app/src/test/java/com/example/englishlearning/ui/MotionSpecContractTest.kt:116`–`126`、`:214`（锚点改规格宿主）
- Modify: `app/src/test/java/com/example/englishlearning/ui/ScreenTransitionContractTest.kt`（补站点数 / 规格唯一 / 位移三条 + `AppMotion.Slow` 读点）
- Test(androidTest): `app/src/androidTest/java/com/example/englishlearning/ui/ScreenTransitionInstrumentedTest.kt`（新建）+ `AppScreenTest.kt` 追加两例

**Interfaces:**
- Consumes: `ScreenTransitionDirection` / `Policy` / `AppOverlayRoute` / `overlayRoute`（D-1、D-2）。
- Produces:
  - `object AppTransition { val PageForwardEnter: EnterTransition; val PageForwardExit: ExitTransition; val PageBackwardEnter; val PageBackwardExit; val CrossfadeEnter; val CrossfadeExit; val DialogEnter; val DialogExit; fun contentTransform(direction: ScreenTransitionDirection): ContentTransform }`
  - 两个 `AnimatedContent` 站点；位移口径 `it / 4`（¼ 幅）

- [ ] **Step 1：RED——connected 行为用例先红在「退场腿根本没被组合」**

先落两枚**站点腿标签**（生产侧，Step 5/6 实现时同一处加上，本步先写测试 ⇒ 测试必然红）：站 1 的腿根 `Box` 打 `Modifier.testTag("overlay_transition_leg")`，站 2 的腿根 `Box` 打 `Modifier.testTag("tab_transition_leg")`。
两判据的形状本批已实测确认：`ui-test-android:1.10.0` 的字节码里**没有一处引用 `InvisibleToUser`**
（`grep -rl "InvisibleToUser" <ui-test classes>` → 空），⇒ 测试查询**不会**因为退场腿 `invisibleToUser()` 而少算一个节点，
「同一帧两腿都在」可以按节点数直接断言；`invisibleToUser` 本身改读 `SemanticsProperties.InvisibleToUser`（`SemanticsPropertyKey<Unit>`，实测存在）。

`app/src/androidTest/java/com/example/englishlearning/ui/ScreenTransitionInstrumentedTest.kt`（新建）：

```kotlin
package com.example.englishlearning.ui

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.core.time.FixedClockProvider
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.InMemoryLocalProfileRepository
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTransitionInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    /** 判据 1：切 tab 的半程里两腿同时存在；settle 后只剩一腿。 */
    @Test
    fun tabSwitchKeepsBothLegsMountedMidTransition() {
        renderReadyAppOnLearningTab()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        assertEquals(
            2,
            composeRule.onAllNodesWithTag("tab_transition_leg").fetchSemanticsNodes().size,
            "转场半程只有一腿 ⇒ AnimatedContent 没在播退场，链仍然当场翻转",
        )
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithTag("tab_transition_leg").fetchSemanticsNodes().size)
    }

    /** 判据 2：退场腿对无障碍隐身，入场腿不隐身。 */
    @Test
    fun outgoingLegIsHiddenFromAccessibility() {
        renderReadyAppOnLearningTab()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("app_tab_reading").performClick()
        composeRule.mainClock.advanceTimeBy(150)
        val legs = composeRule.onAllNodesWithTag("tab_transition_leg").fetchSemanticsNodes()
        assertEquals(2, legs.size)
        val hidden = legs.count { it.config.contains(SemanticsProperties.InvisibleToUser) }
        assertEquals(1, hidden, "两腿里必须恰好一枚带 invisibleToUser，且是离场那枚")
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    /**
     * 判据 3：开全屏子页（站 1）同样两腿共存。
     * 入口与锚点全部现读，不是编的：`app_tab_ai` 与 `profile_open_settings` 分别是
     * `AppBottomBar.kt:187`（`app_tab_${tab.name.lowercase()}`）与 `ProfileScreen.kt:104` 的既有 tag，
     * 点击配方照 `AppScreenTest.kt:617`（`performScrollTo().performClick()`）逐字抄。
     */
    @Test
    fun openingFullScreenSubPageKeepsBothLegsMountedMidTransition() {
        renderReadyAppOnProfileTab()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("profile_open_settings").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("profile_open_settings").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(150)
        assertEquals(
            2,
            composeRule.onAllNodesWithTag("overlay_transition_leg").fetchSemanticsNodes().size,
            "开设置子页的半程只有一腿 ⇒ 站 1 没在播退场，链仍按标志位当场翻转",
        )
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertExists()
        assertEquals(1, composeRule.onAllNodesWithTag("overlay_transition_leg").fetchSemanticsNodes().size)
    }

    /**
     * 判据 4（本批最贵）：整段转场零额外计费出站。
     *
     * **仓内没有现成的计数 transport 可复用**（现算：`grep -rn "AiHttpTransport" app/src/androidTest | grep -v "error("`
     * 只命中 `AppScreenTest.kt` 那六枚「一调就 error」的禁调假件；`RequestInProgressEvidenceTest` 根本不接传输层，
     * 它是纯渲染取证类），所以本类自带一枚计数假件，形状照 `AppScreenTest.kt:1949-1964`
     * 的 `sentenceAnalysisViewModelWith`（含 `AiProfileSecretUseCase` 的内存 `SecretStore`），
     * 只把 `error(...)` 换成「记下请求、回 `NetworkUnavailable`」——回失败而不是回假的 200，
     * 因为本条判据只数次数，不验结果内容（编一份能过解析的响应体就是把判据建立在假数据上）。
     *
     * 牙齿在哪：`DefaultTextProfileResult.Selected(...)` + `has(reference) = success(true)` 让配置是**齐的**，
     * 于是「转场把入场腿重新组合、把某条 `LaunchedEffect` 再点一次」这条风险一旦为真，计数就会跳；
     * 而不是像「空配置」那样无论怎么改都恒为 0。
     * 纯导航（开子页 / 退回基座 / 再开）按产品口径**一次出站都不该有**，所以断言是 `isEmpty()`。
     */
    @Test
    fun transitionsProduceNoExtraBillableOutbound() {
        val transport = CountingAiHttpTransport()
        renderAppScreen(sentenceAnalysisViewModel = sentenceAnalysisViewModelWith(transport))
        composeRule.onNodeWithTag("app_tab_ai").performClick()
        composeRule.waitForIdle()

        repeat(2) {
            composeRule.onNodeWithTag("ai_feature_sentence-analysis").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("sentence_analysis_screen").assertExists()
            pressSystemBack()
            composeRule.waitForIdle()
        }

        assertEquals(
            "开/关子页各两次共四段转场，出站必须为 0（转场只搬画面，不替用户重发请求）：${transport.requests}",
            0,
            transport.requests.size,
        )
    }

    /** 判据 5：冷启动首帧不播。 */
    @Test
    fun firstCompositionDoesNotPlayATransition() {
        composeRule.mainClock.autoAdvance = false
        renderReadyAppOnLearningTab()   // 内部 setContent 后立刻取帧
        assertEquals(1, composeRule.onAllNodesWithTag("tab_transition_leg").fetchSemanticsNodes().size)
        composeRule.mainClock.autoAdvance = true
    }

    // ---------- 宿主与夹具（形状逐字来自本包既有 connected 类，不是新造） ----------

    /** 最小可跑宿主：`AppScreenOverlayContainerTest.kt:110-117` 的四参形状 + 判据 4 需要的长难句 VM。 */
    private fun renderAppScreen(sentenceAnalysisViewModel: SentenceAnalysisViewModel? = null) {
        val repository = InMemoryLocalProfileRepository()
        val clock = FixedClockProvider(Instant.EPOCH, ZoneOffset.UTC)
        composeRule.setContent {
            AppScreen(
                // 与 AppScreenTest.kt:2623-2625 同形：AppViewModel 与 CreateLocalProfileUseCase 共用一份资料仓库，
                // 否则 createProfile() 建完的资料读不回来，宿主永远停在「建资料」那一屏。
                viewModel = AppViewModel(
                    repository,
                    CreateLocalProfileUseCase(repository, clock),
                    AndroidTestHealthyDatabaseStartupProbe,
                ),
                learningSetupViewModel = setupViewModel(),
                todayPlanViewModel = TodayPlanViewModel({ placeholderCardPlan() }, FakeLearningProfileRepository()),
                wordCardViewModel = wordCardFixtureViewModel(),
                sentenceAnalysisViewModel = sentenceAnalysisViewModel,
            )
        }
        createProfile()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("app_tab_learning").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun renderReadyAppOnLearningTab() = renderAppScreen()

    private fun renderReadyAppOnProfileTab() {
        renderAppScreen()
        composeRule.onNodeWithTag("app_tab_profile").performClick()
        composeRule.waitForIdle()
    }

    /** 建资料两步，逐字取自 `AppScreenOverlayContainerTest.kt:436-439`。 */
    private fun createProfile() {
        composeRule.onNodeWithContentDescription("姓名输入").assertExists().performTextInput("学习者")
        composeRule.onNodeWithContentDescription("创建资料").assertExists().performClick()
    }

    /** 系统返回键，逐字取自 `AppScreenOverlayContainerTest.kt:493-506`（先等窗口有焦点再发键）。 */
    private fun pressSystemBack() {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            var focused = false
            instrumentation.runOnMainSync {
                focused = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .any { it.hasWindowFocus() }
            }
            focused
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    /** 学习设置 VM：形状取自 `AppScreenTest.kt:2937-2953`（`test-book` 必须声明为内置册，否则可见性策略把它滤掉）。 */
    private fun setupViewModel(): LearningSetupViewModel {
        val repository = FakeLearningProfileRepository()
        val settingsRepository = FakeLearningSettingsRepository()
        return LearningSetupViewModel(
            repository,
            SeedWordBooksUseCase(
                { "[{\"id\":\"test-book\",\"displayName\":\"测试词书\",\"level\":\"Test\",\"totalWords\":1,\"dataVersion\":\"v1\",\"sourceId\":\"ngsl-nawl-1.2\",\"sourcePolicy\":\"应用内学习分组，不是官方考试大纲词表。词条尚未随本任务打包。\"}]" },
                repository,
            ),
            SelectWordBookAndSetDailyTargetUseCase(repository),
            GetLearningSettingsUseCase(settingsRepository),
            SaveLearningSettingsUseCase(settingsRepository),
            bundledIds = BundledWordBookIdSource { setOf("test-book") },
        )
    }

    /**
     * 出站计数假件：形状照 `AppScreenTest.kt:1949-1951` 的匿名 transport，
     * 只把 `error(...)` 换成「记下 + 回 `NetworkUnavailable`」（不编造能过解析的响应体）。
     */
    private class CountingAiHttpTransport : AiHttpTransport {
        val requests = mutableListOf<AiHttpRequest>()

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            requests += request
            return AiHttpResult.NetworkUnavailable
        }
    }

    /** 长难句 VM 夹具：配置齐全（Selected + 有 Key），所以「多出来的那次出站」必然落到计数假件上。 */
    private fun sentenceAnalysisViewModelWith(
        transport: AiHttpTransport,
    ): SentenceAnalysisViewModel {
        val profile = AiProfile(
            profileId = "transition",
            displayName = "转场配置",
            websiteUrl = "https://example.com",
            endpoint = "https://api.example.com/v1",
            model = "gpt-test",
            capabilities = setOf(AiCapability.Text),
            secretReference = AiProfileSecretUseCase.referenceFor("transition"),
        )
        val secrets = AiProfileSecretUseCase(object : SecretStore {
            override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
            override fun read(reference: SecretReference) = Result.success(charArrayOf('k'))
            override fun delete(reference: SecretReference) = Result.success(Unit)
            override fun has(reference: SecretReference) = Result.success(true)
        })
        return SentenceAnalysisViewModel(
            com.example.englishlearning.sentence.SentenceAnalysisUseCase(
                DefaultTextProfileResolver { DefaultTextProfileResult.Selected(profile) },
                secrets,
                transport,
            ),
            com.example.englishlearning.ai.AiOutboundGrantStore(),
        )
    }
}
```

`DefaultTextProfileResolver` 是 `fun interface`（`ai/DefaultTextProfileSelector.kt:14`），
这里按 `AppScreenTest.kt:1960` 的同款显式 SAM 写法，不写裸 lambda。

四条断言（判据 1/2/3/5）改码前必须红，且红的必须是「节点不存在 / 只有一腿」而不是超时；
判据 4 是防回归闸，改码前后都该绿——**这一点必须在报告里写明，不许拿它冒充 RED**。
若 150ms 那一帧不可控（决策 §4.4 退路），退到「`waitForIdle` 之前用 `waitUntil` 断言曾出现 2 腿 + settle 后 1 腿」，
即放弃毫秒级帧号、仍证明两腿共存；退路与读数如实写进报告，不得拿 settle 断言冒充中间帧断言。

- [ ] **Step 2：RED——`MotionSpecContractTest` 锚点改成规格宿主计数**（决策 §4.3：必须先在旧实现上红一次，红点是「`theme/AppTransition.kt` 必须存在且含 `tween(`」）

```kotlin
    @Test
    fun `transition specs live in exactly one host file`() {
        val transitionHost = File(UI_THEME_SOURCE_PATH + "/AppTransition.kt")
        assertTrue(transitionHost.isFile, "批 D 的位移/时长规格唯一落点缺失：${transitionHost.path}")
        val stripped = stripComments(transitionHost.readText())
        assertTrue(
            Regex("""(?<![A-Za-z0-9_])tween\(""").containsMatchIn(stripped),
            "AppTransition.kt 必须承载 tween( 规格，否则「唯一权威」是空文件",
        )
    }
```

- [ ] **Step 3：跑 RED**（Step 1 的 connected 四条 + Step 2 的一条 + D-3 契约闸新增条），逐条记录红点原文。

- [ ] **Step 4：写 `AppTransition.kt`（全量）**

```kotlin
package com.example.englishlearning.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.example.englishlearning.ui.ScreenTransitionDirection

/**
 * 页面转场规格表：全仓**唯一**的转场时长/缓动/位移落点（批 D-3，决策 §3.3）。
 *
 * 值全部取 `AppMotion` token，页面腿 `Slow` 300 + 入 `EasingOut` / 退 `EasingIn`；
 * 位移取 **¼ 幅不是整幅**：300ms 走完整屏会读成「甩」，¼ 幅保住方向感又不让人等（Material 轴向惯例 ⅛–¼）。
 * 腿本身 `fillMaxSize`，所以 `slideInHorizontally { it / 4 }` 的 `it` 就是全屏宽；
 * 本文件不用 `slideIntoContainer`——它按整容器宽度位移，与 ¼ 幅口径不符。
 *
 * 对话框（`DialogEnter` / `DialogExit`）的可见形态逐字保持 `GlassOverlay` 今天的样子（淡入 + 92% 缩放、无位移）：
 * 浮层不是页面，给它加位移会误导层级。**两站之外不留 per-screen 逃生口**：
 * 不提供「某屏自己传 spec」的参数，逐屏自定义就是长出第二份规格。
 */
object AppTransition {
    private const val PageOffsetDenominator = 4

    val PageForwardEnter = fadeIn(tween(AppMotion.Slow, easing = AppMotion.EasingOut)) +
        slideInHorizontally(tween(AppMotion.Slow, easing = AppMotion.EasingOut)) { it / PageOffsetDenominator }
    val PageForwardExit = fadeOut(tween(AppMotion.Slow, easing = AppMotion.EasingIn)) +
        slideOutHorizontally(tween(AppMotion.Slow, easing = AppMotion.EasingIn)) { -it / PageOffsetDenominator }
    val PageBackwardEnter = fadeIn(tween(AppMotion.Slow, easing = AppMotion.EasingOut)) +
        slideInHorizontally(tween(AppMotion.Slow, easing = AppMotion.EasingOut)) { -it / PageOffsetDenominator }
    val PageBackwardExit = fadeOut(tween(AppMotion.Slow, easing = AppMotion.EasingIn)) +
        slideOutHorizontally(tween(AppMotion.Slow, easing = AppMotion.EasingIn)) { it / PageOffsetDenominator }

    /** 同层互换（同支换 payload，如词卡详情点「下一个」）只做淡入淡出，不假装判得出方向。 */
    val CrossfadeEnter = fadeIn(tween(AppMotion.Slow, easing = AppMotion.EasingOut))
    val CrossfadeExit = fadeOut(tween(AppMotion.Slow, easing = AppMotion.EasingIn))

    val DialogEnter = fadeIn(tween(AppMotion.Normal, easing = AppMotion.EasingOut)) +
        scaleIn(tween(AppMotion.Normal, easing = AppMotion.EasingOut), initialScale = 0.92f)
    val DialogExit = fadeOut(tween(AppMotion.Normal, easing = AppMotion.EasingIn)) +
        scaleOut(tween(AppMotion.Normal, easing = AppMotion.EasingIn), targetScale = 0.92f)

    /**
     * 两站共用同一张表：tab 站的「序号增大 = 新页从右进」与子页站的「更深 = 新页从右进」
     * 是同一条口径（决策 §3.3 里 tab 行与 page 行取值逐字相同），所以不复制第二套规格。
     */
    fun contentTransform(direction: ScreenTransitionDirection): ContentTransform = when (direction) {
        ScreenTransitionDirection.Forward -> PageForwardEnter togetherWith PageForwardExit
        ScreenTransitionDirection.Backward -> PageBackwardEnter togetherWith PageBackwardExit
        ScreenTransitionDirection.Crossfade -> CrossfadeEnter togetherWith CrossfadeExit
    }
}
```

> 上面 12 枚 import 全部按实测写：`fadeIn` / `fadeOut` / `scaleIn` / `scaleOut` / `slideInHorizontally` /
> `slideOutHorizontally` 都由 `androidx.compose.animation.EnterExitTransitionKt` 声明、`ContentTransform`
> 与 `togetherWith` 由 `androidx.compose.animation.AnimatedContentKt` 声明（本批 `javap` 过，见决策 §2），
> 标量动画 `tween` 在 `animation-core`。`val PageForwardEnter` 等六枚若被编译器要求显式类型，
> 补 `: EnterTransition` / `: ExitTransition`（`plus` 的返回类型就是 `EnterTransition`），**不改值**。
>
> `theme/AppTransition.kt` 里唯一一处向 `ui` 包的反向 import 是 `ScreenTransitionDirection`：
> 规格表需要按方向挑一对 transition，而方向属于路由判据、不该搬进 theme。
> 这是有意的一次跨包，不是随手抄的——若评审认为不可接受，改法是把 `contentTransform()` 上提到
> `ui/ScreenTransitionDirection.kt` 里（theme 只留六枚具名 transition），规格落点仍然唯一。

- [ ] **Step 5：站 1——包住整条链**（D-2 之后链体文字不动，只加外层；**同时删掉 D-2 留下的那行 `val route = overlayRoute`**，`route` 此后只由 lambda 入参提供——留着它，退场腿会读外层新值、渲染成新屏，转场看起来就像「只有一屏在淡」）

```kotlin
            AnimatedContent(
                targetState = overlayRoute,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    AppTransition.contentTransform(
                        ScreenTransitionDirectionPolicy.overlay(from = initialState, to = targetState),
                    )
                },
            ) { route ->
                OverlayLeg(route == overlayRoute) {
                    // ← 花括号里就是 D-2 之后那条 35 支链的**原文**：`if (route is AppOverlayRoute.VocabularyDetail) { … }
                    // else if (route == AppOverlayRoute.GlobalSearch) { … } … else if (route == null) { …tab 宿主… }`
                    // 唯一变化是整块右移一级缩进（`git diff -w` 应当只有缩进与首行，没有一条判据文字改动）。
                    // 分支体内部一律不动：payload 从 route 取（D-2 的 payload 表），其余逐字保留。
                }
            }
```

- [ ] **Step 6：腿容器（两站共用一枚私有 composable）**——`isExitingLeg` 的算法就是「这一腿自己的 route ≠ 当前目标」；lambda 入参是腿自己的状态，`overlayRoute` 是当前目标，两者不等 ⇒ 这腿正在退场。

```kotlin
/** 转场腿根容器：腿标签给 connected 判据数腿，退场腿对 TalkBack 隐身（决策 §3.4）。 */
@Composable
private fun OverlayLeg(isCurrentTarget: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("overlay_transition_leg")
            .then(
                if (isCurrentTarget) Modifier
                else Modifier.semantics { invisibleToUser() },
            ),
    ) { content() }
}
```

  生产写法只此一种：`ui-android:1.10.0` 没有 `Modifier.invisibleToUser()`，`invisibleToUser` 是
  `SemanticsPropertyReceiver` 扩展（决策 §2 实测）。入场腿语义零改动；位移只走 `graphicsLayer`，不改测量与布局。
  站 2 的同形状容器（`TabLeg`）连代码一起放在 Step 7，不在这里重复一遍。

- [ ] **Step 7：站 2——`AppScreen.kt:2061` 的 `when (selectedTab)` 换成 `AnimatedContent(targetState = selectedTab)`**

`when (tab)` 的四支内容与外层 `Scaffold` / 侧栏 / `contentMaxWidth` 两级 `Box` **一字不动**（批 B 口径：窗口类别只在宿主判一次），
只把 `AppScreen.kt:2061` 那一枚 `when (selectedTab) { … }` 换成下面这一坨，并在它下面补一枚与 `OverlayLeg` 同形状的 `TabLeg`：

```kotlin
                                        AnimatedContent(
                                            targetState = selectedTab,
                                            modifier = Modifier.fillMaxSize(),
                                            transitionSpec = {
                                                AppTransition.contentTransform(
                                                    ScreenTransitionDirectionPolicy.tab(
                                                        from = initialState,
                                                        to = targetState,
                                                    ),
                                                )
                                            },
                                        ) { tab ->
                                            TabLeg(tab == selectedTab) {
                                                when (tab) {
                                                    AppTab.LEARNING -> TodayPlanScreen(…)
                                                    AppTab.READING -> ReadingAccessScreen(…)
                                                    AppTab.AI -> AiLearningScreen(…)
                                                    AppTab.PROFILE -> ProfileScreen(…)
                                                }
                                            }
                                        }
```

（`when (tab)` 四支的实参逐字沿用今天 `AppScreen.kt:2062` 起的那四段，只把 `selectedTab` 换成 lambda 入参 `tab`；
底栏与侧栏写的是同一个 `selectedTab`，所以 `targetState` 与 `tab == selectedTab` 的判据天然一致。）

`AppScreen.kt` 内新增（与 Step 6 的 `OverlayLeg` 并列，形状逐字相同、只有 tag 不同）：

```kotlin
/** 站 2 的转场腿根容器：判据与 `OverlayLeg` 同一条，只是 tag 归 tab 站。 */
@Composable
private fun TabLeg(isCurrentTarget: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tab_transition_leg")
            .then(
                if (isCurrentTarget) Modifier
                else Modifier.semantics { invisibleToUser() },
            ),
    ) { content() }
}
```

- [ ] **Step 8：`GlassOverlay.kt:58`–`64` 并表**（形态不动，只把四枚 `tween(` 换成 `AppTransition.DialogEnter` / `DialogExit`）。

- [ ] **Step 9：契约闸新增三条**（`ScreenTransitionContractTest`）
  ① 站点数 == 2：`AppScreen.kt` 剥注释后 `AnimatedContent(` 恰好 2 次，且两处 `transitionSpec` 都调 `AppTransition.contentTransform`；
  ② `AppMotion.Slow` 的生产读点只允许在 `theme/AppTransition.kt`（`grep` 全 `ui` 子树，白名单一条）；
  ③ 位移口径：`AppTransition.kt` 里 `slideInHorizontally` / `slideOutHorizontally` 的 lambda 必须是 `/ 4`，出现整幅位移（`{ it }`）即判红；
  ④ 合成双边：假源码把 `tween(AppMotion.Slow)` 写在屏体里必须被判出；`overlayOpen` 那行必须被放行。

- [ ] **Step 10：GREEN——门禁三连 + Step 1 的 connected 四条转绿**，`AppScreenTest` 全类仍 `OK (66 tests)`；若出现「同一文本两屏各一份 ⇒ `onNodeWithText` 歧义红」，先确认测试点在 `waitForIdle()` 之后，不许把转场时长调小来迁就测试。

- [ ] **Step 11：Commit** — `feat(ui): 批 D-3 两站 AnimatedContent 转场与 AppTransition 规格表`

---

### D-4：取证、矩阵登记与收口

**Files:**
- Modify: `docs/verification/ac-traceability-matrix.md`（新增 AC5-07 行 + 补上批 C 漏登记的 AC5-06 行）
- Modify: `docs/verification/ui-redesign-2026-10.md`（续写批 D 读数表）
- Create: `docs/handoff/2026-11-09-batch-d-page-transition-report.md`（按 `docs/handoff/REPORT-TEMPLATE.md` 七节）

- [ ] **Step 1：三连门禁**（JVM 基线现算写进报告 §3，对照式：`336 类 / 2494 条` + 本批新增，0 失败 0 跳过）
- [ ] **Step 2：双形态 connected**——手机竖屏（Compact）与平板形窗口（Wide）各跑 `AppScreenTest` + `ScreenTransitionInstrumentedTest` + `RecoveryScreenTest`，两侧末行 `OK (n tests)` 原文入册。
- [ ] **Step 3：截图册**——`animator_duration_scale=1` 的转场中间帧（¼ 幅位移可见）与 `=0` 的定格帧各一册，落 `.git/qa-scratch/`（不入库）；读数表记位移像素、时长、两屏共存帧数。
- [ ] **Step 4：`gfxinfo` 掉帧对照**——转场前后各 5 次 tab/子页切换的 `adb shell dumpsys gfxinfo <pkg>` 读数（Janky frames%、50/90/95th 百分位），前后对比入册。
- [ ] **Step 5：真机 `bf353dda` 里程碑装机目视**（用户执行）——TalkBack 是否只读一屏、转场跟不跟手、底栏随屏滑走（决策 §3.4.1 的明示变化）能否接受。装机只用 `adb install -r`，不跑 instrumented。
- [ ] **Step 6：矩阵登记**——AC5-07 行（证据 = `AppOverlayRouteResolverTest`、`ScreenTransitionDirectionTest`、`ScreenTransitionContractTest`、`ScreenTransitionInstrumentedTest`、决策文档路径）；同批补 AC5-06 行（批 C 证据已在 `docs/verification/`）。跑 `"$GRADLE" verifyAcTraceabilityMatrix`（该任务只校 AC0–AC4，AC5 段是人工追踪，如实写明）。
- [ ] **Step 7：secret-scan** `scripts/secret-scan.sh` exit 0。
- [ ] **Step 8：报告七节** → 分功能域提交 → **推送请示**（用户点头后才 `git -c credential.interactive=false push origin stage-5-ui-r2-learning-path`，并 `ls-remote` 对 `rev-parse` 核验，远端 tip 回填报告 §4）。

## 台账

进度与裁定记在 `.superpowers/sdd/2026-11-06-motion-and-adaptive/progress.md`（gitignored，接手先读它）；证据表续写 `docs/verification/ui-redesign-2026-10.md`；截图与日志一律落 `.git/qa-scratch/`，不入库。
