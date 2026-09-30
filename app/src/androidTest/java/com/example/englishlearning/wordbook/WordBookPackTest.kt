package com.example.englishlearning.wordbook

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ai.AiOutboundConfirmation
import com.example.englishlearning.ai.AiPayloadKind
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.ChatResponseEnvelope
import com.example.englishlearning.ai.DefaultImageProfileResolver
import com.example.englishlearning.ai.DefaultImageProfileResult
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.RoomAiPreferenceRepository
import com.example.englishlearning.ai.RoomAiProfileRepository
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiChatRequestBuilder
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.ai.net.AiPrompt
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpResult
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.ai.net.UrlConnectionAiHttpTransport
import com.example.englishlearning.ai.net.UrlConnectionAudioHttpTransport
import com.example.englishlearning.core.security.AndroidKeyStoreSecretStore
import com.example.englishlearning.core.storage.AppDatabase
import com.example.englishlearning.imagegen.DrawingPromptPolicy
import com.example.englishlearning.imagegen.DrawingPromptResult
import com.example.englishlearning.imagegen.DrawingPromptUseCase
import com.example.englishlearning.imagegen.GeneratedImage
import com.example.englishlearning.imagegen.ImageGenerationResult
import com.example.englishlearning.imagegen.ImageGenerationUseCase
import com.example.englishlearning.learning.WordBookMetadataPolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 词书包批量打包器（真机 run → `adb pull`）。**打包阶段**的工作，不是运行时功能。
 *
 * 每词三步：
 * 1. 文本服务产出扩展词卡字段（多词性释义 / 例句+译文 / 派生 / 短语 / 近义词），严格校验；
 * 2. `DrawingPromptUseCase` 把「词＋中文释义」变成英文生图提示词；
 * 3. `ImageGenerationUseCase` 出图（图片载荷每次确认；本批确认＝用户在打包前的明确授权），
 *    压成 512×512 WebP 落盘。
 *
 * 纪律：
 * - **不碰真库**（复制成 `probe-english-learning.db` 再读）；
 * - **密钥不出进程**（只进 Authorization 头，`finally` 清零，产物无密钥痕迹）；
 * - **可中断续跑**：每完成一项立即落盘（`cards.jsonl` / `manifest.jsonl`），重跑跳过已完成项。
 */
