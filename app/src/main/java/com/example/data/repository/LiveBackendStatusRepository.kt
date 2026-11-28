package com.example.data.repository

import com.example.data.BackendApiClient
import com.example.data.FirestoreProvider
import com.example.data.FirestoreProvider.awaitResult
import com.example.data.model.BackendIntegrationStatus
import com.example.di.ServiceLocator
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

    private fun resolveProjectId(): String =
        runCatching { db.app.options.projectId }.getOrNull().orEmpty().ifBlank { "fixmechanic-firebase" }

    private fun resolveDatabaseId(): String =
        ServiceLocator.getAppContext()?.let { FirestoreProvider.getDatabaseId(it) }
            ?: "ai-studio-android-e98a79eb-e2fe-4b16-a049-c442e6f88e9e"

    private val _status = MutableStateFlow(
        BackendIntegrationStatus(
            firebaseAuthAvailable = true,
            firestoreAvailable = true,
            fcmAvailable = true,
            backendApiAvailable = false,
            isDemoMode = false,
            activeEnvironment = "Firebase (${resolveProjectId()} / ${resolveDatabaseId()})",
            statusSummary = "Checking Spark dispatch backend & Firestore connectivity..."
        )
    )
    override val status: StateFlow<BackendIntegrationStatus> = _status.asStateFlow()

    init {
        auth.addAuthStateListener {
            refreshStatus()
        }
        refreshStatus()
    }

    override fun clearSession() {
        _status.value = _status.value.copy(
            statusSummary = "Signed out. Ready for Email/Password or Google Sign-In on ${resolveProjectId()}."
        )
    }

    override fun refreshStatus() {
        scope.launch {
            val user = auth.currentUser
            val projectId = resolveProjectId()
            val dbId = resolveDatabaseId()
            val firestoreOk = if (user != null) {
                runCatching {
                    db.collection("applicationConfig").document("current").get().awaitResult()
                    true
                }.getOrDefault(false)
            } else {
                false
            }
            val remoteServerConfigured = backendApiClient.isConfigured()
            val backendOk = if (remoteServerConfigured) backendApiClient.checkHealth() else firestoreOk

            _status.value = _status.value.copy(
                firebaseAuthAvailable = true,
                firestoreAvailable = firestoreOk,
                fcmAvailable = true,
                backendApiAvailable = backendOk,
                isDemoMode = false,
                activeEnvironment = "Firebase ($projectId / $dbId)",
                statusSummary = buildString {
                    appendLine("Auth: ${if (user != null) "Signed in (${user.email ?: user.uid})" else "Ready (Email & Google)"}")
                    appendLine("Firestore ($dbId): ${if (firestoreOk) "Reachable" else if (user == null) "Sign in to verify" else "Offline / Rules check needed"}")
                    append(
                        if (!remoteServerConfigured) {
                            "Dispatch Mode: Direct Cloud Firestore (Real-time)"
                        } else if (backendOk) {
                            "Dispatch Server: Healthy"
                        } else {
                            "Dispatch Server: Unreachable (falling back to Direct Firestore)"
                        }
                    )
                }
            )
        }
    }
}
