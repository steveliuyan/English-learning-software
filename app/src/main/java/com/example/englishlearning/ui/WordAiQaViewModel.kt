package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.core.time.ClockProvider
import com.example.englishlearning.wordqa.WordAiNote
import com.example.englishlearning.wordqa.WordAiNoteRepository
import com.example.englishlearning.wordqa.WordQaKind
import com.example.englishlearning.wordqa.WordQaNotConfiguredReason
import com.example.englishlearning.wordqa.WordQaRequest
import com.example.englishlearning.wordqa.WordQaResult
import com.example.englishlearning.wordqa.WordQaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class PersonalNoteSaveStatus {
    Idle,
    Saving,
    Saved,
    Failed,
}

sealed interface WordAiQaUiState {
    /** 初始态：三个固定问题 chips 可点。 */
    data object Idle : WordAiQaUiState

    data object Asking : WordAiQaUiState

    /** 一次问答的回答。[saved]/[saveFailed] 是「保存笔记」动作的可见结果。 */
    data class Answered(
        val kind: WordQaKind,
        val answer: String,
        val saved: Boolean = false,
        val saveFailed: Boolean = false,
    ) : WordAiQaUiState

    /** 出站确认：与文章生成共用同一条安全约束，确认前零字节出网；[host] 是确认目标。 */
    data class NeedsOutboundConfirmation(val host: String) : WordAiQaUiState

    data class NotConfigured(val reason: WordQaNotConfiguredReason) : WordAiQaUiState

    data class Failed(val failure: AiFailure) : WordAiQaUiState
}

/** 笔记主键工厂：生产用随机 UUID，测试注入固定值。 */
fun interface WordNoteIdFactory {
    fun newId(): String

    companion object {
        val Random = WordNoteIdFactory { java.util.UUID.randomUUID().toString() }
    }
}

/**
 * 词上下文 AI 问答状态机。换问法必须重置旧回答——不允许把 A 问法的答案
 * 存成 B 问法的笔记；确认重放沿用同一请求与同一 host（与阅读生成同型）。
 */
