package com.example.englishlearning.ui.glass

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.ui.AiProfileDraft
import com.example.englishlearning.ui.AiProfileEditorUiState
import com.example.englishlearning.ui.AiProfileListUiState
import com.example.englishlearning.ui.AiProfileSettingsScreen
import com.example.englishlearning.ui.SpeechSettingsScreen
import com.example.englishlearning.ui.SpeechSettingsUiState
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.components.glass.pillButtonInk
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.DomainLight
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 批 2 终审修复席 B 的**配对像素**取证（task-18fix-b）：三块玻璃面板的容器与面板内前景必须同时来自
 * 当前那一套主题 token，且浅色侧逐值不变。
 *
 * 为什么必须上真机像素：JVM 侧 `GlassPanelInkPairingContractTest` 只能钉源码形状（容器恰一处
 * `appColors().surfaceGlass`、面板内每一枚排印点带显式主题墨），钉不出组合期真的取到了本套的色。
 * 「容器迁了、墨没迁」在深色档正是批 2 终审 B 席 C-1 那条「亮墨压白玻璃」（实测 ≈2.18:1）的形状。
 *
 * 三块面板 = `GlassFillBackdropDebtLedger` 里本席迁走的三行：
 *  ① `GlassOverlay` 浮层面板（消费者：`AiProfileSettingsScreen` 音色浮层，选项行 `VoiceOverlayOption`）；
 *  ② `AiProfileSettingsScreen` 的 `SectionCard`（含台账点名的 `CapabilityToggle`）；
 *  ③ `SpeechSettingsScreen` 的 `PreviewCard`（试听卡）。
 * 每块浅/深各一例，另加 `CapabilityToggle` 一例与 C-1 那枚 `PillStyle.Text` 药片的**当场现算**一例。
 *
 * 取样口径五条：
 *  - **整屏截图 + 语义节点 bounds 定位**：`onRoot().captureToImage()` 给的是**已合成**的屏幕像素，
 *    半透明玻璃压在真实页底上的结果就是用户看到的那一档，不必在用例里抄生产几何；取样带由
 *    `boundsInRoot` 现算（面板内边距里那段只有容器、没有字），不写死任何屏幕坐标。
 *  - **容器取不透明像素众数；墨取「离容器众数足够远」的那一族的众数**：
 *    一个文本节点自身像素里底色永远多于字身（字身只占几成），不排除容器就会把玻璃当成墨；
 *    排除门槛 [INK_MIN_DELTA_FROM_BACKDROP] 又把抗锯齿过渡像素（介于两者之间、且每枚值都不同）滤掉，
 *    剩下的字身核心才是众数。取样前必须把节点 `performScrollTo()` 进视口：两块屏都是整列 `verticalScroll`，
 *    视口外节点的 `captureToImage` 区域宽/高为 0，`Bitmap.createBitmap` 直接抛。
 *  - **期望值走 alpha 合成**：`surfaceGlass` 浅 `0xEF` / 深 `0xE6` 都是半透明，直接比 token 原值必然差一整档；
 *    用例内先 src-over 把玻璃与它脚下的实底合成，再比（逐通道 sRGB，与绘制一致）。
 *  - **反向判据**：本套墨还得离**另一套**那档墨足够远，否则「浅套常量写死」在深色档可能因两档近似而蒙过。
 *  - **浅色侧不变量**：浅档用例的期望值取**迁移前那两枚静态常量**（`AppPalette.GlassFill` /
 *    `DomainColors.AiSpeech.deep` / `AppPalette.TextPrimary` / `TextSecondary`），并在断言前先钉住
 *    「浅套 token 与之逐字相等」——浅档像素用例改完仍绿就是浅色侧零变化的硬证据。
 *
 * 每个方法只调一次 `setContent`（同方法两次真机必崩，Task 9 实测）；浮层用例先点开下拉再取样
 * （`GlassOverlay` 在 `visible=false` 时不组合任何内容）。本类不碰 SAF、不做点击动作断言。
 */
@RunWith(AndroidJUnit4::class)
class GlassPanelThemeInkTest {
    @get:Rule val composeRule = createComposeRule()

    // ---------- ② AI 配置卡 SectionCard ----------

