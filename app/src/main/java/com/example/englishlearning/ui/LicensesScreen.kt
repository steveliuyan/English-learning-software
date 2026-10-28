package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.englishlearning.notices.ThirdPartyNotice
import com.example.englishlearning.notices.parseThirdPartyNotices
import com.example.englishlearning.notices.thirdPartyPrivacyStatements
import com.example.englishlearning.ui.components.AppScreenHeader
import com.example.englishlearning.ui.components.BareRow
import com.example.englishlearning.ui.components.SoftCard
import com.example.englishlearning.ui.components.glass.PillButton
import com.example.englishlearning.ui.components.glass.PillStyle
import com.example.englishlearning.ui.theme.AppDomain
import com.example.englishlearning.ui.theme.AppType
import com.example.englishlearning.ui.theme.appColors

/**
 * F5-04「开源许可与隐私说明」页（AC5-04）。数据源是构建期从 `docs/third-party-notices.md`
 * 同步进 assets 的台账（决策 2026-10-15 §1）——门禁校验什么，这里就展示什么。
 * 台账读不到时**如实显示不可用**，绝不渲染硬编码假台账（零假入口原则）。
 *
 * 批 5 Task 3 按 spec §5「许可证」行重排（重做口径 =「空态带重试；入口名与页名统一」）：
 *  1. 页头换共享 `AppScreenHeader`，页名逐字「开源许可与隐私说明」——与 `SettingsScreen.kt` 的入口名同句；
 *     旧「‹」字形借 `headlineSmall` 凑字号的裸标题与锚 `licenses_back` 一并退役（页头返回位固定
 *     testTag `app_screen_header` + 可及名「返回」）；
 *  2. 九枚静态 `Mint*` 直读与 `MaterialTheme.typography` 归零：底色 `appColors().background`、
 *     分组 `SoftCard`、墨 `text1`/`text2`、排印六档 `AppType`；
 *  3. 标题行那层 `horizontalScroll` 下线。它原本是「标题被屏宽截断」的补丁（F5-03 / Task 10 评审 I-4），
 *     但页头本来就带两行标题容量（`maxLines = 2` + `Clip`），把长文本交给纵向滚动才是正解——
 *     横向滚动会让用户以为这一页是横向排版，而下面整段隐私说明根本滚不到；
 *  4. 空态补「重新读取」次键 `licenses_retry`：`onRetry` 由宿主实现（重读 assets，见 `AppScreen`），
 *     形参**没有默认值**——默认 `= {}` 会让这枚键变成点了没反应的假按钮。
 *
 * 台账与隐私说明住同一个 `LazyColumn`（隐私说明是首条 `item`）：两者都是长文本，
 * 各自开会把一屏切成两段滚动。
 *
 * [markdown] 变化时重新解析（`remember(markdown)`）：重试成功换了新文本，条目要跟着换。
 */
@Composable
fun LicensesScreen(
    markdown: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val notices = remember(markdown) { parseThirdPartyNotices(markdown) }
    val colors = appColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("licenses_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppScreenHeader(
            title = "开源许可与隐私说明",
            subtitle = "随应用内置的第三方清单，与仓库台账同一份",
            onBack = onBack,
        )
        if (notices.isEmpty()) {
            UnavailableLedger(onRetry = onRetry)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // 台账容器的稳定锚：条目在 2× 字号下会滚出组合，connected 侧先滚到条目再取证。
                    .testTag("licenses_ledger"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { PrivacyStatement() }
                items(notices, key = { it.id }) { notice -> NoticeCard(notice) }
            }
        }
    }
}

/** 空态：如实说不可用 + 给出路（重读 assets，不联网不改数据）。 */
@Composable
private fun UnavailableLedger(onRetry: () -> Unit) {
    val colors = appColors()
    SoftCard {
        Text(
            text = "许可台账暂不可读",
            style = AppType.Body,
            color = colors.text1,
            modifier = Modifier.testTag("licenses_unavailable"),
        )
        Text(
            text = "应用内置的第三方清单没有读到。重新读取只是再读一次这个文件：不联网，也不改动任何数据。",
            style = AppType.Footnote,
            color = colors.text2,
        )
        PillButton(
            text = "重新读取",
            onClick = onRetry,
            style = PillStyle.Secondary,
            domain = AppDomain.Settings,
            testTag = "licenses_retry",
        )
    }
}

/** 隐私说明：整段长文本，只讲一次（条目台账在它下面）。 */
@Composable
private fun PrivacyStatement() {
    val colors = appColors()
    SoftCard(modifier = Modifier.testTag("licenses_privacy_statement")) {
        Text(text = "隐私说明", style = AppType.Body, color = colors.text1)
        thirdPartyPrivacyStatements().forEach { statement ->
            Text(text = statement, style = AppType.Footnote, color = colors.text2)
        }
    }
}

/**
 * 单条台账：首行一条 `BareRow`（名称 / 许可证·版本 / 展开态），点开原地补五枚字段。
 *
 * 可及名与展开态播报是既有契约（`AccessibilityStateSemanticsContractTest` 按 `fun NoticeCard(` +
 * `contentDescription = title` 取站点，并要求 `stateDescription` 是那对词语）：重排换容器，
 * **不动播报**。整卡可点（`clickable` 挂在 `SoftCard` 上）：52dp 行高之上再要求用户点那行文字，
 * 在大字号下会把命中面缩到字缝里。
 */
@Composable
private fun NoticeCard(notice: ThirdPartyNotice) {
    var expanded by remember { mutableStateOf(false) }
    val colors = appColors()
    val title = notice.field("名称").ifEmpty { notice.id }
    SoftCard(
        modifier = Modifier
            .testTag("license_entry-${notice.id}")
            .clickable { expanded = !expanded }
            .semantics {
                contentDescription = title
                stateDescription = if (expanded) "已展开" else "已收起"
            },
        contentPadding = PaddingValues(0.dp),
    ) {
        BareRow(
            title = title,
            subtitle = "${notice.field("许可证")} · v${notice.field("版本")}",
            trailing = if (expanded) "收起" else "展开",
        )
        if (expanded) {
            listOf("用途", "数据流", "NOTICE 位置", "替代方案", "商业分发结论").forEach { key ->
                Text(
                    text = "$key：${notice.field(key)}",
                    style = AppType.Footnote,
                    color = colors.text2,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 3.dp),
                )
            }
            Text(
                text = "以上字段与仓库台账 docs/third-party-notices.md 逐字一致",
                style = AppType.Label,
                color = colors.text2,
                modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 4.dp, bottom = 12.dp),
            )
        }
    }
}
