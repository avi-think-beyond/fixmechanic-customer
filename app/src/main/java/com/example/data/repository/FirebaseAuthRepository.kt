package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.FirestoreProvider
import com.example.data.FirestoreProvider.awaitResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirebaseAuthRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    companion object {
        private fun createFirestore(context: Context): FirebaseFirestore =
            FirestoreProvider.createFirestore(context.applicationContext)

        private fun createAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    }

    constructor(context: Context) : this(
        db = createFirestore(context),
        auth = createAuth()
    )

    override val isDemoMode: Boolean = false

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    override val isLoggedInFlow: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUserPhone = MutableStateFlow<String?>(
        auth.currentUser?.phoneNumber ?: auth.currentUser?.email
    )
    override val currentUserPhone: StateFlow<String?> = _currentUserPhone.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _isLoggedIn.value = true
                _currentUserPhone.value = user.phoneNumber ?: user.email
                scope.launch {
                    syncUserProfile(user)
                }
            } else {
                _isLoggedIn.value = false
                _currentUserPhone.value = null
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    private suspend fun syncUserProfile(user: FirebaseUser, explicitName: String? = null) {
        val now = System.currentTimeMillis()
        try {
            val userDoc = db.collection("users").document(user.uid)
            val snapshot = userDoc.get().awaitResult()
            val resolvedName = explicitName?.takeIf { it.isNotBlank() }
                ?: user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "FixMechanic Customer"

            if (!snapshot.exists()) {
                val profile = hashMapOf(
                    "uid" to user.uid,
                    "role" to "CUSTOMER",
                    "displayName" to resolvedName,
                    "email" to (user.email ?: ""),
                    "phone" to (user.phoneNumber ?: ""),
                    "disabled" to false,
                    "createdAt" to now,
                    "updatedAt" to now
                )
                userDoc.set(profile).awaitResult()
            } else {
                // Never write role/disabled on update so zero-trust rules succeed
                userDoc.set(
                    mapOf(
                        "email" to (user.email ?: snapshot.getString("email") ?: ""),
                        "displayName" to (snapshot.getString("displayName") ?: resolvedName),
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                ).awaitResult()
            }

            // Best-effort FCM token registration for Spark push alerts
            runCatching {
                val token = FirebaseMessaging.getInstance().token.awaitResult()
                if (token.isNotBlank()) {
                    db.collection("deviceTokens").document("${user.uid}_android").set(
                        mapOf(
                            "userId" to user.uid,
                            "role" to "CUSTOMER",
                            "platform" to "android",
                            "token" to token,
                            "updatedAt" to now
                        ),
                        SetOptions.merge()
                    ).awaitResult()
                }
            }

            _authState.value = AuthState.Authenticated(
                phone = user.phoneNumber ?: user.email ?: user.uid,
                isDemoAccount = false
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/${user.uid}")
            _authState.value = AuthState.Authenticated(
                phone = user.phoneNumber ?: user.email ?: user.uid,
                isDemoAccount = false
            )
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        _authState.value = AuthState.Loading
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).awaitResult()
            val user = authResult.user
            if (user != null) {
                syncUserProfile(user)
                Result.success(Unit)
            } else {
                val err = "Authentication completed but user is null."
                _authState.value = AuthState.Error(err)
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Google Sign-In failed", e)
            _authState.value = AuthState.Error(e.localizedMessage ?: "Google sign-in failed")
            Result.failure(e)
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.length < 6) {
            val err = "Please enter a valid email and password (minimum 6 characters)."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalArgumentException(err))
        }
        _authState.value = AuthState.Loading
        return try {
            val authResult = auth.signInWithEmailAndPassword(cleanEmail, password).awaitResult()
            val user = authResult.user ?: error("Sign-in succeeded but user is null.")
            syncUserProfile(user)
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.localizedMessage ?: "Email sign-in failed")
            Result.failure(e)
        }
    }

    override suspend fun registerWithEmail(name: String, email: String, password: String): Result<Unit> {
        val cleanEmail = email.trim()
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanEmail.isBlank() || password.length < 6) {
            val err = "Full name, valid email, and password (6+ chars) are required."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalArgumentException(err))
        }
        _authState.value = AuthState.Loading
        return try {
            val authResult = auth.createUserWithEmailAndPassword(cleanEmail, password).awaitResult()
            val user = authResult.user ?: error("Registration succeeded but user is null.")
            runCatching {
                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
                ).awaitResult()
            }
            syncUserProfile(user, explicitName = cleanName)
            Result.success(Unit)
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.localizedMessage ?: "Registration failed")
            Result.failure(e)
        }
    }

    override suspend fun requestOtp(phoneNumber: String): Result<String> {
        val err = "Firebase Phone SMS OTP requires the Blaze pay-as-you-go plan. On Spark, please sign in using Email/Password or Google Sign-In."
        _authState.value = AuthState.Error(err)
        return Result.failure(UnsupportedOperationException(err))
    }

    override suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<Boolean> {
        val err = "Firebase Phone SMS OTP requires the Blaze pay-as-you-go plan."
        _authState.value = AuthState.Error(err)
        return Result.failure(UnsupportedOperationException(err))
    }

    override suspend fun logout() {
        auth.signOut()
        _isLoggedIn.value = false
        _currentUserPhone.value = null
        _authState.value = AuthState.Unauthenticated
    }
}
