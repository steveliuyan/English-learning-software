package com.example.englishlearning.ui.components

/**
 * 图标族的**路径数据**：本文件只讲几何，不含颜色、不含渲染码，也不含任何位图或第三方路径。
 * 渲染由 `AppGlyph` 承担。
 * 唯一的非几何取值是 `DuotoneFillAlpha` 的两个底幅不透明度常量——它按主题取档（浅 0.42 / 深 0.30），
 * 不是字形属性，权威出处写在 `docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`。
 *
 * 许可结论：以下坐标全部由本项目自行绘制，不复制、不派生自任何第三方图标集、素材包或字形文件，
 * 因此不触发署名义务；登记在 `docs/third-party-notices.md` 的 `app-authored-ui-icons` 条目。
 * 若日后某一枚改为内嵌上游路径数据，必须同步那条台账与 `material-symbols-icon-paths` 的生效范围说明。
 *
 * 改任何一枚之前先读三份规格：`docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`
 * （枚数、顺序、用途与豁免表都在那里钉着）、`docs/decisions/2026-10-19-ui-redesign-icon-illustration-layer.md`
 * （第三层 `accentPaths` 的分工、渲染顺序与两档入口容器口径）与
 * `docs/design/2026-10-17-ui-redesign-mockups.html` §6.1/§6.2（定稿字形）。
 * 增删字形或改动豁免表属范围变化：先改决策文档的清单，再改下面的枚举，最后才动这里——
 * `AppIconsStructureContractTest` 会把两处不一致判红。
 *
 * 度量基准：24×24 画布；视觉描边重量 2px；圆头与圆角连接（`StrokeCap.Round` / `StrokeJoin.Round`
 * 由渲染器统一给，故本文件不写任何画笔参数）；关键几何留在 4–20 安全区内——这套网格习惯由本文件自身
 * 与渲染方 [AppGlyph] 共同成立（消费方一律经 `AppGlyph` 取形，没有任何一方自带私有字形），
 * 不依赖任何已删除的历史绘制函数。仅参考 Material Symbols 的 24dp 网格与 2px 重量，未内嵌其路径数据。
 *
 * 圆形写作 `CircleFill(cx, cy, r)` / `CircleStroke(cx, cy, r)` 两枚标记串：都由 `AppGlyph` 解释，
 * 几何同源（过上下极点的两段圆弧），差别只在所属层——`CircleFill` 只允许出现在 `fillPaths` 与
 * `accentPaths`，绝不允许出现在 `strokePaths`；`CircleStroke` 反之，只允许出现在 `strokePaths`。
 * 这条分层的理由是真机缺陷（裁决 22-1）：`StrokeOnly` 档只画描边层，识别特征（点、旋钮、圆章）
 * 若只写在填充层，行内徽标与弱档入口上就整层蒸发；凡识别特征只在 `fillPaths` 的字形，必须同时
 * 补一条 `strokePaths`。视觉稿里对应的形状是 `<circle cx=.. cy=.. r=..>`，转录到本文件时换成标记串。
 *
 * 三层结构（2026-10-19 起，见插画层决策记录）：
 *   - `fillPaths` 半透明底幅：表达体积，按主题取 `DuotoneFillAlpha`；
 *   - `accentPaths` **不透明**强调：表达「画上去的那几笔」——书脊、页内文字、镜头高光、图表柱、灯芯……
 *     25 枚功能入口字形必须有，5 枚豁免字形与 `Play`/`Pause`/`Stop` 必须为空；
 *   - `strokePaths` 描边骨架：识别特征的唯一承重层（裁决 22-1 不许放松）。
 *   渲染顺序固定「底幅 → 强调 → 骨架」：强调压在底幅上才有颜料叠色感，骨架压在最上面才不会被色块吃掉轮廓。
 *
 * 逐枚清单（用途（落点）｜三层，逐枚转录自决策文档的清单表；名字与顺序即 `AppIcon` 枚举的顺序）
 *   1. `Back` — 二级层与浮层页头返回键（`AppScreenHeader`）｜豁免（描边only）
 *   2. `ChevronRight` — 入口行/词行尾部「›」指示（`EntryRow`）｜豁免（描边only）
 *   3. `ChevronDown` — 展开/折叠指示（AI 主页折叠行、生成前参数）｜豁免（描边only）
 *   4. `Close` — 对话框、搜索框与浮层关闭键｜豁免（描边only）
 *   5. `Check` — 完成与成功回显对勾（`InlineNoticeKind.Success` 行）｜豁免（描边only）
 *   6. `Search` — 查词入口与全局搜索框（§6.1「查词」；词汇域三入口）｜三层
 *   7. `Book` — 底导「学习」tab 的合上的书（造型系本轮自绘，与现状 tab 的合书意象同义）｜三层
 *   8. `BookOpen` — 摊开的书，词书/阅读页头（§6.1「词书」双页形）｜三层
 *   9. `List` — 列表与任务条目行（今日计划入口行组、词表行）｜三层
 *   10. `Bookmark` — 收藏（§6.1「收藏」；「我的词」收藏段）｜三层
 *   11. `Star` — 行内/入口通用（批次计划未钉站点，用前先补决策文档清单）｜三层
 *   12. `Clock` — 时刻与提醒行（§6.1「提醒」即时钟形；到期时间显示）｜三层
 *   13. `Chart` — 统计与学习记录（§6.1「学习记录」折线形；打卡页）｜三层
 *   14. `Spark` — AI 功能与生成入口（底导「AI」tab）｜三层
 *   15. `Speaker` — 发音药片（§6.1「发音」；`SpeakerButton`）｜三层
 *   16. `Play` — 播放控件起播（发音/音频播放）｜三层
 *   17. `Pause` — 播放控件暂停（`SpeakerState.Paused` 对应形）｜三层
 *   18. `Stop` — 播放控件停止（「退出即停播」链路）｜三层
 *   19. `Mic` — 语音输入与「听/讲」类 AI 功能入口｜三层
 *   20. `Translate` — 翻译功能（文章翻译、拍照翻译译文）｜三层
 *   21. `Camera` — 拍照翻译取图入口（AI 七功能「拍」）｜三层
 *   22. `Image` — 图像类 AI 功能（`ImageStudioScreen`，七功能「画」）｜三层
 *   23. `Document` — 文本类 AI 功能与生成结果行（七功能「文」）｜三层
 *   24. `Newspaper` — 底导「阅读」tab 与今日文章｜三层
 *   25. `Gear` — 底导「设置」tab 与设置域入口｜三层
 *   26. `Person` — 资料卡（裁决②：资料卡不再显示今日计划数字）｜三层
 *   27. `Info` — 关于/许可行（§6.1）与 `ScreenUnavailable` 默认图标｜三层
 *   28. `Bell` — 行内/入口通用（§6.1 提醒行取的是时钟形，本枚落点未钉屏）｜三层
 *   29. `Shield` — 认证备份入口（§6.1「认证备份」盾形；备份与恢复子页）｜三层
 *   30. `Warning` — 警示与破坏性操作行（`InlineNoticeKind.Error`、删除确认）｜三层
 *   31. `Trash` — 删除动作（§6.1「删除」；删词书/清空学习数据）｜三层
 *   32. `Upload` — 导入口（§6.1「导入」箭头向上形）｜三层
 *   33. `Download` — 导出入口（§6.1「导出」箭头向下形）｜三层
 *
 * 三层结构豁免表（`StrokeOnlyIcons`，裁决 4）：`Back`、`ChevronRight`、`ChevronDown`、`Close`、`Check`
 * 是开放折线，没有可闭合的内部区域，强行加填充层等于把描边变实心（正是选型要避开的「两族字形」）；
 * 这五枚只作描边骨架，且不得进入 `EntryIconBadge` 一族的域渐变入口容器（页头 `.back` 的 surface
 * 容器除外——它是稿 `mockups.html:119-122` 的另一种容器：surface 底 + 1dp outlineSoft + `text1` 描边字形，
 * 不走 `EntryIconBadge` 开头的准入 `require`），只作行内/尾部指示。禁令的执行点就是那条 `require`；
 * 别把这句读成「任何 ≥40dp 容器都不许豁免枚进」，那会把页头返回位「修」成运行时报错。
 *
 * 强调层豁免（`AccentlessIcons`）：除上表五枚外，`Play`/`Pause`/`Stop` 也不建强调层——它们的实心感
 * 已由 `fillPaths` 承担，再压一层同色实心只是重复描一遍，没有新信息。
 */

