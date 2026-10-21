package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 批 1 Task 10 修复轮 1（R-10.6①）：共享组件的**稿面几何取值**在 JVM 源码侧的契约面。
 *
 * 为什么需要这一份（评审轮 C2/C3 的几何面）：connected 只能钉**下限**（`≥48dp`、`≥40dp`），
 * 钉不住稿面**取值**——徽标容器 40dp、入口行 56dp/间隙 12dp/padding 8dp、提示条 14×12dp padding、
 * 间隙 10dp、18dp 字形、1dp 描边（稿 `mockups.html:150-160 / :178-186`，实现 = `af07b34`）。
 * 取值漂移（40→48、12→10）connected 全绿，只有源码契约能挡。读法复用共享夹具
 * [uiFiles]/[stripComments]/[countMatches]（`SourceRatchet.kt`），与 `IconBadgeContrastContractTest`
 * 同一族「源码文本即契约」口径：**先剥注释再匹配**（实现文件 KDoc 会合法复述同一批字面量），
 * 且本文件不写裸十六进制/`Color(0x…)` 字面量。
 *
 * 每条用例都配合成正片段 + 合成负片段自证正则承重（先例：本仓
 * `entry_glyph_shapes_are_pinned_tone_by_tone`）；**按顺序承重的锚点**（clickable 在 fillMaxWidth() 之后
 * 且在任何 padding 之前——`heightIn` 不参与判定，判据见 [entryRowOrderIndices] 的 F1 说明；
 * drawBehind 在 padding 外侧；size 在 layer 之前）在断言消息里写明自己钉的是哪一条顺序。
 * 变异自证（brief Step 4 ⑥⑦）：生产 `spacedBy(12.dp)`→`spacedBy(10.dp)` 与本文件 12dp 锚红；
 * 生产提示条 `vertical = 12.dp`→`vertical = 14.dp` 与本文件 padding 锚红。
 *
 * **修复轮 2 追加一条「配方腿」（R2-4 / 二轮评审 I-1，R-10.7③）**：`IconBadgeContrastContractTest`
 * 那张 28 组合弱档对比表在测试端自己 `mixHex(surface, deep, 0.16)` 现算底色，全文没有一条正则读生产
 * 那枚 `lerp` → 表只对**理想配方**成立。`icon_badge_weak_tone_backdrop_recipe_is_pinned_to_source`
 * 补的就是这条生产绑定腿（变异自证 ⑪：生产 `0.16f`→`0.30f` → 本用例红）。几何值与配色配方从此同属
 * 一份契约：**connected 钉下限、本类钉取值与取色来源**。
 *
 * **Task 19 修复轮 2 的 I-1（几何锚补牙）**：`entryRowOrderIndices` 的链头原先用「clickable 之前最近的
 * `modifier`/`Modifier` 接收者」定位，被 Task 19 引入的 `.then(if (enabled) Modifier.clickable(…) else Modifier)`
 * 包装打回原形（链头落进包装内侧，窗口缩到近零），于是「`.padding(` 插在 `fillMaxWidth()` 与 clickable 之间」
 * 与「clickable 挪到链尾 padding 之后」两枚变异在 JVM 侧全部静默放行；同族那枚靠字面邻接承重的
 * `clickableAfterPadding` 禁形正则也已被实测证明数为 0（摆设）。现链头改锚「clickable 之前最近的一枚
 * `.fillMaxWidth()`」，窗口 = `fillIndex until clickableIndex`，两枚变异各配一条合成负片段自证承重，
 * 那枚摆设正则已删（判据不许两条都留着当摆设）。
 */
class SharedComponentGeometryContractTest {

    private companion object {
        const val ENTRY_ROW = "EntryRow.kt"
        const val INLINE_NOTICE = "InlineNotice.kt"
        const val ICON_BADGE = "IconBadge.kt"
    }

    // 读法（M-3，Task 12 兑现）：本类原先自带一份私有 `uiComponentSource`，与另外三份逐字相同，
    // 现已提升为共享夹具（`SourceRatchet.kt`），本文件直接调用同名顶层函数，不再持有第二份实现。

    // ---------- EntryRow.kt ----------

    /** 行最小内容高（稿 `.entry { min-height:56px }`）。connected 只钉 ≥48 下限，56 这个取值归本锚。 */
    private val rowMinHeight56 = Regex("""heightIn\(min = 56\.dp\)""")

    /** 行上下 padding（稿面 8dp）。 */
    private val rowVerticalPadding8 = Regex("""padding\(vertical = 8\.dp\)""")

