package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 3 Task 4：粘贴导入屏的新骨架契约（spec §5 逐屏表「粘贴导入」行）。
 *
 * 这屏原样仍是批 1 之前的形态：自写 `TextButton("← 返回上一层")` + `headlineSmall` 标题、
 * 「≥40 词 / ≤1200 词」的规则**只在事后拒绝文案里出现**（输入框上下都没有常驻说明，
 * 用户是提交后才被告知门槛）、提交按钮 `enabled = !state.submitting`（空表单可点）、
 * 提交按钮埋在整个表单流末尾、按钮圆角是裸 `RoundedCornerShape(18.dp)`、十处静态薄荷桥 + 三处 M3 排印。
 *
 * 七条判据分两档颜色：
 *  - 1–6 是本批要做的六件事，RED 阶段各自必须红；
 *  - 7 是**四枚 tag 字面量各恰 1**（现状本来就绿）：这是宿主与 connected 用例的接口面，
 *    换骨架时最可能的破坏不是删掉而是「顺手重排」把 tag 挪进循环或改名。
 *
 * 取窗一律按「锚点前 N / 后 M 字符」取样（注释已剥，不会把 KDoc 里的示例当代码），
 * 锚点改名即 `fail`，不会静默空转。
 */
class ArticleImportLayoutContractTest {

    private val source: String by lazy { stripComments(uiScreenSource("ArticleImportScreen.kt")) }

