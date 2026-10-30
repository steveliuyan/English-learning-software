package com.example.englishlearning.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
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
        // 批 3 Task 3：旧偏好里的 `FULL_TRANSLATION` 起手不再有第三枚 chip，而是折叠成
        // 「双语 chip 选中 + 译文已展开」的呈现等价（VM 侧 `setMode` 语义零改动）。
        // 上面那条 `article_translation` 断言逐字保留，这里只加严：第三档标识必须为 0，
        // 双语 chip 必须在场，且文案已变成「收起译文」（= 已展开）。
        composeRule.onNodeWithTag("article_mode_full_translation").assertDoesNotExist()
        composeRule.onNodeWithTag("article_mode_bilingual").assertExists()
        composeRule.onNodeWithTag("article_translation_toggle").assertTextContains("收起译文", substring = true)
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

    /**
     * Task 22-K（#14）：原型 `sReadTranslate` 的「只看中文」在本仓是屏内开关。
     * 点开后英文正文整块不组合、译文与提醒在场；再点「看回英文」回到原状。
     */
    @Test
    fun chineseOnlyHidesTheEnglishBodyAndRevealsTheTranslation() {
        composeRule.setContent {
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

        composeRule.onNodeWithTag("article_chinese_only_toggle").performClick()
        composeRule.onNodeWithTag("article_english").assertDoesNotExist()
        composeRule.onNodeWithTag("article_translation").assertExists()
        composeRule.onNodeWithTag("article_translation_caution").assertExists()

        composeRule.onNodeWithTag("article_chinese_only_toggle").performClick()
        composeRule.onNodeWithTag("article_english").assertExists()
    }

    /**
     * Task 22-K（#13）：原型 `.foot` 两枚并列。原型「双语」是跳页，本仓是切到对照态；
     * 译文折叠时点它必须把译文展开（而不是只改模式），并且没有译文时这枚不画。
     */
    @Test
    fun footerBilingualExpandsTheTranslationAndDisappearsWithoutOne() {
        composeRule.setContent {
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

        composeRule.onNodeWithTag("article_translation").assertDoesNotExist()
        composeRule.onNodeWithTag("article_open_bilingual").performClick()
        composeRule.onNodeWithTag("article_translation").assertExists()
        composeRule.onNodeWithTag("article_complete_reading").assertExists()
    }

    @Test
    fun footerBilingualStaysOffWhenTheArticleHasNoTranslation() {
        composeRule.setContent {
            ArticleReadingScreen(
                state(article(chineseText = "")),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
            )
        }

        composeRule.onNodeWithTag("article_open_bilingual").assertDoesNotExist()
        composeRule.onNodeWithTag("article_chinese_only_toggle").assertIsNotEnabled()
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

    /**
     * Task 26 Step 1（F5-03 移交账）：正文整段是一个合并语义节点，TalkBack 聚焦时只能念整篇；
     * 每个高亮词必须以**自定义动作**（朗读时逐词可选出）暴露，且动作标签带上目标词。
     * 同时钉住 `TextRange`：`word` 注解的区间必须逐词落在正文里，不是整段一个注解。
     */
    @Test
    fun everyHighlightExposesAPerWordActionWithItsTextRange() {
        val twoWords = article(englishText = "apple banana", coveredLemmas = listOf("apple", "banana"))
        composeRule.setContent {
            ArticleReadingScreen(
                state(twoWords, cards = listOf(appleCard)),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
            )
        }

        val node = composeRule.onNodeWithTag("article_english").fetchSemanticsNode()
        val labels = node.config[SemanticsActions.CustomActions].map { it.label }
        org.junit.Assert.assertEquals(listOf("打开词卡：apple", "查词：banana"), labels)

        val body = node.config[SemanticsProperties.Text].first()
        val ranges = body.getStringAnnotations("word", 0, body.length)
        org.junit.Assert.assertEquals(
            listOf("apple" to (0 to 5), "banana" to (6 to 12)),
            ranges.map { it.item to (it.start to it.end) },
        )
    }

    /** 逐词动作必须与点按同一落点：有词卡的开词卡，没词卡的走词典占位。 */
    @Test
    fun perWordActionsRouteExactlyLikeTappingTheWord() {
        val opened = mutableListOf<WordCard>()
        var dictionaryOpened = false
        val twoWords = article(englishText = "apple banana", coveredLemmas = listOf("apple", "banana"))
        composeRule.setContent {
            ArticleReadingScreen(
                state(twoWords, cards = listOf(appleCard)),
                onBack = {},
                onOpenCard = { opened += it },
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = { dictionaryOpened = true },
                onOpenPronunciationPlaceholder = {},
            )
        }

        val actions = composeRule.onNodeWithTag("article_english")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.first { it.label == "打开词卡：apple" }.action()
        actions.first { it.label == "查词：banana" }.action()

        org.junit.Assert.assertEquals(listOf(appleCard), opened)
        org.junit.Assert.assertTrue(dictionaryOpened)
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
    fun pronunciationEntrySendsArticleBodyToSentenceReader() {
        var spoken: String? = null
        composeRule.setContent {
            ArticleReadingScreen(
                state(article()),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
                onSpeakSentence = { spoken = it },
            )
        }

        composeRule.onNodeWithTag("article_pronunciation").performClick()

        org.junit.Assert.assertEquals(article().englishText, spoken)
    }

    @Test
    fun modeChipsCallOnModeChange() {
        var changed: ArticleDisplayMode? = null
        composeRule.setContent {
            ArticleReadingScreen(state(article()), onBack = {}, onOpenCard = {}, onModeChange = { changed = it }, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        // 批 3 Task 3：模式行只剩两枚 chip + 同一行的一枚译文开关（原「全文对照」第三枚已折进
        // 「双语 + 译文展开」）。原用例点的是第三枚并期望 `FULL_TRANSLATION`——那正是被撤掉的那档，
        // 现在改成「两枚 chip 都能发回调 + 第三枚标识必须为 0」，档位覆盖不减（ENGLISH_FIRST 原来没被
        // 覆盖，这里顺带补上）。
        composeRule.onNodeWithTag("article_mode_full_translation").assertDoesNotExist()
        composeRule.onNodeWithTag("article_mode_english_first").assertExists()
        composeRule.onNodeWithTag("article_mode_bilingual").assertExists()
        composeRule.onNodeWithTag("article_translation_toggle").assertExists()

        composeRule.onNodeWithTag("article_mode_bilingual").performClick()
        org.junit.Assert.assertEquals(ArticleDisplayMode.BILINGUAL, changed)

        composeRule.onNodeWithTag("article_mode_english_first").performClick()
        org.junit.Assert.assertEquals(ArticleDisplayMode.ENGLISH_FIRST, changed)
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
        // 批 3 Task 3：载体从居中 `GlassDialog` 换成底部面板（`ModalBottomSheet`，一次升到展开位）。
        // 四枚 tag 全部保留，另加一条加严判据：正文**仍然在场且可见**——这是这次换载体的全部目的
        // （原来的对话框进文第一帧把正文整块挡住，用户得先点「我知道了」才看得到文章）。
        // 只断言可见性、不 performClick：面板下方的遮罩仍会吃掉触摸，能看见才是这一条要的结论。
        composeRule.onNodeWithTag("article_english").assertExists()
        composeRule.onNodeWithTag("article_english").assertIsDisplayed()
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
    fun marksSwitchExposesChineseLabel() {
        // F5-03 决策 §4 行为轨：TalkBack 读到的必须是人话，不是「开/关」（契约轨见 AccessibilitySwitchLabelContractTest）。
        composeRule.setContent {
            ArticleReadingScreen(state(article()), onBack = {}, onOpenCard = {}, onModeChange = {}, onToggleTranslation = {}, onOpenDictionaryPlaceholder = {}, onOpenPronunciationPlaceholder = {})
        }

        composeRule.onNodeWithTag("article_marks_switch")
            .assertContentDescriptionContains("标记已背的词", substring = true)
    }

    @Test
    fun completeReadingButtonInvokesCallback() {
        var completed = 0
        composeRule.setContent {
            ArticleReadingScreen(
                state(article()),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
                onCompleteReading = { completed++ },
            )
        }

        composeRule.onNodeWithTag("article_complete_reading").performClick()
        composeRule.waitForIdle()

        org.junit.Assert.assertEquals(1, completed)
    }

    @Test
    fun completedReadingButtonIsDisabled() {
        composeRule.setContent {
            ArticleReadingScreen(
                state(article()).copy(completed = true),
                onBack = {},
                onOpenCard = {},
                onModeChange = {},
                onToggleTranslation = {},
                onOpenDictionaryPlaceholder = {},
                onOpenPronunciationPlaceholder = {},
            )
        }

        composeRule.onNodeWithTag("article_complete_reading").assertIsNotEnabled()
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
