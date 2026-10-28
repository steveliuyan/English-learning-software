package com.example.englishlearning.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 深色令牌消费棘轮（批 1 Task 8 Step 3）。
 *
 * 管什么：`ui` 包里对 `AppPalette.*` / `DomainColors.*` 的**静态**读点——它们绕开
 * `LocalAppColors` / `LocalDomainColors`，在深色模式下必然拿到浅套取值。每个文件当前命中数登记在
 * [DarkTokenSeedAllowlist]，断言是「逐文件精确相等」，两侧都不许偏：
 *  ① 实际 > seed（含全新文件冒出来）→ 新增即挡；
 *  ② 实际 < seed，或 seed 里的文件已改名/消失 → 也挡。
 * 所以迁完一屏的深色读点，必须同步把该文件的 seed 减 1、减到 0 才删行；seed 是债务台账，不是永久豁免。
 * 批 2/批 3 每个迁移任务 Step「迁完本屏深色读点」的依据就是这张表的递减。批 3 收口（计划 line 119 / Task 26 Step 3）
 * 的判据是「seed 清零，或只剩确实与主题无关、且已逐条登记并写明理由的站点」——不是无条件「整表清空」。
 *
 * 不管什么：本棘轮不宣称「全站零静态深色读点」。它只保证「新增即挡 + 存量只准同步递减」。
 *
 * 主题不变量（两类静态读点不是一回事，批 2/批 3 别把力气花错地方，也别拿第二类当第一类的借口）：
 *  - 半透明叠层：`AppPalette.GlassHighlight` / `Scrim`。它们是「白或黑 + alpha」的蒙版，
 *    压在图片或玻璃面板上，取值本就不跟主题走，深浅两套都合法。这一族的站点既留在 [DarkTokenSeedAllowlist]
 *    （计数照常承担），又逐条登记进 [ThemeInvariantMaskReadLedger] 写明「为什么不该迁成 appColors()」；
 *    收口时它们是计划口径允许的「与主题无关、已登记理由」残留，别为了「清零」强行主题化（强行主题化蒙版会让
 *    深色下的面板糊成一团，正是 AC-R-11 要防的）。
 *  - **同族但性质不同的一枚**：`AppPalette.GlassFill`（玻璃容器**填充**）。批 1 终审 Seat A I-1 复算后把它移出
 *    蒙版成员表：`AppColors.kt` 里 `surfaceGlass` 浅套取值与它逐字相同（`0xEFFFFFFF`），说明目标 token 早已备好，
 *    这些站点是**待迁债务**而不是主题不变量；理由与逐文件站点见 [GlassFillBackdropDebtLedger]。
 *    seed 计数不因移出而改变（它们本来就照常计入 debt seed）。批 2 终审修复轮把四枚站点全部迁走：
 *    席 A `components/glass/GlassDialog.kt`、席 B `components/glass/GlassOverlay.kt` +
 *    `AiProfileSettingsScreen.kt`（`SectionCard`）+ `SpeechSettingsScreen.kt`（`PreviewCard`）——
 *    每枚都是「容器 + 面板内前景同批迁」，浅色侧逐值不变，本表随之清空（取样链由合成探针承重，见该表 KDoc）。
 *  - 浅套硬编码：`AppPalette.TextPrimary` / `TextSecondary` / `Surface` / `Separator` /
 *    `OnMintPrimary` 与 `DomainColors` 各域的 `deep`。这些是浅底取值，深底压深字直接不可读，
 *    必须改成 `appColors().x` / `appDomain(AppDomain.X).deep`。`theme/AppType.kt` 的顶层 `TextStyle`
 *    写死 `TextPrimary`/`TextSecondary` 正是这一族的真实债务——**批 3 Task 26 已迁净**（六档改成
 *    `@Composable` getter），本族在 seed 里只剩白胶囊那一处「容器不随主题」的对偶残留（见下）。
 *  - **主题不变白胶囊**：`AiLearningScreen` 头卡状态徽章的容器是 `Color.White.copy(alpha = 0.85f)`
 *    压在品牌渐变上（深浅两套都是亮面），前景只能保持两枚静态墨。它不属于蒙版 token 族（`GlassHighlight` /
 *    `Scrim`），所以不进 [ThemeInvariantMaskReadLedger]，只在 seed 行内写明理由并交由
 *    `AiBadgeContrastFixTest` 钉站点形状与两套复算。
 *
 * 五张表的分工（修复轮 1 的 C1，替代旧版「不设第三张永久豁免表」那句已被 a90f4fe 自身推翻的措辞；
 * 第五张由批 1 终审 F4 补）：
 *  - [DarkTokenSeedAllowlist]：债务台账，`Map<相对路径, 命中数>`，双向精确相等，是「还欠多少」的权威。
 *  - [StaticReadPolicyExemptions]：四个令牌定义/装配文件，`Map<相对路径, 理由>`，当前静态读点为 0，只做文档化 + 空转守卫。
 *  - [ThemeInvariantMaskReadLedger]：主题不变蒙版色站点，`Map<相对路径, 命中数>`，双向精确相等；计数不从 seed 扣除，
 *    只回答「这一处为什么不该迁成 appColors()」。
 *  - [ParameterDefaultStaticReadLedger]：非可组合上下文里能按行形态安全识别的站点（形参默认值 + 文件顶层 val/var），
 *    `Map<相对路径, 命中数>`，双向精确相等；计数同样不从 seed 扣除，只标注「这一处不能就地换 appColors()」。
 *    注记表不是豁免表：它不改变 seed 计数，只是把同一处命中在 seed 之外再记一面，修一处要两张表同减，谁单独漂移都会红。
 *  - [GlassFillBackdropDebtLedger]：玻璃容器填充的**待迁债务**站点，`Map<相对路径, 理由>`，双向精确相等；
 *    计数同样不从 seed 扣除，只回答「这一处为什么批 1 不迁、要连着什么一起迁」。
 *
 * 口径：
 *  - 规则里的 `\b` 是必要的：`LocalDomainColors.current.getValue(...)` 含子串 `DomainColors.`，
 *    没有左边界就会把「新式正确写法」误判成静态读点——`composition local reads of the same name
 *    are not counted as static reads` 用例拿合成片段当场证此事。
 *  - 先剥注释再计数：定义文件的 KDoc 会合法复述 `AppPalette.X`，计入契约就成了空转。
 *  - 一行可含两处命中（`if (selected) DomainColors.AiSpeech.deep else AppPalette.TextSecondary`），
 *    所以本棘轮按**命中数**而不是按行计数，seed 形状是 `Map<文件, 次数>`；这也是它与裸 hex 棘轮的
 *    行集 allowlist 形状不同的原因。
 */

