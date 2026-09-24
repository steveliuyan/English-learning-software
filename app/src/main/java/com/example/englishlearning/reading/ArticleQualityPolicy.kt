package com.example.englishlearning.reading

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure

/**
 * 质量与安全校验全部通过的文章。只有 [ValidatedArticle] 才允许进入派生高亮与落库。
 */
data class ValidatedArticle(val title: String, val englishText: String, val chineseText: String)

/**
 * 一套可参数化的文本约束。三类来源共用**同一套检查**，只换这份约束数据：
 * - AI 生成：词数区间来自词书档位（`forAiGeneration`），必须带译文；
 * - 外刊抓取：长度由站点内容决定，不套词书区间，也没有译文（`forWebFetch`）；
 * - 用户粘贴导入：宽度放宽（40~1200 词），同样没有译文（`forImported`）。
 */
data class ArticleTextConstraints(
    val minWords: Int,
    val maxWords: Int,
    val maxChars: Int,
    val requireTranslation: Boolean,
)

/**
 * 校验失败的具体位置。`validate` 把它折成 `AiException(InvalidResponse)`（AI 链路
 * 只关心「坏了」）；导入用例靠它给出**具体**的 `ImportRejection`，不重复实现检查。
 */
enum class ArticleTextRejection {
    BlankTitle,
    BlankBody,
    BlankChineseBody,
    TooManyChars,
    ControlCharacter,
    DangerousMarkup,
    NotEnglish,
    NotChineseBody,
    TooFewWords,
    TooManyWords,
}

/**
 * 保存前的最后一道闸。AI 输出与任何导入内容同属不可信输入（AGENTS.md），这里的检查
 * 按**先便宜后昂贵**排序。
 *
 * 危险标记必须**带分隔符匹配**（`<script`、`javascript:`、`on\w+=`），裸词匹配会把
 * "conscript"、"scriptwriter" 这类无辜词误杀。
 */
object ArticleQualityPolicy {
    /** 单字段长度上限。防止一次失控响应把内存与数据库同时拖垮。 */
    const val MAX_TEXT_CHARS = 20_000

    /** AI 生成：词数区间取自词书档位的接受区间，必须有译文。 */
    fun forAiGeneration(length: ArticleLengthPolicy.Resolved): ArticleTextConstraints = ArticleTextConstraints(
        minWords = length.acceptedWords.first,
        maxWords = length.acceptedWords.last,
        maxChars = MAX_TEXT_CHARS,
        requireTranslation = true,
    )

    /** 外刊抓取：正文长度由站点决定，不套词书区间；没有译文也不伪造。 */
    val forWebFetch = ArticleTextConstraints(
        minWords = 60,
        maxWords = 2000,
        maxChars = MAX_TEXT_CHARS,
        requireTranslation = false,
    )

    /** 用户粘贴导入：用户自负其责，宽度放宽到 40~1200 词；同样没有译文。 */
    val forImported = ArticleTextConstraints(
        minWords = 40,
        maxWords = 1200,
        maxChars = MAX_TEXT_CHARS,
        requireTranslation = false,
    )

    private val cjkRange = '\u3400'..'\u9FFF'
    private val dangerousMarkup = Regex("<script|<iframe|<img|<a\\s|javascript:|on\\w+\\s*=", RegexOption.IGNORE_CASE)
    private val controlCharacter: (Char) -> Boolean = { it < ' ' && it != '\n' && it != '\t' }

    /**
     * 返回**第一个**未通过的检查；全部通过返回 `null`。检查顺序：空值 → 尺寸 →
     * 危险标记 → 语言 → 词数。这是唯一的检查实现：`validate` 消费它抛异常，
     * 导入用例消费它给用户具体原因。
     */
    fun firstRejection(raw: RawArticle, constraints: ArticleTextConstraints): ArticleTextRejection? {
        if (raw.title.isBlank()) return ArticleTextRejection.BlankTitle
        if (raw.englishText.isBlank()) return ArticleTextRejection.BlankBody
        if (constraints.requireTranslation && raw.chineseText.isBlank()) return ArticleTextRejection.BlankChineseBody

        for (text in listOf(raw.title, raw.englishText, raw.chineseText)) {
            if (text.length > constraints.maxChars) return ArticleTextRejection.TooManyChars
            if (text.any(controlCharacter)) return ArticleTextRejection.ControlCharacter
        }
        for (text in listOf(raw.title, raw.englishText, raw.chineseText)) {
            if (dangerousMarkup.containsMatchIn(text)) return ArticleTextRejection.DangerousMarkup
        }

        if (isNotEnglish(raw.englishText)) return ArticleTextRejection.NotEnglish
        if (constraints.requireTranslation && isNotChinese(raw.chineseText)) return ArticleTextRejection.NotChineseBody

        val words = raw.englishText.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < constraints.minWords) return ArticleTextRejection.TooFewWords
        if (words.size > constraints.maxWords) return ArticleTextRejection.TooManyWords
        return null
    }

    fun validate(raw: RawArticle, constraints: ArticleTextConstraints): Result<ValidatedArticle> = runCatching {
        firstRejection(raw, constraints)?.let { throw rejected() }
        ValidatedArticle(raw.title, raw.englishText, raw.chineseText)
    }

    /** 英文判定：CJK 占比足够低，且 ASCII 字母占多数。 */
    private fun isNotEnglish(text: String): Boolean {
        val visible = text.filterNot { it.isWhitespace() }
        if (visible.isEmpty()) return true
        val cjk = visible.count { it in cjkRange }
        val asciiLetters = visible.count { it in 'a'..'z' || it in 'A'..'Z' }
        return cjk.toDouble() / visible.length >= 0.1 || asciiLetters.toDouble() / visible.length <= 0.5
    }

    /** 中文判定：必须含 CJK 字符且占比可观；纯英文串在这里失败。 */
    private fun isNotChinese(text: String): Boolean {
        val visible = text.filterNot { it.isWhitespace() }
        if (visible.isEmpty()) return true
        val cjk = visible.count { it in cjkRange }
        return cjk == 0 || cjk.toDouble() / visible.length <= 0.2
    }

    private fun rejected() = AiException(AiFailure.InvalidResponse)
}
