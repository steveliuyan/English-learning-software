package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.export.WorksheetPdfWriter
import com.example.englishlearning.learning.worksheet.BuildWorksheetContentUseCase
import com.example.englishlearning.learning.worksheet.WorksheetContentException
import com.example.englishlearning.learning.worksheet.WorksheetContentFailure
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetDocumentBuilder
import com.example.englishlearning.learning.worksheet.WorksheetPage
import com.example.englishlearning.learning.worksheet.WorksheetPaginator
import com.example.englishlearning.learning.worksheet.WorksheetRange
import com.example.englishlearning.learning.worksheet.WorksheetSettings
import com.example.englishlearning.learning.worksheet.WorksheetSource
import com.example.englishlearning.learning.worksheet.WorksheetTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class WorksheetPhase {
    IDLE,
    LOADING,
    SETTINGS,
    PREVIEW,
    ERROR,
}

/**
 * 默写纸流程状态。`settings` 在设置页与预览页之间是同一份，任何阶段都可以改，
 * 这样「返回修改」不会丢失用户已选的模板与开关。
 */
data class WorksheetUiState(
    val phase: WorksheetPhase = WorksheetPhase.IDLE,
    val source: WorksheetSource? = null,
    val settings: WorksheetSettings = WorksheetSettings(),
    val pages: List<WorksheetPage> = emptyList(),
    val renderedFile: File? = null,
    val rendering: Boolean = false,
    val message: String? = null,
) {
    val selectedCount: Int get() = source?.items?.size ?: 0
    val canPreview: Boolean get() = settings.isReadyToPreview() && selectedCount > 0
}

@HiltViewModel
class WorksheetViewModel @Inject constructor(
    private val buildContent: BuildWorksheetContentUseCase,
    private val documentBuilder: WorksheetDocumentBuilder,
    private val paginator: WorksheetPaginator,
    private val pdfWriter: WorksheetPdfWriter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorksheetUiState())
    val uiState: StateFlow<WorksheetUiState> = _uiState

    /** 用户显式点击「导出」后才有值，界面消费一次即清空，避免重进预览页时又弹分享面板。 */
    private val _shareRequest = MutableStateFlow<File?>(null)
    val shareRequest: StateFlow<File?> = _shareRequest

    private var loadedProfileId: String? = null
    private var renderJob: Job? = null
    /** 每次预览/返回都自增，用来丢弃上一轮过期的渲染结果。 */
    private var renderGeneration = 0

    fun load(profileId: String, range: WorksheetRange = WorksheetRange.COMPLETED_TODAY) {
        loadedProfileId = profileId
        invalidateRendering()
        _uiState.value = _uiState.value.copy(
            phase = WorksheetPhase.LOADING,
            settings = _uiState.value.settings.copy(range = range),
            pages = emptyList(),
            renderedFile = null,
            message = null,
        )
        viewModelScope.launch {
            val result = buildContent(profileId, range)
            // 期间用户可能已经切到别的范围，只接受最后一次请求的结果。
            if (_uiState.value.settings.range != range) return@launch
            _uiState.value = result.fold(
                onSuccess = { _uiState.value.copy(phase = WorksheetPhase.SETTINGS, source = it, message = null) },
                onFailure = { _uiState.value.copy(phase = WorksheetPhase.ERROR, message = errorMessage(it)) },
            )
        }
    }

    fun selectRange(range: WorksheetRange) {
        if (_uiState.value.settings.range == range) return
        val profileId = loadedProfileId
        if (profileId == null) {
            updateSettings { copy(range = range) }
        } else {
            load(profileId, range)
        }
    }

    fun selectTemplate(template: WorksheetTemplate) = updateSettings { copy(template = template) }

    fun toggleDirection(direction: WorksheetDirection) = updateSettings {
        copy(directions = directions.toMutableSet().apply { if (!add(direction)) remove(direction) })
    }

    fun toggleGrid(enabled: Boolean) = updateSettings { copy(useFourLineGrid = enabled) }

    fun toggleAnswers(enabled: Boolean) = updateSettings { copy(includeAnswerPage = enabled) }

    /**
     * 生成预览：先按当前设置分页（同步、很快），再在后台写出真实 PDF 并交给界面渲染成图片，
     * 因此预览看到的版式与导出的 PDF 完全一致。设置页与预览页都可以反复调用。
     */
    fun preview() {
        val state = _uiState.value
        val source = state.source ?: return
        if (!state.settings.isReadyToPreview()) {
            _uiState.value = state.copy(message = "至少选择一种默写方向后才能预览。")
            return
        }
        val document = documentBuilder.build(source, state.settings)
        val pages = paginator.paginate(document)
        invalidateRendering()
        val generation = renderGeneration
        _uiState.value = state.copy(
            phase = WorksheetPhase.PREVIEW,
            pages = pages,
            renderedFile = null,
            rendering = true,
            message = null,
        )
        renderJob = viewModelScope.launch { renderPages(generation, pages, state.settings.useFourLineGrid) }
    }

    /** 从预览返回设置：保留用户已选的模板与全部开关，只把阶段退回设置页。 */
    fun dismissPreview() {
        invalidateRendering()
        val state = _uiState.value
        _uiState.value = state.copy(
            phase = if (state.source == null) WorksheetPhase.IDLE else WorksheetPhase.SETTINGS,
            pages = emptyList(),
            renderedFile = null,
            message = null,
        )
    }

    /** 用户点击导出：把已生成的 PDF 交给界面去拉起分享/打开面板。 */
    fun export() {
        val state = _uiState.value
        val file = state.renderedFile ?: return
        _shareRequest.value = file
    }

    fun consumeShareRequest() {
        _shareRequest.value = null
    }

    /** 分享面板没能拉起时给出可读提示，而不是静默失败或崩溃。 */
    fun reportShareUnavailable() {
        _uiState.value = _uiState.value.copy(message = "没有找到可以打开或分享 PDF 的应用，请先安装一个 PDF 阅读器。")
    }

    private suspend fun renderPages(generation: Int, pages: List<WorksheetPage>, useFourLineGrid: Boolean) {
        val result = pdfWriter.write(pages, useFourLineGrid)
        if (generation != renderGeneration) {
            result.getOrNull()?.file?.delete()
            return
        }
        val state = _uiState.value
        if (state.phase != WorksheetPhase.PREVIEW) {
            result.getOrNull()?.file?.delete()
            return
        }
        _uiState.value = result.fold(
            onSuccess = { state.copy(renderedFile = it.file, rendering = false, message = null) },
            onFailure = { state.copy(rendering = false, message = "默写纸生成失败，请稍后重试。") },
        )
    }

    private fun invalidateRendering() {
        renderGeneration++
        renderJob?.cancel()
        renderJob = null
    }

    private fun updateSettings(update: WorksheetSettings.() -> WorksheetSettings) {
        val state = _uiState.value
        _uiState.value = state.copy(settings = state.settings.update(), message = null)
    }

    private fun errorMessage(error: Throwable): String = when ((error as? WorksheetContentException)?.reason) {
        WorksheetContentFailure.NoPlan -> "还没有可用的今日计划。"
        WorksheetContentFailure.NoSelectedWords -> "当前没有可导出的词条。"
        WorksheetContentFailure.StorageUnavailable -> "学习记录暂时无法读取，请稍后重试。"
        null -> "默写词条加载失败，请稍后重试。"
    }
}
