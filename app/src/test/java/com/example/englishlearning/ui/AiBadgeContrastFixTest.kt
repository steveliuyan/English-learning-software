package com.example.englishlearning.ui

import androidx.compose.ui.graphics.toArgb
import com.example.englishlearning.ui.theme.AppColorDark
import com.example.englishlearning.ui.theme.AppColorLight
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppPalette
import com.example.englishlearning.ui.theme.AppleMintEnd
import com.example.englishlearning.ui.theme.AppleMintMiddle
import com.example.englishlearning.ui.theme.AppleMintStart
import com.example.englishlearning.ui.theme.DomainColors
import com.example.englishlearning.ui.theme.DomainDark
import com.example.englishlearning.ui.theme.DomainLight
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Task 23（裁决④ / AC-R-23）：AI 面「把文字当图标」的禁令 + 徽标墨色的复算。
 *
 * 为什么两张判据都在本文件（而不是 plan 原文那条 `"AI"` 字面量断言）：
 *  `grep -rn '"AI"' app/src/main/java/com/example/englishlearning` 现盘 **0 命中**，
 *  plan Step 1 那条 `assertFalse(Regex("\"AI\"").containsMatchIn(src))` 从写下那天起就是空转，
 *  删掉徽标也不会红、留着徽标也不会红——它证明不了任何事。spec §4.2 禁的是「文字当图标」这一**形状**，
 *  本仓 AI 面的真实形状是 `Text(feature.glyph, …)`（单字中文徽标，字形来自 `AiFeature.kt` 的 `glyph`
 *  字段：文/填/听/讲/析/拍/画）与行尾 `Text("›", …)`。本契约锚这两枚真实站点，
 *  并把取样切片**限定在这两枚 AI 文件内**——`Text("›")` 在 `GlobalVocabularySearchScreen.kt`
 *  另有站外站点，不属本批，写宽了会当场判红并把本席推向未授权扩面。
 *
 * 亮度侧按本仓口径（`SourceRatchet` 文件头 + Task 9 实测）：connected 读不到墨色，所以配对合法性
 *  由 JVM 侧从**活 token 常量**当场复算，注释与断言都不抄 hex 字面量——抄了字面量就退化成
 *  「token 改了测试还绿」。渐变站点保持静态（Task 3 口径），前景按 2026-10-18 裁决 #3 取 on-brand。
 *
 * ## 修复轮 1 补的牙（评审席 M3 + m1/m2，控制者裁定见 `task-23-fix1-brief.md` §四）
 *
 * 批 3 原判据只守 token 侧（token×渐变端点）与文件级子串（`contains("AppGlyph(")` 等两枚），
 * 站点侧「前景到底取了哪枚墨」零防线——这正是 B1（三处前景单迁、深色档 1.49–2.21:1）能穿过
 *  全部门与 connected 的洞。修复轮补三族腿：
 *  ① **亮度腿（两套都要有）**：`the mint primary ink pair clears the AA floor on both theme sets`
 *    钉 R-23.13′ 成立的前提（`onBrand` 压浅/深两套 AiSpeech 亮底均 ≥4.5），并反向钉「`onMint` 压两套亮底
 *    **都不达** 4.5」——「亮底只认 onBrand」的 decoy 腿，哪天调色板真把它修达标，本腿先红、逼裁决与站点形状同批改；
 *  ② **站点形状腿 + 现算**：`the mint primary sites print on brand ink on both theme sets`
 *    从源码里把「容器 `MintPrimary`（两套皆亮）之上那两枚前景表达式」抠出来（按钮 `contentColor` 与
 *    `StatusPill` 的「现在能用」档），要求取的是**主题无关的 `colors.onBrand`**（底不随主题变，墨就不分档），
 *    并把取到的墨 token 的**两套活值**当场压两套 AiSpeech 底复算 ≥4.5。
 *    批 3 的裸 `contentColor = colors.onMint` 走这条腿时深档读数 ≈1.49:1，当场红——B1 的直接防线；
 *    **2026-10-29 原型 token 迁移**：`onMint` 改成 `brandDeep` 别名（浅 1A7A63 / 深 7FE3C1），旧分档形的
 *    浅档腿降到 2.69:1 也红了——分档本身失去存在理由（两套底同亮），判据随之收成「单墨 `onBrand`」，
 *    旧分档形从「合规本体」降级成反事实（见 counterfactual 用例）。
 *  ③ **容器不随主题 ⇒ 前景不许单迁**：`the theme invariant white pill keeps its registered static foregrounds`
 *    钉白胶囊（`Color.White.copy(alpha = 0.85f)`，R-23.11 未授权主题化，归 Task 26/27）的两枚前景
 *    保持登记静态（R-23.14 回退），并对两枚静态墨当场压白复算——未配置档 ≥4.5，已配置档按
 *    批前既有债口径钉「≥3.0 且 <4.5」两向（债被真修好时本腿同样先红，见用例消息）。
 *  合成反事实（裸 `colors.onMint` 必须判红）与 m1/m2 的补形在
 *  `the mint primary ink rule catches the bare on mint ink counterfactual` 与下面两条禁字用例里，
 *  它们不依赖生产码，**现在就要绿**。
 *
 * 取样与剥注释一律走共享夹具 `uiScreenSource` / `stripComments`（F6 禁令：测试侧不得再长私有剥注释），
 * 亮度与比值一律走包级共享件 `SourceRatchet.luminance` / `ratio`（本席不写第三枚私有 `ratio`；
 * 类内私有副本是既有债，见 `task-23-fix1-brief.md` §一「复算口径」）。
 */
