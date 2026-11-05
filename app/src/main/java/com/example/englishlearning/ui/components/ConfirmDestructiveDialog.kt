package com.example.englishlearning.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.englishlearning.ui.theme.AppColorSet
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * 批 1 Task 11：破坏性确认对话框（spec §3「删除/清空确认：级联条数 + 不受影响的保留面 + 不可撤销」）。
 *
 * 收的是现网各屏私有的破坏性确认（词书删除、学习数据清空、备份覆盖一类；份数会随批 3 收口归零，
 * 故不写死——重算：`grep -rn 'AlertDialog(' app/src/main/java/com/example/englishlearning/ui --include=*.kt | grep -v components/ | wc -l`）。这些站点的现状是
 * 「级联条数、保留面、不可撤销」三件事各屏各写、少写哪件没人发现，所以：
 *  - **措辞**收成纯函数 [destructiveDialogBody]，由 `ConfirmDestructiveWordingTest` 逐要素断言；
 *  - **按钮墨色**收成纯表 [dialogButtonInk]，由 `ConfirmDestructiveInkTest` 逐档复算（connected 读不到颜色，
 *    Task 9 实测口径，所以「哪枚 token 当容器」只能在 JVM 侧钉死）。
 *
 * 面板与按钮两枚内部构件（[DialogPanel] / [DialogButton]）放在本文件、由
 * [OutboundConfirmDialog] 复用：spec §3 数的是九个共享组件，对话框面板不算第十个组件，
 * 另起一份文件会撞 Task 12 的组件份数棘轮；同一份 `DialogPanel` 也保证两枚对话框不会各长一套卡面配方。
 *
 * 已登记的偏离（不是随手替换）：面板**不复用** `glass/GlassDialog.kt`。本组件立项时那一份的容器底是
 * `AppPalette.GlassFill`（静态浅套近不透明白），深色模式下 `appColors()` 取的墨压在它上面会读不出来；
 * 而在本组件里改用静态墨又会新增 `DarkTokenConsumptionRatchetTest` 的未登记静态读点。
 * 故这里按同一几何（外层 24dp 横向留白 + 内层 24dp padding + hero 档圆角 + 560.dp 高度上限与竖向滚动）自建面板，
 * 底色改走 `appColors().surface`，两套主题各自成立。
 * `GlassDialog` 当时留给既有屏继续用；批 2 终审修复席 A 已把它的容器底迁到 `appColors().surfaceGlass`，
 * 所以「不能复用」的原始理由已经消失。本批仍不合并两族面板：那会把两枚确认框从实底卡面换成半透明玻璃 +
 * 窗口模糊，属于换外观而不是排印统一，且 `GlassDialog` 的公开签名被 `GlassPanelInkPairingContractTest`
 * 逐字冻结（要接它得先动那条契约）。2026-11-05 复核：两族的面板几何与滚动兜底现已逐字同形。
 */

/**
 * 对话框按钮的三档角色。只建三档：多一枚就是第二种「主操作」，会与批 1 唯一按钮 `PillButton`
 * 的档位裁决打架（枚举声明顺序即 `DialogButtonRole.entries` 的顺序，被契约钉住）。
 *
 * - [DestructiveConfirm]：破坏性确认（本文件的确认按钮），墨色见 R-11.1；
 * - [Confirm]：出站确认的「确认发送」，逐字复用装配点已钉的 `primary`/`onPrimary` 那一对；
 * - [Dismiss]：取消，透明容器 + 次要墨，压在面板底上判比值。
 */
enum class DialogButtonRole { DestructiveConfirm, Confirm, Dismiss }

/**
 * 一档按钮的两枚墨：容器与前景。
 *
 * 两字段而不是 `Pair`：与 [NoticeInk] / [EntryRowInk] 同口径，具名字段才能让「容器取哪枚」这件事
 * 在契约里被点名断言，位置参数写反了编译器不会挡。
 */
data class DialogButtonInk(
    val container: Color,
    val label: Color,
)

