package com.example.englishlearning.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.FavoriteWord
import com.example.englishlearning.learning.VocabularyEntry
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.ui.theme.MintGlassTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 20-A（裁决①「我的词」一页三段）的 connected 面。
 *
 * **本文件同时是控制者 R-20A.1 要的那件实证**：本仓屏一律 state-in（屏不收 ViewModel，收 `UiState` + 回调），
 * 26 枚 `hiltViewModel()` 早已在 `MainActivity.kt:54-85` 同一宿主里并存并把状态注入 `AppScreen`；
 * `AppScreen.kt` 也已在同一组合里同时收集 `VocabularyUiState` 与 `FavoritesUiState`。
 * 于是「三个 ViewModel 同宿主并存」这一风险的真实形态是**三份状态同一次组合**——
 * `three_sections_render_in_one_composition` 就用**一次 `setContent`** 同时喂三段真实形状的 `UiState`，
 * 逐段验「容器在 + 该段自己的数据在 + 数据落在该段容器内」。
 * 一个测试方法只做一次 `setContent` 是本仓硬事实（同一次测试里第二次 `setContent` 真机必崩，
 * 先例见 `TodayPlanScreenTest` 的 F3 两条用例注释），所以五种形态是五个方法而不是五次组合。
 *
 * 墨色一律不在此断言（Task 9 实测口径：connected 读不到取色），形状与 token 口径归
 * `MyWordsScreenContractTest`。
 */
