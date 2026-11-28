package com.example.data.repository

import android.app.Activity
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
    val isNewUserSession: StateFlow<Boolean>
    val currentUserPhone: StateFlow<String?>
    val isDemoMode: Boolean

    suspend fun signInWithGoogle(idToken: String): Result<Unit>
    suspend fun signInWithGoogleProvider(activity: Activity): Result<Unit> =
        Result.failure(UnsupportedOperationException("Interactive Google OAuth fallback is not supported."))
    suspend fun checkPendingGoogleSignIn(): Result<Boolean> = Result.success(false)
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>
    suspend fun registerWithEmail(name: String, email: String, password: String): Result<Unit>
    suspend fun requestOtp(phoneNumber: String): Result<String>
    suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<Boolean>
    fun markProfileSetupCompleted() {}
    suspend fun logout()
}

class PrototypeAuthRepository : AuthRepository {
    override val isDemoMode: Boolean = true
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()
    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedInFlow: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    private val _isNewUserSession = MutableStateFlow(false)
    override val isNewUserSession: StateFlow<Boolean> = _isNewUserSession.asStateFlow()
    private val _currentUserPhone = MutableStateFlow<String?>(null)
    override val currentUserPhone: StateFlow<String?> = _currentUserPhone.asStateFlow()

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        Result.failure(IllegalStateException("Prototype mode disabled; use FirebaseAuthRepository."))

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> =
        Result.failure(IllegalStateException("Prototype mode disabled; use FirebaseAuthRepository."))

    override suspend fun registerWithEmail(name: String, email: String, password: String): Result<Unit> =
        Result.failure(IllegalStateException("Prototype mode disabled; use FirebaseAuthRepository."))

    override suspend fun requestOtp(phoneNumber: String): Result<String> =
        Result.failure(IllegalStateException("SMS OTP requires Firebase Blaze plan."))

    override suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<Boolean> =
        Result.failure(IllegalStateException("SMS OTP requires Firebase Blaze plan."))

    override fun markProfileSetupCompleted() {
        _isNewUserSession.value = false
    }

    override suspend fun logout() {
        _isNewUserSession.value = false
        _isLoggedIn.value = false
        _currentUserPhone.value = null
        _authState.value = AuthState.Unauthenticated
    }
}
