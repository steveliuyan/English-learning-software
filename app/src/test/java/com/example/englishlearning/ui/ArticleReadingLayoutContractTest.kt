package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 3 Task 3：文章阅读屏的新骨架契约（spec §6 逐屏表「文章阅读」行，本批最大一枚）。
 *
 * 这屏原样是批 1 之前的形态：自写「返回 / 发音」两枚 M3 实底 `Button`（返回位连 `testTag` 都没有）、
 * `headlineSmall` 标题、整屏一个 `Column + verticalScroll`、「完成阅读」埋在流末尾、
 * 覆盖词用独立 `GlassDialog` 整块挡正文、「全文对照」与「显示译文」两个互不联动的控件、
 * 来源块四行平铺、十四处静态薄荷桥 + 八处 M3 默认排印。
 *
 * 七条判据分两档颜色：
 *  - 1–6 是**本批要做的六件事**，RED 阶段各自必须红；
 *  - 7 是**防回潮**（现状本来就绿）：正文墨绑定与滚动位置回传是两条被别的契约逐字钉住的判据，
 *    换骨架时最可能的破坏不是删掉而是「顺手重排」把字面改掉。
 *
 * Task 22-K（#13 / #14 残留项）在 7 之前补了三块：三档排印逐值（`.prose` / `.prose-zh` / `.article-title`
 * 接到 `AppType` 的对应 token）、页脚两枚并列出口（「双语」+「完成阅读」）、对照态「只看中文」与译文提醒。
 * 原第 7 节顺延为第 9 节，内容未动。
 *
 * 取窗一律按符号深度配对（注释先剥），锚点改名即 `fail`，不会静默空转。
 */
class ArticleReadingLayoutContractTest {

    private val source: String by lazy { stripComments(uiScreenSource("ArticleReadingScreen.kt")) }

    /**
     * 七枚静态薄荷桥。本文件 RED 前有 14 处读点（`MintPrimaryDark` 7 / `MintSurface` 4 /
     * `MintTextMuted` 2 / `MintPrimary` 1 / `MintBackground` 1），换装后必须全归零：
     * 只许组合期读 `appColors()`。
     */
    private val mintBridges = listOf(
        "MintPrimaryDark",
        "MintPrimary",
        "MintTextMuted",
        "MintSurface",
        "MintOutline",
        "MintTint",
        "MintBackground",
    )

    // ---------- 1. 页头 ----------

