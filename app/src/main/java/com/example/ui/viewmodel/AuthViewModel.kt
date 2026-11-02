package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val phoneNumber: String = "",
    val otpCode: String = "",
    val isOtpSent: Boolean = false,
    val countdownSeconds: Int = 30,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val demoNoticeMessage: String? = null,
    val generatedOtp: String? = null,
    val isVerified: Boolean = false,
    val isDemoMode: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(isDemoMode = authRepository.isDemoMode))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun onPhoneChanged(newPhone: String) {
        val filtered = newPhone.filter { it.isDigit() }.take(10)
        _uiState.update { it.copy(phoneNumber = filtered, errorMessage = null, demoNoticeMessage = null) }
    }

    fun onOtpChanged(newOtp: String) {
        val filtered = newOtp.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(otpCode = filtered, errorMessage = null) }
    }

    fun sendOtp(context: android.content.Context? = null) {
        val phone = _uiState.value.phoneNumber
        if (phone.length != 10) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid 10-digit Indian mobile number.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, demoNoticeMessage = null) }
            val result = authRepository.requestOtp(phone)
            result.onSuccess { generatedCode ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isOtpSent = true,
                        countdownSeconds = 45,
                        errorMessage = null,
                        otpCode = "",
                        generatedOtp = generatedCode,
                        demoNoticeMessage = "Verification code generated! Tap 'Send via WhatsApp' to receive or dispatch your OTP freely."
                    )
                }
                startCountdown()

                // If context provided, dispatch via WhatsApp intent freely
                if (context != null) {
                    dispatchViaWhatsApp(context, phone, generatedCode)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to generate verification OTP."
                    )
                }
            }
        }
    }

    fun dispatchViaWhatsApp(context: android.content.Context, phone: String, code: String) {
        try {
            val fullPhone = if (phone.startsWith("91")) phone else "91$phone"
            val messageText = "FixMechanic Live Verification Code: $code\n\nYour security OTP for 24/7 Roadside Assistance. Valid for 10 minutes."
            val encodedMessage = java.net.URLEncoder.encode(messageText, "UTF-8")
            val uri = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$fullPhone&text=$encodedMessage")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("AuthViewModel", "WhatsApp dispatch error", e)
        }
    }

    fun verifyOtp() {
        val otp = _uiState.value.otpCode
        if (otp.length != 6) {
            _uiState.update { it.copy(errorMessage = "Please enter the full 6-digit OTP code.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.verifyOtp(_uiState.value.phoneNumber, otp)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isVerified = true, errorMessage = null) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Authentication failed. Invalid OTP."
                    )
                }
            }
        }
    }

    fun resendOtp(context: android.content.Context? = null) {
        if (_uiState.value.countdownSeconds <= 0) {
            sendOtp(context)
        }
    }

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.countdownSeconds > 0) {
                delay(1000)
                _uiState.update { it.copy(countdownSeconds = it.countdownSeconds - 1) }
            }
        }
    }

    fun signInWithGoogle(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val credentialManager = androidx.credentials.CredentialManager.create(context)
                val googleIdOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(
                    context.getString(com.example.R.string.default_web_client_id)
                ).build()

                val request = androidx.credentials.GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential

                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val authResult = authRepository.signInWithGoogle(idToken)
                    authResult.onSuccess {
                        _uiState.update { it.copy(isLoading = false, isVerified = true, errorMessage = null) }
                    }.onFailure { ex ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = ex.localizedMessage ?: "Firebase Sign-in failed."
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Unexpected credential format returned."
                        )
                    }
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                android.util.Log.w("AuthViewModel", "Sign-in cancelled by user", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Sign-in cancelled. Please select a Google account to continue."
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "Google sign-in error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Google Sign-in failed. Please retry."
                    )
                }
            }
        }
    }

    fun resetState() {
        timerJob?.cancel()
        _uiState.value = AuthUiState(isDemoMode = authRepository.isDemoMode)
    }
}
