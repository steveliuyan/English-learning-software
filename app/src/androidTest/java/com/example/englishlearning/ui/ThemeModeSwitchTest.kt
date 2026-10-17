package com.example.englishlearning.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppThemePreferences
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.rememberAppThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主题三态的**真像素**证据（计划 Task 5 Step 3）：存储里的偏好必须一路走到装配点，
 * 最后落到屏幕像素上——`AppThemePreferences.write` → `read` → `rememberAppThemeMode()` →
 * `MintGlassTheme` 换套 → `LocalAppColors.background`。JVM 侧只能证判定函数
 * （`ThemeModeDecisionTest`），证不了 prefs 读写与组合期重算，所以本类只能在设备上跑。
 *
 * 两处形状决定，都不是随手写的：
 * - **不传字面 `ThemeMode.Dark`，而是先落 prefs 再经 `rememberAppThemeMode()` 回读**：
 *   计划 Step 3 的样例片段是直传枚举的，但那样本类的像素只证明装配点，`AppThemePreferences`
 *   与 `rememberAppThemeMode` 就成了无测试的生产码；Step 2 写明「读写由 Step 3 的 connected 用例覆盖」，
 *   故本类把链路接全，用例数仍是计划期望的两个方法。
 * - **两个方法各一次 `setContent`，绝不在同一方法里 `setContent` 两次**：本仓库已记录的怪癖
 *   （见 `android-connected-qa-loop`），同一次测试两次 `setContent` 真机必崩。
 *
 * 期望像素取 [AppColorDark] / [AppColorLight] 的 `background` token，不重抄 hex：
 * 本类要证的是「换套」这件事（选中了哪一套），token 自身的取值由 `DesignTokenContractTest` 钉死；
 * 在这里复述十六进制只会长出第二份真值并随 token 校准漂移。
 *
 * 只读偏好、只写偏好：本类不碰 Room、不碰备份清单（全局约束 5），
 * `@After` 把偏好清回 [ThemeMode.System]，避免在 emulator-5574 上留下人为的强制深/浅色。
 */
@RunWith(AndroidJUnit4::class)
class ThemeModeSwitchTest {
    @get:Rule val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @After
    fun resetThePreferenceToFollowSystem() {
        AppThemePreferences.write(appContext, ThemeMode.System)
    }

    /**
     * 存 `dark` 后首帧就必须是深套页底：证 prefs 读得到、`rememberAppThemeMode` 初值取得到、
     * 装配点在 `MaterialTheme` 之下供的是 [AppColorDark]。
     */
    @Test
    fun stored_dark_preference_paints_the_dark_background() {
        AppThemePreferences.write(appContext, ThemeMode.Dark)
        composeRule.setContent { ThemeProbe() }
        assertEquals(
            "偏好为 Dark 时根底必须取深套 background（LocalAppColors 未随三态换套）",
            AppColorDark.background.toArgb() and 0xFFFFFF,
            probeCenterRgb(),
        )
    }

    /**
     * 存 `light` 起手是浅套页底，随后**只改 prefs、不重建组合**必须翻成深套页底：
     * 证的是 `rememberAppThemeMode()` 里 `OnSharedPreferenceChangeListener` → 主线程 →
     * `mutableStateOf` → `compositionLocalOf` 读取失效这条活链路。
     */
    @Test
    fun stored_light_preference_flips_to_dark_when_the_preference_changes_live() {
        AppThemePreferences.write(appContext, ThemeMode.Light)
        composeRule.setContent { ThemeProbe() }
        val lightRgb = AppColorLight.background.toArgb() and 0xFFFFFF
        val darkRgb = AppColorDark.background.toArgb() and 0xFFFFFF
        assertEquals(
            "偏好为 Light 时根底必须取浅套 background（装配点被平台深色带跑或 prefs 没读到）",
            lightRgb,
            probeCenterRgb(),
        )

        AppThemePreferences.write(appContext, ThemeMode.Dark)
        // 监听回调来自 SharedPreferences 的磁盘写线程，实现里要跳回主线程才会触发重组；
        // waitUntil 在 UI 线程之外轮询，不会把主线程占死（测试体本身跑在 UI 线程，直接 sleep 轮询会饿死这次重组）。
        composeRule.waitUntil(timeoutMillis = 5_000) { probeCenterRgb() == darkRgb }
        assertEquals(
            "偏好从 Light 改成 Dark 后，活着的组合必须翻成深套 background（监听或失效重算断了）",
            darkRgb,
            probeCenterRgb(),
        )
    }

    /**
     * 探针形状，不是生产形状：装配点只装 token 与 `MaterialTheme`，本身不铺页底
     * （生产页底由 `AppScreen` 一类承担，批 2/批 3 收口），所以这里显式把当前套的
     * `background` 画满整屏，`captureToImage` 才拿得到可比对的颜色；
     * 底色读的就是 `LocalAppColors`，探针因此跟着三态换套。
     */
    @Composable
    private fun ThemeProbe() {
        MintGlassTheme(themeMode = rememberAppThemeMode()) {
            Box(Modifier.fillMaxSize().background(appColors().background).testTag(THEME_PROBE_TAG))
        }
    }

    /** 探针中心像素的 RGB（丢掉 alpha：页底是不透明填充，alpha 不承载信息）。 */
    private fun probeCenterRgb(): Int =
        composeRule.onNodeWithTag(THEME_PROBE_TAG).captureToImage().asAndroidBitmap().let { bitmap ->
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) and 0xFFFFFF
        }
}

private const val THEME_PROBE_TAG = "theme_probe"
