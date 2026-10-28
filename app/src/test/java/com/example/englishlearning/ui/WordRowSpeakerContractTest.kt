package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.SpeakerState
import com.example.englishlearning.ui.components.speakerButtonInk
import com.example.englishlearning.ui.components.speakerContentDescription
import com.example.englishlearning.ui.components.speakerGlyph
import com.example.englishlearning.ui.components.wordRowInks
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 批 1 Task 12 Step 1/2：`WordRow` 与 `SpeakerButton` 的 JVM 侧契约面
 * （纯函数取值 + 纯表墨色 + 源码几何 + 对比度实算，**不含任何设备推断**）。
 *
 * 为什么全在 JVM 侧：connected 读不到颜色（Task 9 实测口径——`AppGlyph`/`Text` 的着色不是语义节点能取的
 * 东西），所以「哪一槽取哪枚 token」「三态朗读文案是否互不相同」「三态各取哪枚字形」这些裁决必须由
 * 纯表 [wordRowInks]/[speakerButtonInk] 与纯函数 [speakerContentDescription]/[speakerGlyph] 承载；
 * connected 侧（`WordRowTest`/`SpeakerButtonTest`）只钉几何、可点面、role 与裁切。
 *
 * 读法复用共享夹具 [uiComponentSource]/[countMatches]（`SourceRatchet.kt`）：**先剥注释再匹配**，
 * 所以实现文件 KDoc 里合法复述的字面量（如「`heightIn(min = 48.dp)`」「播放发音」）喂不进探针。
 * 每条源码正则都配正片段 + 负片段自证承重（见 [probes_match_their_target_and_reject_the_near_miss]）——
 * 正则写松了（例如把 `padding` 在前的错误顺序也判成合规），生产码怎么改都绿，契约就是空转。
 *
 * 比值一律不抄进注释（抄了会随 token 取值漂移），只进断言消息；公式复用共享夹具的 [ratio]
 * （Global Constraints #7 的 int 域 WCAG 实现），测试端只加 Color→int 取样壳。
 */
class WordRowSpeakerContractTest {

    private companion object {
        const val WORD_ROW = "WordRow.kt"
        const val SPEAKER_BUTTON = "SpeakerButton.kt"
    }

    private fun rgbOf(color: Color): Int = color.toArgb() and 0xFFFFFF

    private fun ratioOf(fg: Color, bg: Color): Double = ratio(rgbOf(fg), rgbOf(bg))

    // ---------- SpeakerButton.kt 规则形状 ----------

    /** 触控下限：链上恰一处 48dp 最小高（禁靠 padding 凑，Global Constraints #8）。 */
    private val touchFloor48 = Regex("""heightIn\(min = 48\.dp\)""")

    /**
     * 药片可点面顺序锚：`clickable` 排在 `padding` **之外**（药片链形 = heightIn → clip → background →
     * clickable → padding），判据取「clickable 的下标」与「**本条链内**早于 clickable 的第一枚 padding 的下标」再数值比较。
     *
     * 为什么不用字面文本正则定位（M-c，修复轮 2）：旧形状把回调体 `onClick = { onClick(state) }` 写死进模式，
     * 良性重构（回调改名、换成函数引用、参数换行）会让它带着「顺序错了」这一**误导性消息**红——
     * 而这条契约真正问的只是「谁在链上更靠前」，与回调体怎么写无关。
     * 「真的排反了」那一半仍由反面锚 [paddingOutsideClickable] 负责，它保留 160 字符窄窗口（窗口开太宽会失去定位力）。
     *
     * **padding 的取样窗口必须只有本条 modifier 链**（批 1 终审窄复评 N-3，与 `SharedComponentGeometryContractTest`
     * 的 `entryRowOrderIndices` 同族缺陷）：原先取的是**全文件首个** `.padding(`，于是同一文件里药片链之前
     * 任何别处的 padding（内部图标 Row 的间距、子组件形参默认值）都会把合法链判成假红，而批 2 正是往
     * `SpeakerButton.kt` 里加东西的那一棒。链头用「clickable 之前最近的一枚 `modifier`/`Modifier` 接收者」定位，
     * 第二个下标的语义随之改成「链内早于 clickable 的 padding 下标；-1 = 没有（合规）」。
     */
    private fun pillOrderIndices(source: String): Pair<Int, Int> {
        val clickableIndex = source.indexOf(".clickable(")
        val chainStart = maxOf(
            source.lastIndexOf("modifier", clickableIndex),
            source.lastIndexOf("Modifier", clickableIndex),
        )
        val paddingBeforeClickable = if (chainStart in 0 until clickableIndex) {
            val index = source.indexOf(".padding(", chainStart)
            if (index in 0 until clickableIndex) index else -1
        } else {
            -1
        }
        return clickableIndex to paddingBeforeClickable
    }

    /** [pillOrderIndices] 的判据本体：链上有 clickable，且链内没有早于它的 padding。 */
    private fun pillClickableCoversPill(source: String): Boolean {
        val (clickableIndex, paddingIndex) = pillOrderIndices(source)
        return clickableIndex >= 0 && paddingIndex == -1
    }

    /** 反面形状：`padding` 排在 `clickable` 之外 = 可点面退回内容盒（`EntryRow` 真机 40dp 的成因）。 */
    private val paddingOutsideClickable = Regex("""\.padding\([\s\S]{0,160}?\.clickable\(""")

    /** 水波纹层次锚：`clickable` 在 `background` 之后（反过来水波纹被底色盖住、点了没有反馈）。 */
    private val rippleAboveBackdrop = Regex("""\.background\(ink\.backdrop\)[\s\S]{0,160}?\.clickable\(""")

    /** 药片容器只此一枚：裁角与底色各写一遍（第二处 = 第二份容器，撞组件份数棘轮）。 */
    private val pillClip = Regex("""clip\(AppShape\.Pill\)""")
    private val pillBackdrop = Regex("""\.background\(ink\.backdrop\)""")

