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

    private fun validate(
        raw: RawArticle,
        constraints: ArticleTextConstraints = ArticleQualityPolicy.forAiGeneration(length),
    ) = ArticleQualityPolicy.validate(raw, constraints)

    @Test
    fun acceptsAWellFormedArticle() {
        assertTrue(validate(raw()).isSuccess)
    }

    @Test
    fun rejectsABlankTitle() {
        assertTrue(validate(raw(title = "   ")).isFailure)
    }

    @Test
    fun rejectsABlankEnglishBody() {
        assertTrue(validate(raw(english = "")).isFailure)
    }

    @Test
    fun rejectsABlankChineseBody() {
        assertTrue(validate(raw(chinese = "  ")).isFailure)
    }

    @Test
    fun rejectsAnEnglishBodyThatIsActuallyChinese() {
        assertTrue(validate(raw(english = "这是一段中文。".repeat(20))).isFailure)
    }

    @Test
    fun rejectsAChineseBodyThatIsActuallyEnglish() {
        assertTrue(validate(raw(chinese = "this is english ".repeat(10))).isFailure)
    }

    @Test
    fun rejectsABodyFarShorterThanTheAcceptedRange() {
        assertTrue(validate(raw(english = "too short")).isFailure)
    }

    @Test
    fun rejectsABodyFarLongerThanTheAcceptedRange() {
        assertTrue(validate(raw(english = List(600) { "word" }.joinToString(" "))).isFailure)
    }

    @Test
    fun rejectsOversizedOutput() {
        assertTrue(validate(raw(chinese = "中".repeat(ArticleQualityPolicy.MAX_TEXT_CHARS + 1))).isFailure)
    }

    @Test
    fun rejectsRawHtmlAndScript() {
        // 危险片段必须连同它的分隔符一起判，避免 "conscript" 这类词被误伤。
        assertTrue(validate(raw(title = "<script>alert(1)</script>")).isFailure)
        assertTrue(validate(raw(english = "click javascript:void(0) now")).isFailure)
        assertTrue(validate(raw(english = "<img src=x onerror=alert(1)>")).isFailure)
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
        assertTrue(validate(innocent).isSuccess)
    }

    @Test
    fun rejectsControlCharacters() {
        assertTrue(validate(raw(english = "ab\u0000cd" + " word".repeat(200))).isFailure)
    }

    @Test
    fun webFetchConstraintsValidateWithoutATranslation() {
        // 外刊抓取没有译文也不伪造（Task C）：除译文相关检查外全部照走。
        val result = ArticleQualityPolicy.validate(raw(chinese = ""), ArticleQualityPolicy.forWebFetch)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().chineseText.isEmpty())
    }

    @Test
    fun webFetchConstraintsStillRejectDangerousMarkupAndBadBodies() {
        val constraints = ArticleQualityPolicy.forWebFetch
        assertTrue(ArticleQualityPolicy.validate(raw(chinese = "", title = "<script>x</script>"), constraints).isFailure)
        assertTrue(ArticleQualityPolicy.validate(raw(chinese = "", english = "too short"), constraints).isFailure)
        assertTrue(ArticleQualityPolicy.validate(raw(chinese = "", english = "这是一段中文。".repeat(20)), constraints).isFailure)
    }

    @Test
    fun importedConstraintsAcceptTheDocumentedRange() {
        // forImported：minWords = 40，maxWords = 1200，不需要译文。
        val constraints = ArticleQualityPolicy.forImported
        assertTrue(ArticleQualityPolicy.validate(raw(english = List(40) { "word" }.joinToString(" "), chinese = ""), constraints).isSuccess)
        assertTrue(ArticleQualityPolicy.validate(raw(english = List(1200) { "word" }.joinToString(" "), chinese = ""), constraints).isSuccess)
    }

    @Test
    fun importedConstraintsRejectOutOfRangeBodies() {
        val constraints = ArticleQualityPolicy.forImported
        assertTrue(ArticleQualityPolicy.validate(raw(english = List(39) { "word" }.joinToString(" "), chinese = ""), constraints).isFailure)
        assertTrue(ArticleQualityPolicy.validate(raw(english = List(1201) { "word" }.joinToString(" "), chinese = ""), constraints).isFailure)
    }
}
