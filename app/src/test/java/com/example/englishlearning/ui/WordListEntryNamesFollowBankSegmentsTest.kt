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

        /**
         * §5-A（用户 2026-11-01 拍板「改成核实过的说法」）：hub 词汇三行的**副标题**也不许承诺落点不做的行为。
         * 三句假话按整份 `ProfileScreen.kt`（含注释）归零——注释里留着等于给下一批留可抄的旧说法。
         * 逐句的证伪出处在 `MyWordsScreenContractTest` 那段「三句原型说明逐条核过生产逻辑」的 KDoc。
         */
        val falseClaims = listOf("明天的词单里排到前面", "按答错次数排序", "答对两次")

        /** 行 tag → hub 那行必须写的核实说法（取落点空态句的同一条事实，不换口径）。 */
        val verifiedClaims = listOf(
            "profile_open_favorite_words" to "不排进复习 · 只做手动回看",
            "profile_open_new_words" to "提交「不认识」后自动加入",
            "profile_open_wrong_words" to "标记「不认识」的词 · 答对即移出",
        )

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

    @Test
    fun theHubRowSubtitlesPromiseOnlyWhatTheLandingPagesDo() {
        val hub = read("ProfileScreen.kt")
        falseClaims.forEach { claim ->
            assertEquals(
                0,
                occurrences(hub, claim),
                "hub 不许把落点不做的行为写给用户看（残留「$claim」，现值 ${occurrences(hub, claim)}）：" +
                    "同一句在合并页被逐条核过不成立、整句不写，在两行副标题里却留着——那比命名不一致更硬",
            )
        }
        verifiedClaims.forEach { (rowTag, claim) ->
            // 按「同一行里同时出现 tag 与说法」计数：只要求两处都存在会让说法漂到别的行/别的注释里照样绿。
            assertEquals(
                1,
                hub.lines().count { rowTag in it && claim in it },
                "行 $rowTag 的副标题必须恰一处带上核实过的说法「$claim」：行名对齐了但说法仍是旧的，用户读到的承诺没变",
            )
        }
    }

    @Test
    fun each_hub_claim_is_backed_by_the_landing_page_sentence() {
        // 三句说法不是新造的文案，而是落点页里已被逐条核过生产逻辑的那两句空态 + 收藏段说明句。
        // 本条把两侧钉在一起：谁改了 hub 的说法却没同步落点（或反之）当场红，避免同一条事实两种口径。
        val page = read("MyWordsScreen.kt")
        listOf(
            "只做手动回看" to "收藏段的说明句是本仓核过的说法",
            "后会自动加入" to "生词段空态句核过：提交「不认识」确实写进 vocabulary_entries",
            "重新答对后自动消失" to "错词段空态句核过：最近一次反馈不再是 Again 即离开该册",
        ).forEach { (fragment, why) ->
            assertTrue(
                occurrences(page, fragment) >= 1,
                "合并页必须仍写着「$fragment」（$why）：hub 那三行副标题的事实出处在这里，" +
                    "落点把自己核过的说法删掉而 hub 还在引用，就是引用一句没人承重的话",
            )
        }
    }

    @Test
    fun the_claim_gates_are_not_vacuous() {
        val fixture = """
            val NewWords = ProfileRow("生词", "profile_open_new_words", AppIcon.Bookmark, AppDomain.Learn, "明天的词单里排到前面")
            val WrongWords = ProfileRow("错词", "profile_open_wrong_words", AppIcon.Warning, AppDomain.Review, "按答错次数排序 · 答对两次移出")
        """.trimIndent()
        listOf("明天的词单里排到前面", "按答错次数排序", "答对两次").forEach { claim ->
            assertEquals(
                1,
                occurrences(fixture, claim),
                "合成负片段被放行：假话说法「$claim」没被数出来，归零判据是空转",
            )
        }
        val honestFixture = """
            val NewWords = ProfileRow("生词", "profile_open_new_words", AppIcon.Bookmark, AppDomain.Learn, "提交「不认识」后自动加入")
        """.trimIndent()
        assertEquals(
            1,
            honestFixture.lines().count { "profile_open_new_words" in it && "提交「不认识」后自动加入" in it },
            "合成正片段漏判：同一行里的「tag + 说法」形状没被认出，逐行取法的判据会抓不到回退",
        )
    }
}
