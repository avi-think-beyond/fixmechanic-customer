package com.example.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserProfileData(
    val fullName: String = "Customer",
    val phoneNumber: String = "",
    val email: String = "",
    val emergencyContactName: String = "Not set",
    val emergencyContactPhone: String = "",
    val primaryVehicle: String = "",
    val isNumberMaskingEnabled: Boolean = true,
    val isGpsSharingEnabled: Boolean = true,
    val preferredLanguage: String = "en" // "en" or "hi"
)

interface ProfileRepository {
    val profile: StateFlow<UserProfileData>
    fun updateProfile(profile: UserProfileData)
    suspend fun saveProfile(profile: UserProfileData): Result<Unit> {
        updateProfile(profile)
        return Result.success(Unit)
    }
    fun toggleNumberMasking(enabled: Boolean)
    fun toggleGpsSharing(enabled: Boolean)
    fun setLanguage(langCode: String)
    fun clearSession() {}
}

class PrototypeProfileRepository : ProfileRepository {

    private val _profile = MutableStateFlow(UserProfileData())
    override val profile: StateFlow<UserProfileData> = _profile.asStateFlow()

    override fun updateProfile(profile: UserProfileData) {
        _profile.value = profile
    }

    override fun toggleNumberMasking(enabled: Boolean) {
        _profile.value = _profile.value.copy(isNumberMaskingEnabled = enabled)
    }

    override fun toggleGpsSharing(enabled: Boolean) {
        _profile.value = _profile.value.copy(isGpsSharingEnabled = enabled)
    }

    override fun setLanguage(langCode: String) {
        _profile.value = _profile.value.copy(preferredLanguage = langCode)
    }

    override fun clearSession() {
        _profile.value = UserProfileData()
    }
}
