package com.example.englishlearning.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.export.WorksheetPdfRenderer
import com.example.englishlearning.learning.worksheet.WorksheetPage
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 默写纸预览屏导出按钮的**真像素取色核对**（QA 第三轮任务书 §3 设备清单 ②/③）。
 *
 * 任务书原文钉的是 `AppPalette.OnMintPrimary` 落在 `MintPrimary` 底上的读数；那两个名字在 UI 重设计轮
 * 已被 token 化为 `appColors().onStrong` / `strongSurface`（`WorksheetPreviewScreen.kt:190-191`），
 * 语义没变、只是台账里留的是旧名。本类量的就是改完之后设备上的实际合成结果，三个档位各一条：
 * 浅色 1.0 / 深色 1.0 / 浅色顶档 3.0。
 *
 * 为什么必须是像素而不是源码：`grep` 只能证明按钮写了 `contentColor = colors.onStrong`，
 * 证不了它最终落在哪块底上——按钮底是 `strongSurface`，禁用态换 `tint3`，且旧版这里是
 * `Color.White` 压浅绿（对比度修复就是本轮改的）。只有位图能回答「墨和底同屏时到底差多少」。
 *
 * 取样口径（三条测试共用 [measureExportButtonContrast]）：
 *  - 底 = `worksheet_export_action` 节点捕获面里出现次数最多的 ARGB（Button 的 `containerColor`
 *    是不透明实底，面积绝大多数是它，众数即底）；
 *  - 墨 = **标签节点**捕获面里、排除底色之后出现次数最多的 ARGB。标签节点必须是匹配节点中
 *    面积最小的那一个：`hasText` 在未合并树里**同时命中 Button 和它内部的 Text**
 *    （Button 通过 `MergeDescendantsSemantics` 把子节点文案并进自己的语义，且深度优先先序排在前），
 *    取 `[0]` 等于把按钮量了两遍，量出来的「墨」就是底——本类的「取样面重合」判据就是为这种情况写的；
 *  - 之所以还要「排除底色」：节点捕获面在未合并树里可能带身后合成结果（底 + 字），也可能只有字形层
 *    （字 + 透明，透明像素已被 alpha 门槛挡掉）；两种情形下「排除底色后的众数」都落在真实墨色上；
 *  - 标签捕获面必须**严格小于**按钮捕获面（宽或高至少一侧更小）——两侧同尺寸就是「把按钮量了两遍」，
 *    那种情况下「排除底色」剩下的只有焦点环与抗锯齿边，读数会假绿；
 *  - 候选墨桶必须真有 [MIN_INK_PIXELS] 个像素，否则「对比度达标」是取样面为空造成的假绿——单独判死，
 *    失败时把该捕获面的前几桶一起打出来，避免下次还要靠猜；
 *  - 判据用 WCAG 2.1 相对亮度式，阈值 4.5 = AA 正文（`AppType.Label` 是 12sp，
 *    不属于 large text，不能引用 3:1 的非文本档）。
 *
 * 量到的数值追加进设备侧 `worksheet-preview-color-evidence/report.tsv`，整屏 PNG 同目录落盘，
 * 用 `adb pull` 取回后附进走查册；证据不入库（AGENTS.md 隐私约束）。
 * 每条用例只 `setContent` 一次（同一次测试两次 `setContent` 真机必崩）。
 */
