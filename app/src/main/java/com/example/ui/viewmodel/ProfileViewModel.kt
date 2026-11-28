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
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfileData = UserProfileData(),
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccessMessage: String? = null,
    val errorMessage: String? = null,
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

    fun setEditMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isEditMode = enabled,
                errorMessage = null,
                saveSuccessMessage = if (enabled) null else it.saveSuccessMessage
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, saveSuccessMessage = null) }
    }

    fun saveProfileDetails(
        fullName: String,
        phoneNumber: String,
        primaryVehicle: String,
        emergencyContactName: String,
        emergencyContactPhone: String,
        onSaved: () -> Unit = {}
    ) {
        val cleanName = fullName.trim()
        if (cleanName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your full name.") }
            return
        }

        val current = profileRepository.profile.value
        val updated = current.copy(
            fullName = cleanName,
            phoneNumber = phoneNumber.trim(),
            primaryVehicle = primaryVehicle.trim(),
            emergencyContactName = emergencyContactName.trim(),
            emergencyContactPhone = emergencyContactPhone.trim()
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, saveSuccessMessage = null) }
            val result = profileRepository.saveProfile(updated)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isEditMode = false,
                        saveSuccessMessage = "Profile details updated successfully.",
                        errorMessage = null
                    )
                }
                onSaved()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = err.localizedMessage ?: "Failed to save profile details. Please retry."
                    )
                }
            }
        }
    }

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
        if (show) {
            backendStatusRepository.refreshStatus()
        }
        _uiState.update { it.copy(showBackendStatusDialog = show) }
    }

    fun refreshBackendStatus() {
        backendStatusRepository.refreshStatus()
    }

    fun resetForLogout() {
        profileRepository.clearSession()
        backendStatusRepository.clearSession()
        _uiState.value = ProfileUiState()
    }
}
