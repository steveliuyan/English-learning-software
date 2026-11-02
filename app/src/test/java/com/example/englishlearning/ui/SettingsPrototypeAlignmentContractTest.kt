package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 原型对齐（`sMeSettings`，`assets/app.js:1328-1344`）：**设置现在是「我的」里的子页**，
 * 只放全局开关（2026-11-02 `docs/decisions/2026-11-02-profile-hub-and-four-tabs.md` §3）。
 *
 * 前身是本文件里那条「4 组 9 行台账不许动」的声明。结构重排后 9 行散进「我的」hub 的 13 行
 * （台账判据搬到 `ProfileHubLedgerTest`），本文件只剩**这一屏自己的形状**：
 *   - 页头「设置」+ 返回位（它是子页，不是一级 tab）；
 *   - 「外观」段 + 主题三态分段（原型 `外观` 组唯一一项是真开关）；
 *   - 页末说明逐字照抄（这一次它是**真话**：提醒、计划、词书、备份与许可确实都在「我的」里）；
 *   - 原型写了、本屏不写的「背词 → 词卡显示（先看英文自评 · 认识也看释义）」：本仓仍没有
 *     「首面语言 / 认识也看释义」偏好项（全仓零 `cardFirst` / `revealKnown` 读点）。要画这一行
 *     就得先新增采集与存储——那是功能改动，不在本次版式重排范围内。按「读不到就整枚不显示」不写。
 */
class SettingsPrototypeAlignmentContractTest {

    private val source: String by lazy { uiScreenSource("SettingsScreen.kt") }

    @Test
    fun `the subpage carries the reference scope notice verbatim`() {
        assertEquals(
            1,
            countMatches(
                source,
                Regex(
                    Regex.escape(
                        "这一页只放全局开关。学习提醒、语音朗读、计划、词书、备份与许可都在「我的」里，不在这里重复出现。",
                    ),
                ),
            ),
            "设置子页必须带上参考的说明（`sMeSettings` 页末 notice）。2026-11-02 后它不再说假话，" +
                "2026-11-03 U-13 后行名随 hub 台账同步：" +
                "学习提醒、语音朗读、计划、词书、备份与许可确实都在「我的」hub 里，这一屏只剩外观",
        )
    }

    @Test
    fun `the subpage is headed by the shared header with a back exit`() {
        assertEquals(
            1,
            countMatches(source, Regex("""(^|[^A-Za-z0-9_])AppScreenHeader\(""")),
            "子页必须复用共享页头（返回位只此一处，R-9.2）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"设置\""))),
            "页头标题「设置」必须恰一处",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""onBack: \(\) -> Unit""")),
            "设置是「我的」的子页：必须持有返回出口（onBack）",
        )
        assertTrue(
            countMatches(source, Regex("""onBack\s*=\s*onBack""")) >= 1,
            "页头必须真的接上 onBack，否则返回位画了也没用",
        )
    }

    @Test
    fun `the appearance section keeps the three way theme selector and nothing else`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"外观\""))),
            "「外观」段标题必须恰一处（原型 `外观` 组）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""(^|[^A-Za-z0-9_])ThemeModeRow\(mode = """)),
            "主题三态分段在本屏必须恰一枚调用点（签名那处 `fun ThemeModeRow(mode:` 不算调用）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""fun ThemeModeRow\(""")),
            "主题分段的定义只此一处：第二份定义＝第二套真值",
        )
        listOf("跟随系统", "浅色", "深色").forEach { label ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape("\"$label\""))),
                "三档标签「$label」必须各恰一处（读屏与 connected 侧都按这三段定位）",
            )
        }
    }

    @Test
    fun `the prototype word card row stays out until the preference exists`() {
        assertEquals(0, countMatches(source, Regex("词卡显示")), "原型「词卡显示」行在本仓没有对应偏好项，不造行")
        assertEquals(0, countMatches(source, Regex("先看英文自评")), "「先看英文自评」没有采集与存储，写了就是假开关")
        assertEquals(0, countMatches(source, Regex("认识也看释义")), "「认识也看释义」没有采集与存储，写了就是假开关")
        assertEquals(0, countMatches(source, Regex(Regex.escape("\"背词\""))), "「背词」分组随词卡显示一起不写")
    }

    /** 旧台账不许在这份源码里复活：9 行已经搬进 `ProfileGroups`，两套真值会立刻漂。 */
    @Test
    fun `the old four group ledger is gone from this screen`() {
        assertEquals(0, countMatches(source, Regex("""\bSettingsGroups\b""")), "SettingsGroups 台账已退役（13 行台账在 ProfileScreen.kt 的 ProfileGroups）")
        assertEquals(0, countMatches(source, Regex("""\bSettingsGroup\b""")), "旧分组数据类不得残留")
        listOf(
            "settings_open_setup",
            "settings_open_reminder",
            "settings_open_worksheet",
            "settings_open_ai_profiles",
            "settings_open_speech",
            "settings_word_books",
            "settings_backup",
            "settings_open_about",
        ).forEach { tag ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape("\"$tag\""))),
                "旧一级行「$tag」不得留在设置子页：它们现在是「我的」hub 的行（决策 §2/§3）",
            )
        }
    }

    /** token 纪律：本屏不写裸 hex、不读静态深色 token、不留 `Mint*` 快捷名。 */
    @Test
    fun `the subpage consumes only tokens`() {
        assertEquals(0, countMatches(source, Regex("""Color\(0x[0-9A-Fa-f]{8}\)""")), "本屏不得写裸 Color(0xFF…)")
        assertEquals(
            0,
            countMatches(source, Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")),
            "本屏不得有 AppPalette./DomainColors. 静态读点",
        )
        assertEquals(0, countMatches(source, Regex("""\bMint[A-Z]\w*""")), "中性色一律 appColors()，不留 Mint* 快捷名")
        assertEquals(0, countMatches(source, Regex("""MaterialTheme\.typography\.""")), "排印走 AppType 六档，不读库内档")
    }
}
