package com.example.englishlearning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 网络 / AI 等待面的「进行中语义」（批 C-4；口径见
 * `docs/decisions/2026-11-07-loading-skeleton-shimmer-prefetch.md` 决定 3 的 C-4 段与 §4，
 * `docs/specs/05-release-quality-and-compliance.md` F5-05 第 4 条）。
 *
 * ## 为什么是组件而不是五段屏侧代码
 *
 * 开工实测（决策册 §2.6）：五处 AI 等待臂里四处只有一行裸 `Text`（`正在询问…` / `正在分析…` /
 * `正在生成画面描述…` / `正在生成图片…` / `正在生成…`），拍照翻译的翻译阶段连这一行都没有。
 * 一行裸文案同时缺三件东西：**在向谁请求**、**请求还在飞**、**能不能停下**。五屏各写一份的话，
 * 补齐就要改五处，而历史上这五处的第一版就是各自抄来的——「取消后不会发送」那种假话正是这么长出来的。
 * 所以本组件是这四件东西的**唯一落点**：文案位、活动指示、取消键、取消语义那一句陈述。
 *
 * ## 四条约束（都有判据，`AiWaitInProgressContractTest`）
 *
 *  - `onCancel` **没有默认值**。给一个 `{}` 就等于允许宿主漏接线而界面照旧画出一枚能按的取消键——
 *    那是本批要消灭的假象，组件不能替它提供温床。接线是否真的落到持有在飞请求的 `Job` 上，
 *    由 VM 用例（挂起假 transport + 迟到响应不回写）量，不由这里的形状保证。
 *  - **只画一枚不确定进度指示**。不画骨架：骨架承诺「马上出现的就是这个形状」，而 AI 生成失败率高、
 *    时长不可知，画骨架就是画假内容（决定 3 三分类里的第三类）。不画百分比、不画秒数：
 *    `timeout_seconds` 的唯一权威在 `ai/AiRequestPolicy`，映射到 `AiFailure.Timeout →「等待超时，稍后再试。」`，
 *    这里再写一个数就是第二个权威，且必然是假承诺。
 *  - **不宣告结果**：文案只说「正在向…做什么」，不许出现「已生成 N 篇 / 已有 N 条」这类占位数字
 *    （`docs/decisions/2026-11-06-placeholder-text-is-not-learning-content.md` 同源）。
 *  - **取消那句陈述只有一份，且是真话**：「取消只是不再等待这次结果」——AC2-11 的「取消即零出站」管的是
 *    **未确认时不发送**，对已经出站的请求说「不会发送」是假话。域名级承诺归 `OutboundConfirmDialog` 逐次承担
 *    （AC2-14），本组件连 host 都不读：等待面拿不到权威域名，写一句可能过期的域名比不写更糟。
 *
 * ## 无障碍
 *
 * 进度环是装饰（无 `contentDescription`，读屏念不出也念不错）；被念出来的两句都是文本节点，
 * 顺序即版面顺序：先「在做什么」，再取消键，最后那句取消语义。骨架那一族才需要「零播报」，
 * 这一族本来就是播报内容的。
 */
@Composable
fun RequestInProgress(
    message: String,
    onCancel: () -> Unit,
    statusTestTag: String,
    cancelTestTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    SoftCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.text2,
                strokeWidth = 2.dp,
            )
            Text(
                text = message,
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier.testTag(statusTestTag),
            )
        }
        PillButton(
            text = "取消",
            onClick = onCancel,
            style = PillStyle.Secondary,
            domain = AppDomain.AiSpeech,
            testTag = cancelTestTag,
            contentDescription = "取消",
        )
        // 这句是全仓唯一一份取消陈述：屏侧再抄一遍，就回到「五份里必有一份漏字」的来历。
        Text(
            text = "取消只是不再等待这次结果；请求可能已经发出，费用以你配置的服务为准。",
            style = AppType.Caption,
            color = colors.text2,
        )
    }
}
