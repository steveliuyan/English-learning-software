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
     * clickable → padding），判据取「两个修饰符各自首次出现的下标」再数值比较。
     *
     * 为什么不用字面文本正则定位（M-c，修复轮 2）：旧形状把回调体 `onClick = { onClick(state) }` 写死进模式，
     * 良性重构（回调改名、换成函数引用、参数换行）会让它带着「顺序错了」这一**误导性消息**红——
     * 而这条契约真正问的只是「谁在链上更靠前」，与回调体怎么写无关。
     * 「真的排反了」那一半仍由反面锚 [paddingOutsideClickable] 负责，它保留 160 字符窄窗口（窗口开太宽会失去定位力）。
     */
    private fun pillOrderIndices(source: String): Pair<Int, Int> =
        source.indexOf(".clickable(") to source.indexOf(".padding(")

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
     * 裸 hex 探针：`Color(0x…)` 的字面量位数按「8 位（AARRGGBB）**或** 6 位（RRGGBB）」两档都认
     * （M-b，修复轮 2：原先只写 `{8}`，`Color(0xFF3B30)` 这一类省略 alpha 的写法会漏过本条红线）。
     */
    private val rawHex = Regex("""Color\(\s*0x(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6})\s*\)""")

    /** 字形随态取图走纯函数（不在组件里写第二份 `when`）。 */
    private val glyphFromPureMapper = Regex("""AppGlyph\(\s*icon = speakerGlyph\(state\)""")

    /** 可见文字与朗读文案同源：文字直接取 [speakerContentDescription]，组件内不再另写一份字面量。 */
    private val labelFromReadout = Regex("""Text\(\s*text = speakerContentDescription\(state\)""")

    /**
     * 组件内**不得**再设 `contentDescription`：可见文字已由 `clickable` 的 `MergeDescendants` 并进同一枚
     * 语义节点，另设一份会让 TalkBack 把同一句话念两遍（`SpeakerButton.kt` 的 KDoc 登记了这条取舍）。
     */
    private val duplicateReadout = Regex("""contentDescription\s*=""")

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
        val rejected = ratioOf(AppColorLight.text3, AppColorLight.surface)
        assertTrue(
            rejected < 4.5,
            "被拒候选 text3 压浅套 surface 已达 %.2f:1（≥4.5）——裁决依据失效，需重议词行次要档墨".format(rejected),
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
            clickableIndex in 0 until paddingIndex,
            "clickable 必须排在 padding 之前（可点面 = 整枚药片含内边距）：首次下标 clickable=$clickableIndex, " +
                "padding=$paddingIndex（-1 = 本文件里没有那一枚；clickable > padding 就是把可点面缩回内容盒）",
        )
        assertEquals(0, countMatches(source, paddingOutsideClickable), "padding 排在 clickable 之外会把可点面缩回内容盒（EntryRow 真机 40dp、旧喇叭药片 32.38dp 的成因族）")
        assertEquals(1, countMatches(source, rippleAboveBackdrop), "clickable 必须排在 background 之后，否则水波纹被底色盖住")
        assertEquals(1, countMatches(source, pillClip), "药片裁角只此一处（第二处 = 第二份容器）")
        assertEquals(1, countMatches(source, pillBackdrop), "药片底色只此一处")
        assertEquals(0, countMatches(source, bannedCardShape), "新组件禁读 AppShape.Card（20dp 旧档）")
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
        // 正面判据（下标比较）与反面锚（窄窗口正则）在两段片段上必须各自判得出，见 [pillOrderIndices] 的 M-c 说明。
        val (goodClickable, goodPadding) = pillOrderIndices(goodPill)
        assertTrue(
            goodClickable in 0 until goodPadding,
            "正控：clickable 在 padding 之外的药片链必须满足下标判据，实际 clickable=$goodClickable, padding=$goodPadding",
        )
        assertEquals(0, countMatches(goodPill, paddingOutsideClickable), "正控：合规链不得被反面锚命中")
        val (badClickable, badPadding) = pillOrderIndices(badPill)
        assertFalse(
            badClickable in 0 until badPadding,
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
    }
}
