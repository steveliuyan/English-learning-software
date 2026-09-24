package com.example.englishlearning.reading

import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticlePromptPolicyTest {
    private fun request(excluded: List<String> = emptyList()) = ArticleGenerationRequest(
        profileId = "p1", localDate = "2026-09-24", wordBookId = "cet4",
        articleType = ArticleType.SCIENCE,
        length = resolveArticleLength("cet4", ArticleLengthTier.STANDARD),
        language = TranslationLanguage.ZH,
        targetCards = listOf(
            WordCard("c1", "cet4", "apple", "/ˈæpl/", "n.", "苹果", inflections = listOf("apples")),
            WordCard("c2", "cet4", "brief", "/briːf/", "adj.", "简短的"),
        ),
        excludedLemmas = excluded,
    )

    @Test
    fun promptCarriesEveryInputTheSpecRequires() {
        val prompt = ArticlePromptPolicy.build(request())
        assertTrue(prompt.user.contains("apple"), "缺少当天完成词")
        assertTrue(prompt.user.contains("apples"), "缺少词形变化")
        assertTrue(prompt.user.contains("cet4"), "缺少词书")
        assertTrue(prompt.user.contains("科普"), "缺少文章类型")
        assertTrue(prompt.user.contains("180"), "缺少长度下限") // cet4 STANDARD = 180..300
        assertTrue(prompt.user.contains("300"), "缺少长度上限")
        assertTrue(prompt.user.contains("中文"), "缺少目标语言")
    }

    @Test
    fun promptListsExcludedLemmasSoARegenerationMovesOn() {
        val prompt = ArticlePromptPolicy.build(request(excluded = listOf("apple")))
        assertTrue(prompt.user.contains("apple"))
    }

    @Test
    fun promptDemandsJsonWithoutAnyCoordinates() {
        val prompt = ArticlePromptPolicy.build(request())
        // 系统提示必须只要求三个字段，且不得要求模型给高亮位置——坐标一律由系统派生。
        assertTrue(prompt.system.contains("title"))
        assertTrue(prompt.system.contains("english"))
        assertTrue(prompt.system.contains("chinese"))
        assertFalse(prompt.system.contains("highlight", ignoreCase = true))
        assertFalse(prompt.system.contains("offset", ignoreCase = true))
    }

    @Test
    fun promptIsDeterministicForTheSameInput() {
        assertEquals(ArticlePromptPolicy.build(request()), ArticlePromptPolicy.build(request()))
    }
}