    /** 行间隙（稿 `.en`/`.ic` 的 margin 12dp）。变异自证 ⑥ 的红点。 */
    private val rowGap12 = Regex("""Arrangement\.spacedBy\(12\.dp\)""")

    /** 徽标定位标签（connected 侧量 40dp 容器的唯一口，KDoc 登记的存在理由见 `EntryRow.kt`）。 */
    private val iconBadgeTestTag = Regex("""testTag\("entry_row_icon"\)""")

    /** 省略号三档（标题/副标题/尾部值各一枚；brief 的「两处」只点了标题与副标题，trailing 是同口径第三处——本锚钉剥注释后的生产实面 3）。 */
    private val maxLinesOne = Regex("""maxLines = 1""")
    private val overflowEllipsis = Regex("""overflow = TextOverflow\.Ellipsis""")

    /** 禁止形状：把省略号换成裁切（用例名 not_clipping 的源码面镜像）。 */
    private val overflowClip = Regex("""overflow = TextOverflow\.Clip""")

    /** chevron：16dp 且显式单层。锚点**按顺序承重**（size 写在 layer 之前，与 `IconBadgeContrastContractTest.strokeOnlyBeforeSize` 同理）。 */
    private val chevronStroke16 = Regex("""size = 16\.dp, layer = AppGlyphLayer\.StrokeOnly""")

    /** 发丝线宽度取值。 */
    private val hairlineToPx = Regex("""1\.dp\.toPx\(\)""")

    /**
     * 发丝线顺序锚（R-10.5）：`drawBehind { …1.dp.toPx()… }` 之后紧跟 `.padding(top = 1.dp)`——
     * 先画线再让位，线才在内容外侧；写反（padding 后 drawBehind）就把线画进内容顶边。
     * 全文件 `padding(top = 1.dp)` 剥注释后是 2 处（另一处是副标题的 1dp 间隙，稿 `.ed`），
     * 所以「恰 1 处」必须由这条带 `drawBehind` 前件顺序的形状来钉。
     */
    private val hairlineOutsideContent =
        Regex("""drawBehind\s*\{[^{}]*1\.dp\.toPx\(\)[^{}]*\}\s*\.padding\(top = 1\.dp\)""")

    /**
     * 可点面的第②条交付（批 1 终审 F1 重写；修复轮 2 的 I-1 把链头改锚到 `fillMaxWidth()`）：
     * `clickable` 排在任何 `padding` **之前**。
     *
     * 判据形状照 `WordRowSpeakerContractTest.pillOrderIndices` 的**下标比较**，不用字面文本正则定位：
     * 更早那枚锚 `fillMaxWidth\(\)\s*\.clickable\(` 把「`heightIn` 插在 `fillMaxWidth` 与 `clickable` 之间」
     * 这一**合法**写法判成不合法（数到 0 处），批 2 任何按 `.fillMaxWidth().heightIn(...).clickable(...)` 写的
     * 入口行都会假红。`clickable` 不参与测量，所以 `heightIn` 在不在它前面与可点面尺寸无关（第①条交付
     * 由 [rowMinHeight56] 单独钉）；本函数只看两件事：`clickable` 在 `fillMaxWidth()` 之后、在本条链里
     * 早于 `clickable` 的 `padding(` 不存在。
     *
     * **链头为什么改锚到「`clickable` 之前最近的一枚 `.fillMaxWidth()`」**（修复轮 2 的 I-1；这条判据一度
     * 因此空转、成了摆设）：上一版的链头取「`clickable` 之前最近的一枚 `modifier`/`Modifier` 接收者」，
     * 而 Task 19 之后行链把 clickable 裹进了 `.then(if (enabled) Modifier.clickable(…) else Modifier)`
     * （`EntryRow.kt:161-168`）——`lastIndexOf("Modifier", clickableIndex)` 命中的是**包装内侧**那枚
     * 紧贴 `.clickable(` 的 `Modifier`，链头被拽到离 clickable 只剩一个 `Modifier` 标记长度的地方，
     * 取样窗口随之缩到近零：任何插在 `fillMaxWidth()` 与 clickable 之间的 `.padding(` 都落在窗口**左侧**、
     * 再也数不到 ⇒ 「padding 插进 fillMaxWidth 与 clickable 之间」这一变异 JVM 侧不再判红（旧写法下会红）。
     * 现在窗口 = `fillIndex until clickableIndex`，即以本条链自己的 `fillMaxWidth()` 为起点，
     * 窗口内出现任何 `.padding(` 即违规；`.then(...)` 包装、`heightIn` 插位都不影响判定。
     *
     * 两条既有约束原样保留：
     *  - **取样窗口只有本条 modifier 链**（批 1 终审窄复评 N-3）：链之前别处先出现的 `.padding(`
     *    （上方另一枚 `Box`、子组件形参默认值）不得把合法链判成假红。链头取「clickable 之前**最近的**」
     *    那一枚 `fillMaxWidth()` 而不是「全文件第一枚」，同一文件里更早出现的别的链（别的盒子的
     *    `.fillMaxWidth().padding(…)`）同样污染不了窗口，比旧写法更严。
     *  - 第三个下标语义不变：链内早于 clickable 的第一枚 `padding(` 的下标；-1 = 没有（合规）。
     */
    private fun entryRowOrderIndices(source: String): Triple<Int, Int, Int> {
        val clickableIndex = source.indexOf(".clickable(")
        val fillIndex = if (clickableIndex >= 0) source.lastIndexOf(".fillMaxWidth()", clickableIndex) else -1
        val paddingBeforeClickable = if (fillIndex in 0 until clickableIndex) {
            val index = source.indexOf(".padding(", fillIndex + ".fillMaxWidth()".length)
            if (index in 0 until clickableIndex) index else -1
        } else {
            -1
        }
        return Triple(fillIndex, clickableIndex, paddingBeforeClickable)
    }

