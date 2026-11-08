package com.example.englishlearning.ui

import com.example.englishlearning.ui.components.AppIcon
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 「我的」功能中心台账 + 设置子页 + 两枚新子页的源码契约。
 *
 * 前身是 `SettingsNoDeveloperJargonTest`（设置页 4 组 9 行台账）。2026-11-02 用户拍板
 * （`docs/decisions/2026-11-02-profile-hub-and-four-tabs.md`）把设置降为「我的」里的子页，
 * 台账随形搬到 [ProfileGroups]：**三组 13 行**（我的词汇 3 / 学习 5 / 应用 5）。
 * 直读生产码里的纯数据 object（JVM 可加载），connected 侧 `ProfileScreenTest` 再核对这些
 * tag 真的渲染出来——两张脸对不上都会红。
 *
 * 程序员术语禁令、占位行禁令、备份导出声明三件事原样保留（换文件不换口径）。
 */
private const val PROFILE_FILE = "src/main/java/com/example/englishlearning/ui/ProfileScreen.kt"
private const val SETTINGS_FILE = "src/main/java/com/example/englishlearning/ui/SettingsScreen.kt"

/** Task 25（AC-R-22）：备份导出声明所在的文件（固定文案 `DEFAULT_DISCLOSURE`）。 */
private const val BACKUP_EXPORT_VIEW_MODEL_FILE =
    "src/main/java/com/example/englishlearning/ui/BackupExportViewModel.kt"

class ProfileHubLedgerTest {

    @Test
    fun `profile and settings screens speak user language`() {
        listOf(PROFILE_FILE, SETTINGS_FILE).forEach { path ->
            val src = stripComments(File(path).readText())
            listOf("F4-04", "AI Profile", "Salvage", "salvage").forEach { term ->
                assertFalse(src.contains(term), "${File(path).name} 出现开发者术语「$term」（用户语言要求）")
            }
        }
    }

    @Test
    fun `no placeholder next version rows`() {
        listOf(PROFILE_FILE, SETTINGS_FILE).forEach { path ->
            val src = stripComments(File(path).readText())
            assertFalse(
                Regex("""后续版本|敬请期待|暂未开放""").containsMatchIn(src),
                "${File(path).name} 不得保留「后续版本」占位行",
            )
        }
    }