    /**
     * 七枚静态薄荷桥。RED 前本文件有 10 处读点（`MintPrimaryDark` 4 / `MintSurface` 2 /
     * `MintPrimary` 2 / `MintOutline` 1 / `MintTextMuted` 1，`MintBackground` 1 记在同一族里），
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
        assertTrue(at > 0, "锚点 `$anchor` 不在 ArticleImportScreen.kt 里（改名或搬走即 fail，不允许静默空转）")
        return source.substring(maxOf(0, at - before), minOf(source.length, at + after))
    }

    // ---------- 1. 页头 ----------

    /**
     * 返回位与屏名归共享页头。
     *
     * 这屏原来的返回位是一枚 `TextButton("← 返回上一层")`：`←` 是排版痕迹、「返回上一层」是
     * 多入口时代的旧口径（这屏只有一个入口），且文字是唯一可及名。换成共享页头后：
     * 本文件 `contentDescription = ` 归零（屏内不再自造可及名）、`"← 返回上一层"` 归零、
     * `MaterialTheme.typography` 归零（排印一律走 `AppType`）。
     *
     * 旧锚 `import_back`（connected 用例按它取返回位）同时退役：页头把返回位的 testTag 固定为
     * `app_screen_header`、可及名固定为「返回」，`ArticleImportScreenTest` 同批改按内容描述点。
     */
    @Test
    fun `the shared header owns the back slot and the screen title`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("AppScreenHeader("))),
            "页头必须恰好一枚共享 `AppScreenHeader(`：二级页的返回位与屏名只允许这一套",
        )
        assertTrue(
            "title = \"粘贴导入\"" in source,
            "页头标题必须是屏名「粘贴导入」（正文侧不再重复一枚大标题，一份信息不留两个真值）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "排印一律走 `AppType` 六档：M3 默认排印的读点必须归零（否则字号与行高各屏一套）",
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
    }

    // ---------- 2. 规则前置 ----------

    /**
     * 「40~1200 词」的规则移到输入框**上方**并常驻。
     *
     * 现状这条规则只出现在事后拒绝文案（`ImportRejection.message()` 的「正文太短，至少需要 40 个单词。」），
     * 用户先写、后被告知，等于让人白写一遍。规则前置是这一屏最实的信息架构改动。
     *
     * 判据不钉死数字而是钉**同源**：两个阈值必须读 `ArticleQualityPolicy.forImported` 的
     * `minWords` / `maxWords`，且窗口内不得再出现硬编码的 40 / 1200——写死数字就会与 VM 的判据分叉，
     * 改了策略而界面还在说旧门槛是这类页面最典型的谎言。拒绝文案仍逐字保留「40 个单词」
     * （`ImportRejection.message()` 不动，`ArticleImportTest` 按全等断言）。
     */
    @Test
    fun `the word count rule sits above the body field and reads the same thresholds as the validator`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("import_rule"))),
            "规则说明必须恰好一处（`import_rule`）",
        )
        val ruleAt = source.indexOf("import_rule")
        val bodyAt = source.indexOf("import_body")
        assertTrue(
            ruleAt in 1 until bodyAt,
            "规则必须在正文输入框之前出现（实测规则 $ruleAt / 正文框 $bodyAt）：先说明门槛再让人写",
        )

        val rule = windowAround("import_rule", before = 700, after = 200)
        assertTrue(
            "forImported.minWords" in rule,
            "规则文案的下限必须读 `ArticleQualityPolicy.forImported.minWords`（写死数字会与 VM 判据分叉）",
        )
        assertTrue(
            "forImported.maxWords" in rule,
            "规则文案的上限必须读 `ArticleQualityPolicy.forImported.maxWords`（同上）",
        )
        assertTrue(
            "至少" in rule && "最多" in rule,
            "规则文案必须同时说清下限与上限（只写一半等于没说清门槛）",
        )
        assertTrue(
            "40" !in rule && "1200" !in rule,
            "规则窗口内不得出现硬编码的 40 / 1200：阈值只有一个真值（`ArticleQualityPolicy.forImported`）",
        )
    }

    // ---------- 3. 禁用态 ----------

    /**
     * 提交按钮**常显但空表单禁用**，三个条件缺一即红。
     *
     * 现状 `enabled = !state.submitting`：空表单可点，点下去得到一条事后拒绝，用户白白走一趟。
     * 判据要求三个条件都在同一个 `enabled` 表达式里（标题非空 / 正文非空 / 非提交中）——
     * 只写前两个会让「正在保存」期间可重复提交，只写第三个就是现状。
     */
    @Test
    fun `the submit button stays visible but is disabled until both fields are filled`() {
        // 取样锚必须带引号：`import_submit` 是 `import_submitting` 的前缀，按裸词找会命中提交中的那行提示。
        val at = source.indexOf("\"import_submit\"")
        assertTrue(at > 0, "锚点 `\"import_submit\"` 不在源码里")
        val window = source.substring(at, minOf(source.length, at + 200))
        assertTrue("enabled =" in window, "提交按钮的 `enabled` 必须紧跟在 `import_submit` 附近（实测窗口内没有）")
        assertTrue("title.isNotBlank()" in window, "禁用态必须含「标题非空」：空标题提交必然被拒")
        assertTrue("body.isNotBlank()" in window, "禁用态必须含「正文非空」：空正文提交必然被拒")
        assertTrue("!state.submitting" in window, "禁用态必须含「非提交中」：保存期间不许重复提交")
    }

    // ---------- 4. 吸底操作条 ----------

    /**
     * 提交按钮搬进屏底固定操作条。
     *
     * 现状它在整个表单流的末尾：正文框 + 拒绝提示 + 存储失败提示都在它上面，大字号下要滚过整屏
     * 才点得到，而它恰恰是这一屏唯一的主操作。判据三件事：条本身有 tag；主 CTA 在条内；
     * 表单滚动面排在条**之前**（顺序颠倒意味着条跟着滚，吸底就名不副实）。
     */
    @Test
    fun `the submit action is pinned to a bottom bar below the scrolling form`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("import_action_bar"))),
            "吸底操作条必须恰好一处（`import_action_bar`）",
        )
        val barAt = source.indexOf("import_action_bar")
        // 带引号取 `import_submit`：`import_submitting` 是它的前缀，裸词会先命中提交中的提示行。
        val submitAt = source.indexOf("\"import_submit\"")
        val scrollAt = source.indexOf("verticalScroll")
        assertTrue(
            barAt in 1 until submitAt,
            "主 CTA 必须在吸底条**之内**（实测条 $barAt / 提交 $submitAt）",
        )
        assertTrue(
            scrollAt in 1 until barAt,
            "表单滚动面必须排在吸底条之前（实测滚动 $scrollAt / 条 $barAt）：条不能跟着滚",
        )
    }

    // ---------- 5. 一屏一主操作 ----------

    /**
     * 条内只一枚主 CTA，且圆角走令牌。
     *
     * `Button(` 全文件恰 1（旧返回位那枚 `TextButton` 已归页头）：一屏一个主操作；
     * 裸 `RoundedCornerShape(` 归零——spec §4.3 的按钮圆角是令牌，不是每屏各写一个魔数。
     */
    @Test
    fun `the bar carries exactly one primary call to action shaped by tokens`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("Button("))),
            "全文件只允许一枚 `Button(`（屏底主 CTA）：多个实底按钮就没有主操作",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("TextButton("))),
            "旧返回位那枚 `TextButton(` 必须归零（返回位已归页头）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("RoundedCornerShape("))),
            "圆角一律走 `AppShape` 令牌：裸 `RoundedCornerShape(` 归零（原提交按钮是 18.dp 魔数）",
        )
    }

    // ---------- 6. 令牌 ----------

    /**
     * 取色只许组合期 `appColors()`，容器用共享三档（规则说明是弱卡）。
     *
     * 静态薄荷桥在深浅两套里是同一枚浅墨，压深套底色即失效；这三档容器是批 1 统一过的
     * 「一屏至多一枚强卡」口径的落点。
     */
    @Test
    fun `colors come from the theme getters and the rule card uses a shared container`() {
        for (bridge in mintBridges) {
            assertEquals(
                0,
                countMatches(source, Regex("""\b${Regex.escape(bridge)}\b""")),
                "静态薄荷桥 `$bridge` 必须归零：只许组合期读 `appColors()`（深浅两套各自成立）",
            )
        }
        assertTrue(
            countMatches(source, Regex(Regex.escape("appColors()"))) >= 2,
            "取色点必须走 `appColors()`（实测少于 2 处），静态令牌在深套下失效",
        )
        assertTrue(
            countMatches(source, Regex(Regex.escape("SoftCard("))) >= 1 ||
                countMatches(source, Regex(Regex.escape("StrongCard("))) == 1,
            "规则说明要落在共享容器里（`SoftCard(` ≥ 1 或 `StrongCard(` 恰 1）：本批不再自造卡片",
        )
    }

    // ---------- 7. 接口面防回潮 ----------

    /**
     * 四枚 tag 字面量各恰 1（宿主与 connected 用例的接口面）。
     *
     * `import_title` / `import_body` / `import_submit` / `import_submitting` 被
     * `ArticleImportScreenTest` 逐个按 tag 取，重复一枚就会命中两个节点（`onNodeWithTag` 直接失败）。
     */
    @Test
    fun `the four import tags stay unique`() {
        for (tag in listOf("import_title", "import_body", "import_submit", "import_submitting")) {
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "`$tag` 必须恰好一枚字面量（重复即 connected 用例命中两个节点）",
            )
        }
    }
}