    /** [entryRowOrderIndices] 的判据本体（生产源码与合成片段共用一套口径，避免断言与自证各写一遍）。 */
    private fun clickableCoversWholeBox(source: String): Boolean {
        val (fillIndex, clickableIndex, paddingIndex) = entryRowOrderIndices(source)
        return fillIndex in 0 until clickableIndex && paddingIndex == -1
    }

    /** 断言消息用的实际下标（-1 = 缺那一枚 / 链内没有早于 clickable 的 padding），让「红在顺序上」可读而不是红在计数上。 */
    private fun entryRowOrderDebug(source: String): String {
        val (fillIndex, clickableIndex, paddingIndex) = entryRowOrderIndices(source)
        return "fillMaxWidth=$fillIndex, clickable=$clickableIndex, 链内早于clickable的padding=$paddingIndex"
    }

    /** 链上只此一枚可点源（「整行一个可点目标」的源码面镜像；少了这一条时 indexOf 只认第一枚会放过重复）。 */
    private val singleClickable = Regex("""\.clickable\(""")

    // 修复轮 2 的 I-1：**删掉了**这里原先那枚 `clickableAfterPadding` 禁形正则
    // （`heightIn\(min = 56\.dp\)\s*\.padding\(vertical = 8\.dp\)\s*\.clickable\(`）。
    // 理由（摆设判据，取证见 `t19fix2-i1-probe-decorative-regex.log`）：它靠**字面邻接**承重，而现盘行链把
    // clickable 裹在 `.then(if (enabled) Modifier.clickable(…) else Modifier)` 里，`padding` 与 `clickable`
    // 之间横着 `.then(if (enabled) Modifier`，`\s*` 吞不掉 ⇒ 对真实变异数为 0（`expected:<1> but was:<0>` 实测）。
    // 它守的「clickable 排在 heightIn/padding 之后」这一违规方向已由 [entryRowOrderIndices] 的窗口判据承重
    // （clickable 一旦挪到链尾，链尾那枚 padding 就落进 `fillIndex until clickableIndex` 窗口 ⇒ 判红），
    // 并配了两枚合成负片段自证：紧贴的旧邻接形状与 `.then(` 包装下的链尾形状各一枚。
    // 保留一条数不出任何东西的正则当护栏，比没有护栏更糟——它会让后人以为这一维有人守过。

