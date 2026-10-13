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
    val isVerified: Boolean = false,
    val isDemoMode: Boolean = true
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

    fun sendOtp() {
        val phone = _uiState.value.phoneNumber
        if (phone.length != 10) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid 10-digit Indian mobile number.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, demoNoticeMessage = null) }
            val result = authRepository.requestOtp(phone)
            result.onSuccess { message ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isOtpSent = true,
                        countdownSeconds = 30,
                        errorMessage = null,
                        demoNoticeMessage = if (authRepository.isDemoMode) message else null
                    )
                }
                startCountdown()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Network error. Failed to dispatch OTP."
                    )
                }
            }
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

    fun resendOtp() {
        if (_uiState.value.countdownSeconds <= 0) {
            sendOtp()
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

    fun resetState() {
        timerJob?.cancel()
        _uiState.value = AuthUiState(isDemoMode = authRepository.isDemoMode)
    }
}
