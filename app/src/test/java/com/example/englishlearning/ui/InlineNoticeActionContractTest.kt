package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 11 裁决 **R-11.0**：`InlineNoticeAction` 的**源码契约**——动作链接配方只有一处真值。
 *
 * 背景（Task 10 二轮评审 I-5）：`InlineNotice` 的 `action` 槽原先只保证「48dp + testTag + 合并语义」，
 * 动作本体（`Text` + `clickable` + `FontWeight.Bold` + `noticeInk(kind, appColors()).action`）留给调用方，
 * 批 2/3 收 11 份提示时会长出 11 份链接配方；而 Task 12 的份数棘轮按**文件**数「不走 `InlineNotice(` 的
 * 错误渲染站点」，自排动作链接既不是错误 Text 站点也不新增文件 → 抓不住。裁决是在
 * **`InlineNotice.kt` 同一个文件里**加公开 `InlineNoticeAction`（同文件新增不撞份数棘轮，
 * 且墨色/字重/触控只有一处真值）。本契约钉的就是「那一处真值」不被复制走。
 *
 * 三条承重线：
 *  ① 配方在本文件里各恰一处（字重、取墨、`clickable` 挂在调用方 modifier 之后）——多一处就是第二份配方；
 *  ② 48dp 的**交付机制不变**：`heightIn(min = 48.dp)` + `propagateMinConstraints = true` 仍只落在
 *     `InlineNotice` 的动作槽包裹层（`SharedComponentGeometryContractTest.inline_notice_geometry_literals_are_pinned_in_source`
 *     同口径），`InlineNoticeAction` 自己**不钉水线也不写 `fillMaxHeight()`**——后者是裁决 R-11.0 原文那句
 *     「内部加 `fillMaxHeight()` 承接 48dp」，**真机实测否掉**（有界宿主里可点面被撑到 635.05dp、
 *     整条提示拉成半屏，实测 `expected:<48.0.dp> but was:<635.0476.dp>`；无界宿主里是空操作），
 *     故本契约把「本文件 0 处 `fillMaxHeight`」钉成禁形，连同 import；
 *  ③ 调用方不得再自取动作墨（`color = noticeInk(` 全域 0 处）——这条是 R-11.0 真正的防回潮闸。
 *
 * 口径同 `SharedComponentGeometryContractTest`：先剥注释再匹配（KDoc 会合法复述同一批形状），
 * 每条探针配合成正/负片段自证不是恒真。
 */
class InlineNoticeActionContractTest {

    private companion object {
        const val INLINE_NOTICE = "InlineNotice.kt"
    }

    /**
     * 公开签名的**逐字**形状：批 2/3 十几处调用点都照它写，形参名/顺序/默认值漂一条就全线改名。
     * `(?m)^fun` 是「无可见性修饰符」的锚（`private fun …` / `internal fun …` 不以 `fun` 起行），
     * 可见性另由 [nonPublicAction] 点名，两枚探针各管一面。
     */
    private val publicSignature = Regex(
        """(?m)^fun InlineNoticeAction\(\s*kind: InlineNoticeKind,\s*label: String,\s*onClick: \(\) -> Unit,\s*modifier: Modifier = Modifier,?\s*\)""",
    )

    /** 配方①：稿 `.act` 的 700 字重（`FontWeight.Bold`）。 */
    private val boldWeight = Regex("""fontWeight = FontWeight\.Bold""")

    /** 配方②：动作墨从纯表取（`noticeInk(kind, appColors())`），且只取 `.action` 那一枚。 */
    private val inkFetch = Regex("""noticeInk\(kind, appColors\(\)\)""")
    private val actionFieldRead = Regex("""\.action\b""")

    /**
     * 配方③：48dp 的**交付机制**在包裹层（`propagateMinConstraints = true` 把 `heightIn(min = 48.dp)`
     * 的下限传进槽内），槽内 `clickable` 挂在调用方 [modifier] 之后（调用方的 padding/尺寸落在可点面外侧）。
     */
    private val propagateMinConstraints = Regex("""propagateMinConstraints = true""")
    private val clickOnCallerModifier = Regex("""modifier\s*\.clickable\(""")

    /**
     * 禁形一：`clickable` 排在 `padding` 之后（可点面只剩内容盒，撞 F5-03 的 48dp 下限）。
     * 末位用 `\b` 而不是 `\(`：`Modifier.padding(…).clickable { … }` 这种尾随 lambda 写法同样违规，
     * 只认 `clickable(` 会让它蒙过去（首轮实现的合成正片段就是这么漏的：`.clickable { }` 数出 0 处）。
     */
    private val clickAfterPadding = Regex("""padding\([^)]*\)\s*\.clickable\b""")