    @Test
    fun entry_row_geometry_literals_are_pinned_in_source() {
        val source = uiComponentSource(ENTRY_ROW)
        assertEquals(1, countMatches(source, rowMinHeight56), "入口行最小高必须恰一处 56dp（稿 .entry min-height）")
        assertEquals(1, countMatches(source, rowVerticalPadding8), "入口行上下 padding 必须恰一处 8dp")
        assertEquals(1, countMatches(source, rowGap12), "入口行间隙必须恰一处 12dp（变异自证 ⑥：改 10dp 即此条红）")
        assertEquals(1, countMatches(source, iconBadgeTestTag), "entry_row_icon 标签必须恰一处（多一处 = 第二套徽标定位口径）")
        assertEquals(3, countMatches(source, maxLinesOne), "标题/副标题/尾部值各一枚 maxLines = 1，恰 3 处")
        assertEquals(3, countMatches(source, overflowEllipsis), "同一批三处必须都是 Ellipsis，恰 3 处")
        assertEquals(0, countMatches(source, overflowClip), "入口行文本禁 Clip：裁掉字形就是 not_clipping 用例名说谎")
        assertEquals(1, countMatches(source, chevronStroke16), "chevron 必须恰一处 size = 16.dp 且带 StrokeOnly（锚点按 size→layer 顺序承重）")
        assertEquals(1, countMatches(source, hairlineToPx), "发丝线宽度必须恰一处 1.dp.toPx()")
        assertEquals(1, countMatches(source, hairlineOutsideContent), "发丝线必须恰一处「drawBehind 在前、padding(top = 1.dp) 让位在后」（顺序即契约，R-10.5）")
        assertTrue(
            clickableCoversWholeBox(source),
            "可点面必须覆盖整盒：clickable 排在 fillMaxWidth() 之后、本条链里没有任何早于它的 padding（计划 :1049 的实测教训；" +
                "实际下标 ${entryRowOrderDebug(source)}；padding=-1 = 链内 clickable 之前没有 padding（合规），" +
                "padding ≥ 0 = 可点面缩回内容盒。" +
                "heightIn 在 clickable 之前**不算**违规，两条共同交付见 EntryRow.kt KDoc）",
        )
        assertEquals(1, countMatches(source, singleClickable), "入口行链上 clickable 只此一处（两处 = 第二个可点源，撞「整行一个可点目标」用例）")

        // 合成正片段：新顺序形状必须被下标判据认出来。
        val newShape = "Row(modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)" +
            ".heightIn(min = 56.dp).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp))"
        assertTrue(clickableCoversWholeBox(newShape), "合成正片段漏判：新顺序没被下标判据认出来（${entryRowOrderDebug(newShape)}）")
        // 反假红钉（批 1 终审窄复评 N-3）：**别处**先出现的 `.padding(` 不得把本判据带红。
        // 旧实现取全文件首个 `.padding(` 下标，批 2 只要在行链之前往 EntryRow.kt 里加任何别处的 padding
        // （上方另一枚 Box、子组件形参默认值）就会当场假红——判定窗口必须只有「本条 modifier 链」。
        val otherPaddingEarlier = "Box(Modifier.padding(horizontal = 4.dp))\n" + newShape
        assertTrue(
            clickableCoversWholeBox(otherPaddingEarlier),
            "反假红钉失控：同一文件里行链之前的无关 padding 把合法链判成了违规（${entryRowOrderDebug(otherPaddingEarlier)}）",
        )
        // 反假红钉（同族第二枚，修复轮 2 的 I-1 新增）：同一文件里**更早的另一条链**写了
        // `.fillMaxWidth().padding(…)` 也不许污染本条链的窗口——链头取 clickable 之前最近的那枚 fillMaxWidth。
        val earlierOtherFillChain = "Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))\n" + newShape
        assertTrue(
            clickableCoversWholeBox(earlierOtherFillChain),
            "反假红钉失控：同一文件里更早的另一条 fillMaxWidth→padding 链把合法行链判成了违规（${entryRowOrderDebug(earlierOtherFillChain)}）",
        )
        assertEquals(1, countMatches(newShape, rowGap12), "合成正片段漏判：12dp 间隙锚认不出自家形状")

        // 合成负片段一：链首丢了 `fillMaxWidth()`（可点面不再覆盖整盒宽度）——由 fillIndex 那半边判据承重。
        val legacyShape = "Row(modifier = modifier.heightIn(min = 56.dp).padding(vertical = 8.dp)" +
            ".clickable(role = Role.Button, onClick = onClick))"
        assertFalse(clickableCoversWholeBox(legacyShape), "合成负片段被放行：链首没有 fillMaxWidth() 的旧顺序竟过了下标判据（${entryRowOrderDebug(legacyShape)}）")

        // 合成负片段二：`fillMaxWidth()` 在，但 clickable 排在 `heightIn`/`padding` **之后**（字面紧贴的旧形状，
        // 即被删掉那枚禁形正则原本要抓的那一枚）——由窗口的 padding 那半边承重。
        val legacyTailOrderShape = "Row(modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)" +
            ".padding(vertical = 8.dp).clickable(role = Role.Button, onClick = onClick))"
        assertFalse(
            clickableCoversWholeBox(legacyTailOrderShape),
            "合成负片段被放行：clickable 排在 heightIn/padding 之后的旧邻接形状竟过了窗口判据——被删禁形正则的这一维必须有人守（${entryRowOrderDebug(legacyTailOrderShape)}）",
        )

        // 反假红钉（批 1 终审 F1，本项验收物）：heightIn 插在 fillMaxWidth 与 clickable 之间**同样合法**
        // ——clickable 不参与测量，可点面尺寸只由「链上有 48dp 下限」+「clickable 在任何 padding 之前」
        // 两条共同交付（先例：DialogButton 的 heightIn 在前、NotConfiguredCard 出口 heightIn 在后，
        // 两处 connected 都实测 ≥48dp）。旧锚（要求 clickable 紧跟 fillMaxWidth）在这条上判 0 处，
        // 批 2 任何按 `.fillMaxWidth().heightIn(...).clickable(...)` 写的入口行都会假红。
        val heightInFirstShape = "Row(modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)" +
            ".clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp))"
        assertTrue(
            clickableCoversWholeBox(heightInFirstShape),
            "反假红钉失控：heightIn 插在 clickable 之前的合法链被下标判据误判成不合法（${entryRowOrderDebug(heightInFirstShape)}）",
        )
        // 被删掉那枚禁形正则在这一条上的反假红腿不需要替代品：链尾那枚 `.padding(` 的下标**大于**
        // clickableIndex，天然落在 `fillIndex until clickableIndex` 窗口之外，上面的 assertTrue 已经是这一维。

        // ===== 修复轮 2（窄复评 I-1）：`.then(...)` 包装下的两种「padding 在 clickable 之前」变异 =====
        // 现盘行链把 clickable 包在 `.then(if (enabled) Modifier.clickable(…) else Modifier)` 里
        // （`EntryRow.kt:161-168`），于是「链头 = clickable 之前最近的 `modifier`/`Modifier` 接收者」这条旧锚
        // 落到 `.then(if (enabled) Modifier` 内侧那枚 `Modifier` 上，取样窗口缩到 `Modifier` 到 `.clickable(`
        // 这十几个字符 ≈ 零 ⇒ 下面两枚变异在 JVM 侧全部静默放行（本席已实测：改锚前这两条都红在「被放行」上）。
        // 窗口必须以本条链自己的 `.fillMaxWidth()` 为起点，这两枚才判得出。

        // 变异一（brief 指定的形状）：有人把 `.padding(` 插到 `fillMaxWidth()` 与 clickable 之间
        // ——可点面缩回内容盒，撞计划 :1049 的实测教训。
        val paddingInsertedAfterFill = "Row(modifier = modifier.fillMaxWidth().padding(top = 8.dp)" +
            ".then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)" +
            ".heightIn(min = 56.dp))"
        assertFalse(
            clickableCoversWholeBox(paddingInsertedAfterFill),
            "合成变异被放行：`.padding(` 插在 fillMaxWidth() 与 clickable 之间（`.then(` 包装内）竟仍算「可点面覆盖整盒」（${entryRowOrderDebug(paddingInsertedAfterFill)}）",
        )

        // 变异二：clickable 被挪到链尾（`heightIn`/`padding` 之后），但仍裹在 `.then(if (enabled) Modifier…)` 里
        // ——旧的那枚靠**字面邻接**承重的禁形正则对这一形状数为 0（本席实测留档 `t19fix2-i1-probe-decorative-regex.log`：
        // `expected:<1> but was:<0>`），因为 `.then(if (enabled) Modifier` 横在 `padding` 与 `clickable` 之间、
        // `\s*` 吞不掉；旧下标锚同样放行。真机只给 40dp 可点面（F5-03）。改锚后由窗口判据自己承重。
        val clickableAtTailInsideWrapper = "Row(modifier = modifier.fillMaxWidth().then(stateModifier).then(dividerModifier)" +
            ".heightIn(min = 56.dp).padding(vertical = 8.dp)" +
            ".then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier))"
        assertFalse(
            clickableCoversWholeBox(clickableAtTailInsideWrapper),
            "合成变异被放行：clickable 排在链尾 padding 之后（`.then(` 包装内）竟仍算合规——禁形旧正则因中间多了 .then( 永不命中，本判据必须自己承重（${entryRowOrderDebug(clickableAtTailInsideWrapper)}）",
        )

        // 合法链的正样本（现盘真实形状）：`.then(...)` 包装本身不是违规，padding 只在链尾时才合规。
        val currentChainShape = "Row(modifier = modifier.fillMaxWidth().then(stateModifier)" +
            ".then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)" +
            ".then(dividerModifier).heightIn(min = 56.dp).padding(vertical = 8.dp))"
        assertTrue(
            clickableCoversWholeBox(currentChainShape),
            "反假红钉失控：现盘这条 `.then(...)` 包装 + 链尾 padding 的合法链被判成了违规（${entryRowOrderDebug(currentChainShape)}）",
        )

        // 合成负片段：邻近取值不得互相冒名——48dp 串不得被 56dp 锚命中，10dp 串不得被 12dp 锚命中。
        assertEquals(0, countMatches("Box(Modifier.heightIn(min = 48.dp))", rowMinHeight56), "合成负片段被放行：48dp 下限串被 56dp 取值锚冒名命中")
        assertEquals(0, countMatches("Row(horizontalArrangement = Arrangement.spacedBy(10.dp))", rowGap12), "合成负片段被放行：提示条的 10dp 串被入口行 12dp 锚冒名命中")
        assertEquals(0, countMatches("Text(overflow = TextOverflow.Clip)", overflowEllipsis), "合成负片段被放行：Clip 串被 Ellipsis 锚命中")
        assertEquals(1, countMatches("Text(overflow = TextOverflow.Clip)", overflowClip), "禁形探针失控：Clip 串根本没被数出来")
        assertEquals(0, countMatches("AppGlyph(size = 16.dp)", chevronStroke16), "合成负片段被放行：丢了 StrokeOnly 层的 chevron 也算合规")
        assertEquals(0, countMatches("drawBehind { drawLine() }.padding(top = 2.dp)", hairlineOutsideContent), "合成负片段被放行：让位 2dp 竟也过发丝线顺序锚")
    }

