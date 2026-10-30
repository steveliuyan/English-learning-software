package com.example.englishlearning.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.englishlearning.ui.components.AppIcon
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.EntryRow
import com.example.englishlearning.ui.components.InlineIconBadge
import com.example.englishlearning.ui.components.InlineNotice
import com.example.englishlearning.ui.components.InlineNoticeKind
import com.example.englishlearning.ui.components.SectionHeading
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppShape
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintMiddle
import com.example.englishlearning.ui.theme.AppleMintStart
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.appColors

private val AiHeaderGradient = Brush.linearGradient(listOf(AppleMintStart, AppleMintMiddle, AppleMintEnd))

/**
 * 「AI 助学」一级页（tab 根页）。
 *
 * 骨架按 spec §3.5：**品牌渐变 hero（本屏唯一强卡）→ 现在能用裸行组 → 未上线功能弱卡（可展开）**。
 * 批 4 之前这里是四张各自 `border(1.dp, MintOutline, …)` 的 M3 卡：一屏九块描边卡、35 处静态薄荷桥、
 * 零处 `AppType`，而「查看词表」压在最里层的一枚 `PillButton` 上要两跳才到词库。
 *
 * 三条不变量：
 *  - 本屏的 hero 就是渐变头卡，**不另调 `AppScreenHeader(hero = true)`**：两枚 28sp 标题会撞进同一屏，
 *    connected 侧 `onNodeWithText("AI 助学")` 也会从单节点变多节点；
 *  - 页面上每一句状态描述都来自 [AiFeature]，不做本地改写：尚未实现的功能只给「入口 + 如实进度」，
 *    不制造点开即坏的假功能；
 *  - 白胶囊与渐变上的墨色是 `AiBadgeContrastFixTest` 逐行钉住的登记形状，骨架换了也一字不动。
 *
 * @param todayWordCount 今日计划的新词数，`null` 表示今日计划还不可读。
 * @param dueWordCount 今日计划的待复习词数，`null` 同上。
 * @param aiConfigured 是否已有可用的 AI 配置。取自本机真实的 AI Profile：至少有一套配了密钥才为真。
 *   AI 调用本身（F2-03 网络客户端）仍未接通，所以这一个徽章只描述「配置」状态，不承诺「能生成」。
 * @param onOpenFeature 点开某个功能页。
 * @param onOpenWordList 点「查看词表」那一行，跳去看得到真实词表的地方。
 */
