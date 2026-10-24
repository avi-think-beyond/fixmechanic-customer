package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.datasource.ServiceCatalog
import com.example.data.model.EmergencyRequest
import com.example.data.model.JobStatus
import com.example.data.model.LocationData
import com.example.data.model.Mechanic
import com.example.data.model.StatusHistoryEntry
import com.example.data.model.VehicleType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirestoreEmergencyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : EmergencyRepository {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        FirebaseAuth.getInstance()
    )

    override val isDemoMode: Boolean = false

    private val _activeRequest = MutableStateFlow<EmergencyRequest?>(null)
    override val activeRequest: StateFlow<EmergencyRequest?> = _activeRequest.asStateFlow()

    private val _requestHistory = MutableStateFlow<List<EmergencyRequest>>(emptyList())
    override val requestHistory: StateFlow<List<EmergencyRequest>> = _requestHistory.asStateFlow()

    private var requestsListener: ListenerRegistration? = null
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

                val allRequests = snapshots.documents.mapNotNull { parseRequest(it) }
                    .sortedByDescending { it.createdAt }

                _requestHistory.value = allRequests

                // Active request is the latest one that is not in a terminal state
                val active = allRequests.firstOrNull { !it.status.isTerminal }
                _activeRequest.value = active
            }
    }

    private fun stopListening() {
        requestsListener?.remove()
        requestsListener = null
        activeUserId = null
        _activeRequest.value = null
        _requestHistory.value = emptyList()
    }

    private fun parseRequest(doc: DocumentSnapshot): EmergencyRequest? {
        val vehicleTypeStr = doc.getString("vehicleType") ?: "CAR"
        val vehicleType = try {
            VehicleType.valueOf(vehicleTypeStr.uppercase())
        } catch (_: Exception) {
            VehicleType.CAR
        }

        val issueTitle = doc.getString("issueCategory") ?: "General Breakdown"
        val services = ServiceCatalog.getServices(vehicleType)
        val serviceItem = services.firstOrNull { it.title.equals(issueTitle, ignoreCase = true) }
            ?: services.first()

        val statusStr = doc.getString("status") ?: "REQUESTED"
        val status = mapStringToStatus(statusStr)

        val locMap = doc.get("location") as? Map<*, *>
        val locationData = LocationData(
            latitude = (locMap?.get("latitude") as? Number)?.toDouble(),
            longitude = (locMap?.get("longitude") as? Number)?.toDouble(),
            address = locMap?.get("address") as? String ?: "",
            landmark = locMap?.get("landmark") as? String ?: "",
            city = locMap?.get("city") as? String ?: "",
            isGpsAccurate = locMap?.get("isGpsAccurate") as? Boolean ?: false,
            isObtained = true
        )

        val assignedMechanicId = doc.getString("assignedMechanicId")
        val mechanic = if (!assignedMechanicId.isNullOrBlank()) {
            Mechanic(
                id = assignedMechanicId,
                name = doc.getString("assignedMechanicName") ?: "Assigned Mechanic",
                rating = 4.8f,
                rescuesCompleted = 120,
                vehicleInfo = "FixMechanic Emergency Unit",
                phoneMasked = doc.getString("assignedMechanicPhone") ?: "+91 98XXX-XX321",
                isVerified = true
            )
        } else null

        val createdAtTs = doc.getTimestamp("createdAt")?.toDate()?.time
            ?: System.currentTimeMillis()

        return EmergencyRequest(
            id = doc.id,
            vehicleType = vehicleType,
            serviceItem = serviceItem,
            vehicleMakeModel = doc.getString("vehicleMakeModel") ?: "Vehicle",
            registrationNumber = doc.getString("registrationNumber") ?: "",
            issueDescription = doc.getString("description") ?: "",
            photoUri = doc.getString("photoUri"),
            location = locationData,
            status = status,
            assignedMechanic = mechanic,
            createdAt = createdAtTs,
            baseInspectionFee = (doc.getLong("baseInspectionFee") ?: 249).toInt(),
            finalPartsFee = (doc.getLong("finalPartsFee") ?: 0).toInt(),
            totalPaid = (doc.getLong("totalPaid") ?: 0).toInt(),
            ratingGiven = doc.getDouble("ratingGiven")?.toFloat(),
            feedbackComment = doc.getString("feedbackComment"),
            statusHistory = listOf(StatusHistoryEntry(status, createdAtTs))
        )
    }

    private fun mapStringToStatus(status: String): JobStatus {
        return when (status.uppercase()) {
            "OPEN", "REQUESTED" -> JobStatus.REQUESTED
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
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User not authenticated with Firebase. Please sign in."))

        return try {
            val docRef = db.collection("serviceRequests").document()
            val requestId = docRef.id

            val locationMap = hashMapOf<String, Any?>(
                "latitude" to (request.location.latitude ?: 0.0),
                "longitude" to (request.location.longitude ?: 0.0),
                "address" to request.location.address,
                "landmark" to request.location.landmark,
                "city" to request.location.city,
                "isGpsAccurate" to request.location.isGpsAccurate
            )

            val payload = hashMapOf<String, Any?>(
                "customerId" to uid,
                "vehicleType" to request.vehicleType.name,
                "vehicleMakeModel" to request.vehicleMakeModel,
                "registrationNumber" to request.registrationNumber,
                "issueCategory" to request.serviceItem.title,
                "description" to request.issueDescription,
                "photoUri" to (request.photoUri ?: ""),
                "location" to locationMap,
                "status" to "REQUESTED",
                "assignedMechanicId" to null,
                "assignedMechanicName" to null,
                "assignedMechanicPhone" to null,
                "baseInspectionFee" to request.baseInspectionFee,
                "finalPartsFee" to 0,
                "totalPaid" to 0,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(payload).await()

            // Record initial audit event
            val eventRef = db.collection("requestEvents").document()
            eventRef.set(
                hashMapOf(
                    "requestId" to requestId,
                    "eventType" to "REQUEST_CREATED",
                    "message" to "Roadside assistance request broadcasted to dispatch network",
                    "status" to "REQUESTED",
                    "actorId" to uid,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()

            val created = request.copy(
                id = requestId,
                status = JobStatus.REQUESTED
            )
            _activeRequest.value = created
            Result.success(created)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "serviceRequests")
            Result.failure(e)
        }
    }

    override suspend fun cancelActiveRequest(): Result<Unit> {
        val active = _activeRequest.value
            ?: return Result.failure(IllegalStateException("No active emergency request to cancel."))
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User not authenticated."))

        return try {
            db.collection("serviceRequests").document(active.id).update(
                mapOf(
                    "status" to "CANCELLED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("requestEvents").document().set(
                hashMapOf(
                    "requestId" to active.id,
                    "eventType" to "REQUEST_CANCELLED",
                    "message" to "Request cancelled by customer",
                    "status" to "CANCELLED",
                    "actorId" to uid,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()

            _activeRequest.value = active.copy(status = JobStatus.CANCELLED)
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "serviceRequests/${active.id}")
            Result.failure(e)
        }
    }

    override suspend fun retrySearch(): Result<Unit> {
        val active = _activeRequest.value
            ?: return Result.failure(IllegalStateException("No active request to retry."))
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not logged in."))

        return try {
            db.collection("serviceRequests").document(active.id).update(
                mapOf(
                    "status" to "SEARCHING",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("requestEvents").document().set(
                hashMapOf(
                    "requestId" to active.id,
                    "eventType" to "SEARCH_RETRY",
                    "message" to "Re-broadcasting to expanded mechanic radius",
                    "status" to "SEARCHING",
                    "actorId" to uid,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()

            _activeRequest.value = active.copy(status = JobStatus.SEARCHING)
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "serviceRequests/${active.id}")
            Result.failure(e)
        }
    }

    override suspend fun transitionJobStatus(nextStatus: JobStatus) {
        val active = _activeRequest.value ?: return
        val uid = auth.currentUser?.uid ?: return

        try {
            db.collection("serviceRequests").document(active.id).update(
                mapOf(
                    "status" to nextStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("requestEvents").document().set(
                hashMapOf(
                    "requestId" to active.id,
                    "eventType" to "STATUS_CHANGE",
                    "message" to "Status progressed to ${nextStatus.label}",
                    "status" to nextStatus.name,
                    "actorId" to uid,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()

            _activeRequest.value = active.copy(status = nextStatus)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "serviceRequests/${active.id}")
        }
    }

    override suspend fun completeJob(rating: Float, comment: String): Result<Unit> {
        val active = _activeRequest.value
            ?: return Result.failure(IllegalStateException("No active request to review."))
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User not authenticated."))

        return try {
            // Write to reviews collection
            val reviewDoc = db.collection("reviews").document()
            reviewDoc.set(
                hashMapOf(
                    "requestId" to active.id,
                    "customerId" to uid,
                    "mechanicId" to (active.assignedMechanic?.id ?: "mechanic_assigned"),
                    "rating" to rating.toDouble(),
                    "comment" to comment,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Update service request
            db.collection("serviceRequests").document(active.id).update(
                mapOf(
                    "status" to "COMPLETED",
                    "ratingGiven" to rating.toDouble(),
                    "feedbackComment" to comment,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            _activeRequest.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "reviews")
            Result.failure(e)
        }
    }
}