    /** 禁读的旧档圆角（新组件圆角走 `AppShape.Pill`，不读 `AppShape.Card`）。 */
    private val bannedCardShape = Regex("""AppShape\.Card""")

    /** 墨色只从纯表取一次；组件体内不得出现静态令牌读点或裸 hex（两份既有棘轮的同一红线）。 */
    private val pillInkFromPureTable = Regex("""speakerButtonInk\(appColors\(\)\)""")
    private val staticPaletteRead = Regex("""AppPalette\.|DomainColors\.""")

    /**
     * 裸 hex 探针 = 共享夹具唯一的 [rawColorLiteral]（批 1 终审 F2：口径不许在本文件再抄一遍）。
     * 位数按「8 位（AARRGGBB）**或** 6 位（RRGGBB）」两档都认（M-b，修复轮 2），并容忍 Kotlin 整型字面量
     * 后缀与 `.toInt()/.toLong()/…` 窄化调用（F2：`Color(0xFF6B61L)` 这类写法能编译，
     * 只匹配「十六进制紧跟右括号」的旧正则会被整体绕过）。
     */
    private val rawHex = rawColorLiteral

    /** 字形随态取图走纯函数（不在组件里写第二份 `when`）。 */
    private val glyphFromPureMapper = Regex("""AppGlyph\(\s*icon = speakerGlyph\(state\)""")

    /** 可见文字与朗读文案同源：文字直接取 [speakerContentDescription]，组件内不再另写一份字面量。 */
    private val labelFromReadout = Regex("""Text\(\s*text = speakerContentDescription\(state\)""")

    /**
     * 组件内**不得**再设 `contentDescription`：可见文字已由 `clickable` 的 `MergeDescendants` 并进同一枚
     * 语义节点，另设一份会让 TalkBack 把同一句话念两遍（`SpeakerButton.kt` 的 KDoc 登记了这条取舍）。
     */
    private val duplicateReadout = Regex("""contentDescription\s*=""")

    // ---------- I-1（Task 16 修复轮 1，评审 M2 当席实证）：两枚文字的墨消费点 ----------

    /**
     * 主状态文字墨链：以朗读文案取内容的那枚 `Text`，必须在**自己的实参段内**接墨表的文案槽
     * （[SpeakerButtonInk.label]）。`[^)]` 上界是「命中的那枚就是生效渲染路径」的承重：
     * 没有它，主文字换墨后链会跨过主 `Text` 的闭合括号去吃尾缀槽的墨，绕墨照样绿（负控见探针）。
     * 槽名后的负向前瞻灭掉「消费点写成对槽值的加工调用」这一族假形（`.copy(alpha = …)` 等）。
     */
    private val stateLabelInkChain = Regex(
        """Text\(\s*text = speakerContentDescription\(state\),[^)]{0,120}?color = ink\.label(?![A-Za-z0-9_.])"""
    )

    /**
     * 尾缀槽墨链（I-1 主受体）：从口音标注非空分支的**活分支头**起，分支体内的尾缀文字
     * 必须在自己的实参段内接墨表文案槽。链认分支头字面形状 = 别的函数里、死分支前的同形墨不算数
     * （Task 15 / 14B「锚必须绑生效路径」口径）；分支体**内部可达性**的残余边界登记在用例 KDoc，归批 3 结转族。
     */
    private val accentSuffixInkChain = Regex(
        """if \(accentLabel != null\) \{[^)]{0,120}?Text\(\s*text = accentLabel,[^)]{0,120}?color = ink\.label(?![A-Za-z0-9_.])"""
    )

    /**
     * 文案槽消费总账：**恰 2**——主状态文字 + 口音尾缀共用同一枚文案槽是设计（同墨同档），
     * 本锚不是把「两处」当重复打；它打的是反向：把第三份同形墨塞进死分支/别处来喂上面两条窗口锚。
     */
    private val labelSlotConsumers = Regex("""color = ink\.label(?![A-Za-z0-9_.])""")

    /** 绕墨直接形状（M2 本体族）：任何文字前景不得改接字形槽或药片底色槽（改后者即 1:1 隐形）。 */
    private val textColorFromOtherInkSlot = Regex("""color = ink\.(?:glyph|backdrop)(?![A-Za-z0-9_.])""")

    /** 命名色常量与 Color 构造调用凑色族（PillButtonInkTest R-14A-i.1 同口径的绕墨路径）。 */
    private val textColorFromConstant = Regex("""Color\.[A-Za-z]|Color\(""")

    // ---------- WordRow.kt 规则形状 ----------

    /** 组件定义恰一处（第二处 = 同文件长出第二份词行）。 */
    private val singleDefinition = Regex("""\nfun WordRow\(""")

    /** 顺序锚：可点面（经 `then(touchModifier)` 入链）在 `heightIn`/`padding` 之外。 */
    private val rowClickableOutsidePadding =
        Regex("""\.then\(touchModifier\)[\s\S]{0,160}?heightIn\(min = 48\.dp\)[\s\S]{0,160}?\.padding\(""")

    /** 反面锚：`padding` 排在可点面之外。 */
    private val rowPaddingOutsideClickable = Regex("""\.padding\([\s\S]{0,160}?\.then\(touchModifier\)""")

    /** `role` 由 `onClick` 是否为空决定：为空即链上不挂 `clickable`，行不会被念成按钮。 */
    private val roleFollowsClickability = Regex(
        """val touchModifier = if \(onClick == null\) \{\s*Modifier\s*\} else \{\s*""" +
            """Modifier\.clickable\(role = Role\.Button, onClick = onClick\)\s*\}"""
    )

