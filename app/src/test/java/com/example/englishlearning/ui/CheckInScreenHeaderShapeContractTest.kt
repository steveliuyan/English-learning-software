package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 22-B 修复轮 1 的 F2（A-7）：把「打卡页页头不许再挂 `action`」钉到 **JVM 侧的源码形状**。
 *
 * 为什么现有的 connected 判据不够：`CheckInScreenTest.openFavoritesAndWrongWordsLinksAreNotRenderedOnCheckInHeader`
 * 在 `androidTest` 里，而门控第三项 `:app:compileDebugAndroidTestKotlin` **只编译不跑** ⇒ 有人把
 * 「我的收藏 / 错词本」两条裸文字链加回 `AppScreenHeader` 的 `action` 槽时，接设备的判据不跑就什么都看不见。
 * 本类补的正是门控里跑得到的那一半：源码形状不合法当场红。
 *
 * 与本仓既有源码契约同一口径（夹具复用 [uiScreenSource] / [countMatches]，**不自写剥注释实现**）：
 *  - 页头调用恰一枚（0 = 页头又回到私有 Row；2 = 长出第二枚页头，`SharedComponentUniquenessRatchetTest` 的页头族也会跟着红）；
 *  - 该次调用的**实参文本**里不得出现 `action` 标识符——口径是「实参区间」而不是整文件，
 *    因为历史叙述里合法地留着「action 槽」这个词（`CheckInScreen.kt` 摘除说明就写了它），
 *    整文件计数会把这条判据变成永远红、逼人放宽它。
 *
 * 判据自证有牙：另外两枚方法把**合成字符串**喂给同一提取器与同一判定，
 *  ① `action = { … }` 必须判红（补回槽位就抓得到）；② 只写在注释里的 `action` 必须判绿（证明走的是
 *  [stripComments] 口径而不是子串乱咬）。合成串只在测试内，**不改生产文件**。
 */
class CheckInScreenHeaderShapeContractTest {

    private val source = uiScreenSource("CheckInScreen.kt")

    /** `AppScreenHeader` 调用的锚点（与 `MyWordsScreenContractTest` 的页头族同写法）。 */
    private val headerCall = Regex("""AppScreenHeader\(""")

    @Test
    fun checkInScreenMountsTheSharedHeaderExactlyOnce() {
        assertEquals(
            1,
            countMatches(source, headerCall),
            "CheckInScreen.kt 应恰一处 `AppScreenHeader(`（0 = 页头又长成私有 Row/大字号下塌宽的旧形状；" +
                "2 = 页头长出第二份，屏侧与组件侧从此两套口径）",
        )
    }

    @Test
    fun checkInHeaderCallCarriesNoActionSlot() {
        val arguments = headerArgumentsOfFirstCall(source)
        assertEquals(
            0,
            countMatches(arguments, Regex("""\baction\b""")),
            "打卡页页头那次 `AppScreenHeader(` 调用的实参里不得出现 `action`：Task 22-B（R-22.1）已把" +
                "「我的收藏 / 错词本」两条裸文字链连同两枚形参整体摘除，收藏与错词的全站唯一出口是合并页" +
                " MyWordsScreen。任何把链补回 action 槽的改动都在本条红（connected 那枚只在接设备时才跑，" +
                "门控看不见）。实际实参：$arguments",
        )
    }

    /**
     * 反事实自证（第一腿）：把 `action = { … }` 喂给**同一提取器 + 同一判定**必须判红。
     *
     * 这一腿是「判据不是摆设」的凭据：若提取器退化成空串、或判定被写成恒真，这里就绿不了。
     */
    @Test
    fun theJudgementIsRedWhenAnActionSlotIsPutBack() {
        val synthetic = stripComments(
            """
            class CheckInScreen {
                fun CheckInScreen(onBack: () -> Unit) {
                    // 形如把两条链补回槽位的历史叙述： action = 这一行只是注释，不该被算成命中
                    AppScreenHeader(
                        title = "学习打卡",
                        onBack = onBack,
                        action = {
                            Text("我的收藏")
                            Text("错词本")
                        },
                    )
                }
            }
            """.trimIndent(),
        )
        val arguments = headerArgumentsOfFirstCall(synthetic)
        assertTrue(
            countMatches(arguments, Regex("""\baction\b""")) >= 1,
            "判据失去牙齿：实参区间里明明写着 `action = { … }` 却没被抓到（提取器或判定被改坏了）。实际实参：$arguments",
        )
        assertEquals(
            1,
            countMatches(synthetic, headerCall),
            "合成夹具自证：这一枚串确实只有一处页头调用，反事实才有对照意义",
        )
    }