/**
 * 三档角色 → token 的纯表（无 `@Composable`，[set] 就是浅套或深套那两套之一）。
 *
 * **裁决 R-11.1（作废任务书 Step 3 原稿）**：破坏性确认档取 `set.error` 容器 + `set.surface` 前景。
 * 原稿写的是「`errorFill` 填充 + 白字、按大字/图形 3:1 口径放行」，实算不成立：浅套白字压 `errorFill`
 * 不足正文 4.5，深套白字压 `errorFill` 连非文本 3:1 都不过；而按钮标签 15sp ≈ 11.25pt 够不到 WCAG 大字豁免线
 * （14pt bold / 18pt regular），所以「按大字享受 3:1」的前提根本不存在——**本表与本文件一律不宣称大字豁免**，
 * 三档都按正文 4.5 判。比值不抄在这里（抄了会随 token 取值漂移），由
 * `ConfirmDestructiveInkTest` 当场按相对亮度复算，并把被拒候选（白字压两套 `errorFill`）永久钉成不合格。
 *
 * 与滑动删除那一族红底白字（真机取证的深红底）**同族但不是同一条**：那一族的口径是
 * 「深底浅字 / 浅底深字**按主题套分叉**」——浅套白字压深红、深套墨字压浅红，**不是**「红底一律白字」。
 * 本表正是按套分叉取值：两套的 `error` 分别是深红与浅红，`surface` 分别是白与墨，
 * 于是浅套得到「白字压深红」、深套得到「墨字压浅红」，与那一族同一读法。
 *
 * `errorFill` 保持它现有的职责（`InlineNotice` 的 Error 态背景/描边、装配点的 `errorContainer`），
 * **不得**用作按钮容器——这条禁令由 `ConfirmDestructiveInkTest` 的源码腿钉住（本文件里 `errorFill` 0 处）。
 *
 * `Confirm` 档不新造第三套按钮语义：容器 `onMint` + 前景 `background` 就是 `MintGlassTheme.kt` 装配点
 * 已钉的 `colorScheme.primary` / `onPrimary` 那一对（同一枚 token、同一个方向），
 * 所以「确认发送」与既有主按钮在两套主题下读起来是同一档。
 */
fun dialogButtonInk(role: DialogButtonRole, set: AppColorSet): DialogButtonInk = when (role) {
    DialogButtonRole.DestructiveConfirm -> DialogButtonInk(container = set.error, label = set.surface)
    DialogButtonRole.Confirm -> DialogButtonInk(container = set.onMint, label = set.background)
    DialogButtonRole.Dismiss -> DialogButtonInk(container = Color.Transparent, label = set.text2)
}

/** 不可撤销声明的固定措辞：AGENTS.md 的业务规则（删除级联确认必须明说不可撤销），不是可省的修饰。 */
private const val IRREVERSIBLE_LINE = "此操作不可撤销。"

/**
 * 面板节点锚（connected 侧量面板自身的高度上限与滚动动作用；见 [DialogPanel] 的说明）。
 * 内部构件没有屏级 testTag 惯例可借，故只给这一枚常量，命名与屏侧 `*_dialog_*` 同族。
 */
internal const val DIALOG_PANEL_TAG = "dialog_panel"

/**
 * 破坏性确认正文的**唯一**措辞配方：级联行 → 保留面 → 不可撤销声明。
 *
 * 三件事齐备且顺序固定，这是本函数的全部职责：
 *  - 顺序即读法——「先看我毁掉什么，再看我保住什么，最后看能不能反悔」。把不可撤销排到最前，
 *    TalkBack 用户会先听到结论、最后才听到级联条数（最具体的那条信息）；
 *  - 调用方给的每一行**逐字透传**：组件不猜条数、不猜保留面，具体数字只有调用方知道；
 *  - [irreversible] 为假时那一行**真的消失**——现网确有可逆的破坏性入口（可再生成的重排一类），
 *    旗标为假时谎称不可撤销会让用户放弃本可挽回的操作。
 *
 * 四要素齐备与「图片每次确认」这两条规则在 connected 侧无法断言（那边只能证明字符串在树上），
 * 所以唯一证据面是 `ConfirmDestructiveWordingTest`。
 */
