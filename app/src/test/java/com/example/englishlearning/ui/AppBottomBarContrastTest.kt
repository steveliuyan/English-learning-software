package com.example.englishlearning.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.DomainLight
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Task 17A Step 3 对比契约：底导未选中墨（`text2`）压底（`MintSurface`，即当前主题 `surface` token）
 * 在浅、深两套主题下都必须 ≥ 4.5:1（正文口径），选中态字形墨（AiSpeech `deep`）压同一底按
 * 非文本图形 ≥ 3:1（spec §4.3 口径）复算。
 *
 * 阈值留余量（14A 教训）：判据写 `>= 4.5` / `>= 3.0`，**实测值只进断言消息**——
 * 现版 token 下浅套未选中约 5.15、深套约 7.5，把实测数钉死会让下一次合法微调变成改判据。
 *
 * 契约不只算数：两条源码锚把「复算的这一对」绑到生效渲染路径上——
 * 底导容器实际铺 `background(MintSurface)`、未选中墨实际取 `appColors().text2`、
 * 而 `MintSurface` 的 getter 实际读 `LocalAppColors.current.surface`
 * （`MintPalette.kt`），浅/深两套即 `AppColorLight/AppColorDark.surface`。三者任一搬家，本契约红。
 */
class AppBottomBarContrastTest {

    private fun rgb(color: Color): Int = color.toArgb() and 0xFFFFFF

    private fun sourceOf(name: String, expectedDir: File = File(UI_MAIN_SOURCE_PATH)): String {
        val files = uiFiles().filter { it.name == name }
        assertTrue(files.size == 1, "$name 应恰好一个（期望目录 $UI_MAIN_SOURCE_PATH），实际 ${files.size} 个")
        val file = files.single()
        assertTrue(
            file.parentFile == expectedDir,
            "$name 不在 ${expectedDir.path} 下（搬家会让本契约读错文件）：${file.path}",
        )
        return stripComments(file.readText())
    }

    /** 反空转对：正例必须过线、同式取错字段必须掉线，否则「≥阈值」是公式失控的假象。 */
    private fun assertPairClears(fgName: String, fg: Int, bgName: String, bg: Int, floor: Double, decoy: Pair<String, Int>) {
        val r = ratio(fg, bg)
        assertTrue(
            r >= floor,
            "$fgName 压$bgName 只有 %.2f:1，不足 %.1f:1（浅/深两套 token 漂移到此为止，先对 token 再谈底导）".format(r, floor),
        )
        val decoyRatio = ratio(decoy.second, bg)
        assertTrue(
            decoyRatio < floor,
            "反空转失控：同一条公式判「${decoy.first}」(${decoyRatio}) 也过 %.1f 线，阈值或亮度口径失效".format(floor),
        )
    }

    @Test
    fun `unselected text2 clears 4 to 1 on the light bar surface`() {
        assertPairClears(
            fgName = "浅套 text2", fg = rgb(AppColorLight.text2),
            bgName = "浅套 surface 底", bg = rgb(AppColorLight.surface),
            floor = 4.5,
            decoy = "浅套装饰线 text3" to rgb(AppColorLight.text3),
        )
    }

    @Test
    fun `unselected text2 clears 4 to 1 on the dark bar surface`() {
        assertPairClears(
            fgName = "深套 text2", fg = rgb(AppColorDark.text2),
            bgName = "深套 surface 底", bg = rgb(AppColorDark.surface),
            floor = 4.5,
            decoy = "深套装饰线 outline" to rgb(AppColorDark.outline),
        )
    }

    @Test
    fun `selected ai_speech_deep glyph clears 3 to 1 on both surfaces`() {
        assertPairClears(
            fgName = "浅套 AiSpeech deep", fg = rgb(DomainLight.getValue(AppDomain.AiSpeech).deep),
            bgName = "浅套 surface 底", bg = rgb(AppColorLight.surface),
            floor = 3.0,
            decoy = "浅套 AiSpeech base" to rgb(DomainLight.getValue(AppDomain.AiSpeech).base),
        )
        assertPairClears(
            fgName = "深套 AiSpeech deep", fg = rgb(DomainDark.getValue(AppDomain.AiSpeech).deep),
            bgName = "深套 surface 底", bg = rgb(AppColorDark.surface),
            floor = 3.0,
            decoy = "深套装饰线 outline" to rgb(AppColorDark.outline),
        )
    }

    @Test
    fun `the recomputed pairs are the ones the bar actually renders`() {
        val bar = sourceOf("AppBottomBar.kt")
        val palette = sourceOf("MintPalette.kt", File(UI_THEME_SOURCE_PATH))
        assertTrue(
            bar.contains("background(MintSurface)"),
            "底导容器底色不再是 MintSurface：本契约复算的底与真实渲染脱钩，须同步改判据（并在报告里留痕）",
        )
        assertTrue(
            bar.contains("else appColors().text2"),
            "未选中墨必须走 appColors().text2（活 token），否则 text2 对比复算对不上生效路径",
        )
        assertTrue(
            palette.contains("internal val MintSurface: Color") &&
                palette.contains("get() = LocalAppColors.current.surface"),
            "MintSurface 不再桥接 LocalAppColors.surface：浅/深两套 surface 复算失去依据",
        )
    }
}
