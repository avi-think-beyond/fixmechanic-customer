package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.data.FirestoreProvider
import com.example.data.FirestoreProvider.awaitResult
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class FirebaseAuthRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val onSessionCleared: () -> Unit = {}
) : AuthRepository {

    companion object {
        private const val PROFILE_SYNC_TIMEOUT_MS = 4500L

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

    private val _isNewUserSession = MutableStateFlow(false)
    override val isNewUserSession: StateFlow<Boolean> = _isNewUserSession.asStateFlow()

    private val _currentUserPhone = MutableStateFlow<String?>(
        auth.currentUser?.phoneNumber ?: auth.currentUser?.email
    )
    override val currentUserPhone: StateFlow<String?> = _currentUserPhone.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val profileSyncMutex = Mutex()
    private val isExplicitAuthInProgress = AtomicBoolean(false)
    @Volatile
    private var activeUid: String? = auth.currentUser?.uid

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val nextUid = user?.uid
            if (activeUid != null && activeUid != nextUid) {
                onSessionCleared()
            }
            activeUid = nextUid
            if (user != null) {
                // If signIn/register is actively running, let it determine isNewUser & update state first.
                if (!isExplicitAuthInProgress.get()) {
                    _currentUserPhone.value = user.phoneNumber ?: user.email
                    _isLoggedIn.value = true
                    scope.launch {
                        syncUserProfile(user, authReportedNewUser = false)
                    }
                }
            } else {
                onSessionCleared()
                _isNewUserSession.value = false
                _isLoggedIn.value = false
                _currentUserPhone.value = null
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    override fun markProfileSetupCompleted() {
        _isNewUserSession.value = false
    }

    private fun currentProjectId(): String = runCatching {
        FirebaseApp.getInstance().options.projectId
    }.getOrNull().orEmpty().ifBlank { "unknown" }

    private fun formatAuthError(e: Exception, fallback: String): String {
        val projectId = currentProjectId()
        if (e is FirebaseAuthException) {
            return when (e.errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "This sign-in method is disabled on Firebase project '$projectId'. Verify the installed APK is built with fixmechanic-firebase google-services.json."
                "ERROR_EMAIL_ALREADY_IN_USE" ->
                    "An account already exists with this email. Tap 'Sign In' below to log in."
                "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" ->
                    "Invalid email or password. Please check your credentials and try again."
                "ERROR_INVALID_EMAIL" ->
                    "Please enter a valid email address."
                "ERROR_WEAK_PASSWORD" ->
                    "Password is too weak. Please enter at least 6 characters."
                "ERROR_USER_DISABLED" ->
                    "This customer account has been disabled. Please contact FixMechanic support."
                "ERROR_TOO_MANY_REQUESTS" ->
                    "Too many sign-in attempts. Please wait a moment and try again."
                "ERROR_WEB_CONTEXT_CANCELED" ->
                    "Google Sign-In was cancelled. Please select a Google account to continue."
                else -> "${e.localizedMessage ?: fallback} (code: ${e.errorCode}, project: $projectId)"
            }
        }
        return e.localizedMessage ?: fallback
    }

    private fun resolveAuthProvider(user: FirebaseUser): String {
        val providers = user.providerData.mapNotNull { it.providerId }
        return when {
            providers.any { it.equals("google.com", ignoreCase = true) } -> "google"
            providers.any { it.equals("password", ignoreCase = true) } -> "password"
            providers.any { it.equals("phone", ignoreCase = true) } -> "phone"
            else -> "firebase"
        }
    }

    private suspend fun upsertFirestoreUserDoc(
        user: FirebaseUser,
        explicitName: String?,
        authReportedNewUser: Boolean,
        forceTokenRefresh: Boolean = false
    ): Boolean {
        // Ensure Firebase Auth ID token is ready for Firestore security rules
        runCatching {
            withTimeoutOrNull(3000L) {
                user.getIdToken(forceTokenRefresh).awaitResult()
            }
        }

        val now = System.currentTimeMillis()
        val userDoc = db.collection("users").document(user.uid)
        val snapshot = userDoc.get().awaitResult()
        val resolvedName = explicitName?.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "FixMechanic Customer"
        val authProvider = resolveAuthProvider(user)
        val photoUrl = user.photoUrl?.toString().orEmpty()

        return if (!snapshot.exists()) {
            val profile = hashMapOf(
                "uid" to user.uid,
                "role" to "CUSTOMER",
                "displayName" to resolvedName,
                "email" to (user.email ?: ""),
                "phone" to (user.phoneNumber ?: ""),
                "photoUrl" to photoUrl,
                "authProvider" to authProvider,
                "disabled" to false,
                "profileCompleted" to false,
                "createdAt" to now,
                "updatedAt" to now
            )
            userDoc.set(profile).awaitResult()
            Log.i("FirebaseAuthRepo", "Created users/${user.uid} record ($authProvider)")
            true
        } else {
            // Never write role/disabled on update so zero-trust rules succeed
            val updateName = explicitName?.takeIf { it.isNotBlank() }
                ?: snapshot.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: resolvedName
            val existingPhone = snapshot.getString("phone").orEmpty()
            val resolvedPhone = existingPhone.ifBlank { user.phoneNumber.orEmpty() }
            val updatePayload = mutableMapOf<String, Any>(
                "uid" to user.uid,
                "email" to (user.email ?: snapshot.getString("email") ?: ""),
                "displayName" to updateName,
                "phone" to resolvedPhone,
                "authProvider" to authProvider,
                "updatedAt" to now
            )
            if (photoUrl.isNotBlank()) {
                updatePayload["photoUrl"] = photoUrl
            }
            userDoc.set(updatePayload, SetOptions.merge()).awaitResult()
            val explicitIncomplete = snapshot.getBoolean("profileCompleted") == false
            authReportedNewUser || explicitIncomplete
        }
    }

    private fun registerFcmTokenAsync(uid: String) {
        scope.launch {
            runCatching {
                val token = withTimeoutOrNull(8000L) {
                    FirebaseMessaging.getInstance().token.awaitResult()
                }
                if (!token.isNullOrBlank() && auth.currentUser?.uid == uid) {
                    db.collection("deviceTokens").document("${uid}_android").set(
                        mapOf(
                            "userId" to uid,
                            "role" to "CUSTOMER",
                            "platform" to "android",
                            "token" to token,
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).awaitResult()
                }
            }
        }
    }

    private suspend fun syncUserProfile(
        user: FirebaseUser,
        explicitName: String? = null,
        authReportedNewUser: Boolean = false
    ) {
        profileSyncMutex.withLock {
            val isNewUser: Boolean = try {
                val synced = withTimeoutOrNull(PROFILE_SYNC_TIMEOUT_MS) {
                    upsertFirestoreUserDoc(
                        user = user,
                        explicitName = explicitName,
                        authReportedNewUser = authReportedNewUser,
                        forceTokenRefresh = false
                    )
                }
                if (synced != null) {
                    synced
                } else {
                    Log.w("FirebaseAuthRepo", "Profile sync timed out; continuing login and syncing in background")
                    scope.launch {
                        runCatching {
                            upsertFirestoreUserDoc(
                                user = user,
                                explicitName = explicitName,
                                authReportedNewUser = authReportedNewUser,
                                forceTokenRefresh = true
                            )
                        }.onFailure { err ->
                            handleFirestoreError(
                                (err as? Exception) ?: IllegalStateException(err.message, err),
                                OperationType.WRITE,
                                "users/${user.uid}"
                            )
                        }
                    }
                    authReportedNewUser
                }
            } catch (e: Exception) {
                Log.w("FirebaseAuthRepo", "Initial users/${user.uid} sync failed (${e.message}); retrying with fresh token")
                val retryResult = runCatching {
                    withTimeoutOrNull(PROFILE_SYNC_TIMEOUT_MS) {
                        upsertFirestoreUserDoc(
                            user = user,
                            explicitName = explicitName,
                            authReportedNewUser = authReportedNewUser,
                            forceTokenRefresh = true
                        )
                    }
                }
                retryResult.getOrNull() ?: run {
                    val failure = (retryResult.exceptionOrNull() as? Exception) ?: e
                    handleFirestoreError(failure, OperationType.WRITE, "users/${user.uid}")
                    authReportedNewUser
                }
            }

            // Always set new-user routing flag BEFORE publishing isLoggedIn = true
            _isNewUserSession.value = isNewUser
            _currentUserPhone.value = user.phoneNumber ?: user.email
            _isLoggedIn.value = true
            _authState.value = AuthState.Authenticated(
                phone = user.phoneNumber ?: user.email ?: user.uid,
                isDemoAccount = false
            )

            // Push token registration runs completely out-of-band so login never blocks on FCM
            registerFcmTokenAsync(user.uid)
        }
    }

    private suspend fun completeAuthResult(
        authResult: AuthResult,
        explicitName: String? = null
    ): Result<Unit> {
        val user = authResult.user
        return if (user != null) {
            activeUid = user.uid
            val isNewFromAuth = authResult.additionalUserInfo?.isNewUser == true
            syncUserProfile(
                user = user,
                explicitName = explicitName,
                authReportedNewUser = isNewFromAuth
            )
            Result.success(Unit)
        } else {
            val err = "Authentication completed but user is null."
            _authState.value = AuthState.Error(err)
            Result.failure(IllegalStateException(err))
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        _authState.value = AuthState.Loading
        isExplicitAuthInProgress.set(true)
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).awaitResult()
            completeAuthResult(authResult)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Google Sign-In failed on project ${currentProjectId()}", e)
            val msg = formatAuthError(e, "Google sign-in failed")
            _authState.value = AuthState.Error(msg)
            Result.failure(IllegalStateException(msg, e))
        } finally {
            isExplicitAuthInProgress.set(false)
        }
    }

    override suspend fun signInWithGoogleProvider(activity: Activity): Result<Unit> {
        _authState.value = AuthState.Loading
        isExplicitAuthInProgress.set(true)
        return try {
            val pending = auth.pendingAuthResult
            val authResult = if (pending != null) {
                pending.awaitResult()
            } else {
                val provider = OAuthProvider.newBuilder("google.com")
                    .addCustomParameter("prompt", "select_account")
                    .build()
                auth.startActivityForSignInWithProvider(activity, provider).awaitResult()
            }
            completeAuthResult(authResult)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Google OAuth provider sign-in failed on project ${currentProjectId()}", e)
            val msg = formatAuthError(e, "Google sign-in failed")
            _authState.value = AuthState.Error(msg)
            Result.failure(IllegalStateException(msg, e))
        } finally {
            isExplicitAuthInProgress.set(false)
        }
    }

    override suspend fun checkPendingGoogleSignIn(): Result<Boolean> {
        val pending = auth.pendingAuthResult ?: return Result.success(false)
        _authState.value = AuthState.Loading
        isExplicitAuthInProgress.set(true)
        return try {
            val authResult = pending.awaitResult()
            completeAuthResult(authResult).map { true }
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Pending Google Sign-In failed", e)
            val msg = formatAuthError(e, "Google sign-in failed")
            _authState.value = AuthState.Error(msg)
            Result.failure(IllegalStateException(msg, e))
        } finally {
            isExplicitAuthInProgress.set(false)
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || password.length < 6) {
            val err = "Please enter a valid email and password (minimum 6 characters)."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalArgumentException(err))
        }
        _authState.value = AuthState.Loading
        isExplicitAuthInProgress.set(true)
        return try {
            val authResult = auth.signInWithEmailAndPassword(cleanEmail, password).awaitResult()
            completeAuthResult(authResult)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Email Sign-In failed on project ${currentProjectId()}", e)
            val msg = formatAuthError(e, "Email sign-in failed")
            _authState.value = AuthState.Error(msg)
            Result.failure(IllegalStateException(msg, e))
        } finally {
            isExplicitAuthInProgress.set(false)
        }
    }

    override suspend fun registerWithEmail(name: String, email: String, password: String): Result<Unit> {
        val cleanEmail = email.trim()
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanEmail.isBlank() || !cleanEmail.contains("@") || password.length < 6) {
            val err = "Full name, valid email, and password (6+ chars) are required."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalArgumentException(err))
        }
        _authState.value = AuthState.Loading
        isExplicitAuthInProgress.set(true)
        return try {
            val authResult = auth.createUserWithEmailAndPassword(cleanEmail, password).awaitResult()
            val user = authResult.user ?: error("Registration succeeded but user is null.")
            runCatching {
                withTimeoutOrNull(3000L) {
                    user.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
                    ).awaitResult()
                }
            }
            completeAuthResult(authResult, explicitName = cleanName)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Email Registration failed on project ${currentProjectId()}", e)
            val msg = formatAuthError(e, "Registration failed")
            _authState.value = AuthState.Error(msg)
            Result.failure(IllegalStateException(msg, e))
        } finally {
            isExplicitAuthInProgress.set(false)
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
        val uidToClean = auth.currentUser?.uid
        if (uidToClean != null) {
            // Best-effort cleanup of this device's push token while still authorized as owner
            runCatching {
                withTimeoutOrNull(2500L) {
                    db.collection("deviceTokens").document("${uidToClean}_android").delete().awaitResult()
                }
            }
        }
        activeUid = null
        onSessionCleared()
        auth.signOut()
        _isNewUserSession.value = false
        _isLoggedIn.value = false
        _currentUserPhone.value = null
        _authState.value = AuthState.Unauthenticated
    }
}
