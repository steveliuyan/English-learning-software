package com.example.englishlearning.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.reading.domain.Article
import com.example.englishlearning.reading.domain.ArticleDisplayMode
import com.example.englishlearning.reading.domain.ArticleLengthTier
import com.example.englishlearning.reading.domain.ArticleSource
import com.example.englishlearning.reading.domain.ArticleType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleReadingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val appleCard = WordCard("c1", "cet4", "apple", "", "", "")
    private val bananaCard = WordCard("c2", "cet4", "banana", "", "", "")

    private fun article(
        englishText: String = "The apple grows in the garden",
        chineseText: String = "苹果长在园子里。",
        coveredLemmas: List<String> = listOf("apple"),
        source: ArticleSource = ArticleSource.AiGenerated(modelName = "gpt-x", parameterSummary = "model=gpt-x temperature=0.7"),
        generatedAtEpochMillis: Long = 1_760_000_000_000L,
    ) = Article(
        articleId = "a1",
        profileId = "p1",
        localDate = "2026-09-24",
        activeWordBookId = "cet4",
        articleType = ArticleType.STORY,
        lengthTier = ArticleLengthTier.STANDARD,
        version = 1,
        title = "Apple Day",
        englishText = englishText,
        chineseText = chineseText,
        generatedAtEpochMillis = generatedAtEpochMillis,
        coveredLemmas = coveredLemmas,
        source = source,
    )

    private fun state(
        article: Article,
        mode: ArticleDisplayMode = ArticleDisplayMode.BILINGUAL,
        cards: List<WordCard> = listOf(appleCard, bananaCard),
    ): ArticleReadingUiState {
        val coverage = com.example.englishlearning.reading.ArticleHighlightPolicy.derive(article.englishText, article.coveredLemmas)
        return ArticleReadingUiState(
            article = article,
            mode = mode,
            translationExpanded = mode == ArticleDisplayMode.FULL_TRANSLATION,
            highlights = coverage.highlights,
            uncoveredLemmas = coverage.uncoveredLemmas,
            cards = cards,
        )
    }

    @Test
    fun bilingualModeStartsCollapsedAndToggleRevealsTheTranslation() {
        composeRule.setContent {
            // 屏幕是无状态组合函数：用 snapshot state 驱动「展开/收起」的真实重组。
            var expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            ArticleReadingScreen(
                state(article()).copy(translationExpanded = expanded.value),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = { expanded.value = !expanded.value },
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
            )
        }

        composeRule.onNodeWithTag("article_reading_screen").assertExists()
        composeRule.onNodeWithTag("article_title").assertExists()
        composeRule.onNodeWithTag("article_english").assertExists()
        composeRule.onNodeWithTag("article_translation").assertDoesNotExist()
        composeRule.onNodeWithTag("article_translation_toggle").performClick()
        composeRule.onNodeWithTag("article_translation").assertExists()
    }

    @Test
    fun fullTranslationStartsExpanded() {
        composeRule.setContent {
            ArticleReadingScreen(state(article(), mode = ArticleDisplayMode.FULL_TRANSLATION), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("article_translation").assertExists()
    }

    @Test
    fun englishFirstHidesTheTranslationUntilToggled() {
        composeRule.setContent {
            var expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            ArticleReadingScreen(
                state(article(), mode = ArticleDisplayMode.ENGLISH_FIRST).copy(translationExpanded = expanded.value),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = { expanded.value = !expanded.value },
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
            )
        }

        composeRule.onNodeWithTag("article_translation").assertDoesNotExist()
        composeRule.onNodeWithTag("article_translation_toggle").performClick()
        composeRule.onNodeWithTag("article_translation").assertExists()
    }

    @Test
    fun clickingAHighlightOpensTheCard() {
        val opened = mutableListOf<WordCard>()
        // 整个正文就是一个高亮词：点击文本节点中心必然落在该词的区间内。
        val singleWord = article(englishText = "apple", coveredLemmas = listOf("apple"))
        composeRule.setContent {
            ArticleReadingScreen(state(singleWord), onBack = {}, onOpenCard = { opened += it }, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("article_english").performClick()

        org.junit.Assert.assertEquals(listOf(appleCard), opened)
    }

    @Test
    fun clickingAnUncoveredChipWithoutACardOpensTheDictionaryPlaceholder() {
        var dictionaryOpened = false
        // banana 在请求词表里但没出现在正文：chips 命中不了词卡时走词典占位。
        val withUncovered = article(englishText = "The apple grows", coveredLemmas = listOf("apple", "banana"))
        composeRule.setContent {
            ArticleReadingScreen(
                state(withUncovered, cards = listOf(appleCard)),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = { dictionaryOpened = true },
                onOpenPronunciationPlaceholder = {},
            )
        }

        composeRule.onNodeWithTag("article_uncovered_banana").performClick()

        org.junit.Assert.assertTrue(dictionaryOpened)
    }

    @Test
    fun clickingAnUncoveredChipWithACardOpensTheCard() {
        val opened = mutableListOf<WordCard>()
        val withUncovered = article(englishText = "The apple grows", coveredLemmas = listOf("apple", "banana"))
        composeRule.setContent {
            ArticleReadingScreen(
                state(withUncovered, cards = listOf(appleCard, bananaCard)),
                onBack = {},
                onOpenCard = { opened += it },
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
            )
        }

        composeRule.onNodeWithTag("article_uncovered_banana").performClick()

        org.junit.Assert.assertEquals(listOf(bananaCard), opened)
    }

    @Test
    fun pronunciationEntryCallsThePlaceholder() {
        var pronunciationOpened = false
        composeRule.setContent {
            ArticleReadingScreen(state(article()), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = { pronunciationOpened = true })
        }

        composeRule.onNodeWithTag("article_pronunciation").performClick()

        org.junit.Assert.assertTrue(pronunciationOpened)
    }

    @Test
    fun modeChipsCallOnModeChange() {
        var changed: ArticleDisplayMode? = null
        composeRule.setContent {
            ArticleReadingScreen(state(article()), onBack = {}, onOpenCard = {}, onModeChange = { changed = it }, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("article_mode_full_translation").performClick()

        org.junit.Assert.assertEquals(ArticleDisplayMode.FULL_TRANSLATION, changed)
    }

    @Test
    fun scriptTextIsRenderedAsPlainText() {
        // 不可信输入的防线在质量闸，但界面仍必须把任何存进来的内容当纯文本渲染，
        // 不能因为包含标记字样就执行或丢弃。
        val sneaky = article(englishText = "<script>alert(1)</script> the apple", coveredLemmas = listOf("apple"))
        composeRule.setContent {
            ArticleReadingScreen(state(sneaky), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("article_english").assertTextContains("<script>alert(1)</script>", substring = true)
    }

    @Test
    fun showsTheModelNameForAGeneratedArticle() {
        composeRule.setContent {
            ArticleReadingScreen(state(article(source = ArticleSource.AiGenerated(modelName = "gpt-x", parameterSummary = "model=gpt-x"))), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("article_source_name").assertTextContains("gpt-x", substring = true)
    }

    @Test
    fun showsTheAttributionAndLinkForAFetchedArticle() {
        val fetched = article(
            source = ArticleSource.WebFetched(
                sourceId = "voa-learning-english",
                displayName = "VOA Learning English",
                articleUrl = "https://learningenglish.voanews.com/a/x/7998765.html",
                licenseNote = "VOA 公有领域",
                attributionText = "learningenglish.voanews.com",
            ),
            chineseText = "",
        )
        composeRule.setContent {
            ArticleReadingScreen(state(fetched), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("article_source_name").assertTextContains("VOA Learning English", substring = true)
        composeRule.onNodeWithTag("article_attribution").assertTextContains("learningenglish.voanews.com", substring = true)
        composeRule.onNodeWithTag("article_source_url_label").assertExists()
        composeRule.onNodeWithTag("article_source_link").assertTextContains("https://learningenglish.voanews.com/a/x/7998765.html", substring = true)
    }

    @Test
    fun doesNotExecuteTheSourceLinkAutomatically() {
        val fetched = article(
            source = ArticleSource.WebFetched(
                sourceId = "voa-learning-english",
                displayName = "VOA Learning English",
                articleUrl = "https://learningenglish.voanews.com/a/x/7998765.html",
                licenseNote = "VOA 公有领域",
                attributionText = "learningenglish.voanews.com",
            ),
            chineseText = "",
        )
        composeRule.setContent {
            ArticleReadingScreen(state(fetched), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        // 链接节点存在，但没有任何点击动作挂载——「不自动打开」在语义层可断言。
        val config = composeRule.onNodeWithTag("article_source_link").fetchSemanticsNode().config
        org.junit.Assert.assertFalse(config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun neverShowsTheEndpointOrKeyForAnySource() {
        val generated = article(source = ArticleSource.AiGenerated(modelName = "gpt-x", parameterSummary = "endpoint=https://api.test/v1 key=sk-secret"))
        composeRule.setContent {
            ArticleReadingScreen(state(generated), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        // substring = true：来源区是整段渲染，全等匹配的哨兵对「拼接泄露」恰好免疫。
        composeRule.onNodeWithText("api.test", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("sk-secret", substring = true).assertDoesNotExist()
    }

    @Test
    fun showsTheImportedNoticeAndTimestampForAnImportedArticle() {
        val imported = article(source = ArticleSource.UserImported, generatedAtEpochMillis = 1_760_000_000_000L)
        composeRule.setContent {
            ArticleReadingScreen(state(imported), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("article_source_name").assertTextContains("手动导入", substring = true)
        composeRule.onNodeWithTag("article_source_name").assertTextContains(formatImportedAt(1_760_000_000_000L), substring = true)
        composeRule.onNodeWithTag("article_import_responsibility").assertTextContains("你有权使用", substring = true)
    }

    @Test
    fun notifiesWhenTheSourceHasNoChineseText() {
        val noTranslation = article(chineseText = "")
        composeRule.setContent {
            ArticleReadingScreen(state(noTranslation), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("article_no_translation_notice").assertExists()
        composeRule.onNodeWithTag("article_translation_toggle").assertIsNotEnabled()
        composeRule.onNodeWithTag("article_translation").assertDoesNotExist()
    }

    @Test
    fun coveragePopupListsTheCoveredLemmasOnFirstOpen() {
        // F3-01B：进文覆盖词弹窗——打开文章时告诉用户本文与已背词的关联（竞品对标）。
        val multi = article(englishText = "apple banana", coveredLemmas = listOf("apple", "banana"))
        composeRule.setContent {
            ArticleReadingScreen(state(multi), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("coverage_popup").assertExists()
        composeRule.onNodeWithTag("coverage_popup_count").assertTextContains("2", substring = true)
        composeRule.onNodeWithTag("coverage_chip_apple").assertExists()
        composeRule.onNodeWithTag("coverage_chip_banana").assertExists()
    }

    @Test
    fun coveragePopupClosesAndStaysClosed() {
        composeRule.setContent {
            ArticleReadingScreen(state(article()), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("coverage_popup_close").performClick()
        composeRule.onNodeWithTag("coverage_popup").assertDoesNotExist()
    }

    @Test
    fun coveragePopupIsSkippedWhenTheArticleCoversNothing() {
        val bare = article(coveredLemmas = emptyList())
        composeRule.setContent {
            ArticleReadingScreen(state(bare), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("coverage_popup").assertDoesNotExist()
    }

    @Test
    fun marksSwitchReportsTheNewValue() {
        // F3-01C：标记开关回调把新值交给 VM，由 VM 决定高亮与持久化。
        var reported: Boolean? = null
        composeRule.setContent {
            ArticleReadingScreen(state(article()), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {}, onSetLearnedMarks = { reported = it })
        }

        composeRule.onNodeWithTag("article_marks_switch").performClick()
        org.junit.Assert.assertEquals(false, reported)
    }

    @Test
    fun marksOffRendersNoHighlightsSoTappingTheTextOpensNothing() {
        val opened = mutableListOf<WordCard>()
        val singleWord = article(englishText = "apple", coveredLemmas = listOf("apple"))
        composeRule.setContent {
            ArticleReadingScreen(
                state(singleWord).copy(showLearnedMarks = false, highlights = emptyList()),
                onBack = {},
                onOpenCard = { opened += it },
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
            )
        }

        composeRule.onNodeWithTag("article_english").performClick()
        org.junit.Assert.assertTrue(opened.isEmpty())
    }
}