/**
 * 一枚图标的三层几何：[strokePaths] 是描边骨架层（每枚至少一条），[fillPaths] 是内部半透明底幅
 * （除豁免表外每枚至少一条），[accentPaths] 是不透明强调层（功能入口字形至少一条，见 [AccentlessIcons]）。
 * 合法形状：24×24 画布的 SVG path data 串；[strokePaths] 另接受
 * `CircleStroke(cx, cy, r)`，[fillPaths] 与 [accentPaths] 另接受 `CircleFill(cx, cy, r)`（标记按层分流，互不越层）。
 * 本类不含也不接受任何不透明度取值：底幅透明度是主题属性，取值见 [DuotoneFillAlpha]；强调层恒为不透明。
 */
data class AppIconSpec(
    val name: String,
    val strokePaths: List<String>,
    val fillPaths: List<String>,
    val accentPaths: List<String> = emptyList(),
)

/**
 * 两层结构的底幅不透明度。这是主题属性而非字形属性，所以不放在 [AppIconSpec] 上
 * （逐枚各写一份就是第二套真值）。两个取值转录视觉稿 §6.2 的 CSS：
 * 浅色 `.t-b .ux-f` = `rgba(255,255,255,.42)`，深色 `html[data-theme="dark"] .t-b .ux-f` = `rgba(6,35,26,.30)`。
 * 权威出处（含稿内行号）钉在 `docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`；本注释不写 `文件:行号`，
 * 行号随文件漂移而注释仍看似权威。渲染器按 `LocalIsDark` 调 [forTheme] 取档。
 */
