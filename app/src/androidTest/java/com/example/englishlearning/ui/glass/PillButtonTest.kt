package com.example.englishlearning.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintStart
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.DomainLight
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `PillButton` 的设备侧取色取证（Task 14A-ii）。
 *
 * JVM 侧 `PillButtonInkTest` 的纯表契约只有「表自身合规」的证据；本类证明**渲染时真的是这张表在供色**：
 *  - 禁用底按 token 取样 `AppColorLight.tint2`（逐位 `== 0xFFE5E5EA` 旧断言由 `0a6d63a` 引入，与其时禁用底
 *    实际渲染的 `AppPalette.Separator` 字面量一致；`bc256fd` 将该 token 改为 `#E4EFE9`、`42e9c14` 又把禁用底
 *    改接 `tint2`，逐位等值自此必红，14A-ii 基线取证已实测该红样，故改为容差取样）；
 *  - 非品牌 Primary 浅套「容器收窄到 deep、base 端不上屏」的设备侧唯一证据；
 *  - Secondary 浅套 AiSpeech 的 `set.onMint` 例外分支（4.016 残口的真实修复）；
 *  - 非品牌 Primary 深套 knockout 前景（R-14.4 ① 的设备侧证据）。
 *
 * 取样口径统一走 `colorDistance(...) <= 18.0` 容差（与品牌渐变用例同源）；负向断言同样按生产实际消费的
 * `DomainLight` / token 取值，不再引用 `AppPalette` / `DomainColors` 静态兼容源。
 * 每个用例只做一次 `setContent`（同方法两次 setContent 在真机必崩）。
 */
