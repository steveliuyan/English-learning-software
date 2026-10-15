package com.example.englishlearning.notices

data class ThirdPartyNotice(val id: String, val fields: Map<String, String>) {
    fun field(name: String): String = fields[name].orEmpty()
}

/**
 * 「关于与开源许可」页的展示侧解析器（SP-D，决策 2026-10-15 §1）。
 * 与 root `verifyThirdPartyNotices` 同一 markdown 口径：`## <id>` 起条目，`- 键: 值` 填字段。
 * 只信任已过门禁校验的台账结构；畸形行忽略而不是崩溃。
 */
fun parseThirdPartyNotices(markdown: String): List<ThirdPartyNotice> {
    val entries = mutableListOf<Pair<String, MutableMap<String, String>>>()
    markdown.lineSequence().forEach { line ->
        when {
            line.startsWith("## ") -> entries += line.removePrefix("## ").trim() to mutableMapOf()
            line.startsWith("- ") && entries.isNotEmpty() -> {
                val field = line.removePrefix("- ")
                val key = field.substringBefore(":").trim()
                val value = field.substringAfter(":", missingDelimiterValue = "").trim()
                if (key.isNotEmpty() && value.isNotEmpty()) entries.last().second[key] = value
            }
        }
    }
    return entries.map { (id, fields) -> ThirdPartyNotice(id, fields.toMap()) }
}
