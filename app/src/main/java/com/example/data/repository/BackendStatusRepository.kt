package com.example.data.repository

import com.example.data.model.BackendIntegrationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface BackendStatusRepository {
    val status: StateFlow<BackendIntegrationStatus>
    fun refreshStatus()
    fun clearSession() {}
}

class DefaultBackendStatusRepository : BackendStatusRepository {

    private val _status = MutableStateFlow(
        BackendIntegrationStatus(
            firebaseAuthAvailable = false,
            firestoreAvailable = false,
            fcmAvailable = false,
            backendApiAvailable = false,
            activeEnvironment = "Sandbox UI Prototype (Mock Dispatcher)",
            statusSummary = "FixMechanic backend APIs and Firebase keys are not provisioned yet. The app is running with repository abstractions so you can preview all 17 screens and test business workflows safely."
        )
    )
    override val status: StateFlow<BackendIntegrationStatus> = _status.asStateFlow()

    override fun refreshStatus() {
        // In this prototype, explicitly reports disconnected states as required by user prompt
        _status.value = _status.value.copy(
            activeEnvironment = "Client Prototype (Isolated)",
            statusSummary = "Firebase Auth: Ready for config\nFirestore: Schema ready\nFCM: Pending server key\nBackend API: Offline"
        )
    }
}
