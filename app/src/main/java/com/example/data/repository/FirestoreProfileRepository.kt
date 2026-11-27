package com.example.data.repository

import android.content.Context
import com.example.data.FirestoreProvider
import com.example.data.FirestoreProvider.awaitResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                profileListener?.remove()
                profileListener = db.collection("users").document(user.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            handleFirestoreError(error, OperationType.GET, "users/${user.uid}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            _profile.value = UserProfileData(
                                fullName = snapshot.getString("displayName") ?: user.displayName ?: "FixMechanic Customer",
                                phoneNumber = snapshot.getString("phone") ?: user.phoneNumber ?: "",
                                email = snapshot.getString("email") ?: user.email ?: "",
                                emergencyContactName = snapshot.getString("emergencyContactName") ?: "Family Contact",
                                emergencyContactPhone = snapshot.getString("emergencyContactPhone") ?: "",
                                primaryVehicle = snapshot.getString("primaryVehicle") ?: "Vehicle",
                                isNumberMaskingEnabled = snapshot.getBoolean("isNumberMaskingEnabled") ?: true,
                                isGpsSharingEnabled = snapshot.getBoolean("isGpsSharingEnabled") ?: true,
                                preferredLanguage = snapshot.getString("preferredLanguage") ?: "en"
                            )
                        }
                    }
            } else {
                profileListener?.remove()
                profileListener = null
                _profile.value = UserProfileData()
            }
        }
    }

    override fun updateProfile(profile: UserProfileData) {
        val uid = auth.currentUser?.uid ?: return
        _profile.value = profile
        scope.launch {
            try {
                db.collection("users").document(uid).set(
                    mapOf(
                        "displayName" to profile.fullName,
                        "phone" to profile.phoneNumber,
                        "email" to profile.email,
                        "emergencyContactName" to profile.emergencyContactName,
                        "emergencyContactPhone" to profile.emergencyContactPhone,
                        "primaryVehicle" to profile.primaryVehicle,
                        "isNumberMaskingEnabled" to profile.isNumberMaskingEnabled,
                        "isGpsSharingEnabled" to profile.isGpsSharingEnabled,
                        "preferredLanguage" to profile.preferredLanguage,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).awaitResult()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            }
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
