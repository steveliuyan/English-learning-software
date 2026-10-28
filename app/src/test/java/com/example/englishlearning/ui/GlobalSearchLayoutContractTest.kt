package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 3 Task 5：查词屏的新骨架契约（spec §5 逐屏表「查词」行）。
 *
 * 这屏原样仍是批 1 之前的形态：`Scaffold(containerColor = MintBackground)` + 自写
 * 「← 返回上一层」可点文字 + `headlineSmall` 标题（`MaterialTheme.typography` 15 处）、
 * 提交按钮文案随输入切换成「查看最近搜索」/「全量搜索」（同一枚按钮两个身份）、
 * 历史行是 `MintSurface + 1dp MintOutline` 药片（`border(` 2 处、`RoundedCornerShape(14.dp)` 5 处）、
 * 历史行计数写成「搜索 N 次」长句（行标题已经是那个查询词，再说一遍「搜索」是废话）、
 * 七枚静态薄荷桥共 40 个读点。
 *
 * 七条判据分两档颜色：
 *  - 1–6 是本批要做的六件事，RED 阶段各自必须红；
 *  - 7 是 `GlobalSearchResultRow` 的**定义 + 调用各恰 1**（现状本来就绿）：这枚函数是词行族 seed
 *    的落窗判据（`SharedComponentUniquenessRatchetTest` 的 `:776-787` 口径），R-20C.1 已裁决它
 *    **不迁 `WordRow`**，所以本契约只防「顺手重排」把它拆掉或改名——迁走会让那道 seed 门对本文件
 *    的账目落空，而不是变成合规。
 *
 * 取窗一律按「锚点前 N / 后 M 字符」取样（注释已剥，不会把 KDoc 里的示例当代码），
 * 锚点改名即 `fail`，不会静默空转。
 */
class GlobalSearchLayoutContractTest {

    private val source: String by lazy { stripComments(uiScreenSource("GlobalVocabularySearchScreen.kt")) }

    /**
     * 七枚静态薄荷桥。RED 前本文件共 40 个读点（`MintPrimaryDark` 10 / `MintTextMuted` 10 /
     * `MintPrimary` 9 / `MintSurface` 5 / `MintOutline` 4 / `MintBackground` 2 / `MintTint` 0），
     * 换装后必须全归零：只许组合期读 `appColors()`。
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

    /** 锚点前 `before` / 后 `after` 字符的取样窗口；锚点缺失当场失败。 */
    private fun windowAround(anchor: String, before: Int = 500, after: Int = 300): String {
        val at = source.indexOf(anchor)
        assertTrue(at > 0, "锚点 `$anchor` 不在 GlobalVocabularySearchScreen.kt 里（改名或搬走即 fail，不允许静默空转）")
        return source.substring(maxOf(0, at - before), minOf(source.length, at + after))
    }

    // ---------- 1. 页头 ----------