    /**
     * 禁形：槽内用 `fillMaxHeight()`「承接」48dp（裁决 R-11.0 原文那句，**真机实测否掉**）。
     * `fillMaxHeight()` 吃的是最大高约束不是最小：有界宿主里把可点面撑到 635.05dp、整条提示拉成半屏
     * （实测 `expected:<48.0.dp> but was:<635.0476.dp>`，见 `InlineNoticeTest`）；无界宿主里是空操作。
     * 锚不带 `\(`：`import …layout.fillMaxHeight` 这一行同样算命中（留着 import 就是留着回潮的把手）。
     */
    private val fillMaxHeightAnywhere = Regex("""fillMaxHeight""")

    /** 48dp 水线仍只有一处（在 `InlineNotice` 的动作槽包裹层）。 */
    private val actionFloor48 = Regex("""heightIn\(min = 48\.dp\)""")

    /** 调用方自取动作墨的写法（R-11.0 明令禁止）。 */
    private val callerFetchesActionInk = Regex("""color\s*=\s*noticeInk\(""")

    /** 可见性探针：这枚配方必须是公开 API（`private`/`internal` 会让调用方各自再抄一份）。 */
    private val nonPublicAction = Regex("""(?:private|internal)\s+fun InlineNoticeAction\(""")

    @Test
    fun inline_notice_action_is_the_single_source_of_the_link_recipe() {
        val source = uiComponentSource(INLINE_NOTICE)
        assertEquals(
            1,
            countMatches(source, publicSignature),
            "公开签名必须恰一处且逐字为 (kind, label, onClick, modifier = Modifier)：批 2/3 十几处调用点照它抄",
        )
        assertEquals(0, countMatches(source, nonPublicAction), "InlineNoticeAction 必须公开：收进 private/internal 等于把配方还给各屏")
        assertEquals(1, countMatches(source, boldWeight), "稿 `.act` 的 700 字重必须恰一处（0 处 = 链接读感丢失，2 处 = 第二份配方）")
        assertEquals(1, countMatches(source, inkFetch), "动作墨必须恰一处从纯表 noticeInk(kind, appColors()) 取")
        assertEquals(1, countMatches(source, actionFieldRead), "纯表四枚墨里只许读 `.action` 那一枚恰一次（前景/底/描边属提示条本体）")
        assertEquals(
            1,
            countMatches(source, propagateMinConstraints),
            "48dp 的交付机制必须恰一处在包裹层（propagateMinConstraints = true）：删掉它槽内退回 13sp 行高" +
                "（真机实测 15.24dp，变异自证 ⑨），而包裹层自己仍是 48dp → 只有量在可点节点上的断言会红",
        )
        assertEquals(
            1,
            countMatches(source, clickOnCallerModifier),
            "clickable 必须恰一处挂在调用方传进来的 modifier 之后（`modifier.clickable(…)`）：" +
                "调用方的 padding/尺寸修饰符因此落在可点面外侧——这是「可点面两条共同交付」的第②条" +
                "（clickable 在任何 padding 之前；heightIn 与 clickable 的先后中性，见 EntryRow.kt KDoc）",
        )
        assertEquals(
            0,
            countMatches(source, fillMaxHeightAnywhere),
            "禁形（裁决 R-11.0 原文那句被真机实测否掉）：槽内不得用 fillMaxHeight()「承接」48dp——" +
                "它吃的是最大高约束，有界宿主里把可点面撑到 635.05dp、整条提示拉成半屏" +
                "（实测 expected:<48.0.dp> but was:<635.0476.dp>），无界宿主里是空操作；" +
                "连 import 都算命中（留着 import 就是留着回潮的把手）",
        )
        assertEquals(
            1,
            countMatches(source, actionFloor48),
            "48dp 下限仍只有一处、且只在 InlineNotice 的动作槽包裹层（InlineNoticeAction 不另钉水线）",
        )
        assertEquals(
            0,
            countMatches(source, clickAfterPadding),
            "禁形：clickable 排在 padding 之后（可点面只剩内容盒，撞 F5-03 的 48dp 下限）",
        )

        // 合成正/负片段：签名探针既认自家形状，也不被换名/换序/加默认值的近邻形状冒名。
        val good = "fun InlineNoticeAction(\n    kind: InlineNoticeKind,\n    label: String,\n" +
            "    onClick: () -> Unit,\n    modifier: Modifier = Modifier,\n)"
        assertEquals(1, countMatches(good, publicSignature), "合成正片段漏判：跨行书写的正确签名没被认出来")
        assertEquals(
            0,
            countMatches("fun InlineNoticeAction(label: String, kind: InlineNoticeKind, onClick: () -> Unit)", publicSignature),
            "合成负片段被放行：换了形参顺序也算合规（调用点全部具名传参时会静默漂）",
        )
        assertEquals(
            0,
            countMatches("private fun InlineNoticeAction(kind: InlineNoticeKind, label: String, onClick: () -> Unit, modifier: Modifier = Modifier)", publicSignature),
            "取样壳失控：private 形状竟过了公开签名锚（可见性另有 nonPublicAction 探针，两枚都不得恒真）",
        )
        assertEquals(1, countMatches("private fun InlineNoticeAction(", nonPublicAction), "可见性探针失控：private 形状没被数出来")
        assertEquals(
            1,
            countMatches("Modifier.padding(end = 8.dp).clickable { }", clickAfterPadding),
            "禁形探针失控：padding 后 clickable 的形状根本没被数出来",
        )
        assertEquals(
            0,
            countMatches("Modifier.fillMaxHeight().clickable { }", clickAfterPadding),
            "禁形探针越界：fillMaxHeight 后的 clickable 被判成 padding 后 clickable",
        )
        // 交付机制三枚探针的自证：各认自家形状，且不互相冒名。
        assertEquals(
            1,
            countMatches("Box(propagateMinConstraints = true) { action() }", propagateMinConstraints),
            "探针失控：propagateMinConstraints 的形状根本没被数出来（0 处会让「48dp 怎么交付的」失去源码锚）",
        )
        assertEquals(
            1,
            countMatches("modifier = modifier.clickable(onClick = onClick),", clickOnCallerModifier),
            "探针失控：clickable 挂在调用方 modifier 之后的形状没被认出来",
        )
        assertEquals(
            0,
            countMatches("Modifier.clickable(onClick = onClick)", clickOnCallerModifier),
            "探针越界：大写 `Modifier`（组件内自建链、丢掉调用方 modifier）也被当成承接调用方链",
        )
        assertEquals(
            1,
            countMatches("import androidx.compose.foundation.layout.fillMaxHeight", fillMaxHeightAnywhere),
            "禁形探针失控：连 import 都没数出来（只留 import 不留调用同样是回潮把手）",
        )
        assertEquals(
            1,
            countMatches("modifier.fillMaxHeight().clickable(onClick = onClick)", fillMaxHeightAnywhere),
            "禁形探针失控：调用位的 fillMaxHeight 没被数出来",
        )
    }

