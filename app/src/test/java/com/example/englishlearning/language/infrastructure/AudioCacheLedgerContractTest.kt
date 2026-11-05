package com.example.englishlearning.language.infrastructure

import com.example.englishlearning.ui.APP_MAIN_SOURCE_PATH
import com.example.englishlearning.ui.stripComments
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * 「AI 音频缓存不许长出台账之外的裸 WAV」的源码契约（决策 2026-11-05 §5 D-5 与 §2）。
 *
 * 钉四件事：
 *  1. **实现面**：主源码里任何实现 `SynthesizedAudioCache` / `PronunciationAudioCache` 的类，
 *     都必须先读共享台账（`PronunciationAudioMetadata.fromJson(`）再判可播（`.matches(`）——
 *     端侧缓存本轮之前的形状正是「目录里只有裸 WAV，`isFile && length>0` 就交给播放层」；
 *  2. **判据不是摆设**：同一批判据喂一份「裸 WAV 缓存」合成源码必须抓到，喂合规样本必须放过
 *     （本仓源码契约反复踩过「正则写错→永远绿」）；
 *  3. **端侧来源不许与模型资产漂移**：`SynthesizedAudioProvenance.KokoroOnDevice` 里的模型与两个音色
 *     字面量必须在 `KokoroAndroidEngine.kt` 逐字出现，且 `AppModule` 必须显式注入 `provenance =`；
 *  4. **旧的裸摘要文件不许回潮**：`FilePronunciationAudioCache` 不得再写 `.sha256`——
 *     那枚文件既没有大小也没有来源，留着它只会诱导出第二套台账。
 */
class AudioCacheLedgerContractTest {

    @Test
    fun everyAudioCacheImplementationConsultsTheSharedLedgerBeforePlayingBytes() {
        val implementations = audioCacheImplementations()
        assertTrue(
            implementations.isNotEmpty(),
            "主源码里解析不出任何音频缓存实现：识别面失效，本契约不能作为证据（缓存类改名了就先修本闸）",
        )
        val offenders = implementations.filter { (_, text) -> LEDGER_MARKERS.any { !text.contains(it) } }
        assertTrue(
            offenders.isEmpty(),
            "以下音频缓存实现不查完整性台账就把字节交给播放层：" +
                offenders.keys.joinToString("; ") +
                "——AGENTS.md「损坏或替换后不得播放」在它们身上落空",
        )
        assertTrue(
            implementations.keys.any { it == "FileSynthesizedAudioCache.kt" } &&
                implementations.keys.any { it == "FilePronunciationAudioCache.kt" },
            "两套在册缓存（端侧合成 / 云端发音）少了一套：识别面缩了，判据会跟着空转：${implementations.keys}",
        )
    }

    @Test
    fun theJudgeFlagsABareWavCacheInsteadOfQuietlyPassing() {
        val bare = """
            class BareWavCache(private val root: File) : SynthesizedAudioCache {
                override suspend fun read(text: String, accent: PronunciationAccent): ByteArray? {
                    val file = File(root, text)
                    return if (file.isFile && file.length() > 0L) file.readBytes() else null
                }
                override suspend fun write(text: String, accent: PronunciationAccent, wav: ByteArray) {
                    File(root, text).writeBytes(wav)
                }
            }
        """.trimIndent()
        val ledgered = """
            class LedgeredCache(private val root: File) : SynthesizedAudioCache {
                override suspend fun read(text: String, accent: PronunciationAccent): ByteArray? {
                    val metadata = PronunciationAudioMetadata.fromJson(ledgerText())
                    val bytes = audioBytes()
                    return if (metadata != null && metadata.matches(bytes)) bytes else null
                }
                override suspend fun write(text: String, accent: PronunciationAccent, wav: ByteArray) = Unit
            }
        """.trimIndent()

        val consumer = """
            class SomeProvider(private val audioCache: SynthesizedAudioCache? = null) : PronunciationProvider {
                override suspend fun speak(text: String): PronunciationResult {
                    audioCache?.read(text, PronunciationAccent.American)
                    return PronunciationResult.Played
                }
            }
        """.trimIndent()

        assertTrue(
            ledgerViolations(mapOf("BareWavCache.kt" to bare)).isNotEmpty(),
            "判据抓不住「裸 WAV 缓存」的合成样本：上一条对真实文件的绿不能作为证据",
        )
        assertTrue(
            ledgerViolations(mapOf("LedgeredCache.kt" to ledgered)).isEmpty(),
            "判据把查了台账的样本也判成违规：口径过严，会逼维护者放宽判据而不是补台账",
        )
        assertTrue(
            ledgerViolations(mapOf("SomeProvider.kt" to consumer)).isEmpty(),
            "判据把「只是持有缓存字段的消费者」也当成实现：识别面过宽，真树里每个 Provider 都会被误伤",
        )
        assertFalse(
            audioCacheImplementations().keys.contains("KokoroPronunciationProvider.kt"),
            "真实树上 Provider 消费者被误判成缓存实现：识别面需要收窄",
        )
    }

