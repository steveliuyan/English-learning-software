package com.example.englishlearning.notices

import com.example.englishlearning.ui.APP_MAIN_SOURCE_PATH
import com.example.englishlearning.ui.UI_MAIN_SOURCE_PATH
import com.example.englishlearning.ui.countMatches
import com.example.englishlearning.ui.uiFiles
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * U-6（决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §8）：
 * 「学习数据只在本机 / 卸载前导出」这一条警告**只留一处措辞**。
 *
 * 改前四套说法各写各的（重算：`grep -rn "卸载" app/src/main/java/com/example/englishlearning`）：
 * 许可页那条、备份 hub 页尾那条、学习记录页尾那条、留档导出声明末尾那条——
 * 同一件事四种句子，其中 hub 那条还把 AGENTS.md 要求的「卸载、清除数据或设备损坏」三件只说了一件。
 * 这条警告是用户做数据决策（要不要现在导出）的依据，措辞不一致比措辞不雅更硬。
 *
 * 收法：新增 `notices/LocalDataNotice.kt` 作唯一措辞源，四枚事实各写一遍，
 * 三屏的整句由事实**拼**出来（`const val` 用 `+` 串联），屏里不再手写中文警告。
 *
 * 两套口径（本仓整合批既定分工，写在各自消息里）：
 *  - **旧句归零与整句形状**按整份文件含注释扫（`rawOccurrences`）：注释里的旧句就是下一批的抄写模板；
 *  - **「屏不许自己写这条警告」的棘轮**剥注释扫（`countMatches`）：`ui/theme/AppThemePreference.kt` 的 KDoc
 *    合法引用 AGENTS.md 原话来解释一条设计决定，那是引用不是措辞源，剥注释才不会把合法引用判红。
 *    同一条警告的两个口径不矛盾：前者管「别再抄旧句」，后者管「别再写新句」。
 */
class LocalDataNoticeSingleSourceTest {

    private companion object {
        /** 唯一措辞源：四枚事实 + 由它们拼出的三屏整句 + 指向许可页的出口句。 */
        val factNames = listOf("StoredLocally", "NoAccountNoCloud", "LostOnUninstall", "ExportIsTheOnlyExit")

        val factLiterals = mapOf(
            "StoredLocally" to "只保存在本机",
            "NoAccountNoCloud" to "无账户、也不会自动上云",
            "LostOnUninstall" to "卸载 App、清除数据或设备损坏后可能无法恢复",
            "ExportIsTheOnlyExit" to "导出备份是唯一的留档途径",
        )

        /** 整句名 → 该句必须拼进去的事实名。 */
        val sentenceParts = mapOf(
            "BackupHubWarning" to listOf("StoredLocally", "NoAccountNoCloud", "LostOnUninstall", "ExportIsTheOnlyExit"),
            "LearningRecordsWarning" to listOf("StoredLocally", "NoAccountNoCloud", "LostOnUninstall", "ExportIsTheOnlyExit"),
            "ExportTail" to listOf("StoredLocally", "NoAccountNoCloud", "LostOnUninstall"),
        )

        /** 落点屏 → 它该吃的那枚整句（引用形状，不是自写文案）。 */
        val screenToSentence = listOf(
            "ui/BackupHubScreen.kt" to "BackupHubWarning",
            "ui/LearningRecordsScreen.kt" to "LearningRecordsWarning",
            "ui/BackupExportViewModel.kt" to "ExportTail",
        )

        /** 改前的三套旧句（收源后在 `ui/` 子树含注释归零；本条 KDoc 不复述原句，见类头口径）。 */
        val staleWordings = listOf(
            "所有数据默认只在本机；卸载 App 会一并删除，请先导出备份。",
            "记录按天聚合，只保存在本机；卸载 App 前记得导出备份。",
            "卸载、清除数据或设备损坏前未导出的学习记录可能无法恢复；无账户、无云同步，请自行妥善保存备份文件。",
        )

        /** 这条警告的签名形状：剥注释后 `ui/` 里再出现一枚「卸载」字面量，就是第四套措辞。 */
        val warningSignature = Regex("卸载")

        val sourceFile = File(APP_MAIN_SOURCE_PATH).resolve("notices/LocalDataNotice.kt")

        fun read(relative: String): String {
            val file = File(APP_MAIN_SOURCE_PATH).resolve(relative)
            assertTrue(file.isFile, "$relative 读不到（${file.path}，JVM 工作目录 ${File(".").absolutePath}）")
            return file.readText()
        }

        /** 整份文件含注释的子串计数（改名/旧句归零口径）。 */
        fun rawOccurrences(text: String, phrase: String): Int = Regex(Regex.escape(phrase)).findAll(text).count()

        /** 取 `const val NAME = <表达式>` 那一行的右值（要求声明写在同一行，见决策 §8 的拼法）。 */
        fun initializerOf(source: String, name: String): String {
            val match = Regex("""const val $name = ([^\n]+)""").find(source)
            assertTrue(match != null, "`notices/LocalDataNotice.kt` 里取不到 `const val $name` 的声明行：本判据会空转")
            return match.groupValues[1]
        }
    }