    /**
     * R-11.0 的防回潮闸：全域 `ui` 主源码里不许再出现「调用方自取动作墨」的写法。
     *
     * 扫描面是**全部** main 源文件（含 `InlineNotice.kt` 本体——它自己也不该有这形状，取墨点在
     * `InlineNoticeAction` 的局部 `val ink`），所以本条既挡屏侧复制配方，也挡组件内改成
     * `color = noticeInk(kind, appColors()).action` 这种把纯表调用摊进 `Text` 参数的写法
     * （摊开后 `.action` 与取墨点分散在两行，上一条用例的计数就失去意义）。
     */
    @Test
    fun no_caller_fetches_the_action_ink_itself() {
        val offenders = uiFiles()
            .map { file -> file.relativePathFrom(File(UI_MAIN_SOURCE_PATH)) to countMatches(file.readText(), callerFetchesActionInk) }
            .filter { (_, count) -> count > 0 }
        assertTrue(
            offenders.isEmpty(),
            "调用方不得自取动作墨（R-11.0）：$offenders —— 动作链接一律写 " +
                "`action = { InlineNoticeAction(kind, \"标签\", onClick = { … }) }`（回调必须具名传：" +
                "签名末位是 `modifier`，尾随 lambda 编译不过），墨色/字重/触控只有 InlineNotice.kt 一处真值；" +
                "要改动作墨就改纯表 noticeInk 的 action 字段并同步 InlineNoticeKindTest。"
        )
        // 探针自证（house 口径：禁形探针必须先证明数得出东西，否则全域 0 命中是空转）。
        assertEquals(
            1,
            countMatches("Text(text = \"重试\", color = noticeInk(kind, appColors()).action)", callerFetchesActionInk),
            "探针失控：自取动作墨的形状根本没被数出来（全域 0 命中就成了空转）",
        )
        assertEquals(
            1,
            countMatches("Text(text = \"重试\", color =noticeInk(kind, set).action)", callerFetchesActionInk),
            "探针失控：等号后无空格的写法漏判",
        )
        assertEquals(
            0,
            countMatches("val ink = noticeInk(kind, appColors())\nText(text = label, color = ink.action)", callerFetchesActionInk),
            "探针越界：组件内的正确写法（局部 val 取墨）被判成调用方自取",
        )
    }

    // M-3（Task 12 兑现）：本类原先自带一份私有 uiComponentSource，与另外三份逐字相同，已提升为
    // SourceRatchet.kt 的共享夹具（文件恰一个 + 目录守卫两条断言逐字保留），本文件直接调用同名顶层函数。
}