    // ---------- InlineNotice.kt ----------

    /** 提示条 padding（稿 `.banner` 14×12dp）。变异自证 ⑦ 的红点。 */
    private val noticePadding14x12 = Regex("""padding\(horizontal = 14\.dp, vertical = 12\.dp\)""")

    /** 提示条间隙（稿 `.banner` 10dp）。 */
    private val noticeGap10 = Regex("""Arrangement\.spacedBy\(10\.dp\)""")

    /** 1dp 描边（稿 `.banner` border）。 */
    private val noticeBorder1dp = Regex("""border\(1\.dp,""")

    /** 18dp 状态字形（稿 `.banner .ic`）。 */
    private val noticeGlyph18 = Regex("""size = 18\.dp""")

    /** 48dp 触控下限——只派给动作槽（R-10.1 口径：无动作本体 ~42dp 是合规内容高）。 */
    private val actionFloor48 = Regex("""heightIn\(min = 48\.dp\)""")

    /** 圆角取装配档 18dp（`MintGlassTheme.kt:173` = 稿 `--r-card`）。 */
    private val shapesMedium = Regex("""MaterialTheme\.shapes\.medium""")

    /** 禁止形状：新组件读 20dp 旧档。 */
    private val appShapeCard = Regex("""AppShape\.Card""")

