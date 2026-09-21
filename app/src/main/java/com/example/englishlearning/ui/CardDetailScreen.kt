package com.example.englishlearning.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.englishlearning.learning.domain.WordCard
import com.example.englishlearning.ui.theme.MintBackground
import com.example.englishlearning.ui.theme.MintOutline
import com.example.englishlearning.ui.theme.MintPrimary
import com.example.englishlearning.ui.theme.MintPrimaryDark
import com.example.englishlearning.ui.theme.MintSurface
import com.example.englishlearning.ui.theme.MintTextMuted

/**
 * Static, read-only detail view for a single word card (spec F1-06, slice 2).
 *
 * Every content module renders only when its data is present. A blank field hides
 * the whole module: no empty heading, no "暂无" placeholder (AC1-12). The
 * illustration module is likewise absent when [lemmaToDrawableRes] finds no bundled
 * picture for the lemma.
 *
 * This screen is deliberately wired to nothing: it takes the [WordCard] to show and
 * an [onBack] callback only. The ViewModel detail state, the per-tier settings
 * toggles and the navigation hand-off are delivered by later slices, so none of
 * that is touched here.
 */
@Composable
fun CardDetailScreen(
    card: WordCard,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MintBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("card_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "词义详情",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MintPrimaryDark,
            modifier = Modifier.semantics { contentDescription = "词义详情" },
        )
        Button(
            onClick = onBack,
            modifier = Modifier
                .testTag("card_detail_back")
                .semantics { contentDescription = "返回" },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MintSurface, contentColor = MintPrimaryDark),
        ) { Text("← 返回", fontWeight = FontWeight.Bold) }

        Card(
            colors = CardDefaults.cardColors(containerColor = MintSurface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MintOutline, RoundedCornerShape(24.dp)),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = card.lemma,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MintPrimaryDark,
                    modifier = Modifier
                        .testTag("card_detail_lemma")
                        .semantics { contentDescription = card.lemma },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (card.ipa.isNotBlank()) {
                        Text(
                            text = card.ipa,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MintTextMuted,
                            modifier = Modifier
                                .testTag("card_detail_ipa")
                                .semantics { contentDescription = "音标 ${card.ipa}" },
                        )
                    }
                    if (card.partOfSpeech.isNotBlank()) {
                        Text(
                            text = card.partOfSpeech,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MintPrimary,
                            modifier = Modifier
                                .testTag("card_detail_part_of_speech")
                                .semantics { contentDescription = "词性 ${card.partOfSpeech}" },
                        )
                    }
                }
                if (card.meaningZh.isNotBlank()) {
                    Text(
                        text = card.meaningZh,
                        style = MaterialTheme.typography.titleMedium,
                        color = MintPrimaryDark,
                        modifier = Modifier
                            .testTag("card_detail_meaning")
                            .semantics { contentDescription = "释义 ${card.meaningZh}" },
                    )
                }
            }
        }

        if (!card.example.isNullOrBlank()) {
            val example = card.example
            Text(
                text = example,
                style = MaterialTheme.typography.bodyMedium,
                color = MintTextMuted,
                modifier = Modifier
                    .testTag("card_detail_example")
                    .semantics { contentDescription = "例句 $example" },
            )
        }

        if (card.inflections.isNotEmpty()) {
            val inflections = card.inflections.joinToString("、")
            Text(
                text = "词形变化：$inflections",
                style = MaterialTheme.typography.bodySmall,
                color = MintTextMuted,
                modifier = Modifier
                    .testTag("card_detail_inflections")
                    .semantics { contentDescription = "词形变化 $inflections" },
            )
        }

        lemmaToDrawableRes(card.lemma)?.let { res ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MintSurface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MintOutline, RoundedCornerShape(24.dp)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(res),
                        contentDescription = "说明图 ${card.lemma}",
                        modifier = Modifier
                            .size(160.dp)
                            .testTag("card_detail_illustration"),
                    )
                }
            }
        }
    }
}
