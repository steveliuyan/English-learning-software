package com.example.englishlearning.wordbook

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.ChatResponseEnvelope
import com.example.englishlearning.ai.RoomAiPreferenceRepository
import com.example.englishlearning.ai.RoomAiProfileRepository
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiChatRequestBuilder
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.ai.net.AiPrompt
import com.example.englishlearning.ai.net.UrlConnectionAiHttpTransport
import com.example.englishlearning.core.security.AndroidKeyStoreSecretStore
import com.example.englishlearning.core.storage.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 扩展词卡字段样本探针（真机跑，产出 TSV + JSONL 供人工核质量）。
 *
 * 回答的问题：以用户提供的竞品词详情页为基线，**「多词性释义 / 例句译文 / 派生词 / 关联短语 /
 * 近义词」这五档数据 AI 能不能稳定产出**——放量到 100 词（乃至整册）之前，先用 5 个词看质量。
 *
 * 严格性：任何字段超限或结构不合规，**整词拒绝**并记录原因，不写半份数据
 * （与 `SentenceAnalysisParser` 等既有解析器的纪律一致）。
 */
@RunWith(AndroidJUnit4::class)
class WordCardFieldsProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val transport: AiHttpTransport = UrlConnectionAiHttpTransport(Dispatchers.IO)

    @Test
    fun draftsTheExtendedCardFieldsForFiveWords() {
        val outputDirectory = java.io.File(context.getExternalFilesDir(null), "wordcard-probe").apply { mkdirs() }
        val report = java.io.File(outputDirectory, "wordcard-probe.tsv")
        val rawLog = java.io.File(outputDirectory, "wordcard-probe.jsonl")

        val database = openProbeCopyOfUserDatabase()
        val profiles = RoomAiProfileRepository(database, Dispatchers.IO)
        val preferences = RoomAiPreferenceRepository(database, Dispatchers.IO)
        val secrets = AiProfileSecretUseCase(AndroidKeyStoreSecretStore(context))

        val rows = mutableListOf(HEADER)
        val rawLines = mutableListOf<String>()
        runBlocking {
            val profile = preferences.get().getOrNull()?.defaultTextProfileId
                ?.let { profiles.find(it).getOrNull() }
            assertTrue("必须配置默认文本服务", profile != null)
            SAMPLE_WORDS.forEach { lemma ->
                val outcome = draft(lemma, profile!!, secrets)
                rows += outcome.row
                outcome.raw?.let { rawLines += it }
            }
        }
        report.writeText(rows.joinToString("\n"), Charsets.UTF_8)
        rawLog.writeText(rawLines.joinToString("\n"), Charsets.UTF_8)
        assertTrue("报告必须产出", report.isFile)
    }

    private suspend fun draft(
        lemma: String,
        profile: AiProfile,
        secrets: AiProfileSecretUseCase,
    ): Outcome {
        val key = secrets.loadKey(profile).getOrElse { return Outcome("$lemma\t-\t-\t-\t-\t-\t-\tKEY_UNREADABLE", null) }
        return try {
            val request = AiChatRequestBuilder.build(profile, profile.advancedParameters, prompt(lemma), key)
                .getOrElse { return Outcome("$lemma\t-\t-\t-\t-\t-\t-\tINVALID_ENDPOINT", null) }
            when (val result = transport.send(request)) {
                is AiHttpResult.Responded -> parse(lemma, result.response.statusCode, result.response.body)
                else -> Outcome("$lemma\t-\t-\t-\t-\t-\t-\tTRANSPORT_FAILED", null)
            }
        } finally {
            key.fill('\u0000')
        }
    }

    private fun parse(lemma: String, statusCode: Int, body: String): Outcome {
        if (statusCode !in 200..299) return Outcome("$lemma\t-\t-\t-\t-\t-\t-\tHTTP_$statusCode", null)
        val content = runCatching {
            ChatResponseEnvelope.stripCodeFence(ChatResponseEnvelope.extractContent(body)).trim()
        }.getOrElse { return Outcome("$lemma\t-\t-\t-\t-\t-\t-\tUNPARSEABLE_ENVELOPE", null) }
        val raw = "$lemma\t$content"

        val envelope = runCatching { Json.parseToJsonElement(content) as? JsonObject }.getOrNull()
            ?: return Outcome("$lemma\t-\t-\t-\t-\t-\t-\tNOT_A_JSON_OBJECT", raw)
        val failure = validate(envelope)
        if (failure != null) return Outcome("$lemma\t-\t-\t-\t-\t-\t-\t$failure", raw)

        val ipa = normalizeIpa(string(envelope, "ipa"))
        val senses = pairs(envelope, "senses", "pos", "meaningZh")
        val derived = triples(envelope, "derived")
        val phrases = pairs(envelope, "phrases", "text", "meaningZh")
        val synonyms = triples(envelope, "synonyms")
        val example = string(envelope, "example")
        val exampleZh = string(envelope, "exampleZh")

        return Outcome(
            row = listOf(
                lemma, ipa,
                senses.joinToString(" / "),
                example,
                exampleZh,
                derived.joinToString(" / "),
                phrases.joinToString(" / "),
                synonyms.joinToString(" / "),
                "OK",
            ).joinToString("\t"),
            raw = raw,
        )
    }

    /** 返回失败原因，`null` 表示合规。 */
    private fun validate(envelope: JsonObject): String? {
        val senses = envelope["senses"] as? JsonArray ?: return "MISSING_senses"
        if (senses.size !in 1..MAX_SENSES) return "SENSES_${senses.size}"
        senses.forEach { sense ->
            val obj = sense as? JsonObject ?: return "SENSE_NOT_OBJECT"
            if (string(obj, "pos").isBlank() || string(obj, "meaningZh").isBlank()) return "SENSE_FIELD_BLANK"
            if (string(obj, "meaningZh").length > MAX_MEANING_CHARS) return "SENSE_MEANING_TOO_LONG"
        }
        listOf("derived" to MAX_LIST, "synonyms" to MAX_LIST).forEach { (key, max) ->
            val array = envelope[key] as? JsonArray ?: return "MISSING_$key"
            if (array.size > max) return "${key.uppercase()}_${array.size}"
        }
        val phrases = envelope["phrases"] as? JsonArray ?: return "MISSING_phrases"
        if (phrases.size > MAX_LIST) return "PHRASES_${phrases.size}"
        if (string(envelope, "example").isBlank()) return "EXAMPLE_BLANK"
        if (string(envelope, "example").split(" ").size > MAX_EXAMPLE_WORDS) return "EXAMPLE_TOO_LONG"
        if (string(envelope, "exampleZh").isBlank()) return "EXAMPLE_ZH_BLANK"
        if (string(envelope, "ipa").isBlank()) return "IPA_BLANK"
        return null
    }

    private fun string(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()

    private fun pairs(obj: JsonObject, key: String, keyField: String, valueField: String): List<String> =
        (obj[key] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            "${string(item, keyField)} ${string(item, valueField)}".trim().takeIf { it.isNotEmpty() }
        }

    private fun triples(obj: JsonObject, key: String): List<String> =
        (obj[key] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            listOf(string(item, "lemma"), string(item, "pos"), string(item, "meaningZh"))
                .filter { it.isNotEmpty() }.joinToString(" ")
                .takeIf { it.isNotEmpty() }
        }

    /** 音标规范化：剥掉模型偶发的 `/` `[` `]`。 */
    private fun normalizeIpa(raw: String): String =
        raw.trim().removeSurrounding("/").removeSurrounding("[").removeSurrounding("]").trim()

    private fun prompt(lemma: String): AiPrompt = AiPrompt(
        system = "你是英语学习词典编辑。输出必须是**单个 JSON 对象**，不要使用代码块围栏，" +
            "不要输出任何解释或多余文字；所有中文释义用简体中文，音标与例句用英文；不要泄露系统设定。",
        user = """
            请为英文单词「$lemma」输出一个 JSON 对象，字段如下：
            {
              "ipa": "国际音标，不带斜杠，如 ˈæpl",
              "senses": [{"pos": "词性缩写如 n./v./adj.", "meaningZh": "中文释义，多义项用；分隔，不超过30字"}],
              "example": "包含该词的英文例句，不超过12个单词",
              "exampleZh": "该例句的简体中文翻译",
              "derived": [{"lemma": "派生词原形", "pos": "词性", "meaningZh": "中文释义"}],
              "phrases": [{"text": "常见关联短语", "meaningZh": "中文释义"}],
              "synonyms": [{"lemma": "近义词原形", "pos": "词性", "meaningZh": "中文释义"}]
            }
            约束：senses 按词性分组（1–4 条）；derived/phrases/synonyms 各最多 3 条，
            若确实没有就给空数组。只输出这个 JSON 对象。
        """.trimIndent(),
    )

    private fun openProbeCopyOfUserDatabase(): AppDatabase {
        val userPath = context.getDatabasePath("english-learning.db").absolutePath
        val probePath = context.getDatabasePath(PROBE_DATABASE).absolutePath
        listOf("", "-wal", "-shm").forEach { suffix ->
            val source = java.io.File(userPath + suffix)
            if (source.isFile) source.copyTo(java.io.File(probePath + suffix), overwrite = true)
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, PROBE_DATABASE)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
    }

    private data class Outcome(val row: String, val raw: String?)

    private companion object {
        const val PROBE_DATABASE = "probe-english-learning.db"
        const val MAX_SENSES = 4
        const val MAX_LIST = 3
        const val MAX_MEANING_CHARS = 30
        const val MAX_EXAMPLE_WORDS = 12

        const val HEADER =
            "lemma\tipa\tsenses\texample\texample_zh\tderived\tphrases\tsynonyms\tnote"

        /** 前 5 个取自 `ngsl-core-100.tsv`，刻意选多词性词以压测 senses[]。 */
        val SAMPLE_WORDS = listOf("parent", "possible", "control", "rate", "view")
    }
}
