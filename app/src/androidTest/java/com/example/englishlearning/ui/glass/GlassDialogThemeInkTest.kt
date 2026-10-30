package com.example.englishlearning.ui.glass

import android.graphics.Bitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ui.components.glass.GlassDialog
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import com.example.englishlearning.ui.theme.appColors
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批 2 终审修复席 A 落点 1 的**配对像素**取证：`GlassDialog` 的容器填充与面板内前景必须同时来自
 * **当前那一套**主题 token，浅色侧还要证明迁移逐值不变。
 *
 * JVM 侧 `GlassPanelInkPairingContractTest` 只能钉源码形状（容器恰一处 `appColors().surfaceGlass`、
 * 八枚消费面板逐枚带显式 `color = appColors().text1`），钉不出组合期真的取到了本套的色。
 * 「容器迁了、墨没迁」在深色档是约 1.05:1 的当场不可读（批 1 终审 F4 / seatB 评审 C-1 同一族），
 * 只有像素能证。三条用例各管一件事：
 *  - **浅色侧不变量**：期望像素直接取**迁移前那两枚静态读点的值**（`AppPalette.GlassFill` /
 *    `AppPalette.TextPrimary`），用例内先当场断言它们与浅套 token 逐字相等，再拿这组旧值量新渲染面。
 *    「改完浅色像素用例仍绿」是本次迁移没把事情改坏的唯一硬证据。
 *  - **深色侧配对**：容器必须是**深玻璃**（`AppColorDark.surfaceGlass`）、前景必须是**深套浅墨**
 *    （`AppColorDark.text1`），两者压在一起达正文 AA；反向还不许浅套那枚写死的 `AppPalette.TextPrimary`
 *    出现在深色面板的字身上。
 *  - **兜底可滚**：内容超过 `heightIn(max = 560.dp)` 时面板高度被钉在上限内、最后一行靠滚动可达
 *    （修复前是「面板长到窗口外、按钮被裁掉」那一族 F5-03 缺陷；宿主级长列表取证见
 *    `AppScreenOverlayContainerTest` 的 dozen-books 那一例）。
 *
 * 取样口径四条，都不是随手写的：
 *  - **容器底取节点位图的不透明像素众数**（`captureToImage()` 只截该节点自身图层，界外是透明区）：
 *    面积最大的一档必定是容器填充，圆角与抗锯齿边缘是少数派；众数不依赖版面细节。
 *  - **墨核取「离容器众数足够远」的那一族里的众数**，不是全局众数：上面那条「只截自身图层」的前提在
 *    API 30 模拟器成立、**在真机（MIUI / Android 13 / 440dpi）不成立**——标题位图拿到的是与面板合成后的
 *    内容，全局众数落在玻璃填充 `#F1FDFDFD`（正是下面记的「浅套投影渗色」那一档），「墨核」被量成容器
 *    而与期望差 222 档。排除贴容器族（[INK_MIN_DELTA_FROM_BACKDROP]）+ 成族下限（[INK_MIN_FAMILY_COUNT]）
 *    之后，两种设备行为归一：剩下的像素里面积最大的一档就是字身核心，凑不出族就当场判红。
 *  - **alpha 参与取样键**：`surfaceGlass` 是**半透明**填充（浅 `0xEF`／深 `0xE6`），本包先例
 *    `ThemeModeSwitchTest.OPAQUE_ALPHA_FLOOR = 250` 会把玻璃填充整个排掉（239 < 250），所以此处按
 *    [GLASS_ALPHA_FLOOR] 取样，并把 alpha 纳入判据——深套 `surface`（`#FF17201C`）与深套
 *    `surfaceGlass`（`#E617201C`）RGB 逐字相同，不比 alpha 就分不清「画了玻璃」与「漏画玻璃透出实底」。
 *  - **容器分两条腿判（RGB + alpha）**：`Surface` 的 24dp 投影会从半透明填充背后渗上来，
 *    实测浅套 `#F2FCFCFC`、深套 `#EB171F1B`（alpha +3/+5、RGB −3/−1）。渗色只会把 alpha **往上推**，
 *    所以 alpha 腿判「≥ 该套玻璃且增幅 ≤ 容差」，RGB 腿按容差判；不把生产几何抄进期望值。
 *  - **容差刻意取小 + 四条自检**（同 `ThemeModeSwitchTest` 的口径）：换套判别力、实底判别力（alpha 腿）、
 *    相邻填充判别力、取样点判别力各由一条断言当场钉住，判据不许退化成「任意非透明都算过」。
 *
 * 夹具用 `style = AppType.Headline` + 显式 `color = appColors().text1` 的**生产面板形状**
 * （八枚消费点就是这么写的），不在用例里自造替身；`themeMode` 显式传 `Light`／`Dark`，
 * 取样不受平台夜间档影响。每个方法只调一次 `setContent`（同方法两次真机必崩，Task 9 实测）。
 */
