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
import org.junit.Assert.assertEquals
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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

    /**
     * 批 2 全分支终审评审 I-1 的**设备侧**背书：`Modifier.clickable` 的 `role` 默认 `null`，
     * 缺它则语义树只有「可点按」而无「按钮」，无障碍三元组（testTag + Role + 状态）断一环。
     * JVM 侧 `PillButtonInkTest` 那条只钉源码形状（体内恰一处 `clickable(… role = Role.Button …)`），
     * 本例钉**渲染面**：角色真的挂在那枚唯一的可点节点上。
     *
     * 三条判据一起才有牙：
     *  - 正向：带 tag 的节点必须 `expectValue(Role, Role.Button)`（合并树与未合并树各钉一次——
     *    只钉合并树时，角色由父级合并上来的写法也会算过）；
     *  - 份数：整棵树里 `Role.Button` 恰一枚，且可点节点也恰一枚，两枚是同一个节点
     *      （角色漏挂、或长到正文/面板上多出一枚可点源，都当场红）；
     *  - 反向：标签 `Text` 节点自己**不带** Role——这条证明正向断言不是「任何节点都算命中」的空转。
     */
    @Test
    fun pill_button_carries_the_button_role_on_its_clickable_node() {
        composeRule.setContent { PillButton("试听", onClick = {}, testTag = "glass_btn") }
        val roleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        composeRule.onNodeWithTag("glass_btn").assert(roleMatcher)
        composeRule.onNodeWithTag("glass_btn", useUnmergedTree = true).assert(roleMatcher)
        assertEquals(
            "整棵树恰一枚 Button 角色节点（多挂 = 面板长出第二个按钮语义）",
            1,
            composeRule.onAllNodes(roleMatcher).fetchSemanticsNodes().size,
        )
        assertEquals(
            "可点节点也必须恰一枚，且就是带角色的那一枚",
            composeRule.onAllNodes(roleMatcher).fetchSemanticsNodes().map { it.id },
            composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.id },
        )
        val label = composeRule.onNodeWithText("试听", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "标签 Text 节点自身不得带 Role（否则上面的正向断言可能是自证）：实测 ${label.config.getOrNull(SemanticsProperties.Role)}",
            label.config.getOrNull(SemanticsProperties.Role) == null,
        )
    }

    /**
     * 禁用档同一条角色的取证：`enabled = false` 只改可点性，角色必须**仍在**——
     * TalkBack 用户要靠「按钮，已停用」判断处境，禁用即丢角色等于把控件种类一起抹掉。
     * （禁用不触发回调已由 [disabledButtonDoesNotFire] 钉过，本例不重复点击。）
     */
    @Test
    fun disabled_pill_button_keeps_the_button_role() {
        composeRule.setContent { PillButton("试听", onClick = {}, enabled = false, testTag = "glass_btn") }
        composeRule.onNodeWithTag("glass_btn").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