    @Test
    fun the_light_section_card_still_paints_the_pre_migration_glass_and_ink() {
        assertLightTokensEqualThePreMigrationStatics()
        setEditor(ThemeMode.Light)
        scrollToTag(SECTION_CARD_INK_TAG)
        val glass = modeOf(bandLeftOfTag(SECTION_CARD_INK_TAG, CARD_BAND_INSET_DP, CARD_BAND_WIDTH_DP))
        val ink = inkOfTag(SECTION_CARD_INK_TAG, glass)
        assertGlassIs("SectionCard 容器（浅）", glass, AppPalette.GlassFill, AppColorLight.background)
        // 批 2 终审修复席 C-1（控制者裁决）：密钥态那行 `style = AppType.Body` 是正文档，本腿由
        // 「装饰档 3.1 放行」归位到 [BODY_AA_RATIO]。生产行按 `AppBottomBar.kt:95` 的形状按套钉死后，
        // 浅套这枚墨是 `AppColorLight.onMint`（#174C44，压本底 9.727:1，本席现算）；玻璃容器腿仍与迁移前逐值相同，
        // 浅套侧观感变化只剩前景这一枚（薄荷绿墨→深墨），已列入 18-ii 用户目检。
        assertInkIs("SectionCard 密钥态墨（浅，套内 onMint）", ink, AppColorLight.onMint)
        assertInkIsFarFromTheOtherSet("SectionCard 密钥态墨（浅）", ink, DomainColors.AiSpeech.deep)
        assertContrastAtLeast("浅套密钥态正文墨压浅玻璃（Body = 正文档，阈值 4.5）", ink, glass, BODY_AA_RATIO)
        // 反空转：修复前那枚浅套域色墨（#188F76，现算 4.006:1）喂进同一条 4.5 闸必须不合格——
        // 它若 ≥4.5，说明本行的 4.5 闸没有对象或阈值被写宽，归位裁决须重议。
        val preFixInk = DomainColors.AiSpeech.deep.toArgb()
        val preFixRatio = contrastRatio(preFixInk, glass)
        assertTrue(
            "反空转失控：修复前的浅套域色墨 ${preFixInk.hex()} 压本行浅玻璃 ${glass.hex()} 实测 ${preFixRatio.r2()}，" +
                "已 ≥$BODY_AA_RATIO——那这条正文闸对这行没有对象，换墨与阈值归位的裁决都必须重议",
            preFixRatio < BODY_AA_RATIO,
        )
    }

    @Test
    fun the_dark_section_card_pairs_dark_glass_with_the_dark_set_ink() {
        assertTrue(
            "深浅两套玻璃必须真的不同值，否则本用例的换套判据没有对象",
            AppColorLight.surfaceGlass.toArgb() != AppColorDark.surfaceGlass.toArgb(),
        )
        setEditor(ThemeMode.Dark)
        scrollToTag(SECTION_CARD_INK_TAG)
        val glass = modeOf(bandLeftOfTag(SECTION_CARD_INK_TAG, CARD_BAND_INSET_DP, CARD_BAND_WIDTH_DP))
        val ink = inkOfTag(SECTION_CARD_INK_TAG, glass)
        assertGlassIs("SectionCard 容器（深）", glass, AppColorDark.surfaceGlass, AppColorDark.background)
        assertInkIs("SectionCard 密钥态墨（深）", ink, DomainDark.getValue(AppDomain.AiSpeech).deep)
        assertInkIsFarFromTheOtherSet("SectionCard 密钥态墨（深）", ink, DomainColors.AiSpeech.deep)
        assertContrastAtLeast("深套域色墨压深玻璃", ink, glass, BODY_AA_RATIO)
    }

    /**
     * 台账点名的 `CapabilityToggle`（Task 12.5 修复轮曾被单独回退成 `AppPalette.Surface` / `Separator`）：
     * 深色档未选中档的底必须是**套内 surface**，选中档必须是**套内域色 base 的 12% alpha 压在这层玻璃上**。
     * 取样取行内左端那段只有该行底色、没有字的带（行左内边距 12dp）。
     */
    @Test
    fun the_dark_capability_toggle_container_follows_the_theme_instead_of_the_static_surface() {
        setEditor(ThemeMode.Dark, capabilities = setOf(AiCapability.Text, AiCapability.Speech))
        val selectedFill = modeOf(bandInsideTag("ai_profile_capability_text", 3f, 6f))
        val unselectedFill = modeOf(bandInsideTag("ai_profile_capability_vision", 3f, 6f))
        assertGlassIs("CapabilityToggle 未选中档底（深）", unselectedFill, AppColorDark.surface, AppColorDark.surfaceGlass)
        assertGlassIs(
            "CapabilityToggle 选中档底（深，域色 base 12% 压深玻璃）",
            selectedFill,
            DomainDark.getValue(AppDomain.AiSpeech).base.copy(alpha = 0.12f),
            AppColorDark.surfaceGlass,
        )
        assertTrue(
            "选中档与未选中档必须可分辨（两档同值 = 这一族取值等于没迁）：选中 ${selectedFill.hex()}、未选中 ${unselectedFill.hex()}",
            rgbDelta(selectedFill, unselectedFill) >= 3,
        )
        assertTrue(
            "未选中档不得仍是迁移前写死的白面（AppPalette.Surface = #FFFFFFFF）：实测 ${unselectedFill.hex()}",
            rgbDelta(unselectedFill, AppPalette.Surface.toArgb()) >= WRONG_SET_INK_MIN_CHANNEL_DELTA,
        )
    }