    /** 裁切取值：lemma 单行、释义两行（brief Step 2），一律省略号而不是裁字形。 */
    private val lemmaOneLine = Regex("""color = inks\.lemma,\s*maxLines = 1""")
    private val glossTwoLines = Regex("""color = inks\.gloss,\s*maxLines = 2""")

    /** 词行墨色只从纯表取一次。 */
    private val rowInkFromPureTable = Regex("""wordRowInks\(appColors\(\)\)""")

    /** 两个可空槽必须真的被调用（槽位形同虚设的话批 2 会各自手搓第二份）。 */
    private val leadingSlotInvoked = Regex("""leadingSlot\(\)""")
    private val actionsSlotInvoked = Regex("""content = actionsSlot""")

    /**
     * 右列宽度上限（批 1 终审 F3，C1 I-2）：状态/meta 那一列必须带 `widthIn(max = …)`。
     *
     * 为什么是形状断言而不是取值断言：`Row` 先量**无权重**的孩子，右列既无 weight 又无上限时，
     * `fontScale = 2.0` 加日期型 meta 能把 `weight(1f)` 的 lemma 列挤到 0dp（词头整列消失，连省略号都没有）。
     * 本条只钉「右列必须有一道宽度上限」这一形状；上限取值（120dp）的理由在 `WordRow.kt` KDoc，
     * 实际能否站住由 connected 的 `WordRowTest` 在 fontScale 2.0 下量 lemma 宽度（≥48dp）。
     */
    private val rightColumnWidthCap = Regex("""widthIn\(max = \d+\.dp\)""")

    /** 词行不读域色：`domain` 形参不参与取色（域色只在 `leading` 槽里由调用方组合的徽标自己取）。 */
    private val domainAccentRead = Regex("""appDomain\(""")

    // ---------- Step 1：朗读文案（brief 逐字） ----------

    @Test
    fun each_playback_state_has_one_distinct_readout() {
        val labels = SpeakerState.entries.map(::speakerContentDescription)
        assertEquals(labels.distinct().size, SpeakerState.entries.size, "三态文案必须互不相同，TalkBack 才能区分")
        assertTrue(labels.all { it.contains("发音") })
    }

    /**
     * 文案是逐字契约面（改字要连本用例一起改）：`播放发音` / `暂停发音` / `继续发音`。
     * 措辞按「点下去会发生什么」写；`Paused` 必须读「继续发音」而不是「播放发音」——
     * 暂停后恢复若也念「播放」，用户会以为要从头再放一次。
     */
    @Test
    fun readout_wording_is_pinned_verbatim_per_state() {
        assertEquals("播放发音", speakerContentDescription(SpeakerState.Idle))
        assertEquals("暂停发音", speakerContentDescription(SpeakerState.Playing))
        assertEquals("继续发音", speakerContentDescription(SpeakerState.Paused))
    }

    /**
     * 三态各取一枚字形且互不相同（同一枚图标表达三态 = 视觉通道也读不出状态），
     * 并且三枚都在既有图标族内——新增字形要重走图标决策与许可台账（Task 7 已钉死），
     * 这条把「不许悄悄加第 34 枚」变成可判红的断言。
     */
    @Test
    fun each_state_maps_to_its_own_glyph_of_the_icon_family() {
        val icons = SpeakerState.entries.map(::speakerGlyph)
        assertEquals(icons.distinct().size, SpeakerState.entries.size, "三态字形必须互不相同，实际=$icons")
        assertEquals(AppIcon.Speaker, speakerGlyph(SpeakerState.Idle), "待播态取喇叭（这枚药片的身份）")
        assertEquals(AppIcon.Pause, speakerGlyph(SpeakerState.Playing), "播放中取暂停（点下去的动作）")
        assertEquals(AppIcon.Play, speakerGlyph(SpeakerState.Paused), "已暂停取继续（点下去的动作）")
        assertTrue(icons.all { it in AppIcon.entries }, "三枚字形必须来自既有图标族，实际=$icons")
    }

    // ---------- 墨色纯表 ----------

    /**
     * 药片三枚墨各归各的 token，且**浅薄荷底不取白字**：底是 `tint`，前景按颜色硬规则取 `onMint`
     * （Global Constraints #6：浅薄荷底禁白字，前景取 `on-mint`/`on-brand`；`InlineNotice` Info 档同一配对）。
     * 深套的 `onMint` 本身就是浅薄荷，所以两套用同一枚 token、不按主题分支——这条也在本用例里被复算兜住
     * （深套若掉到 4.5 以下会当场红）。
     *
     * 承重依据是「白字被拒」：白压浅套 `tint` 当场远低于 4.5，比值只进断言消息、注释不抄数。
     * （实现期实测过另一枚候选 `text2` 压浅套 `tint`，它**达** AA，所以不拿它当被拒依据；
     * 不选它的理由是硬规则把薄荷底与 `onMint`/`onBrand` 配对，不是对比度不够。）
     */
    @Test
    fun pill_inks_are_pinboard_slots_and_clear_aa_on_the_tint_backdrop() {
        listOf(AppColorLight, AppColorDark).forEach { set ->
            val ink = speakerButtonInk(set)
            assertEquals(set.onMint, ink.glyph, "字形墨必须取 onMint（薄荷底禁白字，同 InlineNotice 口径）")
            assertEquals(set.onMint, ink.label, "文案墨必须取 onMint")
            assertEquals(set.tint, ink.backdrop, "药片底必须取主题 tint（浅深两套随主题翻，不写死浅绿）")
            val labelRatio = ratioOf(ink.label, ink.backdrop)
            assertTrue(
                labelRatio >= 4.5,
                "文案压药片底只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(labelRatio),
            )
            val glyphRatio = ratioOf(ink.glyph, ink.backdrop)
            assertTrue(
                glyphRatio >= 3.1,
                "图形墨压药片底只有 %.2f:1，不足非文本 3:1 加余量（Task 1 裁决 #1：卡边界不算合格，set=$set）"
                    .format(glyphRatio),
            )
        }
        val whiteOnLightTint = ratioOf(Color.White, AppColorLight.tint)
        assertTrue(
            whiteOnLightTint < 4.5,
            "白字压浅套 tint 已达 %.2f:1（≥4.5）——「浅薄荷底禁白字」的依据失效，需重议药片墨".format(whiteOnLightTint),
        )
    }