class AiBadgeContrastFixTest {

    /** 单字中文徽标形状（m1：并集加命名实参半边，`Text(text = feature.glyph…)` 不再整体逃逸）。 */
    private val textAsBadgeRule = Regex("""Text\(\s*(text\s*=\s*)?feature\.g[A-Za-z]*""")

    /** 行尾箭头形状：`Text("›" …)`——箭头必须由字形渲染，不能是一个字符。 */
    private val textAsChevronRule = Regex("""Text\(\s*"›""")

    /**
     * m1 的第二半边：`.glyph` **裸读点**禁令。`val g = feature.glyph; Text(g)` 这类中转写法绕过一切
     * `Text(` 锚定，但只要读过属性就绕不过这枚点号锚；`AiFeature.glyph` 字段本体在 `AiFeature.kt`
     * （非屏文件，本规则看不见它），它的无消费者账归 Task 26（m4）。
     */
    private val glyphPropertyRead = Regex("""\.glyph\b""")

    /** 本批的取样切片：只有这两枚 AI 屏（R-23.7 的两屏范围）。 */
    private val aiSurfaces = listOf("AiFeatureScreen.kt", "AiLearningScreen.kt")

    /**
     * m2 腿的取样锚：共享字形出口那一次调用里必须出现的字形实参。
     *
     * 2026-11-02 图标去重换锚（只换钉哪一枚实参，**判据没放宽**）：本腿原先钉的是 `AppIcon.Spark`——
     * 那时两屏的徽标（功能行 30dp 行内档、详情页头 44dp 渐变档）共用同一枚 AI 域锚点。用户评审把这枚
     * 共用判成缺陷（「七行图标一样，扫不出哪行是拍照翻译」），字形改由目录 `AiFeature.icon` 逐功能给出，
     * 两个站点随之都读 `feature.icon`；`AppIcon.Spark` 在本页只剩 `EntryRow(icon = AppIcon.Spark)`
     * 那两枚折叠/说明行（属 `EntryRow`，不在 [sharedGlyphExitCall] 并集里，也不是功能字形）。
     * 若仍按 Spark 取样就会判红——那不是「徽标被删了」，是锚点过期。
     *
     * 换锚后仍承重的三件事：取样单元仍是 [sharedGlyphExitCall] 的**单次调用**（「文件级两枚同现」照旧不算数）；
     * 「字形必须出自目录而不是屏侧写死」另由 `AiFeatureIconContractTest` 反向钉住（0 处 `AppIcon.Spark`）；
     * 「≥40dp 档不得显式 StrokeOnly」那条腿不受换锚影响。
     */
    private val sharedGlyphAnchor = "feature.icon"

    /** `AppGlyph` 单次调用（允许一层嵌套括号与跨行）——m2 把「同一枚调用」钉成取样单元。 */
    private val appGlyphCall = Regex("""AppGlyph\((?:[^()]|\([^()]*\))*\)""")

    /**
     * 共享字形出口的**并集**（批 4 Task 1 补的半边）：字形可以直接交给 `AppGlyph` 画，也可以交给
     * 30dp 行内徽标 `InlineIconBadge` 或 40dp 入口容器 `EntryIconBadge`——这两枚内部同样经 `AppGlyph`
     * 出图，只是屏侧不再自己写那枚调用。
     *
     * 批 4 的 AI 主页把功能行的徽标从「私有 38dp 盒子 + `AppGlyph(Spark)`」换成共享
     * `InlineIconBadge`，若取样面仍只认 `AppGlyph(`，就会把「换掉了徽标」误判成「删掉了徽标」。
     * 取样单元仍是**同一枚调用**（Spark 必须出现在实参里），「单字中文当图标」另由
     * [textAsBadgeRule] 与 [glyphPropertyRead] 两族挡住，本并集不会放过它们。
     */
    private val sharedGlyphExitCall =
        Regex("""(?:AppGlyph|InlineIconBadge|EntryIconBadge)\((?:[^()]|\([^()]*\))*\)""")

    /**
     * RED（修复轮判据红样，改生产之前现盘读数）：AI 面不得再有「文字当图标」。
     * 批 3 落地时现盘判红三处的读数记录在 `t23-RED-1-jvm.log`（`AiFeatureScreen` / `AiLearningScreen`
     * 各一枚 `Text(feature.glyph`，`AiLearningScreen` 另有枚 `Text("›")`）；本批已清，现盘绿。
     */
    @Test
    fun `no letter is used as an icon anywhere on the AI surface`() {
        aiSurfaces.forEach { name ->
            val source = uiScreenSource(name)
            assertFalse(
                textAsBadgeRule.containsMatchIn(source),
                "$name 仍在把单字中文当图标（spec §4.2）：应换共享字形出口 AppGlyph(icon = feature.icon, …)",
            )
            assertFalse(
                textAsChevronRule.containsMatchIn(source),
                "$name 仍在用「›」这个字符当箭头（spec §4.2）：应换 AppGlyph(icon = AppIcon.ChevronRight, …)",
            )
        }
    }

