package com.example.englishlearning.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.AppGlyph
import com.example.englishlearning.ui.components.AppGlyphLayer
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.EntryIconBadge
import com.example.englishlearning.ui.components.InlineIconBadge
import com.example.englishlearning.ui.components.allowsInEntryContainer
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppThemePreferences
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.rememberAppThemeMode
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批 1 图标一览的**唯一呈现处**（计划 Task 12 Step 4b，控制者裁决补的一步）：
 * 批 1 不把底导与业务屏换成 `AppGlyph`（那是批 2/批 3），里程碑上真机看到的仍是旧 Canvas 字形，
 * 所以新字形 33 枚在第一棒只能在这里被看见——机器只判两条，其余交给目视清单
 * （清单与结论登记在 `docs/verification/ui-redesign-2026-10.md` 的 AC-R-05b 行）。
 *
 * 三视图（逐枚按 `AppIcon` 枚举序左到右、上到下排列，每行 [PER_ROW] 枚，便于按序号定位）：
 *  - `AppGlyph` 单层描边 24dp（`AppGlyphLayer.StrokeOnly`），墨色走主题通道 `appColors().text1`，
 *    所以深色轮本身就是「同一枚字形在两套墨色下都出得来」的证明；
 *  - `EntryIconBadge` 40dp 域渐变容器 + 两层：豁免表那几枚（Back/ChevronRight/ChevronDown/Close/Check）
 *    **必须跳过**——`IconBadge.kt` 开头的 `require` 会抛 `IllegalArgumentException`，
 *    那是设计层面的拦死（两层豁免枚不得进 ≥40dp 入口容器），不是本用例要绕开的障碍；
 *    跳过关系用「全部枚数 − 豁免枚数 == 进容器的枚数」这一结构等式当场钉住，不写会漂移的绝对数；
 *  - `InlineIconBadge` 30dp tint 底 + 17dp 单层描边：33 枚全上（豁免枚唯一的合法去处就是行内）。
 *
 * 四条平台坑（本仓 `android-connected-qa-loop` 记录，逐条都落进写法）：
 *  - `onNode…` 一律用成员形式（`composeRule.onNodeWithTag(...)`），不裸调顶层函数；
 *  - 不用 `onFirst()`/`onLast()` 这类恒真写法；
 *  - **一个 `@Test` 只 `setContent` 一次**（同一次测试两次 `setContent` 真机必崩）。
 *    所以「浅色/深色两轮」是**同一宿主里的两次 `captureToImage`**：先按 `Light` 落偏好再 `setContent`，
 *    第二张只写 `Dark`，靠 `rememberAppThemeMode()` 的 prefs 监听活链路重绘——不是第二次 `setContent`；
 *  - **connected 读不到颜色**：本类因此不断言任何具体色值。底色取截图左上角像素（容器 padding 外沿即页底），
 *    「换套真的发生了」取两轮整图像素哈希不相等这一事实，两条都不需要知道 token 是多少；
 *    token 取值归 `DesignTokenContractTest`，抄进本类只会长出第二份会漂移的真值。
 *
 * 图片**一律不入库**（与 QR 证据同红线）：写到应用外部文件目录（`getExternalFilesDir`，与
 * `SetupScreenScreenshotTest` 同处、`adb pull` 已验证可达），路径只打到 stdout；
 * 执行者 pull 到**仓库外**的临时目录目视，看完即删。
 *
 * 只在 `emulator-5574` 上跑（`adb install -r` + `am instrument`）；**绝禁 `connectedDebugAndroidTest`**
 * （它会清设备数据），真机 `bf353dda` 不跑本类、也不装测试包。
 */
@RunWith(AndroidJUnit4::class)
class IconGalleryRenderTest {
    @get:Rule val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    /** 起手与收尾都把偏好交还给「跟随系统」：不许给 emulator-5574 留下人为的强制深/浅色。 */
    @Before
    @After
    fun resetThemePreferenceToSystem() {
        AppThemePreferences.write(appContext, ThemeMode.System)
    }

