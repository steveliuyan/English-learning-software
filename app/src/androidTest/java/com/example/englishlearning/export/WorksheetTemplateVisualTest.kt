package com.example.englishlearning.export

import android.content.Context
import android.graphics.Bitmap
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
            preview.render(rendered.file, maxWidth = 1000).forEachIndexed { index, bitmap ->
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

    private companion object {
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