    /**
     * m2：共享字形出口必须**同一次调用**里成对出现——`AppGlyph(` 与字形实参分踹两处
     * （哪怕同文件）不算数，否则有人把字形换成别的形状、文件里再留一枚裸字形引用就蒙过去。
     * 字形实参是 [sharedGlyphAnchor]（`feature.icon`，2026-11-02 图标去重后的唯一出处）。
     * `layer` 侧按裁决只钉一条：≥40dp 容器档不得显式传 `StrokeOnly`（真机「图标一半一半」的共同形状，
     * 40dp 档两层都要出得来；Duotone 缺省的自动化半边由「同一枚调用含字形实参且未显式换层」承重）。
     */
    @Test
    fun `the AI surface draws its shared glyph instead of a letter`() {
        aiSurfaces.forEach { name ->
            val calls = sharedGlyphExitCall.findAll(uiScreenSource(name)).map { it.value }.toList()
            assertTrue(calls.isNotEmpty(), "$name 未调用共享字形出口 AppGlyph( / InlineIconBadge(：徽标被删掉了而不是换掉了")
            val anchoredCalls = calls.filter { it.contains(sharedGlyphAnchor) }
            assertTrue(
                anchoredCalls.isNotEmpty(),
                "$name 的字形实参 `$sharedGlyphAnchor` 没有钉在任何一次共享字形出口的实参里（m2：文件级子串两枚同现 ≠ 同一调用）",
            )
            calls.filter { it.contains("AppGlyphLayer.StrokeOnly") }.forEach { call ->
                val size = Regex("""size\s*=\s*(\d+)\.dp""").find(call)?.groupValues?.get(1)?.toInt()
                assertTrue(
                    size != null && size < 40,
                    "$name 在 ${size ?: "找不到 size 实参"}dp 档显式传了 StrokeOnly——≥40dp 容器不得显式单层" +
                        "（裁决④ 取证：40dp 档 Duotone 两层都出得来，StrokeOnly 是「图标一半一半」那批的形状）：$call",
                )
            }
        }
        // 反空转：并集半边不得把「单字当图标」判成合规出口（否则把徽标换成字也会绿）。
        assertFalse(
            sharedGlyphExitCall.containsMatchIn("""Text(feature.glyph, fontWeight = FontWeight.Bold)"""),
            "共享字形出口的并集把「单字当图标」判成了合规（采样面写宽了）",
        )
    }

    /**
     * 反空转钉：禁字规则必须真的能抓到禁令本体（含 m1 补的命名实参与 `.glyph` 裸读点两形）。
     * 一旦规则被写宽/写窄到「什么都抓不到」，本用例当场判红。
     */
    @Test
    fun `the letter icon rules actually catch the forbidden shapes`() {
        val forbiddenBadge = "Text(feature.glyph, fontWeight = FontWeight.Bold, color = MintPrimaryDark)"
        val forbiddenChevron = "Text(\"›\", style = MaterialTheme.typography.titleLarge, color = MintTextMuted)"
        // m1 补的两形：命名实参逃 `Text\(\s*feature`，中转局部变量逃一切 `Text(` 锚。
        val namedArgumentBadge = "Text(text = feature.glyph, fontWeight = FontWeight.Bold)"
        val indirectRead = "val g = feature.glyph\nText(g, style = MaterialTheme.typography.titleMedium)"
        assertTrue(textAsBadgeRule.containsMatchIn(forbiddenBadge), "单字徽标规则抓不到禁令本体（空转）")
        assertTrue(textAsBadgeRule.containsMatchIn(namedArgumentBadge), "命名实参形状逃逸（m1：并集缺 text= 半边）")
        assertTrue(textAsChevronRule.containsMatchIn(forbiddenChevron), "箭头规则抓不到禁令本体（空转）")
        assertTrue(glyphPropertyRead.containsMatchIn(indirectRead), "「.glyph 裸读点」规则抓不到中转形状（m1 空转）")
        assertTrue(glyphPropertyRead.containsMatchIn(forbiddenBadge), "「.glyph 裸读点」规则连本体都抓不到（空转）")
        // 反向：合规写法不得被误伤（否则「删掉箭头」也会被判红，判据就没有方向性）。
        assertFalse(textAsBadgeRule.containsMatchIn("AppGlyph(icon = AppIcon.Spark, tint = ink, size = 20.dp)"))
        assertFalse(textAsChevronRule.containsMatchIn("AppGlyph(icon = AppIcon.ChevronRight, tint = ink, size = 16.dp)"))
        assertFalse(glyphPropertyRead.containsMatchIn("AppGlyph(icon = AppIcon.Spark, tint = ink, size = 20.dp)"))
    }

    /** m1：AI 两屏内不得出现任何 `.glyph` 裸读点（字段本体在 `AiFeature.kt`，不在本规则视野内）。 */
    @Test
    fun `no AI screen reads the glyph property at all`() {
        aiSurfaces.forEach { name ->
            val hits = glyphPropertyRead.findAll(uiScreenSource(name)).map { it.value }.toList()
            assertTrue(
                hits.isEmpty(),
                "$name 仍在读 `.glyph` 属性（$hits）——无论 `Text(feature.glyph)`、命名实参还是中转局部变量，" +
                    "「单字中文当图标」都从这枚读点长回来（spec §4.2 / m1）",
            )
        }
    }

