package com.example.englishlearning.reading

import com.example.englishlearning.reading.domain.ArticleLengthTier
import kotlin.test.Test
import kotlin.test.assertTrue

class ArticleQualityPolicyTest {
    private val length = resolveArticleLength("cet4", ArticleLengthTier.STANDARD) // acceptedWords = 162..330

    private fun raw(
        title: String = "A Day",
        english: String = List(200) { "word" }.joinToString(" "),
        chinese: String = "这是一段中文译文。".repeat(5),
    ) = RawArticle(title, english, chinese)

    @Test
    fun acceptsAWellFormedArticle() {
        assertTrue(ArticleQualityPolicy.validate(raw(), length).isSuccess)
    }

    @Test
    fun rejectsABlankTitle() {
        assertTrue(ArticleQualityPolicy.validate(raw(title = "   "), length).isFailure)
    }

    @Test
    fun rejectsABlankEnglishBody() {
        assertTrue(ArticleQualityPolicy.validate(raw(english = ""), length).isFailure)
    }

    @Test
    fun rejectsABlankChineseBody() {
        assertTrue(ArticleQualityPolicy.validate(raw(chinese = "  "), length).isFailure)
    }

    @Test
    fun rejectsAnEnglishBodyThatIsActuallyChinese() {
        assertTrue(ArticleQualityPolicy.validate(raw(english = "这是一段中文。".repeat(20)), length).isFailure)
    }

    @Test
    fun rejectsAChineseBodyThatIsActuallyEnglish() {
        assertTrue(ArticleQualityPolicy.validate(raw(chinese = "this is english ".repeat(10)), length).isFailure)
    }

    @Test
    fun rejectsABodyFarShorterThanTheAcceptedRange() {
        assertTrue(ArticleQualityPolicy.validate(raw(english = "too short"), length).isFailure)
    }

    @Test
    fun rejectsABodyFarLongerThanTheAcceptedRange() {
        assertTrue(
            ArticleQualityPolicy.validate(raw(english = List(600) { "word" }.joinToString(" ")), length).isFailure,
        )
    }

    @Test
    fun rejectsOversizedOutput() {
        assertTrue(
            ArticleQualityPolicy.validate(raw(chinese = "中".repeat(ArticleQualityPolicy.MAX_TEXT_CHARS + 1)), length).isFailure,
        )
    }

    @Test
    fun rejectsRawHtmlAndScript() {
        // 危险片段必须连同它的分隔符一起判，避免 "conscript" 这类词被误伤。
        assertTrue(ArticleQualityPolicy.validate(raw(title = "<script>alert(1)</script>"), length).isFailure)
        assertTrue(ArticleQualityPolicy.validate(raw(english = "click javascript:void(0) now"), length).isFailure)
        assertTrue(ArticleQualityPolicy.validate(raw(english = "<img src=x onerror=alert(1)>"), length).isFailure)
    }

    @Test
    fun doesNotFalsePositiveOnInnocentWords() {
        // 计划原文的 innocent english 只有 7 个词，会先被词数规则拒绝而让本测试失去意义；
        // 因此补足词数到接受范围内，只保留「含 script 子串的无辜词」这一验证意图。
        val innocent = raw(
            english = "The conscript and the scriptwriter discussed a scripted scene. " +
                List(180) { "word" }.joinToString(" "),
            chinese = "中文" + "这段译文提到剧本与文字，长度足够通过检查。".repeat(4),
        )
        assertTrue(ArticleQualityPolicy.validate(innocent, length).isSuccess)
    }

    @Test
    fun rejectsControlCharacters() {
        assertTrue(
            ArticleQualityPolicy.validate(raw(english = "ab\u0000cd" + " word".repeat(200)), length).isFailure,
        )
    }

    @Test
    fun fetchedArticlesAreValidatedWithoutATranslation() {
        // 外刊抓取没有译文也不伪造（Task C）：除译文相关检查外全部照走。
        val fetchedLength = ArticleLengthPolicy.Resolved(
            tier = ArticleLengthTier.LONG,
            targetWords = 100..2000,
            acceptedWords = 60..2000,
        )
        val result = ArticleQualityPolicy.validateFetched(raw(chinese = ""), fetchedLength)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().chineseText.isEmpty())
    }

    @Test
    fun fetchedArticlesStillRejectDangerousMarkupAndBadBodies() {
        val fetchedLength = ArticleLengthPolicy.Resolved(
            tier = ArticleLengthTier.LONG,
            targetWords = 100..2000,
            acceptedWords = 60..2000,
        )
        assertTrue(ArticleQualityPolicy.validateFetched(raw(chinese = "", title = "<script>x</script>"), fetchedLength).isFailure)
        assertTrue(ArticleQualityPolicy.validateFetched(raw(chinese = "", english = "too short"), fetchedLength).isFailure)
        assertTrue(ArticleQualityPolicy.validateFetched(raw(chinese = "", english = "这是一段中文。".repeat(20)), fetchedLength).isFailure)
    }
}