@RunWith(AndroidJUnit4::class)
class GlassDialogThemeInkTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * 浅色档：容器 = 迁移前 `AppPalette.GlassFill` 的逐字取值，前景 = 迁移前 `AppPalette.TextPrimary`
     * 的逐字取值。断言顺序刻意是「先证 token ≡ 旧静态值，再证像素 = 旧静态值」，
     * 于是这一例同时是浅色侧的**值不变量**与**渲染不变量**。
     */
    @Test
    fun the_light_glass_dialog_still_paints_the_pre_migration_glass_and_ink() {
        assertEquals(
            "浅套 surfaceGlass 必须与迁移前的静态填充 AppPalette.GlassFill 逐字相同，否则浅色观感会随迁移漂移",
            AppPalette.GlassFill.toArgb(),
            AppColorLight.surfaceGlass.toArgb(),
        )
        assertEquals(
            "浅套 text1 必须与迁移前的静态前景 AppPalette.TextPrimary 逐字相同（八枚面板的前景同源靠的就是它）",
            AppPalette.TextPrimary.toArgb(),
            AppColorLight.text1.toArgb(),
        )
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) { PanelProbe() }
        }
        val ink = assertPanelPaints(AppPalette.GlassFill.toArgb(), AppPalette.TextPrimary.toArgb())
        // 反向：深套那档墨（#E9F2EC）不许出现在浅色面板的字身上（前景被写成深套常量就在这里红）。
        assertInkIsNotTheOtherSet(ink, AppColorDark.text1.toArgb())
    }

    /**
     * 深色档：证「暗玻璃 + 浅墨」这一对。迁移前容器读静态 `GlassFill`（白玻璃）、前景吃 `AppType`
     * 自带的静态浅墨（#1C1C1E），深色档分别塌成「白面板」与「深墨压深玻璃」；
     * 这两支都由容器/前景的期望值断言当场判红（浅白玻璃与深玻璃 RGB 最大通道差 232，远过容差）。
     */
    @Test
    fun the_dark_glass_dialog_pairs_dark_glass_with_dark_set_ink() {
        assertTrue(
            "深浅两套玻璃必须真的不同值，否则本用例的换套判据没有对象",
            AppColorLight.surfaceGlass.toArgb() != AppColorDark.surfaceGlass.toArgb(),
        )
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Dark) { PanelProbe() }
        }
        val ink = assertPanelPaints(AppColorDark.surfaceGlass.toArgb(), AppColorDark.text1.toArgb())
        // 反向：迁移前写死的浅套前景（#1C1C1E）不许出现在深色面板的字身上——
        // 它压深玻璃只有约 1.05:1，正是批 1 终审 F4 说的那一侧。
        assertInkIsNotTheOtherSet(ink, AppPalette.TextPrimary.toArgb())
    }

    /**
     * 兜底可滚：40 行内容远超 560.dp 上限时，面板高度必须被钉在上限内、最后一行必须靠滚动可达。
     *
     * 迁移前（没有 `heightIn` + `verticalScroll`）面板按内容长到窗口之外：最后一行既不在视口内、
     * 树侧也到不了（`performScrollTo()` 找不到可滚祖先会当场抛），设备字号 2.0 叠十几册导出列表时
     * 「取消」就是这么丢的。上限按 `heightIn(max = 560.dp)` 逐字钉（+1dp 只抗 dp→px 取整）。
     */
    @Test
    fun tall_dialog_content_stays_reachable_through_the_scroll_fallback() {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                GlassDialog(onDismiss = {}, modifier = Modifier.testTag(PANEL_TAG)) {
                    Text(text = TALL_TITLE, style = AppType.Headline, color = appColors().text1)
                    repeat(TALL_ROW_COUNT) { index ->
                        Text(
                            text = "导出词书条目 $index",
                            style = AppType.Body,
                            color = appColors().text1,
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .testTag("glass_scroll_row_$index"),
                        )
                    }
                }
            }
        }
        val bounds = composeRule.onNodeWithTag(PANEL_TAG).fetchSemanticsNode().boundsInRoot
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val maxPanelHeightPx = PANEL_MAX_HEIGHT_DP * density + density
        assertTrue(
            "面板高度必须被 heightIn(max = 560.dp) 钉住（迁移前无上限，40 行会把面板长到窗口外）：" +
                "实测高 ${bounds.height}px，上限 ${maxPanelHeightPx}px（560dp + 1dp 取整余量），宽 ${bounds.width}px",
            bounds.height <= maxPanelHeightPx,
        )
        composeRule.onNodeWithTag("glass_scroll_row_${TALL_ROW_COUNT - 1}")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** 生产面板形状的取样夹具：容器交房内共享玻璃档，前景按八枚消费点的写法逐枚显式取主题墨。 */
    @Composable
    private fun PanelProbe() {
        GlassDialog(onDismiss = {}, modifier = Modifier.testTag(PANEL_TAG)) {
            Text(
                text = PANEL_TITLE,
                style = AppType.Headline,
                color = appColors().text1,
                modifier = Modifier.testTag(TITLE_TAG),
            )
            Text(text = "面板正文用于取墨核", style = AppType.Body, color = appColors().text1)
        }
    }

    /**
     * 面板的两条正向判据（容器 RGB 腿 + alpha 腿）+ 标题墨判据 + 四条自检。
     *
     * **容器为什么要分两条腿**：`Surface(shadowElevation = 24.dp)` 的投影画在形状之内的图层下方，
     * 而玻璃填充是半透明的，投影会从背后**渗上来**——实测浅套取到 `#F2FCFCFC`（期望 `#EFFFFFFF`：
     * alpha +3、RGB −3）、深套取到 `#EB171F1B`（期望 `#E617201C`：alpha +5、RGB −1）。
     * 掺进期望值重算投影是不可取的（那是把生产几何抄进用例），所以按渗色的**单调性**判：
     * 叠加只会让 alpha **变大**（`a_out = a_fill + a_backdrop·(1−a_fill) ≥ a_fill`）、让 RGB 向 backdrop 偏一点，
     * 于是 alpha 腿取「≥ 期望值且离期望值 ≤ 容差」，RGB 腿按容差判——
     * 渗色方向不可能把「画成不透明实底」这种缺陷往下拉，alpha 腿照样判红（详见下面自检②）。
     */
    private fun assertPanelPaints(expectedGlass: Int, expectedInk: Int): Int {
        val expectedAlpha = (expectedGlass ushr 24) and 0xFF
        // 两条腿读**两张切片**（2026-10-31 批 4 全量 QA 订正取样口径，容差与阈值一格未动）：
        //  - alpha 腿读「全档众数」——它就是投影渗色的证据，判据仍是「≥ 该套玻璃且增幅 ≤ 容差」；
        //  - RGB / 墨 / 对比三腿读「alpha 恰等于该套玻璃」的那一档众数——那是填充**本身**，
        //    没有被背后 24dp 投影染过。为什么必须分开：填充 alpha 在批 0 从 `0xEF` 改成原型的 `0xE0`
        //    （`surfaceGlass`），投影透过率随之从 6.3% 翻到 12.2%，实测众数被推到 #E6F8F8F8
        //    （RGB −7、alpha +6），恰好越过 6 档容差；而位图里纯填充像素 #E0FFFFFF 仍有万余枚。
        //    这不是放宽：把「容器到底画了哪枚填充」的读数从「被渗色污染的档」换成「填充原值」，
        //    同时实底仍由 alpha 腿与「找不到纯填充像素即判红」两条把死。
        val containerAll = modeOfPanelPixels()
        val container = modeOfPanelPixels(alphaExactly = expectedAlpha)
        assertRgbWithinTolerance("面板容器底（玻璃填充）", container, expectedGlass, GLASS_CHANNEL_TOLERANCE)
        val actualAlpha = (containerAll ushr 24) and 0xFF
        assertTrue(
            "面板容器底的 alpha 必须停在「该套玻璃」这一档：深套 surface（#FF17201C）与 surfaceGlass（#E617201C）" +
                "RGB 逐字相同，不比 alpha 就分不清「画了玻璃」与「漏画玻璃画成实底」；" +
                "容许投影从半透明填充背后渗上来（alpha 只增不减），但增幅不得超过 $GLASS_ALPHA_TOLERANCE 档。" +
                "期望 ≥${expectedAlpha}（${expectedGlass.hex()}）、≤${expectedAlpha + GLASS_ALPHA_TOLERANCE}，实际 $actualAlpha（${containerAll.hex()}）",
            expectedAlpha <= actualAlpha && actualAlpha <= expectedAlpha + GLASS_ALPHA_TOLERANCE,
        )
        val ink = modeOfTitleInkPixels(container)
        assertWithinChannelTolerance("标题墨核（面板前景）", ink, expectedInk, INK_CHANNEL_TOLERANCE)
        // 自检（取样点判别力）：墨核与容器必须是不同像素。面板里根本没画出字时，
        // 下面那条对比断言会退化成「容器压容器 = 1:1」，这条把失配原因先说清。
        assertTrue(
            "墨核必须与容器底不同像素（否则本用例量的可能是空白而不是字身）：墨核 ${ink.hex()}，容器 ${container.hex()}",
            ink != container,
        )
        val contrast = contrastRatio(ink and 0xFFFFFF, container and 0xFFFFFF)
        assertTrue(
            "标题墨压玻璃容器必须达正文 AA $NORMAL_TEXT_AA_RATIO:1（浅套写死的静态墨压深玻璃只有约 1.05:1）：" +
                "墨核 ${ink.hex()}，容器 ${container.hex()}，实测 $contrast",
            NORMAL_TEXT_AA_RATIO <= contrast,
        )
        // 自检①（换套判别力）：容差必须**小于**两套玻璃的 RGB 间距，否则「另一套」也算命中。
        val glassSetSeparation = rgbChannelDelta(AppColorLight.surfaceGlass.toArgb(), AppColorDark.surfaceGlass.toArgb())
        assertTrue(
            "容器 RGB 容差必须小于浅/深两套玻璃的间距（实测 $glassSetSeparation 档），否则本用例失去换套判别力",
            GLASS_CHANNEL_TOLERANCE < glassSetSeparation,
        )
        // 自检②（实底判别力 / alpha 腿）：容差必须小于「玻璃 alpha」到「不透明实底 alpha」的间距，
        // 否则「画成 opaque surface」会被判成通过（浅/深两档离 0xFF 分别只差 16 / 25 档）。
        val nearestOpaqueAlphaGap = minOf(
            abs(0xFF - ((AppColorLight.surfaceGlass.toArgb() ushr 24) and 0xFF)),
            abs(0xFF - ((AppColorDark.surfaceGlass.toArgb() ushr 24) and 0xFF)),
        )
        assertTrue(
            "alpha 容差必须小于玻璃与不透明实底的 alpha 间距（最近一档只有 $nearestOpaqueAlphaGap 档）：容差 $GLASS_ALPHA_TOLERANCE",
            GLASS_ALPHA_TOLERANCE < nearestOpaqueAlphaGap,
        )
        // 自检③（相邻填充判别力）：容差必须小于深套玻璃与该套**不透明**相邻填充的最近间距，
        // 否则「容器漏画、透出别的填色」会在本用例蒙过（渗色实测 ≤3 档，容差 6 仍容不下这些档的最近一档）。
        //
        // 这里刻意**不含 `background`**：轻松档（2026-10-27 裁决 1）把深套页底从 #0E1512 抬到 #111A16，
        // 它与 surface #17201C 的逐通道间距只余 6 档，正好等于本容差 —— 按旧口径这条自检恒红（真机与
        // API 30 模拟器实测同红，与设备无关）。但「容器漏画、透出页底」这条失败模式已被上面那条 alpha 腿
        // 挡死：`background` 是不透明 0xFF、玻璃是 0xE6，只要它透上来 alpha 就跳出 [E6, E6+8]，比 RGB
        // 判据更早红。于是 RGB 容差只需要区分「同为半透明」的相邻档（本套没有第二枚半透明填充），
        // 余下四枚不透明档与玻璃的间距是 tint 10 / tint2 16 / tint3 21 / outlineSoft 12，容差 6 仍有余量。
        val nearestAdjacentFill = minOf(
            rgbChannelDelta(AppColorDark.surfaceGlass.toArgb(), AppColorDark.tint.toArgb()),
            rgbChannelDelta(AppColorDark.surfaceGlass.toArgb(), AppColorDark.tint2.toArgb()),
            rgbChannelDelta(AppColorDark.surfaceGlass.toArgb(), AppColorDark.tint3.toArgb()),
            rgbChannelDelta(AppColorDark.surfaceGlass.toArgb(), AppColorDark.outlineSoft.toArgb()),
        )
        assertTrue(
            "容器 RGB 容差必须小于深套玻璃到相邻不透明填充档的最近间距（实测 $nearestAdjacentFill 档）：容差 $GLASS_CHANNEL_TOLERANCE",
            GLASS_CHANNEL_TOLERANCE < nearestAdjacentFill,
        )
        return ink
    }

    /** 容器 RGB 腿：丢掉 alpha 后逐通道比较（理由见 [assertPanelPaints] 的分腿说明）。 */
    private fun assertRgbWithinTolerance(label: String, actual: Int, expected: Int, tolerance: Int) {
        val delta = rgbChannelDelta(actual, expected)
        assertEquals(
            "$label 的 RGB 必须落在期望 token 取值的逐通道 ±$tolerance 之内（alpha 另腿判）：" +
                "期望 ${expected.hex()}，实际 ${actual.hex()}，最大通道差 $delta",
            0,
            if (delta <= tolerance) 0 else delta,
        )
    }

    /**
     * 反向判据：墨核离**另一套**的墨必须足够远。
     * 只断「等于本套墨」时，若另一套墨恰好也落在容差内（深玻璃 RGB 与浅套墨 #1C1C1E 只差 5/4/2 档，
     * 这类「容器与旧墨近似」正是本仓踩过的坑），写死常量的缺陷就可能蒙过；这条把距离当场钉住。
     */
    private fun assertInkIsNotTheOtherSet(ink: Int, otherSetInk: Int) {
        val delta = rgbChannelDelta(ink, otherSetInk)
        assertTrue(
            "面板前景不得取另一套的墨（写死常量或漏一处主题读点就会命中）：" +
                "实测 ${ink.hex()}，离另一套 ${otherSetInk.hex()} 只有 $delta 档（门槛 $WRONG_SET_INK_MIN_CHANNEL_DELTA）",
            WRONG_SET_INK_MIN_CHANNEL_DELTA <= delta,
        )
    }

    private fun assertWithinChannelTolerance(label: String, actual: Int, expected: Int, tolerance: Int) {
        val delta = maxChannelDelta(actual, expected)
        assertEquals(
            "$label 必须落在期望 token 取值的逐通道 ±$tolerance 之内：期望 ${expected.hex()}，实际 ${actual.hex()}，最大通道差 $delta",
            0,
            if (delta <= tolerance) 0 else delta,
        )
    }

    /** 面板节点不透明像素（含 alpha 作键）的众数 = 玻璃填充。 */
    private fun modeOfPanelPixels(alphaExactly: Int? = null): Int =
        modeOfSampledPixels(
            composeRule.onNodeWithTag(PANEL_TAG).captureToImage().asAndroidBitmap(),
            alphaExactly = alphaExactly,
        )

    /**
     * 标题节点的墨核众数。
     *
     * `useUnmergedTree = true` 是正确性而不是风格：父级带点击语义时会合并子节点，
     * 合并树取到的是整块面板，位图里混进容器填充，众数就变成容器（口径同 `ThemeModeSwitchTest`）。
     *
     * **取样必须排除贴近容器的那一族**（[INK_MIN_DELTA_FROM_BACKDROP]），不能直接取全局众数：
     * 本类原口径默认「`captureToImage()` 只截节点自身图层，界外是透明区」，于是标题位图里唯一起眼的
     * 就是字身。该前提在 API 30 模拟器成立，但**在真机（MIUI / Android 13 / 440dpi）不成立**：
     * 标题节点位图拿到的是与面板合成后的内容，众数落在玻璃填充 #F1FDFDFD（正好是 KDoc 里记的
     * 「浅套投影渗色」那一档），于是「墨核」被量成容器、与期望的 #1F2B26 差 222 档而误红。
     * 排除贴容器族后两种情形都退化成同一件事：剩下的像素里面积最大的那一档就是字身核心
     * （抗锯齿过渡像素介于容器与墨之间且每枚值都不同，靠 [INK_MIN_FAMILY_COUNT] 再滤一遍）。
     */
    private fun modeOfTitleInkPixels(backdrop: Int): Int =
        modeOfSampledPixels(
            composeRule.onNodeWithTag(TITLE_TAG, useUnmergedTree = true)
                .captureToImage()
                .asAndroidBitmap(),
            awayFrom = backdrop,
        )

    /**
     * 逐像素遍历 alpha ≥ [GLASS_ALPHA_FLOOR] 的像素取直方图众数（保留玻璃 alpha，理由见类注释）。
     *
     * [awayFrom] 非空时只统计与它逐通道相差 ≥ [INK_MIN_DELTA_FROM_BACKDROP] 的像素，即「离容器足够远」那一族；
     * 该族若凑不出 [INK_MIN_FAMILY_COUNT] 像素，说明这张位图里根本没有字身（漏画字 / 取错节点），当场判红，
     * 不让众数退化回容器。
     */
    private fun modeOfSampledPixels(bitmap: Bitmap, awayFrom: Int? = null, alphaExactly: Int? = null): Int {
        val counts = HashMap<Int, Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val argb = bitmap.getPixel(x, y)
                val alpha = (argb ushr 24) and 0xFF
                if (alpha < GLASS_ALPHA_FLOOR) continue
                if (alphaExactly != null && alpha != alphaExactly) continue
                if (awayFrom != null && rgbChannelDelta(argb, awayFrom) < INK_MIN_DELTA_FROM_BACKDROP) continue
                counts[argb] = (counts[argb] ?: 0) + 1
            }
        }
        val winner = counts.maxByOrNull { it.value }
        checkNotNull(winner) {
            if (alphaExactly != null) {
                "节点位图里没有 alpha 恰为 $alphaExactly 的像素（尺寸 ${bitmap.width}x${bitmap.height}）——" +
                    "容器不是按该套玻璃 alpha 画的（画成实底 ⇒ 一个纯填充像素也没有）"
            } else {
                "节点位图里没有 alpha ≥ $GLASS_ALPHA_FLOOR 的像素（尺寸 ${bitmap.width}x${bitmap.height}），取样对象根本没渲染？"
            }
        }
        if (awayFrom != null) {
            assertTrue(
                "墨核成族下限：离容器 ${awayFrom.hex()} 足够远的像素只有 ${winner.value} 枚，不足 $INK_MIN_FAMILY_COUNT" +
                    "（尺寸 ${bitmap.width}x${bitmap.height}）——这张位图里没有字身，取样对象不是标题节点或缺字",
                winner.value >= INK_MIN_FAMILY_COUNT,
            )
        }
        return winner.key
    }

    /** 四个通道（含 alpha）的最大绝对差。 */
    private fun maxChannelDelta(a: Int, b: Int): Int {
        fun channel(shift: Int) = abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))
        return maxOf(channel(24), channel(16), channel(8), channel(0))
    }

    /** 只比 RGB 的最大绝对差（alpha 由另一条腿单独判，见 [assertPanelPaints]）。 */
    private fun rgbChannelDelta(a: Int, b: Int): Int = maxChannelDelta(a and 0xFFFFFF, b and 0xFFFFFF)

    private fun Int.hex(): String = String.format(Locale.US, "#%08X", this)

    /** WCAG 2.1 相对亮度（与 JVM 侧 `SourceRatchet.luminance` 同式；那份在 `src/test`，androidTest 看不见只能复刻）。 */
    private fun luminance(rgb: Int): Double {
        fun lin(channel: Int): Double {
            val s = (channel and 0xFF) / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(rgb shr 16) + 0.7152 * lin(rgb shr 8) + 0.0722 * lin(rgb)
    }

    private fun contrastRatio(a: Int, b: Int): Double {
        val hi = maxOf(luminance(a), luminance(b))
        val lo = minOf(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }
}

private const val PANEL_TAG = "glass_dialog_theme_ink_panel"
private const val TITLE_TAG = "glass_dialog_theme_ink_title"
private const val PANEL_TITLE = "确认发送请求"
private const val TALL_TITLE = "选择要导出的词书"
private const val TALL_ROW_COUNT = 40

/**
 * 面板高度上限判据：`GlassDialog` 的兜底是 `heightIn(max = 560.dp)`，这里按 561.dp 收
 * （+1dp 只抗 dp→px 取整）。迁移前没有这条上限，40 行内容会把面板长到窗口外，本条当场红。
 */
private const val PANEL_MAX_HEIGHT_DP = 560f

/** 正文 AA 阈值（与 `ThemeModeSwitchTest` / JVM 侧 `EntryRowInkTest` 同档）。 */
private const val NORMAL_TEXT_AA_RATIO = 4.5

/**
 * 「算不透明」的 alpha 下限：必须**低于**两档玻璃的 alpha（浅 `0xEF` = 239、深 `0xE6` = 230），
 * 否则容器填充会被整个排掉（本包先例 `ThemeModeSwitchTest.OPAQUE_ALPHA_FLOOR = 250` 对实底成立、
 * 对半透明玻璃不成立）。200 又高于圆角与边缘抗锯齿的多数过渡档，众数仍是填充。
 */
private const val GLASS_ALPHA_FLOOR = 200

/**
 * 容器玻璃 RGB 的逐通道容差：实测渗色（投影从半透明填充背后透出）带来 1~3 档偏差，
 * 留 6 档覆盖它，同时仍小于最近的相邻填充档间距（11 档）与两套玻璃间距（232 档）——两条自检当场钉住。
 */
private const val GLASS_CHANNEL_TOLERANCE = 6

/** 玻璃 alpha 的判别腿：容许渗色把 alpha 往上推，但增幅不得超过 8 档（离不透明实底最近一档差 16 档）。 */
private const val GLASS_ALPHA_TOLERANCE = 8

/** 墨核容差：字身核心像素应逐值等于墨，留 12 档只抗不同字号/字重下的覆盖不足。 */
private const val INK_CHANNEL_TOLERANCE = 12

/**
 * 墨核取样门槛：标题位图里离容器至少差这么多才算字身。
 *
 * 真机（MIUI / Android 13）的 `captureToImage()` 会把标题节点按「与面板合成后」返回，全局众数于是落在
 * 玻璃填充上（实测 `#F1FDFDFD`）；排除贴近容器的那一族后，两种设备行为才归一到同一件事。
 */
private const val INK_MIN_DELTA_FROM_BACKDROP = 64

/** 墨核成族下限：单发杂点不算字身（一行标题的字身核心有几百像素）。 */
private const val INK_MIN_FAMILY_COUNT = 25

/** 反向判据门槛：墨核离另一套墨必须远到这一点（两套墨实测间距 205/214/208 档）。 */
private const val WRONG_SET_INK_MIN_CHANNEL_DELTA = 32
