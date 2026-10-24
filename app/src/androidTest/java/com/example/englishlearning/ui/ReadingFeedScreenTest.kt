package com.example.englishlearning.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.reading.FetchFailure
import com.example.englishlearning.reading.FeedItem
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 外刊选读**二级层**（`ReadingFeedScreen`，Task 24 裁决⑤ 第二件）的 connected 取证。
 *
 * 本层是从 `ReadingAccessScreen` 的内联 `FeedSection` 搬出来的，所以这里要证的是「搬家没搬丢」
 * 与「搬家后深色档真的换套」两件事，而不是重新证一遍取源逻辑（取源与 `FeedItem` 数据形状归
 * `ReadingAccessViewModel`，本轮一字未碰，JVM 侧另有 `ReadingHomeLayoutContractTest` 钉源码形状）。
 *
 * 为什么必须上设备：
 *  - 五枚 Feed 状态与列表行的**行为面**（点击把哪一条交给宿主）只有真组合树能证；
 *  - 深色档证据只能走测试内显式 `MintGlassTheme(themeMode = ThemeMode.Dark)`（本仓硬口径，
 *    先例 [ThemeModeSwitchTest]、`ui/glass/GlassPanelThemeInkTest.kt`）——`adb shell cmd uimode night yes`
 *    不是深色档证据，它改的是平台 uiMode，而本层读的是 `LocalAppColors`。
 *
 * 取样口径（沿用 [ThemeModeSwitchTest] 已验证过的三条，不另起炉灶）：
 *  - **容器底取节点像素众数**（[dominantRgb]），不写死坐标：本层是整屏 `Column`，内容只占顶部一小段，
 *    众数必定是页底；版面重排、字号变大都不会把取样点推到字身上。
 *  - **墨核取「离容器众数亮度最远」的那枚不透明像素**，不取「最暗」：深套正文墨是浅色（`text1` #E9F2EC），
 *    按最暗取会取到容器本身、比值恒等于 1。
 *  - **期望像素取 `AppColorDark` / `AppColorLight` 的 token，不重抄 hex**：本层迁移后零枚 `AppPalette`
 *    / `DomainColors` 静态读点、零枚裸 hex（两张棘轮当场可核），token 自身取值由 `DesignTokenContractTest`
 *    钉死；在这里复述十六进制只会长出第二份真值。
 *  - **按 alpha 过滤**（[OPAQUE_ALPHA_FLOOR]）：节点位图里文字节点之外是真正透明区，丢 alpha 后就成了假黑像素，
 *    会被当成墨核（口径同 [ThemeModeSwitchTest] 的注释）。
 *
 * 一个 `@Test` 只调一次 `setContent`（本仓真机怪癖：同一次测试两次 `setContent` 必崩）。
 * 亮度/比值公式与 JVM 侧 `ui/SourceRatchet.kt` 的 `luminance` / `ratio` 同式：那份在 `src/test`，
 * `androidTest` 编译期看不见，只能在此复刻，两处改公式必须一起改。
 * 本类不碰 Room、不碰网络、不碰 SAF：喂的全是假状态。
 */
@RunWith(AndroidJUnit4::class)
class ReadingFeedScreenTest {
    @get:Rule val composeRule = createComposeRule()

    // ---------- ① 五枚 Feed 状态与列表行为 ----------

    @Test
    fun readyFeedListsEveryItemUnderItsSourceCreditAndOpensTheChosenOne() {
        val items = listOf(feedItem(0), feedItem(1), feedItem(2))
        var opened: String? = null
        setFeed(
            themeMode = ThemeMode.Light,
            feed = FeedUiState.Ready(items = items, sourceDisplayName = SOURCE_NAME),
            onFetchFeedItem = { opened = it.articleUrl },
        )

        composeRule.onNodeWithTag("feed_source").assertIsDisplayed()
        // 来源署名口径逐字搬自原内联区：「来源：」+ sourceDisplayName，本层只换版面层次不改文案。
        composeRule.onNodeWithText("来源：$SOURCE_NAME").assertIsDisplayed()
        items.indices.forEach { composeRule.onNodeWithTag("feed_item_$it").assertIsDisplayed() }
        composeRule.onNodeWithTag("feed_item_${items.size}").assertDoesNotExist()
        // 每行都要有可点语义（大触控目标与列表可点性由 54dp 下限 + Button 承担）。
        composeRule.onNodeWithTag("feed_item_1").assertHasClickAction()

        composeRule.onNodeWithTag("feed_item_1").performClick()
        assertEquals("点第二行必须把第二条交给宿主", items[1].articleUrl, opened)
    }

