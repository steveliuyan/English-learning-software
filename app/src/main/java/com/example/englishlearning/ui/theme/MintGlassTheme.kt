package com.example.englishlearning.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/**
 * 全站唯一的 `MaterialTheme` 装配点，同时是 [LocalAppColors] / [LocalDomainColors] / [LocalIsDark]
 * 的唯一提供者，并在 `MaterialTheme` 之内把 [LocalContentColor] 显式提供为当前套的 `text1`（根内容墨）。
 *
 * 存在理由：本仓库此前没有任何 `MaterialTheme(...)` 调用，遍布 `ui/` 的
 * `MaterialTheme.typography.*` 与 `MaterialTheme.colorScheme.*` 读点直接吃 material3 默认值，
 * 深色模式没有落点。这里把排印、配色、圆角一次装配完，并把当前主题的中性 token 集与各功能域
 * 调色板注进 CompositionLocal——
 * `MintPalette.kt` 的组合期 `Mint*` getter 与 [appColors] / [appDomain] 都读这三个 Local。
 *
 * 翻色链路：[shouldUseDarkTheme] 的 `systemInDark` 实参来自 [isSystemInDarkTheme]（组合期状态
 * 读取），所以三态翻动会让本函数的 `dark` 重算，进而让 `compositionLocalOf` 的读取失效重算，
 * 整棵子树的 token 与 M3 配色同时换套（Task 5 把 `themeMode` 换成 `rememberAppThemeMode()` 的
 * prefs 回读，同样是组合期状态）。
 */
@Composable
fun MintGlassTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = shouldUseDarkTheme(themeMode, isSystemInDarkTheme())
    val set = if (dark) AppColorDark else AppColorLight
    val domains = if (dark) DomainDark else DomainLight
    CompositionLocalProvider(
        LocalAppColors provides set,
        LocalDomainColors provides domains,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = appColorScheme(dark),
            typography = appTypography(),
            shapes = AppShapes,
        ) {
            // 根内容墨：AppScreen 有三处 `Modifier.fillMaxSize().background(MintBackground)` 直接当页底，
            // 底下未指定 color 的 Text 走的是 LocalContentColor 默认值（Color.Black），
            // 浅套下近似 text1 看不出来，深套下就是黑压黑。这里显式给出当前主题的中性主墨，
            // 位置必须在 MaterialTheme 之内——material3 若自己也提供 LocalContentColor，外层会被它盖掉。
            // 本仓库解析到的 LocalContentColor 来自 androidx.compose.material3（foundation-android 1.10.0
            // 的 classes.jar 无此符号，现场核验过），import 跟着可解析的那一支走。
            CompositionLocalProvider(LocalContentColor provides set.text1, content = content)
        }
    }
}

/**
 * M3 `ColorScheme`：每个角色都取 [AppColorSet] 字段，本文件不出现裸 hex（全局约束 6）。
 *
 * 为什么是 `lightColorScheme(...)` / `darkColorScheme(...)` 两个分支而不是 `ColorScheme(...)`
 * 构造器：material3 1.4.0 的 `ColorScheme` 构造器每个参数都必传（同签名重载分别是浅层 31 项、
 * 容器 36 项、动态色 44 项，都没有默认值），单点构造就必须为 `scrim` 与 `*Fixed` 族另立 hex；
 * 这两个 builder 才带库内默认值，未在本表列出的角色（`scrim`、`*Fixed` 族、`secondaryContainer`
 * 以外的动态色）因此保持库默认——本批不给它们立新 token。
 *
 * 分支漂移由契约压死：`MaterialThemeAssemblyContractTest` 把两个分支的具名实参各自与同一张共享
 * 取值表逐字比对，并要求两者只差 `onErrorContainer` 一项，深浅两套不可能各自漂移。
 *
 * `primary` 与 `secondary` 同取 `onMint`：设计系统里「可点击前景」只有这一支薄荷深墨，压
 * `surface` / `background` 在深浅两套都在正文阈值以上（配对复算见
 * `MaterialThemeAssemblyContractTest`，按 WCAG 相对亮度当场算）。域色不进 `ColorScheme`：
 * 各功能域一律走 [appDomain] 直读，避免 M3 角色表和域色表长出第二套真值。
 *
 * `onErrorContainer` 是唯一按模式分叉的取值：`errorFill` 在浅套是深红、在深套是浅红，字形极性
 * 相反，所以浅套取 `text1`、深套取 `background`。M3 的 `outline` 槽是**控件边界**（OutlinedButton
 * 描边、Switch 未选中态描边…），受 WCAG 1.4.11 非文本 3:1 约束，故取 `text2`——它在浅套压
 * `surface` / `background` / 三种 tint 容器全部 ≥4.10、深套全部 ≥5.55，取值由契约的 `boundaryRoles`
 * 用例按 WCAG 相对亮度压**生产装配实参**当场复算；`AppColorSet.outline` 是装饰性细线，压各自套的
 * `surface` 只有 1.39（浅）/ 1.55（深），只由新组件直读，不进 M3 槽。`outlineVariant = set.outlineSoft`
 * 的理由（Divider 等装饰线）不变。
 */