fun destructiveDialogBody(
    cascadeLines: List<String>,
    keepLines: List<String>,
    irreversible: Boolean,
): List<String> = cascadeLines + keepLines + if (irreversible) listOf(IRREVERSIBLE_LINE) else emptyList()

/**
 * 对话框面板（两枚对话框共用）：外层 24dp 横向留白 + hero 档圆角 + `surface` 底 + 1dp `outlineSoft` 描边 +
 * 内层 24dp padding。
 *
 * 圆角走 `MaterialTheme.shapes.large`（装配点 = `AppShape.Dialog` 的 24dp，spec §2.3 的 hero 档），
 * **不读 `AppShape.Dialog` 本体**：新组件一律走装配档，与 `InlineNotice` 不读 `AppShape.Card` 同一口径。
 * 底色走 `appColors().surface` 而不是玻璃填充——这句里的「玻璃填充」指**静态常量** `AppPalette.GlassFill`
 * （浅套近不透明白，本组件立项时它是 `GlassDialog` 的容器底）；批 2 终审修复席 A 之后 `GlassDialog` 已改读
 * `appColors().surfaceGlass`，本面板继续走不透明 `surface` 不变，两套主题各自成立（见文件头登记的偏离）。
 *
 * **高度上限 + 竖向滚动（2026-11-05 弹窗排印统一批补）**：链序 `heightIn(max = 560.dp)` → `verticalScroll` →
 * `padding(24.dp)`，与 `glass/GlassDialog.kt` 逐字同形（上限 → 滚动 → 边距：边距放进滚动内容里才不吞首尾行）。
 * 补这一条的原因是本面板原先**没有**上限：`ConfirmDestructiveDialog` 的级联行数由调用方逐字给（清空学习记录
 * 那一档实测两三行，但批 2/3 各屏接自己的确认请求态后行数不可控），设备字号 2.0 下面板长过窗口时，
 * 底部那两枚按钮被顶出屏幕且滚不回来——模态窗口里唯一的出路消失。560.dp 取 `GlassDialog` 已验证过的同一档：
 * 两枚短对话框在一屏内可见，不需要滚动，既有的 connected 零滚动断言据此继续成立。
 *
 * [onDismissRequest] 由调用方给（返回键 / 点 scrim 都走它），面板本身不决定「取消意味着什么」。
 *
 * 面板节点带 [DIALOG_PANEL_TAG]：上限与滚动是**面板**的性质，connected 侧要量的是面板自身的高度与滚动动作
 * （量标题或按钮只能证明「这行在树上」，证不了「长内容时出路还在」），判据见 `DialogTypographyEvidenceTest`。
 */
@Composable
internal fun DialogPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val set = appColors()
    val shape = MaterialTheme.shapes.large
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(set.surface)
                    .border(1.dp, set.outlineSoft, shape)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .semantics { testTag = DIALOG_PANEL_TAG }
                    .padding(24.dp),
                content = content,
            )
        }
    }
}

