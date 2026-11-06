package com.example.englishlearning.wordbook

import com.example.englishlearning.learning.domain.WordCard
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 占位文本不得当学习内容（决策 `docs/decisions/2026-11-06-placeholder-text-is-not-learning-content.md`）。
 *
 * 随包十册的 `example` 字段实测**只有一个取值**——字符串「暂无例句」（45,290 张卡，真实例句 0 条），
 * `phrases` 里 98.7% 的 `text` 与 `meaningZh` 逐字相同。这两串东西过去被界面当内容渲染：
 * 词卡卡面（F1-03 把「英文例句」排在卡面上）印出一行中文占位，详情页把同一串字念两遍。
 * AC1-12 明写「无数据的任一内容模块……不出现空标题或『暂无』占位」，所以这不是范围变化，
 * 而是**数据把既有规格打穿**。
 *
 * 归一只放在 DTO→domain 这一处（内置册与导入册共用），界面不再各自认串——本类的断言全部打在
 * 解析产物上而不是打在某个屏幕的私有实现上，就是为了钉住「只有一个归一点」这件事。
 */
class WordBookContentHonestyTest {

    private fun parse(vararg cards: WordBookPackageFixture.CardSpec): List<WordCard> {
        val directory = Files.createTempDirectory("content-honesty").toFile()
        return WordBookPackageParser.parse(WordBookPackageFixture.writePackage(directory, cards.toList()))
            .getOrThrow()
            .cards
    }

    private fun card(
        lemma: String,
        example: String,
        exampleZh: String? = "例句译文",
        phrases: List<Pair<String, String>> = listOf("a phrase" to "短语"),
        synonyms: List<Pair<String, String>> = listOf("synonym" to "近义"),
    ): WordBookPackageFixture.CardSpec =
        WordBookPackageFixture.CardSpec(
            lemma = lemma,
            rank = 1,
            example = example,
            exampleZh = exampleZh,
            phrases = phrases,
            synonyms = synonyms,
        )

    @Test
    fun theShippedPlaceholderExampleBecomesNoExampleAtAll() {
        val parsed = parse(card("confidence", example = "暂无例句")).single()

        assertNull(parsed.example, "占位串不得作为例句进入 domain：卡面会把它当内容印出来")
        assertNull(parsed.exampleZh, "例句本身不存在时，它的译文是凭空多出来的一行")
    }

    /**
     * 判据本身的空白分支。走解析器到不了这一支：`validateCard` 要求 `example` 非空白
     * （既有用例 `blank exampleZh` 同样钉着「可选字段给了就必须给全」），
     * 所以这里直接打纯函数——将来包格式若允许省略例句，界面侧仍不会漏出一串空白。
     */
    @Test
    fun blankInputIsNoExampleAtTheJudgementLevel() {
        listOf(null, "", "   ", "\t\n").forEach { raw ->
            assertNull(
                WordBookContentPlaceholders.exampleOrNull(raw),
                "空白例句（${raw?.replace("\n", "\\n")?.replace("\t", "\\t")}）应归一为「没有例句」",
            )
        }
    }

    /**
     * 判据只认**整串相等**，不认子串：真实英文句子、以及将来含「暂无」二字的正常中文例句
     * 都不该被误杀。这几条用例是「不许把归一做成模糊匹配」的唯一防线。
     */
    @Test
    fun realSentencesThatMerelyContainThePlaceholderWordsSurviveUntouched() {
        listOf(
            "He has no example yet.",
            "这份材料暂无例句可循，但句子本身是真的。",
            "暂无例句。",
            "The word appears in 暂无例句 contexts.",
        ).forEach { raw ->
            val parsed = parse(card("surf", example = raw, exampleZh = null)).single()
            assertEquals(raw, parsed.example, "整串不等于占位语就不该被归一：$raw")
        }
    }

    @Test
    fun aPhraseWhoseMeaningMerelyRepeatsTheTextLosesItsSecondLine() {
        val parsed = parse(
            card("a", example = "A real example.", phrases = listOf("a lot 许多" to "a lot 许多", "非常" to "非常")),
        ).single()

        assertEquals(2, parsed.phrases.size, "条目本身不删，只停止把同一串字念两遍")
        parsed.phrases.forEach { phrase ->
            assertNull(phrase.meaningZh, "text 与 meaningZh 逐字相同 ⇒ 只讲得出一个字段：${phrase.text}")
        }
    }

    @Test
    fun aPhraseWithAnActualChineseMeaningKeepsBothLines() {
        val parsed = parse(card("make", example = "Make the bed.", phrases = listOf("make a decision" to "做决定")))

        assertEquals("做决定", parsed.single().phrases.single().meaningZh)
    }

