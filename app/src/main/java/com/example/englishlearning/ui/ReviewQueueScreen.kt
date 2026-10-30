package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.InlineIconBadge
import com.example.englishlearning.ui.components.QuietCard
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.StrongCard
import com.example.englishlearning.ui.components.WordRow
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.hairlineTop
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 复习队列（批 1 Task 17，新建屏；对齐原型 `sReview`，`assets/app.js:472-498`）。
 *
 * 这一屏在本轮之前没有承载点：复习从今日学习那枚「开始复习」CTA 直接进词卡，中间没有「哪些词到期、
 * 现在复习的是什么模式」这一层。用户 2026-10-29 拍板「三屏全部新建」，本屏是第二枚，入口是今日学习
 * 软卡裸行组第二行（`today_plan_open_review_queue`，原型 `app.js:343` 的
 * `rowItem({sticker:'clock', title:'复习队列', …, to:'learn-review'})`）。
 *
 * ## 版面逐段对着原型抄
 *
 *  - `barTop({title:'复习队列'})`（`:475`）→ 共享 [AppScreenHeader]。**这一屏的页头没有动作位**：
 *    原型给 `sLearnQueue` 画了 `.bar-action「调整」`，给本屏的 `barTop` 只画了标题与返回，
 *    所以本屏不搬「调整」（判据把 `action = {` 钉成 0）。
 *  - `.strong-card.on-strong`（`:477-483`）→ 共享 [StrongCard]，卡内两行：`.strong-label「到期复习」`
 *    （`app.css:403` = eyebrow 档 + 600 + `--on-strong-2`）与 `.strong-title「N 个词在等你」`
 *    （`:410` = serif 27px，取 [AppType.StrongTitle]）。卡内两行的墨都走 `LocalContentColor.current`：
 *    [AppType] 档位 getter 自带 `text1/text3`，压深套会落成浅底近白字（`Containers.kt` 类 KDoc 同条口径）。
 *  - `section('到期词', '<div class="rows">' …)`（`:484-490`）→ [SectionHeading] + 一组裸 [WordRow]
 *    （`.rows` 是卡片汤之外的裸行列表，`app.css:509-525`，所以不套 SoftCard；分线只由行号给，
 *    首行无线）。行的 `row-sub` = 词性 + 释义，走 [wordListGloss] 那一处口径。
 *  - `section('复习方式', '<div class="card-quiet">…)`（`:491-494`）→ [SectionHeading] + [QuietCard]
 *    （`.card-quiet` = 不铺底、只留 1dp 描边，`app.css:375`）里一行 [WordRow]，
 *    尾部那枚 `.dot` 是原型的选中点（`:646` = 6px 圆、`--brand-deep`），走 `actions` 槽。
 *    这一行**不是控件**：原型只画了一行说明加一个点，没有 radio / switch / 可点态，所以本屏也不给
 *    它 `onClick`、`AppSwitch` 或 `selectable`（判据钉死，防止后来把它「顺手」做成开关）。
 *  - `.foot`（`:496`）一枚主按钮「开始复习」→ [PillButton] 撑满 + 一条 [hairlineTop] 顶线，
 *    取仓内吸底槽的统一写法（`Alignment.BottomCenter` + 自铺页底色），与「今日词单」那一屏同形；
 *    原型的 `.foot` 是渐变底，差异登记在计划批 1 偏离表。
 *
 * ## 原型写了、本屏不写的（三块，全是「原型是 mock 数据、本仓没有对应采集」）
 *
 *  1. `.strong-meta`「最久的一个已经 9 天没见了，现在复习记得最牢。」和到期词行尾那格「9 天前」：
 *     两者都要逐卡读上次复习时间。本屏只吃已备好的计划快照状态（[TodayPlanUiState.Ready]），
 *     不为这两句新开一条仓库→界面的读数通路（AGENTS.md「UI 之外的逻辑保持原样，只在必要时接上界面」——
 *     这里接界面不需要它，写出来才需要编数）。
 *  2. `.meter` 那根 70% 的条与 `.strong-foot`「按遗忘曲线，今天复习过的词 6 天后才会再来。」：
 *     前者原型没写它量的是什么（照抄就是把一枚来历不明的进度条挂上屏），后者把 FSRS 的**逐卡**下次间隔
 *     说成全局的 6 天。同口径先例是批 1 Task 8 打卡页那块「完成度环整块删除」。
 *  3. 「听发音，选释义 / 练听力时用」整行：本仓词卡流程只有「看英文，想中文 + 三档反馈」这一种模式，
 *     没有播放音频选释义的能力。按用户 2026-10-29 拍板「无能力就隐藏」，这一行整行不搬；
 *     留下的「看英文，想中文 / 默认方式」是本仓真实默认模式，`.dot` 说的就是它。
 *
 * 另两条与「今日词单」同判的：到期词行前那枚 44dp 贴纸位是原型的**词级**插画
 * （`st(w.icon, 44)`，`w` 来自 `D.words`），本仓 `WordCard` 没有词级图标字段，前导槽留空；
 * 词头原型是 `serif` + 18px，本屏走共享 [WordRow] 的 [AppType.Body]（15sp，`.row-title` 的档），
 * 不为这一屏给共享词行另开一档。
 *
 * ## 空队列
 *
 * 原型是写死数据，永远有 2 个到期词，没画过「0 个」那一态。本屏的处理与 Task 16 同口径：
 * 0 个时「到期词」整节不渲染、吸底整条不出现（没有可开始的复习），强卡照旧说「0 个词在等你」——
 * 这句是真话，不自创「今天没有到期的词」那种原型里没有的文案。
 *
 * [onStartReview] 的目的地是词卡流程（原型 `data-to="learn-session"`，与今日学习那枚「开始复习」
 * 是同一条路）；[onOpenWord] 走宿主 `AppScreen` 的共享词义详情载体。两者都由宿主接线，本屏不认识导航状态。
 */
