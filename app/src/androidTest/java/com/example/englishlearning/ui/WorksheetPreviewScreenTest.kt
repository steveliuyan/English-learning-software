package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.export.WorksheetPdfRenderer
import com.example.englishlearning.learning.worksheet.WorksheetPage
import com.example.englishlearning.ui.theme.MintGlassTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 默写纸预览屏（批 2 Task 5 / spec §3、§5）。
 *
 * 三条判据都对着旧版的病：返回只能滚到底、导出与返回并排、消息只是一行无色小字。
 */
@RunWith(AndroidJUnit4::class)
class WorksheetPreviewScreenTest {
    @get:Rule val composeRule = createComposeRule()

    /**
     * 页头返回位：共享页头自带 48dp 可点区与 `contentDescription = "返回"`，一进屏就能点到，
     * 不用先滚到页面尽头（旧版唯一的返回路是底部那枚「返回修改」实底按钮）。
     */
    @Test
    fun backIsReachableFromTheHeader() {
        var backs = 0
        composeRule.setContent {
            WorksheetPreviewScreen(
                pages = emptyList(),
                renderedFile = null,
                rendering = false,
                message = null,
                onBack = { backs++ },
                onExport = {},
            )
        }

        composeRule.onNodeWithTag("app_screen_header").assertExists()
        composeRule.onNodeWithContentDescription("返回").assertExists().assertHasClickAction().performClick()
        assertEquals(1, backs)
        composeRule.onNodeWithText("返回修改").assertDoesNotExist()
    }

    /**
     * 导出动作吸底、且没有可用文件时不可点：「导出」不许先亮着等人点出一个空 PDF。
     *
     * 前半句判吸底（动作条与导出按钮同屏可见、不吃滚动），后半句判禁用语义。
     */
    @Test
    fun theExportActionIsPinnedAtTheBottomAndWaitsForTheFile() {
        composeRule.setContent {
            WorksheetPreviewScreen(
                pages = emptyList(),
                renderedFile = null,
                rendering = true,
                message = null,
                onBack = {},
                onExport = {},
            )
        }

        composeRule.onNodeWithTag("worksheet_preview_action_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("worksheet_export_action").assertIsDisplayed().assertIsNotEnabled()
    }

    /**
     * 文件就绪后导出可点，页面图也真出来——与上一条配对，证明禁用不是「永远灰着」的假门槛。
     *
     * 用真实走一遍 `WorksheetPdfRenderer` 拿到的 PDF（同一份文件既是预览源也是导出源），
     * 而不是塞一个空文件：空文件会让 `PdfRenderer` 直接抛，测出来的「可点」是假的。
     */
    @Test
    fun theExportActionUnlocksOnceThePdfIsRendered() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pages = listOf(
            WorksheetPage(pageNumber = 1, pageCount = 2, direction = null, answers = emptyList()),
            WorksheetPage(pageNumber = 2, pageCount = 2, direction = null, answers = emptyList()),
        )
        val rendered = WorksheetPdfRenderer(context).render(pages = pages, useFourLineGrid = true).getOrThrow()
        try {
            var exports = 0
            composeRule.setContent {
                WorksheetPreviewScreen(
                    pages = pages,
                    renderedFile = rendered.file,
                    rendering = false,
                    message = null,
                    onBack = {},
                    onExport = { exports++ },
                )
            }

            composeRule.onNodeWithTag("worksheet_export_action").assertIsDisplayed().assertIsEnabled().performClick()
            assertEquals(1, exports)
            // 页面位图是在 IO 线程上渲染出来的（produceState），要等它落位再判——否则测的是「还没画」。
            composeRule.waitUntil(timeoutMillis = 15_000) {
                composeRule.onAllNodesWithTag("worksheet_preview_pages").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("worksheet_preview_pages").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("第 1 页预览").assertIsDisplayed()
        } finally {
            rendered.file.delete()
        }
    }

    /**
     * 失败 / 提示文案走共享 `InlineNotice`（旧版是一行 `bodySmall` 纯文字，混在标题下方看不出来）。
     *
     * `message` 的三种来路（模板缺失、渲染失败、没有能打开 PDF 的应用）都是「这次没成，告诉你原因」，
     * 所以用 Error 态，与设置页同一句提示同一口径。
     */
    @Test
    fun theFailureMessageRendersAsAnInlineNotice() {
        composeRule.setContent {
            WorksheetPreviewScreen(
                pages = emptyList(),
                renderedFile = null,
                rendering = false,
                message = "默写纸生成失败，请稍后重试。",
                onBack = {},
                onExport = {},
            )
        }

        composeRule.onNodeWithTag("inline_notice_error").assertIsDisplayed()
        composeRule.onNodeWithText("默写纸生成失败，请稍后重试。").assertIsDisplayed()
    }

    /**
     * 顶档（`fontScale = 3.0`，AC5-03 判据要求的档，既有 connected 护栏只覆盖到 2.0）下，
     * 本屏的两枚触控目标仍是 ≥48dp 的可点方块：页头返回位 + 吸底「导出 PDF」。
     *
     * 为什么值得单独一条：QA 第三轮任务书 §3 把这三屏列为「零测试引用族」——48dp 与对比度
     * 此前只有源码 `heightIn(min = 52.dp)` 和静态契约支撑，设备上一次都没量过。
     * 本屏的旧版是「返回修改 + 导出 PDF」并排两枚按钮（`Modifier.weight(1f).height(52.dp)`），
     * 批 2 收口后返回上移到共享页头、行里只剩导出，并把固定高改成 `heightIn(min = 52.dp)`
     * （大字号下按钮可自行长高）。所以「两枚按钮行」的判据落到设备上就是这两枚目标：
     * 行内那枚 + 页头那枚，两个都钉。
     *
     * 口径沿用 `AppScreenHeaderTest` 的先例：尺寸断言读 unclipped 矩形（`heightIn` 生效但被祖先
     * 裁掉时仍算达标，正是「目标尺寸」这条契约要的量法）；`assertIsDisplayed()` 单独钉可见性，
     * 不吃滚动——吸底条若在顶档被挤出屏幕，这条会当场红。写法用 `LocalDensity` 换 fontScale，
     * 不改设备全局字体档（改完还得复原，且会同毒化其它用例）。
     */
    @Test
    fun touchTargetsStayAtLeast48dpAtTopFontScale() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 3f)) {
                MintGlassTheme {
                    WorksheetPreviewScreen(
                        pages = emptyList(),
                        renderedFile = null,
                        rendering = false,
                        message = null,
                        onBack = {},
                        onExport = {},
                    )
                }
            }
        }

        val export = composeRule.onNodeWithTag("worksheet_export_action")
        export.assertIsDisplayed()
        export.assertHeightIsAtLeast(48.dp)
        export.assertWidthIsAtLeast(48.dp)

        val back = composeRule.onNodeWithContentDescription("返回")
        back.assertHasClickAction()
        back.assertIsDisplayed()
        back.assertHeightIsAtLeast(48.dp)
        back.assertWidthIsAtLeast(48.dp)
    }
}
