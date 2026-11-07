package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiFailure
import com.example.englishlearning.ai.AiOutboundGrantStore
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
import kotlinx.coroutines.Job
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

    /** 出站确认：确认前零字节出网；[host] 是确认目标。本屏与长难句分析共享一份会话内域名台账
     *  （决策 2026-11-05 D-1：这两屏按域名确认一次；文章生成、拍照翻译、生图出图仍逐次确认）。 */
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
 *
 * [outboundGrants] 是与长难句分析共享的会话内域名台账（决策 2026-11-05 D-1）；
 * [reset] 清的是「上一问的显示状态」，不清台账——清了就等于回到逐次确认。
 */
@HiltViewModel
class WordAiQaViewModel @Inject constructor(
    private val ask: WordQaUseCase,
    private val notes: WordAiNoteRepository,
    private val noteIds: WordNoteIdFactory,
    private val clock: ClockProvider,
    private val outboundGrants: AiOutboundGrantStore,
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

    /**
     * 在飞的那一次问答（批 C-4）。取消键要停的是**请求**，不是文案：没有这枚句柄，
     * 屏侧的「取消」只能把界面改回 `Idle`，迟到的回答照样会把 `Answered` 推回来。
     */
    private var askJob: Job? = null

    /**
     * 取消这次问答：停掉在飞请求 + 回 `Idle`，并清掉待重放的请求与上一答。
     *
     * 只在 `Asking` 那一档生效——取消是等待面的动作，结果已经落地时不该被它抹掉
     * （`Answered` / `Failed` 都是读到了东西，把它们退回 `Idle` 等于替用户销毁历史）。
     * 授权台账**不回收**：记账发生在点确认那一刻（下面 `confirmOutbound` 的注释同口径），
     * 取消一次请求换来「请再确认一遍」是说不通的因果。
     * 取消不是失败：这里不写 `Failed`，用户主动按下的取消该回到没开始过的样子。
     */
    fun cancelAsk() {
        if (_uiState.value != WordAiQaUiState.Asking) return
        askJob?.cancel()
        askJob = null
        pendingRequest = null
        lastAnswer = null
        _uiState.value = WordAiQaUiState.Idle
    }

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

    /** 删除一条笔记：落库成功才从可见列表移除；失败或已换卡时列表原样保留。 */
    fun deleteNote(noteId: String) {
        val owner = profileId
        if (owner.isBlank() || noteId.isBlank()) return
        viewModelScope.launch {
            notes.delete(owner, noteId).onSuccess {
                if (profileId == owner) {
                    _savedNotes.value = _savedNotes.value.filterNot { it.noteId == noteId }
                }
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
        runAsk(request, confirmedTextHosts = outboundGrants.confirmedHosts())
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
        // 记账点与长难句同一口径：用户点「确认」即授权这个域名，不等请求成功。
        outboundGrants.confirm(pending.host)
        runAsk(request, confirmedTextHosts = setOf(pending.host))
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

    private fun runAsk(request: WordQaRequest, confirmedTextHosts: Set<String>) {
        askJob?.cancel()
        askJob =
            viewModelScope.launch {
                try {
                    when (val result = ask.ask(request, confirmedTextHosts)) {
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