/**
 * 对话框按钮（两枚对话框共用）：整宽等分、≥48dp 触控、圆角走装配档 `small`（= `AppShape.Button` 14dp），
 * 墨色一律从纯表 [dialogButtonInk] 取（源码契约钉「本文件恰一处取墨」）。
 *
 * 48dp 触控面由两条**共同**交付，别把其中一条当成「顺序契约」：① 链上存在 `heightIn(min = 48.dp)`；
 * ② `clickable` 排在任何 `padding` **之前**（即最外层可点面）。`heightIn` 与 `clickable` 的先后
 * **不影响**可点面尺寸——`clickable`/`pointerInput` 不参与测量，`heightIn` 的最小高约束向内传播、
 * 实测尺寸向外冒泡，两种顺序下可点节点都 ≥48dp（本文件的 [DialogButton] 是 heightIn 在前、
 * `NotConfiguredCard` 的出口是 heightIn 在后，两处 connected 用例都实测 ≥48dp，即双证）。
 * `EntryRow` 真机量到 40dp 的「假面」成因是 **`padding` 排在 `clickable` 之前**把可点面挤回内容盒
 * （56 − 8 − 8 = 40，原始现场逐字记在 `EntryRow.kt:70-75`）；`InlineNotice` 动作槽量到 15.24dp
 * 是**链上压根没有 48dp 下限**（变异自证 ⑨），两者都不是 heightIn 的先后问题。
 *
 * `testTag` 与可点语义并到同一枚节点（`mergeDescendants = true`）：标签在父、click 在子的写法会让
 * `onNodeWithTag(…).performClick()` 打在没有 click action 的节点上（`InlineNotice` 动作槽同一处置）。
 *
 * 排印取 [AppType.ButtonLabel]（15sp/600，与全站唯一主按钮 `PillButton` 同一档；原先本处读 `AppType.Title`
 * 的 17sp 是**第二套按钮字号**，同一屏里药片与对话框按钮并排时读起来不像同一档）。
 * **不宣称大字豁免**：15sp ≈ 11.25pt 够不到
 * WCAG 的 14pt bold / 18pt regular 线，所以三档墨色一律按正文 4.5 判（见 [dialogButtonInk] 的 KDoc）。
 */
@Composable
internal fun DialogButton(
    role: DialogButtonRole,
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val ink = dialogButtonInk(role, appColors())
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(ink.container)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = tag },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = AppType.ButtonLabel, color = ink.label)
    }
}

/**
 * 破坏性确认对话框：级联条数 + 不受影响的保留面 + 不可撤销，一次说全（spec §3）。
 *
 * [state] 是显隐开关（`false` 时不组合任何节点，与现网各屏私有 `AlertDialog` 的
 * `if (showDialog) { … }` 同口径），批 2/3 各屏接的是「ViewModel 的确认请求态」而不是自建 Boolean。
 *
 * [cascadeLines] / [keepLines] 由调用方逐字给（组件不猜条数、不猜保留面），[irreversible] 默认 `true`
 * ——默认值取「更严格的那一档」是刻意的：忘记传就是明说不可撤销，而不是悄悄少说一句安全信息。
 * [confirmLabel] 也留给调用方（「删除词书」「清空记录」各说各的动作），只有正文措辞收进纯函数。
 *
 * 排印按 2026-11-05 弹窗排印统一批的三档口径：标题 [AppType.Title]、正文 [AppType.Body]（原先是 `Footnote`
 * 13sp——多行安全说明用最小档读起来费力，且与三枚玻璃面板里的 `Body` 不一致）、出口按钮 [AppType.ButtonLabel]。
 *
 * 两枚按钮的标签是 `confirm_destructive_confirm` / `confirm_destructive_dismiss`：
 * 批 2/3 各屏与 Task 12 的份数棘轮都按这两个 tag 定位（与 `EntryRow` 的 `entry_row_icon`、
 * `InlineNotice` 的 `inline_notice_action` 同一存在理由）。整枚对话框恰两个可点节点，
 * 面板本体不是触控目标（点 scrim 走 [onDismiss]，不建可点节点）。
 */
@Composable
fun ConfirmDestructiveDialog(
    state: Boolean,
    title: String,
    cascadeLines: List<String>,
    keepLines: List<String>,
    irreversible: Boolean = true,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state) return
    val set = appColors()
    val body = destructiveDialogBody(cascadeLines, keepLines, irreversible)
    DialogPanel(onDismissRequest = onDismiss) {
        Text(text = title, style = AppType.Title, color = set.text1)
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            body.forEach { line ->
                Text(text = line, style = AppType.Body, color = set.text1)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DialogButton(
                role = DialogButtonRole.Dismiss,
                label = "取消",
                onClick = onDismiss,
                tag = "confirm_destructive_dismiss",
                modifier = Modifier.weight(1f),
            )
            DialogButton(
                role = DialogButtonRole.DestructiveConfirm,
                label = confirmLabel,
                onClick = onConfirm,
                tag = "confirm_destructive_confirm",
                modifier = Modifier.weight(1f),
            )
        }
    }
}