    /**
     * 反事实自证（第二腿）：`action` 只出现在**注释**里时必须判绿。
     *
     * 钉的是口径来源：提取前先过 [uiScreenSource] / [stripComments]。生产文件 `CheckInScreen.kt` 的
     * 摘除说明里就留着「action 槽」这个词，若判定读的是未剥注释原文，本条会红、
     * `checkInHeaderCallCarriesNoActionSlot` 也会假红——历史叙述不该被当成复活的动作。
     */
    @Test
    fun theJudgementIgnoresActionMentionedOnlyInComments() {
        val synthetic = stripComments(
            """
            class CheckInScreen {
                fun CheckInScreen(onBack: () -> Unit) {
                    // Task 22-B：F5-03 遗留在 action 槽里的两枚裸文字链已摘除
                    AppScreenHeader(
                        title = "学习打卡",
                        onBack = onBack,
                    )
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            0,
            countMatches(headerArgumentsOfFirstCall(synthetic), Regex("""\baction\b""")),
            "只写在注释里的 `action` 不得计入实参（否则真实源码的摘除说明会让本判据永远红）",
        )
    }

    /**
     * 取 `source` 里**第一枚** `AppScreenHeader(` 调用的实参文本（左括号之后到与之配平的右括号之前）。
     *
     * 为什么要区间而不是整文件子串判定：见类 KDoc——历史叙述合法地留着 `action` 这个词。
     * 配平扫描认字符串与字符字面量（区间内 `Text("…)")` 这类右括号不该提前收尾），
     * 且两种「读不到东西」的情形都响亮失败而不是静默给空串：
     *  - 找不到调用（0 处）；
     *  - 调用括号配不平（源码被截断 / 锚点写歪）——空串会让上一条「实参不含 action」假绿，这是本夹具最坏的失效形状。
     */
    private fun headerArgumentsOfFirstCall(source: String): String {
        val marker = "AppScreenHeader("
        val at = source.indexOf(marker)
        assertTrue(
            at >= 0,
            "源码里找不到 `AppScreenHeader(` 调用锚点（页头族锚点改名 / 屏搬家）：" +
                "此时不允许返回空串让实参判定假绿。JVM 工作目录 ${java.io.File(".").absolutePath}",
        )
        var i = at + marker.length
        var depth = 1
        val out = StringBuilder()
        while (i < source.length && depth > 0) {
            val c = source[i]
            when (c) {
                '(' -> { depth++; out.append(c); i++ }
                ')' -> {
                    depth--
                    if (depth > 0) out.append(c)
                    i++
                }
                '"', '\'' -> {
                    val consumed = consumeLiteral(source, i, c)
                    out.append(source, i, consumed)
                    i = consumed
                }
                else -> { out.append(c); i++ }
            }
        }
        assertTrue(
            depth == 0,
            "`AppScreenHeader(` 调用的实参括号配不平（源码截断或锚点写歪）：不允许拿半截实参去判「不含 action」。" +
                "已读到的实参：$out",
        )
        return out.toString()
    }

    /**
     * 从 [start]（指向定界引号）吞掉一整段字符串/字符字面量，返回其**收尾之后**的下标。
     * 只认反斜杠转义；未闭合则吞到文末（由调用方的配平守卫兜住，不在这里静默）。
     */
    private fun consumeLiteral(text: String, start: Int, quote: Char): Int {
        var j = start + 1
        while (j < text.length) {
            when (text[j]) {
                '\\' -> j += 2
                quote -> return j + 1
                else -> j++
            }
        }
        return text.length
    }
}