    /**
     * 返回位与屏名归共享页头。
     *
     * 这屏的返回位原是一枚 `Text("← 返回上一层")` 挂了 `clickable` + `contentDescription`：
     * `←` 是排版痕迹、可及名与可见文案各写一遍（两处真值）。换成共享页头后，本文件
     * `contentDescription = ` 归零（屏内不自造可及名）、`"← 返回上一层"` 归零、
     * `MaterialTheme.typography` 归零（排印一律走 `AppType` 六档），
     * `Scaffold(` 也归零——根节点就是一枚铺满的 `Column`，画面不再套一层 M3 骨架。
     *
     * 旧锚 `global_search_back` 一并退役：页头把返回位的 testTag 固定为 `app_screen_header`、
     * 可及名固定为「返回」，`GlobalVocabularySearchScreenTest` 同批改按内容描述点。
     *
     * 「返回上一层」这条**目的地语义**就此让位于共享页头的「返回」：本屏两个入口（主页查词 /
     * 阅读页点未知词）里后者回的是文章，写「返回首页」是在指错方向——页头的箭头位不承诺具体目的地，
     * 只表达「退一层」，比一句可能在某个入口上说错的文案更稳。
     */
    @Test
    fun `the shared header owns the back slot and the screen title`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("AppScreenHeader("))),
            "页头必须恰好一枚共享 `AppScreenHeader(`：二级页的返回位与屏名只允许这一套",
        )
        assertTrue(
            "title = \"查词\"" in source,
            "页头标题必须是屏名「查词」（正文侧不再重复一枚大标题，一份信息不留两个真值）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("Scaffold("))),
            "根节点不得再套 `Scaffold(`：屏底/内边距归自己的 Column，M3 骨架自带一整套与本设计无关的默认",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "排印一律走 `AppType` 六档：M3 默认排印的读点必须归零（现状 15 处）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("← 返回上一层"))),
            "旧页头的「← 返回上一层」文案必须归零（返回位已由共享页头的图标位承担）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("contentDescription = "))),
            "屏内不得自造可及名：页头的返回位已固定「返回」，其余元素按可见文案播报",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"global_search_screen\""))),
            "根节点必须自我标识 `global_search_screen`（本批五屏同口径：一屏一个可点/可读的根锚）",
        )
    }

    // ---------- 2. 提交文案固定 ----------

    /**
     * 同一枚按钮不因状态改身份。
     *
     * 现状文案随输入切三态（空 →「查看最近搜索」、非空 →「全量搜索」），等于把一枚按钮当两个动作：
     * 用户读完按钮上的字再点，字已经变了。版式重做后按钮恒称「搜索」，**空输入仍可点**（点下去
     * 落回最近搜索，这条能力由 `onSubmit` → 宿主判定，按钮不重复判定），能力不减、身份唯一。
     *
     * `onClick = submit` 与 `submit()` 各恰 1：两条入口（按钮点击 / 键盘搜索键）共用同一个 lambda，
     * 单一提交入口是本屏的行为契约，改文案不许顺手复制一份提交逻辑出去。
     */
    @Test
    fun `the submit action wears one fixed label in every state`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("查看最近搜索"))),
            "旧文案「查看最近搜索」必须归零：按钮的身份不随输入变化",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("全量搜索"))),
            "旧文案「全量搜索」必须归零：同上，一枚按钮两个身份就是没有主操作",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"搜索\""))),
            "按钮文案必须恰好一处「搜索」（一字不变：空输入与非空输入都点它）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("onClick = submit"))),
            "提交按钮必须直连 `onClick = submit`（同一个 lambda），不许在按钮上另写一份提交逻辑",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("onSubmit()"))),
            "`onSubmit()` 全文件恰 1：按钮点击与键盘搜索键必须共用这一条入口",
        )
    }

    // ---------- 3. 历史行去重计数 ----------

    /**
     * 历史行只说一次次数，且不说「搜索 N 次」这种把行标题重复一遍的长句。
     *
     * 行标题就是那个查询词，后面再写「搜索 3 次」等于把「搜索」讲两遍；尾部值 `3 次` 在
     * 一屏多行时更容易横向扫读（尾部是列，句中不是一个数）。
     *
     * 取样窗口锚在**行 tag 字面量**上：`GlobalVocabularySearchScreenTest` 按
     * `global_search_history_<query>` 点行、按 `global_search_history_count_<query>` 读计数，
     * 两枚 tag 都在这一窗口内，所以「换壳不许把计数行连同可读性一起丢掉」也落在同一条判据里。
     */
    @Test
    fun `the history row states its count once and drops the search verb sentence`() {
        // 行 tag 写在 trailingTag 之前，故 `global_search_history_${` 的首次命中就是行本身。
        val row = windowAround("global_search_history_\${", before = 200, after = 460)
        assertTrue(
            "BareRow(" in row,
            "历史行必须换成共享裸行 `BareRow(`（同组靠 1dp 发丝线 + 52dp 缩进，不再一层药片）",
        )
        assertTrue("次" in row, "历史行仍要说清点过几次（尾部值 `N 次`）：次数是这屏唯一的量，不许静默丢")
        assertEquals(
            1,
            countMatches(row, Regex(Regex.escape("次"))),
            "次数只许出现一次的拼法（尾部 `N 次`）：两处写法就是同一件事的两个真值",
        )
        assertEquals(
            0,
            countMatches(row, Regex(Regex.escape("搜索 "))),
            "历史行内不得再写「搜索 N 次」长句：行标题已经是那个查询词，`搜索` 是重复",
        )
        assertEquals(
            1,
            countMatches(row, Regex(Regex.escape("\"global_search_history_count_"))),
            "计数行必须保留 `global_search_history_count_` 标签（关闭次数显示时 connected 用未合并树断言其不存在）",
        )
    }

    // ---------- 4. 历史行容器 / 列表懒加载 ----------

    /**
     * 药片壳全拆：`border(` 归零、裸 `RoundedCornerShape(` 归零、共享裸行 ≥ 1。
     *
     * 现状每行一枚 `background(MintSurface, RoundedCornerShape(14.dp)) + border(1.dp, MintOutline, …)`
     * 的独立药片——历史一多就是「一行一个盒子」，正是 spec §1 点名的卡片汤。同组关系改由
     * 裸行的发丝线表达，盒子只在真需要分层的地方出现。
     *
     * 两条列表（历史 / 结果，互斥分支各一枚）都必须保住 `LazyColumn(` + `weight(1f)`：
     * 结果可以上百条，交给 `weight` 让列表自己滚，是「大字号下能滚到末条」的前提；
     * 改成按内容撑高的 `Column` 就会把提交按钮挤出屏幕。
     */
    @Test
    fun `history rows are bare rows without the pill and both lists stay lazy`() {
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("border("))),
            "屏内不得再描边（药片壳的 1dp MintOutline）：层次归容器的明度差与发丝线",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("RoundedCornerShape("))),
            "圆角一律走 `AppShape` 令牌：裸 `RoundedCornerShape(` 归零（现状 5 处 14.dp 魔数）",
        )
        assertTrue(
            countMatches(source, Regex("""(?<![A-Za-z0-9_])BareRow\(""")) >= 1,
            "历史行必须真接共享 `BareRow(`（删了药片却没接裸行 = 一行裸文字，同组关系也丢了）",
        )
        val listCount = countMatches(source, Regex(Regex.escape("LazyColumn(")))
        assertEquals(
            2,
            listCount,
            "恰两条懒加载列表（历史 / 结果，互斥分支各一枚）：现状就是 2，换骨架不许少也不许多",
        )
        val weighted = Regex(Regex.escape("LazyColumn(")).findAll(source).count { match ->
            source.substring(match.range.last, minOf(source.length, match.range.last + 260)).contains("weight(1f)")
        }
        assertEquals(
            listCount,
            weighted,
            "每条 `LazyColumn(` 都必须带 `weight(1f)`（实测 $weighted/$listCount）：列表按内容撑高会把主操作顶出屏幕",
        )
    }

    // ---------- 5. 清空历史不自己建对话框 ----------

    /**
     * 清空动作只把事件交出去，确认框不在屏里。
     *
     * 「清空历史」是破坏性动作，确认由宿主（`AppScreen` + VM）统一走 `ConfirmDestructiveDialog`；
     * 屏里再建一枚就等于确认逻辑有两个真值，而且这屏是无状态的（`state` / 回调全注入），
     * 建在屏里会让 connected 用例的驱动分支变多而没有新信息。
     *
     * 文案从「清空搜索历史」缩到「清空」：它已经和「最近搜索」同处一行，那个标题就是它的宾语；
     * 2f 字号下长文案会把标题挤成两行。
     */
    @Test
    fun `clearing the history never builds its own dialog`() {
        for (dialog in listOf("AlertDialog(", "ConfirmDestructiveDialog(", "GlassDialog(")) {
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(dialog))),
                "屏内不得建 `$dialog`：确认归宿主统一入口，屏只交事件",
            )
        }
        val clear = windowAround("\"global_search_clear_history\"", before = 160, after = 160)
        assertTrue(
            "onClick = onClearHistory" in clear,
            "「清空」必须直接调注入的 `onClearHistory`（实测窗口内没有）：屏内不自建确认流程",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"清空\""))),
            "「清空」文案恰一处（与「最近搜索」同行，短文案才不挤成两行）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("清空搜索历史"))),
            "旧文案「清空搜索历史」必须归零：同一动作不许留两个写法",
        )
    }

    // ---------- 6. 令牌 ----------

    /**
     * 取色只许组合期 `appColors()` / `appDomain()`。
     *
     * 七枚静态薄荷桥在深浅两套里是同一枚浅墨，压深套底色即失效（现状 40 个读点）。
     * 输入区落在弱卡 `SoftCard` 里：这屏没有「一屏一枚」的强卡需求，强卡额度留着别处用；
     * 域色只出现在输入框的聚焦指示/光标一处（`AppDomain.Library`——查的是词库里的词），
     * 主 CTA 走主操作档 `strongSurface`/`onStrong`，与粘贴导入、文章阅读的吸底 CTA 同一套。
     */
    @Test
    fun `colors come from the theme getters and the input sits in a shared card`() {
        for (bridge in mintBridges) {
            assertEquals(
                0,
                countMatches(source, Regex("""\b${Regex.escape(bridge)}\b""")),
                "静态薄荷桥 `$bridge` 必须归零：只许组合期读 `appColors()`（深浅两套各自成立）",
            )
        }
        assertTrue(
            countMatches(source, Regex(Regex.escape("appColors()"))) >= 3,
            "取色点必须走 `appColors()`（实测少于 3 处），静态令牌在深套下失效",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("appDomain("))) >= 1,
            "域色必须走 `appDomain(`（输入框聚焦态是这屏唯一的域色落点），不许写死一枚 hex",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("SoftCard("))),
            "输入区落在共享弱卡 `SoftCard(` 里（恰 1）：本批不再自造卡片",
        )
    }

    // ---------- 7. 结果行防误迁 ----------

    /**
     * `GlobalSearchResultRow` 定义 + 调用各恰 1，且五枚信息一个不少。
     *
     * R-20C.1 已裁决这枚行**不迁 `WordRow`**：结果行要「lemma + IPA + 释义 + 来源词书 + 次数」
     * 五枚信息，`WordRow` 只有四槽，强迁必丢 IPA 或截断词书名。它同时也是词行族 seed
     * （`GlobalVocabularySearchScreen.kt` 仍在册）的落窗判据——本契约只钉「没有被拆成两枚 /
     * 改名 / 丢掉某枚信息」，族级规则仍归 `SharedComponentUniquenessRatchetTest`。
     */
    @Test
    fun `the result row keeps one definition one call and all five pieces of information`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("private fun GlobalSearchResultRow("))),
            "`GlobalSearchResultRow` 必须恰一处私有定义（拆成两枚或改名即破坏词行族 seed 的落窗判据）",
        )
        assertEquals(
            2,
            countMatches(source, Regex(Regex.escape("GlobalSearchResultRow("))),
            "含定义恰 2 处命中（1 定义 + 1 调用）：调用点被抹掉等于结果区没人画",
        )
        val row = windowAround("private fun GlobalSearchResultRow(", before = 0, after = 2600)
        for (piece in listOf("result.lemma", "result.ipa", "result.meaningZh", "来源", "global_search_result_count_")) {
            assertTrue(
                piece in row,
                "结果行丢了「$piece」这枚信息：R-20C.1 的不迁理由正是「五枚信息塞不进四槽」，不许靠裁剪凑形状",
            )
        }
    }
}