@Composable
fun ReviewQueueScreen(
    state: TodayPlanUiState.Ready,
    onBack: () -> Unit,
    onStartReview: () -> Unit,
    onOpenWord: (WordCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = appColors()
    val dueCount = state.dueQueue.size
    Box(modifier = modifier.fillMaxSize().background(colors.background).testTag("review_queue_screen")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .verticalScroll(rememberScrollState())
                // 底部留 76dp：吸底条压在滚动面之上，最后一行不给它盖住（与「今日词单」同形）。
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppScreenHeader(
                title = "复习队列",
                onBack = onBack,
            )
            StrongCard(modifier = Modifier.testTag("review_queue_summary")) {
                Text(
                    text = "到期复习",
                    style = AppType.Eyebrow,
                    color = LocalContentColor.current.copy(alpha = 0.74f),
                )
                Text(
                    text = "$dueCount 个词在等你",
                    style = AppType.StrongTitle,
                    color = LocalContentColor.current,
                )
            }
            if (dueCount > 0) {
                SectionHeading(text = "到期词")
                state.dueQueue.forEachIndexed { index, card ->
                    WordRow(
                        lemma = card.lemma,
                        gloss = wordListGloss(card),
                        onClick = { onOpenWord(card) },
                        topDivider = index > 0,
                        modifier = Modifier.testTag("review_queue_row_${card.cardId}"),
                    )
                }
            }
            SectionHeading(text = "复习方式")
            QuietCard(modifier = Modifier.testTag("review_queue_mode_card")) {
                WordRow(
                    lemma = "看英文，想中文",
                    gloss = "默认方式",
                    leading = {
                        InlineIconBadge(icon = AppIcon.Document, domain = AppDomain.Review)
                    },
                    actions = {
                        // 原型 `.dot`（`app.css:646`）：6px 圆、`--brand-deep`，纯装饰的「就是这一种」记号。
                        // 「默认方式」四个字已经把这件事念给读屏用户听了，所以这枚点不给 contentDescription，
                        // 免得装饰件混进可点序列（原型这一行也没有 radio / switch）。
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.brandDeep),
                        )
                    },
                    modifier = Modifier.testTag("review_queue_mode_row"),
                )
            }
        }
        // 吸底主按钮：0 个到期词时整条不出现（原型没画那一态，见类 KDoc）。
        // 对齐位取仓内吸底槽的既有写法 `Alignment.BottomCenter`，本槽已登记进
        // `StickyFooterHairlineGuardTest` 的台账。
        if (dueCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colors.background)
                    .then(hairlineTop(colors.outline))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                PillButton(
                    text = "开始复习",
                    onClick = onStartReview,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "review_queue_start",
                    contentDescription = "开始复习到期词",
                )
            }
        }
    }
}
