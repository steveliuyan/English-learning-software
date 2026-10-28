package com.example.englishlearning.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 批 1 设计系统中性 token（浅 + 深两套）与七域强调色两套映射。
 *
 * 取值来自 spec §2.1（`docs/superpowers/specs/2026-10-17-ui-redesign-design.md`）与决策
 * 2026-10-18 裁决 1–3（`docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`）。
 *
 * **2026-10-27 轻松档改色**（决策 `docs/decisions/2026-10-27-relaxed-palette-rulings.md`）：
 * 中性 18 枚与七域域色整体降到「低压力」色阶——背景去黄绿偏中性、`tint` 系去饱和、
 * 七域 base 由 iOS 高饱和亮色改为柔化色（AiSpeech `/2EC99C`→`/6CCBAB`、Learn `0A84FF`→`7BB4E6` 等），
 * 域 deep 同时压深以保证「浅底文字 ≥4.5、白字形压容器 ≥3:1」仍成立。
 * 品牌渐变 `AppleMint*` 四枚**未动**（spec §2.1「品牌渐变保持现状不动」）。
 * 改色依据与逐对实测见设计稿 §4.4（`docs/design/2026-10-25-icon-system-design-sheet.html`）。
 * 本文件是这些 hex 的权威源（`AppPalette.kt` 为浅套静态兼容源，取值由 DesignTokenContractTest
 * 同步钉住）。配对合法性（WCAG 正文 ≥4.5、图形 ≥3.0）不在这里抄录——契约测试从这些字面量
 * 当场按相对亮度复算；在此写「对比合格」类注释只会随取值漂移。
 *
 * 深色 `onBrand` 沿用浅套同值：品牌渐变深浅两套同三段（spec §2.1「品牌渐变保持现状不动」），
 * 裁决 3 只钉「品牌渐变上的文字统一取 on-brand #04241C」而未给深套另立值；深色渐变/深底前景
 * 的最终核验按 AC-R-11 真机实测走（决策文档「影响」节），跌破阈值时另立补充决策。
 */

/** 18 个中性 token；字段名即 camelCase token 名（对应 spec §2.1 的 kebab 名，如 `background`↔`bg`）。 */
data class AppColorSet(
    val background: Color,
    val surface: Color,
    val surfaceGlass: Color,
    val tint: Color,
    val tint2: Color,
    val tint3: Color,
    val outline: Color,
    val outlineSoft: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val onMint: Color,
    val onBrand: Color,
    val error: Color,
    val errorFill: Color,
    val success: Color,
    /** 强档卡容器底（学习页主卡 / 我的页统计条）：两套都是深底，前景配 [onStrong]。 */
    val strongSurface: Color,
    /** 强档卡前景：明暗同值，压两套 strongSurface 均 ≥4.5（§4.1）。 */
    val onStrong: Color,
)

/** 浅套中性 token（spec §2.1 浅色行；2026-10-27 轻松档取值）。 */
val AppColorLight = AppColorSet(
    background = Color(0xFFF6F9F6),
    surface = Color(0xFFFFFFFF),
    surfaceGlass = Color(0xEFFFFFFF),
    tint = Color(0xFFE9F4EE),
    tint2 = Color(0xFFE4EFE9),
    tint3 = Color(0xFFDEEAE4),
    outline = Color(0xFFD3E7DC),
    outlineSoft = Color(0xFFEBF1ED),
    text1 = Color(0xFF1F2B26),
    text2 = Color(0xFF566B62),
    text3 = Color(0xFF7C9188),
    onMint = Color(0xFF174C44),
    onBrand = Color(0xFF04241C),
    error = Color(0xFFB30000),
    errorFill = Color(0xFFFF3B30),
    success = Color(0xFF1B5E20),
    strongSurface = Color(0xFF1F4739),
    onStrong = Color(0xFFE9F2EC),
)

/** 深套中性 token（spec §2.1 深色行；`onBrand` 见文件头说明；2026-10-27 轻松档取值）。 */
val AppColorDark = AppColorSet(
    background = Color(0xFF111A16),
    surface = Color(0xFF17201C),
    surfaceGlass = Color(0xE617201C),
    tint = Color(0xFF1B2A25),
    tint2 = Color(0xFF20302A),
    tint3 = Color(0xFF25352E),
    outline = Color(0xFF2B3A33),
    outlineSoft = Color(0xFF202C26),
    text1 = Color(0xFFE9F2EC),
    text2 = Color(0xFF9FB3AA),
    text3 = Color(0xFF7F938A),
    onMint = Color(0xFFDFF6EC),
    onBrand = Color(0xFF04241C),
    error = Color(0xFFFF8177),
    errorFill = Color(0xFFFF6B61),
    success = Color(0xFF6BD98A),
    strongSurface = Color(0xFF22332C),
    onStrong = Color(0xFFE9F2EC),
)