    /**
     * 词行四枚槽位各归各的 token：lemma `text1`（这一行的主信息）、gloss/status/meta `text2`
     * （次要档，与 [EntryRow] 的副标题/尾部值同槽位同 token）。三枚次要槽共用 `text2` 是**有意**的：
     * 稿面词行没有第三种文字层级，再分档就会有人拿 `text3` 当状态色，而 `text3` 是批 1 Task 1 裁决 #1
     * 明令不得作前景的那一枚——被拒候选在本用例当场复算判不合格（比值只进断言消息）。
     *
     * 词行本体不铺底色（卡面与描边归调用方容器），所以配对对象是**调用方可能给的那两档底**：
     * 本套 `surface`（卡片面）与本套 `background`（页底，行直接落在列表页底时的实际情形），
     * 不是 [InlineNotice] 那种自带 `tint` 底。四枚槽 × 两档底 × 两套主题逐组合复算，
     * 阈值一律正文 AA 4.5、不放宽（M-a，修复轮 2：原先只配 `surface`，漏掉了页底这一档）。
     */
    @Test
    fun word_row_inks_are_pinboard_slots_and_clear_aa_on_surface() {
        listOf(AppColorLight, AppColorDark).forEach { set ->
            val inks = wordRowInks(set)
            assertEquals(set.text1, inks.lemma, "lemma 必须取 text1")
            assertEquals(set.text2, inks.gloss, "释义必须取 text2")
            assertEquals(set.text2, inks.status, "状态必须取 text2（与释义同档，不新造强调色）")
            assertEquals(set.text2, inks.meta, "时间/出处一类元信息必须取 text2")
            listOf("lemma" to inks.lemma, "gloss" to inks.gloss, "status" to inks.status, "meta" to inks.meta)
                .forEach { (slot, ink) ->
                    listOf("surface" to set.surface, "background" to set.background).forEach { (backdrop, color) ->
                        val value = ratioOf(ink, color)
                        assertTrue(
                            value >= 4.5,
                            "词行 $slot 压本套 $backdrop 只有 %.2f:1，不足正文 AA 4.5（set=$set）".format(value),
                        )
                    }
                }
        }
        // 被拒候选的现行依据（2026-10-29 原型 token 迁移后换底）：`text3` 换到原型 #65756C 后
        // 压浅套 `surface` 已升到 4.87（≥4.5），旧依据作废；本用例上方本来就要求每个槽位在
        // `surface` 与 `background` **两底**上都过线，词行也真的会铺在页底上——改按较弱的那枚底
        // （页底 `background`，4.39）钉，仍然证明 `text3` 不是合格的词行次要墨，且两向可判红。
        val rejected = ratioOf(AppColorLight.text3, AppColorLight.background)
        assertTrue(
            rejected < 4.5,
            "被拒候选 text3 压浅套 background 已达 %.2f:1（≥4.5）——裁决依据失效，需重议词行次要档墨".format(rejected),
        )
    }

    // ---------- 源码契约：SpeakerButton.kt ----------

    /**
     * 药片几何与可点面顺序：48dp 下限恰一处、`clickable` 在 `padding` 之外、水波纹在底色之上、
     * 容器（裁角/底色）各恰一处、不读 `AppShape.Card`。
     *
     * 顺序承重的两条各有分工：[pillOrderIndices] 是正面判据（取下标比谁更靠前，M-c 之后不再靠字面文本定位），
     * [paddingOutsideClickable] 是反面锚（顺序错才命中）——只钉正面锚时，把整条链重排成
     * `padding … clickable` 会让正面锚静默不命中（计数变 0 才红），报错说不清「错在顺序」；
     * 反面锚直接把成因点名。正面判据另钉「`.clickable(` 恰一处」，等于旧那条「正面锚命中 1 次」里的份数那一半。
     */
    @Test
    fun pill_geometry_and_touch_order_are_pinned_in_source() {
        val source = uiComponentSource(SPEAKER_BUTTON)
        val (clickableIndex, paddingIndex) = pillOrderIndices(source)
        assertEquals(1, countMatches(source, touchFloor48), "48dp 触控下限必须恰一处（0 = 靠 padding 凑；≥2 = 两份几何）")
        assertEquals(1, countMatches(source, Regex("""\.clickable\(""")), "药片链上 clickable 只此一处（两处 = 两份可点面）")
        assertTrue(
            pillClickableCoversPill(source),
            "clickable 必须排在 padding 之前（可点面 = 整枚药片含内边距）：链内下标 clickable=$clickableIndex, " +
                "链内早于 clickable 的 padding=$paddingIndex（padding=-1 = 链里没有，合规；" +
                "padding ≥ 0 = 可点面缩回内容盒）",
        )
        assertEquals(0, countMatches(source, paddingOutsideClickable), "padding 排在 clickable 之外会把可点面缩回内容盒（EntryRow 真机 40dp、旧喇叭药片 32.38dp 的成因族）")
        assertEquals(1, countMatches(source, rippleAboveBackdrop), "clickable 必须排在 background 之后，否则水波纹被底色盖住")
        assertEquals(1, countMatches(source, pillClip), "药片裁角只此一处（第二处 = 第二份容器）")
        assertEquals(1, countMatches(source, pillBackdrop), "药片底色只此一处")
        assertEquals(0, countMatches(source, bannedCardShape), "新组件禁读 AppShape.Card（20dp 旧档）")

        // 反假红钉（批 1 终审窄复评 N-3 的同族，本文件同形缺陷）：同一文件里**别处**先出现的 `.padding(`
        // 不得把本判据带红。[pillOrderIndices] 原先取的是全文件首个 padding 下标，于是批 2 只要在药片链之前
        // 往 SpeakerButton.kt 加任何别处的 padding（内部图标 Row 的间距、形参默认值）就当场假红。
        val pillChain = "Row(modifier = modifier.heightIn(min = 48.dp).clip(AppShape.Pill)" +
            ".background(ink.backdrop).clickable(role = Role.Button, onClick = onClick)" +
            ".padding(horizontal = 16.dp))"
        val unrelatedPaddingEarlier = "Box(Modifier.padding(horizontal = 4.dp))\n" + pillChain
        val (unrelatedClickable, unrelatedPadding) = pillOrderIndices(unrelatedPaddingEarlier)
        assertTrue(
            pillClickableCoversPill(unrelatedPaddingEarlier),
            "反假红钉失控：行链之前的无关 padding 把合法药片链判成了违规（clickable=$unrelatedClickable, " +
                "链内早于 clickable 的 padding=$unrelatedPadding）"
        )
        // 同一条收窄不得放过真违规：padding 排在 clickable 之前的链必须照旧判红。
        val legacyPillChain = "Row(modifier = modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp)" +
            ".clip(AppShape.Pill).background(ink.backdrop).clickable(role = Role.Button, onClick = onClick))"
        val (legacyClickable, legacyPadding) = pillOrderIndices(legacyPillChain)
        assertFalse(
            pillClickableCoversPill(legacyPillChain),
            "反假红钉越界：padding 在 clickable 之前的旧顺序竟过了判据（clickable=$legacyClickable, padding=$legacyPadding）"
        )
    }

