package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.CardFeedback
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.SkeletonBar
import com.example.englishlearning.ui.components.SkeletonCard
import com.example.englishlearning.ui.components.SkeletonStatGrid
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors
import com.example.englishlearning.ui.theme.appDomain

/**
 * 生词本「重新学习」屏（spec §5 逐屏表「重新学习」行）。
 *
 * 骨架一条线走到底：共享页头（返回位 + 标题）→ 一块可滚正文（`weight(1f)` + 唯一 `verticalScroll`）
 * → 吸底三档评分。评分条只在 `Ready` 出现，且是滚动区的**兄弟**节点——正文再长也不跟着滚，
 * 拇指也不用去找「刚才那三枚按钮去哪了」。
 *
 * 与首版的差别（首版 56 行）：页头左边一枚「返回生词本」实底按钮 + 标题并排（两个东西抢层级）、
 * 词面三行纯文本无容器、三档评分排在正文末尾（要滚到底才够得着）、`Button` 与 `Text` 混用 M3 默认
 * 排印。这一版把「返回」交给页头族、「词面」收进弱卡、「评分」固定底部。
 */
@Composable
fun VocabularyRelearnScreen(
    state: VocabularyRelearnUiState,
    onSubmit: (CardFeedback) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("relearn_screen"),
    ) {
        AppScreenHeader(
            title = "重新学习",
            onBack = onBack,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RelearnBody(state = state, onRetry = onRetry, onBack = onBack)
            // m03560（用户走查，词卡屏同一处抱怨）：三档评分不再吸底——它落在卡面正下方，
            // 剩下的空白留在它下面，握机时拇指不用伸到屏幕最底。槽色与发丝线也随之撤掉
            // （不再有内容从它下面穿过，也就不存在「被截断」的缝），理由与词卡屏 `WordCardFeedbackBar` 同一份。
            if (state is VocabularyRelearnUiState.Ready) {
                RelearnFeedbackBar(submitting = state.submitting, onSubmit = onSubmit)
            }
        }
    }
}

/**
 * 五种宿主状态共用的正文块：整屏只有这一个滚动轴，各态只换内容不换骨架。
 *
 * 次要动作（重试 / 完成页返回）一律 `TextButton`：实底按钮在这屏**只属于三档评分**，
 * 那样「底部那一条就是我要做的决定」才立得住。
 */
