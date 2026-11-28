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
import java.util.concurrent.atomic.AtomicLong

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
    @Volatile
    private var activeUserId: String? = null
    private val sessionGeneration = AtomicLong(0L)

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                startListening(user.uid)
            } else {
                clearSession()
            }
        }
        auth.currentUser?.uid?.let { startListening(it) }
    }

    override fun refreshSession() {
        val currentUid = auth.currentUser?.uid
        if (currentUid != null) {
            stopListening()
            startListening(currentUid)
        } else {
            clearSession()
        }
    }

    override fun clearSession() {
        stopListening()
    }

    private fun startListening(userId: String) {
        if (activeUserId == userId && requestsListener != null) return
        stopListening()
        activeUserId = userId
        val generation = sessionGeneration.incrementAndGet()

        requestsListener = db.collection("serviceRequests")
            .whereEqualTo("customerId", userId)
            .addSnapshotListener { snapshots, error ->
                if (generation != sessionGeneration.get() || activeUserId != userId || auth.currentUser?.uid != userId) {
                    return@addSnapshotListener
                }
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "serviceRequests")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                val allRequests = snapshots.documents
                    .filter { it.getString("customerId") == userId }
                    .mapNotNull { parseRequest(it) }
                    .sortedByDescending { it.createdAt }

                _requestHistory.value = allRequests

                // Keep COMPLETED requests active until customer submits rating so tracking auto-opens rating screen.
                val active = allRequests.firstOrNull {
                    !it.status.isTerminal || (it.status == JobStatus.COMPLETED && it.ratingGiven == null)
                }
                _activeRequest.value = active
            }

        // Query on a single indexed field (isOnline == true) to avoid missing composite index failures,
        // then filter availability and verification status client-side in real time.
        mechanicsListener = db.collection("mechanicProfiles")
            .whereEqualTo("isOnline", true)
            .addSnapshotListener { snapshots, error ->
                if (generation != sessionGeneration.get() || activeUserId != userId || auth.currentUser?.uid != userId) {
                    return@addSnapshotListener
                }
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "mechanicProfiles")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener
                val onlineMechanics = snapshots.documents
                    .mapNotNull { parseAvailableMechanic(it) }
                    .filter { it.isOnline && it.isAvailable && it.isVerified && !it.isBlocked }
                _nearbyMechanics.value = onlineMechanics
            }
    }

    private fun stopListening() {
        sessionGeneration.incrementAndGet()
        requestsListener?.remove()
        requestsListener = null
        mechanicsListener?.remove()
        mechanicsListener = null
        activeUserId = null
        _activeRequest.value = null
        _requestHistory.value = emptyList()
        _nearbyMechanics.value = emptyList()
    }

    private fun readDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }

    private fun extractCoordinatesAndAddress(doc: DocumentSnapshot): Triple<Double?, Double?, String> {
        val hasCustom = doc.getBoolean("hasCustomLocation") == true
        val candidateKeys = if (hasCustom) {
            listOf("customLocation", "currentLocation", "liveLocation", "location", "workshopLocation")
        } else {
            listOf("currentLocation", "liveLocation", "location", "customLocation", "workshopLocation")
        }

        for (key in candidateKeys) {
            val raw = doc.get(key)
            if (raw is Map<*, *>) {
                val lat = readDouble(raw["lat"]) ?: readDouble(raw["latitude"])
                val lng = readDouble(raw["lng"]) ?: readDouble(raw["longitude"])
                val addr = (raw["address"] as? String)
                    ?: (raw["locality"] as? String)
                    ?: (raw["city"] as? String)
                    ?: ""
                if (lat != null && lng != null && !(lat == 0.0 && lng == 0.0)) {
                    return Triple(lat, lng, addr)
                }
            } else if (raw is com.google.firebase.firestore.GeoPoint) {
                if (!(raw.latitude == 0.0 && raw.longitude == 0.0)) {
                    val addr = doc.getString("address") ?: doc.getString("locality") ?: doc.getString("city") ?: ""
                    return Triple(raw.latitude, raw.longitude, addr)
                }
            }
        }

        // Top-level lat/lng fallback
        val topLat = readDouble(doc.get("lat")) ?: readDouble(doc.get("latitude"))
        val topLng = readDouble(doc.get("lng")) ?: readDouble(doc.get("longitude"))
        val topAddr = doc.getString("address")
            ?: doc.getString("locality")
            ?: doc.getString("city")
            ?: ""
        return Triple(topLat, topLng, topAddr)
    }

    private fun parseAvailableMechanic(doc: DocumentSnapshot): Mechanic? {
        val (lat, lng, locality) = extractCoordinatesAndAddress(doc)
        val vehicleTypes = (doc.get("vehicleTypes") as? List<*>)
            ?.mapNotNull { it as? String }
            ?.joinToString(", ")
            ?.ifBlank { "Bike & Car" }
            ?: "Bike & Car"
        val rawStatus = doc.getString("approvalStatus")?.trim().orEmpty()
        val isBlocked = rawStatus.equals("BLOCKED", ignoreCase = true) ||
            rawStatus.equals("SUSPENDED", ignoreCase = true) ||
            rawStatus.equals("REJECTED", ignoreCase = true)
        val isVerified = when {
            isBlocked -> false
            rawStatus.equals("PENDING", ignoreCase = true) -> false
            rawStatus.isBlank() -> true
            rawStatus.equals("APPROVED", ignoreCase = true) ||
                rawStatus.equals("VERIFIED", ignoreCase = true) ||
                rawStatus.equals("ACTIVE", ignoreCase = true) -> true
            else -> false
        }
        val isOnline = doc.getBoolean("isOnline") ?: false
        val isBusy = doc.getBoolean("isBusy") ?: false
        val serviceRadiusKm = readDouble(doc.get("serviceRadiusKm"))

        return Mechanic(
            id = doc.getString("mechanicId")?.takeIf { it.isNotBlank() } ?: doc.id,
            name = doc.getString("name")?.takeIf { it.isNotBlank() }
                ?: doc.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: "Verified Mechanic",
            rating = (doc.get("ratingAverage") as? Number)?.toFloat() ?: 4.9f,
            rescuesCompleted = (doc.get("totalCompletedJobs") as? Number)?.toInt() ?: 0,
            vehicleInfo = doc.getString("workshopName")?.takeIf { it.isNotBlank() } ?: vehicleTypes,
            phoneMasked = "+91-XXXXXX0000",
            isVerified = isVerified,
            currentLat = lat,
            currentLng = lng,
            locality = locality,
            isOnline = isOnline,
            isAvailable = !isBusy,
            isBlocked = isBlocked,
            serviceRadiusKm = serviceRadiusKm
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

    private suspend fun dispatchOffersDirectly(
        requestId: String,
        customerId: String,
        request: EmergencyRequest,
        lat: Double,
        lng: Double,
        now: Long
    ) {
        val sourceMechanics = _nearbyMechanics.value.ifEmpty {
            runCatching {
                val snap = db.collection("mechanicProfiles")
                    .whereEqualTo("isOnline", true)
                    .get()
                    .awaitResult()
                snap.documents
                    .mapNotNull { parseAvailableMechanic(it) }
                    .filter { it.isOnline && it.isAvailable && it.isVerified && !it.isBlocked }
                    .also { fetched ->
                        if (fetched.isNotEmpty()) {
                            _nearbyMechanics.value = fetched
                        }
                    }
            }.getOrDefault(emptyList())
        }

        val candidates = com.example.data.location.NearbyMechanicMatcher.filterAndSortNearby(
            mechanics = sourceMechanics,
            userLat = lat,
            userLng = lng,
            searchRadiusKm = 25.0
        ).ifEmpty {
            com.example.data.location.NearbyMechanicMatcher.filterAndSortNearby(
                mechanics = sourceMechanics,
                userLat = lat,
                userLng = lng,
                searchRadiusKm = 50.0
            )
        }.take(5)

        val expiresAt = now + 90_000L
        candidates.forEach { mechanic ->
            runCatching {
                val offerId = "${requestId}_${mechanic.id}"
                db.collection("jobOffers").document(offerId).set(
                    mapOf(
                        "offerId" to offerId,
                        "requestId" to requestId,
                        "customerId" to customerId,
                        "mechanicId" to mechanic.id,
                        "status" to "PENDING",
                        "vehicleType" to request.vehicleType.toBackendVehicleType(),
                        "serviceCategory" to request.serviceItem.toBackendServiceCategory(),
                        "issueTitle" to request.serviceItem.title,
                        "estimatedPriceInr" to request.baseInspectionFee,
                        "distanceKm" to (mechanic.currentDistanceKm ?: 0f),
                        "etaMinutes" to (mechanic.estimatedArrivalMinutes ?: 10),
                        "location" to mapOf(
                            "lat" to lat,
                            "lng" to lng,
                            "address" to request.location.address,
                            "landmark" to request.location.landmark,
                            "city" to request.location.city
                        ),
                        "createdAt" to now,
                        "expiresAt" to expiresAt
                    )
                ).awaitResult()
            }
        }
    }

    private suspend fun ensureCustomerUserRecord(uid: String, now: Long) {
        val user = auth.currentUser ?: return
        runCatching {
            val userDoc = db.collection("users").document(uid)
            val snap = userDoc.get().awaitResult()
            if (!snap.exists()) {
                val resolvedName = user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")
                    ?: "FixMechanic Customer"
                val providers = user.providerData.mapNotNull { it.providerId }
                val authProvider = when {
                    providers.any { it.equals("google.com", ignoreCase = true) } -> "google"
                    providers.any { it.equals("password", ignoreCase = true) } -> "password"
                    else -> "firebase"
                }
                userDoc.set(
                    hashMapOf(
                        "uid" to uid,
                        "role" to "CUSTOMER",
                        "displayName" to resolvedName,
                        "email" to (user.email ?: ""),
                        "phone" to (user.phoneNumber ?: ""),
                        "photoUrl" to (user.photoUrl?.toString() ?: ""),
                        "authProvider" to authProvider,
                        "disabled" to false,
                        "profileCompleted" to false,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                ).awaitResult()
            }
        }
    }

    private suspend fun submitDirectlyToFirestore(
        uid: String,
        request: EmergencyRequest,
        lat: Double,
        lng: Double,
        generation: Long
    ): EmergencyRequest {
        val now = System.currentTimeMillis()
        val user = auth.currentUser
        // Ensure a valid Firebase Auth token is attached before writing to Firestore
        runCatching { user?.getIdToken(false)?.awaitResult() }
        // Guarantee the customer's users/{uid} record exists even if initial sign-in happened offline
        ensureCustomerUserRecord(uid, now)

        val docRef = db.collection("serviceRequests").document()
        val requestId = docRef.id

        val requestDoc = hashMapOf(
            "requestId" to requestId,
            "customerId" to uid,
            "customerName" to (user?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.email?.substringBefore("@")
                ?: "FixMechanic Customer"),
            "customerEmail" to (user?.email ?: ""),
            "customerPhone" to (user?.phoneNumber ?: ""),
            "vehicleType" to request.vehicleType.toBackendVehicleType(),
            "vehicleMakeModel" to request.vehicleMakeModel.ifBlank { "Vehicle" },
            "vehicleRegNumber" to request.registrationNumber.ifBlank { "UNREGISTERED" },
            "registrationNumber" to request.registrationNumber.ifBlank { "UNREGISTERED" },
            "serviceCategory" to request.serviceItem.toBackendServiceCategory(),
            "issueTitle" to request.serviceItem.title,
            "issueCategory" to request.serviceItem.title,
            "description" to request.issueDescription,
            "photoUri" to (request.photoUri ?: ""),
            "estimatedPriceInr" to request.baseInspectionFee,
            "baseInspectionFee" to request.baseInspectionFee,
            "location" to mapOf(
                "lat" to lat,
                "lng" to lng,
                "latitude" to lat,
                "longitude" to lng,
                "address" to request.location.address,
                "landmark" to request.location.landmark,
                "city" to request.location.city,
                "isGpsAccurate" to request.location.isGpsAccurate
            ),
            "status" to JobStatus.SEARCHING.name,
            "assignedMechanicId" to null,
            "createdAt" to now,
            "updatedAt" to now
        )

        docRef.set(requestDoc).awaitResult()

        // Best-effort: initialize chat room & dispatch nearby mechanic offers
        runCatching {
            db.collection("jobChats").document(requestId).set(
                mapOf(
                    "requestId" to requestId,
                    "customerId" to uid,
                    "mechanicId" to null,
                    "status" to "OPEN",
                    "createdAt" to now,
                    "updatedAt" to now
                )
            ).awaitResult()
        }
        dispatchOffersDirectly(requestId, uid, request, lat, lng, now)

        val created = request.copy(
            id = requestId,
            status = JobStatus.SEARCHING,
            createdAt = now
        )
        if (generation == sessionGeneration.get() && auth.currentUser?.uid == uid) {
            _activeRequest.value = created
        }
        return created
    }

    override suspend fun submitEmergencyRequest(request: EmergencyRequest): Result<EmergencyRequest> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Customer must be signed in with Firebase."))
        val generation = sessionGeneration.get()

        val lat = request.location.latitude
        val lng = request.location.longitude
        if (lat == null || lng == null || lat.isNaN() || lng.isNaN() || (lat == 0.0 && lng == 0.0)) {
            return Result.failure(
                IllegalStateException("Valid GPS or geocoded coordinates are required before dispatching nearby mechanics.")
            )
        }

        if (backendApiClient.isConfigured()) {
            try {
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
                if (generation == sessionGeneration.get() && auth.currentUser?.uid == uid) {
                    _activeRequest.value = created
                }
                return Result.success(created)
            } catch (e: Exception) {
                if (!BackendApiClient.isNetworkFailure(e)) {
                    handleFirestoreError(e, OperationType.CREATE, "createServiceRequest")
                    return Result.failure(e)
                }
            }
        }

        return try {
            val created = submitDirectlyToFirestore(uid, request, lat, lng, generation)
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
        val generation = sessionGeneration.get()

        if (backendApiClient.isConfigured()) {
            try {
                backendApiClient.callFunction(
                    "updateJobStatus",
                    JSONObject()
                        .put("requestId", active.id)
                        .put("targetStatus", "CANCELLED")
                        .put("note", "Cancelled by customer")
                )
                if (generation == sessionGeneration.get() && auth.currentUser?.uid == uid) {
                    _activeRequest.value = null
                }
                return Result.success(Unit)
            } catch (e: Exception) {
                if (!BackendApiClient.isNetworkFailure(e)) {
                    handleFirestoreError(e, OperationType.UPDATE, "updateJobStatus/${active.id}")
                    return Result.failure(e)
                }
            }
        }

        return try {
            val now = System.currentTimeMillis()
            db.collection("serviceRequests").document(active.id).update(
                mapOf(
                    "status" to JobStatus.CANCELLED.name,
                    "cancelledBy" to uid,
                    "cancelReason" to "Cancelled by customer",
                    "updatedAt" to now
                )
            ).awaitResult()
            runCatching {
                db.collection("jobChats").document(active.id).update(
                    mapOf("status" to "CLOSED", "updatedAt" to now)
                ).awaitResult()
            }
            if (generation == sessionGeneration.get() && auth.currentUser?.uid == uid) {
                _activeRequest.value = null
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "serviceRequests/${active.id}")
            Result.failure(e)
        }
    }

    override suspend fun retrySearch(): Result<Unit> {
        val active = _activeRequest.value
            ?: return Result.failure(IllegalStateException("No active request to retry."))
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not logged in."))
        val generation = sessionGeneration.get()

        if (backendApiClient.isConfigured()) {
            try {
                val res = backendApiClient.callFunction(
                    "retryServiceRequest",
                    JSONObject().put("requestId", active.id)
                )
                val statusStr = res.optString("status", "SEARCHING")
                if (generation == sessionGeneration.get() && auth.currentUser?.uid == uid) {
                    _activeRequest.value = active.copy(status = mapStringToStatus(statusStr))
                }
                return Result.success(Unit)
            } catch (e: Exception) {
                if (!BackendApiClient.isNetworkFailure(e)) {
                    handleFirestoreError(e, OperationType.UPDATE, "retryServiceRequest/${active.id}")
                    return Result.failure(e)
                }
            }
        }

        return try {
            val now = System.currentTimeMillis()
            db.collection("serviceRequests").document(active.id).update(
                mapOf(
                    "status" to JobStatus.SEARCHING.name,
                    "updatedAt" to now
                )
            ).awaitResult()
            val lat = active.location.latitude
            val lng = active.location.longitude
            if (lat != null && lng != null) {
                dispatchOffersDirectly(active.id, uid, active, lat, lng, now)
            }
            if (generation == sessionGeneration.get() && auth.currentUser?.uid == uid) {
                _activeRequest.value = active.copy(status = JobStatus.SEARCHING)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "serviceRequests/${active.id}")
            Result.failure(e)
        }
    }

    override suspend fun triggerOfferExpiryCheck(): Result<Unit> {
        val active = _activeRequest.value ?: return Result.success(Unit)
        if (active.status != JobStatus.SEARCHING) return Result.success(Unit)
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val generation = sessionGeneration.get()

        if (!backendApiClient.isConfigured()) {
            return Result.success(Unit)
        }

        return try {
            val res = backendApiClient.callFunction(
                "triggerOfferExpiryCheck",
                JSONObject().put("requestId", active.id)
            )
            if (res.optBoolean("expired", false) && generation == sessionGeneration.get() && auth.currentUser?.uid == uid) {
                _activeRequest.value = active.copy(status = JobStatus.EXPIRED)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (BackendApiClient.isNetworkFailure(e)) Result.success(Unit) else Result.failure(e)
        }
    }

    override suspend fun completeJob(rating: Float, comment: String, paymentMethod: String): Result<Unit> {
        val active = _activeRequest.value
            ?: _requestHistory.value.firstOrNull { it.status == JobStatus.COMPLETED && it.ratingGiven == null }
            ?: return Result.failure(IllegalStateException("No active request to review."))
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User not authenticated."))
        val generation = sessionGeneration.get()

        if (backendApiClient.isConfigured()) {
            try {
                backendApiClient.callFunction(
                    "submitJobRating",
                    JSONObject()
                        .put("requestId", active.id)
                        .put("score", rating.toDouble())
                        .put("feedback", comment)
                        .put("paymentMethod", paymentMethod)
                )
                if (generation == sessionGeneration.get() && auth.currentUser?.uid == uid) {
                    _activeRequest.value = null
                }
                return Result.success(Unit)
            } catch (e: Exception) {
                if (!BackendApiClient.isNetworkFailure(e)) {
                    handleFirestoreError(e, OperationType.CREATE, "submitJobRating")
                    return Result.failure(e)
                }
            }
        }

        return try {
            val now = System.currentTimeMillis()
            val cleanComment = comment.trim()
            db.collection("ratings").document(active.id).set(
                mapOf(
                    "requestId" to active.id,
                    "customerId" to uid,
                    "mechanicId" to (active.assignedMechanic?.id ?: ""),
                    "score" to rating.toDouble(),
                    "feedback" to cleanComment,
                    "paymentMethod" to paymentMethod,
                    "createdAt" to now
                )
            ).awaitResult()
            db.collection("serviceRequests").document(active.id).update(
                mapOf(
                    "ratingGiven" to rating.toDouble(),
                    "feedbackComment" to cleanComment,
                    "paymentMethod" to paymentMethod,
                    "updatedAt" to now
                )
            ).awaitResult()
            if (generation == sessionGeneration.get() && auth.currentUser?.uid == uid) {
                _activeRequest.value = null
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "ratings/${active.id}")
            Result.failure(e)
        }
    }
}
