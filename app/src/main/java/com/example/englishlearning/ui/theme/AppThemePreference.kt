package com.example.englishlearning.ui.theme

/**
 * 主题三态偏好的**纯逻辑**部分（计划 Task 4 Step 4 交付）：三态枚举、存储键、以及「翻不翻黑」判定。
 *
 * `AppThemePreferences` 的 prefs 读写与 `rememberAppThemeMode()` 由 Task 5 追加到本文件：
 * 显示偏好落 `getSharedPreferences`，不进 Room、不进备份包（它不是学习数据），所以本文件目前
 * 刻意不依赖 `Context`——三个符号因此可在 JVM 单测里把三态与回退口径钉死（`ThemeModeDecisionTest`）。
 *
 * 判定结果由装配点 [MintGlassTheme] 在组合期读取，是深色能生效的链条起点：三态翻动时
 * `LocalAppColors` / `LocalDomainColors` / `LocalIsDark` 的读取必须失效重算
 * （`AppColors.kt` 因此取 `compositionLocalOf` 而不是 `staticCompositionLocalOf`）。
 */
enum class ThemeMode(val storageKey: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
}

/** 三态里只有 `System` 跟随平台；`Light` / `Dark` 一律无视平台当前取值。 */
fun shouldUseDarkTheme(mode: ThemeMode, systemInDark: Boolean): Boolean = when (mode) {
    ThemeMode.System -> systemInDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** 存储键写死、大小写敏感：非法/空/未知值一律回退 [ThemeMode.System]，避免「两种写法都合法」的漂移。 */
fun themeModeFromStorage(raw: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.storageKey == raw } ?: ThemeMode.System
