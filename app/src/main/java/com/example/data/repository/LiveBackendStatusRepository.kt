package com.example.data.repository

import com.example.data.BackendApiClient
import com.example.data.FirestoreProvider.awaitResult
import com.example.data.model.BackendIntegrationStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveBackendStatusRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val backendApiClient: BackendApiClient = BackendApiClient(auth)
) : BackendStatusRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _status = MutableStateFlow(
        BackendIntegrationStatus(
            firebaseAuthAvailable = true,
            firestoreAvailable = true,
            fcmAvailable = true,
            backendApiAvailable = false,
            isDemoMode = false,
            activeEnvironment = "Firebase Spark (fixmechanic-firebase / (default))",
            statusSummary = "Checking Spark dispatch backend & Firestore connectivity..."
        )
    )
    override val status: StateFlow<BackendIntegrationStatus> = _status.asStateFlow()

    init {
        refreshStatus()
    }

    override fun refreshStatus() {
        scope.launch {
            val user = auth.currentUser
            val firestoreOk = runCatching {
                db.collection("applicationConfig").document("current").get().awaitResult()
                true
            }.getOrDefault(false)
            val backendOk = backendApiClient.checkHealth()

            _status.value = _status.value.copy(
                firebaseAuthAvailable = true,
                firestoreAvailable = firestoreOk,
                fcmAvailable = true,
                backendApiAvailable = backendOk,
                isDemoMode = false,
                activeEnvironment = "Firebase Spark (fixmechanic-firebase / (default))",
                statusSummary = buildString {
                    appendLine("Auth: ${if (user != null) "Signed in (${user.email ?: user.uid})" else "Ready (Email & Google)"}")
                    appendLine("Firestore (default): ${if (firestoreOk) "Reachable" else "Offline / Auth required"}")
                    append("Spark Dispatch Server: ${if (backendOk) "Healthy" else "Unreachable (start local server on 5055)"}")
                }
            )
        }
    }
}