    /**
     * 药片的取值来源与「一处定义」面：墨色纯表恰一处、无静态令牌读点、无裸 hex、
     * 取图走 [speakerGlyph]、可见文字直接取 [speakerContentDescription]（三条字面量各只出现一次）、
     * 组件内不再另设 `contentDescription`（同一句话念两遍）。
     */
    @Test
    fun pill_reads_inks_and_readout_from_one_source_each() {
        val source = uiComponentSource(SPEAKER_BUTTON)
        assertEquals(1, countMatches(source, pillInkFromPureTable), "墨色必须从纯表取一次（组件内不得临场凑色）")
        assertEquals(0, countMatches(source, staticPaletteRead), "组件体内不得出现 AppPalette./DomainColors. 静态读点（深色令牌棘轮同一红线）")
        assertEquals(0, countMatches(source, rawHex), "组件体内不得出现裸 Color(0x…) 字面量（裸 hex 棘轮同一红线）")
        assertEquals(1, countMatches(source, glyphFromPureMapper), "字形必须经 speakerGlyph(state) 取，不在组件里写第二份 when")
        assertEquals(1, countMatches(source, labelFromReadout), "可见文字必须直接取 speakerContentDescription(state)（屏上写一套、读屏念另一套 = spec §3 点名的重复源）")
        assertEquals(0, countMatches(source, duplicateReadout), "组件内不得另设 contentDescription：clickable 已合并子孙，另设会让同一句话念两遍")
        listOf("播放发音", "暂停发音", "继续发音").forEach { label ->
            assertEquals(
                1,
                countMatches(source, Regex(label)),
                "文案「$label」在实现文件里只应出现一次（一次 = 纯函数里那一处定义；两处 = 组件里又写了一遍）",
            )
        }
    }

    /**
     * Task 16 修复轮 1（I-1 / 评审 M2 当席实证）：药片两枚文字的墨消费点补锚，封「尾缀槽绕墨」回归。
     *
     * 盲区成因：connected 读颜色不可行（Task 9 口径——`Text` 的着色不进语义树），而 M2 那发变异
     * （尾缀前景从文案槽改成药片底色槽，标注当场 1:1 隐形）零新增符号、零字面量，
     * 既不过静态令牌/裸 hex 锚，也不触文案与几何锚——两类既有 JVM 契约全绿存活。
     * 故本用例把「哪枚 `Text` 消费哪枚墨槽」钉成消费形状（先例：PillButtonInkTest 的 I-1/M3 同族修复）：
     *  - 两条**窗口链**（[stateLabelInkChain] / [accentSuffixInkChain]）各恰一次命中，且各自把
     *    「实参段内」写进判据：墨必须长在渲染那枚文字的同一 `Text(...)` 调用里；尾缀链从
     *    非空分支的**活分支头字面**起步，分支体外补的同形墨一律不吃（14B/Task 15 生效路径口径）；
     *  - [labelSlotConsumers] 总账恰 2：主状态文字与尾缀共用文案槽是**设计**（同墨同档），
     *    本账打的是第三份冒充——死分支/别处塞同形墨来喂窗口锚，总账当场失守；
     *  - 两条反面锚（[textColorFromOtherInkSlot] / [textColorFromConstant]）把「换接自家另两枚槽」
     *    与「命名色/构造器凑色」两族绕墨路径点名报错（配合既有的静态令牌/裸 hex 文件级 0 计数，
     *    M2、AppPalette 静态墨、`Color.White`、浮点构造器四种退化形状各有受体，见探针自证）。
     *
     * 残余边界（登记，与 Task 15 复评 m-1 同族、整族结转批 3）：源码锚钉的是**形状与所在分支**，
     * 钉不了分支体**内部**的可达性——若有人在尾缀分支体内再套一层恒假条件并把被拦文字的墨保持为
     * 文案槽，本锚照绿；该形状同时违反本文件多条前提（实参段紧邻、总账恰 2），要绕全锚只能改渲染结构，
     * 而设备侧「尾缀真实可见」的兜底归 connected/批 3 AC-R-12 走查。锚的取样前提是分支头保持现形状：
     * 生产侧若合法重构该分支（如条件加严），本用例以「链计数掉 0」响亮红提示契约随形状联动更新，不许静默失焦。
     */
    @Test
    fun pill_texts_consume_the_label_ink_on_their_live_paths() {
        val source = uiComponentSource(SPEAKER_BUTTON)
        assertEquals(
            1,
            countMatches(source, stateLabelInkChain),
            "以朗读文案为内容的那枚文字必须在自己的实参段内接墨表文案槽作前景" +
                "（换墨、把墨挪出本 Text、或加工槽值都判红——M2 同型的主文字侧一并封死）",
        )
        assertEquals(
            1,
            countMatches(source, accentSuffixInkChain),
            "口音标注非空分支体内的尾缀文字必须在自己实参段内接墨表文案槽：" +
                "评审 M2 的绕墨受体——换接底色槽（1:1 隐形）、静态令牌、命名色任一种，本链立即失守",
        )
        assertEquals(
            2,
            countMatches(source, labelSlotConsumers),
            "文案槽消费恰两处：主状态文字 + 口音尾缀（同墨同档是设计，不是两份实现；" +
                "第三份同形墨 = 喂窗口锚的死分支/别处冒充）",
        )
        assertEquals(
            0,
            countMatches(source, textColorFromOtherInkSlot),
            "两枚文字前景都不得改接字形槽或药片底色槽（M2 直接形状点名）",
        )
        assertEquals(
            0,
            countMatches(source, textColorFromConstant),
            "组件文件不得出现 Color.<命名色> 常量或 Color( 构造调用：绕墨同族路径（PillButtonInkTest R-14A-i.1 口径）",
        )
    }

