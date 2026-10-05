package com.example.englishlearning.wordbook

import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.WordCard
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从**已导入的词书包目录**读词卡的 [WordCardSource] 实现。
 *
 * 与占位词卡共用同一个端口，所以学习流程、事件库、调度器都不需要改动
 * （`WordCardSource` 注释里的承诺）。
 *
 * 设计取舍：
 * - 每次调用都重新解析包（100 词的包解析是毫秒级），**不做常驻缓存**——避免「导入新包后仍读旧内容」
 *   这类只有真机才发现的缓存不一致；将来真需要缓存再加，并配失效测试。
 * - 解析失败（包被外部损坏/半拷贝）返回**空列表**而不是抛异常：词卡读不出来应该表现为
 *   「这册暂时没有内容」，不能让整个学习页崩掉。
 */
/**
 * 词书根目录下已正式发布的册目录。
 *
 * 排除导入流程自己建的暂存/备份目录：备份删除失败或进程中途被杀时它们会残留，
 * 且里面是同 bookId 的完整包——不排除就会被当成一册，读出过期词卡。
 */
internal fun importedBookDirectories(root: File): List<File> =
    root.listFiles().orEmpty()
        .filter { it.isDirectory && !WordBookPackageImporter.isWorkDirectory(it) }
        .sortedBy { it.name }

class ImportedWordBookSource(
    private val root: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WordCardSource {
    private val cache = WordBookPackageCache<WordBookPackage> { directory ->
        WordBookPackageParser.parse(directory).getOrNull()
    }

    // 解析会逐张读图算 SHA-256：调用方（学习页）在 Main 上，必须切走，否则开始学习就卡
    override suspend fun cardIds(wordBookId: String): List<String> = withContext(io) {
        readCards(wordBookId).map(WordCard::cardId)
    }

    override suspend fun cards(cardIds: List<String>): List<WordCard> {
        if (cardIds.isEmpty()) return emptyList()
        return withContext(io) { readRequested(cardIds) }
    }

    private fun readRequested(cardIds: List<String>): List<WordCard> {
        // 解析器保证 cardId 以 `<bookId>:` 开头且册 id 不含冒号，所以只解析被请求到的册，
        // 不必每次把所有已导入册（连同全部配图的哈希）都过一遍。
        // 只认已发布的册目录名，cardId 里的前缀不会被当成任意路径去读。
        val installed = importedBookDirectories(root).map { it.name }.toSet()
        val byId = cardIds.map { it.substringBefore(':') }.distinct()
            .filter { it in installed }
            .flatMap(::readCards)
            .associateBy(WordCard::cardId)
        return cardIds.mapNotNull(byId::get)
    }

    /** 该册是否已经导入（用于界面判断「导入」还是「查看」）。 */
    fun isImported(wordBookId: String): Boolean =
        importedBookDirectories(root).any { it.name == wordBookId }

    private fun readCards(wordBookId: String): List<WordCard> {
        val directory = File(root, wordBookId)
        if (!directory.isDirectory) return emptyList()
        return cache.get(directory)?.cards
            ?.sortedWith(compareBy({ it.rank ?: Int.MAX_VALUE }, { it.lemma }))
            .orEmpty()
    }
}
