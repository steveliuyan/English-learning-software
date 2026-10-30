package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 原型对齐（`sMeReminder`，`assets/app.js:1345-1365`）的**拆分落位**契约。
 *
 * 原型是一屏「提醒与发音」，本仓是两屏（`ReminderSettingsScreen` / `SpeechSettingsScreen`）。
 * 本批**保持两屏**，只把原型上缺的段落/文案补到对应屏：
 *  - 提醒屏：「只在当天没完成计划时提醒」（`app.js:1333` 开启提醒行的 row-sub）+「重复 / 周一到周日 / 每天」
 *    （`:1334`）。重复项在本仓是固定行为（`ReminderSchedulingPolicy` 每天最多一次），只作只读说明。
 *  - 发音屏：自动发音的 row-sub「用本机离线合成，不联网」（`:1360`）——仅在当前供应商是系统 TTS 时成立
 *    （小米 MiMo 走网络），故按引擎条件渲染。
 *
 * 原型写了、本仓不写：
 *  - 页头「保存」（`data-act="toast"` 的 mock）：本仓开关/时间改完即落库，一枚只弹提示的保存键就是假入口。
 *  - 「口音 英式 / 美式」：`SpeechPreference` 没有口音字段（全仓零 `accent` 读点），没有真值可显示。
 *  - 「语速 0.9 倍」：本仓四档是 0.75/1.0/1.25/1.5（`SpeechRateSteps`），0.9 不是可存值；
 *    本屏已有滑杆 + 真实读数，不另抄一枚数字。
 *  - 「试听：delicate」：原型拿一个固定词做试听，本仓试听卡接受用户输入，保留现状。
 */
class ReminderSpeechPrototypeAlignmentContractTest {

    private val reminder: String by lazy { uiScreenSource("ReminderSettingsScreen.kt") }
    private val speech: String by lazy { uiScreenSource("SpeechSettingsScreen.kt") }

    @Test
    fun `the reminder screen gains the prototype subtitle and the read only repeat row`() {
        assertEquals(
            1,
            countMatches(reminder, Regex(Regex.escape("只在当天没完成计划时提醒"))),
            "开启提醒行要补原型副标题「只在当天没完成计划时提醒」（当日计划已完成则不发）",
        )
        assertEquals(1, countMatches(reminder, Regex(Regex.escape("\"重复\""))), "「重复」行必须恰 1 处")
        assertEquals(1, countMatches(reminder, Regex(Regex.escape("周一到周日"))), "重复行的副标题「周一到周日」")
        assertEquals(1, countMatches(reminder, Regex(Regex.escape("\"每天\""))), "重复行的值「每天」（本仓固定每天最多一次）")
        // 既有判据不许放宽：开关仍是唯一一枚 Material Switch，锚与可及名不动。
        assertEquals(1, countMatches(reminder, Regex("""\bSwitch\(""")), "开关仍恰 1 枚（本批只补文案，不换承载控件）")
        assertEquals(1, countMatches(reminder, Regex(Regex.escape("\"reminder_enabled_switch\""))), "开关锚不动")
    }

    @Test
    fun `the speech screen explains that the switch is offline only for system tts`() {
        assertEquals(
            1,
            countMatches(speech, Regex(Regex.escape("用本机离线合成，不联网"))),
            "自动发音行要补原型副标题（app.js:1360）；本仓按引擎条件渲染——这句话只对系统 TTS 成立",
        )
        assertTrue(
            "PronunciationEngine.SystemTts" in speech,
            "离线那句必须挂在「当前供应商 == 系统 TTS」的条件上：小米 MiMo 走已保存的 AI 服务，不经网络的话是假的",
        )
        assertEquals(1, countMatches(speech, Regex("""\bSwitch\(""")), "开关仍恰 1 枚（本批只补文案）")
        assertEquals(1, countMatches(speech, Regex(Regex.escape("\"speech_auto_speak_toggle\""))), "开关锚不动")
        assertEquals(0, countMatches(speech, Regex(Regex.escape("口音"))), "「口音」在本仓没有数据源（SpeechPreference 无 accent 字段），不显示")
        assertEquals(0, countMatches(speech, Regex(Regex.escape("0.9 倍"))), "原型的 0.9 倍不是 `SpeechRateSteps` 的合法档，不抄")
    }
}
