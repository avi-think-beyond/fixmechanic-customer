package com.example.data.repository

import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.Mechanic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface EmergencyRepository {
    val activeRequest: StateFlow<EmergencyRequest?>
    val requestHistory: StateFlow<List<EmergencyRequest>>
    val nearbyMechanics: StateFlow<List<Mechanic>>
    val isDemoMode: Boolean

    suspend fun submitEmergencyRequest(request: EmergencyRequest): Result<EmergencyRequest>
    suspend fun cancelActiveRequest(): Result<Unit>
    suspend fun retrySearch(): Result<Unit>
    suspend fun triggerOfferExpiryCheck(): Result<Unit>
    suspend fun completeJob(rating: Float, comment: String): Result<Unit>
}

class PrototypeEmergencyRepository : EmergencyRepository {
    override val isDemoMode: Boolean = true
    private val _activeRequest = MutableStateFlow<EmergencyRequest?>(null)
    override val activeRequest: StateFlow<EmergencyRequest?> = _activeRequest.asStateFlow()
    private val _requestHistory = MutableStateFlow<List<EmergencyRequest>>(emptyList())
    override val requestHistory: StateFlow<List<EmergencyRequest>> = _requestHistory.asStateFlow()
    private val _nearbyMechanics = MutableStateFlow<List<Mechanic>>(emptyList())
    override val nearbyMechanics: StateFlow<List<Mechanic>> = _nearbyMechanics.asStateFlow()

    override suspend fun submitEmergencyRequest(request: EmergencyRequest): Result<EmergencyRequest> {
        return Result.failure(IllegalStateException("Prototype mode disabled; use FirestoreEmergencyRepository."))
    }

    override suspend fun cancelActiveRequest(): Result<Unit> =
        Result.failure(IllegalStateException("Prototype mode disabled."))

    override suspend fun retrySearch(): Result<Unit> =
        Result.failure(IllegalStateException("Prototype mode disabled."))

    override suspend fun triggerOfferExpiryCheck(): Result<Unit> = Result.success(Unit)

    override suspend fun completeJob(rating: Float, comment: String): Result<Unit> =
        Result.failure(IllegalStateException("Prototype mode disabled."))
}
