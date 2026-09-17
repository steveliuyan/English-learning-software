package com.example.englishlearning.notices

sealed interface NoticeValidationError {
    data class MissingField(val entryId: String, val field: String) : NoticeValidationError
}

private val requiredFields =
    listOf(
        "名称",
        "版本",
        "许可证",
        "用途",
        "数据流",
        "NOTICE 位置",
        "替代方案",
        "商业分发结论",
    )

fun verifyThirdPartyNotices(markdown: String): List<NoticeValidationError> =
    markdown
        .lineSequence()
        .fold(mutableListOf<NoticeEntry>()) { entries, line ->
            when {
                line.startsWith("## ") -> entries += NoticeEntry(line.removePrefix("## ").trim())
                line.startsWith("- ") && entries.isNotEmpty() -> {
                    val field = line.removePrefix("- ")
                    val key = field.substringBefore(":").trim()
                    val value = field.substringAfter(":", missingDelimiterValue = "").trim()
                    if (value.isNotEmpty()) entries.last().fields += key
                }
            }
            entries
        }
        .flatMap { entry ->
            requiredFields.filterNot(entry.fields::contains)
                .map { field -> NoticeValidationError.MissingField(entry.id, field) }
        }

private data class NoticeEntry(
    val id: String,
    val fields: MutableSet<String> = mutableSetOf(),
)
