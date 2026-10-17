package com.example.englishlearning.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 主题三态偏好（跟随系统 / 浅色 / 深色）的唯一落点：纯逻辑 + prefs I/O + 组合期读取。
 *
 * 分两层是有意为之：
 * - [ThemeMode] / [shouldUseDarkTheme] / [themeModeFromStorage] 三个符号**不依赖 `Context`**，
 *   所以三态与回退口径能在 JVM 单测里钉死（`ThemeModeDecisionTest`，Task 4 交付）。
 * - [AppThemePreferences] 与 [rememberAppThemeMode] 需要 `Context`。本仓库 JVM 测试零 Android
 *   依赖（robolectric 在依赖表里但从未用过，不为一个 prefs 开新口径），所以读写与活链路
 *   由 `androidTest` 的 `ThemeModeSwitchTest` 在模拟器上以真像素覆盖。
 *
 * 翻色链路：判定结果由装配点 [MintGlassTheme] 在组合期读取——三态翻动时
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

/**
 * 主题三态偏好的 SharedPreferences 落点。
 *
 * 两条口径（spec §5.2 + 全局约束 5，改动前先读）：
 * - **不进 Room、不做 v33 迁移、不进备份包**：显示偏好不是学习数据，所以不借
 *   `LearningSettingsEntity`，也**不得**把 [PREFS_NAME] 加进任何备份文件清单或 `fileProvider` 路径；
 *   Room 版本号与本文件无关。
 * - **app 私有文件，卸载即失**：与 AGENTS.md「卸载、清除数据或设备损坏前未导出数据，可能无法恢复」
 *   一致——主题偏好属于可丢项，恢复备份后回到 [ThemeMode.System] 是预期行为，不是缺陷。
 *
 * 值形状只走 [ThemeMode.storageKey]（经 [themeModeFromStorage] 回读），本对象不写裸字符串字面量，
 * 避免「存储里出现第四种写法」。
 */
object AppThemePreferences {
    const val PREFS_NAME = "ui_display_prefs"
    const val KEY_THEME_MODE = "theme_mode"

    /** 回读：缺失、空串、未知或大小写不符的取值一律按 [ThemeMode.System] 处理（口径见 [themeModeFromStorage]）。 */
    fun read(context: Context): ThemeMode =
        themeModeFromStorage(preferences(context).getString(KEY_THEME_MODE, null))

    /** 落盘用 `apply()`：偏好改动不该阻塞调用线程，且下一次进程起来时磁盘值已足够。 */
    fun write(context: Context, mode: ThemeMode) {
        preferences(context).edit().putString(KEY_THEME_MODE, mode.storageKey).apply()
    }

    /**
     * 统一入口：`getSharedPreferences` 的实例按「包名 + 文件名」进程级缓存，
     * 所以这里取 applicationContext 只是避免泄漏 Activity，跨 Context 拿到的仍是同一个
     * [SharedPreferences] 对象——[rememberAppThemeMode] 的监听因此能收到别的站点写入的通知。
     */
    internal fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * 组合期读取当前主题三态偏好，并在偏好被改写时立即让读取方重算（不重建 `setContent`）。
 *
 * 活链路的每一环都不可省：
 * 1. 初值经 [AppThemePreferences.read] 回读，非法存储值因此仍走 [ThemeMode.System]；
 * 2. `DisposableEffect` 注册 [SharedPreferences.OnSharedPreferenceChangeListener]，
 *    离开组合时注销——监听是弱引用语义，注册方必须由组合持有（此处的 effect 就是持有者）；
 * 3. 回调线程是 SharedPreferences 的磁盘写线程，而 `mutableStateOf` 的写入必须在主线程才会
 *    立刻触发重组，所以经 `ContextCompat.getMainExecutor` 跳回主线程（minSdk 26，
 *    不能用 `Context.mainExecutor`，那是 API 28）；
 * 4. 状态一变，装配点 [MintGlassTheme] 的 `themeMode` 实参重算，`compositionLocalOf` 的读取失效，
 *    整棵子树换套。
 *
 * key 为 `null` 的回调（`clear()` 一类整体清空）同样重读，因此清空后回到 [ThemeMode.System]。
 */
@Composable
fun rememberAppThemeMode(): ThemeMode {
    val context = LocalContext.current
    val preferences = remember(context) { AppThemePreferences.preferences(context) }
    var mode by remember(preferences) { mutableStateOf(AppThemePreferences.read(context)) }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    DisposableEffect(preferences, mainExecutor) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key == null || key == AppThemePreferences.KEY_THEME_MODE) {
                mainExecutor.execute {
                    mode = themeModeFromStorage(changed.getString(AppThemePreferences.KEY_THEME_MODE, null))
                }
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return mode
}
