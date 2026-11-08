package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 批 2 Task 1：词卡屏的新骨架契约（spec §5 逐屏表「词卡」行 + §4.1 三档容器）。
 *
 * 与 [WordCardScreenContractTest] 的分工：那一份钉的是**行为接线**（退出停播、发音药片统一、
 * 揭晓焦点），本份钉的是**版式骨架**（顶栏三件、软档卡、吸底反馈条、令牌换装）。两份都必须绿，
 * 所以这里只加新判据，不改旧锚。
 *
 * 全部按符号取样，不锚行号；`uiScreenSource` 已剥注释，注释里的示例代码不计入命中。
 */
class WordCardLayoutContractTest {

    private val source: String by lazy { uiScreenSource("WordCardScreen.kt") }

    // ---------- 顶栏：× 关闭 + 计数 + 进度条 ----------

    /**
     * spec §5：「顶栏「× + 3/12 + 进度条」」。
     *
     * 三枚 tag 是这件的**唯一**可观测面：× 是关闭（不是返回今日计划那枚 `word_card_back_to_plan`，
     * 那是 AllDone 态的主操作），计数沿用既有 `word_card_progress`（connected 侧已按它取证），
     * 进度条单独一枚 tag，好让「进度条在、但计数没进顶栏」这种半途改法被判红。
     */
    @Test
    fun `the top bar carries a close action a counter and a progress bar`() {
        listOf(
            "word_card_close",
            "word_card_progress",
            "word_card_progress_bar",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("""testTag("$tag")"""))),
                "顶栏必须有且只有一枚 testTag(\"$tag\")（spec §5 词卡：× + 计数 + 进度条）",
            )
        }
        assertEquals(
            1,
            countMatches(source, Regex("""\bLinearProgressIndicator\(""")),
            "进度条用 M3 LinearProgressIndicator：自绘 Box 当进度条既无数值语义也进不了 TalkBack",
        )
    }

    // ---------- 页头：换档到 AppType ----------

    /**
     * 页头从 `MaterialTheme.typography.headlineSmall` 换到 `AppType` 之后，本文件不再是页头族违规者，
     * `SharedComponentUniquenessRatchetTest` 的页头族 seed 必须同批摘掉 `"WordCardScreen.kt"`——
     * 那条登记值由该测试自己按「实际集合 == seed」点名，不在本用例里重复钉。
     */
    @Test
    fun `the screen header no longer reads the library headline style`() {
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography\.headline""")),
            "页头与标签一律走 AppType 六档；库内 headline 读点会让页头族棘轮继续把本屏记成债务",
        )
        assertTrue(
            "AppType." in source,
            "排印必须取 AppType 六档（spec §4.2）",
        )
    }

    // ---------- 内容卡：软档，不描边 ----------

    /**
     * spec §4.1 弱卡：白面**无描边**、圆角 20、内边距 16。
     *
     * 反空转：同一计数器对 M3 `Card(` 必须数出 1 份（旧实现就是那样），否则 `== 0` 的断言可能
     * 只是因为取样面拿错了文件。
     */
    @Test
    fun `the word card body is a soft card without a border`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("SoftCard("))),
            "词面必须是一枚共享软档卡（spec §4.1 弱卡：白面无描边圆角 20）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "不得退回 M3 Card：三档容器就是替代它的那一层（描边卡片汤的来源）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("border(1.dp"))),
            "弱卡与反馈按钮都不描边；描边只留给输入框与危险动作（spec §4.1）",
        )
        assertEquals(
            2,
            countMatches("Card(\nCard(", Regex("""\bCard\(""")),
            "反空转：`\\bCard(` 必须能数出 2 份，否则上面的 0 是假绿",
        )
    }

    /**
     * 主词是这一屏唯一的显示级元素（spec §5：44sp），取 `AppType.Display` 而不是库内 `displaySmall`。
     *
     * QA3 A2.3 给顶档留了例外：超过钉档 2.0 时词头换到 `AppType.StrongTitle`，否则一枚 10 字母的词
     * 在 3.0 档就排到 449dp 高、被视口切在半截（判据见下面那两条降级用例）。这一条只钉两件事：
     * 显示档仍是 `AppType` 自家的、库内 `display*` 一处都不许混进来。
     */
    @Test
    fun `the lemma takes the display tier of AppType`() {
        assertTrue(
            "AppType.Display" in source,
            "主词取 AppType.Display（44sp + tnum），spec §4.2 只允许六档",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography\.display""")),
            "库内 display* 档与 AppType.Display 同号不同源，混用会让换档只改一半",
        )
    }

    // ---------- 三档反馈：吸底 ----------

    /**
     * spec §5：「三枚反馈按钮固定底部（不认识/模糊/认识）」。
     *
     * 取样面是私有 [WordCardFeedbackBar] 的函数体：三档必须**全部**落在那条吸底容器里。旧实现把它们
     * 排在滚动流末尾，大字号下与卡片一起被卷出屏（`WordCardScreenTest` 那两条大字号用例正是为此而写）。
     *
     * QA3 A2.4 之后条内是**两个容器、一份档位清单**：1.0/2.0 档一枚 `Row` 三等宽，顶档一枚 `Column`
     * 整宽堆叠。于是 `FeedbackButton(` 在条内只剩 2 处（每分支一次），三档由 `FeedbackTiers` 单点给出
     * ——判据随之从「数三枚按钮」换成「数两条 `FeedbackTiers.forEach`」：两分支各自 `forEach` 同一份清单，
     * 才既允许换摆法、又不给「同一档在两处措辞不同」开门。
     */
    @Test
    fun `the three feedback actions live in one pinned bar`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""testTag("word_card_feedback_bar")"""))),
            "吸底条必须有且只有一枚 testTag(\"word_card_feedback_bar\")",
        )
        val bar = bodyOf("private fun WordCardFeedbackBar(")
        assertTrue(bar.isNotEmpty(), "取不到 WordCardFeedbackBar 本体，判据会空转")
        assertEquals(
            2,
            countMatches(bar, Regex("""\bFeedbackButton\(""")),
            "条内只允许两个容器各一处 `FeedbackButton(`（堆叠档 + 等宽档）；第 3 处 = 有一档被写了两遍",
        )
        assertEquals(
            2,
            countMatches(bar, Regex("""FeedbackTiers\.forEach""")),
            "两个摆法分支必须都从 `FeedbackTiers` 这份单点清单画三档，不在分支里重排字面量",
        )
        assertEquals(
            1,
            countMatches(bar, Regex(Regex.escape("""testTag("word_card_feedback_bar")"""))),
            "吸底条的 tag 必须画在它自己的体内",
        )
        assertEquals(
            countMatches(bar, Regex("""\bFeedbackButton\(""")) + 1,
            countMatches(source, Regex("""\bFeedbackButton\(""")),
            "反馈按钮的调用必须全落在吸底条体内：全屏命中只允许比条内多那 1 处 `private fun FeedbackButton(` 定义" +
                "（多出来的每 1 处 = 条外还有第二份入口）",
        )
        // 三档的 tag 是这一屏的可观测面：条外再出现一份 = 第二个入口（`loadingDraws...` 那类占位不算，
        // 那些 tag 各自独立）。
        listOf(
            "word_card_feedback_unknown",
            "word_card_feedback_fuzzy",
            "word_card_feedback_known",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "$tag 只在 `FeedbackTiers` 里出现一次；两处以上等于同一档有两个出口",
            )
        }
    }

    // ---------- 顶档降级：唯一出口常驻 ----------

    /**
     * QA3 A2.5（用户 2026-11-08 拍板「全部都做」＝立为缺陷修）：「显示释义」是回忆态的**唯一出口**，
     * 顶档下它必须常驻吸底区，而不是和词头一起被卷出屏。
     *
     * 上面那条吸底判据的注释记过同一个失效形状：「旧实现把它们排在滚动流末尾，大字号下与卡片一起
     * 被卷出屏」。评分三档当年还了账，揭晓这颗没还——本轮在 emulator-5554 用真机路径复现量到：
     * 3.0 档词头 `progressive` 单枚就占 `[100,358][980,1113]`（755px ≈ 288dp），而吸底评分条从
     * `y=1145` 起画，可滚视口只剩 855px，出口整颗在折叠线之下。
     *
     * 判定收在本屏自有的纯函数里、屏侧只读 `LocalDensity.current.fontScale`——与批 B
     * `AppWindowLayoutPolicy.downgradesHeroHeader` 同族：可单测、可复算，不散成一串 `if`。
     *
     * 阈值取**严格大于 2f**：2.0 是 UI 重设计 spec 钉住的组件大字号验收档（AC-R-07），
     * 那一档的卡面形状由 `lemmaAndRevealedMeaningStayFullyVisibleAtMaximumLargeFontScale` 守着，
     * 不许被这次降级动一根手指；超过它（Android「字体大小」顶档 3.0）才换摆法。
     */
    @Test
    fun `the reveal action is pinned only above the pinned acceptance font tier`() {
        assertFalse(wordCardPinsRevealAction(1f), "1.0 档卡面形状是设计稿口径，不得被降级改动")
        assertFalse(wordCardPinsRevealAction(2f), "2.0 是 AC-R-07 钉住的验收档，那一档仍按稿把出口留在卡内")
        assertTrue(wordCardPinsRevealAction(2.5f), "超过钉档就必须把唯一出口常驻吸底区")
        assertTrue(wordCardPinsRevealAction(3f), "Android 字体大小顶档 3.0 必须常驻")
    }

    // ---------- 顶档降级：词头换档 + 评分条换摆法 ----------

    /**
     * QA3 A2.3 + A2.4（用户 2026-11-08 拍板「全部都做」＝立为缺陷修）：顶档还要换两处摆法。
     *
     * 三条判据合在一条用例里，因为它们钉的是同一个决策的两侧——**只有超过钉档 2.0 才换**：
     *  - 词头从 `AppType.Display`（44sp）换到 `AppType.StrongTitle`（27sp）。3.0 档设备实测一枚
     *    `confidence` 词头按 Display 排到 449dp 高，而可滚视口只剩 220dp：尾字母既没画也没省略号（A2.3）。
     *  - 三档评分从「三等宽横排」换成「整宽上下堆叠 + 每枚一行」。3.0 档横排时每枚 ≈110dp 宽，
     *    「不认识」三字 ≈110dp 已经站不住，左格排成四行、整条吸底区吃掉 226dp——
     *    卡面被压没（A2.4 与 A2.3 是同一件事的两半）。
     *  - 音标/词性与喇叭药片从「同一行」换成「药片另起一行」。3.0 档药片的**可见文字**连字形长到 180dp，
     *    `weight(1f)` 那一侧只剩 89.6dp，`/prəˈɡresɪv/` 被切成三截的音节（A2.8，收口复测实拍发现）。
     *
     * 阈值仍取**严格大于 2f**：2.0 档的卡面形状由 `lemmaAndRevealedMeaningStayFullyVisibleAtMaximumLargeFontScale`
     * 钉死（AC-R-07），这三处降级不许动它一根手指。
     */
    @Test
    fun `the lemma tier, the feedback arrangement and the pronunciation row change only above the pinned acceptance font tier`() {
        listOf(
            ::wordCardLemmaUsesCompactTier,
            ::wordCardStacksFeedbackTiers,
            ::wordCardStacksPronunciationRow,
        ).forEach { decide ->
            assertFalse(decide(1f), "1.0 档是按设计稿画的版面，不得被降级改动")
            assertFalse(decide(2f), "2.0 是 AC-R-07 钉住的验收档，那一档仍按稿画")
            assertTrue(decide(2.5f), "超过钉档就要换档/换摆法")
            assertTrue(decide(3f), "Android 字体大小顶档 3.0 必须换")
        }
    }

    /**
     * 顶档换的是**容器与字号档**，不是文案。
     *
     * 五条约各防一种半途改法：
     *  - `AppType.StrongTitle` 只出现 1 次且和 `AppType.Display` 同在一枚 `if` 里：词头若在别处再换一次档，
     *    「只改一半」就会让 1.0 档的卡面跟着抖；
     *  - 三个判档函数各只有「定义 + 调用」2 处命中：第三处 = 判定被抄进容器里，变成一串散 `if`；
     *  - `fontScale > 2f` 恰好 4 处（出口常驻 / 词头换档 / 评分条换摆法 / 发音行换容器）：
     *    第 5 处必须是新决策漏登记，少一处说明某条降级被并进了别的分支；
     *  - 音标与词性两枚 tag 各只出现 1 次：A2.8 的两个容器分支共用同一份注脚与同一枚药片调用点，
     *    「同一件控件在两个分支各画一遍」正是这条要关的门（药片那侧由
     *    `WordCardScreenContractTest.the_screen_uses_the_shared_pill_and_restates_no_readout` 钉「调用窗口恰两枚」）；
     *  - 三句副说明各只出现 1 次：档位文案的唯一来源是 `FeedbackTiers`，两个容器分支不许各写一遍
     *    （「同一档在两处措辞不同」正是这条要关的门）。
     */
    @Test
    fun `the top tier swaps containers without duplicating the decision or the copy`() {
        assertTrue(
            "if (compactLemma) AppType.StrongTitle else AppType.Display" in source,
            "词头换档必须是同一枚 `if` 的两个分支，不是两处各设 style",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""\bAppType\.StrongTitle\b""")),
            "`AppType.StrongTitle` 只能给词头这一处用；第二处等于换档决策分叉",
        )
        assertEquals(2, countMatches(source, Regex("""wordCardLemmaUsesCompactTier\(""")), "定义 + 唯一调用点")
        assertEquals(2, countMatches(source, Regex("""wordCardStacksFeedbackTiers\(""")), "定义 + 唯一调用点")
        assertEquals(2, countMatches(source, Regex("""wordCardStacksPronunciationRow\(""")), "定义 + 唯一调用点")
        assertEquals(
            4,
            countMatches(source, Regex(Regex.escape("fontScale > 2f"))),
            "本屏的顶档降级只有四件事：揭晓出口常驻、词头换档、评分条换摆法、发音行换容器",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""testTag("word_card_ipa")"""))),
            "音标只能有一处渲染点：A2.8 的两个分支共用同一份注脚，各画一遍就是第二份真值",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""testTag("word_card_part_of_speech")"""))),
            "词性只能有一处渲染点（与音标同一条理由）",
        )
        assertEquals(
            4,
            countMatches(source, Regex("""\bFeedbackTierSpec\(""")),
            "`FeedbackTiers` 三枚 + 类声明一处 = 4；第 5 处等于多出一档",
        )
        listOf("\"想不起来\"", "\"差点没想起\"", "\"脱口而出\"").forEach { caption ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape(caption))),
                "$caption 只在 `FeedbackTiers` 里写一次；两处以上 = 两个分支各写一遍文案",
            )
        }
    }

    // ---------- 令牌换装 ----------

    /**
     * 批 1 起屏侧只消费 `appColors()` / `appDomain(...)`（组合期读，深浅两套各自成立）。
     *
     * `Mint*` 那七枚是 Task 26 之前的静态桥：浅套取值与 token 逐字相同，所以换装不改浅色观感，
     * 但继续留在新屏里就等于给「屏侧还能读静态品牌色」留口子。
     */
    @Test
    fun `the screen consumes theme tokens instead of the static mint bridge`() {
        listOf(
            "MintPrimaryDark",
            "MintPrimary",
            "MintTextMuted",
            "MintSurface",
            "MintOutline",
            "MintTint",
            "MintBackground",
        ).forEach { token ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(token))),
                "$token 是静态薄荷桥，屏侧改用 appColors()/appDomain()（spec §4.4）",
            )
        }
        assertTrue("appColors()" in source, "屏侧必须消费 appColors()")
        assertTrue("appDomain(AppDomain.Learn)" in source, "本屏唯一承担主操作的域色是 Learn（spec §4.4）")
    }

    // ---------- 收尾版面撤出本屏 ----------

    /**
     * 批 1 Task 18：本轮收尾版面**整块搬出词卡屏**，这里钉撤干净了没有。
     *
     * 原型在末张卡推进后走的是 `go('learn-summary')`（`assets/app.js:1688` / `:1701`），收尾画面归新建的
     * `TodaySummaryScreen`，宿主按 `WordCardUiState.AllDone` 直接挂那一屏（`AppScreen.kt`），所以本屏的
     * `AllDone` 分支只剩 `when` 的穷尽性。三条判据各自的变异形态：
     *  - 两枚旧锚点若被搬回来（同一条状态两处渲染，正是 Task 10 台账点过名的重复画面），tag 计数当场非零；
     *  - 收尾屏若被词卡屏自己调用（把新建屏当成词卡流程的一个子视图），`TodaySummaryScreen(` 会出现在这份源码里；
     *  - `word_card_back_to_plan` 必须**仍在且只有一处**：撤收尾版面不等于撤 `NoCards` / `ReviewCompleted`
     *    那两态的出口，两态仍共享 `BackToPlanButton`（只定义一处，所以计数恰 1）。
     *
     * 渲染侧的对偶判据是 `WordCardScreenTest.allDoneIsNoLongerRenderedByTheCardScreen`，
     * 挂载与三个出口归 `AppScreenTest` 那两条 Task 18 用例。
     */
    @Test
    fun `the session summary layout no longer lives on the card screen`() {
        listOf("word_card_all_done", "word_card_completion_breakdown").forEach { tag ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape("""testTag("$tag")"""))),
                "$tag 是撤掉的收尾锚点：再出现就是同一条状态两处渲染",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex("""TodaySummaryScreen\(""")),
            "收尾屏由宿主挂载，不由词卡屏内嵌：这里出现调用就是把两层版面叠在了一起",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""testTag\("word_card_back_to_plan"\)""")),
            "「返回今日计划」仍由 NoCards / ReviewCompleted 共享同一枚 `BackToPlanButton`（定义一处、tag 一处）",
        )
        assertTrue(
            "is WordCardUiState.AllDone ->" in source,
            "`AllDone` 分支要留在 `when (state)` 里保住穷尽性（撤分支等于给未来接线留编译期空档）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("今天的词卡都提交完了")),
            "旧收尾那句文案整句撤走：留在词卡屏就是第二份收尾版面",
        )
    }

    // ---------- 取样助手 ----------

    /** 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。 */
    private fun bodyOf(signature: String): String {
        val start = source.indexOf(signature)
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
}
