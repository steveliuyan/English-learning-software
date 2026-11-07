package com.example.englishlearning.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.MintGlassTheme
import com.example.englishlearning.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「AI 助学」页的 connected 契约（2026-11-02 按参考 `sAiHome` 重做）。
 *
 * 上一版这里钉的是渐变头卡、白胶囊状态徽章、词表裸行与说明折叠行——那些物件随用户裁定整块离场，
 * 判据一并改成参考页的四件：普通页头 + 双列功能卡 + 「还在打磨」分组 + 页末隐私提示。
 * 源码侧的逐字形状由 JVM 侧 `AiLearningLayoutContractTest` 承重；本文件只管「真的画出来了、
 * 点得动 / 点不动、TalkBack 读得到」这些渲染后才成立的事。
 */
@RunWith(AndroidJUnit4::class)
class AiLearningScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val implemented = AiFeature.entries.filter { it.implemented }
    private val unreleased = AiFeature.entries.filter { !it.implemented }

    private fun setScreen(
        onOpenFeature: (AiFeature) -> Unit = {},
        onOpenAiProfiles: () -> Unit = {},
    ) {
        composeRule.setContent {
            AiLearningScreen(onOpenFeature = onOpenFeature, onOpenAiProfiles = onOpenAiProfiles)
        }
    }

    @Test
    fun renders_the_plain_header_and_every_feature_card() {
        setScreen()
        composeRule.onNodeWithTag("ai_learning_screen").assertExists()
        composeRule.onNodeWithTag("app_screen_header").assertExists()
        composeRule.onNodeWithText("AI 助学").assertExists()
        composeRule.onNodeWithText("把词用起来").assertExists()
        composeRule.onNodeWithText("每个功能都先做本机能做的部分，再把必要的文字交给已配置的服务。").assertExists()
        AiFeature.entries.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}").performScrollTo().assertExists()
        }
    }

    /**
     * 参考页的双列格：同一行的两张卡必须并排（第二张贴着第一张右侧），不是上下堆叠。
     * 这条只有渲染后才成立，源码侧 `chunked(2)` 管不到「实际排出来是不是两列」。
     */
    @Test
    fun the_feature_cards_are_laid_out_in_two_columns() {
        setScreen()
        val first = composeRule.onNodeWithTag("ai_feature_sentence-analysis").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithTag("ai_feature_photo-translation").getUnclippedBoundsInRoot()
        assertEquals("同一行的两张卡必须在同一水平线上", first.top, second.top)
        assertTrue(
            "第二张卡必须贴着第一张右侧（左右并排），实际 ${first.right} / ${second.left}",
            second.left > first.right,
        )
    }

    @Test
    fun the_header_action_opens_the_ai_service_page() {
        var opened = 0
        setScreen(onOpenAiProfiles = { opened++ })
        composeRule.onNodeWithTag("ai_learning_open_profiles").performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
        composeRule.onNodeWithContentDescription("AI 服务配置").assertExists()
    }

    /**
     * 已接通的三项可点、回调真的带出对应的功能；未上线的四项也在树上，但**没有** click action
     * ——「画出来、不可点」是参考里「还在打磨」那组的真实形状，不是折叠隐藏。
     */
    @Test
    fun only_the_implemented_cards_are_clickable_and_they_report_their_own_feature() {
        val received = mutableListOf<AiFeature>()
        setScreen(onOpenFeature = { received += it })
        implemented.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}")
                .performScrollTo()
                .assertHasClickAction()
                .performClick()
        }
        composeRule.waitForIdle()
        assertEquals(implemented, received)

        unreleased.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}")
                .performScrollTo()
                .assertExists()
                .assertHasNoClickAction()
        }
    }

    @Test
    fun the_unreleased_group_is_titled_and_carries_the_shared_status_badge() {
        setScreen()
        composeRule.onNodeWithTag("ai_learning_unreleased_section").performScrollTo().assertExists()
        composeRule.onNodeWithText("还在打磨").assertExists()
        unreleased.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_status_badge_${feature.key}", useUnmergedTree = true)
                .performScrollTo()
                .assertTextEquals(featureStatusBadge(false))
        }
        // 已接通的卡不盖章：「现在能用」由「点得动」直接说明，参考页也没有给可用卡加徽章。
        implemented.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_status_badge_${feature.key}").assertDoesNotExist()
        }
    }

    /** TalkBack 读一张卡要同时读到能力名与状态，不能只念标题。 */
    @Test
    fun every_card_is_announced_with_its_own_status() {
        setScreen()
        AiFeature.entries.forEach { feature ->
            composeRule.onNodeWithTag("ai_feature_${feature.key}", useUnmergedTree = true)
                .performScrollTo()
                .assertContentDescriptionEquals("${feature.title}，状态：${featureStatusBadge(feature.implemented)}")
        }
    }

    /** 参考页末那条隐私提示仍在；上一版的两枚入口（查看词表 / 使用说明）确实已经离场。 */
    @Test
    fun the_privacy_notice_stays_and_the_old_entries_are_gone() {
        setScreen()
        composeRule.onNodeWithTag("ai_learning_privacy_notice").performScrollTo().assertExists()
        composeRule.onNodeWithText("拍照翻译的图片只在本机识别，不上传；AI 生图会先把中文描述转成英文提示词。")
            .assertExists()
        composeRule.onNodeWithTag("ai_learning_nav_row").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_learning_explainer_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_learning_header").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_learning_status_badge").assertDoesNotExist()
    }

    /** 2026-10-31 用户裁定删掉的表情不许按旧设计图加回来。 */
    @Test
    fun header_no_longer_carries_the_ai_mascot() {
        setScreen()
        composeRule.onNodeWithTag("ai_mascot", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * 去渐变之后仍要随主题翻色：深色档画出来的是深套背景（`AppColorDark.background`）。
     * 走显式 `MintGlassTheme(themeMode = ThemeMode.Dark)` 包装（先例 `ThemeModeSwitchTest`），
     * 本类默认 `setContent` 不带主题提供者，`cmd uimode night` 翻不动它。
     */
    @Test
    fun dark_tier_paints_the_screen_with_the_dark_background() {
        composeRule.setContent {
            MintGlassTheme(themeMode = ThemeMode.Dark) {
                AiLearningScreen(onOpenFeature = {}, onOpenAiProfiles = {})
            }
        }
        composeRule.waitForIdle()
        val screen = composeRule.onNodeWithTag("ai_learning_screen").captureToImage().asAndroidBitmap()
        assertEquals(
            "深色档必须真的渲染（页底角落 = 深套背景），否则「去渐变」会被误当成「去主题化」",
            AppColorDark.background.toArgb(),
            screen.getPixel(0, 0),
        )
    }

    /**
     * QA3 A2.6①（用户 2026-11-08 拍板「全部都做」）：字体大小顶档 3.0 下，页头副标题不得吃掉首屏。
     *
     * 实测（走查册 §A2.6，图证 `fs300-ai-01-hub.png`）：那一句说明在 3.0 排成 4 行、645px，
     * 首屏几乎只剩标题区——本页**可滚**（根 `ScrollView [0,63][1080,1439]`），所以不是 A2.5 那种流程断裂，
     * 但页头按 1.0 档定尺、随字号线性长高，把唯一主操作推到了两三屏之外。
     *
     * 判据取「行数」这个可解释量，不拿像素门限说话：共享页头的标题腿已经钉死 `maxLines = 2`
     * （`AppScreenHeaderTest` 两条大字号用例），副标题腿原先完全不设上限，是页头唯一会长成整段散文的一腿。
     *
     * **上限为什么是 3 而不是 2**：这句说明在 2.0 恰好排 3 行，而 2.0 是 UI 重设计 spec 钉住的
     * 组件大字号验收档（AC-R-07）——上限压到 2 就是为了让顶档好看而让钉档开始丢字，方向反了。
     * 钉档不丢字由下面那条 `headerSubtitleKeepsEveryWordAtThePinnedFontTier` 当场守着，
     * 本条只管顶档别再无限长高。代价如实登记：3.0 下第 4 行收进省略号，整句仍由语义文本供出
     * （TalkBack 读得到；`Clip` 与 `Ellipsis` 都不改语义）。
     */
    @Test
    fun headerSubtitleStaysWithinThreeLinesAtTopFontScale() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 3f)) {
                AiLearningScreen(onOpenFeature = {}, onOpenAiProfiles = {})
            }
        }

        val layout = subtitleLayoutAtHeader()
        assertTrue(
            "fontScale=3.0 下页头副标题排成 ${layout.lineCount} 行（首屏被说明文字吃掉，A2.6①）：" +
                "行数上限 3，末行止于 ${layout.getLineEnd(layout.lineCount - 1)}/${AI_HUB_SUBTITLE.length}",
            layout.lineCount <= 3,
        )
    }

    /**
     * QA3 A2.6① 的另一半：把上限引进共享页头之后，**钉档 2.0 一个字都不许丢**。
     *
     * 判据不是行数而是「最后一行是否排到了句尾」：`getLineEnd(lineCount - 1) == text.length`
     * 只有整句都进了版面才成立，一旦 `maxLines` 触发省略号截断，末行下标就停在中间。
     * 这条是本轮改动的反向护栏——它不许下一棒为了「顶档更好看」把上限再往下压。
     */
    @Test
    fun headerSubtitleKeepsEveryWordAtThePinnedFontTier() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2f)) {
                AiLearningScreen(onOpenFeature = {}, onOpenAiProfiles = {})
            }
        }

        val layout = subtitleLayoutAtHeader()
        val lastLineEnd = layout.getLineEnd(layout.lineCount - 1)
        assertEquals(
            "fontScale=2.0（AC-R-07 钉档）副标题必须整句排完，丢字即上限压过头了：实际 ${layout.lineCount} 行，" +
                "末行止于 $lastLineEnd/${AI_HUB_SUBTITLE.length}",
            AI_HUB_SUBTITLE.length,
            lastLineEnd,
        )
    }

    /** 取页头那句说明的 `TextLayoutResult`（语义动作，不读绘制像素；两档共用同一条取法）。 */
    private fun subtitleLayoutAtHeader(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(AI_HUB_SUBTITLE)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    /**
     * QA3 A2.6②③（同一条根因：卡片与分段头按 1.0 档定尺）：顶档下功能卡必须改成单列。
     *
     * 双列在 3.0 的实测形状：每格只剩约 180dp，「长难句分析」折成两行、未接通卡的状态徽标被推出卡片可视区
     * （要走查册里那张 `fs300-ai-03-hub-scrolled.png` 才看得到），「还在打磨」分段标题被视口下沿切半行。
     * 顶档唯一不破版的形状是单列——判据按**排出来的实际位置**量，源码侧 `chunked(2)` 数不出来
     * （同一条理由见上面 `the_feature_cards_are_laid_out_in_two_columns` 的注释）。
     */
    @Test
    fun featureCardsFallBackToOneColumnAtTopFontScale() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 3f)) {
                AiLearningScreen(onOpenFeature = {}, onOpenAiProfiles = {})
            }
        }

        val first = composeRule.onNodeWithTag("ai_feature_${implemented[0].key}").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithTag("ai_feature_${implemented[1].key}").getUnclippedBoundsInRoot()
        assertTrue(
            "fontScale=3.0 下前两张功能卡必须上下堆叠（单列）：第二张 top=$second.top 未落到第一张 bottom=${first.bottom} 之下",
            second.top > first.bottom,
        )
    }

    private companion object {
        /** 页头那句说明的原文，逐字取自 [AiLearningScreen]；文案改动时本常量必须跟着改，不许靠模糊匹配蒙过。 */
        const val AI_HUB_SUBTITLE = "每个功能都先做本机能做的部分，再把必要的文字交给已配置的服务。"
    }
}