@Composable
fun AiLearningScreen(
    todayWordCount: Int?,
    dueWordCount: Int?,
    aiConfigured: Boolean,
    onOpenFeature: (AiFeature) -> Unit,
    onOpenWordList: () -> Unit,
) {
    var explainerOpen by rememberSaveable { mutableStateOf(false) }
    // 裁决④（AC-R-23）：默认展开的是**现在能用**的能力，折叠键只管「未上线」那批。
    // 旧形状把 3 枚可用与 4 枚未上线混成一句「AI 功能（7 个）展开 ˅」，
    // 用户要么整页收起看不到能用的东西，要么展开后被四枚坏入口刷屏。
    var unreleasedOpen by rememberSaveable { mutableStateOf(false) }
    val implementedFeatures = AiFeature.entries.filter { it.implemented }
    val unreleasedFeatures = AiFeature.entries.filter { !it.implemented }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors().background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("ai_learning_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AiHeaderCard(aiConfigured = aiConfigured)
        // 分组小标题只当路牌（`SectionHeading` 不画线不铺底），计数放在 trailing 当纯描述。
        SectionHeading(
            text = "现在能用",
            trailing = "${implementedFeatures.size} 个",
            modifier = Modifier.testTag("ai_learning_available_section"),
        )
        // 一组 = 一张软卡；组内每行一条发丝线（`topDivider`），行本身整行可点（裸行 52dp 档）。
        SoftCard(contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.fillMaxWidth()) {
                implementedFeatures.forEachIndexed { index, feature ->
                    AiFeatureRow(
                        feature = feature,
                        topDivider = index > 0,
                        onOpen = { onOpenFeature(feature) },
                    )
                }
            }
        }
        // 未上线那批压成一条折叠行：这一行自己就标着「未上线」，不必点开也知道下面压的是什么。
        // 折叠入口走共享 EntryRow（56dp 可点面 + 展开态语义），不再手搓第二份折叠件。
        SoftCard(contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.fillMaxWidth()) {
                EntryRow(
                    icon = AppIcon.Spark,
                    domain = AppDomain.AiSpeech,
                    title = "未上线功能（${unreleasedFeatures.size} 个）",
                    subtitle = "点开逐条看进度与还差什么",
                    trailing = featureStatusBadge(false),
                    expanded = unreleasedOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_learning_unreleased_toggle"),
                ) { unreleasedOpen = !unreleasedOpen }
                if (unreleasedOpen) {
                    Column(Modifier.fillMaxWidth().animateContentSize()) {
                        unreleasedFeatures.forEach { feature ->
                            AiFeatureRow(
                                feature = feature,
                                topDivider = true,
                                onOpen = { onOpenFeature(feature) },
                            )
                        }
                    }
                }
            }
        }
        // spec §5「AI 主页」：查看词表直达词库——一枚裸行整行可点，副标题就是真实词数。
        SoftCard(contentPadding = PaddingValues(0.dp)) {
            BareRow(
                title = "查看词表",
                subtitle = wordListLine(todayWordCount, dueWordCount),
                leading = { InlineIconBadge(icon = AppIcon.Book, domain = AppDomain.Library) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_learning_nav_row"),
                onClick = onOpenWordList,
            )
        }
        SoftCard(contentPadding = PaddingValues(0.dp)) {
            Column(Modifier.fillMaxWidth().animateContentSize()) {
                EntryRow(
                    icon = AppIcon.Spark,
                    domain = AppDomain.AiSpeech,
                    title = "AI 助学如何起作用？",
                    subtitle = "数据用在哪、发往哪里、现在到哪一步",
                    expanded = explainerOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_learning_explainer_toggle")
                        .semantics { contentDescription = "AI 助学如何起作用说明" },
                ) { explainerOpen = !explainerOpen }
                if (explainerOpen) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExplainerLine("会用到的数据", "今日新词与待复习词、当前词书等级、你的复习反馈记录。")
                        ExplainerLine("数据发往哪里", "只发给你自己配置的第三方 AI 服务；应用不内置密钥，也不上传到我们的服务器。")
                        ExplainerLine("现在的状态", "AI 能力分批接通中：长难句分析与生图正在开发，其余功能点开只能看到进度与依赖。")
                        ExplainerLine("不配置会怎样", "背词、复习、默写纸、阅读记录全部离线可用，不受影响。")
                    }
                }
            }
        }
        // 原型 `sAiHome` 页末 `notice(…, 'info', 'shield')`：把「什么不出本机」集中说一次。
        // 两件事都是真实形状——拍照翻译的图只在本机 OCR（AC3-05），AI 生图先把中文转英文提示词
        // （两段式，省一次按张计费的调用），不是宣传句；因此置在页末而不是头卡里。
        InlineNotice(
            text = "拍照翻译的图片只在本机识别，不上传；AI 生图会先把中文描述转成英文提示词。",
            kind = InlineNoticeKind.Info,
            modifier = Modifier.testTag("ai_learning_privacy_notice"),
        )
    }
}

/**
 * 品牌渐变头卡：本屏唯一强卡（spec §3.5）。
 *
 * 三个刻意保持的形状（`AiBadgeContrastFixTest` 逐行钉，别按「统一主题化」顺手改）：
 *  - 渐变固定在顶层静态 `AiHeaderGradient` 上求值（组合期外），两套主题共用同一组端点；
 *  - 渐变上两行文案取 `appColors().onBrand`：白字压渐变起点只有 1.29:1；
 *  - 状态胶囊容器是主题不变的 `Color.White.copy(alpha = 0.85f)`，前景因此只能取登记的两枚静态墨
 *    （深套 `text2` 压白 2.21:1 是评审席 B1 那条形状）。
 */
