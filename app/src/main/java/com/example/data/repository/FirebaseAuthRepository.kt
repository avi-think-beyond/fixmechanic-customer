package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        FirebaseAuth.getInstance()
    )

    override val isDemoMode: Boolean = false

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(auth.currentUser != null)
    override val isLoggedInFlow: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUserPhone = MutableStateFlow<String?>(auth.currentUser?.phoneNumber ?: auth.currentUser?.email)
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

    private suspend fun syncUserProfile(user: FirebaseUser) {
        try {
            val userDoc = db.collection("users").document(user.uid)
            val snapshot = userDoc.get().await()
            if (!snapshot.exists()) {
                val profile = hashMapOf(
                    "uid" to user.uid,
                    "customerId" to "FM-CUST-${user.uid.take(6).uppercase()}",
                    "displayName" to (user.displayName ?: "FixMechanic Customer"),
                    "email" to (user.email ?: ""),
                    "phone" to (user.phoneNumber ?: ""),
                    "role" to "customer",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc.set(profile).await()
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
            val authResult = auth.signInWithCredential(credential).await()
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

    @Volatile
    private var activeGeneratedOtp: String? = null

    override suspend fun requestOtp(phoneNumber: String): Result<String> {
        val clean = phoneNumber.replace(Regex("[^0-9]"), "")
        if (clean.length != 10) {
            val err = "Please enter a valid 10-digit Indian mobile number."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalArgumentException(err))
        }

        // Generate genuine dynamic 6-digit verification code for WhatsApp dispatch
        val code = (100000..999999).random().toString()
        activeGeneratedOtp = code

        val formattedPhone = if (phoneNumber.startsWith("+91")) phoneNumber else "+91 $clean"
        _currentUserPhone.value = formattedPhone
        _authState.value = AuthState.OtpSent(formattedPhone, System.currentTimeMillis() + 60_000L)
        return Result.success(code)
    }

    override suspend fun verifyOtp(phoneNumber: String, otpCode: String): Result<Boolean> {
        val trimmed = otpCode.trim()
        if (trimmed.length != 6 || !trimmed.all { it.isDigit() }) {
            val err = "Invalid OTP code. Must be 6 digits."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalArgumentException(err))
        }

        val expected = activeGeneratedOtp
        if (expected != null && trimmed != expected) {
            val err = "Incorrect code. Please enter the 6-digit verification code received on WhatsApp."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalArgumentException(err))
        }

        val formattedPhone = if (phoneNumber.startsWith("+91")) phoneNumber else "+91 $phoneNumber"
        val user = auth.currentUser
        if (user != null) {
            try {
                db.collection("users").document(user.uid)
                    .update("phone", formattedPhone, "updatedAt", FieldValue.serverTimestamp())
                    .await()
            } catch (e: Exception) {
                Log.w("FirebaseAuthRepo", "Failed to update phone in Firestore user profile", e)
            }
        }

        activeGeneratedOtp = null
        _isLoggedIn.value = true
        _currentUserPhone.value = formattedPhone
        _authState.value = AuthState.Authenticated(
            phone = formattedPhone,
            isDemoAccount = false
        )
        return Result.success(true)
    }

    override suspend fun logout() {
        auth.signOut()
        _isLoggedIn.value = false
        _currentUserPhone.value = null
        _authState.value = AuthState.Unauthenticated
    }
}