object DuotoneFillAlpha {
    const val Light = 0.42f
    const val Dark = 0.30f
    fun forTheme(isDark: Boolean): Float = if (isDark) Dark else Light
}

/** 枚数、名字与顺序都是决策文档的钉值面；改动等于改产品范围，先改那份文档。 */
enum class AppIcon { Back, ChevronRight, ChevronDown, Close, Check, Search, Book, BookOpen, List, Bookmark, Star, Clock, Chart, Spark, Speaker, Play, Pause, Stop, Mic, Translate, Camera, Image, Document, Newspaper, Gear, Person, Info, Bell, Shield, Warning, Trash, Upload, Download }

/** 三层结构豁免表：这五枚不建填充层也不建强调层（见文件头「三层结构豁免表」段与决策文档裁决 4）。 */
val StrokeOnlyIcons: Set<AppIcon> = setOf(AppIcon.Back, AppIcon.ChevronRight, AppIcon.ChevronDown, AppIcon.Close, AppIcon.Check)

/**
 * 强调层豁免（2026-10-19）：传输控件的实心感已由底幅承担，再叠一层同色实心是重复。
 * 豁免表之外、又不在此表里的字形，都必须带至少一条 `accentPaths`——判据在
 * `AppIconsStructureContractTest` 的「强调层双向契约」用例里，本表是它读的那份真值。
 */
val AccentlessIcons: Set<AppIcon> = StrokeOnlyIcons + setOf(AppIcon.Play, AppIcon.Pause, AppIcon.Stop)

