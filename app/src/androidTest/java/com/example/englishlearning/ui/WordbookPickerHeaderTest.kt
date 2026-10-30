package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.learning.BundledWordBookIdSource
import com.example.englishlearning.learning.GetLearningSettingsUseCase
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
 * 词书选取屏接入统一页头与骨架（批 2 Task 13）的 connected 像素/几何判据。
 *
 * 三条各一个 `@Test`、每个只 `setContent` 一次（本仓真机怪癖：同一测试内两次 setContent 必崩）。
 * 期望像素一律取 [AppColorLight] 的 token，不重抄 hex（R-13.1 / m-1：`outlineSoft` 的权威值写在
 * `AppColors.kt`，brief 与旧注释里抄成 `#E5EFE9` / `0xFFE4EFE9` 都是抄录漂移——取 token 才不会被带偏；
 * 本轮逐值对原型后的实际值是 `0xFFEEF2ED`）；本主题显式锁 `ThemeMode.Light`，
 * 与 `ThemeModeSwitchTest` 同套取样口径（`captureToImage()` 离屏、alpha ≥ 250 过滤）。
 *
 * 批 1 Task 13-A 改判留因：②③ 原来钉的是两列色块的几何（同行两格并排、格缝宽度、缝里透页底），
 * 版面按用户裁决回到原型 `.pick.pick-radio` 的通栏行列表，两条判据随之改成「逐行通栏 + 首尾相接 +
 * 线只在非首行顶边」。强度不降：③ 是一正一反两条腿（恒画线 / 恒不画线各红一次），
 * 而不是只断言「有一枚分隔线」。
 */
