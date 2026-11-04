package com.example.englishlearning.ui

import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * 「AI 助学」目录里**阻塞项必须说实话**的契约（决策 2026-11-05 D-6）。
 *
 * 这套目录此前挂着几条已经过期的阻塞项——它们描述的是「AI 网关还没通」「文章生成还没写」
 * 「发音播放还没做」，而这些东西早就在本仓里跑着并连着测试。过期阻塞项的害处不是难看：
 * 它让未上线卡片看起来永远有理，让真正还缺的那一环（入口、出题、听写交互、多轮对话）
 * 在用户的判断里被埋掉，也让我自己下一轮挑范围时读到一份假的能力清单。
 *
 * 因此本类不是「检查文案顺不顺」，而是**双向钉**：
 *  - 禁语表里的那几条不许再出现在目录（它们已被实现事实证伪）；
 *  - 每条禁语背后那个事实**当场复核**（文件在不在、权限在不在）。
 *    事实没了却仍禁着那句话，等于允许目录继续说谎；所以失败消息要求维护者二选一：
 *    恢复实现，或把短语从禁语表里放回目录并同步决策文档。
 */
class AiFeatureHonestBlockersTest {
    private val catalogueText: String
        get() = AiFeature.entries.flatMap { feature ->
            listOf(feature.status) + feature.dependencies
        }.joinToString("\n")

    @Test
    fun staleBlockersThatTheRepoAlreadySatisfiesAreGoneFromTheCatalogue() {
        STALE_BLOCKERS.forEach { (phrase, satisfiedBy) ->
            assertFalse(
                catalogueText.contains(phrase),
                "目录里还挂着「$phrase」，但 $satisfiedBy 已经落地——这条是过期阻塞项，" +
                    "要么按决策 D-6 改成实话，要么先证明实现已回退并同步决策文档",
            )
        }
    }

    @Test
    fun theFactsThatMakeThoseBlockersStaleAreStillTrue() {
        // 禁语之所以是禁语，是因为下面这些实现**现在真的在**。这一条把它们重新查一遍：
        // 哪天某件被拆了，本条先红，提示「要么恢复，要么把那句阻塞项放回目录并改决策」。
        // 路径口径与同目录的源码契约一致：JVM 单测的工作目录是模块目录（`app/`），
        // 所以这里按相对路径直接定位，并让「找不到」当场以本类的语义失败——
        // 静默跳过等于允许这一条复核永远绿着，而它存在的意义正是「禁语的依据现在还在」。
        val manifest = moduleFile("src/main/AndroidManifest.xml").readText()
        assertTrue(
            manifest.contains("android.permission.INTERNET"),
            "INTERNET 权限不在了：那几条「AI 网关未接通」的旧文案是否该回潮，需要先改决策再动目录",
        )
        listOf(
            "ai/net/UrlConnectionAiHttpTransport.kt",
            "language/infrastructure/AndroidTextToSpeechProvider.kt",
            "language/infrastructure/FilePronunciationAudioCache.kt",
            "learning/WrongWordsRepository.kt",
            "reading/ArticleQualityPolicy.kt",
            "reading/GenerateArticleUseCase.kt",
        ).forEach { relative ->
            val file = moduleFile("$APP_MAIN_SOURCE_PATH/$relative")
            assertTrue(
                file.isFile && file.readText().isNotBlank(),
                "实现「$relative」不在位（禁语表的依据失效）：$STALE_HINT",
            )
        }
    }

