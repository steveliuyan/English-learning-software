package com.example.englishlearning.wordbook

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F1-08 改成「离线烘焙进随包词书」之后，App 侧不再有生成、缓存或出站；
 * 本类就是这条决策在资产上的**唯一闸门**（AC1-33）：
 *
 * 1. 覆盖率：十册 45,290 张卡全部带例句（产线报告列出的失败卡除外）。
 * 2. 成对性：任一 `example` 非空必然 `exampleZh` 非空。
 * 3. 逐条过规则：与产线 `tools/wordbooks/example_validation.py` 同一套阈值
 *    （20–160 字符、含该词或其屈折、无中文、无 Markdown、≤2 句；译文含中文 4–120）。
 *
 * 产线还没合并完时（metadata 里没有 `+examples-` 版本标记）本类整体跳过，
 * 这样基线不会挂着一条红；合并之后它自动变成硬断言，不需要改代码。
 */
class WordBookExampleCoverageTest {

    private val root = File("src/main/assets/wordbooks")

    private val markdown = Regex("[*_`#\\[\\]<>|~]")
    private val cjk = Regex("[\\u4e00-\\u9fff\\u3400-\\u4dbf\\u3000-\\u303f\\uff01-\\uff60]")
    private val cjkIdeograph = Regex("[\\u4e00-\\u9fff]")
    private val terminals = Regex("[.!?]+")

    private fun inflectionForms(lemma: String): Set<String> {
        val forms = mutableSetOf(lemma, lemma + "s", lemma + "es", lemma + "ed", lemma + "d", lemma + "ing", lemma + "er", lemma + "est", lemma + "ly", lemma + "ness")
        if (lemma.endsWith("y")) {
            val stem = lemma.dropLast(1)
            forms += setOf(stem + "ies", stem + "ied", stem + "ier", stem + "iest")
        }
        if (lemma.endsWith("e")) {
            val stem = lemma.dropLast(1)
            forms += setOf(stem + "ing", stem + "ed")
        }
        if (lemma.length > 2) {
            val last = lemma.last()
            forms += setOf(lemma + last + "ed", lemma + last + "ing")
        }
        return forms
    }

    /**
     * 把词条折成可比对的词干：纯字母、"mr." 带点、"cliché" 重音字母、
     * "vapo(u)r" 括注可选字母、"axe/ax" 斜杠并列（与产线同口径）。
     */
    private fun lemmaCores(lemma: String): List<String> {
        val trimmed = lemma.trim()
        if (trimmed.contains('/')) return trimmed.split('/').flatMap { lemmaCores(it) }.filter { it.isNotEmpty() }
        val cores = mutableListOf<String>()
        val bare = trimmed.filter { it.isLetter() || it == '\'' }
        if (bare.isNotEmpty()) cores += bare
        val parenthesised = Regex("\\(([A-Za-z']+)\\)").find(trimmed)
        if (parenthesised != null) {
            cores += trimmed.replace("(", "").replace(")", "").filter { it.isLetter() || it == '\'' }
            cores += (trimmed.substring(0, parenthesised.range.first) + trimmed.substring(parenthesised.range.last + 1))
                .filter { it.isLetter() || it == '\'' }
        }
        val expanded = mutableListOf<String>()
        cores.filter { it.isNotEmpty() }.forEach { core ->
            expanded += core
            listOf("s", "es").forEach { suffix ->
                if (core.endsWith(suffix) && core.length - suffix.length >= 2) expanded += core.dropLast(suffix.length)
            }
        }
        return expanded.distinct()
    }

    private fun withinOneEdit(left: String, right: String): Boolean {
        if (kotlin.math.abs(left.length - right.length) > 1) return false
        val (shorter, longer) = if (left.length <= right.length) left to right else right to left
        if (shorter.length == longer.length) return shorter.indices.count { shorter[it] != longer[it] } == 1
        return longer.indices.any { longer.removeRange(it, it + 1) == shorter }
    }

    private fun containsLemma(sentence: String, lemma: String): Boolean {
        val lowered = sentence.lowercase()
        if (lemma.contains(' ') || lemma.contains('-')) return lemma in lowered
        val tokens = Regex("[\\p{L}]+(?:'\\p{L}+)*").findAll(lowered).map { it.value }.toSet()
        return lemmaCores(lemma).any { core ->
            val forms = inflectionForms(core)
            forms.any { it in tokens } || (core.length >= 5 && tokens.any { token -> forms.any { withinOneEdit(token, it) } })
        }
    }

