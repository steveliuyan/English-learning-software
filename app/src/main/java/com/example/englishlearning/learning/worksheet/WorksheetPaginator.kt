package com.example.englishlearning.learning.worksheet

data class WorksheetQuestionRow(
    val number: Int,
    val lemma: String,
    val ipa: String,
    val partOfSpeech: String,
    val meaningZh: String,
    val pageNumber: Int,
)

data class WorksheetAnswer(
    val number: Int,
    val lemma: String,
    val ipa: String,
    val partOfSpeech: String,
    val meaningZh: String,
)

data class WorksheetPage(
    val pageNumber: Int,
    val pageCount: Int,
    val direction: WorksheetDirection?,
    val template: WorksheetTemplate = WorksheetTemplate.SPELLING_TEST,
    val questionRows: List<WorksheetQuestionRow> = emptyList(),
    val answers: List<WorksheetAnswer> = emptyList(),
)

class WorksheetPaginator {
    fun paginate(document: WorksheetDocument): List<WorksheetPage> {
        val rowsPerPage = rowsPerPage(document.settings)
        val draftPages = buildList {
            document.sections.forEach { section ->
                section.questions.chunked(rowsPerPage).forEach { questions ->
                    add(
                        WorksheetPage(
                            pageNumber = 0,
                            pageCount = 0,
                            direction = section.direction,
                            template = document.settings.template,
                            questionRows = questions.map { question ->
                                WorksheetQuestionRow(
                                    number = question.number,
                                    lemma = question.item.lemma,
                                    ipa = question.item.ipa,
                                    partOfSpeech = question.item.partOfSpeech,
                                    meaningZh = question.item.meaningZh,
                                    pageNumber = 0,
                                )
                            },
                        ),
                    )
                }
            }
            if (document.settings.includeAnswerPage && document.source.items.isNotEmpty()) {
                document.source.items.chunked(ANSWERS_PER_PAGE).forEach { chunk ->
                    add(
                        WorksheetPage(
                            pageNumber = 0,
                            pageCount = 0,
                            direction = null,
                            template = document.settings.template,
                            answers = chunk.map { item ->
                                WorksheetAnswer(
                                    number = document.source.items.indexOf(item) + 1,
                                    lemma = item.lemma,
                                    ipa = item.ipa,
                                    partOfSpeech = item.partOfSpeech,
                                    meaningZh = item.meaningZh,
                                )
                            },
                        ),
                    )
                }
            }
        }
        val pageCount = draftPages.size
        return draftPages.mapIndexed { index, page ->
            val pageNumber = index + 1
            page.copy(
                pageNumber = pageNumber,
                pageCount = pageCount,
                questionRows = page.questionRows.map { it.copy(pageNumber = pageNumber) },
            )
        }
    }

    private fun rowsPerPage(settings: WorksheetSettings): Int = when (settings.template) {
        WorksheetTemplate.FULL_LIST -> FULL_LIST_ROWS_PER_PAGE
        WorksheetTemplate.EBBINGHAUS_REVIEW -> EBBINGHAUS_ROWS_PER_PAGE
        WorksheetTemplate.SPELLING_TEST -> SPELLING_ROWS_PER_PAGE
    }

    private companion object {
        const val SPELLING_ROWS_PER_PAGE = 20
        const val FULL_LIST_ROWS_PER_PAGE = 40
        const val EBBINGHAUS_ROWS_PER_PAGE = 20
        const val ANSWERS_PER_PAGE = 20
    }
}
