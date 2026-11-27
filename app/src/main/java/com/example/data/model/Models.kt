package com.example.data.model

enum class VehicleType(val displayName: String, val hindiName: String) {
    BIKE("Motorcycle / Scooter", "मोटरसाइकिल / स्कूटर"),
    CAR("Car / Four-Wheeler", "कार / चार पहिया वाहन");

    fun toBackendVehicleType(): String = when (this) {
        BIKE -> "TWO_WHEELER"
        CAR -> "FOUR_WHEELER_CAR"
    }

    companion object {
        fun fromBackendCode(raw: String?): VehicleType {
            val s = (raw ?: "").trim().uppercase()
            return when {
                s.contains("TWO_WHEELER") || s == "BIKE" || s == "SCOOTER" || s == "2W" -> BIKE
                else -> CAR
            }
        }
    }
}

data class ServiceItem(
    val id: String,
    val title: String,
    val hindiTitle: String,
    val vehicleType: VehicleType,
    val estimatedPriceInr: Int,
    val etaEstimateMinutes: Int,
    val description: String,
    val categoryIcon: String
) {
    fun toBackendServiceCategory(): String = when (id) {
        "bike_puncture", "bike_tyre", "car_tyre" -> "FLAT_TYRE"
        "bike_battery", "car_battery" -> "BATTERY_JUMPSTART"
        "bike_fuel", "car_fuel" -> "FUEL_DELIVERY"
        "bike_chain_clutch", "bike_chain", "bike_brake" -> "CLUTCH_BRAKE_CHAIN"
        "car_lockout" -> "LOCKOUT"
        "car_overheating" -> "OVERHEATING_COOLANT"
        "bike_towing", "car_towing" -> "TOWING"
        "bike_other", "car_other" -> "OTHER"
        else -> "MINOR_MECHANICAL"
    }
}

/**
 * Authoritative Job Status enum aligned with unified FixMechanic backend:
 * REQUESTED, SEARCHING, ASSIGNED, ACCEPTED, EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED.
 */
enum class JobStatus(
    val stepIndex: Int,
    val label: String,
    val hindiLabel: String,
    val isTerminal: Boolean = false
) {
    REQUESTED(0, "Requested", "अनुरोध प्राप्त"),
    SEARCHING(1, "Searching", "मैकेनिक खोज जारी"),
    ASSIGNED(2, "Assigned", "नियुक्त"),
    ACCEPTED(3, "Accepted", "स्वीकृत"),
    EN_ROUTE(4, "En Route", "रास्ते में"),
    ARRIVED(5, "Arrived", "पहुंच गए"),
    IN_PROGRESS(6, "In Progress", "मरम्मत जारी"),
    COMPLETED(7, "Completed", "संपन्न", isTerminal = true),
    CANCELLED(-1, "Cancelled", "रद्द", isTerminal = true),
    NO_MECHANIC_FOUND(-2, "No Mechanic Found", "कोई मैकेनिक उपलब्ध नहीं", isTerminal = true),
    EXPIRED(-3, "Request Expired", "अनुरोध समाप्त", isTerminal = true)
}

data class LocationData(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String = "",
    val landmark: String = "",
    val city: String = "",
    val isGpsAccurate: Boolean = false,
    val isObtained: Boolean = false
)

data class Mechanic(
    val id: String,
    val name: String,
    val rating: Float,
    val rescuesCompleted: Int,
    val vehicleInfo: String,
    val phoneMasked: String = "+91-XXXXXX0000",
    val isVerified: Boolean = true,
    val photoUrl: String? = null,
    val currentDistanceKm: Float? = null,
    val estimatedArrivalMinutes: Int? = null,
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val locality: String = ""
)

data class ChatMessage(
    val id: String,
    val jobId: String,
    val senderName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isCustomer: Boolean = true
)

data class StatusHistoryEntry(
    val status: JobStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
)

data class EmergencyRequest(
    val id: String,
    val vehicleType: VehicleType,
    val serviceItem: ServiceItem,
    val vehicleMakeModel: String,
    val registrationNumber: String = "",
    val issueDescription: String = "",
    val photoUri: String? = null,
    val location: LocationData,
    val status: JobStatus = JobStatus.REQUESTED,
    val assignedMechanic: Mechanic? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val baseInspectionFee: Int = 249,
    val finalPartsFee: Int = 0,
    val totalPaid: Int = 0,
    val ratingGiven: Float? = null,
    val feedbackComment: String? = null,
    val statusHistory: List<StatusHistoryEntry> = listOf(StatusHistoryEntry(JobStatus.REQUESTED))
)

data class BackendIntegrationStatus(
    val firebaseAuthAvailable: Boolean = false,
    val firestoreAvailable: Boolean = false,
    val fcmAvailable: Boolean = false,
    val backendApiAvailable: Boolean = false,
    val isDemoMode: Boolean = false,
    val activeEnvironment: String = "Firebase Spark (Unified Firestore & Dispatch Server)",
    val statusSummary: String = "Connected to shared Firebase project fixmechanic-firebase ((default) database)."
)
