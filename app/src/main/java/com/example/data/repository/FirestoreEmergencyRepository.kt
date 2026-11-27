package com.example.data.repository

import android.content.Context
import com.example.data.BackendApiClient
import com.example.data.FirestoreProvider
import com.example.data.FirestoreProvider.awaitResult
import com.example.data.datasource.ServiceCatalog
import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.LocationData
import com.example.data.model.Mechanic
import com.example.data.model.StatusHistoryEntry
import com.example.data.model.VehicleType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirestoreEmergencyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val backendApiClient: BackendApiClient = BackendApiClient(auth)
) : EmergencyRepository {

    companion object {
        private fun createFirestore(context: Context): FirebaseFirestore =
            FirestoreProvider.createFirestore(context.applicationContext)

        private fun createAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    }

    constructor(context: Context) : this(
        db = createFirestore(context),
        auth = createAuth(),
        backendApiClient = BackendApiClient(createAuth())
    )

    override val isDemoMode: Boolean = false

    private val _activeRequest = MutableStateFlow<EmergencyRequest?>(null)
    override val activeRequest: StateFlow<EmergencyRequest?> = _activeRequest.asStateFlow()

    private val _requestHistory = MutableStateFlow<List<EmergencyRequest>>(emptyList())
    override val requestHistory: StateFlow<List<EmergencyRequest>> = _requestHistory.asStateFlow()

    private val _nearbyMechanics = MutableStateFlow<List<Mechanic>>(emptyList())
    override val nearbyMechanics: StateFlow<List<Mechanic>> = _nearbyMechanics.asStateFlow()

    private var requestsListener: ListenerRegistration? = null
    private var mechanicsListener: ListenerRegistration? = null
    private var activeUserId: String? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                startListening(user.uid)
            } else {
                stopListening()
            }
        }
        auth.currentUser?.uid?.let { startListening(it) }
    }

    private fun startListening(userId: String) {
        if (activeUserId == userId && requestsListener != null) return
        stopListening()
        activeUserId = userId

        requestsListener = db.collection("serviceRequests")
            .whereEqualTo("customerId", userId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "serviceRequests")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                val allRequests = snapshots.documents
                    .mapNotNull { parseRequest(it) }
                    .sortedByDescending { it.createdAt }

                _requestHistory.value = allRequests

                // Keep COMPLETED requests active until customer submits rating so tracking auto-opens rating screen.
                val active = allRequests.firstOrNull {
                    !it.status.isTerminal || (it.status == JobStatus.COMPLETED && it.ratingGiven == null)
                }
                _activeRequest.value = active
            }

        mechanicsListener = db.collection("mechanicProfiles")
            .whereEqualTo("approvalStatus", "APPROVED")
            .whereEqualTo("isOnline", true)
            .whereEqualTo("isBusy", false)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "mechanicProfiles")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener
                val onlineMechanics = snapshots.documents.mapNotNull { parseAvailableMechanic(it) }
                _nearbyMechanics.value = onlineMechanics
            }
    }

    private fun stopListening() {
        requestsListener?.remove()
        requestsListener = null
        mechanicsListener?.remove()
        mechanicsListener = null
        activeUserId = null
        _activeRequest.value = null
        _requestHistory.value = emptyList()
        _nearbyMechanics.value = emptyList()
    }

    private fun parseAvailableMechanic(doc: DocumentSnapshot): Mechanic? {
        val locMap = doc.get("currentLocation") as? Map<*, *>
        val lat = (locMap?.get("lat") as? Number)?.toDouble()
        val lng = (locMap?.get("lng") as? Number)?.toDouble()
        val locality = locMap?.get("address") as? String ?: ""
        val vehicleTypes = (doc.get("vehicleTypes") as? List<*>)
            ?.mapNotNull { it as? String }
            ?.joinToString(", ")
            ?.ifBlank { "Bike & Car" }
            ?: "Bike & Car"

        return Mechanic(
            id = doc.getString("mechanicId") ?: doc.id,
            name = doc.getString("name") ?: "Verified Mechanic",
            rating = (doc.get("ratingAverage") as? Number)?.toFloat() ?: 4.9f,
            rescuesCompleted = (doc.get("totalCompletedJobs") as? Number)?.toInt() ?: 0,
            vehicleInfo = doc.getString("workshopName")?.takeIf { it.isNotBlank() } ?: vehicleTypes,
            phoneMasked = "+91-XXXXXX0000",
            isVerified = true,
            currentLat = lat,
            currentLng = lng,
            locality = locality
        )
    }

    private fun parseRequest(doc: DocumentSnapshot): EmergencyRequest? {
        val vehicleType = VehicleType.fromBackendCode(doc.getString("vehicleType"))
        val issueTitle = doc.getString("issueTitle")
            ?: doc.getString("issueCategory")
            ?: doc.getString("serviceCategory")
            ?: "Emergency Repair"
        val serviceCategory = doc.getString("serviceCategory") ?: ""
        val services = ServiceCatalog.getServices(vehicleType)
        val serviceItem = services.firstOrNull {
            it.title.equals(issueTitle, ignoreCase = true) ||
                it.toBackendServiceCategory().equals(serviceCategory, ignoreCase = true)
        } ?: services.first()

        val status = mapStringToStatus(doc.getString("status") ?: "REQUESTED")

        val locMap = doc.get("location") as? Map<*, *>
        val lat = (locMap?.get("lat") as? Number)?.toDouble()
            ?: (locMap?.get("latitude") as? Number)?.toDouble()
        val lng = (locMap?.get("lng") as? Number)?.toDouble()
            ?: (locMap?.get("longitude") as? Number)?.toDouble()

        val locationData = LocationData(
            latitude = lat,
            longitude = lng,
            address = locMap?.get("address") as? String ?: "",
            landmark = locMap?.get("landmark") as? String ?: "",
            city = locMap?.get("city") as? String ?: "",
            isGpsAccurate = locMap?.get("isGpsAccurate") as? Boolean ?: false,
            isObtained = (lat != null && lng != null) || !(locMap?.get("address") as? String).isNullOrBlank()
        )

        val assignedMechanicId = doc.getString("assignedMechanicId")
        val mechLocMap = doc.get("assignedMechanicLocation") as? Map<*, *>
        val mechLat = (mechLocMap?.get("lat") as? Number)?.toDouble()
        val mechLng = (mechLocMap?.get("lng") as? Number)?.toDouble()
        val mechanic = if (!assignedMechanicId.isNullOrBlank()) {
            Mechanic(
                id = assignedMechanicId,
                name = doc.getString("assignedMechanicName") ?: "Assigned Mechanic",
                rating = 4.9f,
                rescuesCompleted = 0,
                vehicleInfo = "FixMechanic Verified Unit",
                phoneMasked = doc.getString("assignedMechanicPhoneMasked")
                    ?: doc.getString("assignedMechanicPhone")
                    ?: "+91-XXXXXX0000",
                isVerified = true,
                currentLat = mechLat,
                currentLng = mechLng,
                locality = mechLocMap?.get("address") as? String ?: ""
            )
        } else null

        val createdAtMs = (doc.get("createdAt") as? Number)?.toLong()
            ?: doc.getTimestamp("createdAt")?.toDate()?.time
            ?: System.currentTimeMillis()

        val baseFee = (doc.get("estimatedPriceInr") as? Number)?.toInt()
            ?: (doc.get("baseInspectionFee") as? Number)?.toInt()
            ?: serviceItem.estimatedPriceInr

        return EmergencyRequest(
            id = doc.id,
            vehicleType = vehicleType,
            serviceItem = serviceItem,
            vehicleMakeModel = doc.getString("vehicleMakeModel") ?: "Vehicle",
            registrationNumber = doc.getString("vehicleRegNumber")
                ?: doc.getString("registrationNumber")
                ?: "",
            issueDescription = doc.getString("description") ?: "",
            photoUri = doc.getString("photoUri")?.takeIf { it.isNotBlank() },
            location = locationData,
            status = status,
            assignedMechanic = mechanic,
            createdAt = createdAtMs,
            baseInspectionFee = baseFee,
            finalPartsFee = (doc.get("finalPartsFee") as? Number)?.toInt() ?: 0,
            totalPaid = (doc.get("totalPaid") as? Number)?.toInt() ?: 0,
            ratingGiven = (doc.get("ratingGiven") as? Number)?.toFloat(),
            feedbackComment = doc.getString("feedbackComment"),
            statusHistory = listOf(StatusHistoryEntry(status, createdAtMs))
        )
    }

    private fun mapStringToStatus(raw: String): JobStatus {
        return when (raw.uppercase()) {
            "REQUESTED" -> JobStatus.REQUESTED
            "SEARCHING" -> JobStatus.SEARCHING
            "ASSIGNED" -> JobStatus.ASSIGNED
            "ACCEPTED" -> JobStatus.ACCEPTED
            "EN_ROUTE" -> JobStatus.EN_ROUTE
            "ARRIVED" -> JobStatus.ARRIVED
            "IN_PROGRESS" -> JobStatus.IN_PROGRESS
            "COMPLETED" -> JobStatus.COMPLETED
            "CANCELLED" -> JobStatus.CANCELLED
            "NO_MECHANIC_FOUND" -> JobStatus.NO_MECHANIC_FOUND
            "EXPIRED" -> JobStatus.EXPIRED
            else -> JobStatus.REQUESTED
        }
    }

    override suspend fun submitEmergencyRequest(request: EmergencyRequest): Result<EmergencyRequest> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Customer must be signed in with Firebase."))

        val lat = request.location.latitude
        val lng = request.location.longitude
        if (lat == null || lng == null || lat.isNaN() || lng.isNaN() || (lat == 0.0 && lng == 0.0)) {
            return Result.failure(
                IllegalStateException("Valid GPS or geocoded coordinates are required before dispatching nearby mechanics.")
            )
        }

        return try {
            val payload = JSONObject()
                .put("vehicleType", request.vehicleType.toBackendVehicleType())
                .put("vehicleMakeModel", request.vehicleMakeModel)
                .put("vehicleRegNumber", request.registrationNumber.ifBlank { "UNREGISTERED" })
                .put("serviceCategory", request.serviceItem.toBackendServiceCategory())
                .put("issueTitle", request.serviceItem.title)
                .put("description", request.issueDescription)
                .put("photoUri", request.photoUri ?: "")
                .put("estimatedPriceInr", request.baseInspectionFee)
                .put("lat", lat)
                .put("lng", lng)
                .put("address", request.location.address)
                .put("landmark", request.location.landmark)
                .put("city", request.location.city)
                .put("isGpsAccurate", request.location.isGpsAccurate)

            val result = backendApiClient.callFunction("createServiceRequest", payload)
            val requestId = result.optString("requestId")
            val statusStr = result.optString("status", "SEARCHING")
            val created = request.copy(
                id = requestId,
                status = mapStringToStatus(statusStr)
            )
            _activeRequest.value = created
            Result.success(created)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "createServiceRequest")
            Result.failure(e)
        }
    }

    override suspend fun cancelActiveRequest(): Result<Unit> {
        val active = _activeRequest.value
            ?: return Result.failure(IllegalStateException("No active emergency request to cancel."))
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User not authenticated."))

        return try {
            backendApiClient.callFunction(
                "updateJobStatus",
                JSONObject()
                    .put("requestId", active.id)
                    .put("targetStatus", "CANCELLED")
                    .put("note", "Cancelled by customer")
            )
            _activeRequest.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "updateJobStatus/${active.id}")
            Result.failure(e)
        }
    }

    override suspend fun retrySearch(): Result<Unit> {
        val active = _activeRequest.value
            ?: return Result.failure(IllegalStateException("No active request to retry."))
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not logged in."))

        return try {
            val res = backendApiClient.callFunction(
                "retryServiceRequest",
                JSONObject().put("requestId", active.id)
            )
            val statusStr = res.optString("status", "SEARCHING")
            _activeRequest.value = active.copy(status = mapStringToStatus(statusStr))
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "retryServiceRequest/${active.id}")
            Result.failure(e)
        }
    }

    override suspend fun triggerOfferExpiryCheck(): Result<Unit> {
        val active = _activeRequest.value ?: return Result.success(Unit)
        if (active.status != JobStatus.SEARCHING) return Result.success(Unit)
        auth.currentUser?.uid ?: return Result.success(Unit)

        return try {
            val res = backendApiClient.callFunction(
                "triggerOfferExpiryCheck",
                JSONObject().put("requestId", active.id)
            )
            if (res.optBoolean("expired", false)) {
                _activeRequest.value = active.copy(status = JobStatus.EXPIRED)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeJob(rating: Float, comment: String): Result<Unit> {
        val active = _activeRequest.value
            ?: return Result.failure(IllegalStateException("No active request to review."))
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User not authenticated."))

        return try {
            backendApiClient.callFunction(
                "submitJobRating",
                JSONObject()
                    .put("requestId", active.id)
                    .put("score", rating.toDouble())
                    .put("feedback", comment)
            )
            _activeRequest.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "submitJobRating")
            Result.failure(e)
        }
    }
}