    /**
     * on-brand 前景压品牌渐变的亮度复算（裁决 #3：渐变上一律 on-brand）。
     *
     * 读数只从 token 常量取：`AppColorLight.onBrand` / `AppColorDark.onBrand` 与两处渐变端点，
     * 断言里不出现 hex 字面量。深套 `onBrand` 与浅套同值是 AppColors.kt 文件头登记的裁决
     * （品牌渐变深浅两套同三段），这里复算它、防止有人「顺手」给深套换值而渐变侧没人核。
     */
    @Test
    fun `on_brand_foreground_clears_the_AA_floor_on_both_gradient_ends`() {
        assertEquals(
            AppColorLight.onBrand, AppColorDark.onBrand,
            "深套 on-brand 与浅套不同值：品牌渐变两套同三段，裁决 #3 只给了一个前景值，必须一起核验",
        )
        val foreground = AppColorLight.onBrand.toArgb()
        listOf("渐变起点" to AppleMintStart, "渐变终点" to AppleMintEnd).forEach { (label, stop) ->
            val value = ratio(foreground, stop.toArgb())
            assertTrue(
                value >= 4.5,
                "$label 上 on-brand 前景只有 ${"%.2f".format(value)}:1（AA 正文下限 4.5:1）",
            )
        }
    }

    /**
     * 批 3 结转缺陷收口（`docs/superpowers/plans/2026-10-17-ui-redesign.md` 批 2 修复轮 I-3 末句：
     * 「同屏另有存量缺陷 `AiLearningScreen.kt:156/161`（品牌渐变上白字）结转批 3」）：
     * `AiLearningScreen` 品牌渐变头卡的两行文案必须改走 on-brand 墨，不许再是 `Color.White`。
     *
     * 现算（活 token，断言不抄 hex）：`onBrand` 压三段渐变 12.81 / 9.95 / 6.87:1 全过正文 4.5；
     * 白字压最亮端 `AppleMintStart` 只有 1.29:1 —— Task 26 Step 4 的 360dp 真机截图里，
     * 这两行（「AI 学」与副标题）是全站对比度最低的文字。白胶囊 `.background(Color.White…)`
     * 是容器不是墨，本规则不得误伤它（容器主题化仍归 Task 27 的登记账）。
     */
    @Test
    fun `the AI learning hero prints on brand ink instead of white on the mint gradient`() {
        val source = stripComments(uiScreenSource("AiLearningScreen.kt"))
        val headerScope = Regex("""private fun AiHeaderCard\([\s\S]*?\n\}""").find(source)?.value
        assertTrue(headerScope != null, "抓不到 AiHeaderCard 的作用域（函数搬家/改名：本腿失去对象）")
        val whiteInk = Regex("""color\s*=\s*Color\.White\b""")
        assertEquals(
            0,
            whiteInk.findAll(headerScope!!).count(),
            "品牌渐变头卡仍有 `color = Color.White` 的文案墨（批 2 I-3 结转债）：应改 `appColors().onBrand`",
        )
        assertEquals(
            2,
            Regex("""color\s*=\s*appColors\(\)\.onBrand\b""").findAll(headerScope).count(),
            "头卡两行文案（「AI 助学」与副标题）必须都走 on-brand 墨；少一枚=还有白字，多一枚=墨挪到了别处",
        )
        // 反空转：规则要抓得到禁令本体，且不把白胶囊容器当墨。
        assertTrue(
            whiteInk.containsMatchIn("""Text("AI 助学", color = Color.White)"""),
            "白字规则抓不到禁令本体（空转）",
        )
        assertFalse(
            whiteInk.containsMatchIn(""".background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(50))"""),
            "白胶囊容器被误判成墨：容器不是本条的判据对象",
        )
        val ink = AppColorLight.onBrand.toArgb()
        listOf("起点" to AppleMintStart, "中点" to AppleMintMiddle, "终点" to AppleMintEnd).forEach { (label, stop) ->
            val value = ratio(ink, stop.toArgb())
            assertTrue(
                value >= 4.5,
                "on-brand 压渐变${label}只有 ${"%.2f".format(value)}:1（AA 正文下限 4.5:1）——结转修复的前提破了",
            )
        }
        val whiteOnStart = ratio(0xFFFFFFFF.toInt(), AppleMintStart.toArgb())
        assertTrue(
            whiteOnStart < 4.5,
            "白字压渐变起点已达 ${"%.2f".format(whiteOnStart)}:1——「结转批 3」的依据失实，先复核台账再撤本条",
        )
    }