@Composable
private fun AiHeaderCard(aiConfigured: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(AiHeaderGradient)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("ai_learning_header"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 2026-10-31 用户裁定删掉头卡表情（见 `docs/decisions/2026-10-31-remove-ai-mascot.md`）：
            // 头卡只留标题 / 副标题 / 状态胶囊三段，表情组件与几何纯函数一并删除。
            Text(
                "AI 助学",
                style = AppType.Headline,
                color = appColors().onBrand,
                textAlign = TextAlign.Center,
            )
            // spec §5「AI 主页」：副标题改中性一句。旧句「用你学过的词，生成专属的阅读与练习」在 AI 网关
            // 还没接通时是一句兑现不了的承诺，而这行字压在全站对比度最低的位置上、被读到的概率最高。
            Text(
                text = "这里汇总 AI 能力与各自的接通进度",
                style = AppType.Body,
                color = appColors().onBrand,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .background(Color.White.copy(alpha = 0.85f), AppShape.Pill)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("ai_learning_status_badge")
                    .semantics { contentDescription = if (aiConfigured) "AI 已配置" else "AI 尚未接通" },
            ) {
                Text(
                    text = if (aiConfigured) "AI 已配置" else "AI 尚未接通",
                    style = AppType.Label,
                    // R-23.14：白胶囊容器 `Color.White.copy(alpha = 0.85f)` 不随主题（品牌渐变头卡之上恒为亮面），
                    // 前景必须同为主题不变的两枚静态墨；改成 `appDomain(...)` / `colors.text2` 在深套就是
                    // 亮墨压白（1.69:1 / 2.21:1，B1 第三站形状）。理由与复算见 `AiBadgeContrastFixTest`。
                    color = if (aiConfigured) DomainColors.AiSpeech.deep else AppPalette.TextSecondary,
                )
            }
        }
    }
}

/**
 * 组内的一枚功能入口行（`BareRow`）：行内徽标 + 标题/一行说明 + 状态徽章 + chevron，整行一个可点目标。
 *
 * 徽标走共享 30dp 行内档（`InlineIconBadge`），字形读 `feature.icon`：单字中文（文/填/听/讲/析/拍/画）
 * 从出现那天起就是 spec §4.2 禁的「文字当图标」，但一刀换成七行同一枚 `AppIcon.Spark` 又让整列读成
 * 一行——2026-11-02 评审裁决把这枚字形还给功能自己（见 `AiFeature.icon` 的取值理由：「这一屏属于 AI」
 * 由 `AppDomain.AiSpeech` 配色承担，字形这一栏该说的是「这一行是哪个功能」）。
 *
 * 状态徽章沿用 `trailing` + `trailingTag`（共享组件批 3 加的槽位）：connected 侧要单独把「现在能用 /
 * 未上线」那枚字读出来，`ai_feature_status_badge_<key>` 这个 tag 因此仍由屏侧指定。
 *
 * TalkBack 读一行要同时读到「哪个能力」与「现在能不能用」：`contentDescription` 由屏侧补，
 * 「，状态：」这枚中文引导词是 `AccessibilityLabelContractTest` 禁令 1a 要求的（字面量里必须留中文）。
 */
@Composable
private fun AiFeatureRow(feature: AiFeature, topDivider: Boolean, onOpen: () -> Unit) {
    val status = featureStatusBadge(feature.implemented)
    BareRow(
        title = feature.title,
        subtitle = feature.summary,
        trailing = status,
        trailingTag = "ai_feature_status_badge_${feature.key}",
        leading = { InlineIconBadge(icon = feature.icon, domain = AppDomain.AiSpeech) },
        topDivider = topDivider,
        modifier = Modifier
            .testTag("ai_feature_${feature.key}")
            .semantics { contentDescription = "${feature.title}，状态：$status" },
        onClick = onOpen,
    )
}

/**
 * 词表规模文案。今日计划不可读时如实说不可读，不拿 0 冒充。
 */
private fun wordListLine(todayWordCount: Int?, dueWordCount: Int?): String = when {
    todayWordCount == null || dueWordCount == null -> "今日计划还没读出来，稍后重试。"
    else -> "今日新词 $todayWordCount 个 · 待复习 $dueWordCount 个"
}

/**
 * 说明条的一行：小标题 + 正文。
 *
 * 正文不设 `maxLines`：这四段是「数据发到哪」这类提示性内容，裁掉就是丢信息（与 `EntryRow` 副标题
 * 那套「可省略」的口径相反）。排印取 `AppType.Label` + `AppType.Footnote` 两档，不引新档。
 */
@Composable
private fun ExplainerLine(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = AppType.Label, color = appColors().text1)
        Text(body, style = AppType.Footnote, color = appColors().text2)
    }
}