    // ---------- 源码契约：WordRow.kt ----------

    /** 词行几何与可点面顺序：定义恰一处、48dp 下限恰一处、`clickable` 在 `padding` 之外、role 跟着可点性走、裁切档。 */
    @Test
    fun word_row_geometry_and_touch_order_are_pinned_in_source() {
        val source = uiComponentSource(WORD_ROW)
        assertEquals(1, countMatches(source, singleDefinition), "WordRow 必须恰一处定义")
        assertEquals(1, countMatches(source, touchFloor48), "48dp 触控下限必须恰一处（0 = 靠 padding 凑；≥2 = 两份几何）")
        assertEquals(1, countMatches(source, rowClickableOutsidePadding), "clickable 必须排在 padding 之前（可点面 = 整行）")
        assertEquals(0, countMatches(source, rowPaddingOutsideClickable), "padding 排在 clickable 之外会把可点面缩回内容盒（EntryRow 真机 40dp 的成因）")
        assertEquals(1, countMatches(source, roleFollowsClickability), "role 必须按 onClick 是否为空取 Role.Button/无（非可点行不得念成按钮）")
        assertEquals(1, countMatches(source, lemmaOneLine), "lemma 必须单行 + 省略号（提示性内容）")
        assertEquals(1, countMatches(source, glossTwoLines), "释义必须最多两行（brief Step 2）：一行放不下长释义，不限行会在 fontScale 2.0 下把整行撑成一列")
        assertEquals(1, countMatches(source, rightColumnWidthCap), "状态/meta 右列必须带 widthIn(max = …) 宽度上限（F3：无上限的无权重右列会在 fontScale 2.0 下把 lemma 列挤成 0dp）")
        assertEquals(0, countMatches(source, bannedCardShape), "新组件禁读 AppShape.Card（20dp 旧档）")
    }

    /** 词行墨色来源与两个槽的落地：纯表恰一处、无静态令牌/裸 hex、`leading`/`actions` 各被调用一次。 */
    @Test
    fun word_row_reads_inks_from_the_pure_table_and_really_invokes_both_slots() {
        val source = uiComponentSource(WORD_ROW)
        assertEquals(1, countMatches(source, rowInkFromPureTable), "墨色必须从纯表取一次（组件内不得临场凑色）")
        assertEquals(0, countMatches(source, staticPaletteRead), "组件体内不得出现 AppPalette./DomainColors. 静态读点")
        assertEquals(0, countMatches(source, rawHex), "组件体内不得出现裸 Color(0x…) 字面量")
        assertEquals(1, countMatches(source, leadingSlotInvoked), "leading 槽必须真的被调用一次（形同虚设的话批 2 会各自手搓前导徽标）")
        assertEquals(1, countMatches(source, actionsSlotInvoked), "actions 槽必须真的被渲染一次（RowScope 内，发音药片就放这里）")
    }

    /**
     * 词行**不读域色**（`WordRow.kt` KDoc 登记的取舍）：四枚墨是中性档，域色只出现在 `leading` 槽里
     * 由调用方组合的徽标自己取。所以 [domain] 形参不参与取色——本用例把「本文件 0 处 `appDomain(`」钉住，
     * 挡住「在组件里悄悄凑一枚域强调色」这条路径（要行级域色的正确处置是改纯表并同步补对比契约）。
     */
    @Test
    fun word_row_never_reads_a_domain_accent() {
        val source = uiComponentSource(WORD_ROW)
        assertEquals(
            0,
            countMatches(source, domainAccentRead),
            "词行不得读 appDomain(...)：行级域色要改 wordRowInks 纯表并补对比契约，不在组件里临场凑强调色",
        )
        assertEquals(
            0,
            countMatches(source, staticPaletteRead),
            "词行也不得退回静态 AppPalette./DomainColors. 读点（深色令牌棘轮同一红线）",
        )
    }

    // ---------- 正则自证（正片段 + 负片段） ----------

