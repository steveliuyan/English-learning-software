package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 25（裁决⑥）源码契约：设置页那一行只做**导航**，三条并行入口在宿主侧收成一条 `showBackupHub`。
 *
 * 为什么这些断言必须在 JVM 侧：connected 用例抓得住「点了没进子页」，却抓不住
 * 「子行删了、`SettingsScreen` 还挂着三枚死回调」与「`overlayOpen` 那条共用 `||` 链只摘了一半」
 * ——后者是本仓第三次踩同类坑（子屏仍在底导之上，但底导也一起可见）。
 */
private const val SETTINGS_UI_SOURCE = "src/main/java/com/example/englishlearning/ui/SettingsScreen.kt"
private const val APP_SCREEN_SOURCE = "src/main/java/com/example/englishlearning/ui/AppScreen.kt"
private const val HUB_SCREEN_SOURCE = "src/main/java/com/example/englishlearning/ui/BackupHubScreen.kt"

class BackupEntryUnificationContractTest {
    private fun stripped(path: String): String = stripComments(File(path).readText())

    /** 设置页只剩一条通往备份子页的回调；旧的折叠态与三条并行子入口必须消失。 */
    @Test fun settings_screen_navigates_instead_of_expanding() {
        val src = stripped(SETTINGS_UI_SOURCE)
        listOf(
            "onOpenBackupExport",
            "onOpenBackupRestore",
            "onOpenBackupPairingExport",
            "backupExpanded",
        ).forEach { term ->
            assertFalse(
                src.contains(term),
                "SettingsScreen.kt 仍出现「$term」：备份已收成子页导航，旧折叠态/并行回调必须摘净" +
                    "（子行删了、宿主还挂着死回调是同一族回归）",
            )
        }
        assertTrue(
            src.contains("onOpenBackupHub"),
            "SettingsScreen.kt 必须出现 onOpenBackupHub（备份行是导航到子页，不是折叠）",
        )
        assertFalse(
            src.contains("settings_open_backup_export") ||
                src.contains("settings_delete_all_learning_data"),
            "旧的四枚备份子行 tag 不得再留在设置页（它们已迁到 BackupHubScreen）",
        )
    }

    /** 宿主：`showBackupHub` 至少落在状态、overlayOpen、BackHandler、挂载四处。 */
    @Test fun host_wires_backup_hub_at_every_station() {
        val src = stripped(APP_SCREEN_SOURCE)
        val hits = Regex("""showBackupHub""").findAll(src).count()
        assertTrue(
            hits >= 4,
            "AppScreen.kt 里 showBackupHub 只出现 $hits 次（至少 4：状态 / overlayOpen / BackHandler / 挂载）",
        )
        // 共用链不许只摘一半：其余半边必须原样留着。
        // 取样必须止于**下一条真代码**（`BackHandler(`）：注释在 [stripComments] 后已不存在，
        // 拿注释行当界会让这段"链"一路吃到文件尾，链内断言变成全文件包含，删项也判绿（变异 M2 实测）。
        val overlay = src.substringAfter("val overlayOpen =").substringBefore("BackHandler(")
        assertTrue(
            overlay.contains("showWordBookWords") && overlay.contains("showMyWords"),
            "overlayOpen 取样失真：截取区间没有落在链尾（取样长度 ${overlay.length}）",
        )
        listOf("showWorksheetSettings", "showReadingFeed").forEach { route ->
            assertTrue(
                overlay.contains(route),
                "overlayOpen 链丢了「$route」——共用 || 链只许追加 showBackupHub，其余路由项一律留",
            )
        }
        assertTrue(overlay.contains("showBackupHub"), "overlayOpen 链必须挂上 showBackupHub")

        // 纯计数抓不住「删了一处、别处还在」（≥4 会被剩余站点满足），四枚站点逐条钉形状：
        // 状态 / BackHandler / 挂载三条各自不可被别处的出现替代。
        listOf(
            """var showBackupHub by rememberSaveable""" to "状态位（rememberSaveable）",
            """BackHandler\(enabled = showBackupHub\) \{ showBackupHub = false \}""" to "BackHandler",
            """\} else if \(showBackupHub\) \{""" to "挂载分支",
            """onOpenBackupHub = \{ showBackupHub = true \}""" to "设置页入口接线",
        ).forEach { (pattern, station) ->
            assertTrue(
                Regex(pattern).containsMatchIn(src),
                "AppScreen.kt 缺「$station」这一枚 showBackupHub 站点（模式：$pattern）——" +
                    "≥4 的计数会被其余站点凑满，所以每条必须单独判红",
            )
        }
    }

    /** 子页：三枚节 tag 与四枚入口 tag 各恰一次（tag 是 connected 判据的身份证）。 */
    @Test fun hub_screen_declares_each_tag_exactly_once() {
        val src = stripped(HUB_SCREEN_SOURCE)
        listOf(
            "backup_hub_screen",
            "backup_hub_section_export",
            "backup_hub_section_restore",
            "backup_hub_section_delete",
            "backup_hub_open_export",
            "backup_hub_open_pairing_export",
            "backup_hub_open_restore",
            "backup_hub_delete_all",
        ).forEach { tag ->
            assertEquals(
                1,
                countMatches(src, Regex(""""\Q$tag\E"""")),
                "BackupHubScreen.kt 里 tag「$tag」必须恰出现 1 次",
            )
        }
    }
}