@Composable
fun appColorScheme(dark: Boolean): ColorScheme {
    val set = if (dark) AppColorDark else AppColorLight
    return if (dark) {
        darkColorScheme(
            primary = set.onMint,
            onPrimary = set.background,
            primaryContainer = set.tint,
            onPrimaryContainer = set.onMint,
            secondary = set.onMint,
            onSecondary = set.background,
            secondaryContainer = set.tint3,
            onSecondaryContainer = set.text1,
            tertiary = set.text2,
            onTertiary = set.surface,
            tertiaryContainer = set.tint2,
            onTertiaryContainer = set.text1,
            error = set.error,
            onError = set.surface,
            errorContainer = set.errorFill,
            onErrorContainer = set.background,
            background = set.background,
            onBackground = set.text1,
            surface = set.surface,
            onSurface = set.text1,
            surfaceVariant = set.tint2,
            onSurfaceVariant = set.text1,
            surfaceTint = set.onMint,
            inverseSurface = set.text1,
            inverseOnSurface = set.surface,
            inversePrimary = set.tint,
            outline = set.text2,
            outlineVariant = set.outlineSoft,
            surfaceContainerLowest = set.surface,
            surfaceContainerLow = set.surface,
            surfaceContainer = set.surface,
            surfaceContainerHigh = set.tint2,
            surfaceContainerHighest = set.tint2,
            surfaceBright = set.surface,
            surfaceDim = set.tint2,
        )
    } else {
        lightColorScheme(
            primary = set.onMint,
            onPrimary = set.background,
            primaryContainer = set.tint,
            onPrimaryContainer = set.onMint,
            secondary = set.onMint,
            onSecondary = set.background,
            secondaryContainer = set.tint3,
            onSecondaryContainer = set.text1,
            tertiary = set.text2,
            onTertiary = set.surface,
            tertiaryContainer = set.tint2,
            onTertiaryContainer = set.text1,
            error = set.error,
            onError = set.surface,
            errorContainer = set.errorFill,
            onErrorContainer = set.text1,
            background = set.background,
            onBackground = set.text1,
            surface = set.surface,
            onSurface = set.text1,
            surfaceVariant = set.tint2,
            onSurfaceVariant = set.text1,
            surfaceTint = set.onMint,
            inverseSurface = set.text1,
            inverseOnSurface = set.surface,
            inversePrimary = set.tint,
            outline = set.text2,
            outlineVariant = set.outlineSoft,
            surfaceContainerLowest = set.surface,
            surfaceContainerLow = set.surface,
            surfaceContainer = set.surface,
            surfaceContainerHigh = set.tint2,
            surfaceContainerHighest = set.tint2,
            surfaceBright = set.surface,
            surfaceDim = set.tint2,
        )
    }
}

/**
 * 卡片档圆角的校准值：spec §2.3 钉「卡 18dp」，[AppShape.Card] 的旧值是 20dp。这里只动装配点
 * 的 M3 槽位，[AppShape] 的成员与取值一律不碰——直接读 `AppShape.Card` 的既有调用点留给逐屏
 * 迁移任务收口，不在装配点任务里批量重写。
 */
private val MediumCorner = RoundedCornerShape(18.dp)

/**
 * M3 `Shapes` 五槽对位 spec §2.3 的圆角档：icon 12dp → `extraSmall`、btn 14dp → `small`、
 * card 18dp → `medium`（校准值）、hero 24dp → `large` 与 `extraLarge`（spec 只有四档控件圆角，
 * M3 多出来的一档并入 hero，不引新字号以外的取值）。`AppShape.Pill`（50%）不是 M3 槽位形状，
 * 组件继续按名直读。
 */
val AppShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = AppShape.Button,
    medium = MediumCorner,
    large = AppShape.Dialog,
    extraLarge = AppShape.Dialog,
)
