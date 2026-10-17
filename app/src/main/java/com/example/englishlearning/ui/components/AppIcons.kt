package com.example.englishlearning.ui.components

/**
 * B 案（双色层次 duotone）图标族的**路径数据**：本文件只讲几何，不含颜色、不含渲染码，
 * 也不含任何位图或第三方路径。渲染由 Task 7 的 `AppGlyph` 承担。
 * 唯一的非几何取值是 `DuotoneFillAlpha` 的两个填充层不透明度常量——它按主题取档（浅 0.42 / 深 0.30），
 * 不是字形属性，权威出处写在 `docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`。
 *
 * 许可结论：以下坐标全部由本项目自行绘制，不复制、不派生自任何第三方图标集、素材包或字形文件，
 * 因此不触发署名义务；登记在 `docs/third-party-notices.md` 的 `app-authored-ui-icons` 条目。
 * 若日后某一枚改为内嵌上游路径数据，必须同步那条台账与 `material-symbols-icon-paths` 的生效范围说明。
 *
 * 改任何一枚之前先读两份规格：`docs/decisions/2026-10-18-ui-redesign-contrast-and-icon-layer-rulings.md`
 * （枚数、顺序、用途与两层豁免表都在那里钉着）与 `docs/design/2026-10-17-ui-redesign-mockups.html`
 * §6.1/§6.2（B 案定稿字形）。增删字形或改动豁免表属范围变化：先改决策文档的清单，再改下面的枚举，
 * 最后才动这里——`AppIconsStructureContractTest` 会把两处不一致判红。
 *
 * 度量基准：24×24 画布；视觉描边重量 2px；圆头与圆角连接（`StrokeCap.Round` / `StrokeJoin.Round`
 * 由渲染器统一给，故本文件不写任何画笔参数）；优先无填充，填充只作层次暗示；关键几何留在 4–20
 * 安全区内，与 `AppBottomBar.kt` 的 `drawTabGlyph` 同一 24 网格习惯。仅参考 Material Symbols 的
 * 24dp 网格与 2px 重量，未内嵌其路径数据。
 *
 * 圆形填充写作 `CircleFill(cx, cy, r)` 标记串：该标记由 `AppGlyph` 渲染器（Task 7）解释，
 * 圆心的填充不走 SVG path 语法，所以它只允许出现在 `fillPaths`，绝不允许出现在 `strokePaths`；
 * 视觉稿里对应的形状是 `<circle cx=.. cy=.. r=..>`，转录到本文件时换成标记串。
 *
 * 逐枚清单（用途（落点）｜两层，逐枚转录自决策文档的清单表；名字与顺序即 `AppIcon` 枚举的顺序）
 *   1. `Back` — 二级层与浮层页头返回键（Task 9 `AppScreenHeader`）｜豁免（描边only）
 *   2. `ChevronRight` — 入口行/词行尾部「›」指示（Task 10 `EntryRow`）｜豁免（描边only）
 *   3. `ChevronDown` — 展开/折叠指示（AI 主页折叠行、生成前参数）｜豁免（描边only）
 *   4. `Close` — 对话框、搜索框与浮层关闭键｜豁免（描边only）
 *   5. `Check` — 完成与成功回显对勾（`InlineNoticeKind.Success` 行）｜豁免（描边only）
 *   6. `Search` — 查词入口与全局搜索框（§6.1「查词」；词汇域三入口）｜两层
 *   7. `Book` — 底导「学习」tab 的合上的书（Task 17 接入；造型系本轮自绘，与现状 tab 的合书意象同义）｜两层
 *   8. `BookOpen` — 摊开的书，词书/阅读页头（§6.1「词书」双页形）｜两层
 *   9. `List` — 列表与任务条目行（今日计划入口行组、词表行）｜两层
 *   10. `Bookmark` — 收藏（§6.1「收藏」；「我的词」收藏段）｜两层
 *   11. `Star` — 行内/入口通用（批 2/3 计划未钉站点，用前先补决策文档清单）｜两层
 *   12. `Clock` — 时刻与提醒行（§6.1「提醒」即时钟形；到期时间显示）｜两层
 *   13. `Chart` — 统计与学习记录（§6.1「学习记录」折线形；打卡页）｜两层
 *   14. `Spark` — AI 功能与生成入口（底导「AI」tab；Task 23 替「AI」含字徽标）｜两层
 *   15. `Speaker` — 发音药片（§6.1「发音」；Task 12 `SpeakerButton`）｜两层
 *   16. `Play` — 播放控件起播（发音/音频播放）｜两层
 *   17. `Pause` — 播放控件暂停（`SpeakerState.Paused` 对应形）｜两层
 *   18. `Stop` — 播放控件停止（「退出即停播」链路）｜两层
 *   19. `Mic` — 语音输入与「听/讲」类 AI 功能入口（七功能映射由 Task 23 钉定）｜两层
 *   20. `Translate` — 翻译功能（文章翻译、拍照翻译译文）｜两层
 *   21. `Camera` — 拍照翻译取图入口（AI 七功能「拍」）｜两层
 *   22. `Image` — 图像类 AI 功能（`ImageStudioScreen`，七功能「画」）｜两层
 *   23. `Document` — 文本类 AI 功能与生成结果行（七功能「文」）｜两层
 *   24. `Newspaper` — 底导「阅读」tab 与今日文章（Task 17）｜两层
 *   25. `Gear` — 底导「设置」tab 与设置域入口（Task 17）｜两层
 *   26. `Person` — 资料卡（裁决②：资料卡不再显示今日计划数字）｜两层
 *   27. `Info` — 关于/许可行（§6.1）与 `ScreenUnavailable` 默认图标（Task 9）｜两层
 *   28. `Bell` — 行内/入口通用（§6.1 提醒行取的是时钟形，本枚落点未钉屏）｜两层
 *   29. `Shield` — 认证备份入口（§6.1「认证备份」盾形；备份与恢复子页）｜两层
 *   30. `Warning` — 警示与破坏性操作行（`InlineNoticeKind.Error`、删除确认）｜两层
 *   31. `Trash` — 删除动作（§6.1「删除」；删词书/清空学习数据）｜两层
 *   32. `Upload` — 导入口（§6.1「导入」箭头向上形）｜两层
 *   33. `Download` — 导出入口（§6.1「导出」箭头向下形）｜两层
 *
 * 两层豁免表（`StrokeOnlyIcons`，裁决 4）：`Back`、`ChevronRight`、`ChevronDown`、`Close`、`Check`
 * 是开放折线，没有可闭合的内部区域，强行加填充层等于把描边变实心（正是 B 案选型要避开的「两族字形」）；
 * 这五枚只作描边骨架，且不得进入 ≥40dp 入口容器，只作行内/尾部指示。
 */

