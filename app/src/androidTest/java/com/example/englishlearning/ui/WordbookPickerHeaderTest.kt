package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.GetLearningSettingsUseCase
import com.example.englishlearning.learning.LearningProfile
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.LearningSettings
import com.example.englishlearning.learning.LearningSettingsRepository
import com.example.englishlearning.learning.LearningSettingsRepositoryResult
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.SaveLearningSettingsUseCase
import com.example.englishlearning.learning.SeedWordBooksUseCase
import com.example.englishlearning.learning.SelectWordBookAndSetDailyTargetUseCase
import com.example.englishlearning.learning.WordBook
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 词书选取屏接入统一页头与骨架（批 2 Task 13）的 connected 像素/语义判据。
 *
 * 三条各一个 `@Test`、每个只 `setContent` 一次（本仓真机怪癖：同一测试内两次 setContent 必崩）。
 * 期望像素一律取 [AppColorLight] 的 token，不重抄 hex；本主题显式锁 `ThemeMode.Light`，
 * 与 `ThemeModeSwitchTest` 同套取样口径（`captureToImage()` 离屏、alpha ≥ 250 过滤、逐通道容差 2）。
 */
@RunWith(AndroidJUnit4::class)
class WordbookPickerHeaderTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * ①页头由 `AppScreenHeader` 承载：testTag 存在、标题与副标题文案逐字命中、
     * 返回入口仍是既有的 `← 返回上一层`（`contentDescription` 原样，不被页头的「返回」替换）。
     */
    @Test
    fun wordbook_picker_header_is_carried_by_the_shared_app_screen_header() {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                LearningSetupScreen(
                    profileId = "probe-profile",
                    viewModel = probeSetupViewModel(),
                    onSaved = {},
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithTag("app_screen_header").assertExists()
        composeRule.onNodeWithText("选好词书，开始今天的积累").assertExists()
        composeRule.onNodeWithText("每天一点点，学习会自然变成习惯。").assertExists()
        // 返回位保留现有 TextButton：中性表述「返回上一层」是既有决策，不得被页头写死的「返回」覆盖。
        composeRule.onNodeWithContentDescription("返回上一层").assertExists()
    }

    /**
     * ②首帧骨架：三行 `wordbook_placeholder_0..2` 齐全，每行实测高度 ≥ `WordBookCardMinHeight`（72dp）。
     *
     * 刻意不判「骨架与真实卡逐像素同高」：真卡带进度条约 130dp、骨架按最小行高约 74dp，
     * 是 `AppScreen.kt` 里写明并刻意保留的取舍（首帧要「立刻有东西」，不是复刻终版面）。
     */
    @Test
    fun first_frame_reserves_placeholder_rows_at_the_card_minimum_height() {
        val minHeightPx = WORD_BOOK_CARD_MIN_HEIGHT_DP * composeRule.density.density
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                WordBookList(
                    wordBooks = emptyList(),
                    loading = true,
                    selectedId = null,
                    progressByBook = emptyMap(),
                    progressLoading = true,
                    canDelete = { false },
                    onSelect = {},
                    onRequestDelete = {},
                )
            }
        }
        for (row in 0 until PLACEHOLDER_ROWS) {
            val node = composeRule.onNodeWithTag("wordbook_placeholder_$row")
            node.assertExists()
            val heightPx = node.fetchSemanticsNode().boundsInRoot.height
            assertTrue(
                "骨架第 $row 行高度不足 $WORD_BOOK_CARD_MIN_HEIGHT_DP dp（实测 ${heightPx / composeRule.density.density}dp）",
                heightPx >= minHeightPx - 1f,
            )
        }
    }

    /**
     * ③册与册之间必须是真实分隔线，而不是 `spacedBy` 空白：取相邻两卡之间那条缝的中点像素，
     * 断言它等于 `appColors().outlineSoft`（此处锁浅套档）。
     *
     * 变异证据：把 `WordBookListDivider` 里那条画线调用删掉，本条必须变红——缝会退回纯透明，
     * 取不到任何不透明像素（界外透明像素不筛就会把 0x000000 当成命中的颜色，所以按 alpha ≥ 250 过滤）。
     */
    @Test
    fun adjacent_wordbook_cards_are_separated_by_a_real_outline_soft_divider() {
        val first = WordBook("wb-first", "第一册", "L1", 100, "v1", "user-provided-xlsx")
        val second = WordBook("wb-second", "第二册", "L2", 100, "v1", "user-provided-xlsx")
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                WordBookList(
                    wordBooks = listOf(first, second),
                    loading = false,
                    selectedId = null,
                    progressByBook = emptyMap(),
                    progressLoading = false,
                    canDelete = { true },
                    onSelect = {},
                    onRequestDelete = {},
                    modifier = androidx.compose.ui.Modifier.testTag(LIST_TAG),
                )
            }
        }

        val listBounds = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot
        val upperBounds = composeRule.onNodeWithTag("wordbook_swipe_${first.id}")
            .fetchSemanticsNode().boundsInRoot
        val lowerBounds = composeRule.onNodeWithTag("wordbook_swipe_${second.id}")
            .fetchSemanticsNode().boundsInRoot

        val bitmap = composeRule.onNodeWithTag(LIST_TAG).captureToImage().asAndroidBitmap()
        val listTop = listBounds.top
        // 缝的中点（根坐标）→ 位图坐标；卡片描边/圆角离中点至少 6dp，取样窗只覆盖缝本身。
        val seamMidPointY = ((upperBounds.bottom + lowerBounds.top) / 2f - listTop).roundToInt()
        val window = (SEAM_SAMPLE_WINDOW_DP * composeRule.density.density).roundToInt()
        val x = bitmap.width / 2

        val expectedOutline = rgb(AppColorLight.outlineSoft)
        val surface = rgb(AppColorLight.surface)
        val background = rgb(AppColorLight.background)
        // 自检：outlineSoft 必须与相邻两档（卡片底 surface、页底 background）逐通道分得开，
        // 否则「缝漏画、透出底」或「取样点到卡片边」都会被本用例误判成通过。
        assertTrue(
            "outlineSoft 与 surface 太接近，取样失去判别力",
            maxChannelDelta(expectedOutline, surface) > CHANNEL_TOLERANCE * 2,
        )
        assertTrue(
            "outlineSoft 与 background 太接近，取样失去判别力",
            maxChannelDelta(expectedOutline, background) > CHANNEL_TOLERANCE * 2,
        )

        var opaque = 0
        var matched = 0
        for (y in (seamMidPointY - window)..(seamMidPointY + window)) {
            if (y < 0 || y >= bitmap.height) continue
            val argb = bitmap.getPixel(x, y)
            if (((argb ushr 24) and 0xFF) >= OPAQUE_ALPHA_FLOOR) {
                opaque++
                if (maxChannelDelta(argb and 0xFFFFFF, expectedOutline) <= CHANNEL_TOLERANCE) matched++
            }
        }
        // 判据：缝的中点必须存在 outlineSoft 的不透明像素（=真的画了分隔线）。
        // 变异证据：删掉那条 `.background(appColors().outlineSoft)` 后，中点只剩透明缝（界外透明像素不算命中），
        // matched 归 0 → 本条变红。刻意不把判据写成「中点所有不透明像素都等于 outlineSoft」——卡片自带 1dp 阴影/
        // 描边会在缝的两端混入非 outlineSoft 的不透明像素，那条写法会把「线画对了」也误判成失败。
        assertTrue(
            "册间缝的中点没有 outlineSoft 分隔线（缝里 $opaque 个不透明像素，其中命中 outlineSoft $matched 个）——" +
                "分隔线没画出来（退回 spacedBy 幽灵间距）、读错了令牌，或取样点越过了 outlineSoft 档（期望 $expectedOutline）",
            matched > 0,
        )
    }

    private fun rgb(color: Color): Int = color.toArgb() and 0xFFFFFF

    private fun maxChannelDelta(a: Int, b: Int): Int {
        fun channel(shift: Int) = abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))
        return maxOf(channel(16), channel(8), channel(0))
    }

    private fun probeSetupViewModel(): LearningSetupViewModel {
        val repository = FakeLearningProfileRepository()
        val seedWordBooks = SeedWordBooksUseCase(
            { "[{\"id\":\"cet4\",\"displayName\":\"大学英语四级\",\"level\":\"CET-4\",\"totalWords\":1,\"dataVersion\":\"v1\",\"sourceId\":\"ngsl-nawl-1.2\",\"sourcePolicy\":\"应用内学习分组，不是官方考试大纲词表。\"}]" },
            repository,
        )
        val settingsRepo = FakeLearningSettingsRepository()
        return LearningSetupViewModel(
            repository = repository,
            seedWordBooks = seedWordBooks,
            selectWordBook = SelectWordBookAndSetDailyTargetUseCase(repository),
            getSettings = GetLearningSettingsUseCase(settingsRepo),
            saveSettings = SaveLearningSettingsUseCase(settingsRepo),
            bundledIds = BundledWordBookIdSource { setOf("cet4") },
        )
    }

    private class FakeLearningProfileRepository : LearningProfileRepository {
        private var profile: LearningProfile? = null
        private val wordBooks = mutableListOf<WordBook>()

        override suspend fun current(profileId: String) = RepositoryResult.Success(profile)

        override suspend fun save(profile: LearningProfile): RepositoryResult<Unit> {
            this.profile = profile
            return RepositoryResult.Success(Unit)
        }

        override suspend fun listWordBooks() = RepositoryResult.Success(wordBooks)

        override suspend fun findWordBook(id: String) = RepositoryResult.Success(wordBooks.find { it.id == id })

        override suspend fun upsertWordBook(wordBook: WordBook): RepositoryResult<Unit> {
            wordBooks.removeAll { it.id == wordBook.id }
            wordBooks += wordBook
            return RepositoryResult.Success(Unit)
        }
    }

    private class FakeLearningSettingsRepository : LearningSettingsRepository {
        private val stored = mutableMapOf<String, LearningSettings>()

        override suspend fun find(profileId: String): LearningSettingsRepositoryResult<LearningSettings?> =
            LearningSettingsRepositoryResult.Success(stored[profileId])

        override suspend fun save(settings: LearningSettings): LearningSettingsRepositoryResult<Unit> {
            stored[settings.profileId] = settings
            return LearningSettingsRepositoryResult.Success(Unit)
        }
    }
}

private const val LIST_TAG = "wordbook_list_probe"
private const val PLACEHOLDER_ROWS = 3
private const val WORD_BOOK_CARD_MIN_HEIGHT_DP = 72f
private const val SEAM_SAMPLE_WINDOW_DP = 4f
private const val CHANNEL_TOLERANCE = 2
private const val OPAQUE_ALPHA_FLOOR = 250
