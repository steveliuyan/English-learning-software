package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.DomainLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 24（裁决⑤ 阅读主页版面）的 JVM 源码契约面。
 *
 * 三族判据，各自钉住 brief §二 的一件：
 *  1. [the_daily-article feed is no longer inlined on the reading home]：Feed 从 `ReadingAccessScreen` 内联区
 *     挪进独立二级层（新屏文件 `ReadingFeedScreen.kt`），宿主 `AppScreen.kt` 必须真的持有那层的
 *     状态位、`overlayOpen` 追加项与 `BackHandler`——只建屏不接线等于把 Feed 删了。
 *  2. [the outbound confirmation goes through the shared component]：本屏私有 `OutboundConfirmationDialog`
 *     退役，改调共享 `OutboundConfirmDialog`；同时钉住「共享件在生产树上不是摆设」（计数 ≥ 1），
 *     以及屏侧锚 `outbound_confirmation_text` 不许在接线时静默消失。
 *  3. [generation parameters are behind an expander]：类型与长度偏好不再常驻主页，必须由一枚可展开开关承重。
 *
 * 判据一律**符号锚定**，不锚行号（版面一改行号就漂，注释却看起来仍然权威）。
 * 亮度复算用包级共享夹具 [luminance]/[ratio]（`SourceRatchet.kt`），不在此另写第二份公式、
 * 也不把 hex 抄进断言字面量——断言读的是 `AppColorLight` / `AppColorDark` 的**活符号**。
 *
 * 第四族 [the_home_leads_with_a_today_article_card_and_a_single_more_group] 是版式重做批 1 Task 7
 * （spec §3.4）：页头 + 今日文章卡 + 单个「更多」组，锁定态一句说明替代四枚类型按钮。
 */
class ReadingHomeLayoutContractTest {

    // 逐枚惰性读源：四族判据各红各的，缺哪个文件就只挡得住点它的那条，
    // 不让一枚缺席把另外三条也拖成同一条消息（红样要能指认「哪一件还没做」）。
    private val home: String by lazy { uiScreenSource("ReadingAccessScreen.kt") }
    private val host: String by lazy { uiScreenSource("AppScreen.kt") }
    private val feedScreen: String by lazy { uiScreenSource("ReadingFeedScreen.kt") }
    private val readingScreen: String by lazy { uiScreenSource("ArticleReadingScreen.kt") }

    // ---------- 1. Feed 独立二级层 ----------

    @Test
    fun `the daily article feed is no longer inlined on the reading home`() {
        assertFalse(
            "FeedSection(" in home,
            "阅读主页源码里仍出现 `FeedSection(`：裁决⑤ 第二件要求外刊 Feed 离开主页内联区，" +
                "否则 Feed 一旦 Ready 就把主任务挤出首屏",
        )
        assertFalse(
            "onFetchFeedItem" in home,
            "阅读主页不应再持有 Feed 条目回调：抓取动作随 Feed 一起归二级层（主页只留通往该层的导航）",
        )
        // 二级层本体：状态锚与条目锚逐枚点名，缺任一枚 = 状态族被顺手裁掉。
        // `feed_idle` 是修复轮 1 F-5② 的文案级兜底锚（宿主非 Ready 时把本层停在 Idle，
        // 旧的 `Idle -> Unit` = 一整页空白）；把它记进台账，防后续席把这行改回 `Unit`。
        listOf(
            "feed_idle",
            "feed_loading",
            "feed_empty",
            "feed_error",
            "feed_fetch_failed",
            "feed_source",
            "feed_item_\$index",
        ).forEach { anchor ->
            assertTrue(
                anchor in feedScreen,
                "Feed 二级层缺锚「$anchor」：四枚状态与条目列表必须原样搬过去，不得改取源文案与来源署名口径",
            )
        }
        assertTrue(
            "来源：\${feed.sourceDisplayName}" in feedScreen,
            "「来源：\${feed.sourceDisplayName}」是来源署名的权威口径，搬家时不许改写",
        )
        // 宿主四枚站点：状态位 + overlayOpen 追加 + BackHandler + 挂载分支（照抄 showReadingHistory 那一套）。
        assertTrue(
            "var showReadingFeed by rememberSaveable" in host,
            "宿主缺 Feed 二级层状态位 `showReadingFeed`：屏建了但没人挂，等于把 Feed 删了",
        )
        assertTrue(
            "BackHandler(enabled = showReadingFeed)" in host,
            "宿主缺 Feed 二级层的返回处理器：二级层必须带返回，且不抢底导的返回键",
        )
        assertTrue(
            "ReadingFeedScreen(" in host,
            "宿主缺 `ReadingFeedScreen(` 挂载分支",
        )
        val overlayWindow = host.substringAfter("val overlayOpen =").substringBefore("BackHandler(enabled = !overlayOpen")
        assertTrue(
            "showReadingFeed" in overlayWindow,
            "`showReadingFeed` 必须进 `overlayOpen` 串联，否则 Feed 二级层开着时底导返回键会越过它直接切 tab",
        )
    }

