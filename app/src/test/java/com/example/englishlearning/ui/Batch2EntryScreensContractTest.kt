package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 2 余下五屏的源码契约（原型 `sSplash` / `sOnboarding1` / `sOnboarding2` / `sError` /
 * `sUnavailable`，`app.js:1424-1524`）。
 *
 * 这一批的共同口径是「原型画了的照搬，原型没画的一枚都不补」：
 *  - 启动屏：标题 / 副标题 / 进度条 / 一句说明照搬；`开始建档` 与 `跳过，直接进入` **不搬**——
 *    本时机没有这两条路由（首启必须建档，没有跳过），画出来就是两枚点了没反应的假入口；进度条
 *    改不确定态，原型那 62% 是画稿里写死的假进度。
 *  - 建档·姓名：补「第 1 步 / 共 2 步」眉题、原型副标题与「下一步 · 选词书」；「每天有多少时间」
 *    与「你的目标」两节**不搬**——没有对应偏好字段，画出来只能接 toast。
 *  - 建档·选词书：应用把「选词书 + 每日新增」并成一屏可反复编辑的「学习计划」，因此步骤框与
 *    「建档完成后…」那句承诺都不成立（文章按需生成，不是建档就发一篇），「词卡显示」也没有承载字段；
 *    这三样归本契约的**缺席断言**，理由写在各条消息里。
 *  - 错误屏：按真实失败类型分叉（`AppError.InvalidProfileName` 保留旧文案，其余落「词库没能打开」）；
 *    原型那枚 `LC-DB-03` 错误码与时间戳是画稿样例，**不搬**（本仓 `AppError` 不携带可展示的诊断码）。
 *  - 页面不可用：新建独立屏 + 两枚真出口，并作为「未知 AI 功能 key」的路由兜底挂进宿主。
 *
 * 读法是剥注释后的源文本（`uiScreenSource`），按符号取窗口，不锚行号。
 */
class Batch2EntryScreensContractTest {

    private val appSource: String by lazy { uiScreenSource("AppScreen.kt") }

    private val splash: String by lazy { bodyOf(appSource, "private fun BrandLaunchSurface(") }

    private val profile: String by lazy { bodyOf(appSource, "private fun ProfileCreationScreen(") }

    private val error: String by lazy { bodyOf(appSource, "internal fun ErrorScreen(") }

    private val setup: String by lazy { bodyOf(appSource, "internal fun LearningSetupScreen(") }

    private val unavailable: String by lazy { uiScreenSource("UnavailableScreen.kt") }

    // ---------- ① 启动屏 ----------

