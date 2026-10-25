package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 1 Task 8（spec §5 打卡页）：**连续天数是本屏唯一的大数字**，累计统计降一档。
 *
 * 旧版把「连续 N 天」写成 `streakText` 一整句、再把完成度塞进一枚 132dp 的 `Canvas` 环，
 * 与今日卡的「已完成 / 目标」重复表达同一件事（今日完成的两种画法并列，谁也不是主信息）。
 * 改版口径：显示层级只留一个顶点——连续天数 → `AppType.Display`；累计 → `AppType.Title`；
 * 完成度环整块删除（今日卡里的「完成任务 x/y」已经是这条信息的唯一出处）。
 *
 * 为什么这判据必须落在 JVM 侧：`CheckInScreenTest` 跑在模拟器上，而门控第三项
 * `:app:compileDebugAndroidTestKotlin` **只编译不跑**——把环或第二枚 Display 加回去时，
 * 那侧什么都看不见。本类补的正是门控里跑得到的那一半。
 *
 * 口径与既有源码契约一致：注释先剥（`stripComments`），只在代码里数；
 * 读者不必担心 KDoc 里提到的形状被误咬。
 */
class CheckInDisplayAuthorityTest {

    private val screen: String by lazy { stripComments(uiScreenSource("CheckInScreen.kt")) }

    @Test
    fun `the streak is the only display figure`() {
        assertEquals(
            1,
            countMatches(screen, Regex("""AppType\.Display""")),
            "打卡页恰一枚 Display：连续天数。第二枚 Display 会把「大数字」这个顶点劈成两半，" +
                "用户看不出哪个才是本屏主信息",
        )
        assertTrue(
            "check_in_streak_days" in screen,
            "Display 必须落在连续天数锚 `check_in_streak_days` 上（connected 用例按这个锚取证数值）",
        )
        assertTrue(
            "AppType.Title" in screen,
            "累计统计必须降为 Title（§5：连续天数之上的次级信息）",
        )
    }

    @Test
    fun `the duplicated completion ring is gone`() {
        listOf(
            "check_in_completion_ratio",
            "check_in_completion_arc",
            "check_in_completion_percent",
        ).forEach { gone ->
            assertTrue(
                gone !in screen,
                "重复的完成度环「$gone」必须删除（§5）：今日卡里的「完成任务 x/y」是这条信息的唯一出处",
            )
        }
        assertTrue(
            "Canvas(" !in screen,
            "环删了，Canvas 也应随之离场——留着它就是留一块「以后再画点什么」的空地",
        )
    }
}