/**
 * 债务台账：`ui` 包内静态 `AppPalette.` / `DomainColors.` 读点的按文件命中数，key 为相对 `ui` 根的路径。
 * 顶层 `Brush` / 形参默认值这类「非可组合上下文」站点若在新迁移里出现，也必须登记到这张表，并在 offender
 * 指引下于代码里写明它为何读不了 `appColors()`；这类「与主题无关 / 结构上不可就地迁移」的站点另有专门注记表
 * （[ThemeInvariantMaskReadLedger]、[ParameterDefaultStaticReadLedger]）承载其理由，本表仍是「还欠多少」的唯一权威。
 */
internal val DarkTokenSeedAllowlist: Map<String, Int> = mapOf(
    // ---- Task 26 Step 3 收口后的仅存两类（8 处）：蒙版族 6 + 主题不变白胶囊 2 ----
    // 蒙版族（GlassHighlight / Scrim）：压在图片或玻璃面板上，深浅两套同值都合法，
    // 逐文件理由见 [ThemeInvariantMaskReadLedger]。
    "components/glass/GlassOverlay.kt" to 2,
    "components/glass/GlassDialog.kt" to 1,
    "SpeechSettingsScreen.kt" to 1,
    "components/IconBadge.kt" to 1,
    // 白胶囊族（R-23.14）：`AiLearningScreen` 头卡状态徽章的容器是 `Color.White.copy(alpha = 0.85f)`，
    // 压在品牌渐变上、深浅两套都是亮面，所以前景必须保持两枚**主题不变**的静态墨
    // （`DomainColors.AiSpeech.deep` / `AppPalette.TextSecondary`）；迁成 themed 墨 =
    // 深套亮墨压白（1.69:1 / 2.21:1，B1 第三站形状）。复算与站点钉法见 `AiBadgeContrastFixTest`。
    "AiLearningScreen.kt" to 2,
)

/**
 * 政策豁免表（只做「文档化 + 防空转」，不做永久豁免）：四个令牌定义/装配文件允许引用静态源，
 * 因为它们本身就是静态源的书写地。key 为相对 `ui` 根的路径（修复轮 1 的 M-N2：与其余台账同口径，按精确相等匹配，
 * 不再用裸文件名 + 后缀匹配，免得同名文件互相冒名顶替）。本测试当场断言两件事——
 *  ① 条目文件真实存在：改名或删文件都会让豁免表指向空，棘轮不许无声换扫法；
 *  ② 条目当前命中数为 0，且不在 seed 表里：一旦有人往定义文件里塞**消费**代码（读 `AppPalette.X`
 *    来渲染），第②条当场红，届时只能二选一——改回 `appColors()`，或显式修订本表并写明理由。
 */
internal val StaticReadPolicyExemptions: Map<String, String> = mapOf(
    "theme/AppPalette.kt" to "浅套静态源与 DomainColors 定义本体：值在这里写死，不该出现对自身 object 的读取",
    "theme/AppColors.kt" to "深浅两套 AppColors / DomainPalette 装配源：只产 DataClass 实例，消费一律走 appColors()/appDomain()",
    "theme/MintGlassTheme.kt" to "主题装配点：只提供 CompositionLocal，不读静态源",
    "theme/MintPalette.kt" to "薄荷令牌静态 getter 桥：内部读 LocalAppColors / LocalDomainColors，不读 AppPalette",
)

/**
 * 主题不变蒙版色的成员名（修复轮 1 的 C1；成员表由批 1 终审 F4 收窄）：半透明描边与遮罩，
 * 深底浅底都用同一 alpha 蒙版，token 化只会让两套主题各抄一遍同一个值，所以这两枚不迁成 `appColors()`。
 * 本表把「成员名 ∈ {GlassHighlight, Scrim}」抽成常量，使蒙版色判定可测：
 * `mask ledger splits by member name with synthetic probes` 用合成正/反片段证明这张表真在按成员名分流
 * （命中蒙版色成员即计数、命中其它成员即不计数），而不是恒真。
 *
 * `GlassFill` **已移出本表**（批 1 终审 Seat A I-1 复算裁决）：它是玻璃容器的**填充**而不是蒙版，
 * `AppColors.kt` 里 `surfaceGlass` 的浅套取值与它逐字相同（`0xEFFFFFFF`），说明目标 token 早已备好。
 * 批 1 不迁的理由：**玻璃容器填充必须与面板内前景墨色同批迁移，否则深色下由「意外可读」翻成「当场不可读」**
 * （那四块面板里的文字是无 `color=` 的静态档，墨色来自 `AppType` 钉死的浅套深墨）；
 * 目标值 `appColors().surfaceGlass`（浅 `0xEFFFFFFF` 与现值逐字相同，迁移在浅色侧像素零变化），
 * 落地由批 2/批 3 随屏 + connected 实测（AC-R-11）。站点与逐条理由见 [GlassFillBackdropDebtLedger]。
 */
internal val ThemeInvariantMaskMembers: List<String> = listOf("GlassHighlight", "Scrim")

