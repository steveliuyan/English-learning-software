package com.example.englishlearning.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
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
     *  - **逐通道容差比较（评审 m-1 后的口径：容差取小，判别力靠三条自检钉住）**：`captureToImage()` 是离屏
     *    渲染成位图，不经显示链的色彩管理，本卡实测取样像素与 `AppColorDark.surface` 逐通道差为 0；
     *    容差留 2 只抗实现侧取整。曾经取 24，那是「覆盖显示链取整」的误判——24 宽到能把深套相邻三档填充
     *    （`background` 离 `surface` 11、`tint` 12、`outlineSoft` 16）都算成命中，于是「容器漏画、直接透出页底」
     *    这条缺陷反而测不出来。收紧后由三条自检当场钉住判据不退化：
     *    ①换套判别力（容差的两倍远小于浅/深两套 `surface` 的最大逐通道间距，浅色档踩不进来）；
     *    ②取样点判别力（取样必须离该套正文墨足够远，证量的是容器填充而不是字身——大字号、取样点改动或
     *    卡内排版重排都可能把取样点挪到笔画上，动态字体 2.0 档正是这条兜住的方向，见下）；
     *    ③相邻档判别力（容差必须容不下深套相邻填充，证「漏画容器」会在本用例判红而不是蒙过）。
     *    **①的前提写在主题侧**：本主题把 `surfaceTint` 设为 `set.onMint`（`MintGlassTheme.kt:113/151`），
     *    它与 `surface` 逐通道差 200 上下；当前不混色是**实测事实**——这张卡没显式传 `tonalElevation`
     *    （`TodayPlanScreen.kt:168-175` 只给了 colors/shape/modifier），`DefaultElevationOverlay`
     *    的叠加 alpha 因此为 0，取样与 token 的逐通道差实测 0。注意混色通道并没有被关掉：主题把 M3
     *    `surface` 也设成了同一枚 `set.surface`
     *    （`MintGlassTheme.kt:109/147`），`isSurfaceColor` 按数值成立，所以一旦有人给这张卡显式加
     *    `tonalElevation` 到 ≥3dp，十几个百分点的 tint 会立刻把 2 档容差撑破——
     *    届时必须重算期望值（改用 `surfaceColorAtElevation`）而不是放大容差。
     *
     * 期望值取自 [AppColorDark] / [AppColorLight] 的 `surface` token，不重抄 hex（同前两例的口径）。
     * 一个 `@Test` 只 `setContent` 一次（本仓真机怪癖：同一测试内两次 `setContent` 必崩）。
     *
     * 动态字体（m-3 的处置）：本用例的 `composeRule` 没有钉 fontScale 的公开口子（`setDensity` 只管密度），
     * 所以按「默认档为判据、非默认档允许判红并转人工」执行；判红时先看自检②的消息——
     * 若它说取样离墨不够远，就是大字号把 9/10 那一列推到了笔画上，属取样点问题而非容器回归。
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
        // 自检①（m-2 / m-4）：容差小到浅色档绝不可能被判成深色（否则上面那条断言就成了「任何非白都算过」）。
        // 用的是 maxChannelDelta —— 两套 surface 之间**最宽**的那条通道；判据按最宽通道都还成立，
        // 说明这条自检离失配还有余量（消息与算式必须同名，别写「最小」却算「最大」）。
        assertTrue(
            "容差必须远小于浅/深两套 surface 的最大逐通道间距，否则本用例失去换套判别力",
            SURFACE_CHANNEL_TOLERANCE * 2 < maxChannelDelta(light, dark),
        )
        // 自检②（m-3）：取样点必须落在容器填充上，不能是字身。深套两档正文墨（text1 / text2）与
        // surface 的最大逐通道差实测为 210 / 136，都远大于容差，所以「离墨够远」是一条硬判据：
        // 大字号、取样点挪位或卡内排版重排把 9/10 那一列推到笔画上时，本条当场判红，
        // 而不是让上面那条容差断言靠「恰好量到某个深色像素」蒙过去。
        val nearestInkDelta = minOf(
            maxChannelDelta(actual, rgb(AppColorDark.text1)),
            maxChannelDelta(actual, rgb(AppColorDark.text2)),
        )
        assertTrue(
            "取样像素必须明显区别于该套正文墨色（text1/text2），否则本用例量到的可能是字身而不是容器底：" +
                "取样 $actual，离最近的一档墨只有 $nearestInkDelta 档（门槛 $INK_MIN_CHANNEL_DELTA）",
            INK_MIN_CHANNEL_DELTA <= nearestInkDelta,
        )
        // 自检③（m-1）：容差必须窄到容不下深套**相邻**填充档，否则「容器漏画、透出页底」会被本用例判成通过。
        // 这三档都是这张卡底下可能透出来的底：background 是页底、tint 是卡内 tinted 区块、outlineSoft 是弱描边档。
        // 间距算式与主断言同为 maxChannelDelta（消息与算式必须同名），因此这里取三档里**最近**的那一档：
        // 连最近那一档都踩不进容差，相邻档判别力才成立（背景实测 11 / tint 12 / outlineSoft 16，容差 2）。
        val nearestAdjacentFill = minOf(
            maxChannelDelta(dark, rgb(AppColorDark.background)),
            maxChannelDelta(dark, rgb(AppColorDark.tint)),
            maxChannelDelta(dark, rgb(AppColorDark.outlineSoft)),
        )
        assertTrue(
            "容差必须容不下深套相邻填充档，否则「容器漏画、透出页底」会在本用例蒙过（m-1）：" +
                "容差 $SURFACE_CHANNEL_TOLERANCE，最近的一档离 surface 只有 $nearestAdjacentFill 档（门槛 $ADJACENT_FILL_MIN_CHANNEL_DELTA）",
            ADJACENT_FILL_MIN_CHANNEL_DELTA <= nearestAdjacentFill &&
                SURFACE_CHANNEL_TOLERANCE < nearestAdjacentFill,
        )
    }

    /**
     * AI 服务列表行（修复轮 C-1 那一处）的**配对像素**：容器必须画深套 `surface`，
     * 且同一行里离容器对比度最远的那枚像素（墨核）压容器必须到正文 AA 4.5:1。
     *
     * 为什么还要这条，静态判据（`ContainerInkPairingContractTest`）不够吗：
     * 静态判据只证「源码里那一行写了显式 `color =`」，证不到**组合期取到的那枚色真的是本套墨**。
     * 浅套写死的 `AppType.*` 静态墨（#1C1C1E）压深套 `surface`（#17201C）实测 ≈1.05:1，
     * 也就是批 1 终审 F4 / AC-R-11 说的那一侧：容器迁了、墨没迁，字当场消失。
     * 本例把这枚缺陷钉成像素判据，且判据取「最远像素」而不是「最暗像素」——
     * 深套正文墨是**浅**色（text1 #E9F2EC），按最暗取会取到容器本身，比值恒等于 1，用例恒红。
     *
     * 取样口径两条：
     *  - **容器底色取整行像素直方图的众数**，不取固定坐标：这一行右侧还要排密钥状态徽标与两个默认位图标，
     *    任取一列都可能量到图标。填充是面积最大的一档，字身/描边/图标都是少数派，众数即容器。
     *  - **墨核取标题那个 `Text` 节点的边界内**：节点边界就是这几行字的排版区，
     *    边界里除字就是容器底，所以「离众数最远的像素」就是墨核（抗锯齿边缘只是次远，不影响比值下限）。
     *
     * 阈值 4.5 沿用本仓正文 AA 口径（`EntryRowInkTest`、`InlineNoticeKindTest` 同一档），
     * 亮度/比值公式与 JVM 侧 `SourceRatchet.luminance/ratio` 同式：那份在 `src/test`，
     * `androidTest` 编译期看不见，只能按同一公式在此复刻一份，注释互相点名以免两条口径漂移。
     */
    @Test
    fun the_ai_profile_row_paints_theme_ink_on_the_migrated_container_in_dark_mode() {
        AppThemePreferences.write(appContext, ThemeMode.Dark)
        composeRule.setContent {
            MintGlassTheme(themeMode = rememberAppThemeMode()) {
                AiProfileSettingsScreen(
                    listState = AiProfileListUiState.Ready(
                        listOf(AiProfileListItem(profile = probeProfile(), hasKey = true)),
                    ),
                    editor = null,
                    onAdd = {},
                    onEdit = {},
                    onDraftChange = {},
                    onSave = {},
                    onCloseEditor = {},
                    onDeleteProfile = {},
                    onDeleteKey = {},
                    onBack = {},
                )
            }
        }
        val rowTag = "$AI_PROFILE_ROW_TAG_PREFIX$PROBE_PROFILE_ID"
        composeRule.onNodeWithTag(rowTag).performScrollTo()

        val container = dominantRgb(rowTag)
        val darkSurface = surfaceRgb(AppColorDark)
        assertEquals(
            "AI 服务行容器必须画深套 surface（修复轮把这里从静态 AppPalette.Surface 迁成 appColors().surface，" +
                "迁移读点没接到组合期套色时这里是白底）：期望 $darkSurface ±$SURFACE_CHANNEL_TOLERANCE，实际 $container",
            0,
            maxChannelDelta(container, surfaceRgb(AppColorDark))
                .let { if (it <= SURFACE_CHANNEL_TOLERANCE) 0 else it },
        )

        val inkCore = extremeContrastRgb(AI_PROFILE_TITLE_TEXT, container)
        val contrast = contrastRatio(inkCore, container)
        assertTrue(
            "行标题墨压该行容器必须达正文 AA $NORMAL_TEXT_AA_RATIO:1（浅套写死的 AppType 静态墨压深套 surface " +
                "只有约 1.05:1，即「容器迁了墨没迁」那一侧）：墨核 $inkCore，容器 $container，实测 $contrast",
            NORMAL_TEXT_AA_RATIO <= contrast,
        )
        // 自检：墨核不能就是容器本身。若取样区里根本没字（文案改了、节点空了），
        // 上面那条对比断言会退化成「容器压容器 = 1:1」而判红——这条把失配原因先说清，避免误读成配色回归。
        assertTrue(
            "墨核必须与容器底不同像素，否则本用例量到的可能是空白区而不是字身：墨核 $inkCore，容器 $container",
            inkCore != container,
        )
    }

    /** 本用例喂给列表行的配置：id 与标题都要能被取样锚点找到，取值不含任何真实密钥。 */
    private fun probeProfile(): AiProfile =
        AiProfile(
            profileId = PROBE_PROFILE_ID,
            displayName = AI_PROFILE_TITLE_TEXT,
            websiteUrl = "https://example.com",
            endpoint = "https://api.example.com/v1",
            model = "probe-model",
            capabilities = setOf(AiCapability.Text),
            secretReference = AiProfileSecretUseCase.referenceFor(PROBE_PROFILE_ID),
        )

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

    /** 任意不透明 token 的 RGB（丢掉 alpha，与 [surfaceRgb] 同一比较口径，供「离墨够远」那条反向断言用）。 */
    private fun rgb(color: Color): Int = color.toArgb() and 0xFFFFFF

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

    /** 按 testTag 取节点离屏位图（本类所有像素取样点的唯一入口）。 */
    private fun nodeBitmapByTag(tag: String): Bitmap =
        composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()

    /**
     * 按可见文案取**叶子**节点离屏位图（节点边界即该段字的排版区，界内除字就是它身下的容器）。
     *
     * `useUnmergedTree = true` 不是风格选择而是正确性：列表行 `clickable` 默认 `mergeDescendants`，
     * 合并树上匹配到的是整行，位图里会同时含副标题那档 `text2`——第一版因此把「标题墨被删掉」判成通过
     * （实测最远像素 0x9FB3AA 正是深套 `text2`，比值 7.54:1）。取未合并树才拿得到那一段标题字自己。
     */
    private fun nodeBitmapByText(text: String): Bitmap =
        composeRule.onNodeWithText(text, useUnmergedTree = true).captureToImage().asAndroidBitmap()

    /**
     * 逐像素遍历**不透明**像素（丢掉 alpha）。
     * 必须按 alpha 过滤：`captureToImage()` 抓的是节点所在层的位图，
     * 叶子文字节点之外是透明区，`getPixel` 回 0x00000000，丢掉 alpha 后就成了「纯黑」——
     * 那枚假黑像素会被当成墨核，把「墨压容器」的比值算成「黑压深底」，用例既能假绿也能假红。
     */
    private inline fun forEachOpaquePixel(bitmap: Bitmap, action: (rgb: Int) -> Unit) {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val argb = bitmap.getPixel(x, y)
                if (((argb shr 24) and 0xFF) >= OPAQUE_ALPHA_FLOOR) action(argb and 0xFFFFFF)
            }
        }
    }

    /**
     * 节点不透明像素直方图的**众数**：一张卡/一行里面积最大的一档必定是容器填充，
     * 字身、描边、圆角抗锯齿、图标都只是少数派。用它代替固定坐标取样，
     * 取样点就不依赖「这一行右边排了什么」这种版面细节（版面重排不会把判据挪到图标上）。
     */
    private fun dominantRgb(tag: String): Int {
        val counts = HashMap<Int, Int>()
        forEachOpaquePixel(nodeBitmapByTag(tag)) { rgb -> counts[rgb] = (counts[rgb] ?: 0) + 1 }
        val winner = counts.maxByOrNull { it.value }
        return checkNotNull(winner) { "节点 $tag 没有不透明像素，取不到容器底（取样对象根本没渲染？）" }.key
    }

    /**
     * 该文案节点里离 [reference] **对比度最远**的那枚不透明像素 = 墨核。
     * 刻意不取「最暗」：深套正文墨是浅色（`text1` #E9F2EC），按最暗取会取到容器本身或背景，比值失真。
     *
     * **取样必须窄到那一段字**：第一版按文案 `onNodeWithText(标题)` 取样，跑起来才发现匹配到的是整行
     * （列表行 `clickable` 默认 `mergeDescendants`，把子节点文本合并上来），位图里同时有副标题那档 `text2`，
     * 最远像素取到的是副标题墨（实测 0x9FB3AA，7.54:1）——把生产码里标题那行的显式前景删掉，
     * 用例照样绿，判据对目标缺陷完全失明。现取**未合并树**，节点就是那一段标题字自己。
     */
    private fun extremeContrastRgb(text: String, reference: Int): Int {
        val base = luminance(reference)
        var best = reference
        var bestGap = -1.0
        forEachOpaquePixel(nodeBitmapByText(text)) { rgb ->
            val gap = abs(luminance(rgb) - base)
            if (gap > bestGap) {
                bestGap = gap
                best = rgb
            }
        }
        return best
    }

    /**
     * WCAG 2.1 相对亮度。与 JVM 侧 `SourceRatchet.luminance` 同式——那份在 `src/test`，
     * `androidTest` 编译期看不见，只能在此复刻；两处若要改公式必须一起改，否则深浅两套的
     * 静态判据与真机像素判据会分成两套亮度口径。
     */
    private fun luminance(rgb: Int): Double {
        fun lin(channel: Int): Double {
            val s = (channel and 0xFF) / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(rgb shr 16) + 0.7152 * lin(rgb shr 8) + 0.0722 * lin(rgb)
    }

    /** `(L1 + 0.05) / (L2 + 0.05)`，与 [luminance] 同源（`SourceRatchet.ratio`）。 */
    private fun contrastRatio(a: Int, b: Int): Double {
        val hi = maxOf(luminance(a), luminance(b))
        val lo = minOf(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }
}

private const val MATERIAL_PROBE_TAG = "theme_probe_material_channel"
private const val TOKEN_PROBE_TAG = "theme_probe_token_channel"

/** 批 1 里程碑真机实勘判红的那枚卡（今日计划屏「今日学习进度」），容器读点迁移的行为面就钉在这里。 */
private const val TODAY_PLAN_PROGRESS_TAG = "today_plan_progress_summary"

/** AI 服务列表行的 testTag 前缀（生产码里为 `ai_profile_item_<profileId>`）。 */
private const val AI_PROFILE_ROW_TAG_PREFIX = "ai_profile_item_"

/** 取样用配置 id：只出现在本用例喂进去的假状态里，不含任何真实凭据。 */
private const val PROBE_PROFILE_ID = "probe-profile"

/** 取样用配置名：既是列表行标题的可见文案，也是「墨核」取样节点的定位锚。 */
private const val AI_PROFILE_TITLE_TEXT = "配对判据取样配置"

/**
 * 正文 AA 阈值：与本仓 JVM 侧同档（`EntryRowInkTest` 的 4.5、`InlineNoticeKindTest` 的 R-10.1b）。
 * 浅套写死的 `AppType` 静态墨压深套 `surface` 实测约 1.05:1，与 4.5 之间隔着整整一个数量级，
 * 所以这条阈值不需要精调也不会两可。
 */
private const val NORMAL_TEXT_AA_RATIO = 4.5

/**
 * 「算不透明」的 alpha 下限：只排掉真正透明的空白区（0），保留抗锯齿边缘。
 * 取 250 而不是 255——位图在圆角与描边混合处常见 254/253 这类档位，按 255 筛会把容器边缘整圈排掉。
 */
private const val OPAQUE_ALPHA_FLOOR = 250

/**
 * `surface` 像素的逐通道容差。**刻意取小**：`captureToImage()` 是离屏渲染成位图，不经显示链的色彩管理，
 * 实测取样像素与 `AppColorDark.surface` 逐通道差为 0，留 2 档只抗未来实现里的抗锯齿/混合取整。
 * 容差一大就失去意义——深套相邻的三档填充（`background` 11、`tint` 12、`outlineSoft` 16）与 `surface`
 * 的最大逐通道差都只有十几档，容差 24 时「容器漏画、直接透出页底」会被判成通过（评审 m-1）。
 * 换套判别力（自检①）与相邻档判别力（自检③）各由用例内一条断言当场钉住，不把容差当魔法数放大。
 */
private const val SURFACE_CHANNEL_TOLERANCE = 2

/**
 * 「取样点不是字身」那条反向断言（自检②）的门槛：取样像素与该套正文墨（text1 / text2）里较近一档的
 * 最大逐通道差必须不小于它。深套两档墨与 `surface` 的最大逐通道差实测为 210 / 136，门槛取 32：
 * 比换套判据本身宽（不会与容差抢同一次失配），又远小于真落字身时的距离，
 * 因此只有取样真的挪到笔画上才会红。**不写成容差的倍数**——容差按自检③的要求已经收紧到 2，
 * 再挂钩就退化成「几乎任何像素都算离墨够远」，本条自检也就没判据了。
 */
private const val INK_MIN_CHANNEL_DELTA = 32

/**
 * 自检③的门槛：容差必须容不下深套相邻填充档，否则本用例分不清「画了 surface」与「漏画容器透出页底」。
 * 取 4 而非 1：相邻档离 `surface` 最近的一条通道差为 11（`background`），留出 2 档余量仍能挡住误判，
 * 又不至于把将来某次把容差放大到十几点的改动放过去。
 */
private const val ADJACENT_FILL_MIN_CHANNEL_DELTA = 4

