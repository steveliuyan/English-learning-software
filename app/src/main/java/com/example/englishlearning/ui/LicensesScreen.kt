package com.example.englishlearning.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.notices.ThirdPartyNotice
import com.example.englishlearning.notices.parseThirdPartyNotices
import com.example.englishlearning.notices.thirdPartyPrivacyStatements
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

/**
 * F5-04「关于与开源许可」页（AC5-04）。数据源是构建期从 `docs/third-party-notices.md`
 * 同步进 assets 的台账（决策 2026-10-15 §1）——门禁校验什么，这里就展示什么。
 * 台账读不到时**如实显示不可用**，绝不渲染硬编码假台账（零假入口原则）。
 */
@Composable
fun LicensesScreen(markdown: String, onBack: () -> Unit) {
    val notices = remember(markdown) { parseThirdPartyNotices(markdown) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MintBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("licenses_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // F5-03 / Task 11（§E.2，Task 10 评审 I-4）：标题 + 返回链所在 Row 原不可横向滚动，
        // 大字号下标题会被屏宽截断 → 补 horizontalScroll。语义与 testTag 一字未动。
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "‹",
                style = MaterialTheme.typography.headlineSmall,
                color = MintPrimary,
                modifier = Modifier
                    .testTag("licenses_back")
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "返回" },
            )
            Text(
                "关于与开源许可",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MintPrimaryDark,
            )
        }
        if (notices.isEmpty()) {
            Text(
                "许可台账暂不可读",
                style = MaterialTheme.typography.bodyMedium,
                color = MintTextMuted,
                modifier = Modifier.testTag("licenses_unavailable"),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MintSurface, RoundedCornerShape(18.dp))
                    .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
                    .padding(14.dp)
                    .testTag("licenses_privacy_statement"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("隐私说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                thirdPartyPrivacyStatements().forEach { statement ->
                    Text(statement, style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notices, key = { it.id }) { notice -> NoticeCard(notice) }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: ThirdPartyNotice) {
    var expanded by remember { mutableStateOf(false) }
    val title = notice.field("名称").ifEmpty { notice.id }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MintSurface, RoundedCornerShape(18.dp))
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("license_entry-${notice.id}")
            .clickable { expanded = !expanded }
            .semantics {
                contentDescription = title
                stateDescription = if (expanded) "已展开" else "已收起"
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
        Text(
            "${notice.field("许可证")} · v${notice.field("版本")}",
            style = MaterialTheme.typography.bodySmall,
            color = MintTextMuted,
        )
        if (expanded) {
            listOf("用途", "数据流", "NOTICE 位置", "替代方案", "商业分发结论").forEach { key ->
                Text("$key：${notice.field(key)}", style = MaterialTheme.typography.bodySmall, color = MintTextMuted)
            }
        }
    }
}
