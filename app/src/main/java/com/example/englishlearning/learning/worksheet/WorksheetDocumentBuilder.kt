package com.example.englishlearning.learning.worksheet

data class WorksheetDocument(
    val source: WorksheetSource,
    val settings: WorksheetSettings,
    val sections: List<WorksheetSection>,
)

data class WorksheetSection(
    val direction: WorksheetDirection?,
    val questions: List<WorksheetQuestion>,
)

data class WorksheetQuestion(
    val number: Int,
    val item: WorksheetItem,
)

class WorksheetDocumentBuilder {
    fun build(source: WorksheetSource, settings: WorksheetSettings): WorksheetDocument {
        val questions = source.items.mapIndexed { index, item -> WorksheetQuestion(index + 1, item) }
        // 完整词表与艾宾浩斯模板直接印出单词与释义，与默写方向无关，只生成一组。
        val sections = if (settings.requiresDirection()) {
            buildList {
                if (WorksheetDirection.ZH_TO_EN in settings.directions) {
                    add(WorksheetSection(WorksheetDirection.ZH_TO_EN, questions))
                }
                if (WorksheetDirection.EN_TO_ZH in settings.directions) {
                    add(WorksheetSection(WorksheetDirection.EN_TO_ZH, questions))
                }
            }
        } else {
            listOf(WorksheetSection(null, questions))
        }
        return WorksheetDocument(source, settings, sections)
    }
}