@RunWith(AndroidJUnit4::class)
class MyWordsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val baseInstant: Instant = Instant.parse("2026-10-19T00:30:00Z")

    private fun favorite(lemma: String, index: Int) = FavoriteWord(
        profileId = "p1",
        cardId = "fav-card-$index",
        wordBookId = "cet4",
        lemma = lemma,
        addedAt = baseInstant,
    )

    private fun card(lemma: String, index: Int, meaning: String = "释义$index") = WordCard(
        cardId = "card-$index",
        wordBookId = "cet4",
        lemma = lemma,
        ipa = "",
        partOfSpeech = "n.",
        meaningZh = meaning,
    )

    private fun vocabularyItem(lemma: String, index: Int) = VocabularyItem(
        entry = VocabularyEntry(
            profileId = "p1",
            wordBookId = "cet4",
            cardId = "card-$index",
            addedAt = baseInstant,
            lastFeedback = "Again",
        ),
        card = card(lemma, index),
    )

    /**
     * R-20A.1 实证 + 三段齐现：一次 `setContent`，三段各自的数据都在，且各落在自己的段容器内。
     *
     * 这条同时钉住 R-20A.2 的后半：`section` 是落位参、不是渲染开关——默认落位收藏段时，
     * 生词本段与错词段仍被组合出来（做成「只显示一段的 tab 页」在这里当场红）。
     */
    @Test
    fun three_sections_render_in_one_composition() {
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(words = listOf(favorite("delicious", 0))),
                    vocabularyState = VocabularyUiState.Ready(items = listOf(vocabularyItem("abandon", 1))),
                    wrongWordsState = WrongWordsUiState.Ready(cards = listOf(card("gossamer", 2))),
                )
            }
        }
        composeRule.onNodeWithTag("my_words_screen").assertExists()
        listOf(
            Triple("my_words_section_favorites", "delicious", "收藏"),
            Triple("my_words_section_wordbook", "abandon", "生词本"),
            Triple("my_words_section_wrong", "gossamer", "错词"),
        ).forEach { (tag, lemma, title) ->
            val container = composeRule.onNodeWithTag(tag)
            container.assertExists()
            val row = composeRule.onNodeWithText(lemma)
            row.assertExists()
            composeRule.onNodeWithText(title).assertExists()
            val containerBox = container.fetchSemanticsNode().boundsInRoot
            val rowBox = row.fetchSemanticsNode().boundsInRoot
            assertEquals(
                "词行 $lemma 没有落在自己的段容器 $tag 内（段与段串了 / 数据喂错了段）：" +
                    "容器 $containerBox，行 $rowBox",
                true,
                rowBox.top >= containerBox.top - 1f && rowBox.bottom <= containerBox.bottom + 1f &&
                    rowBox.left >= containerBox.left - 1f && rowBox.right <= containerBox.right + 1f,
            )
        }
    }

    /**
     * 每段的出口：三段行点进详情、收藏行内的「取消收藏」即时生效。
     *
     * 判据是「回调被叫到的顺序、次数与实参」，不是「点击没崩」——三段各自接线，少接一根线就得在这里暴露。
     */
    @Test
    fun rows_and_unfavorite_invoke_the_host_callbacks() {
        val opened = mutableListOf<String>()
        val unfavorited = mutableListOf<String>()
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(
                        words = listOf(favorite("anchor", 0), favorite("beacon", 1)),
                    ),
                    vocabularyState = VocabularyUiState.Ready(items = listOf(vocabularyItem("crisp", 2))),
                    wrongWordsState = WrongWordsUiState.Ready(cards = listOf(card("dune", 3))),
                    onOpenFavoriteWord = { opened += "fav:${it.lemma}" },
                    onUnfavorite = { unfavorited += it.lemma },
                    onOpenVocabularyCard = { opened += "wb:${it.lemma}" },
                    onOpenWrongWord = { opened += "wrong:${it.lemma}" },
                )
            }
        }
        composeRule.onNodeWithTag("my_words_favorite_row_0").performClick()
        composeRule.onNodeWithTag("my_words_wordbook_row_0").performClick()
        composeRule.onNodeWithTag("my_words_wrong_row_0").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("fav:anchor", "wb:crisp", "wrong:dune"), opened)

        composeRule.onNodeWithTag("my_words_favorite_unfavorite_1").assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("beacon"), unfavorited)
    }

    /**
     * 三段全空时的诚实空态：每段各留一条自己的说明，不是一片白屏。
     *
     * m-7（修复轮 2）：这一枚原先只 `assertExists()` 段容器与空态标签，**三句文案本体没有任何判据**——
     * 文案被改成一句话照样绿。评审席要求的正是「逐字文案」承重，所以这里逐段 `assertTextEquals`
     * 钉回三页旧文（`FavoritesScreen.kt:101` / `VocabularyScreen.kt:63` / `WrongWordsScreen.kt:90`）。
     *
     * 朗读层（m-7 第二半）：旧生词页的空态除了文案还带一条专属 `contentDescription`
     * （`VocabularyScreen.kt:64`「生词本为空，提交不认识后会自动加入」），合并页缺它 ⇒ TalkBack 档比旧页少一层，
     * 所以生词本这条必须补上（本方法的真红面）。收藏段与错词段的旧页从来没有专属描述，
     * 而 `contentDescription` 一旦存在就**取代**文案本体被朗读——给它们新造一句反而比旧页读得少，
     * 所以这两段断言「没有专属描述」，让 TalkBack 继续读逐字文案本体。
     */
    @Test
    fun empty_sections_show_their_own_honest_copy() {
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(words = emptyList()),
                    vocabularyState = VocabularyUiState.Ready(items = emptyList()),
                    wrongWordsState = WrongWordsUiState.Ready(cards = emptyList()),
                )
            }
        }
        composeRule.onNodeWithTag("my_words_section_favorites").assertExists()
        composeRule.onNodeWithTag("my_words_section_wordbook").assertExists()
        composeRule.onNodeWithTag("my_words_section_wrong").assertExists()

        composeRule.onNodeWithTag("my_words_favorites_empty")
            .assertTextEquals("还没有收藏。在词卡详情页点星标即可收藏。")
        assertNoOwnContentDescription("my_words_favorites_empty")
        composeRule.onNodeWithTag("my_words_wordbook_empty")
            .assertTextEquals("还没有生词，提交“不认识”后会自动加入。")
            .assertContentDescriptionEquals("生词本为空，提交不认识后会自动加入")
        composeRule.onNodeWithTag("my_words_wrong_empty")
            .assertTextEquals("当前没有待纠正的错词。复习中标记「不认识」的词会出现在这里，重新答对后自动消失。")
        assertNoOwnContentDescription("my_words_wrong_empty")
        // I-4（修复轮 1）：空册不给整册动作——没有词可重学时「开始重新学习」不得出现。
        composeRule.onNodeWithTag("my_words_wordbook_start_relearn").assertDoesNotExist()
    }

    /**
     * 本节点自己没有 `ContentDescription`（读的是 Text 的逐字文案）。
     * 用 `fetchSemanticsNode().config.contains(...)` 直读（本仓口径先例见 `AppBottomBarTest`）而不选
     * `assertContentDescriptionEquals()` 传空串数组：后者对「键缺失」与「空表」两种状态在本仓
     * Compose 1.10.0 上语义不确定，直读没有歧义。
     */
    private fun assertNoOwnContentDescription(tag: String) {
        val config = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
        assertFalse(
            "$tag 不该自带专属朗读描述：旧收藏页与旧错词页都没有这一层，而 contentDescription 一旦存在" +
                "就取代文案本体被朗读，给它们新造一句反而比旧页读得少",
            config.contains(SemanticsProperties.ContentDescription),
        )
    }

    /**
     * m-6（修复轮 2）：「收藏于」时间戳与旧收藏页同文——统一取**日期档**（`yyyy-MM-dd`）。
     *
     * 缺陷形状：`FavoritesScreen.kt:140` 渲染 `收藏于 <yyyy-MM-dd>`（`toLocalDate()`），
     * 合并页 `MyWordsScreen.kt:169` 渲染 `收藏于 <yyyy-MM-dd HH:mm>`；同一条收藏在两处读到两种时间。
     * 摘除前两页并存，必须同文（评审席 m-6 逐字），而合并页自己的注释也写着「说法必须还是同一句」。
     *
     * 判据为什么咬得住：夹具把时刻钉死为 `2026-10-19T00:30:00Z` + `zoneId = UTC`
     * （不靠设备时区，数字可复算），时分档在这一刻必然写成 `… 00:30` ⇒ 修复前判红；
     * 收藏段的时间戳文案整枚匹配 ⇒ 只把「收藏于」改档、把「加入于」带下水也照样红。
     */
    @Test
    fun favorite_timestamp_shows_the_same_date_tier_as_the_favorites_page() {
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(words = listOf(favorite("anchor", 0))),
                    vocabularyState = VocabularyUiState.Ready(items = listOf(vocabularyItem("abandon", 1))),
                    wrongWordsState = WrongWordsUiState.Ready(cards = emptyList()),
                    zoneId = ZoneOffset.UTC,
                )
            }
        }
        composeRule.onNodeWithText("收藏于 2026-10-19").assertExists()
        composeRule.onNodeWithText("收藏于 2026-10-19 00:30").assertDoesNotExist()
        // 反向控：生词本条目的「加入于」仍是时分档（m-6 只统一收藏时间戳，顺手改它属扩面）。
        composeRule.onNodeWithText("加入于 2026-10-19 00:30").assertExists()
    }

    /**
     * I-4（修复轮 1，报告 §1「摘除前必须补齐的能力」的 connected 凭据）：生词本段两枚能力。
     *
     * 判据：段头「开始重新学习」点击回交宿主；行上「标记为已掌握」点击后**不直接回调**——
     * 必须先出共享二次确认（取消/确认两枚出口都在），确认之后回调才收到那一行的 item。
     * 「标记即移行」没有反悔机会，是这条 finding 点名要在摘除前补齐的动作形状。
     */
    @Test
    fun wordbook_offers_relearn_and_mastered_behind_confirmation() {
        var relearnRequests = 0
        val mastered = mutableListOf<VocabularyItem>()
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(words = emptyList()),
                    vocabularyState = VocabularyUiState.Ready(
                        items = listOf(vocabularyItem("abandon", 0), vocabularyItem("bravery", 1)),
                    ),
                    wrongWordsState = WrongWordsUiState.Ready(cards = emptyList()),
                    onStartRelearn = { relearnRequests++ },
                    onMarkMastered = { mastered += it },
                )
            }
        }
        composeRule.onNodeWithTag("my_words_wordbook_start_relearn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, relearnRequests)

        composeRule.onNodeWithTag("my_words_wordbook_mastered_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("确认前不得回调（二次确认是第一道闸）", emptyList<VocabularyItem>(), mastered)
        composeRule.onNodeWithTag("confirm_destructive_dismiss").assertExists()
        composeRule.onNodeWithTag("confirm_destructive_confirm").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("abandon"), mastered.map { it.card?.lemma })
    }

    /**
     * 三份状态同时 `Unavailable` 时的降级：三段各出一枚共享 `ScreenUnavailable`，
     * 出口真的可达（可点 + 回调落到宿主），且**只回交被点那一段的段标**（m-2 修复轮 1：
     * 旧形状一次重试刷三册，另外两段被打回 Loading）。
     */
    @Test
    fun unavailable_sections_each_offer_a_working_retry() {
        val retried = mutableListOf<MyWordsSection>()
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Unavailable,
                    vocabularyState = VocabularyUiState.Unavailable,
                    wrongWordsState = WrongWordsUiState.Unavailable,
                    onRetry = { retried += it },
                )
            }
        }
        // ScreenUnavailable 的动作标签是组件级固定名，三枚并存即三段都给了出口。
        val actions = composeRule.onAllNodesWithTag("screen_unavailable_action").fetchSemanticsNodes()
        assertEquals(
            "三段各自不可用时应恰有 3 枚重试出口（少了就是某段的降级面被省略），实际 ${actions.size}",
            3,
            actions.size,
        )
        // 本仓 Compose 版本（1.10.0）的 SemanticsNodeInteractionCollection 只有 `get(index)`，没有 `onFirst()`
        // （javap 实测：成员是 fetchSemanticsNodes + get），所以下标取第一枚。
        // 树序里第一段是收藏段：点它的重试，回调必须恰收到 Favorites 一枚段标——
        // 收到别的段、或收到三枚（旧共刷形状），都在这条红。
        composeRule.onAllNodesWithTag("screen_unavailable_action")[0]
            .assertHasClickAction()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(MyWordsSection.Favorites), retried)
    }

    /**
     * `section` 参的落位语义（R-20A.2）：指定错词段时视口必须真的落到那一段。
     *
     * 判据为什么咬得住：收藏段刻意给 20 行，错词段排在它下面很远，**不滚动就一定在视口外**；
     * 反过来「首帧还没量到偏移就滚」的写法也一样红（落位实现必须等一次测量）。
     * 末尾那条 `assertIsNotDisplayed` 是反向控：落位生效后顶部第一枚收藏词必须已经离开视口。
     */
    @Test
    fun section_param_lands_the_viewport_on_that_section() {
        val many = (0 until 20).map { favorite("favorite-word-$it", it) }
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(words = many),
                    vocabularyState = VocabularyUiState.Ready(items = listOf(vocabularyItem("wordbook-word", 0))),
                    wrongWordsState = WrongWordsUiState.Ready(cards = listOf(card("wrong-word", 0))),
                    section = MyWordsSection.Wrong,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("my_words_section_wrong").assertIsDisplayed()
        composeRule.onNodeWithText("wrong-word").assertIsDisplayed()
        composeRule.onNodeWithText("favorite-word-0").assertIsNotDisplayed()
    }

    /**
     * I-1（修复轮 1）：`section` 的语义是**初始落位**，落位只许发生一次。
     *
     * 缺陷形状：`LaunchedEffect` 的键集里放了会变的 UiState——任何一册的后台重载
     * （如 `FavoritesViewModel.toggle` 成功后的 reload：Loading→Ready 两次发射）都会重启落位 effect，
     * 把视口从用户当前阅读位置**拽回段首**。生产里这就是「读到一半，页面自己跳回顶部」。
     *
     * 判据为什么咬得住：一次 `setContent` 里喂 `mutableStateOf` 的收藏状态（真实数据流形状——
     * 状态会发射，不是静态对象）；用户已下滚到错词段底部时，点「取消收藏」让收藏状态发射一次
     * （`onUnfavorite` 里换成少一条的 `Ready`，与 VM 的 reload 同形）。修复后落位 effect 只认 `section`
     * 这一枚键、不会重跑 ⇒ 视口必须留在用户所在处；修复前它必然被拉回收藏段首 ⇒ 当场红。
     */
    @Test
    fun state_emit_after_landing_does_not_yank_the_viewport_back() {
        val many = (0 until 20).map { favorite("favorite-word-$it", it) }
        var favorites: FavoritesUiState by mutableStateOf(FavoritesUiState.Ready(words = many))
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = favorites,
                    vocabularyState = VocabularyUiState.Ready(items = emptyList()),
                    wrongWordsState = WrongWordsUiState.Ready(
                        cards = listOf(card("wrong-word-0", 0), card("wrong-word-1", 1), card("wrong-word-2", 2)),
                    ),
                    onUnfavorite = { favorites = FavoritesUiState.Ready(words = many.take(many.lastIndex)) },
                )
            }
        }
        composeRule.waitForIdle()
        // 用户下滚到页面底部读错词段（此时视口已不在 `section` 落位的段首）。
        composeRule.onNodeWithTag("my_words_wrong_row_2").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("my_words_wrong_row_2").assertIsDisplayed()
        // 夹具自证：最后一条收藏行的「取消收藏」此刻仍在视口内，下面的点击是用户真点得到的。
        composeRule.onNodeWithTag("my_words_favorite_unfavorite_19").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        // 收藏状态发射（Ready(20)→Ready(19)）不得触发第二次落位：
        // 用户所在处必须原地不动，段首那枚收藏词不得被拽回视口。
        composeRule.onNodeWithTag("my_words_wrong_row_2").assertIsDisplayed()
        composeRule.onNodeWithText("favorite-word-0").assertIsNotDisplayed()
    }
}
