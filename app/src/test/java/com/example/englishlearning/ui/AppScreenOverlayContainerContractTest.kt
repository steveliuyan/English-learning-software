package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Task 17B 浮层容器契约（批 2 UI 重设计）：`AppScreen.kt` 里那 6 枚裸 `AlertDialog` 必须迁到房内两路共享浮层档，
 * 并且**只能**迁到这两路：
 *  - 路 A（破坏性确认 4 枚）→ `components/ConfirmDestructiveDialog.kt`，正文三要素（级联 → 保留面 → 不可撤销）
 *    由组件的 `destructiveDialogBody` 承载，站点只许逐字给 `cascadeLines` / `keepLines` 与动作；
 *  - 路 B（非破坏选择 2 枚）→ `components/glass/GlassDialog.kt` + 共享 `PillButton`，
 *    自渲染的列表项 tag（`settings_export_book_$bookId`）必须原地保留。
 *
 * 判据形状沿用 17A 的成例（`AppBottomBarContractTest` 的 `stripComments` + `extractFunctionBody`
 * 「体内恰一处 / 体外零处」夹击）：本席按同一口径读源码，再按**调用区间**取窗——
 * 「一枚 `AlertDialog(` 藏在 `if (false)` 死分支里」与「只迁 3 枚留 3 枚」两形都必须当场判红；
 * 只数函数声明、或只问字面在不在文件里，都挡不住这两发（Task 15/16/17A 三处同源的教训）。
 *
 * 逐枚的**实参形状 + 信息要素逐字**一起钉：级联条数、保留面、词书名与日期的插值本体都按字面命中，
 * 因此改措辞、丢条数、丢保留面、把动作换成另一枚调用都会打掉某一条锚。站点自写的
 * 「清空不可撤销。」「删除不可撤销。」一类由组件固定句「此操作不可撤销。」取代，
 * 所以那两枚旧措辞在本文件必须归零（AGENTS.md 业务规则，`ConfirmDestructiveWordingTest` 已在册），
 * 而组件固定句本身也不许被站点重写（重写 = 绕过 `destructiveDialogBody` 配方，措辞契约就管不到它）。
 *
 * 读法只走共享夹具 [uiScreenSource] / [uiComponentSource]（内置「文件恰一个 + 目录守卫 + 剥注释」），
 * 本文件不自写第二份剥注释实现（批 1 终审 F6 的防复发闸盯着这件事）。
 * [extractFunctionBody] / [extractCallRegion] / [balancedRegion] 与 `AppBottomBarContractTest` 的同名私有
 * 夹具同源：那一份是 17A 的**私有**夹具、跨文件不可见，本席按 brief「沿用 17A 成例」在本席重写一份，
 * 而不是改动已关账的 17A；批 3 若做测试夹具归并，两处一起搬进 `SourceRatchet.kt`。
 */
class AppScreenOverlayContainerContractTest {

    // ---------- 锚点 ----------

    private val appScreenSignature = "fun AppScreen("
    private val learningSetupSignature = "internal fun LearningSetupScreen("

    private val destructiveCall = "ConfirmDestructiveDialog("
    private val glassCall = "GlassDialog("
    private val alertDialogCall = "AlertDialog("
    private val alertDialogImport = "androidx.compose.material3.AlertDialog"

    /** 路 A 实参形状：一枚调用内恰一处，缺一枚就是「少说一件」（级联 / 保留面 / 不可撤销旗标 / 标签 / 动作）。 */
    private val destructiveArgShapes = listOf(
        "state = true,",
        "title = ",
        "cascadeLines = ",
        "keepLines = ",
        "irreversible = true,",
        "confirmLabel = ",
        "onConfirm = ",
        "onDismiss = ",
    )

    /** 只有路 A 用得着的形状：窗口内计数之和必须等于全文件计数（多出的一份就是窗外死分支/幽灵调用塞的）。 */
    private val routeAOnlyShapes = listOf(
        "state = true,",
        "cascadeLines = ",
        "keepLines = ",
        "irreversible = true,",
        "confirmLabel = ",
    )

    /** 退役的站点私有 tag（路 A 迁完后随组件固定 tag `confirm_destructive_*` 收敛；控制者实测零测试引用）。 */
    private val retiredDestructiveTags = listOf(
        "search_history_clear_confirm",
        "search_history_clear_cancel",
        "version_delete_confirm",
        "version_delete_cancel",
        "delete_all_learning_data_confirm",
        "delete_all_learning_data_cancel",
        "wordbook_delete_confirm",
        "wordbook_delete_cancel",
    )

    /** 站点自写的「不可撤销」句（迁移前实测三处：`:660`、`:1332`、`:1717`），一律交组件固定句承载。 */
    private val selfWrittenIrreversibleLines = listOf("清空不可撤销。", "删除不可撤销。")

    /** 组件的固定句：站点重写它 = 绕过 `destructiveDialogBody` 配方。 */
    private val componentIrreversibleLine = "此操作不可撤销。"

    /** 路 A 组件签名的 8 枚形参（本体冻结，不许为迁站点加第 9 枚）。 */
    private val routeAParameters = listOf(
        "state: Boolean",
        "title: String",
        "cascadeLines: List<String>",
        "keepLines: List<String>",
        "irreversible: Boolean = true",
        "confirmLabel: String",
        "onConfirm: () -> Unit",
        "onDismiss: () -> Unit",
    )