    /**
     * AiFeatureScreen 的静态令牌读点必须清零；AiLearningScreen 只允许 R-23.14 登记的那**一行例外**。
     *
     * 批 3 原口径「两屏静态读点全零」是控制者派工块错误前提（深套取值必然不劣于浅套）的产物，修复轮改回
     * 如实口径：白胶囊容器 `Color.White.copy(alpha = 0.85f)` 不随主题（且是 `RawColorLiteralRatchet` 的地盘），
     * 前景单迁到 `appDomain(...).deep` / `colors.text2` 等于把亮墨钉死在白底上——已按 R-23.14 回退为
     * 批前那两枚静态读点，容器主题化归 Task 26/27。本用例把例外钉到行、钉到枚数，并与棘轮 seed 交叉对齐
     * （修一处忘另一处即红）；「AI 两屏其余墨色走组合期令牌族」由文件内 `Mint*` 组合期 getter 的
     * 既有契约（`MintTokenIsComposableContractTest`）承重，本处不重复。
     */
    @Test
    fun `the AI screens read tokens through the themed accessors only`() {
        val staticRead = Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")
        val featureHits = staticRead.findAll(uiScreenSource("AiFeatureScreen.kt")).map { it.value }.toList()
        assertTrue(
            featureHits.isEmpty(),
            "AiFeatureScreen.kt 仍有静态令牌读点 $featureHits（深色档会停在浅值）：应改 appColors() / appDomain(AppDomain.AiSpeech)",
        )
        val learningSource = uiScreenSource("AiLearningScreen.kt")
        val learningHits: List<String> = staticRead.findAll(learningSource).map { it.value }.toList().sorted()
        assertEquals(
            // 读点记号止于第一段成员名（正则类不含点号）：`DomainColors.AiSpeech.deep` 命中记作 `DomainColors.AiSpeech`。
            listOf("AppPalette.TextSecondary", "DomainColors.AiSpeech"),
            learningHits,
            "AiLearningScreen.kt 的静态读点必须恰是白胶囊前景那一行登记的两枚（R-23.14 回退形状）：实际 $learningHits。" +
                "多出来=又长出静态墨；少了=seed 与注记漂移；换成 themed 墨=容器未随主题而前景单迁（B1 形状）",
        )
        assertTrue(
            Regex("""color = if \(aiConfigured\) DomainColors\.AiSpeech\.deep else AppPalette\.TextSecondary,""")
                .containsMatchIn(learningSource),
            "登记的例外必须恰在头卡状态徽章那一行（`color = if (aiConfigured) …`），不许挪进别的行躲计数",
        )
        assertEquals(
            2,
            DarkTokenSeedAllowlist["AiLearningScreen.kt"],
            "白胶囊前景的静态读点与 DarkTokenSeedAllowlist 的 seed 不同步（逐文件精确相等口径：修一处两张账同改）",
        )
        // 迁移的目标形状必须在真正没回退的那一侧成立，而不是「把静态读点删了、颜色也不给了」。
        assertTrue(
            uiScreenSource("AiFeatureScreen.kt").contains("appColors()"),
            "AiFeatureScreen.kt 迁移后必须经 appColors() 取墨（徽标前景、按钮与徽章的主题无关亮底墨三处）",
        )
    }

    /** 渐变站点的持有符号名（MintPalette KDoc 的三处静态 Brush 之一）：本批不得把它改成组合期求值。 */
    @Test
    fun `the brand gradient stays a top level static brush`() {
        val source = File("$UI_MAIN_SOURCE_PATH/AiFeatureScreen.kt").readText()
        val declaration = Regex("""^private val AiFeatureGradient = Brush\.linearGradient\(""", RegexOption.MULTILINE)
        assertTrue(
            declaration.containsMatchIn(stripComments(source)),
            "顶层静态渐变 AiFeatureGradient 被改写了：Task 3 口径要求品牌渐变保持静态（DrawScope 之外不求值）",
        )
    }

    // ---- 修复轮 1（B1/M3）：MintPrimary 前景分档的站点形状腿 + 两套亮度现算 ----

    /** AiSpeech 实底容器（`MintPrimary`）在两套主题里的活值——两套皆亮，是 B1 的根因面。 */
    private val mintPrimaryLightBase = DomainLight.getValue(AppDomain.AiSpeech).base.toArgb()
    private val mintPrimaryDarkBase = DomainDark.getValue(AppDomain.AiSpeech).base.toArgb()

    /** 白胶囊的复算底：与 §一 复算口径一致按不透明白算（`GlassButton.kt:70` 同一口径）。 */
    private val pillWhite = 0xFFFFFFFF.toInt()

    /**
     * 墨 token → 两套活值。resolver 认不到的 token 判红而不是放行——新增墨必须先来这边配对，
     * 否则本腿对着一枚没人核过的墨恒绿。`MintPrimaryDark` 两套值按 `MintPalette.kt:58`（读 `text1`）解引用。
     */
    private val inkValues: Map<String, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>> = mapOf(
        "colors.onMint" to (AppColorLight.onMint to AppColorDark.onMint),
        "colors.onBrand" to (AppColorLight.onBrand to AppColorDark.onBrand),
        "colors.text1" to (AppColorLight.text1 to AppColorDark.text1),
        "colors.text2" to (AppColorLight.text2 to AppColorDark.text2),
        "MintPrimaryDark" to (AppColorLight.text1 to AppColorDark.text1),
    )

    /**
     * 容器是**亮底 AiSpeech 实底**的按钮调用里 `contentColor` 的实参表达式（`AiFeatureScreen`「去配置 AI 服务」
     * 那枚按钮的形状）。
     *
     * 容器表达式两种拼法都认：批 3 前的 `MintPrimary`（它就是
     * `LocalDomainColors.current.getValue(AppDomain.AiSpeech).base` 的别名），以及批 4 Task 2 起把
     * 「容器随主题」写在调用点上的 `appDomain(AppDomain.AiSpeech).base`——两者**同一个值**，
     * 本腿只关心「亮底容器上的前景是否分档」，不关心容器用哪个别名。
     */
    private val mintPrimaryButtonCall = Regex(
        """buttonColors\(\s*containerColor = (?:MintPrimary(?![A-Za-z])|appDomain\(AppDomain\.AiSpeech\)\.base),\s*contentColor = ((?:[^(),\n]|\([^(),\n]*\))*)\s*,?\s*\)""",
    )

