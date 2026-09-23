package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.learning.worksheet.BuildWorksheetContentUseCase
import com.example.englishlearning.learning.worksheet.WorksheetContentException
import com.example.englishlearning.learning.worksheet.WorksheetDirection
import com.example.englishlearning.learning.worksheet.WorksheetDocument
import com.example.englishlearning.learning.worksheet.WorksheetDocumentBuilder
import com.example.englishlearning.learning.worksheet.WorksheetPaginator
import com.example.englishlearning.learning.worksheet.WorksheetRange
import com.example.englishlearning.learning.worksheet.WorksheetSettings
import com.example.englishlearning.learning.worksheet.WorksheetSource
import com.example.englishlearning.export.RenderedWorksheet
import com.example.englishlearning.export.WorksheetPdfRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface WorksheetUiState {
    data object Idle : WorksheetUiState
    data object Loading : WorksheetUiState
    data class Ready(val source: WorksheetSource, val settings: WorksheetSettings) : WorksheetUiState
    data class Preview(val document: WorksheetDocument, val pages: List<com.example.englishlearning.learning.worksheet.WorksheetPage>, val rendered: RenderedWorksheet? = null) : WorksheetUiState
    data class Error(val message: String) : WorksheetUiState
}

@HiltViewModel
class WorksheetViewModel @Inject constructor(
    private val buildContent: BuildWorksheetContentUseCase,
    private val documentBuilder: WorksheetDocumentBuilder,
    private val paginator: WorksheetPaginator,
    private val pdfRenderer: WorksheetPdfRenderer,
) : ViewModel() {
    private val _uiState = MutableStateFlow<WorksheetUiState>(WorksheetUiState.Idle)
    val uiState: StateFlow<WorksheetUiState> = _uiState
    private var loadedProfileId: String? = null

    fun load(profileId: String, range: WorksheetRange = WorksheetRange.COMPLETED_TODAY) {
        loadedProfileId = profileId
        _uiState.value = WorksheetUiState.Loading
        viewModelScope.launch {
            val result = buildContent(profileId, range)
            _uiState.value = result.fold(
                onSuccess = { WorksheetUiState.Ready(it, WorksheetSettings(range = range)) },
                onFailure = { WorksheetUiState.Error(errorMessage(it)) },
            )
        }
    }

    fun toggleDirection(direction: WorksheetDirection) {
        val state = _uiState.value as? WorksheetUiState.Ready ?: return
        val directions = state.settings.directions.toMutableSet().apply {
            if (!add(direction)) remove(direction)
        }
        _uiState.value = state.copy(settings = state.settings.copy(directions = directions))
    }

    fun selectRange(range: WorksheetRange) {
        loadedProfileId?.let { load(it, range) }
    }

    fun selectTemplate(template: com.example.englishlearning.learning.worksheet.WorksheetTemplate) =
        updateSettings { copy(template = template) }

    fun toggleGrid(enabled: Boolean) = updateSettings { copy(useFourLineGrid = enabled) }
    fun toggleAnswers(enabled: Boolean) = updateSettings { copy(includeAnswerPage = enabled) }

    fun preview() {
        val state = _uiState.value as? WorksheetUiState.Ready ?: return
        if (!state.settings.isReadyToPreview()) return
        val document = documentBuilder.build(state.source, state.settings)
        _uiState.value = WorksheetUiState.Preview(document, paginator.paginate(document))
    }

    fun exportPreview() {
        val state = _uiState.value as? WorksheetUiState.Preview ?: return
        val result = pdfRenderer.render(state.pages, state.document.settings.useFourLineGrid)
        result.onSuccess { _uiState.value = state.copy(rendered = it) }
            .onFailure { _uiState.value = WorksheetUiState.Error("默写纸导出失败，请稍后重试。") }
    }

    private fun updateSettings(update: WorksheetSettings.() -> WorksheetSettings) {
        val state = _uiState.value as? WorksheetUiState.Ready ?: return
        _uiState.value = state.copy(settings = state.settings.update())
    }

    private fun errorMessage(error: Throwable): String = when ((error as? WorksheetContentException)?.reason) {
        com.example.englishlearning.learning.worksheet.WorksheetContentFailure.NoPlan -> "还没有可用的今日计划。"
        com.example.englishlearning.learning.worksheet.WorksheetContentFailure.NoSelectedWords -> "当前没有可导出的词条。"
        com.example.englishlearning.learning.worksheet.WorksheetContentFailure.StorageUnavailable -> "学习记录暂时无法读取，请稍后重试。"
        null -> "默写词条加载失败，请稍后重试。"
    }
}