    // ---------- 读取 ----------

    /**
     * `AppScreen.kt` 剥注释后的源码。共享夹具内置「屏文件形状 + 恰一个文件 + 父目录恰为 `ui/` 根」三道守卫，
     * 缺失 / 改名 / 搬家都会当场点名，而不是让下面的 `== 0` 断言静默空转。
     */
    private fun appScreenSource(): String {
        val source = uiScreenSource("AppScreen.kt")
        assertTrue(
            source.isNotBlank(),
            "AppScreen.kt 剥注释后为空：契约不许对空文件判绿（文件被搬空 == 没有契约）",
        )
        return source
    }

    // ---------- 配平取窗夹具（与 17A 同口径：字符串感知、未闭合即 fail） ----------

    /**
     * 从 [signature] 锚定函数的花括号体（不含最外层括号）。
     * 字符串字面量整体跳过（串里的 `${…}` 花括号不参与配对）；未闭合直接 fail——
     * 配对器失准时「体外计数」会把体内锚算成体外，必须响亮报错而不是静默切歪。
     */
    private fun extractFunctionBody(stripped: String, signature: String): String {
        val start = stripped.indexOf(signature)
        assertTrue(start >= 0, "找不到函数签名 \"$signature\"：锚点搬家或改名，契约不许空转")
        val open = stripped.indexOf('{', start)
        assertTrue(open >= 0, "$signature 之后找不到函数体起始花括号")
        val region = balancedRegion(stripped, open, '{', '}')
        return stripped.substring(region.first + 1, region.second)
    }

    /**
     * 一枚调用的**完整调用区间**：从 `(` 起配平到 `)`，再并上紧随其后的尾随 lambda 块。
     * 路 B 的 `GlassDialog(onDismiss = …) { 内容 }` 内容长在尾随 lambda 里，只取实参括号
     * 会把 `PillButton(` 与列表项 tag 算到窗外，那些锚就没人管了。
     */
    private fun extractCallRegion(stripped: String, callStart: Int, anchor: String): String {
        val paren = callStart + anchor.length - 1
        assertTrue(
            stripped[paren] == '(',
            "$anchor 之后第 ${paren - callStart} 位不是左括号：调用形状与本夹具口径不符，拒绝猜测",
        )
        val args = balancedRegion(stripped, paren, '(', ')')
        var end = args.second + 1
        val nextBrace = firstNonWhitespace(stripped, end)
        if (nextBrace != null && stripped[nextBrace] == '{') {
            end = balancedRegion(stripped, nextBrace, '{', '}').second + 1
        }
        return stripped.substring(callStart, end)
    }