/**
 * 七个功能域。条目名与 `AppPalette.kt` 的 `DomainColors` 属性名逐字一致——
 * 批 1 迁移按名字对位（计划 Task 2 Step 3），不得改成 AI/SPEECH 之类。
 */
enum class AppDomain { AiSpeech, Learn, Review, Library, Reading, Danger, Settings }

/** 域色对：base = 填充/图标/主按钮，deep = 浅底文字与按压态（与既有 `DomainAccent` 同形）。 */
data class DomainPalette(val base: Color, val deep: Color)

/** 浅套域色：与 `DomainColors` 逐值相同（契约测试双钉：改本表或改 DomainColors 都会红）。 */
val DomainLight: Map<AppDomain, DomainPalette> = mapOf(
    AppDomain.AiSpeech to DomainPalette(base = Color(0xFF6CCBAB), deep = Color(0xFF1A7A63)),
    AppDomain.Learn to DomainPalette(base = Color(0xFF7BB4E6), deep = Color(0xFF175A93)),
    AppDomain.Review to DomainPalette(base = Color(0xFFEBC68B), deep = Color(0xFF8A5712)),
    AppDomain.Library to DomainPalette(base = Color(0xFFB7A0D9), deep = Color(0xFF65478A)),
    AppDomain.Reading to DomainPalette(base = Color(0xFFE297A4), deep = Color(0xFF9C3B4F)),
    AppDomain.Danger to DomainPalette(base = Color(0xFFDD8B81), deep = Color(0xFF9E2B24)),
    AppDomain.Settings to DomainPalette(base = Color(0xFFA6B0AC), deep = Color(0xFF4A5551)),
)

/** 深套域色：deep = base（spec §2.1「深色下域色与 base 合一，避免暗底深字」）。 */
val DomainDark: Map<AppDomain, DomainPalette> = mapOf(
    AppDomain.AiSpeech to DomainPalette(base = Color(0xFF6FD9B6), deep = Color(0xFF6FD9B6)),
    AppDomain.Learn to DomainPalette(base = Color(0xFF84C4EE), deep = Color(0xFF84C4EE)),
    AppDomain.Review to DomainPalette(base = Color(0xFFEFCB92), deep = Color(0xFFEFCB92)),
    AppDomain.Library to DomainPalette(base = Color(0xFFC6A9E4), deep = Color(0xFFC6A9E4)),
    AppDomain.Reading to DomainPalette(base = Color(0xFFEDA8B4), deep = Color(0xFFEDA8B4)),
    AppDomain.Danger to DomainPalette(base = Color(0xFFE39086), deep = Color(0xFFE39086)),
    AppDomain.Settings to DomainPalette(base = Color(0xFFB4BEBA), deep = Color(0xFFB4BEBA)),
)

/**
 * 主题装配用 CompositionLocal（唯一提供者是 `MintGlassTheme.kt` 的装配点）。
 *
 * 取 `compositionLocalOf` 而不是 `staticCompositionLocalOf`：三态主题（跟随系统 / 浅色 / 深色）
 * 在设置页翻动时，`MaterialTheme` 装配不重建，而 `staticCompositionLocalOf` 的读取**不会失效重算**，
 * `MintPalette.kt` 的每一个 `Mint*` 组合期 getter 都会停在旧值——深色模式静默失效，
 * 「整树一次性切换」并不等于「读取可以不失效」。`compositionLocalOf` 多一次记录查找，
 * 换来的是能用的深色（计划 Task 3 实现现场发现，控制者裁决订正）。
 *
 * 静态类型必须写成 `ProvidableCompositionLocal<…>`（计划 Task 2 Interfaces 已订正）：`provides`
 * 是 `ProvidableCompositionLocal` 的成员，基类 `CompositionLocal` 只有 `current`；标注成基类
 * 就是刻意放宽静态类型，装配点的 `LocalAppColors provides set` 会当场编译不过。
 */
val LocalAppColors: ProvidableCompositionLocal<AppColorSet> = compositionLocalOf { AppColorLight }
val LocalDomainColors: ProvidableCompositionLocal<Map<AppDomain, DomainPalette>> = compositionLocalOf { DomainLight }
val LocalIsDark: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** 组合期读取当前主题中性 token 集。 */
@Composable
@ReadOnlyComposable
fun appColors(): AppColorSet = LocalAppColors.current

/** 组合期读取当前主题某功能域的 base/deep 对。 */
@Composable
@ReadOnlyComposable
fun appDomain(domain: AppDomain): DomainPalette = LocalDomainColors.current.getValue(domain)
