package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isDemoMode: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository = ServiceLocator.authRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isVerified = authRepository.isLoggedInFlow.value,
            isDemoMode = authRepository.isDemoMode
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.isLoggedInFlow.collect { loggedIn ->
                _uiState.update { it.copy(isVerified = loggedIn) }
            }
        }
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
                _uiState.update { it.copy(isLoading = false, isVerified = true, errorMessage = null) }
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

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val webClientResId = context.resources.getIdentifier(
                    "default_web_client_id",
                    "string",
                    context.packageName
                )
                if (webClientResId == 0) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Google Sign-In requires google-services.json with default_web_client_id. Use Email & Password login on Spark or add google-services.json."
                        )
                    }
                    return@launch
                }

                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetSignInWithGoogleOption.Builder(
                    context.getString(webClientResId)
                ).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
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
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthViewModel", "Sign-in cancelled by user", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Sign-in cancelled. Please select a Google account to continue."
                    )
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Google sign-in error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Google Sign-in failed. Please retry."
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = AuthUiState(isVerified = false, isDemoMode = authRepository.isDemoMode)
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState(
            isVerified = authRepository.isLoggedInFlow.value,
            isDemoMode = authRepository.isDemoMode
        )
    }
}
