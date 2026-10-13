package com.example.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class OtpSent(val phone: String, val sessionExpiresAt: Long) : AuthState()
    data class Authenticated(val phone: String, val isDemoAccount: Boolean) : AuthState()
    data class Error(val message: String) : AuthState()
}

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val isLoggedInFlow: StateFlow<Boolean>
    val currentUserPhone: StateFlow<String?>
    val isDemoMode: Boolean

    suspend fun requestOtp(phoneNumber: String): Result<String>
    suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<Boolean>
    suspend fun logout()
}

/**
 * Robust Implementation of AuthRepository.
 * Designed with a clean interface for direct replacement by FirebaseAuthRepository
 * once Firebase Authentication is configured in the project.
 * When Firebase is unprovisioned, operates in explicit, clearly labeled Demo Mode.
 */
class PrototypeAuthRepository : AuthRepository {

    override val isDemoMode: Boolean = true

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedInFlow: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUserPhone = MutableStateFlow<String?>(null)
    override val currentUserPhone: StateFlow<String?> = _currentUserPhone.asStateFlow()

    // Demo mode OTP storage for verification (randomized per request, never hardcoded)
    private var generatedDemoOtp: String? = null

    override suspend fun requestOtp(phoneNumber: String): Result<String> {
        val clean = phoneNumber.replace(Regex("[^0-9]"), "")
        if (clean.length != 10) {
            val err = "Please enter a valid 10-digit Indian mobile number."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalArgumentException(err))
        }

        _authState.value = AuthState.Loading
        delay(900) // Realistic network latency

        // In Demo Mode: generate a fresh 6-digit OTP
        val randomizedOtp = (100000..999999).random().toString()
        generatedDemoOtp = randomizedOtp
        val formattedPhone = if (phoneNumber.startsWith("+91")) phoneNumber else "+91 $clean"
        _currentUserPhone.value = formattedPhone

        val expiresAt = System.currentTimeMillis() + 60_000L
        _authState.value = AuthState.OtpSent(formattedPhone, expiresAt)

        return Result.success("Demo Mode: 6-digit OTP sent to $formattedPhone (Use $randomizedOtp for verification in demo mode).")
    }

    override suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<Boolean> {
        val trimmedOtp = otpCode.trim()
        if (trimmedOtp.length != 6 || !trimmedOtp.all { it.isDigit() }) {
            val err = "Invalid OTP. Verification code must be exactly 6 numeric digits."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalArgumentException(err))
        }

        _authState.value = AuthState.Loading
        delay(800) // Realistic network latency

        // Verification check against dynamically generated demo OTP
        val expected = generatedDemoOtp
        if (expected != null && trimmedOtp != expected) {
            val err = "Incorrect OTP entered. Please verify the code or request a new one."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalArgumentException(err))
        }

        _isLoggedIn.value = true
        _authState.value = AuthState.Authenticated(phoneNumber, isDemoAccount = true)
        return Result.success(true)
    }

    override suspend fun logout() {
        _isLoggedIn.value = false
        _currentUserPhone.value = null
        generatedDemoOtp = null
        _authState.value = AuthState.Unauthenticated
    }
}