@RunWith(AndroidJUnit4::class)
class WordBookPackTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val textTransport: AiHttpTransport = UrlConnectionAiHttpTransport(Dispatchers.IO)
    private val imageTransport: AiHttpTransport =
        UrlConnectionAiHttpTransport(Dispatchers.IO, maxResponseBytes = 16 * 1024 * 1024)
    private val binaryTransport: AudioHttpTransport = UrlConnectionAudioHttpTransport(Dispatchers.IO)

    private lateinit var outputDirectory: File
    private lateinit var imagesDirectory: File
    private lateinit var cardsFile: File
    private lateinit var manifestLinesFile: File
    private lateinit var progressFile: File
    private lateinit var failuresFile: File

    /**
     * 只重新汇总：从已落盘的 `cards.jsonl` / `manifest.jsonl` 重建 `book.json` 与 `manifest.json`。
     * 不联网、不读数据库、不碰密钥——改汇总逻辑后不必重跑整册（对第三方中转不可用时的排障尤其重要）。
     */
    @Test
    fun reassemblesBookJsonFromAlreadyPackedCards() {
        prepareDirectories()
        val cards = readCards()
        val manifest = readManifest()
        writeBookJson(cards, manifest)
        writeManifestJson(manifest)
        File(outputDirectory, "pack-summary.txt").writeText(
            "bookId=$BOOK_ID\nreassembled=true\ncards=${cards.size}\nimages=${manifest.size}\n" +
                "imageFiles=${imagesDirectory.listFiles()?.size ?: 0}\n",
            Charsets.UTF_8,
        )
        assertTrue("至少要有已落盘的词条可汇总", cards.isNotEmpty())
    }

    private fun prepareDirectories() {
        outputDirectory = File(context.getExternalFilesDir(null), "wordbook-pack/$BOOK_ID").apply { mkdirs() }
        imagesDirectory = File(outputDirectory, "images").apply { mkdirs() }
        cardsFile = File(outputDirectory, "cards.jsonl")
        manifestLinesFile = File(outputDirectory, "manifest.jsonl")
        progressFile = File(outputDirectory, "progress.tsv")
        failuresFile = File(outputDirectory, "failures.tsv")
    }

    @Test
    fun packsTheFirstHundredWordsWithExtendedCardFieldsAndImages() {
        prepareDirectories()

        val database = openProbeCopyOfUserDatabase()
        val profiles = RoomAiProfileRepository(database, Dispatchers.IO)
        val preferences = RoomAiPreferenceRepository(database, Dispatchers.IO)
        val secrets = AiProfileSecretUseCase(AndroidKeyStoreSecretStore(context))

        var done = 0
        var failed = 0
        runBlocking {
            val preference = preferences.get().getOrNull()
            val textProfile = preference?.defaultTextProfileId?.let { profiles.find(it).getOrNull() }
            val imageProfile = preference?.defaultImageProfileId?.let { profiles.find(it).getOrNull() }
            assertTrue("必须配置默认文本服务", textProfile != null)
            assertTrue("必须配置默认生图服务", imageProfile != null)

            val cards = readCards().toMutableMap()
            val manifest = readManifest().toMutableMap()

            WORDS.forEach { (lemma, rank) ->
                val outcome = packOne(lemma, rank, textProfile!!, imageProfile!!, secrets, cards, manifest)
                appendLine(progressFile, "$lemma\t${if (outcome) "done" else "incomplete"}\tcards=${cards.size}\timages=${manifest.size}")
                if (outcome) done++ else failed++
                // 礼貌间隔：连续数百次调用容易被中转限流
                delay(POLITENESS_DELAY_MS)
            }
            writeBookJson(cards, manifest)
            writeManifestJson(manifest)
        }

        File(outputDirectory, "pack-summary.txt").writeText(
            "bookId=$BOOK_ID\nwords=${WORDS.size}\nfullyPacked=$done\nincomplete=$failed\n" +
                "images=${imagesDirectory.listFiles()?.size ?: 0}\n" +
                "imageBytes=${imagesDirectory.listFiles()?.sumOf { it.length() } ?: 0}\n",
            Charsets.UTF_8,
        )
        assertTrue("至少要打包出若干完整词条", done > 0)
    }

    /** true = 该词的字段与配图都已落盘（本轮完成或此前已完成）。 */
    private suspend fun packOne(
        lemma: String,
        rank: Int,
        textProfile: AiProfile,
        imageProfile: AiProfile,
        secrets: AiProfileSecretUseCase,
        cards: MutableMap<String, String>,
        manifest: MutableMap<String, String>,
    ): Boolean {
        val cardJson = cards[lemma] ?: run {
            val drafted = draftCardWithRetry(lemma, rank, textProfile, secrets) ?: return false
            appendLine(cardsFile, drafted)
            cards[lemma] = drafted
            drafted
        }
        if (manifest.containsKey(lemma)) return true

        val meaningForPrompt = firstSenseMeaning(cardJson)
        val entry = generateImage(lemma, meaningForPrompt, textProfile, imageProfile, secrets) ?: return false
        appendLine(manifestLinesFile, entry)
        manifest[lemma] = entry
        return true
    }

    // ------------------------------------------------------------ 文本：扩展字段

    /**
     * 拉一次词条；失败时按原因决定是否重试。
     *
     * 为什么要重试与礼貌间隔：整册 100 词是**连续数百次调用**，中转容易出现 429/超时；
     * 实测第 4 个词起连续失败（第 1~3 词成功），所以必须区分「限流」与「配置错」
     * 并把失败原因落盘，否则只能看到一句 `incomplete` 无从下手。
     */
    private suspend fun draftCardWithRetry(
        lemma: String,
        rank: Int,
        profile: AiProfile,
        secrets: AiProfileSecretUseCase,
    ): String? {
        var attempt = 1
        var outcome = draftCard(lemma, rank, profile, secrets)
        while (outcome.json == null && outcome.reason in RETRYABLE_REASONS && attempt < MAX_ATTEMPTS) {
            delay(RETRY_BACKOFF_MS * attempt)
            attempt++
            outcome = draftCard(lemma, rank, profile, secrets)
        }
        if (outcome.json == null) {
            appendLine(failuresFile, "$lemma\t${outcome.reason}\tattempts=$attempt")
            return null
        }
        return outcome.json
    }

    private suspend fun draftCard(
        lemma: String,
        rank: Int,
        profile: AiProfile,
        secrets: AiProfileSecretUseCase,
    ): CardOutcome {
        val key = secrets.loadKey(profile).getOrElse { return CardOutcome(null, "KEY_UNREADABLE") }
        return try {
            val request = AiChatRequestBuilder.build(profile, profile.advancedParameters, cardPrompt(lemma), key)
                .getOrElse { return CardOutcome(null, "INVALID_ENDPOINT") }
            when (val result = textTransport.send(request)) {
                is AiHttpResult.Responded -> {
                    val code = result.response.statusCode
                    if (code !in 200..299) {
                        // 只记状态码，不记响应体——中转错误正文可能含端点信息
                        CardOutcome(null, "HTTP_$code")
                    } else {
                        buildCardJson(lemma, rank, result.response.body)
                            ?.let { CardOutcome(it, null) }
                            ?: CardOutcome(null, "VALIDATION_OR_PARSE")
                    }
                }
                AiHttpResult.NetworkUnavailable -> CardOutcome(null, "NETWORK_UNAVAILABLE")
                AiHttpResult.TimedOut -> CardOutcome(null, "TIMEOUT")
                AiHttpResult.ResponseTooLarge -> CardOutcome(null, "RESPONSE_TOO_LARGE")
                AiHttpResult.Cancelled -> CardOutcome(null, "CANCELLED")
            }
        } finally {
            key.fill('\u0000')
        }
    }

    private data class CardOutcome(val json: String?, val reason: String?)

    /** 校验通过才产出词条 JSON；任何超限/缺字段整词拒绝。 */
    private fun buildCardJson(lemma: String, rank: Int, body: String): String? {
        val content = runCatching {
            ChatResponseEnvelope.stripCodeFence(ChatResponseEnvelope.extractContent(body)).trim()
        }.getOrNull() ?: return null
        val envelope = runCatching { Json.parseToJsonElement(content) as? JsonObject }.getOrNull() ?: return null
        if (validate(envelope) != null) return null
        return buildJsonObject {
            put("cardId", "$BOOK_ID:$lemma")
            put("lemma", lemma)
            put("rank", rank)
            put("ipa", normalizeIpa(string(envelope, "ipa")))
            put("senses", envelope["senses"]!!)
            put("example", string(envelope, "example"))
            put("exampleZh", string(envelope, "exampleZh"))
            put("derived", envelope["derived"]!!)
            put("phrases", envelope["phrases"]!!)
            put("synonyms", envelope["synonyms"]!!)
        }.toString()
    }

    private fun validate(envelope: JsonObject): String? {
        if (string(envelope, "ipa").isBlank()) return "IPA_BLANK"
        val senses = envelope["senses"] as? JsonArray ?: return "MISSING_senses"
        if (senses.size !in 1..MAX_SENSES) return "SENSES_${senses.size}"
        senses.forEach { element ->
            val sense = element as? JsonObject ?: return "SENSE_NOT_OBJECT"
            if (string(sense, "pos").isBlank() || string(sense, "meaningZh").isBlank()) return "SENSE_BLANK"
            if (string(sense, "meaningZh").length > MAX_MEANING_CHARS) return "SENSE_TOO_LONG"
        }
        listOf("derived", "phrases", "synonyms").forEach { key ->
            val array = envelope[key] as? JsonArray ?: return "MISSING_$key"
            if (array.size > MAX_LIST) return "${key.uppercase()}_${array.size}"
        }
        if (string(envelope, "example").isBlank()) return "EXAMPLE_BLANK"
        if (string(envelope, "example").split(" ").size > MAX_EXAMPLE_WORDS) return "EXAMPLE_TOO_LONG"
        if (string(envelope, "exampleZh").isBlank()) return "EXAMPLE_ZH_BLANK"
        return null
    }

    private fun firstSenseMeaning(cardJson: String): String = runCatching {
        val card = Json.parseToJsonElement(cardJson) as JsonObject
        val senses = card["senses"] as JsonArray
        val first = senses.first() as JsonObject
        string(first, "meaningZh")
    }.getOrDefault("")

    private fun string(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()

    private fun normalizeIpa(raw: String): String =
        raw.trim().removeSurrounding("/").removeSurrounding("[").removeSurrounding("]").trim()

    // ------------------------------------------------------------ 配图

    private suspend fun generateImage(
        lemma: String,
        meaningZh: String,
        textProfile: AiProfile,
        imageProfile: AiProfile,
        secrets: AiProfileSecretUseCase,
    ): String? {
        val textHost = java.net.URI(textProfile.endpoint).host ?: return null
        val subject = "$lemma（$meaningZh）".take(DrawingPromptPolicy.maxSubjectLength)
        val draft = when (
            val result = DrawingPromptUseCase(
                defaultTextProfile = DefaultTextProfileResolver { DefaultTextProfileResult.Selected(textProfile) },
                secrets = secrets,
                transport = textTransport,
            ).generate(subject = subject, confirmedTextHost = textHost)
        ) {
            is DrawingPromptResult.Draft -> result.prompt
            else -> return null
        }

        val imageHost = java.net.URI(imageProfile.endpoint).host ?: return null
        val generated = when (
            val result = ImageGenerationUseCase(
                defaultImageProfile = DefaultImageProfileResolver { DefaultImageProfileResult.Selected(imageProfile) },
                secrets = secrets,
                transport = imageTransport,
            ).generate(draft, AiOutboundConfirmation(imageHost, AiPayloadKind.Image, confirmed = true))
        ) {
            is ImageGenerationResult.Generated -> result.image
            else -> return null
        }

        val rawBytes = when (generated) {
            is GeneratedImage.Base64 -> android.util.Base64.decode(generated.data, android.util.Base64.DEFAULT)
            is GeneratedImage.Url -> when (val fetched = binaryTransport.send(AudioHttpRequest(url = generated.url))) {
                is AudioHttpResult.Success -> try { fetched.body.copyOf() } finally { fetched.body.fill(0) }
                else -> return null
            }
        }
        val webp = downscaleToWebp(rawBytes) ?: return null
        File(imagesDirectory, "$lemma.webp").writeBytes(webp)
        return buildJsonObject {
            put("lemma", lemma)
            put("file", "images/$lemma.webp")
            put("sha256", sha256Hex(webp))
            put("bytes", webp.size)
            put("width", TARGET_EDGE)
            put("height", TARGET_EDGE)
            put("model", imageProfile.model)
            put("prompt", draft)
            put("matchedBy", "meaning-zh")
        }.toString()
    }

    private fun downscaleToWebp(bytes: ByteArray): ByteArray? = try {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scaled = Bitmap.createScaledBitmap(decoded, TARGET_EDGE, TARGET_EDGE, true)
        val out = ByteArrayOutputStream()
        scaled.compress(WEBP_FORMAT, WEBP_QUALITY, out)
        decoded.recycle()
        scaled.recycle()
        out.toByteArray()
    } catch (_: Exception) {
        null
    }

    // ------------------------------------------------------------ 落盘与汇总

    private fun readCards(): Map<String, String> = readJsonLines(cardsFile) { obj ->
        (obj["lemma"] as? JsonPrimitive)?.content
    }

    private fun readManifest(): Map<String, String> = readJsonLines(manifestLinesFile) { obj ->
        (obj["lemma"] as? JsonPrimitive)?.content
    }

    private fun readJsonLines(file: File, keyOf: (JsonObject) -> String?): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return file.readLines().mapNotNull { line ->
            runCatching {
                val obj = Json.parseToJsonElement(line) as JsonObject
                keyOf(obj)?.let { it to line }
            }.getOrNull()
        }.toMap()
    }

    /**
     * 汇总词条：把配图引用（file + sha256）合进对应词条，使 `book.json` 自成闭环可校验。
     * 只写 manifest 不写进词条是错的——详情页按词条取图，还要能独立校验。
     */
    private fun writeBookJson(cards: Map<String, String>, manifest: Map<String, String>) {
        val ordered = WORDS.mapNotNull { (lemma, _) ->
            val cardJson = cards[lemma] ?: return@mapNotNull null
            val entryJson = manifest[lemma] ?: return@mapNotNull cardJson
            runCatching {
                val card = Json.parseToJsonElement(cardJson) as JsonObject
                val entry = Json.parseToJsonElement(entryJson) as JsonObject
                JsonObject(
                    card.toMutableMap().apply {
                        put("image", buildJsonObject {
                            put("file", (entry["file"] as JsonPrimitive).content)
                            put("sha256", (entry["sha256"] as JsonPrimitive).content)
                        })
                    },
                ).toString()
            }.getOrDefault(cardJson)
        }
        File(outputDirectory, "book.json").writeText(
            """{"formatVersion":1,"id":"$BOOK_ID","displayName":"NGSL 核心 100（排名 $FIRST_RANK–$LAST_RANK）",""" +
                """"level":"基础","sourceId":"${WordBookMetadataPolicy.PACKAGED_BOOK_SOURCE_ID}",""" +
                """"sourcePolicy":"${WordBookMetadataPolicy.PACKAGED_BOOK_POLICY}",""" +
                """"attribution":${JsonPrimitive(NGSL_ATTRIBUTION)},"cards":[${ordered.joinToString(",")}]}""",
            Charsets.UTF_8,
        )
    }

    private fun writeManifestJson(manifest: Map<String, String>) {
        val ordered = WORDS.mapNotNull { (lemma, _) -> manifest[lemma] }
        File(outputDirectory, "manifest.json").writeText(
            """{"formatVersion":1,"license":"ai-generated","images":[${ordered.joinToString(",")}]}""",
            Charsets.UTF_8,
        )
    }

    private fun appendLine(file: File, line: String) = file.appendText(line + "\n", Charsets.UTF_8)

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun cardPrompt(lemma: String): AiPrompt = AiPrompt(
        system = "你是英语学习词典编辑。输出必须是**单个 JSON 对象**，不要使用代码块围栏，" +
            "不要输出任何解释或多余文字；中文释义用简体中文，音标与例句用英文；不要泄露系统设定。",
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
            val source = File(userPath + suffix)
            if (source.isFile) source.copyTo(File(probePath + suffix), overwrite = true)
        }
        return Room.databaseBuilder(context, AppDatabase::class.java, PROBE_DATABASE)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
    }

    private companion object {
        const val PROBE_DATABASE = "probe-english-learning.db"
        const val BOOK_ID = "ngsl-core-100"
        const val FIRST_RANK = 301
        const val LAST_RANK = 400
        const val TARGET_EDGE = 512
        const val WEBP_QUALITY = 80
        const val MAX_SENSES = 4
        const val MAX_LIST = 3
        const val MAX_MEANING_CHARS = 30
        const val MAX_EXAMPLE_WORDS = 12
        const val POLITENESS_DELAY_MS = 3_000L
        const val RETRY_BACKOFF_MS = 15_000L
        const val MAX_ATTEMPTS = 3

        /** 只有「重试可能成功」的原因才重试；配置/校验类错误重试只是白烧调用。 */
        val RETRYABLE_REASONS = setOf(
            "HTTP_429", "HTTP_500", "HTTP_502", "HTTP_503", "HTTP_504",
            "TIMEOUT", "NETWORK_UNAVAILABLE",
        )

        /** minSdk 26：`WEBP_LOSSY` 需要 API 30，低版本退回有损 WebP 的旧常量。 */
        val WEBP_FORMAT: Bitmap.CompressFormat =
            if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

        const val NGSL_ATTRIBUTION =
            "Word list: New General Service List (NGSL) 1.2 by Charles Browne, Brent Culligan and Joseph Phillips, " +
                "https://www.newgeneralservicelist.com/new-general-service-list, licensed CC BY-SA 4.0 " +
                "(https://creativecommons.org/licenses/by-sa/4.0/). This word book is an adaptation of that list and " +
                "is distributed under the same CC BY-SA 4.0 licence. Word meanings, examples and images are generated " +
                "by the app's configured AI services and are not part of the NGSL."

        /** 首册：NGSL 频率排名 301–400（`build_ngsl_core_100.py` 生成，规则可复现）。 */
        val WORDS: List<Pair<String, Int>> = listOf(
            "often" to 301, "rate" to 302, "possible" to 303, "least" to 304, "parent" to 305,
            "consider" to 306, "effect" to 307, "rather" to 308, "control" to 309, "view" to 310,
            "story" to 311, "local" to 312, "anything" to 313, "together" to 314, "value" to 315,
            "hard" to 316, "stand" to 317, "visit" to 318, "watch" to 319, "color" to 320,
            "party" to 321, "continue" to 322, "bit" to 323, "ever" to 324, "eye" to 325,
            "base" to 326, "concern" to 327, "letter" to 328, "center" to 329, "lose" to 330,
            "yet" to 331, "almost" to 332, "development" to 333, "already" to 334, "test" to 335,
            "probably" to 336, "sale" to 337, "suggest" to 338, "nothing" to 339, "whole" to 340,
            "care" to 341, "deal" to 342, "language" to 343, "send" to 344, "fall" to 345,
            "expect" to 346, "return" to 347, "water" to 348, "allow" to 349, "per" to 350,
            "cause" to 351, "power" to 352, "sit" to 353, "walk" to 354, "mother" to 355,
            "subject" to 356, "develop" to 357, "stay" to 358, "record" to 359, "mind" to 360,
            "remember" to 361, "past" to 362, "office" to 363, "force" to 364, "grow" to 365,
            "town" to 366, "light" to 367, "stop" to 368, "several" to 369, "period" to 370,
            "class" to 371, "matter" to 372, "food" to 373, "social" to 374, "require" to 375,
            "political" to 376, "win" to 377, "decide" to 378, "staff" to 379, "figure" to 380,
            "real" to 381, "future" to 382, "policy" to 383, "answer" to 384, "laugh" to 385,
            "among" to 386, "remain" to 387, "ago" to 388, "type" to 389, "shop" to 390,
            "security" to 391, "receive" to 392, "minute" to 393, "note" to 394, "fund" to 395,
            "top" to 396, "game" to 397, "involve" to 398, "account" to 399, "half" to 400,
        )
    }
}
