package com.example.englishlearning.imagegen

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.englishlearning.ai.net.AudioHttpRequest
import com.example.englishlearning.ai.net.AudioHttpResult
import com.example.englishlearning.ai.net.AudioHttpTransport
import com.example.englishlearning.ai.net.UrlConnectionAudioHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 词卡配图数据源抽查实验（真机跑，产出报告文件，不做业务断言）。
 *
 * 目的：回答「词卡配图能不能先走公开图库、无命中再交给 AI」——需要三个事实：
 * 1. **手机网络能不能到图库**（本机沙箱到不了，只有设备能证明）；
 * 2. 命中率与返回质量（拿词表和真实返回逐词核对）；
 * 3. 许可是否可用于 App 内展示。
 *
 * 网络通道用 [UrlConnectionAudioHttpTransport]：它强制 HTTPS、不跟随重定向、返回原始字节——
 * 正好适合「取第三方公开数据」这种不可信出站，且**不带任何密钥**（headers 为空）。
 *
 * 报告写到 `getExternalFilesDir/image-probe/report.tsv`，用 adb pull 取回后人工核对。
 */
@RunWith(AndroidJUnit4::class)
class ImageSourcingProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val transport: AudioHttpTransport = UrlConnectionAudioHttpTransport(Dispatchers.IO)

    /** 只取可安全再分发的许可：公共领域（无需署名、可商用、可修改）。 */
    private val licenceFilter = "cc0,pdm"

    @Test
    fun probesPublicImageSourcesForTheRealWordList() {
        val outputDirectory = java.io.File(context.getExternalFilesDir(null), "image-probe").apply { mkdirs() }
        val report = java.io.File(outputDirectory, "report.tsv")

        val rows = mutableListOf("word\tgroup\tresult_count\tfirst_title\tfirst_license\tfirst_url_host\tnote")
        PROBE_WORDS.forEach { (word, group) ->
            rows += probe(word, group)
        }
        report.writeText(rows.joinToString("\n"), Charsets.UTF_8)

        assertTrue("报告文件必须产出", report.isFile)
        assertTrue("报告不能为空", report.length() > 0)
    }

    private fun probe(word: String, group: String): String {
        val url = "https://api.openverse.org/v1/images/?" +
            "q=${java.net.URLEncoder.encode(word, "UTF-8")}&license=$licenceFilter&page_size=3"
        val outcome = runBlocking {
            runCatching { transport.send(AudioHttpRequest(url = url, timeoutSeconds = 25)) }
        }
        val result = outcome.getOrElse { return "$word\t$group\t-\t-\t-\t-\tEXCEPTION:${it.javaClass.simpleName}" }
        return when (result) {
            is AudioHttpResult.Success -> try {
                val text = String(result.body, Charsets.UTF_8)
                val envelope = Json.parseToJsonElement(text) as? JsonObject
                    ?: return "$word\t$group\t-\t-\t-\t-\tUNEXPECTED_SHAPE"
                val count = (envelope["result_count"] as? JsonPrimitive)?.content ?: "-"
                val first = (envelope["results"] as? JsonArray)?.firstOrNull() as? JsonObject
                if (first == null) {
                    "$word\t$group\t$count\t-\t-\t-\tNO_RESULT"
                } else {
                    val title = (first["title"] as? JsonPrimitive)?.content.orEmpty().replace("\t", " ").take(80)
                    val license = (first["license"] as? JsonPrimitive)?.content.orEmpty()
                    val host = runCatching { java.net.URI(first["url"]!!.jsonPrimitive.content).host }.getOrDefault("-")
                    val tags = ((first["tags"] as? JsonArray) ?: JsonArray(emptyList()))
                        .mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.content }
                        .take(6).joinToString(",")
                    "$word\t$group\t$count\t$title\t$license\t$host\t$tags"
                }
            } finally {
                result.body.fill(0)
            }
            is AudioHttpResult.HttpError -> "$word\t$group\t-\t-\t-\t-\tHTTP_${result.statusCode}".also { result.body.fill(0) }
            AudioHttpResult.InsecureUrl -> "$word\t$group\t-\t-\t-\t-\tINSECURE_URL"
            AudioHttpResult.NetworkUnavailable -> "$word\t$group\t-\t-\t-\t-\tNETWORK_UNAVAILABLE"
            AudioHttpResult.TimedOut -> "$word\t$group\t-\t-\t-\t-\tTIMEOUT"
            AudioHttpResult.ResponseTooLarge -> "$word\t$group\t-\t-\t-\t-\tRESPONSE_TOO_LARGE"
            AudioHttpResult.Cancelled -> "$word\t$group\t-\t-\t-\t-\tCANCELLED"
        }
    }

    private companion object {
        /**
         * 前 12 个是应用当前真实可见的词卡（PlaceholderWordCardSource）；其余为四级常见词，
         * 按「具体名词 / 抽象名词 / 动词 / 形容词副词」分组，用来对比不同类型词的图库适配度。
         */
        val PROBE_WORDS: List<Pair<String, String>> = listOf(
            "ability" to "抽象名词", "achieve" to "动词", "benefit" to "抽象名词", "climate" to "具体名词",
            "develop" to "动词", "economy" to "抽象名词", "feature" to "抽象名词", "generous" to "形容词",
            "influence" to "抽象名词", "maintain" to "动词", "obvious" to "形容词", "reduce" to "动词",

            "apple" to "具体名词", "umbrella" to "具体名词", "bicycle" to "具体名词", "kitchen" to "具体名词",
            "waterfall" to "具体名词", "airport" to "具体名词", "bottle" to "具体名词", "camera" to "具体名词",
            "desert" to "具体名词", "elephant" to "具体名词", "forest" to "具体名词", "guitar" to "具体名词",
            "hamster" to "具体名词", "island" to "具体名词", "jacket" to "具体名词", "kettle" to "具体名词",
            "ladder" to "具体名词", "mountain" to "具体名词", "notebook" to "具体名词", "orange" to "具体名词",
            "pencil" to "具体名词", "rainbow" to "具体名词", "sandwich" to "具体名词", "telescope" to "具体名词",

            "courage" to "抽象名词", "freedom" to "抽象名词", "justice" to "抽象名词", "memory" to "抽象名词",
            "however" to "连词", "consider" to "动词", "improve" to "动词", "recognize" to "动词",
            "necessary" to "形容词", "confident" to "形容词", "suddenly" to "副词",
            "bank" to "多义词", "spring" to "多义词", "charge" to "多义词",
        )
    }
}