    // ---------- ① GlassOverlay 音色浮层 ----------

    @Test
    fun the_light_voice_overlay_still_paints_the_pre_migration_glass_and_ink() {
        assertLightTokensEqualThePreMigrationStatics()
        setEditor(ThemeMode.Light, capabilities = setOf(AiCapability.Text, AiCapability.Speech))
        openVoiceOverlay()
        val glass = modeOf(bandInsideTag(OVERLAY_UNSELECTED_TAG, OVERLAY_BAND_INSET_DP, OVERLAY_BAND_WIDTH_DP))
        val ink = inkOfText(OVERLAY_UNSELECTED_TEXT, glass)
        assertGlassIs("GlassOverlay 容器（浅）", glass, AppPalette.GlassFill, scrimOver(AppColorLight.background))
        assertInkIs("浮层未选中选项墨（浅）", ink, AppPalette.TextPrimary)
        assertContrastAtLeast("浅套正文墨压浅玻璃", ink, glass, BODY_AA_RATIO)
        // 批 2 终审修复席 C（控制者裁决，B 席 concerns ①）：音色浮层**选中档**那行是 `style = AppType.Body`
        // ⇒ 正文档，阈值线就是 [BODY_AA_RATIO]（4.5），曾被按装饰档 3.1 放行。修复前浅套域色墨 #188F76
        // 压本行浅玻璃实底现算只有 4.006:1，两套都不达标；现换成 `appColors().onMint`
        // （浅套 #174C44 压 #FEFFFE = 9.727:1）。浅套这一处观感有变（选中行薄荷绿墨→深墨），属修 AA 失败非回归。
        val selectedInk = inkOfText(OVERLAY_SELECTED_TEXT, glass)
        assertInkIs("浮层选中选项墨（浅，套内 onMint）", selectedInk, AppColorLight.onMint)
        assertInkIsFarFromTheOtherSet("浮层选中选项墨（浅）", selectedInk, DomainDark.getValue(AppDomain.AiSpeech).deep)
        assertContrastAtLeast("浅套选中行正文墨压浅玻璃（Body = 正文档，阈值 4.5）", selectedInk, glass, BODY_AA_RATIO)
        // 反空转：把修复前那枚浅套域色墨喂进同一条 4.5 闸，必须不合格——它若被判 ≥4.5，
        // 说明这条正文闸对这行没有对象或阈值被写宽，本席的换墨与归位裁决都必须重议。
        val preFixInk = DomainColors.AiSpeech.deep.toArgb()
        val preFixRatio = contrastRatio(preFixInk, glass)
        assertTrue(
            "反空转失控：修复前的浅套域色墨 ${preFixInk.hex()} 压本行浅玻璃 ${glass.hex()} 实测 ${preFixRatio.r2()}，" +
                "已 ≥$BODY_AA_RATIO —— 那这条正文闸对这行没有对象，换墨与阈值归位的裁决都要重议",
            preFixRatio < BODY_AA_RATIO,
        )
    }

    /**
     * 深色档：证「暗玻璃 + 主题浅墨」这一对。容器 = `AppColorDark.surfaceGlass` 压「深页底 + Scrim」的实底；
     * 未选中选项墨 = `AppColorDark.text1`，选中那档 = 深套 `onMint`（批 2 修复席 C 与浅套同一枚 token，
     * 现算 #DFF6EC 压深玻璃 #161F1B = 14.873:1，远高于正文档 4.5）。
     * 迁移前的形状（静态白玻璃 + 静态浅套墨）在这一例的容器腿与反向墨腿各自当场判红。
     */
    @Test
    fun the_dark_voice_overlay_pairs_dark_glass_with_the_dark_set_inks() {
        setEditor(ThemeMode.Dark, capabilities = setOf(AiCapability.Text, AiCapability.Speech))
        openVoiceOverlay()
        val glass = modeOf(bandInsideTag(OVERLAY_UNSELECTED_TAG, OVERLAY_BAND_INSET_DP, OVERLAY_BAND_WIDTH_DP))
        val unselectedInk = inkOfText(OVERLAY_UNSELECTED_TEXT, glass)
        val selectedInk = inkOfText(OVERLAY_SELECTED_TEXT, glass)
        composeRule.onNodeWithTag(OVERLAY_SELECTED_TAG).assertExists()
        assertGlassIs("GlassOverlay 容器（深）", glass, AppColorDark.surfaceGlass, scrimOver(AppColorDark.background))
        assertInkIs("浮层未选中选项墨（深）", unselectedInk, AppColorDark.text1)
        assertInkIs("浮层选中选项墨（深，沿用域色 deep）", selectedInk, DomainDark.getValue(AppDomain.AiSpeech).deep)
        assertInkIsFarFromTheOtherSet("浮层未选中选项墨（深）", unselectedInk, AppPalette.TextPrimary)
        assertInkIsFarFromTheOtherSet("浮层选中选项墨（深，须是深套 deep 而非浅套那枚 onMint）", selectedInk, AppColorLight.onMint)
        assertContrastAtLeast("深套浅墨压深玻璃", unselectedInk, glass, BODY_AA_RATIO)
        assertContrastAtLeast("深套选中行正文墨压深玻璃（Body = 正文档，阈值 4.5）", selectedInk, glass, BODY_AA_RATIO)
    }

