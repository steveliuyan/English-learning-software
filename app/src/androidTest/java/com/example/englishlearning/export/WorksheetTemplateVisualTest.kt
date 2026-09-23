package com.example.englishlearning.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetDocumentBuilder
import com.example.englishlearning.learning.worksheet.WorksheetItem
import com.example.englishlearning.learning.worksheet.WorksheetPaginator
import com.example.englishlearning.learning.worksheet.WorksheetSettings
import com.example.englishlearning.learning.worksheet.WorksheetSource
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 把三份导出模板真实渲染成 PNG，用于人工核对版式与真机取证。
 *
 * 图片写入 `targetContext.getExternalFilesDir("worksheet-previews")`，测试不依赖任何应用数据。
 */
@RunWith(AndroidJUnit4::class)
class WorksheetTemplateVisualTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rendersEveryTemplateToPngForInspection() {
        val outputDirectory = File(context.getExternalFilesDir(null), "worksheet-previews").apply { mkdirs() }
        val source = WorksheetSource(
            localDate = LocalDate.of(2026, 9, 23),
            wordBookId = "cet4",
            items = words,
            missingCardCount = 0,
        )
        val renderer = WorksheetPdfRenderer(context)
        val preview = WorksheetPreviewRenderer(context)

        WorksheetTemplate.values().forEach { template ->
            val settings = WorksheetSettings(
                template = template,
                directions = setOf(WorksheetDirection.ZH_TO_EN),
                includeAnswerPage = false,
            )
            val pages = WorksheetPaginator().paginate(WorksheetDocumentBuilder().build(source, settings))
            val rendered = renderer.render(pages, settings.useFourLineGrid).getOrThrow()
            // 22 词：完整词表每页 40 词为单页，拼写测试与艾宾浩斯每页 20 词各需两页。
            val expectedPageCount = when (template) {
                WorksheetTemplate.FULL_LIST -> 1
                WorksheetTemplate.SPELLING_TEST -> 2
                WorksheetTemplate.EBBINGHAUS_REVIEW -> 2
            }
            assertEquals(template.name, expectedPageCount, rendered.pageCount)
            preview.render(rendered.file, maxWidth = PREVIEW_MAX_WIDTH).forEachIndexed { index, bitmap ->
                assertTableFrameClosesAtTopLeft(bitmap, "${template.name} 第 ${index + 1} 页")
                val target = File(outputDirectory, "$template-page-${index + 1}.png")
                FileOutputStream(target).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                assertTrue(target.name, target.length() > 0L)
                bitmap.recycle()
            }
            rendered.file.delete()
        }