@RunWith(AndroidJUnit4::class)
class WordbookPickerHeaderTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * ①页头由 `AppScreenHeader` 承载：testTag 存在、标题与副标题文案逐字命中、
     * 返回入口落在页头的返回位上。
     *
     * 批 2 Task 6 改期望值（不是放宽）：旧版本屏另有一枚 `← 返回上一层` 的 `TextButton` 排在页头**之前**，
     * 页头自己又传 `onBack = null`——「标题下面还有一枚返回」是两个并排的返回入口。现在删掉那枚
     * 文字按钮、把 `onCancel` 接到页头 `onBack`，朗读文案随之变成页头固定的「返回」。判据因此拆成
     * 一正一反：正 = 页头返回位在；反 = 旧文案「返回上一层」必须不存在（返回位只剩一处）。
     * 比原来只断言「有一枚返回」更严：谁再把第二枚返回加回来，本条就红。
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
        // 批 1 Task 12：页名换成原型 `#/plan-edit` 的「学习计划」；旧 subtitle 那句
        // 「每天一点点，学习会自然变成习惯。」不携带口径，随合并屏退役，页头下面第一行改成
        // 原型 `.meta` 那句「计划只影响每天排几个词，不会清掉你已经学过的进度。」——
        // 页头承载的仍是「我在哪 + 怎么提交」两行信息，判据强度不降。
        composeRule.onNodeWithText("学习计划").assertExists()
        composeRule.onNodeWithText("计划只影响每天排几个词，不会清掉你已经学过的进度。").assertExists()
        composeRule.onNodeWithContentDescription("返回").assertExists().assertHasClickAction()
        composeRule.onNodeWithContentDescription("返回上一层").assertDoesNotExist()
    }

    /**
     * ②首帧骨架：`wordbook_row_skeleton_0..<WORD_BOOK_PLACEHOLDER_ROWS>` 逐行齐全，每行实测高度
     * ≥ 生产的 `WordBookRowMinHeight`、每行**通栏**（左缘与列表左缘对齐、宽度 = 列表宽度），
     * 首行贴着列表顶边，相邻两行之间**只许有那一条 1dp 分隔线**（原型
     * `.rows { flex-direction: column }` 没有 gap——行与行之间的分隔是一条 1dp 描边，不是一条缝；
     * 取样口径与 ③ 同源，见 ③ 的说明）。
     *
     * 行数与下限高度都读**生产真值**（R-13.1 / m-4）：测试里另抄一份 `3` / `48f` 的话，生产把行数改成
     * 4、或把最小高度改掉时本例照样绿，判据与被测对象只剩巧合对齐。
     *
     * 批 1 Task 13-A 改判留因：两列 6 格（`WORD_BOOK_PLACEHOLDER_ROWS × WORD_BOOK_TILE_COLUMNS`）随色块
     * 一起退役，骨架回到 3 行单列；判据从「同行两格并排」换成「逐行通栏 + 逐行等高 + 线序与真实行一致」，
     * 强度不降——谁把骨架改回两列，宽度立刻减半，谁把骨架的线画到首行上，顶边贴不住列表。
     */
    @Test
    fun first_frame_reserves_full_width_placeholder_rows_at_the_row_minimum_height() {
        val minHeightPx = WordBookRowMinHeight.value * composeRule.density.density
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                WordBookPicker(
                    wordBooks = emptyList(),
                    loading = true,
                    selectedId = null,
                    activeBookId = null,
                    progressByBook = emptyMap(),
                    progressLoading = true,
                    canDelete = { false },
                    onSelect = {},
                    onRequestDelete = {},
                    modifier = Modifier.testTag(LIST_TAG),
                )
            }
        }
        val listBounds = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot
        val rowCount = WORD_BOOK_PLACEHOLDER_ROWS
        assertTrue("首帧骨架行数必须 > 0，否则下面的循环会空转", rowCount > 0)
        val bounds = (0 until rowCount).map { index ->
            val node = composeRule.onNodeWithTag("wordbook_row_skeleton_$index")
            node.assertExists()
            val b = node.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "骨架第 $index 行高度不足 ${WordBookRowMinHeight.value} dp（实测 ${b.height / composeRule.density.density}dp）",
                b.height >= minHeightPx - 1f,
            )
            assertTrue(
                "骨架第 $index 行必须从列表左缘起（实测 ${b.left - listBounds.left}）——通栏行的左缘不许缩进",
                abs(b.left - listBounds.left) <= 1f,
            )
            assertTrue(
                "骨架第 $index 行必须占满列表宽度（实测 ${b.width} / 列表 ${listBounds.width}）——缩回两列就红",
                abs(b.width - listBounds.width) <= 1f,
            )
            b
        }
        val oneDpPx = composeRule.density.density
        assertTrue(
            "骨架首行必须贴着列表顶边（`.pick:first-child { border-top: 0 }`；" +
                "实测 ${ (bounds[0].top - listBounds.top) / oneDpPx }dp——多出来的就是那条不该有的线）",
            abs(bounds[0].top - listBounds.top) <= 1f,
        )
        for (row in 1 until rowCount) {
            val above = bounds[row - 1]
            val current = bounds[row]
            assertTrue(
                "骨架第 $row 行必须排在第 ${row - 1} 行**下面**（单列；实测 top ${current.top} vs 上行 bottom ${above.bottom}）",
                current.top >= above.bottom - 1f,
            )
            assertTrue(
                "相邻两行之间只许有那一条 1dp 分隔线（原型 `.rows` 没有 gap；实测缝 " +
                    "${(current.top - above.bottom) / oneDpPx}dp）——缝更宽是加了 gap，缝归零是线没了",
                abs((current.top - above.bottom) - oneDpPx) <= 1f,
            )
        }
    }

    /**
     * ③选词书是**通栏单选行**：三册各占一行、每行都占满列表宽度，且分隔线落在**除首行以外**
     * 每一行的顶边（原型 `.pick { border-top: 1px solid var(--line-soft) }` 配
     * `.pick:first-child { border-top: 0 }`）。
     *
     * 取样口径说明（为什么「行与行之间的缝」应当正好是 1dp）：`hairlineTop` 是
     * `drawBehind` + `padding(top = 1.dp)`，而 Compose 会用内侧 padding 裁掉语义节点的 bounds
     * （`PaddingModifier.semanticsBounds`），所以带线那一行的 `boundsInRoot` 从线**下面**开始——
     * 两行语义框之间那一道缝，量到的正是线本身。骨架行用同一口径（线挂在 tag 外侧），两条判据可比。
     *
     * 变异证据（三个方向各红一次，不是只钉「有没有线」）：
     *  - `forEachIndexed` 换回 `chunked(2)` → 每册只剩半宽，通栏断言当场红；
     *  - `topDivider = index > 0` 写成恒 true → 首行语义框被裁出 1dp，顶边不再贴列表顶，反腿红；
     *  - 写成恒 false → 缝归零，正腿红；在 `Column` 上加 `spacedBy` → 缝更宽，也正腿红。
     *
     * 线的**颜色**判据用方向而不是绝对色：浅套 `line-soft`(#EEF2ED) 与页底 `background`(#F1F4EF)
     * 逐通道只差 2–3 级，照旧版「容差 2 取色」的写法根本分不开（两列时代缝里取的是 `background` vs
     * `surface`，那两者差得远）。所以这里取「缝里最暗的像素必须比页底更暗」，并先自检两组 token 的
     * 差值还剩多少判别力——token 哪天被改到彼此靠拢，本条会先在这里红，而不是静默变成空判据
     * （R-13.1 / m-1：期望值一律取 [AppColorLight] 的 token，不重抄 hex）。
     */
    @Test
    fun the_picker_lays_word_books_out_as_full_width_stacked_rows() {
        val first = WordBook("wb-first", "第一册", "L1", 100, "v1", "user-provided-xlsx")
        val second = WordBook("wb-second", "第二册", "L2", 100, "v1", "user-provided-xlsx")
        val third = WordBook("wb-third", "第三册", "L3", 100, "v1", "user-provided-xlsx")
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Light) {
                Column(Modifier.background(AppColorLight.background)) {
                    WordBookPicker(
                        wordBooks = listOf(first, second, third),
                        loading = false,
                        selectedId = null,
                        activeBookId = null,
                        progressByBook = emptyMap(),
                        progressLoading = false,
                        canDelete = { true },
                        onSelect = {},
                        onRequestDelete = {},
                        modifier = Modifier.testTag(LIST_TAG),
                    )
                }
            }
        }

        val listBounds = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot
        fun rowBounds(id: String) = composeRule.onNodeWithTag("wordbook_pick_$id").fetchSemanticsNode().boundsInRoot
        val b1 = rowBounds(first.id)
        val b2 = rowBounds(second.id)
        val b3 = rowBounds(third.id)
        val minHeightPx = WordBookRowMinHeight.value * composeRule.density.density

        for ((index, b) in listOf(b1, b2, b3).withIndex()) {
            assertTrue("第 $index 册必须从列表左缘起（实测 ${b.left - listBounds.left}）", abs(b.left - listBounds.left) <= 1f)
            assertTrue(
                "第 $index 册必须通栏（实测 ${b.width} / 列表 ${listBounds.width}）——两列色块已作废，不许再缩回半宽",
                abs(b.width - listBounds.width) <= 1f,
            )
            assertTrue(
                "第 $index 册的行高不足 ${WordBookRowMinHeight.value} dp（实测 ${b.height / composeRule.density.density}dp）",
                b.height >= minHeightPx - 1f,
            )
        }
        val oneDpPx = composeRule.density.density
        assertTrue("第二册必须换行到第一册下面（实测 ${b2.top} vs ${b1.bottom}）", b2.top >= b1.bottom - 1f)
        assertTrue("第三册必须换行到第二册下面（实测 ${b3.top} vs ${b2.bottom}）", b3.top >= b2.bottom - 1f)
        // 反腿（首行不画线，`.pick:first-child { border-top: 0 }`）：线挂在行框外侧，画了就会把
        // 首行的语义框顶下来 1dp，这里量到的就不是 0。
        assertTrue(
            "第一册必须贴着列表顶边（实测 ${ (b1.top - listBounds.top) / oneDpPx }dp）——" +
                "把 topDivider 写成恒 true，这里就会多出 1dp",
            abs(b1.top - listBounds.top) <= 1f,
        )
        for ((pair, label) in listOf((b1 to b2) to "一二", (b2 to b3) to "二三")) {
            val (above, current) = pair
            assertTrue(
                "第${label}册之间只许有那条 1dp 分隔线（原型 `.rows` 没有 gap；实测缝 " +
                    "${(current.top - above.bottom) / oneDpPx}dp）——缝归零是线没了，更宽是加了 gap",
                abs((current.top - above.bottom) - oneDpPx) <= 1f,
            )
        }

        val bitmap = composeRule.onNodeWithTag(LIST_TAG).captureToImage().asAndroidBitmap()
        val sampleX = ((b1.left + b1.right) / 2f - listBounds.left).roundToInt()
        fun listY(root: Float): Int = (root - listBounds.top).roundToInt()
        fun sumAt(yPx: Int): Int = channelSum(mask(bitmap.getPixel(sampleX, yPx)))

        // token 自检：两组颜色靠拢到取不出方向时，先在这里红，别把下面的方向判据变成空话。
        val backgroundSum = channelSum(rgb(AppColorLight.background))
        val lineSum = channelSum(rgb(AppColorLight.outlineSoft))
        assertTrue(
            "line-soft 与 background 的通道和只差 ${backgroundSum - lineSum}，取样失去判别力（阈值 $MIN_TOKEN_SEPARATION）",
            backgroundSum - lineSum >= MIN_TOKEN_SEPARATION,
        )

        // 页底参照点：第二册自己的上内距里取 6dp 处——内距 12dp（原型 `padding: var(--s3) 0`）
        // 之后才有内容，所以这一像素必定是纯页底。
        val reference = bitmap.getPixel(sampleX, listY(b2.top) + (6 * oneDpPx).roundToInt())
        assertTrue(
            "参照像素必须是不透明页底（alpha=${reference ushr 24}），否则方向判据被半透明层污染",
            reference ushr 24 >= 250,
        )
        val referenceSum = channelSum(mask(reference))

        // 正腿：两行语义框之间那道缝就是线本身，缝里最暗的像素必须明显暗于页底。
        val bandTop = listY(b1.bottom)
        val bandBottom = listY(b2.top)
        assertTrue("缝里没有可取的像素（band $bandTop..$bandBottom）——上面的缝判据应先于这里拦住", bandBottom > bandTop)
        val dividerSum = (bandTop until bandBottom).minOf { sumAt(it) }
        assertTrue(
            "第二册顶边必须有那条 line-soft 分隔线（页底通道和 $referenceSum，缝内最暗 $dividerSum，" +
                "至少要暗 $MIN_LINE_DARKNESS）——线没了就等于两行粘成一块",
            referenceSum - dividerSum >= MIN_LINE_DARKNESS,
        )
    }

    private fun rgb(color: Color): Int = color.toArgb() and 0xFFFFFF

    private fun mask(argb: Int): Int = argb and 0xFFFFFF

    private fun channelSum(rgb: Int): Int = ((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)

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
}

private const val LIST_TAG = "wordbook_list_probe"

/**
 * 线判据的两道阈值，都是**通道和**（R+G+B）上的差值，不是逐通道容差：
 * 浅套 `background` #F1F4EF = 724、`outlineSoft` #EEF2ED = 717，实差 7。
 *
 * [MIN_TOKEN_SEPARATION] 先自检「两组 token 还分得开」（低于它说明颜色被改到彼此靠拢，方向判据会
 * 静默失效，本条先在这里红）；[MIN_LINE_DARKNESS] 取实差的小半（3），够挡住抗锯齿把 1dp 线摊薄，
 * 又远大于「线根本不存在」时的 0。
 */
private const val MIN_TOKEN_SEPARATION = 5
private const val MIN_LINE_DARKNESS = 3