    // ---------- ③ SpeechSettingsScreen 试听卡 PreviewCard ----------

    @Test
    fun the_light_preview_card_still_paints_the_pre_migration_glass_and_ink() {
        assertLightTokensEqualThePreMigrationStatics()
        setSpeechScreen(ThemeMode.Light)
        val glass = modeOf(bandLeftOfTag(PREVIEW_INPUT_TAG, CARD_BAND_INSET_DP, CARD_BAND_WIDTH_DP))
        val ink = inkOfText(PREVIEW_FOOTNOTE_TEXT, glass)
        assertGlassIs("PreviewCard 容器（浅）", glass, AppPalette.GlassFill, AppColorLight.background)
        assertInkIs("PreviewCard 说明墨（浅）", ink, AppPalette.TextSecondary)
        assertContrastAtLeast("浅套说明墨压浅玻璃", ink, glass, BODY_AA_RATIO)
    }

    /**
     * 深色档：容器 = 深玻璃压深页底；卡内说明墨 = `AppColorDark.text2`。
     * 输入框容器曾被写死成 `Color.White`（浅套与套内 `surface` 逐字相同），深色档必须是**套内 surface**——
     * 「深玻璃里的白底输入框」就是本席要消掉的那一侧，这条一并钉住。
     */
    @Test
    fun the_dark_preview_card_pairs_dark_glass_with_dark_set_inks() {
        setSpeechScreen(ThemeMode.Dark)
        val glass = modeOf(bandLeftOfTag(PREVIEW_INPUT_TAG, CARD_BAND_INSET_DP, CARD_BAND_WIDTH_DP))
        val ink = inkOfText(PREVIEW_FOOTNOTE_TEXT, glass)
        val fieldFill = modeOfTag(PREVIEW_INPUT_TAG)
        assertGlassIs("PreviewCard 容器（深）", glass, AppColorDark.surfaceGlass, AppColorDark.background)
        assertInkIs("PreviewCard 说明墨（深）", ink, AppColorDark.text2)
        assertInkIsFarFromTheOtherSet("PreviewCard 说明墨（深）", ink, AppPalette.TextSecondary)
        assertGlassIs("PreviewCard 输入框底（深，套内 surface）", fieldFill, AppColorDark.surface, AppColorDark.surfaceGlass)
        assertContrastAtLeast("深套说明墨压深玻璃", ink, glass, BODY_AA_RATIO)
    }

    // ---------- C-1 那枚药片：迁完必须现算 ----------

    /**
     * 批 2 终审 B 席 C-1 的当场现算：密钥卡里那枚 `PillStyle.Text` + `domain = Reading`
     * 的「清除已保存的密钥」（`ai_profile_key_clear`）。Text 档容器透明 ⇒ 真实底就是它脚下那层**半透明**玻璃。
     *
     * 顺序不许反：① 从纯表 [pillButtonInk] 取当前档位（深套 / Reading / enabled / Text）的那枚前景
     * （本用例不自己写死墨，纯表改了这里就跟着改）；② 把深套 `surfaceGlass` 与它脚下的深页底 src-over
     * 合成成不透明实底；③ 按 WCAG 算 ①压② —— 正文档必须 ≥ [BODY_AA_RATIO]；
     * ④ 再拿屏幕像素复核同一对（药片带里的墨核 / 容器众数），纸上与屏幕上必须同档。
     *
     * 迁移前这枚是「深套 `#FF7A93` 压静态白玻璃」≈ 2.18:1，跌破正文 AA；迁完两侧都过 4.5 才算闭。
     */
    @Test
    fun the_c1_text_pill_on_the_migrated_dark_glass_clears_body_contrast_by_live_computation() {
        setEditor(ThemeMode.Dark)
        val ink = pillButtonInk(
            style = PillStyle.Text,
            domain = AppDomain.Reading,
            enabled = true,
            set = AppColorDark,
            palette = DomainDark.getValue(AppDomain.Reading),
            isDark = true,
        ).foreground
        val paperBackdrop = compositeSrcOver(AppColorDark.surfaceGlass.toArgb(), AppColorDark.background.toArgb())
        val paperRatio = contrastRatio(ink.toArgb(), paperBackdrop)
        log("C-1 纸面：纯表墨 ${ink.toArgb().hex()} 压合成底 ${paperBackdrop.hex()} = ${paperRatio.r2()}")

        scrollToTag(PILL_TAG)
        val pillBounds = boundsOfTag(PILL_TAG)
        val pillGlass = modeOf(pillBounds)
        val pillInk = inkModeOf(pillBounds, pillGlass)
        val pixelRatio = contrastRatio(pillInk, pillGlass)
        log("C-1 像素：药片墨核 ${pillInk.hex()} 压面板底 ${pillGlass.hex()} = ${pixelRatio.r2()}")

        assertInkIs("C-1 药片墨（像素必须等于纯表那一档）", pillInk, ink)
        assertGlassIs("C-1 药片真实底（它脚下那层玻璃）", pillGlass, AppColorDark.surfaceGlass, AppColorDark.background)
        assertContrastAtLeast("C-1 纸面现算", ink.toArgb(), paperBackdrop, BODY_AA_RATIO)
        assertContrastAtLeast("C-1 像素现算", pillInk, pillGlass, BODY_AA_RATIO)
        assertTrue(
            "纸面与像素两条比值必须同档（差 ≤ $PAPER_PIXEL_RATIO_TOLERANCE）：纸上 ${paperRatio.r2()}、像素 ${pixelRatio.r2()}",
            abs(paperRatio - pixelRatio) <= PAPER_PIXEL_RATIO_TOLERANCE,
        )
    }