    /** 禁止形状：提示文案给 maxLines（R-10.1：裁掉就是丢信息，与入口行相反口径）。 */
    private val anyMaxLines = Regex("""maxLines""")

    @Test
    fun inline_notice_geometry_literals_are_pinned_in_source() {
        val source = uiComponentSource(INLINE_NOTICE)
        assertEquals(1, countMatches(source, noticePadding14x12), "提示条 padding 必须恰一处 14×12dp（变异自证 ⑦：vertical 改 14 即此条红）")
        assertEquals(1, countMatches(source, noticeGap10), "提示条间隙必须恰一处 10dp")
        assertEquals(1, countMatches(source, noticeBorder1dp), "提示条描边必须恰一处 border(1.dp,")
        assertEquals(1, countMatches(source, noticeGlyph18), "提示条状态字形必须恰一处 18dp")
        assertEquals(1, countMatches(source, actionFloor48), "48dp 下限必须恰一处且只落在动作槽（多一处 = 给本体也钉了水线）")
        assertEquals(1, countMatches(source, shapesMedium), "圆角必须恰一处 MaterialTheme.shapes.medium（装配档 = 稿 --r-card 18dp）")
        assertEquals(0, countMatches(source, appShapeCard), "新组件禁读 AppShape.Card（20dp 旧档）")
        assertEquals(0, countMatches(source, anyMaxLines), "提示文案不得出现 maxLines（R-10.1 相反口径：这里裁掉 = 丢信息）")

        // 合成正/负片段：padding 锚必须同时吃下两个分量——14×14（变异 ⑦ 的形状）与 12×12 都不得命中。
        val goodPadding = "Modifier.padding(horizontal = 14.dp, vertical = 12.dp)"
        assertEquals(1, countMatches(goodPadding, noticePadding14x12), "合成正片段漏判：14×12 padding 没被锚认出")
        assertEquals(0, countMatches("Modifier.padding(horizontal = 14.dp, vertical = 14.dp)", noticePadding14x12), "合成负片段被放行：vertical 漂到 14dp 也算合规（变异 ⑦ 形状）")
        assertEquals(0, countMatches("Modifier.padding(horizontal = 12.dp, vertical = 12.dp)", noticePadding14x12), "合成负片段被放行：horizontal 漂到 12dp 也算合规")
        // 跨组件冒名：入口行的 12dp 串不得被提示条 10dp 锚命中；48dp 锚不吃入口行的 56dp 串。
        assertEquals(0, countMatches("Row(horizontalArrangement = Arrangement.spacedBy(12.dp))", noticeGap10), "合成负片段被放行：入口行 12dp 串被提示条 10dp 锚冒名命中")
        assertEquals(0, countMatches("Row(Modifier.heightIn(min = 56.dp))", actionFloor48), "合成负片段被放行：入口行 56dp 串被动作槽 48dp 锚冒名命中")
        assertEquals(1, countMatches("Box(Modifier.heightIn(min = 48.dp))", actionFloor48), "合成正片段漏判：动作槽 48dp 下限没被锚认出")
        assertEquals(1, countMatches("Column { Text(modifier = Modifier.padding(top = 1.dp)) }", Regex("""padding\(top = 1\.dp\)""")), "取样壳失控：1dp padding 串本身数不出来（顺序锚另见入口行用例）")
        assertEquals(1, countMatches("Text(text = text, maxLines = 1)", anyMaxLines), "禁形探针失控：maxLines 串根本没被数出来")
    }