/**
 * 玻璃容器填充的待迁债务台账（批 1 终审 F4 补的第五张表，不是豁免）：
 * `AppPalette.GlassFill` 的**尚未迁走**的站点，key 为相对 `ui` 根的路径（与其余台账同一口径，精确相等）。
 *
 * **批 2 终审修复轮已清空**（四枚站点全部连着面板前景同批迁成 `appColors().surfaceGlass`）：
 * 席 A `components/glass/GlassDialog.kt`（task-18fix-a），席 B `components/glass/GlassOverlay.kt`、
 * `AiProfileSettingsScreen.kt`（`SectionCard`）、`SpeechSettingsScreen.kt`（`PreviewCard`）（task-18fix-b）。
 * 浅色侧逐值不变（`AppColorLight.surfaceGlass` 与旧静态值同为 `0xEFFFFFFF`），深色侧才拿到那套深玻璃；
 * 面板内前景（含 `VoiceOverlayOption` / `CapabilityToggle` 这两族曾被单独回退的墨）同批按套取值，
 * 判据见 `GlassPanelInkPairingContractTest` 与本批深色档 connected 取样。
 *
 * 本表空不等于闸空（口径同 [ParameterDefaultStaticReadLedger]）：`glass fill backdrop scan is not a no-op once the ledger is empty`
 * 拿合成目录把「未登记站点」「残留台账行」「蒙版/待迁互斥」三种形状走**同一条**取样链
 * （[glassFillCounts] / [glassFillOffenders]），真实面清零后取样链一旦漂移照样判红。
 * `AppPalette.GlassFill` 这个常量本身仍留在浅套静态源里（`DesignTokenContractTest` 钉其取值），
 * 删除它属批 3 的静态源收口，不在本表口径内。
 */
internal val GlassFillBackdropDebtLedger: Map<String, String> = emptyMap()

/**
 * 主题不变蒙版色台账（不是豁免）：`DarkTokenSeedAllowlist` 里哪些命中是上面那族蒙版色，逐文件计数。
 * 计数仍以 [DarkTokenSeedAllowlist] 为准（蒙版色命中照常计入 debt seed），本表只是「为什么这一处不该迁成
 * `appColors()`」的逐条理由登记，兑现计划 line 119「收口时 seed 清零或仅剩确实与主题无关、已逐条写明理由的站点」
 * 里的「登记理由」落点。
 * 与 seed 同口径双向精确相等：注记里有的站点若实际已不存在 → 红；实际是蒙版色却未登记 → 红；同文件计数漂移 → 红。
 * 值即现场实测的蒙版色读点（GlassOverlay 一枚 Scrim + 一枚描边，GlassDialog / IconBadge / AiProfileSettingsScreen /
 * SpeechSettingsScreen 各一枚描边）。
 *
 * **批 1 终审 F4 的收窄**：本表每文件的旧值里各含一枚 `AppPalette.GlassFill` 填充读点，
 * `GlassFill` 移出蒙版成员表后那些读点不再计入本表（数值逐文件减 1），改由 [GlassFillBackdropDebtLedger]
 * 以「待迁债务」口径登记；debt seed 的计数一处不减。
 */
internal val ThemeInvariantMaskReadLedger: Map<String, Int> = mapOf(
    "components/glass/GlassOverlay.kt" to 2,
    "components/glass/GlassDialog.kt" to 1,
    "SpeechSettingsScreen.kt" to 1,
    "components/IconBadge.kt" to 1,
)

/**
 * 形参默认值 / 文件顶层声明注记（不是豁免；修复轮 1 的 I2，旧名 `NonComposableStaticReadLedger` 过度声称「覆盖所有
 * 非可组合上下文」）：本表只覆盖「能按行形态安全识别、且结构上不能就地换 appColors()」的两类站点——
 *  ① 形参默认值 `名字: 类型 = AppPalette.X`（Kotlin 不允许在默认参数里调 `@Composable`）；
 *  ② 文件顶层 `val/var`（列 0；object 初始化期同样调不了 `@Composable`）。
 * 正确迁移做法是把默认值改成可空哨兵、在函数体内解析（`accent: DomainAccent? = null` 然后
 * `accent ?: appDomain(AppDomain.AiSpeech)`）；顶层 val 则改造成由装配点现造的工厂函数。
 *
 * 有意排除的同族另一形态：**`object` 成员属性初始化器**（如 `theme/AppType.kt` 的 `val Display = TextStyle(...
 * color = AppPalette.TextPrimary)`）。它与「写在 `@Composable` 函数体里的可就地迁移局部 `val myStyle = TextStyle(...
 * AppPalette.X)`」文本形状完全相同，按行取样无法区分二者——若把它补进本表，就会同时误伤可迁移的体内局部（反向假阳），
 * 或漏掉日后新冒出的成员初始化器（重蹈本表被判的过度声称）。故本表**故意不采样**该形态，明确交由 [DarkTokenSeedAllowlist]
 * 兜底。**历史站点（批 3 Task 26 已消失）**：`theme/AppType.kt` 六档 `val Display = TextStyle(... color = AppPalette.TextPrimary)`
 * 整族改成 `@Composable @ReadOnlyComposable` getter（墨由 `appColors().text1/text2` 当场读取），object 成员初始化器的
 * 静态读点随之为零，seed 行已摘除。
 * `parameter default ledger excludes object member initializer by design` 用合成片段钉住这一排除不是靠运气。
 *
 * 计数仍以 [DarkTokenSeedAllowlist] 为准，本表只是注记，修掉一处时两张表一起减 1；两张表都由「精确相等」守着，
 * 新增未登记即红。**历史站点（批 2 Task 14A-i 已消失）**：`components/glass/GlassButton.kt` 的 `PillButton`
 * 形参默认值 `accent: DomainAccent = DomainColors.AiSpeech`（计划 Task 3 现场撞出、brief Step 3 点名要
 * 「一并列入并注明」的那一类）——Task 14A 按 R-14.4 把签名迁成 `domain: AppDomain = AppDomain.AiSpeech`，
 * 组件内得以就地读 `appDomain(domain)` + `appColors()` + `LocalIsDark.current`，该形态随之不存在，本表清空。
 * 表空不等于闸空：`parameter default ledger excludes object member initializer by design` 仍拿合成目录
 * 同时跑主规则取样与独立书写的交叉规则取样，两套站点集必须逐条相等，故「取样正则与 rule 漂移」照样判红。
 * 顶层 `Brush.linearGradient(...)`（数量可用 `grep -rn` 于 `ui/` 现算）读的是保持静态的 `AppleMint*`，不含
 * `AppPalette.` / `DomainColors.`，本规则看不见它们，也不归本棘轮管——见类 KDoc 的「品牌渐变模式不变」口径。
 */
internal val ParameterDefaultStaticReadLedger: Map<String, Int> = emptyMap()

class DarkTokenConsumptionRatchetTest {
    private val uiDir = File(UI_MAIN_SOURCE_PATH)