    // ---------- 夹具 ----------

    private fun setEditor(mode: ThemeMode, capabilities: Set<AiCapability> = setOf(AiCapability.Text)) {
        composeRule.setContent {
            MintGlassTheme(themeMode = mode) {
                AiProfileSettingsScreen(
                    // 列表态与取证无关：editor 非空时整屏渲染的就是编辑态（含三块 SectionCard 与浮层）。
                    listState = AiProfileListUiState.Ready(emptyList()),
                    editor = AiProfileEditorUiState(
                        profileId = "p1",
                        draft = AiProfileDraft(
                            displayName = "公司网关",
                            websiteUrl = "https://example.com",
                            endpoint = "https://api.example.com/v1",
                            model = "gpt-4o-mini",
                            capabilities = capabilities,
                            voice = "nova",
                            pendingKey = "",
                        ),
                        hasStoredKey = true,
                    ),
                    onAdd = {},
                    onEdit = {},
                    onDraftChange = {},
                    onSave = {},
                    onCloseEditor = {},
                    onDeleteProfile = {},
                    onDeleteKey = {},
                    onBack = {},
                )
            }
        }
    }

    private fun setSpeechScreen(mode: ThemeMode) {
        composeRule.setContent {
            MintGlassTheme(themeMode = mode) {
                SpeechSettingsScreen(
                    state = SpeechSettingsUiState(
                        selectedEngine = PronunciationEngine.SystemTts,
                        previewMessage = "已提交试听",
                    ),
                    onSelect = { _, _ -> },
                    onOpenAiProfiles = {},
                    onAddMiMoPreset = {},
                    onPreview = {},
                    onBack = {},
                )
            }
        }
    }

    private fun openVoiceOverlay() {
        composeRule.onNodeWithTag("ai_profile_voice").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_profile_voice_overlay").assertExists()
    }

    /** 浅套 token 与迁移前静态常量逐字相等：浅色侧「零变化」的纸面那一半（另一半是浅档像素期望直接取旧静态值）。 */
    private fun assertLightTokensEqualThePreMigrationStatics() {
        assertEquals(
            "浅套 surfaceGlass 必须与迁移前的 AppPalette.GlassFill 逐字相同，否则浅色观感随迁移漂移",
            AppPalette.GlassFill.toArgb(),
            AppColorLight.surfaceGlass.toArgb(),
        )
        assertEquals(
            "浅套 text1 必须与迁移前的 AppPalette.TextPrimary 逐字相同",
            AppPalette.TextPrimary.toArgb(),
            AppColorLight.text1.toArgb(),
        )
        assertEquals(
            "浅套 text2 必须与迁移前的 AppPalette.TextSecondary 逐字相同",
            AppPalette.TextSecondary.toArgb(),
            AppColorLight.text2.toArgb(),
        )
        assertEquals(
            "浅套 AiSpeech.deep 必须与迁移前的 DomainColors.AiSpeech.deep 逐字相同",
            DomainColors.AiSpeech.deep.toArgb(),
            DomainLight.getValue(AppDomain.AiSpeech).deep.toArgb(),
        )
        assertEquals(
            "浅套 surface 必须与迁移前的 AppPalette.Surface 逐字相同（CapabilityToggle 未选中档）",
            AppPalette.Surface.toArgb(),
            AppColorLight.surface.toArgb(),
        )
    }

    /** Scrim 压页底后的实底：浮层面板脚下那一层，浅深两侧都要先合成它再压玻璃。 */
    private fun scrimOver(page: Color): Color = Color(compositeSrcOver(AppPalette.Scrim.toArgb(), page.toArgb()))

    // ---------- 取样带 ----------

