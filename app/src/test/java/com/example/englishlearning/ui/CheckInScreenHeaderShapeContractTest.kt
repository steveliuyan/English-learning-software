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
 *    整文件计数会把这条判据变成永远红、逼人放宽它；
 *  - 该次调用的配平右括号之后**不得紧跟 `{`**：`action` 是末位形参，尾随 lambda 与具名实参等价，
 *    只看实参区间会被「不写 `action`、直接写 `{ … }`」这条路绕开（R-22B.10）。
 *
 * **覆盖面边界**（修复轮 2 收窄措辞；旧版本此处写成「凡把链补回 action 槽的改动都在本条红」式的全称，不成立）：
 *  本类钉的是「**共享页头那一次调用**的两种挂槽形状（具名 `action` / 尾随 lambda）」；
 *  **不钉**「有人干脆不用共享页头、在屏内把私有 `Row` 页头长回来」——那条路归
 *  [SharedComponentUniquenessRatchetTest] 的页头族与 [CheckInScreenHeaderShapeContractTest.checkInScreenMountsTheSharedHeaderExactlyOnce]
 *  （调用数从 1 掉到 0 时本类第一枚方法先红，但「私有页头 + 另一处共享页头」这类形状只有棘轮看得见）。
 *
 * 判据自证有牙：另外几枚方法把**合成字符串**喂给同一提取器与同一判定，
 *  ① `action = { … }` 必须判红；② 只写在注释里的 `action` 必须判绿（证明走的是
 *  [stripComments] 口径而不是子串乱咬）；③ **尾随 lambda** 形状必须判红；
 *  ④ 调用后紧跟 `when (state) {` 的真实形状必须判绿（假阳对照）。合成串只在测试内，**不改生产文件**。
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
                " MyWordsScreen。共享页头那一次调用的两种挂槽形状（具名 `action` / 尾随 lambda）都在本条红" +
                "（connected 那枚只在接设备时才跑，门控看不见）。实际实参：$arguments",
        )
        // 第二断言（R-22B.10）：`action` 是末位形参，尾随 lambda 与具名实参等价 ⇒ 实参区间干净不代表槽位空。
        assertTrue(
            !headerCallHangsActionSlot(source),
            "打卡页页头那次调用不得以**尾随 lambda** 挂槽：`components/AppScreenHeader.kt` 的" +
                "`action: @Composable (() -> Unit)? = null` 是**末位形参**，" +
                "Kotlin 里 `AppScreenHeader(…) { … }` 与 `AppScreenHeader(…, action = { … })` 等价，" +
                "所以「实参区间里没有 `action` 词元」挡不住这条补链路径。" +
                "注意本探测只看配平右括号之后的**首个非空白字符**——真实源码那里是 `when (state) {` 的 `w`，" +
                "属于后续语句、不算挂槽（对照见 aFollowingWhenBlockIsNotMistakenForATrailingLambda）。实际实参：$arguments",
        )
    }

    /**
     * 反事实自证（第一种挂槽形状）：把 `action = { … }` 喂给**同一提取器 + 同一判定**必须判红。
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
     * 反事实自证（假阳腿）：`action` 只出现在**注释**里时必须判绿。
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
     * 反事实自证（第三腿 / R-22B.10）：`AppScreenHeader` 的 `action` 是**末位形参**
     * （`components/AppScreenHeader.kt`：`action: @Composable (() -> Unit)? = null`），
     * 所以把链补回去的人可以不写 `action` 词元、直接写尾随 lambda：
     * `AppScreenHeader(title = "学习打卡", onBack = onBack) { Text("我的收藏") }`。
     * 这一枚在 Kotlin 里与具名实参**完全等价**（末位形参 = 尾随 lambda 槽），
     * 而「实参区间里不许出现 `action` 词元」这条口径对它是绿的 ⇒ 本腿钉的就是这个漏检形状。
     */
    @Test
    fun theJudgementIsRedWhenTheActionSlotIsHungAsATrailingLambda() {
        val synthetic = stripComments(
            """
            class CheckInScreen {
                fun CheckInScreen(onBack: () -> Unit) {
                    AppScreenHeader(title = "学习打卡", onBack = onBack) { Text("我的收藏"); Text("错词本") }
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            1,
            countMatches(synthetic, headerCall),
            "合成夹具自证：这一枚串确实只有一处页头调用，反事实才有对照意义",
        )
        assertTrue(
            headerCallHangsActionSlot(synthetic),
            "判据失去牙齿：尾随 lambda 就是末位形参 `action` 的具名实参等价形状，" +
                "实参区间零词元不代表槽位是空的。实际实参：${headerArgumentsOfFirstCall(synthetic)}",
        )
    }

    /**
     * 本类的判定入口：那次页头调用**有没有**把内容挂进共享页头。两种挂槽形状都要抓：
     *  1. 具名实参 `action = { … }` —— 落在实参区间里，靠词元判定；
     *  2. **尾随 lambda** `AppScreenHeader(…) { … }` —— 实参区间零词元，靠「配平右括号之后、
     *     跳过分隔空白后的首个非空白字符是不是 `{`」判定。
     *
     * 为什么第 2 条成立：`components/AppScreenHeader.kt` 的 `action: @Composable (() -> Unit)? = null`
     * 是**末位形参**，Kotlin 允许把末位形参的 lambda 实参写在右括号之后，两者语义等价。
     *
     * 假阳面（为什么不是「在剩余文本里找第一个 `{`」）：真实源码 `CheckInScreen.kt:75-78` 那次调用
     * 以 `)` 收尾，紧跟的是 `when (state) {`——它的 `{` 属于**后续语句**，不是这一枚调用的实参。
     * 按「剩余文本第一个 `{`」判定会让本判据对现存代码立刻假红，并误导后来人去改一份合法的生产页头调用。
     * 因此形状必须是：跨过 `)` 之后的**连续空白**（含换行/缩进），只看**首**个非空白字符；
     * `w`（`when (`）、`,`、`)`、`.` 等一律不算挂槽。
     */
    private fun headerCallHangsActionSlot(source: String): Boolean =
        countMatches(headerArgumentsOfFirstCall(source), Regex("""\baction\b""")) >= 1 ||
            firstCharAfterHeaderArgumentsIsTrailingLambdaBrace(source)

    /**
     * 姊妹夹具：第一枚 `AppScreenHeader(` 调用的**配平右括号之后**、跳过连续分隔空白后的首个字符。
     *
     * 返回 `{` ⇔ 那次调用带了尾随 lambda（= 末位形参 `action` 的等价写法）；
     * 调用即文末则返回 null，交由判定视为「没有尾随 lambda」。
     * 只吞空白，绝不向后扫描语句边界——理由见 [headerCallHangsActionSlot] 的假阳面说明。
     */
    private fun firstCharAfterHeaderArgumentsIsTrailingLambdaBrace(source: String): Boolean {
        val afterCloseParen = headerArgumentsAndCloseParenIndexOfFirstCall(source).second
        var i = afterCloseParen
        while (i < source.length && source[i].isWhitespace()) i++
        return i < source.length && source[i] == '{'
    }

    /**
     * 反事实自证（第四腿 / 假阳对照）：真实源码那一枚调用后面紧跟的是 `when (state) {`，
     * 尾随 lambda 探测必须判**绿**。这一腿钉的是探测形状的边界：
     * 若实现退化成「在剩余文本里找第一个 `{`」，本条立刻红，而这个人是无辜的。
     */
    @Test
    fun aFollowingWhenBlockIsNotMistakenForATrailingLambda() {
        val synthetic = stripComments(
            """
            class CheckInScreen {
                fun CheckInScreen(onBack: () -> Unit, state: CheckInUiState) {
                    Column {
                        AppScreenHeader(title = "学习打卡", onBack = onBack)
                        when (state) {
                            CheckInUiState.Loading -> Text("正在读取打卡记录…")
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        assertTrue(
            !headerCallHangsActionSlot(synthetic),
            "假阳：`AppScreenHeader(…)` 之后紧跟 `when (state) {` 属于后续语句，不是尾随 lambda；" +
                "把「剩余文本里第一个 `{`」当成挂槽，会让真实源码的合法形状永远红",
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
    private fun headerArgumentsOfFirstCall(source: String): String =
        headerArgumentsAndCloseParenIndexOfFirstCall(source).first

    /**
     * [headerArgumentsOfFirstCall] 的底层扫描：一并返回**配平右括号之后**的下标，
     * 供尾随 lambda 探测使用（两条判据共用同一次括号配平，避免两份实现走歪）。
     */
    private fun headerArgumentsAndCloseParenIndexOfFirstCall(source: String): Pair<String, Int> {
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
        return out.toString() to i
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
