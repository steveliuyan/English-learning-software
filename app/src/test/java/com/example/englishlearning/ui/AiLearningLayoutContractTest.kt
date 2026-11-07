package com.example.englishlearning.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 2026-11-02 用户裁定：AI 助学页按参考原型 `sAiHome`（`assets/app.js:814-839`）重做。
 *
 * 上一版是「品牌渐变头卡 + 状态胶囊 + 现在能用裸行组 + 未上线折叠行 + 查看词表 + 使用说明」。
 * 用户逐条否掉了它：「把那个 AI 助学这个那个颜色给删掉，跟我之前给你的参考里面就没有这种东西」。
 * 参考页的形状只有四件：
 *  1. 普通页头：眉标「AI 助学」、标题「把词用起来」、一句说明、行尾一枚配置按钮（不是 hero、不是渐变）；
 *  2. 双列功能卡：已实现的功能先排，每张卡 = 功能字形 + 标题 + 一句说明；
 *  3. 「还在打磨」分组：未实现的功能同样双列，但不可点、带状态徽章；
 *  4. 页末一条隐私提示。
 *
 * 因此「查看词表」与「AI 助学如何起作用」两段整块离场：前者在参考里不存在，词库存取另有
 * 「学习」页与「我的 → 词书」两条路；后者是上一版为解释渐变头卡而写的补充说明。
 *
 * 取窗按符号深度配对（注释先剥），锚点改名即 `fail`，不会静默空转。
 */
class AiLearningLayoutContractTest {

    private val source: String by lazy { stripComments(uiScreenSource("AiLearningScreen.kt")) }

    private val headerTitle = "把词用起来"
    private val headerSubtitle = "每个功能都先做本机能做的部分，再把必要的文字交给已配置的服务。"

    // ---------- 1. 普通页头，不是渐变 hero ----------

