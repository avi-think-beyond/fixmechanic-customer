package com.example.data.repository

import android.content.Context
import com.example.data.FirestoreProvider
import com.example.data.FirestoreProvider.awaitResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class FirestoreProfileRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ProfileRepository {

    companion object {
        private fun createFirestore(context: Context): FirebaseFirestore =
            FirestoreProvider.createFirestore(context.applicationContext)

        private fun createAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    }

    constructor(context: Context) : this(
        db = createFirestore(context),
        auth = createAuth()
    )

    private val _profile = MutableStateFlow(UserProfileData())
    override val profile: StateFlow<UserProfileData> = _profile.asStateFlow()

    private var profileListener: ListenerRegistration? = null
    @Volatile
    private var activeUserId: String? = null
    private var sessionGeneration: Long = 0L
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                if (activeUserId == user.uid && profileListener != null) return@addAuthStateListener
                clearSession()
                activeUserId = user.uid
                // Seed immediately from FirebaseAuth so Google/Email account details appear right away
                _profile.value = UserProfileData(
                    fullName = user.displayName?.takeIf { it.isNotBlank() }
                        ?: user.email?.substringBefore("@")
                        ?: "Customer",
                    phoneNumber = user.phoneNumber.orEmpty(),
                    email = user.email.orEmpty(),
                    emergencyContactName = "",
                    emergencyContactPhone = "",
                    primaryVehicle = ""
                )
                val generation = sessionGeneration
                profileListener = db.collection("users").document(user.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (generation != sessionGeneration || activeUserId != user.uid || auth.currentUser?.uid != user.uid) {
                            return@addSnapshotListener
                        }
                        if (error != null) {
                            handleFirestoreError(error, OperationType.GET, "users/${user.uid}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            _profile.value = UserProfileData(
                                fullName = snapshot.getString("displayName")?.takeIf { it.isNotBlank() }
                                    ?: user.displayName?.takeIf { it.isNotBlank() }
                                    ?: user.email?.substringBefore("@")
                                    ?: "FixMechanic Customer",
                                phoneNumber = snapshot.getString("phone") ?: user.phoneNumber ?: "",
                                email = snapshot.getString("email") ?: user.email ?: "",
                                emergencyContactName = snapshot.getString("emergencyContactName") ?: "",
                                emergencyContactPhone = snapshot.getString("emergencyContactPhone") ?: "",
                                primaryVehicle = snapshot.getString("primaryVehicle") ?: "",
                                isNumberMaskingEnabled = snapshot.getBoolean("isNumberMaskingEnabled") ?: true,
                                isGpsSharingEnabled = snapshot.getBoolean("isGpsSharingEnabled") ?: true,
                                preferredLanguage = snapshot.getString("preferredLanguage") ?: "en"
                            )
                        }
                    }
            } else {
                clearSession()
            }
        }
    }

    override fun clearSession() {
        sessionGeneration++
        profileListener?.remove()
        profileListener = null
        activeUserId = null
        _profile.value = UserProfileData()
    }

    override suspend fun saveProfile(profile: UserProfileData): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Please sign in to save your profile."))
        val uid = user.uid
        val sanitized = profile.copy(
            fullName = profile.fullName.trim().ifBlank { user.displayName ?: "FixMechanic Customer" },
            phoneNumber = profile.phoneNumber.trim(),
            email = profile.email.trim().ifBlank { user.email.orEmpty() },
            emergencyContactName = profile.emergencyContactName.trim(),
            emergencyContactPhone = profile.emergencyContactPhone.trim(),
            primaryVehicle = profile.primaryVehicle.trim()
        )
        _profile.value = sanitized

        return try {
            val now = System.currentTimeMillis()
            val userDoc = db.collection("users").document(uid)
            val existing = withTimeoutOrNull(6000L) { userDoc.get().awaitResult() }
            if (existing == null || !existing.exists()) {
                // Include required CUSTOMER role on initial document creation per firestore.rules
                val fullDoc = hashMapOf(
                    "uid" to uid,
                    "role" to "CUSTOMER",
                    "disabled" to false,
                    "displayName" to sanitized.fullName,
                    "phone" to sanitized.phoneNumber,
                    "email" to sanitized.email,
                    "emergencyContactName" to sanitized.emergencyContactName,
                    "emergencyContactPhone" to sanitized.emergencyContactPhone,
                    "primaryVehicle" to sanitized.primaryVehicle,
                    "isNumberMaskingEnabled" to sanitized.isNumberMaskingEnabled,
                    "isGpsSharingEnabled" to sanitized.isGpsSharingEnabled,
                    "preferredLanguage" to sanitized.preferredLanguage,
                    "profileCompleted" to true,
                    "createdAt" to now,
                    "updatedAt" to now
                )
                withTimeoutOrNull(6000L) { userDoc.set(fullDoc).awaitResult() }
                    ?: error("Timed out saving profile. Please check your internet connection.")
            } else {
                // Never modify role/disabled on update per firestore.rules
                val updateMap = mapOf(
                    "displayName" to sanitized.fullName,
                    "phone" to sanitized.phoneNumber,
                    "email" to sanitized.email,
                    "emergencyContactName" to sanitized.emergencyContactName,
                    "emergencyContactPhone" to sanitized.emergencyContactPhone,
                    "primaryVehicle" to sanitized.primaryVehicle,
                    "isNumberMaskingEnabled" to sanitized.isNumberMaskingEnabled,
                    "isGpsSharingEnabled" to sanitized.isGpsSharingEnabled,
                    "preferredLanguage" to sanitized.preferredLanguage,
                    "profileCompleted" to true,
                    "updatedAt" to now
                )
                withTimeoutOrNull(6000L) { userDoc.set(updateMap, SetOptions.merge()).awaitResult() }
                    ?: error("Timed out saving profile. Please check your internet connection.")
            }

            if (sanitized.fullName.isNotBlank() && sanitized.fullName != user.displayName) {
                runCatching {
                    withTimeoutOrNull(3000L) {
                        user.updateProfile(
                            UserProfileChangeRequest.Builder()
                                .setDisplayName(sanitized.fullName)
                                .build()
                        ).awaitResult()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    override fun updateProfile(profile: UserProfileData) {
        _profile.value = profile
        scope.launch {
            saveProfile(profile)
        }
    }

    override fun toggleNumberMasking(enabled: Boolean) {
        updateProfile(_profile.value.copy(isNumberMaskingEnabled = enabled))
    }

    override fun toggleGpsSharing(enabled: Boolean) {
        updateProfile(_profile.value.copy(isGpsSharingEnabled = enabled))
    }

    override fun setLanguage(langCode: String) {
        updateProfile(_profile.value.copy(preferredLanguage = langCode))
    }
}
