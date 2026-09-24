package com.example.englishlearning.reading

import com.example.englishlearning.ai.AiException
import com.example.englishlearning.ai.AiFailure

/**
 * 质量与安全校验全部通过的文章。只有 [ValidatedArticle] 才允许进入派生高亮与落库。
 */
data class ValidatedArticle(val title: String, val englishText: String, val chineseText: String)

/**
 * 保存前的最后一道闸。AI 输出与任何导入内容同属不可信输入（AGENTS.md），这里的检查
 * 按**先便宜后昂贵**排序，每一项失败统一 `AiFailure.InvalidResponse`——界面文案与
 * 用户动作由 `AiFailure.toUserAction()` 决定，不再新造一套错误枚举。
 *
 * 危险标记必须**带分隔符匹配**（`<script`、`javascript:`、`on\w+=`），裸词匹配会把
 * "conscript"、"scriptwriter" 这类无辜词误杀。
 */
object ArticleQualityPolicy {
    /** 单字段长度上限。防止一次失控响应把内存与数据库同时拖垮。 */
    const val MAX_TEXT_CHARS = 20_000

    private val cjkRange = '\u3400'..'\u9FFF'
    private val dangerousMarkup = Regex("<script|<iframe|<img|<a\\s|javascript:|on\\w+\\s*=", RegexOption.IGNORE_CASE)
    private val controlCharacter: (Char) -> Boolean = { it < ' ' && it != '\n' && it != '\t' }

    fun validate(raw: RawArticle, length: ArticleLengthPolicy.Resolved): Result<ValidatedArticle> =
        validateInternal(raw, length, requireTranslation = true)

    /**
     * 外刊抓取专用入口：抓取来源没有译文也不伪造（`chineseText` 允许为空串），
     * 但标题/正文、长度、语言、危险标记等其余检查**全部照走**——抓取内容同属
     * 不可信输入，不因为来源是知名站点就放松任何一项。
     */
    fun validateFetched(raw: RawArticle, length: ArticleLengthPolicy.Resolved): Result<ValidatedArticle> =
        validateInternal(raw, length, requireTranslation = false)

    private fun validateInternal(
        raw: RawArticle,
        length: ArticleLengthPolicy.Resolved,
        requireTranslation: Boolean,
    ): Result<ValidatedArticle> = runCatching {
        checkNotBlank(raw.title)
        checkNotBlank(raw.englishText)
        if (requireTranslation) checkNotBlank(raw.chineseText)

        for (text in listOf(raw.title, raw.englishText, raw.chineseText)) {
            if (text.length > MAX_TEXT_CHARS) throw rejected()
            if (text.any(controlCharacter)) throw rejected()
        }
        for (text in listOf(raw.title, raw.englishText, raw.chineseText)) {
            if (dangerousMarkup.containsMatchIn(text)) throw rejected()
        }

        if (isNotEnglish(raw.englishText)) throw rejected()
        if (requireTranslation && isNotChinese(raw.chineseText)) throw rejected()

        val words = raw.englishText.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size !in length.acceptedWords) throw rejected()

        ValidatedArticle(raw.title, raw.englishText, raw.chineseText)
    }

    private fun checkNotBlank(value: String) {
        if (value.isBlank()) throw rejected()
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