    /**
     * 把「亮底容器上的前景表达式」解成（浅档墨, 深档墨）。认得的形状：
     *  - `implemented` 门控链：`if (implemented) A else if (implemented) B else C`、
     *    `if (implemented) A else B`——门控真时两套主题同取 A（底是亮底，墨不分档），
     *    所以两档都记 A；C / B 是**未上线档**（中性 `tint` 底）的墨，不在本判据的亮底上；
     *  - 裸 token：两档同值；
     *  - 旧 R-23.13 分档形（`if (!LocalIsDark.current) A else B` 一族）：**只为反事实取样**——
     *    2026-10-29 原型 token 迁移后它两种主题都不过线，判据必须判红，不许放行。
     */
    private fun resolveMintPrimaryInks(expression: String): Pair<String, String>? {
        Regex("""^if \(implemented && !LocalIsDark\.current\) ([\w.]+) else if \(implemented\) ([\w.]+) else ([\w.]+)$""")
            .find(expression)?.let { return it.groupValues[1] to it.groupValues[2] }
        Regex("""^if \(!LocalIsDark\.current\) ([\w.]+) else ([\w.]+)$""")
            .find(expression)?.let { return it.groupValues[1] to it.groupValues[2] }
        Regex("""^if \(implemented\) ([\w.]+) else ([\w.]+)$""")
            .find(expression)?.let { return it.groupValues[1] to it.groupValues[1] }
        Regex("""^([\w.]+)$""").find(expression)?.let { return it.groupValues[1] to it.groupValues[1] }
        return null
    }

    /** 站点形状 + 两套亮度现算的**纯判据**（真实站点与合成反事实共用同一条链，判据不分叉）。 */
    private fun mintPrimaryInkOffenders(label: String, expression: String): List<String> {
        val inks = resolveMintPrimaryInks(expression)
            ?: return listOf("$label：认不出的亮底容器前景形状（新增形状须先在 resolveMintPrimaryInks 配对并两套复算）：$expression")
        val lightInk = inkValues[inks.first]
            ?: return listOf("$label：浅档墨 ${inks.first} 不在 inkValues resolver 里（判据不许对没核过的墨恒绿）")
        val darkInk = inkValues[inks.second]
            ?: return listOf("$label：深档墨 ${inks.second} 不在 inkValues resolver 里（同上）")
        val offenders = mutableListOf<String>()
        val lightRatio = ratio(lightInk.first.toArgb(), mintPrimaryLightBase)
        if (lightRatio < 4.5) {
            offenders += "$label：浅档 ${inks.first} 压浅套 AiSpeech 亮底只有 ${"%.2f".format(lightRatio)}:1（AA 正文下限 4.5:1）"
        }
        val darkRatio = ratio(darkInk.second.toArgb(), mintPrimaryDarkBase)
        if (darkRatio < 4.5) {
            offenders += "$label：深档 ${inks.second} 压深套 AiSpeech 底只有 ${"%.2f".format(darkRatio)}:1——" +
                "深套 AiSpeech 底仍是亮底（DomainDark 的 base/deep 合一），亮墨压亮底正是 B1 的深色档回归形状"
        }
        if (
            expression.contains("LocalIsDark") ||
            inks.first != "colors.onBrand" ||
            inks.second != "colors.onBrand"
        ) {
            offenders += "$label：站点侧形状未按 R-23.13′ 取主题无关的 `colors.onBrand`" +
                "（底两套皆亮 ⇒ 墨不分档；`onMint` 是 brandDeep 别名，浅 2.69:1 / 深 1.11:1 都不过线）：$expression"
        }
        return offenders
    }

    /**
     * 亮度腿的前提面（M3①）：`onBrand` 压两套 AiSpeech 亮底**各自**过 AA，外加两枚 decoy——
     * `onMint`（brandDeep 别名）压浅/深两套亮底都必须**不达** 4.5，这是「亮底只认 onBrand、
     * 不再按主题分档」的全部理由。调色板哪天把 decoy 那对修达标了，本用例先红，逼着人回头
     * 复核站点形状而不是让它变成死条款。
     */
    @Test
    fun `the mint primary ink pair clears the AA floor on both theme sets`() {
        listOf(
            "onBrand 压浅套 AiSpeech 亮底" to ratio(AppColorLight.onBrand.toArgb(), mintPrimaryLightBase),
            "onBrand 压深套 AiSpeech 亮底" to ratio(AppColorDark.onBrand.toArgb(), mintPrimaryDarkBase),
        ).forEach { (label, value) ->
            assertTrue(
                value >= 4.5,
                "$label 只有 ${"%.2f".format(value)}:1（AA 正文下限 4.5:1）——R-23.13′ 的前提破了，" +
                    "先修订裁决与站点形状腿，再谈取值",
            )
        }
        listOf(
            "浅套 onMint（brandDeep 别名）压浅套 AiSpeech 亮底" to
                ratio(AppColorLight.onMint.toArgb(), mintPrimaryLightBase),
            "深套 onMint 压深套 AiSpeech 亮底" to
                ratio(AppColorDark.onMint.toArgb(), mintPrimaryDarkBase),
        ).forEach { (label, value) ->
            assertTrue(
                value < 4.5,
                "$label 已达 ${"%.2f".format(value)}:1——「亮底只认 onBrand」的唯一理由消失：" +
                    "届时先复核 onMint 是否重新成为及格的亮底墨、再同步撤站点形状腿（不允许静默改判据方向）",
            )
        }
    }