    @Test
    fun theJudgeFlagsAPlantedStaleBlocker() {
        // 判据是 `contains(phrase)`，而 phrase 全由中文短语拼出来——本仓源码契约反复踩过
        // 「判据写错→永远绿」（决策 2026-11-05 D-6 的同族坑）。这里把同一批短语喂给**同一个谓词**
        // 的合成目录文本：任何一条短语打不中（多打一个字、全角半角错位），本条立刻红，
        // 上一条对真实目录的绿才有意义。
        val flagged = STALE_BLOCKERS.filter { (phrase, _) -> SYNTHETIC_STALE_CATALOGUE.contains(phrase) }
        assertTrue(
            flagged.size == STALE_BLOCKERS.size,
            "禁语表里有 ${STALE_BLOCKERS.size - flagged.size} 条抓不住合成目录（${
                STALE_BLOCKERS.filterNot { (phrase, _) -> SYNTHETIC_STALE_CATALOGUE.contains(phrase) }
                    .joinToString { it.first }
            }）：谓词失效，上一条用例的绿不能作为证据",
        )
    }

    @Test
    fun everyUnreleasedCardStillNamesTheRealGap() {
        // 反向护栏：把阻塞项删光同样是不说实话——那会让一张没接通的卡看起来只差临门一脚。
        // 每张未上线的卡都必须留下一条点名真实缺口的说明。
        val unreleased = AiFeature.entries.filterNot { it.implemented }
        assertTrue(
            unreleased.isNotEmpty(),
            "目录里已无未上线卡片——「真实缺口」这条护栏空转。若 AI 助学七项全部接通，" +
                "请连同本类与禁语表一起删除，并更新决策 2026-11-05 D-6",
        )
        unreleased.forEach { feature ->
            assertTrue(
                feature.dependencies.any { line -> REAL_GAP_MARKERS.any(line::contains) },
                "${feature.title} 的依赖说明里找不到真实缺口标记（${REAL_GAP_MARKERS.joinToString(" / ")}）：" +
                    "${feature.dependencies}",
            )
        }
    }

    @Test
    fun theConfirmationSemanticsMatchWhatTheTwoScreensActuallyDo() {
        // 「按域名确认一次」在本批只给了查词问答与长难句分析两屏（决策 D-1），
        // 所以目录里那一行必须指向**查词问答**，不许继续把文章生成说成同语义——
        // 那条链路是逐次确认，2026-09-24 的裁决拿它当成本护栏。
        val sentence = AiFeature.SENTENCE_ANALYSIS.dependencies.joinToString("\n")
        assertTrue(sentence.contains("按域名确认一次"), "长难句的确认语义必须如实说明放宽后的粒度：$sentence")
        assertTrue(sentence.contains("查词"), "共享台账的另一屏是查词问答，目录必须点名它：$sentence")
        assertFalse(sentence.contains("与文章生成同语义"), "文章生成是逐次确认，不能再说成同语义：$sentence")

        val photo = AiFeature.PHOTO_TRANSLATION.dependencies.joinToString("\n")
        assertTrue(photo.contains("逐次确认"), "拍照翻译仍是逐次确认，目录不许替它宣布放宽：$photo")
        val image = AiFeature.IMAGE_STUDIO.dependencies.joinToString("\n")
        assertTrue(image.contains("出图每次都要确认"), "生图的出图腿每次都要确认，目录不许含糊：$image")
    }

    private fun moduleFile(relativePath: String): File =
        listOf(File(relativePath), File("app/$relativePath")).firstOrNull { it.isFile }
            ?: error("找不到 $relativePath（禁语依据的复核对象必须真实存在，不能空扫）：$STALE_HINT")

    private companion object {
        const val STALE_HINT = "请先恢复实现，或把对应短语放回目录并写一条新的决策文档"

        val STALE_BLOCKERS = listOf(
            "AI 网关：网络客户端与 INTERNET 权限" to "网络传输层与 INTERNET 权限都在位",
            "文章生成与校验：结构、长度、目标词覆盖校验" to "GenerateArticleUseCase 与 ArticleQualityPolicy 已在阅读链路里",
            "发音播放：系统 TTS 优先，需处理英语离线语音包缺失" to "系统 TTS 与端侧合成链路已上线",
            "音频缓存：仅在接入云端语音时才需要" to "FilePronunciationAudioCache 已带哈希边车在位",
            "错词数据：从复习事件里挑出高频遗忘的词" to "WrongWordsRepository 已在复习模块可用",
            "出站确认：按域名确认一次，与文章生成同语义" to "本批已把该语义改为与查词问答共享",
        )

        val REAL_GAP_MARKERS = listOf("尚未开发", "尚未接通", "需要接进本页", "要改成")

        /**
         * 手写的「过期目录」样本：故意按本批改写**之前**的口径写，不复用 [STALE_BLOCKERS] 里的短语拼接——
         * 拼接出来的必然命中，抓不到「短语表自己打错字」这一类失效。两处独立书写、仍然命中，
         * 才说明谓词真的在比对那串字。
         */
        const val SYNTHETIC_STALE_CATALOGUE = """
            未实现：AI 网关：网络客户端与 INTERNET 权限尚未接入。
            未实现：文章生成与校验：结构、长度、目标词覆盖校验还没写。
            未实现：发音播放：系统 TTS 优先，需处理英语离线语音包缺失。
            未实现：音频缓存：仅在接入云端语音时才需要。
            未实现：错词数据：从复习事件里挑出高频遗忘的词。
            出站确认：按域名确认一次，与文章生成同语义。
        """
    }
}