@RunWith(AndroidJUnit4::class)
class PillButtonTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun clickFiresCallbackAndTagsArePresent() {
        var clicked = false
        composeRule.setContent {
            PillButton("试听", onClick = { clicked = true }, testTag = "glass_btn", contentDescription = "试听发音")
        }
        composeRule.onNodeWithTag("glass_btn").assertExists()
        composeRule.onNodeWithContentDescription("试听发音").assertExists()
        composeRule.onNodeWithTag("glass_btn").performClick()
        assertTrue(clicked)
    }

    @Test fun disabledButtonDoesNotFire() {
        var clicked = false
        composeRule.setContent {
            PillButton("试听", onClick = { clicked = true }, enabled = false, testTag = "glass_btn")
        }
        composeRule.onNodeWithTag("glass_btn").performClick()
        composeRule.waitForIdle()
        assertTrue(!clicked)
    }

    @Test fun enabledPrimaryUsesAppleMintGradientAndReadableDarkText() {
        composeRule.setContent { PillButton("开始学习", onClick = {}, testTag = "glass_btn") }
        val bitmap = composeRule.onNodeWithTag("glass_btn").captureToImage().asAndroidBitmap()
        val pixels = (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> bitmap.getPixel(x, y) }
        }
        assertTrue("primary should render mint gradient start", pixels.minOf { colorDistance(it, AppleMintStart.toArgb()) } <= 18.0)
        assertTrue("primary should render softened mint end", pixels.minOf { colorDistance(it, 0xFF65D3B9.toInt()) } <= 18.0)
        assertTrue("primary should render deep teal text", pixels.minOf { colorDistance(it, 0xFF174C44.toInt()) } <= 18.0)
    }

    private fun colorDistance(actual: Int, expected: Int): Double {
        val dr = ((actual shr 16 and 0xFF) - (expected shr 16 and 0xFF)).toDouble()
        val dg = ((actual shr 8 and 0xFF) - (expected shr 8 and 0xFF)).toDouble()
        val db = ((actual and 0xFF) - (expected and 0xFF)).toDouble()
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }

    @Test fun disabledPrimaryDoesNotRenderSaturatedAccent() {
        composeRule.setContent { PillButton("试听", onClick = {}, enabled = false, testTag = "glass_btn") }
        val bitmap = composeRule.onNodeWithTag("glass_btn").captureToImage().asAndroidBitmap()
        val pixels = (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> bitmap.getPixel(x, y) }
        }
        val accent = DomainLight.getValue(AppDomain.AiSpeech).base.toArgb()
        assertFalse(
            "disabled primary must not show saturated mint",
            pixels.any { colorDistance(it, accent) <= 18.0 },
        )
        // 禁用底 = 纯表 set.tint2（浅套 AppColorLight.tint2 #DCEAE1）；按 token 取样，不写逐位等值。
        assertTrue(
            "disabled primary should fill with AppColorLight.tint2",
            pixels.minOf { colorDistance(it, AppColorLight.tint2.toArgb()) } <= 18.0,
        )
    }

    /**
     * B 档「容器收窄到 deep」的设备侧唯一证据：Review + Primary + 浅套 ⇒
     * 画面出现 `DomainLight[Review].deep`，且 `base` 端（#FF9F0A）完全不出现（R-14.1 ① 容器禁令）。
     */
    @Test fun primary_non_brand_light_renders_deep_without_base() {
        composeRule.setContent {
            PillButton("复习计划", onClick = {}, domain = AppDomain.Review, testTag = "glass_btn")
        }
        val bitmap = composeRule.onNodeWithTag("glass_btn").captureToImage().asAndroidBitmap()
        val pixels = (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> bitmap.getPixel(x, y) }
        }
        val deep = DomainLight.getValue(AppDomain.Review).deep.toArgb()
        assertTrue(
            "Review primary container should render domain deep",
            pixels.minOf { colorDistance(it, deep) } <= 18.0,
        )
        val base = DomainLight.getValue(AppDomain.Review).base.toArgb()
        assertFalse(
            "Review primary must not render domain base (R-14.1 container narrowing)",
            pixels.any { colorDistance(it, base) <= 18.0 },
        )
    }

    /**
     * 4.016 残口真实修复的设备侧证据：Secondary + 默认 AiSpeech + 浅套 ⇒
     * 前景取 `AppColorLight.onMint`（#174C44），**不**取域色 deep（#188F76）。
     */
    @Test fun secondary_ai_speech_light_uses_on_mint_ink() {
        composeRule.setContent {
            PillButton("选择语音配置", onClick = {}, style = PillStyle.Secondary, testTag = "glass_btn")
        }
        val bitmap = composeRule.onNodeWithTag("glass_btn").captureToImage().asAndroidBitmap()
        val pixels = (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> bitmap.getPixel(x, y) }
        }
        val onMint = AppColorLight.onMint.toArgb()
        assertTrue(
            "Secondary/AiSpeech (light) foreground should be onMint ink",
            pixels.minOf { colorDistance(it, onMint) } <= 18.0,
        )
        val domainDeep = DomainLight.getValue(AppDomain.AiSpeech).deep.toArgb()
        assertFalse(
            "Secondary/AiSpeech (light) must not fall back to domain deep (4.016 resurgence)",
            pixels.any { colorDistance(it, domainDeep) <= 18.0 },
        )
    }

    /**
     * R-14.4 ① 的设备侧证据：`MintGlassTheme(ThemeMode.Dark)` 下 Learn + Primary ⇒
     * 前景是深套 knockout 墨 `AppColorDark.surface`（#17201C）、容器是深套域色 `#5AC0FF`，且**不得**出现白字
     * （白字压深套亮容器 1.692~2.790 全线崩，正是薄壳把 isDark 写死会塌回的那一支）。
     *
     * 铺 `AppColorDark.outline` 深色页底再取样的原因：`createComposeRule` 宿主 Activity 的窗口底近白，
     * 药片圆角 shape 之外的四角像素会把窗口底采进位图，误触「白字」负向（14A-ii 首跑实测）；
     * 该底色与 knockout 墨（距离 49.7）/ 域色 / 纯白两两距离均 >18，取样面不歧义。
     */
    @Test fun primary_non_brand_dark_uses_dark_knockout_ink() {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Dark) {
                Box(modifier = Modifier.fillMaxSize().background(AppColorDark.outline)) {
                    PillButton("开始学习", onClick = {}, domain = AppDomain.Learn, testTag = "glass_btn")
                }
            }
        }
        val bitmap = composeRule.onNodeWithTag("glass_btn").captureToImage().asAndroidBitmap()
        val pixels = (0 until bitmap.width).flatMap { x ->
            (0 until bitmap.height).map { y -> bitmap.getPixel(x, y) }
        }
        val knockout = AppColorDark.surface.toArgb()
        assertTrue(
            "Learn primary (dark) foreground should be dark knockout surface ink",
            pixels.minOf { colorDistance(it, knockout) } <= 18.0,
        )
        val darkDomain = DomainDark.getValue(AppDomain.Learn).base.toArgb()
        assertTrue(
            "Learn primary (dark) container should render dark domain color",
            pixels.minOf { colorDistance(it, darkDomain) } <= 18.0,
        )
        val white = Color.White.toArgb()
        assertFalse(
            "Learn primary (dark) must not render white text on bright container",
            pixels.any { colorDistance(it, white) <= 18.0 },
        )
    }
}