    /**
     * B1 的直接防线（M3②）：两枚「亮底容器 + 前景」站点（`AiFeatureScreen` 的主按钮 + `StatusPill`
     * 现在能用档）的前景必须取主题无关的 `colors.onBrand`，且这枚墨按两套活值当场压两套 AiSpeech 底
     * 复算 ≥4.5。
     * **修复轮 RED 读数**：批 3 两站点都是裸 `colors.onMint` 单迁形，深档复算 ≈1.49:1，本用例红
     * （`t23-fix1-RED-jvm.log`）；R-23.13 落地后绿。
     * **2026-10-29 原型 token 迁移**：`onMint` 改成 brandDeep 别名后，旧分档形的浅档腿也塌到 2.69:1——
     * 分档失去存在理由（容器两套同亮），站点随之收成单墨 `onBrand`，本腿跟着换成单墨形状。
     */
    @Test
    fun `the mint primary sites print on brand ink on both theme sets`() {
        val featureSource = uiScreenSource("AiFeatureScreen.kt")
        val offenders = mutableListOf<String>()

        val button = mintPrimaryButtonCall.find(featureSource)
        if (button == null) {
            offenders += "抓不到亮底 AiSpeech 实底容器（MintPrimary / appDomain(AppDomain.AiSpeech).base）的 " +
                "buttonColors 调用（站点搬家或容器被换：本腿失去对象）"
        } else {
            offenders += mintPrimaryInkOffenders("AiFeatureScreen「去配置 AI 服务」按钮", button.groupValues[1])
        }

        val pillScope = Regex("""private fun StatusPill\([\s\S]*?\n\}""").find(featureSource)
        val pillExpression = pillScope?.value?.let { Regex("""color = ([^\n,]+),""").find(it)?.groupValues?.get(1)?.trim() }
        if (pillExpression == null) {
            offenders += "抓不到 StatusPill 的前景 color 表达式（函数搬家或改名：本腿失去对象）"
        } else {
            offenders += mintPrimaryInkOffenders("AiFeatureScreen StatusPill「现在能用」档", pillExpression)
        }

        assertTrue(
            offenders.isEmpty(),
            "MintPrimary（两套皆亮的 AiSpeech 实底）上的前景没取主题无关的 `colors.onBrand`，或这枚墨压不住底：\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * 合成反事实（现在就要绿，不依赖生产码）：判据对裸 `colors.onMint` 单迁形必须判红，
     * 对 R-23.13′ 的单墨写法必须放行，对 resolver 不认识的墨必须响亮判红而不是恒绿。
     *
     * 2026-10-29 原型 token 迁移把**旧分档形**从「合规本体」翻成反事实：`onMint` 现在是 brandDeep
     * 别名，浅档腿压亮底只有 2.69:1——判据若还放行它，equal 于让 2.69:1 的浅色文字上线。
     */
    @Test
    fun `the mint primary ink rule catches the bare on mint ink counterfactual`() {
        assertTrue(
            mintPrimaryInkOffenders("反事实·裸前景", "colors.onMint").isNotEmpty(),
            "反事实失控：裸 contentColor = colors.onMint（B1 形状，两套亮底都 ≈1–2.7:1）被判成合规——本腿空转",
        )
        assertTrue(
            mintPrimaryInkOffenders("反事实·裸分档前景", "if (implemented) colors.onMint else MintPrimaryDark").isNotEmpty(),
            "反事实失控：if (implemented) colors.onMint else MintPrimaryDark（「现在能用」档单迁形）被判成合规",
        )
        assertTrue(
            mintPrimaryInkOffenders("反事实·错分档", "if (!LocalIsDark.current) colors.onBrand else colors.text1").isNotEmpty(),
            "反事实失控：分档分错墨（深档 text1 亮墨压亮底）被判成合规",
        )
        assertTrue(
            mintPrimaryInkOffenders("反事实·旧 R-23.13 分档形", "if (!LocalIsDark.current) colors.onMint else colors.onBrand").isNotEmpty(),
            "反事实失控：旧 R-23.13 分档形在原型 token 下的浅档只有 2.69:1，竟被判成合规——" +
                "判据没跟着调色板走，等于给浅色文字放行",
        )
        assertTrue(
            mintPrimaryInkOffenders("反事实·未登记墨", "colors.success").isNotEmpty(),
            "反事实失控：resolver 不认识的墨被静默放行（恒绿判据）",
        )
        assertTrue(
            mintPrimaryInkOffenders("反事实·R-23.13′ 单墨", "colors.onBrand").isEmpty(),
            "R-23.13′ 的按钮写法被判红——判据把修复本体当违规，方向反了",
        )
        assertTrue(
            mintPrimaryInkOffenders(
                "反事实·R-23.13′ 门控单墨",
                "if (implemented) colors.onBrand else colors.text1",
            ).isEmpty(),
            "R-23.13′ 的 StatusPill 写法被判红——判据把修复本体当违规，方向反了",
        )
    }

    /**
     * R-23.14（白胶囊 = 回退，不硬迁）：容器 `Color.White.copy(alpha = 0.85f)` 是主题不变面，
     * 前景必须保持登记的两枚静态墨；同时按 §一 复算口径压白复算两套同值的静态墨——
     * 未配置档过正文 4.5 线；已配置档原先是**批前既有债**（`GlassButton.kt:70` 同口径原话），
     * 按「≥3.0 且 <4.5」两向钉住。
     * **2026-10-27 轻松档把这个债还清了**：浅套 AiSpeech `deep` 从旧值换成 #1A7A63，压白升到 5.24，
     * 已过 4.5。按本用例自己写的规矩（「真修达正文线也要先撤台账再收本腿」），这条腿翻成正向
     * 正文 4.5 下限，`docs/verification` 的 Task 23 节与 `GlassButton.kt` 的债句同时作废。
     * 容器主题化本身归 Task 26/27（R-23.11 未授权本席迁容器）。
     */
    @Test
    fun `the theme invariant white pill keeps its registered static foregrounds`() {
        val learningSource = uiScreenSource("AiLearningScreen.kt")
        assertTrue(
            Regex("""Color\.White\.copy\(alpha = 0\.85f\)""").containsMatchIn(learningSource),
            "白胶囊容器形状变了：本用例只钉「容器不随主题 ⇒ 前景不许单迁」这一对（R-23.14）；" +
                "若 Task 26/27 已把容器主题化，须连着回退注记与本腿一起重算，不许只动一边",
        )
        val expression = Regex("""color = if \(aiConfigured\) ([^,\n]+),""").find(learningSource)?.groupValues?.get(1)?.trim()
        assertTrue(expression != null, "抓不到头卡状态徽章的 aiConfigured 前景表达式（站点搬家？改名？本腿失去对象）")
        assertFalse(
            expression!!.contains("appDomain(") || expression.contains("colors."),
            "白胶囊前景含单迁形实参（$expression）：容器深浅两套都是白底，themed 浅墨在深档就是亮墨压白（B1 第三站形状）",
        )
        assertEquals(
            "DomainColors.AiSpeech.deep else AppPalette.TextSecondary",
            expression,
            "白胶囊前景必须恰为 R-23.14 回退形状（批前形状逐字）：实际 $expression",
        )
        val configuredRatio = ratio(DomainColors.AiSpeech.deep.toArgb(), pillWhite)
        val neutralRatio = ratio(AppPalette.TextSecondary.toArgb(), pillWhite)
        assertTrue(
            neutralRatio >= 4.5,
            "未配置档静态墨压白只有 ${"%.2f".format(neutralRatio)}:1（正文档下限 4.5:1）——这枚不是登记债，是真回归",
        )
        assertTrue(
            configuredRatio >= 4.5,
            "已配置档静态墨压白只有 ${"%.2f".format(configuredRatio)}:1——原登记为「批前既有债（≥3.0 且 <4.5）」，" +
                "2026-10-27 轻松档换墨后应按正文 4.5 下限判；掉回 4.5 以下说明债又回来了，先修墨再谈台账",
        )
    }

    /**
     * R-23.15 保留面 + 评审席 M3 的原始洞：有人把头部渐变徽标的 `tint = colors.onBrand` 顺手改成
     * 白字时，批 3 的三条判据全绿（:101 只算 token×端点、:66 只看文件级子串、:124 看静态读点）。
     * 本腿把「这枚 AppGlyph 调用的实参里有 onBrand、没有 Color.White」钉死（同 m2 的单调用取样单元）。
     *
     * 2026-11-02 换锚：取样单元里的字形实参从 `AppIcon.Spark` 换成 `feature.icon`（图标去重的连带改动，
     * 见 [sharedGlyphAnchor]）。本条钉的仍是「那**一枚**头部渐变徽标调用」——数量仍恒为 1，
     * 实参仍必须带 onBrand 且不得出现 `Color.White`，判据一字未放宽。
     */
    @Test
    fun `the brand gradient badge keeps its on brand tint`() {
        val source = uiScreenSource("AiFeatureScreen.kt")
        val badgeCalls = appGlyphCall.findAll(source).map { it.value }.filter { it.contains("feature.icon") }.toList()
        assertEquals(1, badgeCalls.size, "AiFeatureScreen 头部渐变徽标的 AppGlyph(feature.icon) 调用应恰一枚，实际 ${badgeCalls.size}：$badgeCalls")
        val call = badgeCalls.single()
        assertTrue(
            call.contains("onBrand"),
            "头部渐变徽标的 tint 离开了 on-brand（2026-10-18 裁决 #3；亮度由本类 on_brand… 用例按活 token 复算）：$call",
        )
        assertFalse(
            call.contains("Color.White"),
            "头部渐变徽标长出白字（F5-03 禁令 2 的最坏形状，浅渐变上白字 ≈1.3:1）：$call",
        )
    }
}
