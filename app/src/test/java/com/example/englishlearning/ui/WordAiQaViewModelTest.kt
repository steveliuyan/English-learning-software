package com.example.englishlearning.ui

import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.DefaultTextProfileResult
import com.example.englishlearning.ai.DefaultTextProfileResolver
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.ai.net.AiHttpRequest
import com.example.englishlearning.ai.net.AiHttpResponse
import com.example.englishlearning.ai.net.AiHttpResult
import com.example.englishlearning.ai.net.AiHttpTransport
import com.example.englishlearning.core.security.SecretReference
import com.example.englishlearning.core.security.SecretStore
import com.example.englishlearning.wordqa.WordAiNote
import com.example.englishlearning.wordqa.WordAiNoteRepository
import com.example.englishlearning.wordqa.WordQaKind
import com.example.englishlearning.wordqa.WordQaNotConfiguredReason
import com.example.englishlearning.wordqa.WordQaResult
import com.example.englishlearning.wordqa.WordQaUseCase
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 词 AI 问答状态机：Idle → Asking → Answered/NeedsOutboundConfirmation/Failed/NotConfigured；
 * 确认重放沿用同一请求；保存笔记只允许在 Answered 态，落库成功才置 saved。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WordAiQaViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val profile = AiProfile(
        profileId = "p1",
        displayName = "测试",
        websiteUrl = "https://example.com",
        endpoint = "https://api.example.com/v1",
        model = "gpt-x",
        capabilities = setOf(AiCapability.Text),
        secretReference = SecretReference("ai-profile-p1"),
    )

    private class FakeSecretStore : SecretStore {
        override fun save(reference: SecretReference, secret: CharArray) = Result.success(Unit)
        override fun read(reference: SecretReference) = Result.success("k3y".toCharArray())
        override fun delete(reference: SecretReference) = Result.success(Unit)
        override fun has(reference: SecretReference) = Result.success(true)
    }

    private class RecordingTransport : AiHttpTransport {
        var sends = 0
        var result: AiHttpResult = AiHttpResult.Responded(AiHttpResponse(200, """{"choices":[{"message":{"content":"这是一个回答。"}}]}"""))

        override suspend fun send(request: AiHttpRequest): AiHttpResult {
            sends += 1
            return result
        }
    }

    private class RecordingNotes : WordAiNoteRepository {
        val saved = mutableListOf<WordAiNote>()
        var failSave = false
        override suspend fun save(note: WordAiNote): Result<Unit> =
            if (failSave) Result.failure(IllegalStateException("closed")) else Result.success(Unit).also { saved += note }
        override suspend fun list(profileId: String, lemma: String): Result<List<WordAiNote>> = Result.success(saved)
    }

    /** 构造真实用例 + 内存笔记库；transport/resolver 行为可由测试改写。 */
    private fun viewModel(
        transport: RecordingTransport = RecordingTransport(),
        notes: RecordingNotes = RecordingNotes(),
        resolver: DefaultTextProfileResult = DefaultTextProfileResult.Selected(profile),
    ): Triple<WordAiQaViewModel, RecordingTransport, RecordingNotes> {
        val useCase = WordQaUseCase(
            defaultTextProfile = DefaultTextProfileResolver { resolver },
            secrets = AiProfileSecretUseCase(FakeSecretStore()),
            transport = transport,
        )
        val viewModel = WordAiQaViewModel(
            ask = useCase,
            notes = notes,
            noteIds = { "note-1" },
            clock = com.example.englishlearning.core.time.FixedClockProvider(Instant.ofEpochMilli(1234L), java.time.ZoneOffset.UTC),
        )
        return Triple(viewModel, transport, notes)
    }

    @Test
    fun askFlowEndsInAnsweredWithTheModelText() = runTest(dispatcher) {
        val (viewModel, _, _) = viewModel()

        viewModel.ask(WordQaKind.Sentence, lemma = "apple", context = null, profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        assertEquals(WordAiQaUiState.Answered(WordQaKind.Sentence, "这是一个回答。"), viewModel.uiState.value)
    }

    @Test
    fun firstAskRequiresOutboundConfirmationBeforeTheNetwork() = runTest(dispatcher) {
        val (viewModel, transport, _) = viewModel()

        viewModel.ask(WordQaKind.Sentence, lemma = "apple", context = null, profileId = "default")
        advanceUntilIdle()

        assertEquals(WordAiQaUiState.NeedsOutboundConfirmation("api.example.com"), viewModel.uiState.value)
        assertEquals(0, transport.sends)
    }

    @Test
    fun confirmingReplaysTheSameRequest() = runTest(dispatcher) {
        val (viewModel, transport, _) = viewModel()

        viewModel.ask(WordQaKind.Sentence, lemma = "apple", context = null, profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        assertEquals(WordAiQaUiState.Answered(WordQaKind.Sentence, "这是一个回答。"), viewModel.uiState.value)
        assertEquals(1, transport.sends)
    }

    @Test
    fun decliningConfirmationReturnsToIdleWithoutSending() = runTest(dispatcher) {
        val (viewModel, transport, _) = viewModel()

        viewModel.ask(WordQaKind.Breakdown, lemma = "apple", context = null, profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = false)
        advanceUntilIdle()

        assertEquals(WordAiQaUiState.Idle, viewModel.uiState.value)
        assertEquals(0, transport.sends)
    }

    @Test
    fun unconfiguredProfileSurfacesTheReason() = runTest(dispatcher) {
        val (viewModel, _, _) = viewModel(resolver = DefaultTextProfileResult.NoSelection)

        viewModel.ask(WordQaKind.Sentence, lemma = "apple", context = null, profileId = "default")
        advanceUntilIdle()

        assertEquals(WordAiQaUiState.NotConfigured(WordQaNotConfiguredReason.NoDefaultProfile), viewModel.uiState.value)
    }

    @Test
    fun savingTheAnswerStoresANoteWithTheRequestContext() = runTest(dispatcher) {
        val (viewModel, _, notes) = viewModel()

        viewModel.ask(WordQaKind.Mnemonic, lemma = "apple", context = null, profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()
        viewModel.saveNote()
        advanceUntilIdle()

        val state = viewModel.uiState.value as WordAiQaUiState.Answered
        assertEquals(true, state.saved)
        val note = notes.saved.single()
        assertEquals("default", note.profileId)
        assertEquals("apple", note.lemma)
        assertEquals(WordQaKind.Mnemonic, note.kind)
        assertEquals("这是一个回答。", note.answer)
        assertEquals(1234L, note.createdAtEpochMillis)
        assertEquals("note-1", note.noteId)
    }

    @Test
    fun failedSaveIsVisibleForRetry() = runTest(dispatcher) {
        val notes = RecordingNotes().apply { failSave = true }
        val (viewModel, _, _) = viewModel(notes = notes)

        viewModel.ask(WordQaKind.Sentence, lemma = "apple", context = null, profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()
        viewModel.saveNote()
        advanceUntilIdle()

        val state = viewModel.uiState.value as WordAiQaUiState.Answered
        assertEquals(false, state.saved)
        assertEquals(true, state.saveFailed)
    }

    @Test
    fun confirmationOutsideTheConfirmationStateIsANoop() = runTest(dispatcher) {
        val (viewModel, transport, _) = viewModel()

        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        assertEquals(WordAiQaUiState.Idle, viewModel.uiState.value)
        assertEquals(0, transport.sends)
    }

    @Test
    fun saveNoteOutsideTheAnsweredStateIsANoop() = runTest(dispatcher) {
        val (viewModel, _, notes) = viewModel()

        viewModel.saveNote()
        advanceUntilIdle()

        assertEquals(0, notes.saved.size)
        assertEquals(WordAiQaUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun askingAgainResetsTheAnswer() = runTest(dispatcher) {
        val (viewModel, _, _) = viewModel()

        viewModel.ask(WordQaKind.Sentence, lemma = "apple", context = null, profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()
        viewModel.ask(WordQaKind.Breakdown, lemma = "apple", context = null, profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()

        // 换一种问法：旧回答不得残留（避免把 A 问法的答案存成 B 问法的笔记）。
        assertTrue(viewModel.uiState.value is WordAiQaUiState.Answered)
        assertEquals(WordQaKind.Breakdown, (viewModel.uiState.value as WordAiQaUiState.Answered).kind)
    }

    @Test
    fun resetClearsTheStaleAnswerForTheNextWord() = runTest(dispatcher) {
        val (viewModel, transport, notes) = viewModel()

        viewModel.ask(WordQaKind.Sentence, lemma = "apple", context = null, profileId = "default")
        advanceUntilIdle()
        viewModel.confirmOutbound(confirmed = true)
        advanceUntilIdle()
        viewModel.saveNote()
        advanceUntilIdle()
        viewModel.reset()
        advanceUntilIdle()

        assertEquals(WordAiQaUiState.Idle, viewModel.uiState.value)
        // 重置后旧回答不能再被保存：pendingRequest 与 lastAnswer 都已清空。
        viewModel.saveNote()
        advanceUntilIdle()
        assertEquals(1, notes.saved.size)
    }
}