    private fun sentenceCount(sentence: String): Int =
        terminals.split(sentence.trim()).count { it.isNotBlank() }

    private fun violations(cardId: String, lemma: String, example: String, exampleZh: String?): List<String> {
        val problems = mutableListOf<String>()
        if (example.length !in 20..160) problems += "$cardId 例句长度 ${example.length} 不在 20–160"
        if (cjk.containsMatchIn(example)) problems += "$cardId 例句含中文字符"
        if (markdown.containsMatchIn(example)) problems += "$cardId 例句含 Markdown 标记"
        if (!containsLemma(example, lemma)) problems += "$cardId 例句里没有这个单词或其屈折形式"
        if (sentenceCount(example) > 2) problems += "$cardId 例句超过两句"
        if (!example.first().isUpperCase()) problems += "$cardId 例句不是大写开头"
        if (exampleZh.isNullOrBlank()) {
            problems += "$cardId 有英文例句却没有译文（AC1-33 要求成对）"
        } else {
            if (exampleZh.length !in 4..120) problems += "$cardId 译文长度 ${exampleZh.length} 不在 4–120"
            if (!cjkIdeograph.containsMatchIn(exampleZh)) problems += "$cardId 译文里没有中文"
        }
        return problems
    }

    @Test
    fun everyBundledCardCarriesAQualifiedExamplePair() {
        assertTrue(root.isDirectory, "随包词书目录应可寻：${root.absolutePath}")
        val metadata = File(root, "metadata.json").readText()
        assumeTrue(
            metadata.contains("+examples-"),
            "例句尚未烘焙进随包资产（metadata.dataVersion 无 +examples- 标记），本用例跳过；" +
                "合并工具是 tools/wordbooks/apply_examples.py --data-version-tag ...",
        )

        val books = root.listFiles { file -> file.isDirectory }
            ?.filter { File(it, "book.json").isFile }
            .orEmpty()
        assertEquals(10, books.size, "随包册数与既有口径一致（十册）")

        var cards = 0
        var missing = 0
        val problems = mutableListOf<String>()
        books.forEach { directory ->
            val parsed = WordBookPackageParser.parse(directory).getOrThrow().cards
            cards += parsed.size
            parsed.forEach { card ->
                val example = card.example
                if (example == null) {
                    missing += 1
                    return@forEach
                }
                problems += violations(card.cardId, card.lemma, example, card.exampleZh)
            }
        }

        assertTrue(cards > 40_000, "十册词条数应在万级（当前 $cards）")
        assertEquals(0, missing, "覆盖率目标 100%：还有 $missing 张卡没有例句；失败的词条应出现在产线 sidecar 的 failures 里")
        assertTrue(problems.isEmpty(), "不合格例句 ${problems.size} 条，例如：${problems.take(5)}")
    }

    /** 规则本身的空白分支：这些形状在产线落盘前就该被挡住，不许出现在资产里。 */
    @Test
    fun theContentRulesRejectTheShapesThePipelineRejects() {
        val good = "She had the ability to solve hard problems quickly."
        assertTrue(violations("c", "ability", good, "她有快速解决难题的能力。").isEmpty())
        assertTrue(violations("c", "ability", "Ability.", "她有快速解决难题的能力。").isNotEmpty())
        assertTrue(violations("c", "ability", "She had the ability 能力 to solve it.", "她有快速解决难题的能力。").isNotEmpty())
        assertTrue(violations("c", "ability", "She had the **ability** to solve it well.", "她有快速解决难题的能力。").isNotEmpty())
        assertTrue(violations("c", "ability", "She had the power to solve hard problems fast.", "她有快速解决难题的能力。").isNotEmpty())
        assertTrue(violations("c", "ability", good, "She has ability.").isNotEmpty())
        assertTrue(violations("c", "study", "She studies hard every day with friends.", "她每天和朋友努力学习。").isEmpty())
        assertTrue(violations("c", "stop", "He stopped the car near the school.", "他把车停在学校附近。").isEmpty())
    }
}
