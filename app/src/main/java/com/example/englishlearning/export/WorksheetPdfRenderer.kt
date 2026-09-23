package com.example.englishlearning.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.englishlearning.learning.worksheet.WorksheetPage
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 按参考模板绘制 A4 默写纸：圆角青绿外框、深青底白字表头、浅青交替行底、完整单元格网格。
 *
 * 版式约定：
 * - 每一张表都带完整外框与全格网格，表头、数据行、艾宾浩斯的 Review 列读起来是一张连续的表。
 * - 行高由模板容量推导（满页恰好铺满），词条不足时保持同一行高并在最后一行收尾。
 */
class WorksheetPdfRenderer(
    private val context: Context,
) : WorksheetPdfWriter {
    /** 端口实现：把阻塞的 PDF 写入放到 IO 线程，调用方（ViewModel）只负责在自己的作用域等待。 */
    override suspend fun write(pages: List<WorksheetPage>, useFourLineGrid: Boolean): Result<RenderedWorksheet> =
        withContext(Dispatchers.IO) { render(pages, useFourLineGrid) }

    fun render(pages: List<WorksheetPage>, useFourLineGrid: Boolean): Result<RenderedWorksheet> = runCatching {
        require(pages.isNotEmpty())
        val exportDirectory = File(context.cacheDir, EXPORT_DIRECTORY).apply { mkdirs() }
        val output = File(exportDirectory, "worksheet-${System.currentTimeMillis()}.pdf")
        val temporary = File(exportDirectory, "${output.name}.tmp")
        try {
            val document = PdfDocument()
            try {
                pages.forEach { page ->
                    val pdfPage = document.startPage(
                        PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, page.pageNumber).create(),
                    )
                    drawPage(pdfPage.canvas, page, useFourLineGrid)
                    document.finishPage(pdfPage)
                }
                FileOutputStream(temporary).use(document::writeTo)
            } finally {
                document.close()
            }
            check(temporary.renameTo(output))
            RenderedWorksheet(output, pages.size)
        } catch (error: Throwable) {
            temporary.delete()
            output.delete()
            throw error
        }
    }

    private fun drawPage(canvas: Canvas, page: WorksheetPage, useFourLineGrid: Boolean) {
        canvas.drawRoundRect(frameRect, CORNER, CORNER, frame)
        canvas.drawText(titleFor(page), MARGIN, TITLE_BASELINE, title)
        canvas.drawText("Date：____________", A4_WIDTH - MARGIN - 96f, TITLE_BASELINE, meta)
        canvas.drawText("Page-${page.pageNumber}", A4_WIDTH - MARGIN - 52f, FOOTER_BASELINE, meta)

        if (page.answers.isNotEmpty()) {
            drawAnswers(canvas, page)
        } else {
            when (page.template) {
                WorksheetTemplate.FULL_LIST -> drawFullList(canvas, page)
                WorksheetTemplate.SPELLING_TEST -> drawSpellingTest(canvas, page, useFourLineGrid)
                WorksheetTemplate.EBBINGHAUS_REVIEW -> drawEbbinghaus(canvas, page)
            }
        }
    }

    private fun drawAnswers(canvas: Canvas, page: WorksheetPage) {
        val visibleRows = minOf(page.answers.size, ROWS_PER_COLUMN)
        val rowHeight = rowHeightForTemplateRows()
        drawHeaderRow(canvas, TABLE_LEFT, TABLE_TOP, TABLE_WIDTH, rowHeight, listOf("No.", "Word", "Meaning"), ANSWER_COLUMNS)
        page.answers.take(visibleRows).forEachIndexed { index, answer ->
            val y = TABLE_TOP + rowHeight * (index + 1)
            fillRow(canvas, y, rowHeight, index)
            drawRowCells(
                canvas, TABLE_LEFT, y, rowHeight,
                listOf(
                    Cell.Plain(answer.number.toString()),
                    Cell.Word(answer.lemma, answer.ipa),
                    Cell.Plain("${answer.partOfSpeech} ${answer.meaningZh}"),
                ),
                ANSWER_COLUMNS,
            )
        }
        drawTableGrid(canvas, visibleRows, rowHeight, TABLE_LEFT, TABLE_WIDTH, ANSWER_COLUMNS)
    }

    private fun drawFullList(canvas: Canvas, page: WorksheetPage) {
        val half = (page.questionRows.size + 1) / 2
        val groups = listOf(page.questionRows.take(half), page.questionRows.drop(half))
        val visibleRows = minOf(groups.maxOfOrNull { it.size } ?: 0, ROWS_PER_COLUMN)
        val rowHeight = rowHeightForTemplateRows()
        groups.forEachIndexed { groupIndex, groupRows ->
            val left = if (groupIndex == 0) TABLE_LEFT else TABLE_LEFT + GROUP_WIDTH + GROUP_GAP
            drawHeaderRow(canvas, left, TABLE_TOP, GROUP_WIDTH, rowHeight, listOf("No.", "Word", "Meaning"), FULL_LIST_COLUMNS)
            groupRows.take(visibleRows).forEachIndexed { index, row ->
                val y = TABLE_TOP + rowHeight * (index + 1)
                fillRow(canvas, y, rowHeight, index, left, GROUP_WIDTH)
                drawRowCells(
                    canvas, left, y, rowHeight,
                    listOf(
                        Cell.Plain(row.number.toString()),
                        Cell.Word(row.lemma, row.ipa),
                        Cell.Plain("${row.partOfSpeech} ${row.meaningZh}"),
                    ),
                    FULL_LIST_COLUMNS,
                )
            }
            drawTableGrid(canvas, visibleRows, rowHeight, left, GROUP_WIDTH, FULL_LIST_COLUMNS)
        }
    }

    private fun drawSpellingTest(canvas: Canvas, page: WorksheetPage, useFourLineGrid: Boolean) {
        val visibleRows = minOf(page.questionRows.size, ROWS_PER_COLUMN)
        val rowHeight = rowHeightForTemplateRows()
        // 左组：给出单词与音标，留空写释义。右组：留空写单词，给出释义。
        val left = TABLE_LEFT
        val right = TABLE_LEFT + GROUP_WIDTH + GROUP_GAP
        val promptWidth = GROUP_WIDTH * 0.46f
        val blankWidth = GROUP_WIDTH - SPELLING_NUMBER_WIDTH - promptWidth
        val leftColumns = floatArrayOf(SPELLING_NUMBER_WIDTH, promptWidth, blankWidth)
        val rightColumns = floatArrayOf(SPELLING_NUMBER_WIDTH, blankWidth, promptWidth)
        drawHeaderRow(canvas, left, TABLE_TOP, GROUP_WIDTH, rowHeight, listOf("No.", "Word", ""), leftColumns)
        drawHeaderRow(canvas, right, TABLE_TOP, GROUP_WIDTH, rowHeight, listOf("No.", "", "Meaning"), rightColumns)
        page.questionRows.take(visibleRows).forEachIndexed { index, row ->
            val y = TABLE_TOP + rowHeight * (index + 1)
            fillRow(canvas, y, rowHeight, index, left, GROUP_WIDTH)
            fillRow(canvas, y, rowHeight, index, right, GROUP_WIDTH)
            drawRowCells(
                canvas, left, y, rowHeight,
                listOf(Cell.Plain(row.number.toString()), Cell.Word(row.lemma, row.ipa), Cell.Plain("")),
                leftColumns,
            )
            drawRowCells(
                canvas, right, y, rowHeight,
                listOf(Cell.Plain(row.number.toString()), Cell.Plain(""), Cell.Plain("${row.partOfSpeech} ${row.meaningZh}")),
                rightColumns,
            )
            if (useFourLineGrid) {
                val leftBlankStart = left + SPELLING_NUMBER_WIDTH + promptWidth
                drawFourLineGrid(canvas, leftBlankStart + 6f, y + rowHeight - 10f, left + GROUP_WIDTH - 6f)
                val rightBlankStart = right + SPELLING_NUMBER_WIDTH
                drawFourLineGrid(canvas, rightBlankStart + 6f, y + rowHeight - 10f, rightBlankStart + blankWidth - 6f)
            }
        }
        drawTableGrid(canvas, visibleRows, rowHeight, left, GROUP_WIDTH, leftColumns)
        drawTableGrid(canvas, visibleRows, rowHeight, right, GROUP_WIDTH, rightColumns)
    }

    private fun drawEbbinghaus(canvas: Canvas, page: WorksheetPage) {
        val visibleRows = minOf(page.questionRows.size, ROWS_PER_COLUMN)
        val rowHeight = rowHeightForTemplateRows(headerRows = EBBINGHAUS_HEADER_ROWS)
        val dataTop = TABLE_TOP + rowHeight * EBBINGHAUS_HEADER_ROWS
        val bottom = dataTop + rowHeight * visibleRows

        drawReviewHeader(canvas, rowHeight)
        page.questionRows.take(visibleRows).forEachIndexed { index, row ->
            val y = dataTop + rowHeight * index
            fillRow(canvas, y, rowHeight, index)
            drawRowCells(
                canvas, TABLE_LEFT, y, rowHeight,
                listOf(
                    Cell.Plain(row.number.toString()),
                    Cell.Word(row.lemma, row.ipa),
                    Cell.Plain("${row.partOfSpeech} ${row.meaningZh}"),
                ),
                REVIEW_TEXT_COLUMNS,
            )
        }

        // 整表横向网格：数据行逐行画线，最后一行成为表格下边框。
        strokeRows(canvas, visibleRows, rowHeight, TABLE_LEFT, TABLE_WIDTH, headerRows = EBBINGHAUS_HEADER_ROWS)
        // 表头第二行只在 Review 列分格（左侧三列是跨两行的合并表头）。
        canvas.drawLine(
            TABLE_LEFT + REVIEW_TEXT_WIDTH, TABLE_TOP + rowHeight,
            TABLE_LEFT + TABLE_WIDTH, TABLE_TOP + rowHeight, cellStroke,
        )
        // Review 列：表头第二行起到底边的竖向分隔，与数据行对齐成一张打卡表。
        for (index in 0..REVIEW_DAYS.size) {
            val x = TABLE_LEFT + REVIEW_TEXT_WIDTH + REVIEW_WIDTH * index / REVIEW_DAYS.size
            canvas.drawLine(x, TABLE_TOP + rowHeight, x, bottom, cellStroke)
        }
        strokeColumns(canvas, TABLE_LEFT, TABLE_TOP, bottom, REVIEW_TEXT_COLUMNS)
        canvas.drawRoundRect(
            RectF(TABLE_LEFT, TABLE_TOP, TABLE_LEFT + TABLE_WIDTH, bottom),
            HEADER_CORNER, HEADER_CORNER, tableFrame,
        )
    }

    private fun drawReviewHeader(canvas: Canvas, rowHeight: Float) {
        val left = TABLE_LEFT
        val headerBottom = TABLE_TOP + rowHeight * EBBINGHAUS_HEADER_ROWS
        canvas.drawRoundRect(
            RectF(left, TABLE_TOP, left + TABLE_WIDTH, headerBottom),
            HEADER_CORNER, HEADER_CORNER, headerFillBorder,
        )
        canvas.drawRect(left, TABLE_TOP, left + TABLE_WIDTH, headerBottom, headerFill)
        // 合并表头的列宽必须与数据行同一套（REVIEW_TEXT_COLUMNS），否则表头与列内容错位。
        val headerHeight = rowHeight * EBBINGHAUS_HEADER_ROWS
        drawHeaderCell(canvas, left, TABLE_TOP, headerHeight, "No.", REVIEW_TEXT_COLUMNS[0])
        drawHeaderCell(canvas, left + REVIEW_TEXT_COLUMNS[0], TABLE_TOP, headerHeight, "Word", REVIEW_TEXT_COLUMNS[1])
        drawHeaderCell(
            canvas, left + REVIEW_TEXT_COLUMNS[0] + REVIEW_TEXT_COLUMNS[1], TABLE_TOP, headerHeight,
            "Meaning", REVIEW_TEXT_COLUMNS[2],
        )
        REVIEW_DAYS.forEachIndexed { index, day ->
            val x = left + REVIEW_TEXT_WIDTH + REVIEW_WIDTH * index / REVIEW_DAYS.size
            val w = REVIEW_WIDTH / REVIEW_DAYS.size
            drawCenteredHeader(canvas, x, TABLE_TOP + rowHeight, w, rowHeight, day)
        }
        drawCenteredHeader(canvas, left + REVIEW_TEXT_WIDTH, TABLE_TOP, REVIEW_WIDTH, rowHeight, "Review")
    }

    private fun drawHeaderRow(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        rowHeight: Float,
        headers: List<String>,
        columns: FloatArray,
    ) {
        canvas.drawRoundRect(
            RectF(left, top, left + width, top + rowHeight),
            HEADER_CORNER, HEADER_CORNER, headerFillBorder,
        )
        canvas.drawRect(left, top, left + width, top + rowHeight, headerFill)
        var x = left
        headers.forEachIndexed { index, header ->
            if (header.isNotEmpty()) drawHeaderCell(canvas, x, top, rowHeight, header, columns[index])
            x += columns[index]
        }
    }

    private fun drawHeaderCell(canvas: Canvas, x: Float, y: Float, height: Float, text: String, width: Float) {
        canvas.drawText(ellipsize(text, width - 8f), x + 4f, y + height / 2f + 3f, header)
    }

    private fun drawCenteredHeader(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, text: String) {
        canvas.drawText(text, x + width / 2f - header.measureText(text) / 2f, y + height / 2f + 3f, header)
    }

    /**
     * 一行里的一个单元格。`Word` 把单词与音标拆成两种墨：单词近黑加粗、音标淡灰小字，
     * 两者在纸面上必须一眼可分，而不是连成一串同色文字。
     */
    private sealed interface Cell {
        data class Plain(val text: String) : Cell

        data class Word(val lemma: String, val ipa: String) : Cell
    }

    private fun drawRowCells(
        canvas: Canvas,
        left: Float,
        y: Float,
        rowHeight: Float,
        cells: List<Cell>,
        columns: FloatArray,
    ) {
        var x = left
        cells.forEachIndexed { index, cell ->
            val width = columns[index] - CELL_INSET_X * 2f
            when (cell) {
                is Cell.Plain -> if (cell.text.isNotEmpty()) {
                    drawCell(
                        canvas,
                        x + CELL_INSET_X,
                        y,
                        rowHeight,
                        wrap(cell.text, width, maxLinesFor(rowHeight), body).map { it to body },
                    )
                }

                is Cell.Word -> drawCell(
                    canvas,
                    x + CELL_INSET_X,
                    y,
                    rowHeight,
                    wordCellLines(cell.lemma, cell.ipa, width, rowHeight),
                )
            }
            x += columns[index]
        }
    }

    /** 单词在上、音标在下；行高不够时先保证单词完整，音标让位而不是互相挤成一行。 */
    private fun wordCellLines(lemma: String, ipa: String, width: Float, rowHeight: Float): List<Pair<String, Paint>> {
        val maxLines = maxLinesFor(rowHeight)
        val lemmaLines = wrap(lemma, width, maxLines, lemmaInk).map { it to lemmaInk }
        val remaining = maxLines - lemmaLines.size
        if (remaining <= 0 || ipa.isBlank()) return lemmaLines
        return lemmaLines + wrap(ipa, width, remaining, ipaInk).map { it to ipaInk }
    }

    /** 文本块在行内垂直居中，避免多行单元格贴顶、与相邻列看起来没对齐。 */
    private fun drawCell(canvas: Canvas, x: Float, y: Float, rowHeight: Float, lines: List<Pair<String, Paint>>) {
        if (lines.isEmpty()) return
        val metrics = lines.first().second.fontMetrics
        val ascent = -metrics.ascent
        val blockHeight = LINE_HEIGHT * (lines.size - 1) + ascent + metrics.descent
        var baseline = y + (rowHeight - blockHeight) / 2f + ascent
        lines.forEach { (text, paint) ->
            canvas.drawText(text, x, baseline, paint)
            baseline += LINE_HEIGHT
        }
    }

    private fun maxLinesFor(rowHeight: Float): Int = ((rowHeight - 4f) / LINE_HEIGHT).toInt().coerceAtLeast(1)

    /** 横向网格：表头行以下逐行画线，最后一行即表格下边框。 */
    private fun strokeRows(
        canvas: Canvas,
        rows: Int,
        rowHeight: Float,
        left: Float = TABLE_LEFT,
        width: Float = TABLE_WIDTH,
        headerRows: Int = 1,
    ) {
        val top = TABLE_TOP + rowHeight * headerRows
        for (index in 0..rows) {
            val y = top + rowHeight * index
            canvas.drawLine(left, y, left + width, y, cellStroke)
        }
    }

    /** 竖向网格：包含表格左右外边线，保证与上下边框闭合。 */
    private fun strokeColumns(canvas: Canvas, left: Float, top: Float, bottom: Float, columns: FloatArray) {
        var x = left
        canvas.drawLine(x, top, x, bottom, cellStroke)
        columns.forEach { width ->
            x += width
            canvas.drawLine(x, top, x, bottom, cellStroke)
        }
    }

    /** 一张表的完整网格：横竖线 + 闭合外框，让它读起来像一张真表。 */
    private fun drawTableGrid(
        canvas: Canvas,
        rows: Int,
        rowHeight: Float,
        left: Float,
        width: Float,
        columns: FloatArray,
        headerRows: Int = 1,
    ) {
        val bottom = TABLE_TOP + rowHeight * (rows + headerRows)
        strokeRows(canvas, rows, rowHeight, left, width, headerRows)
        strokeColumns(canvas, left, TABLE_TOP, bottom, columns)
        canvas.drawRoundRect(
            RectF(left, TABLE_TOP, left + width, bottom),
            HEADER_CORNER, HEADER_CORNER, tableFrame,
        )
    }

    private fun fillRow(canvas: Canvas, y: Float, rowHeight: Float, index: Int, left: Float = TABLE_LEFT, width: Float = TABLE_WIDTH) {
        if (index % 2 == 1) canvas.drawRect(left, y, left + width, y + rowHeight, alternateFill)
    }

    private fun drawFourLineGrid(canvas: Canvas, left: Float, baseline: Float, right: Float) {
        canvas.drawLine(left, baseline - 18f, right, baseline - 18f, cellStroke)
        canvas.drawLine(left, baseline - 12f, right, baseline - 12f, cellStroke)
        canvas.drawLine(left, baseline - 6f, right, baseline - 6f, guideStroke)
        canvas.drawLine(left, baseline, right, baseline, cellStroke)
    }

    private fun wrap(text: String, maxWidth: Float, maxLines: Int, paint: Paint): List<String> {
        if (maxLines <= 0) return emptyList()
        val byWord = mutableListOf<String>()
        var current = StringBuilder()
        text.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = StringBuilder(candidate)
            } else {
                byWord += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) byWord += current.toString()
        // 中文释义没有空格，按词切分后仍可能超出列宽，再按字符兜底断行。
        val lines = byWord.flatMap { line -> breakByCharacter(line, maxWidth, paint) }
        if (lines.isEmpty()) return listOf("")
        val visible = lines.take(maxLines).toMutableList()
        if (lines.size > maxLines) visible[visible.lastIndex] = visible.last().dropLast(1) + "…"
        return visible
    }

    private fun breakByCharacter(line: String, maxWidth: Float, paint: Paint): List<String> {
        if (paint.measureText(line) <= maxWidth) return listOf(line)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        line.forEach { character ->
            if (current.isNotEmpty() && paint.measureText("$current$character") > maxWidth) {
                chunks += current.toString()
                current.clear()
            }
            current.append(character)
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    /** 表头用 `header` 笔绘制，就必须用同一支笔测宽，否则会算出偏窄的可用宽度而溢出单元格。 */
    private fun ellipsize(text: String, maxWidth: Float): String {
        if (header.measureText(text) <= maxWidth) return text
        var result = text
        while (result.isNotEmpty() && header.measureText("$result…") > maxWidth) result = result.dropLast(1)
        return "$result…"
    }

    /**
     * 行高按模板容量（每页每栏 [ROWS_PER_COLUMN] 行）推导：满页时正好铺满表格区，
     * 词条不足时保持同一行高并在最后一行收尾，避免表头被撑成大色块或出现幽灵空格子。
     */
    private fun rowHeightForTemplateRows(headerRows: Int = 1): Float {
        val available = TABLE_BOTTOM - TABLE_TOP
        return (available / (ROWS_PER_COLUMN + headerRows)).coerceAtMost(MAX_ROW_HEIGHT)
    }

    private fun titleFor(page: WorksheetPage): String = when {
        page.answers.isNotEmpty() -> "我的词表 · 答案"
        page.template == WorksheetTemplate.FULL_LIST -> "我的词表"
        page.template == WorksheetTemplate.SPELLING_TEST -> "我的词表 · 拼写测试"
        else -> "我的词表 · 艾宾浩斯抗遗忘"
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private val title = textPaint(Color.rgb(14, 80, 65), 13f, bold = true)
    private val meta = textPaint(Color.rgb(78, 117, 106), 8f)
    private val header = textPaint(Color.WHITE, 8f, bold = true)
    private val body = textPaint(WorksheetInk.BODY, WorksheetInk.BODY_SIZE)
    /** 单词：近黑加粗，是每一行最重的墨。 */
    private val lemmaInk = textPaint(WorksheetInk.LEMMA, WorksheetInk.LEMMA_SIZE, bold = true)
    /** 音标：淡灰小字，明显轻于单词。 */
    private val ipaInk = textPaint(WorksheetInk.IPA, WorksheetInk.IPA_SIZE)
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(16, 150, 123)
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
    }
    private val headerFill = Paint().apply { color = Color.rgb(20, 150, 122) }
    private val headerFillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 150, 122) }
    private val alternateFill = Paint().apply { color = Color.rgb(234, 248, 243) }
    private val cellStroke = Paint().apply {
        color = Color.rgb(150, 220, 197)
        strokeWidth = 0.8f
    }
    private val tableFrame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(126, 205, 180)
        style = Paint.Style.STROKE
        strokeWidth = 1.1f
    }
    private val guideStroke = Paint().apply {
        color = Color.rgb(190, 65, 65)
        strokeWidth = 0.8f
        pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
    }
    private val frameRect = RectF(FRAME_LEFT, FRAME_TOP, A4_WIDTH - FRAME_LEFT, A4_HEIGHT - FRAME_TOP)

    private companion object {
        const val EXPORT_DIRECTORY = "worksheets"
        const val A4_WIDTH = 595
        const val A4_HEIGHT = 842
        const val MARGIN = 42f
        const val FRAME_LEFT = 30f
        const val FRAME_TOP = 30f
        const val CORNER = 10f
        const val HEADER_CORNER = 6f
        const val TITLE_BASELINE = 48f
        const val TABLE_TOP = 66f
        const val TABLE_BOTTOM = 800f
        const val FOOTER_BASELINE = 820f
        const val TABLE_LEFT = 42f
        const val TABLE_WIDTH = 511f
        const val GROUP_GAP = 15f
        const val GROUP_WIDTH = (TABLE_WIDTH - GROUP_GAP) / 2f
        const val REVIEW_WIDTH = 200f
        const val REVIEW_TEXT_WIDTH = TABLE_WIDTH - REVIEW_WIDTH
        const val LINE_HEIGHT = 8.4f
        const val MAX_ROW_HEIGHT = 60f
        const val CELL_INSET_X = 4f
        const val ROWS_PER_COLUMN = 20
        const val SPELLING_NUMBER_WIDTH = 22f
        const val EBBINGHAUS_HEADER_ROWS = 2
        val REVIEW_DAYS = listOf("D1", "D2", "D4", "D7", "D15", "D30", "D60", "D90")
        // Word 列要同时容下单词与音标两行，因此比 No. 列宽裕；释义列吃剩下的宽度。
        val FULL_LIST_COLUMNS = floatArrayOf(22f, 100f, GROUP_WIDTH - 122f)
        val ANSWER_COLUMNS = floatArrayOf(26f, 170f, TABLE_WIDTH - 196f)
        val REVIEW_TEXT_COLUMNS = floatArrayOf(26f, 140f, REVIEW_TEXT_WIDTH - 166f)
    }
}
