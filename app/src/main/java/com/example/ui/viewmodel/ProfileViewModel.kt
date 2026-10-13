package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BackendIntegrationStatus
import com.example.data.repository.BackendStatusRepository
import com.example.data.repository.ProfileRepository
import com.example.data.repository.UserProfileData
import com.example.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ProfileUiState(
    val profile: UserProfileData = UserProfileData(),
    val isEditMode: Boolean = false,
    val showBackendStatusDialog: Boolean = false
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository = ServiceLocator.profileRepository,
    private val backendStatusRepository: BackendStatusRepository = ServiceLocator.backendStatusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val profileData: StateFlow<UserProfileData> = profileRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfileData())

    val backendStatus: StateFlow<BackendIntegrationStatus> = backendStatusRepository.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackendIntegrationStatus())

    fun toggleNumberMasking(enabled: Boolean) {
        profileRepository.toggleNumberMasking(enabled)
    }

    fun toggleGpsSharing(enabled: Boolean) {
        profileRepository.toggleGpsSharing(enabled)
    }

    fun setLanguage(langCode: String) {
        profileRepository.setLanguage(langCode)
    }

    fun showBackendDialog(show: Boolean) {
        _uiState.update { it.copy(showBackendStatusDialog = show) }
    }
}
