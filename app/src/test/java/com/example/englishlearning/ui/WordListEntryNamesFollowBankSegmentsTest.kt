package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * U-8（决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §5）：
 * 同一批词的三个入口名统一到**词库分段的短名**——收藏 / 生词 / 错词。
 *
 * 改前的三套名字（**改前**实测；现在照抄那条 `grep` 只会读到 0 命中，重算请改读 hub 的三行与分段枚举体）：
 *  - hub 第一组：旧的三枚长名（`ProfileScreen.kt` 的 `ProfileGroups`）；
 *  - 词库页分段：短名「生词 / 错词 / 收藏」（`WordBankScreen.kt` 的 `WordBankSection.label`，spec §3.3）；
 *  - 合并页：副标题三段并列 + 段卡标题，用的是 hub 那套长名的混合体（`MyWordsScreen.kt`）。
 * 用户从 hub 点第三行进到的那一格写的是「错词」，同一批词在三处三个名字，
 * 而他记住的只有其中最浅的那一层。分段是**落点**、也是这套词在本仓的术语源头，所以以它为准。
 *
 * 期望名从 `WordBankSection` 的枚举体解析而不是写死：分段改名时本判据跟着走，
 * 不会出现"闸与页面各守一套名、闸还绿着"的空转。
 *
 * 两条刻意的收窄：
 *  - 「生词本」作为**功能名**在本仓还有别的用处（词义详情的「加入生词本 / 移出生词本」、
 *    重新学习屏、空态那句朗读描述），本批不动那些句子——收的是入口名，不是全站术语；
 *    所以对 `MyWordsScreen.kt` 只钉 `title = "生词本"` 这一枚形状归零，而不是整词归零。
 *  - 计数连注释一起扫（与 [countMatches] 相反）：入口名会被抄进别的屏与别的测试，
 *    注释里的旧名就是下一批的模板（同 `AiGuidanceMapNamingTest` 口径）。
 */
class WordListEntryNamesFollowBankSegmentsTest {

    private companion object {
        /** 段枚举名 → hub 行的 tag 与合并页段容器 tag 的一部分。 */
        val sectionToHubTag = listOf(
            "Favorite" to "profile_open_favorite_words",
            "New" to "profile_open_new_words",
            "Wrong" to "profile_open_wrong_words",
        )

        val staleHubNames = listOf("收藏的词", "生词本", "易错词")

        val uiDir = File(UI_MAIN_SOURCE_PATH)

        fun read(name: String): String {
            val file = if (name.contains('/')) File(APP_MAIN_SOURCE_PATH).resolve(name) else File(uiDir, name)
            assertTrue(file.isFile, "$name 读不到（${file.path}，工作目录 ${File(".").absolutePath}）")
            return file.readText()
        }

        fun occurrences(text: String, phrase: String): Int = Regex(Regex.escape(phrase)).findAll(text).count()

        /** 从 `WordBankSection` 枚举体解析（段枚举名 → 短名），例如 New -> 生词。 */
        fun segmentLabels(): Map<String, String> {
            val bank = read("WordBankScreen.kt")
            val pattern = Regex("([A-Z][A-Za-z]+)\\(\"([^\"]+)\",\\s*\"word_bank_segment_[a-z]+\"\\)")
            val entries = pattern.findAll(bank)
            val labels = entries.associate { it.groupValues[1] to it.groupValues[2] }
            assertTrue(
                labels.keys.containsAll(sectionToHubTag.map { it.first }),
                "解析 `WordBankSection` 的短名失败（只读到 ${labels.keys}）：本契约的推导源没了，判据会空转",
            )
            return labels
        }
    }

    @Test
    fun theHubRowsUseTheSegmentShortNames() {
        val labels = segmentLabels()
        val hub = read("ProfileScreen.kt")
        sectionToHubTag.forEach { (section, rowTag) ->
            val label = labels.getValue(section)
            assertEquals(
                1,
                occurrences(hub, "ProfileRow(\"$label\", \"$rowTag\""),
                "hub 那一行必须逐字用分段短名「$label」（段 $section → 行 $rowTag）：" +
                    "行名与落地那格的段名不同名，用户记不住自己点的会不会是同批词",
            )
        }
        staleHubNames.forEach { stale ->
            assertEquals(
                0,
                occurrences(hub, stale),
                "旧入口名「$stale」在 `ProfileScreen.kt` 必须归零（含注释）：留着就是给下一批留一套可抄的旧名",
            )
        }
    }

    @Test
    fun theMergedWordPageUsesTheSameThreeShortNames() {
        val labels = segmentLabels()
        val page = read("MyWordsScreen.kt")
        listOf(labels.getValue("Favorite"), labels.getValue("New"), labels.getValue("Wrong")).forEach { label ->
            assertEquals(
                1,
                occurrences(page, "title = \"$label\""),
                "合并页的段卡标题必须恰一处「$label」：与分段同名，也与 hub 行同名",
            )
        }
        assertEquals(
            0,
            occurrences(page, "title = \"生词本\""),
            "段卡标题那一枚「生词本」必须换成短名：本屏其余句子（加入生词本 / 移出生词本 / 空态朗读描述）" +
                "讲的是功能名，不在本批判据内，所以只钉 `title =` 这一枚形状",
        )
        val joined = listOf(labels.getValue("Favorite"), labels.getValue("New"), labels.getValue("Wrong")).joinToString(" · ")
        assertTrue(
            page.contains("subtitle = \"$joined"),
            "合并页副标题的三段并列必须逐字取短名（「$joined」）：那一句是用户进页后第一次核对「我到的是不是那三个入口的合并页」",
        )
    }
}
