package com.example.englishlearning.wordbook

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.LearningProfileRepository
import com.example.englishlearning.learning.RefreshVocabularySearchIndexUseCase
import com.example.englishlearning.learning.RepositoryResult
import com.example.englishlearning.learning.WordBook
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置页「词书导入/导出」的状态：已导入册清单、进行中标记、一句可读结果。 */
internal fun selectExportBook(books: List<String>, requestedBookId: String): String? =
    requestedBookId.takeIf { it in books }

data class WordBookTransferUiState(
    val books: List<String> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
) {
    /** 导出需要一册可打包的目录；没有导入过任何册时导出入口应当不可点。 */
    val canExport: Boolean get() = books.isNotEmpty() && !busy
}

/**
 * 词书包的导入与导出（**离线**）。
 *
 * 关键纪律：
 * - **失败零写入**由 [WordBookPackageImporter] 保证，这里只负责把 URI 内容落到临时文件；
 * - 导入成功后**登记进 Room**（`upsertWordBook`），否则「调整词书与目标」列表里看不到它；
 * - 导出用 SAF 的 `CreateDocument` 返回的 URI，系统已授权，不需要 FileProvider。
 *
 * 临时文件一律写在 `cacheDir` 并在 `finally` 删除：导入/导出的中间产物不该留在设备上。
 */
@HiltViewModel
class WordBookTransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LearningProfileRepository,
    @Named("io") private val io: CoroutineDispatcher,
    /**
     * 导入成功后立刻补齐该册的词条索引，用户下一次查词就不必等「首次解析整包」。
     *
     * 可空仅为了让既有单测不必构造索引依赖；生产装配点一定会传入。
     */
    private val refreshIndex: RefreshVocabularySearchIndexUseCase? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordBookTransferUiState())
    val uiState: StateFlow<WordBookTransferUiState> = _uiState

    private val root: File get() = File(context.filesDir, WORD_BOOK_ROOT)

    fun refresh() {
        val books = importedBookDirectories(root).map { it.name }
        _uiState.value = _uiState.value.copy(books = books)
    }

    /** 导入用户选中的 `.wbpack`（或已解开的目录 zip）。成功后才登记词书元数据。 */
    fun importFrom(uri: Uri) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val outcome = withContext(io) {
                runCatching {
                    root.mkdirs()
                    // busy 已置位，此刻没有进行中的导入，清理残留暂存目录是安全的
                    WordBookPackageImporter.recoverWorkDirectories(root)
                    val staging = File(context.cacheDir, "import-${System.nanoTime()}.wbpack")
                    try {
                        val opened = context.contentResolver.openInputStream(uri)?.use { input ->
                            staging.outputStream().use { output ->
                                copyWithLimit(input, output, ImportLimits.MAX_ARCHIVE_BYTES)
                            }
                            true
                        } ?: false
                        if (!opened) throw WordBookPackageException(WordBookPackageRejection.MissingBookJson)
                        WordBookPackageImporter().import(staging, root).getOrElse { throw it }
                    } finally {
                        staging.delete()
                    }
                }
            }
            outcome.fold(
                onSuccess = { directory ->
                    // register 会重新解析整包（读图算哈希），不能留在 Main
                    val registered = withContext(io) { register(directory) }
                    // 登记成功才建索引：登记失败时这册还不在可见列表里，建了也会被刷新清掉。
                    if (registered) withContext(io) { refreshIndex?.invoke() }
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        message = if (registered) {
                            "已导入《${directory.name}》，可在「调整词书与目标」中选用。"
                        } else {
                            "词书已导入，但登记到词书列表失败，请稍后重试。"
                        },
                    )
                    refresh()
                },
                onFailure = { failure ->
                    _uiState.value = _uiState.value.copy(busy = false, message = importFailureMessage(failure))
                },
            )
        }
    }

    /**
     * 导出指定的已导入词书，写到用户选中的位置。
     * 打包前 [WordBookPackageExporter] 会先解析校验，所以导出的包一定是完整可用的。
     */
    fun exportTo(uri: Uri, requestedBookId: String) {
        if (!_uiState.value.canExport) return
        val bookId = selectExportBook(_uiState.value.books, requestedBookId) ?: return
        _uiState.value = _uiState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val outcome = withContext(io) {
                runCatching {
                    val staging = File(context.cacheDir, "$bookId-${System.nanoTime()}.wbpack")
                    try {
                        WordBookPackageExporter.export(File(root, bookId), staging).getOrElse { throw it }
                        val written = context.contentResolver.openOutputStream(uri)?.use { output ->
                            staging.inputStream().use { input -> input.copyTo(output) }
                            true
                        } ?: false
                        if (!written) error("cannot open destination")
                        bookId
                    } finally {
                        staging.delete()
                    }
                }
            }
            outcome.fold(
                onSuccess = { id ->
                    _uiState.value = _uiState.value.copy(busy = false, message = "已导出《$id》。")
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(busy = false, message = "导出失败，请换一个位置再试。")
                },
            )
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** 把解析出的元数据写进词书表，导入的册才会出现在「调整词书与目标」列表里。 */
    private suspend fun register(directory: File): Boolean {
        val parsed = WordBookPackageParser.parse(directory).getOrNull() ?: return false
        val metadata = parsed.metadata
        if (metadata.totalWords != parsed.cards.size || metadata.totalWords <= 0) return false
        val wordBook = WordBook(
            id = metadata.id,
            displayName = metadata.displayName,
            level = metadata.level,
            totalWords = metadata.totalWords,
            dataVersion = metadata.dataVersion,
            sourceId = metadata.sourceId,
        )
        return repository.upsertWordBook(wordBook) is RepositoryResult.Success
    }

    /** 拒绝原因按因给话，而不是笼统一句「导入失败」——用户至少知道是不是包坏了。 */
    private fun importFailureMessage(failure: Throwable): String =
        when ((failure as? WordBookPackageException)?.rejection) {
            WordBookPackageRejection.ImageHashMismatch,
            WordBookPackageRejection.ImageMissing,
            WordBookPackageRejection.ManifestEntryUnused,
            WordBookPackageRejection.OrphanImage,
            -> "词书包里的配图与清单对不上，文件可能已损坏。"

            WordBookPackageRejection.MetadataRejected ->
                "这个词书包的来源或署名信息不完整，已拒绝导入。"

            WordBookPackageRejection.UnsupportedFormatVersion ->
                "词书包版本过新，请升级应用后再导入。"

            WordBookPackageRejection.PackageTooLarge ->
                "词书包过大或文件过多，已拒绝导入。"

            WordBookPackageRejection.NoCards,
            WordBookPackageRejection.InvalidCard,
            WordBookPackageRejection.InvalidBookJson,
            WordBookPackageRejection.InvalidBookId,
            WordBookPackageRejection.InvalidManifest,
            WordBookPackageRejection.MissingManifest,
            WordBookPackageRejection.MissingBookJson,
            WordBookPackageRejection.UnlistedFile,
            -> "词书包内容不完整或格式不正确，已拒绝导入。"

            null -> "导入失败，请确认选中的是词书包文件。"
        }

    private companion object {
        const val WORD_BOOK_ROOT = "wordbooks"
    }
}
