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
 * 收的是现网 4 份 `AlertDialog` + 1 份行内确认（词书删除、学习数据清空、备份覆盖一类）。这 5 处的现状是
 * 「级联条数、保留面、不可撤销」三件事各屏各写、少写哪件没人发现，所以：
 *  - **措辞**收成纯函数 [destructiveDialogBody]，由 `ConfirmDestructiveWordingTest` 逐要素断言；
 *  - **按钮墨色**收成纯表 [dialogButtonInk]，由 `ConfirmDestructiveInkTest` 逐档复算（connected 读不到颜色，
 *    Task 9 实测口径，所以「哪枚 token 当容器」只能在 JVM 侧钉死）。
 *
 * 面板与按钮两枚内部构件（[DialogPanel] / [DialogButton]）放在本文件、由
 * [OutboundConfirmDialog] 复用：spec §3 数的是九个共享组件，对话框面板不算第十个组件，
 * 另起一份文件会撞 Task 12 的组件份数棘轮；同一份 `DialogPanel` 也保证两枚对话框不会各长一套卡面配方。
 *
 * 已登记的偏离（不是随手替换）：面板**不复用** `glass/GlassDialog.kt`。那一份的容器底是
 * `AppPalette.GlassFill`（静态浅套近不透明白），深色模式下 `appColors()` 取的墨压在它上面会读不出来；
 * 而在本组件里改用静态墨又会新增 `DarkTokenConsumptionRatchetTest` 的未登记静态读点。
 * 故这里按同一几何（外层 24dp 横向留白 + 内层 24dp padding + hero 档圆角）自建面板，
 * 底色改走 `appColors().surface`，两套主题各自成立；`GlassDialog` 留给既有屏继续用，批 3 逐屏收口。
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
 * 不足正文 4.5，深套白字压 `errorFill` 连非文本 3:1 都不过；而 17sp ≈ 12.75pt 够不到 WCAG 大字豁免线
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
 * 底色走 `appColors().surface` 而不是玻璃填充——深色模式下玻璃填充是静态浅值（见文件头登记的偏离）。
 *
 * [onDismissRequest] 由调用方给（返回键 / 点 scrim 都走它），面板本身不决定「取消意味着什么」。
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
 * 修饰符顺序就是契约：`heightIn(min = 48.dp)` 排在 `clickable` **之前**，可点面才是整枚 48dp 盒；
 * 反过来写（`clickable` 后再 `heightIn`/`padding`）可点面会退回内容盒——`EntryRow` 真机量到 40dp
 * 的「假面」教训、`InlineNotice` 动作槽量到 15.24dp 的教训，都是同一条。
 *
 * `testTag` 与可点语义并到同一枚节点（`mergeDescendants = true`）：标签在父、click 在子的写法会让
 * `onNodeWithTag(…).performClick()` 打在没有 click action 的节点上（`InlineNotice` 动作槽同一处置）。
 *
 * 排印取 [AppType.Title]（17sp SemiBold，稿面按钮档）。**不宣称大字豁免**：17sp ≈ 12.75pt 够不到
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
        Text(text = label, style = AppType.Title, color = ink.label)
    }
}

/**
 * 破坏性确认对话框：级联条数 + 不受影响的保留面 + 不可撤销，一次说全（spec §3）。
 *
 * [state] 是显隐开关（`false` 时不组合任何节点，与现网 4 份 `AlertDialog` 的
 * `if (showDialog) { … }` 同口径），批 2/3 各屏接的是「ViewModel 的确认请求态」而不是自建 Boolean。
 *
 * [cascadeLines] / [keepLines] 由调用方逐字给（组件不猜条数、不猜保留面），[irreversible] 默认 `true`
 * ——默认值取「更严格的那一档」是刻意的：忘记传就是明说不可撤销，而不是悄悄少说一句安全信息。
 * [confirmLabel] 也留给调用方（「删除词书」「清空记录」各说各的动作），只有正文措辞收进纯函数。
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
                Text(text = line, style = AppType.Footnote, color = set.text2)
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
