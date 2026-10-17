package com.example.englishlearning.ui

import com.example.englishlearning.ui.theme.ThemeMode
import com.example.englishlearning.ui.theme.shouldUseDarkTheme
import com.example.englishlearning.ui.theme.themeModeFromStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 主题三态判定的纯函数用例（计划 Task 4 Step 4 交付，Task 5 的 prefs 读写复用同一批符号）。
 *
 * 为什么值得单独钉：三态里「跟随系统」是唯一的间接分支，另两态必须无视平台；存储键回退口径
 * 一旦放宽（大小写归一、trim），就会出现「两种写法都合法但只有一种写得出」的漂移，
 * 所以非法值一律回退 `System` 是有意为之的严格，而不是缺失的宽容。
 */
class ThemeModeDecisionTest {

    @Test
    fun `system mode follows the platform`() {
        assertTrue(shouldUseDarkTheme(ThemeMode.System, systemInDark = true))
        assertFalse(shouldUseDarkTheme(ThemeMode.System, systemInDark = false))
    }

    @Test
    fun `explicit light and dark ignore the platform`() {
        assertFalse(shouldUseDarkTheme(ThemeMode.Light, systemInDark = true))
        assertTrue(shouldUseDarkTheme(ThemeMode.Dark, systemInDark = false))
    }

    @Test
    fun `storage round-trips every mode`() {
        ThemeMode.entries.forEach { mode -> assertEquals(mode, themeModeFromStorage(mode.storageKey)) }
    }

    @Test
    fun `blank, null and unknown storage values fall back to system`() {
        listOf(null, "", "darkish", "LIGHT", "  ").forEach { raw ->
            assertEquals(ThemeMode.System, themeModeFromStorage(raw), "raw=$raw 应回退 System")
        }
    }
}