    /**
     * 每条源码正则的承重自证。负片段一律取「真实会被写错的那种近似形状」（顺序反了、取值差一档、
     * 私有影子份、手搓容器），不是随手编的无关文本——本仓源码契约先剥注释，
     * 没有正/负双控的正则等于自证不了「它到底在数什么」。
     */
    @Test
    fun probes_match_their_target_and_reject_the_near_miss() {
        val goodPill = """
            Row(modifier = modifier
                .heightIn(min = 48.dp)
                .clip(AppShape.Pill)
                .background(ink.backdrop)
                .clickable(role = Role.Button, onClick = { onClick(state) })
                .padding(horizontal = 16.dp)) {}
        """.trimIndent()
        val badPill = """
            Row(modifier = modifier
                .padding(horizontal = 16.dp)
                .clickable(role = Role.Button, onClick = { onClick(state) })
                .heightIn(min = 48.dp)) {}
        """.trimIndent()
        // 正面判据（链内下标比较）与反面锚（窄窗口正则）在两段片段上必须各自判得出，见 [pillOrderIndices] 的 M-c/N-3 说明。
        val (goodClickable, goodPadding) = pillOrderIndices(goodPill)
        assertTrue(
            pillClickableCoversPill(goodPill),
            "正控：clickable 在 padding 之外的药片链必须满足下标判据，实际 clickable=$goodClickable, padding=$goodPadding",
        )
        assertEquals(0, countMatches(goodPill, paddingOutsideClickable), "正控：合规链不得被反面锚命中")
        val (badClickable, badPadding) = pillOrderIndices(badPill)
        assertFalse(
            pillClickableCoversPill(badPill),
            "负控：padding 在 clickable 之外的链不得满足下标判据，实际 clickable=$badClickable, padding=$badPadding",
        )
        assertEquals(1, countMatches(badPill, paddingOutsideClickable), "负控：padding 在 clickable 之外必须被反面锚抓到")
        assertEquals(1, countMatches(goodPill, rippleAboveBackdrop), "正控：background 在 clickable 之前（水波纹在底色之上）命中")
        assertEquals(
            0,
            countMatches(".clickable(role = Role.Button, onClick = { onClick(state) })\n.background(ink.backdrop)", rippleAboveBackdrop),
            "负控：clickable 在 background 之前（水波纹被盖住）不得命中",
        )
        assertEquals(1, countMatches("Modifier.clip(AppShape.Pill)", pillClip), "正控：药片裁角命中")
        assertEquals(0, countMatches("Modifier.clip(AppShape.Button)", pillClip), "负控：按钮档圆角不算药片")
        assertEquals(1, countMatches("heightIn(min = 48.dp)", touchFloor48), "正控：48dp 下限命中")
        assertEquals(0, countMatches("heightIn(min = 44.dp)", touchFloor48), "负控：低于下限的取值不得命中")
        assertEquals(1, countMatches("val ink = speakerButtonInk(appColors())", pillInkFromPureTable), "正控：纯表取墨命中")
        assertEquals(0, countMatches("val ink = speakerButtonInk(AppColorLight)", pillInkFromPureTable), "负控：写死浅套不算「读当前套」")
        assertEquals(1, countMatches("AppGlyph(\n    icon = speakerGlyph(state),", glyphFromPureMapper), "正控：跨行取图写法也要命中")
        assertEquals(
            0,
            countMatches("AppGlyph(icon = if (state == SpeakerState.Playing) AppIcon.Pause else AppIcon.Speaker,", glyphFromPureMapper),
            "负控：组件内自写 when 取图不得命中",
        )
        assertEquals(
            1,
            countMatches("Text(\n    text = speakerContentDescription(state),", labelFromReadout),
            "正控：可见文字取朗读文案（跨行）命中",
        )
        assertEquals(0, countMatches("Text(text = \"播放发音\",", labelFromReadout), "负控：组件里另抄一份字面量不得命中")
        assertEquals(1, countMatches("semantics { contentDescription = label }", duplicateReadout), "正控：另设朗读文案必须被抓到")
        assertEquals(0, countMatches("Text(text = speakerContentDescription(state))", duplicateReadout), "负控：只靠可见文字承载语义不得被误判")

        // I-1（评审 M2 当席实证）消费点锚的承重自证：现网形状两条链各命中一次、总账恰 2；
        // M2 / 对调 / 静态墨 / 命名色四族退化形状各自有专属受体。片段用工厂拼两枚文字的取墨，避免手抄漂移。
        fun pillTextBlocks(stateInk: String, suffixInk: String) =
            "Text(\n    text = speakerContentDescription(state),\n    style = AppType.Body,\n    color = $stateInk,\n)\n" +
            "if (accentLabel != null) {\n    Text(\n        text = accentLabel,\n        style = AppType.Body,\n        color = $suffixInk,\n    )\n}"
        val liveTextBlocks = pillTextBlocks("ink.label", "ink.label")
        assertEquals(1, countMatches(liveTextBlocks, stateLabelInkChain), "正控：主状态文字在自己实参段内接文案槽墨")
        assertEquals(1, countMatches(liveTextBlocks, accentSuffixInkChain), "正控：活分支体内的尾缀文字接文案槽墨")
        assertEquals(2, countMatches(liveTextBlocks, labelSlotConsumers), "正控：两枚文字同墨同档，总账恰 2（不是把两处判成重复）")

        val m2Blocks = pillTextBlocks("ink.label", "ink.backdrop")
        assertEquals(0, countMatches(m2Blocks, accentSuffixInkChain), "负控（评审 M2 本形）：尾缀墨换接底色槽，尾缀链必须失守——这就是当席红样")
        assertEquals(1, countMatches(m2Blocks, labelSlotConsumers), "负控（评审 M2）：绕墨后文案槽总账从 2 掉到 1，总账锚同样有牙")
        assertEquals(1, countMatches(m2Blocks, textColorFromOtherInkSlot), "负控（评审 M2）：错槽消费必须被反面锚点名，报错直指成因")

        val swappedBlocks = pillTextBlocks("ink.backdrop", "ink.label")
        assertEquals(
            0,
            countMatches(swappedBlocks, stateLabelInkChain),
            "负控：主文字换墨后，链不得跨过自己 Text 的闭合去吃尾缀槽的墨——[^)] 上界的反空转自证",
        )
        assertEquals(1, countMatches(swappedBlocks, accentSuffixInkChain), "正控：尾缀链与主文字链互不牵连，对调只红主链与错槽锚")

        val staticInkBlocks = pillTextBlocks("ink.label", "com.example.englishlearning.ui.theme.AppPalette.TextPrimary")
        assertEquals(0, countMatches(staticInkBlocks, accentSuffixInkChain), "负控：尾缀换静态令牌墨，链失守")
        assertEquals(1, countMatches(staticInkBlocks, staticPaletteRead), "负控：尾缀换 AppPalette 静态墨必须同时被既有静态读点锚抓到（双受体）")

        assertEquals(0, countMatches(pillTextBlocks("ink.label", "Color.White"), accentSuffixInkChain), "负控：命名色常量不得算成接槽消费")
        assertEquals(1, countMatches(pillTextBlocks("ink.label", "Color.White"), textColorFromConstant), "负控：Color.<命名色> 必须命中常量反面锚")
        assertEquals(1, countMatches(pillTextBlocks("ink.label", "Color(0.5f, 0.5f, 0.5f)"), textColorFromConstant), "负控：浮点参数 Color( 构造凑色也命中（R-14A-i.1 口径）")
        assertEquals(0, countMatches(pillTextBlocks("ink.label.copy(alpha = 0.5f)", "ink.label"), stateLabelInkChain), "负控：把直接取槽换成对槽值的加工调用不得算成消费点（前瞻收紧自证）")

        val goodRow = """
            val touchModifier = if (onClick == null) {
                Modifier
            } else {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            }
            Row(modifier = modifier
                .fillMaxWidth()
                .then(touchModifier)
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp)) {}
        """.trimIndent()
        val badRow = """
            val touchModifier = if (onClick == null) {
                Modifier
            } else {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            }
            Row(modifier = modifier
                .padding(vertical = 8.dp)
                .then(touchModifier)
                .heightIn(min = 48.dp)) {}
        """.trimIndent()
        assertEquals(1, countMatches(goodRow, rowClickableOutsidePadding), "正控：clickable 在 padding 之外的词行链必须命中")
        assertEquals(0, countMatches(goodRow, rowPaddingOutsideClickable), "正控：合规链不得被反面锚命中")
        assertEquals(0, countMatches(badRow, rowClickableOutsidePadding), "负控：padding 在 clickable 之外的链不得命中正面锚")
        assertEquals(1, countMatches(badRow, rowPaddingOutsideClickable), "负控：padding 在 clickable 之外必须被反面锚抓到")
        assertEquals(1, countMatches(goodRow, roleFollowsClickability), "正控：role 随可点性的 if/else 写法必须命中")
        assertEquals(
            0,
            countMatches(
                "val touchModifier = Modifier.clickable(role = Role.Button, onClick = onClick!!)",
                roleFollowsClickability,
            ),
            "负控：无条件挂 Role.Button（非可点行也念成按钮）不得命中",
        )
        assertEquals(1, countMatches("color = inks.gloss,\n    maxLines = 2", glossTwoLines), "正控：跨行的 maxLines 也要命中")
        assertEquals(0, countMatches("color = inks.gloss,\n    maxLines = 3", glossTwoLines), "负控：三行释义不得命中")
        assertEquals(1, countMatches("color = inks.lemma, maxLines = 1", lemmaOneLine), "正控：lemma 单行命中")
        assertEquals(0, countMatches("color = inks.lemma", lemmaOneLine), "负控：漏掉 maxLines 不得命中")
        assertEquals(1, countMatches("if (leadingSlot != null) leadingSlot()", leadingSlotInvoked), "正控：leading 槽被调用命中")
        assertEquals(0, countMatches("if (leading != null) {}", leadingSlotInvoked), "负控：槽存在但不渲染不得命中")
        assertEquals(1, countMatches("Row(content = actionsSlot)", actionsSlotInvoked), "正控：actions 槽被渲染命中")
        assertEquals(0, countMatches("val actionsSlot = actions", actionsSlotInvoked), "负控：只赋值不渲染不得命中")
        assertEquals(1, countMatches("val palette = appDomain(domain)", domainAccentRead), "正控：读域色必须被抓到")
        assertEquals(0, countMatches("val inks = wordRowInks(appColors())", domainAccentRead), "负控：读中性纯表不得被误判成读域色")
        assertEquals(1, countMatches("\nfun WordRow(", singleDefinition), "正控：组件定义命中")
        assertEquals(0, countMatches("\nprivate fun WordRow(", singleDefinition), "负控：屏内私有影子份不得算成共享组件定义")
        // F3（右列宽度上限）的承重自证：上限形状认得出、缺上限与写反方向都判不命中。
        assertEquals(
            1,
            countMatches("Column(modifier = Modifier.widthIn(max = 120.dp), horizontalAlignment = Alignment.End)", rightColumnWidthCap),
            "正控：右列带 widthIn(max = …) 必须命中",
        )
        assertEquals(
            0,
            countMatches("Column(horizontalAlignment = Alignment.End)\n    Text(meta, maxLines = 1)", rightColumnWidthCap),
            "负控：无上限的右列（F3 的失明形状）不得命中",
        )
        assertEquals(
            0,
            countMatches("Modifier.widthIn(min = 120.dp)", rightColumnWidthCap),
            "负控：把上限写成下限（widthIn(min = …)）不得命中——方向写反等于没有上限",
        )
    }
}
