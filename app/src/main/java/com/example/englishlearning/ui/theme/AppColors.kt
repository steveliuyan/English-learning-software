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

/** 浅套中性 token（spec §2.1 浅色行）。 */
val AppColorLight = AppColorSet(
    background = Color(0xFFF1FBF5),
    surface = Color(0xFFFFFFFF),
    surfaceGlass = Color(0xEFFFFFFF),
    tint = Color(0xFFDDF7E8),
    tint2 = Color(0xFFDCEAE1),
    tint3 = Color(0xFFCDEDD9),
    outline = Color(0xFFADE7D2),
    outlineSoft = Color(0xFFE4EFE9),
    text1 = Color(0xFF1C1C1E),
    text2 = Color(0xFF4E756A),
    text3 = Color(0xFF7A9088),
    onMint = Color(0xFF174C44),
    onBrand = Color(0xFF04241C),
    error = Color(0xFFB30000),
    errorFill = Color(0xFFFF3B30),
    success = Color(0xFF1B5E20),
    strongSurface = Color(0xFF0C2A22),
    onStrong = Color(0xFFE9F2EC),
)

/** 深套中性 token（spec §2.1 深色行；`onBrand` 见文件头说明）。 */
val AppColorDark = AppColorSet(
    background = Color(0xFF0E1512),
    surface = Color(0xFF17201C),
    surfaceGlass = Color(0xE617201C),
    tint = Color(0xFF1C2C26),
    tint2 = Color(0xFF22322B),
    tint3 = Color(0xFF27392F),
    outline = Color(0xFF2E4238),
    outlineSoft = Color(0xFF233029),
    text1 = Color(0xFFE9F2EC),
    text2 = Color(0xFF9FB3AA),
    text3 = Color(0xFF7F938A),
    onMint = Color(0xFFDFF6EC),
    onBrand = Color(0xFF04241C),
    error = Color(0xFFFF8177),
    errorFill = Color(0xFFFF6B61),
    success = Color(0xFF6BD98A),
    strongSurface = Color(0xFF1E2B25),
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
    AppDomain.AiSpeech to DomainPalette(base = Color(0xFF2EC99C), deep = Color(0xFF188F76)),
    AppDomain.Learn to DomainPalette(base = Color(0xFF0A84FF), deep = Color(0xFF075EB4)),
    AppDomain.Review to DomainPalette(base = Color(0xFFFF9F0A), deep = Color(0xFFAB5700)),
    AppDomain.Library to DomainPalette(base = Color(0xFFBF5AF2), deep = Color(0xFF8E34B8)),
    AppDomain.Reading to DomainPalette(base = Color(0xFFFF375F), deep = Color(0xFFC81E46)),
    AppDomain.Danger to DomainPalette(base = Color(0xFFFF3B30), deep = Color(0xFFB30000)),
    AppDomain.Settings to DomainPalette(base = Color(0xFF8E8E93), deep = Color(0xFF48484A)),
)

/** 深套域色：deep = base（spec §2.1「深色下域色与 base 合一，避免暗底深字」）。 */
val DomainDark: Map<AppDomain, DomainPalette> = mapOf(
    AppDomain.AiSpeech to DomainPalette(base = Color(0xFF35E0AC), deep = Color(0xFF35E0AC)),
    AppDomain.Learn to DomainPalette(base = Color(0xFF5AC0FF), deep = Color(0xFF5AC0FF)),
    AppDomain.Review to DomainPalette(base = Color(0xFFFFB340), deep = Color(0xFFFFB340)),
    AppDomain.Library to DomainPalette(base = Color(0xFFDA8CFF), deep = Color(0xFFDA8CFF)),
    AppDomain.Reading to DomainPalette(base = Color(0xFFFF7A93), deep = Color(0xFFFF7A93)),
    AppDomain.Danger to DomainPalette(base = Color(0xFFFF6B61), deep = Color(0xFFFF6B61)),
    AppDomain.Settings to DomainPalette(base = Color(0xFFB0B0B5), deep = Color(0xFFB0B0B5)),
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
