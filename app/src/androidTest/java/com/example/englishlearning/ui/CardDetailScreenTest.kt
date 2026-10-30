package com.example.englishlearning.ui

import androidx.compose.runtime.Composer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.AnnotatedString
import kotlin.jvm.functions.Function1
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.learning.domain.DerivedWord
import com.example.englishlearning.learning.domain.PhraseEntry
import com.example.englishlearning.learning.domain.RelatedWord
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.learning.domain.WordSense
import com.example.englishlearning.wordqa.WordAiNote
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
        // 词性两处按原型共存：词头的 `.word-pos`（card_detail_pos）+ 释义行的词性栏。
        composeRule.onNodeWithTag("card_detail_pos").assertExists()
        composeRule.onNodeWithTag("card_detail_part_of_speech").assertExists()
        composeRule.onAllNodesWithText("n.").assertCountEquals(2)
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
        // 词形变化与派生词合并成「词形与派生」一节（原型 sWordDetail：一节两行）
        composeRule.onNodeWithText("词形与派生").assertExists()
        composeRule.onNodeWithText("abilities").assertExists()
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
        // 缺模块整块隐藏：连小节标题也不留
        composeRule.onNodeWithText("例句").assertDoesNotExist()
        composeRule.onNodeWithText("词形与派生").assertDoesNotExist()
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
    fun relearnActionIsHiddenWithoutCallback() {
        composeRule.setContent { CardDetailScreen(card = card(), onBack = {}) }
        composeRule.onNodeWithContentDescription("重新学习 ability").assertDoesNotExist()
    }

    @Test
    fun detailOffersAmericanByDefaultAndSeparateBritishAction() {
        var american = 0
        var british = 0
        composeRule.setContent {
            CardDetailScreen(
                card = card(),
                onBack = {},
                onSpeak = { american++ },
                onSpeakBritish = { british++ },
            )
        }

        // 两口音靠共享药片的 tag 分档，屏上可见文字是「状态档 + 口音尾缀」（Task 16 归一）。
        composeRule.onNodeWithTag("card_detail_speak").assertExists().assertHasClickAction().performClick()
        composeRule.onNodeWithTag("card_detail_speak_british").assertExists().assertHasClickAction().performClick()

        assertEquals(1, american)
        assertEquals(1, british)

        composeRule.onAllNodesWithText("美", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("英", substring = true).assertCountEquals(1)
        // 旧私有件把词头拼进朗读定语那一套已废：屏侧不再有这两条描述。
        composeRule.onNodeWithContentDescription("播放 ability 发音").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("播放 ability 英式发音").assertDoesNotExist()
    }

    @Test
    fun regenerateActionsAreRemovedWithKokoroOnDeviceSynthesis() {
        // Kokoro 端侧实时合成后"重新生成"语义已消失（无缓存、无 API 成本）：
        // 详情页不得再出现任何重新生成入口，口音切换由 美/英 两个发音按钮承担。
        composeRule.setContent {
            CardDetailScreen(card = card(), onBack = {})
        }
        composeRule.onNodeWithContentDescription("重新生成 ability 美式发音").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("重新生成 ability 英式发音").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_regenerate_american").assertDoesNotExist()
        composeRule.onNodeWithTag("card_detail_regenerate_british").assertDoesNotExist()
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

        // 发音入口归一到共享药片后，屏侧不再有「播放 ability 发音」这条朗读定语，点它只认 tag。
        composeRule.onNodeWithTag("card_detail_speak").performClick()
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
            Function1::class.java,
            Boolean::class.javaObjectType,
            // 原始/装箱 Boolean 均为无障碍呈现开关（播控组是否渲染、暂停是否可用），不承载任何业务实体。
            Boolean::class.javaPrimitiveType,
            List::class.java,
            WordAiNote::class.java,
            PronunciationStatus::class.java,
            PersonalNoteSaveStatus::class.java,
            Int::class.javaPrimitiveType,
            Class.forName("kotlin.jvm.internal.DefaultConstructorMarker"),
        )

        assertTrue(parameterTypes.contains(WordCard::class.java))
        assertTrue(parameterTypes.count { it == Function0::class.java } >= 3)
        assertTrue(
            parameterTypes.filterNot { it in allowedTypes }.joinToString() { it.name },
            parameterTypes.all { it in allowedTypes },
        )
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

    // ---------------------------------------------------------------- 版式（2026-09-30 真机走查）

    /** 有多词性释义时只列一遍：不再在顶部重复第一条释义。 */
    @Test
    fun meaningIsListedOnceWhenSensesArePresent() {
        composeRule.setContent {
            CardDetailScreen(
                card = card(
                    lemma = "surf",
                    meaningZh = "冲浪；（在互联网上）冲浪，浏览",
                    senses = listOf(WordSense("v.", "冲浪；（在互联网上）冲浪，浏览"), WordSense("n.", "拍岸碎浪；浪")),
                ),
                onBack = {},
            )
        }

        composeRule.onAllNodesWithText("冲浪；（在互联网上）冲浪，浏览").assertCountEquals(1)
        composeRule.onNodeWithText("v.").assertExists()
        // 「n.」两处按原型共存：词头 `.word-pos` 一枚 + 第二条释义的词性栏一枚。
        composeRule.onAllNodesWithText("n.").assertCountEquals(2)
    }

    @Test
    fun backSharesTheTitleRowWithAFullTouchTarget() {
        composeRule.setContent { CardDetailScreen(card = card(), onBack = {}) }

        val title = composeRule.onNodeWithText("词义详情").getUnclippedBoundsInRoot()
        val back = composeRule.onNodeWithContentDescription("返回").getUnclippedBoundsInRoot()
        val titleCenter = (title.top + title.bottom) / 2
        assertTrue("返回应与标题同一行", back.top <= titleCenter && titleCenter <= back.bottom)
        assertTrue("返回的点击区域至少 48dp 高", back.bottom - back.top >= 48.dp)
    }

    /**
     * 原型 `sWordDetail` 的口音排在音标**下一行**（`p.word-ipa` 之后是一行 `btn-secondary btn-sm`），
     * 不挤在音标同一行里；本仓发音入口走共享药片，药片比原型 `btn-sm` 高（48dp 触控下限），
     * 所以这里判「整枚落在这行下方、且紧贴音标行」，不再断言同一行相交。
     */
    /**
     * F4-01 决策⑦ + 原型 `barTop`：收藏是页头右上角的一枚图标键（`icon-btn`），不是正文里的动作药片。
     * 断言三件事：与标题同一行、触控区 ≥48dp（本仓的可点下限）、点下去走宿主回调。
     */
    @Test
    fun favoriteToggleLivesInTheHeaderRow() {
        var toggled = 0
        composeRule.setContent {
            CardDetailScreen(
                card = card(),
                onBack = {},
                favoritePresent = false,
                onToggleFavorite = { toggled++ },
            )
        }

        val toggle = composeRule.onNodeWithContentDescription("收藏 ability")
        toggle.assertExists().assertHasClickAction()

        val title = composeRule.onNodeWithText("词义详情").getUnclippedBoundsInRoot()
        val bounds = toggle.getUnclippedBoundsInRoot()
        val titleCenter = (title.top + title.bottom) / 2
        assertTrue(
            "收藏键应与页头标题同一行（原型 barTop 右侧 icon-btn），实际 title=$title toggle=$bounds",
            bounds.top <= titleCenter && titleCenter <= bounds.bottom,
        )
        assertTrue(
            "收藏键的触控区至少 48dp 高，实际=$bounds",
            bounds.bottom - bounds.top >= 48.dp,
        )

        toggle.performClick()
        composeRule.waitForIdle()
        assertEquals(1, toggled)
        // 宿主没翻转状态前，键的语义仍是「收藏」——屏侧不做乐观翻转（状态只在宿主）。
        composeRule.onNodeWithContentDescription("取消收藏 ability").assertDoesNotExist()
    }

    /** 宿主不给收藏状态 / 回调时，页头尾槽整枚不渲染（不占位、不留空键）。 */
    @Test
    fun favoriteToggleStaysHiddenWithoutHostState() {
        composeRule.setContent { CardDetailScreen(card = card(), onBack = {}) }

        composeRule.onNodeWithTag("card_detail_favorite_toggle").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("收藏 ability").assertDoesNotExist()
    }

    @Test
    fun speakButtonsSitOnTheirOwnRowBelowTheIpa() {
        composeRule.setContent { CardDetailScreen(card = card(), onBack = {}) }

        val ipa = composeRule.onNodeWithTag("card_detail_ipa").getUnclippedBoundsInRoot()
        val speak = composeRule.onNodeWithTag("card_detail_speak").getUnclippedBoundsInRoot()
        assertTrue(
            "发音入口应在音标行下方（原型：音标一行、口音与次键一行），实际 ipa=$ipa speak=$speak",
            speak.top >= ipa.bottom,
        )
        assertTrue(
            "发音入口应紧跟音标行，不应被推到远处，实际间距=${speak.top - ipa.bottom}",
            speak.top - ipa.bottom <= 24.dp,
        )
    }

    /**
     * F5-03 / Task 10（B1 图标按钮）：详情页美/英两个发音喇叭的触控目标必须 ≥48dp 见方。
     *
     * 两条口径都跑（dispatch-notes §B）：
     *  1. brief 的 `assertHeightIsAtLeast` / `assertWidthIsAtLeast` 读的是**裁剪后** boundsInRoot，
     *     所以断言前先 `performScrollTo()` 把节点滚进首屏——屏外节点会被滚动容器裁剪成 0×0（假红），
     *     本分支 T9 修复轮已在 :442 / :470 两例为同一问题补过锚点；
     *  2. 关键站点另用 `getUnclippedBoundsInRoot` 断真实尺寸，与同文件
     *     `backSharesTheTitleRowWithAFullTouchTarget` 的既有先例同形，不受外层裁剪影响。
     *
     * 与 brief 字面的一处偏离（编译取证）：composeBom 2025.12.00 → ui-test 1.10.0 里
     * `assertHeightIsAtLeast` / `assertWidthIsAtLeast` 直接收 `Dp`
     * （javap：`assertHeightIsAtLeast-3ABfNKs(SemanticsNodeInteraction, float)`），
     * `androidx.compose.ui.unit` 并不存在 brief 写的 `Dp.minHeight()/minWidth()`（编译报 Unresolved reference），
     * 故写成 `assertHeightIsAtLeast(48.dp)`。断言强度一字未减。
     */
    @Test
    fun accentSpeakerButtonsMeetTheMinimumTouchTarget() {
        composeRule.setContent {
            CardDetailScreen(card = card(), onBack = {}, onSpeak = {}, onSpeakBritish = {})
        }

        listOf("card_detail_speak", "card_detail_speak_british").forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp)

            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue(
                "$tag 的触控目标应至少 48dp 高，实际=$bounds",
                bounds.bottom - bounds.top >= 48.dp,
            )
            assertTrue(
                "$tag 的触控目标应至少 48dp 宽，实际=$bounds",
                bounds.right - bounds.left >= 48.dp,
            )
        }
    }

    /** 配图是理解词义的主要辅助，紧跟单词卡，而不是滑到最底才看到。 */
    @Test
    fun illustrationComesBeforeTheExample() {
        composeRule.setContent { CardDetailScreen(card = card(), onBack = {}) }

        val image = composeRule.onNodeWithTag("card_detail_illustration").getUnclippedBoundsInRoot()
        val example = composeRule.onNodeWithTag("card_detail_example").getUnclippedBoundsInRoot()
        assertTrue("配图应在例句之前", image.bottom <= example.top)
    }

    @Test
    fun exampleIsAHeadedSectionWithTheWordInBold() {
        composeRule.setContent {
            CardDetailScreen(
                card = card(example = "She has the ability to explain ideas.", exampleZh = "她有能力解释想法。"),
                onBack = {},
            )
        }

        composeRule.onNodeWithText("例句").assertExists()
        val example = composeRule.onNodeWithTag("card_detail_example").annotatedText()
        val start = example.text.indexOf("ability")
        assertTrue("例句中的单词应加粗", example.isBold(start until start + "ability".length))
    }

    /**
     * 关联词三节按原型形状渲染（批 1 Task 11）：派生词与词形变化并进「词形与派生」一节，
     * 各按「；」/「 · 」连接成一串；近义词是静态芯片；常用搭配一行搭配 + 一行释义。
     * 旧「lemma + 释义」词行与私有行件已从本屏退场（其他屏对 `WordRow` 的用法不受影响）。
     */
    @Test
    fun relationSectionsFollowThePrototypeShapes() {
        composeRule.setContent {
            CardDetailScreen(
                card = card(
                    lemma = "surf",
                    derived = listOf(DerivedWord("surfer", "n.", "冲浪者；网虫")),
                    inflections = listOf("surfed", "surfing"),
                    phrases = listOf(PhraseEntry("surf the Internet", "在网上冲浪")),
                    synonyms = listOf(RelatedWord("breaker", "n.", "碎浪")),
                ),
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("card_detail_wordforms").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("词形与派生").assertExists()
        // 派生词只留 lemma 串（原型 `w.derived.join('；')`）：不再有词性 / 释义两栏。
        assertEquals(
            "surfer",
            composeRule.onNodeWithTag("card_detail_derived").annotatedText().text,
        )
        assertEquals(
            "surfed · surfing",
            composeRule.onNodeWithTag("card_detail_inflections").annotatedText().text,
        )

        composeRule.onNodeWithTag("card_detail_synonyms").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("breaker").assertExists()

        composeRule.onNodeWithTag("card_detail_phrases").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("surf the Internet").assertExists()
        composeRule.onNodeWithText("在网上冲浪").assertExists()

        // 「n.」两处按原型共存：词头 `.word-pos` 一枚 + 释义行词性栏一枚；三节关联词里没有第三处。
        composeRule.onAllNodesWithText("n.").assertCountEquals(2)
        // 私有行件那一整条拼接文本不再存在。
        composeRule.onNodeWithText("surfer  n.  冲浪者；网虫").assertDoesNotExist()
    }

    // ---------------------------------------------------------------- 我的笔记展示栏（2026-10-13 真机反馈两轮迭代）

    /** 笔记直接展示在例句下方的「我的笔记」框里；添加/删除仍在弹窗内完成。 */
    @Test
    fun notesAreDisplayedBelowTheExampleAndManagedInADialog() {
        var deleted: String? = null
        composeRule.setContent {
            CardDetailScreen(
                card = card(),
                onBack = {},
                notes = listOf(wordNote("n1", "记住：able + ity")),
                onSavePersonalNote = {},
                onDeleteNote = { deleted = it },
            )
        }

        // 不点任何东西就能看到笔记正文——这是用户第二轮反馈的核心诉求。
        composeRule.onNodeWithText("记住：able + ity").assertExists()
        composeRule.onNodeWithTag("card_detail_notes_section").assertExists()
        // 顶部入口按钮已废弃：避免两个入口。
        composeRule.onNodeWithTag("card_detail_notes_entry").assertDoesNotExist()
        // 展示栏位于例句框下方（阅读顺序契约）。
        // I-7（Task 9 评审）：本分支无障碍批改动后页面整体高于一屏，笔记栏落在首屏外时
        // boundsInRoot 会被滚动容器裁剪成空矩形（top 归 0），位置比较即失真——补 performScrollTo 锚点。
        // N-2（Task 10 携带的 T9 复评 Minor）：复评指出「两个节点都被滚进同一可视区后，
        // 裁剪坐标的比较可能退化为恒真」（滚到位后 top 差值只反映视口排布，不再反映文档顺序）。
        // 现改用 `getUnclippedBoundsInRoot`（同文件 `illustrationComesBeforeTheExample` 的既有先例口径）：
        // 它给出未裁剪的根坐标，值随真实文档顺序而变，与是否在首屏无关；同时把比较收紧为
        // 「笔记顶边在例句文本底边之下」，比原来的 top>top 更强。performScrollTo 保留（点击类断言仍需锚点）。
        // 证伪力：伪修反证把笔记栏移到例句上方后本例必红，见报告 §N-2。
        composeRule.onNodeWithTag("card_detail_notes_section").performScrollTo()
        composeRule.waitForIdle()
        val exampleBounds = composeRule.onNodeWithTag("card_detail_example").getUnclippedBoundsInRoot()
        val notesBounds = composeRule.onNodeWithTag("card_detail_notes_section").getUnclippedBoundsInRoot()
        assertTrue(
            "我的笔记展示栏应在例句下方，实际 example=$exampleBounds notes=$notesBounds",
            notesBounds.top > exampleBounds.bottom,
        )

        composeRule.onNodeWithTag("card_detail_notes_manage").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("card_detail_personal_note_input").assertExists()
        composeRule.onNodeWithTag("card_detail_note_delete_n1").performClick()
        composeRule.waitForIdle()
        assertEquals("n1", deleted)

        composeRule.onNodeWithTag("card_detail_notes_dialog_close").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun emptyNotesSectionStillInvitesTheFirstNoteThroughTheDialog() {
        composeRule.setContent {
            CardDetailScreen(card = card(), onBack = {}, notes = emptyList(), onSavePersonalNote = {})
        }

        composeRule.onNodeWithTag("card_detail_notes_section").assertExists()
        // I-7 同上（T8 先例锚点）：屏外节点的 performClick 会落到空矩形的中心（屏幕左上角），
        // 弹窗根本不会被打开——先滚入首屏再点。
        composeRule.onNodeWithTag("card_detail_notes_manage").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("card_detail_notes_empty").assertExists()
        composeRule.onNodeWithTag("card_detail_personal_note_input").assertExists()
    }

    private fun wordNote(noteId: String, answer: String) = WordAiNote(
        noteId = noteId,
        profileId = "default",
        wordBookId = "cet4",
        cardId = "placeholder:cet4:ability",
        lemma = "ability",
        kind = com.example.englishlearning.wordqa.WordQaKind.Personal,
        answer = answer,
        createdAtEpochMillis = 1L,
    )

    private fun SemanticsNodeInteraction.annotatedText(): AnnotatedString =
        fetchSemanticsNode().config[SemanticsProperties.Text].first()

    private fun AnnotatedString.isBold(range: IntRange): Boolean =
        spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start <= range.first && it.end > range.last }

    private fun card(
        lemma: String = "ability",
        meaningZh: String = "能力；才能",
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
        meaningZh = meaningZh,
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
