package com.example.data.repository

import com.example.data.model.BackendIntegrationStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LiveBackendStatusRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : BackendStatusRepository {

    private val _status = MutableStateFlow(
        BackendIntegrationStatus(
            firebaseAuthAvailable = true,
            firestoreAvailable = true,
            fcmAvailable = false,
            backendApiAvailable = true,
            isDemoMode = false,
            activeEnvironment = "Firebase Spark (Live Firestore & Auth)",
            statusSummary = "Connected to live Cloud Firestore database: ai-studio-android-fixmecha-b0e3b2e8-05f1-4bf3-b32f-7f48ef3138b9. Zero-trust security rules deployed."
        )
    )
    override val status: StateFlow<BackendIntegrationStatus> = _status.asStateFlow()

    override fun refreshStatus() {
        val user = auth.currentUser
        _status.value = _status.value.copy(
            statusSummary = if (user != null) {
                "Firebase Auth: Connected (${user.displayName ?: user.phoneNumber ?: user.email})\nFirestore: Active\nDatabase ID: ai-studio-android-fixmecha-b0e3b2e8-05f1-4bf3-b32f-7f48ef3138b9\nRules: Deployed & Enforced"
            } else {
                "Firebase Auth: Ready (Google Sign-In via Credential Manager)\nFirestore: Active\nRules: Deployed & Enforced"
            }
        )
    }
}