/**
 * 一枚图标的两层几何：[strokePaths] 是描边骨架层（每枚至少一条），[fillPaths] 是内部半透明层
 * （除豁免表外每枚至少一条）。两种合法形状：24×24 画布的 SVG path data 串，或 `CircleFill(cx, cy, r)` 标记串。
 * 本类不含也不接受任何不透明度取值：填充层透明度是主题属性，取值见 [DuotoneFillAlpha]。
 */
data class AppIconSpec(
    val name: String,
    val strokePaths: List<String>,
    val fillPaths: List<String>,
)

/**
 * B 案两层结构的填充层不透明度。这是主题属性而非字形属性，所以不放在 [AppIconSpec] 上
 * （逐枚各写一份就是第二套真值）。两个取值转录视觉稿 §6.2 的 B 案 CSS：
 * 浅色 `.t-b .ux-f` = `rgba(255,255,255,.42)`，深色 `html[data-theme="dark"] .t-b .ux-f` = `rgba(6,35,26,.30)`。
 * 权威出处（含稿内行号）钉在 `docs/decisions/2026-10-18-ui-redesign-icon-duotone.md`；本注释不写 `文件:行号`，
 * 行号随文件漂移而注释仍看似权威。渲染器（Task 7 `AppGlyph`）按 `LocalIsDark` 调 [forTheme] 取档。
 */
object DuotoneFillAlpha {
    const val Light = 0.42f
    const val Dark = 0.30f
    fun forTheme(isDark: Boolean): Float = if (isDark) Dark else Light
}