    /**
     * Task 25（AC-R-22 / 审计 §4）：备份导出的固定声明**不得否认已实现的认证恢复**。
     */
    @Test
    fun `backup export disclosure points at the real restore exit`() {
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
     * hub 台账（决策 §2 + §9）：**恰 3 组、恰 14 行**，行标题、tag、组标题逐字钉死。
     *
     * 14 = 我的词汇 3（参考原样）+ 学习 6（原 5 + U-13 拆出的「学习提醒」）
     * + 应用 5（原 5，「提醒与发音」拆出后「语音朗读」留在本组挨着「AI 服务」）。
     */
    @Test
    fun `profile hub ledger is exactly three groups and fourteen rows`() {
        assertEquals(3, ProfileGroups.all.size, "我的页必须恰 3 组，实为 ${ProfileGroups.all.size} 组")
        assertEquals(
            listOf("我的词汇", "学习", "应用"),
            ProfileGroups.all.map { it.title },
            "三个分组标题按参考 `sMeHome` 钉死",
        )
        assertEquals(
            listOf(3, 6, 5),
            ProfileGroups.all.map { it.rows.size },
            "组内行数按决策 §9 钉死：我的词汇 3 / 学习 6 / 应用 5",
        )
        val rows = ProfileGroups.all.flatMap { it.rows }
        assertEquals(14, rows.size, "一级行必须恰 14 条，实为 ${rows.size} 条")
        assertEquals(
            listOf(
                // U-8（决策 2026-11-03 §5）：词汇三行改取词库分段短名；U-12 同批把末行改「关于」。
                // 行名的**跨屏对齐**由 `WordListEntryNamesFollowBankSegmentsTest` / `AboutRowNameIsJustAboutTest`
                // 从各自权威源推导，这里只钉台账本体——两套口径别混。
                "收藏", "生词", "错词",
                "学习记录", "学习计划", "词书", "学习提醒", "生成默写纸", "词书内容包",
                "AI 服务", "语音朗读", "设置", "数据与备份", "关于",
            ),
            rows.map { it.title },
            "十四行标题按参考与决策 §9 钉死（U-8 / U-12 / U-13 之后：分段短名、「关于」、中转页拆两行）",
        )
        assertEquals(
            listOf(
                "profile_open_favorite_words", "profile_open_new_words", "profile_open_wrong_words",
                "profile_open_learning_records", "profile_open_plan", "profile_open_word_book",
                "profile_open_reminder", "profile_open_worksheet", "profile_open_word_book_transfer",
                "profile_open_ai_profiles", "profile_open_speech", "profile_open_settings",
                "profile_open_backup", "profile_open_about",
            ),
            rows.map { it.tag },
            "十四枚行 tag 是 connected 判据的身份证，逐字钉死",
        )
        val tags = rows.map { it.tag } + ProfileGroups.all.map { it.tag }
        assertEquals(tags.size, tags.toSet().size, "组与行的 testTag 不得重复：$tags")
        assertTrue(rows.none { it.title.isBlank() }, "行标题不许为空")
        // 每一行都必须挂 AppIcon 白名单字形，禁文字徽标（R-E）。
        assertTrue(rows.all { it.icon in AppIconGlyphsForTest }, "每一行都必须给 AppIcon 字形而不是文字徽标")
    }

    /**
     * 设置子页**只放全局开关**（决策 §3）：外观分段 + 一句与参考同义的说明。
     * 十二枚旧分发回调（提醒/词书/备份/关于/AI/安装包…）一律不得回潮。
     */
    @Test
    fun `settings subpage keeps only the appearance switch and the scope notice`() {
        val src = stripComments(File(SETTINGS_FILE).readText())
        listOf(
            "onOpenSetup", "onOpenWorksheet", "onOpenAiProfiles", "onOpenSpeechSettings",
            "onOpenReminderSettings", "onOpenBackupHub", "onOpenAbout",
            "onImportWordBook", "onExportWordBook", "onOpenReminderSpeech", "onOpenWordBookTransfer",
        ).forEach { callback ->
            assertEquals(
                0,
                countMatches(src, Regex(Regex.escape(callback))),
                "设置子页不得持有「$callback」：那是「我的」hub 的行，设置只放全局开关（决策 §3）",
            )
        }
        assertTrue("ThemeModeRow(" in src, "设置子页必须保留主题三态分段")
        assertTrue(
            "onBack: () -> Unit" in src,
            "设置是子页：必须由页头返回位回到「我的」",
        )
        assertTrue(
            src.contains("这一页只放全局开关。学习提醒、语音朗读、计划、词书、备份与许可都在「我的」里，不在这里重复出现。"),
            "设置子页必须带上参考的说明（`sMeSettings` 页末 notice，U-13 后行名随 hub 台账同步）——它现在是真话，不是免责声明",
        )
    }

    /**
     * 词书内容包子页（决策 §4）：沿用原 SAF 通路、忙碌禁用、结果提示与选册对话框。
     * 选册对话框从 `AppScreen.kt` 搬进本屏——文件选择器的 owner 与消费屏同层，
     * 与 `BackupExportScreen` / `BackupRestoreScreen` 的既有分工一致。
     */
    @Test
    fun `word book transfer page owns the saf pipeline and the export picker`() {
        val src = stripComments(uiScreenSource("WordBookTransferScreen.kt"))
        assertEquals(
            1,
            countMatches(src, Regex("""(^|[^A-Za-z0-9_])AppScreenHeader\(""")),
            "词书内容包页必须复用共享页头",
        )
        listOf("word_book_transfer_import", "word_book_transfer_export").forEach { tag ->
            assertEquals(
                1,
                countMatches(src, Regex(Regex.escape("\"$tag\""))),
                "$tag 必须恰一处",
            )
        }
        assertTrue("ActivityResultContracts.OpenDocument()" in src, "导入必须走系统文件选择器（SAF，不申请存储权限）")
        assertTrue(
            "CreateDocument(\"application/zip\")" in src,
            "导出必须走系统「另存为」（.wbpack 是私有 zip 格式）",
        )
        assertEquals(
            1,
            countMatches(src, Regex("""(^|[^A-Za-z0-9_])GlassDialog\(""")),
            "选册对话框必须恰一枚共享玻璃档",
        )
        assertEquals(
            1,
            countMatches(src, Regex("""(^|[^A-Za-z0-9_])PillButton\(""")),
            "选册对话框的「取消」必须走共享药片且恰一枚",
        )
        listOf("word_book_export_pick_", "word_book_export_cancel").forEach { anchor ->
            assertTrue(
                countMatches(src, Regex(Regex.escape("\"$anchor"))) >= 1,
                "选册对话框的定位锚「$anchor」必须在册",
            )
        }
        assertEquals(
            0,
            countMatches(src, Regex("""(^|[^A-Za-z0-9_])AlertDialog\(""")),
            "不得自搓裸 M3 AlertDialog（对话框份数棘轮口径）",
        )
        assertEquals(
            0,
            countMatches(src, Regex("""Color\(0x""")),
            "新屏不得写裸 hex",
        )
        assertTrue(
            "enabled = " in src,
            "忙碌/未导入时子行必须真禁用（enabled 语义），不是「点了没反应」",
        )
    }

    /** 词书内容包子页与设置子页在宿主里各有状态 / 覆盖判定 / 返回 / 挂载四处站点（U-13 后中转页不再有）。 */
    @Test
    fun `host wires the remaining subpages through every station`() {
        val host = stripComments(uiScreenSource("AppScreen.kt"))
        val overlay = host.substringAfter("val overlayOpen =").substringBefore("BackHandler(")
        assertTrue(overlay.contains("showWordBookTransfer"), "overlayOpen 链必须挂上 showWordBookTransfer")
        listOf(
            """var showWordBookTransfer by rememberSaveable""" to "词书内容包状态位",
            """BackHandler\(enabled = showWordBookTransfer\) \{ showWordBookTransfer = false \}""" to "词书内容包 BackHandler",
            """\} else if \(route is AppOverlayRoute\.WordBookTransfer\) \{""" to "词书内容包挂载分支",
            """onOpenWordBookTransfer = \{ showWordBookTransfer = true \}""" to "我的 hub 入口接线",
        ).forEach { (pattern, station) ->
            assertTrue(
                Regex(pattern).containsMatchIn(host),
                "AppScreen.kt 缺「$station」站点（模式：$pattern）",
            )
        }
    }
}

/** 台账里每一行必须给 AppIcon；本测试独立书写一份白名单以防生产侧换成文字徽标。 */
private val AppIconGlyphsForTest = AppIcon.entries.toSet()