@RunWith(AndroidJUnit4::class)
class WorksheetPreviewColorEvidenceTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun exportButtonInkClearsWcagAAOnTheStrongSurfaceInLightTheme() {
        withPdf { file ->
            setContent(ThemeMode.Light, file, 1f)
            val screen = composeRule.onNodeWithTag("worksheet_preview_screen")
            screen.assertIsDisplayed()
            save("light-fs100", screen)
            record("light-fs100", measureExportButtonContrast())
        }
    }

    @Test
    fun exportButtonInkClearsWcagAAOnTheStrongSurfaceInDarkTheme() {
        withPdf { file ->
            setContent(ThemeMode.Dark, file, 1f)
            val screen = composeRule.onNodeWithTag("worksheet_preview_screen")
            screen.assertIsDisplayed()
            save("dark-fs100", screen)
            record("dark-fs100", measureExportButtonContrast())
        }
    }

    /**
     * 顶档取色 + 任务书 ③「按钮行长高不破版」留影。
     *
     * 吸底条与「导出 PDF」文案都用 `assertIsDisplayed()` 判——它读**裁剪后**矩形，
     * 顶档下若吸底条被挤出屏幕、或文案被 clip 吃掉一行，这条会当场红；
     * 与 `WorksheetPreviewScreenTest.touchTargetsStayAtLeast48dpAtTopFontScale`
     * （unclipped 尺寸口径）互补：一条证「还够大」，一条证「还在屏上且没被裁」。
     */
    @Test
    fun exportButtonKeepsItsContrastAndItsRowStaysOnScreenAtTopFontScale() {
        withPdf { file ->
            setContent(ThemeMode.Light, file, 3f)
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithTag("worksheet_preview_pages").fetchSemanticsNodes().isNotEmpty()
            }
            val screen = composeRule.onNodeWithTag("worksheet_preview_screen")
            screen.assertIsDisplayed()
            composeRule.onNodeWithTag("worksheet_preview_action_bar").assertIsDisplayed()
            composeRule.onNodeWithText(LABEL).assertIsDisplayed()
            save("light-fs300", screen)
            record("light-fs300", measureExportButtonContrast())
        }
    }

    private fun measureExportButtonContrast(): ContrastReading {
        val buttonPixels = composeRule.onNodeWithTag("worksheet_export_action")
            .captureToImage()
            .toPixelMap()
        val container = dominantOpaque(buttonPixels)
        assertTrue(
            "导出按钮取样面为空（captureToImage 拿到全透明位图），对比度判据会空转",
            container != null,
        )

        // 墨从**标签节点自己那层**取，不从按钮整层里挑「对比度最大的那桶」：instrumented 宿主里按钮会带
        // 一圈白色 focus indicator（贴着外沿、实测约 1000px，浅档下比标签的实心墨桶还多），拿它当墨
        // 就等于核了一条生产触屏上根本不出现的东西。
        val labelPixels = smallestNodeWithText(LABEL).captureToImage().toPixelMap()
        assertTrue(
            "标签捕获面与按钮捕获面同尺寸（${labelPixels.width}x${labelPixels.height}）：" +
                "取样面重合，`hasText` 命中的是最小节点这条前提没成立，量到的「墨」其实是底或焦点环",
            labelPixels.width < buttonPixels.width || labelPixels.height < buttonPixels.height,
        )
        val ink = dominantOpaqueExcluding(labelPixels, container!!.first)
        assertTrue(
            "「$LABEL」标签层取不到足量（>=$MIN_INK_PIXELS）非底色的不透明像素：文案没画出来，" +
                "无从核对对比度。底色 ${hex(container.first)}；标签捕获面前几桶：" +
                topBuckets(labelPixels, excluding = container.first),
            ink != null && ink.second >= MIN_INK_PIXELS,
        )
        val reading = ContrastReading(
            containerHex = hex(container.first),
            inkHex = hex(ink!!.first),
            containerPixels = container.second,
            inkPixels = ink.second,
            ratio = contrast(Color(container.first), Color(ink.first)),
        )
        assertTrue(
            "导出按钮墨底对比度不足 WCAG AA 正文 4.5：底 ${reading.containerHex}" +
                "（${reading.containerPixels}px）/ 墨 ${reading.inkHex}" +
                "（${reading.inkPixels}px）实测 ${"%.2f".format(reading.ratio)}",
            reading.ratio >= 4.5,
        )
        return reading
    }

    /**
     * `hasText` 命中的**面积最小**节点 = 真正画字的那个 Text。
     *
     * 未合并树里 Button 与它内部的 Text 都带这段文案（Button 把子节点语义并进来了），先序遍历把 Button
     * 排在 `[0]`；直接取 `[0]` 会量到按钮底，「墨 == 底」的假红就是这么来的（本会话实测）。
     */
    private fun smallestNodeWithText(text: String): SemanticsNodeInteraction {
        val matcher = composeRule.onAllNodes(hasText(text), useUnmergedTree = true)
        val nodes = matcher.fetchSemanticsNodes()
        assertTrue("页面上找不到文案「$text」，取样面选不出来", nodes.isNotEmpty())
        val smallestIndex = nodes
            .map { it.boundsInRoot.width * it.boundsInRoot.height }
            .withIndex()
            .minByOrNull { (_, area) -> area }!!
            .index
        return matcher[smallestIndex]
    }

    /** 位图里出现次数最多的**不透明** ARGB，连同它的像素数（null = 一面全透明）。 */
    private fun dominantOpaque(pixels: PixelMap): Pair<Int, Int>? =
        dominantOpaqueExcluding(pixels, exclude = null)

    /** 同上，但跳过 [exclude] 这一桶——用来在「底 + 字」合成面里挑出墨。 */
    private fun dominantOpaqueExcluding(pixels: PixelMap, exclude: Int?): Pair<Int, Int>? {
        val counts = HashMap<Int, Int>()
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                if (color.alpha > 0.999f) {
                    val argb = color.toOpaqueArgb()
                    if (argb != exclude) {
                        counts[argb] = (counts[argb] ?: 0) + 1
                    }
                }
            }
        }
        val top = counts.maxByOrNull { it.value } ?: return null
        return top.key to top.value
    }

    /** 失败诊断用：捕获面里（除 [excluding] 外）像素数最多的前几桶。 */
    private fun topBuckets(pixels: PixelMap, excluding: Int, limit: Int = 4): String {
        val counts = HashMap<Int, Int>()
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                if (color.alpha > 0.999f) {
                    val argb = color.toOpaqueArgb()
                    if (argb != excluding) {
                        counts[argb] = (counts[argb] ?: 0) + 1
                    }
                }
            }
        }
        if (counts.isEmpty()) return "（全是底色或全透明）"
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .joinToString(separator = ", ") { "${hex(it.key)}=${it.value}px" }
    }

    /**
     * 按钮所在的那一帧真状态：`enabled = !rendering && renderedFile != null`，所以必须喂一份
     * 真渲染出来的 PDF。空文件会让 `produceState` 里的 `WorksheetPreviewRenderer` 直接抛
     * （它返回 `List<Bitmap>`，不包 Result），测出来的「可点」也是假的——与既有
     * `theExportActionUnlocksOnceThePdfIsRendered` 同一个理由。
     */
    private fun withPdf(body: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rendered = WorksheetPdfRenderer(context)
            .render(pages = samplePages(), useFourLineGrid = true)
            .getOrThrow()
        try {
            body(rendered.file)
        } finally {
            rendered.file.delete()
        }
    }

    @Composable
    private fun host(themeMode: ThemeMode, renderedFile: File, fontScale: Float) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
            MintGlassTheme(themeMode = themeMode) {
                WorksheetPreviewScreen(
                    pages = samplePages(),
                    renderedFile = renderedFile,
                    rendering = false,
                    message = null,
                    onBack = {},
                    onExport = {},
                )
            }
        }
    }

    private fun setContent(themeMode: ThemeMode, renderedFile: File, fontScale: Float) {
        composeRule.setContent { host(themeMode, renderedFile, fontScale) }
    }

    private fun evidenceDirectory(): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        EVIDENCE_DIR,
    ).apply { mkdirs() }

    private fun save(name: String, node: SemanticsNodeInteraction) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        File(evidenceDirectory(), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun record(name: String, reading: ContrastReading) {
        val line = listOf(
            name,
            reading.containerHex,
            reading.containerPixels.toString(),
            reading.inkHex,
            reading.inkPixels.toString(),
            "%.2f".format(reading.ratio),
        ).joinToString(separator = "\t")
        File(evidenceDirectory(), "report.tsv").appendText(line + "\n")
    }

    private data class ContrastReading(
        val containerHex: String,
        val inkHex: String,
        val containerPixels: Int,
        val inkPixels: Int,
        val ratio: Double,
    )

    private fun Color.toOpaqueArgb(): Int = android.graphics.Color.argb(
        255,
        (red * 255f).roundToInt(),
        (green * 255f).roundToInt(),
        (blue * 255f).roundToInt(),
    )

    private fun hex(argb: Int): String = String.format(
        "#%02X%02X%02X",
        android.graphics.Color.red(argb),
        android.graphics.Color.green(argb),
        android.graphics.Color.blue(argb),
    )

    private fun channelLuminance(value: Double): Double =
        if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    private fun relativeLuminance(color: Color): Double =
        0.2126 * channelLuminance(color.red.toDouble()) +
            0.7152 * channelLuminance(color.green.toDouble()) +
            0.0722 * channelLuminance(color.blue.toDouble())

    private fun contrast(a: Color, b: Color): Double {
        val first = relativeLuminance(a)
        val second = relativeLuminance(b)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    private companion object {
        const val EVIDENCE_DIR = "worksheet-preview-color-evidence"

        /** 吸底那枚按钮的标签文案，同时是取样节点的选择器。 */
        const val LABEL = "导出 PDF"

        /** 抗锯齿中间灰桶的数量远小于实心墨桶；低于这个数的桶不当「墨」候选，防止取到混色。 */
        const val MIN_INK_PIXELS = 200

        fun samplePages(): List<WorksheetPage> = (1..2).map {
            WorksheetPage(pageNumber = it, pageCount = 2, direction = null, answers = emptyList())
        }
    }
}
