package com.example.englishlearning.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
 * 整棵子树的 token 与 M3 配色同时换套。`themeMode` 实参由调用方提供：生产接线（`MainActivity`）
 * 传 [rememberAppThemeMode] 的 prefs 回读，同样是组合期状态，改偏好即翻色。
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
            // 根内容墨：AppScreen 一类页面以 `Modifier.background(MintBackground)` 直接铺页底，
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
 * 2026-10-29 原型对齐批 0：角色表整体改映到原型 token——
 * `primary` = `brandDeep`（选中态、开关、主按钮墨），`primaryContainer` = `brandTint`，
 * `error` / `errorContainer` = 原型 `danger` / `dangerTint`，`outline` 取 `text3`（控件边界，
 * 压 `surface` 实测 4.87，过 WCAG 1.4.11 的 3:1），`outlineVariant` 取装饰细线 `outline`。
 * 浅深两分支共用同一张表：原型 `--danger-tint` 两套都已按各自底调过，不再需要
 * `onErrorContainer` 分叉（旧的 `errorFill` 极性补偿随之作废）。
 *
 * 分支漂移由契约压死：`MaterialThemeAssemblyContractTest` 把两个分支的具名实参各自与同一张共享
 * 取值表逐字比对，并要求两者逐项相同，深浅两套不可能各自漂移。
 *
 * 域色不进 `ColorScheme`：各功能域一律走 [appDomain] 直读，避免 M3 角色表和域色表长出第二套真值。
 */
@Composable
fun appColorScheme(dark: Boolean): ColorScheme {
    val set = if (dark) AppColorDark else AppColorLight
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = set.brandDeep,
        onPrimary = set.surface,
        primaryContainer = set.brandTint,
        onPrimaryContainer = set.brandDeep,
        secondary = set.text2,
        onSecondary = set.surface,
        secondaryContainer = set.brandTint,
        onSecondaryContainer = set.brandDeep,
        tertiary = set.text1,
        onTertiary = set.surface,
        tertiaryContainer = set.tint2,
        onTertiaryContainer = set.text1,
        error = set.error,
        onError = set.surface,
        errorContainer = set.dangerTint,
        onErrorContainer = set.error,
        background = set.background,
        onBackground = set.text1,
        surface = set.surface,
        onSurface = set.text1,
        surfaceVariant = set.tint,
        onSurfaceVariant = set.text2,
        surfaceTint = set.brand,
        inverseSurface = set.strongSurface,
        inverseOnSurface = set.onStrong,
        inversePrimary = set.brand,
        outline = set.text3,
        outlineVariant = set.outline,
        surfaceContainerLowest = set.surface,
        surfaceContainerLow = set.surface,
        surfaceContainer = set.surface,
        surfaceContainerHigh = set.surface2,
        surfaceContainerHighest = set.tint2,
        surfaceBright = set.surface,
        surfaceDim = set.surface2,
    )
}

/**
 * M3 `Shapes` 五槽对位原型圆角档：10dp → `extraSmall`、按钮 14dp → `small`、
 * 卡片 20dp → `medium`、对话框/面板/强卡 26dp → `large` 与 `extraLarge`。
 * `AppShape.Pill`（50%）不是 M3 槽位形状，组件继续按名直读。
 */
val AppShapes: Shapes = Shapes(
    extraSmall = AppShape.Small,
    small = AppShape.Button,
    medium = AppShape.Card,
    large = AppShape.Dialog,
    extraLarge = AppShape.Dialog,
)