    // ---------- IconBadge.kt ----------

    /** 入口容器 40dp 档：两档共用同一枚外层 Box，`size(40.dp)` 在文件里只许出现一遍（R-10.2 的份数证据）。 */
    private val entryBox40 = Regex("""size\(40\.dp\)""")

    /**
     * 弱档底色配方锚（R-10.7③ / I-1）：稿 `.ic-tint` 的
     * `color-mix(in srgb, var(--d) 16%, var(--surface))` 在生产里就是这一枚 `lerp`；
     * `\s*` 只容忍换行缩进，不容忍参数换位（换位的写法该由 `IconBadgeContrastContractTest` 的复算先红）。
     */
    private val weakToneRecipe = Regex("""lerp\(colors\.surface,\s*palette\.deep,\s*0\.16f\)""")

    /** 禁形一（brief 原文正则）：混色基色被换成 `colors.background`——稿混的是 `--surface`，不是 `--background`。 */
    private val weakToneOnBackground = Regex("""lerp\(colors\.background,""")

    /**
     * 禁形二：同一枚 lerp 的系数漂到 0.16 之外（`0.30f`/`0.40f`/`0.24f` 全判红）。
     * 窄口径靠两点收：前件必须是 `colors.surface` + `palette.deep` 这两枚实参，负向预查 `(?!16f)`
     * 只放行那个已裁决的系数——所以 `GRADIENT_START_FRACTION = 0.35`、玻璃高光 alpha、
     * `InlineNotice` 的 `lerp(set.surface, set.errorFill, 0.12f)` 都不在本锚视野里。
     */
    private val weakToneCoefficientDrift = Regex("""lerp\(colors\.surface,\s*palette\.deep,\s*0\.(?!16f)\d+f\)""")

    @Test
    fun icon_badge_entry_container_is_one_shared_40dp_box() {
        val source = uiComponentSource(ICON_BADGE)
        assertEquals(
            1,
            countMatches(source, entryBox40),
            "入口容器必须只有一枚 size(40.dp)（两档共用；多一处 = 弱档另起容器，connected 严格相等那两条会一起红）",
        )
        // 合成片段：取值漂移（40→48/36）与行内 30dp 档都不得冒名命中 40dp 锚。
        assertEquals(1, countMatches("Box(modifier = Modifier.size(40.dp).clip(shape))", entryBox40), "合成正片段漏判：40dp 容器锚认不出自家形状")
        assertEquals(0, countMatches("Box(modifier = Modifier.size(48.dp))", entryBox40), "合成负片段被放行：容器漂到 48dp 也算 40dp 档（connected 只钉下限抓不住）")
        assertEquals(0, countMatches("Box(modifier = Modifier.size(30.dp))", entryBox40), "合成负片段被放行：行内 30dp 档被入口 40dp 锚冒名命中")
    }