    @Test
    fun theOnDeviceProvenanceCannotDriftAwayFromTheModelAssets() {
        val provenance = strippedOf("language/domain/PronunciationAudioMetadata.kt")
        val engine = strippedOf("language/infrastructure/KokoroAndroidEngine.kt")
        val wiring = strippedOf("di/AppModule.kt")

        ENGINE_ASSET_LITERALS.forEach { literal ->
            assertTrue(engine.contains(literal), "引擎资产常量变了（$literal 已不在 KokoroAndroidEngine.kt）：台账字面量必须同步")
            assertTrue(provenance.contains(literal), "台账里的来源字面量缺了 $literal：端侧音频将无法回答「哪个模型生成的」")
        }
        assertTrue(
            wiring.contains("provenance ="),
            "AppModule 不再显式注入 provenance：Kokoro 缓存会退回默认来源，装配点从此看不见台账的出处",
        )
    }

    @Test
    fun theCloudCacheNoLongerShipsABareChecksumFile() {
        val cloudCache = strippedOf("language/infrastructure/FilePronunciationAudioCache.kt")

        assertFalse(
            cloudCache.contains(".sha256"),
            "云端发音缓存又长出 `.sha256` 裸摘要文件：它既没有大小也没有来源，等于第二套残缺台账（决策 D-1）",
        )
        assertTrue(
            cloudCache.contains("PronunciationAudioMetadata.of("),
            "云端发音缓存不再写台账：来源/大小/创建时间将回退成无处可查",
        )
    }

    /** 主源码里的音频缓存**实现**（不是持有缓存字段的消费者）：文件 → 去注释后的正文。 */
    private fun audioCacheImplementations(): Map<String, String> =
        File(mainSourceDir(), "language/infrastructure")
            .listFiles { f -> f.isFile && f.extension == "kt" }
            .orEmpty()
            .map { it.name to stripComments(readFile(it)) }
            .filter { (_, text) -> IMPLEMENTS_CACHE_PATTERN.containsMatchIn(text) }
            .toMap()

    private fun ledgerViolations(sources: Map<String, String>): List<String> =
        sources.filter { (_, text) -> IMPLEMENTS_CACHE_PATTERN.containsMatchIn(text) }
            .filter { (_, text) -> LEDGER_MARKERS.any { !text.contains(it) } }
            .keys
            .toList()

    private fun mainSourceDir(): File =
        listOf(
            File(APP_MAIN_SOURCE_PATH),
            File("app/$APP_MAIN_SOURCE_PATH"),
        ).firstOrNull { it.isDirectory }
            ?: error("找不到主源码目录（$APP_MAIN_SOURCE_PATH）：本契约将空转，必须先修锚点")

    private fun strippedOf(relativePath: String): String {
        val file = File(mainSourceDir(), relativePath)
        assertTrue(file.isFile, "读不到 $relativePath：判据将空转")
        return stripComments(readFile(file))
    }

    private fun readFile(file: File): String = file.readText(Charsets.UTF_8)

    private companion object {
        /**
         * 实现面（不是持有缓存字段的消费者）：超类型位上出现两个缓存接口之一，
         * 后面紧跟 `{`（`val cache: SynthesizedAudioCache? = null` 这种成员声明不算）。
         */
        val IMPLEMENTS_CACHE_PATTERN = Regex(
            """:\s*(?:[A-Za-z_][\w.]*\.)?(?:SynthesizedAudioCache|PronunciationAudioCache)\s*\{"""
        )

        /** 读侧两枚必备动作：先解台账，再判可播。 */
        val LEDGER_MARKERS = listOf("PronunciationAudioMetadata.fromJson(", ".matches(")

        /** 与 `KokoroAndroidEngine` 的私有资产常量逐字对齐；换模型时这条闸会先红。 */
        val ENGINE_ASSET_LITERALS = listOf(
            "model-files-v1.0/kokoro-v1.0.int8.onnx",
            "af_heart.npy",
            "bf_emma.npy",
        )
    }
}
