package com.example.englishlearning.ui

import androidx.compose.runtime.Composer
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.learning.domain.DerivedWord
import com.example.englishlearning.learning.domain.PhraseEntry
import com.example.englishlearning.learning.domain.RelatedWord
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.learning.domain.WordSense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File
import java.lang.reflect.Modifier as ReflectModifier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Semantics of the F1-06 card detail screen (slice 2 static version).
 *
 * Asserts the same test-tag / content-description contract as [WordCardScreenTest]
 * and, crucially, the "missing module is hidden entirely" rule (spec F1-06 / AC1-12):
 * absent fields and absent illustrations leave no node behind — no empty heading,
 * no "暂无" placeholder, no image area.
 */
@RunWith(AndroidJUnit4::class)
class CardDetailScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun rendersLemmaIpaPartOfSpeechMeaningAndIllustration() {
        composeRule.setContent { CardDetailScreen(card = card(), onBack = {}) }

        composeRule.onNodeWithTag("card_detail_screen").assertExists()
        composeRule.onNodeWithTag("card_detail_lemma").assertExists()
        composeRule.onNodeWithText("ability").assertExists()
        composeRule.onNodeWithTag("card_detail_ipa").assertExists()
        composeRule.onNodeWithText("əˈbɪləti").assertExists()
        composeRule.onNodeWithTag("card_detail_part_of_speech").assertExists()
        composeRule.onNodeWithText("n.").assertExists()
        composeRule.onNodeWithTag("card_detail_meaning").assertExists()
        composeRule.onNodeWithText("能力；才能").assertExists()
        composeRule.onNodeWithTag("card_detail_illustration").assertExists()
    }

    @Test
    fun pronunciationStatusIsShownAsAStableActionableMessage() {
        composeRule.setContent {
            CardDetailScreen(
                card = card(),
                onBack = {},
                pronunciationStatus = PronunciationStatus.Unavailable,
            )
        }

        composeRule.onNodeWithTag("card_detail_pronunciation_message").assertExists()
        composeRule.onNodeWithText("当前语音不可用，请检查语音设置。").assertExists()
    }

    @Test
    fun optionalExampleAndInflectionsRenderWhenPresent() {
        composeRule.setContent {
            CardDetailScreen(
                card = card(example = "She has the ability to explain ideas.", inflections = listOf("abilities")),
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("card_detail_example").assertExists()
        composeRule.onNodeWithText("She has the ability to explain ideas.").assertExists()
        composeRule.onNodeWithTag("card_detail_inflections").assertExists()
        composeRule.onNodeWithText("词形变化：abilities").assertExists()
    }

    @Test
    fun optionalModulesAreAbsentWhenNotProvided() {
        composeRule.setContent {
            CardDetailScreen(
                card = card(example = null, inflections = emptyList()),
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("card_detail_example").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_inflections").assertDoesNotExist()
    }

    @Test
    fun illustrationHiddenForUnknownLemma() {
        composeRule.setContent { CardDetailScreen(card = card(lemma = "mysteryword"), onBack = {}) }

        composeRule.onNodeWithTag("card_detail_illustration").assertDoesNotExist()
    }

    /**
     * 词书包导入的配图走 `imagePath`（应用私有目录里的真实文件），不走内置 drawable 映射。
     * 详情页必须优先渲染它——否则导入册的词卡「有图却看不见」，真机走查会直接暴露。
     */
    @Test
    fun importedImageFileIsRenderedWhenPresent() {
        val imageFile = writeTempImage("imported-card-image.png")
        composeRule.setContent {
            CardDetailScreen(card = card(lemma = "surf", imagePath = imageFile.absolutePath), onBack = {})
        }

        composeRule.onNodeWithTag("card_detail_image_file").assertExists()
        composeRule.onNodeWithContentDescription("配图 surf").assertExists()
    }

    /**
     * 配图文件被外部删除/损坏时不得崩屏，也不得留下一个空图框——退化为「无图」并给出可读提示。
     */
    @Test
    fun unreadableImageFileDegradesWithoutCrashing() {
        val missing = File(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
            "definitely-missing-${System.nanoTime()}.png",
        )
        composeRule.setContent {
            CardDetailScreen(card = card(lemma = "surf", imagePath = missing.absolutePath), onBack = {})
        }

        composeRule.onNodeWithTag("card_detail_image_file").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_image_unreadable").assertExists()
    }

    /**
     * 导入册的扩展字段（多词性释义、例句译文、派生词、短语、近义词）都要在详情页出现。
     * 竞品参考图（`outputs/word-card-reference/reference-surf-detail.png`）把这些列为词详情页基线。
     */
    @Test
    fun sensesExampleTranslationDerivedPhrasesAndSynonymsRender() {
        composeRule.setContent {
            CardDetailScreen(
                card = card(
                    lemma = "surf",
                    senses = listOf(
                        WordSense("v.", "冲浪；（在互联网上）冲浪，浏览"),
                        WordSense("n.", "拍岸碎浪；浪"),
                    ),
                    exampleZh = "我整晚上都在网上闲逛。",
                    derived = listOf(DerivedWord("surfer", "n.", "冲浪者；网虫")),
                    phrases = listOf(PhraseEntry("surf the Internet", "在网上冲浪")),
                    synonyms = listOf(RelatedWord("breaker", "n.", "碎浪")),
                ),
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("card_detail_senses").assertExists()
        composeRule.onNodeWithText("冲浪；（在互联网上）冲浪，浏览", substring = true).assertExists()
        composeRule.onNodeWithText("拍岸碎浪；浪", substring = true).assertExists()
        composeRule.onNodeWithTag("card_detail_example_zh").assertExists()
        composeRule.onNodeWithText("我整晚上都在网上闲逛。", substring = true).assertExists()
        composeRule.onNodeWithTag("card_detail_derived").assertExists()
        composeRule.onNodeWithText("surfer", substring = true).assertExists()
        composeRule.onNodeWithTag("card_detail_phrases").assertExists()
        composeRule.onNodeWithText("surf the Internet", substring = true).assertExists()
        composeRule.onNodeWithTag("card_detail_synonyms").assertExists()
        composeRule.onNodeWithText("breaker", substring = true).assertExists()
    }

    /** 没有扩展字段的占位词卡不得凭空长出这些区块（缺模块整块隐藏）。 */
    @Test
    fun extendedModulesStayHiddenForPlaceholderCards() {
        composeRule.setContent { CardDetailScreen(card = card(), onBack = {}) }

        composeRule.onNodeWithTag("card_detail_senses").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_example_zh").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_derived").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_phrases").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_synonyms").assertDoesNotExist()
    }

    private fun writeTempImage(name: String): File {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "$name-${System.nanoTime()}")
        // 1×1 透明 PNG：只要能解码即可，尺寸不参与断言。
        val bytes = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
            android.util.Base64.DEFAULT,
        )
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun pronunciationAndRelearnActionsInvokeCallbacks() {
        var spoken = 0
        var spokenLemma = ""
        var relearned = 0
        composeRule.setContent {
            CardDetailScreen(
                card = card(),
                onBack = {},
                onSpeak = { spoken++; spokenLemma = "ability" },
                onRelearn = { relearned++ },
            )
        }

        composeRule.onNodeWithContentDescription("播放 ability 发音").performClick()
        composeRule.onNodeWithContentDescription("重新学习 ability").performClick()
        assertEquals("ability", spokenLemma)
        assertEquals(1, spoken)
        assertEquals(1, relearned)
    }

    @Test
    fun cardDetailPublicBoundaryUsesStableAllowlistAndRejectsSensitiveBusinessTypes() {
        val screenMethod = Class.forName("com.example.englishlearning.ui.CardDetailScreenKt")
            .declaredMethods
            .singleOrNull { method ->
                method.name == "CardDetailScreen" && ReflectModifier.isPublic(method.modifiers)
            }

        assertNotNull(screenMethod)
        val parameterTypes = requireNotNull(screenMethod).parameterTypes.toList()
        val allowedTypes = setOf(
            WordCard::class.java,
            Modifier::class.java,
            Composer::class.java,
            Function0::class.java,
            Int::class.javaPrimitiveType,
            Class.forName("kotlin.jvm.internal.DefaultConstructorMarker"),
            // 无字段发音状态枚举：文案由屏幕固定映射，防泄露设计的合法边界类型。
            PronunciationStatus::class.java,
        )

        assertTrue(parameterTypes.contains(WordCard::class.java))
        assertTrue(parameterTypes.count { it == Function0::class.java } >= 3)
        assertTrue(parameterTypes.all { it in allowedTypes })
        assertFalse(parameterTypes.contains(AiProfile::class.java))
        assertFalse(parameterTypes.contains(String::class.java))
        assertFalse(parameterTypes.contains(CharArray::class.java))
    }

    @Test
    fun backInvokesCallback() {
        var backed = 0
        composeRule.setContent { CardDetailScreen(card = card(), onBack = { backed++ }) }

        composeRule.onNodeWithContentDescription("返回").assertExists().performClick()
        composeRule.waitForIdle()

        assertEquals(1, backed)
    }

    @Test
    fun askAiEntryInvokesCallback() {
        var asked = 0
        composeRule.setContent {
            CardDetailScreen(card = card(), onBack = {}, onAskAi = { asked++ })
        }

        composeRule.onNodeWithContentDescription("问 AI（ability）").assertExists().performClick()
        composeRule.waitForIdle()
        assertEquals(1, asked)
    }

    private fun card(
        lemma: String = "ability",
        example: String? = "She has the ability to explain complex ideas simply.",
        inflections: List<String> = emptyList(),
        imagePath: String? = null,
        senses: List<WordSense> = emptyList(),
        exampleZh: String? = null,
        derived: List<DerivedWord> = emptyList(),
        phrases: List<PhraseEntry> = emptyList(),
        synonyms: List<RelatedWord> = emptyList(),
    ) = WordCard(
        cardId = "placeholder:cet4:$lemma",
        wordBookId = "cet4",
        lemma = lemma,
        ipa = "əˈbɪləti",
        partOfSpeech = "n.",
        meaningZh = "能力；才能",
        example = example,
        inflections = inflections,
        imagePath = imagePath,
        senses = senses,
        exampleZh = exampleZh,
        derived = derived,
        phrases = phrases,
        synonyms = synonyms,
    )
}
