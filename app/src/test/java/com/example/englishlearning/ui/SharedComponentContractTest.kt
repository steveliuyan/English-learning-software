package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 批 0 共享组件对照原型的源码契约（计划 `2026-10-29-ui-prototype-alignment.md` Step 1）。
 *
 * 判据只钉**承重的那几个值**：几何、底色/前景 token、状态分叉、可选交互的语义角色。
 * 组件正文里的注释先剥掉再匹配（KDoc 会合法复述同一批 token 名），一律按符号取源、不锚行号。
 *
 * 原型出处（`assets/app.css` / `assets/app.js`，只读）：
 *  - `.badge` / `.badge-sm` / `.badge-lg`：44/32/64dp，圆角 14/10/20，底 `tint`，内描边 5%/9% 白；
 *  - `.card` / `.card-soft` / `.card-quiet`：底 `surface`/`tint`/透明，前两者与 quiet 都 1dp `line` 描边，
 *    圆角 20、内距 16；
 *  - `.strong-card`：底 `strong`、墨 `on-strong`、圆角 26、内距 20、右上径向薄荷光；
 *  - `.row`：上下 12、无横向内距、间隙 12、标题 15/550、副行 12/ink-3、尾值 13/ink-3、顶线 line-soft；
 *  - `.seg`：3 内距 + 2 间隙 + 药片 + surface-2 底 + line 描边，项 34 高（无障碍下限改 48，见 KDoc）、
 *    选中 surface/ink（solid 档 strong/on-strong）；
 *  - `.chip`：30 高、药片、line 描边、surface 底、ink-2 墨；选中 brand-tint 底 + brand-deep 墨；
 *  - `.switch`：46×28，拇指 20、内缩 3、行程 18，开 brand-deep、关 surface-2；
 *  - `.pick`：22dp 标记、圆角 7（radio 50%）、1.5dp 边、选中 brand-deep + 白勾；
 *  - `.field` + `.input`：标签 12/600 ink-2、内距 12/14、圆角 14、line 边、focus brand-deep + 3dp 30% 薄荷环；
 *  - `.notice`：内距 12/16、圆角 14、无描边、底 tint、墨 ink-2；
 *  - `.empty`：贴纸 84 圆角 26、上下 32 / 左右 16、间隙 12、标题 15/600、说明 12/ink-3；
 *  - `.plan-bar`：高 10、间隙 2、药片、轨道 on-strong 14%、新学段 brand、复习段 on-strong 62%。
 */
class SharedComponentContractTest {

    private fun source(name: String): String = stripComments(uiComponentSource(name))

    /** 原型 `app.js` 的 STICKERS 清单（32 枚，顺序无关）。 */
    private val prototypeStickers = setOf(
        "book", "books", "clock", "document", "microphone", "speaker", "spark", "chart",
        "camera", "shield", "upload", "download", "search", "list", "bookmark", "star",
        "translate", "image", "newspaper", "gear", "person", "info", "bell", "warning",
        "trash", "play", "pause", "stop", "cloud", "qr", "edit", "target",
    )

