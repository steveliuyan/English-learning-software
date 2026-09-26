package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.ai.AiProfileRepository
import com.example.englishlearning.ai.AiProfileSecretUseCase
import com.example.englishlearning.ai.domain.AiCapability
import com.example.englishlearning.ai.domain.AiProfile
import com.example.englishlearning.language.SpeechPreferenceRepository
import com.example.englishlearning.language.domain.PronunciationEngine
import com.example.englishlearning.language.domain.SpeechPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SpeechProfileStatus { Available, MissingKey }

data class SpeechProfileCandidate(val profileId: String, val displayName: String, val status: SpeechProfileStatus)

data class SpeechSettingsUiState(
    val selectedEngine: PronunciationEngine = PronunciationEngine.SystemTts,
    val openAiProfileId: String? = null,
    val miMoProfileId: String? = null,
    val candidates: List<SpeechProfileCandidate> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class SpeechSettingsViewModel @Inject constructor(
    private val profiles: AiProfileRepository,
    private val secrets: AiProfileSecretUseCase,
    private val preferences: SpeechPreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SpeechSettingsUiState())
    val state: StateFlow<SpeechSettingsUiState> = _state

    fun load() = viewModelScope.launch {
        val preference = preferences.get().getOrDefault(SpeechPreference())
        val speechProfiles = profiles.list().getOrDefault(emptyList()).filter { AiCapability.Speech in it.capabilities }
        _state.value = SpeechSettingsUiState(
            selectedEngine = preference.selectedEngine,
            openAiProfileId = preference.openAiProfileId,
            miMoProfileId = preference.miMoProfileId,
            candidates = speechProfiles.map { it.toCandidate() },
        )
    }

    fun select(engine: PronunciationEngine, profileId: String? = null) = viewModelScope.launch {
        val current = _state.value
        val next = SpeechPreference(
            selectedEngine = engine,
            openAiProfileId = if (engine == PronunciationEngine.OpenAi) profileId else current.openAiProfileId,
            miMoProfileId = if (engine == PronunciationEngine.MiMo) profileId else current.miMoProfileId,
        )
        if (preferences.save(next).isSuccess) {
            _state.value = current.copy(
                selectedEngine = next.selectedEngine,
                openAiProfileId = next.openAiProfileId,
                miMoProfileId = next.miMoProfileId,
                message = null,
            )
        } else {
            _state.value = current.copy(message = "本机存储暂时不可用，改动没有保存。")
        }
    }

    private fun AiProfile.toCandidate() = SpeechProfileCandidate(
        profileId = profileId,
        displayName = displayName,
        status = if (secrets.hasKey(this).getOrDefault(false)) SpeechProfileStatus.Available else SpeechProfileStatus.MissingKey,
    )
}