    private fun boundsOfTag(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** 节点**左侧**那段只有面板内边距的带（面板内容左边缘到容器边缘之间，[insetDp] 起算宽度 [widthDp]）。 */
    private fun bandLeftOfTag(tag: String, insetDp: Float, widthDp: Float): Rect {
        val b = boundsOfTag(tag)
        val density = density()
        val left = (b.left - insetDp * density - widthDp * density).coerceAtLeast(0f)
        return verticalBand(b, left, left + widthDp * density)
    }

    /** 节点**内左侧**那段带（行/框自己铺底、还没有字的那一段，如 toggle 行左内边距、浮层行左内边距）。 */
    private fun bandInsideTag(tag: String, insetDp: Float, widthDp: Float): Rect {
        val b = boundsOfTag(tag)
        val density = density()
        val left = (b.left + insetDp * density).coerceAtMost(b.right - 1f)
        return verticalBand(b, left, (left + widthDp * density).coerceAtMost(b.right))
    }

    /** 掐掉上下各 25%，避开相邻行与描边。 */
    private fun verticalBand(b: Rect, left: Float, right: Float): Rect =
        Rect(left, b.top + b.height * 0.25f, right, b.bottom - b.height * 0.25f)

    private fun density(): Float =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density

    // ---------- 像素取样 ----------

    /** 带内不透明像素众数 = 该处的实际填充（玻璃 / 输入框底 / toggle 底）。 */
    private fun modeOf(rect: Rect): Int = modeOfBitmap(cropRootBitmap(rect), OPAQUE_ALPHA_FLOOR, exclude = null)

    /** 节点自身像素的众数 = 该节点**自己铺的底色**（如输入框容器）；父级带点击语义时会合并子节点，故用未合并树。 */
    private fun modeOfTag(tag: String): Int = nodeBitmap(tag).modeOfBitmap(exclude = null)

    /**
     * 该文本节点的字身核心墨：先排除容器 [backdrop]（底色像素永远多于字身），
     * 再取剩下那一族的众数（门槛 [INK_MIN_DELTA_FROM_BACKDROP] 把抗锯齿过渡像素也一并滤掉）。
     */
    /**
     * 该文本节点的字身核心墨 = 「离容器最远且成族」的那一档像素（不是众数）：
     * 一行短标签里抗锯齿过渡像素每档只出现几十次却**族数极多**，字身核心也可能只占少数，
     * 取众数会取到中间调（实测深套浮层 `alloy` 取到 `#676D6A`）；而中间调永远落在「容器 ↔ 字身」之间，
     * 取离容器最远的那一族必然落在字身核心。判据不变：那一族仍必须逐值等于该套那枚主题墨（±[INK_CHANNEL_TOLERANCE]）。
     */
    private fun inkOfText(text: String, backdrop: Int): Int =
        extremeInk(
            composeRule.onNodeWithText(text, useUnmergedTree = true).captureToImage().asAndroidBitmap(),
            backdrop,
        )

    /** 带 tag 的**文本节点**自身像素的众数（中文整句字身够密时够用；短拉丁标签请用 [inkOfText]）。 */
    private fun inkOfTag(tag: String, backdrop: Int): Int =
        nodeBitmap(tag).modeOfBitmap(exclude = backdrop)

    private fun extremeInk(bitmap: Bitmap, backdrop: Int): Int {
        val counts = HashMap<Int, Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val p = bitmap.getPixel(x, y)
                if (((p ushr 24) and 0xFF) < OPAQUE_ALPHA_FLOOR) continue
                if (rgbDelta(p, backdrop) < INK_MIN_DELTA_FROM_BACKDROP) continue
                counts[p] = (counts[p] ?: 0) + 1
            }
        }
        val lBg = luminance(backdrop)
        val winner = counts.filter { it.value >= INK_MIN_FAMILY_COUNT }
            .maxByOrNull { abs(luminance(it.key) - lBg) }
        return checkNotNull(winner) {
            "文本节点里没有「离容器 ≥$INK_MIN_DELTA_FROM_BACKDROP 档且成族（≥$INK_MIN_FAMILY_COUNT 像素）」的一档" +
                "（尺寸 ${bitmap.width}x${bitmap.height}，容器 ${backdrop.hex()}）：这行根本没画字，本用例没有对象"
        }.key
    }

    private fun nodeBitmap(tag: String): Bitmap =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()

    private fun Bitmap.modeOfBitmap(exclude: Int?): Int = modeOfBitmap(this, OPAQUE_ALPHA_FLOOR, exclude)

    /** 整列 `verticalScroll` 的屏里，视口外节点 capture 出来的区域宽高为 0（`Bitmap.createBitmap` 直接抛）：先滚进视口。 */
    private fun scrollToTag(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.waitForIdle()
    }

    /** 药片这一族：先定容器众数，再在「离容器 ≥ [INK_MIN_DELTA_FROM_BACKDROP] 档」的那一族里取众数 = 墨核。 */
    private fun inkModeOf(rect: Rect, backdrop: Int): Int =
        modeOfBitmap(cropRootBitmap(rect), OPAQUE_ALPHA_FLOOR, exclude = backdrop)

    private fun modeOfBitmap(bitmap: Bitmap, alphaFloor: Int, exclude: Int?): Int {
        val counts = HashMap<Int, Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val p = bitmap.getPixel(x, y)
                if (((p ushr 24) and 0xFF) < alphaFloor) continue
                if (exclude != null && rgbDelta(p, exclude) < INK_MIN_DELTA_FROM_BACKDROP) continue
                counts[p] = (counts[p] ?: 0) + 1
            }
        }
        val winner = counts.maxByOrNull { it.value }
        return checkNotNull(winner) {
            "取样区里没有可算像素（尺寸 ${bitmap.width}x${bitmap.height}，alphaFloor=$alphaFloor，" +
                "排除 ${exclude?.hex() ?: "无"}）：取样带落空或根本没画字，本用例没有对象"
        }.key
    }

    /** 整屏截图按 bounds 裁剪（captureToImage 给的是已合成好的屏幕像素，玻璃压页底的结果就在里面）。 */
    private fun cropRootBitmap(rect: Rect): Bitmap {
        val screen = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val left = rect.left.toInt().coerceIn(0, screen.width - 1)
        val top = rect.top.toInt().coerceIn(0, screen.height - 1)
        val width = max(1, min(rect.width.toInt(), screen.width - left))
        val height = max(1, min(rect.height.toInt(), screen.height - top))
        return Bitmap.createBitmap(screen, left, top, width, height)
    }

    // ---------- 判据 ----------

    /** 容器腿：实测众数必须落在「该档填充压该实底」的合成结果 ±[GLASS_CHANNEL_TOLERANCE] 内。 */
    private fun assertGlassIs(label: String, actual: Int, fill: Color, backdrop: Color) {
        val expected = compositeSrcOver(fill.toArgb(), backdrop.toArgb())
        val delta = rgbDelta(actual, expected)
        assertTrue(
            "$label：期望 ${expected.hex()}（${fill.toArgb().hex()} 压 ${backdrop.toArgb().hex()} 的合成结果），" +
                "实测 ${actual.hex()}，最大通道差 $delta > $GLASS_CHANNEL_TOLERANCE —— 容器没随套换档，或压根没画玻璃",
            delta <= GLASS_CHANNEL_TOLERANCE,
        )
        val separation = rgbDelta(
            compositeSrcOver(AppColorLight.surfaceGlass.toArgb(), AppColorLight.background.toArgb()),
            compositeSrcOver(AppColorDark.surfaceGlass.toArgb(), AppColorDark.background.toArgb()),
        )
        assertTrue(
            "自检（换套判别力）：容器容差必须小于浅/深两套玻璃压各自页底的间距（实测 $separation 档），" +
                "否则「另一套」也算命中，本用例失去牙齿",
            GLASS_CHANNEL_TOLERANCE < separation,
        )
    }

    private fun assertInkIs(label: String, actual: Int, expected: Color) {
        val delta = rgbDelta(actual, expected.toArgb())
        assertTrue(
            "$label：期望 ${expected.toArgb().hex()}，实测 ${actual.hex()}，最大通道差 $delta > $INK_CHANNEL_TOLERANCE",
            delta <= INK_CHANNEL_TOLERANCE,
        )
    }

    private fun assertInkIsFarFromTheOtherSet(label: String, actual: Int, otherSetInk: Color) {
        val delta = rgbDelta(actual, otherSetInk.toArgb())
        assertTrue(
            "$label：不得取另一套的墨（实测 ${actual.hex()} 离 ${otherSetInk.toArgb().hex()} 只有 $delta 档，" +
                "门槛 $WRONG_SET_INK_MIN_CHANNEL_DELTA）——写死常量或漏一处主题读点就会命中这条",
            delta >= WRONG_SET_INK_MIN_CHANNEL_DELTA,
        )
    }

    private fun assertContrastAtLeast(label: String, ink: Int, backdrop: Int, threshold: Double) {
        val ratio = contrastRatio(ink, backdrop)
        log("$label：墨 ${ink.hex()} 压底 ${backdrop.hex()} = ${ratio.r2()}（阈值 ${threshold}）")
        assertTrue(
            "$label：WCAG 实测 ${ratio.r2()}，未达正文阈值 ${threshold}（本席不许放宽阈值；达不到就改容器档或换合格墨）",
            ratio >= threshold,
        )
    }

    /** 现算取证进 logcat（`am instrument -w -e` 输出与 logcat 里都可见），报告要抄这几个数。 */
    private fun log(message: String) {
        Log.i(TAG, message)
        println("[glass-panel-ink] $message")
    }

    // ---------- 数学 ----------

    /** src-over 逐通道合成（sRGB 空间，与绘制一致）：`a = fgA/255`，结果 = `fg*a + bg*(1-a)`。 */
    private fun compositeSrcOver(foreground: Int, background: Int): Int {
        val fgA = ((foreground ushr 24) and 0xFF) / 255.0
        val bgA = ((background ushr 24) and 0xFF) / 255.0
        fun blend(shift: Int): Int {
            val f = (foreground shr shift) and 0xFF
            val b = (background shr shift) and 0xFF
            return (f * fgA + b * bgA * (1 - fgA)).roundToInt().coerceIn(0, 255)
        }
        val alpha = ((fgA + bgA * (1 - fgA)) * 255).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (blend(16) shl 16) or (blend(8) shl 8) or blend(0)
    }

    private fun luminance(argb: Int): Double {
        fun lin(channel: Int): Double {
            val s = (channel and 0xFF) / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(argb shr 16) + 0.7152 * lin(argb shr 8) + 0.0722 * lin(argb)
    }

    private fun contrastRatio(a: Int, b: Int): Double {
        val hi = max(luminance(a), luminance(b))
        val lo = min(luminance(a), luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    /** 只比 RGB 三通道（alpha 已被合成腿吃掉）。 */
    private fun rgbDelta(a: Int, b: Int): Int = max(
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        max(abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)), abs((a and 0xFF) - (b and 0xFF))),
    )

    private fun Int.hex(): String = String.format(Locale.US, "#%08X", this)

    private fun Double.r2(): String = String.format(Locale.US, "%.2f:1", this)

    private companion object {
        const val TAG = "GlassPanelThemeInk"
    }
}

