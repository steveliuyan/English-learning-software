package com.example.englishlearning.wordbook

import android.content.res.AssetManager
import com.example.englishlearning.learning.WordCardSource
import com.example.englishlearning.learning.domain.WordCard
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads bundled book directories after atomically installing and validating them privately.
 *
 * [parsePackage] 抽成构造参数（默认真实现），是为了让「读一次卡到底完整解析了几次词书包」
 * 成为**可观测的行为**：解析器是 `WordBookPackageParser` 这个 object，直接静态调用就没法在
 * JVM 上数字数。这里沿用 `WordBookPackageCache` 已经采用的同一手法（它也是注入解析函数）。
 */
class BundledWordBookSource(
    private val assets: AssetManager,
    private val root: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val parsePackage: (File) -> Result<WordBookPackage> = WordBookPackageParser::parse,
) : WordCardSource {
    private val cache = WordBookPackageCache<WordBookPackage> { directory ->
        parsePackage(directory).getOrNull()
    }

    override suspend fun cardIds(wordBookId: String): List<String> = withContext(io) {
        readCards(wordBookId).map(WordCard::cardId)
    }

    override suspend fun cards(cardIds: List<String>): List<WordCard> = withContext(io) {
        if (cardIds.isEmpty()) return@withContext emptyList()
        val idsByBook = cardIds.groupBy { it.substringBefore(':') }
        val cards = idsByBook.values.flatMap { ids -> readCards(ids.first().substringBefore(':')) }
            .associateBy(WordCard::cardId)
        cardIds.mapNotNull(cards::get)
    }

    private fun readCards(bookId: String): List<WordCard> {
        if (!SAFE_ID.matches(bookId)) return emptyList()
        val directory = ensureInstalled(bookId) ?: return emptyList()
        return cache.get(directory)?.cards
            ?.sortedWith(compareBy({ it.rank ?: Int.MAX_VALUE }, { it.lemma }))
            .orEmpty()
    }

    private fun ensureInstalled(bookId: String): File? {
        val target = File(root, bookId)
        if (target.isDirectory && cache.get(target) != null) return target
        val staging = File(root, ".bundled-staging-$bookId-${System.nanoTime()}")
        return try {
            root.mkdirs()
            copyAssetTree("wordbooks/$bookId", staging)
            if (cache.get(staging) == null) return null
            target.deleteRecursively()
            if (!staging.renameTo(target)) return null
            cache.move(staging, target)
            target
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        destination.mkdirs()
        for (name in assets.list(assetPath).orEmpty()) {
            val childAsset = "$assetPath/$name"
            val child = File(destination, name)
            val children = assets.list(childAsset).orEmpty()
            if (children.isEmpty()) {
                assets.open(childAsset).use { input -> child.outputStream().use(input::copyTo) }
            } else {
                copyAssetTree(childAsset, child)
            }
        }
    }

    private companion object {
        val SAFE_ID = Regex("[a-z0-9][a-z0-9-]{0,63}")
    }
}