    // ---------- 2. 出站确认走共享件 ----------

    @Test
    fun `the outbound confirmation goes through the shared component`() {
        assertFalse(
            "private fun OutboundConfirmationDialog" in home,
            "阅读主页仍自带私有 `OutboundConfirmationDialog`：本席是共享 `OutboundConfirmDialog` 的第一枚生产接线，" +
                "私有份必须退役",
        )
        assertFalse(
            "GlassDialog(" in home,
            "阅读主页不应再自带玻璃对话框：面板份数归共享件，屏内再开一份就是新增玻璃面板债务" +
                "（`GlassPanelInkPairingContractTest` 的面板族同步只登记共享件侧）",
        )
        assertEquals(
            1,
            countMatches(home, Regex("""\bOutboundConfirmDialog\(""")),
            "阅读主页必须恰一处调用共享 `OutboundConfirmDialog(`（0 = 没接线，2 = 长出自家第二份出站确认）",
        )
        // 屏侧锚：`outbound_confirmation_text` 是 connected 判据点名的锚，接线时不许静默丢。
        // 形状如实登记（修复轮 1 F-1 附注，本批不改形状）：它在生产侧是
        // `Box(Modifier.testTag("outbound_confirmation_text"))` 这层**零尺寸 tag 垫片**——
        // 共享件内部是 `DialogPanel`，对话框进独立窗口，这枚 Box 既没有内容也没有尺寸。
        // 因此本条只保证「这层垫片不被顺手删」，**不是**「槽位里有内容」的证据；
        // 行为面由 connected 的 `outbound_confirm_body` + `outbound_confirm_dismiss` / `outbound_confirm_confirm` 兜住。
        assertTrue(
            "outbound_confirmation_text" in home,
            "锚 `outbound_confirmation_text` 必须在接线后继续存在（connected 侧点名它）",
        )
        // 载荷档位：阅读生成发的是文本，必须显式给 Text——留给默认值就等于把隐私档位交给未来改动者。
        assertEquals(
            1,
            countMatches(home, Regex("""PayloadKind\.Text""")),
            "出站确认必须显式声明 `PayloadKind.Text`：图片档每次都问，两档措辞可区分是共享件的立论",
        )

        // 「共享件不是摆设」这条由**上一行**兜住（修复轮 1 F-1：删掉原判据）：原判据扫整棵 `ui/` 树却
        // 不排除本屏，主页那枚在场时求和必 ≥1，主页那枚被删时先红的还是上一行 ⇒ 零增量判别力。
        // 而「除本屏外还 ≥1 枚」这一版也**不能写**：本屏就是共享件当前唯一的生产接线（现算
        // `grep -rn "OutboundConfirmDialog(" app/src/main --include=*.kt` ⇒ 组件声明 1 处 +
        // `ReadingAccessScreen.kt` 调用 1 处），把它写成 ≥1 会当场红在别的席的账上。
        // 后续屏接上共享件时，要钉的是「调用点总数 = 在册份数」这种可数口径，不是一句 ≥1 的安慰句。
    }

    // ---------- 3. 类型与长度偏好下沉生成前参数 ----------