    @Test
    fun `the splash shows the prototype brand frame but never fakes a skip route`() {
        assertTrue(splash.isNotEmpty(), "取不到 `BrandLaunchSurface(` 本体，判据会空转")
        listOf(
            "\"英语学习\"",
            "\"背词 · 阅读 · AI 助学\"",
            "\"正在打开本机词库…\"",
            "ic_learning_mark",
        ).forEach { anchor ->
            assertTrue(anchor in splash, "启动屏必须逐字带原型文案 $anchor（`app.js:1424-1438`）")
        }
        assertTrue("size(88.dp)" in splash, "启动 logo 必须按原型 88px 这一档画（`app.js:1429`）")
        assertEquals(
            1,
            countMatches(splash, Regex("""\bLinearProgressIndicator\(""")),
            "进度指示恰好 1 处：原型那根 120×4 的条是启动态唯一的动态元素",
        )
        assertEquals(
            0,
            countMatches(splash, Regex(Regex.escape("\"开始建档\""))) +
                countMatches(splash, Regex(Regex.escape("\"跳过，直接进入\""))),
            "`开始建档` / `跳过，直接进入` 必须为 0：本时机没有这两条路由（首启强制建档），" +
                "画出来就是两枚点了没反应的假入口（零假入口原则）",
        )
        assertEquals(
            0,
            countMatches(splash, Regex("""progress\s*=\s*0\.62f""")),
            "62% 是原型画稿里写死的假进度：本仓不知道真实加载到哪一步，只能画不确定态",
        )
        assertEquals(
            1,
            countMatches(splash, Regex(Regex.escape("AppleMintGradient"))),
            "品牌渐变恰 1 处（与 `launch/BrandLaunchActivity` 同源）：启动两帧之间不许换底色",
        )
        assertTrue(
            countMatches(splash, Regex("""AppType\.""")) >= 3,
            "启动屏至少三处排印取 `AppType.*`（标题 / 副标题 / 说明），实际 " +
                "${countMatches(splash, Regex("""AppType\."""))} 处",
        )
    }

    // ---------- ② 建档·姓名 ----------

    @Test
    fun `the name step frames itself as step one and keeps the connected anchors`() {
        assertTrue(profile.isNotEmpty(), "取不到 `ProfileCreationScreen(` 本体，判据会空转")
        assertTrue(
            "\"第 1 步 / 共 2 步\"" in profile,
            "眉题必须逐字「第 1 步 / 共 2 步」（`app.js:1444`）：这一屏是首启两步里的第一步",
        )
        assertTrue(
            "\"只为把每天的量定得刚好，随时可以改。\"" in profile,
            "页头副标题必须逐字对齐原型（`app.js:1446`）：原话解释了「为什么要问你名字」",
        )
        assertTrue(
            "\"下一步 · 选词书\"" in profile,
            "主键文案必须是「下一步 · 选词书」（`app.js:1463`）：下一步真是选词书，" +
                "沿用「创建」会把这枚按钮说成终局动作",
        )
        listOf("\"创建资料\"", "\"姓名输入\"", "\"请输入名字提示\"", "\"先认识一下你\"").forEach { anchor ->
            assertTrue(anchor in profile, "既有锚 $anchor 必须逐字保留：`AppScreenTest` 按它走首启流程")
        }
        // 「每天有多少时间」与「你的目标」两节需要偏好字段（时长档 / 学习目标）承载，本仓没有；
        // 原型上它们只是 chip 与 toast，搬过来就是两排假选项。
        listOf("\"每天有多少时间\"", "\"你的目标\"", "\"5 分钟\"", "\"1 小时以上\"").forEach { absent ->
            assertEquals(
                0,
                countMatches(profile, Regex(Regex.escape(absent))),
                "$absent 必须不出现：没有对应偏好字段，画出来只能接 toast（零假入口原则）",
            )
        }
    }

    // ---------- ③ 建档·选词书（并屏口径） ----------

    @Test
    fun `the book step stays one editable screen and drops what it cannot say`() {
        assertTrue(setup.isNotEmpty(), "取不到 `LearningSetupScreen(` 本体，判据会空转")
        listOf("\"学习计划\"", "\"选择词书\"", "\"每日新增\"", "SetupSaveBar(").forEach { anchor ->
            assertTrue(anchor in setup, "并屏后必须仍在 $anchor：这是原型 `sOnboarding2` 的可承载部分")
        }
        listOf(
            "\"第 2 步 / 共 2 步\"" to "同一屏以后还要从设置里反复打开，步骤框只在首启成立",
            "\"词卡显示\"" to "本仓没有「先英文 / 先中文」偏好字段，分段控件无处落值",
            "建档完成后" to "文章是按需生成，不是建档就发一篇——这句承诺在当前实现里为假",
        ).forEach { (absent, reason) ->
            assertEquals(
                0,
                countMatches(setup, Regex(Regex.escape(absent))),
                "$absent 必须不出现：$reason",
            )
        }
        // 原型那句「今天立刻会有 N 个新词和 1 篇文章」里的 N 在屏内另有真值来源（`state.dailyNewTarget`），
        // 但句子后半段为假，整句不搬；数字本身由「每日新增」节与实时预估句承载。
        assertEquals(
            0,
            countMatches(setup, Regex(Regex.escape("篇文章"))),
            "原型那句「…和 1 篇文章等你」整句不搬（`app.js:1502`）：文章按需生成，" +
                "拆半句拼数字就成第二套事实",
        )
    }

    // ---------- ④ 错误屏 ----------

    @Test
    fun `the error screen branches on the real failure and never invents a diagnostic`() {
        assertTrue(error.isNotEmpty(), "取不到 `ErrorScreen(` 本体，判据会空转")
        assertTrue(
            "internal fun ErrorScreen(error: AppError, onRetry: () -> Unit)" in appSource,
            "错误屏必须收 `AppError`：旧形态把任何失败都说成「无法创建资料，请检查姓名」，" +
                "存储故障时用户会去改名字",
        )
        assertTrue(
            "AppError.InvalidProfileName" in error,
            "只有 `InvalidProfileName` 才该落「检查姓名」那一支",
        )
        listOf(
            "\"无法创建资料，请检查姓名\"",
            "\"返回重新填写\"",
            "\"词库没能打开\"",
            "\"本机数据库读取时出了点问题。可以先重试。\"",
            "\"重试\"",
            "Sticker.Warning",
            "EmptyState(",
        ).forEach { anchor ->
            assertTrue(anchor in error, "错误屏必须带 $anchor（`app.js:1493-1508` 的可搬部分）")
        }
        assertEquals(
            0,
            countMatches(error, Regex(Regex.escape("LC-DB-03"))),
            "`LC-DB-03` 是原型画稿里的样例错误码，`AppError` 不携带可展示诊断——编一个就是假数据",
        )
        assertEquals(
            0,
            countMatches(error, Regex(Regex.escape("\"从备份恢复\""))),
            "「从备份恢复」不挂在这一屏：回滚入口归恢复态（`NeedsRecovery` 分支已交付），" +
                "在这里再挂一枚会绕过恢复流程的前置校验",
        )
        listOf("MintBackground", "MintPrimary", "MintPrimaryDark").forEach { token ->
            assertEquals(
                0,
                countMatches(error, Regex("""\b$token\b""")),
                "错误屏不得再读静态 Mint 桥 `$token`：底色与墨一律 `appColors()`",
            )
        }
        assertTrue(
            "import com.example.englishlearning.core.error.AppError" in appSource,
            "宿主文件必须显式导入 `AppError`：全限定名写在 when 分支里读不出意图",
        )
        assertTrue(
            "ErrorScreen(error = state.error" in appSource,
            "宿主必须把真实失败类型传进错误屏（`state.error`），不许退回无参形态",
        )
    }

    // ---------- ⑤ 页面不可用 ----------

    @Test
    fun `the unavailable page is a real screen with two honest exits`() {
        listOf(
            "\"页面不可用\"",
            "\"这个页面在当前版本没有开放\"",
            "\"功能还在打磨，先回到今日学习。已经生成的文章与词单不会受到影响。\"",
            "\"回到今日学习\"",
            "\"看看别的功能\"",
            "Sticker.Info",
            "EmptyState(",
            "AppScreenHeader(",
        ).forEach { anchor ->
            assertTrue(anchor in unavailable, "不可用页必须带 $anchor（`app.js:1511-1523`）")
        }
        assertEquals(
            2,
            countMatches(unavailable, Regex("""\bPillButton\(""")),
            "`PillButton(` 必须恰 2 处：原型两枚出口（主「回到今日学习」/ 次「看看别的功能」）",
        )
        assertEquals(
            1,
            countMatches(unavailable, Regex("""\bPillStyle\.Secondary\b""")),
            "次键恰 1 处：两枚都画实底会把「看看别的」读成第二条主路径",
        )
        assertTrue(
            "widthIn(max = 260.dp)" in unavailable,
            "动作列必须复用原型 260dp 上限：满屏宽的两枚大键在平板上会拉成两条横幅",
        )
        assertEquals(
            0,
            countMatches(unavailable, Regex(Regex.escape("\"重试\""))),
            "不可用页没有可重试的动作（这一页不是失败态），不许长出一枚假重试",
        )
        assertEquals(
            0,
            countMatches(unavailable, Regex(Regex.escape("Mint"))),
            "不可用页不得读静态 Mint 桥：一律 `appColors()`",
        )
        assertEquals(
            0,
            countMatches(unavailable, Regex("""MaterialTheme\.typography""")),
            "排印必须走六档 `AppType`，不得回退 `MaterialTheme.typography`",
        )
    }

    @Test
    fun `the unavailable page is mounted as the unknown feature fallback`() {
        assertEquals(
            1,
            countMatches(appSource, Regex("""\bUnavailableScreen\(""")),
            "宿主必须恰调用 1 处 `UnavailableScreen(`：它是「未知 AI 功能 key」的兜底页，不是第二套通用空态",
        )
        assertTrue(
            "else if (selectedFeatureKey != null)" in appSource,
            "挂载条件必须是「key 在、枚举认不出」：已知 key 由上面各功能屏接走，不许被兜底页截胡",
        )
        listOf("onGoLearning = {", "onBrowseFeatures = {").forEach { wiring ->
            assertTrue(wiring in appSource, "兜底页两枚出口必须真接线（$wiring）")
        }
        assertTrue(
            "BackHandler(enabled = selectedFeatureKey != null)" in appSource,
            "返回键必须按 key 判定（`selectedFeatureKey != null`）：未知 key 时 `selectedFeature` 为 null，" +
                "旧条件会让用户困在兜底页",
        )
        assertTrue(
            "|| selectedFeatureKey != null ||" in appSource,
            "overlayOpen 必须按 key 判定：否则底导会从兜底页下面露出来",
        )
    }

    // ---------- 夹具 ----------

    /** 按花括号配对取 `fun X(` 的函数体（源已剥注释）；取不到返回空串，让调用处各自响亮判红。 */
    private fun bodyOf(text: String, signature: String): String {
        val start = text.indexOf(signature)
        if (start < 0) return ""
        val openParen = text.indexOf('(', start)
        if (openParen < 0) return ""
        var depth = 0
        var index = openParen
        while (index < text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            index++
        }
        if (index >= text.length) return ""
        val open = text.indexOf('{', index)
        if (open < 0) return ""
        var braces = 1
        for (cursor in open + 1 until text.length) {
            when (text[cursor]) {
                '{' -> braces++
                '}' -> {
                    braces--
                    if (braces == 0) return text.substring(open + 1, cursor)
                }
            }
        }
        return ""
    }
}
