package com.example.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserProfileData(
    val fullName: String = "Aditya Sharma",
    val phoneNumber: String = "+91 98765 43210",
    val email: String = "asharma05246@gmail.com",
    val emergencyContactName: String = "Pooja Sharma (Family)",
    val emergencyContactPhone: String = "+91 98111 22334",
    val primaryVehicle: String = "Honda City ZX & RE Classic 350",
    val isNumberMaskingEnabled: Boolean = true,
    val isGpsSharingEnabled: Boolean = true,
    val preferredLanguage: String = "en" // "en" or "hi"
)

interface ProfileRepository {
    val profile: StateFlow<UserProfileData>
    fun updateProfile(profile: UserProfileData)
    fun toggleNumberMasking(enabled: Boolean)
    fun toggleGpsSharing(enabled: Boolean)
    fun setLanguage(langCode: String)
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
}