    @Test
    fun `generation parameters live behind an expander instead of the home column`() {
        assertTrue(
            "reading_generation_params_toggle" in home,
            "主页必须有一枚具名的「生成前参数」开关：偏好区靠它出现，而不是常驻长列",
        )
        // 偏好族的落点按**花括号配对**取展开分支本体来判：不锚行号，也不靠「下一个具名锚」猜边界——
        // 原判据的右腿 `"generationParamsExpanded" in home` 读的就是这份源码，永不红（修复轮 1 F-1）；
        // 而「偏好只要排在开关之后就算对」的窗口式写法也抓不住「平铺回主页、顺序仍在开关之后」这一形状。
        val expandedBranch = balancedBranchBody(home, "if (generationParamsExpanded)")
        assertTrue(
            expandedBranch.isNotEmpty(),
            "主页必须保留 `if (generationParamsExpanded) { … }` 这一层展开分支本体" +
                "（取不到 = 开关不再承重，本条与下面两枚一起红才是可读的红样）",
        )
        assertTrue(
            "reading_preference" in expandedBranch,
            "偏好区 `reading_preference` 必须落在 `if (generationParamsExpanded)` 的分支本体里，" +
                "而不是平铺在主页长列上：把生产改回「偏好常驻主页」时本条必须红（修复轮 1 F-1 的变异自证）",
        )
        assertTrue(
            "ArticleType.entries" in expandedBranch,
            "四枚类型行（`ArticleType.entries`）与偏好区同族，也必须由同一层展开分支承重：" +
                "只把卡收进分支、类型回到常驻，等于给平铺换个名字",
        )
        assertTrue(
            Regex("""if \(!generationParamsExpanded\)""").containsMatchIn(home) ||
                Regex("""if \(generationParamsExpanded\)""").containsMatchIn(home),
            "展开态必须用 `generationParamsExpanded` 这一个开关承重：不许把偏好区改成第三层导航",
        )
        // 持久化路径不动：四枚类型仍由 `ArticleType.entries` 全量驱动，但**只剩展开区一支**——
        // 批 1 Task 7 把锁定态那四枚 disabled 类型按钮换成一句说明（`reading_access_locked_types`），
        // 锁定态不再有类型入口。计数因此 2 → 1：这是「摘掉一支」，不是放宽到 ≥1。
        assertEquals(
            1,
            countMatches(home, Regex("""ArticleType\.entries""")),
            "类型行必须由 `ArticleType.entries` 全量驱动且恰一处（生成前参数展开区）：" +
                "锁定态的类型按钮已退役（改用一句说明），少到 0 = 展开区把类型裁了，回到 2 = 锁定态又把按钮铺回来了",
        )
        assertTrue(
            "onSelectType: (ArticleType) -> Unit" in home,
            "`onSelectType` 回调形状不许改：它是屏通往 ViewModel 的偏好写入通道，改签名就是把持久化路径动了",
        )
    }

    // ---------- 4. 版式重做批 1 Task 7：页头 + 今日文章卡 + 更多组 ----------

