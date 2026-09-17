plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

val requiredNoticeFields = listOf(
    "名称", "版本", "许可证", "用途", "数据流", "NOTICE 位置", "替代方案", "商业分发结论",
)

tasks.register("verifyThirdPartyNotices") {
    group = "verification"
    description = "Validates mandatory fields in docs/third-party-notices.md."
    val noticesFile = layout.projectDirectory.file("docs/third-party-notices.md")
    inputs.file(noticesFile)
    doLast {
        data class Entry(val id: String, val fields: MutableSet<String> = mutableSetOf())
        val entries = mutableListOf<Entry>()
        noticesFile.asFile.forEachLine { line ->
            when {
                line.startsWith("## ") -> entries += Entry(line.removePrefix("## ").trim())
                line.startsWith("- ") && entries.isNotEmpty() -> {
                    val field = line.removePrefix("- ")
                    val key = field.substringBefore(":").trim()
                    val value = field.substringAfter(":", missingDelimiterValue = "").trim()
                    if (value.isNotEmpty()) entries.last().fields += key
                }
            }
        }
        check(entries.isNotEmpty()) { "No third-party notice entries found." }
        val errors = entries.flatMap { entry ->
            requiredNoticeFields.filterNot(entry.fields::contains)
                .map { field -> "${entry.id}: 缺少必填字段“$field”" }
        }
        check(errors.isEmpty()) { errors.joinToString(separator = "\n") }
        logger.lifecycle("Validated ${entries.size} third-party notice entries.")
    }
}

tasks.register("check") {
    group = "verification"
    dependsOn("verifyThirdPartyNotices")
}
