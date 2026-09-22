package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.reading.ArticleRepository
import com.example.englishlearning.reading.ReadingPreferenceRepository
import com.example.englishlearning.reading.domain.ArticleType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ReadingAccessViewModel @Inject constructor(
    private val articles: ArticleRepository,
    private val preferences: ReadingPreferenceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ReadingAccessUiState>(ReadingAccessUiState.Loading)
    val uiState: StateFlow<ReadingAccessUiState> = _uiState

    fun load(profileId: String, isUnlocked: Boolean, unlockReason: String) {
        _uiState.value = ReadingAccessUiState.Loading
        viewModelScope.launch {
            val preference = preferences.getPreference(profileId)
            val history = articles.findHistory(profileId)
            _uiState.value = when {
                preference.isFailure || history.isFailure -> ReadingAccessUiState.Unavailable
                !isUnlocked -> ReadingAccessUiState.Locked(unlockReason)
                else -> ReadingAccessUiState.Ready(preference.getOrThrow(), history.getOrThrow().size)
            }
        }
    }

    fun selectType(type: ArticleType) {
        val current = _uiState.value as? ReadingAccessUiState.Ready ?: return
        val updatedPreference = current.preference.copy(defaultArticleType = type)
        _uiState.value = current.copy(preference = updatedPreference)
        viewModelScope.launch { preferences.savePreference(updatedPreference) }
    }
}
