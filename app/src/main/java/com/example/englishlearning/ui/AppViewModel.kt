package com.example.englishlearning.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.example.englishlearning.core.error.AppError
import com.example.englishlearning.core.storage.DatabaseStartupProbe
import com.example.englishlearning.profile.CreateLocalProfileUseCase
import com.example.englishlearning.profile.LocalProfile
import com.example.englishlearning.profile.LocalProfileRepository
import com.example.englishlearning.profile.ProfileException
import com.example.englishlearning.profile.toSafeAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AppUiState { data object Loading: AppUiState; data object NeedsProfile: AppUiState; data class Ready(val profile: LocalProfile): AppUiState; data class Error(val error: AppError): AppUiState; data class NeedsRecovery(val reason: String): AppUiState }
@HiltViewModel
class AppViewModel @Inject constructor(private val repository: LocalProfileRepository, private val create: CreateLocalProfileUseCase, private val startupProbe: DatabaseStartupProbe): ViewModel() {
    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val uiState: StateFlow<AppUiState> = _uiState

    // Serializes each action's repository access with its `_uiState` write so a newer action can
    // never be overwritten by a slower, earlier one (e.g. a late "no profile" reload).
    private val stateMutex = Mutex()

    init { reload() }
    fun reload() = viewModelScope.launch {
        stateMutex.withLock {
            // SP-B 决策 §3：迁移/打开失败呈现为可恢复态——先于任何仓储读取判定，
            // 否则首个 DAO 访问只会把失败伪装成通用 StorageUnavailable。
            val recoveryReason = startupProbe.failureReason
            if (recoveryReason != null) {
                _uiState.value = AppUiState.NeedsRecovery(recoveryReason)
            } else {
                runCatching { repository.getDefault() }
                    .onSuccess { profile -> _uiState.value = profile?.let { AppUiState.Ready(it) } ?: AppUiState.NeedsProfile }
                    .onFailure { _uiState.value = AppUiState.Error(it.toSafeAppError()) }
            }
        }
    }

    /** 「重试」入口：重开数据库（失败路径绝不删文件），无论成败都刷新状态。 */
    fun retryDatabaseStartup() = viewModelScope.launch {
        startupProbe.retry()
        reload()
    }
    fun createProfile(name: String) = viewModelScope.launch {
        stateMutex.withLock {
            runCatching { create(name) }
                .fold(
                    onSuccess = { result ->
                        result
                            .onSuccess { _uiState.value = AppUiState.Ready(it) }
                            .onFailure { error -> _uiState.value = AppUiState.Error(error.toSafeAppError()) }
                    },
                    onFailure = { error -> _uiState.value = AppUiState.Error(error.toSafeAppError()) },
                )
        }
    }
}