    /**
     * 静态读点规则。`\b` 排除 `LocalDomainColors.`（CompositionLocal，新式正确写法）；
     * `[A-Za-z0-9_]+` 让每个成员读取各计一次，同一行的两处命中不会被合并成一处。
     */
    private val rule = Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")

    /**
     * 形态取样正则一律以 `rule.pattern` 收尾拼装（修复轮 1 的 M-N4：旧版各写一份 `\b(?:AppPalette|DomainColors)\.`
     * 字面副本，`rule` 放宽时注记表会静默少报）。这样形态取样永远与主规则同温，`rule` 一改两侧同步漂移，
     * `ledger sites are a subset of the static read rule` 用例再交叉断言注记站点必为 `rule` 命中，兜住耦合。
     *
     * **D-M-1（批 1 终审 Seat D）：正因为收尾用的是 `rule.pattern`，那条「注记站点必为 rule 命中」是构造出来的恒真**
     * ——被取样到的行必然含 rule 命中，`rule` 本体被换掉时它一声不吭。所以那条用例另配一份
     * **独立书写**的交叉取样（[crossCheckedRule] 与下面两条 cross 形态正则），两侧站点集必须精确相等。
     */
    private val topLevelDeclarationRead =
        Regex("""^(private |internal |public )?(val|var)\s+\w+.*${rule.pattern}""")

    private val typedDeclarationRead =
        Regex("""\w+\s*:\s*[A-Z]\w*\s*=\s*${rule.pattern}""")

