package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 5 Task 2：学习提醒重排的源码契约（spec §5「学习提醒」行 + 计划对账「时间改 Material TimePicker /
 * 加载失败带重试 / 整屏可滚」）。
 *
 * 该屏旧形态（`ReminderSettingsScreen.kt` 144 行）的四处毛病：
 *  - 两处自写 `TextButton("返回")`、`Text("学习提醒", fontSize = 22.sp)` 手搓标题，`appColors()` 读点为 **0**
 *    （整屏不吃令牌，深色模式下靠 Material 默认色蒙混）；
 *  - 时间选择是自写 `TimeGrid`（八枚整点 `TextButton`，15 分钟步进口号的实现是「整点」），
 *    既不能选任意分钟、也不符 spec §5「时间改 Material `TimePicker`」；
 *  - 读设置失败只留一行 `InlineNotice(Error)`，**没有重试**——用户只能退出重进；
 *  - 根列没有 `verticalScroll`，大字号 / 小屏下「提醒时间 + 隐私说明」直接掉出屏幕。
 *
 * 重做口径与批 5 Task 1 一致：页头换共享 `AppScreenHeader`，排印全走六档 `AppType`，
 * 色全走 `appColors()` / `appDomain(AppDomain.Settings)`，分组走 `SoftCard`（零强卡、零 M3 `Card(`）。
 *
 * 读法是注释剥除后的源文本（`uiScreenSource`），按符号取窗口，不锚行号。
 */
class ReminderSettingsLayoutContractTest {

    private val source: String by lazy { uiScreenSource("ReminderSettingsScreen.kt") }

    /** 屏内保留锚：改动只许换承载控件，不许把锚弄丢（connected 与宿主用例全靠它们定位）。 */
    private val keptTags = listOf(
        "reminder_settings_screen",
        "reminder_settings_loading",
        "reminder_inexact_notice",
        "reminder_enabled_switch",
        "reminder_privacy_notice",
        "reminder_time_button",
        "reminder_time_confirm",
        "reminder_retry",
    )

    // ---------- ① 页头（两个分支都换） ----------