        assertTrue(File(outputDirectory, "${WorksheetTemplate.FULL_LIST}-page-1.png").exists())
        assertTrue(File(outputDirectory, "${WorksheetTemplate.SPELLING_TEST}-page-1.png").exists())
        assertTrue(File(outputDirectory, "${WorksheetTemplate.SPELLING_TEST}-page-2.png").exists())
        assertTrue(File(outputDirectory, "${WorksheetTemplate.EBBINGHAUS_REVIEW}-page-1.png").exists())
        assertTrue(File(outputDirectory, "${WorksheetTemplate.EBBINGHAUS_REVIEW}-page-2.png").exists())
    }

    /**
     * 表格外框必须闭合到左上角，不能与直角单元格「打架」。
     *
     * 这条断言来自用户实测：外框一度画成圆角矩形，而表头底色与单元格网格线都是直角，
     * 于是四角同时存在直角的填充边缘和圆角的轮廓弧线，看起来像两个角叠在一起、绿角还溢出弧线。
     *
     * 取样的可判别性：圆角矩形在离角点 1 pt 处，弧线已经向内退了约 3 pt（半径 6 pt 时），
     * 因此 (左, 上+1pt) 与 (左+1pt, 上) 这两点若落在**表头底色**上，就说明外框在角上没有线。
     * 断言用「与表头底色的明暗差」而不是具体色值，改配色不会误报。
     */
    private fun assertTableFrameClosesAtTopLeft(bitmap: Bitmap, label: String) {
        val scale = PREVIEW_MAX_WIDTH.toFloat() / WorksheetPaper.A4_WIDTH
        val leftPx = (WorksheetPaper.TABLE_LEFT * scale).roundToInt()
        val topPx = (WorksheetPaper.TABLE_TOP * scale).roundToInt()
        // 沿边线离开角点 1 pt；只在「沿边」方向偏移，不垂直于边线，因此仍应落在边线上。
        val alongEdge = (1f * scale).roundToInt().coerceAtLeast(2)
        val inset = (6f * scale).roundToInt()

        val headerInk = bitmap.getPixel(leftPx + inset, topPx + inset)
        val verticalEdge = bitmap.getPixel(leftPx, topPx + alongEdge)
        val horizontalEdge = bitmap.getPixel(leftPx + alongEdge, topPx)

        assertTrue(
            "$label 左上角内侧应当是深色表头底色，实际取到 ${hex(headerInk)}；取样坐标与版式不符，断言失去意义",
            channelSum(headerInk) < HEADER_INK_MAX,
        )
        listOf("竖边" to verticalEdge, "横边" to horizontalEdge).forEach { (edge, pixel) ->
            assertTrue(
                "$label 的外框没有闭合到左上角（$edge）：取到 ${hex(pixel)}，与表头底色 ${hex(headerInk)} 近乎同色。" +
                    "这通常意味着外框被画成圆角、而表头与网格线是直角，四角出现「两个角叠在一起」。",
                channelSum(pixel) > channelSum(headerInk) + EDGE_MIN_GAIN,
            )
        }
    }

    private fun channelSum(color: Int): Int = Color.red(color) + Color.green(color) + Color.blue(color)

    private fun hex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    private companion object {
        const val PREVIEW_MAX_WIDTH = 1000
        /** 表头底色（深青）三通道之和约 292，白底为 765；用 600 把白底与表头底色分开。 */
        const val HEADER_INK_MAX = 600
        /** 边线（浅青）比表头底色亮约 219，取 60 留足抗锯齿余量。 */
        const val EDGE_MIN_GAIN = 60
        val words = listOf(
            WorksheetItem("1", "abandon", "/əˈbændən/", "v.", "放弃；抛弃", null),
            WorksheetItem("2", "ability", "/əˈbɪləti/", "n.", "能力；才能", null),
            WorksheetItem("3", "absorb", "/əbˈzɔːb/", "v.", "吸收；吸引", null),
            WorksheetItem("4", "academic", "/ˌækəˈdemɪk/", "adj.", "学术的；学院的", null),
            WorksheetItem("5", "accelerate", "/əkˈseləreɪt/", "v.", "加速；促进", null),
            WorksheetItem("6", "accompany", "/əˈkʌmpəni/", "v.", "陪伴；伴随", null),
            WorksheetItem("7", "accurate", "/ˈækjərət/", "adj.", "准确的；精密的", null),
            WorksheetItem("8", "achieve", "/əˈtʃiːv/", "v.", "实现；达到", null),
            WorksheetItem("9", "acquire", "/əˈkwaɪə(r)/", "v.", "获得；习得", null),
            WorksheetItem("10", "adapt", "/əˈdæpt/", "v.", "适应；改编", null),
            WorksheetItem("11", "adequate", "/ˈædɪkwət/", "adj.", "足够的；适当的", null),
            WorksheetItem("12", "adjust", "/əˈdʒʌst/", "v.", "调整；校准", null),
            WorksheetItem("13", "administration", "/ədˌmɪnɪˈstreɪʃn/", "n.", "管理；行政部门", null),
            WorksheetItem("14", "admire", "/ədˈmaɪə(r)/", "v.", "钦佩；欣赏", null),
            WorksheetItem("15", "adopt", "/əˈdɒpt/", "v.", "采用；收养", null),
            WorksheetItem("16", "advocate", "/ˈædvəkeɪt/", "v.", "提倡；拥护", null),
            WorksheetItem("17", "aesthetic", "/iːsˈθetɪk/", "adj.", "审美的；美学的", null),
            WorksheetItem("18", "affection", "/əˈfekʃn/", "n.", "喜爱；感情", null),
            WorksheetItem("19", "aggressive", "/əˈɡresɪv/", "adj.", "好斗的；有进取心的", null),
            WorksheetItem("20", "agriculture", "/ˈæɡrɪkʌltʃə(r)/", "n.", "农业；农学", null),
            WorksheetItem("21", "allocate", "/ˈæləkeɪt/", "v.", "分配；拨给", null),
            WorksheetItem("22", "ambiguous", "/æmˈbɪɡjuəs/", "adj.", "模棱两可的；含糊的", null),
        )
    }
}
