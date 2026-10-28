package com.example.englishlearning.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 原型对齐批 0 的中性 token（浅 + 深两套）与七域强调色两套映射。
 *
 * 取值来自冻结 HTML 原型 `assets/app.css` 的 `:root` / `html[data-theme='dark']`，
 * 映射表与兼容策略见 `docs/decisions/2026-10-29-ui-prototype-token-alignment.md`，
 * 规格副本见 spec §2.1（`docs/superpowers/specs/2026-10-17-ui-redesign-design.md`）。
 *
 * 本文件是这些 hex 的权威源（`AppPalette.kt` 为浅套静态兼容源，取值由
 * `PrototypeTokenContractTest` 同步钉住）。配对合法性（WCAG 正文 ≥4.5、图形 ≥3.0）
 * 不在这里抄录——契约测试从这些字面量当场按相对亮度复算。
 *
 * 五个**迁移期兼容字段**（`surfaceGlass` / `tint3` / `onMint` / `onBrand` / `errorFill`）
 * 保留是为了让尚未逐屏迁移的 57 个界面继续编译；批 1–3 逐屏改用原型 token，批 4 删除。
 */

/** 28 枚中性 token（23 原型 + 5 迁移期兼容）；字段名即 camelCase token 名。 */
data class AppColorSet(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    /** 迁移期兼容：原型导航药片用 `surface` 88% + 模糊；批 3 换掉后删除。 */
    val surfaceGlass: Color,
    val tint: Color,
    val tint2: Color,
    /** 迁移期兼容：原型无 tint-3，暂取 `brandTint`。 */
    val tint3: Color,
    val outline: Color,
    val outlineSoft: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val brand: Color,
    val brandDeep: Color,
    val brandTint: Color,
    /** 迁移期兼容：薄荷底文字改取 `brandDeep`。 */
    val onMint: Color,
    /** 迁移期兼容：品牌渐变上的墨（浅深同值，原型渐变未改）。 */
    val onBrand: Color,
    val error: Color,
    /** 迁移期兼容：实心危险填充改取 `error` / `dangerTint` 组合。 */
    val errorFill: Color,
    val dangerTint: Color,
    val success: Color,
    val feedbackAgain: Color,
    val feedbackFuzzy: Color,
    val feedbackKnown: Color,
    /** 强档卡容器底（学习页主卡 / 我的页统计条）：两套都是深底，前景配 [onStrong]。 */
    val strongSurface: Color,
    /** 主按钮按压态底（原型 `--strong-hi`）。 */
    val strongSurfaceHigh: Color,
    /** 强档卡前景：明暗同值，压两套 strongSurface 均 ≥4.5。 */
    val onStrong: Color,
    /** 强档卡次级文字（原型 `--on-strong-2`，74%/72% alpha）。 */
    val onStrongMuted: Color,
)

/** 浅套中性 token（原型 `:root` 逐值）。 */
val AppColorLight = AppColorSet(
    background = Color(0xFFF1F4EF),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF7F9F5),
    surfaceGlass = Color(0xE0FFFFFF),
    tint = Color(0xFFE8F2EA),
    tint2 = Color(0xFFDCEBE1),
    tint3 = Color(0xFFE4F4EC),
    outline = Color(0xFFDDE5DD),
    outlineSoft = Color(0xFFEEF2ED),
    text1 = Color(0xFF15211C),
    text2 = Color(0xFF55665D),
    text3 = Color(0xFF65756C),
    brand = Color(0xFF6CCBAB),
    brandDeep = Color(0xFF1A7A63),
    brandTint = Color(0xFFE4F4EC),
    onMint = Color(0xFF1A7A63),
    onBrand = Color(0xFF04241C),
    error = Color(0xFFA5291F),
    errorFill = Color(0xFFFF3B30),
    dangerTint = Color(0xFFFDEEEC),
    success = Color(0xFF1B5E20),
    feedbackAgain = Color(0xFFB3573F),
    feedbackFuzzy = Color(0xFFA8792A),
    feedbackKnown = Color(0xFF1A7A63),
    strongSurface = Color(0xFF1F4739),
    strongSurfaceHigh = Color(0xFF2A5A49),
    onStrong = Color(0xFFE9F2EC),
    onStrongMuted = Color(0xBDE9F2EC),
)

/** 深套中性 token（原型 `html[data-theme='dark']` 逐值）。 */
val AppColorDark = AppColorSet(
    background = Color(0xFF101814),
    surface = Color(0xFF161F1A),
    surface2 = Color(0xFF1A2420),
    surfaceGlass = Color(0xE0161F1A),
    tint = Color(0xFF1C2A24),
    tint2 = Color(0xFF22322B),
    tint3 = Color(0xFF1D322B),
    outline = Color(0xFF2A3931),
    outlineSoft = Color(0xFF202D27),
    text1 = Color(0xFFE9F2EC),
    text2 = Color(0xFFA5B8AE),
    text3 = Color(0xFF93A69C),
    brand = Color(0xFF6FD9B6),
    brandDeep = Color(0xFF7FE3C1),
    brandTint = Color(0xFF1D322B),
    onMint = Color(0xFF7FE3C1),
    onBrand = Color(0xFF04241C),
    error = Color(0xFFFF8177),
    errorFill = Color(0xFFFF6B61),
    dangerTint = Color(0xFF34211F),
    success = Color(0xFF6BD98A),
    feedbackAgain = Color(0xFFE39086),
    feedbackFuzzy = Color(0xFFEFCB92),
    feedbackKnown = Color(0xFF7FE3C1),
    strongSurface = Color(0xFF22332C),
    strongSurfaceHigh = Color(0xFF2B4038),
    onStrong = Color(0xFFE9F2EC),
    onStrongMuted = Color(0xB8E9F2EC),
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