    @Test
    fun `both branches wear the shared header and the text back button retires`() {
        assertEquals(
            2,
            countMatches(source, Regex("""\bAppScreenHeader\(""")),
            "`AppScreenHeader(` 必须恰 2 处：`viewModel == null` 的降级分支与正常分支各一。" +
                "只换一处会让「提醒功能暂不可用」那条路径留着自写返回键（本屏是唯一双分支屏）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bTextButton\(""")),
            "自写 `TextButton(` 必须清零：返回位归页头，时间入口归 `PillButton`（三档药片）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("\"reminder_settings_back\""))),
            "旧返回锚 `reminder_settings_back` 必须退役：页头返回位固定 testTag `app_screen_header` + 可及名「返回」",
        )
        assertEquals(
            2,
            countMatches(source, Regex("""onBack = onBack""")),
            "两个分支的页头都必须把 `onBack` 接进返回位（漏一处 = 那条路径上返回键消失，用户卡在页里）",
        )
    }

    // ---------- ② 手搓字号归零 / 六档排印 ----------

    @Test
    fun `the screen reads six step type instead of hand rolled font sizes`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bfontSize""")),
            "`fontSize` 读点必须清零（现状 5 处）：手搓字号绕过六档 `AppType`，大字号档下也不跟系统缩放分档",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\d+\.sp""")),
            "`<数字>.sp` 必须清零：一切字号走 `AppType` 六档",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("import androidx.compose.ui.unit.sp"))),
            "`androidx.compose.ui.unit.sp` 的 import 也必须下线——留着它就是下次手搓字号的入口",
        )
        val typeReads = countMatches(source, Regex("""AppType\."""))
        assertTrue(typeReads >= 8, "六档排印至少 8 处读点（现 $typeReads）：标题/说明/标签/空态各有所属")
        assertEquals(
            0,
            countMatches(source, Regex("""MaterialTheme\.typography""")),
            "排印必须走六档 `AppType`，不得回退 `MaterialTheme.typography`",
        )
    }

    // ---------- ③ 时间控件改 Material ----------

    @Test
    fun `the time grid becomes the material time picker`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bTimePicker\(""")),
            "时间选择必须换成 Material `TimePicker(`（恰 1 处）：spec §5「时间改 Material TimePicker」",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bTimeGrid""")),
            "自写 `TimeGrid` 必须整段下线（八枚整点 `TextButton` + `25.sp` 口径都在它体内）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""reminder_time_\$""")),
            "动态锚 `reminder_time_\$hour` 必须清零：整点网格退役，滑到任意分钟只回写一个时间值",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"reminder_time_button\""))),
            "时间入口锚 `reminder_time_button` 必须恰 1 处（承载控件换成药片，锚不变）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"reminder_time_confirm\""))),
            "对话框确认键锚 `reminder_time_confirm` 必须恰 1 处：确认才回写 `onTimeChange`，取消不落盘",
        )
    }

    // ---------- ④ 失败带重试 ----------

    @Test
    fun `the read failure notice carries a retry that re-runs the load`() {
        assertEquals(
            1,
            countMatches(source, Regex("""\bInlineNotice\(""")),
            "错误提示必须收在共享 `InlineNotice(`（恰 1 处）：本屏只有「读/存失败」一条错误通道",
        )
        assertTrue(
            "InlineNoticeKind.Error" in source,
            "错误态必须显式 `InlineNoticeKind.Error`（Info/Success 混用会让读失败看起来像一句普通提示）",
        )
        val noticeAt = source.indexOf("InlineNotice(")
        val retryAt = source.indexOf("\"reminder_retry\"")
        assertTrue(noticeAt > 0, "源码里找不到 `InlineNotice(` 锚点")
        assertTrue(retryAt > noticeAt, "重试键 `reminder_retry` 必须落在 `InlineNotice(` 之后（$retryAt vs $noticeAt）")
        assertTrue(
            retryAt - noticeAt < 900,
            "重试键必须挂在错误提示里（距 `InlineNotice(` ${retryAt - noticeAt} 字符）：" +
                "spec §5「加载失败带重试」，重试埋在别处等于没有",
        )
        assertTrue(
            "viewModel.retry()" in source,
            "重试必须真的重跑读取（`viewModel.retry()`）：`load()` 在 `loading == false` 时静默不动，" +
                "直接调它等于按了个假按钮",
        )
    }

    // ---------- ⑤ 整屏可滚 ----------

    @Test
    fun `both branches scroll so nothing falls off a small screen`() {
        assertEquals(
            2,
            countMatches(source, Regex("""\bverticalScroll\(""")),
            "`verticalScroll(` 必须恰 2 处（两分支各一）：大字号 / 小屏下「提醒时间 + 隐私说明」" +
                "会被裁掉，而本屏没有任何别处的滚动壳可借",
        )
    }

    // ---------- ⑥ 令牌化 ----------

    @Test
    fun `the screen takes ink from the token set and settings domain`() {
        // 计数口径：本屏两分支各取一次 `val colors = appColors()` 再复用（不逐属性重取），
        // 所以「≥ 2」= 两分支都吃了令牌；比按属性数点更能防「一条分支整段漏令牌」。
        // 真正的「不许私造颜色」由下面 `Color(0x` 为 0 与全仓 `RawColorLiteralRatchetTest` 双钉。
        val colorReads = countMatches(source, Regex("""appColors\(\)"""))
        assertTrue(colorReads >= 2, "`appColors()` 至少 2 处读点（现 $colorReads，改前为 0）：两个分支各一处")
        assertEquals(
            0,
            countMatches(source, Regex("""Color\(\s*0x""")),
            "裸字面量颜色必须为 0：底色/一二级墨/描边全部走 `appColors()` 与域色",
        )
        assertTrue(
            countMatches(source, Regex("""appDomain\(AppDomain\.Settings\)""")) >= 1,
            "本屏属 `AppDomain.Settings` 域，域色至少 1 处读点（开关轨道取 `appDomain(AppDomain.Settings).base`，" +
                "药片的 `domain = AppDomain.Settings` 只决定药片自己的墨色）",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bMint[A-Za-z0-9_]*""")),
            "`Mint*` 静态桥必须保持 0 读点（含 import）：强调墨走 `appDomain(AppDomain.Settings).deep`",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bCard\(""")),
            "M3 `Card(` 必须为 0：分组走 `SoftCard` / `BareRow` 三档容器",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\bStrongCard\(""")),
            "强档卡必须为 0：本屏是设置页，一屏至多一枚强卡的额度留给主任务页",
        )
    }

    // ---------- ⑦ 锚清单 + 开关口径 ----------

    @Test
    fun `every retained anchor still has exactly one home and the switch keeps its label`() {
        keptTags.forEach { tag ->
            assertTrue(
                countMatches(source, Regex(Regex.escape("\"$tag\""))) >= 1,
                "锚「$tag」丢了：重排只许换承载控件，不许把既有锚弄丢（connected 用例全靠它们定位）",
            )
        }
        assertEquals(
            1,
            countMatches(source, Regex("""\bSwitch\(""")),
            "开启提醒必须是一枚 Material `Switch(`（恰 1 处）：spec §5「开关统一 Switch」",
        )
        val switchAt = source.indexOf("Switch(")
        assertTrue(switchAt > 0, "源码里找不到 `Switch(` 锚点")
        // 窗口取「`Switch(` 之后 400 字符」：全站约定参数表里 `Switch(...)` 在前、
        // `modifier = Modifier.testTag(...).semantics { ... }` 在尾部（`AccessibilitySwitchLabelContractTest` 规则 1）。
        val window = source.substring(switchAt, minOf(source.length, switchAt + 400))
        assertTrue(
            "reminder_enabled_switch" in window,
            "`Switch(` 参数表里必须带 `reminder_enabled_switch` 锚（窗口：${window.trim()}）",
        )
        assertTrue(
            "contentDescription" in window,
            "开关修饰链必须带 `contentDescription`：规则 1 的 80 字符窗口口径",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("contentDescription = \"开启学习提醒\""))),
            "开关可及名必须逐字保留「开启学习提醒」（`AccessibilitySwitchLabelContractTest` 口径）",
        )
    }
}