    /**
     * 2026-11-06 设备走查抓到的第二形：随包数据里「没有内容」还用一枚全角破折号「—」表示，
     * 且**整条都是它**（`text` 与 `meaningZh` 同为「—」；近义词的 `lemma` 同）。实测读数
     * 78,392 条搭配里 6,517 条、91,971 条近义里 3,224 条是这个形状。
     *
     * 只归一 `meaningZh` 挡不住它：详情页于是画出一节「常用搭配」，里面只有一行「—」——
     * 比念两遍更接近空标题，也正是 AC1-12 点名的形状。所以这一支要**整条丢掉**，
     * 条目空了整节就不画（屏侧已有 `isNotEmpty()` 闸）。
     */
    @Test
    fun aPhraseWhoseTextIsTheNoDataMarkLeavesNoEntryBehind() {
        val parsed = parse(
            card(
                "resolve",
                example = "Resolve the conflict.",
                phrases = listOf("—" to "—", "take care of" to "照顾"),
            ),
        ).single()

        assertEquals(
            listOf("take care of"),
            parsed.phrases.map { it.text },
            "整条只有「—」的搭配不是一条搭配：留下它就是留一行假内容",
        )
    }

    @Test
    fun aSynonymWhoseLemmaIsTheNoDataMarkLeavesNoChipBehind() {
        val parsed = parse(
            card(
                "huge",
                example = "A huge success.",
                synonyms = listOf("—" to "—", "enormous" to "巨大的"),
            ),
        ).single()

        assertEquals(
            listOf("enormous"),
            parsed.synonyms.map { it.lemma },
            "「—」不可能是近义词：详情页的近义 chip 会把它画成一枚点得动的假词",
        )
    }

    /** 防空杀：判据只认整串相等的记号，带破折号的真短语（含连接号、破折号在句内）一律保留。 */
    @Test
    fun realEntriesThatMerelyContainADashSurviveUntouched() {
        val parsed = parse(
            card(
                "well-known",
                example = "A well-known fact.",
                phrases = listOf("mother-to-be" to "准妈妈", "the — not so simple — part" to "没那么简单的部分"),
                synonyms = listOf("famous" to "著名的"),
            ),
        ).single()

        assertEquals(2, parsed.phrases.size, "破折号出现在串里不是记号：整串相等才算")
        assertEquals("准妈妈", parsed.phrases.first().meaningZh)
        assertEquals(listOf("famous"), parsed.synonyms.map { it.lemma })
    }

    /**
     * 随包十册是本批口径的**真实出处**：这条用例读仓库里的资产原件，断言解析产物里
     * 一张假例句卡都不剩，也不剩一条「—」搭配或「—」近义。上面几条走夹具，只有这条走十册真数据——
     * 它同时是「归一确实作用在生产数据上」与「十册例句全缺」这两句读数的现场证据。
     */
    @Test
    fun noBundledWordBookCardRendersAPlaceholderExampleAfterNormalization() {
        val root = File("src/main/assets/wordbooks")
        assertTrue(root.isDirectory, "随包词书目录应在 :app 模块工作目录下可寻：${root.absolutePath}")

        val books = root.listFiles { file -> file.isDirectory }
            ?.filter { File(it, "book.json").isFile }
            .orEmpty()
        assertEquals(10, books.size, "随包册数与既有口径一致（十册）")

        var cards = 0
        var phrases = 0
        var synonyms = 0
        books.forEach { directory ->
            val parsed = WordBookPackageParser.parse(directory).getOrThrow().cards
            cards += parsed.size
            parsed.forEach { card ->
                card.example?.let { example ->
                    assertFalse(
                        example.trim() in WordBookContentPlaceholders.EXAMPLES,
                        "${card.cardId} 的例句仍是占位串：$example",
                    )
                    assertTrue(example.isNotBlank(), "${card.cardId} 的例句是空白却没被归一")
                }
                phrases += card.phrases.size
                card.phrases.forEach { phrase ->
                    assertFalse(
                        phrase.text.trim() in WordBookContentPlaceholders.NO_DATA_MARKS,
                        "${card.cardId} 仍留着一条只有「—」的搭配",
                    )
                    phrase.meaningZh?.let { meaning ->
                        assertFalse(
                            meaning.trim() in WordBookContentPlaceholders.NO_DATA_MARKS,
                            "${card.cardId} 的搭配「${phrase.text}」仍带着「—」释义",
                        )
                    }
                }
                synonyms += card.synonyms.size
                card.synonyms.forEach { synonym ->
                    assertFalse(
                        synonym.lemma.trim() in WordBookContentPlaceholders.NO_DATA_MARKS,
                        "${card.cardId} 仍留着一条只有「—」的近义词",
                    )
                }
            }
        }
        assertTrue(cards > 40_000, "十册词条数应在万级（实测读数由本用例现算，当前累计 $cards）")
        // 归一不是空转：真数据里确实有「—」条目被丢掉（夹具证形状，本条证作用在生产数据上）。
        assertTrue(
            phrases < 78_392 && synonyms < 91_971,
            "十册原始条目数为搭配 78,392 / 近义 91,971（2026-11-06 实测），" +
                "归一后应各自减少（当前 $phrases / $synonyms）；一条没少说明记号判据没作用到真数据",
        )
    }
}