    @Test
    fun oneFileOwnsTheWordingAndWritesEachFactOnce() {
        assertTrue(
            sourceFile.isFile,
            "单一措辞源缺失：应存在 $UI_MAIN_SOURCE_PATH 之外的 `notices/LocalDataNotice.kt`（${sourceFile.path}）——" +
                "改前四套句子散在许可页、备份 hub、学习记录与导出声明四处，收源只许收进这一处",
        )
        val source = sourceFile.readText()
        factNames.forEach { name ->
            val literal = factLiterals.getValue(name)
            assertEquals(
                1,
                rawOccurrences(source, "\"$literal\""),
                "事实「$literal」在单一真源里必须恰写一遍（声明 `const val $name`）：" +
                    "同一枚事实写两遍，两条句子就会各自漂走，等于把四套收成两套",
            )
            assertTrue(
                initializerOf(source, name) == "\"$literal\"",
                "`const val $name` 必须就是那句事实本体（读到 ${initializerOf(source, name)}）：不要往里塞第二件事",
            )
        }
        // 源文件自己也不许夹带整句之外的第二套说法：拼句的声明必须走 `+`，不写成整句字面量。
        sentenceParts.keys.forEach { sentence ->
            val initializer = initializerOf(source, sentence)
            assertTrue(
                "+" in initializer,
                "`const val $sentence` 必须由事实拼出（读到 $initializer）：整句直接写成一条字面量，" +
                    "事实改动时这一句就会漂成第五套说法",
            )
        }
    }

    @Test
    fun everySentenceIsBuiltFromTheFactsItStates() {
        val source = if (sourceFile.isFile) sourceFile.readText() else ""
        assertTrue(source.isNotEmpty(), "单一措辞源缺失（见上一条判据的消息），本条无从核验拼法")
        sentenceParts.forEach { (sentence, parts) ->
            val initializer = initializerOf(source, sentence)
            parts.forEach { fact ->
                assertTrue(
                    Regex("""\b$fact\b""").containsMatchIn(initializer),
                    "`const val $sentence` 必须拼进事实 $fact（读到 $initializer）：" +
                        "少了这一枚，页面那句就只说了这件事的一部分——hub 旧句正是只说了「卸载」、" +
                        "漏了「清除数据或设备损坏」，而 AGENTS.md 三件都要求说全",
                )
            }
        }
        val pointer = initializerOf(source, "PrivacyStatementsPointer")
        assertTrue(
            "开源许可与隐私说明" in pointer,
            "出口句必须指向真存在的那一页（读到 $pointer）：`AboutScreen` 的入口名与 `LicensesScreen` 的页名逐字是「开源许可与隐私说明」",
        )
    }

    @Test
    fun theThreeScreensReadTheWarningFromTheSingleSource() {
        screenToSentence.forEach { (relative, sentence) ->
            val source = read(relative)
            assertEquals(
                1,
                rawOccurrences(source, "LocalDataNotice.$sentence"),
                "$relative 必须恰一处吃 `LocalDataNotice.$sentence`：屏里自写中文警告就是本批判掉的形状" +
                    "（改前三屏各写一套，用户在三处读到三种说法）",
            )
        }
        assertEquals(
            1,
            rawOccurrences(read("ui/BackupExportViewModel.kt"), "LocalDataNotice.PrivacyStatementsPointer"),
            "导出声明必须带一枚指向许可页的出口：那条声明是全仓最长的一处，" +
                "「完整说明在哪儿」只能在这里回答，别处不再复述一遍完整政策",
        )
    }

    @Test
    fun theOldPerScreenWordingsAreGoneFromTheUiTree() {
        staleWordings.forEach { stale ->
            uiFiles(File(UI_MAIN_SOURCE_PATH), expectedHint = "U-6 旧句归零：$UI_MAIN_SOURCE_PATH").forEach { file ->
                assertEquals(
                    0,
                    rawOccurrences(file.readText(), stale),
                    "旧措辞在 `ui/${file.name}` 必须含注释归零：留着就是给下一批留一条可抄的旧句子" +
                        "（收源后这条警告只能来自 `notices/LocalDataNotice.kt`）",
                )
            }
        }
    }

    @Test
    fun noUiFileWritesTheUninstallWarningItself() {
        val offenders = uiFiles(File(UI_MAIN_SOURCE_PATH), expectedHint = "U-6 棘轮：$UI_MAIN_SOURCE_PATH")
            .mapNotNull { file ->
                val hits = countMatches(file.readText(), warningSignature)
                if (hits > 0) "${file.name}: $hits 处" else null
            }
        assertEquals(
            emptyList<String>(),
            offenders,
            "`ui/` 剥注释后不许再有任何字符串字面量写这条警告（命中：$offenders）：" +
                "口径改动只能改单一真源一处，屏跟着改句子就是又长出第四套。" +
                "本条**剥注释**是刻意的——`ui/theme/` 等处合法引用 AGENTS.md 原话解释设计决定，那是引用不是措辞源",
        )
    }

    @Test
    fun theGatesAreNotVacuous() {
        // 反空转三件：旧句能被计数、事实重复能被计数、引用形状缺失能被计数。
        val fakeScreen = """
            // 注释里复述旧句同样算命中（含注释口径）
            val notice = "所有数据默认只在本机；卸载 App 会一并删除，请先导出备份。"
        """.trimIndent()
        assertEquals(1, rawOccurrences(fakeScreen, staleWordings.first()), "夹具没被旧句计数抓到，归零判据会空转")
        assertEquals(1, countMatches(fakeScreen, warningSignature), "夹具没被签名棘轮抓到，`ui/` 里再写一套也照样绿")

        val duplicatedFact = """
            const val StoredLocally = "${factLiterals.getValue("StoredLocally")}"
            const val Other = "${factLiterals.getValue("StoredLocally")}"
        """.trimIndent()
        assertEquals(2, rawOccurrences(duplicatedFact, "\"${factLiterals.getValue("StoredLocally")}\""), "事实重复计数失效，「每枚事实恰一处」会空转")

        assertEquals(
            0,
            rawOccurrences("val text = LocalDataNotice.BackupHubWarning", "LocalDataNotice.LearningRecordsWarning"),
            "引用形状计数必须区分枚名：否则 hub 那一处会被当成记录屏也写了引用",
        )
    }
}