    @Test
    fun `the page uses the shared plain header instead of a gradient hero`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("AppScreenHeader("))),
            "页头必须由共享 AppScreenHeader 承载且恰一处（参考 `head({...})`，不再是自绘头卡）",
        )
        listOf(
            "\"AI 助学\"" to "眉标",
            "\"$headerTitle\"" to "标题",
            "\"$headerSubtitle\"" to "说明",
        ).forEach { (literal, what) ->
            assertEquals(
                1,
                countMatches(source, Regex(Regex.escape(literal))),
                "$what「${literal.trim('"')}」必须恰一处（参考页头逐字）",
            )
        }
        listOf(
            "ai_learning_header",
            "ai_learning_status_badge",
            "AiHeaderGradient",
            "Brush.linearGradient",
            "AppleMintStart",
            "AppleMintMiddle",
            "AppleMintEnd",
        ).forEach { retired ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(retired))),
                "旧渐变头卡构件「$retired」必须整块离场：用户明确说参考版本里没有这种东西",
            )
        }
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("""contentDescription = "返回""""))),
            "一级页没有返回位：页面不得自写返回语义（返回位只归共享页头）",
        )
    }

    /** 行尾配置按钮：打开 AI 服务页，标签 `ai_learning_open_profiles`。 */
    @Test
    fun `the header action opens the AI service page`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""testTag("ai_learning_open_profiles")"""))),
            "配置按钮必须有 tag `ai_learning_open_profiles`（参考 `aria-label=\"AI 服务配置\"` 的落点）",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("""contentDescription = "AI 服务配置""""))),
            "配置按钮的可及名必须是「AI 服务配置」",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""onOpenAiProfiles: \(\) -> Unit""")),
            "屏形参 onOpenAiProfiles 必须恰一处声明",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""onClick = onOpenAiProfiles""")),
            "配置按钮必须接屏的 onOpenAiProfiles（再包一层会变成第二个真值）",
        )
    }

    // ---------- 2. 双列功能卡 ----------

    @Test
    fun `the implemented features form a two column card grid`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("chunked(columns)"))),
            "功能卡的分列必须由 `columns` 这一枚局部量给出（QA3 A2.6② 起列数是字号档的函数）：" +
                "写死 `chunked(2)` 就是让顶档继续按 1.0 档定尺，列数判据见下面那条纯函数用例",
        )
        assertEquals(
            2,
            countMatches(source, Regex(Regex.escape("aiFeatureGridColumns("))),
            "`aiFeatureGridColumns(` 必须恰两处（定义 + 屏侧唯一消费点）：多出来的每一处都是第二套列数判断",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("Spacer(Modifier.weight(1f))"))),
            "末行不满列时必须补等宽占位，否则最后一张卡会撑满整行（参考 CSS grid 不会这样）",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""(^|[^A-Za-z0-9_])AppCard\(""")),
            "卡片必须复用共享 AppCard 且恰一处（可点档与静态档只差一层 clickable）：自搓描边卡会撞容器棘轮",
        )
        listOf("SoftCard(", "StrongCard(", "BareRow(", "EntryRow(").forEach { retired ->
            assertEquals(
                0,
                countMatches(source, Regex("""(^|[^A-Za-z0-9_])${Regex.escape(retired)}""")),
                "旧列表容器「$retired」必须随形状离场：本页只剩双列卡",
            )
        }
        assertEquals(
            2,
            countMatches(source, Regex("""(^|[^A-Za-z0-9_])AiFeatureCard\(""")),
            "AiFeatureCard 必须恰两处（定义 + 双列调用），不得为两组各抄一份",
        )
        assertTrue(
            """testTag("ai_feature_${'$'}{feature.key}")""" in source,
            "每张功能卡必须带 `ai_feature_<key>` tag（connected 按它定位）",
        )
        assertTrue(
            """testTag("ai_feature_status_badge_${'$'}{feature.key}")""" in source,
            "状态徽章必须带 `ai_feature_status_badge_<key>` tag",
        )
        assertTrue(
            "InlineIconBadge(icon = feature.icon" in source,
            "卡片徽标必须读 `feature.icon`（字形由目录逐功能给出，不写死 Spark）",
        )
        assertTrue(
            "featureStatusBadge(feature.implemented)" in source,
            "状态词必须来自共享 `featureStatusBadge`，屏侧不另写一套可用/未上线措辞",
        )
    }

    /**
     * 列数判据（QA3 A2.6②）：走查册实测顶档 3.0 下每张卡在半宽列里排到约 714dp 高、徽标被推出可视区，
     * 「双列」这一形状只在 1.0/2.0 档成立。
     *
     * 判档口径沿用批 B 的「阈值严格大于钉档」：`2f` 是 AC-R-07 钉住的验收档，那一档的形状是设计稿口径，
     * 不能因为「顺手上探」把双列改单列；只有**超过**钉档才降级。抽成 `internal` 纯函数是为了让这条
     * 算术在 JVM 侧逐档点名（connected 那侧只能验「顶档确实上下堆叠」，验不出 2.0 不许动）。
     */
    @Test
    fun `the card grid falls back to one column only above the pinned font tier`() {
        assertEquals(2, aiFeatureGridColumns(1f), "1.0 档是设计稿口径：双列")
        assertEquals(2, aiFeatureGridColumns(2f), "2.0 是 AC-R-07 钉住的验收档，形状不得被顶档整改带走")
        assertEquals(1, aiFeatureGridColumns(2.5f), "超过钉档就必须单列，让每张卡拿到整幅宽度")
        assertEquals(1, aiFeatureGridColumns(3f), "Android 字体大小顶档 3.0 必须单列")
    }

    @Test
    fun `only implemented features are clickable and the rest sit under the polishing section`() {
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("features = implementedFeatures, onOpenFeature = onOpenFeature"))),
            "已实现组必须接真实回调且恰一处",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("features = unreleasedFeatures, onOpenFeature = null"))),
            "未实现组必须传 null 回调（不可点），不得假装点开能用",
        )
        assertEquals(
            1,
            countMatches(source, Regex("""onOpen: \(\(\) -> Unit\)\?""")),
            "AiFeatureCard 的可点回调必须是可空的：可空这一枚就是「未上线卡不可点」的形状凭据",
        )
        assertEquals(
            1,
            countMatches(source, Regex(Regex.escape("\"还在打磨\""))),
            "未实现分组标题必须逐字「还在打磨」（参考 `section('还在打磨', …)`）",
        )
        assertTrue(
            "ai_learning_unreleased_section" in source,
            "「还在打磨」小标题必须有 testTag 锚",
        )
    }

    // ---------- 3. 两段旧内容整块离场 ----------

    @Test
    fun `the word list entry and the explainer are gone`() {
        listOf(
            "ai_learning_nav_row",
            "ai_learning_explainer_toggle",
            "查看词表",
            "wordListLine",
            "AI 助学如何起作用",
            "ai_learning_unreleased_toggle",
        ).forEach { retired ->
            assertEquals(
                0,
                countMatches(source, Regex(Regex.escape(retired))),
                "「$retired」不属于参考页形状：词库另有学习页与「我的 → 词书」两条路，说明折叠随渐变头卡一起离场",
            )
        }
    }

    // ---------- 4. 令牌与隐私提示 ----------

    @Test
    fun `the screen speaks the six step type scale and the themed accessors only`() {
        assertEquals(
            0,
            countMatches(source, Regex("""\bMint[A-Za-z]*""")),
            "屏侧不得读静态薄荷桥：墨色一律 `appColors()`（组合期），深套才会翻色",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("MaterialTheme.typography"))),
            "屏侧不得再用 `MaterialTheme.typography`：六档 `AppType` 是唯一排印入口",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""Color\(0x[0-9A-Fa-f]{8}\)""")),
            "本屏不得写裸 Color(0xFF…)：参考页的正常卡面由 AppCard + appColors() 承担",
        )
        assertEquals(
            0,
            countMatches(source, Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")),
            "本屏不得有静态令牌读点：白胶囊已随头卡离场，深套不会浮浅套值",
        )
        val textCalls = countMatches(source, Regex("""(?<![A-Za-z])Text\("""))
        val typedReads = countMatches(source, Regex("""AppType\.[A-Z][A-Za-z]*"""))
        assertTrue(textCalls > 0, "取不到任何 `Text(`：取样面失效，本条会空转")
        assertEquals(
            textCalls,
            typedReads,
            "本屏每一处 `Text(` 都要显式取一档 `AppType`（$textCalls 处 Text / $typedReads 处读点）",
        )
        assertEquals(
            0,
            countMatches(source, Regex(Regex.escape("fontWeight ="))),
            "六档 token 自带字重，屏侧不得再 `fontWeight =` 覆写",
        )
    }

    @Test
    fun `no material button survives and the privacy notice stays`() {
        listOf("Button(", "PillButton(", "TextButton(").forEach { anchor ->
            assertEquals(
                0,
                countMatches(source, Regex("""(?:^|[^A-Za-z0-9_])${Regex.escape(anchor)}""")),
                "屏侧不得再有 $anchor：页头动作是图标按钮，功能入口是整张卡",
            )
        }
        assertEquals(
            1,
            countMatches(source, Regex("""(^|[^A-Za-z0-9_])InlineNotice\(""")),
            "页末隐私提示必须复用共享 InlineNotice 且恰一处（参考 `notice(…, 'info', 'shield')`）",
        )
        assertTrue(
            """testTag("ai_learning_privacy_notice")""" in source,
            "隐私提示必须保留 `ai_learning_privacy_notice` 锚（connected 按它定位）",
        )
        assertTrue(
            "拍照翻译的图片只在本机识别，不上传；AI 生图会先把中文描述转成英文提示词。" in source,
            "隐私提示文案必须逐字保留（两条都是真实形状，不是宣传句）",
        )
    }
}