object AppIcons {
    /**
     * 逐枚路径数据。每枚的来历写在它前面一行：
     *  - 「转录 §<节号>」= 视觉稿那一节已经画出的几何，坐标逐字照抄（§5 共享组件 banner、§6.1 骨架网格、
     *    §6.2 sprite 定稿形、§7 关键屏）；
     *  - 「原创自绘（B 案度量）」= 稿中未逐条给出、按文件头度量补齐的本项目自绘几何。
     * 同一子句里不得混写两种标记；一枚的外框与内幅分写两子句是允许的（各自来历不同）。
     */
    private val specs: List<AppIconSpec> = listOf(
        // Back：转录 §6.1 骨架层（开放折线，豁免枚）
        AppIconSpec(name = "Back", strokePaths = listOf("M15 5l-7 7 7 7"), fillPaths = emptyList()),
        // ChevronRight：原创自绘（B 案度量）——端点对称的开口折线，与 Back 同一折角半径
        AppIconSpec(name = "ChevronRight", strokePaths = listOf("M9 5l7 7-7 7"), fillPaths = emptyList()),
        // ChevronDown：原创自绘（B 案度量）——ChevronRight 旋转 90° 的同尺寸折线
        AppIconSpec(name = "ChevronDown", strokePaths = listOf("M5 9l7 7 7-7"), fillPaths = emptyList()),
        // Close：原创自绘（B 案度量）——两条等长对角线，端点在 6–18 区间对称
        AppIconSpec(name = "Close", strokePaths = listOf("M6 6l12 12", "M18 6L6 18"), fillPaths = emptyList()),
        // Check：原创自绘（B 案度量）——短长臂对角的对勾，17dp 行内仍可辨
        AppIconSpec(name = "Check", strokePaths = listOf("M5 12l5 5 9-9"), fillPaths = emptyList()),
        // Search：手柄折线、圆环与圆形底幅转录 §6.2（视觉稿 `<circle cx=11 cy=11 r=6>` 写成两段圆弧 + 标记串）；
        // 镜心高光与手柄厚块原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "Search",
            strokePaths = listOf("M11 5a6 6 0 010 12a6 6 0 010-12", "M20 20l-4.6-4.6"),
            fillPaths = listOf("CircleFill(11, 11, 6)"),
            accentPaths = listOf("CircleFill(11, 11, 2.4)", "M16.6 15.4l2.8 2.8-1.4 1.4-2.8-2.8z"),
        ),
        // Book：书壳与书脊原创自绘（B 案度量）；底幅层左页半幅转录 §6.2；强调层书脊厚线与书签带
        // 原创自绘（B 案度量）。2026-10-30 起界面由位图插画呈现（`AppIllustrations`），矢量仅作兜底，
        // 几何停在重绘前版本（2026-10-29 的样板已随矢量路线一并退役）。
        AppIconSpec(
            name = "Book",
            strokePaths = listOf(
                "M6.5 5h11a2 2 0 012 2v11a2 2 0 01-2 2h-11a2 2 0 01-2-2V7a2 2 0 012-2z",
                "M12 5v14",
            ),
            fillPaths = listOf("M5.4 6.4h6v11.4H5.4z"),
            accentPaths = listOf("M11.2 5.6h1.6v12.8h-1.6z", "M14 5.6h3v5.4l-1.5-1.2-1.5 1.2z"),
        ),
        // BookOpen：骨架层两页转录 §6.2 双页形（左 8 段 / 右 8 段、中线 x=12，对称重绘：
        // 旧版右页 `h-4` 起笔，两页不等宽，真机上读成「一厚一薄」）；底幅只给左页内幅（B 案度量）；
        // 书脊厚线 + 左页两行文字原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "BookOpen",
            strokePaths = listOf("M4 5h6.5a1.5 1.5 0 011.5 1.5V19a3 3 0 00-3-2H4z", "M20 5h-6.5a1.5 1.5 0 00-1.5 1.5V19a3 3 0 013-2H20z"),
            fillPaths = listOf("M5 6h5.5a1 1 0 011 1v10.4a3 3 0 00-2.6-1.4H5z"),
            accentPaths = listOf("M11.2 6.6h1.6v11.4h-1.6z", "M5.2 7.8h4.2v1.6H5.2z", "M5.2 10.6h3.2v1.6H5.2z"),
        ),
        // List：原创自绘（B 案度量）——三条文字线 + 三枚圆点条目。圆点必须留在描边层（裁决 22-1）：
        // 行内与弱档入口只画描边，旧版把圆点写成填充方块，StrokeOnly 下退化成三条横线。
        // 强调层给两段实心字块，行内 22dp 下读成「有内容的列表」而不是三根线。
        AppIconSpec(
            name = "List",
            strokePaths = listOf(
                "M8 6h12", "M8 12h12", "M8 18h12",
                "CircleStroke(5.2, 6, 0.5)", "CircleStroke(5.2, 12, 0.5)", "CircleStroke(5.2, 18, 0.5)",
            ),
            fillPaths = listOf("CircleFill(5.2, 6, 1.2)", "CircleFill(5.2, 12, 1.2)", "CircleFill(5.2, 18, 1.2)"),
            accentPaths = listOf("M8 9.2h6.4v1.7H8z", "M8 15.2h4.6v1.7H8z"),
        ),
        // Bookmark：骨架层转录 §6.1；左半内幅原创自绘（B 案度量）；书签横记原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "Bookmark",
            strokePaths = listOf("M6 4h12v16l-6-3-6 3z"),
            fillPaths = listOf("M7.4 5.4h4.6v11.2l-4.6 2.3z"),
            accentPaths = listOf("M8.4 7h7.2v1.8H8.4z"),
        ),
        // Star：骨架层与底幅层同串转录 §6.2（i-star-o 与 i-star-f 是同一枚闭合折线）；两段实心刻记原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "Star",
            strokePaths = listOf("M6.4 4h11.2v15.2L12 15.6l-5.6 3.6z"),
            fillPaths = listOf("M6.4 4h11.2v15.2L12 15.6l-5.6 3.6z"),
            accentPaths = listOf("M9.4 8.4h5.2v1.8H9.4z", "M10.8 11.8h2.4v1.8h-2.4z"),
        ),
        // Clock：指针与圆环转录 §6.2（视觉稿 `<circle cx=12 cy=12 r=8>` 写成两段圆弧）；
        // 轴心与指针厚块原创自绘（B 案度量，强调层）。2026-10-30 起界面由位图插画呈现
        // （`AppIllustrations`），矢量仅作兜底，几何停在重绘前版本。
        AppIconSpec(
            name = "Clock",
            strokePaths = listOf("M12 4a8 8 0 010 16a8 8 0 010-16", "M12 7.6V12l3.2 2"),
            fillPaths = listOf("CircleFill(12, 12, 8)"),
            accentPaths = listOf(
                "CircleFill(12, 12, 1.7)",
                "M12.4 11.2l3 2-1 1.4-3-2z",
            ),
        ),
        // Chart：折线与坐标轴转录 §6.1；折线下方面积原创自绘（B 案度量）；三根实心柱原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "Chart",
            strokePaths = listOf("M4 18l5-6 4 3 6-8", "M4 6v12h16"),
            fillPaths = listOf("M4 18l5-6 4 3 6-8v11z"),
            accentPaths = listOf("M6 15h2.6v3H6z", "M10 12h2.6v6H10z", "M14 8h2.6v10H14z"),
        ),
        // Spark：主星芒与伴星转录 §6.2（伴星端点到 21.7，是视觉稿原几何，未改写坐标）；伴星实心与主星芒核心原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "Spark",
            strokePaths = listOf(
                "M11 3.5l1.8 4.5 4.5 1.8-4.5 1.8L11 16.1l-1.8-4.5L4.7 9.8l4.5-1.8z",
                "M18.6 14.6l.9 2.2 2.2.9-2.2.9-.9 2.2-.9-2.2-2.2-.9 2.2-.9z",
            ),
            fillPaths = listOf("M11 3.5l1.8 4.5 4.5 1.8-4.5 1.8L11 16.1l-1.8-4.5L4.7 9.8l4.5-1.8z"),
            accentPaths = listOf(
                "M18.6 14.6l.9 2.2 2.2.9-2.2.9-.9 2.2-.9-2.2-2.2-.9 2.2-.9z",
                "CircleFill(11, 9.8, 1.8)",
            ),
        ),
        // Speaker：喇叭体与两道波弧转录 §6.2；喇叭口实心块与声源圆点原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "Speaker",
            strokePaths = listOf("M5 10v4h3l4 3V7L8 10z", "M16 9a4 4 0 010 6", "M18.6 6.4a7.6 7.6 0 010 11.2"),
            fillPaths = listOf("M5 10v4h3l4 3V7L8 10z"),
            accentPaths = listOf("M5.4 10v4h3.2v-4z", "CircleFill(16.6, 12, 1)"),
        ),
        // Play：原创自绘（B 案度量）——三角形重心右移以补视觉配重，内幅只作层次暗示（传输控件不建强调层）
        AppIconSpec(
            name = "Play",
            strokePaths = listOf("M8 5.5l10.5 6.5L8 18.5z"),
            fillPaths = listOf("M9.8 8.2l6 3.8-6 3.8z"),
        ),
        // Pause：原创自绘（B 案度量）——两条竖线骨架 + 同位实心条（传输控件不建强调层）
        AppIconSpec(
            name = "Pause",
            strokePaths = listOf("M9 5v14", "M15 5v14"),
            fillPaths = listOf("M7.6 5h2.8v14H7.6z", "M13.6 5h2.8v14h-2.8z"),
        ),
        // Stop：原创自绘（B 案度量）——方框骨架 + 内缩实心块（传输控件不建强调层）
        AppIconSpec(
            name = "Stop",
            strokePaths = listOf("M6 6h12v12H6z"),
            fillPaths = listOf("M8 8h8v8H8z"),
        ),
        // Mic：原创自绘（B 案度量）——胶囊振膜 + 托架半环 + 立杆与底座；网罩实心块与灯点原创自绘（强调层）
        AppIconSpec(
            name = "Mic",
            strokePaths = listOf(
                "M12 4a3.5 3.5 0 013.5 3.5v3a3.5 3.5 0 01-7 0v-3a3.5 3.5 0 013.5-3.5z",
                "M8 12a4 4 0 008 0",
                "M12 16v3",
                "M9 20h6",
            ),
            fillPaths = listOf("M12 5.5a2 2 0 012 2v3a2 2 0 01-4 0v-3a2 2 0 012-2z"),
            accentPaths = listOf("M10.4 12.4h3.2v2.4h-3.2z", "CircleFill(12, 7.4, 1.5)"),
        ),
        // Translate：原创自绘（B 案度量）——大小两枚 A 字骨（拉丁/目标语），小字内幅与横杠作层次（强调层）
        AppIconSpec(
            name = "Translate",
            strokePaths = listOf("M4 13l3.5-8 3.5 8", "M5.4 10h4.2", "M13 20l3.5-6.5 3.5 6.5", "M14.6 17h3.8"),
            fillPaths = listOf("M16.5 14.5l1.75 4h-3.5z"),
            accentPaths = listOf("M16.5 14.5l1.75 4h-3.5z", "M5.7 10.6h3.4v1.4H5.7z"),
        ),
        // Camera：机身与镜头圆转录 §7 屏⑥（视觉稿 `<circle cx=12 cy=13 r=3.2>` 写成两段圆弧 + 标记串）；
        // 镜心与闪光块原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "Camera",
            strokePaths = listOf("M4 8h4l2-2h4l2 2h4v11H4z", "M12 9.8a3.2 3.2 0 010 6.4a3.2 3.2 0 010-6.4"),
            fillPaths = listOf("CircleFill(12, 13, 3.2)"),
            accentPaths = listOf("CircleFill(12, 13, 1.9)", "M15.8 9.4h2.2v2.2h-2.2z"),
        ),
        // Image：原创自绘（B 案度量）——画框 + 山形，日光圆作底幅层；日光圆同时描边一份（裁决 22-1）；
        // 山体实心块与日光实心圆原创自绘（强调层）
        AppIconSpec(
            name = "Image",
            strokePaths = listOf("M4 5h16v14H4z", "M7 16l3.5-4.5 3 3.5 3-4 2.5 3", "CircleStroke(9, 9, 2)"),
            fillPaths = listOf("CircleFill(9, 9, 2)"),
            accentPaths = listOf("CircleFill(9, 9, 2)", "M7 16.6l3.4-4.4 2.8 3.2 2.2-2.6 1.9 2.3v1.5H7z"),
        ),
        // Document：原创自绘（B 案度量）——折角页 + 折线，正文区作底幅层；正文框同时进描边层（裁决 22-1）；
        // 折角实心三角与两行字块原创自绘（强调层）
        AppIconSpec(
            name = "Document",
            strokePaths = listOf("M6 4h8l4 4v12H6z", "M14 4v4h4", "M7.4 10h9.2v8.6H7.4z"),
            fillPaths = listOf("M7.4 10h9.2v8.6H7.4z"),
            accentPaths = listOf("M14 4l4 4h-4z", "M9 12h6v1.6H9z", "M9 15h4.4v1.6H9z"),
        ),
        // Newspaper：原创自绘（B 案度量）——报面 + 两行正文，报头横带作底幅层；报头横带同时进描边层（裁决 22-1）；
        // 报头实心带与图片块原创自绘（强调层）
        AppIconSpec(
            name = "Newspaper",
            strokePaths = listOf("M4 5h16v14H4z", "M7 12h10", "M7 15.5h6", "M5.4 6.4h13.2v3H5.4z"),
            fillPaths = listOf("M5.4 6.4h13.2v3H5.4z"),
            accentPaths = listOf("M5.4 6.4h13.2v3H5.4z", "M12.6 11.4h6v5.2h-6z"),
        ),
        // Gear：滑杆三条转录 §6.1「设置」（视觉稿 `<circle cx=17 cy=17 r=2.5>` 写成标记串）；
        // 旋钮芯原创自绘（B 案度量，强调层）。旋钮必须留在描边层（裁决 22-1）。
        // 2026-10-30 起界面由位图插画呈现（`AppIllustrations`），矢量仅作兜底，几何停在重绘前版本。
        AppIconSpec(
            name = "Gear",
            strokePaths = listOf(
                "M4 7h16", "M4 12h16", "M4 17h10",
                "CircleStroke(17, 17, 2.5)",
            ),
            fillPaths = listOf("CircleFill(17, 17, 2.5)"),
            accentPaths = listOf("CircleFill(17, 17, 1.4)"),
        ),
        // Person：原创自绘（B 案度量）——头部圆环 + 肩线半环，头圆作底幅层；头芯与肩块原创自绘（强调层）
        AppIconSpec(
            name = "Person",
            strokePaths = listOf("M12 4a4 4 0 010 8a4 4 0 010-8", "M4.5 20a7.5 7.5 0 0115 0"),
            fillPaths = listOf("CircleFill(12, 8, 4)"),
            accentPaths = listOf("CircleFill(12, 8, 2.4)", "M7.8 19.6l1.5-3h5.4l1.5 3z"),
        ),
        // Info：圆环、字干与点转录 §6.1「关于 / 许可」（视觉稿 `<circle cx=12 cy=12 r=8>` 写成两段圆弧）；
        // 字干厚块与圆芯原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "Info",
            strokePaths = listOf("M12 4a8 8 0 010 16a8 8 0 010-16", "M12 11v6", "M12 7.5v.5"),
            fillPaths = listOf("CircleFill(12, 12, 8)"),
            accentPaths = listOf("M11.2 10.8h1.6v6.2h-1.6z", "CircleFill(12, 12, 2.4)"),
        ),
        // Bell：骨架层两笔转录 §6.1；钟体内幅原创自绘（B 案度量）。钟口横沿与钟舌同时进描边层（裁决 22-1）。
        // 钟口实心带与钟舌实心圆原创自绘（B 案度量，强调层）。
        AppIconSpec(
            name = "Bell",
            strokePaths = listOf(
                "M5 12a7 7 0 0114 0",
                "M9 15h6",
                "M5 12v2.6h14V12",
                "M10.5 17.6a1.5 1.5 0 003 0",
            ),
            fillPaths = listOf("M6 12a6 6 0 0112 0v3H6z"),
            accentPaths = listOf("M5.2 13.6h13.6v1.8H5.2z", "CircleFill(12, 17.6, 1.5)"),
        ),
        // Shield：盾体与对勾转录 §6.2（骨架层与底幅层同串）；对勾厚块原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "Shield",
            strokePaths = listOf("M12 4l7 3v5c0 4.2-3 7.3-7 8.2-4-.9-7-4-7-8.2V7z", "M9.2 12.2l2 2 4-4.2"),
            fillPaths = listOf("M12 4l7 3v5c0 4.2-3 7.3-7 8.2-4-.9-7-4-7-8.2V7z"),
            accentPaths = listOf("M10.4 11.4l1.6 1.6 3.2-3.4 1.2 1.2-4.4 4.6-2.8-2.8z"),
        ),
        // Warning：三角与感叹号转录 §5（banner warn 用图）；三角内幅原创自绘（B 案度量）；叹号厚块与点原创自绘（强调层）
        AppIconSpec(
            name = "Warning",
            strokePaths = listOf("M12 4l8 14H4z", "M12 9v4", "M12 15.5v.5"),
            fillPaths = listOf("M12 7l5.4 9.4H6.6z"),
            accentPaths = listOf("M11.1 9h1.8v4.6h-1.8z", "CircleFill(12, 15.6, 1.1)"),
        ),
        // Trash：骨架层三笔转录 §6.1 删除形；桶身内幅转录 §6.2（两稿几何本就近似，坐标逐字未改）；
        // 桶口实心带与桶身块原创自绘（B 案度量，强调层）
        AppIconSpec(
            name = "Trash",
            strokePaths = listOf("M5 7h14", "M9 7V5h6v2", "M7 7l1 12h8l1-12"),
            fillPaths = listOf("M7.6 8h8.8l-.9 11.2H8.5z"),
            accentPaths = listOf("M5.4 6.2h13.2v1.8H5.4z", "M8.8 9.6h6.4l-.6 8.4H9.4z"),
        ),
        // Upload：箭头与顶栏三笔转录 §6.1「导入」；箭头实心头原创自绘（B 案度量）；托盘实心条原创自绘（强调层）
        AppIconSpec(
            name = "Upload",
            strokePaths = listOf("M12 20V9", "M8 13l4-4 4 4", "M5 5h14"),
            fillPaths = listOf("M12 9.6l3.4 3.4H8.6z"),
            accentPaths = listOf("M4.6 4.2h14.8v1.8H4.6z"),
        ),
        // Download：箭头与底栏三笔转录 §6.1「导出」；箭头实心头原创自绘（B 案度量）；托盘实心条原创自绘（强调层）
        AppIconSpec(
            name = "Download",
            strokePaths = listOf("M12 4v9", "M8 10l4 4 4-4", "M5 19h14"),
            fillPaths = listOf("M12 13.4l-3.4-3.4h6.8z"),
            accentPaths = listOf("M4.6 18.2h14.8v1.8H4.6z"),
        ),
    )

    val all: Map<AppIcon, AppIconSpec> = specs.associateBy { AppIcon.valueOf(it.name) }
}

/** 读取本枚的路径数据；枚数与名字由 `AppIcon` 枚举钉住，缺枚在这里就是 `NoSuchElementException`。 */
val AppIcon.spec: AppIconSpec
    get() = AppIcons.all.getValue(this)
