package com.example.englishlearning.ui.mascot

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.englishlearning.ui.AiLearningScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AI 表情的真机语义测试。
 *
 * 动画本身没法在语义树里断言（逐帧画面不是语义属性），所以这里只锁四件**出错就必红**的事：
 * 尺寸真的够大、两种真实配置状态的描述属实、**绝不声称尚未实现的能力**、以及它是装饰而非按钮。
 *
 * 注意本文件用 **JUnit 4**（`androidTest` 源集）：断言来自 `org.junit.Assert`，
 * 其 `assertTrue` 是**消息在前**，与 JVM 测试用的 `kotlin.test`（实际值在前）相反。
 */
@RunWith(AndroidJUnit4::class)
class AiMascotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun freezeTheClockSoTheInfiniteAnimationCannotStallIdle() {
        // 呼吸是无限动画。autoAdvance 保持默认时，等待空闲可能被永不结束的帧请求拖住，
        // 表现为断言超时而不是失败——比直接失败更难定位。关掉自动推进后由测试自己控制时钟，
        // 断言只读静态语义与尺寸，不依赖任何一帧的具体画面。
        composeRule.mainClock.autoAdvance = false
    }

    private fun showMascot(aiConfigured: Boolean) {
        composeRule.setContent {
            AiMascot(aiConfigured = aiConfigured, modifier = Modifier.size(140.dp))
        }
    }

    private fun mascotDescription(): String =
        composeRule.onNodeWithTag(AI_MASCOT_TEST_TAG)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            .orEmpty()
            .joinToString(separator = " ")

    @Test
    fun mascotIsRendered() {
        showMascot(aiConfigured = true)
        composeRule.onNodeWithTag(AI_MASCOT_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun describesTheUnconfiguredStateTruthfully() {
        showMascot(aiConfigured = false)
        assertTrue(
            "未配置时必须如实说明尚未接通，实际描述为「${mascotDescription()}」",
            mascotDescription().contains("AI 尚未接通"),
        )
    }

    @Test
    fun describesTheConfiguredStateTruthfully() {
        showMascot(aiConfigured = true)
        assertTrue(
            "已配置时必须如实说明已配置，实际描述为「${mascotDescription()}」",
            mascotDescription().contains("AI 已配置"),
        )
    }

    /**
     * 表情描述里绝不允许出现的字样。它们都在暗示一个尚未实现的能力，中英并列是因为
     * `contentDescription` 两种语言都可能被写入。
     */
    private val forbiddenCapabilityWords =
        listOf("思考", "生成中", "工作中", "忙碌", "处理中", "正在", "thinking", "busy")

    @Test
    fun theUnconfiguredMascotNeverClaimsAnUnimplementedCapability() {
        assertNoCapabilityClaim(aiConfigured = false)
    }

    @Test
    fun theConfiguredMascotNeverClaimsAnUnimplementedCapability() {
        assertNoCapabilityClaim(aiConfigured = true)
    }

    /**
     * F2-03 与 F2-06 尚未接通，AI 功能点开还不能用。表情若声称「思考中 / 生成中 / 忙碌」，
     * 就是在暗示一个不存在的能力——这正是本项目反复踩过的「文案说谎」缺陷。
     * 这条断言让任何人日后加回这类文案时立刻失败。
     *
     * 两种状态拆成两个测试方法而**不是**在方法里循环：`createComposeRule()` 的
     * `setContent` 每个测试方法只能调用一次，循环调用会抛
     * `IllegalStateException: has already set content`——那是测试自身的缺陷，
     * 会掩盖真正想抓的文案问题。禁令词表只维护在这里一份。
     */
    private fun assertNoCapabilityClaim(aiConfigured: Boolean) {
        showMascot(aiConfigured)
        val description = mascotDescription()
        for (word in forbiddenCapabilityWords) {
            assertFalse(
                "表情在 aiConfigured=$aiConfigured 下声称了未实现的能力「$word」：$description",
                description.contains(word, ignoreCase = true),
            )
        }
    }

    @Test
    fun isDecorativeAndDoesNotPretendToBeAButton() {
        // 它只是装饰：不能有点击行为。一个点了没反应的「按钮」比没有按钮更糟。
        showMascot(aiConfigured = true)
        composeRule.onNodeWithTag(AI_MASCOT_TEST_TAG).assertHasNoClickAction()
    }

    @Test
    fun theRealHeaderCardGivesTheMascotRoomToBeLarge() {
        // 这条才有意义：它测的是**真实头卡**里渲染出来的尺寸。
        // 只测「我传进去的 modifier 生效」是自证其说，头卡被改小它也不会红。
        composeRule.setContent {
            AiLearningScreen(
                todayWordCount = 5,
                dueWordCount = 3,
                aiConfigured = true,
                onOpenFeature = {},
                onOpenWordList = {},
            )
        }
        composeRule.onNodeWithTag(AI_MASCOT_TEST_TAG).assertHeightIsAtLeast(120.dp)
    }
}
