package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 19（AC-R-21）源码契约：设置页只许说用户语言。
 *
 * 「F4-04 / AI Profile / Salvage」这类内部术语曾以字符串字面量混进一级页文案
 * （`.elpbak` 副标题、配对导出与恢复的括号注、占位行副标题），源码文本即契约：
 * 先剥注释（共用夹具 [stripComments]，字符串字面量原样保留）再禁词扫描，
 * 注释里的历史说明不受本契约约束。占位行禁令对应 IA 决策文档裁决②：
 * 「后续版本 / 敬请期待 / 暂未开放」的假入口整族删除，不降级为灰行。
 * 第三条用例把裁决②/⑥ 的 IA 形状（4 组 9 行与逐行标题）钉在 [SettingsGroups] 台账上。
 */
private const val SETTINGS_FILE = "src/main/java/com/example/englishlearning/ui/SettingsScreen.kt"

/** Task 25（AC-R-22）：备份导出声明所在的文件（固定文案 `DEFAULT_DISCLOSURE`）。 */
private const val BACKUP_EXPORT_VIEW_MODEL_FILE =
    "src/main/java/com/example/englishlearning/ui/BackupExportViewModel.kt"

class SettingsNoDeveloperJargonTest {
    @Test fun settings_screen_speaks_user_language() {
        val src = stripComments(File(SETTINGS_FILE).readText())
        listOf("F4-04", "AI Profile", "Salvage", "salvage").forEach { term ->
            assertFalse(src.contains(term), "设置页出现开发者术语「$term」（AC-R-21 要求用户语言）")
        }
    }

    @Test fun no_placeholder_next_version_rows() {
        val src = stripComments(File(SETTINGS_FILE).readText())
        assertFalse(
            Regex("""后续版本|敬请期待|暂未开放""").containsMatchIn(src),
            "设置页不得保留「后续版本」占位行（裁决②）",
        )
    }

    /**
     * Task 25（AC-R-22 / 审计 §4）：备份导出的固定声明**不得否认已实现的认证恢复**。
     *
     * 旧文案写「当前包不可用于认证恢复，F4-04 完成后才支持恢复」——认证恢复（F4-04）早已实现，
     * 这句话既过期又让用户以为换机恢复不存在；`Salvage` 还是内部术语。新文案必须指向真实出口
     * 「认证导出」。注释里的历史说明不计入本契约（先剥注释）。
     */
    @Test fun backup_export_disclosure_points_at_the_real_restore_exit() {
        val src = stripComments(File(BACKUP_EXPORT_VIEW_MODEL_FILE).readText())
        listOf("Salvage", "F4-04", "暂不可用于认证恢复", "完成后才支持").forEach { term ->
            assertFalse(
                src.contains(term),
                "备份导出声明出现「$term」：不得否认已实现的认证恢复，也不得把内部编号写给用户（AC-R-22）",
            )
        }
        assertTrue(
            src.contains("认证导出"),
            "备份导出声明必须指向真实出口「认证导出」（只说「不能换机恢复」而不给出口，用户读完仍不知道怎么办）",
        )
    }

    /**
     * IA 台账（AC-R-21 / 裁决②/⑥）：**恰 4 组、恰 9 行**，行标题与 tag 逐字钉死。
     * 直读生产码里的 [SettingsGroups]（纯数据 object，JVM 可加载），connected 侧
     * `SettingsScreenTest` 再核对这些 tag 真的渲染出来——两张脸对不上都会红。
     *
     * 2026-10-31 去重：许可行从设置移除（唯一入口在关于页），行数 10 → 9。
     */
    @Test fun settings_groups_are_exactly_four_groups_nine_rows() {
        assertEquals(4, SettingsGroups.all.size, "设置页必须恰 4 组，实为 ${SettingsGroups.all.size} 组")
        assertEquals(
            listOf("学习", "AI 与语音", "外观与关于", "数据与安全"),
            SettingsGroups.all.map { it.title },
            "四个分组标题按裁决②钉死（「学习提醒」归「学习」组，不再有「账户」「阅读」组）",
        )
        val rows = SettingsGroups.all.flatMap { it.entries }
        assertEquals(9, rows.size, "设置页一级行必须恰 9 条（2026-10-31 许可行移出本页），实为 ${rows.size} 条")
        assertEquals(
            listOf(
                "词书与每日目标", "学习提醒", "生成默写纸",
                "AI 服务与密钥", "语音朗读",
                "主题", "关于",
                "词书内容包", "备份与恢复",
            ),
            rows.map { it.title },
            "九行标题按稿 §7④ 与裁决②钉死（许可与备份的重复入口已按 2026-10-31 决策收口）",
        )
        val tags = rows.map { it.tag } + SettingsGroups.all.map { it.tag }
        assertEquals(tags.size, tags.toSet().size, "组与行的 testTag 不得重复：$tags")
        assertTrue(rows.none { it.title.isBlank() }, "行标题不许为空")
        // 除「主题」分段卡外，每一行都必须挂 AppIcon 白名单字形，禁文字徽标（R-E）。
        assertEquals(
            listOf("settings_theme_mode"),
            rows.filter { it.icon == null }.map { it.tag },
            "只许「主题」一行没有行首图标；其余行必须给 AppIcon 字形而不是文字徽标",
        )
    }

    /**
     * 修复轮 1（评审 I3）：**台账不得是第二套真值**——9 枚行标题与 4 枚组标题在
     * `SettingsScreen.kt` 里各只许出现一次，且那次就在 [SettingsGroups] 的定义处。
     *
     * 这条为什么必须在 JVM 侧：connected 侧 `SettingsScreenTest` 拿 `entry.title` 去树上找，
     * 抓得住「台账改了、渲染没改」这种**漂移**，却抓不住渲染处另抄了一份**恰好相同**的字面量
     * （两份都改齐时它全绿，而第二套真值已经种下）。`125ced3` 的现场就是后一种：
     * 台账 9 枚 + 渲染 9 枚，逐行两枚。现在渲染侧只按 tag 经 `settingsEntry` 取，
     * 本用例把这枚「只许一处」钉死；把标题再抄回调用处，这条先红。
     *
     * 读法同族口径：先 [stripComments]（注释里用「」引述的标题不计），只数**带引号的字面量**，
     * 并配一枚合成负片段自证计数器不是空转。
     */
    @Test fun ledger_titles_are_defined_exactly_once_in_settings_screen() {
        val src = stripComments(File(SETTINGS_FILE).readText())
        val titles = SettingsGroups.all.flatMap { group ->
            listOf(group.title) + group.entries.map { it.title }
        }
        assertEquals(13, titles.size, "4 组 + 9 行共 13 枚标题（台账形状变了先改这条）")
        titles.forEach { title ->
            val quoted = Regex(""""\Q$title\E"""")
            assertEquals(
                1,
                countMatches(src, quoted),
                "标题「$title」在 SettingsScreen.kt 里必须只写在 SettingsGroups 一处" +
                    "（渲染侧另抄一份 = 第二套真值；改成按 tag 读台账）",
            )
        }
        // 反空转：计数器必须判得出「同一枚标题抄了两份」这个形状。
        assertEquals(
            2,
            countMatches("""Text("词书与每日目标"); SettingsEntry("词书与每日目标", …)""", Regex(""""词书与每日目标""")),
            "计数器失控：抄了两份也只数出 1 处，本用例就成了空判",
        )
    }
}
