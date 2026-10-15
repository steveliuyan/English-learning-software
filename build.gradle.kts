plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
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

tasks.register("verifyAcTraceabilityMatrix") {
    group = "verification"
    description = "AC5-01: every AC0..AC4 id in docs/specs/00-04 has an evidence row in the matrix."
    val specFiles = fileTree("docs/specs").matching {
        include("00-*.md", "01-*.md", "02-*.md", "03-*.md", "04-*.md")
    }
    val matrixFile = layout.projectDirectory.file("docs/verification/ac-traceability-matrix.md")
    inputs.files(specFiles)
    inputs.file(matrixFile)
    doLast {
        val idRegex = Regex("""AC[0-4]-\d{2}""")
        val required = specFiles.files.flatMap { idRegex.findAll(it.readText(Charsets.UTF_8)).map { m -> m.value } }.toSet()
        check(required.isNotEmpty()) { "No AC ids found in docs/specs/00-04 (spec glob broken?)." }
        val rows = matrixFile.asFile.readLines(Charsets.UTF_8)
            .filter { it.startsWith("| AC") }
            .map { line -> line.split('|').map { cell -> cell.trim() } }
            .filter { cells -> cells.getOrElse(1) { "" }.matches(Regex("""AC[0-4]-\d{2}""")) }
        val byId = rows.associate { cells -> cells[1] to cells.getOrElse(4) { "" } }
        val errors = mutableListOf<String>()
        (required - byId.keys).sorted().forEach { errors += "矩阵缺少行: $it" }
        byId.forEach { (id, evidence) ->
            if (evidence.isEmpty() || evidence == "（待填）") errors += "证据为空: $id"
        }
        (byId.keys - required).sorted().forEach { errors += "矩阵存在 specs 之外的行: $it" }
        check(errors.isEmpty()) { errors.joinToString(separator = "\n") }
        logger.lifecycle("AC matrix covered ${byId.size} ids with evidence.")
    }
}

val verifyReleaseArtifact = tasks.register("verifyReleaseArtifact") {
    group = "verification"
    description = "F5-01: release APK builds and carries no secrets (AC5-05 release-build scan)."
    dependsOn(":app:assembleRelease")
    doLast {
        val outDir = project(":app").layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apk = outDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
        check(apk != null) { "assembleRelease 未产出 APK" }
        val rc = providers.exec { commandLine("bash", "scripts/apk-secret-scan.sh", apk.absolutePath) }.result.get()
        check(rc.exitValue == 0) { "APK 密钥扫描命中：见上方输出（AC5-05 发布阻断）" }
        logger.lifecycle("verifyReleaseArtifact: ${apk.name} scanned clean (signature check pending release keystore).")
    }
}

tasks.register("checkReleaseReadiness") {
    group = "verification"
    description = "Stage-5 F5-01 release gate: notices + AC matrix + unit tests + secret scans + release artifact."
    dependsOn("verifyThirdPartyNotices", "verifyAcTraceabilityMatrix", ":app:testDebugUnitTest", verifyReleaseArtifact)
    doLast {
        val rc = providers.exec { commandLine("bash", "scripts/secret-scan.sh") }.result.get()
        check(rc.exitValue == 0) { "secret-scan 命中：见上方输出（AC5-05 发布阻断）" }
        logger.lifecycle("checkReleaseReadiness: notices, AC matrix, unit tests and secret scan all green.")
    }
}

tasks.register("check") {
    group = "verification"
    dependsOn("verifyThirdPartyNotices", "verifyAcTraceabilityMatrix")
}