/** 枚数、名字与顺序都是决策文档的钉值面；改动等于改产品范围，先改那份文档。 */
enum class AppIcon { Back, ChevronRight, ChevronDown, Close, Check, Search, Book, BookOpen, List, Bookmark, Star, Clock, Chart, Spark, Speaker, Play, Pause, Stop, Mic, Translate, Camera, Image, Document, Newspaper, Gear, Person, Info, Bell, Shield, Warning, Trash, Upload, Download }

/** 两层豁免表：这五枚不建填充层（见文件头「两层豁免表」段与决策文档裁决 4）。 */
val StrokeOnlyIcons: Set<AppIcon> = setOf(AppIcon.Back, AppIcon.ChevronRight, AppIcon.ChevronDown, AppIcon.Close, AppIcon.Check)

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
        // Search：手柄折线、圆环与圆形填充转录 §6.2（视觉稿 `<circle cx=11 cy=11 r=6>` 写成两段圆弧 + 标记串）
        AppIconSpec(
            name = "Search",
            strokePaths = listOf("M11 5a6 6 0 010 12 6 6 0 010-12", "M20 20l-4.6-4.6"),
            fillPaths = listOf("CircleFill(11, 11, 6)"),
        ),
        // Book：书壳与书脊原创自绘（B 案度量）；填充层转录 §6.2 左页半幅
        AppIconSpec(
            name = "Book",
            strokePaths = listOf("M6.5 5h11a2 2 0 012 2v11a2 2 0 01-2 2h-11a2 2 0 01-2-2V7a2 2 0 012-2z", "M12 5v14"),
            fillPaths = listOf("M5 6h5.2v10.6H5z"),
        ),
        // BookOpen：骨架层两页转录 §6.2 双页形；左页内幅原创自绘（B 案度量）
        AppIconSpec(
            name = "BookOpen",
            strokePaths = listOf("M4 5h7a2 2 0 012 2v12a3 3 0 00-3-2H4z", "M20 5h-4a2 2 0 00-2 2v12a3 3 0 013-2h3z"),
            fillPaths = listOf("M5 6h5.5a2 2 0 012 2v9.6a3 3 0 00-2.5-1.6H5z"),
        ),
        // List：原创自绘（B 案度量）——三条文字线 + 圆点条目作填充层
        AppIconSpec(
            name = "List",
            strokePaths = listOf("M8 6h12", "M8 12h12", "M8 18h12"),
            fillPaths = listOf("M4 4.8h2.4v2.4H4z", "M4 10.8h2.4v2.4H4z", "M4 16.8h2.4v2.4H4z"),
        ),
        // Bookmark：骨架层转录 §6.1；左半内幅原创自绘（B 案度量）
        AppIconSpec(
            name = "Bookmark",
            strokePaths = listOf("M6 4h12v16l-6-3-6 3z"),
            fillPaths = listOf("M7.4 5.4h4.6v11.2l-4.6 2.3z"),
        ),
        // Star：骨架层与填充层同串转录 §6.2（i-star-o 与 i-star-f 是同一枚闭合折线）
        AppIconSpec(
            name = "Star",
            strokePaths = listOf("M6.4 4h11.2v15.2L12 15.6l-5.6 3.6z"),
            fillPaths = listOf("M6.4 4h11.2v15.2L12 15.6l-5.6 3.6z"),
        ),
        // Clock：指针与圆环转录 §6.2（视觉稿 `<circle cx=12 cy=12 r=8>` 写成两段圆弧）
        AppIconSpec(
            name = "Clock",
            strokePaths = listOf("M12 4a8 8 0 010 16 8 8 0 010-16", "M12 7.6V12l3.2 2"),
            fillPaths = listOf("CircleFill(12, 12, 8)"),
        ),
        // Chart：折线与坐标轴转录 §6.1；折线下方面积原创自绘（B 案度量）
        AppIconSpec(
            name = "Chart",
            strokePaths = listOf("M4 18l5-6 4 3 6-8", "M4 6v12h16"),
            fillPaths = listOf("M4 18l5-6 4 3 6-8v11z"),
        ),
        // Spark：主星芒与伴星转录 §6.2（伴星端点到 21.7，是视觉稿原几何，未改写坐标）
        AppIconSpec(
            name = "Spark",
            strokePaths = listOf(
                "M11 3.5l1.8 4.5 4.5 1.8-4.5 1.8L11 16.1l-1.8-4.5L4.7 9.8l4.5-1.8z",
                "M18.6 14.6l.9 2.2 2.2.9-2.2.9-.9 2.2-.9-2.2-2.2-.9 2.2-.9z",
            ),
            fillPaths = listOf("M11 3.5l1.8 4.5 4.5 1.8-4.5 1.8L11 16.1l-1.8-4.5L4.7 9.8l4.5-1.8z"),
        ),
        // Speaker：喇叭体与两道波弧转录 §6.2
        AppIconSpec(
            name = "Speaker",
            strokePaths = listOf("M5 10v4h3l4 3V7L8 10z", "M16 9a4 4 0 010 6", "M18.6 6.4a7.6 7.6 0 010 11.2"),
            fillPaths = listOf("M5 10v4h3l4 3V7L8 10z"),
        ),
        // Play：原创自绘（B 案度量）——三角形重心右移以补视觉配重，内幅只作层次暗示
        AppIconSpec(
            name = "Play",
            strokePaths = listOf("M8 5.5l10.5 6.5L8 18.5z"),
            fillPaths = listOf("M9.8 8.2l6 3.8-6 3.8z"),
        ),
        // Pause：原创自绘（B 案度量）——两条竖线骨架 + 同位实心条
        AppIconSpec(
            name = "Pause",
            strokePaths = listOf("M9 5v14", "M15 5v14"),
            fillPaths = listOf("M7.6 5h2.8v14H7.6z", "M13.6 5h2.8v14h-2.8z"),
        ),
        // Stop：原创自绘（B 案度量）——方框骨架 + 内缩实心块
        AppIconSpec(
            name = "Stop",
            strokePaths = listOf("M6 6h12v12H6z"),
            fillPaths = listOf("M8 8h8v8H8z"),
        ),
        // Mic：原创自绘（B 案度量）——胶囊振膜 + 托架半环 + 立杆与底座
        AppIconSpec(
            name = "Mic",
            strokePaths = listOf(
                "M12 4a3.5 3.5 0 013.5 3.5v3a3.5 3.5 0 01-7 0v-3a3.5 3.5 0 013.5-3.5z",
                "M8 12a4 4 0 008 0",
                "M12 16v3",
                "M9 20h6",
            ),
            fillPaths = listOf("M12 5.5a2 2 0 012 2v3a2 2 0 01-4 0v-3a2 2 0 012-2z"),
        ),
        // Translate：原创自绘（B 案度量）——大小两枚 A 字骨（拉丁/目标语），小字内幅作层次
        AppIconSpec(
            name = "Translate",
            strokePaths = listOf("M4 13l3.5-8 3.5 8", "M5.4 10h4.2", "M13 20l3.5-6.5 3.5 6.5", "M14.6 17h3.8"),
            fillPaths = listOf("M16.5 14.5l1.75 4h-3.5z"),
        ),
        // Camera：机身与镜头圆转录 §7 屏⑥（视觉稿 `<circle cx=12 cy=13 r=3.2>` 写成两段圆弧 + 标记串）
        AppIconSpec(
            name = "Camera",
            strokePaths = listOf("M4 8h4l2-2h4l2 2h4v11H4z", "M12 9.8a3.2 3.2 0 010 6.4 3.2 3.2 0 010-6.4"),
            fillPaths = listOf("CircleFill(12, 13, 3.2)"),
        ),
        // Image：原创自绘（B 案度量）——画框 + 山形，日光圆作填充层
        AppIconSpec(
            name = "Image",
            strokePaths = listOf("M4 5h16v14H4z", "M7 16l3.5-4.5 3 3.5 3-4 2.5 3"),
            fillPaths = listOf("CircleFill(9, 9, 2)"),
        ),
        // Document：原创自绘（B 案度量）——折角页 + 折线，正文区作填充层
        AppIconSpec(
            name = "Document",
            strokePaths = listOf("M6 4h8l4 4v12H6z", "M14 4v4h4"),
            fillPaths = listOf("M7.4 10h9.2v8.6H7.4z"),
        ),
        // Newspaper：原创自绘（B 案度量）——报面 + 两行正文，报头横带作填充层
        AppIconSpec(
            name = "Newspaper",
            strokePaths = listOf("M4 5h16v14H4z", "M7 12h10", "M7 15.5h6"),
            fillPaths = listOf("M5.4 6.4h13.2v3H5.4z"),
        ),
        // Gear：滑杆三条与旋钮圆转录 §6.1「设置」（视觉稿 `<circle cx=17 cy=17 r=2.5>` 写成标记串）
        AppIconSpec(
            name = "Gear",
            strokePaths = listOf("M4 7h16", "M4 12h16", "M4 17h10"),
            fillPaths = listOf("CircleFill(17, 17, 2.5)"),
        ),
        // Person：原创自绘（B 案度量）——头部圆环 + 肩线半环，头圆作填充层
        AppIconSpec(
            name = "Person",
            strokePaths = listOf("M12 4a4 4 0 010 8 4 4 0 010-8", "M4.5 20a7.5 7.5 0 0115 0"),
            fillPaths = listOf("CircleFill(12, 8, 4)"),
        ),
        // Info：圆环、字干与点转录 §6.1「关于 / 许可」（视觉稿 `<circle cx=12 cy=12 r=8>` 写成两段圆弧）
        AppIconSpec(
            name = "Info",
            strokePaths = listOf("M12 4a8 8 0 010 16 8 8 0 010-16", "M12 11v6", "M12 7.5v.5"),
            fillPaths = listOf("CircleFill(12, 12, 8)"),
        ),
        // Bell：骨架层两笔转录 §6.1；钟体内幅原创自绘（B 案度量）
        AppIconSpec(
            name = "Bell",
            strokePaths = listOf("M5 12a7 7 0 0114 0", "M9 15h6"),
            fillPaths = listOf("M6 12a6 6 0 0112 0v3H6z"),
        ),
        // Shield：盾体与对勾转录 §6.2（骨架层与填充层同串）
        AppIconSpec(
            name = "Shield",
            strokePaths = listOf("M12 4l7 3v5c0 4.2-3 7.3-7 8.2-4-.9-7-4-7-8.2V7z", "M9.2 12.2l2 2 4-4.2"),
            fillPaths = listOf("M12 4l7 3v5c0 4.2-3 7.3-7 8.2-4-.9-7-4-7-8.2V7z"),
        ),
        // Warning：三角与感叹号转录 §5（banner warn 用图）；三角内幅原创自绘（B 案度量）
        AppIconSpec(
            name = "Warning",
            strokePaths = listOf("M12 4l8 14H4z", "M12 9v4", "M12 15.5v.5"),
            fillPaths = listOf("M12 7l5.4 9.4H6.6z"),
        ),
        // Trash：骨架层三笔转录 §6.1 删除形；桶身内幅转录 §6.2（两稿几何本就近似，坐标逐字未改）
        AppIconSpec(
            name = "Trash",
            strokePaths = listOf("M5 7h14", "M9 7V5h6v2", "M7 7l1 12h8l1-12"),
            fillPaths = listOf("M7.6 8h8.8l-.9 11.2H8.5z"),
        ),
        // Upload：箭头与顶栏三笔转录 §6.1「导入」；箭头实心头原创自绘（B 案度量）
        AppIconSpec(
            name = "Upload",
            strokePaths = listOf("M12 20V9", "M8 13l4-4 4 4", "M5 5h14"),
            fillPaths = listOf("M12 9.6l3.4 3.4H8.6z"),
        ),
        // Download：箭头与底栏三笔转录 §6.1「导出」；箭头实心头原创自绘（B 案度量）
        AppIconSpec(
            name = "Download",
            strokePaths = listOf("M12 4v9", "M8 10l4 4 4-4", "M5 19h14"),
            fillPaths = listOf("M12 13.4l-3.4-3.4h6.8z"),
        ),
    )

    val all: Map<AppIcon, AppIconSpec> = specs.associateBy { AppIcon.valueOf(it.name) }
}

/** 读取本枚的路径数据；枚数与名字由 `AppIcon` 枚举钉住，缺枚在这里就是 `NoSuchElementException`。 */
val AppIcon.spec: AppIconSpec
    get() = AppIcons.all.getValue(this)