    /**
     * 二级页的返回位归共享页头，发音按钮进 `action` 槽。
     *
     * 原来自写的是两枚 M3 实底 `Button`（「返回」没有 `testTag`、也没有 `contentDescription`，
     * TalkBack 只念得到按钮文字；「发音」那枚是本屏唯一的可及名来源）。页头接管后本文件
     * `MaterialTheme.typography` 归零、`Text("返回")` 归零——后者也是 `SharedComponentUniquenessRatchetTest`
     * 页头族能摘掉本文件的前提。
     *
     * 页头标题取**屏名**而不是文章标题：正文标题已经由 `article_title` 留在正文侧
     * （connected 侧按这个 tag 取，`article_title` 不得消失），页头只承载返回与发音——
     * 两处都渲染同一句标题就是一份信息两个真值，且长标题会把页头撑成两行而正文再重复一遍。
     */
    @Test
    fun `the shared header owns the back slot and carries the pronunciation action`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("AppScreenHeader("))),
            "页头必须恰好一枚共享 `AppScreenHeader(`：二级页的返回位与屏名只允许这一套",
        )
        val header = callSpan(source, "AppScreenHeader(")
        assertTrue(header.isNotEmpty(), "取不到 `AppScreenHeader(` 调用，判据会空转")
        assertTrue(
            "onBack = onBack" in header,
            "页头必须直接接屏的 `onBack`：自己再包一层 lambda 会把返回通道变成第二个真值",
        )
        assertTrue(
            "article_pronunciation" in header,
            "发音按钮必须落在页头的 `action` 槽调用窗口内（返回位 + 发音是这一行页头的全部内容）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("""Text("返回")"""))),
            "自写的「返回」实底按钮必须消失：返回位归页头（固定 `contentDescription = \"返回\"`）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "屏侧不得读 M3 默认排印：六档 `AppType` 是唯一入口（spec §4.2）",
        )
    }

    // ---------- 2. 吸底主操作 ----------

    /**
     * 「完成阅读」从流末尾提到吸底操作条。
     *
     * 原来它排在正文、未覆盖词之后：文章越长越点不到，而它恰恰是本屏**唯一的主操作**。
     * 新骨架把主 CTA 放进页头与正文之外的第三条带（顶部发丝线 + 页底墨），正文滚动面在它上面，
     * 滚动时不动。
     */
    @Test
    fun `the complete action sits in a sticky bar under the scrolling body`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""testTag("article_complete_action_bar")"""))),
            "吸底操作条必须恰一枚 `article_complete_action_bar`",
        )
        val barAt = source.indexOf("""testTag("article_complete_action_bar")""")
        val hairlineAt = source.indexOf("hairlineTop(")
        val ctaAt = source.indexOf("""testTag("article_complete_reading")""")
        val scrollAt = source.indexOf("verticalScroll(scrollState)")
        assertTrue(barAt > 0 && ctaAt > barAt, "主 CTA `article_complete_reading` 必须落在吸底操作条之内（实测条 $barAt / CTA $ctaAt）")
        assertTrue(
            hairlineAt in barAt until ctaAt,
            "吸底条必须用共享 `hairlineTop(` 画顶部分隔线，且线在按钮之前（实测线 $hairlineAt / 条 $barAt / CTA $ctaAt）",
        )
        assertTrue(
            scrollAt in 1 until barAt,
            "正文滚动容器 `verticalScroll(scrollState)` 必须排在吸底条**之前**：反了就是把整屏连操作条一起滚",
        )
    }

    // ---------- 3. 覆盖词面板 ----------

    /**
     * 覆盖词从「整块挡正文的对话框」改成底部面板。
     *
     * `GlassDialog` 是居中模态：进文第一帧就把正文挡住，用户要先点「我知道了」才看得到文章。
     * 底部面板从下沿升起、可下滑收起，正文仍在上半屏；内层的 `heightIn(max = 360.dp)` 二次滚动
     * 也随之取消（面板自己有滚动面，两把滚动轴会互相不知道）。
     *
     * 四枚 tag 与身份字面「本文覆盖你已背的」逐字保留：connected 侧按它们取节点，
     * `GlassPanelInkPairingContractTest` 按身份字面认这枚面板。
     */
    @Test
    fun `the coverage panel becomes a bottom sheet instead of a blocking dialog`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("GlassDialog("))),
            "覆盖词不得再走 `GlassDialog(`：居中模态在进文第一帧就把正文整块挡住",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("ModalBottomSheet("))),
            "覆盖词面板必须恰一枚 `ModalBottomSheet(`（可下滑收起的底部面板）",
        )
        val sheetAt = source.indexOf("ModalBottomSheet(")
        assertTrue(
            source.indexOf("""testTag("coverage_popup")""") > sheetAt,
            "`coverage_popup` 必须挂在底部面板之内（面板自绘容器，锚点不许留在外面）",
        )
        assertTrue(
            "本文覆盖你已背的" in source,
            "身份字面「本文覆盖你已背的」必须逐字保留：`GlassPanelInkPairingContractTest` 按它认这枚面板",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("heightIn(max = 360.dp)"))),
            "面板内不得再套 `heightIn(max = 360.dp)` 的二次滚动：面板自己有滚动面，两把轴会互相不知道",
        )
        // 容器与前景同源取墨（原口径由 `GlassPanelInkPairingContractTest` 钉在 `GlassDialog` 族上；
        // 本屏退出该族后同一口径在这里接手，**不放宽**）：容器锚必须是 `appColors().surface` 的
        // 直接取值（`.copy` / 提读成局部变量都不算），面板窗口内不得出现任何静态墨。
        val sheet = callSpan(source, "ModalBottomSheet(")
        assertTrue(sheet.isNotEmpty(), "取不到 `ModalBottomSheet(` 调用段，配墨判据会空转")
        assertTrue(
            "containerColor = appColors().surface" in sheet,
            "面板容器锚必须是 `containerColor = appColors().surface`（直读，不许 .copy 或提读换形）",
        )
        assertEquals(
            0,
            countMatches(sheet, Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")),
            "面板窗口内不得出现静态墨读点（AppPalette / DomainColors）：静态浅套墨压深套面板即黑压黑",
        )
        // 「每一处前景都显式给墨」按**配平**判而不是拍一个数字：面板里每一处 `style = AppType.`
        // 都必须配一枚 `color = appColors().`（`AppType` 只给字号与字重、不带颜色，漏给就落下
        // 容器色）。数字会随面板加删行漂移，配平不会。
        val styleSites = countMatches(sheet, Regex(Regex.escape("style = AppType.")))
        val inkSites = countMatches(sheet, Regex(Regex.escape("color = appColors().")))
        assertTrue(styleSites >= 3, "面板里应至少有三处字号点（标题 / 关闭 / 词表），实测 $styleSites")
        assertEquals(
            styleSites,
            inkSites,
            "面板内 $styleSites 处字号点必须配 $styleSites 处显式 `appColors()` 墨，实测 $inkSites 处：" +
                "只借 `AppType` 会连着容器色一起落下，深套面板上现原形",
        )
    }

    // ---------- 4. 译文控件合并 ----------

    /**
     * 「全文对照」折叠进「双语 + 译文已展开」，译文开关与模式 chip 同一行。
     *
     * 现状是两个互不联动的控件：模式 chip 只改偏好（`FULL_TRANSLATION` 只做初始展开），
     * 「显示译文」按钮只翻 `translationExpanded`——用户想看全译得点两处。VM 侧零改动：
     * 屏侧把第三档折叠成「双语 + 已展开」的呈现等价，chip 只发前两档。
     */
    @Test
    fun `the translation toggle joins the mode chip row and the third mode is folded away`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("article_mode_full_translation"))),
            "第三枚模式 chip（`article_mode_full_translation`）必须撤掉：它折叠成「双语 + 译文已展开」，不再有自己的按钮",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""testTag("article_translation_toggle")"""))),
            "译文开关必须恰一处 `article_translation_toggle`",
        )
        // 取样锚用**带引号的字面量**而不是 `testTag("…")`：两枚 chip 的 tag 是按位置传给
        // `ModeChip(label, mode, tag, …)` 的，`testTag(tag)` 在组件体内，按 `testTag(` 找会落空。
        // 引号字面量既定位得到，也正好是「这一屏只声明过一次这个 tag」的同一份证据。
        val chipAt = source.indexOf("\"article_mode_bilingual\"")
        val toggleAt = source.indexOf("\"article_translation_toggle\"")
        assertTrue(chipAt in 1 until toggleAt, "译文开关必须排在「双语」chip 之后（实测 chip $chipAt / 开关 $toggleAt）")
        val between = source.substring(chipAt, toggleAt)
        assertTrue(
            "SectionSpacing" !in between && "Spacer(Modifier.height(" !in between,
            "译文开关必须与模式 chip 同一行同一容器：中间夹段落间距或高度占位就是把它们拆回两处",
        )
        assertTrue(
            between.length < 400,
            "译文开关与「双语」chip 的字符距离必须 < 400（实测 ${between.length}）：距离一长就说明中间隔了别的区块",
        )
    }

    // ---------- 5. 来源块两行 ----------

    /**
     * 来源块四行收成两行：站名 + 行尾域名 / 链接标签 + URL。
     *
     * 这是**去重而不是删信息**：域名是授权署名（`attributionText`）、URL 是「原文链接（不会自动打开）」
     * 这条安全能力的唯一可见处，两者都不能删。收法是把四枚 tag 归到两条 `Row` 里，
     * 且每枚 tag 全文件恰一处——三分支各写一遍 `article_source_name` 会让 connected 侧的
     * `onNodeWithTag` 撞多个节点。
     */
    @Test
    fun `the source block collapses to two rows without dropping attribution or the url`() {
        listOf(
            "article_source_name",
            "article_attribution",
            "article_source_url_label",
            "article_source_link",
        ).forEach { anchor ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$anchor\""))),
                "来源块锚「$anchor」必须恰一处字面量：三分支各写一遍会让 connected 侧按 tag 取到多个节点",
            )
        }
        val nameRow = callSpanContaining(source, "Row(", """testTag("article_source_name")""")
        assertTrue(nameRow.isNotEmpty(), "取不到承载 `article_source_name` 的 `Row(`，判据会空转")
        assertTrue(
            "article_attribution" in nameRow,
            "站名与行尾域名必须在同一行（`article_source_name` + `article_attribution`）",
        )
        val urlRow = callSpanContaining(source, "Row(", """testTag("article_source_url_label")""")
        assertTrue(urlRow.isNotEmpty(), "取不到承载 `article_source_url_label` 的 `Row(`，判据会空转")
        assertTrue(
            "article_source_link" in urlRow,
            "链接标签与 URL 必须在同一行（`article_source_url_label` + `article_source_link`）",
        )
    }

    // ---------- 6. 令牌 ----------

    /** 七枚静态薄荷桥归零 + 屏侧不自造圆角。 */
    @Test
    fun `the mint bridge stays retired and no hand rolled shape survives`() {
        mintBridges.forEach { token ->
            assertEquals(
                0,
                countMatches(source, Regex(token)),
                "静态薄荷桥 `$token` 必须归零：换装后只许组合期读 `appColors()`（`DarkTokenConsumptionRatchetTest` 同口径）",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("Color(0xFF17342A)"))),
            "正文旧墨裸 hex 必须归零（`ReadingHomeLayoutContractTest` 同钉）",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("appColors()"))) >= 3,
            "屏侧必须消费 `appColors()`（深色套才跟着换）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("RoundedCornerShape("))),
            "屏侧不得自造圆角：20 / 14 / 12 三档由 `AppShape` 与三档容器承载（spec §4.1）",
        )
    }

    // ---------- 7. 原型逐值（Task 22-K：#13 / #14 的残留项） ----------

    /**
     * 原型 `sReadArticle` 的三档排印逐值搬：
     *
     *  - `.prose`：衬线 17.5px / 行高 1.72 → `AppType.Prose`（17.5sp / 30sp），旧实现是 15sp 正文 + 手写 22.5sp 行高；
     *  - `.prose-zh`：无衬线 13px / 1.75 → `AppType.ProseZh`（13sp / 23sp）；
     *  - `.article-title`：衬线 30px → `AppType.ArticleTitle`（30sp / 35sp），旧实现是 24sp 的 `Headline`。
     *
     * 三档 token 本来就在主题里（`AppType.kt` 的 `ProseSpec` / `ProseZhSpec` / `ArticleTitleSpec` 逐值取自
     * 这张样式表），本轮只是把屏侧接到它们上面——不是新字号，也不是手写 `fontSize`。
     */
    @Test
    fun `the prose tiers come from the serif tokens with the prototype sizes`() {
        assertTrue(
            "style = AppType.Prose.copy(color = appColors().text1)" in source,
            "英文正文取 `AppType.Prose`（17.5sp 衬线 / 30sp 行高，原型 `.prose` 的逐值档）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("lineHeight = 22.5.sp"))),
            "旧「15sp 正文 + 手写 22.5sp 行高」必须退役：行高已由 `AppType.Prose` 承担，两处并存就是第二套行距",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("style = AppType.ProseZh"))) == 1,
            "整段译文取 `AppType.ProseZh`（13sp / 23sp，原型 `.prose-zh`）",
        )
        assertTrue(
            "style = AppType.ArticleTitle" in source,
            "正文标题取 `AppType.ArticleTitle`（30sp 衬线，原型 `.article-title`）",
        )
    }

    /**
     * 原型 `.foot` 是**两枚并列**：`btn-secondary`「双语」+ `btn-primary`「读完了」。
     *
     * 本仓的对照态是同一支阅读屏的呈现（Task 21-D 裁决不新开页），所以页脚第二枚的落点是
     * 「切到双语 + 展开译文」而不是跳页；没有译文时整枚不画（点了没反应等于死控件）。
     * 主操作仍是「完成阅读」，两枚各占一半宽，主次同原型。
     */
    @Test
    fun `the sticky footer pairs the prototype bilingual and finish exits`() {
        val bar = bodyOf("private fun ArticleCompleteBar(")
        assertTrue(bar.isNotEmpty(), "取不到 `ArticleCompleteBar(` 本体：判据会空转")
        assertTrue(
            "\"article_open_bilingual\"" in bar,
            "页脚第二枚「双语」必须带锚 `article_open_bilingual`（connected 侧按它取样）",
        )
        assertTrue("双语" in bar, "第二枚文案逐字取原型「双语」")
        assertTrue(
            "weight(1f)" in bar,
            "两枚并列：原型 `.foot` 的 `flex:1 1 0`，主次各占一半",
        )
    }

    // ---------- 8. 对照态（Task 22-K：#14 的残留项） ----------

    /**
     * 原型 `sReadTranslate` 的 `bar-action`「只看中文」：本仓不新开页（Task 21-D 裁决），
     * 把它落成模式行上的第三枚文字动作 `article_chinese_only_toggle`。
     *
     * 形状钉三条：①点开自动展开译文（否则屏幕上会只剩标题）；②中文态下英文正文 / 未覆盖词 /
     * 覆盖入口 / 标注行都不组合（原型对照页只有英文与中文两段，没有这些英文侧控件）；
     * ③切回「英文优先 / 双语」即退出中文态（模式 chip 是真状态，不能被一个隐藏开关盖住）。
     */
    @Test
    fun `the reader offers the prototype chinese only action and hides the english side`() {
        val screen = bodyOf("fun ArticleReadingScreen(")
        assertTrue(screen.isNotEmpty(), "取不到 `ArticleReadingScreen(` 本体：判据会空转")
        assertTrue("\"article_chinese_only_toggle\"" in source, "「只看中文」动作必须带锚 `article_chinese_only_toggle`")
        assertTrue("只看中文" in source, "文案逐字取原型 `bar-action`「只看中文」")
        assertTrue("看回英文" in source, "同一枚在中文态要把返回路径说出来：文案翻成「看回英文」")
        assertTrue(
            "if (!chineseOnly)" in screen,
            "英文侧（正文 / 未覆盖词 / 覆盖入口 / 标注行）必须在中文态下整块不组合",
        )
        assertTrue(
            "onToggleTranslation()" in screen,
            "点「只看中文」且在译文折叠时，必须先展开译文，否则中文态屏幕是空的",
        )
        assertTrue(
            "chineseOnly = false" in screen,
            "切模式 chip 必须退出中文态：模式是唯一真状态，不许被隐藏开关盖住",
        )
    }

    /**
     * 原型对照页末尾的 `notice(…, 'info', 'warning')`：「译文由本机 AI 配置生成，仅作辅助理解；
     * 正式引用请以英文原文为准。」
     *
     * 「本机 AI 配置生成」只对 AI 生成的文章成立：导入的文章译文随包进来、外刊抓取没有译文。
     * 所以这句按 `ArticleSource` 分三支写，落在译文之后、只在译文可见时出现（隐藏状态下提醒没有对象）。
     * 原型那枚 icon 是 warning，但语气档是 info——共享件只有 Error / Info / Success 三档，取 Info。
     */
    @Test
    fun `the translation carries the prototype caution for the source it actually has`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"article_translation_caution\""))),
            "译文提醒必须恰一处（`article_translation_caution`）",
        )
        assertTrue(
            "仅作辅助理解；正式引用请以英文原文为准。" in source,
            "后半句逐字取原型：这是「别拿译文当原文」的唯一一句人话",
        )
        assertTrue("译文由你配置的 AI 服务生成" in source, "AI 分支按真实来源改写成「你配置的 AI 服务」")
        assertTrue("译文随文章一起导入" in source, "导入分支单列一句：原型那句对导入文章不成立")
        assertTrue("InlineNotice(" in source, "提醒走共享 `InlineNotice`，屏侧不手搓提示条")
        val cautionAt = source.indexOf("article_translation_caution")
        val translationAt = source.indexOf("\"article_translation\"")
        assertTrue(translationAt > 0 && translationAt < cautionAt, "提醒必须排在译文块之后")
    }

    // ---------- 9. 既有锚点（防回潮） ----------

    /**
     * 正文墨绑定与滚动位置回传**逐字**保留。
     *
     * 两条都被别的契约钉着：`ReadingHomeLayoutContractTest` 按精确子串钉正文那一行（两套 AA 复算的取样形），
     * `SearchCloseReadingStateRetentionTest` 按逐字钉 `rememberScrollState(articleScrollPosition)` 与
     * `snapshotFlow { scrollState.value }.collect(onArticleScrollPositionChange)`。
     * 换骨架时最可能的破坏是「顺手重排」把这三行改掉——那会让两条既有判据在别处变红却指不到本屏。
     */
    @Test
    fun `the reading body keeps its ink binding and scroll handoff verbatim`() {
        // 期望值变化（Body → Prose），不是放宽：Task 22-K 按原型 `.prose` 把正文字档换成 17.5sp 衬线，
        // 「显式 `appColors().text1` 配平」这个形状本身一字未动，两套 AA 复算的取样面也不变。
        assertTrue(
            "style = AppType.Prose.copy(color = appColors().text1)" in source,
            "英文正文墨必须逐字保留 `style = AppType.Prose.copy(color = appColors().text1)`（R-22.3 配平形状）",
        )
        assertTrue(
            "rememberScrollState(articleScrollPosition)" in source,
            "滚动状态必须仍用传入的 `articleScrollPosition` 初始化（搜索关闭后位置不丢）",
        )
        assertTrue(
            "snapshotFlow { scrollState.value }.collect(onArticleScrollPositionChange)" in source,
            "滚动变化必须仍持续回传（查词期间的滚动在关闭搜索后可恢复）",
        )
        listOf(
            "article_reading_screen",
            "article_pronunciation",
            "article_title",
            "article_mode_english_first",
            "article_mode_bilingual",
            "article_marks_row",
            "article_marks_switch",
            "article_complete_reading",
            "article_status_live_region",
        ).forEach { anchor ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$anchor\""))),
                "锚「$anchor」必须恰一处字面量：这是 connected 侧与既有语义契约的取样点，复制一份即撞多节点",
            )
        }
    }

    // ---------- 取样助手 ----------

    /**
     * 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。
     *
     * 函数体左花括号取**括号深度为 0** 的那一枚：默认参数里可以带 lambda 空体
     * （`onOpenCard: (WordCard) -> Unit = {}`），照「签名之后第一枚 `{`」配对会取到那个 `{}`。
     */
    private fun bodyOf(signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        var parenDepth = 0
        for (index in start until source.length) {
            when (source[index]) {
                '(' -> parenDepth++
                ')' -> parenDepth--
                '{' -> if (parenDepth == 0) return balancedFrom(index)
            }
        }
        return ""
    }

    /**
     * 取一次调用的完整段（`anchor` 含左括号，如 `"BareRow("`）；取不到返回空串。
     *
     * 段必须**连尾随 lambda 一起**取：`TextButton(onClick = …) { Text("收起译文") }`
     * 的实参括号在 lambda 之前就闭合了，只取括号段会对着「没有内容」的调用判红（批 3 Task 2 踩过）。
     */
    private fun callSpan(text: String, anchor: String): String {
        val start = text.indexOf(anchor)
        if (start < 0) return ""
        return spanAt(text, start + anchor.length - 1)
    }

    /**
     * 取**含 `needle` 的那一枚**调用段：同一文件里 `Row(` 有多处，
     * 只取第一枚会对着别的行判红/判绿（取样面错位），所以按需要点名的 tag 反查。
     */
    private fun callSpanContaining(text: String, anchor: String, needle: String): String {
        var from = 0
        while (true) {
            val start = text.indexOf(anchor, from)
            if (start < 0) return ""
            val span = spanAt(text, start + anchor.length - 1)
            if (needle in span) return span
            if (span.isEmpty()) return ""
            from = start + 1
        }
    }

    /** 从 `open`（指向左括号）起配对括号；随后若紧跟 `{ … }`，把尾随 lambda 一并纳入段。 */
    private fun spanAt(text: String, open: Int): String {
        if (text.getOrNull(open) != '(') return ""
        var depth = 0
        var end = -1
        for (index in open until text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        end = index
                        break
                    }
                }
            }
        }
        if (end < 0) return ""
        var cursor = end + 1
        while (cursor < text.length && text[cursor].isWhitespace()) cursor++
        if (text.getOrNull(cursor) == '{') {
            val lambdaEnd = braceEnd(text, cursor)
            if (lambdaEnd > 0) return text.substring(open, lambdaEnd + 1)
        }
        return text.substring(open, end + 1)
    }

    /** 从左花括号起按深度配对，返回右花括号下标；取不到返回 -1。 */
    private fun braceEnd(text: String, open: Int): Int {
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    /** 从左花括号起按深度配对，返回去掉外层花括号的段。 */
    private fun balancedFrom(open: Int): String {
        if (source.getOrNull(open) != '{') return ""
        val end = braceEnd(source, open)
        return if (end < 0) "" else source.substring(open + 1, end)
    }
}
