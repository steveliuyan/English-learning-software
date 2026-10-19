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
import androidx.compose.ui.test.performScrollTo
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
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * 以下几处形状决定，都不是随手写的：
 * - **偏好取「与平台相反」的那一档，而不是固定 `Dark`**：装配点算 `dark` 用的是
 *   `shouldUseDarkTheme(themeMode, isSystemInDarkTheme())`，若模拟器正处于夜间模式而 prefs 完全没生效，
 *   回退 `System` 也会画出深套页底——固定断言深色就成了「为错误的理由通过」的空用例。
 *   存反档以后，通过只能通过「偏好压过平台」这条路径，无论本机在不在夜间模式都成立。
 *   平台当前档位由 [platformInDark] 直接读 `Configuration.uiMode` 得到，不走被测逻辑。
 * - **不传字面 `ThemeMode.Dark`，而是先落 prefs 再经 `rememberAppThemeMode()` 回读**：
 *   计划 Step 3 的样例片段是直传枚举的，但那样本类的像素只证明装配点，`AppThemePreferences`
 *   与 `rememberAppThemeMode` 就成了无测试的生产码；Step 2 写明「读写由 Step 3 的 connected 用例覆盖」，
 *   故本类把链路接全（方法数不在此抄录，本文件里的 `@Test` 本体就是台账）。
 * - **探针同时铺两条通道的底色**：上层 `Box` 画 `MaterialTheme.colorScheme.background`（M3 通道，
 *   证 `appColorScheme(dark)` 分支跟着换），下层 `Box` 画 `appColors().background`（token 通道，
 *   证 `LocalAppColors` 跟着换）。只测 token 通道会漏掉「CompositionLocal 换了但 ColorScheme 没换」
 *   这一类装配缺陷；两层各占半屏，各自中心像素互不遮挡。
 * - **每个方法各一次 `setContent`，绝不在同一方法里 `setContent` 两次**：本仓库已记录的怪癖
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
     * 深色偏好下，今日计划屏「今日学习进度」卡的**容器像素**必须落在深套 `surface` 那一档。
     *
     * 为什么这条排在批 2 最前面（Task 12.5 的行为面）：批 1 Task 3 已把 `Mint*` 墨色改成组合期读取，
     * 而业务屏容器仍写静态 `AppPalette.Surface`（浅套写死白）。两者配对后，真机 `bf353dda` 深色档实测到
     * 「白底 + 主题浅墨」的近不可读文案（`docs/verification/ui-redesign-2026-10.md`「真机深色实勘」）。
     * 本用例就是把那枚缺陷钉成像素判据：容器底必须是**当前主题的 surface**，而不是永远 `#FFFFFFFF`。
     *
     * 三条取样口径，都不是随手写的：
     *  - **偏好显式写 `ThemeMode.Dark`，不再走「与平台相反」那一档**：本类前两已经把「prefs 压过平台」与
     *    「prefs 改动翻活组合」两条链路证完了；这一条要证的是**业务容器的读点跟不跟主题**。
     *    若把偏好读取链路再复制一遍，本用例就成了前两件的影子；此处刻意只留「容器色 == 深套 surface」这一件事。
     *  - **取样点取本节点宽 9/10、高 1/2，而不是几何中心**：卡片里是左对齐的三行文案，中心像素会落在字身上，
     *    量到的是墨色而不是容器底；宽 9/10 仍在这张卡的水平内边距（16dp）之内，量到的必定是容器填充，
     *    且离 1dp 描边与 18dp 圆角都足够远。
     *  - **逐通道容差比较（类 KDoc 预告的处置，不是放宽判定）**：M3 `Card` 的默认 `tonalElevation` 非零，
     *    `Surface` 会按 `ElevationOverlay` 把 `colorScheme.surfaceTint` 混进容器色，所以渲染像素必然偏离
     *    token 原值若干级；本用例仍判「换套没换套」——容差由下面的自检钉住远小于浅/深两套 `surface`
     *    的最小逐通道间距，浅色档不可能踩进这条容差。
     *
     * 期望值取自 [AppColorDark] / [AppColorLight] 的 `surface` token，不重抄 hex（同前两例的口径）。
     * 一个 `@Test` 只 `setContent` 一次（本仓真机怪癖：同一测试内两次 `setContent` 必崩）。
     */
    @Test
    fun a_business_container_paints_the_stored_set_surface_in_dark_mode() {
        AppThemePreferences.write(appContext, ThemeMode.Dark)
        composeRule.setContent {
            MintGlassTheme(themeMode = rememberAppThemeMode()) {
                TodayPlanScreen(
                    TodayPlanUiState.Ready(
                        wordBookName = "测试词书",
                        localDateLabel = "2026-10-19",
                        newTarget = 2,
                        dueTarget = 3,
                        totalTasks = 5,
                        newDone = 1,
                        dueDone = 1,
                    ),
                )
            }
        }
        composeRule.onNodeWithTag(TODAY_PLAN_PROGRESS_TAG).performScrollTo()

        val actual = probeSurfaceRgb(TODAY_PLAN_PROGRESS_TAG)
        val dark = surfaceRgb(AppColorDark)
        val light = surfaceRgb(AppColorLight)
        assertEquals(
            "今日计划屏进度卡容器必须画深套 surface（写静态 AppPalette.Surface 时这里是白底，" +
                "即真机实勘那处「白底 + 主题浅墨」）：期望 $dark ±$SURFACE_CHANNEL_TOLERANCE，实际 $actual",
            0,
            maxChannelDelta(actual, dark).let { if (it <= SURFACE_CHANNEL_TOLERANCE) 0 else it },
        )
        // 自检：容差小到浅色档绝不可能被判成深色（否则上面那条断言就成了「任何非白都算过」）。
        assertTrue(
            "容差必须远小于浅/深两套 surface 的最小逐通道间距，否则本用例失去换套判别力",
            SURFACE_CHANNEL_TOLERANCE * 2 < maxChannelDelta(light, dark),
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

    /** 该套 `surface` 的期望像素（丢掉 alpha：卡片容器同为不透明填充，容差事由见用例注释）。 */
    private fun surfaceRgb(set: AppColorSet): Int = set.surface.toArgb() and 0xFFFFFF

    /** 两个 RGB 的逐通道最大绝对差（本类容差判据的唯一算式）。 */
    private fun maxChannelDelta(a: Int, b: Int): Int {
        fun channel(shift: Int) = abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))
        return maxOf(channel(16), channel(8), channel(0))
    }

    /** 指定探针节点的中心像素。 */
    private fun probeCenterRgb(tag: String): Int =
        composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let { bitmap ->
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) and 0xFFFFFF
        }

    /**
     * 指定容器节点的**填充取样点**像素：宽 9/10、高 1/2。
     * 与 [probeCenterRgb] 同一套 `captureToImage` + `getPixel` + 丢 alpha 口径，只是取样点让开了左对齐的字身
     * （理由见 [a_business_container_paints_the_stored_set_surface_in_dark_mode] 的注释）。
     */
    private fun probeSurfaceRgb(tag: String): Int =
        composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let { bitmap ->
            bitmap.getPixel(bitmap.width * 9 / 10, bitmap.height / 2) and 0xFFFFFF
        }
}

private const val MATERIAL_PROBE_TAG = "theme_probe_material_channel"
private const val TOKEN_PROBE_TAG = "theme_probe_token_channel"

/** 批 1 里程碑真机实勘判红的那枚卡（今日计划屏「今日学习进度」），容器读点迁移的行为面就钉在这里。 */
private const val TODAY_PLAN_PROGRESS_TAG = "today_plan_progress_summary"

/**
 * `surface` 像素的逐通道容差：覆盖 M3 `Card` 默认 tonalElevation 带来的 `surfaceTint` 混色与显示链取整，
 * 又远小于浅/深两套 `surface` 的最小逐通道间距（该判别力由用例内的自检当场钉住）。
 */
private const val SURFACE_CHANNEL_TOLERANCE = 24