    /**
     * 弱档（稿 `.ic-tint`）底色的**配方**在生产源码里必须就是那张对比表所依据的那一枚表达式。
     *
     * 这条是给 `IconBadgeContrastContractTest.both_entry_tones_clear_3_to_1_in_both_theme_sets`
     * 那张「7 域 × 2 档 × 2 主题 = 28 组合」表**补的生产绑定腿**（R-10.7③，修复轮 2 的 I-1）：
     * 那张表在测试端自己按 `mixHex(surface, deep, 0.16)` 现算底色，读的**不是**生产
     * `IconBadge.kt` 的 `lerp(colors.surface, palette.deep, 0.16f)` —— 于是表算的是**理想配方**：
     * 生产把 `colors.surface` 写成 `colors.background`、或把系数改成 `0.30f/0.40f`，那 26 条契约全绿
     * （控制者补算：漂移后深套最低仍在 3:1 以上，所以这是「偏离稿面配方」而非当场破线，故 I-1 不列
     * Critical——但「配方 = 稿面那一个」这件事得有人守，就是本锚）。
     * 与同族几何锚一个口径：先剥注释再匹配、配合成正/负片段自证正则承重。
     * 变异自证 ⑪：生产 `0.16f`→`0.30f` → 本用例正锚红（恰 1 处判成 0 处）。
     */
    @Test
    fun icon_badge_weak_tone_backdrop_recipe_is_pinned_to_source() {
        val source = uiComponentSource(ICON_BADGE)
        assertEquals(
            1,
            countMatches(source, weakToneRecipe),
            "弱档底色必须恰一处 lerp(colors.surface, palette.deep, 0.16f)（稿 .ic-tint 的 color-mix 16% 配方）",
        )
        assertEquals(
            0,
            countMatches(source, weakToneOnBackground),
            "禁形：混色基色不得换成 colors.background（稿 .ic-tint 混的是 --surface，不是 --background）",
        )
        assertEquals(
            0,
            countMatches(source, weakToneCoefficientDrift),
            "禁形：同一枚 lerp 的系数不得漂到 0.16 之外（0.30f/0.40f 都算偏离稿面配方，28 组合表只对理想配方成立）",
        )

        // 合成正片段：生产那枚写法（含 background(...) 外壳）必须被正锚认出、且不被两枚禁形锚误伤。
        val goodRecipe = "Modifier.background(lerp(colors.surface, palette.deep, 0.16f))"
        assertEquals(1, countMatches(goodRecipe, weakToneRecipe), "合成正片段漏判：16% 混 surface 的配方没被锚认出")
        assertEquals(0, countMatches(goodRecipe, weakToneOnBackground), "合成正片段越界：surface 配方被 background 禁形锚误判")
        assertEquals(0, countMatches(goodRecipe, weakToneCoefficientDrift), "合成正片段越界：0.16f 被系数漂移禁形锚误判")

        // 合成负片段：三种漂移形状各必须被对应禁形锚命中、且不被正锚放行。
        val backgroundDrift = "Modifier.background(lerp(colors.background, palette.deep, 0.16f))"
        assertEquals(1, countMatches(backgroundDrift, weakToneOnBackground), "合成负片段失控：background 换色没被禁形锚数出来")
        assertEquals(0, countMatches(backgroundDrift, weakToneRecipe), "合成负片段被放行：colors.background 换色竟也过 16% 配方锚")
        val coefficientDrift = "Modifier.background(lerp(colors.surface, palette.deep, 0.30f))"
        assertEquals(1, countMatches(coefficientDrift, weakToneCoefficientDrift), "合成负片段失控：0.30f 没被系数漂移锚数出来（变异 ⑪ 的形状）")
        assertEquals(0, countMatches(coefficientDrift, weakToneRecipe), "合成负片段被放行：0.30f 竟也过 16% 配方锚")
        assertEquals(
            1,
            countMatches("Modifier.background(lerp(colors.surface, palette.deep, 0.40f))", weakToneCoefficientDrift),
            "合成负片段失控：0.40f 没被系数漂移锚数出来",
        )
        // 跨文件冒名：InlineNotice 的 12% 混底（set.surface / set.errorFill，另一套取色口径）
        // 既不得命中入口弱档配方锚，也不得被系数漂移锚误判成「同一枚 lerp 漂了」。
        val noticeBackdrop = "backdrop = lerp(set.surface, set.errorFill, 0.12f)"
        assertEquals(0, countMatches(noticeBackdrop, weakToneRecipe), "合成负片段被放行：提示条 12% 混底被入口弱档配方锚冒名命中")
        assertEquals(0, countMatches(noticeBackdrop, weakToneCoefficientDrift), "合成负片段被放行：提示条 12% 混底被系数漂移禁形锚冒名命中（本锚只管 palette.deep 那枚）")
        // 邻近字段冒名：GRADIENT_START_FRACTION = 0.35（Double 常量、无 f 后缀、不在 lerp 调用位上）
        // 不得被任何一枚配方锚命中——禁形锚只管同一枚 lerp(colors.surface, palette.deep, …) 里的系数。
        assertEquals(0, countMatches("private const val GRADIENT_START_FRACTION = 0.35", weakToneCoefficientDrift), "合成负片段被放行：禁形锚宽到误伤了渐变亮端的 0.35 常量")
    }
}