    @Test
    fun app_glyph_stroke_only_view_rounds_render_non_blank_in_light_and_dark() {
        val light = renderRound("icon-gallery-appglyph-light.png", ThemeMode.Light) { cell ->
            AppGlyph(icon = cell.icon, tint = appColors().text1, size = 24.dp, layer = AppGlyphLayer.StrokeOnly)
        }
        val dark = reRenderAfterThemeFlip("icon-gallery-appglyph-dark.png", ThemeMode.Dark, previous = light)
        assertRoundRendered("AppGlyph 24dp", light)
        assertRoundRendered("AppGlyph 24dp", dark)
        assertThemeFlipReachedTheAssemblyPoint(light, dark)
    }

    @Test
    fun entry_badge_view_rounds_render_non_blank_in_light_and_dark_and_skip_the_exempt_icons() {
        val entryIcons = AppIcon.entries.filter { allowsInEntryContainer(it) }
        val exemptCount = AppIcon.entries.size - entryIcons.size
        assertTrue(
            "豁免表为空：EntryIconBadge 的 require 与 allowsInEntryContainer 已脱钩，本用例的「跳过」会变成假跳过",
            exemptCount > 0,
        )
        val light = renderRound("icon-gallery-entrybadge-light.png", ThemeMode.Light, icons = entryIcons) { cell ->
            EntryIconBadge(icon = cell.icon, domain = domainFor(cell.index))
        }
        val dark = reRenderAfterThemeFlip("icon-gallery-entrybadge-dark.png", ThemeMode.Dark, previous = light)
        assertRoundRendered("EntryIconBadge 40dp", light)
        assertRoundRendered("EntryIconBadge 40dp", dark)
        assertThemeFlipReachedTheAssemblyPoint(light, dark)
        assertEquals(
            "进容器的枚数与「全部枚数 − 豁免枚数」不等：过滤口径漂了（豁免枚进容器会让 require 直接抛）",
            AppIcon.entries.size - exemptCount,
            entryIcons.size,
        )
    }

    @Test
    fun inline_badge_view_rounds_render_every_icon_including_the_exempt_ones() {
        val light = renderRound("icon-gallery-inlinebadge-light.png", ThemeMode.Light) { cell ->
            InlineIconBadge(icon = cell.icon, domain = domainFor(cell.index))
        }
        val dark = reRenderAfterThemeFlip("icon-gallery-inlinebadge-dark.png", ThemeMode.Dark, previous = light)
        assertRoundRendered("InlineIconBadge 30dp/17dp", light)
        assertRoundRendered("InlineIconBadge 30dp/17dp", dark)
        assertThemeFlipReachedTheAssemblyPoint(light, dark)
    }

    // ---------- 宿主、取样与落盘 ----------

    /** 一次取样：整图哈希（判「有没有重绘」）+ 非底色像素占比（判「有没有画东西」）。 */
    private class Snapshot(val bitmap: Bitmap, val signature: Int, val inkRatio: Float)

    private class Cell(val index: Int, val icon: AppIcon)

    private class Round(val fileName: String, val mode: ThemeMode, val snapshot: Snapshot, val file: File)

    private fun renderRound(
        fileName: String,
        mode: ThemeMode,
        icons: List<AppIcon> = AppIcon.entries.toList(),
        glyph: @Composable (Cell) -> Unit,
    ): Round {
        AppThemePreferences.write(appContext, mode)
        composeRule.setContent {
            MintGlassTheme(themeMode = rememberAppThemeMode()) {
                Gallery(icons = icons, glyph = glyph)
            }
        }
        composeRule.waitForIdle()
        return save(fileName, mode, capture())
    }

