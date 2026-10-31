package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * U-7（决策 `docs/decisions/2026-11-03-duplication-consolidation.md` §4）：
 * 「每天最多一次 / 非精确」这条规则**只讲一遍**，而长得能点的「重复 / 周一到周日 / 每天」死行整行下线。
 *
 * 复述的现状（**改前**实测，行号随本批删除已整体前移，重算请用字面量而不是行号：
 * `grep -n '每天最多一次\|非精确' app/src/main/java/com/example/englishlearning/ui/Reminder*.kt`）：
 * 同一句「每天最多一次，系统可能稍有延迟」在提醒屏出现三处
 * ——`ReminderSettingsScreen.kt` 的 `viewModel == null` 降级分支页头副标题、正常分支页头副标题、
 * 卡内 `reminder_inexact_notice`，
 * 导航页 `ReminderSpeechScreen.kt` 的副标题又复读第四遍（「…（非精确、每天最多一次）」）。
 * 四处互相独立，改一处就剩下三处旧话；AGENTS.md 的口径是「非精确提醒允许延迟**并在设置中说明**」，
 * 要的是说明存在，不是说明出现四次。权威那一处留在卡内（紧挨开关本身，用户正是在决定要不要开它时读到）。
 *
 * 死行按旧 D-3 的同判据**删除**而不是补 `onClick`：改前那枚
 * `BareRow(title = "重复", subtitle = "周一到周日", trailing = "每天", showChevron = false)` 没有 `onClick`，
 * 本仓也没有按星期配置提醒的能力（`ReminderSchedulingPolicy` 固定每天最多一次）。
 * 用户看到的是一行设置项的样子，读出来的是「这里可以改重复频率」——把固定行为摆成设置项就是谎称可改。
 * 它承载的信息（每天一次）本来就写在同一个 `SoftCard` 的那句说明里，删行不丢事实。
 *
 * 计数一律走 [countMatches]（剥注释、字符串感知）：本文件的判据钉的是「这句话由谁**渲染给用户**」，
 * 而这几屏的注释恰好都在复述规则与它的出处（那些注释行就是下一任要读的历史，不算违规）。
 */
class ReminderRuleOnceContractTest {

    private val reminder: String by lazy { uiScreenSource("ReminderSettingsScreen.kt") }
    private val hub: String by lazy { uiScreenSource("ReminderSpeechScreen.kt") }

    @Test
    fun the_inexact_rule_is_stated_once_on_the_reminder_screen() {
        assertEquals(
            1,
            countMatches(reminder, capRule),
            "「每天最多一次」在提醒屏只许讲一处——卡内 `reminder_inexact_notice` 那一句（现值 " +
                "${countMatches(reminder, capRule)}）：页头副标题复述它，就是同一句话要人肉对齐三次",
        )
        assertEquals(
            1,
            countMatches(reminder, inexactRule),
            "「非精确」在提醒屏只许讲一处（同上，现值 ${countMatches(reminder, inexactRule)}）",
        )
        // 权威那一句不许被顺手删掉：判据删的是复述，不是规则本身。
        assertEquals(
            1,
            countMatches(reminder, Regex(Regex.escape("\"reminder_inexact_notice\""))),
            "`reminder_inexact_notice` 必须恰 1 处：AGENTS.md 要求「非精确提醒并在设置中说明」，" +
                "这一枚锚就是那句说明的所在，也是 connected 点名的对象",
        )
        // 那句说明不许只是「还在屏上」，它必须仍坐在卡里、紧挨开关——用户正是在决定要不要开它时读到。
        // 本屏 `SoftCard` 是尾随 lambda 写法（`SoftCard {`），所以按位置取窗口，不数 `SoftCard(`。
        val noticeAt = reminder.indexOf("\"reminder_inexact_notice\"")
        val switchAt = reminder.indexOf("Switch(", noticeAt)
        assertTrue(
            noticeAt >= 0 && reminder.lastIndexOf("SoftCard {", noticeAt) >= 0,
            "`reminder_inexact_notice` 必须落在某张 `SoftCard` 之后（现值 noticeAt=${noticeAt}）：说明漂到卡外就成了页头副标题的替身",
        )
        assertTrue(
            switchAt > noticeAt && switchAt - noticeAt < 900,
            "开关必须紧挨在那句说明之后（`Switch(` 距说明 ${switchAt - noticeAt} 字符）：" +
                "规则讲的是这条开关的行为，隔半屏就不像同一件事了",
        )
    }

