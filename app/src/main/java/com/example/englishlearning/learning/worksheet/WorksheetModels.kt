package com.example.englishlearning.learning.worksheet

import java.time.LocalDate

enum class WorksheetRange {
    COMPLETED_TODAY,
    ALL_PLAN_ITEMS,
    COMPLETED_NEW,
    COMPLETED_REVIEW,
    DIFFICULT_TODAY,
}

enum class WorksheetDirection {
    ZH_TO_EN,
    EN_TO_ZH,
}

/**
 * 版式模板。三份模板对应参考样例：完整词表（双栏 40 词/页）、
 * 拼写测试（左右镜像，每页 20 词）、艾宾浩斯复习（每页 20 词带 D1…D90 空格）。
 */
enum class WorksheetTemplate {
    FULL_LIST,
    SPELLING_TEST,
    EBBINGHAUS_REVIEW,
}

data class WorksheetItem(
    val cardId: String,
    val lemma: String,
    val ipa: String,
    val partOfSpeech: String,
    val meaningZh: String,
    val example: String?,
)

data class WorksheetSource(
    val localDate: LocalDate,
    val wordBookId: String,
    val items: List<WorksheetItem>,
    val missingCardCount: Int,
)

data class WorksheetSettings(
    val range: WorksheetRange = WorksheetRange.COMPLETED_TODAY,
    val template: WorksheetTemplate = WorksheetTemplate.SPELLING_TEST,
    val directions: Set<WorksheetDirection> = setOf(WorksheetDirection.ZH_TO_EN),
    val useFourLineGrid: Boolean = true,
    val includeAnswerPage: Boolean = true,
) {
    /**
     * 只有拼写测试需要选择默写方向：完整词表与艾宾浩斯模板都直接印出单词与释义。
     */
    fun requiresDirection(): Boolean = template == WorksheetTemplate.SPELLING_TEST

    fun isReadyToPreview(): Boolean = !requiresDirection() || directions.isNotEmpty()
}

sealed interface WorksheetContentFailure {
    data object NoPlan : WorksheetContentFailure
    data object StorageUnavailable : WorksheetContentFailure
    data object NoSelectedWords : WorksheetContentFailure
}
