package com.hypermusic.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.BuildConfig
import com.hypermusic.core.data.repository.AppUpdateRepository
import com.hypermusic.core.model.AppUpdateData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class UpToDate(val currentVersion: String) : UpdateUiState
    data class NoPublishedRelease(val message: String = "No public releases published yet") : UpdateUiState
    data class UpdateAvailable(val updateData: AppUpdateData) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class AppUpdateViewModel(
    private val updateRepository: AppUpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        checkForUpdates()
    }

    fun checkForUpdates() {
        if (_uiState.value is UpdateUiState.Checking) return

        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            val result = updateRepository.checkForUpdates()
            result.fold(
                onSuccess = { checkResult ->
                    if (checkResult.isUpdateAvailable && checkResult.updateData != null) {
                        _uiState.value = UpdateUiState.UpdateAvailable(checkResult.updateData)
                    } else if (!checkResult.hasPublishedReleases) {
                        _uiState.value = UpdateUiState.NoPublishedRelease()
                    } else {
                        _uiState.value = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                    }
                },
                onFailure = { error ->
                    _uiState.value = UpdateUiState.Error(
                        error.message ?: "Failed to check for updates. Check internet connection."
                    )
                }
            )
        }
    }
}