/** 密钥卡里那枚按套取墨的 `Text`（`ai_profile_key_state`：本席从静态 `DomainColors.AiSpeech.deep` 迁到 `appDomain(...)`）。 */
private const val SECTION_CARD_INK_TAG = "ai_profile_key_state"

/** 取样带：面板内容左侧那段只有卡内 16dp 内边距、没有字的空白（全部由 bounds 现算）。 */
private const val CARD_BAND_INSET_DP = 12f
private const val CARD_BAND_WIDTH_DP = 5f

/** 浮层行左内边距 20dp，取行内 12~18dp 那一段（只有面板玻璃，没有字）。 */
private const val OVERLAY_BAND_INSET_DP = 12f
private const val OVERLAY_BAND_WIDTH_DP = 6f

/** 浮层里「自动」是选中档（draft.voice = "nova" 时不是），alloy 是未选中档：一档域色墨、一档套内正文墨。 */
private const val OVERLAY_SELECTED_TAG = "ai_profile_voice_nova"
private const val OVERLAY_UNSELECTED_TAG = "ai_profile_voice_alloy"

/** 墨要从**字节点**取，不是从整行取：整行 bounds 会含面板描边与投影，字节点不会。 */
private const val OVERLAY_SELECTED_TEXT = "nova（当前）"
private const val OVERLAY_UNSELECTED_TEXT = "alloy"

