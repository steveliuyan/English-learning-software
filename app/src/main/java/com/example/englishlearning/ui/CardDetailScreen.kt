package com.example.englishlearning.ui

import android.graphics.BitmapFactory
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
 * The screen stays UI-only: it receives a [WordCard] and explicit callbacks for navigation,
 * pronunciation and relearning. Storage, provider selection and learning state remain outside
 * the composable.
 */
@Composable
fun CardDetailScreen(
    card: WordCard,
    onBack: () -> Unit,
    onSpeak: () -> Unit = {},
    onRelearn: () -> Unit = {},
    // 安全边界：详情页只收无字段的发音状态枚举，固定文案由本屏映射——
    // 任意字符串（含潜在敏感内容）无法从调用方塞进详情页。
    pronunciationStatus: PronunciationStatus = PronunciationStatus.Idle,
    onAskAi: () -> Unit = {},
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onSpeak,
                        modifier = Modifier.semantics { contentDescription = "播放 ${card.lemma} 发音" },
                    ) { Text("播放发音") }
                    TextButton(
                        onClick = onRelearn,
                        modifier = Modifier.semantics { contentDescription = "重新学习 ${card.lemma}" },
                    ) { Text("重新学习") }
                    TextButton(
                        onClick = onAskAi,
                        modifier = Modifier
                            .testTag("card_detail_ask_ai")
                            .semantics { contentDescription = "问 AI（${card.lemma}）" },
                    ) { Text("问 AI") }
                }
                pronunciationStatus.message()?.let { message ->
                    Text(
                        text = message,
                        color = MintTextMuted,
                        modifier = Modifier
                            .testTag("card_detail_pronunciation_message")
                            .semantics { contentDescription = message },
                    )
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

        if (card.senses.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MintSurface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MintOutline, RoundedCornerShape(24.dp))
                    .testTag("card_detail_senses"),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.senses.forEach { sense ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = sense.partOfSpeech,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MintPrimary,
                            )
                            Text(
                                text = sense.meaningZh,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MintPrimaryDark,
                            )
                        }
                    }
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

        if (!card.exampleZh.isNullOrBlank()) {
            val exampleZh = card.exampleZh
            Text(
                text = exampleZh,
                style = MaterialTheme.typography.bodySmall,
                color = MintTextMuted,
                modifier = Modifier
                    .testTag("card_detail_example_zh")
                    .semantics { contentDescription = "例句译文 $exampleZh" },
            )
        }

        if (card.derived.isNotEmpty()) {
            WordRelationSection(
                title = "派生词",
                tag = "card_detail_derived",
                lines = card.derived.map { "${it.lemma} ${it.partOfSpeech} ${it.meaningZh}" },
            )
        }

        if (card.phrases.isNotEmpty()) {
            WordRelationSection(
                title = "关联短语",
                tag = "card_detail_phrases",
                lines = card.phrases.map { "${it.text} ${it.meaningZh}" },
            )
        }

        if (card.synonyms.isNotEmpty()) {
            WordRelationSection(
                title = "近义词",
                tag = "card_detail_synonyms",
                lines = card.synonyms.map { "${it.lemma} ${it.partOfSpeech} ${it.meaningZh}" },
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

        // 配图优先用词书包导入的真实文件（`imagePath`）；没有才回退到内置 drawable 映射。
        // 两条路都渲染在同一个 `card_detail_illustration` 卡片里，界面看不出差别。
        val importedImage = card.imagePath
        if (!importedImage.isNullOrBlank()) {
            ImportedIllustration(lemma = card.lemma, path = importedImage)
        } else {
            lemmaToDrawableRes(card.lemma)?.let { res ->
                IllustrationCard {
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

/**
 * 导入配图的渲染：文件被外部删除或解码失败时**不留空图框**，改为一行可读提示。
 * 与内置图一样，缺图时不该让整个详情页崩掉——词卡的其他内容仍然可用。
 */
@Composable
private fun ImportedIllustration(lemma: String, path: String) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    if (bitmap == null) {
        Text(
            text = "配图暂时读不出来，可重新导入这册词书。",
            style = MaterialTheme.typography.bodySmall,
            color = MintTextMuted,
            modifier = Modifier
                .testTag("card_detail_image_unreadable")
                .semantics { contentDescription = "配图读不出来" },
        )
        return
    }
    IllustrationCard {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "配图 $lemma",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_detail_image_file"),
        )
    }
}

@Composable
private fun IllustrationCard(content: @Composable () -> Unit) {
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
        ) { content() }
    }
}

/** 派生词 / 关联短语 / 近义词共用的小节版式：一个标题 + 若干行。 */
@Composable
private fun WordRelationSection(title: String, tag: String, lines: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MintSurface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MintOutline, RoundedCornerShape(24.dp))
            .testTag(tag),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MintPrimaryDark,
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MintTextMuted,
                )
            }
        }
    }
}
