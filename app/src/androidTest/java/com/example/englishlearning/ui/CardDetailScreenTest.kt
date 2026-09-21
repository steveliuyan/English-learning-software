package com.example.englishlearning.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.domain.WordCard
import org.junit.Assert.assertEquals
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

    @Test
    fun backInvokesCallback() {
        var backed = 0
        composeRule.setContent { CardDetailScreen(card = card(), onBack = { backed++ }) }

        composeRule.onNodeWithContentDescription("返回").assertExists().performClick()
        composeRule.waitForIdle()

        assertEquals(1, backed)
    }

    private fun card(
        lemma: String = "ability",
        example: String? = "She has the ability to explain complex ideas simply.",
        inflections: List<String> = emptyList(),
    ) = WordCard(
        cardId = "placeholder:cet4:$lemma",
        wordBookId = "cet4",
        lemma = lemma,
        ipa = "əˈbɪləti",
        partOfSpeech = "n.",
        meaningZh = "能力；才能",
        example = example,
        inflections = inflections,
    )
}
