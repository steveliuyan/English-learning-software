package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 25（裁决⑥）connected 判据：设置页「备份与恢复」不再就地展开，而是进入一枚真子页
 * [BackupHubScreen]——三节（导出 / 恢复 / 删除全部学习数据）固定顺序、四枚入口各归其节。
 *
 * 口径沿用本包既有纪律：**一个 `@Test` 只 `setContent` 一次**（同一次测试两次 `setContent` 必崩）；
 * 大字号档用**组合期** `LocalDensity provides Density(density, fontScale = 2f)` 取证
 * （先例 `AppScreenOverlayContainerTest`），不碰设备全局 `font_scale`。
 *
 * 这里只钉子页自身的形状与接线；「设置页那一行真的导航到它」「返回键回到设置页」归宿主腿
 * （`SettingsScreenTest` 的折叠行改造 + `AppScreenOverlayContainerTest` 的集成腿）。
 */
@RunWith(AndroidJUnit4::class)
class BackupEntryUnificationTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setHub(
        onBack: () -> Unit = {},
        onOpenBackupExport: () -> Unit = {},
        onOpenBackupPairingExport: () -> Unit = {},
        onOpenBackupRestore: () -> Unit = {},
        onDeleteAllLearningData: () -> Unit = {},
        deleteAllLearningDataNotice: String? = null,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.System) {
                val hub: @androidx.compose.runtime.Composable () -> Unit = {
                    BackupHubScreen(
                        onBack = onBack,
                        onOpenBackupExport = onOpenBackupExport,
                        onOpenBackupPairingExport = onOpenBackupPairingExport,
                        onOpenBackupRestore = onOpenBackupRestore,
                        onDeleteAllLearningData = onDeleteAllLearningData,
                        deleteAllLearningDataNotice = deleteAllLearningDataNotice,
                    )
                }
                if (fontScale == null) {
                    hub()
                } else {
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(density.density, fontScale = fontScale),
                    ) { hub() }
                }
            }
        }
    }

    private val entryTags = listOf(
        "backup_hub_open_export",
        "backup_hub_open_pairing_export",
        "backup_hub_open_restore",
        "backup_hub_delete_all",
    )

    /**
     * 三节固定顺序 + 四枚入口全可达；删除行必须落在「删除」那一节的包络里
     * （只断「都存在」抓不住挂错节）。
     */
    @Test
    fun hubRendersThreeSectionsInOrderWithAllFourEntries() {
        setHub()
        composeRule.onNodeWithTag("backup_hub_screen").assertExists()

        val sectionTops = listOf(
            "backup_hub_section_export",
            "backup_hub_section_restore",
            "backup_hub_section_delete",
        ).map { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value
        }
        assertTrue(
            "三节必须按 导出 → 恢复 → 删除 自上而下排列，实测 top=$sectionTops",
            sectionTops[0] < sectionTops[1] && sectionTops[1] < sectionTops[2],
        )

        entryTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHasClickAction()
        }

        val deleteSection = composeRule.onNodeWithTag("backup_hub_section_delete").getUnclippedBoundsInRoot()
        val deleteRow = composeRule.onNodeWithTag("backup_hub_delete_all").getUnclippedBoundsInRoot()
        assertTrue(
            "「删除全部学习数据」必须落在删除节内（节 $deleteSection，行 $deleteRow）",
            deleteRow.top >= deleteSection.top && deleteRow.bottom <= deleteSection.bottom,
        )

        // 四枚入口的标题与副标题在本屏是**唯一真值**（设置页不再抄一份）；旧口径
        // 「暂不可用于认证恢复」随迁移消失，内部编号一律不得出现。
        listOf(
            "导出学习数据留档",
            "认证导出（可用于新设备恢复）",
            "从旧设备恢复（认证恢复）",
            "删除全部学习数据",
            "生成 `.elpbak` 留档文件；不含密钥，不能用于换机恢复",
            "旧设备系统认证后生成配对码与受保护备份",
            "扫描旧设备配对码并替换本机数据",
            "清空学习记录、复习调度、每日计划与阅读完成；文章与收藏保留",
        ).forEach { copy ->
            composeRule.onNodeWithText(copy).performScrollTo().assertExists()
        }
        listOf("F4-04", "F4-03", "暂不可用于认证恢复").forEach { jargon ->
            assertTrue(
                "子页可见文案不得出现内部编号或过期口径「$jargon」（AC-R-21 / AC-R-22）",
                composeRule.onAllNodesWithText(jargon, substring = true).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    /** 四枚入口各点一次：只触发自己的回调（接线接错时这里先红）。 */
    @Test
    fun eachEntryInvokesOnlyItsOwnCallback() {
        var export = 0
        var pairingExport = 0
        var restore = 0
        var delete = 0
        setHub(
            onOpenBackupExport = { export++ },
            onOpenBackupPairingExport = { pairingExport++ },
            onOpenBackupRestore = { restore++ },
            onDeleteAllLearningData = { delete++ },
        )
        entryTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
            composeRule.waitForIdle()
        }
        assertEquals("留档导出回调", 1, export)
        assertEquals("认证导出回调", 1, pairingExport)
        assertEquals("恢复回调", 1, restore)
        assertEquals("删除回调", 1, delete)
    }

    /** 页头返回键：固定 `contentDescription = "返回"`。 */
    @Test
    fun headerBackInvokesOnBack() {
        var back = 0
        setHub(onBack = { back++ })
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        assertEquals(1, back)
    }

    /** 删除回执是条件渲染：传了读得到，不传不留空行。 */
    @Test
    fun deleteNoticeIsReadableOnTheHub() {
        val receipt = "学习数据已删除；文章与收藏保留。"
        setHub(deleteAllLearningDataNotice = receipt)
        composeRule.onNodeWithText(receipt).performScrollTo().assertIsDisplayed()
    }

    /** 同一子页在未传回执时不得出现那句话（防止实现成常驻空行）。 */
    @Test
    fun deleteNoticeIsAbsentWhenNoReceipt() {
        val receipt = "学习数据已删除；文章与收藏保留。"
        setHub()
        composeRule.onNodeWithText(receipt).assertDoesNotExist()
    }

    /** 大字号（组合期 `fontScale = 2f`）下四枚入口仍逐个可达。 */
    @Test
    fun hubEntriesRemainReachableAtDoubleFontScale() {
        setHub(fontScale = 2f)
        entryTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }
}
