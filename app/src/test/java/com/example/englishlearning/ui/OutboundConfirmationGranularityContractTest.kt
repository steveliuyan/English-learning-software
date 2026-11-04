package com.example.englishlearning.ui

import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * 出站确认粒度的**源码级**契约（决策 2026-11-05 D-1）：哪些腿接台账、哪些腿必须继续逐次问。
 *
 * 为什么这一半只能钉在源码上：`AiOutboundGrantStore` 是 `@Singleton`，而 ViewModel 的构造注入
 * 由 Hilt 在运行时完成。JVM 侧的行为测试永远只看见「我 new 出来的那个 store」，看不见
 * 生产接线有没有把同一枚台账递进**不该**接的屏。所以「逐次确认的四条腿不许顺手接台账」
 * 这句话在本仓只有两种表达方式：源码里不许出现那个类型名，或者根本没人写判据。
 *
 * 口径与本仓其他源码契约一致（见 [SourceRatchet]）：先 [stripComments] 再匹配——
 * 实现文件的 KDoc 会合法复述「本屏不接台账，因为……」，不剥注释的判据会被注释本身满足，契约成空转。
 */
class OutboundConfirmationGranularityContractTest {
    private val perCallHosts = listOf(
        "ui/ReadingAccessViewModel.kt",
        "ui/PhotoTranslationViewModel.kt",
        "ui/ImageStudioViewModel.kt",
        "ui/AiProfileSettingsViewModel.kt",
        "ai/AiConnectionProbe.kt",
    )

    private val ledgerHosts = listOf(
        "ui/SentenceAnalysisViewModel.kt",
        "ui/WordAiQaViewModel.kt",
    )

    @Test
    fun noPerCallLegMayTouchTheSessionLedger() {
        // 「只放宽高频两屏」是用户当场拍的板，不是实现者的偏好。
        // 这四条腿各有不接台账的理由：文章生成按张计费且内容量大（2026-09-24 裁决把它当成本护栏）、
        // 拍照翻译的文本可能整页、生图出图按张计费、连接探活是**窄授权**（确认语只承诺了一次模型列表查询）。
        // 任何一条接上台账，就等于用一次点击换掉了那条腿自己的成本/隐私承诺。
        perCallHosts.forEach { relative ->
            val source = stripped(relative)
            assertTrue(
                "AiOutboundGrantStore" !in source,
                "$relative 引用了 AiOutboundGrantStore：这一腿是逐次确认，接台账=静默放宽（决策 2026-11-05 D-1）",
            )
        }
    }

    @Test
    fun theArticleAndPhotoLegsStillStartFromAnEmptyConfirmationSet() {
        // 新请求那条腿必须继续传空集合。传了台账就等于把「逐次」写成「按域名一次」，
        // 而这正是上面那条源码判据要挡的事——两半合起来才封住：类型不许进来，字面量不许变。
        listOf(
            "ui/ReadingAccessViewModel.kt" to "confirmedTextHosts = emptySet()",
            "ui/PhotoTranslationViewModel.kt" to "confirmedTextHosts = emptySet()",
        ).forEach { (relative, anchor) ->
            assertTrue(
                countMatches(stripped(relative), Regex(Regex.escape(anchor))) >= 1,
                "$relative 里找不到「$anchor」：新请求路径必须从空集合起步，逐次确认才成立",
            )
        }
    }

    @Test
    fun theImageLegStillAsksOnEveryDraw() {
        // 生图的两段式确认（决策与目录文案都是这么写的）：提示词那一腿按域名一次（它自己的
        // 私有字段，跨屏不共享），**出图那一腿每次都问**。后者是本条唯一要钉的东西。
        val source = stripped("ui/ImageStudioViewModel.kt")

        assertTrue(
            Regex("""runImage\(\s*prompt\s*,\s*answer\s*=\s*null\s*\)""").containsMatchIn(source),
            "ImageStudioViewModel 的出图入口必须先以「没有确认答案」起步，否则「按张计费的出站每次都要确认」名存实亡",
        )
    }

    @Test
    fun theTwoLedgerScreensReadAndWriteExactlyOneSharedGrant() {
        ledgerHosts.forEach { relative ->
            val source = stripped(relative)

            assertTrue(
                source.contains("outboundGrants.confirmedHosts()"),
                "$relative 的新请求路径没读台账：不读=用户确认过也照样每次问，D-1 的放宽没落地",
            )
            assertTrue(
                countMatches(source, Regex("""outboundGrants\.confirm\(""")) == 1,
                "$relative 必须恰好一处往台账记账（且只在用户点「确认」的那条分支）：多处记账=代码代填确认",
            )
            assertTrue(
                "confirmedTextHosts = emptySet()" !in source,
                "$relative 里还留着「confirmedTextHosts = emptySet()」：新请求绕过台账，放宽就是假的",
            )
        }
    }

    @Test
    fun theJudgementsFlagAPlantedViolationInsteadOfQuietlyPassing() {
        // 合成 must-flag 夹具：本类的判据全是「字符串在不在」，一旦正则写错或注释没剥，
        // 失败方式会是永远绿。这里拿一段**故意违规**的源码喂给同样的判据，钉住它们真的抓得住。
        val planted = """
            class Leaky(private val g: AiOutboundGrantStore) {
                fun ask(r: Any) = runAsk(r, confirmedTextHosts = emptySet())
                fun grant() = g.confirm("x")
                fun grantAgain() = g.confirm("y")
                fun draw() = runImage(prompt, answer = confirmed)
            }
        """.trimIndent()

        assertTrue("AiOutboundGrantStore" in planted, "台账判据失效：连合成都抓不到")
        assertTrue(countMatches(planted, Regex(Regex.escape("confirmedTextHosts = emptySet()"))) == 1, "空集合判据失效")
        assertTrue(countMatches(planted, Regex("""outboundGrants\.confirm\(""")) == 0, "记账判据失效：合成体用的是 g.confirm")
        assertTrue(
            !Regex("""runImage\(\s*prompt\s*,\s*answer\s*=\s*null\s*\)""").containsMatchIn(planted),
            "出图判据失效：合成体已把 answer = null 改掉，不该再算命中",
        )
    }

    private fun stripped(relative: String): String {
        val file = File(APP_MAIN_SOURCE_PATH).resolve(relative)
        assertTrue(file.isFile, "源码契约扫描缺失：${file.path}（JVM 工作目录 ${File(".").absolutePath}）")
        val source = stripComments(file.readText())
        assertTrue(source.isNotBlank(), "$relative 剥注释后为空：契约将无声通过")
        return source
    }
}