    /**
     * D-M-1 交叉样本：与 [rule] 今日逐字等价、但**不引用 `rule.pattern`** 的第二套字面规则
     * （`\b` 同样必要：`LocalDomainColors.` 含子串 `DomainColors.`，无锚点会把正确写法判成静态读点）。
     * 它只服务一条断言：`rule` 一旦漂移（去掉 `\b`、换成员字符类、改前缀名单），两套取样集合立刻不等，
     * 那条原本恒真的子断言才有牙齿。
     */
    private val crossCheckedRule = Regex("""\b(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")

    private val crossTopLevelDeclarationRead =
        Regex("""^(private |internal |public )?(val|var)\s+\w+.*${crossCheckedRule.pattern}""")

    private val crossTypedDeclarationRead =
        Regex("""\w+\s*:\s*[A-Z]\w*\s*=\s*${crossCheckedRule.pattern}""")

    /** 蒙版色判定（C1）：主规则前缀 + 成员名白名单，命中那两枚真蒙版色成员之一才计数（GlassFill 已由 F4 移出）。 */
    private val maskRule = Regex("""\bAppPalette\.(?:${ThemeInvariantMaskMembers.joinToString("|")})\b""")

    /** 玻璃容器填充读点（F4 的「待迁债务」取样）：字面写死 `GlassFill`，不随成员表漂移，与 [maskRule] 互补。 */
    private val glassFillRead = Regex("""\bAppPalette\.GlassFill\b""")

    private fun File.relativeUiPath(): String = relativePathFrom(uiDir)

    private fun actualCounts(): Map<String, Int> = uiFiles(uiDir)
        .associate { it.relativeUiPath() to countMatches(it.readText(), rule) }
        .filterValues { it > 0 }

    private fun maskCounts(dir: File = uiDir): Map<String, Int> = uiFiles(dir)
        .associate { it.relativePathFrom(dir) to countMatches(it.readText(), maskRule) }
        .filterValues { it > 0 }

    /** 玻璃容器填充站点，按 `dir` 取样（真实面与合成探针对走同一条链，dir 缺省为 ui 根）。 */
    private fun glassFillCounts(dir: File = uiDir): Map<String, Int> = uiFiles(dir)
        .associate { it.relativePathFrom(dir) to countMatches(it.readText(), glassFillRead) }
        .filterValues { it > 0 }

    /**
     * 待迁债务取样链的**纯判据**（真实面与合成样片共用，返回 offender 列表，空 = 合规）：
     * 未登记 → 红、台账残留 → 红、一站点两处填充 → 红、站点不在 seed 或 seed 少于填充数 → 红、
     * 同文件蒙版计数与蒙版台账漂移 → 红。
     */
    private fun glassFillOffenders(
        label: String,
        actual: Map<String, Int>,
        ledger: Map<String, String>,
        seed: Map<String, Int>,
        maskActual: Map<String, Int>,
        maskLedger: Map<String, Int>,
    ): List<String> {
        val offenders = mutableListOf<String>()
        val unregistered = actual.filterKeys { it !in ledger }
        if (unregistered.isNotEmpty()) {
            offenders += "$label：出现未登记的 AppPalette.GlassFill 容器填充站点 $unregistered —— 玻璃填充是待迁债务，" +
                "必须逐条登记进 GlassFillBackdropDebtLedger 写明「与面板前景同批迁」的理由，别把它塞回蒙版台账"
        }
        val stale = ledger.keys.filterNot { it in actual }
        if (stale.isNotEmpty()) {
            offenders += "$label：GlassFill 待迁台账里的站点已无该读点（已随屏迁成 appColors().surfaceGlass 或改了名）：$stale —— " +
                "删本表行的同时要把 DarkTokenSeedAllowlist 的对应 seed 一并减 1"
        }
        val counts = actual.filterKeys { it in ledger.keys }
        if (counts.values.any { it != 1 }) {
            offenders += "$label：每一处玻璃填充待迁站点应各恰一处 GlassFill 读点（多一处 = 第二份容器填充口径）：$counts"
        }
        counts.forEach { (path, count) ->
            val seedCount = seed[path]
            if (seedCount == null || seedCount < count) {
                offenders += "$label：GlassFill 待迁站点 $path 不在 debt seed 里或 seed 少于其填充读点数（$seedCount）——" +
                    "移出蒙版表不减少债务，两处台账必须同时看得见这一处"
            }
            if (path in maskLedger.keys && maskActual[path] != maskLedger[path]) {
                offenders += "$label：$path 的蒙版计数 ${maskActual[path]} 与其台账 ${maskLedger[path]} 不符——" +
                    "蒙版与待迁债务两套取样必须在同一文件上各自闭合"
            }
        }
        return offenders
    }

    /**
     * 命中「形参默认值 / 文件顶层 val」两种行形态的站点，返回「相对 `dir` 路径: 折叠行」；dir 缺省为 ui 根。
     * 两条形态正则可替换（D-M-1）：默认走以 `${rule.pattern}` 收尾的那一对，交叉取样传 [crossCheckedRule]
     * 那一套独立书写的字面副本，两侧的站点集合必须精确相等。
     */
    private fun parameterDefaultShapedSites(
        dir: File = uiDir,
        topLevelRead: Regex = topLevelDeclarationRead,
        typedRead: Regex = typedDeclarationRead,
    ): List<String> = uiFiles(dir).flatMap { file ->
        stripComments(file.readText()).lines()
            .filter { line ->
                val atColumnZero = line.isNotEmpty() && !line.first().isWhitespace()
                (atColumnZero && topLevelRead.containsMatchIn(line)) ||
                    typedRead.containsMatchIn(line)
            }
            .map { line -> "${file.relativeTo(dir).invariantSeparatorsPath}: ${line.trim().replace(Regex("""\s+"""), " ")}" }
    }

    @Test
    fun `seed and exemption keys point at real scanned files`() {
        val files = uiFiles(uiDir)
        val byPath = files.map { it.relativeUiPath() }.toSet()
        val unknown = DarkTokenSeedAllowlist.keys.filterNot { it in byPath }
        assertTrue(
            unknown.isEmpty(),
            "seed 里登记的静态读点文件不在扫描集里（改名/移动/已迁完都要同步 seed）：$unknown；扫描根 $UI_MAIN_SOURCE_PATH"
        )
        val unknownExemptions = StaticReadPolicyExemptions.keys.filterNot { it in byPath }
        assertTrue(
            unknownExemptions.isEmpty(),
            "政策豁免表指向定义文件已不存在：$unknownExemptions —— 豁免空转即为棘轮失效，必须先修表或修路径"
        )
        val unknownMaskFiles = ThemeInvariantMaskReadLedger.keys.filterNot { it in byPath }
        assertTrue(
            unknownMaskFiles.isEmpty(),
            "蒙版色台账指向的文件不在扫描集里（改名/已迁完都要同步台账）：$unknownMaskFiles"
        )
        val unknownLedgerFiles = ParameterDefaultStaticReadLedger.keys.filterNot { it in byPath }
        assertTrue(
            unknownLedgerFiles.isEmpty(),
            "形参默认值注记表指向的文件不在扫描集里（改名/已迁完都要同步注记）：$unknownLedgerFiles"
        )
    }

    @Test
    fun `no static dark read appears in a file the seed does not list`() {
        val unseeded = actualCounts().filterKeys { it !in DarkTokenSeedAllowlist }
        assertTrue(
            unseeded.isEmpty(),
            "新增静态深色读点（应改为 appColors().x / appDomain(AppDomain.X).deep，并把本屏迁移交回批 2/批 3 的收口任务）：\n" +
                unseeded.entries.joinToString("\n") { (path, count) -> "$path 出现 $count 处" } +
                "\n若该文件的命中全部是主题不变蒙版色（AppPalette.GlassHighlight/Scrim），它本就该留在 seed" +
                "并登记进 ThemeInvariantMaskReadLedger——别删 seed、别强行主题化蒙版；" +
                "AppPalette.GlassFill 不属这一族，它是待迁债务（见 GlassFillBackdropDebtLedger），只能连着面板前景一起迁；" +
                "除此之外不接受临时豁免。"
        )
    }

    @Test
    fun `a file whose reads dropped below the seed must decrement the seed too`() {
        val actual = actualCounts()
        val stale = DarkTokenSeedAllowlist.filterKeys { it !in actual }
        assertTrue(
            stale.isEmpty(),
            "seed 登记的文件已无静态深色读点（该文件债务已全部迁走）：$stale —— 应把该行的 seed 一并删掉；" +
                "但若它仍有命中、只是 seed 写多了，那是计数漂移、由逐文件相等用例处理，不是删行"
        )
    }

    @Test
    fun `per-file static read count equals the seed count exactly`() {
        val actual = actualCounts()
        val drifted = DarkTokenSeedAllowlist
            .filterKeys { it in actual }
            .filter { (path, seedCount) -> actual.getValue(path) != seedCount }
        assertTrue(
            drifted.isEmpty(),
            "静态深色读点与 seed 不符（增即挡，减了不同步改 seed 也挡）：\n" +
                drifted.entries.joinToString("\n") { (path, seedCount) ->
                    "$path 实际 ${actual.getValue(path)} 处，seed $seedCount 处——" +
                        "应改为 appColors().x / appDomain(AppDomain.X).deep，并同步把本文件 seed 减 1"
                }
        )
    }

    @Test
    fun `policy exempt definition files stay free of static consumption reads`() {
        // M-N2：政策豁免表 key 是相对路径，与 actualCounts()/seed 同一口径，直接精确集合判定，不再 endsWith 匹配裸名。
        val actual = actualCounts()
        val leaked = StaticReadPolicyExemptions.keys.filter { it in actual }
        assertTrue(
            leaked.isEmpty(),
            "政策豁免的定义文件里出现了静态读点（豁免只覆盖「这里是值的来源」，不覆盖消费）：" +
                leaked.joinToString("\n") { "$it 命中 ${actual.getValue(it)} 处" } +
                "\n改回 appColors() / appDomain()；若确实必须留在定义文件里，先修订本表理由并同步 seed"
        )
        val seededExemptFiles = StaticReadPolicyExemptions.keys.filter { it in DarkTokenSeedAllowlist.keys }
        assertTrue(
            seededExemptFiles.isEmpty(),
            "定义文件既被政策豁免又进了债务 seed，两套口径自相矛盾：$seededExemptFiles"
        )
    }

    @Test
    fun `parameter default and top level shaped reads are exactly the registered ledger`() {
        val sites = parameterDefaultShapedSites()
        val detected = sites.groupingBy { it.substringBefore(": ") }.eachCount()
        val unregistered = detected.filterKeys { it !in ParameterDefaultStaticReadLedger }
        assertTrue(
            unregistered.isEmpty(),
            "出现未登记的「形参默认值 / 文件顶层 val」静态读点：\n" +
                sites.filter { it.substringBefore(": ") in unregistered.keys }.joinToString("\n") +
                "\n首选改结构迁移（形参默认值换成可空哨兵、在函数体内 appDomain(...) 解析；顶层 val 改造成装配点现造的工厂函数）；" +
                "object 成员属性初始化器是同族另一形态，不登记进本表，交由 DarkTokenSeedAllowlist 承载其 seed。" +
                "确实结构上无法迁移才登记进 ParameterDefaultStaticReadLedger 并写明理由；本用例只做形态点名，判红由人来裁决。"
        )
        assertTrue(
            ParameterDefaultStaticReadLedger.keys.all { it in detected },
            "注记表里登记的站点已不存在（结构已改对或文件已改名）：" +
                ParameterDefaultStaticReadLedger.keys.filterNot { it in detected } +
                " —— 注记必须随之删除，残留注记会让下一个同类站点蒙过去。"
        )
        assertEquals(
            ParameterDefaultStaticReadLedger,
            detected,
            "形参默认值 / 文件顶层形态站点与注记表逐文件计数不符",
        )
    }

    @Test
    fun `ledger sites are a subset of the static read rule and of the seed`() {
        // M-N4：形态取样与主规则可能各写一份而漂移；本用例交叉断言每个注记站点既是一个 rule 命中，又落在 seed 里。
        val ruleHits = parameterDefaultShapedSites().filter { site ->
            rule.containsMatchIn(site.substringAfter(": "))
        }
        val sites = parameterDefaultShapedSites()
        assertTrue(
            ruleHits.size == sites.size,
            "注记表点名的站点里出现了不是主规则命中的行（形态正则与 rule 漂移）：\n" +
                sites.filterNot { it in ruleHits }.joinToString("\n")
        )
        val outsideSeed = sites.map { it.substringBefore(": ") }.distinct().filterNot { it in DarkTokenSeedAllowlist.keys }
        assertTrue(
            outsideSeed.isEmpty(),
            "注记表点名的文件不在债务 seed 里——注记必须 ⊆ 债务，谁单独漂移都会红：$outsideSeed"
        )
        ParameterDefaultStaticReadLedger.forEach { (path, count) ->
            assertTrue(
                (DarkTokenSeedAllowlist[path] ?: 0) >= count,
                "注记表在 $path 记了 $count 处，超过 seed ${DarkTokenSeedAllowlist[path]} 处——注记 ⊆ 债务"
            )
        }

        // ---- D-M-1（批 1 终审 Seat D）：把上面那条恒真子断言变成能被生产漂移打红的交叉断言 ----
        // `topLevelDeclarationRead` / `typedDeclarationRead` 都以 `${rule.pattern}` 收尾拼装，
        // 所以「被取样到的行必然是 rule 命中」是**构造出来的恒真**：`rule` 本体被换掉（去掉词边界锚、
        // 换成员字符类、改前缀名单）时它一声不吭。下面这套站点用**独立书写**的 [crossCheckedRule] 取一样的一份，
        // 两个集合精确相等——`rule` 一改，主取样漂移、交叉取样不动，本条立刻红。
        val crossSites = parameterDefaultShapedSites(
            topLevelRead = crossTopLevelDeclarationRead,
            typedRead = crossTypedDeclarationRead,
        )
        assertEquals(
            sites.sorted(),
            crossSites.sorted(),
            "主规则取样与独立书写的交叉规则取样不再同源：`rule` 与 (?:AppPalette|DomainColors) 那条字面口径漂移了。" +
                "两套站点集必须逐条相等，否则「注记 ⊆ 静态读点」这条交叉断言就失去意义（先对齐规则再谈台账）",
        )

        // 合成负片段自证：本交叉不是第二枚恒真。词边界锚一旦缺失，`LocalDomainColors.`（正确写法）
        // 就会被当成静态读点，两套取样在下面的行样本上立刻给出不同结果。
        val probeLines = listOf(
            "val staticInk = AppPalette.TextPrimary",
            "private val localInk = LocalDomainColors.current.text1",
        )
        val anchored = Regex("""^(private |internal |public )?(val|var)\s+\w+.*${crossCheckedRule.pattern}""")
        val unanchored = Regex("""^(private |internal |public )?(val|var)\s+\w+.*(?:AppPalette|DomainColors)\.[A-Za-z0-9_]+""")
        assertEquals(
            listOf("val staticInk = AppPalette.TextPrimary"),
            probeLines.filter { anchored.containsMatchIn(it) },
            "交叉规则本身失效：带词边界锚的取样应当只抓到静态读点那一行",
        )
        assertEquals(
            probeLines,
            probeLines.filter { unanchored.containsMatchIn(it) },
            "漂移形态构造失败：去掉词边界锚后 `LocalDomainColors.` 也必须被抓到，否则本交叉断言打不红",
        )
        assertNotEquals(
            probeLines.filter { anchored.containsMatchIn(it) },
            probeLines.filter { unanchored.containsMatchIn(it) },
            "合成负片段未被判出：带锚 / 丢锚两种规则给出同一集合时，上面那条精确相等就是空转",
        )
    }

    @Test
    fun `theme invariant mask reads are exactly the registered ledger`() {
        val actual = maskCounts()
        val unregistered = actual.filterKeys { it !in ThemeInvariantMaskReadLedger }
        assertTrue(
            unregistered.isEmpty(),
            "出现未登记的主题不变蒙版色（AppPalette.GlassHighlight/Scrim）读点：$unregistered —— " +
                "蒙版色是合法残留，必须逐条登记进 ThemeInvariantMaskReadLedger 并写明理由，" +
                "别让「与主题无关」的站点游离在任何台账之外。"
        )
        val stale = ThemeInvariantMaskReadLedger.keys.filterNot { it in actual }
        assertTrue(
            stale.isEmpty(),
            "蒙版色台账里登记的文件已无蒙版色读点（已迁走/改名）：$stale —— 台账随之删除，残留会让下一处蒙版色蒙混过去。"
        )
        val drifted = ThemeInvariantMaskReadLedger.filterKeys { it in actual }
            .filter { (path, ledgerCount) -> actual.getValue(path) != ledgerCount }
        assertTrue(
            drifted.isEmpty(),
            "蒙版色逐文件计数与台账不符（增即挡，减了不同步改也挡）：\n" +
                drifted.entries.joinToString("\n") { (path, ledgerCount) ->
                    "$path 实际 ${actual.getValue(path)} 处，台账 $ledgerCount 处"
                }
        )
    }

    @Test
    fun `theme invariant mask ledger is a subset of the seed`() {
        // 蒙版色不豁免计数：每处蒙版色读点必须同时计入 debt seed（修一处两张表同减，谁单独漂移都会红）。
        ThemeInvariantMaskReadLedger.forEach { (path, maskCount) ->
            val seedCount = DarkTokenSeedAllowlist[path]
            assertTrue(
                seedCount != null && seedCount >= maskCount,
                "蒙版色台账在 $path 记了 $maskCount 处，但 debt seed 为 ${seedCount ?: "无"}——" +
                    "蒙版色计入 debt 台账，不允许只进注记表不进 seed"
            )
        }
        val actual = maskCounts()
        actual.forEach { (path, maskCount) ->
            val readCount = actualCounts()[path] ?: 0
            assertTrue(
                readCount >= maskCount,
                "$path 蒙版色 $maskCount 处却只有静态读点总计 $readCount 处——蒙版色取样与主规则口径矛盾"
            )
        }
    }

    @Test
    fun `mask ledger splits by member name with synthetic probes`() {
        // C1：证明蒙版色判定真在按成员名分流（不是恒真）。正片段被计数、反片段不被计数，缺一不可。
        // F4 收窄后 GlassFill 归到反片段一侧——它仍是**主规则**命中的债务读点，只是不再算「主题不变蒙版色」。
        val positive = "val scrim = AppPalette.Scrim\nval edge = AppPalette.GlassHighlight\n"
        val negative = "val fill = AppPalette.GlassFill\nval ink = AppPalette.TextPrimary\nval line = AppPalette.Separator\n" +
            "val ink2 = AppPalette.TextSecondary\nval deep = DomainColors.AiSpeech.deep\n" +
            "val local = LocalAppPalette.GlassFill\n"
        assertEquals(5, rule.findAll(stripComments(negative)).count(), "主规则必须仍认出 GlassFill + 三枚浅套硬编码 + 一枚域 deep（共 5 处静态读点）")
        assertEquals(2, maskRule.findAll(stripComments(positive + negative)).count(), "蒙版色规则只许认出 Scrim 与 GlassHighlight 两枚")
        assertTrue(
            maskRule.findAll(stripComments(negative)).none(),
            "蒙版色规则不得把 GlassFill（批 1 终审 F4 移出的待迁债务）/ 浅套硬编码 / 各域 deep / LocalAppPalette " +
                "误判成蒙版色（否则台账恒真）：" +
                maskRule.findAll(stripComments(negative)).map { it.value }
        )
    }

    /**
     * **F4 的验收物之二**：`AppPalette.GlassFill` 是「待迁债务」，不是「主题不变蒙版色」。
     *
     * 三段承重（缺一即恒真）：
     *  ① 逐文件 GlassFill 命中数与 [GlassFillBackdropDebtLedger] **双向精确相等**（新增未登记 → 红，
     *     迁走一处而台账残留 → 也红），台账 key 与其余台账同为相对 `ui` 根的精确路径；
     *     批 2 终审修复轮后真实面已清零，取样链的空转守卫见
     *     `glass fill backdrop scan is not a no-op once the ledger is empty`；
     *  ② 每一处 GlassFill 站点都必须同时落在 [DarkTokenSeedAllowlist] 里，且 seed 不减
     *     ——移出蒙版表**不等于**移出债务台账，本表只是理由的另一面；
     *  ③ 蒙版规则与 GlassFill 取样互斥（同一枚读点不会既算蒙版又算待迁债务）。
     */
    @Test
    fun `glass fill backdrop reads are migratable debt registered apart from the mask ledger`() {
        val actual = glassFillCounts()
        val offenders = glassFillOffenders(
            "真实源码",
            actual,
            GlassFillBackdropDebtLedger,
            DarkTokenSeedAllowlist,
            maskCounts(),
            ThemeInvariantMaskReadLedger,
        )
        assertTrue(
            offenders.isEmpty(),
            "GlassFill 待迁债务的两张表不同步：\n" + offenders.joinToString("\n") +
                "\n迁走一处 = 本表删行 + DarkTokenSeedAllowlist 同文件 seed 减 1；只动一张都会漂。" +
                "台账已清空后取样链是否空转，另见 `glass fill backdrop scan is not a no-op once the ledger is empty`。",
        )
        // ③ 互斥：蒙版规则抓不到 GlassFill，待迁规则抓不到蒙版成员——同一段文本两份取样不得重叠。
        val both = "val a = AppPalette.GlassFill\nval b = AppPalette.GlassHighlight\nval c = AppPalette.Scrim\n"
        val stripped = stripComments(both)
        assertEquals(1, glassFillRead.findAll(stripped).count(), "待迁取样必须只认出 GlassFill 那一枚")
        assertEquals(2, maskRule.findAll(stripped).count(), "蒙版取样必须只认出 GlassHighlight 与 Scrim 两枚")
        assertTrue(
            glassFillRead.findAll(stripped).map { it.value }.toSet()
                .intersect(maskRule.findAll(stripped).map { it.value }.toSet()).isEmpty(),
            "两份取样重叠 = 同一枚读点被登记两次，seed 递减时会有一张表漂不掉"
        )
    }

    /**
     * **台账清空后的空转守卫（批 2 终审修复席 B）**：[GlassFillBackdropDebtLedger] 现为空表，
     * 上面那条判据在真实面上失去样本。空表不等于闸空——本用例拿合成目录把**同一条**取样链
     * （[glassFillCounts] + [glassFillOffenders]）的四种形状逐一点名：
     *  ① 合成站点必须被取样到（否则后面三条全是空对空）；
     *  ② 站点存在而台账没登记 → 红（新增即挡）；
     *  ③ 登记了但 seed 没同步看见这一处 → 红（「只减债务表、不动 seed」那条漂移）；
     *  ④ 站点已迁走而台账行残留 → 红（「迁完不删行」那条漂移）；登记齐全时 → 放行（证明②③④不是恒真）。
     * 另钉真实面：源码里 GlassFill 容器填充站点确已归零，且空表里没有任何残留 key。
     */
    @Test
    fun `glass fill backdrop scan is not a no-op once the ledger is empty`() {
        val dir = java.nio.file.Files.createTempDirectory("glass-fill-debt-ratchet").toFile()
        try {
            File(dir, "PanelProbe.kt").writeText(
                """
                @Composable
                fun PanelProbe() {
                    Surface(color = AppPalette.GlassFill, border = BorderStroke(1.dp, AppPalette.GlassHighlight))
                }
                """.trimIndent() + "\n"
            )
            val probePath = "PanelProbe.kt"
            val actual = glassFillCounts(dir)
            assertEquals(
                mapOf(probePath to 1),
                actual,
                "合成目录没被 GlassFill 取样链抓到：真实面清零后这条链就成了空转（取样正则或扫描根漂了）",
            )
            val maskProbe = maskCounts(dir)
            assertEquals(
                mapOf(probePath to 1),
                maskProbe,
                "合成目录的蒙版取样没抓到 GlassHighlight：下面那条「蒙版与待迁各自闭合」的判据会失去对象",
            )
            assertTrue(
                glassFillOffenders("未登记样片", actual, emptyMap(), mapOf(probePath to 2), maskProbe, emptyMap()).isNotEmpty(),
                "反空转失控：新增未登记的 GlassFill 站点没被点名（债务表不再挡新增）",
            )
            assertTrue(
                glassFillOffenders("seed 漂移样片", actual, mapOf(probePath to "理由"), emptyMap(), maskProbe, emptyMap()).isNotEmpty(),
                "反空转失控：登记了债务行却没同步 debt seed，这条漂移没被点名",
            )
            assertTrue(
                glassFillOffenders("残留台账样片", emptyMap(), mapOf(probePath to "理由"), mapOf(probePath to 1), maskProbe, emptyMap()).isNotEmpty(),
                "反空转失控：站点已迁走而台账行残留，这条漂移没被点名（残留行会让下一处同类站点蒙过去）",
            )
            assertTrue(
                glassFillOffenders("合规样片", actual, mapOf(probePath to "理由"), mapOf(probePath to 2), maskProbe, emptyMap()).isEmpty(),
                "反空转失控：登记齐全（债务行 + seed + 蒙版闭合）的样片也被判红，上面三条就成了恒真",
            )
            assertTrue(
                glassFillOffenders(
                    "蒙版漂移样片",
                    actual,
                    mapOf(probePath to "理由"),
                    mapOf(probePath to 2),
                    maskProbe,
                    mapOf(probePath to 2),
                ).isNotEmpty(),
                "反空转失控：同文件的蒙版计数与蒙版台账不符时没被点名——两张表的「各自闭合」没有牙齿",
            )
            assertTrue(
                glassFillCounts().isEmpty(),
                "真实源码仍有 AppPalette.GlassFill 容器填充站点：${glassFillCounts()} —— 批 2 已四处全迁，" +
                    "新增玻璃填充必须直接写 appColors().surfaceGlass",
            )
            assertEquals(
                emptyMap<String, Int>(),
                glassFillCounts().filterKeys { it in DarkTokenSeedAllowlist.keys },
                "GlassFill 债务台账清空后，真实面必须同时是零站点（否则 seed 里藏着一条没有台账理由的填充读点）",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `parameter default ledger excludes object member initializer by design`() {
        // I2：合成正/反片段，钉住「形参默认值/顶层 val 被认出」与「object 成员属性初始化器被明确排除」都不是靠运气。
        val dir = java.nio.file.Files.createTempDirectory("param-default-ratchet").toFile()
        try {
            File(dir, "Probe.kt").writeText(
                """
                object AppType {
                    val Display = TextStyle(color = AppPalette.TextPrimary)
                }
                @Composable fun Row(accent: DomainAccent = DomainColors.AiSpeech) {}
                private val TopInk = AppPalette.Separator
                @Composable fun Body() {
                    val myStyle = TextStyle(color = AppPalette.TextSecondary)
                }
                """.trimIndent() + "\n"
            )
            val detected = parameterDefaultShapedSites(dir).map { it.substringAfter(": ") }
            assertTrue(
                detected.any { it.contains("accent: DomainAccent = DomainColors.AiSpeech") },
                "形参默认值形态必须被认出：$detected"
            )
            assertTrue(
                detected.any { it.contains("private val TopInk = AppPalette.Separator") },
                "文件顶层 val 形态必须被认出：$detected"
            )
            assertTrue(
                detected.none { it.contains("val Display = TextStyle") },
                "object 成员属性初始化器（缩进 val，无列 0、无 名字: 类型 =）必须被本表排除，不能误纳：$detected"
            )
            assertTrue(
                detected.none { it.contains("val myStyle = TextStyle") },
                "可就地迁移的 @Composable 体内局部 val 必须被排除（否则反向假阳把债务误标成注记）：$detected"
            )
            // 14A-i 之后真实源码的注记表已空，`ledger sites are a subset...` 在真实面上失去样本，
            // 所以这里用同一份合成目录把「主规则取样」与「独立书写的交叉规则取样」逐条对齐：
            // 形态正则一旦与 rule 漂移（丢锚点 / 换成员字符类 / 改前缀名单），本条立刻红，交叉断言不空转。
            assertTrue(
                parameterDefaultShapedSites(dir).isNotEmpty(),
                "合成目录取样为空：下面的两套取样相等就成了空对空的重言，Probe.kt 的两种行形态必须先修"
            )
            assertEquals(
                parameterDefaultShapedSites(dir).sorted(),
                parameterDefaultShapedSites(
                    dir,
                    crossTopLevelDeclarationRead,
                    crossTypedDeclarationRead,
                ).sorted(),
                "同一份源码上主规则取样与独立书写的交叉规则取样不再同源：形态正则与 rule 漂移了",
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `composition local reads of the same name are not counted as static reads`() {
        val snippet = "val c = LocalDomainColors.current.getValue(AppDomain.Learn).base\n" +
            "val d = AppPalette.Separator\n"
        val hits = rule.findAll(stripComments(snippet)).count()
        assertTrue(
            hits == 1,
            "规则必须只认静态源读取、不把 LocalDomainColors.current 算成债务：命中 $hits 次（应为 1）"
        )
    }
}
