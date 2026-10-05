package com.example.englishlearning.learning

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val INDEX_PLACEHOLDER_CARD_PREFIX = "placeholder:"

/**
 * 把词书内容同步进本地词条索引——索引的**唯一建立与维护入口**。
 *
 * 之所以做成「全量比对一次」而不是在每个安装/导入/删除点各写一段增量逻辑：
 * 三类事件的正确行为完全一致（让索引与当前可见词书一致），分散实现必然漏一处。
 * 放在这里以后，只要有一次刷新跑过，索引就一定收敛。
 *
 * 判据是 `dataVersion`：版本没变就不重新解析词书包，所以重复调用几乎不花钱。
 *
 * **失败关闭**：任何一册内容不完整都返回失败并且**不动该册索引**。
 * 宁可暂时用着旧索引，也不能把一份不完整的内容写进去——那会让查词结果缺词，
 * 而缺词是用户察觉不到的静默错误。
 */
class RefreshVocabularySearchIndexUseCase(
    private val profiles: LearningProfileRepository,
    private val cards: WordCardSource,
    private val index: VocabularySearchIndexRepository,
    private val bundledIds: BundledWordBookIdSource,
    private val importedIds: ImportedWordBookIdSource,
) {
    /** @return 成功时返回本次真正重建的词书数量。 */
    suspend operator fun invoke(): RepositoryResult<Int> {
        val all = when (val result = profiles.listWordBooks()) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return result
        }
        val visible = WordBookVisibility.visible(all, bundledIds.ids(), importedIds.ids())
        val indexedVersions = when (val result = index.indexedVersions()) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return result
        }

        var rebuilt = 0
        for (book in visible) {
            currentCoroutineContext().ensureActive()
            if (indexedVersions[book.id] == book.dataVersion) continue

            val rawIds = cards.cardIds(book.id)
            // 占位册（内容尚未随包交付）不建索引，也不覆盖——留着下次刷新再说。
            if (rawIds.isNotEmpty() && rawIds.all { it.startsWith(INDEX_PLACEHOLDER_CARD_PREFIX) }) continue
            val ids = rawIds.filterNot { it.startsWith(INDEX_PLACEHOLDER_CARD_PREFIX) }
            if (book.totalWords > 0 && ids.isEmpty()) {
                return RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            }
            val content = cards.cards(ids).filter { it.wordBookId == book.id }
            if (content.size != ids.size) {
                return RepositoryResult.Failure(LearningProfileRepositoryError.StorageUnavailable)
            }
            when (val write = index.replaceWordBook(book.id, book.displayName, book.dataVersion, content)) {
                is RepositoryResult.Success -> rebuilt += 1
                is RepositoryResult.Failure -> return write
            }
        }

        // 清理已删除词书的残留索引行：删除入口不必自己记得删索引，刷新一定能收敛。
        return when (val prune = index.deleteMissing(visible.map { it.id })) {
            is RepositoryResult.Success -> RepositoryResult.Success(rebuilt)
            is RepositoryResult.Failure -> prune
        }
    }
}