    /**
     * 第二轮：**不再 `setContent`**（平台坑第三条），只翻偏好，让活链路重绘同一棵宿主。
     * `waitUntil` 的谓词里带副作用（抓图）是有意的：prefs 回调经主线程 executor 跳板落到 `mutableStateOf`，
     * 落地时机不由测试决定，只能轮询「整图哈希变了」；抓图本身只做像素取样，落盘留到判定完成后一次。
     */
    private fun reRenderAfterThemeFlip(fileName: String, mode: ThemeMode, previous: Round): Round {
        AppThemePreferences.write(appContext, mode)
        var latest: Snapshot? = null
        composeRule.waitUntil(timeoutMillis = 20_000) {
            val snapshot = capture()
            latest = snapshot
            snapshot.signature != previous.snapshot.signature
        }
        return save(fileName, mode, checkNotNull(latest) { "waitUntil 没有产生任何取样：$fileName" })
    }

    /**
     * 宿主必须自己铺主题底色：`captureToImage()` 只截该节点**自身图层**，不合成它身后的窗口，
     * 不铺底时深色轮的 PNG 会落成「浅底 + 淡墨」，人眼无法据此核验深色可读性（目视清单的证据就废了），
     * 而 `inkRatio` 的底色取样也只剩采样器自己造出来的白。铺了 `appColors().background` 之后，
     * 左上角取样仍是页底（8dp padding 落在底上），两条断言口径不变，深色轮才真的能看。
     */
    @Composable
    private fun Gallery(icons: List<AppIcon>, glyph: @Composable (Cell) -> Unit) {
        Column(
            modifier = Modifier
                .testTag(GALLERY_TAG)
                .background(appColors().background)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icons.withIndex().map { Cell(it.index, it.value) }.chunked(PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { cell -> glyph(cell) }
                }
            }
        }
    }

    private fun domainFor(index: Int): AppDomain = AppDomain.entries[index % AppDomain.entries.size]

    private fun capture(): Snapshot {
        val bitmap = composeRule.onNodeWithTag(GALLERY_TAG).captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        // 底色 = 截图自身左上角像素：容器有 8dp padding，左上角落在页底上。connected 读不到颜色，
        // 所以「什么算底色」只能由图自己给，不能由 token 给。
        val backdrop = bitmap.getPixel(0, 0)
        val different = pixels.count { it != backdrop }
        return Snapshot(bitmap = bitmap, signature = Arrays.hashCode(pixels), inkRatio = different.toFloat() / pixels.size)
    }

    private fun save(fileName: String, mode: ThemeMode, snapshot: Snapshot): Round {
        val dir = appContext.getExternalFilesDir(null) ?: error("应用外部文件目录不可用，无法落图标一览证据")
        val file = File(dir, fileName)
        FileOutputStream(file).use { out -> snapshot.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        // 路径只打到 instrumentation 输出；图片一律不入库（与 QR 证据同红线）。
        println("ICON_GALLERY_SAVED ${file.absolutePath}")
        return Round(fileName = fileName, mode = mode, snapshot = snapshot, file = file)
    }

    private fun assertRoundRendered(view: String, round: Round) {
        assertTrue(
            "$view 的 ${round.mode} 轮非底色像素占比为 0：容器里什么都没画出来（空绘制）。位图 ${round.file.name}",
            round.snapshot.inkRatio > 0f,
        )
        assertTrue(
            "$view 的 ${round.mode} 轮截图文件为空字节：写盘链路断了，目视清单无从核对。${round.file.absolutePath}",
            round.file.length() > 0L,
        )
    }

    private fun assertThemeFlipReachedTheAssemblyPoint(light: Round, dark: Round) {
        assertNotEquals(
            "${light.fileName} 与 ${dark.fileName} 逐像素相同：写 Dark 没走到装配点" +
                "（rememberAppThemeMode 的活链路断了，或本类违反了「一次 setContent」这一前提）",
            light.snapshot.signature,
            dark.snapshot.signature,
        )
    }

    private companion object {
        const val GALLERY_TAG = "icon_gallery_root"

        /** 每行枚数：33 枚落 5 行、28 枚落 4 行，容器高度在 emulator-5574 上无需滚动即可完整入镜。 */
        const val PER_ROW = 8
    }
}
