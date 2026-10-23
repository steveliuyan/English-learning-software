package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.learning.FavoriteWord
import com.example.englishlearning.learning.VocabularyEntry
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.ui.theme.MintGlassTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
     * 钉回三页旧文（旧收藏页 :101 / `VocabularyScreen.kt:63` / 旧错词页 :90；收藏页与错词页两枚屏
     * 已于 Task 22-B 下线，故此处只留中文页名与旧文行号，避免指向已不存在的文件）。
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
     * R-22B.3（22-B 修复轮 1 的 F1，唯一 major）：错词段非空时必须在册旧错词页那行**排序说明**。
     *
     * 缺陷形状：Task 22-B 摘屏时只把旧错词页的**空态**文案搬进合并页，`WrongWordsScreen.kt:97`
     * 在 `Ready` 且列表非空时渲染的那行「最近一次标记为「不认识」的词，按到期先后排列。」随屏一起丢了，
     * 全仓 `git grep "按到期先后排列"` 零命中 ⇒ 合并页的错词段比旧页少一层解释，用户读不出这一列按什么排。
     *
     * 三条腿各挡一种破坏：
     *  ① 逐字 `assertTextEquals` —— 把这句「润色」成别的说法当场红（搬回来的原话不许改）；
     *  ② `my_words_wrong_row_0` 同屏在册 —— 挡「用说明行替换掉列表」这种假修（补一层 ≠ 挤掉词行）；
     *  ③ 说明行的矩形必须落在 `my_words_section_wrong` 容器内 —— 挡「挂到别的段/段外」。
     * 空态侧不得出现这行由既有 `empty_sections_show_their_own_honest_copy` 的整段形状间接承重，
     * 本条不复述（一次 `setContent` 是本仓硬约束，两种形态不能塞进同一枚方法）。
     */
    @Test
    fun wrongWordsSectionKeepsTheOrderingHintFromTheLegacyPage() {
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(words = emptyList()),
                    vocabularyState = VocabularyUiState.Ready(items = emptyList()),
                    wrongWordsState = WrongWordsUiState.Ready(cards = listOf(card("gossamer", 0))),
                )
            }
        }
        val hint = composeRule.onNodeWithTag("my_words_wrong_order_hint")
        hint.assertExists()
        hint.assertTextEquals("最近一次标记为「不认识」的词，按到期先后排列。")
        composeRule.onNodeWithTag("my_words_wrong_row_0").assertExists()
        hint.performScrollTo()
        val containerBox = composeRule.onNodeWithTag("my_words_section_wrong").fetchSemanticsNode().boundsInRoot
        val hintBox = hint.fetchSemanticsNode().boundsInRoot
        assertEquals(
            "排序说明行没有落在错词段容器内（挂到别的段就等于该段的解释仍然缺失）：" +
                "容器 $containerBox，说明行 $hintBox",
            true,
            hintBox.top >= containerBox.top - 1f && hintBox.bottom <= containerBox.bottom + 1f &&
                hintBox.left >= containerBox.left - 1f && hintBox.right <= containerBox.right + 1f,
        )
    }

    /**
     * m-6（修复轮 2）：「收藏于」时间戳与旧收藏页同文——统一取**日期档**（`yyyy-MM-dd`）。
     *
     * 缺陷形状：旧收藏页 `:140`（该屏已于 Task 22-B 下线）渲染 `收藏于 <yyyy-MM-dd>`（`toLocalDate()`），
     * 合并页 `MyWordsScreen.kt:169` 渲染 `收藏于 <yyyy-MM-dd HH:mm>`；同一条收藏在两处读到两种时间。
     * 摘除前两页并存，必须同文（评审席 m-6 逐字），而合并页自己的注释也写着「说法必须还是同一句」；
     * 摘除后这条判据仍是合并页单侧的口径闸——「收藏于」只许读成日期档。
     *
     * 判据为什么咬得住：夹具把时刻钉死为 `2026-10-19T00:30:00Z` + `zoneId = UTC`
     * （不靠设备时区，数字可复算），时分档在这一刻必然写成 `… 00:30` ⇒ 修复前判红；
     * 收藏段的时间戳文案整枚匹配 ⇒ 只把「收藏于」改档、把「加入于」带下水也照样红。
     *
     * F4（22-B 修复轮 1 / A-6）：方法名从 `…_as_the_favorites_page` 更名为 `favoriteTimestampUsesTheSharedDateTier`
     * ——对照物（旧收藏页）已随 Task 22-B 下线，旧名会指挥后来人拿现存屏去和一个不存在的页对表；
     * **断言本体一字未动**，本条从此只钉合并页自己这一侧的日期档口径。
     */
    @Test
    fun favoriteTimestampUsesTheSharedDateTier() {
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
     *
     * N-4（修复轮 3）：**先自证发射真的发生，再断言视口没动**。原先的写法对「发射没发生」是瞎的——
     * 若 `onUnfavorite` 断线（或夹具喂错形参），两条视口断言会双双静默绿，整枚用例退化成空转。
     * 现在点击后先钉三件数据面的事：回调恰收到那一枚收藏（`unfavorited` 捕获列表，沿用本文件既有写法）、
     * 少一条后的**那一行**已从组合里消失（`my_words_favorite_row_19`）、段头条数从 `20 个` 变成 `19 个`；
     * 三条都过了，才轮到「视口未被拉回」那两条。断言顺序即因果顺序：状态变了 → 视口没动。
     */
    @Test
    fun state_emit_after_landing_does_not_yank_the_viewport_back() {
        val many = (0 until 20).map { favorite("favorite-word-$it", it) }
        val unfavorited = mutableListOf<String>()
        var favorites: FavoritesUiState by mutableStateOf(FavoritesUiState.Ready(words = many))
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = favorites,
                    vocabularyState = VocabularyUiState.Ready(items = emptyList()),
                    wrongWordsState = WrongWordsUiState.Ready(
                        cards = listOf(card("wrong-word-0", 0), card("wrong-word-1", 1), card("wrong-word-2", 2)),
                    ),
                    onUnfavorite = {
                        unfavorited += it.lemma
                        favorites = FavoritesUiState.Ready(words = many.take(many.lastIndex))
                    },
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
        // ---- 先证「发射真的发生」（N-4）----
        // ① 回调收到的是被点那一枚，且恰一次（断线 / 接错行都在这一条红）。
        assertEquals(listOf("favorite-word-19"), unfavorited)
        // ② 数据面真的变了：Ready(20)→Ready(19) 后第 20 行必须已从组合里消失。
        composeRule.onNodeWithTag("my_words_favorite_row_19").assertDoesNotExist()
        // ③ 段头条数同批跟着变（旧值 20 不得还在册）。
        composeRule.onNodeWithText("19 个").assertExists()
        composeRule.onNodeWithText("20 个").assertDoesNotExist()
        // ---- 再证「视口没被拉回」----
        // 收藏状态发射（Ready(20)→Ready(19)）不得触发第二次落位：
        // 用户所在处必须原地不动，段首那枚收藏词不得被拽回视口。
        composeRule.onNodeWithTag("my_words_wrong_row_2").assertIsDisplayed()
        composeRule.onNodeWithText("favorite-word-0").assertIsNotDisplayed()
    }

    /**
     * N-1（修复轮 3）：`markMastered` **失败**后合并页必须自己把结果说出来。
     *
     * 缺陷形状：`VocabularyViewModel.markMastered` 遇 `StorageUnavailable` 只置 `_mastery = Failure` 并
     * `return@launch`（**不 reload**），而合并页原先不读这枚状态 ⇒ 用户点了确认、行还在、零反馈，
     * 只能猜「是没点着还是没保存」。老生词页有那枚结果框，摘除前两页必须同一句说法。
     *
     * 判据四条：① 结果框与老页 `VocabularyScreen.kt:90-96` 的标题/正文**逐字**在册；② 点「知道了」
     * 只回调复位、**不得**再回调 `onMarkMastered`（复位不是重做动作）；③ 复位后（`mastery` 由夹具翻回
     * `Idle`）框必须消失——证明复位出口真的接在渲染上，不是只叫到回调；
     * ④ 反向控：成功态那两句不得出现在失败框里（两态共用一套说法就是「保存失败」被读成「已掌握」）。
     *
     * 夹具用 `mutableStateOf` 的 `mastery`（真实 `StateFlow` 形状），所以一个方法仍只一次 `setContent`。
     */
    @Test
    fun mastery_failure_shows_the_same_result_dialog_as_the_old_page_and_resets_on_ack() {
        var mastery: VocabularyMasteryState by mutableStateOf(VocabularyMasteryState.Failure)
        var dismissRequests = 0
        val mastered = mutableListOf<VocabularyItem>()
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(words = emptyList()),
                    vocabularyState = VocabularyUiState.Ready(items = listOf(vocabularyItem("abandon", 0))),
                    wrongWordsState = WrongWordsUiState.Ready(cards = emptyList()),
                    masteryState = mastery,
                    onDismissMasteryMessage = {
                        dismissRequests++
                        mastery = VocabularyMasteryState.Idle
                    },
                    onMarkMastered = { mastered += it },
                )
            }
        }
        composeRule.onNodeWithTag("my_words_mastery_result_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("保存失败").assertIsDisplayed()
        composeRule.onNodeWithText("复习状态未更新，生词仍保留在生词本中。请重试。").assertIsDisplayed()
        composeRule.onNodeWithText("已标记为掌握").assertDoesNotExist()
        composeRule.onNodeWithText("该词已从生词本移除，复习记录已保留。").assertDoesNotExist()

        composeRule.onNodeWithTag("my_words_mastery_result_dismiss").performClick()
        composeRule.waitForIdle()
        assertEquals(1, dismissRequests)
        assertEquals(
            "「知道了」是复位出口，不是重做动作（回调 onMarkMastered 会把同一条词二次提交成 Known）",
            emptyList<VocabularyItem>(),
            mastered,
        )
        composeRule.onNodeWithTag("my_words_mastery_result_dialog").assertDoesNotExist()
    }

    /**
     * N-1（修复轮 3）：`markMastered` **成功**后的结果框与老生词页逐字同文。
     *
     * 与失败那枚拆成两枚方法，是本仓「一个测试方法只允许一次 `setContent`」的硬约束（见本类 KDoc），
     * 不是可以把两种形态塞进一次组合的情况。
     */
    @Test
    fun mastery_success_shows_the_same_result_dialog_as_the_old_page() {
        var dismissRequests = 0
        val mastered = mutableListOf<VocabularyItem>()
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(words = emptyList()),
                    vocabularyState = VocabularyUiState.Ready(items = listOf(vocabularyItem("abandon", 0))),
                    wrongWordsState = WrongWordsUiState.Ready(cards = emptyList()),
                    masteryState = VocabularyMasteryState.Success,
                    onDismissMasteryMessage = { dismissRequests++ },
                    onMarkMastered = { mastered += it },
                )
            }
        }
        composeRule.onNodeWithTag("my_words_mastery_result_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("已标记为掌握").assertIsDisplayed()
        composeRule.onNodeWithText("该词已从生词本移除，复习记录已保留。").assertIsDisplayed()
        composeRule.onNodeWithText("保存失败").assertDoesNotExist()
        composeRule.onNodeWithText("复习状态未更新，生词仍保留在生词本中。请重试。").assertDoesNotExist()

        composeRule.onNodeWithTag("my_words_mastery_result_dismiss").performClick()
        composeRule.waitForIdle()
        assertEquals(1, dismissRequests)
        assertEquals(
            "「知道了」是复位出口，不是重做动作",
            emptyList<VocabularyItem>(),
            mastered,
        )
    }

    /**
     * N-1（修复轮 3）：`Submitting` **不渲染**任何结果框。
     *
     * 为什么单独钉这一枚：提交中弹「已标记为掌握」是比缺反馈更坏的错态（用户会以为已经保存完），
     * 而老页 `VocabularyScreen.kt:98-100` 那两条分支就是靠 `-> Unit` 什么都不画。
     * 这里同时反向钉住：段内动作（标记已掌握）仍在，说明「没有框」不是因为整段没渲染。
     * `Idle` 那一半另拆一枚方法（本仓硬约束：一个测试方法只许一次 `setContent`），
     * 两枚合起来才是老页那两条 `-> Unit` 分支的等价覆盖。
     */
    @Test
    fun submitting_renders_no_result_dialog() {
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(words = emptyList()),
                    vocabularyState = VocabularyUiState.Ready(items = listOf(vocabularyItem("abandon", 0))),
                    wrongWordsState = WrongWordsUiState.Ready(cards = emptyList()),
                    masteryState = VocabularyMasteryState.Submitting,
                )
            }
        }
        composeRule.onNodeWithTag("my_words_mastery_result_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("已标记为掌握").assertDoesNotExist()
        composeRule.onNodeWithText("保存失败").assertDoesNotExist()
        composeRule.onNodeWithTag("my_words_wordbook_mastered_0").assertExists()
    }

    /** N-1（修复轮 3）：`Idle`（初始与复位后的取值）同样不渲染结果框——与 `Submitting` 那枚成对。 */
    @Test
    fun idle_renders_no_result_dialog() {
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = FavoritesUiState.Ready(words = emptyList()),
                    vocabularyState = VocabularyUiState.Ready(items = listOf(vocabularyItem("abandon", 0))),
                    wrongWordsState = WrongWordsUiState.Ready(cards = emptyList()),
                    masteryState = VocabularyMasteryState.Idle,
                )
            }
        }
        composeRule.onNodeWithTag("my_words_mastery_result_dialog").assertDoesNotExist()
        composeRule.onNodeWithText("已标记为掌握").assertDoesNotExist()
        composeRule.onNodeWithText("保存失败").assertDoesNotExist()
        composeRule.onNodeWithTag("my_words_wordbook_mastered_0").assertExists()
    }

    // ---------------------------------------------------------------------------------------------
    // Task 20-D（计划 Step 4c / R-20D.1③）：合并页三枚 `meta` 站点的**布局级不截断判据**。
    //
    // 站点（重算：`grep -rn "meta = " app/src/main/java/com/example/englishlearning/ui/MyWordsScreen.kt`；
    //  原并列的旧收藏页站点已随该屏于 Task 22-B 下线）：
    //  `MyWordsScreen.kt:167` 收藏段 `收藏于 yyyy-MM-dd`、`:224` 生词本死行 `加入于 <日期时间>`、
    //  `:234` 生词本活行 `加入于 <日期时间>`。R-20D.1② 登记的缺口正是「合并页渲染了但只到文本层」：
    //  `favoriteTimestampUsesTheSharedDateTier`（原名点名已下线的旧收藏页，22-B 修复轮 1 的 F4 已更名）一直绿着，而右列当时是
    //  `maxLines = 1` —— 语义文本给全文，`Ellipsis` 截断时 `onNodeWithText` 读不出差别。
    //  ⇒ 这里改读 `textLayout()`（形状同 `b76f133` 的 `disabledRowShowsTheUnavailableNoticeInFull`）。
    //
    // 三站点共用一次 `setContent`（本仓硬约束：一个方法只许一次组合；三段同宿主正是 R-20A.1 的实证形状），
    // 所以夹具把两枚生词本条目的时刻错开（00:30 / 08:15），三枚 `meta` 文本互不相同、逐枚可定位。
    // ---------------------------------------------------------------------------------------------

    /** 20-D 夹具：可指定加入时刻与「词卡已随词书删除」（`card == null` ⇒ 走 `:224` 那枚死行分支）。 */
    private fun vocabularyItemAt(
        lemma: String,
        index: Int,
        addedAt: Instant,
        card: WordCard?,
    ) = VocabularyItem(
        entry = VocabularyEntry(
            profileId = "p1",
            wordBookId = "cet4",
            cardId = "card-$index",
            addedAt = addedAt,
            lastFeedback = "Again",
        ),
        card = card,
    )

    /** 20-D 夹具：三段同宿主的合并页（字号由调用处的 `LocalDensity` 钉死）。 */
    private fun showMergedPageWithLongAndShortMeta() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1f)) {
                MintGlassTheme {
                    MyWordsScreen(
                        favoritesState = FavoritesUiState.Ready(words = listOf(favorite("anchor", 0))),
                        vocabularyState = VocabularyUiState.Ready(
                            items = listOf(
                                vocabularyItemAt(
                                    lemma = "abandon",
                                    index = 0,
                                    addedAt = baseInstant,
                                    card = null,
                                ),
                                vocabularyItemAt(
                                    lemma = "bravery",
                                    index = 1,
                                    addedAt = Instant.parse("2026-10-19T08:15:00Z"),
                                    card = card("bravery", 1),
                                ),
                            ),
                        ),
                        wrongWordsState = WrongWordsUiState.Ready(cards = emptyList()),
                        zoneId = ZoneOffset.UTC,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * 站点 `:167` / `:224` / `:234` 的布局级判据（裁定 R-20D.2：`meta` 两行、右列**不加宽**、`status` 维持单行）。
     *
     * 每枚 `meta` 四条腿，各钉一种破坏：
     *  ① `maxLines ≥ 2`（R-20D.2 的行数口径；现盘 `WordRow.kt:189` 的 `maxLines = 1` 在这一条天然红）；
     *  ② 宽度落在右列区间 `48dp–120.1dp`（上限钉「不靠加宽糊过去」，下限钉右列没被挤塌）；
     *  ③ 纵向腿 `getLineEnd(末行) == 文本长度`（整句真被画出来）；
     *  ④ 横向腿逐行 `getLineRight ≤ 盒宽 + 1dp`（`Ellipsis` 与 `Clip` 都不改语义文本，只有这里分得出）。
     * `:224`/`:234` 的 `加入于 <日期时间>` 另钉 `lineCount ≥ 2`：换行策略真的被用上（120dp 单行装不下这句，
     * 修复前它占 1 行且被省略号收尾）。字号在组合级钉 `1f`，判据不随设备「字体大小」滑档漂移。
     */
    @Test
    fun metaSlotsOfTheThreeMergedSitesAreLaidOutInTwoAllowedLinesInsideTheColumnCap() {
        showMergedPageWithLongAndShortMeta()
        assertMetaSlotSurvives("收藏于 2026-10-19", expectMinLines = 1)
        assertMetaSlotSurvives("加入于 2026-10-19 00:30", expectMinLines = 2)
        assertMetaSlotSurvives("加入于 2026-10-19 08:15", expectMinLines = 2)
    }

    /**
     * `fontScale = 2.0` 档的同一批站点（组合级钉 `2f`，先例 `WordRowTest`/`EntryRowTest`/`CheckInScreenTest`）。
     *
     * 大字号档**只判策略腿**，不判「整句可见」：右列上限是 `120.dp`（不随 sp 放大），而 `meta` 的行数预算
     * 按 R-20D.2 只有两行，`加入于 2026-10-19 00:30` 在 26sp 下需要三行以上——`WordRow.kt` KDoc 早已写明
     * 「fontScale 2.0 下靠省略号退化，不靠裁切」，本条不把它改成假绿。这里钉的是：
     *  ① 两行额度仍在（`maxLines ≥ 2`）；② 宽度仍被 120dp 收住（大字档正是当年 F3 挤瘪 lemma 的那一档）；
     *  ③ 第二行**确实被占上**（`lineCount ≥ 2`，修复前 `maxLines = 1` 时必为 1 行）；
     *  ④ 逐行不伸出文本盒 +1dp（裁切信号在任何字号下都不许出现）；
     *  ⑤ 哨兵：单行行高 ≥58px，证明 `2f` 真的落进了组合（同档实测 scale 1.0 = 40px、2.0 = 77px，
     *     出处 `AppScreenOverlayContainerTest.assertScaleTookEffect`）——护栏不许在 scale 1.0 下空转。
     */
    @Test
    fun metaSlotsKeepTheTwoLineBudgetAndTheColumnCapAtDoubleFontScale() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                MintGlassTheme {
                    MyWordsScreen(
                        favoritesState = FavoritesUiState.Ready(words = listOf(favorite("anchor", 0))),
                        vocabularyState = VocabularyUiState.Ready(
                            items = listOf(
                                vocabularyItemAt(
                                    lemma = "abandon",
                                    index = 0,
                                    addedAt = baseInstant,
                                    card = null,
                                ),
                            ),
                        ),
                        wrongWordsState = WrongWordsUiState.Ready(cards = emptyList()),
                        zoneId = ZoneOffset.UTC,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        listOf("加入于 2026-10-19 00:30").forEach { meta ->
            val layout = metaLayout(meta)
            val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
            val widthDp = layout.size.width / density
            val maxLines = layout.layoutInput.maxLines
            val lineHeightPx = layout.size.height / layout.lineCount
            assertTrue(
                "fontScale=2.0 下「$meta」所在槽仍要允许两行；实测 maxLines=$maxLines",
                maxLines >= 2,
            )
            assertTrue(
                "fontScale=2.0 下「$meta」仍不得越过右列 120dp 上限（当年 F3 无上限时把 weight(1f) 的 " +
                    "lemma 列挤成 0dp）；实测宽度 ${widthDp}dp、lineCount=${layout.lineCount}",
                widthDp <= 120.1,
            )
            assertTrue(
                "fontScale=2.0 下「$meta」必须真的用上第二行（`lineCount ≥ 2` 才是换行策略在生效）；" +
                    "实测 lineCount=${layout.lineCount} maxLines=$maxLines widthDp=${widthDp}",
                layout.lineCount >= 2,
            )
            assertTrue(
                "「$meta」字号哨兵：单行行高要 ≥58px 才算 fontScale 2.0 落进组合（实测 scale 1.0 = 40px、" +
                    "2.0 = 77px），护栏不许在 scale 1.0 下空转；实测行高 ${lineHeightPx}px、" +
                    "size=${layout.size} lineCount=${layout.lineCount}",
                lineHeightPx >= 58f,
            )
            val bounds = composeRule.onNodeWithText(meta, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val tolerancePx = with(Density(density, fontScale = 1f)) { 1.dp.toPx() }
            for (line in 0 until layout.lineCount) {
                assertTrue(
                    "fontScale=2.0 下「$meta」第 $line 行不得伸出文本盒 +1dp（裁切在任何字号下都不合法）：" +
                        "lineRight=${layout.getLineRight(line)} boxWidth=${bounds.width} tolerancePx=$tolerancePx",
                    layout.getLineRight(line) <= bounds.width + tolerancePx,
                )
            }
        }
    }

    /**
     * 读 `meta` 那枚文本节点的 `TextLayoutResult`。
     *
     * 走**未合并树**：合并页的收藏行与活行都带 `onClick`（外层 `clickable` 合并语义），
     * 合并后一次 `GetTextLayoutResult` 回调会把行内多枚文本并成一包、`layouts.single()` 当场炸
     * （先例 `EntryRowTest.entry_row_truncates_title_and_subtitle_with_ellipsis_not_clipping`）。
     */
    private fun metaLayout(meta: String): TextLayoutResult {
        val node = composeRule.onNodeWithText(meta, useUnmergedTree = true)
        node.performScrollTo()
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    /**
     * 四站点共用的布局级不截断判据（R-20D.2 口径，与旧收藏页那枚同形状冒烟类逐腿同构；
     * 该类随旧收藏页于 Task 22-B 一并下线，四站点的布局级承重此后只在合并页这一侧在册）。
     *
     * **RED 覆盖账**：现盘天然红只证到第一腿（`maxLines >= 2`，`t20D-RED-*.log` 的栈即停在这里——
     * `assertTrue` 快速失败，后四条腿没能在 `maxLines = 1` 的现盘上各自见红）。它们并非恒真：
     * 上限腿挡「加宽糊过去」、下限腿挡右列挤塌成 0dp、`lineCount` 腿要求第二行真被占上、
     * `getLineEnd` 腿要求末行抵达文本末尾（有字被省略号吃掉即红）、逐行 `getLineRight` 腿分得出
     * `Ellipsis`/`Clip` 都不改语义文本的那种横向真裁切；这五条的背书来自 GREEN 轮的正向读数
     * （`t20D-GREEN-FavoritesScreenTest.log` / `t20D-GREEN-MyWordsScreenTest.log`）。
     */
    private fun assertMetaSlotSurvives(meta: String, expectMinLines: Int) {
        val node = composeRule.onNodeWithText(meta, useUnmergedTree = true)
        val layout = metaLayout(meta)
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val widthDp = layout.size.width / density
        val maxLines = layout.layoutInput.maxLines
        val boxWidth = node.fetchSemanticsNode().boundsInRoot.width
        val tolerancePx = with(Density(density, fontScale = 1f)) { 1.dp.toPx() }
        assertTrue(
            "「$meta」所在槽必须允许两行（R-20D.2：meta 是自由文本承载位，截断即用户可见的信息丢失）；" +
                "实测 maxLines=$maxLines 说明它还在单行右列槽里",
            maxLines >= 2,
        )
        assertTrue(
            "「$meta」必须待在右列 `widthIn(max = 120.dp)` 上限内（R-20D.2 明确不加宽：加宽挤压左列 " +
                "lemma/gloss 阅读主体），也不得塌成 0dp：实测宽度 ${widthDp}dp（判定区间 48dp–120.1dp）、" +
                "lineCount=${layout.lineCount} size=${layout.size}",
            widthDp in 48.0..120.1,
        )
        assertTrue(
            "「$meta」至少要占 ${expectMinLines} 行（两行换行策略真的被用上，不是靠加宽糊过去）：" +
                "实测 lineCount=${layout.lineCount} maxLines=$maxLines widthDp=${widthDp}",
            layout.lineCount >= expectMinLines,
        )
        assertEquals(
            "「$meta」必须整句被布局（末行没到文本末尾 = 有字被省略号吃掉）：" +
                "lineCount=${layout.lineCount} maxLines=$maxLines widthDp=${widthDp} size=${layout.size} " +
                "lineEnd=${layout.getLineEnd(layout.lineCount - 1)}",
            meta.length,
            layout.getLineEnd(layout.lineCount - 1),
        )
        for (line in 0 until layout.lineCount) {
            assertTrue(
                "「$meta」第 $line 行不得伸出文本盒 +1dp（逐行真裁切信号）：" +
                    "lineRight=${layout.getLineRight(line)} boxWidth=$boxWidth tolerancePx=$tolerancePx",
                layout.getLineRight(line) <= boxWidth + tolerancePx,
            )
        }
    }
}
