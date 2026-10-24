package com.example.englishlearning.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.FavoriteWord
import com.example.englishlearning.learning.VocabularyEntry
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.ui.theme.MintGlassTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 26 Step 4b：「我的词」进入帧时的**测量件**（评审席 `task-20A-review.md` m-1，控制者裁定 ②）。
 *
 * 被测形状就是生产形状：`MyWordsScreen` 的三段是 eager `Column + verticalScroll` 全量组合，
 * 峰值 = 收藏 + 生词本 + 错词三册一次排布。两个方法的分工：
 *  - `cold_six_hundred_rows_first_composition`：进程首次组合就进 600 行 —— **上界**（含类加载/字体/JIT 预热）；
 *  - `warm_entry_six_hundred_rows`：先组合空态热身，再把三册同时换成 200 行 —— **操作数**，
 *    近似真机「App 已在前台、点进我的词」那一帧（生产侧三份状态早已在宿主里，屏只是换内容）。
 *
 * 读数打到 logcat `QA-T26-TIMING`（原始行用 `adb logcat -s QA-T26-TIMING` 取）。口径边界（写进报告，不许外推）：
 *  - 量的是组合 + 测量/布局 + Compose 空闲，**不含**进程冷启、Repository 取数与 GPU 提交；
 *  - 设备是 360dp 宽的小屏模拟器（`wm density 480`，1080×1920），不是真机；
 *  - 不做阈值断言：阈值由控制者按读数裁定是否立 `LazyColumn` 改造任务。
 *
 * 两个方法各自一次 `setContent`（同方法内二次 `setContent` 必崩，见 `MyWordsScreenTest` 头注），
 * 因此请**分别单跑**本文件的两个方法：同一次 `am instrument` 连跑会把后一个的「冷」字弄脏。
 */
@RunWith(AndroidJUnit4::class)
class MyWordsFirstFrameTimingTest {
    @get:Rule val composeRule = createComposeRule()

    private val baseInstant: Instant = Instant.parse("2026-10-19T00:30:00Z")

    private fun favorite(index: Int) = FavoriteWord(
        profileId = "p1",
        cardId = "fav-card-$index",
        wordBookId = "cet4",
        lemma = "favorite$index",
        addedAt = baseInstant,
    )

    private fun card(index: Int) = WordCard(
        cardId = "card-$index",
        wordBookId = "cet4",
        lemma = "lemma$index",
        ipa = "/ˈlemə/",
        partOfSpeech = "n.",
        meaningZh = "释义$index",
    )

    private fun vocabularyItem(index: Int) = VocabularyItem(
        entry = VocabularyEntry(
            profileId = "p1",
            wordBookId = "cet4",
            cardId = "card-$index",
            addedAt = baseInstant,
            lastFeedback = "Again",
        ),
        card = card(index),
    )

    private fun favoritesState(rows: Int) = FavoritesUiState.Ready((0 until rows).map(::favorite))

    private fun vocabularyState(rows: Int) = VocabularyUiState.Ready((0 until rows).map(::vocabularyItem))

    private fun wrongState(rows: Int) = WrongWordsUiState.Ready((0 until rows).map(::card))

    private fun log(label: String, start: Long) {
        val idleNanos = System.nanoTime()
        Log.i("QA-T26-TIMING", "$label idle=${"%.1f".format((idleNanos - start) / 1_000_000.0)}ms")
    }

    @Test
    fun cold_six_hundred_rows_first_composition() {
        var firstLayoutNanos by mutableStateOf(0L)
        val start = System.nanoTime()
        composeRule.setContent {
            MintGlassTheme {
                Box(
                    modifier = Modifier.onGloballyPositioned {
                        if (firstLayoutNanos == 0L) firstLayoutNanos = System.nanoTime()
                    },
                ) {
                    MyWordsScreen(
                        favoritesState = favoritesState(200),
                        vocabularyState = vocabularyState(200),
                        wrongWordsState = wrongState(200),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val firstLayoutMs = (firstLayoutNanos - start) / 1_000_000.0
        Log.i("QA-T26-TIMING", "cold600 firstLayout=${"%.1f".format(firstLayoutMs)}ms")
        log("cold600", start)
        assertTrue("没测到首次布局（onGloballyPositioned 未触发）", firstLayoutNanos > 0L)
        composeRule.onNodeWithTag("my_words_wrong_row_199").assertExists()
    }

    @Test
    fun warm_entry_six_hundred_rows() {
        var rows by mutableStateOf(0)
        composeRule.setContent {
            MintGlassTheme {
                MyWordsScreen(
                    favoritesState = favoritesState(rows),
                    vocabularyState = vocabularyState(rows),
                    wrongWordsState = wrongState(rows),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("my_words_wrong_empty").assertExists()
        // 热身完毕：现在换入三册各 200 行，量这一帧（组合 → 布局 → 空闲）。
        val start = System.nanoTime()
        rows = 200
        composeRule.waitForIdle()
        log("warm600", start)
        composeRule.onNodeWithTag("my_words_wrong_row_199").assertExists()
    }
}
