package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppPalette
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
        // 二级层本体：四枚 Feed 状态与条目锚逐枚点名，缺任一枚 = 状态族被顺手裁掉。
        listOf(
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

        // 共享件不是摆设：整棵 `ui/` 生产树（排除组件本体）里至少有一枚真实调用方。
        val productionCallsites = uiFiles().sumOf { file ->
            if (file.name == "OutboundConfirmDialog.kt") 0
            else countMatches(file.readText(), Regex("""\bOutboundConfirmDialog\("""))
        }
        assertTrue(
            productionCallsites >= 1,
            "`OutboundConfirmDialog(` 在 ui/ 生产树上计数为 $productionCallsites：共享件零调用方就是「摆设组件」，" +
                "本席的接线必须把它钉成在用件",
        )
    }

    // ---------- 3. 类型与长度偏好下沉生成前参数 ----------

    @Test
    fun `generation parameters live behind an expander instead of the home column`() {
        assertTrue(
            "reading_generation_params_toggle" in home,
            "主页必须有一枚具名的「生成前参数」开关：偏好区靠它出现，而不是常驻长列",
        )
        val toggleWindow = home.substringAfter("reading_generation_params_toggle").substringBefore("reading_history_entry")
        assertTrue(
            "reading_preference" in toggleWindow || "generationParamsExpanded" in home,
            "偏好区必须落在开关之后的展开分支里（开关锚与偏好区之间要能看见展开判据）",
        )
        assertTrue(
            Regex("""if \(!generationParamsExpanded\)""").containsMatchIn(home) ||
                Regex("""if \(generationParamsExpanded\)""").containsMatchIn(home),
            "展开态必须用 `generationParamsExpanded` 这一个开关承重：不许把偏好区改成第三层导航",
        )
        // 持久化路径不动：四枚类型仍由 `ArticleType.entries` 全量驱动（Locked 一支 + 展开区一支），
        // 少一枚 = 搬家时顺手裁了类型，多一枚 = 主页又长出常驻类型列。
        assertEquals(
            2,
            countMatches(home, Regex("""ArticleType\.entries""")),
            "类型行必须由 `ArticleType.entries` 全量驱动且恰两处（锁定态一支、生成前参数展开区一支）：" +
                "偏好只是下沉到开关之后，取源与写入参数一枚都不许多、一枚都不许少",
        )
        assertTrue(
            "onSelectType: (ArticleType) -> Unit" in home,
            "`onSelectType` 回调形状不许改：它是屏通往 ViewModel 的偏好写入通道，改签名就是把持久化路径动了",
        )
    }

    // ---------- 4. 英文正文前景 token 化后的两套 AA ----------

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
     * 「前景单迁、容器未迁」这一族的反向护栏（Task 23 的 Blocker 在本屏的对偶形态）。
     *
     * `ReadingAccessScreen` 的四枚 `AppPalette.OnMintPrimary` 会被深色令牌消费棘轮算成「待迁静态读点」，
     * 但**迁成 `appColors().onMint` 就是当场回归**：容器 `MintPrimary` 读 `LocalDomainColors` 的
     * `AiSpeech.base`，浅套 #2EC99C、深套 #35E0AC **两套都是亮面**，所以两套都要深墨；
     * `appColors().onMint` 的深套取值是浅墨 #DFF6EC，压亮薄荷底只有 1.49:1。
     * 本条把三件事一次钉死：现配两套合格、迁移目标深档不合格、屏内确实还是那四枚现配。
     * 结论与剩余台账一并登记在 `docs/verification/ui-redesign-2026-10.md` 的 Task 24 节，交 Task 26 收口。
     */
    @Test
    fun `the mint primary pair stays dark ink on both theme sets`() {
        val bodyAa = 4.5
        val mintInkLight = ratio(rgbOf(AppPalette.OnMintPrimary), rgbOf(DomainLight.getValue(AppDomain.AiSpeech).base))
        val mintInkDarkContainer = ratio(rgbOf(AppPalette.OnMintPrimary), rgbOf(DomainDark.getValue(AppDomain.AiSpeech).base))
        val onMintDark = ratio(rgbOf(AppColorDark.onMint), rgbOf(DomainDark.getValue(AppDomain.AiSpeech).base))

        assertTrue(
            mintInkLight >= bodyAa,
            "浅套：OnMintPrimary 压 MintPrimary 应 ≥$bodyAa，实测 ${"%.4f".format(mintInkLight)}",
        )
        assertTrue(
            mintInkDarkContainer >= bodyAa,
            "深套：现配静态深墨压亮薄荷底应 ≥$bodyAa，实测 ${"%.4f".format(mintInkDarkContainer)}——" +
                "这一条成立才说明这四枚是「与主题无关的合法残留」而不是待迁债务",
        )
        assertTrue(
            onMintDark < bodyAa,
            "若 `appColors().onMint` 压深套 MintPrimary 也过正文线，那本条就该反向要求迁移；" +
                "现实测 ${"%.4f".format(onMintDark)} 必须不达线，四枚才许留在原配",
        )
        // 屏内形状：四枚现配逐枚绑在同一枚容器表达式上，且没有混进 onMint。
        assertEquals(
            4,
            countMatches(home, Regex("""AppPalette\.OnMintPrimary""")),
            "阅读主页的 OnMintPrimary 站点数必须与 DarkTokenConsumptionRatchetTest 的 seed 同格相等（现 4）",
        )
        assertEquals(
            0,
            countMatches(home, Regex("""appColors\(\)\.onMint\b""")),
            "阅读主页不得出现 `appColors().onMint`：它配的是暗薄荷容器，不是本屏的亮 MintPrimary 容器",
        )
        // 配对的**写法**有两种（同一行的 `containerColor = MintPrimary, contentColor = AppPalette.OnMintPrimary`
        // 与 TypeButton 那种逐枚换行的 `colors = ButtonDefaults.buttonColors(...)`），所以按两侧符号各自点数，
        // 两数相等才说明四枚深墨没有一枚压到别的容器上；不按单行正则判，那种判法会把换行写法静默漏掉。
        assertEquals(
            4,
            countMatches(home, Regex("""containerColor = MintPrimary\b""")),
            "阅读主页应恰四枚 `MintPrimary` 亮面容器（站点数一变，本条与 OnMintPrimary 那两枚计数必须同步改，" +
                "否则等于放开「深墨压到别的容器」的口子）",
        )
        assertEquals(
            4,
            countMatches(home, Regex("""contentColor = AppPalette\.OnMintPrimary""")),
            "四枚深墨前景必须逐枚挂在 MintPrimary 容器档上：与上一枚计数相等才配对",
        )
    }
}
