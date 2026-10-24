package com.example.data.repository

import android.content.Context
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreProfileRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ProfileRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        FirebaseAuth.getInstance()
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
                                phoneNumber = snapshot.getString("phone") ?: user.phoneNumber ?: "+91 98XXX-XXXXX",
                                email = snapshot.getString("email") ?: user.email ?: "",
                                emergencyContactName = snapshot.getString("emergencyContactName") ?: "Family Contact",
                                emergencyContactPhone = snapshot.getString("emergencyContactPhone") ?: "+91 98111 22334",
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
                db.collection("users").document(uid).update(
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
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            }
        }
    }

    override fun toggleNumberMasking(enabled: Boolean) {
        val updated = _profile.value.copy(isNumberMaskingEnabled = enabled)
        updateProfile(updated)
    }

    override fun toggleGpsSharing(enabled: Boolean) {
        val updated = _profile.value.copy(isGpsSharingEnabled = enabled)
        updateProfile(updated)
    }

    override fun setLanguage(langCode: String) {
        val updated = _profile.value.copy(preferredLanguage = langCode)
        updateProfile(updated)
    }
}
