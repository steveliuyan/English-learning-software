package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.LocalProfile
import com.example.englishlearning.profile.LocalProfileRepository
import com.example.englishlearning.profile.ProfileException
import com.example.englishlearning.profile.toSafeAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AppUiState { data object Loading: AppUiState; data object NeedsProfile: AppUiState; data class Ready(val profile: LocalProfile): AppUiState; data class Error(val error: AppError): AppUiState }
@HiltViewModel
class AppViewModel @Inject constructor(private val repository: LocalProfileRepository, private val create: CreateLocalProfileUseCase): ViewModel() {
    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val uiState: StateFlow<AppUiState> = _uiState
    init { reload() }
    fun reload() = viewModelScope.launch {
        runCatching { repository.getDefault() }
            .onSuccess { profile -> _uiState.value = profile?.let { AppUiState.Ready(it) } ?: AppUiState.NeedsProfile }
            .onFailure { _uiState.value = AppUiState.Error(it.toSafeAppError()) }
    }
    fun createProfile(name: String) = viewModelScope.launch {
        runCatching { create(name) }
            .getOrNull()
            ?.onSuccess { _uiState.value = AppUiState.Ready(it) }
            ?.onFailure { error -> _uiState.value = AppUiState.Error(error.toSafeAppError()) }
            ?: run { _uiState.value = AppUiState.Error(AppError.DatabaseMigrationFailed) }
    }
}