@HiltViewModel
class WordAiQaViewModel @Inject constructor(
    private val ask: WordQaUseCase,
    private val notes: WordAiNoteRepository,
    private val noteIds: WordNoteIdFactory,
    private val clock: ClockProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<WordAiQaUiState>(WordAiQaUiState.Idle)
    val uiState: StateFlow<WordAiQaUiState> = _uiState
    private val _savedNotes = MutableStateFlow<List<WordAiNote>>(emptyList())
    val savedNotes: StateFlow<List<WordAiNote>> = _savedNotes
    private val _personalNoteSaveStatus = MutableStateFlow(PersonalNoteSaveStatus.Idle)
    val personalNoteSaveStatus: StateFlow<PersonalNoteSaveStatus> = _personalNoteSaveStatus

    private var profileId: String = ""
    private var wordBookId: String = ""
    private var cardId: String = ""
    private var pendingRequest: WordQaRequest? = null
    private var lastAnswer: Pair<WordQaKind, String>? = null

    /** 打开新词的问答层时清空上一词的状态，避免把 A 词的回答显示到 B 词的屏幕上。 */
    fun reset() {
        pendingRequest = null
        lastAnswer = null
        _savedNotes.value = emptyList()
        _personalNoteSaveStatus.value = PersonalNoteSaveStatus.Idle
        _uiState.value = WordAiQaUiState.Idle
    }

    fun savePersonalNote(lemma: String, text: String, wordBookId: String = this.wordBookId, cardId: String = this.cardId) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        this.wordBookId = wordBookId
        this.cardId = cardId
        _personalNoteSaveStatus.value = PersonalNoteSaveStatus.Saving
        viewModelScope.launch {
            notes.save(
                WordAiNote(
                    noteId = noteIds.newId(),
                    profileId = profileId,
                    wordBookId = wordBookId,
                    cardId = cardId,
                    lemma = lemma,
                    kind = WordQaKind.Personal,
                    answer = trimmed,
                    createdAtEpochMillis = clock.instant().toEpochMilli(),
                ),
            ).onSuccess {
                _personalNoteSaveStatus.value = PersonalNoteSaveStatus.Saved
                loadNotes(profileId, lemma, wordBookId, cardId)
            }.onFailure {
                _personalNoteSaveStatus.value = PersonalNoteSaveStatus.Failed
            }
        }
    }

    fun loadNotes(profileId: String, lemma: String, wordBookId: String = "", cardId: String = "") {
        val identityChanged = this.profileId != profileId ||
            this.wordBookId != wordBookId ||
            this.cardId != cardId
        this.profileId = profileId
        this.wordBookId = wordBookId
        this.cardId = cardId
        if (identityChanged) {
            _savedNotes.value = emptyList()
            _personalNoteSaveStatus.value = PersonalNoteSaveStatus.Idle
        }
        viewModelScope.launch {
            notes.list(profileId, lemma, wordBookId, cardId).onSuccess { loaded ->
                if (this@WordAiQaViewModel.profileId == profileId &&
                    this@WordAiQaViewModel.wordBookId == wordBookId &&
                    this@WordAiQaViewModel.cardId == cardId
                ) {
                    _savedNotes.value = loaded
                }
            }
        }
    }

    fun ask(kind: WordQaKind, lemma: String, context: String?, profileId: String) {
        this.profileId = profileId
        val request = WordQaRequest(kind, lemma, context)
        pendingRequest = request
        lastAnswer = null
        _uiState.value = WordAiQaUiState.Asking
        runAsk(request, confirmedTextHost = null)
    }

    fun confirmOutbound(confirmed: Boolean) {
        val pending = _uiState.value as? WordAiQaUiState.NeedsOutboundConfirmation
        if (pending == null) return
        if (!confirmed) {
            _uiState.value = WordAiQaUiState.Idle
            return
        }
        val request = pendingRequest ?: run {
            _uiState.value = WordAiQaUiState.Idle
            return
        }
        _uiState.value = WordAiQaUiState.Asking
        runAsk(request, confirmedTextHost = pending.host)
    }

    /** 只在 Answered 态有效；把当前问答按其真实 kind/lemma 落为一条笔记。 */
    fun saveNote() {
        val answered = _uiState.value as? WordAiQaUiState.Answered ?: return
        val request = pendingRequest ?: return
        val answer = lastAnswer ?: return
        val (kind, text) = answer
        viewModelScope.launch {
            val result = notes.save(
                WordAiNote(
                    noteId = noteIds.newId(),
                    profileId = profileId,
                    wordBookId = wordBookId,
                    cardId = cardId,
                    lemma = request.lemma,
                    kind = kind,
                    answer = text,
                    createdAtEpochMillis = clock.instant().toEpochMilli(),
                ),
            )
            // 仅当用户还停留在同一条回答上才更新可见状态；期间换问法的结果不被覆盖。
            if (_uiState.value is WordAiQaUiState.Answered && lastAnswer == answer) {
                if (result.isSuccess) loadNotes(profileId, request.lemma, wordBookId, cardId)
                _uiState.value = if (result.isSuccess) {
                    answered.copy(saved = true, saveFailed = false)
                } else {
                    answered.copy(saved = false, saveFailed = true)
                }
            }
        }
    }

    private fun runAsk(request: WordQaRequest, confirmedTextHost: String?) {
        viewModelScope.launch {
            try {
                when (val result = ask.ask(request, confirmedTextHost)) {
                    is WordQaResult.Answered -> {
                        lastAnswer = request.kind to result.answer
                        _uiState.value = WordAiQaUiState.Answered(request.kind, result.answer)
                    }
                    is WordQaResult.NeedsConfirmation ->
                        _uiState.value = WordAiQaUiState.NeedsOutboundConfirmation(result.host)
                    is WordQaResult.Failed -> _uiState.value = WordAiQaUiState.Failed(result.failure)
                    is WordQaResult.NotConfigured -> _uiState.value = WordAiQaUiState.NotConfigured(result.reason)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.value = WordAiQaUiState.Idle
            }
        }
    }
}