    /**
     * 批 1 Task 7（版式重做 §3.4 阅读页）的四件：
     *  ① 换用共享页头——「换一篇」从页内次级按钮搬进页头 `action` 槽，只在有今日文章时出现；
     *  ② 今日文章收进一枚具名卡（`reading_today_article`），标题/译文/类型长度/主 CTA 都在这枚卡里；
     *  ③ 外刊 / 粘贴 / 历史三枚入口收进同一个「更多」组（`reading_more_group`），不再三条平铺大按钮；
     *  ④ 锁定态四枚 disabled 类型按钮换成一句说明（`reading_access_locked_types`）——锁定只挡「生成」，
     *    类型选择属于生成前参数，没生成入口时就不该占版面。
     *
     * 「换一篇」的二次确认（Task 7 Step 1 第 7 条）：重生成会替换当天文章，必须过共享
     * [com.example.englishlearning.ui.components.ConfirmDestructiveDialog]，且屏内恰一处。
     */
    @Test
    fun `the home leads with a today article card and a single more group`() {
        assertEquals(
            1,
            countMatches(home, Regex("""AppScreenHeader\(""")),
            "阅读主页必须换用共享页头（页头族 seed 已摘此屏）",
        )
        assertEquals(
            1,
            countMatches(home, Regex("""reading_today_article""")),
            "今日文章卡锚必须恰一处",
        )
        assertEquals(
            1,
            countMatches(home, Regex("""reading_more_group""")),
            "外刊/导入/历史必须收进同一个更多组",
        )
        assertEquals(
            0,
            countMatches(home, Regex("""SecondaryCtaButton\("换一篇"|"换一篇",\s*"reading_regenerate"""")),
            "换一篇不再是页内次级按钮",
        )
        // 页头 action 的形状：换一篇只在「有今日文章」时进槽，且 tag 仍是 connected 侧点名的 `reading_regenerate`。
        assertTrue(
            "state.todayArticle != null" in home && "reading_regenerate" in home,
            "页头 action 必须按「今天有没有文章」决定在场，并把换一篇的锚 `reading_regenerate` 留在槽里",
        )
        // 锁定态：一句说明替代四枚类型按钮。
        assertTrue("reading_access_locked_types" in home, "锁定态用一句说明替代四枚类型按钮")
        assertEquals(
            0,
            countMatches(home, Regex("""TypeButton\(type, false""")),
            "锁定态不得再平铺四枚类型按钮",
        )
        // 重生成二次确认：恰一处共享对话框，且三条文案逐字点名（替换什么 / 什么不受影响 / 不是不可逆）。
        assertEquals(
            1,
            countMatches(home, Regex("""ConfirmDestructiveDialog\(""")),
            "「换一篇」必须过共享二次确认对话框：0 = 一键就把当天文章换掉，2 = 长出第二份确认",
        )
        listOf(
            "当前这篇文章会被新生成的文章替换",
            "已解锁的历史文章与阅读记录不受影响",
            "irreversible = false",
        ).forEach { line ->
            assertTrue(line in home, "换一篇确认框缺一句「$line」：替换面、保留面与可逆档必须逐字说清")
        }
        // 更多组的三枚入口各带自己的 tag：connected 用例按 tag 点，而不是回到「按文本猜按钮」。
        listOf("reading_more_feed", "reading_more_import", "reading_more_history").forEach { tag ->
            assertEquals(1, countMatches(home, Regex(tag)), "更多组缺入口锚「$tag」或出现两份")
        }
        // 旧的三枚平铺入口与历史大按钮在 Ready 态必须离场（锁定态保留历史按钮一支）。
        listOf("reading_source_feed", "reading_source_import").forEach { old ->
            assertEquals(0, countMatches(home, Regex(old)), "旧入口锚「$old」必须随三枚平铺大按钮一起离场")
        }
        assertEquals(
            1,
            countMatches(home, Regex("""HistoryButton\(onOpenHistory,""")),
            "HistoryButton 只留锁定态一支：Ready 态的历史入口归更多组裸行（2 = 同一屏两处历史入口）",
        )
    }

    // ---------- 5. 英文正文前景 token 化后的两套 AA ----------

    /**
     * `ArticleReadingScreen` 英文正文那一枚裸 hex 的处置判据。
     *
     * 立论是**容器归属**而非「前景换主题访问器总没错」：`HighlightedEnglish` 的 `ClickableText` 落在屏根
     * `Column` 的 `MintBackground` 上，而 `MintBackground` 是读 `LocalAppColors.current.background` 的
     * **组合期 getter**（`theme/MintPalette.kt`），容器随主题换套 ⇒ 前景同样换套两套各自成立。
     * 反例族（Task 23 的 B1）是「容器主题不变的亮面」，那一族换前景必然把深档压成黑压黑。
     */
    @Test
    fun `the english body ink clears the AA gate on both theme sets`() {
        val bodyAa = 4.5
        val lightBg = rgbOf(AppColorLight.background)
        val darkBg = rgbOf(AppColorDark.background)
        val lightInk = rgbOf(AppColorLight.text1)
        val darkInk = rgbOf(AppColorDark.text1)
        val legacyInk = 0x17342A

        assertTrue(
            "Color(0xFF17342A)" !in stripComments(readingScreen),
            "英文正文前景仍带裸 hex：本席要摘的就是这一枚（台账两处同批，见 RawColorLiteralRatchetTest）",
        )
        assertTrue(
            "style = AppType.Body.copy(color = appColors().text1)" in stripComments(readingScreen),
            "英文正文必须归到房内 `AppType.Body` + 显式 `appColors().text1` 的配平形状（R-22.3 方向，只限这一行）",
        )
        val lightRatio = ratio(lightInk, lightBg)
        val darkRatio = ratio(darkInk, darkBg)
        assertTrue(
            lightRatio >= bodyAa && darkRatio >= bodyAa,
            "正文档两套都必须 ≥$bodyAa：实测浅套 ${"%.4f".format(lightRatio)}、深套 ${"%.4f".format(darkRatio)}",
        )
        // 反空转：迁移前那枚浅套硬编码墨压在深底上必须**不合格**。它若也过闸，说明本条底取错了、
        // 或者深档 background 根本没换套——两种情况都意味着这条判据在骗人。
        val legacyOnDark = ratio(legacyInk, darkBg)
        assertTrue(
            legacyOnDark < bodyAa,
            "旧墨 $legacyInk 压深套底实测 ${"%.4f".format(legacyOnDark)} 本应不达正文线：它若过闸，" +
                "说明本条没在量「不迁会怎样」，判据空转",
        )
        // 未达正文线的墨也不许按装饰档放行：它必须仍在 3.1 以下，才证明这一族是「不可读」而不是「勉强装饰」。
        assertTrue(
            legacyOnDark < 3.1,
            "旧墨压深底若落在 3.1–4.5 之间，说明本条要重述为装饰档；现取 ${"%.4f".format(legacyOnDark)} 必须低于 3.1 才算真回归被修掉",
        )
    }

    private fun rgbOf(color: Color): Int = color.toArgb() and 0xFFFFFF

    /**
     * 按花括号配对取 `if (condition) { … }` 的分支本体（源已剥注释，字符串模板里的成对 `{}` 一并计入）。
     *
     * 取不到就返回**空串**而不是抛异常：让调用处那几枚断言各自响亮判红（红样要能指认「哪一件没做」），
     * 而不是把「版面坏了」压成一条 `error()` 堆栈。边界靠配对而不是靠「下一个具名锚」——
     * 锚一旦改名，窗口就会一路吞到文件末尾，把「平铺」看成「合规」。
     */
    private fun balancedBranchBody(source: String, condition: String): String {
        val start = source.indexOf(condition)
        if (start < 0) return ""
        val open = source.indexOf('{', start)
        if (open < 0) return ""
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, index)
                }
            }
        }
        return ""
    }

    /**
     * 品牌亮薄荷底上的前景必须是**与主题无关的深墨**（Task 26 Step 3 收口）。
     *
     * 容器 `MintPrimary` 读 `LocalDomainColors` 的 `AiSpeech.base`：浅套 #2EC99C、深套 #35E0AC，
     * **两套都是亮面**，所以前景不能随套翻转——`appColors().onMint` 浅套 #174C44 / 深套 #DFF6EC，
     * 深套压亮薄荷只有 1.49:1（当场回归）。Task 24 时期这四枚是静态 `AppPalette.OnMintPrimary`，
     * 与棘轮「静态读点」口径冲突；Task 26 迁到 `appColors().onBrand`（明暗两套同值 #04241C），
     * 既满足**与套无关**，又把浅套比值从 4.62 抬到 7.81（深套 9.74）。
     *
     * 本条钉四件事：`onBrand` 两套同值；两套压亮薄荷都 ≥4.5；`onMint` 深档不达线（禁迁）；
     * 屏内**唯一**一枚主色按钮（今日文章 / 生成那一枚）换到 `appColors().onBrand`，且没有一枚混进 `onMint`。
     *
     * 批 1 Task 7 把站点数 4 → 1：计划 §Task 7 Step 1 的口径是「一屏只剩主 CTA 一枚薄荷主色」。
     * 原四枚里另外三枚不在页面上、而在三个辅助函数里——四枚类型行（`TypeButton`）、
     * 生成失败横幅与未配置横幅里的「去配置」各一枚。Task 7 把它们降为次级档（`MintSurface` / `MintTint`
     * 容器 + 1dp `MintOutline` 描边），于是「一屏一枚主色」既是运行时事实、也是源码级计数：
     * 类型行与横幅动作都不再抢主任务叙事。主 CTA 本体（`PrimaryCtaButton`）的配平与亮度判据一字未改。
     */
    @Test
    fun `the mint primary pair stays dark ink on both theme sets`() {
        val bodyAa = 4.5
        val lightMintBase = DomainLight.getValue(AppDomain.AiSpeech).base
        val darkMintBase = DomainDark.getValue(AppDomain.AiSpeech).base
        val onBrandLight = ratio(rgbOf(AppColorLight.onBrand), rgbOf(lightMintBase))
        val onBrandDark = ratio(rgbOf(AppColorDark.onBrand), rgbOf(darkMintBase))
        val onMintDark = ratio(rgbOf(AppColorDark.onMint), rgbOf(darkMintBase))

        assertEquals(
            AppColorLight.onBrand,
            AppColorDark.onBrand,
            "品牌前景必须明暗同值：容器两套都是亮薄荷，前景区分套就等于至少一套配错",
        )
        assertTrue(
            onBrandLight >= bodyAa,
            "浅套：onBrand 压 MintPrimary 应 ≥$bodyAa，实测 ${"%.4f".format(onBrandLight)}",
        )
        assertTrue(
            onBrandDark >= bodyAa,
            "深套：onBrand 压 MintPrimary 应 ≥$bodyAa，实测 ${"%.4f".format(onBrandDark)}",
        )
        assertTrue(
            onMintDark < bodyAa,
            "若 `appColors().onMint` 压深套 MintPrimary 也过正文线，那本条就该反向要求迁移；" +
                "现实测 ${"%.4f".format(onMintDark)} 必须不达线，才证明不能拿套内墨来配这四枚",
        )
        // 屏内形状：主 CTA 这一枚绑在同一枚容器表达式上，且没有混进 onMint / 旧静态符号。
        assertEquals(
            1,
            countMatches(home, Regex("""appColors\(\)\.onBrand\b""")),
            "阅读主页的 onBrand 站点数必须与 DarkTokenConsumptionRatchetTest 的口径同步（现 1：只有主 CTA 一枚）",
        )
        assertEquals(
            0,
            countMatches(home, Regex("""AppPalette\.OnMintPrimary""")),
            "静态 `AppPalette.OnMintPrimary` 已迁净，屏内不得复现（棘轮会把它重新算成待迁读点）",
        )
        assertEquals(
            0,
            countMatches(home, Regex("""appColors\(\)\.onMint\b""")),
            "阅读主页不得出现 `appColors().onMint`：它配的是暗薄荷容器，不是本屏的亮 MintPrimary 容器",
        )
        // 配对的**写法**有两种（同一行的 `containerColor = MintPrimary, contentColor = appColors().onBrand`
        // 与 TypeButton 那种逐枚换行的 `colors = ButtonDefaults.buttonColors(...)`），所以按两侧符号各自点数，
        // 两数相等才说明深墨没有压到别的容器上；不按单行正则判，那种判法会把换行写法静默漏掉。
        assertEquals(
            1,
            countMatches(home, Regex("""containerColor = MintPrimary\b""")),
            "阅读主页应恰一枚 `MintPrimary` 亮面容器（主 CTA）：站点数一变，本条与 onBrand 那两枚计数必须同步改，" +
                "否则等于放开「深墨压到别的容器」的口子",
        )
        assertEquals(
            1,
            countMatches(home, Regex("""contentColor = appColors\(\)\.onBrand\b""")),
            "唯一一枚深墨前景必须挂在 MintPrimary 容器档上：与上一枚计数相等才配对",
        )
    }
}
