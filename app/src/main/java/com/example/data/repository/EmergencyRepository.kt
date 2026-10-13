package com.example.data.repository

import com.example.data.datasource.ServiceCatalog
import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.LocationData
import com.example.data.model.Mechanic
import com.example.data.model.StatusHistoryEntry
import com.example.data.model.VehicleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface EmergencyRepository {
    val activeRequest: StateFlow<EmergencyRequest?>
    val requestHistory: StateFlow<List<EmergencyRequest>>
    val isDemoMode: Boolean

    suspend fun submitEmergencyRequest(request: EmergencyRequest): Result<EmergencyRequest>
    suspend fun cancelActiveRequest(): Result<Unit>
    suspend fun retrySearch(): Result<Unit>
    suspend fun transitionJobStatus(nextStatus: JobStatus)
    suspend fun completeJob(rating: Float, comment: String): Result<Unit>
}

/**
 * Implementation of EmergencyRepository.
 * Manages roadside assistance requests, authoritative status updates, and history.
 * Operates in Demo Mode prior to Firebase Firestore / Cloud Functions configuration.
 */
class PrototypeEmergencyRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : EmergencyRepository {

    override val isDemoMode: Boolean = true

    private val _activeRequest = MutableStateFlow<EmergencyRequest?>(null)
    override val activeRequest: StateFlow<EmergencyRequest?> = _activeRequest.asStateFlow()

    private var dispatchJob: Job? = null

    private val _requestHistory = MutableStateFlow<List<EmergencyRequest>>(
        // Labeled sample records for history inspection
        listOf(
            EmergencyRequest(
                id = "FM-REQ-20261009-8412",
                vehicleType = VehicleType.CAR,
                serviceItem = ServiceCatalog.carServices[1], // Battery jump start
                vehicleMakeModel = "Hyundai Creta SX (Petrol)",
                registrationNumber = "DL 03 CB 9142",
                issueDescription = "Battery drained overnight after hazard lights left on.",
                location = LocationData(
                    latitude = 28.5355,
                    longitude = 77.3910,
                    address = "Sector 18, Noida, Uttar Pradesh",
                    landmark = "Opposite Atta Market Entrance",
                    city = "Noida",
                    isGpsAccurate = true,
                    isObtained = true
                ),
                status = JobStatus.COMPLETED,
                assignedMechanic = Mechanic(
                    id = "M-902",
                    name = "Virender Singh",
                    rating = 4.9f,
                    rescuesCompleted = 412,
                    vehicleInfo = "FixMechanic Mobile Service Van (DL 1L 9901)",
                    phoneMasked = "+91 98XXX-XX902",
                    isVerified = true
                ),
                createdAt = System.currentTimeMillis() - 86400000L * 4,
                baseInspectionFee = 399,
                finalPartsFee = 0,
                totalPaid = 399,
                ratingGiven = 5.0f,
                feedbackComment = "Mechanic arrived in 15 mins with industrial jumper cables. Excellent service!",
                statusHistory = listOf(
                    StatusHistoryEntry(JobStatus.REQUESTED, System.currentTimeMillis() - 86400000L * 4),
                    StatusHistoryEntry(JobStatus.SEARCHING, System.currentTimeMillis() - 86400000L * 4 + 5000),
                    StatusHistoryEntry(JobStatus.ASSIGNED, System.currentTimeMillis() - 86400000L * 4 + 20000),
                    StatusHistoryEntry(JobStatus.ACCEPTED, System.currentTimeMillis() - 86400000L * 4 + 35000),
                    StatusHistoryEntry(JobStatus.EN_ROUTE, System.currentTimeMillis() - 86400000L * 4 + 60000),
                    StatusHistoryEntry(JobStatus.ARRIVED, System.currentTimeMillis() - 86400000L * 4 + 900000),
                    StatusHistoryEntry(JobStatus.IN_PROGRESS, System.currentTimeMillis() - 86400000L * 4 + 960000),
                    StatusHistoryEntry(JobStatus.COMPLETED, System.currentTimeMillis() - 86400000L * 4 + 1800000)
                )
            ),
            EmergencyRequest(
                id = "FM-REQ-20261001-5129",
                vehicleType = VehicleType.BIKE,
                serviceItem = ServiceCatalog.bikeServices[0], // Flat tyre
                vehicleMakeModel = "Royal Enfield Classic 350",
                registrationNumber = "UP 16 AX 5431",
                issueDescription = "Rear tyre punctured by nail on highway.",
                location = LocationData(
                    latitude = 28.5800,
                    longitude = 77.2600,
                    address = "DND Flyway, Toll Plaza, Delhi-NCR",
                    landmark = "Near Lane 4 Toll Booth",
                    city = "Noida",
                    isGpsAccurate = true,
                    isObtained = true
                ),
                status = JobStatus.COMPLETED,
                assignedMechanic = Mechanic(
                    id = "M-415",
                    name = "Amit Sharma",
                    rating = 4.8f,
                    rescuesCompleted = 285,
                    vehicleInfo = "FixMechanic Quick Rescue Bike",
                    phoneMasked = "+91 98XXX-XX415",
                    isVerified = true
                ),
                createdAt = System.currentTimeMillis() - 86400000L * 12,
                baseInspectionFee = 199,
                finalPartsFee = 50,
                totalPaid = 249,
                ratingGiven = 4.5f,
                feedbackComment = "Very prompt puncture vulcanization on the roadside.",
                statusHistory = listOf(
                    StatusHistoryEntry(JobStatus.REQUESTED, System.currentTimeMillis() - 86400000L * 12),
                    StatusHistoryEntry(JobStatus.COMPLETED, System.currentTimeMillis() - 86400000L * 12 + 1500000)
                )
            )
        )
    )
    override val requestHistory: StateFlow<List<EmergencyRequest>> = _requestHistory.asStateFlow()