@Composable
private fun RelearnBody(
    state: VocabularyRelearnUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = appColors()
    when (state) {
        VocabularyRelearnUiState.Idle, VocabularyRelearnUiState.Loading -> Column(
            // 批 C-3（Spec 05 F5-05 / 决策 2026-11-07 决定 3 第一类「等数据」）：旧首帧是
            // 48dp 转圈 + 一句「正在准备生词…」。这一屏回填后落的是「计数行 + 一张生词卡 + 吸底三档」，
            // 所以三档形状各给一块，且顺序与真实版面逐字相同：
            //  ① 计数行 `第 N / M 个`（Footnote 一行）——位置给、数字不给：写「第 0 / 0 个」就是 F1-01
            //     那枚假数字换了个承载，所以这里是一条 0.24f 的短横；
            //  ② 生词卡：`SoftCard(contentPadding = 20.dp)` 里词头（Display）+ 音标 + 释义 → `bars = 3`，
            //     下限 170dp 取那一档真实高度；
            //  ③ 吸底三档评分条（Ready 才挂，但它是这一帧之后必然长出来的那一块）：三枚 `weight(1f)`
            //     等宽按钮、单行、`spacedBy(8.dp)` → `SkeletonStatGrid(cells = 3)`，48dp 触控下限同档。
            // 外层那一条 `verticalScroll` 与 `padding(horizontal = 20.dp)` 由宿主那一支给，本分支不再自铺，
            // 否则骨架比真卡多缩一进（旧转圈那版的 `padding(vertical = 40.dp)` 同理随转圈退役）。
            modifier = Modifier
                .fillMaxWidth()
                .testTag("relearn_loading"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkeletonBar(fraction = 0.24f, height = 10.dp)
            SkeletonCard(bars = 3, minHeight = 170.dp)
            SkeletonStatGrid(cells = 3, rowHeight = 48.dp)
        }

        VocabularyRelearnUiState.Unavailable -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "生词本暂时无法加载",
                style = AppType.Title,
                color = colors.text1,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "生词还在，只是这次没能读出来；再试一次通常就好。",
                style = AppType.Footnote,
                color = colors.text2,
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("relearn_retry"),
            ) {
                Text("重试", style = AppType.Label, color = colors.text1)
            }
        }

        is VocabularyRelearnUiState.Ready -> {
            Text(
                text = "第 ${state.position} / ${state.total} 个",
                style = AppType.Footnote,
                color = colors.text2,
                modifier = Modifier
                    .testTag("relearn_progress")
                    .semantics { contentDescription = "第 ${state.position} 个，共 ${state.total} 个" },
            )
            SoftCard(contentPadding = PaddingValues(20.dp)) {
                Text(
                    text = state.card.lemma,
                    style = AppType.Display,
                    color = colors.text1,
                    modifier = Modifier
                        .testTag("relearn_lemma")
                        .semantics { contentDescription = state.card.lemma },
                )
                if (state.card.ipa.isNotBlank()) {
                    Text(
                        text = state.card.ipa,
                        style = AppType.Footnote,
                        color = colors.text2,
                    )
                }
                if (state.card.meaningZh.isNotBlank()) {
                    Text(
                        text = state.card.meaningZh,
                        style = AppType.Body,
                        color = colors.text1,
                    )
                }
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    style = AppType.Footnote,
                    color = colors.text2,
                )
            }
            if (state.submitting) {
                Text(
                    text = "正在保存复习结果…",
                    style = AppType.Footnote,
                    color = colors.text2,
                )
            }
        }

        is VocabularyRelearnUiState.Done -> {
            Text(
                text = "重新学习完成",
                style = AppType.Headline,
                color = colors.text1,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "本次完成 ${state.total} 个生词",
                style = AppType.Body,
                color = colors.text1,
            )
            Text(
                text = "认识的词已移出生词本，其他词会保留供下次复习。",
                style = AppType.Footnote,
                color = colors.text2,
            )
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("relearn_done_back"),
            ) {
                Text("返回生词本", style = AppType.Label, color = colors.text1)
            }
        }
    }
}

/**
 * 三档评分条：三枚按钮等宽。
 *
 * m03560（用户走查「太下了，放在单词下面居中」）之后它不再是吸底槽——跟着卡面住在内容列的文档流里，
 * 槽色与槽顶发丝线一起撤掉（没有内容从它下面穿过，那 1dp 失去对象），与词卡屏同一口径。
 *
 * 三档的墨色分工与词卡屏同口径：认识 = 学习域实底（正向、最常用）、模糊 = 中性 tint（中间档）、
 * 不认识 = 白面描字（负向但不吓人）。字级统一 `AppType.Label`，单行不换行——三枚等宽的按钮里
 * 换行会把「不认识」拆成两行，反而认不出是哪个档。
 */
@Composable
private fun RelearnFeedbackBar(
    submitting: Boolean,
    onSubmit: (CardFeedback) -> Unit,
) {
    val colors = appColors()
    val learn = appDomain(AppDomain.Learn)
    Row(
        // m03560：三档评分跟着卡面走（文档流内），所以不自铺 `surface`、不补槽顶发丝线；
        // 横向留白由外层内容列给（20dp），这里只留一点上下气口。
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("relearn_feedback_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onSubmit(CardFeedback.Unknown) },
            enabled = !submitting,
            shape = AppShape.Button,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.tint,
                contentColor = colors.onMint,
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("relearn_feedback_unknown"),
        ) {
            Text("不认识", style = AppType.Label, color = colors.onMint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = { onSubmit(CardFeedback.Fuzzy) },
            enabled = !submitting,
            shape = AppShape.Button,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.tint2,
                contentColor = colors.text1,
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("relearn_feedback_fuzzy"),
        ) {
            Text("模糊", style = AppType.Label, color = colors.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = { onSubmit(CardFeedback.Known) },
            enabled = !submitting,
            shape = AppShape.Button,
            colors = ButtonDefaults.buttonColors(
                containerColor = learn.base,
                contentColor = colors.onBrand,
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("relearn_feedback_known"),
        ) {
            Text("认识", style = AppType.Label, color = colors.onBrand, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