    @Test
    fun loadingFeedStateShowsOnlyTheWaitingLine() {
        setFeed(ThemeMode.Light, FeedUiState.Loading)

        composeRule.onNodeWithTag("feed_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_item_0").assertDoesNotExist()
        composeRule.onNodeWithTag("feed_source").assertDoesNotExist()
    }

    @Test
    fun emptyFeedStateExplainsTheSourceInsteadOfPaintingABlankScreen() {
        setFeed(ThemeMode.Light, FeedUiState.Empty)

        composeRule.onNodeWithTag("feed_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_item_0").assertDoesNotExist()
    }

    @Test
    fun unreachableFeedStateNamesTheSourceOutage() {
        setFeed(ThemeMode.Light, FeedUiState.Unreachable)

        composeRule.onNodeWithTag("feed_error").assertIsDisplayed()
        composeRule.onNodeWithText("外刊来源暂时不可用，请检查网络后重试。").assertIsDisplayed()
    }

    @Test
    fun fetchFailedFeedStateCarriesTheConcreteFailureLabel() {
        setFeed(ThemeMode.Light, FeedUiState.FetchFailed(FetchFailure.BodyNotFound))

        composeRule.onNodeWithTag("feed_fetch_failed").assertIsDisplayed()
        // 文案 = 常量表里那条「来源侧原因」，不许把原始响应体塞进界面。
        composeRule.onNodeWithText("抓取失败：页面里找不到正文，链接可能已失效").assertIsDisplayed()
    }

    @Test
    fun idleFeedStateRendersNothingButTheHeader() {
        setFeed(ThemeMode.Light, FeedUiState.Idle)

        // Idle 依旧不渲染任何内容：宿主在进入本层时已触发一次 openFeed()，第一帧起就应是 Loading；
        // 这条钉住「Idle 不是空白列表」，防止有人把它改成 `Empty` 的同形文案。
        composeRule.onNodeWithTag("app_screen_header").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_loading").assertDoesNotExist()
        composeRule.onNodeWithTag("feed_item_0").assertDoesNotExist()
    }

