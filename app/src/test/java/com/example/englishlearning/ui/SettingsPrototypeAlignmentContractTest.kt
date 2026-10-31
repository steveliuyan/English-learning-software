package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 原型对齐（`sMeSettings`，`assets/app.js:1328-1344`）：**只动副标题与版式细节**。
 *
 * AC-R-21 的台账 `SettingsGroups` 是硬边界：恰 4 组、恰 9 行。本批不增删任何一行，
 * 所以这条用例把「4 组 / 9 行」再念一遍——它是本批的**不许动**声明，不是新增期望。
 * （2026-10-31 去重：许可行移出设置页，唯一入口在关于页；9 行是那次决策后的现盘形状。）
 *
 * 原型写了、本屏不写的两块：
 *  - 「背词 → 词卡显示（先看英文自评 · 认识也看释义）」：本仓没有「首面语言 / 认识也看释义」的
 *    偏好项（全仓零 `cardFirst` / `revealKnown` 读点），要画这一行就得先新增采集与存储——那是功能改动，
 *    且会把 9 行变成 10 行、直接破 AC-R-21。按「读不到就整枚不显示」不写。
 *  - 页末那条 `这一页只放全局开关…提醒与发音、计划、词书、备份与许可都在「我的」里`：本屏的 4 组 9 行
 *    **正是**提醒 / 词书 / 备份的入口（许可在关于页），照抄这句会当场说假话。不写。
 */
class SettingsPrototypeAlignmentContractTest {

    private val source: String by lazy { uiScreenSource("SettingsScreen.kt") }

    @Test
    fun `the ledger shape is untouched at four groups and nine rows`() {
        assertEquals(4, SettingsGroups.all.size, "本批不许增删分组（AC-R-21）")
        assertEquals(9, SettingsGroups.all.sumOf { it.entries.size }, "本批不许增删一级行（AC-R-21）")
    }

    @Test
    fun `the reminder row carries the prototype subtitle without gaining a row`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("只在当天没完成计划时提醒"))),
            "「学习提醒」行副标题补上原型的「只在当天没完成计划时提醒」（`app.js:1332` 开启提醒行的 row-sub；" +
                "本仓 `ReminderSchedulingPolicy` 当日完成即取消，同一行为）",
        )
        assertEquals(0, countMatches(source, Regex("词卡显示")), "原型「词卡显示」行在本仓没有对应偏好项，不造行（且 9 行形状锁定）")
        assertEquals(0, countMatches(source, Regex("这一页只放全局开关")), "原型页末那句与 4 组 9 行的现实矛盾（本屏就住着提醒/词书/备份），不抄")
        assertTrue(
            countMatches(source, Regex(Regex.escape("\"学习提醒\""))) == 1,
            "「学习提醒」标题仍只写在台账一处（渲染侧按 tag 取，第二套真值会让 AC-R-21 的台账失效）",
        )
    }
}
