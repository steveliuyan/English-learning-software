package com.example.englishlearning.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppThemePreferences
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.rememberAppThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主题三态的**真像素**证据（计划 Task 5 Step 3）：存储里的偏好必须一路走到装配点，
 * 最后落到屏幕像素上——`AppThemePreferences.write` → `read` → `rememberAppThemeMode()` →
 * `MintGlassTheme` 换套 → 页底颜色。JVM 侧只能证判定函数（`ThemeModeDecisionTest`），
 * 证不了 prefs 读写与组合期重算，所以本类只能在设备上跑。
 *
 * 四处形状决定，都不是随手写的：
 * - **偏好取「与平台相反」的那一档，而不是固定 `Dark`**：装配点算 `dark` 用的是
 *   `shouldUseDarkTheme(themeMode, isSystemInDarkTheme())`，若模拟器正处于夜间模式而 prefs 完全没生效，
 *   回退 `System` 也会画出深套页底——固定断言深色就成了「为错误的理由通过」的空用例。
 *   存反档以后，通过只能通过「偏好压过平台」这条路径，无论本机在不在夜间模式都成立。
 *   平台当前档位由 [platformInDark] 直接读 `Configuration.uiMode` 得到，不走被测逻辑。
 * - **不传字面 `ThemeMode.Dark`，而是先落 prefs 再经 `rememberAppThemeMode()` 回读**：
 *   计划 Step 3 的样例片段是直传枚举的，但那样本类的像素只证明装配点，`AppThemePreferences`
 *   与 `rememberAppThemeMode` 就成了无测试的生产码；Step 2 写明「读写由 Step 3 的 connected 用例覆盖」，
 *   故本类把链路接全，用例数仍是计划期望的两个方法。
 * - **探针同时铺两条通道的底色**：上层 `Box` 画 `MaterialTheme.colorScheme.background`（M3 通道，
 *   证 `appColorScheme(dark)` 分支跟着换），下层 `Box` 画 `appColors().background`（token 通道，
 *   证 `LocalAppColors` 跟着换）。只测 token 通道会漏掉「CompositionLocal 换了但 ColorScheme 没换」
 *   这一类装配缺陷；两层各占半屏，各自中心像素互不遮挡。
 * - **两个方法各一次 `setContent`，绝不在同一方法里 `setContent` 两次**：本仓库已记录的怪癖
 *   （见 `android-connected-qa-loop`），同一次测试两次 `setContent` 真机必崩。
 *
 * 期望像素取 [AppColorDark] / [AppColorLight] 的 `background` token，不重抄 hex：
 * 本类要证的是「换套」这件事（选中了哪一套），token 自身的取值由 `DesignTokenContractTest` 钉死；
 * 在这里复述十六进制只会长出第二份真值并随 token 校准漂移。两条通道取的是同一个 token，
 * 所以同一个期望值分别比对两个节点——差异只可能来自装配分支，不来自取色口径。
 *
 * 精确相等断言的适用范围（不是疏漏）：页底是不带 alpha 的纯色填充，本用例假定设备按 sRGB 直出、
 * 渲染链不做色域转换，`getPixel` 取回的就是 token 值。若日后 P3 屏或带色域管理的显示层造成取整漂移，
 * 处置是改成逐通道容差比较，而不是放宽「必须换套」这条判定。
 *
 * 只读偏好、只写偏好：本类不碰 Room、不碰备份清单（全局约束 5）。
 * 偏好清理走 `@Before` + `@After` 两道：起手清保证「上一轮残留不会替本轮说话」，收尾清保证
 * emulator-5574 不留下人为的强制深/浅色——哪怕断言先炸，收尾仍会跑。
 */
@RunWith(AndroidJUnit4::class)
class ThemeModeSwitchTest {
    @get:Rule val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    /**
     * 平台当前是否夜间模式。刻意不复用 `isSystemInDarkTheme()`：本类靠它挑出与平台相反的那一档，
     * 取值必须来自独立观测点（`Configuration.uiMode`），与被测装配点的判定路径无关。
     */
    private val platformInDark: Boolean
        get() = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    /** 起手清 + 收尾清：见类注释末段。 */
    @Before
    @After
    fun resetThePreferenceToFollowSystem() {
        AppThemePreferences.write(appContext, ThemeMode.System)
    }

