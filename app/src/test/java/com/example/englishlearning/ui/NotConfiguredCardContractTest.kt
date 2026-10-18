package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.components.notConfiguredCardInks
import com.example.englishlearning.ui.components.notConfiguredStatusLine
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainLight
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 批 1 Task 11：`NotConfiguredCard`（spec §3「AI 未配置：一句现状 + 一个『去配置 ›』出口，
 * **绝不再做只给文字的死屏**」）的墨色纯表 + 源码契约。
 *
 * 两件事只有 JVM 侧钉得住：
 *  ① 墨色——connected 读不到颜色（Task 9 实测口径），所以四枚槽位取哪枚 token 由 [notConfiguredCardInks]
 *     这张纯表承载（与 `entryRowInks` / `noticeInk` 同族），本类逐槽等值 + 逐槽复算比值；
 *  ② 死屏禁令——「必须给出口」是**签名形状**问题（`onConfigure` 不可空、不可有默认空实现），
 *     connected 用例只能证明「这一处渲染出了出口」，证明不了「组件不允许不渲染出口」；
 *     那要靠源码契约钉形参形状（屏侧「重排后必须调用 `NotConfiguredCard(`」那条按 brief Step 2 归批 3 收口）。
 *
 * 比值口径照抄 `EntryRowInkTest`：共享夹具 [ratio]（int 域 WCAG）当场复算、只写进断言消息，注释不抄数值；
 * 装饰档（chevron）按 3:1 **加余量**判 ≥3.1（Task 1 裁决 #1 / `EntryRowInkTest` 同口径：卡在边界不算合格）。
 */
class NotConfiguredCardContractTest {

    private companion object {
        const val NOT_CONFIGURED_CARD = "NotConfiguredCard.kt"
    }

    private val sets: List<AppColorSet> = listOf(AppColorLight, AppColorDark)

    private fun rgbOf(color: Color): Int = color.toArgb() and 0xFFFFFF

    private fun ratioOf(fg: Color, bg: Color): Double = ratio(rgbOf(fg), rgbOf(bg))

    /**
     * 出口标签探针（`semantics { testTag = "…" }` 的赋值形，带**闭合引号**锚）：
     * 用 `Modifier.testTag("not_configured_goto")` 那种调用形写标签的话本探针数不出来——
     * 这是刻意的（理由写在下面那条断言的消息里），不是漏判。
     */
    private val exitTagProbe = Regex("testTag\\s*=\\s*\"not_configured_goto\"")

