package com.example.englishlearning.learning.spelling

import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.learning.domain.WordCard
import java.text.Normalizer
import java.util.Locale

/**
 * 一条拼写题：给中文释义 / 音标 / 词性做提示，让用户拼出英文单词。
 * 由 [WordCard] 派生，[answer] 即题目的标准答案。
 */
data class SpellingPrompt(
    val cardId: String,
    val wordBookId: String,
    val answer: String,
    val partOfSpeech: String,
    val meaningZh: String,
    val ipa: String,
    val example: String? = null,
) {
    companion object {
        fun from(card: WordCard): SpellingPrompt = SpellingPrompt(
            cardId = card.cardId,
            wordBookId = card.wordBookId,
            answer = card.lemma,
            partOfSpeech = card.partOfSpeech,
            meaningZh = card.meaningZh,
            ipa = card.ipa,
            example = card.example,
        )

        fun from(cards: List<WordCard>): List<SpellingPrompt> = cards.map(::from)
    }
}

/** 判卷结果。 */
sealed interface SpellingVerdict {
    /** 完全正确（忽略大小写、空白、标点、变音符号）。 */
    data object Correct : SpellingVerdict

    /** 只差一点（编辑距离 1~[SpellingJudge.tolerance] 个字母），可提示重拼。 */
    data class CloseMiss(val distance: Int) : SpellingVerdict

    /** 错误。 */
    data class Wrong(val distance: Int) : SpellingVerdict
}

/**
 * 判卷结果映射到 V1 三档反馈（见 [CardFeedback] 的固定映射 F1-03）：
 * 拼对→认识，差一点→模糊，拼错→不认识。
 *
 * 映射放在这里而不是界面层：拼写测验把结果写进复习排期时，靠的就是这一处唯一的对应关系，
 * 别处不能再各写一份（两份映射早晚会分叉）。
 */
fun SpellingVerdict.toCardFeedback(): CardFeedback = when (this) {
    is SpellingVerdict.Correct -> CardFeedback.Known
    is SpellingVerdict.CloseMiss -> CardFeedback.Fuzzy
    is SpellingVerdict.Wrong -> CardFeedback.Unknown
}

/** 单次作答记录。 */
data class SpellingResult(
    val prompt: SpellingPrompt,
    val input: String,
    val verdict: SpellingVerdict,
) {
    val isCorrect: Boolean get() = verdict is SpellingVerdict.Correct
}

/**
 * 归一化与判卷。全部逻辑与 Android 无关，可纯 JVM 单测。
 *
 * 判卷刻意宽松：学习者常忽略大小写、漏打空格/连字符、用直引号替弯引号、
 * 拼错变音符号——这些都不该判错。
 */
object SpellingJudge {
    private val apostrophes = mapOf(
        '\u2018' to '\'',
        '\u2019' to '\'',
        '\u02BC' to '\'',
        '\uFF07' to '\'',
    )
    private val combiningMarks = Regex("\\p{M}+")

    /**
     * 计算用于「严格相等」比较的键：统一撇号、去变音符号、小写，
     * 并丢弃空白与所有非字母数字（字母数字与撇号保留）。
     */
    fun compareKey(raw: String): String {
        if (raw.isBlank()) return ""
        val unified = buildString(raw.length) {
            raw.forEach { c -> append(apostrophes[c] ?: c) }
        }
        val stripped = Normalizer.normalize(unified, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
        val lowered = stripped.lowercase(Locale.ROOT)
        val sb = StringBuilder(lowered.length)
        lowered.forEach { c ->
            when {
                c.isLetterOrDigit() -> sb.append(c)
                c == '\'' -> sb.append(c)
                // 空白、连字符、标点一律忽略，避免 "a lot" / "a-lot" 这类误判。
                else -> Unit
            }
        }
        return sb.toString()
    }

    fun judge(expected: String, input: String): SpellingVerdict {
        val target = compareKey(expected)
        val typed = compareKey(input)
        if (target.isEmpty()) return SpellingVerdict.Wrong(0)
        if (typed == target) return SpellingVerdict.Correct
        val distance = levenshtein(typed, target)
        return if (distance in 1..tolerance(target.length)) {
            SpellingVerdict.CloseMiss(distance)
        } else {
            SpellingVerdict.Wrong(distance)
        }
    }

    /** 允许的近似距离：短词 1，长词 2。 */
    fun tolerance(answerLength: Int): Int = if (answerLength <= 5) 1 else 2

    /** 标准 Levenshtein 编辑距离（按字符）。 */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}

/**
 * 一次拼写测验的会话状态机（纯逻辑，无 Android 依赖）。
 *
 * 使用流程：读 [current] → [submit] 作答（每题只能提交一次）→ [advance] 前进；
 * 全部完成后 [isFinished] 为 true，可读 [correctCount] / [accuracy] / [results]。
 */
class SpellingQuiz private constructor(
    val prompts: List<SpellingPrompt>,
) {
    private val mutable = mutableListOf<SpellingResult>()
    private var index = 0
    private var currentAnswered = false

    val total: Int get() = prompts.size
    val currentIndex: Int get() = index
    val answeredCount: Int get() = mutable.size
    val correctCount: Int get() = mutable.count { it.isCorrect }
    val isFinished: Boolean get() = index >= prompts.size
    val current: SpellingPrompt? get() = prompts.getOrNull(index)
    val isCurrentAnswered: Boolean get() = currentAnswered

    /** 提交当前题目的作答；题目已答、已结束或列表为空时返回 null。 */
    fun submit(input: String): SpellingResult? {
        val prompt = current ?: return null
        if (currentAnswered) return null
        val result = SpellingResult(prompt, input, SpellingJudge.judge(prompt.answer, input))
        mutable += result
        currentAnswered = true
        return result
    }

    /** 前进到下一题；必须当前题已作答，否则原地不动并返回 false。 */
    fun advance(): Boolean {
        if (!currentAnswered || isFinished) return false
        index += 1
        currentAnswered = false
        return true
    }

    fun results(): List<SpellingResult> = mutable.toList()

    fun accuracy(): Float = if (mutable.isEmpty()) 0f else correctCount.toFloat() / mutable.size

    companion object {
        fun of(prompts: List<SpellingPrompt>): SpellingQuiz = SpellingQuiz(prompts)
    }
}
