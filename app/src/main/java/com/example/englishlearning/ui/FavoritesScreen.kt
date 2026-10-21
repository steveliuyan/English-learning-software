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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.FavoriteWord
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted
import java.time.ZoneId

/**
 * F4-01 决策⑦收藏页：按收藏时间降序列出收藏词；点行打开只读词卡详情
 * （由宿主把 [onOpenWord] 接到回源加载），行内「取消收藏」即时生效。
 * UI-only：数据与动作全部来自 [FavoritesUiState] 与回调。
 */
@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    onBack: () -> Unit,
    onOpenWord: (FavoriteWord) -> Unit,
    onUnfavorite: (FavoriteWord) -> Unit,
    zoneId: ZoneId = ZoneId.systemDefault(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("favorites_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // F5-03 / Task 11（§E.2，Task 10 评审 I-4）：头部 Row 带 `fillMaxWidth()` 在前的 `horizontalScroll`。
        // 取证（Task 11 评审 I-1）：该顺序**不产生滚动范围**，本轮未据此宣称「返回链可滚进视口」已成立；
        // 留着它只钉住修饰符顺序（反序会把 weight(1f) 标题挤成 0 宽）。真实修法归 UI 重设计轮（L-T11-1），
        // 逐位取证见 CheckInScreen.kt 的同处注释。
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                "我的收藏",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MintPrimaryDark,
                modifier = Modifier.weight(1f),
            )
            Text(
                "返回",
                color = MintPrimaryDark,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .testTag("favorites_back")
                    .semantics { contentDescription = "返回" }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(onClick = onBack)
                    .padding(10.dp),
            )
        }
        when (state) {
            FavoritesUiState.Loading -> Text("正在读取收藏…", color = MintTextMuted)
            FavoritesUiState.Unavailable -> Text(
                "收藏暂时无法读取，请稍后重试",
                color = MintTextMuted,
                modifier = Modifier.testTag("favorites_unavailable"),
            )
            is FavoritesUiState.Ready -> {
                state.notice?.let {
                    Text(it, color = MintTextMuted, modifier = Modifier.testTag("favorites_notice"))
                }
                if (state.words.isEmpty()) {
                    Text(
                        "还没有收藏。在词卡详情页点星标即可收藏。",
                        color = MintTextMuted,
                        modifier = Modifier.testTag("favorites_empty"),
                    )
                } else {
                    state.words.forEachIndexed { index, favorite ->
                        FavoriteRow(index = index, favorite = favorite, zoneId = zoneId, onOpen = { onOpenWord(favorite) }, onUnfavorite = { onUnfavorite(favorite) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    index: Int,
    favorite: FavoriteWord,
    zoneId: ZoneId,
    onOpen: () -> Unit,
    onUnfavorite: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(18.dp))
            .testTag("favorites_word_$index")
            .semantics { contentDescription = "收藏 ${favorite.lemma}" }
            .clickable(onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(favorite.lemma, fontWeight = FontWeight.Bold, color = MintPrimaryDark)
                Text(
                    // m-6（修复轮 2）：时间戳档由本页原来自拼的 `toLocalDate()` 改为共享
                    // `wordListDateLabel`——渲染结果同一档、同一句（`yyyy-MM-dd`），但「收藏于」的格式
                    // 从此只有一处定义，与「我的词」收藏段并存期间不可能再分叉。
                    "收藏于 ${wordListDateLabel(favorite.addedAt, zoneId)}",
                    color = MintTextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(
                onClick = onUnfavorite,
                colors = ButtonDefaults.textButtonColors(contentColor = MintTextMuted),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .testTag("favorites_unfavorite_$index")
                    .semantics { contentDescription = "取消收藏 ${favorite.lemma}" },
            ) { Text("取消收藏") }
        }
    }
}