    /**
     * 四枚槽位各归各的 token，并压本套面板底（`surface`）达标。
     *
     * 出口墨取 `text1` + 组件侧 `FontWeight.Bold`，**不取域色 deep**：与 R-10.1b 同一条裁决——
     * 稿 `.act` 那类「链接用强调色」的写法压浅套底不足正文 4.5（AiSpeech deep 压 surface 也在 4.5 之下），
     * 批 1 Task 1 的低对比禁令会当场挡下；`text1` 两套都富余，链接读感由字重承担。
     * chevron 取 `text2`（`EntryRow` 同槽位同 token），装饰档按 ≥3.1 判。
     */
    @Test
    fun not_configured_card_inks_are_pinboard_slots_on_the_panel_surface() {
        sets.forEach { set ->
            val inks = notConfiguredCardInks(set)
            assertEquals(set.text1, inks.title, "现状句必须取 text1（它是这张卡的主信息）")
            assertEquals(set.text2, inks.detail, "补充说明必须取 text2（次要信息，与入口行副标题同档）")
            assertEquals(set.text1, inks.exit, "出口墨必须取 text1 + 组件侧 Bold（R-10.1b 同族裁决，不用域色 deep）")
            assertEquals(set.text2, inks.chevron, "尾部「›」必须取 text2（与 EntryRow 的 chevron 同槽位同 token）")

            val title = ratioOf(inks.title, set.surface)
            assertTrue(title >= 4.5, "现状句压面板 surface 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(title))
            val detail = ratioOf(inks.detail, set.surface)
            assertTrue(detail >= 4.5, "补充说明压面板 surface 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(detail))
            val exit = ratioOf(inks.exit, set.surface)
            assertTrue(exit >= 4.5, "出口文案压面板 surface 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(exit))
            val chevron = ratioOf(inks.chevron, set.surface)
            assertTrue(
                chevron >= 3.1,
                "chevron 压面板 surface 只有 %.2f:1，装饰档按 3:1+余量（≥3.1）判（set=$set）".format(chevron)
            )
        }
        // 被拒候选当场不合格（house 口径的可判红裁决依据）：稿 `.act` 那类域色 deep（浅套 AiSpeech）
        // 压浅套面板 surface 不足正文 4.5——这才是「出口墨取 text1、不取强调色」的依据本身在承重。
        val rejected = ratioOf(DomainLight.getValue(AppDomain.AiSpeech).deep, AppColorLight.surface)
        assertTrue(
            rejected < 4.5,
            "裁决依据失效：浅套 AiSpeech deep 压 surface 已升到 %.2f:1（≥4.5），「出口墨不得回稿面强调色」需重议".format(rejected)
        )
    }

    /**
     * 「一句现状」的措辞：点名功能 + 说清缺什么，且**只有一句**（不许换行、不许塞第二件事）。
     *
     * 反空转两条：换功能名必须换文案（写死常量即红）；文案里必须真出现功能名
     * （否则用户不知道是哪项能力不可用，四个消费屏同屏时无法区分）。
     */
    @Test
    fun status_line_names_the_feature_and_stays_one_sentence() {
        val line = notConfiguredStatusLine("长难句分析")
        assertTrue(line.contains("长难句分析"), "现状句必须点名功能：$line")
        assertTrue(line.contains("AI 服务"), "现状句必须说清缺的是 AI 服务（不是含糊的「暂不可用」）：$line")
        assertFalse(line.contains("\n"), "现状句必须是一句，不许多行：$line")
        assertTrue(
            notConfiguredStatusLine("拍照翻译") != line,
            "换功能名必须换文案（写死常量就是装饰形参）：$line"
        )
        assertEquals("「拍照翻译」还没有可用的 AI 服务", notConfiguredStatusLine("拍照翻译"), "措辞是契约面：改字要连本条一起改")
    }

    /**
     * 死屏禁令的源码面：出口是**必填回调**，不是可选装饰。
     *
     * 钉三件事：`onConfigure: () -> Unit` 恰一处；不得有可空变体；不得有默认值
     * （给了默认值就等于允许「不接出口也能编译」，正是 spec §3 点名的「只给文字的死屏」）。
     * 其余锚点同 `SharedComponentGeometryContractTest` 口径：48dp 只落在出口那一枚、圆角走装配档、
     * 不读 `AppShape.Card`、不给 `maxLines`（裁掉现状句就是丢信息）、不读静态浅套源、不写裸 hex。
     */
    @Test
    fun the_configure_exit_is_mandatory_and_is_the_only_touch_target() {
        val source = uiComponentSource(NOT_CONFIGURED_CARD)
        assertEquals(1, countMatches(source, Regex("""onConfigure: \(\) -> Unit,""")), "出口回调必须恰一处且为必填形参")
        assertEquals(
            0,
            countMatches(source, Regex("""onConfigure: \(\(\) -> Unit\)\?""")),
            "死屏禁令：出口回调不得可空（可空 = 允许渲染一张没有出口的卡）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""onConfigure: \(\) -> Unit =""")),
            "死屏禁令：出口回调不得有默认值（有默认值 = 不接出口也能编译，正是 spec §3 点名的死屏）",
        )
        assertEquals(
            1,
            countMatches(source, exitTagProbe),
            "出口标签必须恰一处（批 2/3 各屏与 Task 12 的份数棘轮都按这个 tag 定位出口）。" +
                "钉的是 `semantics { testTag = … }` 的赋值形而不是 `Modifier.testTag(…)`：标签要与可点语义" +
                "并在同一枚**合并容器**节点上（`mergeDescendants = true`），照的是 `InlineNotice` 动作槽那份真机验过的写法；" +
                "写成 `Modifier.testTag(…)` 时合并树可见性只能依赖 `clickable` 隐含的 mergeDescendants，不够显式。",
        )
        assertEquals(
            1,
            countMatches("""Modifier.semantics(mergeDescendants = true) { testTag = "not_configured_goto" }""", exitTagProbe),
            "探针失控：出口标签的赋值形根本没被数出来",
        )
        assertEquals(
            0,
            countMatches("""Modifier.semantics { testTag = "not_configured_goto_other" }""", exitTagProbe),
            "探针越界：换了标签名也算命中（tag 是批 2/3 的定位锚，漂一个字符全线找不到出口）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""heightIn\(min = 48\.dp\)""")),
            "48dp 触控下限必须恰一处、且只落在出口（卡片本体是内容不是触控目标，钉它等于造一条稿里没有的水线）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""fillMaxWidth\(\)\s*\.clickable\(""")),
            "出口必须整行可点：clickable 排在 fillMaxWidth() 之后、任何 padding 之前" +
                "（EntryRow 的「40dp 假面」教训；本锚按生产现链字面取样，heightIn 与 clickable 的先后中性——" +
                "可点面两条共同交付见 EntryRow.kt KDoc 与 SharedComponentGeometryContractTest.entryRowOrderIndices）",
        )
        assertEquals(1, countMatches(source, Regex("""MaterialTheme\.shapes\.medium""")), "圆角必须恰一处装配档（= 稿 --r-card 18dp）")
        assertEquals(0, countMatches(source, Regex("""AppShape\.Card""")), "新组件禁读 AppShape.Card（20dp 旧档）")
        assertEquals(0, countMatches(source, Regex("""maxLines""")), "现状句与补充说明都不得裁（spec §3：fontScale 2.0 下不裁文字）")
        assertEquals(
            1,
            countMatches(source, Regex("""notConfiguredCardInks\(appColors\(\)\)""")),
            "墨色必须恰一处从纯表取（组件内不临场凑色）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""InlineIconBadge\(""")),
            "行内徽标必须走 IconBadge 那一枚 30dp 容器（手搓第二份容器会撞 Task 12 的组件份数棘轮）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""size = 16\.dp, layer = AppGlyphLayer\.StrokeOnly""")),
            "尾部「›」必须恰一处 16dp + 显式 StrokeOnly（与 EntryRow 的 chevron 同一形状，锚点按 size→layer 顺序承重）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\b(?:AppPalette|DomainColors)\.""")),
            "新组件不得读静态浅套源（深色模式会拿到浅值；DarkTokenConsumptionRatchetTest 同口径）",
        )
        assertEquals(
            0,
            countMatches(source, rawColorLiteral),
            "新组件不得写裸 hex（口径 = 共享夹具 rawColorLiteral，与 RawColorLiteralRatchetTest 同一份，批 1 终审 F2）",
        )

        // 合成正/负片段：必填形参探针既认自家形状，也认得出「可空」「带默认值」两种死屏后门。
        val good = "fun NotConfiguredCard(\n    featureLabel: String,\n    onConfigure: () -> Unit,\n" +
            "    modifier: Modifier = Modifier,\n    detail: String? = null,\n)"
        assertEquals(1, countMatches(good, Regex("""onConfigure: \(\) -> Unit,""")), "合成正片段漏判：必填出口没被认出来")
        assertEquals(
            1,
            countMatches("fun NotConfiguredCard(onConfigure: (() -> Unit)? = null)", Regex("""onConfigure: \(\(\) -> Unit\)\?""")),
            "死屏后门探针失控：可空出口没被数出来",
        )
        assertEquals(
            1,
            countMatches("fun NotConfiguredCard(onConfigure: () -> Unit = {})", Regex("""onConfigure: \(\) -> Unit =""")),
            "死屏后门探针失控：带默认值的出口没被数出来",
        )
        assertEquals(
            0,
            countMatches("fun NotConfiguredCard(onConfigure: () -> Unit = {})", Regex("""onConfigure: \(\) -> Unit,"""")),
            "探针越界：带默认值的出口竟也算必填形参（两条探针必须互斥，否则死屏后门能蒙过去）",
        )
    }

    @Test
    fun card_container_and_border_tokens_are_pinned_to_the_current_set() {
        val source = uiComponentSource("NotConfiguredCard.kt")
        // 卡面底与描边读的是当前套（浅/深各自一枚），不是静态浅值：纯表只管四枚槽位墨、不代管容器色，
        // 所以这两枚得在源码侧钉住。少了这条，把 set.surface 换成 AppPalette.Surface 一类静态读点
        // 只有「AppPalette|DomainColors 0 处」那条粗探针能挡，换成别的静态写法就漏（评审 M-2）。
        assertEquals(1, countMatches(source, Regex("""\.background\(set\.surface\)""")), "卡面底必须恰一处读当前套 surface")
        assertEquals(1, countMatches(source, Regex("""border\(1\.dp, set\.outlineSoft""")), "描边必须恰一处读当前套 outlineSoft")
        // 上面两条只钉了 token **名字**，钉不住 `set` 是谁：`val set = AppColorLight` 能让两条照常绿，
        // 而深色模式下卡面就成了静态浅值（复评 N-2）。故再钉「本文件 `val set = appColors()` 恰一处」，
        // 让「读的是当前套」这件事本身也有锚。
        assertEquals(1, countMatches(source, Regex("""val set = appColors\(\)""")), "容器色必须取自当前套 appColors()，不得写死某一套")
        // 负控：静态读点形态一条都不许出现（正控已由上面两条恰一处承担）
        assertEquals(0, countMatches(source, Regex("""AppPalette\.(Surface|Separator)""")), "容器色不得退回静态 token")
    }

    // M-3（Task 12 兑现）：本类原先自带一份私有 uiComponentSource，与另外三份逐字相同，已提升为
    // SourceRatchet.kt 的共享夹具（文件恰一个 + 目录守卫两条断言逐字保留），本文件直接调用同名顶层函数。
}