    @Test
    fun `the sticker catalog mirrors the prototype set and every asset is bundled`() {
        val source = stripComments(uiComponentSource("StickerBadge.kt"))
        val entries = Regex("""(?m)^\s{4}([A-Z][A-Za-z]*)\(\s*"([a-z]+)"""")
            .findAll(source.substringAfter("enum class Sticker(").substringBefore("}"))
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
        assertEquals(
            prototypeStickers,
            entries.values.toSet(),
            "贴纸清单必须与原型 STICKERS 32 枚逐字一致（少一枚 = 画不出来，多一枚 = 原型没有的资源）",
        )
        entries.values.forEach { asset ->
            val file = File("src/main/res/drawable-nodpi/sticker_$asset.png")
            assertTrue(
                file.isFile,
                "缺少贴纸位图 $file —— 原型 assets/icons/$asset.png 必须拷进 drawable-nodpi，不联网取图",
            )
            assertTrue(
                "R.drawable.sticker_$asset" in source,
                "Sticker.$asset 必须引用 R.drawable.sticker_$asset（资源名逐枚对位，不许统一回退到一枚默认图）",
            )
        }
    }

    @Test
    fun `sticker badge matches the prototype three sizes and inset ring`() {
        val source = stripComments(uiComponentSource("StickerBadge.kt"))
        listOf(
            "Small(32.dp, 10.dp)",
            "Medium(44.dp, 14.dp)",
            "Large(64.dp, 20.dp)",
        ).forEach { entry ->
            assertTrue(entry in source, "贴纸徽标尺寸档必须逐字为 $entry（原型 .badge / .badge-sm / .badge-lg）")
        }
        assertTrue("colors.tint" in source, "徽标底必须是 tint token（原型 .badge background: var(--tint)）")
        assertTrue("ContentScale.Crop" in source, "贴纸必须铺满裁切（原型 .badge img object-fit: cover）")
        assertTrue("Stroke(width = 1.dp.toPx())" in source, "内描边必须 1dp（原型 box-shadow: inset 0 0 0 1px）")
        assertTrue(
            "colors.text1.copy(alpha = 0.05f)" in source,
            "浅套内描边必须是 rgba(21,33,28,.05) 的 token 表达（ink-1 的 5% alpha），不许写裸十六进制",
        )
        assertTrue(
            "Color.White.copy(alpha = 0.09f)" in source,
            "深套内描边必须是 rgba(255,255,255,.09) 的 token 表达（白 9% alpha）",
        )
    }

    @Test
    fun `the card family mirrors the four prototype surfaces`() {
        val containers = stripComments(uiComponentSource("Containers.kt"))

        val appCard = functionBody(containers, "fun AppCard(")
        assertTrue(appCard.isNotEmpty(), "AppCard 必须存在（原型 .card 是 54 处的主卡）")
        assertTrue("background(colors.surface)" in appCard, "AppCard 底必须是 surface")
        assertTrue("border(1.dp, colors.outline, AppShape.Card)" in appCard, "AppCard 必须 1dp line 描边")
        assertTrue("PaddingValues(16.dp)" in appCard, "AppCard 内距必须是 16dp（原型 .card padding s4）")

        val soft = functionBody(containers, "fun SoftCard(")
        assertTrue("background(colors.tint)" in soft, "SoftCard 底必须是 tint（原型 .card-soft）")
        assertTrue("border" !in soft, "SoftCard 不描边（原型 .card-soft 的 border 是 transparent）")
        assertTrue("PaddingValues(16.dp)" in soft, "SoftCard 内距必须是 16dp")

        val quiet = functionBody(containers, "fun QuietCard(")
        assertTrue(quiet.isNotEmpty(), "QuietCard 必须存在（原型 .card-quiet 是 7 处无底卡）")
        assertTrue("border(1.dp, colors.outline, AppShape.Card)" in quiet, "QuietCard 必须 1dp line 描边")
        assertTrue("background(" !in quiet, "QuietCard 不铺底（原型 .card-quiet background: transparent）")
        assertTrue("PaddingValues(16.dp)" in quiet, "QuietCard 内距必须是 16dp")

        val strong = functionBody(containers, "fun StrongCard(")
        assertTrue("RoundedCornerShape(26.dp)" in strong, "强卡圆角 26dp（原型 --r-xl）")
        assertTrue("PaddingValues(20.dp)" in strong, "强卡内距 20dp（原型 --s5）")
        assertTrue("Brush.radialGradient" in strong, "强卡右上径向薄荷光（原型 .strong-card::after）")
        assertTrue(
            "colors.strongSurface" in strong && "colors.onStrong" in strong,
            "深套强卡底/墨必须取 strongSurface + onStrong（原型 --strong / --on-strong）",
        )
        assertTrue(
            "colors.tint2" in strong && "colors.text1" in strong,
            "浅套强卡维持 2026-10-31 用户评审的 tint2/text1 落点（原型浅套为深底；该偏离须在报告中点名）",
        )
    }

    @Test
    fun `the bare row mirrors the prototype row metrics`() {
        val body = functionBody(stripComments(uiComponentSource("Containers.kt")), "fun BareRow(")
        assertTrue(body.isNotEmpty(), "取不到 BareRow 本体，判据空转")
        assertTrue("heightIn(min = 48.dp)" in body, "裸行触控下限 48dp（spec §2.3 无障碍下限）")
        assertTrue(
            "padding(vertical = 12.dp)" in body && "horizontal = 15.dp" !in body,
            "裸行上下 12dp、无横向内距（原型 .row padding: var(--s3) 0；横向内距归宿主卡片）",
        )
        assertTrue("spacedBy(12.dp)" in body, "行内间隙 12dp（原型 gap: var(--s3)）")
        assertTrue("fontWeight = FontWeight(550)" in body, "行标题 550 字重（原型 .row-title）")
        assertTrue("letterSpacing = (-0.075).sp" in body, "行标题 -0.005em 字距（15sp × -0.005 = -0.075sp）")
        assertTrue("AppType.Caption" in body, "副行 12sp（原型 .row-sub font-size: caption）")
        assertTrue("colors.text3" in body, "副行 / 尾值墨取 ink-3")
        assertTrue("AppType.Meta" in body, "尾值 13sp（原型 .row-value font-size: meta）")
        assertTrue("size = 18.dp" in body, "行尾箭头 18dp（原型 icon('next', 18)）")
        assertTrue(
            "hairlineTop(if (topDivider) colors.outlineSoft else null, startIndent = 0.dp)" in body,
            "分隔线必须是全宽 line-soft（原型 .row border-top 无缩进）；旧 52dp 缩进作废",
        )
        assertTrue("BareRowDividerIndent" !in body, "52dp 缩进常量不得再出现在裸行里")
    }

    @Test
    fun `the segmented control mirrors the prototype track and its accessible floor`() {
        val source = stripComments(uiComponentSource("SegmentedControl.kt"))
        assertTrue("padding(3.dp)" in source, "分段轨道内距 3dp（原型 .seg）")
        assertTrue("spacedBy(2.dp)" in source, "分段项间隙 2dp")
        assertTrue("background(colors.surface2)" in source, "轨道底 surface-2")
        assertTrue("border(1.dp, colors.outline, AppShape.Pill)" in source, "轨道 1dp line 描边 + 药片")
        assertTrue("AppShape.Pill" in source, "段项药片形")
        assertTrue(
            "heightIn(min = 48.dp)" in source,
            "段项最小高 48dp：原型 34dp 低于 F5-03 触控下限，spec §2.3 明令无障碍下限优先（报告点名偏离）",
        )
        assertTrue("colors.surface" in source && "colors.text1" in source, "选中档 surface 底 + ink 墨")
        assertTrue("colors.strongSurface" in source && "colors.onStrong" in source, "solid 档选中 strong/on-strong（原型 .seg-solid）")
        assertTrue("colors.text2" in source, "未选中墨 ink-2")
        assertTrue("selectable(" in source, "段项必须是 selectable（TalkBack 读「已选择」，不是裸 clickable）")
    }

    @Test
    fun `the chip mirrors the prototype pressed and quiet tones`() {
        val source = stripComments(uiComponentSource("AppChip.kt"))
        assertTrue("height(30.dp)" in source, "芯片本体 30 高（原型 .chip）")
        assertTrue("heightIn(min = 48.dp)" in source, "可点区域 48dp（30dp 本体包在 48dp 触控盒里）")
        assertTrue("padding(horizontal = 12.dp)" in source, "芯片左右内距 12dp（原型 0 var(--s3)）")
        assertTrue("AppShape.Pill" in source, "芯片药片形")
        assertTrue("colors.brandTint" in source && "colors.brandDeep" in source, "选中档 brand-tint 底 + brand-deep 墨")
        assertTrue("colors.surface2" in source && "colors.text3" in source, "quiet 档 surface-2 底 + ink-3 墨")
        assertTrue("colors.surface" in source && "colors.text2" in source, "默认为 surface 底 + ink-2 墨")
        assertTrue("colors.outline" in source, "默认档 1dp line 描边")
        assertTrue("toggleable(" in source, "芯片是开合筛选（aria-pressed），必须 toggleable 而不是裸 clickable")
    }

    @Test
    fun `the switch mirrors the prototype track thumb and travel`() {
        val source = stripComments(uiComponentSource("AppSwitch.kt"))
        assertTrue("46.dp" in source && "28.dp" in source, "开关轨道 46×28（原型 .switch）")
        assertTrue("20.dp" in source, "拇指 20dp")
        assertTrue("padding(3.dp)" in source, "拇指内缩 3dp")
        assertTrue("offset(x = 18.dp)" in source || "18.dp" in source, "拇指行程 18dp（原型 translateX(18px)）")
        assertTrue("colors.brandDeep" in source, "开态轨道 brand-deep")
        assertTrue("colors.surface2" in source, "关态轨道 surface-2")
        assertTrue("Role.Switch" in source, "开关语义角色必须是 Switch")
        assertTrue("toggleable(" in source, "开关必须 toggleable")
        assertTrue("AppMotion.Normal" in source && "AppMotion.EasingOut" in source, "拇指位移走 220ms 入场缓动")
    }

    @Test
    fun `the pick row mirrors the prototype mark and row metrics`() {
        val source = stripComments(uiComponentSource("PickRow.kt"))
        assertTrue("22.dp" in source, "选择标记 22dp（原型 .pick-mark）")
        assertTrue("RoundedCornerShape(7.dp)" in source, "复选标记圆角 7dp")
        assertTrue("CircleShape" in source, "单选标记圆角 50%")
        assertTrue("border(1.5.dp" in source, "标记 1.5dp 描边")
        assertTrue("colors.brandDeep" in source, "选中标记 brand-deep 底/边")
        assertTrue("Color.White" in source, "选中勾为白色（原型 .pick[aria-checked] color:#fff）")
        assertTrue("padding(vertical = 12.dp)" in source, "选择行上下 12dp（原型 .pick padding var(--s3) 0）")
        assertTrue("spacedBy(12.dp)" in source, "标记与文字间隙 12dp")
        assertTrue("heightIn(min = 48.dp)" in source, "选择行最小高 48dp（F5-03 下限；原型自然高 46dp）")
        assertTrue("selectable(" in source, "无第二动作的选择行仍走 selectable（单选/复选语义 + ToggleableState）")
        // 批 1 Task 13-A：词书行要在同一枚节点上再挂一条长按（删除），`selectable` 给不出第二个动作，
        // 故组件多出一条 `combinedClickable` 形。两条并存是**刻意的**，不是把 selectable 放宽成 clickable：
        // 角色、Selected、stateDescription 三样在两条形状上都在，TalkBack 念得出的东西一件没少。
        assertTrue("combinedClickable(" in source, "带第二动作的选择行走 combinedClickable（点按选中 + 长按删除同一个节点）")
        assertTrue("Role.RadioButton" in source, "单选角色随 radio=true 落到语义上（原型 .pick-radio）")
        assertTrue("Role.Checkbox" in source, "复选角色：非单选档仍是 Checkbox，不许统一降级成 Button")
        assertTrue("selected = checked" in source, "combinedClickable 自己不发 Selected，组件必须自己写进语义")
        assertTrue("CustomAccessibilityAction(" in source, "第二动作要登记成读屏自定义动作（F5-03：手势不是可及路径）")
        assertTrue("stateDescription = if (checked) \"已选\" else \"未选\"" in source, "选中态双值进语义，不只靠标记变色")
        assertTrue("AppType.Meta" in source, "尾部值取 `.row-value` 档（13sp / ink-3，原型 app.css:544）")
        assertTrue("trailing" in source && "onCheckedChange(!checked)" in source, "尾部值形参与「点按翻选中」都在组件里")
    }

    @Test
    fun `the field mirrors the prototype input and focus ring`() {
        val source = stripComments(uiComponentSource("AppField.kt"))
        assertTrue("BasicTextField(" in source, "字段必须是自绘输入框（原型 input 不是 M3 OutlinedTextField）")
        assertTrue("padding(horizontal = 14.dp, vertical = 12.dp)" in source, "输入内距 12/14（原型 .input）")
        assertTrue("AppShape.Button" in source, "输入框圆角 14dp（原型 --r-md）")
        assertTrue("border(1.dp" in source, "输入框 1dp 描边")
        assertTrue("0.30f" in source, "焦点环 3dp brand 30%（原型 box-shadow 0 0 0 3px brand 30%）")
        assertTrue("3.dp" in source, "焦点环宽 3dp")
        assertTrue("FocusRequester" in source || "collectIsFocusedAsState" in source, "必须跟踪焦点态才能画焦点环")
        assertTrue("AppType.Label" in source, "字段标签 12/600（原型 .field-label）")
        assertTrue("AppType.Caption" in source, "提示 / 错误 12sp（原型 .field-hint / .field-error）")
        assertTrue("colors.error" in source, "错误态描边/文案取 danger token")
    }

    @Test
    fun `the empty state mirrors the prototype sticker and copy ladder`() {
        val source = stripComments(uiComponentSource("EmptyState.kt"))
        assertTrue("84.dp" in source, "空状态贴纸 84dp（原型 .empty img）")
        assertTrue("corner = 26.dp" in source, "空状态贴纸圆角 26dp（原型 .empty img border-radius: 26px）")
        assertTrue("StickerBadge(" in source || "Sticker." in source, "空状态必须复用贴纸资源（不另起位图出口）")
        assertTrue("padding(vertical = 32.dp, horizontal = 16.dp)" in source || ("32.dp" in source && "16.dp" in source), "上下 32 / 左右 16（原型 .empty）")
        assertTrue("spacedBy(12.dp)" in source, "空状态纵向间隙 12dp")
        assertTrue("AppType.Body" in source, "标题 15sp（原型 .empty-title font-size: body）")
        assertTrue("FontWeight(600)" in source, "标题 600 字重")
        assertTrue("AppType.Caption" in source && "colors.text3" in source, "说明 12sp / ink-3（原型 .empty-body）")
    }

    @Test
    fun `the plan bar mirrors the prototype two segment progress`() {
        val source = stripComments(uiComponentSource("PlanBar.kt"))
        assertTrue("height(10.dp)" in source, "进度条高 10dp（原型 .plan-bar）")
        assertTrue("spacedBy(2.dp)" in source, "两段间隙 2dp")
        assertTrue("0.14f" in source, "轨道 on-strong 14%（原型 rgba(233,242,236,.14)）")
        assertTrue("colors.brand" in source, "新学段 brand（原型 .plan-seg > i）")
        assertTrue("0.62f" in source, "复习段 on-strong 62%（原型 .plan-seg-due）")
        assertTrue("progressBarRangeInfo" in source, "必须暴露 progressbar 语义（原型 role=progressbar + aria-valuenow）")
        assertTrue("AppMotion.Slow" in source && "AppMotion.EasingOut" in source, "段宽过渡 300ms 入场缓动")
    }

    /**
     * 按钮 = 原型 `.btn`（批 0 漏项，批 1 学习页主 CTA 对照时补上）。
     *
     * 原型出处（`assets/app.css:565`）：
     * ```
     * .btn { min-height:46px; padding:0 var(--s5); border-radius:var(--r-md);
     *        font-size:var(--fs-body); font-weight:600; letter-spacing:-.005em }
     * ```
     * 即「14 圆角 / 15px 600 / 左右 20」，**不是药片**——`--r-pill` 在原型只给导航、分段、芯片、
     * 进度条、开关这些轨道件。本仓的 `PillButton` 名字是历史 API（全仓 50+ 调用点），形状按原型走 14。
     *
     * 两处登记偏离：
     *  - `min-height` 取 **48** 而非原型 46：无障碍触控下限，spec §2.3「不得靠 padding 凑」；
     *  - 竖直内距从 0 改为 12：48 高减 15sp/24 行高后上下各 12，字形仍居中。
     */
    @Test
    fun `the button mirrors the prototype btn geometry`() {
        val source = stripComments(uiComponentSource("GlassButton.kt"))
        val body = functionBody(source, "fun PillButton(")
        assertTrue(body.isNotEmpty(), "GlassButton.kt 里取不到 `fun PillButton(` 的签名 + 函数体")
        assertTrue("shape = AppShape.Button" in body, "按钮圆角 14dp（原型 .btn → var(--r-md)）")
        assertTrue(
            "AppShape.Pill" !in body,
            "按钮不再是药片：--r-pill 在原型只给导航/分段/芯片/进度条/开关，.btn 是 14",
        )
        assertTrue("heightIn(min = 48.dp)" in body, "触控下限 48dp（原型 min-height 46，无障碍取 48）")
        assertTrue(
            "AppType.ButtonLabel" in body,
            "按钮文案 15sp/600（原型 .btn = --fs-body + font-weight 600），不是 Title 的 17sp",
        )
        assertTrue(
            "padding(horizontal = 20.dp, vertical = 12.dp)" in body,
            "左右 20dp（原型 padding 0 var(--s5)）+ 竖直 12dp 撑到 48 高",
        )
    }

    @Test
    fun `the notice mirrors the prototype banner geometry and ink`() {
        val source = stripComments(uiComponentSource("InlineNotice.kt"))
        assertTrue("padding(horizontal = 16.dp, vertical = 12.dp)" in source, "提示内距 16/12（原型 .notice padding s3 s4）")
        assertTrue("AppShape.Button" in source, "提示圆角 14dp（原型 --r-md）")
        assertTrue("border(1.dp" !in source, "提示无描边（原型 .notice 没有 border）")
        assertTrue("spacedBy(12.dp)" in source, "字形与文案间隙 12dp")
        assertTrue("size = 17.dp" in source, "字形 17dp（原型 icon(ic, 17)）")
        assertTrue("AppType.Caption" in source, "文案 12sp（原型 font-size: caption）")
        assertTrue(
            "InlineNoticeKind.Info -> NoticeInk(foreground = set.text2, backdrop = set.tint, action = set.text1)" in source,
            "默认态 = tint 底 + ink-2 墨 + text1 动作墨（原型 .notice 本体；动作墨是 R-10.1b 的对比裁决）",
        )
        assertTrue(
            "InlineNoticeKind.Error -> NoticeInk(foreground = set.error, backdrop = set.dangerTint, action = set.text1)" in source,
            "错误态 = danger-tint 底 + danger 墨（原型 .notice-error）",
        )
    }

    /**
     * 按花括号配对取 `fun X(` 的**签名 + 函数体**（源已剥注释）；取不到返回空串，让调用处各自响亮判红。
     *
     * 从签名起的理由：卡片的内距写在默认形参上（`contentPadding: PaddingValues = PaddingValues(16.dp)`），
     * 只取 `{` 之后会把这枚取值漏在窗口外，判据就成了空转。
     */
    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val open = source.indexOf('{', start)
        if (open < 0) return ""
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        return ""
    }
}