    /** 从 [open]（必为 [openChar]）配平到同名闭合字符，返回闭合两端下标；字符串整段跳过，未闭合即 fail。 */
    private fun balancedRegion(text: String, open: Int, openChar: Char, closeChar: Char): Pair<Int, Int> {
        assertTrue(text[open] == openChar, "取窗起点不是 $openChar：本夹具只接受指向 $openChar 的下标")
        var depth = 0
        var i = open
        var inString = false
        var escaped = false
        while (i < text.length) {
            val c = text[i]
            when {
                inString -> when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }

                c == '"' -> inString = true
                c == openChar -> depth++
                c == closeChar -> {
                    depth--
                    if (depth == 0) return Pair(open, i)
                }
            }
            i++
        }
        fail("从下标 $open 起的 $openChar…$closeChar 未闭合：配对器无法界定区间，本契约拒绝猜测")
    }

    private fun firstNonWhitespace(text: String, from: Int): Int? {
        var i = from
        while (i < text.length) {
            if (!text[i].isWhitespace()) return i
            i++
        }
        return null
    }

    private fun count(hay: String, needle: String): Int = Regex(needle, RegexOption.LITERAL).findAll(hay).count()

    private fun occurrencesOf(hay: String, needle: String): List<Int> {
        val out = mutableListOf<Int>()
        var index = hay.indexOf(needle)
        while (index >= 0) {
            out += index
            index = hay.indexOf(needle, index + needle.length)
        }
        return out
    }

    // ---------- 浮层调用取样 ----------

    private enum class OverlayKind { Destructive, Glass }

    private data class OverlayCall(val kind: OverlayKind, val start: Int, val region: String)

    /** 按出现顺序取出全部浮层调用的完整区间（两类锚点互不为前缀，计数不会互相吞）。 */
    private fun overlayCalls(source: String): List<OverlayCall> {
        val found = mutableListOf<OverlayCall>()
        listOf(destructiveCall to OverlayKind.Destructive, glassCall to OverlayKind.Glass).forEach { (anchor, kind) ->
            occurrencesOf(source, anchor).forEach { start ->
                found += OverlayCall(kind = kind, start = start, region = extractCallRegion(source, start, anchor))
            }
        }
        return found.sortedBy { it.start }
    }

    private fun destructiveCallsIn(source: String): List<OverlayCall> =
        overlayCalls(source).filter { it.kind == OverlayKind.Destructive }

    /** 按路 A 标题字面认枚：标题是「这一枚是谁」的唯一真值，换站点或改标题都只会判不出窗口，不会被别的窗口蒙中。 */
    private fun destructiveRegionByTitle(source: String, titleLiteral: String): String {
        val matches = destructiveCallsIn(source).filter { it.region.contains("title = \"$titleLiteral\"") }
        assertEquals(
            1,
            matches.size,
            "路 A 站点「$titleLiteral」必须恰一枚且标题逐字为 title = \"$titleLiteral\"，实际 ${matches.size} 枚",
        )
        return matches.single().region
    }

    private fun glassRegionContaining(source: String, literal: String): String {
        val matches = overlayCalls(source).filter { it.kind == OverlayKind.Glass && it.region.contains(literal) }
        assertEquals(1, matches.size, "路 B 站点（含字面「$literal」）必须恰一枚，实际 ${matches.size} 枚")
        return matches.single().region
    }

    // ---------- 可达性锚判据（修复轮 1，Minor-6） ----------

    /** 一枚浮层在其宿主内的可达锚：守卫字面 + 所属路的调用锚 + 身份逐字。 */
    private data class GuardSite(val label: String, val guard: String, val anchor: String, val identity: String)

    /** 守卫块开口到调用之间不许出现的控制流关键字（`if (false) { … }` 那一族的共同特征）。 */
    private val branchKeyword = Regex("""\b(?:if|else|when|for|while|do|try|catch|finally)\b""")

    /**
     * 「浮层直接长在守卫的活分支下」四道判据（对任意 source 成立，真树与合成样片走同一条）：
     *  ① 守卫字面全文件**恰一处**——0 处 = 调用不在守卫块里（搬去死分支/别处）；≥2 处 = 第二枚同文守卫冒充；
     *  ② 守卫配平块从开口到该调用之间**没有控制流关键字**——整枚再套一层 `if (false)` 会在这段留下 `if`，
     *     当场红；普通取值声明（`val preview = state.deletePreview`）与 lambda 形参表不放行关键字，不误伤；
     *  ③ 块内本路调用**恰一枚**且另一路浮层零枚——同守卫下塞幽灵副本、或两路互套都判红；
     *  ④ 块内命中该枚的身份逐字——「恰一枚」不许被别的浮层顶替。
     */
    private fun assertOverlayGuardedUnderLiveBranch(source: String, site: GuardSite) {
        val guards = occurrencesOf(source, site.guard)
        assertEquals(
            1,
            guards.size,
            "站点「${site.label}」的守卫字面「${site.guard}」必须全文件恰一处（实测 ${guards.size} 处）：" +
                "0 处 = 这枚浮层不在守卫的活分支里；≥2 处 = 有同文守卫冒充，或整枚被搬进死分支后原地留了影子",
        )
        val guardPos = guards.single()
        val brace = site.guard.lastIndexOf('{')
        assertTrue(brace >= 0, "守卫字面「${site.guard}」不带左花括号：可达性锚只服务「守卫 → 块」形状")
        val block = balancedRegion(source, guardPos + brace, '{', '}')
        val blockText = source.substring(block.first + 1, block.second)
        val otherAnchor = if (site.anchor == destructiveCall) glassCall else destructiveCall
        assertEquals(0, count(blockText, otherAnchor), "站点「${site.label}」的守卫块里混进了另一路浮层「$otherAnchor」：两路不许互套")
        assertEquals(
            1,
            count(blockText, site.anchor),
            "站点「${site.label}」的守卫块内「${site.anchor}」必须恰一枚（实测 ${count(blockText, site.anchor)} 枚：两枚 = 死分支幽灵副本也塞在同一守卫下）",
        )
        val callOffset = blockText.indexOf(site.anchor)
        assertTrue(
            !branchKeyword.containsMatchIn(blockText.substring(0, callOffset)),
            "站点「${site.label}」没直接长在守卫「${site.guard}」的活分支上：调用之前出现了控制流关键字" +
                "（模式 ${branchKeyword.pattern}）——整枚再套一层 if (false)/when 就是死分支，份数/形状/逐字三例都看不见",
        )
        assertTrue(
            site.identity in blockText,
            "守卫「${site.guard}」块内的调用不是站点「${site.label}」：身份逐字「${site.identity}」没命中",
        )
    }

    /** 从 [anchor] 之后配平取出**实参括号窗口**（含两端括号）；锚缺失即 fail，不许静默给空窗。 */
    private fun signatureWindow(declaration: String, anchor: String): String {
        val start = declaration.indexOf(anchor)
        assertTrue(start >= 0, "找不到签名锚 \"$anchor\"：解析链不许对缺锚的输入空转")
        val parens = balancedRegion(declaration, start + anchor.length, '(', ')')
        return declaration.substring(parens.first, parens.second + 1)
    }

    /** 实参括号窗口 → 逐枚形参名单（按逗号切、trim、滤空；本仓签名带尾逗号，尾逗号不许成枚）。 */
    private fun splitParameters(signature: String): List<String> =
        signature.removePrefix("(").removeSuffix(")").split(',').map { it.trim() }.filter { it.isNotEmpty() }

    // ---------- 用例 1：裸 `AlertDialog` 族与其实导入、旧措辞一起归零 ----------

    @Test
    fun `bare alert dialog family, its import and the self written irreversible lines are gone`() {
        val source = appScreenSource()
        assertEquals(0, count(source, alertDialogCall), "AppScreen.kt 仍有 ${count(source, alertDialogCall)} 处裸 AlertDialog(：容器必须交两路共享档承载")
        assertEquals(0, count(source, alertDialogImport), "AppScreen.kt 仍导入 material3 AlertDialog（容器已由共享组件承载）")
        selfWrittenIrreversibleLines.forEach { literal ->
            assertEquals(0, count(source, literal), "站点仍自写「$literal」：不可撤销声明必须交组件固定句承载")
        }
        assertEquals(0, count(source, componentIrreversibleLine), "站点重写组件固定句 = 绕过 destructiveDialogBody 配方，措辞契约将管不到这一行")
        retiredDestructiveTags.forEach { tag ->
            assertEquals(0, count(source, tag), "路 A 站点私有 tag「$tag」未随组件固定 tag 收敛（实测零测试引用，可直接退役）")
        }
        listOf("confirm_destructive_confirm", "confirm_destructive_dismiss").forEach { tag ->
            assertEquals(
                0,
                count(source, tag),
                "站点不许自写组件固定 tag「$tag」：那是 ConfirmDestructiveDialog.kt 的资产，抄过来就是第二份标签口径",
            )
        }
        // 反空转（M1 的击穿形状）：同一具计数器必须数得出「死分支里塞一份」的副本，否则上面那个 0 是恒真。
        val ghost = "if (false) { $alertDialogCall onDismissRequest = {} } }"
        assertEquals(1, count(ghost, alertDialogCall), "反空转失控：死分支里的 AlertDialog( 副本没被数出来")
        assertEquals(
            1,
            count("import androidx.compose.material3.AlertDialog\n", alertDialogImport),
            "反空转失控：合成导入行没被数出来（导入腿是恒真的）",
        )
        assertEquals(1, count("Text(\"删除不可撤销。\", fontWeight = FontWeight.Bold)", "删除不可撤销。"), "反空转失控：旧措辞样片没被数出来")
    }

    // ---------- 用例 2：逐位钉数 —— 路 A 恰 7 枚、路 B 恰 2 枚，且都长在生效宿主函数体内 ----------

    @Test
    fun `nine overlays route to exactly seven destructive confirmations and two glass pickers`() {
        val source = appScreenSource()
        assertEquals(
            7,
            count(source, destructiveCall),
            "ConfirmDestructiveDialog( 必须恰 7 枚（清空搜索历史 / 删除这一版 / 删除全部学习数据 / 删除词书 / " +
                "删除这套 AI 配置 / 清除已保存的密钥 / 删除一条拍照翻译记录），实际 ${count(source, destructiveCall)}",
        )
        assertEquals(2, count(source, glassCall), "GlassDialog( 必须恰 2 枚（导出词书选择器 / 发现已学过的单词），实际 ${count(source, glassCall)}")
        val calls = overlayCalls(source)
        assertEquals(9, calls.size, "浮层调用取样必须恰 9 枚（迁 3 留 3、或多迁一枚都会掉在这里）")
        assertEquals(7, calls.count { it.kind == OverlayKind.Destructive }, "路 A 取样份数不对")
        assertEquals(2, calls.count { it.kind == OverlayKind.Glass }, "路 B 取样份数不对")

        // 体内/体外夹击：6+1 枚属于 AppScreen 宿主，1+1 枚属于 LearningSetupScreen 宿主，两体之外一枚都不许有。
        val appScreenBody = extractFunctionBody(source, appScreenSignature)
        val setupBody = extractFunctionBody(source, learningSetupSignature)
        assertEquals(
            6,
            count(appScreenBody, destructiveCall),
            "AppScreen 函数体内应有 6 枚破坏性确认（清空搜索历史 / 删除这一版 / 删除全部学习数据 / 删除 AI 配置 / 清除密钥 / 删除一条拍照翻译记录）",
        )
        assertEquals(1, count(appScreenBody, glassCall), "AppScreen 函数体内应有 1 枚玻璃选择器（导出词书）")
        assertEquals(1, count(setupBody, destructiveCall), "LearningSetupScreen 函数体内应有 1 枚破坏性确认（删除词书）")
        assertEquals(1, count(setupBody, glassCall), "LearningSetupScreen 函数体内应有 1 枚玻璃选择器（发现已学过的单词）")
        assertEquals(
            7,
            count(appScreenBody, destructiveCall) + count(setupBody, destructiveCall),
            "路 A 有一枚不在两个宿主函数体内：幽灵函数或窗外死分支里的假调用不算生效渲染路径",
        )
        assertEquals(
            2,
            count(appScreenBody, glassCall) + count(setupBody, glassCall),
            "路 B 有一枚不在两个宿主函数体内（同上）",
        )

        // 取窗器自证：两枚宿主体切歪时上面的体内计数只是巧合，所以切割点必须单独钉。
        assertTrue(
            appScreenBody.contains("learningDataDeletionViewModel?.cancelDelete()"),
            "取窗器切歪：AppScreen 体内读不到「删除全部学习数据」的动作，函数体在站点之前就被截断",
        )
        assertTrue(
            !appScreenBody.contains(learningSetupSignature),
            "取窗器切歪：AppScreen 体吞进了 LearningSetupScreen 的定义（右括号配平错位）",
        )
        assertTrue(
            setupBody.contains("WordBookPicker("),
            "取窗器切歪：LearningSetupScreen 体没覆盖到词书选择器的调用",
        )

        // 反空转（M2 的击穿形状）：少迁一枚（4 变 3）必须被这套份数逻辑判出来。
        val shortByOne = "$destructiveCall state = true, title = \"a\")\n" +
            "$destructiveCall state = true, title = \"b\")\n" +
            "$destructiveCall state = true, title = \"c\")\n" +
            "$glassCall onDismiss = {}) {}\n"
        val sampled = overlayCalls(shortByOne).count { it.kind == OverlayKind.Destructive }
        assertEquals(3, sampled, "反空转失控：三枚路 A 的样片被取样成 $sampled 枚，份数判据没有牙齿")
        assertTrue(sampled != 4, "反空转失控：份数判据恒真")
    }

    // ---------- 用例 3：路 A 每枚调用的实参形状恰一处 + 窗外零处 ----------

    @Test
    fun `each destructive confirmation carries the full argument shape exactly once`() {
        val source = appScreenSource()
        val routeA = destructiveCallsIn(source)
        assertEquals(7, routeA.size, "路 A 取样份数不对，形状判据无从谈起")
        routeA.forEach { call ->
            destructiveArgShapes.forEach { shape ->
                assertEquals(
                    1,
                    count(call.region, shape),
                    "路 A 调用（起于下标 ${call.start}）内「$shape」必须恰一处，实际 ${count(call.region, shape)} 次",
                )
            }
            assertEquals(0, count(call.region, alertDialogCall), "路 A 调用区间内不得残留 AlertDialog(")
            assertEquals(0, count(call.region, glassCall), "一枚路 A 调用区间内不得嵌 GlassDialog(（两路不混用）")
        }
        // 体内恰一处 ⇒ 体外零处：全文件计数 == 7 == 七枚窗口之和。
        routeAOnlyShapes.forEach { shape ->
            val inWindows = routeA.sumOf { count(it.region, shape) }
            assertEquals(7, count(source, shape), "「$shape」全文件计数不等于 7：窗外多出来的一份 = 死分支/幽灵调用塞的假形状")
            assertEquals(7, inWindows, "「$shape」有命中落在七枚路 A 调用区间之外")
        }
        // 反空转（修复轮 1，Important-3）：路 A 缺 keepLines 的那一枚必须被**真判据**打掉。旧版只验
        // 「计数器能数出 needle」（`assertEquals(0, count(missingKeep, "keepLines = "))` 由样片构造必真，
        // 取窗器歪了、形状循环写错了，锚都不咬）。现在两枚样片走主判据同一条谓词——
        // extractCallRegion 取窗 + 逐形参「恰一处」（与用例 2 那枚取样器反空转同族写法）：
        // 缺 keepLines 的样片必须**恰被点名 keepLines 这一枚**；完整样片必须零违例（判据不恒假）。
        val missingKeep = "$destructiveCall state = true,\n    title = \"删除这一版？\",\n    cascadeLines = listOf(\"a\"),\n" +
            "    irreversible = true,\n    confirmLabel = \"确认删除\",\n    onConfirm = { vm.confirm() },\n    onDismiss = { vm.cancel() },\n)"
        val completeShape = "$destructiveCall state = true,\n    title = \"删除这一版？\",\n    cascadeLines = listOf(\"a\"),\n" +
            "    keepLines = listOf(\"b\"),\n    irreversible = true,\n    confirmLabel = \"确认删除\",\n" +
            "    onConfirm = { vm.confirm() },\n    onDismiss = { vm.cancel() },\n)"
        fun shapeViolations(region: String) = destructiveArgShapes.filter { count(region, it) != 1 }
        assertEquals(
            listOf("keepLines = "),
            shapeViolations(extractCallRegion(missingKeep, 0, destructiveCall)),
            "反空转失控：缺 keepLines 的样片没被真判据**恰点名 keepLines 这一枚**（取窗或形状循环是坏的）",
        )
        assertTrue(
            shapeViolations(extractCallRegion(completeShape, 0, destructiveCall)).isEmpty(),
            "反空转失控：完整样片被真判据误伤（判据恒假，主用例随之全是假红）",
        )
    }

    // ---------- 用例 4：六枚的信息要素、tag 与动作逐字保留 ----------

    @Test
    fun `each migrated overlay keeps its own numbers, kept surfaces, tags and actions verbatim`() {
        val source = appScreenSource()

        // 站点 1（迁移前 `:647`）清空搜索历史。
        val searchClear = destructiveRegionByTitle(source, "清空搜索历史？")
        listOf(
            "将删除本机全部 \${pending.recordCount} 条搜索记录。",
            "将删除本机全部搜索记录（暂时读不到具体条数）。",
            "收藏、文章与学习记录不受影响。",
            "confirmLabel = \"确认清空\"",
            "onConfirm = { globalSearch?.clearHistory() }",
            "onDismiss = { globalSearch?.cancelClearHistory() }",
        ).forEach { literal ->
            assertEquals(1, count(searchClear, literal), "「清空搜索历史」一枚的要素/动作变了（逐字保留是红线）：$literal")
        }

        // 站点 2（迁移前 `:1325`）删除这一版。
        val versionDelete = destructiveRegionByTitle(source, "删除这一版？")
        listOf(
            "将删除《\${pending.article.title}》第 \${pending.article.version} 版（\${pending.article.localDate}）。",
            "级联影响：该版阅读完成记录 \${pending.impact.removedCompletionCount} 条将一并删除；" +
                "同组剩余 \${pending.impact.remainingVersionCount} 个版本可读。",
            "confirmLabel = \"确认删除\"",
            "onConfirm = { articleVersionViewModel?.confirmDelete() }",
            "onDismiss = { articleVersionViewModel?.cancelDelete() }",
        ).forEach { literal ->
            assertEquals(1, count(versionDelete, literal), "「删除这一版」一枚的条数/动作变了：$literal")
        }

        // 站点 3（迁移前 `:1702`）删除全部学习数据。
        val wipeAll = destructiveRegionByTitle(source, "删除全部学习数据？")
        listOf(
            "将删除学习记录 \${pending.eventCount} 条、复习调度 ",
            "\${pending.reviewStateCount} 条、每日计划 \${pending.planCount} 天、",
            "阅读完成 \${pending.completionCount} 条、迁移审计 \${pending.migrationAuditCount} 条。",
            "文章 \${pending.keptArticleCount} 篇与收藏 \${pending.keptFavoriteCount} 条保留；",
            "词书、搜索历史与设置不受影响。",
            "confirmLabel = \"确认删除\"",
            "onConfirm = { learningDataDeletionViewModel?.confirmDelete(state.profile.id) }",
            "onDismiss = { learningDataDeletionViewModel?.cancelDelete() }",
        ).forEach { literal ->
            assertEquals(1, count(wipeAll, literal), "「删除全部学习数据」一枚的条数/保留面/动作变了：$literal")
        }

        // 站点 4（迁移前 `:1873`）删除词书（标题带词书名插值）。
        val wordBookDelete = destructiveRegionByTitle(source, "删除《\${book.displayName}》？")
        listOf(
            "将删除这册导入词书的词卡文件与词条 \${preview.entryCount} 条。",
            "将删除这册导入词书的词卡文件与全部词条内容。",
            "学习历史与文章保留：该册学习记录 \${preview.keptEventCount} 条、文章 \${preview.keptArticleCount} 篇仍可翻阅（复习状态与生词笔记同保留）。",
            "本机学习历史与文章保留，仍可翻阅；暂时读不到具体条数。",
            "其他词书不受影响。",
            "confirmLabel = \"删除\"",
            "onConfirm = { viewModel.confirmDelete(profileId) }",
            "onDismiss = viewModel::cancelDelete",
        ).forEach { literal ->
            assertEquals(1, count(wordBookDelete, literal), "「删除词书」一枚的级联/保留面/动作变了：$literal")
        }

        // 站点 4b（批 4 Task 3）删除这套 AI 配置：元数据与已保存的密钥一起走，保留面要说全。
        val aiProfileDelete = destructiveRegionByTitle(source, "删除这套 AI 配置？")
        listOf(
            "将删除这台设备上这套配置的名称、网站地址、Endpoint、模型与高级参数，并清除已保存的密钥。",
            "文章、收藏、学习记录与词库不受影响；其他 AI 配置也不受影响。",
            "confirmLabel = \"确认删除\"",
            "onConfirm = {",
            "aiProfileViewModel?.deleteProfile(profileId)",
            "onDismiss = { pendingAiProfileDelete = null }",
        ).forEach { literal ->
            assertEquals(1, count(aiProfileDelete, literal), "「删除这套 AI 配置」一枚的级联/保留面/动作变了：$literal")
        }

        // 站点 4c（批 4 Task 3）清除已保存的密钥：元数据全留，只丢密钥。
        val aiProfileKeyClear = destructiveRegionByTitle(source, "清除已保存的密钥？")
        listOf(
            "清除后这套配置将无法调用服务，需要重新粘贴密钥才能再用。",
            "名称、网站地址、Endpoint、模型与高级参数保留；文章、收藏与学习记录不受影响。",
            "confirmLabel = \"确认清除\"",
            "onConfirm = {",
            "aiProfileViewModel?.deleteKey(profileId)",
            "onDismiss = { pendingAiProfileKeyClear = null }",
        ).forEach { literal ->
            assertEquals(1, count(aiProfileKeyClear, literal), "「清除已保存的密钥」一枚的级联/保留面/动作变了：$literal")
        }

        // 站点 4d（批 4 Task 4）删除一条拍照翻译记录：原图与译文一起走，保留面要说全。
        val photoTranslationDelete = destructiveRegionByTitle(source, "删除这条翻译记录？")
        listOf(
            "将删除这条已保存的原文、译文，以及随它保存的原图（图片文件同时从本机清除）。",
            "其他历史记录、词库、文章与学习记录不受影响。",
            "confirmLabel = \"确认删除\"",
            "onConfirm = {",
            "photoTranslationViewModel?.deleteSaved(state.profile.id, recordId)",
            "onDismiss = { pendingPhotoTranslationDelete = null }",
        ).forEach { literal ->
            assertEquals(1, count(photoTranslationDelete, literal), "「删除这条翻译记录」一枚的级联/保留面/动作变了：$literal")
        }

        // 站点 5（迁移前 `:1608`）导出词书选择器（路 B）：列表项 tag 原地保留，取消动作逐字不变。
        val exportPicker = glassRegionContaining(source, "选择要导出的词书")
        listOf(
            "GlassDialog(onDismiss = { showWordBookExportPicker = false })",
            "text = \"选择要导出的词书\"",
            "AppType.Headline",
            "AppType.Body",
            "settings_export_book_\$bookId",
            "exportWordBookId = bookId",
            "exportLauncher.launch(\"\$bookId.wbpack\")",
            "testTag = \"wordbook_export_cancel\"",
            "text = \"取消\"",
        ).forEach { literal ->
            assertEquals(1, count(exportPicker, literal), "「选择要导出的词书」一枚的要素/tag/动作变了：$literal")
        }
        assertEquals(1, count(exportPicker, "PillButton("), "导出选择器只有「取消」一枚动作，必须走共享 PillButton 且不得长出第二枚")
        assertEquals(
            count(exportPicker, "PillButton("),
            count(exportPicker, "Button("),
            "路 B 正文按钮不许手搓 Button(：`Button(` 计数必须全部由共享 PillButton 贡献（PillButton 含 Button 子串，两数相等才说明没有裸按钮）",
        )

        // 站点 6（迁移前 `:1913`）发现已学过的单词（路 B）：二选一两个动作逐字保留。
        val migration = glassRegionContaining(source, "发现已学过的单词")
        listOf(
            "GlassDialog(onDismiss = { viewModel.resolveMigration(profileId, apply = false) })",
            "text = \"发现已学过的单词\"",
            "目标词书里有 \${preview.candidates.size} 个单词你之前已经学过，要把它们标记为已学习吗？原词书的学习记录会原样保留。",
            "testTag = \"wordbook_migration_confirm\"",
            "testTag = \"wordbook_migration_skip\"",
            "viewModel.resolveMigration(profileId, apply = true)",
        ).forEach { literal ->
            assertEquals(1, count(migration, literal), "「发现已学过的单词」一枚的条数/tag/动作变了：$literal")
        }
        // `apply = false` 这枚动作本来就有两处落点（返回手势的 onDismiss + 「暂不标记」按钮），
        // 迁移后仍是两处：一枚进容器形参、一枚进 PillButton 的 onClick，谁也不许多一份或少一份。
        assertEquals(
            2,
            count(migration, "viewModel.resolveMigration(profileId, apply = false)"),
            "「暂不标记」语义的动作必须恰两处（容器 onDismiss + 第二枚按钮）：少一处 = 按返回即静默标记，多一处 = 幽灵动作",
        )
        assertEquals(2, count(migration, "PillButton("), "迁移弹窗是一对二选一按钮，两枚都必须走共享 PillButton")
        assertEquals(1, count(migration, "AppType.Body"), "路 B 正文必须走 AppType.Body（房内约定形状：玻璃面板内前景逐枚显式配 `color = appColors().text1`，取样看共享件 `components/glass/GlassDialog.kt` 的面板内容与 `OutboundConfirmDialog` 的正文行）")

        // 路 B 自渲染 tag 不许跟着路 A 一起退役：窗外零处复核。
        listOf("settings_export_book_\$bookId", "wordbook_migration_confirm", "wordbook_migration_skip").forEach { tag ->
            assertEquals(1, count(source, tag), "路 B 自渲染 tag「$tag」必须恰一处保留（它不是路 A 的组件固定 tag，不许收敛）")
        }
        assertEquals(3, count(source, "PillButton("), "AppScreen.kt 的药片调用必须恰 3 枚（导出取消 1 + 迁移二选一 2），不得手搓按钮")
    }

    // ---------- 用例 5：路 A 组件本体冻结（不许为迁站点加参数） ----------

    @Test
    fun `the frozen route A component keeps its eight parameter signature`() {
        val component = uiComponentSource("ConfirmDestructiveDialog.kt")
        val signatureAnchor = "fun ConfirmDestructiveDialog"
        val signature = signatureWindow(component, signatureAnchor)
        // 按「切枚 + 逐枚比对名单」判，不按逗号计数判：本仓签名带尾逗号，逗号数会把尾逗号也算成一枚参数。
        val parameters = splitParameters(signature)
        assertEquals(routeAParameters, parameters, "路 A 组件签名被改动（本体冻结：参数名、顺序与默认值都不许为迁站点改动）")
        routeAParameters.forEach { parameter ->
            assertEquals(1, count(signature, parameter), "路 A 组件签名里的「$parameter」应恰一处（不许把两枚参数并成一枚）")
        }
        // 反空转（修复轮 1，Important-3）：旧写法是 `parameters + "enabled…"` 的 List 拼接——由构造必真，
        // 解析链整体坏掉（取窗歪、分隔符错、trim/filter 吞枚）它照样绿，注释承诺的「走一遍解析+比对」根本没发生。
        // 现在合成一枚**九参签名字符串**，用与主判据**同一条** signatureWindow + splitParameters 解析它：
        // 解析出的名单必须与八参名单**不等**、且恰 9 枚。再补同链正向自证：把八参名单原样拼回签名解析，
        // 必须逐枚相等——证明上面那条「不等」是第九枚参数造成的，不是解析链本来就对不上名单。
        val nineParamSample = "fun ConfirmDestructiveDialog(" + routeAParameters.joinToString(", ") + ", enabled: Boolean = true,)"
        val parsedNine = splitParameters(signatureWindow(nineParamSample, "fun ConfirmDestructiveDialog"))
        assertEquals(
            routeAParameters.size + 1,
            parsedNine.size,
            "反空转失控：九参样片没被解析成 9 枚（实测 ${parsedNine.size} 枚——解析链与主判据不是同一条，或样片形状不合）",
        )
        assertTrue(
            parsedNine != routeAParameters,
            "反空转失控：九参样片经同链解析后仍等于八参名单，名单比对这条腿是恒真的",
        )
        val eightParamSample = "fun ConfirmDestructiveDialog(" + routeAParameters.joinToString(", ") + ",)"
        assertEquals(
            routeAParameters,
            splitParameters(signatureWindow(eightParamSample, "fun ConfirmDestructiveDialog")),
            "反空转失控：同链解析连合法八参样片都对不上名单，上一条「不等」就没有意义（解析链坏了要走响亮报错，不是靠假不等）",
        )
    }

    // ---------- 用例 6（修复轮 1，Minor-6）：可达性锚——六枚浮层必须直接长在活分支守卫下 ----------

    /**
     * 补的洞：用例 1/2/3/4 全是**字符串判据**，分不清活分支与死分支——评审点名形状「整枚浮层塞进
     * `if (false) { … }`」时份数、形状、逐字三例照绿，而设备侧只有 2 枚有背书（路 A 删词书、路 B 导出选择器），
     * 其余 4 枚可以「死着绿」。本用例按现树实际形状逐枚钉守卫字面（六枚各恰一处）：
     *  `pendingClear?.let { pending ->` / `pendingDelete?.let { pending ->` / `if (pending != null) {` /
     *  `state.pendingDelete?.let { book ->` / `if (showWordBookExportPicker) {` / `state.pendingMigration?.let { preview ->`
     * 四道判据见 [assertOverlayGuardedUnderLiveBranch]。红样留档（修复轮 1）：把站点 4 整枚塞进
     *  `if (false)` 后，旧五例照绿（`fix17b-item4-oldshape-passes.log`）、本例判红
     *  （`fix17b-item4-deadbranch-red.log`）；撤变异复绿。合成对拍：同一条判据拒 `if (false)` 包裹样片、
     *  放裸守卫样片——本例自己也不许是恒真式。
     */
    @Test
    fun `every overlay sits directly under its own live-branch guard`() {
        val source = appScreenSource()
        listOf(
            GuardSite("清空搜索历史？（路 A）", "pendingClear?.let { pending ->", destructiveCall, "title = \"清空搜索历史？\""),
            GuardSite("删除这一版？（路 A）", "pendingDelete?.let { pending ->", destructiveCall, "title = \"删除这一版？\""),
            GuardSite("删除全部学习数据？（路 A）", "if (pending != null) {", destructiveCall, "title = \"删除全部学习数据？\""),
            GuardSite("删除《词书名》？（路 A，LearningSetupScreen 体内）", "state.pendingDelete?.let { book ->", destructiveCall, "title = \"删除《\${book.displayName}》？\""),
            GuardSite("删除这套 AI 配置？（路 A，批 4 Task 3）", "pendingAiProfileDelete?.let { profileId ->", destructiveCall, "title = \"删除这套 AI 配置？\""),
            GuardSite("清除已保存的密钥？（路 A，批 4 Task 3）", "pendingAiProfileKeyClear?.let { profileId ->", destructiveCall, "title = \"清除已保存的密钥？\""),
            GuardSite("删除一条拍照翻译记录？（路 A，批 4 Task 4）", "pendingPhotoTranslationDelete?.let { recordId ->", destructiveCall, "title = \"删除这条翻译记录？\""),
            GuardSite("选择要导出的词书（路 B）", "if (showWordBookExportPicker) {", glassCall, "text = \"选择要导出的词书\""),
            GuardSite("发现已学过的单词（路 B）", "state.pendingMigration?.let { preview ->", glassCall, "text = \"发现已学过的单词\""),
        ).forEach { site -> assertOverlayGuardedUnderLiveBranch(source, site) }

        // 合成对拍（反空转）：判据必须咬住「守卫活着、浮层被 if (false) 包了一层」的形状——
        // 这正是「旧五例放过」的那一发，若本例对它也绿，本例就是第二个恒真式。
        val site = GuardSite(
            "合成样片",
            "if (showWordBookExportPicker) {",
            glassCall,
            "text = \"选择要导出的词书\"",
        )
        val deadBranchGhost = "if (showWordBookExportPicker) {\n    if (false) {\n        GlassDialog(onDismiss = {}) { Text(text = \"选择要导出的词书\") }\n    }\n}\n"
        assertFailsWith<AssertionError>(
            "反空转失控：整枚套进 if (false) 的幽灵样片没被可达性锚咬住——本例与旧三例同病",
        ) { assertOverlayGuardedUnderLiveBranch(deadBranchGhost, site) }
        val liveShape = "if (showWordBookExportPicker) {\n    GlassDialog(onDismiss = {}) { Text(text = \"选择要导出的词书\") }\n}\n"
        assertOverlayGuardedUnderLiveBranch(liveShape, site)
    }
}
