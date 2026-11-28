package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.repository.AuthRepository
import com.example.di.ServiceLocator
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val isRegisterMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isVerified: Boolean = false,
    val isNewUser: Boolean = false,
    val isDemoMode: Boolean = false,
    val authRefreshCount: Int = 0
)

class AuthViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isVerified = authRepository.isLoggedInFlow.value,
            isNewUser = authRepository.isNewUserSession.value,
            isDemoMode = authRepository.isDemoMode
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.isLoggedInFlow.collect { loggedIn ->
                _uiState.update {
                    it.copy(
                        isVerified = loggedIn,
                        isNewUser = if (loggedIn) authRepository.isNewUserSession.value else false
                    )
                }
            }
        }
        viewModelScope.launch {
            authRepository.isNewUserSession.collect { isNew ->
                _uiState.update { it.copy(isNewUser = isNew) }
            }
        }
    }

    fun checkPendingSignIn() {
        viewModelScope.launch {
            val result = authRepository.checkPendingGoogleSignIn()
            result.onSuccess { handled ->
                if (handled) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isVerified = true,
                            isNewUser = authRepository.isNewUserSession.value,
                            errorMessage = null,
                            authRefreshCount = it.authRefreshCount + 1
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Google Sign-In failed."
                    )
                }
            }
        }
    }

    fun consumeNewUserProfilePrompt() {
        authRepository.markProfileSetupCompleted()
        _uiState.update { it.copy(isNewUser = false) }
    }

    fun onNameChanged(value: String) {
        _uiState.update { it.copy(fullName = value, errorMessage = null) }
    }

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun toggleRegisterMode() {
        _uiState.update { it.copy(isRegisterMode = !it.isRegisterMode, errorMessage = null, infoMessage = null) }
    }

    fun submitEmailAuth() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = if (s.isRegisterMode) {
                authRepository.registerWithEmail(s.fullName, s.email, s.password)
            } else {
                authRepository.signInWithEmail(s.email, s.password)
            }
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isVerified = true,
                        isNewUser = authRepository.isNewUserSession.value,
                        errorMessage = null,
                        authRefreshCount = it.authRefreshCount + 1
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Authentication failed."
                    )
                }
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private suspend fun tryGoogleProviderFallback(activity: Activity?, priorError: String) {
        if (activity == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = priorError
                )
            }
            return
        }
        Log.i("AuthViewModel", "Falling back to Firebase Google OAuth provider flow")
        val fallbackResult = authRepository.signInWithGoogleProvider(activity)
        fallbackResult.onSuccess {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isVerified = true,
                    isNewUser = authRepository.isNewUserSession.value,
                    errorMessage = null,
                    authRefreshCount = it.authRefreshCount + 1
                )
            }
        }.onFailure { err ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = err.message ?: priorError
                )
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val activity = context.findActivity()
            try {
                val webClientId = runCatching {
                    context.getString(R.string.default_web_client_id).trim()
                }.getOrDefault("")
                if (webClientId.isBlank()) {
                    tryGoogleProviderFallback(
                        activity = activity,
                        priorError = "Google Sign-In requires google-services.json with default_web_client_id."
                    )
                    return@launch
                }

                val activityContext: Context = activity ?: context
                val credentialManager = CredentialManager.create(activityContext)
                val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = activityContext, request = request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val authResult = authRepository.signInWithGoogle(idToken)
                    authResult.onSuccess {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isVerified = true,
                                isNewUser = authRepository.isNewUserSession.value,
                                errorMessage = null,
                                authRefreshCount = it.authRefreshCount + 1
                            )
                        }
                    }.onFailure { ex ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = ex.message ?: "Firebase Sign-in failed."
                            )
                        }
                    }
                } else {
                    tryGoogleProviderFallback(
                        activity = activity,
                        priorError = "Unexpected credential format returned."
                    )
                }
            } catch (e: GetCredentialCancellationException) {
                val msg = e.errorMessage?.toString().orEmpty()
                val isConfigFailure = msg.contains("Developer console", ignoreCase = true) ||
                    msg.contains("28436", ignoreCase = true) ||
                    msg.contains("Caller has been temporarily blocked", ignoreCase = true) ||
                    msg.contains("reauth failed", ignoreCase = true)
                if (isConfigFailure && activity != null) {
                    Log.w("AuthViewModel", "CredentialManager returned config error ($msg); using OAuth fallback", e)
                    tryGoogleProviderFallback(activity, msg)
                } else {
                    Log.w("AuthViewModel", "Sign-in cancelled by user", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Google Sign-In cancelled. Please select a Google account or use Email & Password."
                        )
                    }
                }
            } catch (e: NoCredentialException) {
                Log.w("AuthViewModel", "No CredentialManager Google credential available; using Firebase OAuth fallback", e)
                tryGoogleProviderFallback(
                    activity = activity,
                    priorError = "No Google account credential found for this build."
                )
            } catch (e: GetCredentialException) {
                Log.w("AuthViewModel", "CredentialManager error (${e.type}); using Firebase OAuth fallback", e)
                val detail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() }
                    ?: e.localizedMessage
                    ?: "Google Sign-In failed (${e.type})."
                tryGoogleProviderFallback(activity = activity, priorError = detail)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Google sign-in error", e)
                tryGoogleProviderFallback(
                    activity = activity,
                    priorError = e.localizedMessage ?: "Google Sign-in failed. Please retry."
                )
            }
        }
    }

    fun logout(context: Context? = null) {
        // Synchronously reset verified state so UI never flashes stale session while async sign-out completes
        _uiState.value = AuthUiState(isVerified = false, isNewUser = false, isDemoMode = authRepository.isDemoMode)
        viewModelScope.launch {
            if (context != null) {
                runCatching {
                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                }
            }
            authRepository.logout()
            _uiState.value = AuthUiState(isVerified = false, isNewUser = false, isDemoMode = authRepository.isDemoMode)
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState(
            isVerified = authRepository.isLoggedInFlow.value,
            isNewUser = authRepository.isNewUserSession.value,
            isDemoMode = authRepository.isDemoMode
        )
    }
}