    @Test
    fun backFromTheFeedLayerIsTheHeaderActionNotAnInlineCollapse() {
        var backed = 0
        setFeed(ThemeMode.Light, FeedUiState.Loading, onBack = { backed++ })

        // 二级层的返回位走共享页头（`AppScreenHeader` 的 48dp 命中区，contentDescription「返回」）；
        // 原内联区没有这一层，本条就是「多一层导航」这笔代价的行为面。
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backed)
    }

    // ---------- ② 浅色档：搬家后逐值不变 ----------

    /**
     * 浅色档：页底与来源署名墨必须仍是浅套那两枚 token。
     *
     * 原内联区读的是 `MintBackground` / `MintPrimaryDark` / `MintTextMuted`，而这三枚在批 1 Task 3
     * 之后就是**组合期读 `LocalAppColors` 的别名**，所以本层换成直读 `appColors()` 在浅色侧是逐值等价
     * （浅套 `background` = #F1FBF5、`text1` = #1C1C1E）。这条就是把「等价」钉成像素，防止搬家顺手换档。
     */
    @Test
    fun the_light_feed_layer_still_paints_the_light_set_background_and_ink() {
        setFeed(ThemeMode.Light, readyFeed())

        val background = dominantRgb("reading_feed_screen")
        assertEquals(
            "浅色档页底必须是浅套 background（期望 ${hex(rgb(AppColorLight.background))}，实际 ${hex(background)}）",
            rgb(AppColorLight.background),
            nearestWithinTolerance(background, rgb(AppColorLight.background)),
        )
        val ink = extremeContrastRgb("feed_source", background)
        assertEquals(
            "来源署名墨必须是浅套 text1（期望 ${hex(rgb(AppColorLight.text1))}，实际 ${hex(ink)}）",
            rgb(AppColorLight.text1),
            nearestWithinTolerance(ink, rgb(AppColorLight.text1)),
        )
        assertContrastAtLeast("浅套 text1 压浅套页底", ink, background)
    }

    // ---------- ③ 深色档：换套证据（本层唯一的深色证据） ----------

    /**
     * 深色档（测试内显式 `MintGlassTheme(themeMode = ThemeMode.Dark)`）：页底翻成深套 `background`，
     * 来源署名墨翻成深套 `text1`，且这条 4.5:1 闸对「浅套写死的墨」必须判红。
     *
     * 三条自检，缺一不可（否则本例可能靠巧合绿）：
     *  ①**换套判别力**：浅/深两套 `background` 的最大逐通道差远大于容差，浅色档踩不进来；
     *  ②**取样点判别力**：页底众数必须离该套两档正文墨都足够远，证量的是填充而不是字身
     *    （大字号档下众数区被字挤占时这条先红，而不是让容差断言靠某个字身像素蒙过）；
     *  ③**反空转**：把浅套 `text1`（#1C1C1E）喂进同一条正文闸压深套页底，必须不合格——
     *    它若 ≥4.5，说明本层的「墨随套换」这件事根本没有判据。
     */
    @Test
    fun the_dark_feed_layer_swaps_both_the_background_and_the_source_ink() {
        assertTrue(
            "深浅两套页底必须真的不同值，否则本用例的换套判据没有对象",
            AppColorLight.background.toArgb() != AppColorDark.background.toArgb(),
        )
        setFeed(ThemeMode.Dark, readyFeed())

        val background = dominantRgb("reading_feed_screen")
        val darkBackground = rgb(AppColorDark.background)
        assertEquals(
            "深色档页底必须是深套 background（期望 ${hex(darkBackground)} ±$CHANNEL_TOLERANCE，实际 ${hex(background)}；" +
                "停在 ${hex(rgb(AppColorLight.background))} 就是本层没读到套色）",
            0,
            maxChannelDelta(background, darkBackground).let { if (it <= CHANNEL_TOLERANCE) 0 else it },
        )
        // 自检①：容差的两倍必须远小于两套页底的最大逐通道差，否则「任何非深底都算过」。
        assertTrue(
            "容差必须远小于浅/深两套 background 的最大逐通道间距，否则本用例失去换套判别力",
            CHANNEL_TOLERANCE * 2 < maxChannelDelta(rgb(AppColorLight.background), darkBackground),
        )
        // 自检②：取样像素离深套两档墨都足够远 ⇒ 量到的是页底填充而不是字身。
        val nearestInkDelta = minOf(
            maxChannelDelta(background, rgb(AppColorDark.text1)),
            maxChannelDelta(background, rgb(AppColorDark.text2)),
        )
        assertTrue(
            "页底取样必须明显区别于该套正文墨，否则量到的可能是字身而不是填充：取样 ${hex(background)}，" +
                "离最近一档墨只有 $nearestInkDelta 档（门槛 $INK_MIN_CHANNEL_DELTA）",
            INK_MIN_CHANNEL_DELTA <= nearestInkDelta,
        )

        val ink = extremeContrastRgb("feed_source", background)
        assertNotEquals(
            "墨核不能就是页底本身：若 feed_source 没渲染出字（文案改了/节点空了），" +
                "下面的对比断言会退化成「底压底 = 1:1」，那条要红得可读",
            background,
            ink,
        )
        assertEquals(
            "深色档来源署名墨必须是深套 text1（期望 ${hex(rgb(AppColorDark.text1))}，实际 ${hex(ink)}）",
            rgb(AppColorDark.text1),
            nearestWithinTolerance(ink, rgb(AppColorDark.text1)),
        )
        assertContrastAtLeast("深套 text1 压深套页底", ink, background)
        // 自检③：反空转——浅套那枚写死的墨喂进同一条闸必须不合格。
        val lightInkOnDarkBackground = contrastRatio(rgb(AppColorLight.text1), darkBackground)
        assertTrue(
            "反空转失控：浅套 text1 ${hex(rgb(AppColorLight.text1))} 压深套页底 ${hex(darkBackground)} 实测 " +
                "${lightInkOnDarkBackground.r2()}，已 ≥$BODY_AA_RATIO —— 那本层「墨随套换」就没有判据",
            lightInkOnDarkBackground < BODY_AA_RATIO,
        )
    }

    // ---------- 取样 ----------

    /** 一次 `setContent`：本类每个方法只调自己那一次，参数只有档位、Feed 状态与两个回调。 */
    private fun setFeed(
        themeMode: ThemeMode,
        feed: FeedUiState,
        onBack: () -> Unit = {},
        onFetchFeedItem: (FeedItem) -> Unit = {},
    ) {
        composeRule.setContent {
            MintGlassTheme(themeMode = themeMode) {
                ReadingFeedScreen(feed = feed, onBack = onBack, onFetchFeedItem = onFetchFeedItem)
            }
        }
    }

    /** 像素腿用的假列表：两条足够让 `feed_source` 有字可取，又不会把取样区撑出屏幕。 */
    private fun readyFeed(): FeedUiState.Ready =
        FeedUiState.Ready(
            items = listOf(feedItem(0), feedItem(1)),
            sourceDisplayName = SOURCE_NAME,
        )

    /** 指定节点不透明像素直方图的众数 = 该处的实际填充。 */
    private fun dominantRgb(tag: String): Int {
        val counts = HashMap<Int, Int>()
        forEachOpaquePixel(nodeBitmap(tag)) { rgb -> counts[rgb] = (counts[rgb] ?: 0) + 1 }
        val winner = counts.maxByOrNull { it.value }
        return checkNotNull(winner) { "节点 $tag 没有不透明像素，取不到页底（取样对象根本没渲染？）" }.key
    }

    /** 该节点里离 [reference] 亮度最远的那枚不透明像素 = 墨核（口径见类注释）。 */
    private fun extremeContrastRgb(tag: String, reference: Int): Int {
        val base = luminance(reference)
        var best = reference
        var bestGap = -1.0
        forEachOpaquePixel(nodeBitmap(tag)) { rgb ->
            val gap = abs(luminance(rgb) - base)
            if (gap > bestGap) {
                bestGap = gap
                best = rgb
            }
        }
        return best
    }

    private fun nodeBitmap(tag: String): Bitmap =
        composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()

    private inline fun forEachOpaquePixel(bitmap: Bitmap, action: (rgb: Int) -> Unit) {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val argb = bitmap.getPixel(x, y)
                if (((argb shr 24) and 0xFF) >= OPAQUE_ALPHA_FLOOR) action(argb and 0xFFFFFF)
            }
        }
    }

    /**
     * 「落在容差内就归零，否则返回实际偏差」——让 [assertEquals] 直接把偏差档数写进失败消息，
     * 免得判红时只知道两个整数不相等（同一手法见 [ThemeModeSwitchTest] 的容器腿）。
     */
    private fun nearestWithinTolerance(actual: Int, expected: Int): Int =
        if (maxChannelDelta(actual, expected) <= CHANNEL_TOLERANCE) expected else actual

    private fun maxChannelDelta(a: Int, b: Int): Int {
        fun channel(shift: Int) = abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))
        return maxOf(channel(16), channel(8), channel(0))
    }

    private fun rgb(color: Color): Int = color.toArgb() and 0xFFFFFF

    private fun hex(rgb: Int): String = String.format("#%06X", rgb)

    private fun contrastRatio(a: Int, b: Int): Double {
        val hi = maxOf(luminance(a), luminance(b))
        val lo = minOf(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun Double.r2(): String = String.format(Locale.US, "%.3f", this)

    private fun assertContrastAtLeast(label: String, ink: Int, background: Int) {
        val ratio = contrastRatio(ink, background)
        assertTrue(
            "$label 必须达正文 AA $BODY_AA_RATIO:1（墨 ${hex(ink)}，底 ${hex(background)}，实测 ${ratio.r2()}）",
            BODY_AA_RATIO <= ratio,
        )
    }

    /** WCAG 2.1 相对亮度；与 JVM 侧 `ui/SourceRatchet.kt` 的 `luminance` 同式（见类注释）。 */
    private fun luminance(rgb: Int): Double {
        fun lin(channel: Int): Double {
            val s = (channel and 0xFF) / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin(rgb shr 16) + 0.7152 * lin(rgb shr 8) + 0.0722 * lin(rgb)
    }

    private fun feedItem(index: Int) = FeedItem(
        title = "测试外刊标题 $index",
        articleUrl = "https://example.com/article-$index",
        publishedAtEpochMillis = 1_760_000_000_000L + index,
        summary = "摘要 $index",
    )

    private companion object {
        const val SOURCE_NAME = "测试外刊源"

        /**
         * 像素容差：`captureToImage()` 是离屏渲染，不经显示链色管，实测取样像素与 token 逐通道差为 0，
         * 留 2 只抗未来取整（同一档位与理由见 [ThemeModeSwitchTest] 的 `SURFACE_CHANNEL_TOLERANCE`）。
         */
        const val CHANNEL_TOLERANCE = 2

        /** 正文 AA 阈值，与 JVM 侧同一档（`ReadingHomeLayoutContractTest` 的 4.5）。 */
        const val BODY_AA_RATIO = 4.5

        /** 只排掉真正透明的空白区，保留抗锯齿边缘（同 [ThemeModeSwitchTest] 的 `OPAQUE_ALPHA_FLOOR`）。 */
        const val OPAQUE_ALPHA_FLOOR = 250

        /** 「取样点不是字身」的门槛：离该套最近一档墨至少差这么多档（同上 `INK_MIN_CHANNEL_DELTA`）。 */
        const val INK_MIN_CHANNEL_DELTA = 32
    }
}