private const val PREVIEW_INPUT_TAG = "speech_preview_input"
private const val PREVIEW_FOOTNOTE_TEXT = "用当前选中的朗读服务播放这段文字。"
private const val PILL_TAG = "ai_profile_key_clear"

/** 「算不透明」下限：与席 A 同口径，必须低于两档玻璃的 alpha（浅 `0xEF` = 239 / 深 `0xE6` = 230）。 */
private const val OPAQUE_ALPHA_FLOOR = 200

/** 容器合成结果的逐通道容差：投影渗色与 dp→px 取整实测 ≤3 档，留 5 档，仍远小于两套玻璃压页底的间距。 */
private const val GLASS_CHANNEL_TOLERANCE = 5

/** 墨核容差：字身核心应逐值等于那档墨，留 12 档只抗不同字号/字重下的覆盖不足。 */
private const val INK_CHANNEL_TOLERANCE = 12

/** 墨核取样门槛：离容器至少差这么多才算字身（抗锯齿过渡像素落在两者之间，被排除）。 */
private const val INK_MIN_DELTA_FROM_BACKDROP = 64

/** 墨核成族下限：单发杂点不算字身（一行短标签的字身核心也有几百像素）。 */
private const val INK_MIN_FAMILY_COUNT = 25

/** 反向判据门槛：墨离另一套那档墨、深色档里的输入框离写死的白，都必须远到这一点。 */
private const val WRONG_SET_INK_MIN_CHANNEL_DELTA = 32

/** C-1 纸面与像素两条比值的同档容差（像素受抗锯齿与投影渗色影响）。 */
private const val PAPER_PIXEL_RATIO_TOLERANCE = 0.6

/** 正文 AA 阈值（与席 A / JVM 侧同档，本席不许放宽）。 */
private const val BODY_AA_RATIO = 4.5