    override suspend fun submitEmergencyRequest(request: EmergencyRequest): Result<EmergencyRequest> {
        delay(600) // Simulated network creation time

        // Generate a production-formatted unique tracking ID
        val datePrefix = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val randomSuffix = (1000..9999).random()
        val generatedId = "FM-REQ-$datePrefix-$randomSuffix"

        val initialRequest = request.copy(
            id = generatedId,
            status = JobStatus.SEARCHING,
            statusHistory = listOf(
                StatusHistoryEntry(JobStatus.REQUESTED),
                StatusHistoryEntry(JobStatus.SEARCHING, note = "Broadcasting to nearest roadside response units")
            )
        )

        _activeRequest.value = initialRequest

        // Launch simulated dispatch lifecycle
        startDispatchSimulation(initialRequest)

        return Result.success(initialRequest)
    }

    private fun startDispatchSimulation(request: EmergencyRequest) {
        dispatchJob?.cancel()
        dispatchJob = scope.launch {
            // Step 1: Searching window
            delay(4000)

            val current = _activeRequest.value
            if (current != null && current.status == JobStatus.SEARCHING) {
                val assignedMechanic = Mechanic(
                    id = "MECH-${(100..999).random()}",
                    name = "Rajesh Kumar (Senior Technician)",
                    rating = 4.85f,
                    rescuesCompleted = 348,
                    vehicleInfo = if (request.vehicleType == VehicleType.CAR)
                        "FixMechanic Service Van (DL 01 AA 4088)"
                    else
                        "FixMechanic Response Bike (DL 05 RT 1244)",
                    phoneMasked = "+91 98XXX-XX552",
                    isVerified = true,
                    currentDistanceKm = 2.1f,
                    estimatedArrivalMinutes = 14
                )

                // Advance to ASSIGNED
                val updatedAssigned = current.copy(
                    status = JobStatus.ASSIGNED,
                    assignedMechanic = assignedMechanic,
                    statusHistory = current.statusHistory + StatusHistoryEntry(
                        JobStatus.ASSIGNED,
                        note = "Dispatched to ${assignedMechanic.name}"
                    )
                )
                _activeRequest.value = updatedAssigned

                delay(2000)
                // Advance to ACCEPTED
                if (_activeRequest.value?.status == JobStatus.ASSIGNED) {
                    val updatedAccepted = updatedAssigned.copy(
                        status = JobStatus.ACCEPTED,
                        statusHistory = updatedAssigned.statusHistory + StatusHistoryEntry(
                            JobStatus.ACCEPTED,
                            note = "Mechanic confirmed dispatch"
                        )
                    )
                    _activeRequest.value = updatedAccepted

                    delay(3000)
                    // Advance to EN_ROUTE
                    if (_activeRequest.value?.status == JobStatus.ACCEPTED) {
                        _activeRequest.value = updatedAccepted.copy(
                            status = JobStatus.EN_ROUTE,
                            statusHistory = updatedAccepted.statusHistory + StatusHistoryEntry(
                                JobStatus.EN_ROUTE,
                                note = "Mechanic moving to breakdown location"
                            )
                        )
                    }
                }
            }
        }
    }

    override suspend fun retrySearch(): Result<Unit> {
        val current = _activeRequest.value ?: return Result.failure(IllegalStateException("No active request to retry."))
        val retried = current.copy(
            status = JobStatus.SEARCHING,
            assignedMechanic = null,
            statusHistory = current.statusHistory + StatusHistoryEntry(
                JobStatus.SEARCHING,
                note = "Restarting search with expanded radius"
            )
        )
        _activeRequest.value = retried
        startDispatchSimulation(retried)
        return Result.success(Unit)
    }

    override suspend fun cancelActiveRequest(): Result<Unit> {
        dispatchJob?.cancel()
        val current = _activeRequest.value ?: return Result.failure(IllegalStateException("No active request to cancel."))
        val cancelledItem = current.copy(
            status = JobStatus.CANCELLED,
            statusHistory = current.statusHistory + StatusHistoryEntry(JobStatus.CANCELLED, note = "Cancelled by customer")
        )
        _requestHistory.value = listOf(cancelledItem) + _requestHistory.value
        _activeRequest.value = null
        return Result.success(Unit)
    }

    override suspend fun transitionJobStatus(nextStatus: JobStatus) {
        val current = _activeRequest.value ?: return
        val updatedHistory = current.statusHistory + StatusHistoryEntry(nextStatus)

        if (nextStatus == JobStatus.COMPLETED) {
            val completed = current.copy(
                status = JobStatus.COMPLETED,
                totalPaid = current.baseInspectionFee,
                statusHistory = updatedHistory
            )
            _activeRequest.value = completed
        } else {
            _activeRequest.value = current.copy(
                status = nextStatus,
                statusHistory = updatedHistory
            )
        }
    }

    override suspend fun completeJob(rating: Float, comment: String): Result<Unit> {
        dispatchJob?.cancel()
        val current = _activeRequest.value ?: return Result.failure(IllegalStateException("No active job to complete."))
        val completed = current.copy(
            status = JobStatus.COMPLETED,
            ratingGiven = rating,
            feedbackComment = comment,
            totalPaid = current.baseInspectionFee,
            statusHistory = current.statusHistory + StatusHistoryEntry(
                JobStatus.COMPLETED,
                note = "Customer completed review ($rating stars)"
            )
        )
        _requestHistory.value = listOf(completed) + _requestHistory.value
        _activeRequest.value = null
        return Result.success(Unit)
    }
}
