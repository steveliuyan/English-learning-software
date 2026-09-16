import java.io.File

val requiredFields = listOf(
    "名称",
    "版本",
    "许可证",
    "用途",
    "数据流",
    "NOTICE 位置",
    "替代方案",
    "商业分发结论",
)

val noticesFile = args.singleOrNull()?.let(::File)
    ?: error("Usage: kotlinc -script tools/verify-third-party-notices.main.kts docs/third-party-notices.md")
require(noticesFile.isFile) { "Notices file does not exist: ${noticesFile.path}" }

data class Entry(val id: String, val fields: MutableSet<String> = mutableSetOf())

val entries = mutableListOf<Entry>()
noticesFile.forEachLine { line ->
    when {
        line.startsWith("## ") -> entries += Entry(line.removePrefix("## ").trim())
        line.startsWith("- ") && entries.isNotEmpty() -> entries.last().fields +=
            line.removePrefix("- ").substringBefore(":").trim()
    }
}
require(entries.isNotEmpty()) { "No third-party notice entries found." }
val errors = entries.flatMap { entry ->
    requiredFields.filterNot(entry.fields::contains).map { field -> "${entry.id}: 缺少必填字段“$field”" }
}
require(errors.isEmpty()) { errors.joinToString(separator = "\n") }
println("Validated ${entries.size} third-party notice entries.")