    @Test
    fun the_hub_row_stops_restating_the_rule() {
        assertEquals(
            0,
            countMatches(hub, capRule),
            "「提醒与发音」导航页不再复读「每天最多一次」（现值 ${countMatches(hub, capRule)}）：" +
                "那一行只要说清「什么时候会提醒 + 默认关着」，调度上限归提醒屏那一句",
        )
        assertEquals(
            0,
            countMatches(hub, inexactRule),
            "导航页同样不复读「非精确」（现值 ${countMatches(hub, inexactRule)}）",
        )
        // 但行副标题不能因此变成空话：两条屏侧事实仍在（何时提醒 + 默认状态）。
        assertTrue(
            "只在当天没完成计划时提醒" in hub && "默认关闭" in hub,
            "导航页那行的两条屏侧事实必须留着：复读的是调度上限，不是整句副标题",
        )
    }

    @Test
    fun the_dead_repeat_row_is_gone() {
        listOf("\"重复\"", "周一到周日", "\"每天\"", "reminder_repeat_row").forEach { literal ->
            assertEquals(
                0,
                countMatches(reminder, Regex(Regex.escape(literal))),
                "死行残留「$literal」（现值 ${countMatches(reminder, Regex(Regex.escape(literal)))}）：" +
                    "无 `onClick`、`showChevron = false` 的一行设置项，读起来像「这里能改重复频率」，" +
                    "而本仓没有这个能力——按旧 D-3 的判据该删的不是文案，是那一行",
            )
        }
    }

    // ---------- 反空转 ----------

    @Test
    fun the_rules_are_not_vacuous() {
        val repeated = """
            AppScreenHeader(title = "学习提醒", subtitle = "每天最多一次，系统可能稍有延迟")
            Text("每天最多一次（非精确提醒）", modifier = Modifier.testTag("reminder_inexact_notice"))
        """.trimIndent()
        assertEquals(2, countMatches(repeated, capRule), "合成正片段漏判：复读的两句没被数出来，「恰 1 处」是空转")
        assertEquals(1, countMatches(repeated, inexactRule), "合成正片段漏判：「非精确」没被数出来")

        val deadRowFixture = """
            BareRow(title = "重复", subtitle = "周一到周日", trailing = "每天", modifier = Modifier.testTag("reminder_repeat_row"))
        """.trimIndent()
        listOf("\"重复\"", "周一到周日", "\"每天\"", "reminder_repeat_row").forEach { literal ->
            assertEquals(
                1,
                countMatches(deadRowFixture, Regex(Regex.escape(literal))),
                "合成负片段被放行：死行形状「$literal」没被认出，删除判据抓不到回退",
            )
        }

        // 注释不计入：这几屏的注释本来就在复述规则与出处，按原文计数会把诚实的历史判成违规。
        val commentOnly = "//  ReminderSchedulingPolicy 固定每天最多一次（非精确）\n" +
            "Text(\"开启提醒\", modifier = Modifier.testTag(\"reminder_inexact_notice\"))\n"
        assertEquals(0, countMatches(commentOnly, capRule), "合成负片段被放行：注释里的历史被算成屏侧复读")
        assertEquals(0, countMatches(commentOnly, inexactRule), "同上：「非精确」在注释里不算一处")
    }

    private companion object {
        val capRule = Regex(Regex.escape("每天最多一次"))
        val inexactRule = Regex(Regex.escape("非精确"))
    }
}