    /**
     * 存「与平台相反」那一档后首帧就必须是那一套的页底，且两条通道同时到位：
     * 证 prefs 读得到、`rememberAppThemeMode` 初值取得到、`LocalAppColors` 与 `ColorScheme` 都随三态换套，
     * 并排除「偏好没生效但平台恰好同色」这条空用例路径。
     */
    @Test
    fun a_preference_that_opposes_the_platform_paints_the_stored_set() {
        val stored = if (platformInDark) ThemeMode.Light else ThemeMode.Dark
        val expected = backgroundRgb(if (platformInDark) AppColorLight else AppColorDark)
        AppThemePreferences.write(appContext, stored)
        composeRule.setContent { ThemeProbe() }

        assertEquals(
            "偏好为 $stored（平台夜间=$platformInDark）时 M3 通道必须取该套 background（ColorScheme 未随三态换套）",
            expected,
            probeCenterRgb(MATERIAL_PROBE_TAG),
        )
        assertEquals(
            "偏好为 $stored（平台夜间=$platformInDark）时 token 通道必须取该套 background（LocalAppColors 未随三态换套）",
            expected,
            probeCenterRgb(TOKEN_PROBE_TAG),
        )
    }

    /**
     * 起手是与平台相反的 A 档，随后**只改 prefs、不重建组合**必须翻成 B 档页底：
     * 证的是 `rememberAppThemeMode()` 里 `OnSharedPreferenceChangeListener` → 投递主线程 →
     * `mutableStateOf` → `compositionLocalOf` 读取失效这条活链路。
     * 起手反档意味着首帧只能由「偏好压过平台」解释；随后翻档改的是同一像素，监听断了就停在 A 档红。
     */
    @Test
    fun changing_the_stored_preference_flips_the_live_composition() {
        val first = if (platformInDark) ThemeMode.Light else ThemeMode.Dark
        val second = if (platformInDark) ThemeMode.Dark else ThemeMode.Light
        val firstExpected = backgroundRgb(if (platformInDark) AppColorLight else AppColorDark)
        val secondExpected = backgroundRgb(if (platformInDark) AppColorDark else AppColorLight)

        AppThemePreferences.write(appContext, first)
        composeRule.setContent { ThemeProbe() }
        assertEquals(
            "偏好为 $first（平台夜间=$platformInDark）时两条通道都必须取该套 background",
            listOf(firstExpected, firstExpected),
            listOf(probeCenterRgb(MATERIAL_PROBE_TAG), probeCenterRgb(TOKEN_PROBE_TAG)),
        )

        AppThemePreferences.write(appContext, second)
        // 监听回调不保证在主线程，实现里经 mainExecutor 投递后再写 state；本用例只证这条活链路通，
        // 没做过「删掉跳板会红」的反证（口径见 AppThemePreference 的注释）。
        // waitUntil 在 UI 线程之外轮询，不会把主线程占死（测试体若阻塞轮询会饿死这次重组）。
        composeRule.waitUntil(timeoutMillis = 5_000) {
            probeCenterRgb(MATERIAL_PROBE_TAG) == secondExpected &&
                probeCenterRgb(TOKEN_PROBE_TAG) == secondExpected
        }
        assertEquals(
            "偏好从 $first 改成 $second 后，活着的组合必须翻成该套 background（监听或失效重算断了）",
            listOf(secondExpected, secondExpected),
            listOf(probeCenterRgb(MATERIAL_PROBE_TAG), probeCenterRgb(TOKEN_PROBE_TAG)),
        )
    }

    /**
     * 探针形状，不是生产形状：装配点只装 token 与 `MaterialTheme`，本身不铺页底
     * （生产页底由 `AppScreen` 一类承担，批 2/批 3 收口），所以这里显式铺两条通道的底色，
     * `captureToImage` 才拿得到可比对的颜色；两层各占上下半屏，中心像素互不遮挡。
     */
    @Composable
    private fun ThemeProbe() {
        MintGlassTheme(themeMode = rememberAppThemeMode()) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                        .testTag(MATERIAL_PROBE_TAG),
                )
                Box(
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .background(appColors().background)
                        .testTag(TOKEN_PROBE_TAG),
                )
            }
        }
    }

    /** 该套页底的期望像素（丢掉 alpha：页底是不透明填充，alpha 不承载信息；适用范围见类注释）。 */
    private fun backgroundRgb(set: AppColorSet): Int = set.background.toArgb() and 0xFFFFFF

    /** 指定探针节点的中心像素。 */
    private fun probeCenterRgb(tag: String): Int =
        composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let { bitmap ->
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) and 0xFFFFFF
        }
}

private const val MATERIAL_PROBE_TAG = "theme_probe_material_channel"
private const val TOKEN_PROBE_TAG = "theme_probe_token_channel"
